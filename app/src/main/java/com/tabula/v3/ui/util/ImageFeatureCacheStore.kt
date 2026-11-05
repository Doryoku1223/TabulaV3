package com.tabula.v3.ui.util

import com.tabula.v3.data.model.ImageFeatures
import java.util.concurrent.ConcurrentHashMap

/**
 * T11a 图片特征缓存：键 = (imageId, 内容版本毫秒, 开关组合)。
 * 内容版本按 T03 契约取 MediaStore DATE_MODIFIED（毫秒）。
 *
 * putIfReliable：阳性（HDR/动态照片）与可信阴性（definitive=true）才入缓存；
 * 忙碌/快速模式/失败产生的不可信阴性（definitive=false 且阴性）一律拒绝，
 * 避免把瞬态状态永久缓存为"非 HDR/非动态"。
 */
internal data class ImageFeatureCacheKey(
    val imageId: Long,
    val contentVersion: Long,
    val enableHdr: Boolean,
    val enableMotion: Boolean
)

internal object ImageFeatureCacheStore {
    private val cache = ConcurrentHashMap<ImageFeatureCacheKey, ImageFeatures>()

    fun get(key: ImageFeatureCacheKey): ImageFeatures? = cache[key]

    fun putIfReliable(key: ImageFeatureCacheKey, features: ImageFeatures) {
        val reliable = features.definitive || features.isHdr || features.isMotionPhoto
        if (reliable) cache[key] = features
    }

    fun clear() {
        cache.clear()
    }
}
