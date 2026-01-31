package com.tabula.v3.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tabula.v3.ui.util.HapticFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * ColorOS 16 风格预测性返回容器
 *
 * 动画效果：
 * - 进入动画：从右侧平滑推入 + 圆角渐变 + 背景遮罩
 * - 返回动画：右滑 + 圆角化 + 阴影 + 背景遮罩渐隐
 *
 * @param currentScreen 当前屏幕
 * @param onNavigateBack 返回导航回调
 * @param backgroundContent 背景内容（返回后显示的页面）
 * @param foregroundContent 前景内容（当前页面）
 */
@Composable
fun PredictiveBackContainer(
    currentScreen: AppScreen,
    onNavigateBack: () -> Unit,
    backgroundContent: @Composable () -> Unit,
    foregroundContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val shadowElevationPx = with(density) { 24.dp.toPx() }
    
    // ========== 返回手势动画状态 ==========
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableStateOf(BackEventCompat.EDGE_LEFT) }
    var gestureScreen by remember { mutableStateOf<AppScreen?>(null) }
    val progress = backProgress.value.coerceIn(0f, 1f)
    val renderProgress = if (gestureScreen == currentScreen) progress else 0f
    
    // ========== 进入动画状态 ==========
    // enterProgress: 1.0 = 完全在屏幕外（右侧），0.0 = 完全进入
    val enterProgress = remember { Animatable(0f) }
    var previousScreen by remember { mutableStateOf<AppScreen?>(null) }
    
    // 在 composition 阶段计算是否是前进导航（避免第一帧闪烁）
    val isForwardNavigation = remember(currentScreen, previousScreen) {
        previousScreen != null && when {
            previousScreen == AppScreen.DECK && currentScreen != AppScreen.DECK -> true
            previousScreen == AppScreen.SETTINGS && currentScreen in listOf(
                AppScreen.ABOUT, AppScreen.SUPPORT, AppScreen.STATISTICS,
                AppScreen.VIBRATION_SOUND, AppScreen.IMAGE_DISPLAY, AppScreen.LAB
            ) -> true
            else -> false
        }
    }
    
    // 标记是否正在等待进入动画开始（用于第一帧定位）
    var pendingEnterAnimation by remember { mutableStateOf(false) }
    
    // 当检测到前进导航时，立即标记（在 composition 阶段）
    if (isForwardNavigation && currentScreen != AppScreen.DECK && enterProgress.value == 0f && !enterProgress.isRunning) {
        pendingEnterAnimation = true
    }

    // 检测屏幕变化，触发进入动画
    LaunchedEffect(currentScreen) {
        // 重置返回动画状态
        backProgress.snapTo(0f)
        
        val shouldAnimate = isForwardNavigation && currentScreen != AppScreen.DECK
        
        // 更新 previousScreen
        previousScreen = currentScreen
        
        if (shouldAnimate) {
            // 触发进入动画
            enterProgress.snapTo(1f)  // 从屏幕外开始
            pendingEnterAnimation = false
            enterProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 280,  // 稍快一点
                    easing = FastOutSlowInEasing
                )
            )
        } else {
            pendingEnterAnimation = false
        }
    }

    // 颜色配置

    // 预测性返回处理
    if (currentScreen != AppScreen.DECK) {
        PredictiveBackHandler { backEvents: Flow<BackEventCompat> ->
            var maxProgress = 0f
            var lastProgress = backProgress.value
            var didHaptic = false
            backProgress.stop()
            gestureScreen = currentScreen

            try {
                backEvents.collect { event ->
                    val clampedProgress = event.progress.coerceIn(0f, 1f)
                    lastProgress = clampedProgress
                    maxProgress = maxOf(maxProgress, clampedProgress)
                    swipeEdge = event.swipeEdge
                    backProgress.snapTo(clampedProgress)
                    if (!didHaptic && clampedProgress > 0f) {
                        HapticFeedback.lightTap(context)
                        didHaptic = true
                    }
                }
                val settleFrom = if (lastProgress > 0f) lastProgress else maxProgress
                backProgress.snapTo(settleFrom.coerceIn(0f, 1f))
                backProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
                onNavigateBack()
            } catch (e: CancellationException) {
                val settleFrom = if (lastProgress > 0f) lastProgress else maxProgress
                backProgress.snapTo(settleFrom.coerceIn(0f, 1f))
                backProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            } finally {
                backProgress.snapTo(0f)
                gestureScreen = null
            }
        }
    }

    // ========== 计算动画状态 ==========
    val enterValue = enterProgress.value.coerceIn(0f, 1f)
    
    // 是否正在进行动画（包括等待动画开始的第一帧）
    val isEnterAnimating = enterValue > 0.001f || pendingEnterAnimation
    val isBackAnimating = renderProgress > 0.001f
    val isAnimating = isEnterAnimating || isBackAnimating
    
    // 计算实际的进入动画进度（第一帧使用 1.0 确保在屏幕外）
    val effectiveEnterValue = if (pendingEnterAnimation) 1f else enterValue

    // 根容器背景设为黑色（兜底）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) 
    ) {
        // ========== 背景层 ==========
        // 始终渲染背景内容，避免闪烁
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        ) {
            // 背景内容始终渲染
            backgroundContent()
            
            // 背景遮罩：仅在动画期间显示
            if (isAnimating && currentScreen != AppScreen.DECK) {
                val maskAlpha = when {
                    isEnterAnimating -> lerp(0.15f, 0.35f, effectiveEnterValue)  // 进入时遮罩
                    isBackAnimating -> lerp(0.15f, 0f, renderProgress)  // 返回时遮罩渐隐
                    else -> 0f
                }
                if (maskAlpha > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = maskAlpha))
                    )
                }
            }
        }

        // ========== 前景层 ==========
        if (currentScreen != AppScreen.DECK) {
            // 计算动画参数（使用 effectiveEnterValue 避免第一帧闪烁）
            val animProgress = when {
                isEnterAnimating -> effectiveEnterValue
                isBackAnimating -> renderProgress
                else -> 0f
            }
            
            // 圆角：动画时显示圆角
            val cornerRadius = lerp(0f, 40f, animProgress)
            
            // 位移：进入从右侧滑入，返回向右侧滑出
            val translationX = animProgress * screenWidthPx

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        this.translationX = translationX
                        
                        // 阴影和圆角：在动画进行中显示
                        if (animProgress > 0.001f) {
                            shadowElevation = shadowElevationPx * animProgress
                            shape = RoundedCornerShape(cornerRadius.dp)
                            clip = true
                        }
                    }
                    // 拦截所有触摸事件，防止穿透到背景层
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                // 消费所有触摸事件，不做任何处理
                            }
                        }
                    }
            ) {
                foregroundContent()
            }
        }
    }
}

/**
 * 线性插值
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}
