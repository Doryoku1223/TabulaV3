package com.tabula.v3.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 液态玻璃效果配置 V2
 * 
 * 增强的视觉模拟效果，提供更接近 iOS 26 液态玻璃的外观
 */
data class LiquidGlassConfigV2(
    val cornerRadius: Dp = 16.dp,
    val blurRadius: Dp = 20.dp,              // 模糊半径
    val surfaceAlpha: Float = 0.15f,         // 表面透明度
    val surfaceColor: Color = Color.White,   // 表面颜色
    val highlightAlpha: Float = 0.35f,       // 高光透明度（增强）
    val borderAlpha: Float = 0.4f,           // 边框透明度（增强）
    val dispersionWidth: Dp = 3.dp,          // 色散边缘宽度（增强）
    val dispersionAlpha: Float = 0.6f,       // 色散透明度（增强）
    val innerGlowAlpha: Float = 0.15f,       // 内发光透明度
    val depthGlowAlpha: Float = 0.12f,       // 底部深度发光
    val refractionOffset: Dp = 1.dp          // 折射偏移量（视觉模拟）
) {
    companion object {
        /**
         * 默认配置 - 适用于通用组件
         */
        val Default = LiquidGlassConfigV2()
        
        /**
         * 顶栏/底栏配置 - 更柔和的效果
         */
        val Bar = LiquidGlassConfigV2(
            cornerRadius = 0.dp,
            blurRadius = 25.dp,
            surfaceAlpha = 0.12f,
            highlightAlpha = 0.25f,
            borderAlpha = 0.3f,
            dispersionWidth = 2.dp,
            dispersionAlpha = 0.5f
        )
        
        /**
         * 卡片配置 - 更强的效果
         */
        val Card = LiquidGlassConfigV2(
            cornerRadius = 20.dp,
            blurRadius = 24.dp,
            surfaceAlpha = 0.18f,
            highlightAlpha = 0.4f,
            borderAlpha = 0.45f,
            dispersionWidth = 4.dp,
            dispersionAlpha = 0.7f,
            depthGlowAlpha = 0.15f
        )
        
        /**
         * 设置项配置
         */
        val Settings = LiquidGlassConfigV2(
            cornerRadius = 16.dp,
            blurRadius = 20.dp,
            surfaceAlpha = 0.15f,
            highlightAlpha = 0.35f,
            borderAlpha = 0.4f,
            dispersionWidth = 3.dp,
            dispersionAlpha = 0.6f
        )
        
        /**
         * 按钮配置 - 胶囊形状
         */
        val Button = LiquidGlassConfigV2(
            cornerRadius = 100.dp,
            blurRadius = 28.dp,
            surfaceAlpha = 0.16f,
            highlightAlpha = 0.38f,
            borderAlpha = 0.42f,
            dispersionWidth = 3.dp,
            dispersionAlpha = 0.65f
        )
        
        /**
         * 切换器配置 - 用于 ModeToggle 等
         */
        val Toggle = LiquidGlassConfigV2(
            cornerRadius = 100.dp,
            blurRadius = 34.dp,
            surfaceAlpha = 0.14f,
            highlightAlpha = 0.32f,
            borderAlpha = 0.38f,
            dispersionWidth = 2.5.dp,
            dispersionAlpha = 0.55f
        )
        
        /**
         * 弹窗/底栏配置 - 大圆角
         */
        val Sheet = LiquidGlassConfigV2(
            cornerRadius = 28.dp,
            blurRadius = 22.dp,
            surfaceAlpha = 0.16f,
            highlightAlpha = 0.35f,
            borderAlpha = 0.4f,
            dispersionWidth = 3.5.dp,
            dispersionAlpha = 0.65f
        )
    }
}

/**
 * 彩虹色散颜色 - 模拟棱镜效果
 */
private val DISPERSION_COLORS = listOf(
    Color(0xFFFF6B6B), // 红
    Color(0xFFFF8E53), // 橙
    Color(0xFFFFE66D), // 黄
    Color(0xFF69F0AE), // 绿
    Color(0xFF64B5F6), // 蓝
    Color(0xFF7C4DFF), // 靛
    Color(0xFFBA68C8)  // 紫
)

/**
 * 绘制增强的色散边缘效果
 */
private fun DrawScope.drawDispersionEdges(
    config: LiquidGlassConfigV2,
    dispersionWidthPx: Float
) {
    val alpha = config.dispersionAlpha
    
    // 顶部边缘 - 彩虹渐变
    drawRect(
        brush = Brush.horizontalGradient(
            colors = DISPERSION_COLORS.map { it.copy(alpha = alpha) }
        ),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, dispersionWidthPx)
    )
    
    // 底部边缘 - 反向彩虹渐变
    drawRect(
        brush = Brush.horizontalGradient(
            colors = DISPERSION_COLORS.reversed().map { it.copy(alpha = alpha) }
        ),
        topLeft = Offset(0f, size.height - dispersionWidthPx),
        size = Size(size.width, dispersionWidthPx)
    )
    
    // 左侧边缘
    drawRect(
        brush = Brush.verticalGradient(
            colors = DISPERSION_COLORS.map { it.copy(alpha = alpha * 0.8f) }
        ),
        topLeft = Offset(0f, 0f),
        size = Size(dispersionWidthPx, size.height)
    )
    
    // 右侧边缘
    drawRect(
        brush = Brush.verticalGradient(
            colors = DISPERSION_COLORS.reversed().map { it.copy(alpha = alpha * 0.8f) }
        ),
        topLeft = Offset(size.width - dispersionWidthPx, 0f),
        size = Size(dispersionWidthPx, size.height)
    )
    
    // 四角增强色散（模拟光线折射聚集）
    val cornerSize = dispersionWidthPx * 3f
    
    // 左上角
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF6B6B).copy(alpha = alpha * 0.6f),
                Color.Transparent
            ),
            center = Offset(0f, 0f),
            radius = cornerSize
        ),
        topLeft = Offset(0f, 0f),
        size = Size(cornerSize, cornerSize)
    )
    
    // 右上角
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFE66D).copy(alpha = alpha * 0.6f),
                Color.Transparent
            ),
            center = Offset(size.width, 0f),
            radius = cornerSize
        ),
        topLeft = Offset(size.width - cornerSize, 0f),
        size = Size(cornerSize, cornerSize)
    )
    
    // 左下角
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF64B5F6).copy(alpha = alpha * 0.6f),
                Color.Transparent
            ),
            center = Offset(0f, size.height),
            radius = cornerSize
        ),
        topLeft = Offset(0f, size.height - cornerSize),
        size = Size(cornerSize, cornerSize)
    )
    
    // 右下角
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFBA68C8).copy(alpha = alpha * 0.6f),
                Color.Transparent
            ),
            center = Offset(size.width, size.height),
            radius = cornerSize
        ),
        topLeft = Offset(size.width - cornerSize, size.height - cornerSize),
        size = Size(cornerSize, cornerSize)
    )
}

/**
 * 绘制高光效果
 */
private fun DrawScope.drawHighlights(config: LiquidGlassConfigV2) {
    val alpha = config.highlightAlpha
    
    // 顶部渐变高光
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = alpha),
            0.15f to Color.White.copy(alpha = alpha * 0.5f),
            0.35f to Color.White.copy(alpha = alpha * 0.15f),
            0.5f to Color.Transparent,
            1f to Color.Transparent
        )
    )
    
    // 左上角径向高光（模拟光源）
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = alpha * 0.8f),
            0.3f to Color.White.copy(alpha = alpha * 0.3f),
            0.6f to Color.White.copy(alpha = alpha * 0.1f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.2f, size.height * 0.1f),
            radius = size.minDimension * 0.6f
        )
    )
    
    // 底部深度发光
    if (config.depthGlowAlpha > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.6f to Color.Transparent,
                0.85f to Color.White.copy(alpha = config.depthGlowAlpha * 0.4f),
                1f to Color.White.copy(alpha = config.depthGlowAlpha)
            )
        )
    }
}

/**
 * 绘制内发光效果
 */
private fun DrawScope.drawInnerGlow(
    config: LiquidGlassConfigV2,
    cornerRadiusPx: Float
) {
    if (config.innerGlowAlpha <= 0f) return
    
    val glowColor = Color.White.copy(alpha = config.innerGlowAlpha)
    val strokeWidth = cornerRadiusPx * 0.5f
    
    // 使用渐变模拟内发光
    drawRoundRect(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.7f to Color.Transparent,
            0.9f to glowColor.copy(alpha = config.innerGlowAlpha * 0.3f),
            1f to glowColor,
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension / 2f
        ),
        cornerRadius = CornerRadius(cornerRadiusPx)
    )
}

/**
 * 液态玻璃容器 V2
 * 
 * 增强的液态玻璃效果，包含：
 * - 真实的背景模糊（使用 Modifier.blur）
 * - 增强的彩虹色散边缘
 * - 多层高光效果
 * - 渐变边框
 * - 内发光和深度感
 */
@Composable
fun LiquidGlassBoxV2(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    config: LiquidGlassConfigV2 = LiquidGlassConfigV2.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { config.cornerRadius.toPx() }
    val dispersionWidthPx = with(density) { config.dispersionWidth.toPx() }
    
    val actualShape = shape ?: RoundedCornerShape(config.cornerRadius)
    
    // 渐变边框
    val borderGradient = remember(config.borderAlpha) {
        Brush.sweepGradient(
            0f to Color.White.copy(alpha = config.borderAlpha),
            0.125f to Color.White.copy(alpha = config.borderAlpha * 1.3f),
            0.25f to Color.White.copy(alpha = config.borderAlpha * 0.5f),
            0.375f to Color.White.copy(alpha = config.borderAlpha * 0.8f),
            0.5f to Color.White.copy(alpha = config.borderAlpha * 0.6f),
            0.625f to Color.White.copy(alpha = config.borderAlpha * 0.4f),
            0.75f to Color.White.copy(alpha = config.borderAlpha * 0.9f),
            0.875f to Color.White.copy(alpha = config.borderAlpha * 0.5f),
            1f to Color.White.copy(alpha = config.borderAlpha)
        )
    }
    
    Box(
        modifier = modifier
            .clip(actualShape)
            // 背景模糊层
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.graphicsLayer {
                        val blurRadiusPx = with(density) { config.blurRadius.toPx() }
                        if (blurRadiusPx > 0f) {
                            renderEffect = RenderEffect.createBlurEffect(
                                blurRadiusPx,
                                blurRadiusPx,
                                Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    }
                } else Modifier
            )
            // 半透明表面
            .background(
                brush = Brush.verticalGradient(
                    0f to config.surfaceColor.copy(alpha = config.surfaceAlpha * 0.8f),
                    0.5f to config.surfaceColor.copy(alpha = config.surfaceAlpha),
                    1f to config.surfaceColor.copy(alpha = config.surfaceAlpha * 1.1f)
                )
            )
            // 绘制液态玻璃效果层
            .drawWithContent {
                // 绘制内容
                drawContent()
                
                // 色散边缘效果
                drawDispersionEdges(config, dispersionWidthPx)
                
                // 高光效果
                drawHighlights(config)
                
                // 内发光
                drawInnerGlow(config, cornerRadiusPx)
            }
            // 渐变边框
            .border(
                width = 1.dp,
                brush = borderGradient,
                shape = actualShape
            ),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

/**
 * 液态玻璃卡片 V2
 */
@Composable
fun LiquidGlassCardV2(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBoxV2(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfigV2.Card,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃设置组 V2
 */
@Composable
fun LiquidGlassSettingsV2(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBoxV2(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfigV2.Settings,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃顶栏/底栏 V2
 */
@Composable
fun LiquidGlassBarV2(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBoxV2(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfigV2.Bar,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃按钮容器 V2
 */
@Composable
fun LiquidGlassButtonV2(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBoxV2(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfigV2.Button,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃切换器容器 V2
 */
@Composable
fun LiquidGlassToggleV2(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBoxV2(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfigV2.Toggle,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃弹窗/底栏容器 V2
 */
@Composable
fun LiquidGlassSheetV2(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBoxV2(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfigV2.Sheet,
        contentAlignment = contentAlignment,
        content = content
    )
}
