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

/**
 * 图片卡片组件
 *
 * 支持两种显示模式：
 * - Crop 模式：图片完全填充卡片，不留黑边（固定样式）
 * - Fit 模式：完整显示图片内容，可能有黑边（自适应样式）
 * 
 * 性能优化：
 * - 使用固定的目标尺寸 (1080px)，避免加载原始 4K/HDR 图片
 * - 使用稳定的缓存键，防止重复加载
 * - 禁用不必要的重组触发
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
    
    // 根据图片比例计算合适的加载尺寸
    // 保持图片原始比例，避免 Coil 加载时预先裁剪
    // 当图片没有有效尺寸信息时，使用默认的 3:4 加载尺寸
    val targetSize = remember(imageFile.id, imageFile.aspectRatio, imageFile.hasDimensionInfo) {
        if (!imageFile.hasDimensionInfo) {
            // 无有效尺寸信息，使用默认的 3:4 尺寸
            Size(1080, 1440)
        } else {
            val maxDimension = 1440
            if (imageFile.aspectRatio < 1f) {
                // 竖图：高度为主
                val height = maxDimension
                val width = (height * imageFile.aspectRatio).toInt().coerceAtLeast(540)
                Size(width, height)
            } else {
                // 横图：宽度为主
                val width = maxDimension
                val height = (width / imageFile.aspectRatio).toInt().coerceAtLeast(540)
                Size(width, height)
            }
        }
    }
    
    // 使用稳定的缓存键，基于图片 ID
    val cacheKey = remember(imageFile.id) { "card_${imageFile.id}" }

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
                .size(targetSize)  // 根据模式计算的尺寸
                .memoryCacheKey(cacheKey)  // 稳定的缓存键
                .diskCacheKey(cacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(150)
                .build(),
            contentDescription = imageFile.displayName,
            imageLoader = imageLoader,
            contentScale = contentScaleMode,  // 根据模式选择缩放方式
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
