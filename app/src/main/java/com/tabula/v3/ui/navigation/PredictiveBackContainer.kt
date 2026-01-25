package com.tabula.v3.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = LocalIsDarkTheme.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val shadowElevationPx = with(density) { 24.dp.toPx() }
    
    // 动画状态
    var backProgress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableStateOf(BackEventCompat.EDGE_LEFT) }
    var isBackTriggered by remember { mutableStateOf(false) } // 是否触发了返回

    // 颜色配置
    val scrimColor = Color.Black.copy(alpha = 0.25f) // 背景遮罩色

    // 预测性返回处理
    if (currentScreen != AppScreen.DECK) {
        PredictiveBackHandler { backEvents: Flow<BackEventCompat> ->
            // 记录最大进度，因为 cancel 时 progress 可能已经归零
            var maxProgress = 0f
            
            try {
                backEvents.collect { event ->
                    backProgress = event.progress
                    maxProgress = maxOf(maxProgress, event.progress)
                    // 忽略 event.swipeEdge，统一行为
                }
                // 手势完成 -> 触发返回
                isBackTriggered = true
                
                // 执行完成动画
                scope.launch {
                    val anim = Animatable(backProgress)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                    onNavigateBack()
                }
            } catch (e: CancellationException) {
                // 手势取消 (系统判定)
                // 用户要求：无论滑没滑到底，只要开始滑动，松手照样快速过渡返回，不要弹簧回弹
                // 所以只要 maxProgress 稍微动了一点点，就强制执行返回逻辑
                if (maxProgress > 0.01f) {
                    isBackTriggered = true
                    scope.launch {
                        // 如果当前 progress 掉下来了，从当前位置补间动画到 1f
                        val anim = Animatable(backProgress)
                        anim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        )
                        onNavigateBack()
                    }
                } else {
                    // 真的几乎没动 -> 瞬间归零，不放弹簧动画，避免拖泥带水
                    backProgress = 0f
                    isBackTriggered = false
                }
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
        if (backProgress > 0f || currentScreen == AppScreen.DECK) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
            ) {
                backgroundContent()
                
                // 仅保留极淡的遮罩，增强前后景分离感（可选，如果不想要可以设 alpha 为 0）
                // 既然用户要求完美复原，且底层不缩放，那遮罩也不要动，或者给一个固定的暗度？
                // 观察图4，背景似乎变暗了一点点。给一个极淡的动态遮罩。
                if (backProgress > 0f && currentScreen != AppScreen.DECK) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = lerp(0.15f, 0f, backProgress)))
                    )
                }
            }
        }

        // ========== 前景层 ==========
        if (currentScreen != AppScreen.DECK) {
            // 圆角：随进度变大 (0 -> 48dp)
            val cornerRadius = lerp(0f, 48f, backProgress)
            
            // 位移：始终向右移动
            val translationX = backProgress * screenWidthPx
            
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
                        if (backProgress > 0f) {
                            shadowElevation = shadowElevationPx
                            shape = RoundedCornerShape(cornerRadius.dp)
                            clip = true
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
