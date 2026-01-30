package com.tabula.v3.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal 用于判断是否启用液态玻璃主题
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/**
 * 液态玻璃效果配置
 * 
 * 模拟 iOS 26 液态玻璃效果的视觉特征
 */
data class LiquidGlassConfig(
    val cornerRadius: Dp = 16.dp,
    val blurRadius: Dp = 20.dp,
    val tintAlpha: Float = 0.12f,
    val highlightAlpha: Float = 0.2f,
    val borderAlpha: Float = 0.25f,
    val dispersionAlpha: Float = 0.06f,
    val innerShadowAlpha: Float = 0.08f,
    val depthGlowAlpha: Float = 0.1f
) {
    companion object {
        /**
         * 默认配置 - 适用于卡片、按钮等通用组件
         */
        val Default = LiquidGlassConfig()
        
        /**
         * 顶栏/底栏配置 - 更柔和的效果
         */
        val Bar = LiquidGlassConfig(
            cornerRadius = 0.dp,
            blurRadius = 25.dp,
            tintAlpha = 0.1f,
            highlightAlpha = 0.15f,
            borderAlpha = 0.2f,
            dispersionAlpha = 0.04f
        )
        
        /**
         * 卡片配置 - 更强的效果
         */
        val Card = LiquidGlassConfig(
            cornerRadius = 20.dp,
            blurRadius = 24.dp,
            tintAlpha = 0.15f,
            highlightAlpha = 0.25f,
            borderAlpha = 0.3f,
            dispersionAlpha = 0.08f,
            depthGlowAlpha = 0.12f
        )
        
        /**
         * 设置项配置 - 圆角较小
         */
        val Settings = LiquidGlassConfig(
            cornerRadius = 16.dp,
            blurRadius = 20.dp,
            tintAlpha = 0.12f,
            highlightAlpha = 0.18f,
            borderAlpha = 0.22f,
            dispersionAlpha = 0.05f
        )
        
        /**
         * 弹窗/底栏配置 - 大圆角
         */
        val Sheet = LiquidGlassConfig(
            cornerRadius = 28.dp,
            blurRadius = 22.dp,
            tintAlpha = 0.14f,
            highlightAlpha = 0.2f,
            borderAlpha = 0.25f,
            dispersionAlpha = 0.06f
        )
    }
}

/**
 * 液态玻璃容器组件
 * 
 * 使用 AGSL RuntimeShader 实现真实的折射和色散效果（Android 13+）
 * 基于物理的光学效果：
 * - 真实的边缘折射效果（基于 SDF 距离场）
 * - 7色色散效果（红、橙、黄、绿、青、蓝、紫）
 * - 背景模糊
 * - 顶部高光（模拟光照）
 * - 渐变边框
 * 
 * @param modifier 修饰符
 * @param shape 形状
 * @param config 液态玻璃配置
 * @param contentAlignment 内容对齐方式
 * @param content 内容
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    config: LiquidGlassConfig = LiquidGlassConfig.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    // 将 LiquidGlassConfig 转换为 LiquidGlassConfigV2
    val v2Config = remember(config) {
        LiquidGlassConfigV2(
            cornerRadius = config.cornerRadius,
            blurRadius = config.blurRadius,
            surfaceAlpha = config.tintAlpha,
            surfaceColor = Color.White,
            highlightAlpha = config.highlightAlpha * 1.5f,  // 增强高光
            borderAlpha = config.borderAlpha * 1.5f,        // 增强边框
            dispersionWidth = 3.dp,
            dispersionAlpha = config.dispersionAlpha * 8f,  // 增强色散可见度
            innerGlowAlpha = config.innerShadowAlpha,
            depthGlowAlpha = config.depthGlowAlpha
        )
    }
    
    LiquidGlassBoxV2(
        modifier = modifier,
        shape = shape,
        config = v2Config,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 简化的液态玻璃容器，使用预设配置
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfig.Card,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃顶栏/底栏容器
 */
@Composable
fun LiquidGlassBar(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfig.Bar,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃设置项容器
 */
@Composable
fun LiquidGlassSettings(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfig.Settings,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃弹窗/底栏容器
 */
@Composable
fun LiquidGlassSheet(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = LiquidGlassConfig.Sheet,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 根据当前主题自动选择玻璃效果
 */
@Composable
fun AdaptiveGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    isLiquidGlassEnabled: Boolean = LocalLiquidGlassEnabled.current,
    liquidGlassConfig: LiquidGlassConfig = LiquidGlassConfig.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    if (isLiquidGlassEnabled) {
        LiquidGlassBox(
            modifier = modifier,
            shape = shape,
            config = liquidGlassConfig,
            contentAlignment = contentAlignment,
            content = content
        )
    } else {
        Box(
            modifier = modifier.then(
                if (shape != null) Modifier.clip(shape) else Modifier
            ),
            contentAlignment = contentAlignment
        ) {
            content()
        }
    }
}

/**
 * 液态玻璃主题的背景色
 */
object LiquidGlassColors {
    // 浅色背景上的玻璃
    val LightSurface = Color(0x20FFFFFF)
    val LightSurfaceVariant = Color(0x30FFFFFF)
    val LightCard = Color(0x25FFFFFF)
    
    // 深色背景上的玻璃
    val DarkSurface = Color(0x20000000)
    val DarkSurfaceVariant = Color(0x30000000)
    val DarkCard = Color(0x25000000)
    
    // 通用强调色（带透明度）
    val AccentOverlay = Color(0x15FFD54F)
}
