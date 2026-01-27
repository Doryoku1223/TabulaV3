package com.tabula.v3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.data.repository.LocalImageRepository
import com.tabula.v3.ui.components.AlbumChips
import com.tabula.v3.ui.components.AlbumChipsEmpty
import com.tabula.v3.ui.components.AlbumEditDialog
import com.tabula.v3.ui.components.BatchCompletionScreen
import com.tabula.v3.ui.components.CategorizedAlbumsView
import com.tabula.v3.ui.components.DraggableAlbumsGrid
import com.tabula.v3.ui.components.FrostedGlass
import com.tabula.v3.ui.components.ModeToggle
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.SwipeableCardStack
import com.tabula.v3.ui.components.TopBar
import com.tabula.v3.ui.components.UndoSnackbar
import com.tabula.v3.ui.components.ViewerOverlay
import com.tabula.v3.ui.components.ViewerState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import kotlinx.coroutines.launch

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
    onNavigateToAlbumDetail: (Album) -> Unit = {},
    onNavigateToAlbums: () -> Unit = {},
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = true,
    motionSoundVolume: Int = 100,
    enableSwipeHaptics: Boolean = true,
    // 推荐算法回调
    getRecommendedBatch: suspend (List<ImageFile>, Int) -> List<ImageFile> = { images, size -> 
        images.shuffled().take(size) 
    },
    // 相册归类相关
    albums: List<Album> = emptyList(),
    systemBuckets: List<LocalImageRepository.SystemBucket> = emptyList(),
    currentImageAlbumIds: Set<String> = emptySet(),
    onAlbumSelect: (imageId: Long, imageUri: String, albumId: String) -> Unit = { _, _, _ -> },
    onCreateAlbum: (name: String, color: Long?, emoji: String?) -> Unit = { _, _, _ -> },
    onUndoAlbumAction: () -> Unit = {},
    onReorderAlbums: (List<String>) -> Unit = {},
    onSystemBucketClick: (String) -> Unit = {},
    onSyncClick: () -> Unit = {},
    lastAlbumActionName: String? = null,
    // 模式切换
    isAlbumMode: Boolean = false,
    onModeChange: (Boolean) -> Unit = {}
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

    // ========== 相册归类状态 ==========
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var showUndoSnackbar by remember { mutableStateOf(false) }
    var undoMessage by remember { mutableStateOf("") }

    // 用于异步初始化批次
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var needsInitialBatch by remember { mutableStateOf(true) }

    // 异步初始化批次
    androidx.compose.runtime.LaunchedEffect(allImages, isLoading, needsInitialBatch) {
        if (currentBatch.isEmpty() && allImages.isNotEmpty() && !isLoading && needsInitialBatch) {
            needsInitialBatch = false
            val newBatch = getRecommendedBatch(allImages, batchSize)
            
            if (newBatch.isNotEmpty()) {
                currentBatch = newBatch
                currentIndex = 0
                markedCount = 0
                deckState = DeckState.BROWSING
            } else {
                // 如果推荐结果为空（例如都在冷却中），直接显示完成状态
                deckState = DeckState.COMPLETED
            }
        }
    }

    // 开始新一组
    fun startNewBatch() {
        scope.launch {
            val newBatch = getRecommendedBatch(allImages, batchSize)
            if (newBatch.isNotEmpty()) {
                currentBatch = newBatch
                currentIndex = 0
                markedCount = 0
                deckState = DeckState.BROWSING
            } else {
                 deckState = DeckState.COMPLETED
            }
        }
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
                isAlbumMode -> "albums"  // 图集模式
                allImages.isEmpty() -> "empty"
                deckState == DeckState.COMPLETED -> "completed"
                currentBatch.isEmpty() -> "loading" // 等待批次加载
                else -> "browsing"
            },
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "deck_state",
            modifier = Modifier.fillMaxSize()
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
                "albums" -> {
                    // 图集模式 - 显示相册列表
                    AlbumsGridContent(
                        albums = albums,
                        systemBuckets = systemBuckets,
                        allImages = allImages,
                        onCreateAlbum = onCreateAlbum,
                        onAlbumClick = onNavigateToAlbumDetail,
                        onSystemBucketClick = onSystemBucketClick,
                        onReorderAlbums = onReorderAlbums,
                        onSyncClick = onSyncClick,
                        onSettingsClick = onNavigateToSettings
                    )
                }
                else -> {
                    // 浏览模式
                    val currentImage = currentBatch.getOrNull(currentIndex)
                    
                    DeckContent(
                        images = currentBatch,
                        currentIndex = currentIndex,
                        topBarDisplayMode = topBarDisplayMode,
                        showHdrBadges = showHdrBadges,
                        showMotionBadges = showMotionBadges,
                        enableSwipeHaptics = enableSwipeHaptics,
                        albums = albums,
                        currentImageAlbumIds = currentImageAlbumIds,
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
                        onSettingsClick = onNavigateToSettings,
                        onAlbumClick = { album ->
                            currentImage?.let { image ->
                                onAlbumSelect(image.id, image.uri.toString(), album.id)
                                undoMessage = "已添加到「${album.name}」"
                                showUndoSnackbar = true
                                // 自动进入下一张
                                if (currentIndex < currentBatch.lastIndex) {
                                    currentIndex++
                                    onKeep()
                                }
                            }
                        },
                        onAddAlbumClick = { showCreateAlbumDialog = true },
                        onAlbumsClick = onNavigateToAlbums
                    )
                }
            }
        }

        // 底部模式切换器 (悬浮)
        if (deckState != DeckState.COMPLETED) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                 ModeToggle(
                    isAlbumMode = isAlbumMode,
                    onModeChange = onModeChange
                )
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

        // 撤销 Snackbar
        UndoSnackbar(
            visible = showUndoSnackbar,
            message = undoMessage,
            onUndo = {
                onUndoAlbumAction()
                showUndoSnackbar = false
            },
            onDismiss = { showUndoSnackbar = false },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 新建相册对话框
    if (showCreateAlbumDialog) {
        AlbumEditDialog(
            isEdit = false,
            onConfirm = { name, color, emoji ->
                onCreateAlbum(name, color, emoji)
                showCreateAlbumDialog = false
            },
            onDismiss = { showCreateAlbumDialog = false }
        )
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
    albums: List<Album>,
    currentImageAlbumIds: Set<String>,
    onIndexChange: (Int) -> Unit,
    onRemove: (ImageFile) -> Unit,
    onCardClick: (ImageFile, SourceRect) -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAddAlbumClick: () -> Unit,
    onAlbumsClick: () -> Unit
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

        // 底部剩余提示 (上移至标签上方，设计感优化)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${remaining} 张待整理",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 2.sp, // 增加字间距，增加呼吸感
                    fontWeight = FontWeight.Medium
                ),
                color = textColor.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        // 相册归类 Chips
        if (albums.isNotEmpty()) {
            AlbumChips(
                albums = albums,
                selectedAlbumIds = currentImageAlbumIds,
                onAlbumClick = onAlbumClick,
                onAddAlbumClick = onAddAlbumClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp) // 增加底部间距，避免遮挡
            )
        } else {
            AlbumChipsEmpty(
                onAddAlbumClick = onAddAlbumClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp)
            )
        }
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



/**
 * TODO
 * 
 * TODO
 */
@Composable
private fun AlbumsGridContent(
    albums: List<Album>,
    systemBuckets: List<LocalImageRepository.SystemBucket> = emptyList(),
    allImages: List<ImageFile>,
    onCreateAlbum: (String, Long?, String?) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSystemBucketClick: (String) -> Unit = {},
    onReorderAlbums: (List<String>) -> Unit = {},
    onSyncClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    var showCreateDialog by remember { mutableStateOf(false) }
    
    // TODO
    var isTabbedView by remember { mutableStateOf(false) }
    // TODO
    var selectedTab by remember { mutableIntStateOf(0) }

    // TODO
    val listState = rememberLazyListState()
    var topBarHeight by remember { mutableStateOf(0.dp) }
    
    // TODO
    val collapseThreshold = 40.dp
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { collapseThreshold.toPx() }
    val blurRadius = 32.dp
    
    // TODO
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                collapseThresholdPx // TODO
            } else {
                listState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(collapseThresholdPx)
            }
        }
    }
    
    val collapsedFraction by remember {
        derivedStateOf { scrollOffset / collapseThresholdPx }
    }
    
    // TODO
    // TODO
    val glassAlpha by animateFloatAsState(
        targetValue = if (collapsedFraction > 0.15f) {
            if (isDarkTheme) 0.42f else 0.18f
        } else {
            if (isDarkTheme) 0.16f else 0.08f
        },
        label = "TopBarGlassAlpha"
    )

    val topBarVisibleHeight = if (topBarHeight > 0.dp) {
        topBarHeight
    } else {
        136.dp
    }

    val contentTopPadding = topBarVisibleHeight + if (isTabbedView) 36.dp else 24.dp
    val tabSwitcher: (@Composable () -> Unit)? = if (isTabbedView) {
        {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                        .padding(2.dp)
                ) {
                    Row {
    // TODO
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .clip(RoundedCornerShape(100))
                                .background(
                                    if (selectedTab == 0) {
                                        if (isDarkTheme) Color(0xFF636366) else Color.White
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                    selectedTab = 0
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "App图集",
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Medium,
                                color = textColor
                            )
                        }

    // TODO
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .clip(RoundedCornerShape(100))
                                .background(
                                    if (selectedTab == 1) {
                                        if (isDarkTheme) Color(0xFF636366) else Color.White
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                    selectedTab = 1
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "手机相册",
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. Blurred backdrop (top bar region only)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topBarVisibleHeight)
                .clipToBounds()
        ) {
            CategorizedAlbumsView(
                appAlbums = if (isTabbedView && selectedTab == 1) null else albums,
                systemBuckets = if (isTabbedView && selectedTab == 0) null else systemBuckets,
                allImages = allImages,
                onAppAlbumClick = onAlbumClick,
                onSystemBucketClick = onSystemBucketClick,
                onReorderAlbums = onReorderAlbums,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                isDarkTheme = isDarkTheme,
                hideHeaders = isTabbedView,
                listState = listState,
                topPadding = contentTopPadding,
                headerContent = tabSwitcher,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = -contentTopPadding)
                    .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            )
        }

        // 2. Content area (base)
        CategorizedAlbumsView(
            appAlbums = if (isTabbedView && selectedTab == 1) null else albums,
            systemBuckets = if (isTabbedView && selectedTab == 0) null else systemBuckets,
            allImages = allImages,
            onAppAlbumClick = onAlbumClick,
            onSystemBucketClick = onSystemBucketClick,
            onReorderAlbums = onReorderAlbums,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            isDarkTheme = isDarkTheme,
            hideHeaders = isTabbedView,
            listState = listState,
            topPadding = contentTopPadding,
            headerContent = tabSwitcher,
            userScrollEnabled = true
        )

        // 3. Top bar (floating glass)
        val topBarTint = (if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
            .copy(alpha = glassAlpha)
        val topBarHighlight = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDarkTheme) 0.08f else 0.12f),
                Color.White.copy(alpha = if (isDarkTheme) 0.03f else 0.05f),
                Color.Transparent
            )
        )

        FrostedGlass(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    topBarHeight = with(density) { size.height.toDp() }
                },
            shape = RoundedCornerShape(0.dp),
            blurRadius = blurRadius,
            tint = topBarTint,
            borderWidth = 0.dp,
            highlightBrush = topBarHighlight,
            noiseAlpha = if (isDarkTheme) 0.03f else 0.05f,
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 14.dp)
            ) {
    // TODO
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 16.dp)
                ) {
    // TODO
                    Text(
                        text = "图集",
                        color = textColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    
    // TODO
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
    // TODO
                        IconButton(
                            onClick = {
                                com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                isTabbedView = !isTabbedView
                            }
                        ) {
                            Icon(
                                imageVector = if (isTabbedView) Icons.Rounded.ViewModule else Icons.Rounded.ViewList,
                                contentDescription = "切换视图",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
    // TODO
                        IconButton(
                            onClick = {
                                com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                onSyncClick()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = "同步到相册",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
    // TODO
                        IconButton(
                            onClick = {
                                com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                onSettingsClick()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "设置",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // TODO
    if (showCreateDialog) {
        AlbumEditDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color, emoji ->
                onCreateAlbum(name, color, emoji)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    allImages: List<ImageFile>,
    onAlbumClick: (Album) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    
    // 建立 ID 到 ImageFile 的映射，加速查找
    val imageMap = remember(allImages) { allImages.associateBy { it.id } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 提示文字
        item(span = { GridItemSpan(2) }) {
            Text(
                text = "点击查看详情",
                color = secondaryTextColor,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(albums) { album ->
            // 尝试获取封面图片
            val coverImage = album.coverImageId?.let { imageMap[it] }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { 
                        com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                        onAlbumClick(album) 
                    }
            ) {
                // 封面图容器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // 正方形
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            color = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
                        )
                ) {
                   if (coverImage != null) {
                       AsyncImage(
                           model = ImageRequest.Builder(context)
                               .data(coverImage.uri)
                               .crossfade(true)
                               .build(),
                           contentDescription = null,
                           contentScale = ContentScale.Crop,
                           modifier = Modifier.fillMaxSize()
                       )
                   } else {
                       // 没有图片时的背景色
                       val albumColor = album.color?.let { Color(it) } ?: Color(0xFF7986CB)
                       Box(
                           modifier = Modifier
                               .fillMaxSize()
                               .background(albumColor.copy(alpha = 0.2f)),
                           contentAlignment = Alignment.Center
                       ) {
                           if (album.emoji != null) {
                               Text(
                                   text = album.emoji,
                                   fontSize = 32.sp
                               )
                           } else {
                               Box(
                                   modifier = Modifier
                                       .size(32.dp)
                                       .background(albumColor, RoundedCornerShape(8.dp))
                               )
                           }
                       }
                   }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 标题
                Text(
                    text = album.name,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                // 数量
                Text(
                    text = "${album.imageCount} 张",
                    color = secondaryTextColor,
                    fontSize = 13.sp,
                )
            }
        }
        
        // 底部留白，为了不被 toggle 遮挡
        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
