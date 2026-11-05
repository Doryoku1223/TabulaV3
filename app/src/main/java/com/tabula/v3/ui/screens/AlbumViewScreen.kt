package com.tabula.v3.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.model.SyncMode
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.data.repository.resolveAlbumCoverImage
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.components.AlbumDeleteConfirmDialog
import com.tabula.v3.ui.components.AlbumEditDialog
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.SwipeableViewerOverlay
import com.tabula.v3.ui.components.SwipeableViewerState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.gridDragHandlerString
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.gridThumbnailCacheKey
import com.tabula.v3.ui.util.coverImageRequest
import com.tabula.v3.ui.util.ItemBounds
import com.tabula.v3.ui.util.rememberStringDragSelectState

/**
 * 相册视图屏幕
 *
 * 展示所有自定义相册，点击进入相册内容。
 * 支持相册管理（新建、编辑、删除、排序）。
 *
 * 设计风格：
 * - 大卡片布局，突出视觉效果
 * - 渐变封面 + Emoji 图标
 * - 流畅的交互动画
 */
@Composable
fun AlbumViewScreen(
    albums: List<Album>,
    allImages: List<ImageFile>,
    getImagesForAlbum: suspend (String) -> List<Long>,
    getAlbumImages: (suspend (String) -> List<ImageFile>)? = null,
    onCreateAlbum: (name: String, color: Long?, emoji: String?) -> Unit,
    onUpdateAlbum: (Album) -> Unit,
    onSetCover: ((albumName: String, image: ImageFile) -> Unit)? = null,
    onDeleteAlbum: (String) -> Unit,
    onHideAlbum: ((String) -> Unit)? = null,
    onUnhideAlbum: ((String) -> Unit)? = null,
    onDeleteEmptyAlbum: ((String) -> Unit)? = null,
    onExcludeFromRecommend: ((String, Boolean) -> Unit)? = null,  // 屏蔽/取消屏蔽图集
    isAlbumExcludedFromRecommend: ((String) -> Boolean)? = null,   // 检查图集是否被屏蔽
    onNavigateBack: () -> Unit,
    initialAlbumId: String? = null,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = false,
    motionSoundVolume: Int = 100,
    onMoveToAlbum: ((List<String>, String) -> Unit)? = null,
    onCopyToAlbum: ((List<String>, String) -> Unit)? = null,
    onDeleteImages: ((List<String>) -> Unit)? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current

    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    // 查找当前相册
    val currentAlbum = remember(initialAlbumId, albums) {
        albums.find { it.id == initialAlbumId }
    }

    // 状态
    var editingAlbum by remember { mutableStateOf<Album?>(null) }
    var viewerState by remember { mutableStateOf<SwipeableViewerState?>(null) }
    var albumImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    // T05：加载失败有明确状态；失败时保留旧列表，可重试。
    var albumLoadError by remember { mutableStateOf(false) }
    var albumLoadRevision by remember { mutableIntStateOf(0) }
    
    // 渐进式加载状态
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreImages by remember { mutableStateOf(true) }
    
    // 响应 allImages 变化：当图片被删除时，自动从 albumImages 中移除
    // 这解决了在图集中删除图片后不能实时刷新的问题。
    // O5③：同步按完整媒体身份键匹配——删除确认行后，另一卷的同数值 ID 行
    // 不因纯 ID 匹配被连带剪掉（下一轮重载前保持可见）。
    LaunchedEffect(allImages) {
        if (albumImages.isNotEmpty()) {
            // 获取当前 allImages 中存在的完整身份键集合
            val validIdentityKeys = allImages.map { it.mediaIdentityKey() }.toSet()
            // 过滤掉已不存在的图片
            val filteredImages = albumImages.filter { it.mediaIdentityKey() in validIdentityKeys }
            // 只有在确实有图片被删除时才更新状态，避免不必要的重组
            if (filteredImages.size != albumImages.size) {
                albumImages = filteredImages
            }
        }
    }
    
    // 加载图片逻辑 - 优化版：渐进式加载 + Map 查找 + 协程取消检查
    LaunchedEffect(currentAlbum?.id, albumLoadRevision) {
        if (currentAlbum != null) {
            // 重置加载状态
            hasMoreImages = true
            albumLoadError = false
            try {
                if (getAlbumImages != null) {
                    // T05：详情直接消费仓库返回的完整 ImageFile——查询结果即最终结果，
                    // 不再压平成 (id, uri) 映射后逐 URI 补读（旧实现的 N+1 来源）。
                    val images = getAlbumImages(currentAlbum.id)

                    // 协程取消检查：切相册或重建后旧结果不覆盖新页面
                    if (!isActive) return@LaunchedEffect

                    albumImages = images
                } else {
                    // 兼容旧的加载方式 - 也使用 Set 优化
                    val imageIds = getImagesForAlbum(currentAlbum.id).toSet()
                    albumImages = allImages.filter { it.id in imageIds }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 失败保留旧列表，置错误状态供 UI 提示与重试。
                if (isActive) albumLoadError = true
            }
        } else {
            albumImages = emptyList()
        }
    }
    
    // 如果没有指定相册，直接退出
    LaunchedEffect(initialAlbumId) {
        if (initialAlbumId == null) {
            onNavigateBack()
        }
    }

    // 返回拦截
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (currentAlbum != null) {
            // 直接显示详情
             AlbumContentView(
                album = currentAlbum,
                images = albumImages,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                loadError = albumLoadError,
                onLoadRetry = { albumLoadRevision++ },
                isDarkTheme = isDarkTheme,
                showHdrBadges = showHdrBadges,
                showMotionBadges = showMotionBadges,
                onImageClick = { image, sourceRect, sortedList ->
                    val index = sortedList.indexOf(image).coerceAtLeast(0)
                    viewerState = SwipeableViewerState(
                        images = sortedList,
                        initialIndex = index,
                        sourceRect = sourceRect
                    )
                },
                onEditClick = { editingAlbum = currentAlbum },
                // 根据相册是否隐藏，显示隐藏或取消隐藏选项
                onHideClick = if (!currentAlbum.isHidden) onHideAlbum?.let { callback -> 
                    { callback(currentAlbum.id); onNavigateBack() }
                } else null,
                onUnhideClick = if (currentAlbum.isHidden) onUnhideAlbum?.let { callback ->
                    { callback(currentAlbum.id) }
                } else null,
                onDeleteEmptyClick = if (onDeleteEmptyAlbum != null && currentAlbum.imageCount == 0) {
                    { onDeleteEmptyAlbum(currentAlbum.id); onNavigateBack() }
                } else null,
                onExcludeFromRecommend = onExcludeFromRecommend?.let { callback ->
                    { excluded: Boolean -> callback(currentAlbum.id, excluded) }
                },
                isExcludedFromRecommend = isAlbumExcludedFromRecommend?.invoke(currentAlbum.id) ?: false,
                onSetCover = onSetCover?.let { callback ->
                    { image: ImageFile -> callback(currentAlbum.name, image) }
                },
                onNavigateBack = onNavigateBack,
                albums = albums,
                allImages = allImages,
                onMoveToAlbum = onMoveToAlbum,
                onCopyToAlbum = onCopyToAlbum,
                onDeleteImages = onDeleteImages
            )
        } else {
             // Loading state
             Box(Modifier.fillMaxSize())
        }
        
        // 查看器 - 支持左右滑动切换图片
        viewerState?.let { state ->
            SwipeableViewerOverlay(
                viewerState = state,
                onDismiss = { viewerState = null },
                showHdr = showHdrBadges,
                showMotionPhoto = showMotionBadges,
                playMotionSound = playMotionSound,
                motionSoundVolume = motionSoundVolume
            )
        }
    }
    
     // 编辑相册对话框
    editingAlbum?.let { album ->
        AlbumEditDialog(
            isEdit = true,
            initialName = album.name,
            initialColor = album.color,
            initialEmoji = album.emoji,
            existingAlbumNames = albums.map { it.name },
            onConfirm = { name, color, emoji ->
                onUpdateAlbum(album.copy(name = name, color = color, emoji = emoji))
                editingAlbum = null
            },
            onDismiss = { editingAlbum = null }
        )
    }

}

/**
 * 相册内容视图
 */
@Composable
private fun AlbumLoadErrorRow(
    text: String,
    textColor: Color,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = 0.8f),
            fontSize = 13.sp
        )
        Text(
            text = "重试",
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onRetry() }
        )
    }
}

@Composable
private fun AlbumContentView(
    album: Album,
    images: List<ImageFile>,
    textColor: Color,
    secondaryTextColor: Color,
    loadError: Boolean = false,
    onLoadRetry: () -> Unit = {},
    isDarkTheme: Boolean,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    onImageClick: (ImageFile, SourceRect, List<ImageFile>) -> Unit,
    onEditClick: () -> Unit,
    onHideClick: (() -> Unit)? = null,
    onUnhideClick: (() -> Unit)? = null,
    onDeleteEmptyClick: (() -> Unit)? = null,
    onExcludeFromRecommend: ((Boolean) -> Unit)? = null,
    isExcludedFromRecommend: Boolean = false,
    onSetCover: ((ImageFile) -> Unit)? = null,  // C1/T03-COVER：album 名由外层闭包绑定
    onNavigateBack: () -> Unit,
    albums: List<Album> = emptyList(),
    allImages: List<ImageFile> = emptyList(),
    onMoveToAlbum: ((List<String>, String) -> Unit)? = null,
    onCopyToAlbum: ((List<String>, String) -> Unit)? = null,
    onDeleteImages: ((List<String>) -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    // 排序状态：true = 最新在前（倒序），false = 最早在前（正序）
    var sortDescending by remember { mutableStateOf(true) }
    
    // 排序变化时重置 grid state，直接从顶部开始
    val gridState = remember(sortDescending) { LazyGridState() }
    
    // 排序后的图片列表
    val sortedImages = remember(images, sortDescending) {
        if (sortDescending) {
            images.sortedByDescending { it.dateModified }
        } else {
            images.sortedBy { it.dateModified }
        }
    }
    
    // 多选模式状态。O5③：选择集按完整媒体身份键（卷|ID）——不同卷的同数值
    // ID 是不同照片，必须可独立选中/取消，批量删除只消费确认选中的行。
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedImageKeys by remember { mutableStateOf(setOf<String>()) }

    // 图集选择弹窗状态
    var showAlbumPicker by remember { mutableStateOf(false) }
    var isCopyMode by remember { mutableStateOf(false) }  // true=复制, false=移动

    // 隐藏确认弹窗状态
    var showHideConfirm by remember { mutableStateOf(false) }

    // 删除空图集确认弹窗状态
    var showDeleteEmptyConfirm by remember { mutableStateOf(false) }

    // 删除图片确认弹窗状态
    var showDeleteImagesConfirm by remember { mutableStateOf(false) }

    // 滑动多选状态
    val dragSelectState = rememberStringDragSelectState(
        items = sortedImages,
        itemKey = { it.mediaIdentityKey() },
        selectedIds = selectedImageKeys,
        onSelectionChange = { newSelection ->
            selectedImageKeys = newSelection
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

    // 退出多选模式
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedImageKeys = emptySet()
    }

    // 切换选中状态
    fun toggleSelection(identityKey: String) {
        selectedImageKeys = if (identityKey in selectedImageKeys) {
            selectedImageKeys - identityKey
        } else {
            selectedImageKeys + identityKey
        }
        // 如果取消选中后没有选中项，退出多选模式
        if (selectedImageKeys.isEmpty()) {
            isSelectionMode = false
        }
    }

    // 全选/取消全选
    fun toggleSelectAll() {
        selectedImageKeys = if (selectedImageKeys.size == images.size) {
            emptySet()
        } else {
            images.map { it.mediaIdentityKey() }.toSet()
        }
        if (selectedImageKeys.isEmpty()) {
            isSelectionMode = false
        }
    }
    
    // 返回处理：多选模式下先退出多选
    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }
    
    // 背景色（确保覆盖整个屏幕包括导航栏区域）
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
        // 顶部栏 - 根据多选模式切换显示内容
        AnimatedContent(
            targetState = isSelectionMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                fadeOut(animationSpec = tween(200))
            },
            label = "topBarTransition"
        ) { selectionMode ->
            if (selectionMode) {
                // 多选模式顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 取消按钮
                    IconButton(onClick = { exitSelectionMode() }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "取消",
                            tint = textColor
                        )
                    }
                    
                    // 选中数量
                    Text(
                        text = "已选 ${selectedImageKeys.size} 项",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 全选按钮
                    TextButton(
                        onClick = { toggleSelectAll() }
                    ) {
                        Text(
                            text = if (selectedImageKeys.size == images.size) "取消全选" else "全选",
                            color = Color(0xFF007AFF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // 普通模式顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = textColor
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (album.emoji != null) {
                                Text(text = album.emoji, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = album.name,
                                color = textColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${images.size} 张照片",
                            color = secondaryTextColor,
                            fontSize = 13.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 排序按钮
                        if (images.size > 1) {
                            IconButton(onClick = { sortDescending = !sortDescending }) {
                                Icon(
                                    imageVector = Icons.Outlined.SwapVert,
                                    contentDescription = if (sortDescending) "当前：最新在前" else "当前：最早在前",
                                    tint = secondaryTextColor
                                )
                            }
                        }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "更多",
                                    tint = textColor
                                )
                            }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(
                                    color = if (isDarkTheme) Color(0xFF2C2C2E) else Color.White,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .width(180.dp)
                        ) {
                            // 编辑相册
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "编辑相册",
                                        color = textColor,
                                        fontWeight = FontWeight.Medium
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    onEditClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Edit, 
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            
                            // 屏蔽/取消屏蔽图集（从推荐中排除）
                            if (onExcludeFromRecommend != null) {
                                // 分隔线
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .height(0.5.dp)
                                        .background(secondaryTextColor.copy(alpha = 0.2f))
                                )
                                
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            if (isExcludedFromRecommend) "取消屏蔽" else "屏蔽图集",
                                            color = textColor,
                                            fontWeight = FontWeight.Medium
                                        ) 
                                    },
                                    onClick = {
                                        showMenu = false
                                        HapticFeedback.lightTap(context)
                                        onExcludeFromRecommend(!isExcludedFromRecommend)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isExcludedFromRecommend) Icons.Outlined.CheckCircle else Icons.Outlined.Block, 
                                            contentDescription = null,
                                            tint = if (isExcludedFromRecommend) Color(0xFF34C759) else secondaryTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            
                            // 隐藏相册（如果有回调）
                            if (onHideClick != null) {
                                // 分隔线
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .height(0.5.dp)
                                        .background(secondaryTextColor.copy(alpha = 0.2f))
                                )
                                
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            "隐藏相册",
                                            color = textColor,
                                            fontWeight = FontWeight.Medium
                                        ) 
                                    },
                                    onClick = {
                                        showMenu = false
                                        showHideConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.VisibilityOff, 
                                            contentDescription = null,
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            
                            // 取消隐藏相册（如果相册已隐藏）
                            if (onUnhideClick != null) {
                                // 分隔线
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .height(0.5.dp)
                                        .background(secondaryTextColor.copy(alpha = 0.2f))
                                )
                                
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            "取消隐藏",
                                            color = textColor,
                                            fontWeight = FontWeight.Medium
                                        ) 
                                    },
                                    onClick = {
                                        showMenu = false
                                        onUnhideClick()
                                        HapticFeedback.lightTap(context)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Visibility, 
                                            contentDescription = null,
                                            tint = Color(0xFF34C759),  // 绿色表示恢复
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            
                            // 删除相册（仅空图集可用）
                            if (onDeleteEmptyClick != null) {
                                // 分隔线
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .height(0.5.dp)
                                        .background(secondaryTextColor.copy(alpha = 0.2f))
                                )
                                
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            "删除相册", 
                                            color = Color(0xFFFF3B30),
                                            fontWeight = FontWeight.Medium
                                        ) 
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteEmptyConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = Color(0xFFFF3B30),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                        }
                    }
                }
            }
        }

        // 只有当 imageCount 也为 0 时才显示空状态，避免加载期间闪烁
        if (images.isEmpty() && album.imageCount == 0) {
            // C0-DETAIL-EMPTY-RETRY：首次加载失败且列表为空时，错误与重试必须可见，
            // 不能被友好空态遮盖。
            Column(modifier = Modifier.fillMaxSize()) {
                if (loadError) {
                    AlbumLoadErrorRow(
                        text = "图集加载失败，未能获取照片",
                        textColor = textColor,
                        onRetry = onLoadRetry
                    )
                }
                // 空状态 - TaTa 占领相册
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // TaTa 图片
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(R.drawable.zpcat1)
                            .crossfade(true)
                            .build(),
                        contentDescription = "TaTa",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    // 主标题
                    Text(
                        text = "糟糕",
                        color = textColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 副标题 - TaTa 强调显示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TaTa",
                            color = Color(0xFF007AFF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = " 占领了你的相册",
                            color = textColor.copy(alpha = 0.8f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 提示文字
                    Text(
                        text = "在滑一滑中添加照片到这里",
                        color = secondaryTextColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
        } else {
            // T05：加载失败给出明确状态与重试入口（保留旧列表不清空）。
            if (loadError) {
                AlbumLoadErrorRow(
                    text = "图集加载失败，当前显示为上次结果",
                    textColor = textColor,
                    onRetry = onLoadRetry
                )
            }

            // 照片网格 - 使用 WindowInsets 获取导航栏高度，让内容沉浸到导航栏下方
            val navBarInsets = WindowInsets.navigationBars
            val navBarHeight = with(LocalDensity.current) { navBarInsets.getBottom(this).toDp() }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 4.dp,
                    end = 4.dp,
                    top = 4.dp,
                    // 底部留出导航栏空间 + 多选模式下的操作栏空间
                    bottom = navBarHeight + if (isSelectionMode) 80.dp else 4.dp
                ),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .gridDragHandlerString(dragSelectState, isSelectionMode = isSelectionMode)
                // 注：LazyVerticalGrid 的预取由 Compose 自动管理
                // 图片加载优化主要通过 Coil 的缓存策略实现
            ) {
                // O5③：列表 key/拖选边界注册用完整身份键——不同卷同数值 ID 行
                // 共存时不再重复 key 崩溃或边界互相覆盖。
                items(sortedImages, key = { it.mediaIdentityKey() }) { image ->
                    val identityKey = image.mediaIdentityKey()
                    // 使用 Box 包装并注册位置（窗口坐标系），支持滑动多选
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
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
                    ) {
                        PhotoGridItem(
                            image = image,
                            showHdrBadge = showHdrBadges,
                            showMotionBadge = showMotionBadges,
                            onClick = { sourceRect ->
                                onImageClick(image, sourceRect, sortedImages)
                            },
                            onSetCover = onSetCover?.let { callback ->
                                { callback(image) }
                            },
                            isSelectionMode = isSelectionMode,
                            isSelected = identityKey in selectedImageKeys,
                            onLongPress = {
                                // 长按进入多选模式并选中当前图片
                                if (!isSelectionMode) {
                                    HapticFeedback.heavyTap(context)
                                    isSelectionMode = true
                                    selectedImageKeys = setOf(identityKey)
                                }
                            },
                            onToggleSelect = {
                                toggleSelection(identityKey)
                            }
                        )
                    }
                }
            }
        }
        }
        
        // 多选模式底部操作栏
        androidx.compose.animation.AnimatedVisibility(
            visible = isSelectionMode && selectedImageKeys.isNotEmpty(),
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (isDarkTheme) Color.Black.copy(alpha = 0.95f) 
                                else Color.White.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 复制到其他图集按钮
                    if (onCopyToAlbum != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    HapticFeedback.mediumTap(context)
                                    isCopyMode = true
                                    showAlbumPicker = true
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileCopy,
                                contentDescription = "复制到图集",
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "复制到图集",
                                color = Color(0xFF30D158),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // 移动到其他图集按钮
                    if (onMoveToAlbum != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    HapticFeedback.mediumTap(context)
                                    isCopyMode = false
                                    showAlbumPicker = true
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DriveFileMove,
                                contentDescription = "移动到图集",
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "移动到图集",
                                color = Color(0xFF007AFF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // 删除按钮
                    if (onDeleteImages != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    HapticFeedback.mediumTap(context)
                                    showDeleteImagesConfirm = true
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "删除",
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "删除",
                                color = Color(0xFFFF3B30),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // 删除图片确认弹窗
        if (showDeleteImagesConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteImagesConfirm = false },
                containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White,
                title = {
                    Text(
                        text = "删除图片",
                        color = if (isDarkTheme) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "确定要删除选中的 ${selectedImageKeys.size} 张图片吗？\n\n此操作将永久删除图片，无法恢复。",
                        color = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // O5③：回调携带确认行的完整媒体身份键，消费侧
                            // （MainActivity）按身份解析，不按数值 ID 串卷。
                            onDeleteImages?.invoke(selectedImageKeys.toList())
                            showDeleteImagesConfirm = false
                            exitSelectionMode()
                            HapticFeedback.mediumTap(context)
                        }
                    ) {
                        Text(
                            text = "删除",
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteImagesConfirm = false }) {
                        Text(
                            text = "取消",
                            color = Color(0xFF007AFF)
                        )
                    }
                }
            )
        }
        
        // 图集选择弹窗
        if (showAlbumPicker) {
            AlbumPickerDialog(
                albums = albums.filter { it.id != album.id },  // 排除当前图集
                allImages = allImages,
                title = if (isCopyMode) "复制到图集" else "移动到图集",
                onAlbumSelected = { targetAlbumId ->
                    // 根据模式执行复制或移动操作。
                    // C1/T03-MOVECOPY-IDENTITY：确认集合按完整媒体身份键传递，
                    // 消费端（AlbumManager）从当前媒体集合按身份重新解析——
                    // 不再压缩成数值 ID（跨卷同 ID 不折叠、不串卷）。
                    if (isCopyMode) {
                        onCopyToAlbum?.invoke(selectedImageKeys.toList(), targetAlbumId)
                    } else {
                        onMoveToAlbum?.invoke(selectedImageKeys.toList(), targetAlbumId)
                    }
                    showAlbumPicker = false
                    exitSelectionMode()
                    HapticFeedback.mediumTap(context)
                },
                onDismiss = { showAlbumPicker = false },
                isDarkTheme = isDarkTheme
            )
        }
        
        // 隐藏相册确认对话框
        if (showHideConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showHideConfirm = false },
                containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White,
                title = {
                    Text(
                        text = "隐藏相册",
                        color = if (isDarkTheme) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "隐藏「${album.name}」？\n\n隐藏后相册不会在列表中显示，但不会删除任何文件。你可以通过图库导航栏的眼睛图标重新显示隐藏的相册。",
                        color = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onHideClick?.invoke()
                            showHideConfirm = false
                            HapticFeedback.mediumTap(context)
                        }
                    ) {
                        Text(
                            text = "隐藏",
                            color = Color(0xFF007AFF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHideConfirm = false }) {
                        Text(
                            text = "取消",
                            color = Color(0xFF8E8E93)
                        )
                    }
                }
            )
        }
        
        // 删除空图集确认对话框
        if (showDeleteEmptyConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteEmptyConfirm = false },
                containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White,
                title = {
                    Text(
                        text = "删除相册",
                        color = if (isDarkTheme) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "确定删除「${album.name}」？\n\n这将删除该相册的文件夹。此操作不可撤销。",
                        color = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteEmptyClick?.invoke()
                            showDeleteEmptyConfirm = false
                            HapticFeedback.mediumTap(context)
                        }
                    ) {
                        Text(
                            text = "删除",
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteEmptyConfirm = false }) {
                        Text(
                            text = "取消",
                            color = Color(0xFF007AFF)
                        )
                    }
                }
            )
        }
    }
}

/**
 * 照片网格项
 * 
 * 支持显示 HDR/Live 标识、长按设置封面、多选模式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    image: ImageFile,
    showHdrBadge: Boolean = false,
    showMotionBadge: Boolean = false,
    onClick: (SourceRect) -> Unit,
    onSetCover: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelect: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageLoader = remember { CoilSetup.getImageLoader(context) }
    val coordinatesHolder = remember { AlbumLayoutCoordinatesHolder() }
    var showCoverMenu by remember { mutableStateOf(false) }
    
    // 选中状态的动画
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectionScale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .scale(selectionScale)
            .clip(RoundedCornerShape(4.dp))
            .onGloballyPositioned { coordinates ->
                coordinatesHolder.value = coordinates
            }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        // 多选模式下，点击切换选中状态
                        HapticFeedback.lightTap(context)
                        onToggleSelect()
                    } else {
                        // 普通模式下，点击查看大图
                        HapticFeedback.lightTap(context)
                        val rect = coordinatesHolder.value?.takeIf { it.isAttached }?.boundsInRoot()
                        val sourceRect = if (rect != null) {
                            SourceRect(
                                x = rect.left,
                                y = rect.top,
                                width = rect.width,
                                height = rect.height,
                                cornerRadius = 4f
                            )
                        } else {
                            SourceRect()
                        }
                        onClick(sourceRect)
                    }
                },
                onLongClick = {
                    HapticFeedback.heavyTap(context)
                    if (!isSelectionMode) {
                        // 长按进入多选模式
                        onLongPress()
                    } else if (onSetCover != null) {
                        // 多选模式下长按可以设置封面（可选）
                        showCoverMenu = true
                    }
                }
            )
    ) {
        // 缩略图缓存键：完整媒体身份 + 内容版本（C1/T03-THUMBKEY）。
        // 两网格与查看器共用 grid_ 命名空间（请求规格一致），查看器首开
        // 直接温命中网格缩略图。
        val cacheKey = remember(image.id, image.dateModified) { gridThumbnailCacheKey("grid_", image) }

        // 占位符背景色 - 在图片加载前显示，避免空白闪烁
        val isDarkTheme = LocalIsDarkTheme.current
        val placeholderColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
        
        // 先绘制占位符背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(placeholderColor)
        )
        
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(image.uri)
                .size(Size(240, 240))
                .precision(Precision.INEXACT)
                .bitmapConfig(Bitmap.Config.RGB_565)
                // 启用硬件位图加速（Android 8.0+）- 更快的渲染
                .allowHardware(true)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                // 禁用 crossfade 动画 - 直接显示，减少视觉延迟
                .crossfade(false)
                .build(),
            contentDescription = image.displayName,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // 选中状态遮罩和勾选标记
        if (isSelectionMode) {
            // 半透明遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) Color.Black.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
            )
            
            // 勾选图标
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .shadow(2.dp, CircleShape)
                    .background(
                        color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.9f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "已选中",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    // 空心圆
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Transparent, CircleShape)
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }
        
        // HDR / Live 标识
        val badges = rememberPhotoGridBadges(
            image = image,
            showHdr = showHdrBadge,
            showMotion = showMotionBadge
        )
        
        if (badges.isNotEmpty() && !isSelectionMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                badges.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // 设置封面菜单（非多选模式）
        if (showCoverMenu && onSetCover != null && !isSelectionMode) {
            DropdownMenu(
                expanded = showCoverMenu,
                onDismissRequest = { showCoverMenu = false },
                modifier = Modifier
                    .background(
                        color = Color(0xFF2C2C2E),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                DropdownMenuItem(
                    text = { 
                        Text(
                            "设为相册封面",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
                    onClick = {
                        showCoverMenu = false
                        onSetCover()
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private class AlbumLayoutCoordinatesHolder(var value: LayoutCoordinates? = null)

/**
 * 记住照片网格项的标识
 * 使用 EXIF/XMP 数据进行真正的 HDR/Motion 检测
 */
@Composable
private fun rememberPhotoGridBadges(
    image: ImageFile,
    showHdr: Boolean,
    showMotion: Boolean
): List<String> {
    val features = com.tabula.v3.ui.util.rememberImageFeatures(
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
 * 图集选择弹窗
 */
@Composable
private fun AlbumPickerDialog(
    albums: List<Album>,
    allImages: List<ImageFile>,
    title: String = "移动到图集",
    onAlbumSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val imageLoader = remember { CoilSetup.getImageLoader(context) }
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = Color(0xFF8E8E93)
    
    // 每个图集项高度约 72dp，最多显示 3.5 个（可以看到下面还有内容）
    val maxListHeight = 72.dp * 3.5f
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // 获取导航栏高度
            val navBarInsets = WindowInsets.navigationBars
            val navBarHeight = with(LocalDensity.current) { navBarInsets.getBottom(this).toDp() }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(backgroundColor)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* 阻止点击穿透 */ }
                    .padding(bottom = navBarHeight)  // 底部留出导航栏空间
            ) {
                // 顶部拖拽指示器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .background(
                                color = Color.Gray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
                
                // 标题
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                
                if (albums.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有其他图集可选择",
                            color = secondaryTextColor,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    // 图集列表 - 最多显示约 3.5 个
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxListHeight)
                    ) {
                        items(albums.size) { index ->
                            val album = albums[index]
                            // 查找封面图片（C1/T03-COVER：按完整媒体身份解析）
                            val coverImage = resolveAlbumCoverImage(album, allImages)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAlbumSelected(album.id)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 封面图片或 Emoji
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            color = if (album.color != null) 
                                                Color(album.color).copy(alpha = 0.2f) 
                                            else Color(0xFF007AFF).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (coverImage != null) {
                                        // C1/T03-COVERKEY：封面请求经生产构造器（身份+内容版本键）。
                                        AsyncImage(
                                            model = coverImageRequest(
                                                context, coverImage, "album_list_cover_",
                                                size = coil.size.Size(144, 144),
                                                crossfade = true, inexact = true
                                            ),
                                            contentDescription = album.name,
                                            imageLoader = imageLoader,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // 显示 Emoji 或默认图标
                                        Text(
                                            text = album.emoji ?: "📁",
                                            fontSize = 24.sp
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(14.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.name,
                                        color = textColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${album.imageCount} 张照片",
                                        color = secondaryTextColor,
                                        fontSize = 13.sp
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(scaleX = -1f, scaleY = 1f)  // 翻转成向右箭头
                                )
                            }
                        }
                    }
                }
                
                // 取消按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消",
                        color = Color(0xFF007AFF),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 通过 URI 从 MediaStore 查询图片信息
 * 用于加载已清理原图后同步到系统相册的图片
 */
