package com.tabula.v3.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃风格分割线配置
 * 
 * 设计目标：
 * - 看起来像玻璃内部的"刻线/层次"，不是白色实线
 * - 在浅色背景下不抢眼，但能提供结构分组
 * - 与玻璃外边界（rim/separator）风格一致
 */
data class GlassDividerConfig(
    // === 颜色 ===
    // 使用 iOS separator 系的中性灰，禁止纯白
    val baseColor: Color = Color(0x3C, 0x3C, 0x43),  // iOS separator 基色
    val baseAlpha: Float = 0.22f,                     // 默认透明度 (0.18~0.28)
    
    // === 尺寸 ===
    val thickness: Dp = 0.5.dp,                       // 线宽（像素对齐）
    val horizontalPadding: Dp = 18.dp,                // 两端留白
    val startPadding: Dp = 0.dp,                      // 额外左侧留白（用于带图标的列表）
    
    // === 两端淡出 ===
    val fadeEnabled: Boolean = true,                  // 是否启用两端淡出
    val fadeLength: Dp = 16.dp,                       // 淡出区域长度
    
    // === 浮雕效果（可选，模拟玻璃刻线） ===
    val embossEnabled: Boolean = false,               // 是否启用浮雕
    val embossLightAlpha: Float = 0.08f,              // 上方亮线 alpha
    val embossDarkAlpha: Float = 0.12f,               // 下方暗线 alpha
    val embossSpacing: Dp = 0.5.dp,                   // 两条线间距
    
    // === 自适应对比（根据玻璃参数动态调整） ===
    val adaptiveContrast: Boolean = true,             // 是否启用自适应
    val surfaceAlphaReference: Float = 0.20f          // 参考 surfaceAlpha，用于计算调整
) {
    companion object {
        val Default = GlassDividerConfig()
        
        /**
         * 用于设置项列表（带图标缩进）
         */
        val SettingsItem = GlassDividerConfig(
            startPadding = 52.dp,
            baseAlpha = 0.20f,
            fadeLength = 12.dp
        )
        
        /**
         * 用于卡片内部分组（无缩进）
         */
        val CardSection = GlassDividerConfig(
            horizontalPadding = 16.dp,
            baseAlpha = 0.24f,
            fadeLength = 18.dp
        )
        
        /**
         * 带浮雕效果（更明显的层次感）
         */
        val Embossed = GlassDividerConfig(
            baseAlpha = 0.18f,
            embossEnabled = true,
            embossLightAlpha = 0.06f,
            embossDarkAlpha = 0.10f
        )
        
        /**
         * 极简版（几乎不可见）
         */
        val Subtle = GlassDividerConfig(
            baseAlpha = 0.14f,
            fadeLength = 24.dp
        )
    }
}

/**
 * 玻璃风格分割线
 * 
 * 特性：
 * - 中性灰色调，低对比度
 * - 两端自然淡出
 * - 像素对齐，不发虚
 * - 可选浮雕效果
 * - 自适应对比度
 */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
    config: GlassDividerConfig = GlassDividerConfig.Default,
    glassSurfaceAlpha: Float? = null  // 外部玻璃容器的 surfaceAlpha，用于自适应
) {
    val density = LocalDensity.current
    
    // 计算自适应 alpha
    val effectiveAlpha = remember(config, glassSurfaceAlpha) {
        if (config.adaptiveContrast && glassSurfaceAlpha != null) {
            // surfaceAlpha 越高（越雾），dividerAlpha 越低
            // surfaceAlpha 越低（越清透），dividerAlpha 略升
            val alphaRatio = glassSurfaceAlpha / config.surfaceAlphaReference
            val adjusted = config.baseAlpha / alphaRatio.coerceIn(0.7f, 1.4f)
            adjusted.coerceIn(0.12f, 0.35f)
        } else {
            config.baseAlpha
        }
    }
    
    // 计算像素对齐的线宽
    val thicknessPx = with(density) { config.thickness.toPx() }
    val useOnePixelFallback = thicknessPx < 1f
    val actualThicknessPx = if (useOnePixelFallback) 1f else kotlin.math.ceil(thicknessPx)
    val alphaMultiplier = if (useOnePixelFallback) 0.7f else 1f
    
    val finalAlpha = effectiveAlpha * alphaMultiplier
    
    // 计算高度（浮雕模式需要更高）
    val totalHeight = if (config.embossEnabled) {
        config.thickness + config.embossSpacing + config.thickness
    } else {
        config.thickness
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = config.startPadding)
            .height(totalHeight)
    ) {
        val width = size.width
        val height = size.height
        val startX = with(density) { config.horizontalPadding.toPx() }
        val endX = width - startX
        val lineLength = endX - startX
        
        if (lineLength <= 0f) return@Canvas
        
        val fadeLengthPx = with(density) { config.fadeLength.toPx() }
        
        // 基色
        val baseColor = config.baseColor.copy(alpha = finalAlpha)
        
        if (config.fadeEnabled && fadeLengthPx > 0f) {
            // === 带淡出的渐变线 ===
            // 计算渐变停止点
            val fadeRatio = (fadeLengthPx / lineLength).coerceIn(0f, 0.3f)
            val solidStart = fadeRatio
            val solidEnd = 1f - fadeRatio
            
            val gradientBrush = Brush.horizontalGradient(
                0f to baseColor.copy(alpha = 0f),
                solidStart to baseColor,
                solidEnd to baseColor,
                1f to baseColor.copy(alpha = 0f),
                startX = startX,
                endX = endX
            )
            
            if (config.embossEnabled) {
                // === 浮雕模式：上亮下暗 ===
                val lightColor = Color.White.copy(alpha = config.embossLightAlpha * alphaMultiplier)
                val darkColor = config.baseColor.copy(alpha = config.embossDarkAlpha * alphaMultiplier)
                val spacingPx = with(density) { config.embossSpacing.toPx() }
                
                // 上方亮线
                val lightY = actualThicknessPx / 2f
                val lightGradient = Brush.horizontalGradient(
                    0f to lightColor.copy(alpha = 0f),
                    solidStart to lightColor,
                    solidEnd to lightColor,
                    1f to lightColor.copy(alpha = 0f),
                    startX = startX,
                    endX = endX
                )
                drawLine(
                    brush = lightGradient,
                    start = Offset(startX, lightY),
                    end = Offset(endX, lightY),
                    strokeWidth = actualThicknessPx,
                    cap = StrokeCap.Round
                )
                
                // 下方暗线
                val darkY = actualThicknessPx + spacingPx + actualThicknessPx / 2f
                val darkGradient = Brush.horizontalGradient(
                    0f to darkColor.copy(alpha = 0f),
                    solidStart to darkColor,
                    solidEnd to darkColor,
                    1f to darkColor.copy(alpha = 0f),
                    startX = startX,
                    endX = endX
                )
                drawLine(
                    brush = darkGradient,
                    start = Offset(startX, darkY),
                    end = Offset(endX, darkY),
                    strokeWidth = actualThicknessPx,
                    cap = StrokeCap.Round
                )
            } else {
                // === 单线模式 ===
                // 像素对齐 Y 坐标
                val lineY = kotlin.math.floor(height / 2f) + 0.5f
                
                drawLine(
                    brush = gradientBrush,
                    start = Offset(startX, lineY),
                    end = Offset(endX, lineY),
                    strokeWidth = actualThicknessPx,
                    cap = StrokeCap.Round
                )
            }
        } else {
            // === 无淡出的实线 ===
            val lineY = kotlin.math.floor(height / 2f) + 0.5f
            
            if (config.embossEnabled) {
                val lightColor = Color.White.copy(alpha = config.embossLightAlpha * alphaMultiplier)
                val darkColor = config.baseColor.copy(alpha = config.embossDarkAlpha * alphaMultiplier)
                val spacingPx = with(density) { config.embossSpacing.toPx() }
                
                // 上亮
                drawLine(
                    color = lightColor,
                    start = Offset(startX, actualThicknessPx / 2f),
                    end = Offset(endX, actualThicknessPx / 2f),
                    strokeWidth = actualThicknessPx,
                    cap = StrokeCap.Round
                )
                // 下暗
                drawLine(
                    color = darkColor,
                    start = Offset(startX, actualThicknessPx + spacingPx + actualThicknessPx / 2f),
                    end = Offset(endX, actualThicknessPx + spacingPx + actualThicknessPx / 2f),
                    strokeWidth = actualThicknessPx,
                    cap = StrokeCap.Round
                )
            } else {
                drawLine(
                    color = baseColor,
                    start = Offset(startX, lineY),
                    end = Offset(endX, lineY),
                    strokeWidth = actualThicknessPx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * 简化版：用于替换现有 Divider
 * 自动根据主题选择合适的颜色
 */
@Composable
fun GlassDivider(
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    startPadding: Dp = 52.dp,  // 兼容现有设置项的图标缩进
    glassSurfaceAlpha: Float? = null
) {
    val config = remember(isDarkTheme, startPadding) {
        GlassDividerConfig(
            // 深色/浅色主题使用相同的中性灰，通过 alpha 调整
            baseColor = Color(0x3C, 0x3C, 0x43),
            baseAlpha = if (isDarkTheme) 0.26f else 0.20f,
            startPadding = startPadding,
            horizontalPadding = 0.dp,  // startPadding 已包含
            fadeEnabled = true,
            fadeLength = 20.dp
        )
    }
    
    GlassDivider(
        modifier = modifier,
        config = config,
        glassSurfaceAlpha = glassSurfaceAlpha
    )
}
