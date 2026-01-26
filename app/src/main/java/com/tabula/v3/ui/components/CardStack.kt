package com.tabula.v3.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.rememberImageFeatures
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 手势方向判定
 */
private enum class SwipeDirection {
    NONE,
    HORIZONTAL,  // 左右滑 → 洗牌
    UP           // 上滑 → 删除
}

@Composable
private fun rememberImageBadges(
    image: ImageFile?,
    showHdr: Boolean,
    showMotion: Boolean
): List<String> {
    if (image == null) return emptyList()

    val features = rememberImageFeatures(
        image = image,
        enableHdr = showHdr,
        enableMotion = showMotion
    )

    val badges = mutableListOf<String>()
    if (showHdr && features?.isHdr == true) {
        badges.add("HDR")
    }
    if (showMotion && features?.isMotionPhoto == true) {
        badges.add("Live")
    }

    return badges
}

/**
 * 三层卡片堆叠组件 - 无限洗牌手势引擎
 *
 * 核心交互：
 * - 左右滑：洗牌（抽走当前卡插到底部）
 * - 上滑：删除（飞出屏幕）
 * - 点击：打开查看器
 *
 * @param images 完整图片列表
 * @param currentIndex 当前索引
 * @param onIndexChange 索引变化回调
 * @param onRemove 删除回调
 * @param onCardClick 卡片点击回调（传递图片和源位置）
 * @param modifier 外部修饰符
 */
@Composable
fun SwipeableCardStack(
    images: List<ImageFile>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onRemove: (ImageFile) -> Unit,
    onCardClick: ((ImageFile, SourceRect) -> Unit)? = null,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    enableSwipeHaptics: Boolean = true,
    modifier: Modifier = Modifier,
    cardAspectRatio: Float = 3f / 4f
) {
    if (images.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    // 屏幕尺寸
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 基础偏移量
    val baseOffsetPx = with(density) { 24.dp.toPx() }

    // 阈值配置
    val swipeThresholdPx = screenWidthPx * 0.25f
    val velocityThreshold = 800f
    val deleteThresholdPx = screenHeightPx * 0.15f

    // 动画时长
    val shuffleAnimDuration = 120

    // ========== 顶层卡片拖拽状态 ==========
    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val dragRotation = remember { Animatable(0f) }
    val dragAlpha = remember { Animatable(1f) }
    val dragScale = remember { Animatable(1f) }  // 新增：缩放动画

    // 手势方向锁定
    var lockedDirection by remember { mutableStateOf(SwipeDirection.NONE) }
    var isDragging by remember { mutableStateOf(false) }
    var hasDragged by remember { mutableStateOf(false) }  // 是否发生过拖动
    var swipeThresholdHapticTriggered by remember { mutableStateOf(false) }
    var deleteThresholdHapticTriggered by remember { mutableStateOf(false) }

    // ========== 背景卡片呼吸感响应 ==========
    var breathScale by remember { mutableFloatStateOf(0f) }

    // ========== 过渡动画状态 ==========
    var isTransitioning by remember { mutableStateOf(false) }
    var pendingIndexChange by remember { mutableIntStateOf(0) }

    // ========== 卡片位置记录（用于容器变换）==========
    var topCardBounds by remember { mutableStateOf(Rect.Zero) }

    // 获取三张卡的数据
    val currentImage = images.getOrNull(currentIndex)
    val nextImage = images.getOrNull((currentIndex + 1) % images.size)
    val prevImage = images.getOrNull((currentIndex - 1 + images.size) % images.size)

    // 检查边界
    val hasNext = currentIndex < images.lastIndex
    val hasPrev = currentIndex > 0

    /**
     * 重置拖拽状态
     */
    suspend fun resetDragState() {
        scope.launch { dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragRotation.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragAlpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        lockedDirection = SwipeDirection.NONE
        breathScale = 0f
        swipeThresholdHapticTriggered = false
        deleteThresholdHapticTriggered = false
    }

    /**
     * 执行洗牌动画（插底 + 顶上）
     */
    suspend fun executeShuffleAnimation(direction: Int) {
        isTransitioning = true
        pendingIndexChange = direction

        val targetX = if (direction > 0) -baseOffsetPx else baseOffsetPx
        val targetRotation = if (direction > 0) -8f else 8f

        scope.launch { dragOffsetX.animateTo(targetX, tween(shuffleAnimDuration)) }
        scope.launch { dragOffsetY.animateTo(0f, tween(shuffleAnimDuration)) }
        scope.launch { dragRotation.animateTo(targetRotation, tween(shuffleAnimDuration)) }

        kotlinx.coroutines.delay(shuffleAnimDuration.toLong() / 2)

        // 计算新索引：允许超出边界，让调用方决定是否进入完成页面
        val newIndex = when {
            direction > 0 -> currentIndex + 1  // 右滑始终 +1，即使超出边界
            direction < 0 && hasPrev -> currentIndex - 1
            else -> currentIndex
        }

        // 始终通知索引变化（调用方会处理边界情况）
        if (newIndex != currentIndex) {
            onIndexChange(newIndex)
        }

        dragOffsetX.snapTo(0f)
        dragOffsetY.snapTo(0f)
        dragRotation.snapTo(0f)
        dragAlpha.snapTo(1f)

        isTransitioning = false
        lockedDirection = SwipeDirection.NONE
        breathScale = 0f
    }

    /**
     * 执行删除动画 - macOS 最小化风格
     * 
     * 卡片缩小 + 抛物线轨迹飞向右上角回收站图标
     */
    suspend fun executeDeleteAnimation(playHaptic: Boolean) {
        val currentImg = currentImage ?: return
        
        // 触发震动反馈
        if (enableSwipeHaptics && playHaptic) {
            HapticFeedback.heavyTap(context)
        }
        
        // macOS Genie 效果动画时长
        val animDuration = 400
        
        // 目标位置（右上角回收站图标位置）
        val targetX = screenWidthPx * 0.25f   // 向右偏移
        val targetY = -screenHeightPx * 0.55f // 向上飞
        
        // 使用缓动曲线让动画更流畅
        val easeInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        
        // 同时执行多个动画
        scope.launch { 
            dragOffsetX.animateTo(
                targetX, 
                tween(animDuration, easing = easeInOut)
            )
        }
        scope.launch { 
            dragOffsetY.animateTo(
                targetY, 
                tween(animDuration, easing = easeInOut)
            )
        }
        scope.launch { 
            // 缩小到 5%
            dragScale.animateTo(
                0.05f, 
                tween(animDuration, easing = easeInOut)
            )
        }
        scope.launch { 
            // 旋转效果（向右旋转）
            dragRotation.animateTo(
                15f, 
                tween(animDuration, easing = easeInOut)
            )
        }
        scope.launch { 
            // 渐出（后半段开始）
            kotlinx.coroutines.delay((animDuration * 0.5).toLong())
            dragAlpha.animateTo(
                0f, 
                tween((animDuration * 0.5).toInt(), easing = easeInOut)
            )
        }

        // 等待动画完成一部分后执行回调
        kotlinx.coroutines.delay((animDuration * 0.6).toLong())
        onRemove(currentImg)

        // 重置状态
        dragOffsetX.snapTo(0f)
        dragOffsetY.snapTo(0f)
        dragRotation.snapTo(0f)
        dragAlpha.snapTo(1f)
        dragScale.snapTo(1f)
        lockedDirection = SwipeDirection.NONE
        breathScale = 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // ========== 底层卡片 (Prev) ==========
        if (prevImage != null && hasPrev) {
            key(prevImage.id) {
                ImageCard(
                    imageFile = prevImage,
                    modifier = Modifier
                        .zIndex(0f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(cardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = 0.90f + breathScale * 0.01f
                            scaleY = 0.90f + breathScale * 0.01f
                            translationX = -baseOffsetPx
                            rotationZ = -8f
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = rememberImageBadges(prevImage, showHdrBadges, showMotionBadges)
                )
            }
        } else {
            ImageCardPlaceholder(
                modifier = Modifier
                    .zIndex(0f)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(cardAspectRatio)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = 0.90f
                        scaleY = 0.90f
                        translationX = -baseOffsetPx
                        rotationZ = -8f
                    },
                cornerRadius = 16.dp,
                elevation = 4.dp
            )
        }

        // ========== 中层卡片 (Next) ==========
        if (nextImage != null && hasNext) {
            key(nextImage.id) {
                ImageCard(
                    imageFile = nextImage,
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(cardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = 0.95f + breathScale * 0.01f
                            scaleY = 0.95f + breathScale * 0.01f
                            translationX = baseOffsetPx
                            rotationZ = 8f
                        },
                    cornerRadius = 16.dp,
                    elevation = 6.dp,
                    badges = rememberImageBadges(nextImage, showHdrBadges, showMotionBadges)
                )
            }
        } else {
            ImageCardPlaceholder(
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(cardAspectRatio)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = 0.95f
                        scaleY = 0.95f
                        translationX = baseOffsetPx
                        rotationZ = 8f
                    },
                cornerRadius = 16.dp,
                elevation = 6.dp
            )
        }

        // ========== 顶层卡片 (Current) - 可交互 ==========
        if (currentImage != null) {
            key(currentImage.id) {
                val velocityTracker = remember { VelocityTracker() }

                ImageCard(
                    imageFile = currentImage,
                    modifier = Modifier
                        .zIndex(if (isTransitioning && pendingIndexChange != 0) -1f else 2f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(cardAspectRatio)
                        .onGloballyPositioned { coordinates ->
                            topCardBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = dragOffsetX.value
                            translationY = dragOffsetY.value
                            rotationZ = dragRotation.value
                            alpha = dragAlpha.value
                            scaleX = dragScale.value
                            scaleY = dragScale.value
                        }
                        .pointerInput(currentIndex) {
                            detectTapGestures(
                                onTap = {
                                    // 只有没有发生拖动时才触发点击
                                    if (!hasDragged && onCardClick != null) {
                                        val sourceRect = SourceRect(
                                            x = topCardBounds.left,
                                            y = topCardBounds.top,
                                            width = topCardBounds.width,
                                            height = topCardBounds.height,
                                            cornerRadius = 16f
                                        )
                                        onCardClick(currentImage, sourceRect)
                                    }
                                }
                            )
                        }
                        .pointerInput(currentIndex) {
                            detectDragGestures(
                                onDragStart = { _ ->
                                    isDragging = true
                                    hasDragged = false
                                    velocityTracker.resetTracking()
                                    swipeThresholdHapticTriggered = false
                                    deleteThresholdHapticTriggered = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    hasDragged = true
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                                    if (lockedDirection == SwipeDirection.NONE) {
                                        val totalDx = abs(dragOffsetX.value + dragAmount.x)
                                        val totalDy = abs(dragOffsetY.value + dragAmount.y)

                                        if (totalDy > totalDx * 1.5f && dragAmount.y < 0) {
                                            lockedDirection = SwipeDirection.UP
                                        } else if (totalDx > 20f || totalDy > 20f) {
                                            lockedDirection = SwipeDirection.HORIZONTAL
                                        }
                                    }

                                    scope.launch {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                val newY = (dragOffsetY.value + dragAmount.y).coerceAtMost(0f)
                                                dragOffsetY.snapTo(newY)
                                                dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x * 0.3f)
                                                if (enableSwipeHaptics &&
                                                    !deleteThresholdHapticTriggered &&
                                                    -newY > deleteThresholdPx
                                                ) {
                                                    deleteThresholdHapticTriggered = true
                                                    HapticFeedback.heavyTap(context)
                                                }
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                val newX = dragOffsetX.value + dragAmount.x
                                                dragOffsetX.snapTo(newX)
                                                dragOffsetY.snapTo(dragOffsetY.value + dragAmount.y * 0.2f)
                                                if (enableSwipeHaptics &&
                                                    !swipeThresholdHapticTriggered &&
                                                    abs(newX) > swipeThresholdPx
                                                ) {
                                                    swipeThresholdHapticTriggered = true
                                                    HapticFeedback.mediumTap(context)
                                                }
                                            }
                                            else -> {
                                                dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x)
                                                dragOffsetY.snapTo(dragOffsetY.value + dragAmount.y)
                                            }
                                        }

                                        val rotation = (dragOffsetX.value / screenWidthPx) * 15f
                                        dragRotation.snapTo(rotation.coerceIn(-20f, 20f))

                                        breathScale = (abs(dragOffsetX.value) / swipeThresholdPx).coerceIn(0f, 1f)
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val velocity = velocityTracker.calculateVelocity()

                                    scope.launch {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                if (abs(dragOffsetY.value) > deleteThresholdPx ||
                                                    abs(velocity.y) > velocityThreshold
                                                ) {
                                                    executeDeleteAnimation(playHaptic = !deleteThresholdHapticTriggered)
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                val triggered = abs(dragOffsetX.value) > swipeThresholdPx ||
                                                        abs(velocity.x) > velocityThreshold

                                                if (triggered) {
                                                    // dragOffsetX > 0 表示卡片向右移动（手指向右滑），对应"上一张"
                                                    // dragOffsetX < 0 表示卡片向左移动（手指向左滑），对应"下一张"
                                                    val direction = if (dragOffsetX.value > 0) -1 else 1
                                                    
                                                    // 向前滑(direction > 0)始终允许，即使超出边界（进入完成页）
                                                    // 向后滑(direction < 0)需要有上一张
                                                    if (direction > 0 || (direction < 0 && hasPrev)) {
                                                        if (enableSwipeHaptics && !swipeThresholdHapticTriggered) {
                                                            HapticFeedback.mediumTap(context)
                                                        }
                                                        executeShuffleAnimation(direction)
                                                    } else {
                                                        resetDragState()
                                                    }
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                            else -> {
                                                resetDragState()
                                            }
                                        }
                                        // 重置拖动标记
                                        hasDragged = false
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    hasDragged = false
                                    scope.launch { resetDragState() }
                                }
                            )
                        },
                    cornerRadius = 16.dp,
                    elevation = 8.dp,
                    badges = rememberImageBadges(currentImage, showHdrBadges, showMotionBadges)
                )
            }
        }
    }
}

