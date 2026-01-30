package com.tabula.v3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tabula.v3.data.model.Album
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 图集创建/编辑对话框
 *
 * 支持：
 * - 输入图集名称
 * - 实时预览标签样式（使用内置 Glassmorphism 效果）
 *
 * 设计风格：iOS 风格的圆角卡片对话框
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumEditDialog(
    isEdit: Boolean = false,
    initialName: String = "",
    initialColor: Long? = null,
    initialTextColor: Long? = null,
    initialEmoji: String? = null,
    onConfirm: (name: String, color: Long?, emoji: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current

    // 状态
    var name by remember { mutableStateOf(initialName) }

    // 颜色
    val backgroundColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val inputBgColor = if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFF2F2F7)
    val accentColor = Color(0xFF007AFF) // iOS 蓝

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    // 液态玻璃模式下使用更不透明的背景确保可读性
    val effectiveBgColor = if (isLiquidGlassEnabled) {
        backgroundColor.copy(alpha = 0.98f)
    } else {
        backgroundColor
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(effectiveBgColor)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "取消",
                            tint = secondaryColor
                        )
                    }

                    Text(
                        text = if (isEdit) "编辑图集" else "新建图集",
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                HapticFeedback.mediumTap(context)
                                onConfirm(name.trim(), null, null)
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = name.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "确认",
                            tint = if (name.isNotBlank()) accentColor else secondaryColor.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 标签样式预览 - 使用 Glassmorphism 效果
                Text(
                    text = "标签预览",
                    color = secondaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                
                // 模拟卡片底部的标签效果 - Glassmorphism 风格
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (isDarkTheme) {
                                    listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
                                } else {
                                    listOf(Color(0xFFE5E5EA), Color(0xFFF2F2F7))
                                }
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 玻璃效果标签预览（自动适配液态玻璃/毛玻璃）
                    AdaptiveGlass(
                        shape = RoundedCornerShape(14.dp),
                        blurRadius = 24.dp,
                        tint = if (isDarkTheme) {
                            Color.Black.copy(alpha = 0.55f)
                        } else {
                            Color.White.copy(alpha = 0.65f)
                        },
                        borderBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                if (isDarkTheme) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f),
                                if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f)
                            )
                        ),
                        borderWidth = 0.5.dp,
                        highlightBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDarkTheme) 0.08f else 0.2f),
                                Color.White.copy(alpha = 0.02f)
                            )
                        ),
                        noiseAlpha = 0f,
                        backdropConfig = BackdropLiquidGlassConfig.Default.copy(cornerRadius = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.ifBlank { "图集名称" },
                            color = if (isDarkTheme) Color.White else Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 名称输入
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(inputBgColor)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(accentColor),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            if (name.isEmpty()) {
                                Text(
                                    text = "输入图集名称",
                                    color = secondaryColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 提示文字
                Text(
                    text = "标签将使用统一的玻璃拟态风格显示",
                    color = secondaryColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 颜色选项
 */
@Composable
private fun ColorOption(
    color: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "color_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, Color.White, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Emoji 选项
 */
@Composable
private fun EmojiOption(
    emoji: String,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "emoji_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF007AFF).copy(alpha = 0.15f)
            isDarkTheme -> Color(0xFF3A3A3C)
            else -> Color(0xFFF2F2F7)
        },
        label = "emoji_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF007AFF)
            else -> Color.Transparent
        },
        label = "emoji_border"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp
        )
    }
}

/**
 * 删除确认对话框
 */
@Composable
fun AlbumDeleteConfirmDialog(
    albumName: String,
    imageCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current

    val backgroundColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color.White
    // 液态玻璃模式下使用更不透明的背景
    val effectiveBgColor = if (isLiquidGlassEnabled) {
        backgroundColor.copy(alpha = 0.98f)
    } else {
        backgroundColor
    }
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val dangerColor = Color(0xFFFF3B30)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(effectiveBgColor)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "删除相册",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "确定要删除「$albumName」吗？",
                color = textColor,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            if (imageCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "相册中的 $imageCount 张照片不会被删除",
                    color = secondaryColor,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 取消按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFF2F2F7))
                        .clickable { onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 删除按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(dangerColor)
                        .clickable {
                            HapticFeedback.heavyTap(context)
                            onConfirm()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "删除",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
