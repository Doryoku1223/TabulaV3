package com.tabula.v3.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
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
 * 动画效果（更接近 OPPO/ColorOS）：
 * - 前景层：缩小 + 右滑 + 圆角化 + 阴影
 * - 背景层：视差放大 + 暗色遮罩渐隐
 * - 左侧返回指示器（箭头）
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
    
    // 动画状态
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableStateOf(BackEventCompat.EDGE_LEFT) }
    var gestureScreen by remember { mutableStateOf<AppScreen?>(null) }
    val progress = backProgress.value.coerceIn(0f, 1f)
    val renderProgress = if (gestureScreen == currentScreen) progress else 0f

    LaunchedEffect(currentScreen) {
        backProgress.snapTo(0f)
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

    // 根容器背景设为黑色（兜底）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) 
    ) {
        // ========== 背景层 ==========
        // 始终渲染背景，移除所有动态缩放和遮罩
        // 这样可以彻底避免"灰白色断层"和边缘漏光，同时大幅降低 GPU 负载
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .drawWithContent {
                    val shouldDraw = renderProgress > 0f || currentScreen == AppScreen.DECK
                    if (shouldDraw) {
                        drawContent()
                    }
                }
        ) {
            backgroundContent()
            if (renderProgress > 0f && currentScreen != AppScreen.DECK) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = lerp(0.15f, 0f, renderProgress)))
                )
            }
        }

        // ========== 前景层 ==========
        if (currentScreen != AppScreen.DECK) {
            // 圆角：随进度变大 (0 -> 48dp)
            val cornerRadius = lerp(0f, 48f, renderProgress)
            
            // 位移：始终向右移动（无论左/右边缘触发）
            val translationX = renderProgress * screenWidthPx
            
            // 缩放：移除，始终为 1.0
            val scale = 1f 

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        this.scaleX = scale
                        this.scaleY = scale
                        this.translationX = translationX
                        
                        // 阴影：仅在移动时显示
                        if (renderProgress > 0f) {
                            shadowElevation = shadowElevationPx
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
