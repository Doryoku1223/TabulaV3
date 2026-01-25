package com.tabula.v3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tabula.v3.R
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.ThemeMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 设置屏幕 - 极简主义设计风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    imageCount: Int,
    trashCount: Int,
    onThemeChange: (ThemeMode) -> Unit,
    onBatchSizeChange: (Int) -> Unit,
    onTopBarModeChange: (TopBarDisplayMode) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToStatistics: () -> Unit = {} // 新增回调，暂给默认值避免报错
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 主题色配置 - 极简灰白调
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7) // iOS 风格背景灰
    val cardColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    
    // 品牌强调色
    val accentColor = TabulaColors.EyeGold
    
    // 当前设置状态
    var currentTheme by remember { mutableStateOf(preferences.themeMode) }
    var showDeleteConfirm by remember { mutableStateOf(preferences.showDeleteConfirm) }
    var currentBatchSize by remember { mutableIntStateOf(preferences.batchSize) }
    var currentTopBarMode by remember { mutableStateOf(preferences.topBarDisplayMode) }

    // 底栏状态
    var showThemeSheet by remember { mutableStateOf(false) }
    var showBatchSizeSheet by remember { mutableStateOf(false) }
    var showTopBarModeSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ========== 顶部大标题栏 ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 返回按钮
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // 标题
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ========== 内容滚动区 ==========
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ========== 外观 ==========
            SectionHeader("外观", textColor)
            
            SettingsGroup(cardColor) {
                SettingsItem(
                    icon = Icons.Outlined.DarkMode,
                    iconTint = Color(0xFF5E5CE6), // Indigo
                    title = "主题模式",
                    value = when (currentTheme) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        showThemeSheet = true
                    }
                )
                
                Divider(isDarkTheme)
                
                SettingsItem(
                    icon = Icons.Outlined.TextFormat,
                    iconTint = Color(0xFFFF9F0A), // Orange
                    title = "顶部显示",
                    value = when (currentTopBarMode) {
                        TopBarDisplayMode.INDEX -> "索引"
                        TopBarDisplayMode.DATE -> "日期"
                    },
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        showTopBarModeSheet = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ========== 行为 ==========
            SectionHeader("浏览体验", textColor)
            
            SettingsGroup(cardColor) {
                SettingsItem(
                    icon = Icons.Outlined.Numbers,
                    iconTint = Color(0xFF30D158), // Green
                    title = "每组数量",
                    value = "$currentBatchSize 张",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        showBatchSizeSheet = true
                    }
                )
                
                Divider(isDarkTheme)
                
                SettingsSwitchItem(
                    icon = Icons.Outlined.Delete,
                    iconTint = Color(0xFFFF453A), // Red
                    title = "删除前确认",
                    textColor = textColor,
                    checked = showDeleteConfirm,
                    onCheckedChange = {
                        HapticFeedback.lightTap(context)
                        showDeleteConfirm = it
                        preferences.showDeleteConfirm = it
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ========== 存储 & 统计 (带小猫彩蛋) ==========
            SectionHeader("数据统计", textColor)
            
            Box(Modifier.fillMaxWidth()) {
                // 小猫彩蛋 (趴在卡片边缘)
                Image(
                    painter = painterResource(id = R.drawable.cutecat),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 20.dp) // 稍微往右一点
                        .offset(y = (-38).dp) // 再往上一点
                        .size(68.dp) // 稍微大一点
                        .zIndex(1f)
                )

                SettingsGroup(cardColor) {
                    // 新增：综合统计
                    SettingsItem(
                        icon = Icons.Outlined.Analytics,
                        iconTint = Color(0xFFBF5AF2), // Purple
                        title = "综合统计",
                        value = "",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        onClick = {
                            HapticFeedback.lightTap(context)
                            onNavigateToStatistics()
                        }
                    )
                    
                    Divider(isDarkTheme)

                    SettingsItem(
                        icon = Icons.Outlined.Image,
                        iconTint = Color(0xFF0A84FF), // Blue
                        title = "照片库",
                        value = "$imageCount 张",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        showArrow = false,
                        onClick = { }
                    )
                    
                    Divider(isDarkTheme)
                    
                    SettingsItem(
                        icon = Icons.Outlined.Delete,
                        iconTint = Color(0xFFFF9F0A), // Orange
                        title = "回收站",
                        value = "$trashCount 项",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        showArrow = false,
                        onClick = { }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ========== 关于 ==========
            SectionHeader("关于", textColor)
            
            SettingsGroup(cardColor) {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    iconTint = accentColor,
                    title = "关于 Tabula",
                    value = "v3.0.1",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onNavigateToAbout()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))

            // ========== 隐私声明 ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDarkTheme) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = secondaryTextColor.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "绝不上传任何照片数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // ========== 各种底栏 (Modals) ==========
    
    // 主题选择
    if (showThemeSheet) {
        CustomBottomSheet(
            title = "选择主题",
            onDismiss = { showThemeSheet = false },
            containerColor = cardColor,
            textColor = textColor
        ) {
            OptionItem("跟随系统", currentTheme == ThemeMode.SYSTEM, accentColor, textColor) {
                val mode = ThemeMode.SYSTEM
                currentTheme = mode
                preferences.themeMode = mode
                onThemeChange(mode)
                showThemeSheet = false
            }
            OptionItem("浅色模式", currentTheme == ThemeMode.LIGHT, accentColor, textColor) {
                val mode = ThemeMode.LIGHT
                currentTheme = mode
                preferences.themeMode = mode
                onThemeChange(mode)
                showThemeSheet = false
            }
            OptionItem("深色模式", currentTheme == ThemeMode.DARK, accentColor, textColor) {
                val mode = ThemeMode.DARK
                currentTheme = mode
                preferences.themeMode = mode
                onThemeChange(mode)
                showThemeSheet = false
            }
        }
    }

    // 每组数量选择
    if (showBatchSizeSheet) {
        CustomBottomSheet(
            title = "每组显示数量",
            onDismiss = { showBatchSizeSheet = false },
            containerColor = cardColor,
            textColor = textColor
        ) {
            listOf(5, 10, 15, 20, 30).forEach { size ->
                OptionItem("$size 张", currentBatchSize == size, accentColor, textColor) {
                    currentBatchSize = size
                    preferences.batchSize = size
                    onBatchSizeChange(size)
                    showBatchSizeSheet = false
                }
            }
        }
    }

    // 顶部显示模式选择
    if (showTopBarModeSheet) {
        CustomBottomSheet(
            title = "顶部显示模式",
            onDismiss = { showTopBarModeSheet = false },
            containerColor = cardColor,
            textColor = textColor
        ) {
            OptionItem("索引 (1/15)", currentTopBarMode == TopBarDisplayMode.INDEX, accentColor, textColor) {
                val mode = TopBarDisplayMode.INDEX
                currentTopBarMode = mode
                preferences.topBarDisplayMode = mode
                onTopBarModeChange(mode)
                showTopBarModeSheet = false
            }
            OptionItem("日期 (Jan 2026)", currentTopBarMode == TopBarDisplayMode.DATE, accentColor, textColor) {
                val mode = TopBarDisplayMode.DATE
                currentTopBarMode = mode
                preferences.topBarDisplayMode = mode
                onTopBarModeChange(mode)
                showTopBarModeSheet = false
            }
        }
    }
}

// ========== 辅助组件 ==========

@Composable
fun SectionHeader(title: String, textColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        ),
        color = textColor.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsGroup(
    backgroundColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
    ) {
        content()
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    textColor: Color,
    secondaryTextColor: Color,
    showArrow: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标背景
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor
        )
        
        if (showArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = secondaryTextColor.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    textColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TabulaColors.EyeGold,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE9E9EB)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun Divider(isDarkTheme: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 64.dp) // 图标宽度 + 间距
            .height(0.5.dp)
            .background(if (isDarkTheme) Color(0xFF38383A) else Color(0xFFE5E5EA))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    containerColor: Color,
    textColor: Color,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
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

@Composable
fun OptionItem(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
