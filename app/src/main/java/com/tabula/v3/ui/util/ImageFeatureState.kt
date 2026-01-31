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
import java.util.concurrent.ConcurrentHashMap

/**
 * 图片特征缓存 - 避免重复检测导致闪烁
 */
private object ImageFeatureCache {
    // 缓存：imageId -> (enableHdr, enableMotion) -> features
    private val cache = ConcurrentHashMap<Long, ConcurrentHashMap<Pair<Boolean, Boolean>, ImageFeatures>>()
    
    fun get(imageId: Long, enableHdr: Boolean, enableMotion: Boolean): ImageFeatures? {
        return cache[imageId]?.get(Pair(enableHdr, enableMotion))
    }
    
    fun put(imageId: Long, enableHdr: Boolean, enableMotion: Boolean, features: ImageFeatures) {
        cache.getOrPut(imageId) { ConcurrentHashMap() }[Pair(enableHdr, enableMotion)] = features
    }
    
    fun clear() {
        cache.clear()
    }
}

@Composable
fun rememberImageFeatures(
    image: ImageFile,
    enableHdr: Boolean,
    enableMotion: Boolean
): ImageFeatures? {
    val context = LocalContext.current
    
    // 先从缓存获取，避免初始值为 null 导致闪烁
    val cachedFeatures = remember(image.id, enableHdr, enableMotion) {
        ImageFeatureCache.get(image.id, enableHdr, enableMotion)
    }
    
    var features by remember(image.id, enableHdr, enableMotion) {
        mutableStateOf(cachedFeatures)
    }

    LaunchedEffect(image.id, enableHdr, enableMotion) {
        if (!enableHdr && !enableMotion) {
            features = null
            return@LaunchedEffect
        }
        
        // 如果已有缓存，直接使用
        val cached = ImageFeatureCache.get(image.id, enableHdr, enableMotion)
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
        
        ImageFeatureCache.put(image.id, enableHdr, enableMotion, detected)
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
    if (ImageFeatureCache.get(image.id, enableHdr, enableMotion) != null) return
    
    val detected = ImageFeatureDetector.detect(
        context = context,
        uri = image.uri,
        checkHdr = enableHdr,
        checkMotion = enableMotion
    )
    
    ImageFeatureCache.put(image.id, enableHdr, enableMotion, detected)
}
