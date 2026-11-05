package com.tabula.v3.ui.util

import android.graphics.Bitmap

/**
 * T06a：从 Coil 内存缓存借用的 Bitmap 生成"本方所有"的缩放副本。
 *
 * Bitmap.createScaledBitmap 在目标尺寸与源一致时返回源 Bitmap 本身（F15），
 * 直接持有并在稍后 recycle() 会回收 Coil 缓存与正在显示的共享位图。
 * 这里在别名发生时强制做一次像素级拷贝，保证调用方独占返回值；
 * 拷贝失败返回 null，调用方应回退到全新解码（如 createGenieBitmapFast）。
 */
internal fun ownedScaledCopyOrNull(source: Bitmap, maxSize: Int): Bitmap? {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return null

    val scale = minOf(
        maxSize.toFloat() / source.width,
        maxSize.toFloat() / source.height,
        1f
    )
    val targetWidth = (source.width * scale).toInt().coerceAtLeast(50)
    val targetHeight = (source.height * scale).toInt().coerceAtLeast(50)

    val scaled = try {
        Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false)
    } catch (e: Exception) {
        null
    } ?: return null

    if (scaled !== source) return scaled

    // F15 路径：同尺寸缩放返回了源本身——复制独立像素，调用方才能安全 recycle。
    return try {
        source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
    } catch (e: Exception) {
        null
    }
}
