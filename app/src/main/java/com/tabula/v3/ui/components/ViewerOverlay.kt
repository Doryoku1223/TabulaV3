package com.tabula.v3.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.CoilSetup
import kotlinx.coroutines.launch

/**
 * 查看器覆盖层状态
 */
data class ViewerState(
    val image: ImageFile,
    val sourceRect: SourceRect
)

/**
 * 源卡片位置信息（用于容器变换动画）
 */
data class SourceRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val cornerRadius: Float = 16f
)

/**
 * 沉浸式图片查看器覆盖层 - 苹果级丝滑动画
 *
 * 动画流程：
 * 1. 入场：从卡片位置展开到屏幕中央（原比例显示）
 * 2. 交互：双指缩放 + 双击放大（位置固定，不可拖动）
 * 3. 出场：从中央收缩回卡片原位置
 *
 * @param viewerState 查看器状态（图片 + 源位置）
 * @param onDismiss 关闭回调
 * @param modifier 外部修饰符
 */
@Composable
fun ViewerOverlay(
    viewerState: ViewerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val imageLoader = CoilSetup.getImageLoader(context)

    // 屏幕尺寸 (像素)
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 源位置信息
    val source = viewerState.sourceRect
    val image = viewerState.image

    // 计算目标位置（居中显示，保持原比例）
    val imageAspectRatio = if (image.height > 0) image.width.toFloat() / image.height else 1f

    // 计算适配屏幕的目标尺寸（保持原比例，留出边距）
    val maxWidth = screenWidthPx * 0.95f
    val maxHeight = screenHeightPx * 0.80f

    val (targetWidth, targetHeight) = if (imageAspectRatio > maxWidth / maxHeight) {
        // 宽图：以宽度为准
        maxWidth to (maxWidth / imageAspectRatio)
    } else {
        // 高图：以高度为准
        (maxHeight * imageAspectRatio) to maxHeight
    }

    // 目标位置（屏幕中心）
    val targetX = (screenWidthPx - targetWidth) / 2
    val targetY = (screenHeightPx - targetHeight) / 2

    // ========== 动画状态 ==========
    // 0 = 卡片位置，1 = 展开位置
    val animProgress = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }

    // 用户缩放（位置固定，仅缩放）
    var userScale by remember { mutableFloatStateOf(1f) }

    // 缩放手势状态（忽略平移）
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        val newScale = (userScale * zoomChange).coerceIn(1f, 4f)
        userScale = newScale
    }

    // 入场动画 - 苹果级弹簧
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.8f,  // 轻微弹性
                stiffness = 300f      // 适中刚度
            )
        )
    }

    /**
     * 执行退出动画
     */
    fun exitViewer() {
        if (isExiting) return
        isExiting = true

        scope.launch {
            // 先平滑重置缩放
            userScale = 1f

            // 执行收缩动画
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 350f
                )
            )
            onDismiss()
        }
    }

    // ========== 计算动画插值 ==========
    val progress = animProgress.value

    // 背景透明度
    val backgroundAlpha = progress * 0.95f

    // 位置插值
    val currentX = lerp(source.x, targetX, progress)
    val currentY = lerp(source.y, targetY, progress)

    // 尺寸插值
    val currentWidth = lerp(source.width, targetWidth, progress)
    val currentHeight = lerp(source.height, targetHeight, progress)

    // 圆角插值
    val currentCornerRadius = lerp(source.cornerRadius, 4f, progress)

    // 转换为 dp
    val currentWidthDp = with(density) { currentWidth.toDp() }
    val currentHeightDp = with(density) { currentHeight.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                // 点击背景退出
                detectTapGestures(onTap = { exitViewer() })
            },
        contentAlignment = Alignment.TopStart
    ) {
        // 图片容器
        Box(
            modifier = Modifier
                .graphicsLayer {
                    // 定位
                    translationX = currentX
                    translationY = currentY
                    // 用户缩放（围绕中心）
                    scaleX = userScale
                    scaleY = userScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .size(currentWidthDp, currentHeightDp)
                .clip(RoundedCornerShape(currentCornerRadius.dp))
                .background(Color.Black)
                .transformable(state = transformableState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { exitViewer() },
                        onDoubleTap = {
                            // 双击切换缩放
                            userScale = if (userScale > 1.5f) 1f else 2.5f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 高清图片 (ARGB_8888)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.uri)
                    .crossfade(100)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .build(),
                contentDescription = image.displayName,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 线性插值
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}
