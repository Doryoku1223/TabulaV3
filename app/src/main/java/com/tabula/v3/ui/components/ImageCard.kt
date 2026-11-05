package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.di.cardMemoryCacheKey

/**
 * 图片卡片组件
 *
 * 支持两种显示模式：
 * - Crop 模式：图片完全填充卡片，不留黑边（固定样式）
 * - Fit 模式：完整显示图片内容，可能有黑边（自适应样式）
 * 
 * 性能优化：
 * - 使用较小的目标尺寸 (1080px)，加快解码速度
 * - 禁用硬件位图避免 HDR/大图兼容性问题
 * - 使用稳定的缓存键，防止重复加载
 *
 * @param imageFile 要显示的图片
 * @param modifier 外部修饰符
 * @param cornerRadius 圆角半径
 * @param elevation 阴影高度
 * @param contentScaleMode 内容缩放模式（Crop 或 Fit）
 */
@Composable
fun ImageCard(
    imageFile: ImageFile,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 8.dp,
    badges: List<String> = emptyList(),
    contentScaleMode: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val imageLoader = CoilSetup.getImageLoader(context)
    val shape = RoundedCornerShape(cornerRadius)
    
    // 性能优化：使用 720px 尺寸以加快解码速度
    // 卡片预览不需要高分辨率，720px 在手机屏幕上已经足够清晰
    // 从 1080px 降到 720px 可以：
    // 1. 减少约 55% 的内存占用
    // 2. 加快解码速度
    // 3. 减少磁盘 I/O
    val targetSize = remember(imageFile.id, imageFile.aspectRatio, imageFile.hasDimensionInfo) {
        if (!imageFile.hasDimensionInfo) {
            // 无有效尺寸信息，使用默认的 3:4 尺寸
            Size(540, 720)  // 从 810x1080 降到 540x720
        } else {
            val maxDimension = 720  // 从 1080 降到 720
            if (imageFile.aspectRatio < 1f) {
                // 竖图：高度为主
                val height = maxDimension
                val width = (height * imageFile.aspectRatio).toInt().coerceAtLeast(270)
                Size(width, height)
            } else {
                // 横图：宽度为主
                val width = maxDimension
                val height = (width / imageFile.aspectRatio).toInt().coerceAtLeast(270)
                Size(width, height)
            }
        }
    }
    
    // 使用稳定的缓存键，基于图片 ID
    // C4/T07 任务 C：remember 键必须含 uri——cardMemoryCacheKey 的键格式含
    // 卷限定 URI，同 ID 同 DATE_MODIFIED 但卷/URI 不同的行（跨卷同数值 ID）
    // 若只按 (id, dateModified) 记忆，请求会用新 URI 取数据却挂在旧卷的缓存
    // 键下（串图/缓存污染）。uri + dateModified 双键：内容版本或行 URI 变化
    // 都重算；普通重组两键不变，无多余请求变化
    val cacheKey = remember(imageFile.uri, imageFile.dateModified) { cardMemoryCacheKey(imageFile) }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        // 图片显示
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageFile.uri)
                .size(targetSize)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                // 性能优化：启用硬件位图加速解码和渲染
                // Genie 动画需要的 Bitmap 会单独加载（allowHardware=false）
                .allowHardware(true)
                .crossfade(100)
                .build(),
            contentDescription = imageFile.displayName,
            imageLoader = imageLoader,
            contentScale = contentScaleMode,
            modifier = Modifier.fillMaxSize()
        )

        MediaBadgeRow(
            badges = badges,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )
    }
}

/**
 * 带占位符的图片卡片组件
 *
 * 当没有图片时显示占位符
 *
 * @param modifier 外部修饰符
 * @param cornerRadius 圆角半径
 * @param elevation 阴影高度
 */
@Composable
fun ImageCardPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 4.dp
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(shape)
            .background(Color(0xFFE8E8E8)),
        contentAlignment = Alignment.Center
    ) {
        // 空白占位符
    }
}
