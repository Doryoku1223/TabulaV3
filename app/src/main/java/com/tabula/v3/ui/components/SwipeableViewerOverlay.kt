package com.tabula.v3.ui.components

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.util.findActivity
import com.tabula.v3.ui.util.gridThumbnailCacheKey
import com.tabula.v3.ui.util.rememberImageFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 可滑动查看器覆盖层状态
 * 
 * @param images 图片列表
 * @param initialIndex 初始显示的图片索引
 * @param sourceRect 源卡片位置信息（用于容器变换动画）
 */
data class SwipeableViewerState(
    val images: List<ImageFile>,
    val initialIndex: Int,
    val sourceRect: SourceRect = SourceRect()
)

/**
 * 可滑动的沉浸式图片查看器覆盖层 - 支持左右滑动切换图片
 *
 * 动画流程：
 * 1. 入场：从卡片位置展开到屏幕中央（原比例显示）
 * 2. 交互：左右滑动切换图片，双指缩放 + 双击放大
 * 3. 出场：从中央收缩回卡片原位置
 *
 * @param viewerState 查看器状态（图片列表 + 初始索引 + 源位置）
 * @param onDismiss 关闭回调
 * @param modifier 外部修饰符
 * @param showHdr 是否显示HDR
 * @param showMotionPhoto 是否显示动态照片
 * @param playMotionSound 是否播放动态照片声音
 * @param motionSoundVolume 动态照片声音音量
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableViewerOverlay(
    viewerState: SwipeableViewerState,
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

    val images = viewerState.images
    val initialIndex = viewerState.initialIndex

    // 容器尺寸
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    // 源位置信息
    val initialSourceRect = remember { viewerState.sourceRect }
    val isSourceValid = initialSourceRect.width > 0f && initialSourceRect.height > 0f
    
    val source = if (isSourceValid) {
        initialSourceRect
    } else {
        SourceRect(
            x = containerWidthPx / 2f - 100f,
            y = containerHeightPx / 2f - 150f,
            width = 200f,
            height = 300f,
            cornerRadius = 16f
        )
    }

    // Pager 状态 - 用于滑动切换图片
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex.coerceAtLeast(0)),
        pageCount = { images.size }
    )
    
    // 当前页面的缩放状态 - 用于控制 Pager 滑动
    var currentPageZoom by remember { mutableFloatStateOf(1f) }
    
    // 多指触摸状态 - 用于立即禁用 Pager 滑动，避免缩放回弹
    var isMultiTouchActive by remember { mutableStateOf(false) }
    
    // 当前显示的图片
    val currentIndex = pagerState.currentPage
    
    // 页面切换时重置状态，避免状态卡住
    LaunchedEffect(currentIndex) {
        // 切换到新页面时，重置多指触摸状态和缩放状态
        isMultiTouchActive = false
        currentPageZoom = 1f
    }
    val currentImage = images.getOrNull(currentIndex)

    // 使用当前图片计算目标位置
    val imageAspectRatio = currentImage?.let { 
        if (it.actualHeight > 0) it.actualWidth.toFloat() / it.actualHeight else 1f 
    } ?: 1f

    val maxWidth = containerWidthPx * 0.95f
    val maxHeight = containerHeightPx * 0.90f

    val (targetWidth, targetHeight) = if (containerWidthPx > 0f && containerHeightPx > 0f && maxHeight > 0f) {
        if (imageAspectRatio > maxWidth / maxHeight) {
            maxWidth to (maxWidth / imageAspectRatio)
        } else {
            (maxHeight * imageAspectRatio) to maxHeight
        }
    } else {
        0f to 0f
    }

    val targetX = (containerWidthPx - targetWidth) / 2
    val targetY = (containerHeightPx - targetHeight) / 2

    // 动画状态
    val animProgress = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }

    // 入场动画
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 500f
            )
        )
    }

    val activity = context.findActivity()
    val window = activity?.window
    val originalColorMode = remember(window) { window?.colorMode ?: ActivityInfo.COLOR_MODE_DEFAULT }

    DisposableEffect(window) {
        onDispose {
            if (window != null) {
                window.colorMode = originalColorMode
            }
        }
    }

    fun exitViewer() {
        if (isExiting) return
        isExiting = true

        scope.launch {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 600f
                )
            )
            onDismiss()
        }
    }

    val progress = animProgress.value
    
    // 放大模式：当缩放 > 1 时，容器扩展到全屏
    val isZoomed = currentPageZoom > 1.05f
    
    // 使用动画平滑过渡尺寸和位置
    val zoomExpandProgress by animateFloatAsState(
        targetValue = if (isZoomed) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "zoomExpand"
    )
    
    // 放大模式下背景完全不透明，正常模式下 95% 透明度
    val baseAlpha = progress * 0.95f
    val backgroundAlpha = lerp(baseAlpha, 1f, zoomExpandProgress)
    
    val currentX = lerp(source.x, targetX, progress)
    val currentY = lerp(source.y, targetY, progress)
    val currentWidth = lerp(source.width, targetWidth, progress).coerceAtLeast(0f)
    val currentHeight = lerp(source.height, targetHeight, progress).coerceAtLeast(0f)
    val currentCornerRadius = lerp(source.cornerRadius, 4f, progress).coerceAtLeast(0f)
    
    // 计算实际显示的尺寸和位置（在正常模式和放大模式之间插值）
    val displayWidth = lerp(currentWidth, containerWidthPx, zoomExpandProgress).coerceAtLeast(0f)
    val displayHeight = lerp(currentHeight, containerHeightPx, zoomExpandProgress).coerceAtLeast(0f)
    val displayX = lerp(currentX, 0f, zoomExpandProgress)
    val displayY = lerp(currentY, 0f, zoomExpandProgress)
    val displayCornerRadius = lerp(currentCornerRadius, 0f, zoomExpandProgress).coerceAtLeast(0f)
    
    val displayWidthDp = with(density) { displayWidth.toDp() }
    val displayHeightDp = with(density) { displayHeight.toDp() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { exitViewer() })
            },
        contentAlignment = Alignment.TopStart
    ) {
        val actualWidth = constraints.maxWidth.toFloat()
        val actualHeight = constraints.maxHeight.toFloat()

        LaunchedEffect(actualWidth, actualHeight) {
            containerWidthPx = actualWidth
            containerHeightPx = actualHeight
        }

        // 使用 HorizontalPager 实现左右滑动
        // 当图片放大或双指触摸时禁用滑动，避免缩放回弹
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .graphicsLayer {
                    translationX = displayX
                    translationY = displayY
                }
                .size(displayWidthDp, displayHeightDp)
                .clip(RoundedCornerShape(displayCornerRadius.dp)),
            userScrollEnabled = !isMultiTouchActive && currentPageZoom <= 1.05f,
            // O5③：Pager key 用完整媒体身份键——列表含不同卷同数值 ID 行时
            // 不再重复 key 崩溃（与回收站查看器同修法）。
            key = { images.getOrNull(it)?.mediaIdentityKey() ?: it.toString() },
            // 添加 flingBehavior 确保滑动后对齐到页面，防止卡在两图之间
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.35f  // 滑动超过35%就切换到下一页
            )
        ) { pageIndex ->
            val pageImage = images.getOrNull(pageIndex)
            if (pageImage != null) {
                SwipeableImagePage(
                    image = pageImage,
                    imageLoader = imageLoader,
                    showHdr = showHdr,
                    showMotionPhoto = showMotionPhoto,
                    playMotionSound = playMotionSound,
                    motionSoundVolume = motionSoundVolume,
                    onDismiss = { exitViewer() },
                    window = window,
                    originalColorMode = originalColorMode,
                    onZoomChanged = { zoom ->
                        // 只更新当前页面的缩放状态
                        if (pageIndex == pagerState.currentPage) {
                            currentPageZoom = zoom
                        }
                    },
                    onMultiTouchChanged = { isMultiTouch ->
                        // 只更新当前页面的多指触摸状态
                        if (pageIndex == pagerState.currentPage) {
                            isMultiTouchActive = isMultiTouch
                        }
                    }
                )
            }
        }

        // 顶部页码指示器
        if (images.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${currentIndex + 1} / ${images.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 可滑动查看器的单个图片页面
 * 使用 BoxWithConstraints 计算图片实际显示尺寸，确保缩放和拖动逻辑正确
 * 
 * 手势功能：
 * 1. 双指缩放：流畅丝滑，支持缩放到最大后再缩小回原比例
 * 2. 单击：原比例时退出大图；放大时先动画恢复原比例，再次单击才退出
 * 3. 双击：在原比例和放大之间切换
 * 4. 长按：HDR对比/Live Photo播放，松手后不退出
 * 5. 单指拖拽：放大后支持拖动查看
 * 6. 左右滑动：原比例下支持切换上/下一张
 */
@Composable
private fun SwipeableImagePage(
    image: ImageFile,
    imageLoader: coil.ImageLoader,
    showHdr: Boolean,
    showMotionPhoto: Boolean,
    playMotionSound: Boolean,
    motionSoundVolume: Int,
    onDismiss: () -> Unit,
    window: android.view.Window?,
    originalColorMode: Int,
    onZoomChanged: (Float) -> Unit = {},
    onMultiTouchChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 使用 Animatable 实现丝滑的缩放和位移动画
    val animatedScale = remember { Animatable(1f) }
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }
    
    var zoomSize by remember { mutableStateOf(Size.Zero) }
    var lastTransformTime by remember { mutableLongStateOf(0L) }

    val minScale = 1f
    val maxScale = 8f
    val doubleTapScale = 3f
    
    // 当前缩放值（用于判断状态）
    val currentScale = animatedScale.value
    val currentOffset = Offset(animatedOffsetX.value, animatedOffsetY.value)
    
    // 当缩放变化时通知父组件
    LaunchedEffect(currentScale) {
        onZoomChanged(currentScale)
    }

    // HDR / Live Photo 功能
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
    var wasLongPressing by remember { mutableStateOf(false) }
    val pressDelayMs = 80L

    // 高清图加载状态
    var shouldLoadUltraHd by remember { mutableStateOf(false) }
    
    // 缩略图缓存键（C1/T03-THUMBKEY）：与网格共用 grid_ 命名空间——身份 +
    // 内容版本，跨卷同 ID 不串图、编辑后重载；首开温命中网格缩略图。
    val thumbnailCacheKey = remember(image.id, image.dateModified) {
        gridThumbnailCacheKey("grid_", image)
    }

    LaunchedEffect(currentScale) {
        if (currentScale > 1.5f && !shouldLoadUltraHd) {
            shouldLoadUltraHd = true
        }
    }

    fun clampOffset(offset: Offset, scale: Float, size: Size): Offset {
        if (size.width <= 0f || size.height <= 0f) return Offset.Zero
        val maxOffsetX = ((size.width * scale) - size.width).coerceAtLeast(0f) / 2f
        val maxOffsetY = ((size.height * scale) - size.height).coerceAtLeast(0f) / 2f
        return Offset(
            x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    // 动画恢复到原比例
    fun animateToOriginalScale() {
        scope.launch {
            launch {
                animatedScale.animateTo(
                    targetValue = minScale,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f
                    )
                )
            }
            launch {
                animatedOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f
                    )
                )
            }
            launch {
                animatedOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f
                    )
                )
            }
        }
    }

    LaunchedEffect(isPressing, isHdr, motionInfo) {
        if (!isPressing) {
            isHdrComparePressed = false
            isLivePressed = false
            return@LaunchedEffect
        }
        delay(pressDelayMs)
        if (isPressing) {
            // 优先级：Live Photo > HDR 对比
            // 如果图片同时有 HDR 和 Live Photo，长按只播放 Live Photo
            if (motionInfo != null) {
                isLivePressed = true
                // 不触发 HDR 对比，保持 HDR 效果显示
            } else if (isHdr) {
                // 只有纯 HDR 图片（没有 Live Photo）才触发对比模式
                isHdrComparePressed = true
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

    // 使用 BoxWithConstraints 计算图片实际显示尺寸
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 双指缩放检测放在外层，让整个页面区域都能响应双指缩放
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    
                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }
                        
                        // 只在双指或更多手指时处理缩放
                        if (pressedPointers.size >= 2) {
                            onMultiTouchChanged(true)
                            
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                lastTransformTime = SystemClock.uptimeMillis()
                                
                                val size = zoomSize
                                val currentAnimScale = animatedScale.value
                                val currentAnimOffset = Offset(animatedOffsetX.value, animatedOffsetY.value)
                                val newScale = (currentAnimScale * zoomChange).coerceIn(minScale, maxScale)
                                
                                if (size.width > 0f && size.height > 0f) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val scaleRatio = if (currentAnimScale == 0f) 1f else newScale / currentAnimScale
                                    val panMultiplier = currentAnimScale.coerceIn(1f, maxScale)
                                    val adjustedPan = Offset(panChange.x * panMultiplier, panChange.y * panMultiplier)
                                    val newOffset = (currentAnimOffset + adjustedPan) + (centroid - center) * (1 - scaleRatio)
                                    val clampedOffset = clampOffset(newOffset, newScale, size)
                                    
                                    scope.launch {
                                        animatedScale.snapTo(newScale)
                                        if (newScale <= minScale) {
                                            animatedOffsetX.snapTo(0f)
                                            animatedOffsetY.snapTo(0f)
                                        } else {
                                            animatedOffsetX.snapTo(clampedOffset.x)
                                            animatedOffsetY.snapTo(clampedOffset.y)
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        animatedScale.snapTo(newScale)
                                    }
                                }
                            }
                            
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    
                    onMultiTouchChanged(false)
                }
            }
            // 放大状态下的单指拖动也放在外层
            .pointerInput(currentScale > 1.01f) {
                if (currentScale > 1.01f) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        lastTransformTime = SystemClock.uptimeMillis()
                        if (zoomSize.width > 0f && zoomSize.height > 0f) {
                            val latestScale = animatedScale.value
                            val latestOffset = Offset(animatedOffsetX.value, animatedOffsetY.value)
                            val panMultiplier = latestScale.coerceIn(1f, maxScale)
                            val adjustedPan = Offset(dragAmount.x * panMultiplier, dragAmount.y * panMultiplier)
                            val newOffset = latestOffset + adjustedPan
                            val clampedOffset = clampOffset(newOffset, latestScale, zoomSize)
                            scope.launch {
                                animatedOffsetX.snapTo(clampedOffset.x)
                                animatedOffsetY.snapTo(clampedOffset.y)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 计算图片实际显示尺寸（保持宽高比）
        val aspectRatio = if (image.actualHeight > 0) {
            image.actualWidth.toFloat() / image.actualHeight
        } else {
            1f
        }
        val containerRatio = maxWidth.value / maxHeight.value
        val (targetWidth, targetHeight) = if (aspectRatio > containerRatio) {
            maxWidth to (maxWidth / aspectRatio)
        } else {
            (maxHeight * aspectRatio) to maxHeight
        }

        // 高清图目标尺寸
        val hdTargetSize = run {
            val baseSize = maxOf(constraints.maxWidth, constraints.maxHeight).toFloat()
            val scaledSize = (baseSize * currentScale).toInt()
            val imageDim = maxOf(image.actualWidth, image.actualHeight)
            val maxAllowed = if (imageDim > 0) imageDim.coerceAtMost(4096) else 4096
            val targetDim = scaledSize.coerceIn(1, maxAllowed)
            coil.size.Size(targetDim, targetDim)
        }

        Box(
            modifier = Modifier
                .size(targetWidth, targetHeight)
                .onSizeChanged { size ->
                    zoomSize = Size(size.width.toFloat(), size.height.toFloat())
                }
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    translationX = currentOffset.x
                    translationY = currentOffset.y
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                // 单击/双击/长按只在图片区域有效（黑色区域点击不会退出）
                .pointerInput(isHdr, motionInfo) {
                    detectTapGestures(
                        onTap = {
                            if (wasLongPressing) {
                                wasLongPressing = false
                                return@detectTapGestures
                            }
                            val timeSinceTransform = SystemClock.uptimeMillis() - lastTransformTime
                            if (timeSinceTransform > 300) {
                                val targetScale = animatedScale.targetValue
                                if (targetScale > 1.05f) {
                                    animateToOriginalScale()
                                } else {
                                    onDismiss()
                                }
                            }
                        },
                        onLongPress = {
                            wasLongPressing = true
                            isPressing = true
                        },
                        onPress = {
                            isPressing = true
                            tryAwaitRelease()
                            if (isHdrComparePressed || isLivePressed) {
                                wasLongPressing = true
                            }
                            isPressing = false
                        },
                        onDoubleTap = { tapOffset ->
                            val targetScale = if (currentScale > 1.2f) minScale else doubleTapScale
                            if (zoomSize.width <= 0f || zoomSize.height <= 0f) {
                                scope.launch {
                                    animatedScale.animateTo(targetScale, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    animatedOffsetX.snapTo(0f)
                                    animatedOffsetY.snapTo(0f)
                                }
                                return@detectTapGestures
                            }
                            if (targetScale <= minScale) {
                                animateToOriginalScale()
                            } else {
                                val center = Offset(zoomSize.width / 2f, zoomSize.height / 2f)
                                val scaleChange = targetScale / currentScale
                                val newOffset = (currentOffset + (tapOffset - center) * (1 - scaleChange))
                                val clampedOffset = clampOffset(newOffset, targetScale, zoomSize)
                                scope.launch {
                                    launch {
                                        animatedScale.animateTo(targetScale, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    }
                                    launch {
                                        animatedOffsetX.animateTo(clampedOffset.x, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    }
                                    launch {
                                        animatedOffsetY.animateTo(clampedOffset.y, spring(dampingRatio = 0.8f, stiffness = 400f))
                                    }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 底层：缩略图（立即可见）
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

            // 上层：高清图片
            // 注意：只有在长按播放 Live Photo 时才渲染 MotionPhotoPlayer
            // 否则 TextureView 会覆盖底层的 HDR 图片，导致 HDR 效果消失
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
                    modifier = Modifier.fillMaxSize()
                )
                
                // 只有在长按时才显示 MotionPhotoPlayer，避免 TextureView 覆盖 HDR 图片
                if (motionInfo != null && isLivePressed) {
                    MotionPhotoPlayer(
                        imageUri = image.uri,
                        motionInfo = motionInfo,
                        modifier = Modifier.fillMaxSize(),
                        playWhen = true,
                        playAudio = playMotionSound,
                        volumePercent = motionSoundVolume
                    )
                }
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
