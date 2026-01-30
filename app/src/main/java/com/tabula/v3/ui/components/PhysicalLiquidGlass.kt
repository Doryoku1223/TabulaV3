package com.tabula.v3.ui.components

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.graphics.Color as AndroidColor
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.random.Random

/**
 * 物理液态玻璃配置 v3
 * 
 * 设计目标：iOS 级打磨，圆角真实、滚动响应、背景自适应
 * 
 * 核心特性：
 * - 双边界厚度 + 圆角衰减：边界线在圆角处柔和过渡
 * - 环带折射：边缘窄带强折射，中心清透
 * - 主反射条 + 滚动响应：滚动时轻微漂移
 * - 防 banding dither：噪点同时作为抗色带
 */
data class PhysicalLiquidGlassConfig(
    // 几何参数
    val cornerRadius: Dp = 20.dp,
    
    // 模糊参数
    val blurRadius: Dp = 20.dp,
    
    // === 环带折射 (Ring Refraction) ===
    val refractionCenter: Float = 0.018f,        // 中心折射（很弱）
    val refractionEdgePeak: Float = 0.044f,      // 边缘峰值（增强）
    val edgeRingWidthDp: Dp = 2.5.dp,            // 折射环带宽度（收窄）
    val ringFalloff: Float = 2.8f,               // 衰减更陡（厚度集中 1~3dp）
    
    // === 边缘色散（门控） ===
    val dispersionEdge: Float = 0.005f,
    val fresnelPower: Float = 2.3f,
    val fresnelIntensity: Float = 0.55f,
    
    // 液态张力参数
    val edgeThickness: Float = 0.15f,
    val bulgeHeight: Float = 0.10f,
    
    // 表面参数 - 中心清透
    val tintColor: Color = Color(0xFFF0F0F2),
    val tintStrength: Float = 0.12f,
    val surfaceAlpha: Float = 0.20f,
    
    // === 高光系统 ===
    // 主反射条
    val primarySpecularIntensity: Float = 0.14f,
    val primarySpecularWidth: Float = 0.85f,
    val primarySpecularYPos: Float = 0.12f,
    // 次级高光
    val secondarySpecularIntensity: Float = 0.75f,
    val secondarySpecularSize: Float = 0.12f,
    val specularYBias: Float = -0.22f,
    
    // 边缘发光
    val edgeGlowIntensity: Float = 0.52f,
    val edgeGlowWidth: Dp = 2.dp,
    
    // === 双边界厚度系统 ===
    // 顶边
    val topRimLightHeight: Dp = 1.dp,
    val topRimLightAlpha: Float = 0.45f,
    val topInnerDarkHeight: Dp = 1.dp,
    val topInnerDarkAlpha: Float = 0.10f,        // +0.02 增强
    // 底边
    val bottomSeparatorHeight: Dp = 0.5.dp,
    val bottomInnerHighlightHeight: Dp = 1.dp,
    val bottomInnerHighlightAlpha: Float = 0.16f, // +0.02 增强
    
    // === 圆角衰减 ===
    val cornerFadeEnabled: Boolean = true,       // 圆角处边界线衰减
    val cornerFadeDistance: Dp = 16.dp,          // 衰减区域宽度
    val cornerFadeAmount: Float = 0.18f,         // 衰减量（降低 18% alpha）
    
    // === 阴影 ===
    val shadowElevation: Dp = 1.5.dp,
    val shadowAlpha: Float = 0.08f,
    val innerShadowHeight: Dp = 1.5.dp,
    val innerShadowAlpha: Float = 0.05f,
    
    // === 轻微动态（液态活性） ===
    val subtleMotionEnabled: Boolean = true,
    val motionAmplitude: Float = 0.003f,
    val motionSpeed: Float = 0.20f,
    
    // === 滚动响应（主反射条微动态） ===
    val scrollResponseEnabled: Boolean = true,   // 滚动时主反射条漂移
    val scrollSpecularDrift: Float = 0.03f,      // Y 偏移幅度 ±0.03
    val scrollIntensityDrift: Float = 0.02f,     // 强度变化幅度 ±0.02
    
    // === 噪点 + Dither ===
    val noiseAlpha: Float = 0.012f,              // 略增，兼做防 banding
    val noiseDitherEnabled: Boolean = true,      // 启用 dither 模式
    
    // 边框
    val borderAlpha: Float = 0.30f
) {
    companion object {
        val Default = PhysicalLiquidGlassConfig()
        
        /**
         * iOS Clean Glass v3 - 极简清透
         * 
         * 适用场景：
         * - 导航栏、工具栏、Tab 栏
         * - 列表项、设置项
         * - 需要大面积使用的轻量组件
         * - 文字密集区域（需要高可读性）
         * 
         * 特点：tint 极轻、边缘克制、中心完全清透
         */
        val iOSCleanGlass = PhysicalLiquidGlassConfig(
            cornerRadius = 16.dp,
            blurRadius = 20.dp,
            // 环带折射（克制）
            refractionCenter = 0.015f,
            refractionEdgePeak = 0.036f,
            edgeRingWidthDp = 2.dp,
            ringFalloff = 3.0f,
            // 色散/菲涅尔
            dispersionEdge = 0.0035f,
            fresnelPower = 2.5f,
            fresnelIntensity = 0.48f,
            // 表面（更清透）
            tintStrength = 0.10f,
            surfaceAlpha = 0.16f,
            // 高光（低调）
            primarySpecularIntensity = 0.10f,
            primarySpecularWidth = 0.78f,
            primarySpecularYPos = 0.10f,
            secondarySpecularIntensity = 0.65f,
            secondarySpecularSize = 0.10f,
            specularYBias = -0.25f,
            edgeGlowIntensity = 0.45f,
            // 双边界（柔和）
            topRimLightAlpha = 0.40f,
            topInnerDarkAlpha = 0.08f,
            bottomInnerHighlightAlpha = 0.13f,
            // 圆角衰减
            cornerFadeEnabled = true,
            cornerFadeDistance = 14.dp,
            cornerFadeAmount = 0.20f,
            // 阴影（极轻）
            shadowElevation = 1.dp,
            shadowAlpha = 0.05f,
            innerShadowAlpha = 0.04f,
            // 动态（关闭，保持稳定）
            subtleMotionEnabled = false,
            scrollResponseEnabled = true,
            scrollSpecularDrift = 0.02f,
            scrollIntensityDrift = 0.015f,
            // 噪点 dither
            noiseAlpha = 0.010f,
            noiseDitherEnabled = true,
            borderAlpha = 0.24f
        )
        
        /**
         * iOS Liquid Glass v3 - 液态质感
         * 
         * 适用场景：
         * - 卡片、面板、弹窗
         * - 重要操作按钮
         * - 需要视觉层次感的独立组件
         * - 滚动列表中的突出项
         * 
         * 特点：边缘厚度明显、滚动时有微动态、更立体
         */
        val iOSLiquidGlass = PhysicalLiquidGlassConfig(
            cornerRadius = 24.dp,
            blurRadius = 24.dp,
            // 环带折射（明显）
            refractionCenter = 0.020f,
            refractionEdgePeak = 0.050f,
            edgeRingWidthDp = 3.dp,
            ringFalloff = 2.5f,
            // 色散/菲涅尔
            dispersionEdge = 0.0055f,
            fresnelPower = 2.2f,
            fresnelIntensity = 0.60f,
            // 表面
            tintStrength = 0.13f,
            surfaceAlpha = 0.21f,
            // 高光（可见）
            primarySpecularIntensity = 0.16f,
            primarySpecularWidth = 0.86f,
            primarySpecularYPos = 0.11f,
            secondarySpecularIntensity = 0.80f,
            secondarySpecularSize = 0.12f,
            specularYBias = -0.20f,
            edgeGlowIntensity = 0.56f,
            // 双边界（立体）
            topRimLightAlpha = 0.48f,
            topInnerDarkAlpha = 0.11f,
            bottomInnerHighlightAlpha = 0.17f,
            // 圆角衰减
            cornerFadeEnabled = true,
            cornerFadeDistance = 18.dp,
            cornerFadeAmount = 0.16f,
            // 阴影
            shadowElevation = 2.dp,
            shadowAlpha = 0.08f,
            innerShadowAlpha = 0.06f,
            // 动态（开启）
            subtleMotionEnabled = true,
            motionAmplitude = 0.0035f,
            motionSpeed = 0.20f,
            scrollResponseEnabled = true,
            scrollSpecularDrift = 0.03f,
            scrollIntensityDrift = 0.02f,
            // 噪点 dither
            noiseAlpha = 0.012f,
            noiseDitherEnabled = true,
            borderAlpha = 0.30f
        )
        
        // 别名
        val Card = iOSLiquidGlass
        
        val Settings = PhysicalLiquidGlassConfig(
            cornerRadius = 16.dp,
            blurRadius = 20.dp,
            refractionCenter = 0.016f,
            refractionEdgePeak = 0.040f,
            edgeRingWidthDp = 2.5.dp,
            ringFalloff = 2.8f,
            dispersionEdge = 0.004f,
            fresnelPower = 2.4f,
            fresnelIntensity = 0.50f,
            tintStrength = 0.11f,
            surfaceAlpha = 0.18f,
            primarySpecularIntensity = 0.11f,
            primarySpecularWidth = 0.80f,
            secondarySpecularIntensity = 0.70f,
            secondarySpecularSize = 0.10f,
            topRimLightAlpha = 0.42f,
            topInnerDarkAlpha = 0.09f,
            bottomInnerHighlightAlpha = 0.14f,
            cornerFadeEnabled = true,
            cornerFadeDistance = 14.dp,
            cornerFadeAmount = 0.18f,
            shadowElevation = 1.dp,
            shadowAlpha = 0.06f,
            subtleMotionEnabled = false,
            scrollResponseEnabled = true,
            noiseAlpha = 0.010f,
            noiseDitherEnabled = true,
            borderAlpha = 0.26f
        )
        
        val Button = PhysicalLiquidGlassConfig(
            cornerRadius = 100.dp,
            blurRadius = 16.dp,
            refractionCenter = 0.020f,
            refractionEdgePeak = 0.054f,
            edgeRingWidthDp = 2.5.dp,
            ringFalloff = 2.2f,
            dispersionEdge = 0.0055f,
            fresnelPower = 2.1f,
            fresnelIntensity = 0.62f,
            tintStrength = 0.12f,
            surfaceAlpha = 0.20f,
            primarySpecularIntensity = 0.17f,
            primarySpecularWidth = 0.88f,
            secondarySpecularIntensity = 0.85f,
            secondarySpecularSize = 0.11f,
            specularYBias = -0.18f,
            topRimLightAlpha = 0.50f,
            topInnerDarkAlpha = 0.11f,
            bottomInnerHighlightAlpha = 0.17f,
            cornerFadeEnabled = true,
            cornerFadeDistance = 20.dp,
            cornerFadeAmount = 0.15f,
            shadowElevation = 1.5.dp,
            shadowAlpha = 0.08f,
            subtleMotionEnabled = true,
            motionAmplitude = 0.003f,
            scrollResponseEnabled = false,
            noiseAlpha = 0.010f,
            noiseDitherEnabled = true,
            borderAlpha = 0.32f
        )
        
        val Toggle = PhysicalLiquidGlassConfig(
            cornerRadius = 100.dp,
            blurRadius = 18.dp,
            refractionCenter = 0.018f,
            refractionEdgePeak = 0.046f,
            edgeRingWidthDp = 2.dp,
            ringFalloff = 2.6f,
            dispersionEdge = 0.0045f,
            fresnelPower = 2.3f,
            fresnelIntensity = 0.54f,
            tintStrength = 0.11f,
            surfaceAlpha = 0.19f,
            primarySpecularIntensity = 0.13f,
            primarySpecularWidth = 0.84f,
            secondarySpecularIntensity = 0.75f,
            secondarySpecularSize = 0.11f,
            topRimLightAlpha = 0.44f,
            topInnerDarkAlpha = 0.10f,
            bottomInnerHighlightAlpha = 0.15f,
            cornerFadeEnabled = true,
            cornerFadeDistance = 18.dp,
            cornerFadeAmount = 0.15f,
            shadowElevation = 1.dp,
            shadowAlpha = 0.06f,
            subtleMotionEnabled = true,
            motionAmplitude = 0.0025f,
            scrollResponseEnabled = false,
            noiseAlpha = 0.010f,
            noiseDitherEnabled = true,
            borderAlpha = 0.28f
        )
    }
}

/**
 * 物理液态玻璃 AGSL 着色器 v3 - iOS 级打磨
 * 
 * 核心特性：
 * 1. 环带折射 (Ring Refraction)：边缘 0~3dp 窄带内强折射，中心几乎不动
 * 2. 主反射条 (Primary Specular)：细长带状高光，偏顶部，可见但克制
 * 3. 次级高光 (Secondary Specular)：尖锐小点高光
 * 4. 边缘门控色散：只在最外侧 1~2px 有克制的 RGB 分离
 * 5. 轻微动态：边缘有极轻微"液态活性"
 * 
 * 注意：双边界（rim/inner dark/separator/inner highlight）在 Kotlin 层最后绘制
 */
private const val LIQUID_GLASS_SURFACE_SHADER = """
    uniform float2 uSize;
    uniform float uCornerRadius;
    uniform float uEdgeThickness;
    uniform float uBulgeHeight;
    uniform float uFresnelPower;
    uniform float uFresnelIntensity;
    // 环带折射
    uniform float uRefractionCenter;
    uniform float uRefractionEdgePeak;
    uniform float uEdgeRingWidth;
    uniform float uRingFalloff;
    // 色散
    uniform float uDispersionEdge;
    // 表面
    uniform float3 uTintColor;
    uniform float uTintStrength;
    uniform float uSurfaceAlpha;
    // 主反射条
    uniform float uPrimarySpecIntensity;
    uniform float uPrimarySpecWidth;
    uniform float uPrimarySpecYPos;
    // 次级高光
    uniform float uSecondarySpecIntensity;
    uniform float uSecondarySpecSize;
    uniform float uSpecularYBias;
    // 边缘发光
    uniform float uEdgeGlowIntensity;
    uniform float uEdgeGlowWidth;
    // 动态
    uniform float uTime;
    uniform float uMotionAmplitude;
    uniform float uMotionEnabled;
    // Dither
    uniform float uDitherEnabled;
    
    // SDF 圆角矩形
    float sdRoundedRect(float2 p, float2 halfSize, float radius) {
        float2 q = abs(p) - halfSize + radius;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }
    
    // 计算 SDF 梯度
    float2 calcGradient(float2 p, float2 halfSize, float radius) {
        float eps = 1.0;
        float dx = sdRoundedRect(p + float2(eps, 0.0), halfSize, radius) 
                 - sdRoundedRect(p - float2(eps, 0.0), halfSize, radius);
        float dy = sdRoundedRect(p + float2(0.0, eps), halfSize, radius) 
                 - sdRoundedRect(p - float2(0.0, eps), halfSize, radius);
        return float2(dx, dy) / (2.0 * eps);
    }
    
    // 计算 3D 法线
    float3 calcNormal(float2 p, float2 halfSize, float radius, float sd, float thickness, float bulge, float ringMask) {
        float2 grad = calcGradient(p, halfSize, radius);
        float gradLen = length(grad);
        float2 gradNorm = gradLen > 0.001 ? grad / gradLen : float2(0.0, 0.0);
        
        float minDim = min(halfSize.x, halfSize.y);
        float edgeDist = clamp(-sd / (thickness * minDim), 0.0, 1.0);
        
        float curvature = 1.0 - smoothstep(0.0, 1.0, edgeDist);
        curvature = curvature * curvature;
        
        // 环带区域法线更强（形成厚度带）
        float normalStrength = curvature * bulge * 8.0 * (1.0 + ringMask * 1.5);
        float3 normal = float3(
            -gradNorm.x * normalStrength,
            -gradNorm.y * normalStrength,
            1.0
        );
        
        return normalize(normal);
    }
    
    // 菲涅尔效应
    float fresnel(float3 normal, float power) {
        float cosTheta = max(normal.z, 0.0);
        return pow(1.0 - cosTheta, power);
    }
    
    // 主反射条 - 细长带状（anisotropic-like）
    float primarySpecular(float2 uv, float yPos, float width, float3 normal) {
        // Y 位置衰减（高斯形态）
        float yDist = abs(uv.y - yPos);
        float yFalloff = exp(-yDist * yDist * 80.0);
        // X 方向拉伸（带状）
        float xFalloff = 1.0 - smoothstep(0.0, 0.5, abs(uv.x - 0.5) / width);
        // 法线调制
        float normalFactor = max(0.0, normal.z);
        return yFalloff * xFalloff * normalFactor;
    }
    
    // 次级高光 - 偏上、尖锐
    float secondarySpecular(float3 normal, float size, float2 uv, float yBias) {
        float3 lightDir = normalize(float3(0.35, -0.55 + yBias, 0.75));
        float3 viewDir = float3(0.0, 0.0, 1.0);
        float3 halfVec = normalize(lightDir + viewDir);
        float NdotH = max(dot(normal, halfVec), 0.0);
        float shininess = 100.0 / max(size, 0.05);
        float spec = pow(NdotH, shininess);
        float yFade = smoothstep(0.85, 0.0, uv.y);
        return spec * (0.6 + 0.4 * yFade);
    }
    
    // 简单噪声函数用于轻微动态
    float noise(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }
    
    half4 main(float2 fragCoord) {
        float2 halfSize = uSize * 0.5;
        float2 p = fragCoord - halfSize;
        float2 uv = fragCoord / uSize;
        
        // SDF
        float sd = sdRoundedRect(p, halfSize, uCornerRadius);
        
        if (sd > 0.0) {
            return half4(0.0);
        }
        
        // === 环带折射遮罩 (Ring Mask) ===
        // 距离边缘的像素距离
        float distFromEdge = -sd;
        // 环带区域：0~edgeRingWidth 内为 1，之外快速衰减
        float ringMask = 1.0 - smoothstep(0.0, uEdgeRingWidth, distFromEdge);
        ringMask = pow(ringMask, 1.0 / uRingFalloff);
        
        // 轻微动态扰动（只影响环带区域）
        float motion = 0.0;
        if (uMotionEnabled > 0.5) {
            float noiseVal = noise(uv * 3.0 + uTime * 0.5);
            motion = (noiseVal - 0.5) * uMotionAmplitude * ringMask;
        }
        
        // 计算法线（环带区域更强）
        float3 normal = calcNormal(p, halfSize, uCornerRadius, sd, uEdgeThickness, uBulgeHeight, ringMask);
        
        // 边缘因子
        float minDim = min(halfSize.x, halfSize.y);
        float edgeFactor = 1.0 - smoothstep(0.0, uEdgeThickness * minDim, distFromEdge);
        
        // === 1. 环带折射 ===
        // 边缘峰值折射，中心弱折射，通过环带遮罩混合
        float refraction = mix(uRefractionCenter, uRefractionEdgePeak, ringMask + motion);
        
        // === 2. 菲涅尔 ===
        float fresnelTerm = fresnel(normal, uFresnelPower) * uFresnelIntensity;
        float fresnelMask = smoothstep(0.12, 0.45, fresnelTerm);
        
        // === 3. 主反射条（带状，可见） ===
        float primarySpec = primarySpecular(uv, uPrimarySpecYPos, uPrimarySpecWidth, normal) * uPrimarySpecIntensity;
        
        // === 4. 次级高光（尖锐小点） ===
        float secondarySpec = secondarySpecular(normal, uSecondarySpecSize, uv, uSpecularYBias) * uSecondarySpecIntensity;
        // 边缘稍减弱
        secondarySpec *= (0.55 + 0.45 * (1.0 - edgeFactor));
        
        // === 5. 边缘门控色散 ===
        float dispersionGate = fresnelMask * ringMask;
        float dispersionAmount = dispersionGate * uDispersionEdge * 70.0;
        float3 dispersionColor = float3(
            1.0 + dispersionAmount * 0.22,
            1.0,
            1.0 + dispersionAmount * 0.16
        );
        
        // === 6. 边缘发光 ===
        float glowFactor = smoothstep(uEdgeGlowWidth, 0.0, distFromEdge) * uEdgeGlowIntensity;
        glowFactor *= (0.4 + 0.6 * ringMask);
        
        // === 7. 组合颜色 ===
        float3 tint = uTintColor * uTintStrength;
        
        float3 surfaceColor = tint;
        // 环带区域折射增强（可见的厚度带）
        surfaceColor += float3(ringMask * refraction * 1.8);
        // 菲涅尔贡献
        surfaceColor += float3(fresnelTerm * 0.25 * (0.6 + 0.4 * ringMask));
        // 主反射条
        surfaceColor += float3(primarySpec);
        // 次级高光
        surfaceColor += float3(secondarySpec);
        // 边缘发光
        surfaceColor += float3(glowFactor * 0.32);
        
        // 应用色散
        surfaceColor *= dispersionColor;
        
        // === 8. Alpha ===
        float alpha = uSurfaceAlpha;
        // 环带区域更不透明（形成厚度感）
        alpha += ringMask * 0.14;
        alpha += fresnelTerm * 0.10;
        alpha += primarySpec * 0.3;
        alpha += glowFactor * 0.12;
        
        // 抗锯齿
        float aaEdge = smoothstep(0.0, -1.5, sd);
        alpha *= aaEdge;
        
        return half4(half3(surfaceColor), half(clamp(alpha, 0.0, 1.0)));
    }
"""

/**
 * 检查设备是否支持物理液态玻璃效果
 * 需要 Android 13+ (API 33+) 支持 RuntimeShader
 */
fun isPhysicalLiquidGlassSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * 创建高频噪点纹理画刷
 * 使用 32x32 更小的 tile 避免灰边/色带
 */
@Composable
private fun rememberNoiseBrush(): Brush {
    val noise = remember { createHighFreqNoiseBitmap(32) }
    return remember(noise) {
        ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * 创建高频噪点位图
 * 更高频、更均匀，避免灰雾
 */
private fun createHighFreqNoiseBitmap(size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val random = Random(42) // 固定种子保证一致性

    for (i in pixels.indices) {
        // 更均匀的高频噪点：50% 概率为白点，其余透明
        // alpha 范围更窄，避免造成灰雾
        val isWhite = random.nextFloat() > 0.5f
        val alpha = if (isWhite) random.nextInt(15, 45) else 0
        pixels[i] = AndroidColor.argb(alpha, 255, 255, 255)
    }

    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}

// iOS separator 颜色 (自带 alpha 0x4D ≈ 30%)
private val iOSSeparatorColor = Color(0x4D3C3C43)

/**
 * 计算像素对齐的 hairline 参数
 * 确保 0.5dp 线条不抖动、不虚边、不被裁切
 * 
 * @param requestedHeightPx 请求的高度（像素）
 * @param totalHeightPx 组件总高度（像素）
 * @param density 屏幕密度
 * @return Pair(实际高度px, Y偏移调整px) 确保线条完整可见
 */
private fun calculatePixelAlignedHairline(
    requestedHeightPx: Float,
    totalHeightPx: Float,
    density: Float
): Pair<Float, Float> {
    // 1px 的物理像素
    val onePhysicalPx = 1f
    
    // 如果请求的高度 < 1px，使用 1px 但后续会降低 alpha
    val actualHeightPx = if (requestedHeightPx < onePhysicalPx) {
        onePhysicalPx
    } else {
        // 对齐到整数像素
        kotlin.math.ceil(requestedHeightPx).toFloat()
    }
    
    // Y 坐标对齐：确保线条中心在像素中心
    // 对于底部线条，需要确保 (totalHeight - lineHeight) 对齐到 0.5px 或整数
    val bottomY = totalHeightPx - actualHeightPx
    val alignedBottomY = kotlin.math.floor(bottomY) + 0.5f
    val yOffset = alignedBottomY - bottomY
    
    return Pair(actualHeightPx, yOffset)
}

/**
 * 判断是否需要使用 1px fallback（当 0.5dp 不稳定时）
 */
private fun shouldUseFallbackHairline(requestedHeightPx: Float): Boolean {
    return requestedHeightPx < 1f
}

/**
 * 物理液态玻璃容器 v3
 * 
 * 新特性：
 * - 圆角衰减：边界线在圆角处柔和过渡
 * - 滚动响应：主反射条随滚动轻微漂移
 * - 防 banding dither：噪点同时作为抗色带
 * 
 * @param scrollOffset 可选的滚动偏移量，用于驱动主反射条微动态
 */
@Composable
fun PhysicalLiquidGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    config: PhysicalLiquidGlassConfig = PhysicalLiquidGlassConfig.Default,
    enabled: Boolean = true,
    scrollOffset: Float = 0f,  // 滚动偏移量（像素）
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val actualShape = shape ?: RoundedCornerShape(config.cornerRadius)
    val cornerRadiusPx = with(density) { config.cornerRadius.toPx() }
    val edgeGlowWidthPx = with(density) { config.edgeGlowWidth.toPx() }
    val edgeRingWidthPx = with(density) { config.edgeRingWidthDp.toPx() }
    val cornerFadeDistancePx = with(density) { config.cornerFadeDistance.toPx() }
    
    // === 双边界尺寸 ===
    val topRimLightHeightPx = with(density) { config.topRimLightHeight.toPx() }
    val topInnerDarkHeightPx = with(density) { config.topInnerDarkHeight.toPx() }
    val requestedSeparatorPx = with(density) { config.bottomSeparatorHeight.toPx() }
    val useFallbackSeparator = shouldUseFallbackHairline(requestedSeparatorPx)
    val actualSeparatorHeightPx = if (useFallbackSeparator) 1f else kotlin.math.ceil(requestedSeparatorPx)
    val separatorAlphaMultiplier = if (useFallbackSeparator) 0.7f else 1f
    val bottomInnerHighlightHeightPx = with(density) { config.bottomInnerHighlightHeight.toPx() }
    val innerShadowHeightPx = with(density) { config.innerShadowHeight.toPx() }
    
    // 噪点纹理画刷
    val noiseBrush = rememberNoiseBrush()
    
    // 边框渐变
    val borderGradient = remember(config.borderAlpha) {
        Brush.sweepGradient(
            0f to Color.White.copy(alpha = config.borderAlpha * 1.15f),
            0.12f to Color.White.copy(alpha = config.borderAlpha * 0.18f),
            0.25f to Color.White.copy(alpha = config.borderAlpha * 0.72f),
            0.38f to Color.White.copy(alpha = config.borderAlpha * 0.10f),
            0.5f to Color.White.copy(alpha = config.borderAlpha * 0.52f),
            0.62f to Color.White.copy(alpha = config.borderAlpha * 0.08f),
            0.75f to Color.White.copy(alpha = config.borderAlpha * 0.62f),
            0.88f to Color.White.copy(alpha = config.borderAlpha * 0.14f),
            1f to Color.White.copy(alpha = config.borderAlpha * 1.15f)
        )
    }
    
    val shouldUseShader = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    
    if (shouldUseShader) {
        PhysicalLiquidGlassBoxImpl(
            modifier = modifier,
            shape = actualShape,
            config = config,
            cornerRadiusPx = cornerRadiusPx,
            edgeGlowWidthPx = edgeGlowWidthPx,
            edgeRingWidthPx = edgeRingWidthPx,
            cornerFadeDistancePx = cornerFadeDistancePx,
            topRimLightHeightPx = topRimLightHeightPx,
            topInnerDarkHeightPx = topInnerDarkHeightPx,
            bottomSeparatorHeightPx = actualSeparatorHeightPx,
            separatorAlphaMultiplier = separatorAlphaMultiplier,
            bottomInnerHighlightHeightPx = bottomInnerHighlightHeightPx,
            innerShadowHeightPx = innerShadowHeightPx,
            borderGradient = borderGradient,
            noiseBrush = noiseBrush,
            scrollOffset = scrollOffset,
            contentAlignment = contentAlignment,
            content = content
        )
    } else {
        FallbackGlassBox(
            modifier = modifier,
            shape = actualShape,
            config = config,
            cornerFadeDistancePx = cornerFadeDistancePx,
            topRimLightHeightPx = topRimLightHeightPx,
            topInnerDarkHeightPx = topInnerDarkHeightPx,
            bottomSeparatorHeightPx = actualSeparatorHeightPx,
            separatorAlphaMultiplier = separatorAlphaMultiplier,
            bottomInnerHighlightHeightPx = bottomInnerHighlightHeightPx,
            innerShadowHeightPx = innerShadowHeightPx,
            borderGradient = borderGradient,
            noiseBrush = noiseBrush,
            scrollOffset = scrollOffset,
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

/**
 * 物理液态玻璃实现 v3 (API 33+)
 * 
 * v3 新特性：
 * - 圆角衰减：边界线在圆角处柔和过渡
 * - 滚动响应：主反射条随滚动轻微漂移
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PhysicalLiquidGlassBoxImpl(
    modifier: Modifier,
    shape: Shape,
    config: PhysicalLiquidGlassConfig,
    cornerRadiusPx: Float,
    edgeGlowWidthPx: Float,
    edgeRingWidthPx: Float,
    cornerFadeDistancePx: Float,
    topRimLightHeightPx: Float,
    topInnerDarkHeightPx: Float,
    bottomSeparatorHeightPx: Float,
    separatorAlphaMultiplier: Float,
    bottomInnerHighlightHeightPx: Float,
    innerShadowHeightPx: Float,
    borderGradient: Brush,
    noiseBrush: Brush,
    scrollOffset: Float,
    contentAlignment: Alignment,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceShader = remember { RuntimeShader(LIQUID_GLASS_SURFACE_SHADER) }
    
    // 动画时间
    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlassMotion")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (100000 / config.motionSpeed).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    // 滚动驱动的主反射条漂移（阻尼处理）
    val scrollDrift = if (config.scrollResponseEnabled) {
        val normalizedScroll = (scrollOffset / 500f).coerceIn(-1f, 1f)
        normalizedScroll * config.scrollSpecularDrift
    } else 0f
    
    val scrollIntensityDrift = if (config.scrollResponseEnabled) {
        val absScroll = kotlin.math.abs(scrollOffset / 500f).coerceIn(0f, 1f)
        absScroll * config.scrollIntensityDrift
    } else 0f
    
    val separatorColor = remember(separatorAlphaMultiplier) {
        if (separatorAlphaMultiplier < 1f) {
            Color(0x3C, 0x3C, 0x43, (0x4D * separatorAlphaMultiplier).toInt())
        } else {
            iOSSeparatorColor
        }
    }
    
    val topInnerDarkColor = remember(config.topInnerDarkAlpha) {
        Color(0x3C / 255f, 0x3C / 255f, 0x43 / 255f, config.topInnerDarkAlpha)
    }
    
    // 圆角衰减系数计算函数
    val cornerFadeEnabled = config.cornerFadeEnabled
    val cornerFadeAmount = config.cornerFadeAmount
    
    Layout(
        content = {
            // 层 0: 背景模糊
            Box(modifier = Modifier.blur(config.blurRadius, BlurredEdgeTreatment.Unbounded))
            
            // 层 1: AGSL 玻璃表面
            Box(modifier = Modifier.graphicsLayer {
                surfaceShader.setFloatUniform("uSize", size.width, size.height)
                surfaceShader.setFloatUniform("uCornerRadius", cornerRadiusPx)
                surfaceShader.setFloatUniform("uEdgeThickness", config.edgeThickness)
                surfaceShader.setFloatUniform("uBulgeHeight", config.bulgeHeight)
                surfaceShader.setFloatUniform("uFresnelPower", config.fresnelPower)
                surfaceShader.setFloatUniform("uFresnelIntensity", config.fresnelIntensity)
                surfaceShader.setFloatUniform("uRefractionCenter", config.refractionCenter)
                surfaceShader.setFloatUniform("uRefractionEdgePeak", config.refractionEdgePeak)
                surfaceShader.setFloatUniform("uEdgeRingWidth", edgeRingWidthPx)
                surfaceShader.setFloatUniform("uRingFalloff", config.ringFalloff)
                surfaceShader.setFloatUniform("uDispersionEdge", config.dispersionEdge)
                surfaceShader.setFloatUniform("uTintColor",
                    config.tintColor.red, config.tintColor.green, config.tintColor.blue)
                surfaceShader.setFloatUniform("uTintStrength", config.tintStrength)
                surfaceShader.setFloatUniform("uSurfaceAlpha", config.surfaceAlpha)
                // 主反射条（含滚动漂移）
                surfaceShader.setFloatUniform("uPrimarySpecIntensity", 
                    config.primarySpecularIntensity + scrollIntensityDrift)
                surfaceShader.setFloatUniform("uPrimarySpecWidth", config.primarySpecularWidth)
                surfaceShader.setFloatUniform("uPrimarySpecYPos", 
                    config.primarySpecularYPos + scrollDrift)
                surfaceShader.setFloatUniform("uSecondarySpecIntensity", config.secondarySpecularIntensity)
                surfaceShader.setFloatUniform("uSecondarySpecSize", config.secondarySpecularSize)
                surfaceShader.setFloatUniform("uSpecularYBias", config.specularYBias)
                surfaceShader.setFloatUniform("uEdgeGlowIntensity", config.edgeGlowIntensity)
                surfaceShader.setFloatUniform("uEdgeGlowWidth", edgeGlowWidthPx)
                surfaceShader.setFloatUniform("uTime", if (config.subtleMotionEnabled) time else 0f)
                surfaceShader.setFloatUniform("uMotionAmplitude", config.motionAmplitude)
                surfaceShader.setFloatUniform("uMotionEnabled", if (config.subtleMotionEnabled) 1f else 0f)
                // Dither
                surfaceShader.setFloatUniform("uDitherEnabled", if (config.noiseDitherEnabled) 1f else 0f)
                
                renderEffect = RenderEffect.createShaderEffect(surfaceShader).asComposeRenderEffect()
            })
            
            // 层 2: 噪点 (兼做 dither)
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind { drawRect(noiseBrush, alpha = config.noiseAlpha) }
            })
            
            // 层 3: 内阴影（底边）
            Box(modifier = Modifier.drawWithCache {
                val gradient = Brush.verticalGradient(
                    0f to Color.Transparent, 0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = config.innerShadowAlpha),
                    startY = size.height - innerShadowHeightPx, endY = size.height
                )
                onDrawBehind {
                    if (config.innerShadowAlpha > 0f) {
                        drawRect(gradient, Offset(0f, size.height - innerShadowHeightPx),
                            Size(size.width, innerShadowHeightPx))
                    }
                }
            })
            
            // 层 4: 内容
            Box(contentAlignment = contentAlignment) { content() }
            
            // 层 5: 底部内高光（带圆角衰减）
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    val startY = h - bottomSeparatorHeightPx - bottomInnerHighlightHeightPx
                    val baseAlpha = config.bottomInnerHighlightAlpha
                    
                    // 圆角衰减渐变：两端淡出
                    val fadeStart = if (cornerFadeEnabled) cornerFadeDistancePx else 0f
                    val fadeEnd = if (cornerFadeEnabled) w - cornerFadeDistancePx else w
                    
                    val gradient = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = baseAlpha * (1f - cornerFadeAmount)),
                        (fadeStart / w).coerceIn(0f, 0.3f) to Color.White.copy(alpha = baseAlpha),
                        (fadeEnd / w).coerceIn(0.7f, 1f) to Color.White.copy(alpha = baseAlpha),
                        1f to Color.White.copy(alpha = baseAlpha * (1f - cornerFadeAmount))
                    )
                    
                    // 垂直渐变叠加
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = baseAlpha * 0.5f),
                            1f to Color.White.copy(alpha = baseAlpha)
                        ),
                        topLeft = Offset(fadeStart, startY),
                        size = Size(fadeEnd - fadeStart, bottomInnerHighlightHeightPx),
                        alpha = 1f
                    )
                }
            })
            
            // 层 6: 底部 separator（带圆角衰减）
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val alignedY = kotlin.math.floor(size.height - bottomSeparatorHeightPx) + 0.5f
                    
                    if (cornerFadeEnabled) {
                        val fadeStart = cornerFadeDistancePx
                        val fadeEnd = w - cornerFadeDistancePx
                        val fadedAlpha = separatorColor.alpha * (1f - cornerFadeAmount)
                        
                        val gradient = Brush.horizontalGradient(
                            0f to separatorColor.copy(alpha = fadedAlpha),
                            (fadeStart / w).coerceIn(0f, 0.25f) to separatorColor,
                            (fadeEnd / w).coerceIn(0.75f, 1f) to separatorColor,
                            1f to separatorColor.copy(alpha = fadedAlpha)
                        )
                        drawRect(gradient, Offset(0f, alignedY), Size(w, bottomSeparatorHeightPx))
                    } else {
                        drawRect(separatorColor, Offset(0f, alignedY), Size(w, bottomSeparatorHeightPx))
                    }
                }
            })
            
            // 层 7: 顶部内侧暗边（带圆角衰减）
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val baseAlpha = config.topInnerDarkAlpha
                    
                    if (cornerFadeEnabled) {
                        val fadeStart = cornerFadeDistancePx
                        val fadeEnd = w - cornerFadeDistancePx
                        val fadedAlpha = baseAlpha * (1f - cornerFadeAmount)
                        
                        val gradient = Brush.horizontalGradient(
                            0f to topInnerDarkColor.copy(alpha = fadedAlpha),
                            (fadeStart / w).coerceIn(0f, 0.25f) to topInnerDarkColor,
                            (fadeEnd / w).coerceIn(0.75f, 1f) to topInnerDarkColor,
                            1f to topInnerDarkColor.copy(alpha = fadedAlpha)
                        )
                        drawRect(gradient, Offset(0f, topRimLightHeightPx),
                            Size(w, topInnerDarkHeightPx))
                    } else {
                        val vGradient = Brush.verticalGradient(
                            0f to topInnerDarkColor,
                            0.5f to topInnerDarkColor.copy(alpha = baseAlpha * 0.5f),
                            1f to Color.Transparent,
                            startY = topRimLightHeightPx,
                            endY = topRimLightHeightPx + topInnerDarkHeightPx
                        )
                        drawRect(vGradient, Offset(0f, topRimLightHeightPx),
                            Size(w, topInnerDarkHeightPx))
                    }
                }
            })
            
            // 层 8: 顶部 rim light（带圆角衰减）
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val baseAlpha = config.topRimLightAlpha
                    
                    if (cornerFadeEnabled) {
                        val fadeStart = cornerFadeDistancePx
                        val fadeEnd = w - cornerFadeDistancePx
                        val fadedAlpha = baseAlpha * (1f - cornerFadeAmount)
                        
                        // 水平渐变（圆角衰减）
                        val hGradient = Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = fadedAlpha),
                            (fadeStart / w).coerceIn(0f, 0.25f) to Color.White.copy(alpha = baseAlpha),
                            (fadeEnd / w).coerceIn(0.75f, 1f) to Color.White.copy(alpha = baseAlpha),
                            1f to Color.White.copy(alpha = fadedAlpha)
                        )
                        drawRect(hGradient, Offset.Zero, Size(w, topRimLightHeightPx))
                        
                        // 叠加垂直渐变
                        val vGradient = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = baseAlpha * 0.6f),
                            0.4f to Color.White.copy(alpha = baseAlpha * 0.25f),
                            1f to Color.Transparent,
                            startY = 0f, endY = topRimLightHeightPx
                        )
                        drawRect(vGradient, Offset(fadeStart, 0f), 
                            Size(fadeEnd - fadeStart, topRimLightHeightPx))
                    } else {
                        val gradient = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = baseAlpha),
                            0.3f to Color.White.copy(alpha = baseAlpha * 0.5f),
                            0.65f to Color.White.copy(alpha = baseAlpha * 0.18f),
                            1f to Color.Transparent,
                            startY = 0f, endY = topRimLightHeightPx
                        )
                        drawRect(gradient, Offset.Zero, Size(w, topRimLightHeightPx))
                    }
                }
            })
        },
        modifier = modifier
            .shadow(
                elevation = config.shadowElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = config.shadowAlpha),
                spotColor = Color.Black.copy(alpha = config.shadowAlpha)
            )
            .clip(shape)
            .border(0.5.dp, borderGradient, shape)
    ) { measurables, constraints ->
        val contentPlaceable = measurables[4].measure(constraints)
        val w = contentPlaceable.width
        val h = contentPlaceable.height
        val fixed = Constraints.fixed(w, h)
        
        val placeables = measurables.mapIndexed { i, m ->
            if (i == 4) contentPlaceable else m.measure(fixed)
        }
        
        layout(w, h) { placeables.forEach { it.place(0, 0) } }
    }
}

/**
 * 降级版本 v3: 使用 Canvas 绘制 (API < 33)
 * 支持圆角衰减和滚动响应
 */
@Composable
private fun FallbackGlassBox(
    modifier: Modifier,
    shape: Shape,
    config: PhysicalLiquidGlassConfig,
    cornerFadeDistancePx: Float,
    topRimLightHeightPx: Float,
    topInnerDarkHeightPx: Float,
    bottomSeparatorHeightPx: Float,
    separatorAlphaMultiplier: Float,
    bottomInnerHighlightHeightPx: Float,
    innerShadowHeightPx: Float,
    borderGradient: Brush,
    noiseBrush: Brush,
    scrollOffset: Float,
    contentAlignment: Alignment,
    content: @Composable BoxScope.() -> Unit
) {
    val separatorColor = remember(separatorAlphaMultiplier) {
        if (separatorAlphaMultiplier < 1f) {
            Color(0x3C / 255f, 0x3C / 255f, 0x43 / 255f, (0x4D / 255f) * separatorAlphaMultiplier)
        } else {
            iOSSeparatorColor
        }
    }
    
    val topInnerDarkColor = remember(config.topInnerDarkAlpha) {
        Color(0x3C / 255f, 0x3C / 255f, 0x43 / 255f, config.topInnerDarkAlpha)
    }
    
    val cornerFadeEnabled = config.cornerFadeEnabled
    val cornerFadeAmount = config.cornerFadeAmount
    
    Layout(
        content = {
            // 层 0: 背景模糊
            Box(modifier = Modifier.blur(config.blurRadius, BlurredEdgeTreatment.Unbounded))
            
            // 层 1: 玻璃表面 (Canvas)
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind { drawPhysicalGlassEffect(size.width, size.height, config, scrollOffset) }
            })
            
            // 层 2: 噪点
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind { drawRect(noiseBrush, alpha = config.noiseAlpha) }
            })
            
            // 层 3: 内阴影
            Box(modifier = Modifier.drawWithCache {
                val gradient = Brush.verticalGradient(
                    0f to Color.Transparent, 0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = config.innerShadowAlpha),
                    startY = size.height - innerShadowHeightPx, endY = size.height
                )
                onDrawBehind {
                    if (config.innerShadowAlpha > 0f) {
                        drawRect(gradient, Offset(0f, size.height - innerShadowHeightPx),
                            Size(size.width, innerShadowHeightPx))
                    }
                }
            })
            
            // 层 4: 内容
            Box(contentAlignment = contentAlignment) { content() }
            
            // 层 5-8: 带圆角衰减的边界线（简化版）
            Box(modifier = Modifier.drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    val fadeStart = if (cornerFadeEnabled) cornerFadeDistancePx else 0f
                    val fadeEnd = if (cornerFadeEnabled) w - cornerFadeDistancePx else w
                    
                    // 底部内高光
                    val hlAlpha = config.bottomInnerHighlightAlpha
                    val hlGradient = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = hlAlpha * (1f - cornerFadeAmount)),
                        0.15f to Color.White.copy(alpha = hlAlpha),
                        0.85f to Color.White.copy(alpha = hlAlpha),
                        1f to Color.White.copy(alpha = hlAlpha * (1f - cornerFadeAmount))
                    )
                    drawRect(hlGradient, 
                        Offset(0f, h - bottomSeparatorHeightPx - bottomInnerHighlightHeightPx),
                        Size(w, bottomInnerHighlightHeightPx))
                    
                    // 底部 separator
                    val alignedY = kotlin.math.floor(h - bottomSeparatorHeightPx) + 0.5f
                    val sepGradient = Brush.horizontalGradient(
                        0f to separatorColor.copy(alpha = separatorColor.alpha * (1f - cornerFadeAmount)),
                        0.15f to separatorColor,
                        0.85f to separatorColor,
                        1f to separatorColor.copy(alpha = separatorColor.alpha * (1f - cornerFadeAmount))
                    )
                    drawRect(sepGradient, Offset(0f, alignedY), Size(w, bottomSeparatorHeightPx))
                    
                    // 顶部内侧暗边
                    val darkAlpha = config.topInnerDarkAlpha
                    val darkGradient = Brush.horizontalGradient(
                        0f to topInnerDarkColor.copy(alpha = darkAlpha * (1f - cornerFadeAmount)),
                        0.15f to topInnerDarkColor,
                        0.85f to topInnerDarkColor,
                        1f to topInnerDarkColor.copy(alpha = darkAlpha * (1f - cornerFadeAmount))
                    )
                    drawRect(darkGradient, Offset(0f, topRimLightHeightPx),
                        Size(w, topInnerDarkHeightPx))
                    
                    // 顶部 rim light
                    val rimAlpha = config.topRimLightAlpha
                    val rimGradient = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = rimAlpha * (1f - cornerFadeAmount)),
                        0.15f to Color.White.copy(alpha = rimAlpha),
                        0.85f to Color.White.copy(alpha = rimAlpha),
                        1f to Color.White.copy(alpha = rimAlpha * (1f - cornerFadeAmount))
                    )
                    drawRect(rimGradient, Offset.Zero, Size(w, topRimLightHeightPx))
                }
            })
        },
        modifier = modifier
            .shadow(
                elevation = config.shadowElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = config.shadowAlpha),
                spotColor = Color.Black.copy(alpha = config.shadowAlpha)
            )
            .clip(shape)
            .border(0.5.dp, borderGradient, shape)
    ) { measurables, constraints ->
        val contentPlaceable = measurables[4].measure(constraints)
        val w = contentPlaceable.width
        val h = contentPlaceable.height
        val fixed = Constraints.fixed(w, h)
        
        // Fallback 简化为 6 层
        val placeables = measurables.mapIndexed { i, m ->
            if (i == 4) contentPlaceable else m.measure(fixed)
        }
        
        layout(w, h) { placeables.forEach { it.place(0, 0) } }
    }
}

/**
 * 使用 Canvas 绘制物理玻璃效果 v3（降级版）
 * 
 * 特性：
 * - 环带折射：边缘窄带内强折射，中心清透
 * - 主反射条：细长带状，偏顶部，含滚动漂移
 * - 次级高光：尖锐小点
 * - 边缘门控色散
 */
private fun DrawScope.drawPhysicalGlassEffect(
    width: Float,
    height: Float,
    config: PhysicalLiquidGlassConfig,
    scrollOffset: Float = 0f
) {
    val centerX = width / 2f
    val centerY = height / 2f
    val minDim = min(width, height)
    
    // 滚动漂移
    val scrollDrift = if (config.scrollResponseEnabled) {
        (scrollOffset / 500f).coerceIn(-1f, 1f) * config.scrollSpecularDrift
    } else 0f
    
    // 1. 玻璃色调底层（很轻）
    drawRect(
        color = config.tintColor.copy(alpha = config.surfaceAlpha),
        size = Size(width, height)
    )
    
    // 2. 环带折射效果（边缘窄带更亮，形成厚度感）
    val ringGradient = Brush.radialGradient(
        0f to Color.Transparent,
        0.40f to Color.Transparent,
        0.75f to Color.White.copy(alpha = config.refractionEdgePeak * 0.8f),
        0.88f to Color.White.copy(alpha = config.refractionEdgePeak * 2.5f),
        0.96f to Color.White.copy(alpha = config.refractionEdgePeak * 4.0f),
        1f to Color.White.copy(alpha = config.refractionEdgePeak * 5.0f),
        center = Offset(centerX, centerY),
        radius = minDim * 0.65f
    )
    drawRect(brush = ringGradient, size = Size(width, height))
    
    // 3. 主反射条（细长带状，偏顶部，含滚动漂移）
    val primarySpecY = height * (config.primarySpecularYPos + scrollDrift)
    val primarySpecHeight = height * 0.08f
    val primarySpecGradient = Brush.verticalGradient(
        0f to Color.Transparent,
        0.3f to Color.White.copy(alpha = config.primarySpecularIntensity * 0.4f),
        0.5f to Color.White.copy(alpha = config.primarySpecularIntensity),
        0.7f to Color.White.copy(alpha = config.primarySpecularIntensity * 0.4f),
        1f to Color.Transparent,
        startY = primarySpecY - primarySpecHeight / 2,
        endY = primarySpecY + primarySpecHeight / 2
    )
    val specWidth = width * config.primarySpecularWidth
    val specLeft = (width - specWidth) / 2
    drawRect(
        brush = primarySpecGradient,
        topLeft = Offset(specLeft, (primarySpecY - primarySpecHeight / 2).coerceAtLeast(0f)),
        size = Size(specWidth, primarySpecHeight)
    )
    
    // 4. 次级高光（尖锐小点，偏上）
    val secondarySpecEndY = height * (0.30f + config.specularYBias * 0.4f)
    val secondarySpecGradient = Brush.verticalGradient(
        0f to Color.White.copy(alpha = config.secondarySpecularIntensity * 0.45f),
        0.15f to Color.White.copy(alpha = config.secondarySpecularIntensity * 0.20f),
        0.3f to Color.White.copy(alpha = config.secondarySpecularIntensity * 0.06f),
        0.5f to Color.Transparent,
        1f to Color.Transparent,
        startY = 0f,
        endY = secondarySpecEndY.coerceAtLeast(height * 0.12f)
    )
    drawRect(
        brush = secondarySpecGradient, 
        size = Size(width, secondarySpecEndY.coerceAtLeast(height * 0.12f))
    )
    
    // 5. 边缘门控色散（只在最外侧极薄区域）
    val dispersion = config.dispersionEdge
    // 红色边缘
    val redEdge = Brush.radialGradient(
        0f to Color.Transparent,
        0.92f to Color.Transparent,
        0.97f to Color.Red.copy(alpha = dispersion * 5f),
        1f to Color.Red.copy(alpha = dispersion * 9f),
        center = Offset(centerX, centerY),
        radius = minDim * 0.55f
    )
    drawRect(brush = redEdge, size = Size(width, height))
    
    // 蓝色边缘
    val blueEdge = Brush.radialGradient(
        0f to Color.Transparent,
        0.90f to Color.Transparent,
        0.95f to Color.Blue.copy(alpha = dispersion * 4f),
        1f to Color.Blue.copy(alpha = dispersion * 7f),
        center = Offset(centerX, centerY),
        radius = minDim * 0.52f
    )
    drawRect(brush = blueEdge, size = Size(width, height))
    
    // 6. 边缘发光
    val edgeGlow = Brush.radialGradient(
        0f to Color.Transparent,
        0.82f to Color.Transparent,
        0.94f to Color.White.copy(alpha = config.edgeGlowIntensity * 0.10f),
        1f to Color.White.copy(alpha = config.edgeGlowIntensity * 0.18f),
        center = Offset(centerX, centerY),
        radius = minDim * 0.6f
    )
    drawRect(brush = edgeGlow, size = Size(width, height))
}

// ============================================================
// 便捷组件
// ============================================================

@Composable
fun PhysicalLiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    PhysicalLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = PhysicalLiquidGlassConfig.Card,
        enabled = enabled,
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun PhysicalLiquidGlassSettings(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    PhysicalLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = PhysicalLiquidGlassConfig.Settings,
        enabled = enabled,
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun PhysicalLiquidGlassButton(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    PhysicalLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = PhysicalLiquidGlassConfig.Button,
        enabled = enabled,
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun PhysicalLiquidGlassToggle(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    PhysicalLiquidGlassBox(
        modifier = modifier,
        shape = shape,
        config = PhysicalLiquidGlassConfig.Toggle,
        enabled = enabled,
        contentAlignment = contentAlignment,
        content = content
    )
}
