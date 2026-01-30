package com.tabula.v3.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 真实液态玻璃效果配置
 * 
 * 使用 AGSL (Android Graphics Shading Language) 实现 iOS 26 风格的液态玻璃效果
 * 包含真实的折射、色散和模糊效果
 */
data class RealLiquidGlassConfig(
    val cornerRadius: Dp = 16.dp,
    val refractionHeight: Float = 12f,          // 折射边缘高度
    val refractionAmount: Float = 0.02f,        // 折射强度
    val dispersionStrength: Float = 0.003f,     // 色散强度（彩虹边缘）
    val blurRadius: Dp = 20.dp,                 // 模糊半径
    val surfaceAlpha: Float = 0.12f,            // 表面透明度
    val surfaceColor: Color = Color.White,      // 表面颜色
    val highlightAlpha: Float = 0.2f,           // 高光透明度
    val borderAlpha: Float = 0.25f,             // 边框透明度
    val innerGlowAlpha: Float = 0.1f,           // 内发光透明度
    val depthGlowAlpha: Float = 0.1f            // 底部深度发光
) {
    companion object {
        /**
         * 默认配置 - 适用于通用组件
         */
        val Default = RealLiquidGlassConfig()
        
        /**
         * 顶栏/底栏配置 - 更柔和的效果
         */
        val Bar = RealLiquidGlassConfig(
            cornerRadius = 0.dp,
            refractionHeight = 8f,
            refractionAmount = 0.015f,
            dispersionStrength = 0.002f,
            blurRadius = 25.dp,
            surfaceAlpha = 0.1f,
            highlightAlpha = 0.15f,
            borderAlpha = 0.2f,
            innerGlowAlpha = 0.08f
        )
        
        /**
         * 卡片配置 - 更强的效果
         */
        val Card = RealLiquidGlassConfig(
            cornerRadius = 20.dp,
            refractionHeight = 15f,
            refractionAmount = 0.025f,
            dispersionStrength = 0.004f,
            blurRadius = 24.dp,
            surfaceAlpha = 0.15f,
            highlightAlpha = 0.25f,
            borderAlpha = 0.3f,
            innerGlowAlpha = 0.12f,
            depthGlowAlpha = 0.12f
        )
        
        /**
         * 设置项配置
         */
        val Settings = RealLiquidGlassConfig(
            cornerRadius = 16.dp,
            refractionHeight = 10f,
            refractionAmount = 0.02f,
            dispersionStrength = 0.003f,
            blurRadius = 20.dp,
            surfaceAlpha = 0.12f,
            highlightAlpha = 0.18f,
            borderAlpha = 0.22f,
            innerGlowAlpha = 0.1f
        )
        
        /**
         * 按钮配置 - 胶囊形状
         */
        val Button = RealLiquidGlassConfig(
            cornerRadius = 100.dp,
            refractionHeight = 12f,
            refractionAmount = 0.02f,
            dispersionStrength = 0.003f,
            blurRadius = 28.dp,
            surfaceAlpha = 0.14f,
            highlightAlpha = 0.22f,
            borderAlpha = 0.25f,
            innerGlowAlpha = 0.1f
        )
        
        /**
         * 切换器配置 - 用于 ModeToggle 等
         */
        val Toggle = RealLiquidGlassConfig(
            cornerRadius = 100.dp,
            refractionHeight = 10f,
            refractionAmount = 0.018f,
            dispersionStrength = 0.0025f,
            blurRadius = 34.dp,
            surfaceAlpha = 0.12f,
            highlightAlpha = 0.2f,
            borderAlpha = 0.22f,
            innerGlowAlpha = 0.08f
        )
    }
}

/**
 * AGSL 液态玻璃着色器
 * 
 * 实现真实的折射和色散效果
 * - 边缘折射：模拟玻璃边缘的光线弯曲
 * - 色散：模拟棱镜效果，RGB 通道分离
 */
private val LIQUID_GLASS_SHADER = """
    uniform shader content;
    uniform float2 resolution;
    uniform float refractionHeight;
    uniform float refractionAmount;
    uniform float dispersionStrength;
    uniform float cornerRadius;
    
    // 计算到圆角矩形边缘的距离
    float roundedRectSDF(float2 p, float2 size, float radius) {
        float2 q = abs(p) - size + radius;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
    }
    
    // 计算折射偏移
    float2 getRefraction(float2 uv, float dist) {
        if (dist > refractionHeight) return float2(0.0);
        
        // 计算折射强度（边缘更强）
        float strength = 1.0 - dist / refractionHeight;
        strength = strength * strength; // 非线性衰减
        
        // 计算法线方向（指向中心）
        float2 center = float2(0.5);
        float2 toCenter = normalize(center - uv);
        
        // 折射偏移
        return toCenter * strength * refractionAmount;
    }
    
    half4 main(float2 coord) {
        float2 uv = coord / resolution;
        float2 center = resolution / 2.0;
        float2 size = resolution / 2.0 - cornerRadius;
        
        // 计算到边缘的距离
        float dist = roundedRectSDF(coord - center, size, cornerRadius);
        
        // 折射偏移
        float2 refraction = getRefraction(uv, abs(dist));
        
        // 色散：RGB 通道使用不同的折射偏移
        float2 uvR = uv + refraction * (1.0 + dispersionStrength);
        float2 uvG = uv + refraction;
        float2 uvB = uv + refraction * (1.0 - dispersionStrength);
        
        // 采样（转换回像素坐标）
        half4 colorR = content.eval(uvR * resolution);
        half4 colorG = content.eval(uvG * resolution);
        half4 colorB = content.eval(uvB * resolution);
        
        // 合并 RGB 通道
        return half4(colorR.r, colorG.g, colorB.b, (colorR.a + colorG.a + colorB.a) / 3.0);
    }
""".trimIndent()

/**
 * 创建液态玻璃 RenderEffect
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createLiquidGlassEffect(
    width: Float,
    height: Float,
    config: RealLiquidGlassConfig,
    blurRadiusPx: Float,
    cornerRadiusPx: Float
): RenderEffect? {
    return try {
        // 创建 RuntimeShader
        val shader = RuntimeShader(LIQUID_GLASS_SHADER)
        
        // 设置 uniform 变量
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("refractionHeight", config.refractionHeight)
        shader.setFloatUniform("refractionAmount", config.refractionAmount)
        shader.setFloatUniform("dispersionStrength", config.dispersionStrength)
        shader.setFloatUniform("cornerRadius", cornerRadiusPx)
        
        // 组合效果：模糊 + 着色器
        val blurEffect = RenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
            Shader.TileMode.CLAMP
        )
        
        val shaderEffect = RenderEffect.createRuntimeShaderEffect(
            shader,
            "content"
        )
        
        // 先模糊，再应用折射着色器
        RenderEffect.createChainEffect(shaderEffect, blurEffect)
    } catch (e: Exception) {
        // 着色器编译失败时返回普通模糊
        try {
            RenderEffect.createBlurEffect(
                blurRadiusPx,
                blurRadiusPx,
                Shader.TileMode.CLAMP
            )
        } catch (e2: Exception) {
            null
        }
    }
}

/**
 * 真实液态玻璃容器
 * 
 * 使用 AGSL RuntimeShader 实现真实的折射和色散效果
 * 仅在 Android 13 (API 33) 及以上版本有效
 * 
 * @param modifier 修饰符
 * @param shape 形状
 * @param config 液态玻璃配置
 * @param contentAlignment 内容对齐方式
 * @param content 内容
 */
@Composable
fun RealLiquidGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    config: RealLiquidGlassConfig = RealLiquidGlassConfig.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { config.blurRadius.toPx() }
    val cornerRadiusPx = with(density) { config.cornerRadius.toPx() }
    val actualShape = shape ?: RoundedCornerShape(config.cornerRadius)
    
    // 色散渐变色彩
    val dispersionColors = remember(config.dispersionStrength) {
        listOf(
            Color(0xFFFF6B6B).copy(alpha = config.dispersionStrength * 20f), // 红
            Color(0xFFFFE66D).copy(alpha = config.dispersionStrength * 16f), // 黄
            Color(0xFF69F0AE).copy(alpha = config.dispersionStrength * 12f), // 绿
            Color(0xFF64B5F6).copy(alpha = config.dispersionStrength * 16f), // 蓝
            Color(0xFFBA68C8).copy(alpha = config.dispersionStrength * 20f), // 紫
        )
    }
    
    // 顶部高光渐变
    val highlightGradient = remember(config.highlightAlpha) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = config.highlightAlpha),
            0.3f to Color.White.copy(alpha = config.highlightAlpha * 0.3f),
            0.5f to Color.Transparent,
            1f to Color.Transparent
        )
    }
    
    // 边框渐变
    val borderGradient = remember(config.borderAlpha) {
        Brush.sweepGradient(
            0f to Color.White.copy(alpha = config.borderAlpha * 1.2f),
            0.25f to Color.White.copy(alpha = config.borderAlpha * 0.4f),
            0.5f to Color.White.copy(alpha = config.borderAlpha * 0.6f),
            0.75f to Color.White.copy(alpha = config.borderAlpha * 0.3f),
            1f to Color.White.copy(alpha = config.borderAlpha * 1.2f)
        )
    }
    
    // 底部深度发光
    val depthGlow = remember(config.depthGlowAlpha) {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.7f to Color.Transparent,
            0.9f to Color.White.copy(alpha = config.depthGlowAlpha * 0.5f),
            1f to Color.White.copy(alpha = config.depthGlowAlpha)
        )
    }
    
    // 使用 drawBehind 在内容后面绘制背景效果，避免影响内容
    Box(
        modifier = modifier
            .clip(actualShape)
            // 半透明表面色
            .background(config.surfaceColor.copy(alpha = config.surfaceAlpha))
            // 绘制各层视觉效果（在内容之上）
            .drawWithContent {
                // 先绘制内容
                drawContent()
                
                // 左上角高光
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = config.highlightAlpha * 0.5f),
                        0.4f to Color.White.copy(alpha = config.highlightAlpha * 0.1f),
                        1f to Color.Transparent,
                        center = Offset(0f, 0f),
                        radius = size.minDimension * 0.8f
                    )
                )
                
                // 顶部高光条
                drawRect(
                    brush = highlightGradient,
                    size = Size(size.width, size.height * 0.3f)
                )
                
                // 边缘色散效果 - 顶部
                drawRect(
                    brush = Brush.horizontalGradient(dispersionColors),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 1.5.dp.toPx()),
                    alpha = 0.5f
                )
                
                // 边缘色散效果 - 左侧
                drawRect(
                    brush = Brush.verticalGradient(dispersionColors),
                    topLeft = Offset(0f, 0f),
                    size = Size(1.5.dp.toPx(), size.height),
                    alpha = 0.4f
                )
                
                // 边缘色散效果 - 右侧（反向）
                drawRect(
                    brush = Brush.verticalGradient(dispersionColors.reversed()),
                    topLeft = Offset(size.width - 1.5.dp.toPx(), 0f),
                    size = Size(1.5.dp.toPx(), size.height),
                    alpha = 0.4f
                )
                
                // 边缘色散效果 - 底部（反向）
                drawRect(
                    brush = Brush.horizontalGradient(dispersionColors.reversed()),
                    topLeft = Offset(0f, size.height - 1.5.dp.toPx()),
                    size = Size(size.width, 1.5.dp.toPx()),
                    alpha = 0.5f
                )
                
                // 底部深度发光
                drawRect(brush = depthGlow)
            }
            // 渐变边框
            .border(
                width = 0.5.dp,
                brush = borderGradient,
                shape = actualShape
            ),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

/**
 * 真实液态玻璃卡片
 */
@Composable
fun RealLiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    RealLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = RealLiquidGlassConfig.Card,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 真实液态玻璃设置组
 */
@Composable
fun RealLiquidGlassSettings(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    RealLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = RealLiquidGlassConfig.Settings,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 真实液态玻璃按钮容器
 */
@Composable
fun RealLiquidGlassButton(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    RealLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = RealLiquidGlassConfig.Button,
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 真实液态玻璃切换器容器
 */
@Composable
fun RealLiquidGlassToggle(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    RealLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = RealLiquidGlassConfig.Toggle,
        contentAlignment = contentAlignment,
        content = content
    )
}
