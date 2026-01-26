package com.tabula.v3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.R
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.ui.components.BatchCompletionScreen
import com.tabula.v3.ui.components.BottomIndicator
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.SwipeableCardStack
import com.tabula.v3.ui.components.TopBar
import com.tabula.v3.ui.components.ViewerOverlay
import com.tabula.v3.ui.components.ViewerState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback

/**
 * Deck 屏幕状态
 */
private enum class DeckState {
    BROWSING,     // 浏览中
    COMPLETED,    // 一组完成
    EMPTY         // 无图片
}

/**
 * 主界面 (Deck Screen) - 卡片堆叠照片清理
 *
 * 核心逻辑：
 * - 从相册随机抽取 batchSize 张作为一组
 * - 滑完一组后显示总结页面
 * - 可选择"再来一组"继续
 *
 * @param allImages 所有图片列表
 * @param batchSize 每组数量
 * @param isLoading 是否正在加载
 * @param topBarDisplayMode 顶部栏显示模式
 * @param onRemove 删除（移到回收站）回调
 * @param onNavigateToTrash 导航到回收站
 * @param onNavigateToSettings 导航到设置
 */
@Composable
fun DeckScreen(
    allImages: List<ImageFile>,
    batchSize: Int,
    isLoading: Boolean,
    topBarDisplayMode: TopBarDisplayMode = TopBarDisplayMode.INDEX,
    onRemove: (ImageFile) -> Unit,
    onKeep: () -> Unit = {},
    onNavigateToTrash: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = true,
    motionSoundVolume: Int = 100,
    enableSwipeHaptics: Boolean = true
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)

    // ========== 批次状态 ==========
    var currentBatch by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var markedCount by remember { mutableIntStateOf(0) }
    var deckState by remember { mutableStateOf(DeckState.BROWSING) }

    // ========== 查看器状态 ==========
    var viewerState by remember { mutableStateOf<ViewerState?>(null) }

    // 初始化批次
    if (currentBatch.isEmpty() && allImages.isNotEmpty() && !isLoading) {
        currentBatch = allImages.shuffled().take(batchSize)
        currentIndex = 0
        markedCount = 0
        deckState = DeckState.BROWSING
    }

    // 开始新一组
    fun startNewBatch() {
        currentBatch = allImages.shuffled().take(batchSize)
        currentIndex = 0
        markedCount = 0
        deckState = DeckState.BROWSING
    }

    // 处理返回键（关闭查看器）
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        AnimatedContent(
            targetState = when {
                isLoading -> "loading"
                allImages.isEmpty() -> "empty"
                deckState == DeckState.COMPLETED -> "completed"
                else -> "browsing"
            },
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "deck_state"
        ) { state ->
            when (state) {
                "loading" -> LoadingState()
                "empty" -> EmptyState()
                "completed" -> {
                    BatchCompletionScreen(
                        totalReviewed = currentBatch.size,
                        totalMarked = markedCount,
                        onContinue = { startNewBatch() },
                        onViewMarked = onNavigateToTrash
                    )
                }
                else -> {
                    // 浏览模式
                    DeckContent(
                        images = currentBatch,
                        currentIndex = currentIndex,
                        topBarDisplayMode = topBarDisplayMode,
                        showHdrBadges = showHdrBadges,
                        showMotionBadges = showMotionBadges,
                        enableSwipeHaptics = enableSwipeHaptics,
                        onIndexChange = { newIndex ->
                            // 如果向后滑超过最后一张，进入完成页面
                            if (newIndex >= currentBatch.size) {
                                if (enableSwipeHaptics) {
                                    HapticFeedback.doubleTap(context)
                                }
                                deckState = DeckState.COMPLETED
                            } else {
                                if (newIndex > currentIndex) {
                                    onKeep()
                                }
                                currentIndex = newIndex.coerceIn(0, currentBatch.lastIndex)
                            }
                        },
                        onRemove = { image ->
                            onRemove(image)
                            markedCount++
                            // 删除后如果没有剩余，进入完成页面
                            val newBatch = currentBatch.toMutableList().apply { remove(image) }
                            currentBatch = newBatch
                            if (currentIndex >= newBatch.size && newBatch.isNotEmpty()) {
                                currentIndex = newBatch.lastIndex
                            } else if (newBatch.isEmpty()) {
                                if (enableSwipeHaptics) {
                                    HapticFeedback.doubleTap(context)
                                }
                                deckState = DeckState.COMPLETED
                            }
                        },
                        onCardClick = { image, sourceRect ->
                            viewerState = ViewerState(image, sourceRect)
                        },
                        onTrashClick = onNavigateToTrash,
                        onSettingsClick = onNavigateToSettings
                    )
                }
            }
        }

        // 查看器覆盖层
        viewerState?.let { state ->
            ViewerOverlay(
                viewerState = state,
                onDismiss = { viewerState = null },
                showHdr = showHdrBadges,
                showMotionPhoto = showMotionBadges,
                playMotionSound = playMotionSound,
                motionSoundVolume = motionSoundVolume
            )
        }
    }
}

/**
 * Deck 内容布局
 */
@Composable
private fun DeckContent(
    images: List<ImageFile>,
    currentIndex: Int,
    topBarDisplayMode: TopBarDisplayMode,
    showHdrBadges: Boolean,
    showMotionBadges: Boolean,
    enableSwipeHaptics: Boolean,
    onIndexChange: (Int) -> Unit,
    onRemove: (ImageFile) -> Unit,
    onCardClick: (ImageFile, SourceRect) -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < images.lastIndex
    val remaining = (images.size - currentIndex - 1).coerceAtLeast(0)
    val currentImage = images.getOrNull(currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 顶部栏（显示时间或索引）
        TopBar(
            currentIndex = currentIndex,
            totalCount = images.size,
            currentImage = currentImage,
            displayMode = topBarDisplayMode,
            onTrashClick = onTrashClick,
            onSettingsClick = onSettingsClick
        )

        // 卡片堆叠区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (images.isNotEmpty() && currentIndex < images.size) {
                SwipeableCardStack(
                    images = images,
                    currentIndex = currentIndex,
                    onIndexChange = onIndexChange,
                    onRemove = onRemove,
                    onCardClick = onCardClick,
                    showHdrBadges = showHdrBadges,
                    showMotionBadges = showMotionBadges,
                    enableSwipeHaptics = enableSwipeHaptics,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 底部剩余提示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "还剩 $remaining 张",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.6f)
            )
        }

        // 底部指示器
        BottomIndicator(
            hasPrev = hasPrev,
            hasNext = hasNext
        )

        Spacer(modifier = Modifier.padding(bottom = 8.dp))
    }
}

/**
 * 加载状态
 */
@Composable
private fun LoadingState() {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Tabula Logo",
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = textColor,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在加载照片...",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState() {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Tabula Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "没有找到照片",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请确保已授权访问相册",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
