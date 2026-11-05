package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AlbumCleanupDisplayMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.ui.components.PhysicalLiquidGlassBox
import com.tabula.v3.ui.components.PhysicalLiquidGlassConfig
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶部栏组件
 *
 * 显示：进度指示 (2/15) 或 时间 (2023 Jul) | 图集选择 | 回收站图标 | 设置图标
 *
 * @param currentIndex 当前索引（0-based）
 * @param totalCount 总数量
 * @param currentImage 当前图片（用于获取时间）
 * @param displayMode 显示模式（索引或时间）
 * @param onTrashClick 回收站按钮点击
 * @param onSettingsClick 设置按钮点击
 * @param onTrashButtonBoundsChanged 回收站按钮位置变化回调（用于Genie动画目标点）
 * @param isAlbumCleanupMode 是否处于图集清理模式
 * @param albumCleanupName 图集清理模式下的图集名称
 * @param albumCleanupTotalGroups 图集清理模式下的总组数
 * @param albumCleanupRemainingGroups 图集清理模式下的剩余组数
 * @param albumCleanupTotalImages 图集清理模式下的总照片数
 * @param albumCleanupRemainingImages 图集清理模式下的剩余照片数
 * @param albumCleanupDisplayMode 图集清理显示模式（组/张）
 * @param onAlbumCleanupDisplayModeToggle 点击切换显示模式的回调
 * @param onAlbumCleanupClick 图集选择按钮点击回调
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
    onTrashButtonBoundsChanged: ((Rect) -> Unit)? = null,
    // 图集清理模式参数
    isAlbumCleanupMode: Boolean = false,
    albumCleanupName: String? = null,
    albumCleanupTotalGroups: Int = 0,
    albumCleanupRemainingGroups: Int = 0,
    albumCleanupTotalImages: Int = 0,
    albumCleanupRemainingImages: Int = 0,
    albumCleanupDisplayMode: AlbumCleanupDisplayMode = AlbumCleanupDisplayMode.GROUPS,
    onAlbumCleanupDisplayModeToggle: (() -> Unit)? = null,
    onAlbumCleanupClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onShareClick: (() -> Unit)? = null,
    shareEnabled: Boolean = false
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val buttonBgColor = if (isDarkTheme) TabulaColors.CatBlackLight else Color.White
    val buttonIconColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val accentColor = Color(0xFF007AFF)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：索引/时间 或 图集清理进度
        if (isAlbumCleanupMode && albumCleanupName != null) {
            // 图集清理模式：显示图集名称和进度（点击可切换显示模式）
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,  // 禁用点击反馈效果
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onAlbumCleanupDisplayModeToggle?.invoke()
                    }
                )
            ) {
                Text(
                    text = albumCleanupName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 根据显示模式显示组数或照片数
                // 照片模式下，剩余数需要减去当前批次已滑过的数量，实现实时更新
                val displayRemainingImages = (albumCleanupRemainingImages - currentIndex).coerceAtLeast(0)
                val statsText = when (albumCleanupDisplayMode) {
                    AlbumCleanupDisplayMode.GROUPS -> "共 $albumCleanupTotalGroups 组 · 剩余 $albumCleanupRemainingGroups 组"
                    AlbumCleanupDisplayMode.PHOTOS -> "共 $albumCleanupTotalImages 张 · 剩余 $displayRemainingImages 张"
                }
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        } else {
            // 普通模式：显示索引或时间
            when (displayMode) {
                TopBarDisplayMode.INDEX -> {
                    AdaptiveTopBarText(
                        text = "${currentIndex + 1}/$totalCount",
                        baseFontSize = 36f, color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                TopBarDisplayMode.DATE -> {
                    val dateText = currentImage?.dateModified?.let { timestamp ->
                        formatDateForTopBar(timestamp)
                    } ?: "${currentIndex + 1}/$totalCount"

                    AdaptiveTopBarText(
                        text = dateText, baseFontSize = 32f, color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 右侧图标按钮组
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onShareClick != null) {
                ActionIconButton(
                    icon = Icons.Outlined.Share,
                    contentDescription = "分享当前照片",
                    onClick = { HapticFeedback.lightTap(context); onShareClick() },
                    backgroundColor = buttonBgColor,
                    iconColor = buttonIconColor,
                    enabled = shareEnabled
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // 图集选择按钮（仅在有回调时显示）
            if (onAlbumCleanupClick != null) {
                ActionIconButton(
                    icon = Icons.Rounded.PhotoLibrary,
                    contentDescription = "选择图集",
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onAlbumCleanupClick()
                    },
                    backgroundColor = if (isAlbumCleanupMode) accentColor.copy(alpha = 0.15f) else buttonBgColor,
                    iconColor = if (isAlbumCleanupMode) accentColor else buttonIconColor,
                    forceNormalStyle = isAlbumCleanupMode  // 高亮时强制使用普通样式
                )
                
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            // 回收站图标按钮
            ActionIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "回收站",
                onClick = {
                    HapticFeedback.lightTap(context)
                    onTrashClick()
                },
                backgroundColor = buttonBgColor,
                iconColor = buttonIconColor,
                modifier = if (onTrashButtonBoundsChanged != null) {
                    Modifier.onGloballyPositioned { coordinates ->
                        onTrashButtonBoundsChanged(coordinates.boundsInRoot())
                    }
                } else {
                    Modifier
                }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 设置图标按钮
            ActionIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "设置",
                onClick = {
                    HapticFeedback.lightTap(context)
                    onSettingsClick()
                },
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
/** Keep all action targets reachable on narrow screens and with large font settings. */
@Composable
private fun AdaptiveTopBarText(text: String, baseFontSize: Float, color: Color, modifier: Modifier) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier) {
        var fontSize by remember(text, baseFontSize, maxWidth, fontScale) { mutableFloatStateOf(baseFontSize) }
        Text(
            text = text,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.didOverflowWidth && fontSize > 14f) fontSize -= 1f }
        )
    }
}

private fun formatDateForTopBar(timestamp: Long): String {
    val date = Date(timestamp)  // 直接使用，已经是毫秒
    val format = SimpleDateFormat("yyyy MMM", Locale.ENGLISH)
    return format.format(date)
}

/**
 * 操作图标按钮组件 - 统一的图标按钮样式
 * 
 * 用于顶部栏的操作按钮，带背景色和圆角。
 * 自动适配液态玻璃主题。
 * 
 * @param icon 图标
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 * @param backgroundColor 背景色
 * @param iconColor 图标颜色
 * @param modifier 外部修饰符
 */
@Composable
fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    forceNormalStyle: Boolean = false,  // 强制使用普通样式（用于高亮状态）
    enabled: Boolean = true
) {
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    
    // 当需要特殊高亮背景时，不使用液态玻璃模式
    if (isLiquidGlassEnabled && !forceNormalStyle) {
        // 液态玻璃模式：使用玻璃按钮
        PhysicalLiquidGlassBox(
            modifier = modifier
                .size(48.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick),
            config = PhysicalLiquidGlassConfig.Button.copy(
                cornerRadius = 12.dp,
                surfaceAlpha = 0.18f,
                tintStrength = 0.10f
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )
        }
    } else {
        // 普通模式：使用 Box 而不是 IconButton，避免圆形默认样式
        Box(
            modifier = modifier
                .size(48.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )
        }
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

        Spacer(modifier = Modifier.width(4.dp))

        // 当前点（深色）
        IndicatorDot(
            isActive = true,
            isVisible = true,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )

        Spacer(modifier = Modifier.width(4.dp))

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
