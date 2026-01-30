package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * - 新建按钮在第一个位置（索引0）
 *
 * @param albums 相册列表
 * @param selectedIndex 当前选中的标签索引（0 = 新建，1+ = 相册）
 * @param onTagPositionChanged 标签位置变化回调，返回索引和对应的屏幕坐标
 * @param modifier 外部修饰符
 */
@Composable
fun AlbumDropTarget(
    albums: List<Album>,
    selectedIndex: Int,
    onTagPositionChanged: ((Int, TagPosition) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 自动滚动到选中的标签
    // 使用 scrollToItem（瞬间滚动）而不是 animateScrollToItem（动画滚动）
    // 这样位置数据会立即更新，避免用户快速松手时目标位置不准确的问题
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.scrollToItem(
                index = selectedIndex,
                scrollOffset = -100 // 留一些边距
            )
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 新建按钮放在第一个位置（索引0）
            item(key = "create_new") {
                DropTargetChip(
                    text = "+ 新建图集",
                    isSelected = selectedIndex == 0,
                    isCreateNew = true,
                    onPositioned = { coordinates ->
                        onTagPositionChanged?.invoke(0, coordinates)
                    }
                )
            }
            
            // 相册标签（索引从1开始）
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
                DropTargetChip(
                    text = album.name,
                    isSelected = (index + 1) == selectedIndex,
                    backgroundColor = album.color,
                    textColor = album.textColor,
                    onPositioned = { coordinates ->
                        onTagPositionChanged?.invoke(index + 1, coordinates)
                    }
                )
            }
        }
    }
}

/**
 * 单个标签Chip - Glassmorphism 玻璃拟态风格
 *
 * 设计特点：
 * - 高斯模糊背景 (20-30dp)
 * - 半透明填充 (白色/黑色 40%-70%)
 * - 细微边框 (0.5-1dp, 白色 20%-30% 透明度)
 * - 选中时有放大效果
 *
 * @param text 标签文字
 * @param isSelected 是否选中
 * @param isCreateNew 是否是新建按钮
 * @param onPositioned 位置回调，返回标签在屏幕上的坐标
 */
@Composable
private fun DropTargetChip(
    text: String,
    isSelected: Boolean,
    isCreateNew: Boolean = false,
    backgroundColor: Long? = null,  // 保留参数但不使用，保持接口兼容
    textColor: Long? = null,        // 保留参数但不使用，保持接口兼容
    onPositioned: ((TagPosition) -> Unit)? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 选中时放大动画
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_scale"
    )
    
    // Glassmorphism 颜色配置
    // 填充颜色：选中时更不透明
    val fillColor = if (isDarkTheme) {
        if (isSelected) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.45f)
    } else {
        if (isSelected) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.55f)
    }
    
    // 边框颜色：细微的白色/黑色边框
    val borderColor = if (isDarkTheme) {
        if (isSelected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.2f)
    } else {
        if (isSelected) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f)
    }
    
    // 文字颜色
    val chipTextColor = if (isDarkTheme) Color.White else Color.Black
    
    // 圆角
    val cornerRadius = 14.dp
    
    // 使用 AdaptiveGlass 组件实现玻璃效果（自动适配液态玻璃/毛玻璃）
    AdaptiveGlass(
        modifier = Modifier
            .scale(scale)
            .onGloballyPositioned { coordinates ->
                onPositioned?.invoke(
                    TagPosition(
                        coordinates = coordinates,
                        updatedAt = android.os.SystemClock.uptimeMillis()
                    )
                )
            },
        shape = RoundedCornerShape(cornerRadius),
        blurRadius = 24.dp,
        tint = fillColor,
        borderBrush = Brush.verticalGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = borderColor.alpha * 0.5f)
            )
        ),
        borderWidth = if (isSelected) 1.dp else 0.5.dp,
        highlightBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDarkTheme) 0.1f else 0.3f),
                Color.White.copy(alpha = if (isDarkTheme) 0.02f else 0.05f)
            )
        ),
        noiseAlpha = 0f,  // 不使用噪点
        backdropConfig = BackdropLiquidGlassConfig.Default.copy(cornerRadius = cornerRadius),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = chipTextColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
