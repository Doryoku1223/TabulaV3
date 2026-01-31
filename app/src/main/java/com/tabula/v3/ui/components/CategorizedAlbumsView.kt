package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.tabula.v3.data.repository.LocalImageRepository
import com.tabula.v3.ui.util.HapticFeedback
import kotlin.math.roundToInt

/**
 * 分类相册网格
 * 
 * 显示两个分类：
 * 1. App图集 - 软件内创建的相册
 * 2. 手机相册 - 系统自带的相册（来自不同文件夹）
 */
@Composable
fun CategorizedAlbumsView(
    appAlbums: List<Album>?,
    systemBuckets: List<LocalImageRepository.SystemBucket>?,
    allImages: List<ImageFile>,
    onAppAlbumClick: (Album) -> Unit,
    onSystemBucketClick: (String) -> Unit,
    onReorderAlbums: (List<String>) -> Unit,
    onCreateAlbumClick: (() -> Unit)? = null,  // 新建图集点击回调
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    hideHeaders: Boolean = false, // 是否隐藏分节标题
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 100.dp, // 顶部内边距
    headerContent: (@Composable () -> Unit)? = null,
    userScrollEnabled: Boolean = true
) {
    val context = LocalContext.current
    val imageMap = remember(allImages) { allImages.associateBy { it.id } }
    
    // 拖拽状态：拖拽时禁用 LazyColumn 滚动
    var isDraggingAlbum by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = 100.dp),
        userScrollEnabled = userScrollEnabled && !isDraggingAlbum  // 拖拽时禁用滚动
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
        // App 图集区域 (仅当 appAlbums 不为 null 时显示)
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
                // 空状态：显示新建图集卡片
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
                            // 填充两个空位保持布局对齐
                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    } else {
                        EmptyAlbumHint(
                            text = "还没有创建图集\n在滑一滑界面归类照片即可创建",
                            textColor = secondaryTextColor
                        )
                    }
                }
            } else if (hideHeaders) {
                // 简化后使用 3 列网格布局，支持拖拽排序
                item {
                    DraggableAlbumsGridInternal(
                        albums = appAlbums,
                        allImages = allImages,
                        imageMap = imageMap,
                        onAlbumClick = onAppAlbumClick,
                        onReorder = onReorderAlbums,
                        onCreateAlbumClick = onCreateAlbumClick,
                        onDraggingChange = { dragging -> isDraggingAlbum = dragging },
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        isDarkTheme = isDarkTheme,
                        lazyListState = listState
                    )
                }
            } else {
                item {
                    // 横向滚动的App图集卡片
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 新建图集按钮放在最前面
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
                            val coverImage = album.coverImageId?.let { imageMap[it] }
                            AppAlbumCard(
                                album = album,
                                coverImage = coverImage,
                                onClick = { onAppAlbumClick(album) },
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            }

            // 分隔 (如果下方还有内容)
            if (systemBuckets != null) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // 手机相册区域 (仅当 systemBuckets 不为 null 时显示)
        if (systemBuckets != null) {
            if (!hideHeaders) {
                item {
                    SectionHeader(
                        title = "手机相册",
                        subtitle = "${systemBuckets.size} 个",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }

            if (systemBuckets.isEmpty()) {
                item {
                    EmptyAlbumHint(
                        text = "没有找到手机相册",
                        textColor = secondaryTextColor
                    )
                }
            } else {
                items(systemBuckets, key = { it.name }) { bucket ->
                    val coverImage = bucket.coverImageId?.let { imageMap[it] }
                    SystemBucketRow(
                        bucket = bucket,
                        coverImage = coverImage,
                        onClick = { onSystemBucketClick(bucket.name) },
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }

        // 底部留白
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 分区标题
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
 * 空状态提示
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
 * App图集卡片（横向滚动版）
 */
@Composable
private fun AppAlbumCard(
    album: Album,
    coverImage: ImageFile?,
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
        // 封面
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
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
                // 没有封面时显示小猫咪图片
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.zpcat1)
                        .crossfade(true)
                        .build(),
                    contentDescription = "空图集封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 名称
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
 * App图集卡片（网格布局版，一行三个）
 */
@Composable
private fun AppAlbumGridCard(
    album: Album,
    coverImage: ImageFile?,
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
        // 封面 - 使用宽度自适应，保持正方形比例
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))  // 3列布局圆角稍小
                .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
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
                // 没有封面时显示小猫咪图片
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.zpcat1)
                        .crossfade(true)
                        .build(),
                    contentDescription = "空图集封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 名称 - 3列布局字体稍小
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
 * 系统相册行（列表样式）
 */
@Composable
private fun SystemBucketRow(
    bucket: LocalImageRepository.SystemBucket,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩略图
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📷", fontSize = 24.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 名称和数量
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

        // 箭头
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = secondaryTextColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 新建图集卡片（网格布局版）
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
        // 封面 - 显示 + 号
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

        // 名称
        Text(
            text = "新建图集",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // 占位符（保持与其他卡片高度一致）
        Text(
            text = " ",
            color = secondaryTextColor,
            fontSize = 11.sp
        )
    }
}

/**
 * 新建图集卡片（横向滚动版）
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
        // 封面 - 显示 + 号
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

        // 名称
        Text(
            text = "新建图集",
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 占位符（保持与其他卡片高度一致）
        Text(
            text = " ",
            color = secondaryTextColor,
            fontSize = 12.sp
        )
    }
}

/**
 * 可拖拽排序的相册网格（内部组件）
 * 
 * 支持长按拖拽排序，3列布局，第一个位置为新建按钮
 * 支持拖拽到边缘时自动滚动
 */
@Composable
private fun DraggableAlbumsGridInternal(
    albums: List<Album>,
    allImages: List<ImageFile>,
    imageMap: Map<Long, ImageFile>,
    onAlbumClick: (Album) -> Unit,
    onReorder: (List<String>) -> Unit,
    onCreateAlbumClick: (() -> Unit)?,
    onDraggingChange: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    lazyListState: LazyListState
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // 拖拽状态
    var draggingAlbumId by remember { mutableStateOf<String?>(null) }
    var draggingStartIndex by remember { mutableIntStateOf(-1) }
    var currentTargetIndex by remember { mutableIntStateOf(-1) }  // 当前预览目标位置
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    
    // 网格布局参数
    val itemWidth = remember { mutableFloatStateOf(0f) }
    val itemHeight = remember { mutableFloatStateOf(0f) }
    val itemSpacing = with(density) { 8.dp.toPx() }  // 卡片间距
    val columnsPerRow = 3
    
    // ========== 自动滚动相关状态 ==========
    // 当前触摸点的屏幕 Y 坐标（实时更新）
    var currentTouchScreenY by remember { mutableFloatStateOf(0f) }
    // 是否正在自动滚动
    var isAutoScrolling by remember { mutableStateOf(false) }
    // 边缘检测区域大小（像素）- 只有进入这个区域才触发滚动
    // 设置为 50dp，这样只有非常靠近边缘时才会触发
    val edgeThresholdPx = with(density) { 50.dp.toPx() }
    // 顶部安全区域（状态栏高度，约 24-32dp，这里设置稍大一点）
    val topSafeArea = with(density) { 40.dp.toPx() }
    // 底部安全区域（底部导航栏等，设置较小值使触发区域更靠近底部）
    val bottomSafeArea = with(density) { 24.dp.toPx() }
    // 最大滚动速度（像素/帧）
    val maxScrollSpeedPx = with(density) { 18.dp.toPx() }
    // 最小滚动速度（像素/帧）
    val minScrollSpeedPx = with(density) { 4.dp.toPx() }
    // 滚动补偿值（用于在自动滚动时保持卡片跟手）
    var scrollCompensation by remember { mutableFloatStateOf(0f) }
    
    // 获取屏幕高度
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // 自动滚动协程 - 基于拖拽状态运行
    LaunchedEffect(draggingAlbumId) {
        if (draggingAlbumId != null) {
            // 重置滚动补偿
            scrollCompensation = 0f
            // 拖拽开始，启动滚动检测循环
            android.util.Log.d("DragScroll", "=== 拖拽开始 ===")
            android.util.Log.d("DragScroll", "屏幕高度: $screenHeightPx, 顶部安全区: $topSafeArea, 底部安全区: $bottomSafeArea, 边缘阈值: $edgeThresholdPx")
            while (draggingAlbumId != null) {
                val touchY = currentTouchScreenY
                
                // 计算顶部和底部边缘区域
                // 顶部：从安全区域末端开始，向下 edgeThreshold 的区域触发向上滚动
                val topEdgeStart = topSafeArea
                val topEdgeEnd = topSafeArea + edgeThresholdPx
                // 底部：从屏幕底部往上 bottomSafeArea + edgeThreshold 的位置开始触发向下滚动
                val bottomEdgeStart = screenHeightPx - bottomSafeArea - edgeThresholdPx
                val bottomEdgeEnd = screenHeightPx - bottomSafeArea
                
                when {
                    touchY > topEdgeStart && touchY < topEdgeEnd -> {
                        // 在顶部边缘区域 - 向上滚动（列表内容向下移动）
                        // 越靠近 topEdgeStart，滚动越快
                        val distanceFromEdgeStart = topEdgeEnd - touchY
                        val intensity = (distanceFromEdgeStart / edgeThresholdPx).coerceIn(0f, 1f)
                        val speed = minScrollSpeedPx + (maxScrollSpeedPx - minScrollSpeedPx) * intensity
                        val actualScrolled = lazyListState.scrollBy(-speed)
                        // 补偿滚动量，保持卡片跟手
                        // scrollBy 返回实际滚动量（向上为负），需要补偿相同的量
                        scrollCompensation += actualScrolled
                        dragOffsetY += actualScrolled
                        isAutoScrolling = true
                        android.util.Log.d("DragScroll", "顶部滚动: touchY=$touchY, 区域=[$topEdgeStart, $topEdgeEnd], intensity=$intensity, scrolled=$actualScrolled")
                    }
                    touchY > bottomEdgeStart && touchY < bottomEdgeEnd -> {
                        // 在底部边缘区域 - 向下滚动（列表内容向上移动）
                        // 越靠近 bottomEdgeEnd，滚动越快
                        val distanceFromEdgeStart = touchY - bottomEdgeStart
                        val intensity = (distanceFromEdgeStart / edgeThresholdPx).coerceIn(0f, 1f)
                        val speed = minScrollSpeedPx + (maxScrollSpeedPx - minScrollSpeedPx) * intensity
                        val actualScrolled = lazyListState.scrollBy(speed)
                        // 补偿滚动量，保持卡片跟手
                        // scrollBy 返回实际滚动量（向下为正），需要补偿相同的量
                        scrollCompensation += actualScrolled
                        dragOffsetY += actualScrolled
                        isAutoScrolling = true
                        android.util.Log.d("DragScroll", "底部滚动: touchY=$touchY, 区域=[$bottomEdgeStart, $bottomEdgeEnd], intensity=$intensity, scrolled=$actualScrolled")
                    }
                    else -> {
                        if (isAutoScrolling) {
                            android.util.Log.d("DragScroll", "停止滚动: touchY=$touchY, 顶部区域=[$topEdgeStart, $topEdgeEnd], 底部区域=[$bottomEdgeStart, $bottomEdgeEnd]")
                        }
                        isAutoScrolling = false
                    }
                }
                delay(16L)  // 约 60fps
            }
            isAutoScrolling = false
            scrollCompensation = 0f
            android.util.Log.d("DragScroll", "=== 拖拽结束 ===")
        }
    }
    
    // 构建显示项目列表（新建按钮 + 图集）- 提前定义，因为下面的函数需要用
    val hasCreateButton = onCreateAlbumClick != null
    // 网格偏移：如果有新建按钮，albumIndex 需要 +1 才是实际的网格位置
    val gridOffset = if (hasCreateButton) 1 else 0
    
    // 计算拖拽目标位置（返回的是 albumIndex，不是 gridIndex）
    fun getTargetIndex(startAlbumIndex: Int, offsetX: Float, offsetY: Float): Int {
        if (itemWidth.floatValue <= 0 || itemHeight.floatValue <= 0) return startAlbumIndex
        
        // 转换为网格位置来计算
        val startGridIndex = startAlbumIndex + gridOffset
        val startRow = startGridIndex / columnsPerRow
        val startCol = startGridIndex % columnsPerRow
        
        // 计算水平和垂直方向的格子偏移（考虑间距）
        val cellWidth = itemWidth.floatValue + itemSpacing
        val cellHeight = itemHeight.floatValue + with(density) { 12.dp.toPx() }  // 垂直间距
        
        val colOffset = (offsetX / cellWidth).roundToInt()
        val rowOffset = (offsetY / cellHeight).roundToInt()
        
        // 计算新的网格位置
        var newCol = startCol + colOffset
        var newRow = startRow + rowOffset
        
        // 计算总网格项数（包括新建按钮）
        val totalGridItems = albums.size + gridOffset
        val maxRow = (totalGridItems - 1) / columnsPerRow
        
        // 限制行范围
        newRow = newRow.coerceIn(0, maxRow)
        
        // 计算新的网格索引
        var newGridIndex = newRow * columnsPerRow + newCol
        
        // 确保不会拖到新建按钮的位置（gridIndex = 0）
        if (hasCreateButton && newGridIndex < 1) {
            newGridIndex = 1
        }
        
        // 确保不超过最后一个图集的位置
        val maxGridIndex = albums.lastIndex + gridOffset
        newGridIndex = newGridIndex.coerceIn(gridOffset, maxGridIndex)
        
        // 转换回 albumIndex
        return newGridIndex - gridOffset
    }
    
    // 计算非拖拽卡片的位移（让位动画）
    fun calculateDisplacement(albumIndex: Int): Pair<Float, Float> {
        if (draggingStartIndex < 0 || currentTargetIndex < 0) return 0f to 0f
        if (albumIndex == draggingStartIndex) return 0f to 0f  // 正在拖拽的卡片不需要位移
        
        val cellWidth = itemWidth.floatValue + itemSpacing
        val cellHeight = itemHeight.floatValue + with(density) { 12.dp.toPx() }
        
        // 计算这个卡片是否需要让位
        val fromIdx = draggingStartIndex
        val toIdx = currentTargetIndex
        
        if (fromIdx < toIdx) {
            // 向后拖拽：fromIdx 和 toIdx 之间的卡片需要向前移动一格
            if (albumIndex in (fromIdx + 1)..toIdx) {
                // 使用网格位置计算（考虑新建按钮偏移）
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
            // 向前拖拽：toIdx 和 fromIdx 之间的卡片需要向后移动一格
            if (albumIndex in toIdx until fromIdx) {
                // 使用网格位置计算（考虑新建按钮偏移）
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
        // 按行显示
        val totalItems = if (hasCreateButton) albums.size + 1 else albums.size
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
                        // 新建按钮（第一个位置）
                        CreateAlbumCard(
                            onClick = createClick,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // 图集卡片
                        val albumIndex = if (hasCreateButton) itemIndex - 1 else itemIndex
                        
                        if (albumIndex in albums.indices) {
                            val album = albums[albumIndex]
                            
                            // 使用 key 让 Compose 正确跟踪每个卡片（避免列表重排时动画状态混乱）
                            androidx.compose.runtime.key(album.id) {
                                val isDragging = draggingAlbumId == album.id
                                val coverImage = album.coverImageId?.let { imageMap[it] }
                                
                                // 计算让位位移
                                val (displacementX, displacementY) = calculateDisplacement(albumIndex)
                                
                                // 让位动画
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
                                
                                // 记录当前卡片在屏幕上的位置
                                var cardScreenY by remember { mutableFloatStateOf(0f) }
                                // 记录拖拽开始时触摸点的屏幕 Y 坐标
                                var touchStartScreenY by remember { mutableFloatStateOf(0f) }
                                // 记录用户手指的原始移动量（不含滚动补偿）
                                var rawDragOffsetY by remember { mutableFloatStateOf(0f) }
                                
                                Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            // 拖拽中：直接使用拖拽偏移
                                            translationX = dragOffsetX
                                            translationY = dragOffsetY
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                            shadowElevation = 16f
                                        } else {
                                            // 让位位移（非拖拽状态）
                                            translationX = animatedDisplacementX
                                            translationY = animatedDisplacementY
                                        }
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        if (itemWidth.floatValue == 0f) {
                                            itemWidth.floatValue = coordinates.size.width.toFloat()
                                            itemHeight.floatValue = coordinates.size.height.toFloat()
                                        }
                                        // 记录卡片在屏幕上的 Y 坐标
                                        cardScreenY = coordinates.positionInRoot().y
                                    }
                                    .pointerInput(album.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { startOffset ->
                                                HapticFeedback.heavyTap(context)
                                                draggingAlbumId = album.id
                                                draggingStartIndex = albumIndex
                                                currentTargetIndex = albumIndex
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                rawDragOffsetY = 0f
                                                // 记录拖拽开始时触摸点的屏幕 Y 坐标
                                                touchStartScreenY = cardScreenY + startOffset.y
                                                currentTouchScreenY = touchStartScreenY
                                                android.util.Log.d("DragScroll", "onDragStart: cardScreenY=$cardScreenY, startOffset.y=${startOffset.y}, touchStartScreenY=$touchStartScreenY")
                                                onDraggingChange(true)
                                            },
                                            onDragEnd = {
                                                // 计算最终目标位置
                                                val targetIdx = currentTargetIndex
                                                val startIdx = draggingStartIndex
                                                
                                                HapticFeedback.lightTap(context)
                                                
                                                // 停止自动滚动
                                                currentTouchScreenY = 0f
                                                
                                                // 立即重置所有拖拽状态（避免列表重排后的视觉跳动）
                                                draggingAlbumId = null
                                                draggingStartIndex = -1
                                                currentTargetIndex = -1
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                rawDragOffsetY = 0f
                                                onDraggingChange(false)
                                                
                                                // 如果位置变化了，重新排序并保存
                                                if (targetIdx != startIdx && targetIdx >= 0) {
                                                    val newList = albums.toMutableList()
                                                    val item = newList.removeAt(startIdx)
                                                    newList.add(targetIdx, item)
                                                    onReorder(newList.map { it.id })
                                                }
                                            },
                                            onDragCancel = {
                                                // 停止自动滚动
                                                currentTouchScreenY = 0f
                                                
                                                draggingAlbumId = null
                                                draggingStartIndex = -1
                                                currentTargetIndex = -1
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                rawDragOffsetY = 0f
                                                onDraggingChange(false)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x
                                                dragOffsetY += dragAmount.y
                                                // 记录用户手指的原始移动量（不含滚动补偿）
                                                rawDragOffsetY += dragAmount.y
                                                
                                                // 实时更新触摸点的屏幕 Y 坐标（用于自动滚动检测）
                                                // 使用原始移动量，不受滚动补偿影响
                                                currentTouchScreenY = touchStartScreenY + rawDragOffsetY
                                                
                                                // 实时更新目标位置（用于让位动画）
                                                val newTarget = getTargetIndex(draggingStartIndex, dragOffsetX, dragOffsetY)
                                                if (newTarget != currentTargetIndex) {
                                                    currentTargetIndex = newTarget
                                                    HapticFeedback.lightTap(context)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                AppAlbumGridCard(
                                    album = album,
                                    coverImage = coverImage,
                                    onClick = { if (!isDragging) onAlbumClick(album) },
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            }  // 关闭 key 块
                        } else {
                            // 填充空位
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
