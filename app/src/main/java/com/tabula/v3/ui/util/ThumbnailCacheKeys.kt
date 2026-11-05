package com.tabula.v3.ui.util

import android.content.Context
import android.graphics.Bitmap
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.di.cardMemoryCacheKey

/**
 * 图集/系统图集网格缩略图的 Coil memory/disk 缓存键。
 *
 * C1/T03-THUMBKEY：键必须携带完整媒体身份（卷|ID）与内容版本
 * （DATE_MODIFIED 毫秒）——跨卷同数值 ID 行不得共享缩略图条目（串图），
 * 同一照片编辑后（内容版本变化）必须重新加载。prefix 区分使用面
 * （album_grid_ / sys_grid_），避免不同网格互相挤占缓存。
 */
internal fun gridThumbnailCacheKey(prefix: String, image: ImageFile): String =
    // C1/T03-THUMBKEY：完整媒体身份键（卷|ID，跨卷同数值 ID 行不串图）+
    // 内容版本（DATE_MODIFIED 毫秒，编辑后重新加载；与 ImageHashCache
    // ver_ 的内容版本币制一致）。
    "$prefix${image.mediaIdentityKey()}_${image.dateModified}"

/**
 * C1/T03-COVERKEY：图集封面请求构造器（各封面展示面的唯一生产入口）。
 * 缓存键 = [gridThumbnailCacheKey]（完整媒体身份 + 内容版本）——同一照片
 * 编辑内容（dateModified 变化）后请求与缓存身份随之变化，跨卷同数值 ID
 * 行彼此隔离。视觉规格（size/crossfade/allowHardware/rgb565/inexact）由
 * 调用面按既有展示决定，默认保持各站点原值。
 */
fun coverImageRequest(
    context: Context,
    coverImage: ImageFile,
    prefix: String,
    size: Size? = null,
    crossfade: Boolean = false,
    allowHardware: Boolean = true,
    rgb565: Boolean = false,
    inexact: Boolean = false
): ImageRequest {
    // C1/T03-COVERKEY：显式缓存键 = 完整媒体身份 + 内容版本——同一照片
    // 编辑内容（dateModified 变化）后请求与缓存身份随之变化；跨卷同数值
    // ID 行彼此隔离。
    val key = gridThumbnailCacheKey(prefix, coverImage)
    val builder = ImageRequest.Builder(context)
        .data(coverImage.uri)
    val sized = if (size != null) builder.size(size) else builder
    val precise = if (inexact) sized.precision(Precision.INEXACT) else sized
    val configed = if (rgb565) precise.bitmapConfig(Bitmap.Config.RGB_565) else precise
    return configed
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .allowHardware(allowHardware)
        .crossfade(crossfade)
        .build()
}

/**
 * deck 查看器（ViewerOverlay）底层缩略图请求构造——240×240 INEXACT
 * RGB_565、非硬件位图，作为查看器打开瞬间的即时占位层（上层高清请求
 * 加载完成后淡入覆盖）。
 *
 * C3/T06-VIEWERKEY：deck 两种卡片模式的卡片位图由 cardMemoryCacheKey
 * （卷限定 URI + DATE_MODIFIED 内容版本）生产/消费（ImageCard 显示请求与
 * PreloadingManager 预载）。本请求复用同一键——键含卷（跨卷同数值 ID 行
 * 不串图）与内容版本（同 ID 编辑后自然失效），且从卡片点开查看器时占位
 * 层温命中卡片已解码位图（修复原裸 ID album_grid_ 键的错配：无生产者、
 * 自写自读跨卷串图、内容更新陈旧）。
 *
 * 内存策略 READ_ONLY：查看器 240px 解码规格与卡片 720px 不同，命中即借用
 * 展示、未命中解码后不回写，避免不同规格位图互相挤占同一键的条目；磁盘
 * 缓存按键存原始来源字节（两请求均无变换），与卡片共享同一磁盘条目无别名。
 * 解码规格（尺寸/RGB_565/非硬件/无渐变）为占位层既有视觉行为，保持不变。
 */
internal fun deckViewerThumbnailRequest(
    context: Context,
    image: ImageFile
): ImageRequest = ImageRequest.Builder(context)
    .data(image.uri)
    .size(coil.size.Size(240, 240))
    .precision(Precision.INEXACT)
    .bitmapConfig(Bitmap.Config.RGB_565)
    .memoryCacheKey(cardMemoryCacheKey(image))
    .diskCacheKey(cardMemoryCacheKey(image))
    .memoryCachePolicy(CachePolicy.READ_ONLY)
    .diskCachePolicy(CachePolicy.ENABLED)
    .crossfade(false)
    .allowHardware(false)
    .build()

/**
 * deck 查看器高清层请求构造（C4/T07 任务 D）——上层全质量图（ARGB_8888、
 * 动态目标尺寸、INEXACT），加载完成后淡入覆盖底层缩略图占位。
 *
 * 原实现无显式缓存键：Coil 默认内存键落到 URI 字符串，不含内容版本——同
 * URI 原地编辑（DATE_MODIFIED 更新）后旧高清位图被持续命中（新图片被旧
 * 高清缓存覆盖）。显式键沿用既有约定：gridThumbnailCacheKey（完整媒体身份
 * 卷|ID + DATE_MODIFIED 内容版本，viewer_hd_ 前缀独立命名空间）——编辑后
 * 键变化自然失效；跨卷同数值 ID 行不串图；未更新图片键不变照常复用。
 * 尺寸维度沿用默认行为（显式键下由 Coil isSizeValid 按请求尺寸校验命中，
 * 与原默认键的尺寸语义一致）；解码规格（ARGB_8888/INEXACT/渐变 200）不变。
 */
internal fun viewerHdRequest(
    context: Context,
    image: ImageFile,
    targetSize: coil.size.Size
): coil.request.ImageRequest {
    val key = gridThumbnailCacheKey("viewer_hd_", image)
    return ImageRequest.Builder(context)
        .data(image.uri)
        .size(targetSize)
        .precision(Precision.INEXACT)
        .crossfade(200)
        .bitmapConfig(Bitmap.Config.ARGB_8888)
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .build()
}
