package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.Album
import com.tabula.v3.ui.theme.LocalIsDarkTheme

/**
 * 每行显示的标签数量
 */
const val TAGS_PER_ROW = 7

/**
 * 标签选择器组件 - 用于下滑归类功能
 *
 * 功能：
 * - 显示可选的相册标签（多行网格布局）
 * - 根据 selectedIndex 高亮选中的标签
 * - 当选中的标签不在可见区域时自动滚动（垂直和水平）
 * - 精确回调每个标签的屏幕位置
 * - 新建按钮在第一个位置（索引0）
 * - 支持2D自由滑动选择标签
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
    val isDarkTheme = LocalIsDarkTheme.current
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current
    
    // 总标签数（包括新建按钮）
    val totalTags = albums.size + 1
    // 计算总行数
    val totalRows = (totalTags + TAGS_PER_ROW - 1) / TAGS_PER_ROW
    
    // 计算选中标签所在的行和列
    val selectedRow = selectedIndex / TAGS_PER_ROW
    val selectedCol = selectedIndex % TAGS_PER_ROW
    
    // 每行的水平滚动状态
    val rowScrollStates = remember { mutableStateMapOf<Int, androidx.compose.foundation.ScrollState>() }
    
    // 自动滚动到选中的标签所在行（垂直滚动）
    LaunchedEffect(selectedRow) {
        // 估算每行高度（标签高度 + 间距）
        val rowHeightPx = with(density) { 52.dp.toPx() }
        val targetScrollY = (selectedRow * rowHeightPx).toInt()
        verticalScrollState.animateScrollTo(targetScrollY)
    }
    
    // 自动滚动到选中的标签所在列（水平滚动）
    LaunchedEffect(selectedIndex) {
        val rowState = rowScrollStates[selectedRow]
        if (rowState != null) {
            // 估算每个标签宽度（标签宽度 + 间距）
            val chipWidthPx = with(density) { 70.dp.toPx() }  // 平均宽度估算
            val targetScrollX = (selectedCol * chipWidthPx).toInt()
            // 滚动到让选中的标签在中间位置
            val screenWidthPx = with(density) { 360.dp.toPx() }  // 估算屏幕宽度
            val centeredScrollX = (targetScrollX - screenWidthPx / 2 + chipWidthPx / 2).toInt().coerceAtLeast(0)
            rowState.animateScrollTo(centeredScrollX)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // 使用 Column + Row 实现多行网格布局，支持垂直滚动
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)  // 内部顶部padding，让放大的标签能完整显示
                .heightIn(max = 140.dp)  // 最多显示约3行
                .verticalScroll(verticalScrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            for (rowIndex in 0 until totalRows) {
                val rowStartIndex = rowIndex * TAGS_PER_ROW
                val rowEndIndex = minOf(rowStartIndex + TAGS_PER_ROW, totalTags)
                
                // 每行独立的水平滚动状态
                val rowScrollState = remember { androidx.compose.foundation.ScrollState(0) }
                // 记录到 map 中以便自动滚动使用
                LaunchedEffect(rowIndex) {
                    rowScrollStates[rowIndex] = rowScrollState
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rowScrollState)
                        .padding(horizontal = 12.dp)
                ) {
                    for (globalIndex in rowStartIndex until rowEndIndex) {
                        val isSelected = globalIndex == selectedIndex
                        
                        if (globalIndex == 0) {
                            // 新建按钮
                            DropTargetChip(
                                text = "+ 新建图集",
                                isSelected = isSelected,
                                isCreateNew = true,
                                onPositioned = { coordinates ->
                                    onTagPositionChanged?.invoke(globalIndex, coordinates)
                                }
                            )
                        } else {
                            // 相册标签
                            val albumIndex = globalIndex - 1
                            val album = albums.getOrNull(albumIndex)
                            if (album != null) {
                                DropTargetChip(
                                    text = album.name,
                                    isSelected = isSelected,
                                    backgroundColor = album.color,
                                    textColor = album.textColor,
                                    onPositioned = { coordinates ->
                                        onTagPositionChanged?.invoke(globalIndex, coordinates)
                                    }
                                )
                            }
                        }
                    }
                    // 末尾留白
                    Spacer(modifier = Modifier.width(12.dp))
                }
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
    // 填充颜色：选中时使用对比色（浅色模式用深色，深色模式用浅色）
    val fillColor = if (isDarkTheme) {
        if (isSelected) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.45f)
    } else {
        if (isSelected) Color.Black.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.55f)
    }
    
    // 边框颜色：选中时使用更明显的边框
    val borderColor = if (isDarkTheme) {
        if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
    } else {
        if (isSelected) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f)
    }
    
    // 文字颜色：选中时使用对比色
    val chipTextColor = if (isSelected) {
        if (isDarkTheme) Color.Black else Color.White
    } else {
        if (isDarkTheme) Color.White else Color.Black
    }
    
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
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
