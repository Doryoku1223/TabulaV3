package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import coil.request.ImageRequest
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.CoilSetup

/**
 * 图片卡片组件 - Crop 填充模式
 *
 * 图片完全填充卡片，不留黑边
 *
 * @param imageFile 要显示的图片
 * @param modifier 外部修饰符
 * @param cornerRadius 圆角半径
 * @param elevation 阴影高度
 */
@Composable
fun ImageCard(
    imageFile: ImageFile,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 8.dp
) {
    val context = LocalContext.current
    val imageLoader = CoilSetup.getImageLoader(context)
    val shape = RoundedCornerShape(cornerRadius)

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
        // 单层图片 - Crop 填充，无黑边
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageFile.uri)
                .crossfade(150)
                .build(),
            contentDescription = imageFile.displayName,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,  // 裁剪填充
            modifier = Modifier.fillMaxSize()
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
