package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 玻璃风格 BottomSheet 配置
 * 
 * 关键设计：
 * - 液态玻璃模式下：使用不透明背景 + 玻璃效果叠加，确保可读性
 * - 普通模式下：使用标准 Material 样式
 */
data class GlassBottomSheetConfig(
    val cornerRadius: Int = 24,
    // 液态玻璃模式下的背景（需要有足够的不透明度）
    val glassBackgroundAlpha: Float = 0.92f,
    // 文字颜色
    val useAdaptiveTextColor: Boolean = true
)

/**
 * 玻璃风格 BottomSheet
 * 
 * 自动适配液态玻璃主题，确保内容可读性
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    containerColor: Color,
    textColor: Color,
    isDarkTheme: Boolean = false,
    config: GlassBottomSheetConfig = GlassBottomSheetConfig(),
    content: @Composable () -> Unit
) {
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // 液态玻璃模式下使用更不透明的背景，确保可读性
    val effectiveContainerColor = if (isLiquidGlassEnabled) {
        if (isDarkTheme) {
            Color(0xFF1C1C1E).copy(alpha = config.glassBackgroundAlpha)
        } else {
            Color(0xFFF2F2F7).copy(alpha = config.glassBackgroundAlpha)
        }
    } else {
        containerColor
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isLiquidGlassEnabled) Color.Transparent else containerColor,
        shape = RoundedCornerShape(topStart = config.cornerRadius.dp, topEnd = config.cornerRadius.dp),
        dragHandle = {
            // 自定义拖拽手柄
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .background(
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.3f) 
                                else Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
    ) {
        if (isLiquidGlassEnabled) {
            // 液态玻璃模式：使用玻璃容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = config.cornerRadius.dp, topEnd = config.cornerRadius.dp))
            ) {
                // 不透明背景层（确保可读性）
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(effectiveContainerColor)
                )
                
                // 玻璃效果层
                PhysicalLiquidGlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    config = PhysicalLiquidGlassConfig.iOSCleanGlass.copy(
                        cornerRadius = config.cornerRadius.dp,
                        surfaceAlpha = 0.12f,
                        tintStrength = 0.08f,
                        topRimLightAlpha = 0.35f,
                        bottomInnerHighlightAlpha = 0f,
                        topInnerDarkAlpha = 0f
                    ),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                        content()
                    }
                }
            }
        } else {
            // 普通模式：标准样式
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                content()
            }
        }
    }
}

/**
 * 玻璃风格选项项
 */
@Composable
fun GlassOptionItem(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = if (subtitle != null) 12.dp else 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (subtitle != null) Alignment.Top else Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) accentColor else textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已选择",
                tint = accentColor,
                modifier = if (subtitle != null) Modifier.padding(top = 2.dp) else Modifier
            )
        }
    }
}

/**
 * 玻璃风格 Dialog 容器
 * 用于适配 AlertDialog 等对话框
 */
@Composable
fun GlassDialogContainer(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    
    if (isLiquidGlassEnabled) {
        val backgroundColor = if (isDarkTheme) {
            Color(0xFF2C2C2E)
        } else {
            Color(0xFFF2F2F7)
        }
        
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
        ) {
            // 不透明背景
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(backgroundColor.copy(alpha = 0.95f))
            )
            
            // 玻璃效果
            PhysicalLiquidGlassBox(
                modifier = Modifier.fillMaxWidth(),
                config = PhysicalLiquidGlassConfig.iOSCleanGlass.copy(
                    cornerRadius = 16.dp,
                    surfaceAlpha = 0.10f,
                    tintStrength = 0.06f
                ),
                contentAlignment = Alignment.TopStart
            ) {
                content()
            }
        }
    } else {
        content()
    }
}
