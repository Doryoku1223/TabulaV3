package com.tabula.v3.ui.screens

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.ui.components.MediaBadgeRow
import com.tabula.v3.ui.components.MotionPhotoPlayer
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.DragSelectState
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.ui.util.gridDragHandlerString
import com.tabula.v3.ui.util.rememberStringDragSelectState
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.ItemBounds
import com.tabula.v3.ui.util.findActivity
import com.tabula.v3.ui.util.rememberImageFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 回收站屏幕 - 增强版 (重设计选中样式与弹窗)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    deletedImages: List<ImageFile>,
    onRestore: (ImageFile) -> Unit,
    onRestoreBatch: (List<ImageFile>) -> Unit = {},
    onPermanentDelete: (ImageFile) -> Unit,
    onPermanentDeleteBatch: (List<ImageFile>) -> Unit,
    onClearAll: () -> Unit,
    onNavigateBack: () -> Unit,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = true,
    motionSoundVolume: Int = 100
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLoader = CoilSetup.getImageLoader(context)

    val isDarkTheme = LocalIsDarkTheme.current
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val cardColor = if (isDarkTheme) TabulaColors.CatBlackLight else Color.White
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF666666)

    // 清空确认对话框
    var showClearDialog by remember { mutableStateOf(false) }
    
    // 全屏查看状态
    var viewerState by remember { mutableStateOf<ViewerState?>(null) }
    
    // 多选模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    // T03：选择集以媒体身份键（卷|ID）为粒度，跨卷同数值 ID 记录独立选中。
    var selectedImages by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // 长按操作菜单
    val sheetState = rememberModalBottomSheetState()
    
    // 单项删除确认
    var imageToDelete by remember { mutableStateOf<ImageFile?>(null) }
    
    // 帮助提示对话框
    var showHelpDialog by remember { mutableStateOf(false) }
    
    // 排序状态：true = 最新在前（倒序），false = 最早在前（正序）
    var sortDescending by remember { mutableStateOf(true) }
    
    // 网格滚动状态（以 sortDescending 为 key，切换排序时重建 state，直接从顶部开始）
    val staggeredGridState = remember(sortDescending) { LazyStaggeredGridState() }
    
    // 排序后的图片列表（按移入回收站的时间排序）
    val sortedImages = remember(deletedImages, sortDescending) {
        if (sortDescending) {
            deletedImages.sortedByDescending { it.deletedAt }
        } else {
            deletedImages.sortedBy { it.deletedAt }
        }
    }
    
    // 滑动多选状态
    val dragSelectState = rememberStringDragSelectState(
        items = sortedImages,
        itemKey = { it.mediaIdentityKey() },
        selectedIds = selectedImages,
        onSelectionChange = { newSelection ->
            selectedImages = newSelection
            // 如果取消选中所有项，退出多选模式
            if (newSelection.isEmpty() && isSelectionMode) {
                isSelectionMode = false
            }
        },
        onEnterSelectionMode = {
            if (!isSelectionMode) {
                isSelectionMode = true
            }
        }
    )

    // 返回键处理
    BackHandler(enabled = viewerState != null || isSelectionMode) {
        when {
            viewerState != null -> viewerState = null
            isSelectionMode -> {
                isSelectionMode = false
                selectedImages = emptySet()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .statusBarsPadding()
                // navigationBarsPadding 移到内容底部，实现沉浸式效果
        ) {
            // ========== 顶部栏 ==========
            if (isSelectionMode) {
                SelectionModeTopBar(
                    selectedCount = selectedImages.size,
                    textColor = textColor,
                    onCancel = {
                        isSelectionMode = false
                        selectedImages = emptySet()
                    },
                    onRestoreSelected = {
                        // T02c：批量恢复走单一共享事务；单条恢复也复用同一路径。
                        val images = selectedImages.mapNotNull { key ->
                            deletedImages.find { it.mediaIdentityKey() == key }
                        }
                        if (images.size == 1) onRestore(images.first()) else onRestoreBatch(images)
                        isSelectionMode = false
                        selectedImages = emptySet()
                    },
                    onDeleteSelected = {
                        val imagesToDelete = selectedImages.mapNotNull { key ->
                            deletedImages.find { it.mediaIdentityKey() == key }
                        }
                        if (imagesToDelete.isNotEmpty()) {
                            onPermanentDeleteBatch(imagesToDelete)
                        }
                        isSelectionMode = false
                        selectedImages = emptySet()
                    }
                )
            } else {
                RecycleBinTopBar(
                    itemCount = deletedImages.size,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    sortDescending = sortDescending,
                    onToggleSort = { sortDescending = !sortDescending },
                    onBack = onNavigateBack,
                    onHelp = { showHelpDialog = true },
                    onClearAll = {
                        if (deletedImages.isNotEmpty()) {
                            showClearDialog = true
                        }
                    }
                )
            }

            // ========== 内容区 ==========
            if (deletedImages.isEmpty()) {
                EmptyRecycleBin(
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            } else {
                // 获取导航栏高度，实现沉浸式效果
                val navBarHeight = WindowInsets.navigationBars
                    .asPaddingValues().calculateBottomPadding()
                
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(3),
                    state = staggeredGridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .gridDragHandlerString(dragSelectState, isSelectionMode = isSelectionMode),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp + navBarHeight  // 底部留出导航栏空间
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), // 增加间距
                    verticalItemSpacing = 12.dp
                ) {
                    items(sortedImages, key = { it.mediaIdentityKey() }) { image ->
                        val identityKey = image.mediaIdentityKey()
                        val isSelected = selectedImages.contains(identityKey)
                        
                        // 使用 Box 包装并注册位置（窗口坐标系），仅多选模式下追踪位置
                        Box(
                            modifier = if (isSelectionMode) {
                                Modifier.onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInWindow()
                                    val size = coordinates.size
                                    dragSelectState.registerItemBounds(
                                        identityKey,
                                        ItemBounds(
                                            left = position.x,
                                            top = position.y,
                                            right = position.x + size.width,
                                            bottom = position.y + size.height
                                        )
                                    )
                                }
                            } else Modifier
                        ) {
                            RecycleBinItem(
                                image = image,
                                imageLoader = imageLoader,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                badges = rememberImageBadges(image, showHdrBadges, showMotionBadges),
                                onClick = {
                                    if (isSelectionMode) {
                                        // 在多选模式下切换选中
                                        if (!isSelected) HapticFeedback.lightTap(context)
                                        selectedImages = if (isSelected) {
                                            selectedImages - identityKey
                                        } else {
                                            selectedImages + identityKey
                                        }
                                    } else {
                                        // 打开全屏查看
                                        val index = sortedImages.indexOf(image)
                                        viewerState = ViewerState(index)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        // 进入多选模式，触发振动
                                        HapticFeedback.mediumTap(context)
                                        isSelectionMode = true
                                        selectedImages = setOf(identityKey)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ========== 全屏图片查看器 ==========
        AnimatedVisibility(
            visible = viewerState != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            viewerState?.let { state ->
                FullScreenViewer(
                    images = sortedImages,
                    initialIndex = state.initialIndex,
                    onDismiss = { viewerState = null },
                    onRestore = { image ->
                        onRestore(image)
                        // 如果删除完了就关闭查看器
                        if (sortedImages.size <= 1) {
                            viewerState = null
                        }
                    },
                    onDelete = { image ->
                        // 触发删除确认
                        imageToDelete = image
                    },
                    showHdr = showHdrBadges,
                    showMotionPhoto = showMotionBadges,
                    playMotionSound = playMotionSound,
                    motionSoundVolume = motionSoundVolume
                )
            }
        }
    }

    // ========== 弹窗区域 ==========
    
    // 0. 帮助提示对话框
    if (showHelpDialog) {
        TabulaDialog(
            title = "关于删除",
            text = "从回收站删除的照片会被直接删除，不会经过系统相册的「最近删除」。\n\n" +
                   "如果您开启了云同步（如 OPPO 云服务），系统相册中可能会显示灰色占位图，" +
                   "这是因为本地文件已删除，但云端仍保留元数据。这属于正常现象，照片已被正确删除。",
            confirmText = "知道了",
            cancelText = "",
            isDestructive = false,
            onConfirm = { showHelpDialog = false },
            onDismiss = { showHelpDialog = false }
        )
    }

    // 1. 清空回收站确认
    if (showClearDialog) {
        TabulaDialog(
            title = "清空回收站",
            text = "您确定要彻底删除回收站中的所有 ${deletedImages.size} 张照片吗？此操作无法撤销。",
            confirmText = "清空",
            cancelText = "取消",
            isDestructive = true,
            onConfirm = {
                onClearAll()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    // 2. 单张删除确认 (在全屏查看时)
    if (imageToDelete != null) {
        TabulaDialog(
            title = "彻底删除",
            text = "要允许 Tabula 删除这张照片吗？",
            confirmText = "删除",
            cancelText = "取消",
            isDestructive = true,
            onConfirm = {
                imageToDelete?.let { image ->
                    onPermanentDelete(image)
                    if (deletedImages.size <= 1) {
                        viewerState = null
                    }
                }
                imageToDelete = null
            },
            onDismiss = { imageToDelete = null }
        )
    }
}

// ========== 组件定义 ==========

/**
 * 优雅的自定义对话框
 * 自动适配液态玻璃主题
 */
@Composable
fun TabulaDialog(
    title: String,
    text: String,
    confirmText: String,
    cancelText: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val isDarkTheme = LocalIsDarkTheme.current
        val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
        val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
        // 液态玻璃模式下使用更不透明的背景
        val effectiveBgColor = if (isLiquidGlassEnabled) {
            backgroundColor.copy(alpha = 0.98f)
        } else {
            backgroundColor
        }
        val textColor = if (isDarkTheme) Color.White else Color.Black
        
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = effectiveBgColor,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(320.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = textColor.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (cancelText.isNotEmpty()) Arrangement.SpaceBetween else Arrangement.Center
                ) {
                    // 取消按钮（仅当 cancelText 不为空时显示）
                    if (cancelText.isNotEmpty()) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                contentColor = if (isDarkTheme) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                        ) {
                            Text(
                                text = cancelText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    
                    // 确认按钮
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) TabulaColors.DangerRed else TabulaColors.EyeGold,
                            contentColor = if (isDestructive) Color.White else TabulaColors.CatBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .then(if (cancelText.isNotEmpty()) Modifier.weight(1f) else Modifier.fillMaxWidth())
                            .height(48.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                    ) {
                        Text(
                            text = confirmText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 回收站图片项 - 全新设计
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinItem(
    image: ImageFile,
    imageLoader: coil.ImageLoader,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    badges: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current

    val aspectRatio = if (image.height > 0) {
        image.width.toFloat() / image.height
    } else {
        1f
    }.coerceIn(0.5f, 2f)

    // 动画状态
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.88f else 1f,
        label = "scale"
    )
    
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 12.dp,
        label = "corner"
    )

    Box(
        modifier = Modifier,
        contentAlignment = Alignment.Center
    ) {
        // 图片主体
        Box(
            modifier = Modifier
                .scale(scale)
                .shadow(
                    elevation = if (isSelected) 4.dp else 0.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.uri)
                    .crossfade(100)
                    .build(),
                contentDescription = image.displayName,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
            )
            
            MediaBadgeRow(
                badges = badges,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )

            // 选中时的覆盖蒙版 (轻微黑透)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.1f))
                )
            }
        }

        // 选中状态指示器
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    // 选中状态：蓝色实心圆角矩形
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF007AFF), RoundedCornerShape(6.dp))
                            .border(1.5.dp, Color.White, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (!isSelected) {
                    // 未选中状态：半透明白色描边圆圈
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * 全屏查看器状态
 */
private data class ViewerState(val initialIndex: Int)

/**
 * 全屏图片查看器 - 支持左右滑动切换图片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenViewer(
    images: List<ImageFile>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onRestore: (ImageFile) -> Unit,
    onDelete: (ImageFile) -> Unit,
    showHdr: Boolean,
    showMotionPhoto: Boolean,
    playMotionSound: Boolean,
    motionSoundVolume: Int
) {
    val context = LocalContext.current
    val imageLoader = CoilSetup.getImageLoader(context)

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
    val displayImage = images.getOrNull(currentIndex)

    val activity = context.findActivity()
    val window = activity?.window
    val originalColorMode = remember(window) { window?.colorMode ?: ActivityInfo.COLOR_MODE_DEFAULT }

    androidx.compose.runtime.DisposableEffect(window) {
        onDispose {
            if (window != null) {
                window.colorMode = originalColorMode
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 使用 HorizontalPager 实现左右滑动
        // 当图片放大或双指触摸时禁用滑动，避免缩放回弹
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isMultiTouchActive && currentPageZoom <= 1.05f,
            key = { images.getOrNull(it)?.mediaIdentityKey() ?: it.toString() },
            // 添加 flingBehavior 确保滑动后对齐到页面，防止卡在两图之间
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.35f  // 滑动超过35%就切换到下一页
            )
        ) { pageIndex ->
            val pageImage = images.getOrNull(pageIndex)
            if (pageImage != null) {
                RecycleBinImagePage(
                    image = pageImage,
                    imageLoader = imageLoader,
                    showHdr = showHdr,
                    showMotionPhoto = showMotionPhoto,
                    playMotionSound = playMotionSound,
                    motionSoundVolume = motionSoundVolume,
                    onDismiss = onDismiss,
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

        // 顶部渐变遮罩 + 关闭按钮 + 页码
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
            
            Text(
                text = "${currentIndex + 1} / ${images.size}",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 底部操作栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 恢复按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .combinedClickable(onClick = { displayImage?.let { onRestore(it) } })
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = "恢复",
                        tint = TabulaColors.SuccessGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "恢复",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                
                // 删除按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .combinedClickable(onClick = { displayImage?.let { onDelete(it) } })
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = "彻底删除",
                        tint = TabulaColors.DangerRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "删除",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 回收站图片页面 - HorizontalPager 中的单个页面
 * 包含缩放、拖动、HDR/Live Photo 功能
 * 
 * 手势功能：
 * 1. 双指缩放：流畅丝滑，支持缩放到最大后再缩小回原比例
 * 2. 单击：原比例时退出大图；放大时先动画恢复原比例，再次单击才退出
 * 3. 双击：在原比例和放大之间切换
 * 4. 长按：HDR对比/Live Photo播放，松手后不退出
 * 5. 单指拖拽：放大后支持拖动查看
 * 6. 左右滑动：原比例下支持切换上/下一张
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinImagePage(
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
    val animatedScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val animatedOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val animatedOffsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    
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
    
    // 动画恢复到原比例
    fun animateToOriginalScale() {
        scope.launch {
            launch {
                animatedScale.animateTo(
                    targetValue = minScale,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f
                    )
                )
            }
            launch {
                animatedOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f
                    )
                )
            }
            launch {
                animatedOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f
                    )
                )
            }
        }
    }

    // HDR / Live Photo 功能
    val currentFeatures = rememberImageFeatures(
        image = image,
        enableHdr = showHdr,
        enableMotion = showMotionPhoto
    )
    val isHdr = showHdr && (currentFeatures?.isHdr == true)
    val currentMotionInfo = if (showMotionPhoto) currentFeatures?.motionPhotoInfo else null
    var isHdrComparePressed by remember { mutableStateOf(false) }
    var isLivePressed by remember { mutableStateOf(false) }
    var isPressing by remember { mutableStateOf(false) }
    var wasLongPressing by remember { mutableStateOf(false) }
    val pressDelayMs = 80L

    LaunchedEffect(isPressing, isHdr, currentMotionInfo) {
        if (!isPressing) {
            isHdrComparePressed = false
            isLivePressed = false
            return@LaunchedEffect
        }
        delay(pressDelayMs)
        if (isPressing) {
            // 优先级：Live Photo > HDR 对比
            // 如果图片同时有 HDR 和 Live Photo，长按只播放 Live Photo
            if (currentMotionInfo != null) {
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
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
                                lastTransformTime = android.os.SystemClock.uptimeMillis()
                                
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
                        lastTransformTime = android.os.SystemClock.uptimeMillis()
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
        val aspectRatio = if (image.height > 0) {
            image.width.toFloat() / image.height
        } else {
            1f
        }
        val containerRatio = maxWidth.value / maxHeight.value
        val (targetWidth, targetHeight) = if (aspectRatio > containerRatio) {
            maxWidth to (maxWidth / aspectRatio)
        } else {
            (maxHeight * aspectRatio) to maxHeight
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
                .pointerInput(isHdr, currentMotionInfo) {
                    detectTapGestures(
                        onTap = {
                            if (wasLongPressing) {
                                wasLongPressing = false
                                return@detectTapGestures
                            }
                            val timeSinceTransform = android.os.SystemClock.uptimeMillis() - lastTransformTime
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
                                    animatedScale.animateTo(
                                        targetScale,
                                        androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                                    )
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
                                        animatedScale.animateTo(
                                            targetScale,
                                            androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                                        )
                                    }
                                    launch {
                                        animatedOffsetX.animateTo(
                                            clampedOffset.x,
                                            androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                                        )
                                    }
                                    launch {
                                        animatedOffsetY.animateTo(
                                            clampedOffset.y,
                                            androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                                        )
                                    }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.uri)
                    .crossfade(200)
                    .build(),
                contentDescription = image.displayName,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // 只有在长按时才显示 MotionPhotoPlayer，避免 TextureView 覆盖 HDR 图片
            if (currentMotionInfo != null && isLivePressed) {
                MotionPhotoPlayer(
                    imageUri = image.uri,
                    motionInfo = currentMotionInfo,
                    modifier = Modifier.fillMaxSize(),
                    playWhen = true,
                    playAudio = playMotionSound,
                    volumePercent = motionSoundVolume
                )
            }
        }
    }
}

/**
 * 多选模式顶部栏
 */
@Composable
private fun SelectionModeTopBar(
    selectedCount: Int,
    textColor: Color,
    onCancel: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "取消",
                tint = textColor
            )
        }

        Text(
            text = "已选 $selectedCount 项",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = textColor
        )

        Row {
            IconButton(
                onClick = onRestoreSelected,
                enabled = selectedCount > 0
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = "恢复所选",
                    tint = if (selectedCount > 0) TabulaColors.SuccessGreen else Color(0xFFBDBDBD)
                )
            }
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = "删除所选",
                    tint = if (selectedCount > 0) TabulaColors.DangerRed else Color(0xFFBDBDBD)
                )
            }
        }
    }
}

/**
 * 回收站顶部栏
 */
@Composable
private fun RecycleBinTopBar(
    itemCount: Int,
    textColor: Color,
    secondaryTextColor: Color,
    sortDescending: Boolean,
    onToggleSort: () -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 把返回按钮和标题放在一起，使其对齐
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "回收站",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor
                )
                if (itemCount > 0) {
                    Text(
                        text = "$itemCount 项待处理",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 排序按钮
            if (itemCount > 1) {
                IconButton(onClick = onToggleSort) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = if (sortDescending) "当前：最新在前" else "当前：最早在前",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // 帮助按钮
            IconButton(onClick = onHelp) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "帮助",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            if (itemCount > 0) {
                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TabulaColors.DangerRed.copy(alpha = 0.1f),
                        contentColor = TabulaColors.DangerRed
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}


/**
 * 空回收站状态
 */
@Composable
private fun EmptyRecycleBin(
    textColor: Color,
    secondaryTextColor: Color
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.Gray.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = secondaryTextColor.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "回收站空空如也",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "暂无被删除的照片",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor
            )
        }
    }
}

@Composable
private fun rememberImageBadges(
    image: ImageFile,
    showHdr: Boolean,
    showMotion: Boolean
): List<String> {
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
