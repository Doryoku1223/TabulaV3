package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶部栏组件
 *
 * 显示：进度指示 (2/15) 或 时间 (2023 Jul) | 回收站图标 | 设置图标
 *
 * @param currentIndex 当前索引（0-based）
 * @param totalCount 总数量
 * @param currentImage 当前图片（用于获取时间）
 * @param displayMode 显示模式（索引或时间）
 * @param onTrashClick 回收站按钮点击
 * @param onSettingsClick 设置按钮点击
 * @param modifier 外部修饰符
 */
@Composable
fun TopBar(
    currentIndex: Int,
    totalCount: Int,
    currentImage: ImageFile? = null,
    displayMode: TopBarDisplayMode = TopBarDisplayMode.INDEX,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val buttonBgColor = if (isDarkTheme) TabulaColors.CatBlackLight else Color.White
    val buttonIconColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：索引或时间
        when (displayMode) {
            TopBarDisplayMode.INDEX -> {
                Text(
                    text = "${currentIndex + 1}/$totalCount",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = textColor
                )
            }
            TopBarDisplayMode.DATE -> {
                val dateText = currentImage?.dateModified?.let { timestamp ->
                    formatDateForTopBar(timestamp)
                } ?: "${currentIndex + 1}/$totalCount"

                Text(
                    text = dateText,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = textColor
                )
            }
        }

        // 右侧图标按钮组
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 回收站图标按钮
            ActionIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "回收站",
                onClick = onTrashClick,
                backgroundColor = buttonBgColor,
                iconColor = buttonIconColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 设置图标按钮
            ActionIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "设置",
                onClick = onSettingsClick,
                backgroundColor = buttonBgColor,
                iconColor = buttonIconColor
            )
        }
    }
}

/**
 * 格式化日期为 "2023 Jul" 格式
 * 注意：timestamp 已经是毫秒格式（在 LocalImageRepository 中已转换）
 */
private fun formatDateForTopBar(timestamp: Long): String {
    val date = Date(timestamp)  // 直接使用，已经是毫秒
    val format = SimpleDateFormat("yyyy MMM", Locale.ENGLISH)
    return format.format(date)
}

/**
 * 操作图标按钮组件
 */
@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = backgroundColor,
            contentColor = iconColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = iconColor
        )
    }
}

/**
 * 底部分页指示器
 *
 * 三个点的指示器：上一个 | 当前 | 下一个
 *
 * @param hasPrev 是否有上一张
 * @param hasNext 是否有下一张
 * @param modifier 外部修饰符
 */
@Composable
fun BottomIndicator(
    hasPrev: Boolean,
    hasNext: Boolean,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val activeColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val inactiveColor = if (isDarkTheme) Color(0xFF666666) else Color(0xFFBDBDBD)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一个点
        IndicatorDot(
            isActive = false,
            isVisible = hasPrev,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 当前点（深色）
        IndicatorDot(
            isActive = true,
            isVisible = true,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 下一个点
        IndicatorDot(
            isActive = false,
            isVisible = hasNext,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )
    }
}

/**
 * 指示器圆点
 */
@Composable
private fun IndicatorDot(
    isActive: Boolean,
    isVisible: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val color = when {
        !isVisible -> Color.Transparent
        isActive -> activeColor
        else -> inactiveColor
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
