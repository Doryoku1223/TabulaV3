package com.tabula.v3.ui.components

import android.os.Build
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃配置
 * 
 * 此配置类用于向后兼容，实际效果由 PhysicalLiquidGlass 组件实现
 */
data class BackdropLiquidGlassConfig(
    val cornerRadius: Dp = 16.dp,
    val blurRadius: Dp = 12.dp,
    val refractionHeight: Dp = 12.dp,
    val refractionAmount: Dp = 24.dp,
    val chromaticAberration: Float = 0.3f,
    val depthEffect: Float = 0.1f,
    val contrast: Float = 0.05f,
    val whitePoint: Float = 0.02f,
    val chromaMultiplier: Float = 1.1f,
    val surfaceAlpha: Float = 0.4f,
    val surfaceColor: Color = Color.White,
    val borderAlpha: Float = 0.3f,
    val highlightAlpha: Float = 0.2f
) {
    /**
     * 转换为 PhysicalLiquidGlassConfig v2
     */
    fun toPhysicalConfig(): PhysicalLiquidGlassConfig {
        return PhysicalLiquidGlassConfig(
            cornerRadius = cornerRadius,
            refractionCenter = chromaticAberration * 0.035f,
            refractionEdgePeak = chromaticAberration * 0.08f,
            dispersionEdge = chromaticAberration * 0.008f,
            edgeThickness = depthEffect + 0.1f,
            bulgeHeight = depthEffect * 0.8f,
            surfaceAlpha = surfaceAlpha * 1.8f,
            tintColor = surfaceColor,
            tintStrength = contrast + 0.08f,
            borderAlpha = borderAlpha
        )
    }
    
    companion object {
        val Default = BackdropLiquidGlassConfig()
        
        val Card = BackdropLiquidGlassConfig(
            cornerRadius = 20.dp,
            blurRadius = 16.dp,
            refractionHeight = 16.dp,
            refractionAmount = 32.dp,
            surfaceAlpha = 0.35f,
            borderAlpha = 0.35f,
            highlightAlpha = 0.25f
        )
        
        val Settings = BackdropLiquidGlassConfig(
            cornerRadius = 16.dp,
            blurRadius = 12.dp,
            refractionHeight = 12.dp,
            refractionAmount = 24.dp,
            surfaceAlpha = 0.4f,
            borderAlpha = 0.3f,
            highlightAlpha = 0.2f
        )
        
        val Button = BackdropLiquidGlassConfig(
            cornerRadius = 100.dp,
            blurRadius = 16.dp,
            refractionHeight = 12.dp,
            refractionAmount = 28.dp,
            surfaceAlpha = 0.45f,
            borderAlpha = 0.35f,
            highlightAlpha = 0.22f
        )
        
        val Toggle = BackdropLiquidGlassConfig(
            cornerRadius = 100.dp,
            blurRadius = 20.dp,
            refractionHeight = 10.dp,
            refractionAmount = 24.dp,
            surfaceAlpha = 0.38f,
            borderAlpha = 0.28f,
            highlightAlpha = 0.18f
        )
        
        val Bar = BackdropLiquidGlassConfig(
            cornerRadius = 0.dp,
            blurRadius = 16.dp,
            refractionHeight = 8.dp,
            refractionAmount = 20.dp,
            surfaceAlpha = 0.35f,
            borderAlpha = 0.25f,
            highlightAlpha = 0.15f
        )
    }
}

/**
 * 检查设备是否支持液态玻璃效果
 * 
 * 需要 Android 13+ (API 33+) 支持 RuntimeShader
 */
fun isBackdropLiquidGlassSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * 液态玻璃容器
 * 
 * 使用物理液态玻璃 (PhysicalLiquidGlass) 实现真实的光学效果
 */
@Composable
fun BackdropLiquidGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    config: BackdropLiquidGlassConfig = BackdropLiquidGlassConfig.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val actualShape = shape ?: RoundedCornerShape(config.cornerRadius)
    val physicalConfig = config.toPhysicalConfig()
    
    PhysicalLiquidGlassBox(
        modifier = modifier,
        shape = actualShape,
        config = physicalConfig,
        enabled = isBackdropLiquidGlassSupported(),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃卡片
 */
@Composable
fun BackdropLiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    BackdropLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = BackdropLiquidGlassConfig.Card,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃设置组
 */
@Composable
fun BackdropLiquidGlassSettings(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    BackdropLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = BackdropLiquidGlassConfig.Settings,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃按钮容器
 */
@Composable
fun BackdropLiquidGlassButton(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    BackdropLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = BackdropLiquidGlassConfig.Button,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃切换器容器
 */
@Composable
fun BackdropLiquidGlassToggle(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    BackdropLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = BackdropLiquidGlassConfig.Toggle,
        contentAlignment = contentAlignment,
        content = content
    )
}
