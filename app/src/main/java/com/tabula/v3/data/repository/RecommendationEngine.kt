package com.tabula.v3.data.repository

import android.content.Context
import com.tabula.v3.data.cache.ImageHashCache
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.RecommendMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片推荐引擎
 * 
 * 支持两种推荐模式：
 * 1. 随机漫步模式：真正随机抽取照片，抽取过的照片会进入冷却期，短期内不会再被抽中
 * 2. 相似推荐模式：使用多维度聚类算法检测相似照片组，整组呈现给用户
 * 
 * 两种模式使用独立的冷却机制：
 * - 随机漫步：以单张照片为单位冷却
 * - 相似推荐：以相似组为单位冷却
 * 
 * 屏蔽图集功能：
 * - 用户可标记某些图集为"已整理/屏蔽"，这些图集中的照片不会出现在推荐流中
 * - 通过 AlbumManager.getExcludedAlbumIds() 获取被屏蔽的图集列表
 */
class RecommendationEngine(
    private val context: Context
) {
    private val preferences = AppPreferences(context)
    private val hashCache = ImageHashCache.getInstance(context)
    private val similarGroupDetector = SimilarGroupDetector(context, hashCache)
    private val albumManager = AlbumManager.getInstance(context)
    
    // 缓存检测到的相似组（避免重复检测）；TC58：同 ID 改内容或成员增删随指纹失效。
    private val similarGroupsCache = SimilarGroupsCache()
    
    /**
     * 根据当前推荐模式获取一批照片
     * 
     * @param allImages 本批可抽样的照片
     * @param batchSize 批次大小（仅随机模式使用，相似模式返回动态大小）
     * @param anchorImage 锚点照片（已废弃，保留参数兼容性）
     * @param identityCandidates 旧冷却身份解析集合，包含已归类及可恢复的回收站照片；不参与抽样
     * @return 推荐的照片列表
     */
    suspend fun getRecommendedBatch(
        allImages: List<ImageFile>,
        batchSize: Int,
        anchorImage: ImageFile? = null,
        identityCandidates: List<ImageFile> = allImages
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        if (allImages.isEmpty()) return@withContext emptyList()
        
        // 过滤掉被屏蔽图集的照片
        val filteredImages = filterExcludedAlbums(allImages)
        if (filteredImages.isEmpty()) {
            android.util.Log.d(TAG, "No images available after filtering excluded albums")
            return@withContext emptyList()
        }

        when (preferences.recommendMode) {
            RecommendMode.RANDOM_WALK -> {
                // 清理过期的抽取记录
                preferences.cleanupExpiredPickRecords()
                // 身份解析不能只看 deck：屏蔽、归类和软删除都可能使同 ID
                // 的他卷照片暂时离池。合并本批输入，避免解析集合遗漏 deck 行；
                // 迁移之外仍只按 filteredImages 抽样。
                migrateLegacyPickRecords(identityCandidates + allImages)
                getRandomWalkBatch(filteredImages, batchSize)
            }
            RecommendMode.SIMILAR -> {
                // 清理过期的相似组记录
                preferences.cleanupExpiredSimilarGroupRecords()
                getSimilarBatch(filteredImages)
            }
        }
    }
    
    /**
     * 过滤掉被屏蔽图集的照片
     * 
     * @param allImages 所有照片
     * @return 过滤后的照片列表（不包含被屏蔽图集中的照片）
     */
    private fun filterExcludedAlbums(allImages: List<ImageFile>): List<ImageFile> {
        val excludedAlbumIds = albumManager.getExcludedAlbumIds()
        if (excludedAlbumIds.isEmpty()) {
            return allImages
        }
        
        val filtered = allImages.filter { image ->
            image.bucketDisplayName !in excludedAlbumIds
        }
        
        android.util.Log.d(TAG, "Filtered ${allImages.size - filtered.size} images from ${excludedAlbumIds.size} excluded albums")
        return filtered
    }
    
    /**
     * 获取下一个相似组
     * 
     * 相似推荐模式专用方法，返回完整的相似组对象。
     * 
     * @param allImages 所有可用照片
     * @return 下一个待处理的相似组，如果没有则返回 null
     */
    suspend fun getNextSimilarGroup(allImages: List<ImageFile>): SimilarGroup? = 
        withContext(Dispatchers.IO) {
            if (allImages.isEmpty()) return@withContext null
            
            // 过滤掉被屏蔽图集的照片
            val filteredImages = filterExcludedAlbums(allImages)
            if (filteredImages.isEmpty()) return@withContext null
            
            // 清理过期记录
            preferences.cleanupExpiredSimilarGroupRecords()
            
            // 获取相似组（使用缓存）
            val groups = getOrDetectSimilarGroups(filteredImages)
            
            // 获取冷却中的组ID
            val cooldownGroupIds = preferences.getSimilarGroupCooldownIds()
            
            // 过滤掉冷却中的组
            val availableGroups = groups.filter { it.id !in cooldownGroupIds }
            
            // 返回优先级最高的组（按大小降序已排好）
            availableGroups.firstOrNull()
        }
    
    /**
     * 标记相似组已处理完成
     * 
     * 处理完一个相似组后调用此方法，使该组进入冷却期。
     * 
     * @param group 已处理的相似组
     */
    fun markSimilarGroupProcessed(group: SimilarGroup) {
        preferences.recordSimilarGroupProcessed(group.id)
    }
    
    /**
     * 清除相似组缓存
     * 
     * 当图片列表发生变化时调用（如删除图片后）
     */
    fun invalidateSimilarGroupCache() {
        similarGroupsCache.invalidate()
    }
    
    /**
     * 从冷却池中移除指定的照片
     * 
     * 用于切换推荐算法时，将当前批次中未浏览的照片从冷却池中移除，
     * 确保这些照片不会因为算法切换而被"浪费"进入冷却期。
     * 
     * 注意：此方法只影响随机漫步模式的单张照片冷却池。
     * 相似推荐模式使用的是组冷却池（以组ID为单位），不受此方法影响。
     * 这是预期的行为：
     * - 从随机漫步切换走时：未浏览的单张照片被移除冷却，可被新算法重新推荐
     * - 从相似推荐切换走时：当前组已部分浏览，保留在组冷却池中是合理的
     * 
     * @param images 要移除冷却的照片行（C1/T03-DECKCOOLDOWN：按完整媒体
     * 身份键 (卷|ID) 移除——跨卷同数值 ID 的他卷已看记录不受影响）
     */
    fun removeFromCooldown(images: List<ImageFile>) {
        preferences.removeImagesFromCooldownByIdentity(images.map { it.mediaIdentityKey() })
    }
    
    /**
     * 随机漫步模式
     * 
     * 真正随机抽取照片，但排除在冷却期内的照片。
     * 如果可用照片不足，则从冷却期内的照片中补充（按冷却期剩余时间排序）。
     */
    /**
     * C1/T03-DECKCOOLDOWN 旧数据迁移：裸数值 ID 冷却记录在完整候选集内
     * 唯一可解析时改写为完整媒体身份键（幂等，迁移后裸键消失）。歧义
     * （跨卷同 ID 多行）或缺失（行已不在）保留旧键安全降级——裸键对该 ID
     * 各卷行均抑制，方向安全，过期后由 cleanupExpiredPickRecords 清除。
     * 随机模式批前调用，身份解析不受 deck 或屏蔽过滤影响；唯一可解析项
     * 首轮迁完，之后零写入。
     * SIMILAR 模式回退随机路径不经迁移——裸键兜底抑制方向不变，下次
     * 随机批即迁移。屏蔽全池（过滤后为空）时不产出批次、迁移跳过，
     * 裸键兜底保留，恢复可见后恢复迁移。
     *
     * MainActivity 提供完整图库和回收站身份；同一身份可能在多个快照中
     * 重复出现，先按完整身份去重，再判断裸 ID 是否唯一。
     */
    private fun migrateLegacyPickRecords(identityCandidates: List<ImageFile>) {
        val legacy = preferences.getPickCooldownIndex().byLegacyBareId
        if (legacy.isEmpty()) return
        val candidates = identityCandidates.distinctBy { it.mediaIdentityKey() }
        legacy.forEach { (bareId, _) ->
            val rows = candidates.filter { it.id == bareId }
            if (rows.size == 1) {
                preferences.migratePickRecordToIdentity(bareId, rows.single().mediaIdentityKey())
            }
        }
    }

    private fun getRandomWalkBatch(
        allImages: List<ImageFile>,
        batchSize: Int
    ): List<ImageFile> {
        // 旧裸 ID 记录已在 getRecommendedBatch 用过滤前完整候选集迁移完毕
        val cooldown = preferences.getPickCooldownIndex()

        // 冷却判定按完整媒体身份（卷|ID）；旧裸 ID 键仅对未迁移记录兜底
        fun inCooldown(image: ImageFile): Boolean =
            cooldown.byIdentityKey.containsKey(image.mediaIdentityKey()) ||
                cooldown.byLegacyBareId.containsKey(image.id)

        fun pickedAt(image: ImageFile): Long =
            cooldown.byIdentityKey[image.mediaIdentityKey()]?.pickedAt
                ?: cooldown.byLegacyBareId[image.id]?.pickedAt
                ?: 0L

        // 分离可用照片和冷却中的照片
        val (cooldownImages, availableImages) = allImages.partition(::inCooldown)

        val result = mutableListOf<ImageFile>()
        val usedKeys = mutableSetOf<String>()  // 防止批次内重复（身份键：跨卷同 ID 不互斥）

        // 优先从可用照片中随机抽取
        val shuffledAvailable = availableImages.shuffled()
        val newlyPicked = shuffledAvailable.take(batchSize)
        for (img in newlyPicked) {
            val key = img.mediaIdentityKey()
            if (key !in usedKeys) {
                result.add(img)
                usedKeys.add(key)
            }
        }

        // 如果可用照片不足，从冷却中的照片补充（优先选择冷却期即将结束的）
        if (result.size < batchSize && cooldownImages.isNotEmpty()) {
            val sortedCooldown = cooldownImages.sortedBy { img -> pickedAt(img) }
            for (img in sortedCooldown) {
                if (result.size >= batchSize) break
                val key = img.mediaIdentityKey()
                if (key !in usedKeys) {
                    result.add(img)
                    usedKeys.add(key)
                }
            }
        }

        // 只记录新抽取的照片（不在冷却中的），避免重置从冷却中补充的照片的冷却期
        preferences.recordImagesPickedByIdentity(newlyPicked.map { it.mediaIdentityKey() })
        
        // 如果总照片数 <= batchSize，清理冷却池避免无限循环
        if (allImages.size <= batchSize) {
            android.util.Log.d(TAG, "Total images (${allImages.size}) <= batchSize ($batchSize), clearing cooldown to prevent loop")
            preferences.clearAllPickRecords()
        }
        
        return result
    }
    
    /**
     * 相似推荐模式（新算法）
     * 
     * 使用多维度聚类算法检测相似照片组：
     * 1. dHash 感知哈希 - 内容相似性
     * 2. 时间相近 - 连拍/多角度拍摄
     * 3. 尺寸/文件大小 - 同设备/同模式
     * 4. 同一相册
     * 
     * 返回完整的一组相似照片（动态大小），按时间顺序排列。
     */
    private suspend fun getSimilarBatch(allImages: List<ImageFile>): List<ImageFile> {
        return try {
            // 获取下一个相似组
            val group = getNextSimilarGroup(allImages)
            
            if (group != null) {
                // 标记该组已处理（进入冷却）
                markSimilarGroupProcessed(group)
                android.util.Log.d(TAG, "Returning similar group: ${group.size} images, id=${group.id}")
                group.images
            } else {
                // 如果没有可用的相似组，回退到随机模式
                android.util.Log.d(TAG, "No similar groups available, falling back to random")
                getRandomWalkBatch(allImages, preferences.batchSize)
            }
        } catch (e: CancellationException) {
            // 协程取消异常必须重新抛出
            throw e
        } catch (e: Exception) {
            // 相似推荐出错时回退到随机模式，保证用户体验
            android.util.Log.e(TAG, "Similar batch error, falling back to random: ${e.message}", e)
            getRandomWalkBatch(allImages, preferences.batchSize)
        }
    }
    
    /**
     * 获取或检测相似组（带缓存）
     */
    private suspend fun getOrDetectSimilarGroups(allImages: List<ImageFile>): List<SimilarGroup> {
        val now = System.currentTimeMillis()

        // TTL 内且输入身份+内容指纹未变才复用；同 ID 改内容/成员增删触发重算。
        similarGroupsCache.lookup(allImages, now)?.let { return it }

        // 重新检测
        android.util.Log.d(TAG, "Detecting similar groups...")
        val groups = similarGroupDetector.detectSimilarGroups(allImages)
        similarGroupsCache.store(groups, allImages, now)

        android.util.Log.d(TAG, "Detected ${groups.size} similar groups")
        return groups
    }
    
    companion object {
        private const val TAG = "RecommendationEngine"
        
        @Volatile
        private var instance: RecommendationEngine? = null
        
        fun getInstance(context: Context): RecommendationEngine {
            return instance ?: synchronized(this) {
                instance ?: RecommendationEngine(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
}
