package com.tabula.v3.data.repository

import android.content.Context
import com.tabula.v3.data.cache.ImageHashCache
import com.tabula.v3.data.cache.toHashKey
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 相似组数据类
 * 
 * @param images 组内图片列表（按时间排序）
 * @param startTime 组的最早时间
 * @param endTime 组的最晚时间
 */
data class SimilarGroup(
    val images: List<ImageFile>,
    val startTime: Long,
    val endTime: Long
) {
    /** 组内图片数量 */
    val size: Int get() = images.size

    /**
     * 组的唯一标识（冷却与专清永久标记的主键）。
     * C1/T03-GROUPID：身份 = 成员完整媒体身份键集合的内容寻址摘要——
     * 排序后哈希（成员重排不产生变化）；键含卷（不同卷的不同成员集合必
     * 不相同，跨卷同数值 ID 的组不再共用标记）。旧口径
     * "startTime_首张数字 ID" 对成员重排不稳定且跨卷碰撞，识别与解析见
     * [isLegacyGroupId]/[parseLegacyGroupId]。
     */
    val id: String get() = similarGroupId(images)

    /** 组的时间跨度（毫秒） */
    val durationMs: Long get() = endTime - startTime
}

/**
 * C1/T03-GROUPID：相似组身份 = 排序后的成员完整媒体身份键集合的
 * SHA-256 截断摘要。成员集相同 ⇒ 身份相同（检测与持久化恢复一致）；
 * 任一成员不同（含卷不同）⇒ 身份不同。
 */
internal fun similarGroupId(images: List<ImageFile>): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    images.map { it.mediaIdentityKey() }.sorted().forEach { key ->
        digest.update(key.toByteArray(Charsets.UTF_8))
        digest.update('\n'.code.toByte())
    }
    return "grp_" + digest.digest().joinToString("") { "%02x".format(it) }.take(16)
}

/** 旧口径组 ID 形如 "${startTime 毫秒}_${首张数字 ID}"（无 grp_ 前缀）。 */
internal fun isLegacyGroupId(groupId: String): Boolean =
    !groupId.startsWith("grp_") && Regex("^\\d+_\\d+$").matches(groupId)

/** 解析旧口径组 ID 为 (startTime 毫秒, 首张数字 ID)；非旧格式返回 null。 */
internal fun parseLegacyGroupId(groupId: String): Pair<Long, Long>? {
    if (!isLegacyGroupId(groupId)) return null
    val parts = groupId.split('_')
    val start = parts[0].toLongOrNull() ?: return null
    val firstId = parts[1].toLongOrNull() ?: return null
    return start to firstId
}

/**
 * 相似组检测器
 * 
 * 使用多维度相似度 + 约束并查集聚类算法检测相似照片组。
 * 
 * 核心特点：
 * 1. 多维度判断：时间 + 尺寸 + 文件大小 + 相册 + dHash
 * 2. 约束并查集：防止 single-linkage 桥接效应
 * 3. 组代表图：earliest/latest/medoid 三类代表
 * 4. 动态门槛：大组时自动提高合并门槛
 */
class SimilarGroupDetector(
    private val context: Context,
    private val hashCache: ImageHashCache
) {
    companion object {
        private const val TAG = "SimilarGroupDetector"
        
        /** 时间窗口：只比较此范围内的照片对（10分钟） */
        const val TIME_WINDOW_MS = 10 * 60 * 1000L
        
        /** 单组最大照片数 */
        const val MAX_GROUP_SIZE = 50
        
        /** 大组阈值：超过后提高合并门槛 */
        const val LARGE_GROUP_THRESHOLD = 30
        
        /** 并发计算哈希的批次大小 */
        private const val HASH_COMPUTE_BATCH_SIZE = 10
    }
    
    /**
     * 检测相似组（两阶段优化版）
     * 
     * 阶段1：元数据预筛选（快速，不计算哈希）
     * 阶段2：哈希验证（只对候选图片计算哈希）
     * 
     * @param images 所有图片列表
     * @return 相似组列表（按组大小降序排列）
     */
    suspend fun detectSimilarGroups(
        images: List<ImageFile>
    ): List<SimilarGroup> = withContext(Dispatchers.Default) {
        if (images.size < 2) return@withContext emptyList()
        
        android.util.Log.d(TAG, "Detecting similar groups from ${images.size} images")
        
        // Step 1: 按时间排序
        val sorted = images.sortedBy { it.dateModified }
        
        // Step 2: 元数据预筛选 - 找出时间窗口内的候选图片对
        android.util.Log.d(TAG, "Phase 1: Metadata pre-filtering...")
        val candidatePairs = mutableListOf<Pair<Int, Int>>()
        val candidateImageIndices = mutableSetOf<Int>()
        
        for (i in sorted.indices) {
            for (j in (i + 1) until sorted.size) {
                val timeDiff = sorted[j].dateModified - sorted[i].dateModified
                if (timeDiff > TIME_WINDOW_MS) {
                    break
                }
                
                // 快速元数据检查（不需要哈希）
                val metaScore = calculateMetaScoreOnly(sorted[i], sorted[j])
                if (metaScore >= 25f) {  // 宽松阈值，后续哈希验证会进一步过滤
                    candidatePairs.add(i to j)
                    candidateImageIndices.add(i)
                    candidateImageIndices.add(j)
                }
            }
        }
        
        android.util.Log.d(TAG, "Found ${candidatePairs.size} candidate pairs, ${candidateImageIndices.size} unique images")
        
        if (candidatePairs.isEmpty()) {
            return@withContext emptyList()
        }
        
        // Step 3: 只对候选图片计算哈希
        val candidateImages = candidateImageIndices.map { sorted[it] }
        val hashMap = ensureHashesComputed(candidateImages)
        android.util.Log.d(TAG, "Phase 2: Computed hashes for ${candidateImages.size} candidate images")
        
        // Step 4: 约束并查集聚类（使用哈希验证）
        val unionFind = ConstrainedUnionFind(sorted, hashMap)
        
        for ((i, j) in candidatePairs) {
            val photo1 = sorted[i]
            val photo2 = sorted[j]
            val hash1 = hashMap[photo1.toHashKey()]
            val hash2 = hashMap[photo2.toHashKey()]
            
            // 计算完整相似度（含哈希）
            val (metaScore, hashDist) = calculateSimilarityWithHash(
                photo1, photo2, hash1, hash2
            )
            
            // 检查组大小
            val rootI = unionFind.find(i)
            val rootJ = unionFind.find(j)
            
            if (rootI == rootJ) continue
            
            val sizeI = unionFind.getGroupSize(i)
            val sizeJ = unionFind.getGroupSize(j)
            val combinedSize = sizeI + sizeJ
            
            // 决定是否连边（考虑组大小）
            if (!shouldConnectWithGroupSize(photo1, photo2, metaScore, hashDist, combinedSize)) {
                continue
            }
            
            // 代表图检查（防桥接）
            val repsI = unionFind.getRepresentatives(i)
            val repsJ = unionFind.getRepresentatives(j)
            
            val canJoinI = canJoinGroupWithRepCheck(j, repsI, sorted, hashMap)
            val canJoinJ = canJoinGroupWithRepCheck(i, repsJ, sorted, hashMap)
            
            if (canJoinI && canJoinJ) {
                unionFind.tryUnion(i, j)
            }
        }
        
        // Step 5: 收集结果
        val groups = collectGroups(sorted, unionFind)
        android.util.Log.d(TAG, "Detected ${groups.size} similar groups")
        
        groups
    }
    
    /**
     * 快速计算元数据分数（不需要哈希）
     * 用于预筛选阶段
     */
    private fun calculateMetaScoreOnly(photo1: ImageFile, photo2: ImageFile): Float {
        var score = 0f
        
        val timeDiffSeconds = abs(photo1.dateModified - photo2.dateModified) / 1000
        
        // 时间相近度（0-25分）
        score += when {
            timeDiffSeconds < 5 -> 25f
            timeDiffSeconds < 30 -> 22f
            timeDiffSeconds < 60 -> 18f
            timeDiffSeconds < 180 -> 14f
            timeDiffSeconds < 300 -> 10f
            timeDiffSeconds < 600 -> 5f
            else -> 0f
        }
        
        // 尺寸一致（0-20分）
        if (photo1.actualWidth == photo2.actualWidth && 
            photo1.actualHeight == photo2.actualHeight) {
            score += 20f
        } else {
            val aspectDiff = abs(photo1.aspectRatio - photo2.aspectRatio)
            if (aspectDiff < 0.02f) score += 12f
            else if (aspectDiff < 0.1f) score += 6f
        }
        
        // 文件大小相近（0-10分）
        val sizeDiffPercent = abs(photo1.size - photo2.size).toFloat() / 
                              maxOf(photo1.size, 1L)
        score += when {
            sizeDiffPercent < 0.05f -> 10f
            sizeDiffPercent < 0.10f -> 8f
            sizeDiffPercent < 0.20f -> 5f
            sizeDiffPercent < 0.30f -> 2f
            else -> 0f
        }
        
        // 同一相册（0-5分）
        if (photo1.bucketDisplayName != null && 
            photo1.bucketDisplayName == photo2.bucketDisplayName) {
            score += 5f
        }
        
        return score
    }
    
    /**
     * 确保所有图片的 pHash 已计算。
     * C1/T03-HASHVOLUME：内部映射与缓存全部按完整媒体身份键（卷|ID）——
     * 不同卷的同数值 ID 行各自计算、各自缓存，互不折叠。
     *
     * @return Map<身份键, hash?>，hash 为 null 表示计算失败
     */
    suspend fun ensureHashesComputed(
        images: List<ImageFile>
    ): Map<ImageHashCache.HashKey, Long?> = withContext(Dispatchers.IO) {
        if (images.isEmpty()) return@withContext emptyMap()

        val keys = images.map { it.toHashKey() }
        // TC58/C1.1：内容版本（DATE_MODIFIED 毫秒）参与缓存有效性。
        val contentVersions = images.associate { it.toHashKey() to it.dateModified }

        // Step 1: 批量查询已缓存的哈希（版本过期视为未缓存）
        val cached = hashCache.getHashesBatch(keys, contentVersions)

        // Step 2: 需要计算：未缓存/版本过期/此前失败（有界重试：每轮一次）
        val needCompute = images.filter { img ->
            val cachedHash = cached[img.toHashKey()]
            cachedHash == null || cachedHash.isFailed
        }

        android.util.Log.d(TAG, "Need to compute hash for ${needCompute.size} images")

        // Step 3: 并发计算哈希（分批处理避免内存压力）
        val newResults = mutableMapOf<ImageHashCache.HashKey, PerceptualHash.HashResult>()

        if (needCompute.isNotEmpty()) {
            try {
                needCompute.chunked(HASH_COMPUTE_BATCH_SIZE).forEach { batch ->
                    val batchResults = batch.map { img ->
                        async {
                            try {
                                img.toHashKey() to PerceptualHash.computeDHash(context, img.uri)
                            } catch (e: CancellationException) {
                                throw e  // 重新抛出取消异常
                            } catch (e: Exception) {
                                // 单张图片计算失败不影响其他图片
                                android.util.Log.w(TAG, "Hash compute failed for ${img.id}: ${e.message}")
                                img.toHashKey() to PerceptualHash.HashResult.Failed(e.message ?: "Unknown error")
                            }
                        }
                    }.awaitAll()

                    batchResults.forEach { (key, result) ->
                        newResults[key] = result
                    }
                }

                // Step 4: 批量保存新计算的哈希（附内容版本）
                hashCache.saveHashesBatch(newResults, contentVersions)
            } catch (e: CancellationException) {
                // 协程取消时重新抛出，让上层正确处理
                android.util.Log.d(TAG, "Hash computation cancelled")
                throw e
            } catch (e: Exception) {
                // 其他异常记录日志但不中断流程
                android.util.Log.e(TAG, "Hash computation error: ${e.message}", e)
            }
        }

        // Step 5: 合并结果返回
        val result = mutableMapOf<ImageHashCache.HashKey, Long?>()

        // 已缓存的
        for ((key, cachedHash) in cached) {
            result[key] = cachedHash.hash
        }

        // 新计算的
        for ((key, hashResult) in newResults) {
            result[key] = when (hashResult) {
                is PerceptualHash.HashResult.Success -> hashResult.hash
                is PerceptualHash.HashResult.Failed -> null
            }
        }

        result
    }
    
    /**
     * 计算两张照片的相似度（含内容指纹）
     * 
     * @return Pair<元数据分数, Hash Hamming 距离?>
     */
    private fun calculateSimilarityWithHash(
        photo1: ImageFile,
        photo2: ImageFile,
        hash1: Long?,
        hash2: Long?
    ): Pair<Float, Int?> {
        var metaScore = 0f
        
        val timeDiffSeconds = abs(photo1.dateModified - photo2.dateModified) / 1000
        
        // ========== 时间相近度（0-25分）==========
        metaScore += when {
            timeDiffSeconds < 5 -> 25f      // 连拍级别
            timeDiffSeconds < 30 -> 22f     // 快速切换角度
            timeDiffSeconds < 60 -> 18f     // 同一分钟内
            timeDiffSeconds < 180 -> 14f    // 3分钟内（多角度拍摄）
            timeDiffSeconds < 300 -> 10f    // 5分钟内
            timeDiffSeconds < 600 -> 5f     // 10分钟内
            else -> 0f
        }
        
        // ========== 尺寸一致（0-20分）==========
        // 使用 actualWidth/actualHeight（考虑 EXIF 旋转）
        if (photo1.actualWidth == photo2.actualWidth && 
            photo1.actualHeight == photo2.actualHeight) {
            metaScore += 20f
        } else {
            val aspectDiff = abs(photo1.aspectRatio - photo2.aspectRatio)
            if (aspectDiff < 0.02f) metaScore += 12f
            else if (aspectDiff < 0.1f) metaScore += 6f
        }
        
        // ========== 文件大小相近（0-10分）==========
        val sizeDiffPercent = abs(photo1.size - photo2.size).toFloat() / 
                              maxOf(photo1.size, 1L)
        metaScore += when {
            sizeDiffPercent < 0.05f -> 10f   // 5%以内
            sizeDiffPercent < 0.10f -> 8f    // 10%以内
            sizeDiffPercent < 0.20f -> 5f    // 20%以内
            sizeDiffPercent < 0.30f -> 2f    // 30%以内
            else -> 0f
        }
        
        // ========== 同一相册（0-5分）==========
        // 权重降低，避免截图误判
        if (photo1.bucketDisplayName != null && 
            photo1.bucketDisplayName == photo2.bucketDisplayName) {
            metaScore += 5f
        }
        
        // ========== pHash Hamming 距离 ==========
        val hashDistance = if (hash1 != null && hash2 != null) {
            PerceptualHash.hammingDistance(hash1, hash2)
        } else {
            null
        }
        
        return metaScore to hashDistance
    }
    
    /**
     * 判断两张照片是否应该连边
     */
    private fun shouldConnect(
        photo1: ImageFile,
        photo2: ImageFile,
        metaScore: Float,
        hashDistance: Int?
    ): Boolean {
        val timeDiffSeconds = abs(photo1.dateModified - photo2.dateModified) / 1000
        val sameResolution = (photo1.actualWidth == photo2.actualWidth && 
                              photo1.actualHeight == photo2.actualHeight)
        
        return when {
            // ========== Hash 缺失：严格的硬限制 ==========
            hashDistance == null -> {
                // 三重约束：时间近 + 同分辨率 + 高元数据分
                timeDiffSeconds <= 180 && sameResolution && metaScore >= 55f
            }
            
            // ========== Hash 存在：分层判断 ==========
            
            // Tier 1: 内容高度相似（hashDist ≤ 6）
            hashDistance <= 6 -> metaScore >= 15f
            
            // Tier 2: 内容可能相似（hashDist 7-10）
            hashDistance <= 10 -> metaScore >= 30f
            
            // Tier 3: 内容边缘相似（hashDist 11-14）
            hashDistance <= 14 -> metaScore >= 45f
            
            // Tier 4: 内容不相似
            else -> false
        }
    }
    
    /**
     * 考虑组大小的连边判断
     */
    private fun shouldConnectWithGroupSize(
        photo1: ImageFile,
        photo2: ImageFile,
        metaScore: Float,
        hashDistance: Int?,
        combinedSize: Int
    ): Boolean {
        // 基础判断
        if (!shouldConnect(photo1, photo2, metaScore, hashDistance)) {
            return false
        }
        
        // 大组时提高门槛
        if (combinedSize > LARGE_GROUP_THRESHOLD) {
            // 大组只允许 hashDist <= 10 的合并
            if (hashDistance == null || hashDistance > 10) {
                return false
            }
            // 且 metaScore 要求更高
            if (metaScore < 40f) {
                return false
            }
        }
        
        // 接近最大值时更严格
        if (combinedSize > MAX_GROUP_SIZE - 5) {
            // 只允许 hashDist <= 6 的合并
            if (hashDistance == null || hashDistance > 6) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 代表图约束检查
     */
    private fun canJoinGroupWithRepCheck(
        newPhotoIdx: Int,
        groupReps: List<Int>,
        images: List<ImageFile>,
        hashMap: Map<ImageHashCache.HashKey, Long?>
    ): Boolean {
        val newPhoto = images[newPhotoIdx]
        val newHash = hashMap[newPhoto.toHashKey()]

        // 需要与至少一个代表图通过检查
        return groupReps.any { repIdx ->
            val repPhoto = images[repIdx]
            val repHash = hashMap[repPhoto.toHashKey()]
            
            val timeDiff = abs(newPhoto.dateModified - repPhoto.dateModified) / 1000
            val sameRes = (newPhoto.actualWidth == repPhoto.actualWidth && 
                          newPhoto.actualHeight == repPhoto.actualHeight)
            
            val hashDist = if (newHash != null && repHash != null) {
                PerceptualHash.hammingDistance(newHash, repHash)
            } else {
                null
            }
            
            when {
                // Hash 缺失：超严格
                hashDist == null -> {
                    timeDiff <= 60 && sameRes
                }
                // Hash 存在
                hashDist <= 8 -> true
                hashDist <= 12 -> sameRes || timeDiff <= 120
                else -> false
            }
        }
    }
    
    /**
     * 收集聚类结果
     */
    private fun collectGroups(
        sorted: List<ImageFile>,
        unionFind: ConstrainedUnionFind
    ): List<SimilarGroup> {
        val groups = mutableMapOf<Int, MutableList<ImageFile>>()
        
        for (i in sorted.indices) {
            val root = unionFind.find(i)
            groups.getOrPut(root) { mutableListOf() }.add(sorted[i])
        }
        
        return groups.values
            .filter { it.size >= 2 }
            .map { groupImages ->
                SimilarGroup(
                    images = groupImages.sortedBy { it.dateModified },
                    startTime = groupImages.minOf { it.dateModified },
                    endTime = groupImages.maxOf { it.dateModified }
                )
            }
            .sortedByDescending { it.images.size }
    }
    
    // ========== 内部类：约束并查集 ==========
    
    /**
     * 组代表图集合
     */
    private data class GroupRepresentatives(
        val earliestIdx: Int,
        val latestIdx: Int,
        val medoidIdx: Int
    ) {
        fun toList(): List<Int> = listOf(earliestIdx, medoidIdx, latestIdx).distinct()
    }
    
    /**
     * 约束并查集
     */
    private inner class ConstrainedUnionFind(
        private val images: List<ImageFile>,
        private val hashMap: Map<ImageHashCache.HashKey, Long?>
    ) {
        private val size = images.size
        private val parent = IntArray(size) { it }
        private val rank = IntArray(size) { 0 }
        private val groupSize = IntArray(size) { 1 }
        private val representatives = mutableMapOf<Int, GroupRepresentatives>()
        
        init {
            for (i in 0 until size) {
                representatives[i] = GroupRepresentatives(i, i, i)
            }
        }
        
        fun find(x: Int): Int {
            if (parent[x] != x) {
                parent[x] = find(parent[x])
            }
            return parent[x]
        }
        
        fun getGroupSize(x: Int): Int = groupSize[find(x)]
        
        fun getRepresentatives(x: Int): List<Int> {
            return representatives[find(x)]?.toList() ?: listOf(x)
        }
        
        fun tryUnion(x: Int, y: Int): Boolean {
            val rootX = find(x)
            val rootY = find(y)
            
            if (rootX == rootY) return true
            
            val newSize = groupSize[rootX] + groupSize[rootY]
            if (newSize > MAX_GROUP_SIZE) {
                return false
            }
            
            val (smaller, larger) = if (rank[rootX] < rank[rootY]) {
                rootX to rootY
            } else {
                rootY to rootX
            }
            
            parent[smaller] = larger
            if (rank[rootX] == rank[rootY]) {
                rank[larger]++
            }
            
            groupSize[larger] = newSize
            
            val repsX = representatives[rootX]
            val repsY = representatives[rootY]
            if (repsX != null && repsY != null) {
                representatives[larger] = selectNewRepresentatives(repsX, repsY)
            }
            representatives.remove(smaller)
            
            return true
        }
        
        private fun selectNewRepresentatives(
            repsX: GroupRepresentatives,
            repsY: GroupRepresentatives
        ): GroupRepresentatives {
            val candidates = listOf(
                repsX.earliestIdx, repsX.latestIdx, repsX.medoidIdx,
                repsY.earliestIdx, repsY.latestIdx, repsY.medoidIdx
            ).distinct()
            
            val earliest = candidates.minByOrNull { images[it].dateModified }!!
            val latest = candidates.maxByOrNull { images[it].dateModified }!!
            val medoid = selectMedoid(candidates)
            
            return GroupRepresentatives(earliest, latest, medoid)
        }
        
        private fun selectMedoid(candidates: List<Int>): Int {
            if (candidates.size <= 1) return candidates.first()
            
            return candidates.minByOrNull { candidate ->
                val hash = hashMap[images[candidate].toHashKey()]
                if (hash == null) {
                    Int.MAX_VALUE.toDouble()
                } else {
                    val distances = candidates
                        .filter { it != candidate }
                        .mapNotNull { other ->
                            val otherHash = hashMap[images[other].toHashKey()]
                            otherHash?.let { PerceptualHash.hammingDistance(hash, it) }
                        }
                    
                    if (distances.isEmpty()) {
                        Int.MAX_VALUE.toDouble()
                    } else {
                        distances.average()
                    }
                }
            } ?: candidates.first()
        }
    }
}
