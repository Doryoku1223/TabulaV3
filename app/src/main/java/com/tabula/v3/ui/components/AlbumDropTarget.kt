package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.Album
import com.tabula.v3.ui.theme.LocalIsDarkTheme

/**
 * 标签选择器组件 - 用于下滑归类功能
 *
 * 功能：
 * - 显示可选的相册标签
 * - 根据selectedIndex高亮选中的标签
 * - 自动滚动到选中的标签
 * - 精确回调每个标签的屏幕位置
 *
 * @param albums 相册列表
 * @param selectedIndex 当前选中的标签索引
 * @param onTagPositionChanged 标签位置变化回调，返回索引和对应的屏幕坐标
 * @param modifier 外部修饰符
 */
@Composable
fun AlbumDropTarget(
    albums: List<Album>,
    selectedIndex: Int,
    onTagPositionChanged: ((Int, Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 自动滚动到选中的标签
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = -100 // 留一些边距
            )
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
                DropTargetChip(
                    text = album.name,
                    isSelected = index == selectedIndex,
                    onPositioned = { bounds ->
                        onTagPositionChanged?.invoke(index, bounds)
                    }
                )
            }
            
            // 新建按钮
            item(key = "create_new") {
                DropTargetChip(
                    text = "+ 新建",
                    isSelected = selectedIndex == albums.size,
                    onPositioned = { bounds ->
                        onTagPositionChanged?.invoke(albums.size, bounds)
                    }
                )
            }
        }
    }
}

/**
 * 单个标签Chip - 简洁文字样式
 *
 * @param text 标签文字
 * @param isSelected 是否选中
 * @param onPositioned 位置回调，返回标签在屏幕上的坐标
 */
@Composable
private fun DropTargetChip(
    text: String,
    isSelected: Boolean,
    onPositioned: ((Rect) -> Unit)? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 选中时放大动画
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_scale"
    )
    
    // 选中时背景变色
    val backgroundColor = if (isSelected) {
        if (isDarkTheme) Color(0xFF48484A) else Color(0xFFD1D1D6)
    } else {
        if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    }
    
    val textColor = if (isDarkTheme) Color.White else Color.Black
    
    // 简洁的文字标签样式
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .onGloballyPositioned { coordinates ->
                onPositioned?.invoke(coordinates.boundsInRoot())
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
