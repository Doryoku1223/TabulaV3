package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.data.repository.resolveCoverRow
import com.tabula.v3.ui.util.gridThumbnailCacheKey

import com.tabula.v3.data.repository.LocalImageRepository
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.StringDragSelectState
import com.tabula.v3.ui.util.gridDragHandlerString
import com.tabula.v3.ui.util.itemDragReceiverString
import com.tabula.v3.ui.util.rememberStringDragSelectState
import com.tabula.v3.ui.util.coverImageRequest
import com.tabula.v3.ui.util.toggleSelection
import kotlin.math.roundToInt

/**
 * ???????????
 * 
 * ???????????
 * 1. App??? - ??????????????
 * 2. ?????? - ??????????????????????
 */
@Composable
fun CategorizedAlbumsView(
    appAlbums: List<Album>?,
    systemBuckets: List<LocalImageRepository.SystemBucket>?,
    allImages: List<ImageFile>,
    onAppAlbumClick: (Album) -> Unit,
    onSystemBucketClick: (String) -> Unit,
    onReorderAlbums: (List<String>) -> Unit,
    onCreateAlbumClick: (() -> Unit)? = null,  // ????????????
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onEnterSelectionMode: (initialId: String) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    hideHeaders: Boolean = false, // ????????????
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 100.dp, // ????????
    headerContent: (@Composable () -> Unit)? = null,
    userScrollEnabled: Boolean = true,
    disableImageLoading: Boolean = false,  // ???????????????????????????????
    otherAlbums: List<Album> = emptyList(),  // ????"????"?????
    onOtherAlbumsClick: () -> Unit = {}     // ??"????"?????
) {
    val context = LocalContext.current
    // C1/T03-COVER：按数值 ID 分组的**多值**映射——跨卷同 ID 行不折叠，
    // 封面解析按封面卷归属选取（resolveCoverRow）。
    val imageMap = remember(allImages) { allImages.groupBy { it.id } }
    val selectableIds = remember(appAlbums, systemBuckets) {
        buildList {
            appAlbums?.forEach { add(it.id) }
            systemBuckets?.forEach { add(it.name) }
        }
    }
    val dragSelectState = rememberStringDragSelectState(
        items = selectableIds,
        itemKey = { it },
        selectedIds = selectedIds,
        onSelectionChange = onSelectionChange,
        onEnterSelectionMode = {}
    )
    val startSelectionForId: (String) -> Unit = { id ->
        if (isSelectionMode) {
            onSelectionChange(toggleSelection(selectedIds, id))
        } else {
            onEnterSelectionMode(id)
        }
    }
    
    // ??????????????? LazyColumn ????
    var isDraggingAlbum by remember { mutableStateOf(false) }
    
    // ========== ?????????? ==========
    // ??????????????????????????500ms ??????????????
    // ?????????????"??????"???????????????????????????
    var enableDragging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        enableDragging = true
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .gridDragHandlerString(dragSelectState, isSelectionMode),
        contentPadding = PaddingValues(top = topPadding, bottom = 100.dp),
        userScrollEnabled = userScrollEnabled && !isDraggingAlbum  // ???????????
    ) {
        if (headerContent != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    headerContent()
                }
            }
        }
        // App ??????? (???? appAlbums ??? null ????)
        if (appAlbums != null) {
            if (!hideHeaders) {
                item {
                    SectionHeader(
                        title = "App 图集",
                        subtitle = "${appAlbums.size} 个",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }

            if (appAlbums.isEmpty()) {
                // ??????????????????
                item {
                    if (onCreateAlbumClick != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CreateAlbumCard(
                                onClick = onCreateAlbumClick,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                isDarkTheme = isDarkTheme,
                                modifier = Modifier.weight(1f)
                            )
                            // ????????????????????
                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    } else {
                        EmptyAlbumHint(
                            text = "还没有创建图集\n点击上方“+”按钮创建第一个图集吧",
                            textColor = secondaryTextColor
                        )
                    }
                }
            } else if (hideHeaders) {
                // ??????? 3 ????????
                // ??????????????????????500ms ??????????????
                item {
                    if (enableDragging) {
                        // ???2??????????????????
                        DraggableAlbumsGridInternal(
                            albums = appAlbums,
                            allImages = allImages,
                            imageMap = imageMap,
                            onAlbumClick = onAppAlbumClick,
                            onReorder = onReorderAlbums,
                            onCreateAlbumClick = onCreateAlbumClick,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onSelectionChange = onSelectionChange,
                            onStartSelection = startSelectionForId,
                            dragSelectState = dragSelectState,
                            onDraggingChange = { dragging -> isDraggingAlbum = dragging },
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isDarkTheme = isDarkTheme,
                            lazyListState = listState,
                            disableImageLoading = disableImageLoading
                        )
                    } else {
                        // ???1???????????????????????????
                        SimpleAlbumsGridInternal(
                            albums = appAlbums,
                            imageMap = imageMap,
                            onAlbumClick = onAppAlbumClick,
                            onCreateAlbumClick = onCreateAlbumClick,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onSelectionChange = onSelectionChange,
                            onStartSelection = startSelectionForId,
                            dragSelectState = dragSelectState,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isDarkTheme = isDarkTheme,
                            disableImageLoading = disableImageLoading
                        )
                    }
                }
            } else {
                item {
                    // ?????????App??????
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // ??????????????????
                        if (onCreateAlbumClick != null) {
                            item(key = "create_new") {
                                CreateAlbumCardHorizontal(
                                    onClick = onCreateAlbumClick,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme
                                )
                            }
                        }
                        items(appAlbums, key = { it.id }) { album ->
                            val coverImage = album.coverImageId?.let { coverId ->
                                resolveCoverRow(album.coverVolumeName, coverId, imageMap[coverId].orEmpty())
                            }
                            AppAlbumCard(
                                album = album,
                                coverImage = coverImage,
                                onClick = {
                                    if (isSelectionMode) {
                                        onSelectionChange(toggleSelection(selectedIds, album.id))
                                    } else {
                                        onAppAlbumClick(album)
                                    }
                                },
                                onLongClick = { startSelectionForId(album.id) },
                                isSelectionMode = isSelectionMode,
                                isSelected = album.id in selectedIds,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                isDarkTheme = isDarkTheme,
                                disableImageLoading = disableImageLoading
                            )
                        }
                    }
                }
            }

            // ??? (??????????????)
            if (systemBuckets != null) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // "????"??????????????????
        if (otherAlbums.isNotEmpty()) {
            item(key = "other_albums_card") {
                OtherAlbumsCard(
                    count = otherAlbums.size,
                    totalImages = otherAlbums.sumOf { it.imageCount },
                    onClick = onOtherAlbumsClick,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    isDarkTheme = isDarkTheme
                )
            }
        }

        // ?????????? (???? systemBuckets ??? null ????)
        if (systemBuckets != null) {
            if (!hideHeaders) {
                item {
                    SectionHeader(
                        title = "系统相册",
                        subtitle = "${systemBuckets.size} 个",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }

            if (systemBuckets.isEmpty()) {
                item {
                    EmptyAlbumHint(
                        text = "暂无系统相册",
                        textColor = secondaryTextColor
                    )
                }
            } else {
                items(systemBuckets, key = { it.name }) { bucket ->
                    val coverImage = bucket.coverImageId?.let { coverId ->
                        resolveCoverRow(bucket.volumeName, coverId, imageMap[coverId].orEmpty())
                    }
                    SystemBucketRow(
                        bucket = bucket,
                        coverImage = coverImage,
                        onClick = {
                            if (isSelectionMode) {
                                onSelectionChange(toggleSelection(selectedIds, bucket.name))
                            } else {
                                onSystemBucketClick(bucket.name)
                            }
                        },
                        onLongClick = { startSelectionForId(bucket.name) },
                        isSelectionMode = isSelectionMode,
                        isSelected = bucket.name in selectedIds,
                        dragSelectState = dragSelectState,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }

        // ???????
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * ????????
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = secondaryTextColor,
            fontSize = 14.sp
        )
    }
}

/**
 * ???????
 */
@Composable
private fun EmptyAlbumHint(
    text: String,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * ???????
 */
@Composable
private fun SelectionIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
                contentDescription = "?????",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color.White, CircleShape)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun AppAlbumCard(
    album: Album,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    disableImageLoading: Boolean = false  // ????????????????????
) {
    val context = LocalContext.current

    // ??? coverImage ? null ???? coverImageId??????? ID ???? URI ??? fallback
    // C1/T03-COVER：封面 URI = 解析行的卷限定 URI；解析失败（缺失/歧义/
    // 暂时不可读）→ null 占位，不猜聚合 URI（跨卷串图向量）。
    val coverUri = coverImage?.uri
    
    // ????????????
    var loadFailed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .combinedClickable(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onClick()
                },
                onLongClick = {
                    HapticFeedback.lightTap(context)
                    onLongClick()
                }
            )
    ) {
        // ????
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
        ) {
            if (disableImageLoading) {
                // ????????????????????????
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                )
            } else if (coverUri != null && !loadFailed) {
                // ??????? - ?????????????????????????
                // C1/T03-COVER：键 = 封面行身份 + 内容版本（封面更换/编辑后刷新）。
                val coverCacheKey = remember(coverImage?.mediaIdentityKey(), coverImage?.dateModified) {
                    gridThumbnailCacheKey("album_thumb_", coverImage)
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUri)
                        .size(coil.size.Size(180, 180))  // ???????????????
                        .precision(coil.size.Precision.INEXACT)
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)  // ???????
                        .memoryCacheKey(coverCacheKey)
                        .diskCacheKey(coverCacheKey)
                        .allowHardware(true)
                        .crossfade(false)  // ?????????????
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { loadFailed = true }
                )
            } else {
                // ???????????????????????
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.zpcat1)
                        .crossfade(80)
                        .build(),
                    contentDescription = "?????????",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(if (isSelected) Color(0x33007AFF) else Color.Transparent)
                )
                SelectionIndicator(
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 标题
        Text(
            text = album.name,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 数量
        Text(
            text = "${album.imageCount} 张",
            color = secondaryTextColor,
            fontSize = 12.sp
        )
    }
}

/**
 * App????????????????????????
 */
@Composable
private fun AppAlbumGridCard(
    album: Album,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    disableImageLoading: Boolean = false  // ????????????????????
) {
    val context = LocalContext.current
    // C1/T03-COVER：封面 URI = 解析行的卷限定 URI；解析失败（缺失/歧义/
    // 暂时不可读）→ null 占位，不猜聚合 URI（跨卷串图向量）。
    val coverUri = coverImage?.uri
    // ????????????
    var loadFailed by remember { mutableStateOf(false) }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = {
                HapticFeedback.lightTap(context)
                onClick()
            },
            onLongClick = {
                HapticFeedback.lightTap(context)
                onLongClick()
            }
        )
    } else {
        Modifier.clickable {
            HapticFeedback.lightTap(context)
            onClick()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(clickModifier)
    ) {
        // ???? - ??????????????????????????
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))  // 3???????????
                .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
        ) {
            if (disableImageLoading) {
                // ????????????????????????
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                )
            } else if (coverUri != null && !loadFailed) {
                // ??????? - ?????????????????????????
                // C1/T03-COVER：键 = 封面行身份 + 内容版本（封面更换/编辑后刷新）。
                val coverCacheKey = remember(coverImage?.mediaIdentityKey(), coverImage?.dateModified) {
                    gridThumbnailCacheKey("album_thumb_", coverImage)
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUri)
                        .size(coil.size.Size(150, 150))  // ????????3????????
                        .precision(coil.size.Precision.INEXACT)
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)  // ???????
                        .memoryCacheKey(coverCacheKey)
                        .diskCacheKey(coverCacheKey)
                        .allowHardware(true)
                        .crossfade(false)  // ?????????????
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { loadFailed = true }
                )
            } else {
                // ???????????????????????
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.zpcat1)
                        .crossfade(80)
                        .build(),
                    contentDescription = "?????????",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(if (isSelected) Color(0x33007AFF) else Color.Transparent)
                )
                SelectionIndicator(
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ???? - 3????????????
        Text(
            text = album.name,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // 数量
        Text(
            text = "${album.imageCount} 张",
            color = secondaryTextColor,
            fontSize = 11.sp
        )
    }
}

/**
 * ????????????????
 */
@Composable
private fun SystemBucketRow(
    bucket: LocalImageRepository.SystemBucket,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    dragSelectState: StringDragSelectState<String>,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode) {
                    Modifier.itemDragReceiverString(dragSelectState, bucket.name)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onClick()
                },
                onLongClick = {
                    HapticFeedback.lightTap(context)
                    onLongClick()
                }
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ?????
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
        ) {
            if (coverImage != null) {
                // C1/T03-COVERKEY：请求经生产构造器（身份+内容版本缓存键）。
                AsyncImage(
                    model = coverImageRequest(
                        context, coverImage, "system_bucket_cover_", crossfade = true
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🖼️", fontSize = 24.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // ?????????
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bucket.name,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${bucket.imageCount} 张",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // ???
        if (isSelectionMode) {
            SelectionIndicator(
                isSelected = isSelected,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * ??????????????????
 */
@Composable
private fun CreateAlbumCard(
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
    ) {
        // ???? - ??? + ??
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF636366),
                fontSize = 48.sp,
                fontWeight = FontWeight.Light
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 标题
        Text(
            text = "新建图集",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // ??????????????????????????
        Text(
            text = " ",
            color = secondaryTextColor,
            fontSize = 11.sp
        )
    }
}

/**
 * ????????????????????
 */
@Composable
private fun CreateAlbumCardHorizontal(
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
    ) {
        // ???? - ??? + ??
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF636366),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 标题
        Text(
            text = "新建图集",
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // ??????????????????????????
        Text(
            text = " ",
            color = secondaryTextColor,
            fontSize = 12.sp
        )
    }
}

/**
 * "????"????
 *
 * ??????????????"????"????
 */
@Composable
private fun OtherAlbumsCard(
    count: Int,
    totalImages: Int,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ?????
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\uD83D\uDCC1",  // folder emoji (📁)
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "其他相册",
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${count} 个相册 · ${totalImages} 张照片",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = secondaryTextColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * ?????????????????????????
 * 
 * ?????????????3?????????????????????
 * ???????????????????
 * ??????????????????????????????
 */
@Composable
private fun DraggableAlbumsGridInternal(
    albums: List<Album>,
    allImages: List<ImageFile>,
    imageMap: Map<Long, List<ImageFile>>,
    onAlbumClick: (Album) -> Unit,
    onReorder: (List<String>) -> Unit,
    onCreateAlbumClick: (() -> Unit)?,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onStartSelection: (String) -> Unit,
    dragSelectState: StringDragSelectState<String>,
    onDraggingChange: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    lazyListState: LazyListState,
    disableImageLoading: Boolean = false  // ????????????????????
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // ========== ?????????? ==========
    // ??????????????????????????????? StateFlow ??????? UI "????"
    var orderedAlbums by remember { mutableStateOf(albums) }
    
    // ?????? albums ?????????????????????????????????
    // ??? albums ?? ID ?????? key??????? order ??????????????
    val albumIds = remember(albums) { albums.map { it.id }.toSet() }
    LaunchedEffect(albumIds) {
        // ?? albums ????????????????/?????????????
        orderedAlbums = albums
    }
    
    // ?????
    var draggingAlbumId by remember { mutableStateOf<String?>(null) }
    var draggingStartIndex by remember { mutableIntStateOf(-1) }
    var currentTargetIndex by remember { mutableIntStateOf(-1) }  // ????????????
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    
    // ========== ????? ==========
    var hasDragged by remember { mutableStateOf(false) }  // ??????????????????????????????
    
    // ?????????
    val itemWidth = remember { mutableFloatStateOf(0f) }
    val itemHeight = remember { mutableFloatStateOf(0f) }
    val itemSpacing = with(density) { 8.dp.toPx() }  // ??????
    val columnsPerRow = 3
    
    // ========== ???????????? ==========
    // ????????????? Y ???????????
    var currentTouchScreenY by remember { mutableFloatStateOf(0f) }
    // ??????????????
    var isAutoScrolling by remember { mutableStateOf(false) }
    // ???????????????????- ?????????????????????
    // ????? 50dp????????????????????????
    val edgeThresholdPx = with(density) { 50.dp.toPx() }
    // ???????????????????? 24-32dp????????????????
    val topSafeArea = with(density) { 40.dp.toPx() }
    // ????????????????????????????????????????????????
    val bottomSafeArea = with(density) { 24.dp.toPx() }
    // ??????????????/???
    val maxScrollSpeedPx = with(density) { 18.dp.toPx() }
    // ???????????????/???
    val minScrollSpeedPx = with(density) { 4.dp.toPx() }
    // ????????????????????????????????????
    var scrollCompensation by remember { mutableFloatStateOf(0f) }
    
    // ?????????
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // ?????????? - ?????????????
    LaunchedEffect(draggingAlbumId) {
        if (draggingAlbumId != null) {
            // ???????????
            scrollCompensation = 0f
            // ??????????????????????
            android.util.Log.d("DragScroll", "=== ?????? ===")
            android.util.Log.d("DragScroll", "??????: $screenHeightPx, ?????????: $topSafeArea, ????????: $bottomSafeArea, ??????: $edgeThresholdPx")
            while (draggingAlbumId != null) {
                val touchY = currentTouchScreenY
                
                // ?????????????????
                // ????????????????????????? edgeThreshold ???????????????
                val topEdgeStart = topSafeArea
                val topEdgeEnd = topSafeArea + edgeThresholdPx
                // ????????????????? bottomSafeArea + edgeThreshold ??????????????????
                val bottomEdgeStart = screenHeightPx - bottomSafeArea - edgeThresholdPx
                val bottomEdgeEnd = screenHeightPx - bottomSafeArea
                
                when {
                    touchY > topEdgeStart && touchY < topEdgeEnd -> {
                        // ???????????? - ?????????????????????????
                        // ????? topEdgeStart?????????
                        val distanceFromEdgeStart = topEdgeEnd - touchY
                        val intensity = (distanceFromEdgeStart / edgeThresholdPx).coerceIn(0f, 1f)
                        val speed = minScrollSpeedPx + (maxScrollSpeedPx - minScrollSpeedPx) * intensity
                        val actualScrolled = lazyListState.scrollBy(-speed)
                        // ??????????????????????
                        // scrollBy ???????????????????????????????????????
                        scrollCompensation += actualScrolled
                        dragOffsetY += actualScrolled
                        isAutoScrolling = true
                        android.util.Log.d("DragScroll", "????????: touchY=$touchY, ????=[$topEdgeStart, $topEdgeEnd], intensity=$intensity, scrolled=$actualScrolled")
                    }
                    touchY > bottomEdgeStart && touchY < bottomEdgeEnd -> {
                        // ??????????? - ?????????????????????????
                        // ????? bottomEdgeEnd?????????
                        val distanceFromEdgeStart = touchY - bottomEdgeStart
                        val intensity = (distanceFromEdgeStart / edgeThresholdPx).coerceIn(0f, 1f)
                        val speed = minScrollSpeedPx + (maxScrollSpeedPx - minScrollSpeedPx) * intensity
                        val actualScrolled = lazyListState.scrollBy(speed)
                        // ??????????????????????
                        // scrollBy ???????????????????????????????????????
                        scrollCompensation += actualScrolled
                        dragOffsetY += actualScrolled
                        isAutoScrolling = true
                        android.util.Log.d("DragScroll", "???????: touchY=$touchY, ????=[$bottomEdgeStart, $bottomEdgeEnd], intensity=$intensity, scrolled=$actualScrolled")
                    }
                    else -> {
                        if (isAutoScrolling) {
                            android.util.Log.d("DragScroll", "??????: touchY=$touchY, ????????=[$topEdgeStart, $topEdgeEnd], ???????=[$bottomEdgeStart, $bottomEdgeEnd]")
                        }
                        isAutoScrolling = false
                    }
                }
                delay(16L)  // ? 60fps
            }
            isAutoScrolling = false
            scrollCompensation = 0f
            android.util.Log.d("DragScroll", "=== ??????? ===")
        }
    }
    
    // ????????????????????? + ?????- ???????????????????????
    val hasCreateButton = onCreateAlbumClick != null
    // ?????????????????????albumIndex ??? +1 ???????????????
    val gridOffset = if (hasCreateButton) 1 else 0
    
    // ????????????????????? albumIndex?????? gridIndex??
    fun getTargetIndex(startAlbumIndex: Int, offsetX: Float, offsetY: Float): Int {
        if (itemWidth.floatValue <= 0 || itemHeight.floatValue <= 0) return startAlbumIndex
        
        // ?????????????????
        val startGridIndex = startAlbumIndex + gridOffset
        val startRow = startGridIndex / columnsPerRow
        val startCol = startGridIndex % columnsPerRow
        
        // ????????????????????????????
        val cellWidth = itemWidth.floatValue + itemSpacing
        val cellHeight = itemHeight.floatValue + with(density) { 12.dp.toPx() }  // ??????
        
        val colOffset = (offsetX / cellWidth).roundToInt()
        val rowOffset = (offsetY / cellHeight).roundToInt()
        
        // ??????????????
        var newCol = startCol + colOffset
        var newRow = startRow + rowOffset
        
        // ????????????????????????????- ?????????????
        val totalGridItems = orderedAlbums.size + gridOffset
        val maxRow = (totalGridItems - 1) / columnsPerRow
        
        // ????????
        newRow = newRow.coerceIn(0, maxRow)
        
        // ???????????????
        var newGridIndex = newRow * columnsPerRow + newCol
        
        // ??????????????????????gridIndex = 0??
        if (hasCreateButton && newGridIndex < 1) {
            newGridIndex = 1
        }
        
        // ??????????????????????? - ?????????????
        // ????????? lastIndex ? -1 ???? coerceIn ????
        val maxGridIndex = orderedAlbums.lastIndex.coerceAtLeast(0) + gridOffset
        newGridIndex = newGridIndex.coerceIn(gridOffset, maxGridIndex)
        
        // ????? albumIndex
        return newGridIndex - gridOffset
    }
    
    // ??????????????????????????
    fun calculateDisplacement(albumIndex: Int): Pair<Float, Float> {
        if (draggingStartIndex < 0 || currentTargetIndex < 0) return 0f to 0f
        if (albumIndex == draggingStartIndex) return 0f to 0f  // ???????????????????
        
        val cellWidth = itemWidth.floatValue + itemSpacing
        val cellHeight = itemHeight.floatValue + with(density) { 12.dp.toPx() }
        
        // ???????????????????
        val fromIdx = draggingStartIndex
        val toIdx = currentTargetIndex
        
        if (fromIdx < toIdx) {
            // ????????fromIdx ?? toIdx ??????????????????
            if (albumIndex in (fromIdx + 1)..toIdx) {
                // ???????????????????????????
                val currentGridIndex = albumIndex + gridOffset
                val currentRow = currentGridIndex / columnsPerRow
                val currentCol = currentGridIndex % columnsPerRow
                
                val prevGridIndex = currentGridIndex - 1
                val prevRow = prevGridIndex / columnsPerRow
                val prevCol = prevGridIndex % columnsPerRow
                
                val dx = (prevCol - currentCol) * cellWidth
                val dy = (prevRow - currentRow) * cellHeight
                return dx to dy
            }
        } else if (fromIdx > toIdx) {
            // ????????toIdx ?? fromIdx ??????????????????
            if (albumIndex in toIdx until fromIdx) {
                // ???????????????????????????
                val currentGridIndex = albumIndex + gridOffset
                val currentRow = currentGridIndex / columnsPerRow
                val currentCol = currentGridIndex % columnsPerRow
                
                val nextGridIndex = currentGridIndex + 1
                val nextRow = nextGridIndex / columnsPerRow
                val nextCol = nextGridIndex % columnsPerRow
                
                val dx = (nextCol - currentCol) * cellWidth
                val dy = (nextRow - currentRow) * cellHeight
                return dx to dy
            }
        }
        
        return 0f to 0f
    }
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ??????? - ?????????????
        val totalItems = if (hasCreateButton) orderedAlbums.size + 1 else orderedAlbums.size
        val rowCount = (totalItems + columnsPerRow - 1) / columnsPerRow
        
        for (rowIndex in 0 until rowCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (colIndex in 0 until columnsPerRow) {
                    val itemIndex = rowIndex * columnsPerRow + colIndex
                    
                    val createClick = onCreateAlbumClick
                    if (createClick != null && itemIndex == 0) {
                        // ?????????????????
                        CreateAlbumCard(
                            onClick = createClick,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // ?????? - ?????????????
                        val albumIndex = if (hasCreateButton) itemIndex - 1 else itemIndex
                        
                        if (albumIndex in orderedAlbums.indices) {
                            val album = orderedAlbums[albumIndex]
                            
                            // ??? key ?? Compose ??????????????????????????????????????
                            androidx.compose.runtime.key(album.id) {
                                val isDragging = draggingAlbumId == album.id
                                val coverImage = album.coverImageId?.let { coverId ->
                                    resolveCoverRow(album.coverVolumeName, coverId, imageMap[coverId].orEmpty())
                                }
                                
                                // ??????????
                                val (displacementX, displacementY) = calculateDisplacement(albumIndex)
                                
                                // ???????
                                val animatedDisplacementX by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = displacementX,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    ),
                                    label = "displacement_x"
                                )
                                val animatedDisplacementY by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = displacementY,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    ),
                                    label = "displacement_y"
                                )
                                
                                // ????????????????????
                                var cardScreenY by remember { mutableFloatStateOf(0f) }
                                // ???????????????????? Y ????
                                var touchStartScreenY by remember { mutableFloatStateOf(0f) }
                                // ??????????????????????????????????
                                var rawDragOffsetY by remember { mutableFloatStateOf(0f) }
                                // 长按1秒自动进入多选模式的定时器
                                var selectionTimerTriggered by remember { mutableStateOf(false) }
                                var selectionTimerJob by remember { mutableStateOf<Job?>(null) }
                                
                                Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            // ??????????????????
                                            translationX = dragOffsetX
                                            translationY = dragOffsetY
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                            shadowElevation = 16f
                                        } else {
                                            // ????????????????
                                            translationX = animatedDisplacementX
                                            translationY = animatedDisplacementY
                                        }
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        if (itemWidth.floatValue == 0f) {
                                            itemWidth.floatValue = coordinates.size.width.toFloat()
                                            itemHeight.floatValue = coordinates.size.height.toFloat()
                                        }
                                        // ?????????????? Y ????
                                        cardScreenY = coordinates.positionInRoot().y
                                    }
                                    .then(
                                        if (isSelectionMode) {
                                            Modifier.itemDragReceiverString(dragSelectState, album.id)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .pointerInput(isSelectionMode, album.id) {
                                        if (!isSelectionMode) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { startOffset ->
                                                    HapticFeedback.heavyTap(context)
                                                    draggingAlbumId = album.id
                                                    draggingStartIndex = albumIndex
                                                    currentTargetIndex = albumIndex
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    rawDragOffsetY = 0f
                                                    hasDragged = false  // ??????????
                                                    selectionTimerTriggered = false
                                                    // ???????????????????? Y ????
                                                    touchStartScreenY = cardScreenY + startOffset.y
                                                    currentTouchScreenY = touchStartScreenY
                                                    android.util.Log.d("DragScroll", "onDragStart: cardScreenY=$cardScreenY, startOffset.y=${startOffset.y}, touchStartScreenY=$touchStartScreenY")
                                                    onDraggingChange(true)
                                                    // 启动长按1秒自动进入多选模式的定时器
                                                    // detectDragGesturesAfterLongPress 约在400ms后触发 onDragStart，
                                                    // 再等600ms达到约1秒总时长
                                                    selectionTimerJob?.cancel()
                                                    selectionTimerJob = coroutineScope.launch {
                                                        delay(600L)
                                                        if (!hasDragged && !selectionTimerTriggered) {
                                                            selectionTimerTriggered = true
                                                            HapticFeedback.heavyTap(context)
                                                            onStartSelection(album.id)
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    // ?????????
                                                    selectionTimerJob?.cancel()
                                                    selectionTimerJob = null
                                                    currentTouchScreenY = 0f
                                                    if (selectionTimerTriggered) {
                                                        // 已通过定时器进入多选模式，仅做清理
                                                    } else if (!hasDragged) {
                                                        HapticFeedback.lightTap(context)
                                                        onStartSelection(album.id)
                                                    } else {
                                                        val targetIdx = currentTargetIndex
                                                        val startIdx = draggingStartIndex
                                                        HapticFeedback.lightTap(context)
                                                        if (targetIdx != startIdx && targetIdx >= 0) {
                                                            val newList = orderedAlbums.toMutableList()
                                                            val item = newList.removeAt(startIdx)
                                                            newList.add(targetIdx, item)
                                                            orderedAlbums = newList
                                                            onReorder(newList.map { it.id })
                                                        }
                                                    }
                                                    // ?????????????
                                                    draggingAlbumId = null
                                                    draggingStartIndex = -1
                                                    currentTargetIndex = -1
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    rawDragOffsetY = 0f
                                                    hasDragged = false
                                                    selectionTimerTriggered = false
                                                    onDraggingChange(false)
                                                },
                                                onDragCancel = {
                                                    // ?????????
                                                    selectionTimerJob?.cancel()
                                                    selectionTimerJob = null
                                                    currentTouchScreenY = 0f
                                                    draggingAlbumId = null
                                                    draggingStartIndex = -1
                                                    currentTargetIndex = -1
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    rawDragOffsetY = 0f
                                                    hasDragged = false
                                                    selectionTimerTriggered = false
                                                    onDraggingChange(false)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    // 定时器已触发多选，忽略后续拖动
                                                    if (selectionTimerTriggered) {
                                                        change.consume()
                                                        return@detectDragGesturesAfterLongPress
                                                    }
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    dragOffsetY += dragAmount.y
                                                    // ??????????????????????????????????
                                                    rawDragOffsetY += dragAmount.y
                                                    // 拖动阈值：40像素，避免手指微小抖动误判为拖动
                                                    val dragThreshold = 40f
                                                    if (!hasDragged && (kotlin.math.abs(dragOffsetX) > dragThreshold || kotlin.math.abs(dragOffsetY) > dragThreshold)) {
                                                        hasDragged = true
                                                        // 用户确实在拖动，取消多选定时器
                                                        selectionTimerJob?.cancel()
                                                    }
                                                    // ??????????????? Y ??????????????????
                                                    // ??????????????????????????
                                                    currentTouchScreenY = touchStartScreenY + rawDragOffsetY
                                                    // ??????????????????????????
                                                    val newTarget = getTargetIndex(draggingStartIndex, dragOffsetX, dragOffsetY)
                                                    if (newTarget != currentTargetIndex) {
                                                        currentTargetIndex = newTarget
                                                        HapticFeedback.lightTap(context)
                                                    }
                                                }
                                            )
                                        }
                                    }
                            ) {
                                AppAlbumGridCard(
                                    album = album,
                                    coverImage = coverImage,
                                    onClick = {
                                        if (isSelectionMode) {
                                            onSelectionChange(toggleSelection(selectedIds, album.id))
                                        } else if (!isDragging) {
                                            onAlbumClick(album)
                                        }
                                    },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = album.id in selectedIds,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme,
                                    modifier = Modifier.fillMaxWidth(),
                                    disableImageLoading = disableImageLoading
                                )
                            }
                            }  // ??? key ??
                        } else {
                            // ?????
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleAlbumsGridInternal(
    albums: List<Album>,
    imageMap: Map<Long, List<ImageFile>>,
    onAlbumClick: (Album) -> Unit,
    onCreateAlbumClick: (() -> Unit)?,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onStartSelection: (String) -> Unit,
    dragSelectState: StringDragSelectState<String>,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    disableImageLoading: Boolean = false
) {
    val context = LocalContext.current
    val columnsPerRow = 3
    
    // ????????????????????? + ?????
    val hasCreateButton = onCreateAlbumClick != null
    val totalItems = if (hasCreateButton) albums.size + 1 else albums.size
    val rowCount = (totalItems + columnsPerRow - 1) / columnsPerRow
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        for (rowIndex in 0 until rowCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (colIndex in 0 until columnsPerRow) {
                    val itemIndex = rowIndex * columnsPerRow + colIndex
                    
                    val createClick = onCreateAlbumClick
                    if (createClick != null && itemIndex == 0) {
                        // ?????????????????
                        CreateAlbumCard(
                            onClick = createClick,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // ??????
                        val albumIndex = if (hasCreateButton) itemIndex - 1 else itemIndex
                        
                        if (albumIndex in albums.indices) {
                            val album = albums[albumIndex]
                            val coverImage = album.coverImageId?.let { coverId ->
                                resolveCoverRow(album.coverVolumeName, coverId, imageMap[coverId].orEmpty())
                            }
                            
                            Box(modifier = Modifier.weight(1f)) {
                                AppAlbumGridCard(
                                    album = album,
                                    coverImage = coverImage,
                                    onClick = {
                                        if (isSelectionMode) {
                                            onSelectionChange(toggleSelection(selectedIds, album.id))
                                        } else {
                                            onAlbumClick(album)
                                        }
                                    },
                                    onLongClick = { onStartSelection(album.id) },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = album.id in selectedIds,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isSelectionMode) {
                                                Modifier.itemDragReceiverString(dragSelectState, album.id)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    disableImageLoading = disableImageLoading
                                )
                            }
                        } else {
                            // ?????
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
