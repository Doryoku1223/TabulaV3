package com.tabula.v3.data.repository

import com.tabula.v3.data.model.ImageFile

/**
 * RecommendationEngine 相似组内存缓存的版本化持有者。
 *
 * TC58：缓存命中除 TTL 外还要求输入列表的身份+内容指纹不变——同媒体 ID
 * 改内容（DATE_MODIFIED/size 变化）或成员增删都必须失效旧组，避免编辑后
 * 在 TTL 窗口内继续推荐按旧内容分出的相似组。指纹对输入顺序不敏感，
 * 过滤/排序差异不产生伪失效；失效不影响无关的组冷却与专清进度。
 */
internal class SimilarGroupsCache(private val validityMs: Long = DEFAULT_VALIDITY_MS) {

    private var cached: List<SimilarGroup>? = null
    private var cacheTimestamp: Long = 0L
    private var cacheFingerprint: List<MemberFingerprint>? = null

    /**
     * 成员指纹条目 = (卷, 数值 ID, 内容版本, 大小)。
     * C1/T03-GROUPCACHE-VOLUME：卷参与相等性——A 卷照片被 B 卷同
     * (id, dateModified, size) 照片替换时指纹必须变化，缓存组不得
     * 继续携带 A 卷旧 URI；null 卷（T03 前旧数据路径）与具名卷不等。
     */
    internal data class MemberFingerprint(
        val volumeName: String?,
        val id: Long,
        val dateModified: Long,
        val size: Long
    )

    /**
     * 输入列表的身份+内容版本：按所有字段排序后精确比较。
     * 顺序不敏感，避免数值折叠导致不同版本碰撞；重复成员保留
     * （两卷同数值 ID 行是指纹中的两条不同条目）。
     */
    internal fun fingerprintOf(images: List<ImageFile>): List<MemberFingerprint> =
        images.map { MemberFingerprint(it.volumeName, it.id, it.dateModified, it.size) }
            .sortedWith(
                compareBy({ it.volumeName ?: "" }, { it.id }, { it.dateModified }, { it.size })
            )

    /** TTL 内且指纹匹配才返回缓存组；内容变更、成员变化或过期返回 null。 */
    @Synchronized
    internal fun lookup(images: List<ImageFile>, now: Long): List<SimilarGroup>? {
        val cached = this.cached ?: return null
        if (now - cacheTimestamp >= validityMs) return null
        if (cacheFingerprint != fingerprintOf(images)) return null
        return cached
    }

    @Synchronized
    internal fun store(groups: List<SimilarGroup>, images: List<ImageFile>, now: Long) {
        cached = groups
        cacheTimestamp = now
        cacheFingerprint = fingerprintOf(images)
    }

    /** 调用方显式失效（移除/恢复/刷新钩子沿用）；之后 lookup 必然重算。 */
    @Synchronized
    internal fun invalidate() {
        cached = null
        cacheTimestamp = 0L
        cacheFingerprint = null
    }

    companion object {
        /** 缓存有效期 5 分钟（沿用原 RecommendationEngine.CACHE_VALIDITY_MS）。 */
        internal const val DEFAULT_VALIDITY_MS = 5 * 60 * 1000L
    }
}
