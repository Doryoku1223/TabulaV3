package com.tabula.v3.di

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import java.io.File

/**
 * Coil 图片加载器极致优化配置
 *
 * 针对 120Hz 高速滑动场景优化：
 * 1. 内存缓存：可用 RAM 的 25%
 * 2. 默认 RGB_565 格式节省 50% 内存
 * 3. 磁盘缓存 150MB
 * 4. 禁用网络（纯本地应用）
 */
object CoilSetup {

    @Volatile
    private var imageLoader: ImageLoader? = null

    /**
     * 获取单例 ImageLoader
     *
     * 双重检查锁定确保线程安全
     */
    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: createImageLoader(context.applicationContext).also {
                imageLoader = it
            }
        }
    }

    /**
     * 创建高性能 ImageLoader
     */
    private fun createImageLoader(context: Context): ImageLoader {
        val availableMemory = getAvailableMemory(context)
        // 使用可用内存的 25% 作为图片缓存
        val memoryCacheSize = (availableMemory * 0.25).toLong().coerceIn(
            minimumValue = 32L * 1024 * 1024,  // 最小 32MB
            maximumValue = 256L * 1024 * 1024  // 最大 256MB
        )

        return ImageLoader.Builder(context)
            // ========== 内存缓存配置 ==========
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(memoryCacheSize.toInt())
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            // ========== 磁盘缓存配置 ==========
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150MB
                    .build()
            }
            // ========== 缓存策略 ==========
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // ========== Bitmap 配置 ==========
            // 使用 ARGB_8888 以确保高质量和正确的透明度支持
            // 虽然耗损更多内存 (32位)，但对于动图透明背景是必须的
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            // 禁用 RGB_565，强制使用 ARGB_8888
            .allowRgb565(false)
            // ========== 性能优化 ==========
            // 允许硬件位图（Android 8.0+）
            .allowHardware(true)
            // 交叉淡入动画保持流畅
            .crossfade(150)
            // 尊重缓存头
            .respectCacheHeaders(false)
            // ========== 禁用网络 + 动画支持 ==========
            .components {
                // 添加动画 GIF/WebP 支持
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    /**
     * 获取设备可用内存（字节）
     */
    private fun getAvailableMemory(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // 使用较保守的估算：取 availMem 和 memoryClass 的较小值
        val memoryClass = activityManager.memoryClass * 1024L * 1024L
        return minOf(memoryInfo.availMem, memoryClass)
    }

    /**
     * 清除所有缓存
     */
    fun clearCache(context: Context) {
        getImageLoader(context).apply {
            memoryCache?.clear()
            diskCache?.clear()
        }
    }

    /**
     * 获取缓存大小信息（用于调试）
     */
    fun getCacheStats(context: Context): CacheStats {
        val loader = getImageLoader(context)
        return CacheStats(
            memoryCacheSize = loader.memoryCache?.size ?: 0,
            memoryCacheMaxSize = loader.memoryCache?.maxSize ?: 0,
            diskCacheSize = loader.diskCache?.size ?: 0,
            diskCacheMaxSize = loader.diskCache?.maxSize ?: 0
        )
    }

    data class CacheStats(
        val memoryCacheSize: Int,
        val memoryCacheMaxSize: Int,
        val diskCacheSize: Long,
        val diskCacheMaxSize: Long
    )
}
