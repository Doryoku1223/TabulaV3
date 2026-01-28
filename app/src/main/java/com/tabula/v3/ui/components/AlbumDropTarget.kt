package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.Album
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.launch

/**
 * 拖拽目标结果
 */
sealed class DropTargetResult {
    data class AlbumSelected(val album: Album, val targetBounds: Rect) : DropTargetResult()
    data class CreateNew(val targetBounds: Rect) : DropTargetResult()
    object None : DropTargetResult()
}

/**
 * 标签选择器组件 - 用于下滑归类功能
 *
 * 功能：
 * - 显示可选的相册标签
 * - 响应拖拽位置，高亮悬停的标签
 * - 支持标签变大、背景色变化、吸附效果
 * - 超出屏幕时自动滚动
 *
 * @param albums 相册列表
 * @param dragPosition 当前拖拽位置（屏幕坐标）
 * @param isDragging 是否正在拖拽
 * @param onHoveredAlbumChange 悬停相册变化回调
 * @param modifier 外部修饰符
 */
@Composable
fun AlbumDropTarget(
    albums: List<Album>,
    dragPosition: Offset?,
    isDragging: Boolean,
    onHoveredAlbumChange: (Album?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // 存储每个标签的位置
    var targetBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var createNewBounds by remember { mutableStateOf(Rect.Zero) }
    
    // 当前悬停的标签ID
    var hoveredId by remember { mutableStateOf<String?>(null) }
    
    // 背景渐变
    val gradientBrush = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.9f)
            )
        } else {
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.8f),
                Color.White.copy(alpha = 0.95f)
            )
        }
    )
    
    // 自动滚动逻辑
    LaunchedEffect(dragPosition, isDragging) {
        if (!isDragging || dragPosition == null) return@LaunchedEffect
        
        val scrollThreshold = with(density) { 60.dp.toPx() }
        val scrollAmount = 1  // 滚动的item数量
        
        // 检测是否接近边缘
        val screenEdgeLeft = scrollThreshold
        val screenEdgeRight = with(density) { 360.dp.toPx() } // 假设屏幕宽度
        
        when {
            dragPosition.x < screenEdgeLeft -> {
                // 向左滚动
                val targetIndex = (listState.firstVisibleItemIndex - scrollAmount).coerceAtLeast(0)
                scope.launch {
                    listState.animateScrollToItem(targetIndex)
                }
            }
            dragPosition.x > screenEdgeRight -> {
                // 向右滚动
                scope.launch {
                    listState.animateScrollToItem(listState.firstVisibleItemIndex + scrollAmount)
                }
            }
        }
    }
    
    // 检测悬停的标签
    LaunchedEffect(dragPosition, isDragging, targetBounds) {
        if (!isDragging || dragPosition == null) {
            hoveredId = null
            onHoveredAlbumChange(null)
            return@LaunchedEffect
        }
        
        // 检查是否在"新建"按钮上
        if (createNewBounds.contains(dragPosition)) {
            hoveredId = "create_new"
            onHoveredAlbumChange(null)
            return@LaunchedEffect
        }
        
        // 检查是否在某个相册标签上
        var found = false
        for ((id, bounds) in targetBounds) {
            // 扩大检测范围，增加吸附感
            val expandedBounds = bounds.inflate(with(density) { 10.dp.toPx() })
            if (expandedBounds.contains(dragPosition)) {
                hoveredId = id
                onHoveredAlbumChange(albums.find { it.id == id })
                found = true
                break
            }
        }
        
        if (!found) {
            hoveredId = null
            onHoveredAlbumChange(null)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(gradientBrush)
            .padding(top = 24.dp, bottom = 16.dp)
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(albums, key = { it.id }) { album ->
                DropTargetChip(
                    album = album,
                    isHovered = hoveredId == album.id,
                    onBoundsChange = { bounds ->
                        targetBounds = targetBounds + (album.id to bounds)
                    }
                )
            }
            
            // 新建按钮
            item(key = "create_new") {
                CreateNewChip(
                    isHovered = hoveredId == "create_new",
                    onBoundsChange = { bounds ->
                        createNewBounds = bounds
                    }
                )
            }
        }
    }
}

/**
 * 获取当前选中的目标
 */
fun getDropTarget(
    albums: List<Album>,
    targetBounds: Map<String, Rect>,
    createNewBounds: Rect,
    dragPosition: Offset?
): DropTargetResult {
    if (dragPosition == null) return DropTargetResult.None
    
    // 检查新建
    if (createNewBounds.contains(dragPosition)) {
        return DropTargetResult.CreateNew(createNewBounds)
    }
    
    // 检查相册
    for ((id, bounds) in targetBounds) {
        if (bounds.contains(dragPosition)) {
            val album = albums.find { it.id == id }
            if (album != null) {
                return DropTargetResult.AlbumSelected(album, bounds)
            }
        }
    }
    
    return DropTargetResult.None
}

/**
 * 单个标签Chip - 带悬停动画
 */
@Composable
private fun DropTargetChip(
    album: Album,
    isHovered: Boolean,
    onBoundsChange: (Rect) -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 悬停时放大动画
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_scale"
    )
    
    // 背景色动画
    val backgroundColor = if (isHovered) {
        if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFE8E8ED)
    } else {
        if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    }
    
    val borderColor = if (isHovered) {
        if (isDarkTheme) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }
    
    val textColor = if (isDarkTheme) Color.White else Color.Black
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onGloballyPositioned { coordinates ->
                onBoundsChange(coordinates.boundsInRoot())
            }
    ) {
        // 图标区域（Emoji或颜色块）
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .then(
                    if (isHovered) {
                        Modifier.background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    borderColor,
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (album.emoji != null) {
                Text(
                    text = album.emoji,
                    fontSize = 28.sp
                )
            } else {
                val albumColor = album.color?.let { Color(it) } ?: Color(0xFF7986CB)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(albumColor)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // 名称
        Text(
            text = album.name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isHovered) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp)
        )
    }
}

/**
 * 新建按钮Chip
 */
@Composable
private fun CreateNewChip(
    isHovered: Boolean,
    onBoundsChange: (Rect) -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "create_chip_scale"
    )
    
    val backgroundColor = if (isHovered) {
        if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFE8E8ED)
    } else {
        if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    }
    
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val iconTint = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF636366)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onGloballyPositioned { coordinates ->
                onBoundsChange(coordinates.boundsInRoot())
            }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "新建图集",
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = "新建",
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isHovered) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * 扩展Rect以增加吸附范围
 */
private fun Rect.inflate(amount: Float): Rect {
    return Rect(
        left = left - amount,
        top = top - amount,
        right = right + amount,
        bottom = bottom + amount
    )
}
