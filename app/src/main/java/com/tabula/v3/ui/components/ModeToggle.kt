package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
// TODO
 * 
// TODO
// TODO
 */
@Composable
fun ModeToggle(
    isAlbumMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 容器背景色：浅色模式下使用纯白半透明，深色模式下使用深灰半透明
    // 避免使用与页面背景相同的灰色 (F2F2F7)，否则会"隐身"
    val containerTint = if (isDarkTheme) {
        Color(0xFF1C1C1E).copy(alpha = 0.68f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    val highlightBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),
            Color.White.copy(alpha = 0.08f)
        )
    )
    
    // 边框：垂直渐变，模拟顶部高光反射
    val borderBrush = if (isDarkTheme) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f), // TODO
                Color.White.copy(alpha = 0.05f) // TODO
            )
        )
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.8f),
                Color.White.copy(alpha = 0.2f)
            )
        )
    }

    // 滑块颜色
    val selectedBgColor = if (isDarkTheme) {
        Color(0xFF3A3A3C).copy(alpha = 0.85f)
    } else {
        Color(0xFFF2F2F7).copy(alpha = 0.9f) // TODO
    }
    
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val unselectedTextColor = if (isDarkTheme) Color.Gray else Color.Gray

    // 滑块动画
    val segmentWidth = 80.dp
    // 字体排版
    val fontSize = 15.sp

    val offsetX by animateDpAsState(
        targetValue = if (isAlbumMode) segmentWidth else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "toggle_offset"
    )

    AdaptiveGlass(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(100),
                spotColor = Color.Black.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(100),
        blurRadius = 34.dp,
        tint = containerTint,
        borderBrush = borderBrush,
        borderWidth = 1.dp,
        highlightBrush = highlightBrush,
        noiseAlpha = if (isDarkTheme) 0.04f else 0.06f,
        backdropConfig = BackdropLiquidGlassConfig.Bar.copy(cornerRadius = 100.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
        // 滑动选中背景
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(segmentWidth)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(100),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(100))
                .background(selectedBgColor)
                .padding(vertical = 8.dp)
        ) {
             Text(text = " ", fontSize = fontSize) // Placeholder
        }

        // 按钮
        Row {
            // 照片
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .clip(RoundedCornerShape(100))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isAlbumMode) {
                            HapticFeedback.lightTap(context)
                            onModeChange(false)
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "照片",
                    fontSize = fontSize,
                    fontWeight = if (!isAlbumMode) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (!isAlbumMode) textColor else unselectedTextColor
                )
            }

            // 图集
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .clip(RoundedCornerShape(100))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isAlbumMode) {
                            HapticFeedback.lightTap(context)
                            onModeChange(true)
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "图集",
                    fontSize = fontSize,
                    fontWeight = if (isAlbumMode) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isAlbumMode) textColor else unselectedTextColor
                )
            }
        }
        }
    }
}

