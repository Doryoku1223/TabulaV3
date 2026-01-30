package com.tabula.v3.ui.components

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.util.findActivity
import com.tabula.v3.ui.util.rememberImageFeatures
import kotlinx.coroutines.delay
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
    modifier: Modifier = Modifier,
    showHdr: Boolean = false,
    showMotionPhoto: Boolean = false,
    playMotionSound: Boolean = true,
    motionSoundVolume: Int = 100
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val imageLoader = CoilSetup.getImageLoader(context)

    // 容器尺寸（由 BoxWithConstraints 提供，在下方计算）
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    // 源位置信息 - 使用remember保存初始值，确保退出动画时使用正确的位置
    val initialSourceRect = remember { viewerState.sourceRect }
    val image = viewerState.image
    
    // 检查源位置是否有效（宽高大于0）
    val isSourceValid = initialSourceRect.width > 0f && initialSourceRect.height > 0f
    
    // 如果源位置无效，使用屏幕中心作为fallback（在容器尺寸确定后计算）
    val source = if (isSourceValid) {
        initialSourceRect
    } else {
        // 无效时使用屏幕中心作为源位置（会产生居中缩放效果）
        SourceRect(
            x = containerWidthPx / 2f - 100f,
            y = containerHeightPx / 2f - 150f,
            width = 200f,
            height = 300f,
            cornerRadius = 16f
        )
    }
    val features = rememberImageFeatures(
        image = image,
        enableHdr = showHdr,
        enableMotion = showMotionPhoto
    )
    val isHdr = showHdr && (features?.isHdr == true)
    val motionInfo = if (showMotionPhoto) features?.motionPhotoInfo else null
    var isHdrComparePressed by remember { mutableStateOf(false) }
    var isLivePressed by remember { mutableStateOf(false) }
    var isPressing by remember { mutableStateOf(false) }
    var pressStartedAt by remember { mutableLongStateOf(0L) }
    val pressDelayMs = 80L
    
    // 高清图加载状态
    var isHdImageLoaded by remember { mutableStateOf(false) }
    
    // 超高清图加载状态（用于放大时）
    var isUltraHdLoaded by remember { mutableStateOf(false) }
    var shouldLoadUltraHd by remember { mutableStateOf(false) }
    
    // 缩略图缓存键（与网格一致，确保立即显示）
    val thumbnailCacheKey = remember(image.id) { "album_grid_${image.id}" }
    val sysThumbnailCacheKey = remember(image.id) { "sys_grid_${image.id}" }

    // 计算目标位置（居中显示，保持原比例）
    // 使用 actualWidth/actualHeight 考虑 EXIF 旋转，确保收缩动画位置正确
    val imageAspectRatio = if (image.actualHeight > 0) image.actualWidth.toFloat() / image.actualHeight else 1f

    // 计算适配屏幕的目标尺寸（保持原比例，在容器内留出边距）
    val maxWidth = containerWidthPx * 0.95f
    val maxHeight = containerHeightPx * 0.90f  // 在容器内留出边距

    val (targetWidth, targetHeight) = if (containerWidthPx > 0f && containerHeightPx > 0f && maxHeight > 0f) {
        if (imageAspectRatio > maxWidth / maxHeight) {
            // 宽图：以宽度为准
            maxWidth to (maxWidth / imageAspectRatio)
        } else {
            // 高图：以高度为准
            (maxHeight * imageAspectRatio) to maxHeight
        }
    } else {
        0f to 0f
    }

    // 目标位置（在整个容器内居中）
    val targetX = (containerWidthPx - targetWidth) / 2
    val targetY = (containerHeightPx - targetHeight) / 2

    // ========== 动画状态 ==========
    // 0 = 卡片位置，1 = 展开位置
    val animProgress = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }

    // 用户缩放 + 拖拽（使用普通状态，保证即时响应）
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    var contentSize by remember { mutableStateOf(Size.Zero) }
    
    // 跟踪最近的缩放/拖动时间，防止缩放结束后误触发单击退出
    var lastTransformTime by remember { mutableLongStateOf(0L) }
    
    // 当用户放大超过 1.5x 时，触发加载原图
    LaunchedEffect(userScale) {
        if (userScale > 1.5f && !shouldLoadUltraHd) {
            shouldLoadUltraHd = true
        }
    }

    val minScale = 1f
    val maxScale = 8f
    val doubleTapScale = 3f

    fun clampOffset(
        offset: Offset,
        scale: Float,
        size: Size
    ): Offset {
        if (size.width <= 0f || size.height <= 0f) return Offset.Zero
        val maxOffsetX = ((size.width * scale) - size.width).coerceAtLeast(0f) / 2f
        val maxOffsetY = ((size.height * scale) - size.height).coerceAtLeast(0f) / 2f
        return Offset(
            x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    fun applyScaleAndOffset(newScale: Float, newOffset: Offset, size: Size) {
        val clampedScale = newScale.coerceIn(minScale, maxScale)
        if (size.width <= 0f || size.height <= 0f) {
            userScale = clampedScale
            userOffset = Offset.Zero
            return
        }
        if (clampedScale <= minScale) {
            userScale = minScale
            userOffset = Offset.Zero
            return
        }
        userScale = clampedScale
        userOffset = clampOffset(newOffset, clampedScale, size)
    }

    // 入场动画 - 优化为更丝滑的参数
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.9f,  // 更高阻尼，减少弹跳
                stiffness = 500f      // 更高刚度，响应更快
            )
        )
    }

    val activity = context.findActivity()
    val window = activity?.window
    val originalColorMode = remember(window) { window?.colorMode ?: ActivityInfo.COLOR_MODE_DEFAULT }
    LaunchedEffect(isPressing, isHdr, motionInfo) {
        if (!isPressing) {
            isHdrComparePressed = false
            isLivePressed = false
            return@LaunchedEffect
        }
        delay(pressDelayMs)
        if (isPressing) {
            if (isHdr) {
                isHdrComparePressed = true
            }
            if (motionInfo != null) {
                isLivePressed = true
            }
        }
    }

    val desiredColorMode = when {
        isHdr && !isHdrComparePressed -> ActivityInfo.COLOR_MODE_HDR
        isHdr && isHdrComparePressed -> ActivityInfo.COLOR_MODE_DEFAULT
        else -> originalColorMode
    }

    LaunchedEffect(desiredColorMode, window) {
        if (window != null) {
            window.colorMode = desiredColorMode
        }
    }

    DisposableEffect(window) {
        onDispose {
            if (window != null) {
                window.colorMode = originalColorMode
            }
        }
    }

    /**
     * 执行退出动画 - 优化版本，并行动画更丝滑
     */
    fun exitViewer() {
        if (isExiting) return
        isExiting = true

        // 重置缩放和偏移
        userScale = 1f
        userOffset = Offset.Zero
        
        scope.launch {
            // 主收缩动画
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.92f,  // 更高阻尼，更平滑
                    stiffness = 600f       // 更高刚度，响应更快
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
    val currentWidth = lerp(source.width, targetWidth, progress).coerceAtLeast(0f)
    val currentHeight = lerp(source.height, targetHeight, progress).coerceAtLeast(0f)

    // 圆角插值 (防止负数导致 Crash)
    val currentCornerRadius = lerp(source.cornerRadius, 4f, progress).coerceAtLeast(0f)

    // 转换为 dp
    val currentWidthDp = with(density) { currentWidth.toDp() }
    val currentHeightDp = with(density) { currentHeight.toDp() }

    LaunchedEffect(contentSize, userScale) {
        if (contentSize.width <= 0f || contentSize.height <= 0f) return@LaunchedEffect
        if (userScale <= minScale) {
            userOffset = Offset.Zero
        } else {
            userOffset = clampOffset(userOffset, userScale, contentSize)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                // 点击背景退出
                detectTapGestures(onTap = { exitViewer() })
            },
        contentAlignment = Alignment.TopStart
    ) {
        // 获取实际容器尺寸（在整个屏幕内居中）
        val actualWidth = constraints.maxWidth.toFloat()
        val actualHeight = constraints.maxHeight.toFloat()
        
        // 更新容器尺寸
        LaunchedEffect(actualWidth, actualHeight) {
            containerWidthPx = actualWidth
            containerHeightPx = actualHeight
        }
        
        // 图片容器
        Box(
            modifier = Modifier
                .graphicsLayer {
 // TODO
                    translationX = currentX + userOffset.x
                    translationY = currentY + userOffset.y
 // TODO
                    scaleX = userScale
                    scaleY = userScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .size(currentWidthDp, currentHeightDp)
                .onSizeChanged { size ->
                    contentSize = Size(size.width.toFloat(), size.height.toFloat())
                }
                .clip(RoundedCornerShape(currentCornerRadius.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // 记录缩放/拖动时间，防止误触发单击退出
                        lastTransformTime = SystemClock.uptimeMillis()
                        
                        val size = contentSize
                        val oldScale = userScale
                        val newScale = (userScale * zoom).coerceIn(minScale, maxScale)
                        if (size.width <= 0f || size.height <= 0f) {
                            userScale = newScale
                            userOffset = Offset.Zero
                            return@detectTransformGestures
                        }
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val scaleChange = if (oldScale == 0f) 1f else newScale / oldScale
                        // 拖动倍率与缩放比例成正比，放大越多拖动越快
                        val panMultiplier = oldScale.coerceIn(1f, maxScale)
                        val adjustedPan = Offset(pan.x * panMultiplier, pan.y * panMultiplier)
                        val newOffset = (userOffset + adjustedPan) + (centroid - center) * (1 - scaleChange)
                        applyScaleAndOffset(newScale, newOffset, size)
                    }
                }
                .pointerInput(isHdr, motionInfo) {
                    detectTapGestures(
                        onTap = {
                            // 单击退出，但如果刚刚进行了缩放/拖动操作（300ms内），不触发退出
                            val timeSinceTransform = SystemClock.uptimeMillis() - lastTransformTime
                            if (timeSinceTransform > 300) {
                                exitViewer()
                            }
                        },
                        onDoubleTap = { tapOffset ->
                            // 双击缩放
                            val targetScale = if (userScale > 1.2f) minScale else doubleTapScale
                            val size = contentSize
                            if (size.width <= 0f || size.height <= 0f) {
                                userScale = targetScale
                                userOffset = Offset.Zero
                                return@detectTapGestures
                            }
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val scaleChange = if (userScale == 0f) 1f else targetScale / userScale
                            val newOffset = if (targetScale <= minScale) {
                                Offset.Zero
                            } else {
                                (userOffset + (tapOffset - center) * (1 - scaleChange))
                            }
                            applyScaleAndOffset(targetScale, newOffset, size)
                        },
                        onLongPress = {
                            // 长按用于 HDR 对比和 Live Photo 触发
                            pressStartedAt = SystemClock.uptimeMillis()
                            isPressing = true
                        },
                        onPress = {
                            // 任何触摸开始时记录
                            pressStartedAt = SystemClock.uptimeMillis()
                            tryAwaitRelease()
                            // 释放时结束 HDR/Live 播放
                            isPressing = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 底层：缩略图（立即可见，避免黑屏）
            // 使用与网格相同的缓存键，确保从内存缓存立即加载
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.uri)
                    .size(coil.size.Size(240, 240))
                    .precision(Precision.INEXACT)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .memoryCacheKey(thumbnailCacheKey)
                    .diskCacheKey(thumbnailCacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            
            // 上层：高清图片 (ARGB_8888)，加载完成后淡入覆盖
            // 计算高清图目标尺寸：普通查看时使用屏幕适配尺寸，放大时使用原图尺寸
            val hdTargetSize = if (shouldLoadUltraHd) {
                // 放大时加载原图尺寸（最大 4096，避免 OOM）
                val maxDim = maxOf(image.actualWidth, image.actualHeight).coerceAtMost(4096).coerceAtLeast(1)
                coil.size.Size(maxDim, maxDim)
            } else {
                // 普通查看时使用屏幕 2 倍尺寸（保证清晰度）
                // 确保 targetDim 至少为 1，避免容器尺寸为 0 时崩溃
                val targetDim = (maxOf(containerWidthPx, containerHeightPx) * 1.5f).toInt().coerceIn(1, 2048)
                coil.size.Size(targetDim, targetDim)
            }
            
            if (motionInfo != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(image.uri)
                            .size(hdTargetSize)
                            .precision(Precision.INEXACT)
                            .crossfade(200)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build(),
                        contentDescription = image.displayName,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success) {
                                isHdImageLoaded = true
                                if (shouldLoadUltraHd) {
                                    isUltraHdLoaded = true
                                }
                            }
                        }
                    )
                    MotionPhotoPlayer(
                        imageUri = image.uri,
                        motionInfo = motionInfo,
                        modifier = Modifier.fillMaxSize(),
                        playWhen = isLivePressed,
                        playAudio = playMotionSound,
                        volumePercent = motionSoundVolume
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(image.uri)
                        .size(hdTargetSize)
                        .precision(Precision.INEXACT)
                        .crossfade(200)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build(),
                    contentDescription = image.displayName,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            isHdImageLoaded = true
                            if (shouldLoadUltraHd) {
                                isUltraHdLoaded = true
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * 线性插值
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}
