package com.tabula.v3.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Genie Effect 覆盖层 - 标准macOS神灯吸入效果
 * 
 * 物理本质：整张图片在被吸入瓶口（目标点）的过程中，
 * 高度被压缩，宽度随着靠近瓶口呈指数级收缩
 */
@Composable
fun GenieEffectOverlay(
    bitmap: Bitmap?,
    sourceBounds: Rect,
    targetX: Float,
    targetY: Float,
    progress: Float,
    screenHeight: Float = 2000f,
    modifier: Modifier = Modifier
) {
    if (bitmap == null || progress <= 0f) return
    
    // 增加网格密度，让曲线更平滑
    val meshCols = 16
    val meshRows = 32
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val verts = calculateGenieVertices(
            sourceLeft = sourceBounds.left,
            sourceTop = sourceBounds.top,
            sourceWidth = sourceBounds.width,
            sourceHeight = sourceBounds.height,
            destX = targetX,
            destY = targetY,
            progress = progress.coerceIn(0f, 1f),
            meshCols = meshCols,
            meshRows = meshRows,
            screenHeight = screenHeight
        )
        
        // 透明度：最后20%开始淡出
        val alpha = if (progress > 0.8f) {
            1f - (progress - 0.8f) / 0.2f
        } else {
            1f
        }
        
        val paint = Paint().apply {
            this.alpha = (alpha * 255).toInt()
            isFilterBitmap = true
            isAntiAlias = true
        }
        
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(
                bitmap,
                meshCols,
                meshRows,
                verts,
                0,
                null,
                0,
                paint
            )
        }
    }
}

/**
 * 计算 macOS 风格神奇效果的网格顶点
 * 
 * 基于 https://daniate.github.io/2021/07/27/细说如何完美实现macOS中的神奇效果/
 * 
 * 极致丝滑版本：
 * 1. 两阶段动画平滑过渡
 * 2. 使用高阶缓动函数
 * 3. 曲线形变更自然
 */
private fun calculateGenieVertices(
    sourceLeft: Float,
    sourceTop: Float,
    sourceWidth: Float,
    sourceHeight: Float,
    destX: Float,
    destY: Float,
    progress: Float,
    meshCols: Int,
    meshRows: Int,
    screenHeight: Float
): FloatArray {
    val vertexCount = (meshCols + 1) * (meshRows + 1)
    val verts = FloatArray(vertexCount * 2)
    
    val originalCenterX = sourceLeft + sourceWidth / 2
    
    // ========== 平滑的两阶段动画 ==========
    // 使用 smoothstep 让两阶段之间无缝过渡
    val curveRatio = 0.4f
    
    // 曲线收窄进度（使用 smoothstep 缓动）
    val rawCurveProgress = (progress / curveRatio).coerceIn(0f, 1f)
    val curveProgress = smoothstep(rawCurveProgress)
    
    // 向下平移进度（平滑启动）
    val rawTranslationProgress = ((progress - curveRatio * 0.5f) / (1f - curveRatio * 0.5f)).coerceIn(0f, 1f)
    val translationProgress = smoothstep(rawTranslationProgress)
    
    // ========== 收窄参数 ==========
    val targetOffsetX = (destX - originalCenterX) / sourceWidth
    val clampedOffset = targetOffsetX.coerceIn(-0.48f, 0.48f)
    val finalCenterNorm = 0.5f + clampedOffset
    
    var index = 0
    
    for (row in 0..meshRows) {
        val rowRatio = row.toFloat() / meshRows
        val y = 1f - rowRatio  // 反转坐标系
        
        // ========== 1. 优化的正弦曲线 ==========
        // 使用更平滑的曲线系数
        val leftMax = curveProgress * finalCenterNorm
        val leftD = leftMax / 2f
        val leftA = leftD
        // 使用 smoothed sin 让曲线更自然
        val sinValueLeft = sin(PI.toFloat() * y + PI.toFloat() / 2f)
        val leftNorm = leftA * sinValueLeft + leftD
        
        val rightMin = 1f - curveProgress * (1f - finalCenterNorm)
        val rightD = (rightMin + 1f) / 2f
        val rightA = 1f - rightD
        val sinValueRight = sin(PI.toFloat() * y - PI.toFloat() / 2f)
        val rightNorm = rightA * sinValueRight + rightD
        
        var leftX = sourceLeft + leftNorm * sourceWidth
        var rightX = sourceLeft + rightNorm * sourceWidth
        
        // ========== 2. 平滑的向下吸收 ==========
        val originalY = sourceTop + sourceHeight * rowRatio
        val totalTranslation = destY - sourceTop - sourceHeight
        
        // 使用非线性的平移，底部先到达
        val rowTranslationFactor = 1f + rowRatio * 0.3f  // 底部移动更快
        val effectiveTranslation = translationProgress * totalTranslation * rowTranslationFactor
        var meshY = originalY + effectiveTranslation.coerceAtMost(destY - originalY)
        
        // 平滑地收缩已到达目标的行
        if (meshY >= destY - 1f) {
            meshY = destY
            // 已吸收的行宽度收缩到0
            val absorptionProgress = ((meshY - (originalY + effectiveTranslation * 0.8f)) / (destY - originalY)).coerceIn(0f, 1f)
            val shrinkFactor = 1f - smoothstep(absorptionProgress)
            val centerX = (leftX + rightX) / 2f
            leftX = lerp(centerX, leftX, shrinkFactor)
            rightX = lerp(centerX, rightX, shrinkFactor)
        }
        
        // ========== 3. 极致平滑的最终收缩 ==========
        if (progress > 0.85f) {
            val squeeze = smoothstep((progress - 0.85f) / 0.15f)
            val centerX = (leftX + rightX) / 2f
            val currentWidth = rightX - leftX
            val targetWidth = currentWidth * (1f - squeeze)
            leftX = lerp(centerX - targetWidth / 2f, destX, squeeze.pow(1.5f))
            rightX = lerp(centerX + targetWidth / 2f, destX, squeeze.pow(1.5f))
            meshY = lerp(meshY, destY, squeeze.pow(1.2f))
        }
        
        // ========== 4. 生成顶点 ==========
        for (col in 0..meshCols) {
            val colRatio = col.toFloat() / meshCols
            val x = if (progress >= 0.995f) destX else lerp(leftX, rightX, colRatio)
            val finalY = if (progress >= 0.995f) destY else meshY
            
            verts[index++] = x
            verts[index++] = finalY
        }
    }
    
    return verts
}

/**
 * Smoothstep 缓动函数 - 极致平滑的 S 形曲线
 * 比 easeInOutQuad 更平滑，没有突变
 */
private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * 更平滑的 Smootherstep（Ken Perlin 改进版）
 */
private fun smootherstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

/**
 * 线性插值
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

/**
 * 缓动函数 - 加速（先慢后快）
 */
private fun easeInQuad(t: Float): Float = t * t

/**
 * 缓动函数 - 先加速后减速（平滑过渡）
 */
private fun easeInOutQuad(t: Float): Float {
    return if (t < 0.5f) {
        2f * t * t
    } else {
        1f - (-2f * t + 2f).pow(2f) / 2f
    }
}

/**
 * Genie动画控制器
 */
class GenieAnimationController {
    var isAnimating by mutableStateOf(false)
        private set
    
    var progress by mutableFloatStateOf(0f)
        private set
    
    var targetX by mutableFloatStateOf(0f)
        private set
    
    var targetY by mutableFloatStateOf(0f)
        private set
    
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    
    var sourceBounds by mutableStateOf(Rect.Zero)
        private set
    
    var screenHeight by mutableFloatStateOf(2000f)
        private set
    
    private val animatable = Animatable(0f)
    
    suspend fun startAnimation(
        bitmap: Bitmap,
        sourceBounds: Rect,
        targetX: Float,
        targetY: Float,
        screenHeight: Float = 2000f,
        durationMs: Int = 380,  // 稍快但丝滑
        onComplete: () -> Unit = {}
    ) {
        this.bitmap = bitmap
        this.sourceBounds = sourceBounds
        this.targetX = targetX
        this.targetY = targetY
        this.screenHeight = screenHeight
        this.isAnimating = true
        this.progress = 0f
        
        animatable.snapTo(0f)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                // iOS/macOS 风格的缓动曲线 - 快速启动，优雅减速
                easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
            )
        ) {
            progress = value
        }
        
        onComplete()
        reset()
    }
    
    fun reset() {
        isAnimating = false
        progress = 0f
        bitmap?.recycle()
        bitmap = null
    }
}

@Composable
fun rememberGenieAnimationController(): GenieAnimationController {
    return remember { GenieAnimationController() }
}

/**
 * 从ImageFile创建用于Genie效果的缩略图Bitmap
 * 使用较小的目标尺寸以提高加载速度
 */
suspend fun createGenieBitmap(
    context: android.content.Context,
    imageUri: android.net.Uri,
    width: Int,
    height: Int
): Bitmap? {
    return try {
        // 限制最大尺寸为 400px，提高加载速度
        val maxSize = 400
        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height, 1f)
        val targetWidth = (width * scale).toInt().coerceAtLeast(100)
        val targetHeight = (height * scale).toInt().coerceAtLeast(100)
        
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, options)
        }
        
        // 使用更激进的采样以提高速度
        val sampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565  // 使用更小的颜色深度
        }
        
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, decodeOptions)
        }?.let { bitmap ->
            // 缩放到目标尺寸
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        }
    } catch (e: Exception) {
        null
    }
}

private fun calculateInSampleSize(
    options: android.graphics.BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}
