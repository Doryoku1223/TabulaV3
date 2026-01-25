package com.tabula.v3.di

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import coil.size.Size
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 图片预加载管理器
 *
 * 针对卡片堆叠滑动场景优化：
 * - 预加载当前索引的前后各 N 张图片
 * - 使用精确的卡片尺寸避免加载原图
 * - 后台协程执行，不阻塞 UI
 *
 * @param context 应用上下文
 * @param preloadRange 预加载范围（前后各多少张），默认 2
 */
class PreloadingManager(
    private val context: Context,
    private val preloadRange: Int = 2
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageLoader = CoilSetup.getImageLoader(context)

    // 记录已预加载的索引，避免重复加载
    private val preloadedIndices = mutableSetOf<Int>()

    // 上次预加载的中心索引
    private var lastPreloadCenter: Int = -1

    /**
     * 根据当前索引预加载相邻图片
     *
     * @param images 完整图片列表
     * @param currentIndex 当前显示的索引
     * @param cardSize 卡片尺寸（宽 x 高），用于精确加载
     */
    fun preloadAround(
        images: List<ImageFile>,
        currentIndex: Int,
        cardSize: IntSize
    ) {
        // 如果中心索引没变，跳过
        if (currentIndex == lastPreloadCenter) return
        lastPreloadCenter = currentIndex

        scope.launch {
            // 计算需要预加载的索引范围
            val startIndex = (currentIndex - preloadRange).coerceAtLeast(0)
            val endIndex = (currentIndex + preloadRange).coerceAtMost(images.lastIndex)

            for (index in startIndex..endIndex) {
                // 跳过已预加载的
                if (index in preloadedIndices) continue

                val image = images.getOrNull(index) ?: continue
                preloadImage(image, cardSize)
                preloadedIndices.add(index)
            }

            // 清理过期的预加载记录（保留最近的范围）
            val keepRange = (currentIndex - preloadRange * 2)..(currentIndex + preloadRange * 2)
            preloadedIndices.removeAll { it !in keepRange }
        }
    }

    /**
     * 预加载单张图片
     *
     * @param image 要预加载的图片
     * @param targetSize 目标尺寸
     */
    private suspend fun preloadImage(image: ImageFile, targetSize: IntSize) {
        try {
            val request = ImageRequest.Builder(context)
                .data(image.uri)
                // 关键：使用精确的卡片尺寸，严禁加载原图
                .size(Size(targetSize.width, targetSize.height))
                // 精确尺寸裁剪
                .precision(coil.size.Precision.EXACT)
                // 预加载到内存缓存
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()

            imageLoader.enqueue(request)

            Log.d(TAG, "预加载图片: ${image.displayName}, 尺寸: ${targetSize.width}x${targetSize.height}")
        } catch (e: Exception) {
            Log.w(TAG, "预加载失败: ${image.displayName}", e)
        }
    }

    /**
     * 立即预加载指定索引的图片（优先级更高）
     *
     * @param images 图片列表
     * @param indices 要预加载的索引列表
     * @param cardSize 卡片尺寸
     */
    fun preloadImmediate(
        images: List<ImageFile>,
        indices: List<Int>,
        cardSize: IntSize
    ) {
        scope.launch {
            indices.forEach { index ->
                val image = images.getOrNull(index) ?: return@forEach
                preloadImage(image, cardSize)
                preloadedIndices.add(index)
            }
        }
    }

    /**
     * 重置预加载状态（例如切换相册时）
     */
    fun reset() {
        preloadedIndices.clear()
        lastPreloadCenter = -1
    }

    companion object {
        private const val TAG = "PreloadingManager"
    }
}
