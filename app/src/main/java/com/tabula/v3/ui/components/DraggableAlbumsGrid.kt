package com.tabula.v3.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.ui.util.HapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 可拖拽排序的相册网格
 *
 * 支持长按拖拽排序，松手后保存新顺序
 */
@Composable
fun DraggableAlbumsGrid(
    albums: List<Album>,
    allImages: List<ImageFile>,
    onAlbumClick: (Album) -> Unit,
    onReorder: (List<String>) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    // 建立 ID 到 ImageFile 的映射
    val imageMap = remember(allImages) { allImages.associateBy { it.id } }
    
    // 拖拽状态
    var draggingAlbumId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    
    // 临时排序列表（拖拽过程中实时更新）
    var orderedAlbums by remember(albums) { mutableStateOf(albums) }
    
    // 网格布局参数
    val itemWidth = remember { mutableStateOf(0f) }
    val itemHeight = remember { mutableStateOf(0f) }
    
    // 计算拖拽目标位置
    fun getTargetIndex(currentIndex: Int, offsetX: Float, offsetY: Float): Int {
        if (itemWidth.value <= 0 || itemHeight.value <= 0) return currentIndex
        
        val columnsPerRow = 2
        val currentRow = currentIndex / columnsPerRow
        val currentCol = currentIndex % columnsPerRow
        
        // 计算水平和垂直方向的格子偏移
        val colOffset = (offsetX / itemWidth.value).roundToInt()
        val rowOffset = (offsetY / itemHeight.value).roundToInt()
        
        val newCol = (currentCol + colOffset).coerceIn(0, columnsPerRow - 1)
        val newRow = (currentRow + rowOffset).coerceIn(0, (orderedAlbums.size - 1) / columnsPerRow)
        
        val newIndex = newRow * columnsPerRow + newCol
        return newIndex.coerceIn(0, orderedAlbums.lastIndex)
    }
    
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
                text = "长按拖动排序 · 点击查看详情",
                color = secondaryTextColor,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(orderedAlbums, key = { it.id }) { album ->
            val isDragging = draggingAlbumId == album.id
            val coverImage = album.coverImageId?.let { imageMap[it] }
            
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 10f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffsetX
                            translationY = dragOffsetY
                            scaleX = 1.05f
                            scaleY = 1.05f
                            shadowElevation = 16f
                        }
                    }
                    .onGloballyPositioned { coordinates ->
                        if (itemWidth.value == 0f) {
                            itemWidth.value = coordinates.size.width.toFloat()
                            itemHeight.value = coordinates.size.height.toFloat()
                        }
                    }
                    .pointerInput(album.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                HapticFeedback.heavyTap(context)
                                draggingAlbumId = album.id
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                // 保存新顺序
                                val newOrder = orderedAlbums.map { it.id }
                                onReorder(newOrder)
                                
                                HapticFeedback.lightTap(context)
                                draggingAlbumId = null
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggingAlbumId = null
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                orderedAlbums = albums // 重置
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                dragOffsetY += dragAmount.y
                                
                                // 计算当前索引和目标索引
                                val currentIndex = orderedAlbums.indexOfFirst { it.id == album.id }
                                val targetIndex = getTargetIndex(currentIndex, dragOffsetX, dragOffsetY)
                                
                                // 实时重排
                                if (targetIndex != currentIndex && targetIndex in orderedAlbums.indices) {
                                    val newList = orderedAlbums.toMutableList()
                                    val item = newList.removeAt(currentIndex)
                                    newList.add(targetIndex, item)
                                    orderedAlbums = newList
                                    
                                    // 重置偏移（相对于新位置）
                                    HapticFeedback.lightTap(context)
                                    dragOffsetX -= (targetIndex % 2 - currentIndex % 2) * itemWidth.value
                                    dragOffsetY -= (targetIndex / 2 - currentIndex / 2) * itemHeight.value
                                }
                            }
                        )
                    }
            ) {
                AlbumGridItem(
                    album = album,
                    coverImage = coverImage,
                    onClick = { if (!isDragging) onAlbumClick(album) },
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    isDarkTheme = isDarkTheme,
                    isDragging = isDragging
                )
            }
        }
        
        // 底部留白
        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 单个相册网格项
 */
@Composable
private fun AlbumGridItem(
    album: Album,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    isDragging: Boolean = false
) {
    val context = LocalContext.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isDragging) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { },
                            onDragEnd = { },
                            onDragCancel = { },
                            onDrag = { _, _ -> }
                        )
                    }
                } else Modifier
            )
    ) {
        // 封面图容器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    color = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
                )
                .then(
                    if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(20.dp))
                    else Modifier
                )
                .pointerInput(Unit) {
                    // 单击检测（独立于拖拽）
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.firstOrNull()?.pressed == false) {
                                // 简单点击（没有拖拽）
                                val change = event.changes.firstOrNull()
                                if (change != null && !change.isConsumed) {
                                    HapticFeedback.lightTap(context)
                                    onClick()
                                }
                            }
                        }
                    }
                }
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
