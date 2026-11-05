package com.tabula.v3.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tabula.v3.data.model.ImageFeatures
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.ImageFeatureDetector

@Composable
fun rememberImageFeatures(
    image: ImageFile,
    enableHdr: Boolean,
    enableMotion: Boolean
): ImageFeatures? {
    val context = LocalContext.current
    
    // T11a：缓存键含内容版本（DATE_MODIFIED 毫秒）与开关组合；不可信阴性不入缓存。
    val cacheKey = remember(image.id, image.dateModified, enableHdr, enableMotion) {
        ImageFeatureCacheKey(image.id, image.dateModified, enableHdr, enableMotion)
    }

    // 先从缓存获取，避免初始值为 null 导致闪烁
    val cachedFeatures = remember(cacheKey) {
        ImageFeatureCacheStore.get(cacheKey)
    }
    
    var features by remember(cacheKey) {
        mutableStateOf(cachedFeatures)
    }

    LaunchedEffect(cacheKey) {
        if (!enableHdr && !enableMotion) {
            features = null
            return@LaunchedEffect
        }
        
        // 如果已有缓存，直接使用
        val cached = ImageFeatureCacheStore.get(cacheKey)
        if (cached != null) {
            features = cached
            return@LaunchedEffect
        }

        val detected = ImageFeatureDetector.detect(
            context = context,
            uri = image.uri,
            checkHdr = enableHdr,
            checkMotion = enableMotion
        )
        
        // 不可信阴性（快速模式/信号量繁忙/失败）只用于本次显示，留待重试。
        ImageFeatureCacheStore.putIfReliable(cacheKey, detected)
        features = detected
    }

    return features
}

/**
 * 预加载图片特征（用于提前检测即将显示的图片）
 */
suspend fun preloadImageFeatures(
    context: android.content.Context,
    image: ImageFile,
    enableHdr: Boolean,
    enableMotion: Boolean
) {
    if (!enableHdr && !enableMotion) return
    
    // 已缓存则跳过
    val cacheKey = ImageFeatureCacheKey(image.id, image.dateModified, enableHdr, enableMotion)
    if (ImageFeatureCacheStore.get(cacheKey) != null) return
    
    val detected = ImageFeatureDetector.detect(
        context = context,
        uri = image.uri,
        checkHdr = enableHdr,
        checkMotion = enableMotion
    )
    
    ImageFeatureCacheStore.putIfReliable(cacheKey, detected)
}
