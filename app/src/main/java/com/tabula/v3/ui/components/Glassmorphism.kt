package com.tabula.v3.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 液态玻璃 (Liquid Glass) 组件
 * 
 * 设计理念：一块凝固的液态玻璃，底部边缘如水的表面张力般柔和发光
 * 
 * @param tintGradient 背景渐变（推荐：顶部较浓，底部较淡，让内容透出）
 * @param tint 纯色背景（当 tintGradient 为 null 时使用）
 * @param highlightBrush 可选的高光渐变覆盖层
 * @param meniscusHeight 底部 "meniscus" 高光高度（模拟水边效果），设为 0 禁用
 * @param meniscusAlpha 底部高光的白色透明度
 */
@Composable
fun FrostedGlass(
    modifier: Modifier = Modifier,
    shape: Shape,
    blurRadius: Dp,
    tint: Color = Color.Transparent,
    tintGradient: Brush? = null,
    noiseAlpha: Float = 0.06f,
    borderBrush: Brush? = null,
    borderWidth: Dp = 0.dp,
    // 高光渐变层（向后兼容）
    highlightBrush: Brush? = null,
    // 底部 meniscus（水边）效果参数
    meniscusHeight: Dp = 0.dp,
    meniscusAlpha: Float = 0f,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }
    val meniscusPx = with(density) { meniscusHeight.toPx() }
    val noiseBrush = rememberNoiseBrush()

    Layout(
        content = {
            // Layer 0: 模糊背景层 + 噪点 + meniscus 高光（单一内聚表面）
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .then(
                        if (tintGradient != null) {
                            Modifier.background(tintGradient)
                        } else {
                            Modifier.background(tint)
                        }
                    )
                    .drawWithCache {
                        // 底部 meniscus 高光渐变（水边表面张力效果）
                        val meniscusGradient = if (meniscusAlpha > 0f && meniscusPx > 0f) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = meniscusAlpha)
                                ),
                                startY = size.height - meniscusPx,
                                endY = size.height
                            )
                        } else null
                        
                        onDrawWithContent {
                            drawContent()
                            // 噪点纹理
                            drawRect(noiseBrush, alpha = noiseAlpha)
                            // 底部 meniscus 高光 - 如水的边缘反光
                            meniscusGradient?.let { gradient ->
                                drawRect(
                                    brush = gradient,
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - meniscusPx),
                                    size = androidx.compose.ui.geometry.Size(size.width, meniscusPx)
                                )
                            }
                        }
                    }
            )

            // Layer 1: 高光渐变层（可选，向后兼容）
            if (highlightBrush != null) {
                Box(
                    modifier = Modifier.background(highlightBrush)
                )
            }

            // Layer 2: 内容层
            Box(
                contentAlignment = contentAlignment
            ) {
                content()
            }
        },
        modifier = modifier
            .clip(shape)
            .then(
                if (borderBrush != null && borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderBrush, shape)
                } else {
                    Modifier
                }
            )
    ) { measurables, constraints ->
        val contentPlaceable = measurables.last().measure(constraints)
        val width = contentPlaceable.width
        val height = contentPlaceable.height
        val fixed = Constraints.fixed(width, height)

        val backgroundPlaceable = measurables[0].measure(fixed)
        
        // 高光层（如果存在）
        val highlightPlaceable = if (highlightBrush != null) {
            measurables[1].measure(fixed)
        } else null

        layout(width, height) {
            backgroundPlaceable.place(0, 0)
            highlightPlaceable?.place(0, 0)
            contentPlaceable.place(0, 0)
        }
    }
}

@Composable
private fun rememberNoiseBrush(): Brush {
    val noise = remember { createNoiseBitmap(64) }
    return remember(noise) {
        ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
    }
}

private fun createNoiseBitmap(size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val random = Random(0)

    for (i in pixels.indices) {
        val alpha = random.nextInt(0, 38)
        pixels[i] = AndroidColor.argb(alpha, 255, 255, 255)
    }

    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}

/**
 * 自适应玻璃容器 - 根据当前主题自动选择玻璃效果
 * 
 * 当启用液态玻璃主题时，使用 Backdrop 库提供真实液态玻璃效果
 * 否则使用 FrostedGlass 提供毛玻璃模糊效果
 * 
 * @param modifier 修饰符
 * @param shape 形状
 * @param blurRadius 模糊半径（用于毛玻璃模式）
 * @param tint 背景色调（用于毛玻璃模式）
 * @param tintGradient 背景渐变（用于毛玻璃模式）
 * @param backdropConfig Backdrop 液态玻璃配置（用于液态玻璃模式）
 * @param contentAlignment 内容对齐方式
 * @param content 内容
 */
@Composable
fun AdaptiveGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    // 毛玻璃参数
    blurRadius: Dp = 20.dp,
    tint: Color = Color.Transparent,
    tintGradient: Brush? = null,
    noiseAlpha: Float = 0.06f,
    borderBrush: Brush? = null,
    borderWidth: Dp = 0.dp,
    highlightBrush: Brush? = null,
    meniscusHeight: Dp = 0.dp,
    meniscusAlpha: Float = 0f,
    // Backdrop 液态玻璃参数
    backdropConfig: BackdropLiquidGlassConfig = BackdropLiquidGlassConfig.Default,
    // 通用参数
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    
    if (isLiquidGlassEnabled) {
        // 使用 Backdrop 库的液态玻璃效果
        BackdropLiquidGlassBox(
            modifier = modifier,
            shape = shape,
            config = backdropConfig,
            contentAlignment = contentAlignment,
            content = content
        )
    } else {
        // 使用毛玻璃效果
        FrostedGlass(
            modifier = modifier,
            shape = shape,
            blurRadius = blurRadius,
            tint = tint,
            tintGradient = tintGradient,
            noiseAlpha = noiseAlpha,
            borderBrush = borderBrush,
            borderWidth = borderWidth,
            highlightBrush = highlightBrush,
            meniscusHeight = meniscusHeight,
            meniscusAlpha = meniscusAlpha,
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

/**
 * 自适应玻璃卡片 - 使用卡片预设配置
 */
@Composable
fun AdaptiveGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    blurRadius: Dp = 24.dp,
    tint: Color = Color.White.copy(alpha = 0.15f),
    tintGradient: Brush? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    AdaptiveGlass(
        modifier = modifier,
        shape = shape,
        blurRadius = blurRadius,
        tint = tint,
        tintGradient = tintGradient,
        backdropConfig = BackdropLiquidGlassConfig.Card,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 自适应玻璃顶栏/底栏 - 使用栏目预设配置
 */
@Composable
fun AdaptiveGlassBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 28.dp,
    tint: Color = Color.White.copy(alpha = 0.2f),
    tintGradient: Brush? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    AdaptiveGlass(
        modifier = modifier,
        shape = shape,
        blurRadius = blurRadius,
        tint = tint,
        tintGradient = tintGradient,
        backdropConfig = BackdropLiquidGlassConfig.Bar,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 自适应玻璃设置组 - 使用设置预设配置
 */
@Composable
fun AdaptiveGlassSettings(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    blurRadius: Dp = 20.dp,
    tint: Color = Color.White.copy(alpha = 0.12f),
    tintGradient: Brush? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    AdaptiveGlass(
        modifier = modifier,
        shape = shape,
        blurRadius = blurRadius,
        tint = tint,
        tintGradient = tintGradient,
        backdropConfig = BackdropLiquidGlassConfig.Settings,
        contentAlignment = contentAlignment,
        content = content
    )
}
