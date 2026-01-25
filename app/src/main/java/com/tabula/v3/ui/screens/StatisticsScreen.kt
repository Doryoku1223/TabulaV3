package com.tabula.v3.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors

/**
 * 综合统计页面
 *
 * 展示清理进度、累计数据等
 */
@Composable
fun StatisticsScreen(
    reviewedCount: Int = 1250, // 模拟累计数据
    totalImages: Int = 8636,   // 模拟总数
    deletedCount: Int = 450,   // 模拟累计删除
    markedCount: Int = 13,     // 当前标记
    onBack: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 配色
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val cardColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val accentColor = TabulaColors.EyeGold

    // 动画状态
    var animationPlayed by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val progress = if (totalImages > 0) reviewedCount.toFloat() / totalImages else 0f
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) progress else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ========== 顶部栏 ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor
                )
            }
            
            Text(
                text = "综合统计",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ========== 顶部图表卡片 ==========
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 圆环图表
                    Box(contentAlignment = Alignment.Center) {
                        CircularChart(
                            progress = animatedProgress,
                            size = 160.dp,
                            color = accentColor,
                            trackColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = textColor
                            )
                            Text(
                                text = "整理进度",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 底部数据 (已查看 / 总数)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn(
                            value = "$reviewedCount",
                            label = "已整理",
                            textColor = textColor,
                            subLabel = "/ $totalImages 张"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // ========== 详细数据列表 ==========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .padding(vertical = 8.dp)
            ) {
                StatRow(
                    icon = Icons.Outlined.Delete,
                    iconColor = Color(0xFFFF453A),
                    label = "累计清除",
                    value = "$deletedCount 张",
                    textColor = textColor
                )
                
                StatDivider(isDarkTheme)
                
                StatRow(
                    icon = Icons.Outlined.Check,
                    iconColor = Color(0xFF30D158),
                    label = "当前标记",
                    value = "$markedCount 张",
                    textColor = textColor
                )
                
                StatDivider(isDarkTheme)
                
                StatRow(
                    icon = Icons.Outlined.Image,
                    iconColor = Color(0xFF0A84FF),
                    label = "剩余整理",
                    value = "${totalImages - reviewedCount} 张",
                    textColor = textColor
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 底部标语
            Text(
                text = "Keep it clean, Keep it Tabula.",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryTextColor.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CircularChart(
    progress: Float,
    size: Dp,
    color: Color,
    trackColor: Color
) {
    val strokeWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 20.dp.toPx() }
    
    Canvas(modifier = Modifier.size(size)) {
        // Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Progress
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360 * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    textColor: Color,
    subLabel: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = textColor
            )
            if (subLabel != null) {
                Text(
                    text = " $subLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun StatRow(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = textColor
        )
    }
}

@Composable
private fun StatDivider(isDarkTheme: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 76.dp, end = 24.dp)
            .height(0.5.dp)
            .background(if (isDarkTheme) Color(0xFF38383A) else Color(0xFFE5E5EA))
    )
}
