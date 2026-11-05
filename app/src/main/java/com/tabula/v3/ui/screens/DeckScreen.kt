package com.tabula.v3.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.ui.components.TagPosition
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.ui.util.refreshedViewerImage
import com.tabula.v3.ui.util.refreshedCleanupMonth
import com.tabula.v3.ui.components.buildMonthInfos
import com.tabula.v3.ui.util.relocatedIndexAfterSync
import com.tabula.v3.ui.util.syncCleanupBatchToSnapshot
import com.tabula.v3.ui.util.syncRowsToSnapshot
import com.tabula.v3.data.repository.resolveCoverRow
import com.tabula.v3.ui.util.coverImageRequest

import com.tabula.v3.data.preferences.AlbumCleanupDisplayMode
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.rememberAppPreferences
import com.tabula.v3.data.preferences.SwipeStyle
import com.tabula.v3.data.preferences.TagSelectionMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.data.repository.LocalImageRepository
import androidx.compose.ui.geometry.Offset
import com.tabula.v3.ui.components.ActionIconButton
import com.tabula.v3.ui.components.AlbumDropTarget
import com.tabula.v3.ui.components.TAGS_PER_ROW
import com.tabula.v3.ui.components.AlbumEditDialog
import com.tabula.v3.ui.components.BatchCompletionScreen
import com.tabula.v3.ui.components.CategorizedAlbumsView
import com.tabula.v3.ui.components.DraggableAlbumsGrid
import com.tabula.v3.ui.components.FixedTagBar
import com.tabula.v3.ui.components.ModeToggle
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.SwipeableCardStack
import com.tabula.v3.ui.components.GenieEffectOverlay
import com.tabula.v3.ui.components.rememberGenieAnimationController
import com.tabula.v3.ui.components.TopBar
import com.tabula.v3.ui.components.CardShareState
import com.tabula.v3.ui.util.rememberPhotoShareAction
import com.tabula.v3.ui.components.UndoSnackbar
import com.tabula.v3.ui.components.ViewerOverlay
import com.tabula.v3.ui.components.ViewerState
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.clampSelectionToVisible
import com.tabula.v3.ui.util.selectAllOrClear
import com.tabula.v3.ui.util.resolveAlbumCleanupImages
import com.tabula.v3.ui.components.quickaction.QuickActionButton
import com.tabula.v3.ui.components.quickaction.rememberSafeArea
import com.tabula.v3.ui.components.AlbumCleanupBottomSheet
import com.tabula.v3.ui.components.MonthInfo
import com.tabula.v3.ui.components.AlbumSelectionSheet
import com.tabula.v3.data.repository.AlbumCleanupEngine
import com.tabula.v3.data.repository.AlbumCleanupInfo
import com.tabula.v3.data.repository.AlbumCleanupBatch
import com.tabula.v3.di.CoilSetup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect

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
    allImagesForCleanup: List<ImageFile> = allImages,
    batchSize: Int,
    isLoading: Boolean,
    loadingProgress: Float = 0f,
    loadingPhase: String = "正在扫描照片...",
    loadingScanned: Int = 0,
    loadingTotal: Int = 0,
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
    isAdaptiveCardStyle: Boolean = false,
    swipeStyle: SwipeStyle = SwipeStyle.SHUFFLE,
    // 标签收纳模式
    tagSelectionMode: TagSelectionMode = TagSelectionMode.SWIPE_AUTO,
    tagsPerRow: Int = TAGS_PER_ROW,
    tagSwitchSpeed: Float = 1.0f,
    // 推荐算法回调
    getRecommendedBatch: suspend (List<ImageFile>, Int) -> List<ImageFile> = { images, size -> 
        images.shuffled().take(size) 
    },
    // 推荐模式刷新触发器（用于切换算法时刷新批次）
    recommendModeRefreshKey: Int = 0,
    // 从冷却池移除照片的回调（切换算法时用于移除未浏览的照片；
    // C1/T03-DECKCOOLDOWN：传照片行，接收端按完整媒体身份键移除）
    onRemoveFromCooldown: (List<ImageFile>) -> Unit = {},
    // 相册归类相关
    albums: List<Album> = emptyList(),
    systemBuckets: List<LocalImageRepository.SystemBucket> = emptyList(),
    currentImageAlbumIds: Set<String> = emptySet(),
    onAlbumSelect: (imageId: Long, imageUri: String, albumId: String) -> Unit = { _, _, _ -> },
    onCreateAlbum: (name: String, color: Long?, emoji: String?) -> Unit = { _, _, _ -> },
    // 新建图集并归档图片（用于下滑归类时选择"新建"的场景）
    onCreateAlbumAndClassify: (name: String, color: Long?, emoji: String?, ImageFile) -> Unit = { _, _, _, _ -> },
    onUndoAlbumAction: () -> Unit = {},
    onCommitArchive: () -> Unit = {},  // Snackbar 消失时提交归档
    onReorderAlbums: (List<String>) -> Unit = {},
    onSystemBucketClick: (String) -> Unit = {},
    onSyncClick: () -> Unit = {},
    lastAlbumActionName: String? = null,
    // 模式切换
    isAlbumMode: Boolean = false,
    onModeChange: (Boolean) -> Unit = {},
    // 显示/隐藏 隐藏的图集
    showHiddenAlbums: Boolean = false,
    onToggleShowHidden: () -> Unit = {},
    // 图集操作回调
    onHideAlbum: ((Album) -> Unit)? = null,  // 隐藏图集
    onExcludeAlbum: ((Album, Boolean) -> Unit)? = null,  // 屏蔽/取消屏蔽图集
    isAlbumExcluded: ((Album) -> Boolean)? = null,  // 检查图集是否被屏蔽
    // 流体云：批次剩余数量回调
    onBatchRemainingChange: (Int) -> Unit = {},
    // 快捷操作按钮
    quickActionEnabled: Boolean = false,
    leftHandButtonX: Float = 0.85f,
    leftHandButtonY: Float = 0.55f,
    onQuickActionPositionChanged: (isLeftHand: Boolean, x: Float, y: Float) -> Unit = { _, _, _ -> },
    // 图集清理模式
    albumCleanupEngine: AlbumCleanupEngine? = null,
    albumCleanupInfos: List<AlbumCleanupInfo> = emptyList(),
    onRefreshAlbumCleanupInfos: () -> Unit = {},
    // 图集清理模式状态（从 TabulaApp 传入，避免导航时丢失）
    isAlbumCleanupMode: Boolean = false,
    onAlbumCleanupModeChange: (Boolean) -> Unit = {},
    selectedCleanupAlbum: Album? = null,
    onSelectedCleanupAlbumChange: (Album?) -> Unit = {},
    // "其他图集"导航
    onNavigateToOtherAlbums: (List<Album>) -> Unit = {},
    // 原图清理
    pendingCleanupCount: Int = 0,
    onCleanupSourceImages: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val appPreferences = rememberAppPreferences()
    val imageLoader = remember(context) { CoilSetup.getImageLoader(context) }
    
    // 背景颜色 - 纯白/纯黑，液态玻璃效果只在卡片上
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
    
    // 撤销所需的状态：记住最后一次归档操作的图片和位置
    var lastClassifiedImage by remember { mutableStateOf<ImageFile?>(null) }
    var lastClassifiedIndex by remember { mutableIntStateOf(-1) }
    var lastClassifyWasSwipe by remember { mutableStateOf(false) }  // 是否是下滑归类
    
    // ========== 删除撤销状态 ==========
    // 记住最后一次删除（上滑移入回收站）操作的图片和位置
    var lastDeletedImage by remember { mutableStateOf<ImageFile?>(null) }
    var lastDeletedIndex by remember { mutableIntStateOf(-1) }
    var isDeletePending by remember { mutableStateOf(false) }  // 是否有待确认的删除操作
    
    // 下滑归类模式状态（用于隐藏底部切换按钮）
    var isClassifyMode by remember { mutableStateOf(false) }
    
    // 等待归档的图片（用于新建图集时自动归档）
    var pendingClassifyImage by remember { mutableStateOf<ImageFile?>(null) }
    
    // ========== LIST_POPUP 模式：图集选择弹层状态 ==========
    var showAlbumSelectionSheet by remember { mutableStateOf(false) }

    // 用于异步初始化批次
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var needsInitialBatch by remember { mutableStateOf(true) }
    
    // ========== 快捷操作按钮状态 ==========
    val safeArea = rememberSafeArea()
    
    // 加载下一组的状态（用于显示加载指示器）
    var isLoadingNextBatch by remember { mutableStateOf(false) }
    
    // ========== 图集清理模式状态 ==========
    var showAlbumCleanupSheet by remember { mutableStateOf(false) }
    // isAlbumCleanupMode 和 selectedCleanupAlbum 现在从外部传入（TabulaApp 级别管理）
    var analyzingAlbumId by remember { mutableStateOf<String?>(null) }
    var analysisProgress by remember { mutableStateOf(0f) }
    var currentCleanupBatch by remember { mutableStateOf<AlbumCleanupBatch?>(null) }
    var albumCleanupTotalGroups by remember { mutableIntStateOf(0) }
    var albumCleanupRemainingGroups by remember { mutableIntStateOf(0) }
    var albumCleanupTotalImages by remember { mutableIntStateOf(0) }
    var albumCleanupRemainingImages by remember { mutableIntStateOf(0) }
    var albumCleanupDisplayMode by remember { mutableStateOf(appPreferences.albumCleanupDisplayMode) }
    
    // 月份专清状态
    var selectedCleanupMonth by remember { mutableStateOf<MonthInfo?>(null) }
    var isMonthCleanupMode by remember { mutableStateOf(false) }  // 区分图集清理和月份清理
    
    // 当前分析任务（用于取消快速切换时的旧任务）
    var currentAnalysisJob by remember { mutableStateOf<Job?>(null) }
    
    // 预加载的下一批（用于组切换时无缝衔接）
    var preloadedNextBatch by remember { mutableStateOf<AlbumCleanupBatch?>(null) }
    var isPreloadingNextBatch by remember { mutableStateOf(false) }
    
    // 断点续传：保存当前位置（防抖处理，避免频繁写入）
    var lastSavedIndex by remember { mutableIntStateOf(-1) }

    // No currently accessible batch does not imply that unfinished groups were reviewed.
    fun showCleanupAvailability(cleanupId: String) {
        currentCleanupBatch = null
        preloadedNextBatch = null
        currentBatch = emptyList()
        currentIndex = 0
        needsInitialBatch = false
        albumCleanupTotalGroups = albumCleanupEngine?.getTotalGroups(cleanupId) ?: 0
        albumCleanupRemainingGroups = albumCleanupEngine?.getRemainingGroups(cleanupId) ?: 0
        albumCleanupTotalImages = albumCleanupEngine?.getTotalImages(cleanupId) ?: 0
        albumCleanupRemainingImages = albumCleanupEngine?.getRemainingImages(cleanupId) ?: 0
        deckState = if (albumCleanupEngine?.isAlbumCompleted(cleanupId) == true) {
            DeckState.COMPLETED
        } else {
            DeckState.BROWSING
        }
    }
    androidx.compose.runtime.LaunchedEffect(currentIndex, isAlbumCleanupMode, currentCleanupBatch) {
        if (!isAlbumCleanupMode || albumCleanupEngine == null || currentCleanupBatch == null) return@LaunchedEffect
        // 只在索引真正变化时保存，且至少变化2张才保存（减少写入频率）
        if (currentIndex != lastSavedIndex && (lastSavedIndex == -1 || kotlin.math.abs(currentIndex - lastSavedIndex) >= 2)) {
            kotlinx.coroutines.delay(500)  // 防抖延迟
            albumCleanupEngine.saveCheckpoint(currentCleanupBatch!!.groupIds, currentIndex)
            lastSavedIndex = currentIndex
        }
    }
    
    // 使用 rememberUpdatedState 捕获最新状态，避免 DisposableEffect 中访问过期值
    val latestCleanupBatch by androidx.compose.runtime.rememberUpdatedState(currentCleanupBatch)
    val latestIndex by androidx.compose.runtime.rememberUpdatedState(currentIndex)
    val latestIsAlbumCleanupMode by androidx.compose.runtime.rememberUpdatedState(isAlbumCleanupMode)
    
    // 退出时强制保存断点（避免防抖机制导致最后一次保存丢失）
    // 使用 Unit 作为 key，确保只在组件销毁时触发一次
    DisposableEffect(Unit) {
        onDispose {
            if (latestIsAlbumCleanupMode && latestCleanupBatch != null && albumCleanupEngine != null) {
                albumCleanupEngine.saveCheckpoint(latestCleanupBatch!!.groupIds, latestIndex)
            }
        }
    }
    
    // 监听批次剩余数量变化，通知流体云
    androidx.compose.runtime.LaunchedEffect(currentBatch, currentIndex, deckState) {
        val remaining = if (deckState == DeckState.BROWSING && currentBatch.isNotEmpty()) {
            (currentBatch.size - currentIndex - 1).coerceAtLeast(0)
        } else {
            0
        }
        onBatchRemainingChange(remaining)
    }
    
    // 图集/月份清理模式：预加载下一批（当剩余 ≤3 张时开始预加载）
    // 只预加载下一批的前 3 张图片，避免内存压力
    androidx.compose.runtime.LaunchedEffect(currentBatch, currentIndex, isAlbumCleanupMode, preloadedNextBatch, currentCleanupBatch, isMonthCleanupMode) {
        if (!isAlbumCleanupMode || albumCleanupEngine == null) return@LaunchedEffect
        if (isPreloadingNextBatch || preloadedNextBatch != null) return@LaunchedEffect
        if (currentCleanupBatch == null) return@LaunchedEffect
        
        // 获取当前清理的 ID 和图片列表（支持图集和月份两种模式）
        val cleanupId: String?
        val cleanupImages: List<ImageFile>
        
        if (isMonthCleanupMode && selectedCleanupMonth != null) {
            cleanupId = selectedCleanupMonth?.cleanupAlbumId
            cleanupImages = selectedCleanupMonth?.images ?: emptyList()
        } else if (!isMonthCleanupMode && selectedCleanupAlbum != null) {
            val album = selectedCleanupAlbum!!
            cleanupId = album.id
            cleanupImages = resolveAlbumCleanupImages(album, allImagesForCleanup)
        } else {
            return@LaunchedEffect
        }
        
        if (cleanupId == null) return@LaunchedEffect
        
        val remaining = (currentBatch.size - currentIndex - 1).coerceAtLeast(0)
        // 【性能优化】更积极的预加载：从 ≤3 改成 ≤5，给预加载更多时间完成
        if (remaining <= 5) {
            // 开始预加载下一批
            isPreloadingNextBatch = true
            
            // 预取下一批数据，排除当前正在处理的组（避免重复）
            // 分析范围仍是当前图集/月；已保存成员的访问与版本校验使用完整快照，
            // 避免外部编辑跨月或移动图集后把仍可访问的成员误判为权限缺席。
            val currentGroupIds = currentCleanupBatch?.groupIds ?: emptyList()
            val nextBatch = albumCleanupEngine.getNextBatch(cleanupId, cleanupImages, currentGroupIds, latestSnapshot = allImagesForCleanup)
            if (nextBatch != null) {
                preloadedNextBatch = nextBatch
                
                // 只预加载前 3 张图片，使用低优先级
                nextBatch.images.take(3).forEach { image ->
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(image.uri)
                        .size(512, 512)  // 使用较小尺寸预加载
                        .memoryCacheKey(image.uri.toString())
                        .build()
                    imageLoader.enqueue(request)
                }
            }
            
            isPreloadingNextBatch = false
        }
    }

    // 图集/月份清理模式恢复：当从设置返回时，状态已保持但批次数据需要重新加载
    androidx.compose.runtime.LaunchedEffect(isAlbumCleanupMode, selectedCleanupAlbum, selectedCleanupMonth, isMonthCleanupMode, allImages, allImagesForCleanup, isLoading, currentCleanupBatch) {
        // 如果处于清理模式，但批次数据为空（组件重建后丢失），需要恢复
        if (isAlbumCleanupMode && currentCleanupBatch == null && 
            !isLoading && albumCleanupEngine != null) {
            
            // 根据是月份清理还是图集清理，获取对应的 ID 和图片列表
            val cleanupId: String?
            var cleanupImages: List<ImageFile>
            val cleanupName: String?
            
            if (isMonthCleanupMode && selectedCleanupMonth != null) {
                // 月份清理模式
                cleanupId = selectedCleanupMonth?.cleanupAlbumId
                cleanupImages = selectedCleanupMonth?.images ?: emptyList()
                cleanupName = selectedCleanupMonth?.displayName
                val rebuilt = refreshedCleanupMonth(selectedCleanupMonth!!, allImagesForCleanup)
                selectedCleanupMonth = rebuilt
                cleanupImages = rebuilt.images
            } else if (!isMonthCleanupMode && selectedCleanupAlbum != null) {
                // 图集清理模式
                val album = selectedCleanupAlbum!!
                cleanupId = album.id
                cleanupImages = resolveAlbumCleanupImages(album, allImagesForCleanup)
                cleanupName = album.name
            } else {
                // 没有有效的清理目标，退出
                return@LaunchedEffect
            }

            if (cleanupId == null || (cleanupImages.isEmpty() && albumCleanupEngine.getTotalGroups(cleanupId) < 0)) {
                // 任务 A：清空可能源于权限收窄/外部删除（暂时不可见）——存在
                // 断点即持有会话（隐藏照片、保留断点与未处理意图），访问恢复
                // 后经本 effect 续接；无断点才退出清理
                val hasHeldIntent = cleanupId != null && albumCleanupEngine.getCheckpoint(cleanupId) != null
                if (!hasHeldIntent) {
                    onAlbumCleanupModeChange(false)
                    onSelectedCleanupAlbumChange(null)
                    selectedCleanupMonth = null
                    isMonthCleanupMode = false
                }
                return@LaunchedEffect
            }
            
            android.util.Log.d("DeckScreen", "Restoring cleanup mode for: $cleanupName")
            
            // 尝试从断点恢复
            val checkpointResult = albumCleanupEngine.getCheckpointBatch(cleanupId, cleanupImages, latestSnapshot = allImagesForCleanup)
            if (checkpointResult != null) {
                val (batch, savedIndex) = checkpointResult
                currentCleanupBatch = batch
                currentBatch = batch.images
                currentIndex = savedIndex
                lastSavedIndex = savedIndex
                markedCount = 0
                deckState = DeckState.BROWSING
                needsInitialBatch = false
                
                albumCleanupTotalGroups = albumCleanupEngine.getTotalGroups(cleanupId)
                albumCleanupRemainingGroups = albumCleanupEngine.getRemainingGroups(cleanupId)
                albumCleanupTotalImages = albumCleanupEngine.getTotalImages(cleanupId)
                albumCleanupRemainingImages = albumCleanupEngine.getRemainingImages(cleanupId)
                
                android.util.Log.d("DeckScreen", "Restored from checkpoint: index=$savedIndex")
            } else {
                // 没有断点，获取下一批
                val batch = albumCleanupEngine.getNextBatch(cleanupId, cleanupImages, latestSnapshot = allImagesForCleanup)
                if (batch != null) {
                    currentCleanupBatch = batch
                    currentBatch = batch.images
                    currentIndex = 0
                    lastSavedIndex = -1
                    markedCount = 0
                    deckState = DeckState.BROWSING
                    needsInitialBatch = false
                    
                    albumCleanupTotalGroups = albumCleanupEngine.getTotalGroups(cleanupId)
                    albumCleanupRemainingGroups = albumCleanupEngine.getRemainingGroups(cleanupId)
                    albumCleanupTotalImages = albumCleanupEngine.getTotalImages(cleanupId)
                    albumCleanupRemainingImages = albumCleanupEngine.getRemainingImages(cleanupId)
                } else {
                    showCleanupAvailability(cleanupId)
                    onRefreshAlbumCleanupInfos()
                }
            }
        }
    }

    // 异步初始化批次
    androidx.compose.runtime.LaunchedEffect(allImages, isLoading, needsInitialBatch) {
        android.util.Log.d("DeckScreen", "LaunchedEffect: allImages.size=${allImages.size}, isLoading=$isLoading, needsInitialBatch=$needsInitialBatch, currentBatch.isEmpty=${currentBatch.isEmpty()}")
        // 图集清理模式下跳过全局批次初始化
        if (isAlbumCleanupMode) return@LaunchedEffect
        if (currentBatch.isEmpty() && allImages.isNotEmpty() && !isLoading && needsInitialBatch) {
            android.util.Log.d("DeckScreen", "Starting batch fetch...")
            try {
                val newBatch = getRecommendedBatch(allImages, batchSize)
                android.util.Log.d("DeckScreen", "Got batch: size=${newBatch.size}")
                
                // 只有成功获取后才设置 needsInitialBatch = false
                needsInitialBatch = false
                
                if (newBatch.isNotEmpty()) {
                    currentBatch = newBatch
                    currentIndex = 0
                    markedCount = 0
                    deckState = DeckState.BROWSING
                } else {
                    // 如果推荐结果为空（例如都在冷却中），直接显示完成状态
                    deckState = DeckState.COMPLETED
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消（例如组件重新创建），不做处理，让下次LaunchedEffect重试
                android.util.Log.d("DeckScreen", "Batch fetch cancelled, will retry")
            } catch (e: Exception) {
                android.util.Log.e("DeckScreen", "Error fetching batch", e)
                needsInitialBatch = false  // 出错时也设置，避免无限重试
            }
        }
    }

    // 监听推荐模式切换，实时刷新批次
    // 用于在设置中切换算法后，卡片界面立即显示新算法的推荐结果
    var lastRecommendModeRefreshKey by remember { mutableIntStateOf(recommendModeRefreshKey) }
    androidx.compose.runtime.LaunchedEffect(recommendModeRefreshKey) {
        // 首次加载时跳过（防止重复刷新）
        if (lastRecommendModeRefreshKey == recommendModeRefreshKey) return@LaunchedEffect
        lastRecommendModeRefreshKey = recommendModeRefreshKey
        
        // 图集清理模式下不响应推荐模式切换
        if (isAlbumCleanupMode) return@LaunchedEffect
        
        // 如果没有加载完成或图片列表为空，跳过
        if (isLoading || allImages.isEmpty()) return@LaunchedEffect
        
        android.util.Log.d("DeckScreen", "Recommend mode changed, refreshing batch...")
        
        // 显示加载状态，避免切换时的视觉闪烁
        isLoadingNextBatch = true
        
        try {
            // 将当前批次中未浏览的照片从冷却池中移除
            // 只针对随机漫步模式的单张照片冷却池有效
            // 相似推荐模式使用的是组冷却池，这里的移除对其无影响
            // 
            // currentIndex 是当前正在看的照片索引：
            // - 索引 0 到 currentIndex 的照片用户已经看过，保留在冷却池
            // - 索引 currentIndex+1 及之后的照片用户还没看，从冷却池移除
            if (currentBatch.isNotEmpty() && currentIndex < currentBatch.size) {
                val unviewedImages = currentBatch.drop(currentIndex + 1)
                if (unviewedImages.isNotEmpty()) {
                    android.util.Log.d("DeckScreen", "Removing ${unviewedImages.size} unviewed images from cooldown")
                    // 在 IO 线程执行移除操作，commit() 会同步完成；
                    // C1/T03-DECKCOOLDOWN：传行而非裸 ID，跨卷同 ID 他卷已看记录不受影响
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        onRemoveFromCooldown(unviewedImages)
                    }
                }
            }
            
            // 获取新的批次
            val newBatch = getRecommendedBatch(allImages, batchSize)
            if (newBatch.isNotEmpty()) {
                currentBatch = newBatch
                currentIndex = 0
                markedCount = 0
                deckState = DeckState.BROWSING
                android.util.Log.d("DeckScreen", "Got new batch after mode change: size=${newBatch.size}")
            } else {
                // 如果推荐结果为空（例如都在冷却中），显示完成状态
                deckState = DeckState.COMPLETED
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消（例如用户快速切换页面），不做处理
            android.util.Log.d("DeckScreen", "Batch refresh cancelled")
            throw e  // 必须重新抛出 CancellationException
        } catch (e: Exception) {
            android.util.Log.e("DeckScreen", "Error fetching batch after mode change", e)
            // 出错时保持当前批次，不影响用户体验
        } finally {
            isLoadingNextBatch = false
        }
    }

    // 开始新一组
    fun startNewBatch() {
        if (isLoadingNextBatch) return  // 防止重复点击
        
        scope.launch {
            isLoadingNextBatch = true
            try {
                val newBatch = getRecommendedBatch(allImages, batchSize)
                if (newBatch.isNotEmpty()) {
                    currentBatch = newBatch
                    currentIndex = 0
                    markedCount = 0
                    deckState = DeckState.BROWSING
                } else {
                    deckState = DeckState.COMPLETED
                }
            } finally {
                isLoadingNextBatch = false
            }
        }
    }
    
    /**
     * 处理图集/月份清理模式下的批次完成
     * 
     * 统一逻辑：标记组完成 → 清除断点 → 获取下一批 → 更新状态
     * 
     * 注意：此函数必须在协程中调用
     */
    suspend fun handleCleanupBatchCompletion() {
        if (albumCleanupEngine == null || currentCleanupBatch == null) {
            deckState = DeckState.COMPLETED
            return
        }
        
        // 标记当前批次的所有组为已处理
        albumCleanupEngine.markGroupsProcessed(currentCleanupBatch!!.groupIds)
        
        // 获取当前清理的 ID 和图片列表（支持图集和月份两种模式）
        val cleanupId = if (isMonthCleanupMode) selectedCleanupMonth?.cleanupAlbumId else selectedCleanupAlbum?.id
        val cleanupImages = if (isMonthCleanupMode) {
            selectedCleanupMonth?.images ?: emptyList()
        } else {
            selectedCleanupAlbum?.let { album ->
                resolveAlbumCleanupImages(album, allImagesForCleanup)
            } ?: emptyList()
        }
        
        // 清除断点（组已完成，不需要恢复了）
        cleanupId?.let { id ->
            albumCleanupEngine.clearCheckpoint(id)
        }
        lastSavedIndex = -1
        
        // 更新剩余组数和照片数，获取下一批
        if (cleanupId != null) {
            albumCleanupRemainingGroups = albumCleanupEngine.getRemainingGroups(cleanupId)
            albumCleanupRemainingImages = albumCleanupEngine.getRemainingImages(cleanupId)
            
            // 优先使用预加载的批次
            val nextBatch = if (preloadedNextBatch != null) {
                val batch = preloadedNextBatch
                preloadedNextBatch = null  // 清空预加载
                batch
            } else {
                // 没有预加载，实时获取
                albumCleanupEngine.getNextBatch(cleanupId, cleanupImages, latestSnapshot = allImagesForCleanup)
            }
            
            if (nextBatch != null) {
                currentCleanupBatch = nextBatch
                currentBatch = nextBatch.images
                currentIndex = 0
                markedCount = 0
            } else {
                showCleanupAvailability(cleanupId)
                onRefreshAlbumCleanupInfos()
            }
        } else {
            deckState = DeckState.COMPLETED
            onRefreshAlbumCleanupInfos()
        }
    }
    
    // C4/T07：回前台刷新（mediaAccessRevision）产生的新快照向可见消费面同步
    //（P2 复审项，纯逻辑见 ui/util/ForegroundRefreshSync）——完整媒体身份
    // 键（卷|ID，O5③ 同口径）。本 effect 声明在 handleCleanupBatchCompletion
    // 之后：不可访问的未处理组保持待恢复，不按用户完成标记。
    // · 普通批次：存活行替换为快照最新 ImageFile（dateModified/尺寸随外部
    //   编辑更新），失效行剔除、顺序保持、currentIndex 对齐（钳制同 deck
    //   移除路径约定）；剪空走 needsInitialBatch 重新取批。
    // · 清理批次：仅保留待处理成员完整可见的组，按组重建边界；缺席组
    //   保留持久处理意图，月份成员从完整新池重建。
    // · 大图查看器：viewerState.image 按新快照校验——身份仍存在则更新图片
    //   对象（保持查看位置），已失效则关闭查看器。
    androidx.compose.runtime.LaunchedEffect(allImages, allImagesForCleanup, isLoading) {
        val pool = if (allImagesForCleanup.isNotEmpty()) allImagesForCleanup else allImages
        // 尚未加载完成：等待首次加载。pool 为空且加载已完成 = 有效空快照（全部
        // 照片被外部删除/权限收窄至空）：各面按空池同步——查看器关闭、批次
        // 清空、清理会话转入持有（不标记完成、不清断点），访问恢复后续接
        if (pool.isEmpty() && isLoading) return@LaunchedEffect

        // C-2/C-3 顺序：预取与月份快照先同步，再处理活动批次。
        preloadedNextBatch?.let { batch ->
            val synced = syncCleanupBatchToSnapshot(batch, pool)
            // 预取批整批失效：直接丢弃（预取 effect 会在刷新后的池上重取；
            // 保留空批会被完成通道消费成空界面）
            preloadedNextBatch = if (synced.images.isEmpty()) null else synced
        }
        selectedCleanupMonth?.let { month ->
            selectedCleanupMonth = refreshedCleanupMonth(month, pool)
        }
        currentCleanupBatch?.let { batch ->
            val synced = syncCleanupBatchToSnapshot(batch, pool, currentBatch.drop(currentIndex))
            if (synced !== batch) {
                if (synced.images.isEmpty()) {
                    // 任务 A：批次行整体不可见 ≠ 用户完成——快照缺席区分不了
                    // 外部删除与权限收窄，不得按完成语义标记组。转入持有：
                    // 清运行态批次（恢复通道在访问恢复后按断点/最新快照续接）、
                    // 保留断点与未处理意图、隐藏不可操作照片
                    currentCleanupBatch = null
                    currentBatch = emptyList()
                    currentIndex = 0
                    deckState = DeckState.BROWSING
                } else {
                    currentCleanupBatch = synced
                }
            }
        }
        if (currentBatch.isNotEmpty()) {
            // deck 空间同步（C-1：与清理空间分列——deck 已剔除用户已处理行，
            // 清理批仍含之，两空间索引互不可算；索引按当前行身份在同步后
            // 列表中重定位）
            val refreshedDeck = syncRowsToSnapshot(currentBatch, pool)
            val allowedKeys = currentCleanupBatch?.images?.map { it.mediaIdentityKey() }?.toSet()
            val syncedDeck = if (isAlbumCleanupMode && allowedKeys != null) {
                refreshedDeck.filter { it.mediaIdentityKey() in allowedKeys }
            } else refreshedDeck
            if (syncedDeck != currentBatch) {
                if (syncedDeck.isEmpty()) {
                    // deck 可见行全部失效：清理模式下转入持有（同上，不标记
                    // 不清断点）；普通模式清空待重取
                    if (isAlbumCleanupMode && currentCleanupBatch != null) {
                        currentCleanupBatch = null
                        currentBatch = emptyList()
                        currentIndex = 0
                        deckState = DeckState.BROWSING
                    } else {
                        currentIndex = 0
                        currentBatch = syncedDeck
                        needsInitialBatch = true
                    }
                } else {
                    currentIndex = relocatedIndexAfterSync(currentBatch, syncedDeck, currentIndex)
                        .coerceIn(0, syncedDeck.lastIndex)
                    currentBatch = syncedDeck
                }
            }
        }

        viewerState?.let { state ->
            val refreshed = refreshedViewerImage(state.image, pool)
            if (refreshed == null) {
                viewerState = null
            } else if (refreshed != state.image) {
                viewerState = state.copy(image = refreshed)
            }
        }
    }

    // 图集/月份清理模式：开始下一组（图集模式可能切换到其他图集，月份模式只继续当前月份）
    fun startNextCleanupBatch() {
        if (isLoadingNextBatch || albumCleanupEngine == null) return
        
        scope.launch {
            isLoadingNextBatch = true
            try {
                // 获取当前清理的 ID 和图片列表
                val cleanupId: String?
                val cleanupImages: List<ImageFile>
                
                if (isMonthCleanupMode && selectedCleanupMonth != null) {
                    // 月份清理模式
                    cleanupId = selectedCleanupMonth?.cleanupAlbumId
                    cleanupImages = selectedCleanupMonth?.images ?: emptyList()
                } else if (!isMonthCleanupMode && selectedCleanupAlbum != null) {
                    // 图集清理模式
                    val album = selectedCleanupAlbum!!
                    cleanupId = album.id
                    cleanupImages = resolveAlbumCleanupImages(album, allImagesForCleanup)
                } else {
                    deckState = DeckState.COMPLETED
                    return@launch
                }
                
                if (cleanupId == null) {
                    deckState = DeckState.COMPLETED
                    return@launch
                }
                
                // 检查当前清理目标是否还有剩余组
                val remaining = albumCleanupEngine.getRemainingGroups(cleanupId)
                
                if (remaining > 0) {
                    // 还有剩余组，继续当前清理目标
                    val nextBatch = albumCleanupEngine.getNextBatch(cleanupId, cleanupImages, latestSnapshot = allImagesForCleanup)
                    if (nextBatch != null) {
                        currentCleanupBatch = nextBatch
                        currentBatch = nextBatch.images
                        currentIndex = 0
                        markedCount = 0
                        deckState = DeckState.BROWSING
                        
                        // 更新统计信息
                        albumCleanupRemainingGroups = albumCleanupEngine.getRemainingGroups(cleanupId)
                        albumCleanupRemainingImages = albumCleanupEngine.getRemainingImages(cleanupId)
                        return@launch
                    }
                    showCleanupAvailability(cleanupId)
                    return@launch
                }
                
                // 当前清理目标已完成
                if (isMonthCleanupMode) {
                    // 月份清理模式：直接显示完成，不自动切换到其他月份
                    deckState = DeckState.COMPLETED
                    return@launch
                }
                
                // 图集清理模式：查找其他有剩余组的图集
                onRefreshAlbumCleanupInfos()
                
                // 从 albumCleanupInfos 中筛选有剩余组的图集
                val availableAlbums = albumCleanupInfos.filter { info ->
                    !info.isCompleted && info.remainingGroups > 0
                }
                
                if (availableAlbums.isEmpty()) {
                    // 所有图集都已完成
                    deckState = DeckState.COMPLETED
                    return@launch
                }
                
                // 随机选择一个图集
                val randomInfo = availableAlbums.random()
                val nextAlbum = randomInfo.album
                
                // 切换到新图集
                onSelectedCleanupAlbumChange(nextAlbum)
                albumCleanupEngine.exitCleanupMode()  // 先退出当前模式
                preloadedNextBatch = null  // 清空旧图集的预加载数据
                
                // 获取新图集的图片
                val albumImages = resolveAlbumCleanupImages(nextAlbum, allImagesForCleanup)
                
                // 分析新图集（如果需要）
                if (randomInfo.totalGroups < 0) {
                    // 需要分析
                    analyzingAlbumId = nextAlbum.id
                    analysisProgress = 0f
                    albumCleanupEngine.analyzeAlbum(nextAlbum, albumImages).collect { progress ->
                        analysisProgress = progress
                    }
                    analyzingAlbumId = null
                }
                
                // 获取新批次
                val nextBatch = albumCleanupEngine.getNextBatch(nextAlbum.id, albumImages, latestSnapshot = allImagesForCleanup)
                if (nextBatch != null) {
                    currentCleanupBatch = nextBatch
                    currentBatch = nextBatch.images
                    currentIndex = 0
                    markedCount = 0
                    deckState = DeckState.BROWSING
                    
                    // 更新统计信息
                    albumCleanupTotalGroups = albumCleanupEngine.getTotalGroups(nextAlbum.id)
                    albumCleanupRemainingGroups = albumCleanupEngine.getRemainingGroups(nextAlbum.id)
                    albumCleanupTotalImages = albumCleanupEngine.getTotalImages(nextAlbum.id)
                    albumCleanupRemainingImages = albumCleanupEngine.getRemainingImages(nextAlbum.id)
                    
                    // 刷新图集清理信息
                    onRefreshAlbumCleanupInfos()
                } else {
                    showCleanupAvailability(nextAlbum.id)
                }
            } finally {
                isLoadingNextBatch = false
            }
        }
    }

    // 处理返回键（关闭查看器）
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }

    // ========== 预渲染优化：图集界面滑动过渡动画 ==========
    // 使用水平偏移实现"推入推出"效果
    // 卡片模式: slideProgress = 0f (卡片在中间，图集在右侧屏幕外)
    // 图集模式: slideProgress = 1f (图集在中间，卡片在左侧屏幕外)
    val slideProgress by animateFloatAsState(
        targetValue = if (isAlbumMode) 1f else 0f,
        animationSpec = tween(
            durationMillis = 280,  // 从 350ms 降到 280ms，减少双视图同时渲染的时间
            easing = FastOutSlowInEasing
        ),
        label = "slide_progress"
    )
    
    // 性能优化：记录是否曾进入过图集模式，一旦进入过就保持 AlbumsGridContent 在组合树中
    // 避免反复切换时的重组开销
    var hasEverEnteredAlbumMode by remember { mutableStateOf(isAlbumMode) }
    if (isAlbumMode) hasEverEnteredAlbumMode = true
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ========== 卡片模式内容（AnimatedContent 处理 loading/empty/completed/browsing）==========
        AnimatedContent(
            targetState = when {
                isLoading -> "loading"
                allImages.isEmpty() -> "empty"
                deckState == DeckState.COMPLETED -> "completed"
                currentBatch.isEmpty() -> "loading" // 等待批次加载
                else -> "browsing"
            },
            transitionSpec = {
                // 从 completed 切换到 browsing 时使用更快的动画，减少闪烁感
                when {
                    initialState == "completed" && targetState == "browsing" -> {
                        fadeIn(animationSpec = tween(150)) togetherWith 
                            fadeOut(animationSpec = tween(100))
                    }
                    else -> {
                        fadeIn(animationSpec = tween(300)) togetherWith 
                            fadeOut(animationSpec = tween(300))
                    }
                }
            },
            label = "deck_state",
            modifier = Modifier
                .fillMaxSize()
                // 卡片界面：从中间滑向左侧（slideProgress: 0→1 时，offsetX: 0→-30%）
                // 使用较小的偏移比例，让卡片"被推开"的感觉更自然
                .graphicsLayer { 
                    translationX = -size.width * 0.3f * slideProgress
                    // 轻微缩放，增加层次感
                    val scale = 1f - 0.05f * slideProgress
                    scaleX = scale
                    scaleY = scale
                    // 渐隐效果：完全移出时设为0跳过绘制
                    alpha = if (slideProgress >= 0.99f) 0f else 1f - 0.3f * slideProgress
                }
        ) { state ->
            when (state) {
                "loading" -> LoadingState(
                    progress = loadingProgress,
                    phase = loadingPhase,
                    scanned = loadingScanned,
                    total = loadingTotal
                )
                "empty" -> EmptyState()
                "completed" -> {
                    BatchCompletionScreen(
                        totalReviewed = currentBatch.size,
                        totalMarked = markedCount,
                        onContinue = { 
                            // 图集/月份清理模式使用专门的函数处理
                            if (isAlbumCleanupMode) {
                                startNextCleanupBatch()
                            } else {
                                startNewBatch()
                            }
                        },
                        onViewMarked = onNavigateToTrash,
                        isLoading = isLoadingNextBatch
                    )
                }
                else -> {
                    // 浏览模式
                    val currentImage = currentBatch.getOrNull(currentIndex)
                    
                    // 过滤掉隐藏的图集，用于下滑归类功能
                    DeckContent(
                        images = currentBatch,
                        currentIndex = currentIndex,
                        topBarDisplayMode = topBarDisplayMode,
                        showHdrBadges = showHdrBadges,
                        showMotionBadges = showMotionBadges,
                        enableSwipeHaptics = enableSwipeHaptics,
                        isAdaptiveCardStyle = isAdaptiveCardStyle,
                        swipeStyle = swipeStyle,
                        tagSelectionMode = tagSelectionMode,
                        tagsPerRow = tagsPerRow,
                        tagSwitchSpeed = tagSwitchSpeed,
                        albums = albums.filter { !it.isHidden },
                        currentImageAlbumIds = currentImageAlbumIds,
                        onIndexChange = { newIndex ->
                            // 统一批次完成判断：索引超出范围或批次为空
                            if (newIndex >= currentBatch.size || currentBatch.isEmpty()) {
                                // 图集清理模式：使用共用函数处理批次完成
                                if (isAlbumCleanupMode) {
                                    scope.launch { handleCleanupBatchCompletion() }
                                } else {
                                    // 普通模式：进入完成页面
                                    // 不额外触发振动，CardStack 滑动时已触发过
                                    deckState = DeckState.COMPLETED
                                }
                            } else {
                                if (newIndex > currentIndex) {
                                    onKeep()
                                }
                                // 防止空列表时索引越界
                                currentIndex = if (currentBatch.isEmpty()) {
                                    0
                                } else {
                                    newIndex.coerceIn(0, currentBatch.lastIndex)
                                }
                            }
                        },
                        onRemove = { image ->
                            // 如果已有待确认的删除操作，先提交它
                            if (isDeletePending) {
                                lastDeletedImage?.let { pendingImage ->
                                    onRemove(pendingImage)
                                }
                                lastDeletedImage = null
                                lastDeletedIndex = -1
                                isDeletePending = false
                            }
                            
                            // 记录撤销信息（在移除前记录索引位置）
                            val imageIndex = currentBatch.indexOf(image)
                            lastDeletedImage = image
                            lastDeletedIndex = imageIndex
                            isDeletePending = true
                            
                            // 显示撤销 Snackbar（延迟执行真正的删除）
                            undoMessage = "已移入回收站"
                            showUndoSnackbar = true
                            
                            markedCount++
                            // 删除后如果没有剩余
                            val newBatch = currentBatch.toMutableList().apply { remove(image) }
                            currentBatch = newBatch
                            if (currentIndex >= newBatch.size && newBatch.isNotEmpty()) {
                                currentIndex = newBatch.lastIndex
                            } else if (newBatch.isEmpty()) {
                                if (enableSwipeHaptics) {
                                    HapticFeedback.doubleTap(context)
                                }
                                // 图集/月份清理模式：使用共用函数处理批次完成
                                if (isAlbumCleanupMode) {
                                    scope.launch { handleCleanupBatchCompletion() }
                                } else {
                                    // 普通模式：进入完成页面
                                    deckState = DeckState.COMPLETED
                                }
                            }
                        },
                        onCardClick = { image, sourceRect ->
                            viewerState = ViewerState(image, sourceRect)
                        },
                        onTrashClick = onNavigateToTrash,
                        onSettingsClick = onNavigateToSettings,
                        onAlbumClick = { album ->
                            currentImage?.let { image ->
                                // 如果有待确认的删除操作，先提交它
                                if (isDeletePending) {
                                    lastDeletedImage?.let { pendingImage ->
                                        onRemove(pendingImage)
                                    }
                                    lastDeletedImage = null
                                    lastDeletedIndex = -1
                                    isDeletePending = false
                                }
                                
                                // 记录撤销信息
                                lastClassifiedImage = image
                                lastClassifiedIndex = currentIndex
                                lastClassifyWasSwipe = false
                                
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
                        onAddAlbumClick = { image ->
                            pendingClassifyImage = image  // 保存待归档的图片
                            showCreateAlbumDialog = true
                        },
                        onAlbumsClick = onNavigateToAlbums,
                        isAlbumMode = isAlbumMode,
                        onModeChange = onModeChange,
                        // 下滑归类专用回调：使用回调传入的 image 而不是 currentImage
                        // 因为 Genie 动画的 onComplete 是异步的，currentImage 可能已经变化
                        onSwipeClassifyToAlbum = { image, album ->
                            // 如果有待确认的删除操作，先提交它
                            if (isDeletePending) {
                                lastDeletedImage?.let { pendingImage ->
                                    onRemove(pendingImage)
                                }
                                lastDeletedImage = null
                                lastDeletedIndex = -1
                                isDeletePending = false
                            }
                            
                            // 记录撤销信息（在移除前记录索引位置）
                            val imageIndex = currentBatch.indexOf(image)
                            lastClassifiedImage = image
                            lastClassifiedIndex = imageIndex
                            lastClassifyWasSwipe = true
                            
                            onAlbumSelect(image.id, image.uri.toString(), album.id)
                            undoMessage = "已添加到「${album.name}」"
                            showUndoSnackbar = true
                            
                            // 从当前批次中移除已归类的图片
                            // 因为 Genie 动画已经播放了图片"飞走"的效果，用户预期图片已被处理
                            val newBatch = currentBatch.toMutableList().apply { remove(image) }
                            currentBatch = newBatch
                            
                            if (newBatch.isEmpty()) {
                                // 如果没有剩余图片
                                if (enableSwipeHaptics) {
                                    HapticFeedback.doubleTap(context)
                                }
                                // 图集/月份清理模式：使用共用函数处理批次完成
                                if (isAlbumCleanupMode) {
                                    scope.launch { handleCleanupBatchCompletion() }
                                } else {
                                    // 普通模式：进入完成页面
                                    deckState = DeckState.COMPLETED
                                }
                            } else if (currentIndex >= newBatch.size) {
                                // 如果当前索引超出范围，调整到最后一张
                                currentIndex = newBatch.lastIndex
                            }
                            // 不需要增加 currentIndex，因为图片已被移除，原来的下一张会自动变成当前位置的图片
                        },
                        // 下滑归类模式状态回调
                        onClassifyModeChange = { mode ->
                            isClassifyMode = mode
                        },
                        // 图集清理模式参数
                        isAlbumCleanupMode = isAlbumCleanupMode,
                        albumCleanupName = if (isMonthCleanupMode) selectedCleanupMonth?.displayName else selectedCleanupAlbum?.name,
                        albumCleanupTotalGroups = albumCleanupTotalGroups,
                        albumCleanupRemainingGroups = albumCleanupRemainingGroups,
                        albumCleanupTotalImages = albumCleanupTotalImages,
                        albumCleanupRemainingImages = albumCleanupRemainingImages,
                        albumCleanupDisplayMode = albumCleanupDisplayMode,
                        onAlbumCleanupDisplayModeToggle = {
                            // 切换显示模式并持久化保存
                            val newMode = when (albumCleanupDisplayMode) {
                                AlbumCleanupDisplayMode.GROUPS -> AlbumCleanupDisplayMode.PHOTOS
                                AlbumCleanupDisplayMode.PHOTOS -> AlbumCleanupDisplayMode.GROUPS
                            }
                            albumCleanupDisplayMode = newMode
                            appPreferences.albumCleanupDisplayMode = newMode
                        },
                        onAlbumCleanupClick = if (albumCleanupEngine != null) {
                            { 
                                // 打开选择弹窗前先刷新数据，确保显示的剩余组数与当前界面一致
                                onRefreshAlbumCleanupInfos()
                                showAlbumCleanupSheet = true 
                            }
                        } else null,
                        // LIST_POPUP 模式：下滑触发图集选择弹层
                        onShowAlbumSelectionSheet = {
                            showAlbumSelectionSheet = true
                        }
                    )
                }
            }
        }

        // ========== 预渲染的图集界面 ==========
        // 性能优化：一旦进入过图集模式，保持 AlbumsGridContent 在组合树中
        // 后续切换时只需移动 graphicsLayer 位置，无需重新组合，消除切换卡顿
        if (hasEverEnteredAlbumMode || slideProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 图集界面：从右侧滑向中间（slideProgress: 0→1 时，offsetX: 100%→0）
                    .graphicsLayer { 
                        translationX = size.width * (1f - slideProgress)
                        // 渐显效果，让出现更柔和
                        alpha = slideProgress
                    }
            ) {
                AlbumsGridContent(
                    albums = albums,
                    systemBuckets = systemBuckets,
                    allImages = allImages,
                    onCreateAlbum = onCreateAlbum,
                    onAlbumClick = onNavigateToAlbumDetail,
                    onSystemBucketClick = onSystemBucketClick,
                    onReorderAlbums = onReorderAlbums,
                    onHideAlbum = onHideAlbum,
                    onExcludeAlbum = onExcludeAlbum,
                    isAlbumExcluded = isAlbumExcluded,
                    onSyncClick = onSyncClick,
                    onSettingsClick = onNavigateToSettings,
                    showHiddenAlbums = showHiddenAlbums,
                    onToggleShowHidden = onToggleShowHidden,
                    onNavigateToOtherAlbums = onNavigateToOtherAlbums,
                    pendingCleanupCount = pendingCleanupCount,
                    onCleanupSourceImages = onCleanupSourceImages
                )
            }
        }

        // 底部模式切换器 (悬浮) - 在所有模式下都显示，但下滑归类时隐藏
        // 移到顶层，确保在图片模式和图集模式下都可见
        androidx.compose.animation.AnimatedVisibility(
            visible = !isLoading && allImages.isNotEmpty() && !isClassifyMode && deckState != DeckState.COMPLETED,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color.Transparent)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                ModeToggle(
                    isAlbumMode = isAlbumMode,
                    onModeChange = onModeChange
                )
            }
        }

        // ========== 快捷操作按钮 ==========
        QuickActionButton(
            visible = quickActionEnabled && 
                !isLoading && 
                currentBatch.isNotEmpty() && 
                !isClassifyMode && 
                !isAlbumMode &&
                deckState == DeckState.BROWSING &&
                viewerState == null,
            hasPrevious = currentIndex > 0,
            hasNext = currentIndex < currentBatch.lastIndex,
            positionX = leftHandButtonX,  // 使用统一的位置设置
            positionY = leftHandButtonY,
            safeArea = safeArea,
            onPrevious = {
                if (currentIndex > 0) {
                    currentIndex--
                }
            },
            onNext = {
                if (currentIndex < currentBatch.lastIndex) {
                    onKeep()
                    currentIndex++
                }
            },
            onPositionChanged = { x, y ->
                onQuickActionPositionChanged(true, x, y)
            }
        )

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

        // 撤销 Snackbar（支持归档撤销和删除撤销）
        UndoSnackbar(
            visible = showUndoSnackbar,
            message = undoMessage,
            onUndo = {
                if (isDeletePending) {
                    // ========== 删除撤销 ==========
                    lastDeletedImage?.let { image ->
                        // 检查图片是否已在批次中（防止重复插入）
                        val isDuplicate = currentBatch.any { it.id == image.id }
                        
                        if (!isDuplicate) {
                            // 将图片重新插入 batch
                            val newBatch = currentBatch.toMutableList()
                            // 使用安全的插入索引：如果原索引仍有效则使用，否则插入到当前位置
                            val insertIndex = if (lastDeletedIndex >= 0 && lastDeletedIndex <= newBatch.size) {
                                lastDeletedIndex
                            } else {
                                // 原索引无效，插入到当前位置或末尾
                                currentIndex.coerceIn(0, newBatch.size)
                            }
                            newBatch.add(insertIndex, image)
                            currentBatch = newBatch
                            currentIndex = insertIndex
                            // 减少删除计数（因为撤销了）
                            markedCount = (markedCount - 1).coerceAtLeast(0)
                            // 如果之前进入了完成状态，恢复浏览状态
                            if (deckState == DeckState.COMPLETED) {
                                deckState = DeckState.BROWSING
                            }
                        }
                    }
                    
                    // 清理删除撤销状态
                    lastDeletedImage = null
                    lastDeletedIndex = -1
                    isDeletePending = false
                } else {
                    // ========== 归档撤销 ==========
                    // 执行数据层撤销（取消待执行的归档操作）
                    onUndoAlbumAction()
                    
                    // 恢复 UI 状态
                    lastClassifiedImage?.let { image ->
                        // 检查图片是否已在批次中（防止重复插入）
                        val isDuplicate = currentBatch.any { it.id == image.id }
                        
                        if (lastClassifyWasSwipe && !isDuplicate) {
                            // 下滑归类：将图片重新插入 batch
                            val newBatch = currentBatch.toMutableList()
                            // 使用安全的插入索引：如果原索引仍有效则使用，否则插入到当前位置
                            val insertIndex = if (lastClassifiedIndex >= 0 && lastClassifiedIndex <= newBatch.size) {
                                lastClassifiedIndex
                            } else {
                                // 原索引无效，插入到当前位置或末尾
                                currentIndex.coerceIn(0, newBatch.size)
                            }
                            newBatch.add(insertIndex, image)
                            currentBatch = newBatch
                            currentIndex = insertIndex
                            // 如果之前进入了完成状态，恢复浏览状态
                            if (deckState == DeckState.COMPLETED) {
                                deckState = DeckState.BROWSING
                            }
                        } else if (!lastClassifyWasSwipe) {
                            // 点击 Chip 归类：回退索引
                            if (currentIndex > 0 && currentIndex > lastClassifiedIndex) {
                                currentIndex = lastClassifiedIndex
                            }
                        }
                    }
                    
                    // 清理归档撤销状态
                    lastClassifiedImage = null
                    lastClassifiedIndex = -1
                }
                showUndoSnackbar = false
            },
            onDismiss = { 
                if (isDeletePending) {
                    // ========== 确认删除 ==========
                    // Snackbar 超时消失 = 用户确认删除
                    // 执行真正的删除操作
                    lastDeletedImage?.let { image ->
                        onRemove(image)
                    }
                    
                    // 清理删除撤销状态
                    lastDeletedImage = null
                    lastDeletedIndex = -1
                    isDeletePending = false
                } else {
                    // ========== 确认归档 ==========
                    // Snackbar 超时消失 = 用户确认归档
                    // 执行真正的复制操作
                    onCommitArchive()
                    
                    // 清理归档撤销状态
                    lastClassifiedImage = null
                    lastClassifiedIndex = -1
                }
                showUndoSnackbar = false 
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // LIST_POPUP 模式：图集选择弹层（过滤掉隐藏的图集）
    if (showAlbumSelectionSheet) {
        val currentImage = currentBatch.getOrNull(currentIndex)
        AlbumSelectionSheet(
            albums = albums.filter { !it.isHidden },
            allImages = allImages,
            onAlbumSelected = { album ->
                showAlbumSelectionSheet = false
                currentImage?.let { image ->
                    // 如果有待确认的删除操作，先提交它
                    if (isDeletePending) {
                        lastDeletedImage?.let { pendingImage ->
                            onRemove(pendingImage)
                        }
                        lastDeletedImage = null
                        lastDeletedIndex = -1
                        isDeletePending = false
                    }
                    
                    // 记录撤销信息
                    lastClassifiedImage = image
                    lastClassifiedIndex = currentIndex
                    lastClassifyWasSwipe = true
                    
                    onAlbumSelect(image.id, image.uri.toString(), album.id)
                    undoMessage = "已添加到「${album.name}」"
                    showUndoSnackbar = true
                    
                    // 从当前批次中移除已归类的图片
                    val newBatch = currentBatch.toMutableList().apply { remove(image) }
                    currentBatch = newBatch
                    
                    if (newBatch.isEmpty()) {
                        if (enableSwipeHaptics) {
                            HapticFeedback.doubleTap(context)
                        }
                        deckState = DeckState.COMPLETED
                    } else if (currentIndex >= newBatch.size) {
                        currentIndex = newBatch.lastIndex
                    }
                }
            },
            onCreateNewAlbum = {
                showAlbumSelectionSheet = false
                pendingClassifyImage = currentImage
                showCreateAlbumDialog = true
            },
            onDismiss = {
                showAlbumSelectionSheet = false
            }
        )
    }

    // 新建相册对话框
    if (showCreateAlbumDialog) {
        AlbumEditDialog(
            isEdit = false,
            existingAlbumNames = albums.map { it.name },
            onConfirm = { name, color, emoji ->
                val pending = pendingClassifyImage
                if (pending != null) {
                    // 如果有待确认的删除操作，先提交它
                    if (isDeletePending) {
                        lastDeletedImage?.let { pendingImage ->
                            onRemove(pendingImage)
                        }
                        lastDeletedImage = null
                        lastDeletedIndex = -1
                        isDeletePending = false
                    }
                    
                    // 有待归档的图片，创建图集后自动归档
                    onCreateAlbumAndClassify(name, color, emoji, pending)
                    pendingClassifyImage = null
                    
                    // 显示成功提示
                    undoMessage = "已添加到「$name」"
                    showUndoSnackbar = true
                    
                    // 移除当前图片并切换到下一张
                    val imageIndex = currentBatch.indexOf(pending)
                    lastClassifiedImage = pending
                    lastClassifiedIndex = imageIndex
                    lastClassifyWasSwipe = true
                    
                    if (imageIndex >= 0) {
                        currentBatch = currentBatch.toMutableList().also { it.removeAt(imageIndex) }
                        if (currentIndex >= currentBatch.size && currentIndex > 0) {
                            currentIndex = currentBatch.size - 1
                        }
                    }
                } else {
                    // 普通新建图集
                    onCreateAlbum(name, color, emoji)
                }
                showCreateAlbumDialog = false
            },
            onDismiss = { 
                showCreateAlbumDialog = false
                pendingClassifyImage = null  // 取消时清除待归档图片
            }
        )
    }
    
    // 图集清理选择弹窗
    // 抽取图集清理启动逻辑为共用函数
    fun startAlbumCleanup(album: Album, forceReset: Boolean = false) {
        val engine = albumCleanupEngine ?: return  // 确保 engine 非空
        
        // 取消之前的分析任务（避免快速切换时状态混乱）
        currentAnalysisJob?.cancel()
        
        // 重置月份清理模式标识（确保是图集清理模式）
        isMonthCleanupMode = false
        selectedCleanupMonth = null
        
        // 记录当前选中的 albumId，用于在协程中校验
        val targetAlbumId = album.id
        
        currentAnalysisJob = scope.launch {
            analyzingAlbumId = album.id
            analysisProgress = 0f
            
            // 如果是重新整理，先重置状态
            if (forceReset) {
                engine.resetAlbumState(album.id)
            }
            
            // 获取图集内的图片
            val albumImages = resolveAlbumCleanupImages(album, allImagesForCleanup)
            
            if (albumImages.isEmpty() && (forceReset || engine.getTotalGroups(album.id) < 0)) {
                analyzingAlbumId = null
                Toast.makeText(context, "该图集没有可清理的照片", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // Preserve a complete persisted directory when current access is partial.
            if (forceReset || engine.getTotalGroups(album.id) < 0) {
                engine.analyzeAlbum(album, albumImages).collect { progress ->
                    analysisProgress = progress
                }
            }
            
            analyzingAlbumId = null
            
            // 如果是强制重置，跳过断点恢复
            val checkpointResult = if (forceReset) {
                null
            } else {
                engine.getCheckpointBatch(album.id, albumImages, latestSnapshot = allImagesForCleanup)
            }
            
            if (checkpointResult != null) {
                // 有断点，从断点恢复
                val (batch, savedIndex) = checkpointResult
                onAlbumCleanupModeChange(true)
                currentCleanupBatch = batch
                currentBatch = batch.images
                currentIndex = savedIndex
                lastSavedIndex = savedIndex
                markedCount = 0
                deckState = DeckState.BROWSING
                
                albumCleanupTotalGroups = engine.getTotalGroups(album.id)
                albumCleanupRemainingGroups = engine.getRemainingGroups(album.id)
                albumCleanupTotalImages = engine.getTotalImages(album.id)
                albumCleanupRemainingImages = engine.getRemainingImages(album.id)
                
                android.util.Log.d("DeckScreen", "Restored from checkpoint: index=$savedIndex")
            } else {
                // 没有断点，正常获取下一批
                val batch = engine.getNextBatch(album.id, albumImages, latestSnapshot = allImagesForCleanup)
                if (batch != null) {
                    onAlbumCleanupModeChange(true)
                    currentCleanupBatch = batch
                    currentBatch = batch.images
                    currentIndex = 0
                    lastSavedIndex = -1
                    markedCount = 0
                    deckState = DeckState.BROWSING
                    
                    albumCleanupTotalGroups = engine.getTotalGroups(album.id)
                    albumCleanupRemainingGroups = engine.getRemainingGroups(album.id)
                    albumCleanupTotalImages = engine.getTotalImages(album.id)
                    albumCleanupRemainingImages = engine.getRemainingImages(album.id)
                } else {
                    // 没有照片需要整理
                    albumCleanupTotalGroups = engine.getTotalGroups(album.id)
                    albumCleanupRemainingGroups = engine.getRemainingGroups(album.id)
                    albumCleanupTotalImages = engine.getTotalImages(album.id)
                    albumCleanupRemainingImages = engine.getRemainingImages(album.id)
                    
                    onAlbumCleanupModeChange(true)
                    onSelectedCleanupAlbumChange(album)
                    showCleanupAvailability(album.id)
                }
            }
            
            // 刷新图集清理信息
            onRefreshAlbumCleanupInfos()
            showAlbumCleanupSheet = false
        }
    }
    
    // 月份清理启动函数
    fun startMonthCleanup(monthInfo: MonthInfo, forceReset: Boolean = false) {
        val engine = albumCleanupEngine ?: return
        
        // 取消之前的分析任务（避免快速切换时状态混乱）
        currentAnalysisJob?.cancel()
        
        currentAnalysisJob = scope.launch {
            val virtualAlbum = monthInfo.toVirtualAlbum()
            val albumId = virtualAlbum.id
            
            analyzingAlbumId = albumId
            analysisProgress = 0f
            
            // 如果是重新整理，先重置状态
            if (forceReset) {
                engine.resetAlbumState(albumId)
            }
            
            // 使用月份的图片列表
            val monthImages = monthInfo.images
            
            if (monthImages.isEmpty() && (forceReset || engine.getTotalGroups(albumId) < 0)) {
                analyzingAlbumId = null
                return@launch
            }
            
            android.util.Log.d("DeckScreen", "Starting month cleanup: ${monthInfo.displayName}, ${monthImages.size} images")
            
            if (forceReset || engine.getTotalGroups(albumId) < 0) {
                engine.analyzeAlbum(virtualAlbum, monthImages).collect { progress ->
                    analysisProgress = progress
                }
            }
            
            analyzingAlbumId = null
            
            // 如果是强制重置，跳过断点恢复
            val checkpointResult = if (forceReset) {
                null
            } else {
                engine.getCheckpointBatch(albumId, monthImages, latestSnapshot = allImagesForCleanup)
            }
            
            if (checkpointResult != null) {
                // 有断点，从断点恢复
                val (batch, savedIndex) = checkpointResult
                onAlbumCleanupModeChange(true)
                isMonthCleanupMode = true
                currentCleanupBatch = batch
                currentBatch = batch.images
                currentIndex = savedIndex
                lastSavedIndex = savedIndex
                markedCount = 0
                deckState = DeckState.BROWSING
                
                albumCleanupTotalGroups = engine.getTotalGroups(albumId)
                albumCleanupRemainingGroups = engine.getRemainingGroups(albumId)
                albumCleanupTotalImages = engine.getTotalImages(albumId)
                albumCleanupRemainingImages = engine.getRemainingImages(albumId)
                
                android.util.Log.d("DeckScreen", "Month cleanup restored from checkpoint: index=$savedIndex")
            } else {
                // 没有断点，正常获取下一批
                val batch = engine.getNextBatch(albumId, monthImages, latestSnapshot = allImagesForCleanup)
                if (batch != null) {
                    onAlbumCleanupModeChange(true)
                    isMonthCleanupMode = true
                    currentCleanupBatch = batch
                    currentBatch = batch.images
                    currentIndex = 0
                    lastSavedIndex = -1
                    markedCount = 0
                    deckState = DeckState.BROWSING
                    
                    albumCleanupTotalGroups = engine.getTotalGroups(albumId)
                    albumCleanupRemainingGroups = engine.getRemainingGroups(albumId)
                    albumCleanupTotalImages = engine.getTotalImages(albumId)
                    albumCleanupRemainingImages = engine.getRemainingImages(albumId)
                    
                    android.util.Log.d("DeckScreen", "Month cleanup started: ${batch.imageCount} images in ${batch.groupCount} groups")
                } else {
                    // 没有照片需要整理
                    albumCleanupTotalGroups = engine.getTotalGroups(albumId)
                    albumCleanupRemainingGroups = engine.getRemainingGroups(albumId)
                    albumCleanupTotalImages = engine.getTotalImages(albumId)
                    albumCleanupRemainingImages = engine.getRemainingImages(albumId)
                    
                    onAlbumCleanupModeChange(true)
                    isMonthCleanupMode = true
                    showCleanupAvailability(albumId)
                }
            }
            
            showAlbumCleanupSheet = false
        }
    }
    
    if (showAlbumCleanupSheet && albumCleanupEngine != null) {
        AlbumCleanupBottomSheet(
            albums = albums,
            albumCleanupInfos = albumCleanupInfos,
            selectedAlbumId = selectedCleanupAlbum?.id,
            analyzingAlbumId = analyzingAlbumId,
            analysisProgress = analysisProgress,
            onSelectAlbum = { album ->
                onSelectedCleanupAlbumChange(album)
            },
            onSwitchToGlobal = {
                // 保存断点（如果当前正在清理图集或月份）
                if (isAlbumCleanupMode && currentCleanupBatch != null) {
                    albumCleanupEngine.saveCheckpoint(currentCleanupBatch!!.groupIds, currentIndex)
                }
                
                // 切换回全局整理模式
                onAlbumCleanupModeChange(false)
                onSelectedCleanupAlbumChange(null)
                selectedCleanupMonth = null  // 重置月份选择
                isMonthCleanupMode = false   // 重置月份清理模式标识
                currentCleanupBatch = null
                lastSavedIndex = -1
                albumCleanupEngine.exitCleanupMode()
                showAlbumCleanupSheet = false
                // 清空当前批次并重置索引，触发 LaunchedEffect 重新加载全局批次
                currentBatch = emptyList()
                currentIndex = 0
                markedCount = 0
                deckState = DeckState.BROWSING  // 重置为浏览状态，避免显示"完成"页面
                needsInitialBatch = true
            },
            onDismiss = {
                showAlbumCleanupSheet = false
            },
            onStartCleanup = {
                selectedCleanupAlbum?.let { album ->
                    startAlbumCleanup(album, forceReset = false)
                }
            },
            onResetAndStartCleanup = { album ->
                // 重置并重新开始清理（用于已完成的图集）
                onSelectedCleanupAlbumChange(album)
                startAlbumCleanup(album, forceReset = true)
            },
            // 月份专清参数
            allImages = allImagesForCleanup,
            selectedMonthId = selectedCleanupMonth?.id,
            onSelectMonth = { monthInfo ->
                selectedCleanupMonth = monthInfo
            },
            onStartMonthCleanup = {
                selectedCleanupMonth?.let { month ->
                    startMonthCleanup(month, forceReset = false)
                }
            },
            onResetAndStartMonthCleanup = { month ->
                // 重置并重新开始月份清理（用于已完成的月份）
                selectedCleanupMonth = month
                startMonthCleanup(month, forceReset = true)
            }
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
    isAdaptiveCardStyle: Boolean,
    swipeStyle: SwipeStyle,
    tagSelectionMode: TagSelectionMode = TagSelectionMode.SWIPE_AUTO,
    tagsPerRow: Int = TAGS_PER_ROW,
    tagSwitchSpeed: Float = 1.0f,
    albums: List<Album>,
    currentImageAlbumIds: Set<String>,
    onIndexChange: (Int) -> Unit,
    onRemove: (ImageFile) -> Unit,
    onCardClick: (ImageFile, SourceRect) -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAddAlbumClick: (ImageFile?) -> Unit,  // 可选参数：待归档的图片
    onAlbumsClick: () -> Unit,
    isAlbumMode: Boolean = false,
    onModeChange: (Boolean) -> Unit = {},
    // 下滑归类专用回调（携带图片信息，避免异步回调时 currentImage 变化的问题）
    onSwipeClassifyToAlbum: (ImageFile, Album) -> Unit = { _, album -> onAlbumClick(album) },
    // 下滑归类模式状态回调
    onClassifyModeChange: (Boolean) -> Unit = {},
    // 固定标签点击模式的归类回调
    onFixedTagAlbumClick: (Album) -> Unit = onAlbumClick,
    // LIST_POPUP 模式：下滑触发弹层回调
    onShowAlbumSelectionSheet: (() -> Unit)? = null,
    // 图集清理模式参数
    isAlbumCleanupMode: Boolean = false,
    albumCleanupName: String? = null,
    albumCleanupTotalGroups: Int = 0,
    albumCleanupRemainingGroups: Int = 0,
    albumCleanupTotalImages: Int = 0,
    albumCleanupRemainingImages: Int = 0,
    albumCleanupDisplayMode: AlbumCleanupDisplayMode = AlbumCleanupDisplayMode.GROUPS,
    onAlbumCleanupDisplayModeToggle: (() -> Unit)? = null,
    onAlbumCleanupClick: (() -> Unit)? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val shareState = remember { CardShareState() }
    val sharePhoto = rememberPhotoShareAction()

    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < images.lastIndex
    val remaining = (images.size - currentIndex - 1).coerceAtLeast(0)
    val currentImage = images.getOrNull(currentIndex)
    
    // 下滑归类模式状态
    var isClassifyMode by remember { mutableStateOf(false) }
    // 默认选中第一个相册（索引1），因为索引0是新建按钮
    var selectedAlbumIndex by remember(albums) { mutableIntStateOf(if (albums.isNotEmpty()) 1 else 0) }
    
    // 标签位置映射（索引 -> TagPosition）
    // 使用 mutableStateMapOf 代替 mutableStateOf<Map>，避免每次更新单个标签位置时触发整体重组
    val tagPositions = remember { mutableStateMapOf<Int, TagPosition>() }
    
    // 回收站按钮位置（用于上滑删除的 Genie 动画目标点）
    var trashButtonBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val genieController = rememberGenieAnimationController()
    
    // 固定标签点击模式：触发 Genie 动画的目标索引和相册
    var fixedTagTriggerIndex by remember { mutableStateOf<Int?>(null) }
    var pendingFixedTagAlbum by remember { mutableStateOf<Album?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部栏（显示时间或索引）
            TopBar(
                currentIndex = currentIndex,
                totalCount = images.size,
                currentImage = currentImage,
                displayMode = topBarDisplayMode,
                onTrashClick = onTrashClick,
                onSettingsClick = onSettingsClick,
                onShareClick = { shareState.snapshot()?.let(sharePhoto) },
                shareEnabled = currentImage != null && shareState.isAvailable,
                onTrashButtonBoundsChanged = { bounds ->
                    trashButtonBounds = bounds
                },
                // 图集清理模式参数
                isAlbumCleanupMode = isAlbumCleanupMode,
                albumCleanupName = albumCleanupName,
                albumCleanupTotalGroups = albumCleanupTotalGroups,
                albumCleanupRemainingGroups = albumCleanupRemainingGroups,
                albumCleanupTotalImages = albumCleanupTotalImages,
                albumCleanupRemainingImages = albumCleanupRemainingImages,
                albumCleanupDisplayMode = albumCleanupDisplayMode,
                onAlbumCleanupDisplayModeToggle = onAlbumCleanupDisplayModeToggle,
                onAlbumCleanupClick = onAlbumCleanupClick
            )

            // 卡片堆叠区域
            // 固定标签模式下有双行标签，卡片位置较高；其他模式卡片可以更靠下
            val cardTopPadding = if (tagSelectionMode == TagSelectionMode.FIXED_TAP) 32.dp else 48.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = cardTopPadding),  // 卡片向下移动
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
                        modifier = Modifier.fillMaxSize(),
                        isAdaptiveCardStyle = isAdaptiveCardStyle,
                        swipeStyle = swipeStyle,
                        // 下滑归类相关参数
                        // SWIPE_AUTO: 启用下滑归类手势
                        // FIXED_TAP: 禁用下滑手势
                        // LIST_POPUP: 禁用下滑归类，但通过 onDownSwipeTrigger 触发弹层
                        enableDownSwipeClassify = tagSelectionMode == TagSelectionMode.SWIPE_AUTO,
                        onDownSwipeTrigger = if (tagSelectionMode == TagSelectionMode.LIST_POPUP) onShowAlbumSelectionSheet else null,
                        albums = albums,
                        onClassifyToAlbum = { image, album ->
                            // 将图片添加到图集（使用新的下滑归类回调）
                            onSwipeClassifyToAlbum(image, album)
                        },
                        onCreateNewAlbum = { image ->
                            // 打开新建图集对话框，并传递待归档的图片
                            onAddAlbumClick(image)
                        },
                        onClassifyModeChange = { mode ->
                            isClassifyMode = mode
                            onClassifyModeChange(mode)  // 同步到外部
                            if (mode) {
                                // 进入归类模式时，默认选中第一个标签（新建后面的）
                                selectedAlbumIndex = if (albums.isNotEmpty()) 1 else 0
                            }
                        },
                        onSelectedIndexChange = { index ->
                            selectedAlbumIndex = index
                        },
                        // 传递标签位置
                        tagPositions = tagPositions,
                        // 每行标签数量和切换速度
                        tagsPerRow = tagsPerRow,
                        tagSwitchSpeed = tagSwitchSpeed,
                        // 传递回收站按钮位置（用于上滑删除的 Genie 动画）
                        trashButtonBounds = trashButtonBounds,
                        // 固定标签点击模式：外部触发 Genie 动画
                        fixedTagTriggerIndex = fixedTagTriggerIndex,
                        onFixedTagAnimationComplete = {
                            // 动画完成后，执行归类回调
                            pendingFixedTagAlbum?.let { album ->
                                currentImage?.let { image ->
                                    onSwipeClassifyToAlbum(image, album)
                                }
                            }
                            // 重置状态
                            fixedTagTriggerIndex = null
                            pendingFixedTagAlbum = null
                        },
                        genieController = genieController,
                        shareState = shareState
                    )
                }
            }

            // 底部固定高度区域 - 保持布局稳定，避免下滑时整体移动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp) // 固定高度（增加高度以避免标签被遮挡）
            ) {
                // ========== 模式一：下滑自动选择 ==========
                if (tagSelectionMode == TagSelectionMode.SWIPE_AUTO) {
                    // 正常模式：显示剩余提示
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isClassifyMode,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 卡片下方统一显示当前批次剩余张数
                            // 图集清理模式的组数信息已在左上角TopBar显示
                            val statusText = if (isAlbumCleanupMode) {
                                "这一组还剩 $remaining 张照片"
                            } else {
                                "${remaining} 张待整理"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                            // 底部留白，为切换按钮预留空间
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }
                    
                    // 归类模式：显示标签选择器
                    // 使用 AnimatedVisibility 控制整个显示/隐藏，确保退出动画能正常执行
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isClassifyMode,
                        enter = androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 250,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            )
                        ) + androidx.compose.animation.slideInVertically(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 300,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            ),
                            initialOffsetY = { it / 2 }  // 从下方滑入
                        ) + androidx.compose.animation.scaleIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 250,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            ),
                            initialScale = 0.92f
                        ),
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 200,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            )
                        ) + androidx.compose.animation.slideOutVertically(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 250,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            ),
                            targetOffsetY = { it / 3 }  // 向下滑出
                        ) + androidx.compose.animation.scaleOut(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 200,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            ),
                            targetScale = 0.95f
                        )
                    ) {
                        AlbumDropTarget(
                            albums = albums,
                            selectedIndex = selectedAlbumIndex,
                            onTagPositionChanged = { index, tagPosition ->
                                // 直接更新 Map，mutableStateMapOf 只会触发依赖该 key 的重组
                                tagPositions[index] = tagPosition
                            },
                            modifier = Modifier.fillMaxWidth(),
                            tagsPerRow = tagsPerRow
                        )
                    }
                }
                
                // ========== 模式二：固定标签点击 ==========
                if (tagSelectionMode == TagSelectionMode.FIXED_TAP) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 剩余提示（减少间距让标签往上）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 卡片下方统一显示当前批次剩余张数
                            val statusText = if (isAlbumCleanupMode) {
                                "这一组还剩 $remaining 张照片"
                            } else {
                                "${remaining} 张待整理"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                        
                        // 固定标签栏（过滤掉隐藏的图集）
                        val visibleAlbums = albums.filter { !it.isHidden }
                        FixedTagBar(
                            albums = visibleAlbums,
                            onAlbumClick = { album ->
                                currentImage?.let { image ->
                                    // 找到相册在可见列表中的索引（+1 因为索引0是新建按钮）
                                    val albumIndex = visibleAlbums.indexOf(album) + 1
                                    // 设置待归类的相册和触发索引
                                    pendingFixedTagAlbum = album
                                    fixedTagTriggerIndex = albumIndex
                                }
                            },
                            onCreateNewAlbumClick = {
                                onAddAlbumClick(currentImage)
                            },
                            onTagPositionChanged = { index, tagPosition ->
                                // 直接更新 Map，mutableStateMapOf 只会触发依赖该 key 的重组
                                tagPositions[index] = tagPosition
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // 底部留白，为模式切换按钮预留空间
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                
                // ========== 模式三：弹层列表选择 ==========
                if (tagSelectionMode == TagSelectionMode.LIST_POPUP) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 剩余提示 + 下滑归类提示
                        val statusText = if (isAlbumCleanupMode) {
                            "这一组还剩 $remaining 张照片"
                        } else {
                            "${remaining} 张待整理"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = textColor.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "↓ 下滑选择图集",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.35f),
                            fontSize = 11.sp
                        )
                        // 底部留白，为切换按钮预留空间
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
        }
        
        // 底部模式切换器已移至 DeckScreen 顶层，确保在图片和图集模式下都可见
        if (genieController.isAnimating) {
            GenieEffectOverlay(
                bitmap = genieController.bitmap,
                sourceBounds = genieController.sourceBounds,
                targetX = genieController.targetX,
                targetY = genieController.targetY,
                progress = genieController.progress,
                screenHeight = genieController.screenHeight,
                direction = genieController.direction,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 加载状态（带进度条）
 *
 * 显示阶段化的加载进度：
 * - 扫描照片 (0~80%)：显示 "正在扫描照片... 3,247 / 10,532"
 * - 加载图集 (80~95%)：显示 "正在加载图集..."
 * - 即将完成 (95~100%)：显示 "即将完成..."
 */
@Composable
private fun LoadingState(
    progress: Float = 0f,
    phase: String = "正在扫描照片...",
    scanned: Int = 0,
    total: Int = 0
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val progressBarColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val trackColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    // 平滑动画过渡（避免进度条跳动）
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "loading_progress"
    )

    // 构建副标题文字
    val subtitleText = if (total > 0 && progress < 0.8f) {
        val formatter = java.text.NumberFormat.getIntegerInstance()
        "${formatter.format(scanned)} / ${formatter.format(total)}"
    } else {
        null
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Tabula Logo",
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = progressBarColor,
                trackColor = trackColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 阶段描述
            Text(
                text = phase,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )

            // 数量副标题（仅扫描阶段显示）
            if (subtitleText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
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
 * 将图集列表按收纳规则分为"显示列表"和"其他列表"
 *
 * 收纳规则：
 * 1. 系统图集总数（非 Tabula 创建）超过阈值时启用
 * 2. 图片数 <= smallThreshold 的非 Tabula 图集被收纳
 * 3. 用户 pin 过的图集始终显示
 * 4. 被收纳的图集不超过总数的 80%
 * 5. Tabula 创建的图集（路径以 Pictures/Tabula/ 开头）永不被收纳
 */
private fun consolidateAlbums(
    visibleAlbums: List<Album>,
    pinnedNames: Set<String>
): Pair<List<Album>, List<Album>> {
    // 区分 Tabula 图集和系统图集
    val tabulaAlbums = visibleAlbums.filter {
        it.systemAlbumPath?.startsWith("Pictures/Tabula/", ignoreCase = true) == true
    }
    val systemAlbums = visibleAlbums.filter {
        it.systemAlbumPath?.startsWith("Pictures/Tabula/", ignoreCase = true) != true
    }

    // 系统图集数量未达到阈值，不收纳
    if (systemAlbums.size <= AppPreferences.OTHER_ALBUMS_TRIGGER_THRESHOLD) {
        return visibleAlbums to emptyList()
    }

    // 按图片数量分组
    var smallThreshold = AppPreferences.OTHER_ALBUMS_SMALL_THRESHOLD
    var candidatesForOther = systemAlbums.filter { album ->
        album.imageCount <= smallThreshold && album.name !in pinnedNames
    }

    // 保护：如果收纳比例超过上限，逐步降低阈值
    while (candidatesForOther.size > (systemAlbums.size * AppPreferences.OTHER_ALBUMS_MAX_RATIO).toInt()
        && smallThreshold > 1
    ) {
        smallThreshold--
        candidatesForOther = systemAlbums.filter { album ->
            album.imageCount <= smallThreshold && album.name !in pinnedNames
        }
    }

    // 如果没有需要收纳的，全部显示
    if (candidatesForOther.isEmpty()) {
        return visibleAlbums to emptyList()
    }

    val otherIds = candidatesForOther.map { it.id }.toSet()
    val displayedSystemAlbums = systemAlbums.filter { it.id !in otherIds }

    // 保持原始排序：Tabula 图集 + 未被收纳的系统图集
    val displayed = visibleAlbums.filter { it.id !in otherIds }
    return displayed to candidatesForOther
}

/**
 * 图集网格内容
 * 
 * 显示用户创建的图集列表，支持毛玻璃导航栏效果。
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
    onHideAlbum: ((Album) -> Unit)? = null,  // 隐藏图集
    onExcludeAlbum: ((Album, Boolean) -> Unit)? = null,  // 屏蔽/取消屏蔽图集
    isAlbumExcluded: ((Album) -> Boolean)? = null,  // 检查图集是否被屏蔽
    onSyncClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    showHiddenAlbums: Boolean = false,
    onToggleShowHidden: () -> Unit = {},
    onNavigateToOtherAlbums: (List<Album>) -> Unit = {},
    pendingCleanupCount: Int = 0,
    onCleanupSourceImages: () -> Unit = {}
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    var showCreateDialog by remember { mutableStateOf(false) }
    
    // 计算是否有隐藏的图集
    val hasHiddenAlbums = albums.any { it.isHidden }
    
    // 简化后只显示 App 图集，移除了 Tab 切换功能

    // 内容层的滚动状态
    val listState = rememberLazyListState()
    var topBarHeight by remember { mutableStateOf(0.dp) }
    
    val density = LocalDensity.current

    val topBarVisibleHeight = if (topBarHeight > 0.dp) {
        topBarHeight
    } else {
        // 默认高度，状态栏+标题栏
        100.dp
    }

    // 顶部内容间距
    val contentTopPadding = topBarVisibleHeight + 8.dp

    // 根据 showHiddenAlbums 过滤显示的图集
    val visibleAlbums = if (showHiddenAlbums) {
        albums  // 显示所有图集（包括隐藏的）
    } else {
        albums.filter { !it.isHidden }  // 只显示未隐藏的图集
    }

    // ========== "其他图集"收纳逻辑 ==========
    val appPreferences = rememberAppPreferences()
    val pinnedNames = remember { appPreferences.pinnedAlbumNames }

    // 计算收纳结果
    val (displayAlbums, otherAlbums) = remember(visibleAlbums, pinnedNames) {
        consolidateAlbums(visibleAlbums, pinnedNames)
    }

    // ========== 多选模式状态 ==========
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedAlbumIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkActionDialog by remember { mutableStateOf(false) }

    // 可选中的图集 ID 集合
    val selectableIds = remember(displayAlbums) {
        displayAlbums.map { it.id }.toSet()
    }

    // 当可见集合变化时，裁剪选中集合
    LaunchedEffect(selectableIds) {
        val clamped = clampSelectionToVisible(selectedAlbumIds, selectableIds)
        if (clamped != selectedAlbumIds) {
            selectedAlbumIds = clamped
        }
        if (selectedAlbumIds.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }

    // 多选模式返回键处理
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedAlbumIds = emptySet()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 内容区域
        CategorizedAlbumsView(
                appAlbums = displayAlbums,
                systemBuckets = null,  // 简化：只显示 App 图集
                allImages = allImages,
                onAppAlbumClick = onAlbumClick,
                onSystemBucketClick = onSystemBucketClick,
                onReorderAlbums = onReorderAlbums,
                onCreateAlbumClick = { showCreateDialog = true },
                isSelectionMode = isSelectionMode,
                selectedIds = selectedAlbumIds,
                onSelectionChange = { selectedAlbumIds = it },
                onEnterSelectionMode = { initialId ->
                    isSelectionMode = true
                    selectedAlbumIds = setOf(initialId)
                },
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                isDarkTheme = isDarkTheme,
                hideHeaders = true,  // 只有 App 图集，不需要分类标题
                listState = listState,
                topPadding = contentTopPadding,
                headerContent = null,
                userScrollEnabled = true,
                otherAlbums = otherAlbums,
                onOtherAlbumsClick = { onNavigateToOtherAlbums(otherAlbums) }
            )

        // ========== 顶部导航栏（纯色背景，移除毛玻璃效果以提升性能）==========
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(backgroundColor)
                .onSizeChanged { size ->
                    topBarHeight = with(density) { size.height.toDp() }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                // 顶部栏内容 - 与照片界面保持一致的padding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                ) {
                    if (isSelectionMode) {
                        // ========== 多选模式顶部栏 ==========
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 左侧关闭按钮
                            IconButton(
                                onClick = {
                                    HapticFeedback.lightTap(context)
                                    isSelectionMode = false
                                    selectedAlbumIds = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "退出选择",
                                    tint = textColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 中间标题 - 使用 weight 避免重叠
                            Text(
                                text = "共 ${selectableIds.size} 图集 · 已选 ${selectedAlbumIds.size}",
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                textAlign = TextAlign.Center
                            )

                            // 右侧按钮组
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 全选/取消全选
                                val allSelected = selectableIds.isNotEmpty() && selectedAlbumIds.containsAll(selectableIds)
                                TextButton(
                                    onClick = {
                                        HapticFeedback.lightTap(context)
                                        selectedAlbumIds = selectAllOrClear(selectableIds.toList(), selectedAlbumIds)
                                    }
                                ) {
                                    Text(
                                        text = if (allSelected) "取消" else "全选",
                                        color = Color(0xFF007AFF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                // 操作按钮
                                TextButton(
                                    onClick = {
                                        HapticFeedback.lightTap(context)
                                        showBulkActionDialog = true
                                    },
                                    enabled = selectedAlbumIds.isNotEmpty()
                                ) {
                                    Text(
                                        text = "操作",
                                        color = if (selectedAlbumIds.isNotEmpty()) Color(0xFF007AFF) else Color(0xFF8E8E93),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // ========== 正常模式顶部栏 ==========
                        // 标题
                        Text(
                            text = "图集",
                            color = textColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                        
                        // 右侧按钮组
                        val buttonBgColor = if (isDarkTheme) TabulaColors.CatBlackLight else Color.White
                        val buttonIconColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
                        
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 显示/隐藏 隐藏图集按钮（仅当有隐藏图集时显示）
                            if (hasHiddenAlbums) {
                                ActionIconButton(
                                    icon = if (showHiddenAlbums) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (showHiddenAlbums) "隐藏已隐藏的图集" else "显示隐藏的图集",
                                    onClick = {
                                        com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                        onToggleShowHidden()
                                    },
                                    backgroundColor = if (showHiddenAlbums) {
                                        Color(0xFF007AFF).copy(alpha = 0.15f)
                                    } else {
                                        buttonBgColor
                                    },
                                    iconColor = if (showHiddenAlbums) {
                                        Color(0xFF007AFF)
                                    } else {
                                        buttonIconColor
                                    }
                                )
                            }
                            
                            // 清理原图按钮（有待清理的原图时显示）
                            if (pendingCleanupCount > 0) {
                                ActionIconButton(
                                    icon = Icons.Outlined.CleaningServices,
                                    contentDescription = "清理原图",
                                    onClick = {
                                        com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                        onCleanupSourceImages()
                                    },
                                    backgroundColor = Color(0xFFFF9500).copy(alpha = 0.15f),
                                    iconColor = Color(0xFFFF9500)
                                )
                            }
                            
                            // 设置按钮
                            ActionIconButton(
                                icon = Icons.Outlined.Settings,
                                contentDescription = "设置",
                                onClick = {
                                    com.tabula.v3.ui.util.HapticFeedback.lightTap(context)
                                    onSettingsClick()
                                },
                                backgroundColor = buttonBgColor,
                                iconColor = buttonIconColor
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 新建图集对话框
    if (showCreateDialog) {
        AlbumEditDialog(
            existingAlbumNames = albums.map { it.name },
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color, emoji ->
                onCreateAlbum(name, color, emoji)
                showCreateDialog = false
            }
        )
    }

    // ========== 批量操作对话框 ==========
    if (showBulkActionDialog) {
        var checkExclude by remember { mutableStateOf(false) }
        var checkHide by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showBulkActionDialog = false },
            title = {
                Text(
                    text = "批量操作",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "已选 ${selectedAlbumIds.size} 个图集，选择要执行的操作：",
                        fontSize = 14.sp,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checkExclude = !checkExclude }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = checkExclude,
                            onCheckedChange = { checkExclude = it }
                        )
                        Text(
                            text = "屏蔽图集",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Text(
                        text = "屏蔽后图集中的照片不会出现在推荐流中",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checkHide = !checkHide }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = checkHide,
                            onCheckedChange = { checkHide = it }
                        )
                        Text(
                            text = "隐藏图集",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Text(
                        text = "隐藏后图集不会在图集列表中显示",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedAlbumsList = displayAlbums.filter { it.id in selectedAlbumIds }
                        selectedAlbumsList.forEach { album ->
                            if (checkExclude) onExcludeAlbum?.invoke(album, true)
                            if (checkHide) onHideAlbum?.invoke(album)
                        }
                        showBulkActionDialog = false
                        isSelectionMode = false
                        selectedAlbumIds = emptySet()
                    },
                    enabled = checkHide || checkExclude
                ) {
                    Text(
                        text = "确定",
                        color = if (checkHide || checkExclude) Color(0xFF007AFF) else Color(0xFF8E8E93),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkActionDialog = false }) {
                    Text(
                        text = "取消",
                        color = Color(0xFF8E8E93)
                    )
                }
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
    // C1/T03-COVER：多值映射——跨卷同 ID 行不折叠。
    val imageMap = remember(allImages) { allImages.groupBy { it.id } }

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
            // C1/T03-COVER：封面解析按完整媒体身份（卷|ID）——跨卷同 ID 不串图。
            val coverImage = album.coverImageId?.let { coverId ->
                resolveCoverRow(album.coverVolumeName, coverId, imageMap[coverId].orEmpty())
            }
            val coverUri = coverImage?.uri 
            
            // 跟踪图片加载状态
            var loadFailed by remember { mutableStateOf(false) }
            
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
                   if (coverUri != null && !loadFailed) {
                       // C1/T03-COVERKEY：封面请求经生产构造器（身份+内容版本键）。
                       AsyncImage(
                           model = coverImageRequest(
                               context, coverImage!!, "deck_album_cover_", crossfade = true
                           ),
                           contentDescription = null,
                           contentScale = ContentScale.Crop,
                           modifier = Modifier.fillMaxSize(),
                           onError = { loadFailed = true }
                       )
                   } else {
                       // 没有图片或加载失败时的背景色
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
