package com.tabula.v3.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Cloud
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tabula.v3.R
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.RecommendMode
import com.tabula.v3.data.preferences.ThemeMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import kotlin.math.roundToInt

/**
 * 设置屏幕 - 极简主义设计风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    imageCount: Int,
    trashCount: Int,
    albumCount: Int = 0,
    onThemeChange: (ThemeMode) -> Unit,
    onBatchSizeChange: (Int) -> Unit,
    onTopBarModeChange: (TopBarDisplayMode) -> Unit,
    showHdrBadges: Boolean,
    showMotionBadges: Boolean,
    playMotionSound: Boolean,
    motionSoundVolume: Int,
    hapticEnabled: Boolean,
    hapticStrength: Int,
    swipeHapticsEnabled: Boolean,
    onShowHdrBadgesChange: (Boolean) -> Unit,
    onShowMotionBadgesChange: (Boolean) -> Unit,
    onPlayMotionSoundChange: (Boolean) -> Unit,
    onMotionSoundVolumeChange: (Int) -> Unit,
    onHapticEnabledChange: (Boolean) -> Unit,
    onHapticStrengthChange: (Int) -> Unit,
    onSwipeHapticsEnabledChange: (Boolean) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToAlbums: () -> Unit = {},
    onRecommendModeChange: () -> Unit = {},  // 推荐模式变更后通知主页刷新
    fluidCloudEnabled: Boolean = false,
    onFluidCloudEnabledChange: (Boolean) -> Unit = {}
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
    var currentShowHdrBadges by remember { mutableStateOf(showHdrBadges) }
    var currentShowMotionBadges by remember { mutableStateOf(showMotionBadges) }
    var currentPlayMotionSound by remember { mutableStateOf(playMotionSound) }
    var currentMotionSoundVolume by remember { mutableIntStateOf(motionSoundVolume) }
    var currentHapticEnabled by remember { mutableStateOf(hapticEnabled) }
    var currentHapticStrength by remember { mutableIntStateOf(hapticStrength) }
    var currentSwipeHapticsEnabled by remember { mutableStateOf(swipeHapticsEnabled) }
    var currentRecommendMode by remember { mutableStateOf(preferences.recommendMode) }
    var currentFluidCloudEnabled by remember { mutableStateOf(fluidCloudEnabled) }

    // 底栏状态
    var showThemeSheet by remember { mutableStateOf(false) }
    var showBatchSizeSheet by remember { mutableStateOf(false) }
    var showTopBarModeSheet by remember { mutableStateOf(false) }
    var showVibrationSoundPage by remember { mutableStateOf(false) }
    var showRecommendModeSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = showVibrationSoundPage) {
        HapticFeedback.lightTap(context)
        showVibrationSoundPage = false
    }

    if (showVibrationSoundPage) {
        VibrationSoundScreen(
            backgroundColor = backgroundColor,
            cardColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            accentColor = accentColor,
            showMotionBadges = currentShowMotionBadges,
            playMotionSound = currentPlayMotionSound,
            motionSoundVolume = currentMotionSoundVolume,
            hapticEnabled = currentHapticEnabled,
            hapticStrength = currentHapticStrength,
            swipeHapticsEnabled = currentSwipeHapticsEnabled,
            onPlayMotionSoundChange = {
                HapticFeedback.lightTap(context)
                currentPlayMotionSound = it
                onPlayMotionSoundChange(it)
            },
            onMotionSoundVolumeChange = {
                currentMotionSoundVolume = it
                onMotionSoundVolumeChange(it)
            },
            onHapticEnabledChange = {
                HapticFeedback.lightTap(context)
                currentHapticEnabled = it
                HapticFeedback.updateSettings(
                    enabled = it,
                    strength = currentHapticStrength
                )
                onHapticEnabledChange(it)
            },
            onHapticStrengthChange = {
                currentHapticStrength = it
                HapticFeedback.updateSettings(
                    enabled = currentHapticEnabled,
                    strength = it
                )
                onHapticStrengthChange(it)
            },
            onSwipeHapticsEnabledChange = {
                HapticFeedback.lightTap(context)
                currentSwipeHapticsEnabled = it
                onSwipeHapticsEnabledChange(it)
            },
            onNavigateBack = { showVibrationSoundPage = false }
        )
    } else {
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
                
                SettingsItem(
                    icon = Icons.Outlined.Shuffle,
                    iconTint = Color(0xFFBF5AF2), // Purple
                    title = "推荐模式",
                    value = when (currentRecommendMode) {
                        RecommendMode.RANDOM_WALK -> "随机漫步"
                        RecommendMode.SIMILAR -> "相似推荐"
                    },
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        showRecommendModeSheet = true
                    }
                )
                
                // 冷却期设置已移除，改为自动随机分配
                
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
                Divider(isDarkTheme)

                SettingsSwitchItem(
                    icon = Icons.Outlined.Image,
                    iconTint = Color(0xFF0A84FF), // Blue
                    title = "显示 HDR 标识",
                    textColor = textColor,
                    checked = currentShowHdrBadges,
                    onCheckedChange = {
                        HapticFeedback.lightTap(context)
                        currentShowHdrBadges = it
                        onShowHdrBadgesChange(it)
                    }
                )

                Divider(isDarkTheme)

                SettingsSwitchItem(
                    icon = Icons.Outlined.Movie,
                    iconTint = Color(0xFF30D158), // Green
                    title = "显示 Live 照片",
                    textColor = textColor,
                    checked = currentShowMotionBadges,
                    onCheckedChange = {
                        HapticFeedback.lightTap(context)
                        currentShowMotionBadges = it
                        onShowMotionBadgesChange(it)
                    }
                )

                Divider(isDarkTheme)

                SettingsItem(
                    icon = Icons.Outlined.VolumeUp,
                    iconTint = Color(0xFF64D2FF), // Light Blue
                    title = "振动与声音",
                    value = "",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        showVibrationSoundPage = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            
            // ========== 进阶功能 ==========
            SectionHeader("进阶功能", textColor)
            
            SettingsGroup(cardColor) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Cloud,
                    iconTint = Color(0xFF5E5CE6), // Indigo
                    title = "流体云",
                    textColor = textColor,
                    checked = currentFluidCloudEnabled,
                    onCheckedChange = { enabled ->
                        HapticFeedback.lightTap(context)
                        currentFluidCloudEnabled = enabled
                        onFluidCloudEnabledChange(enabled)
                    }
                )
                
                // 流体云说明
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "退出应用时在流体云/状态栏显示剩余照片数量，支持 ColorOS 流体云",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor.copy(alpha = 0.7f)
                    )
                }
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
                    value = "v3.1.0",
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

    }

    if (!showVibrationSoundPage) {
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

        // 推荐模式选择
        if (showRecommendModeSheet) {
            CustomBottomSheet(
                title = "推荐模式",
                onDismiss = { showRecommendModeSheet = false },
                containerColor = cardColor,
                textColor = textColor
            ) {
                OptionItem(
                    title = "随机漫步",
                    subtitle = "真正随机抽取，已看过的照片短期内不会再次出现",
                    isSelected = currentRecommendMode == RecommendMode.RANDOM_WALK,
                    accentColor = accentColor,
                    textColor = textColor
                ) {
                    currentRecommendMode = RecommendMode.RANDOM_WALK
                    preferences.recommendMode = RecommendMode.RANDOM_WALK
                    onRecommendModeChange()  // 通知主页刷新
                    showRecommendModeSheet = false
                }
                OptionItem(
                    title = "相似推荐",
                    subtitle = "优先推荐相似照片，帮助清理连拍和重复照片",
                    isSelected = currentRecommendMode == RecommendMode.SIMILAR,
                    accentColor = accentColor,
                    textColor = textColor
                ) {
                    currentRecommendMode = RecommendMode.SIMILAR
                    preferences.recommendMode = RecommendMode.SIMILAR
                    onRecommendModeChange()  // 通知主页刷新
                    showRecommendModeSheet = false
                }
            }
        }
    }
}

@Composable
private fun VibrationSoundScreen(
    backgroundColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    showMotionBadges: Boolean,
    playMotionSound: Boolean,
    motionSoundVolume: Int,
    hapticEnabled: Boolean,
    hapticStrength: Int,
    swipeHapticsEnabled: Boolean,
    onPlayMotionSoundChange: (Boolean) -> Unit,
    onMotionSoundVolumeChange: (Int) -> Unit,
    onHapticEnabledChange: (Boolean) -> Unit,
    onHapticStrengthChange: (Int) -> Unit,
    onSwipeHapticsEnabledChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val context = LocalContext.current
        // 顶部栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onNavigateBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "振动与声音",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader("声音", textColor)
            SettingsGroup(cardColor) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Movie,
                    iconTint = Color(0xFF30D158),
                    title = "Live 声音",
                    textColor = textColor,
                    checked = playMotionSound,
                    onCheckedChange = onPlayMotionSoundChange,
                    enabled = showMotionBadges
                )

                Divider(LocalIsDarkTheme.current)

                SettingsSliderItem(
                    icon = Icons.Outlined.VolumeUp,
                    iconTint = Color(0xFF64D2FF),
                    title = "Live 音量",
                    value = motionSoundVolume,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    enabled = showMotionBadges && playMotionSound,
                    accentColor = accentColor,
                    onValueChange = onMotionSoundVolumeChange
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            SectionHeader("振动", textColor)
            SettingsGroup(cardColor) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Vibration,
                    iconTint = Color(0xFFFF9F0A),
                    title = "启用振动",
                    textColor = textColor,
                    checked = hapticEnabled,
                    onCheckedChange = onHapticEnabledChange
                )

                Divider(LocalIsDarkTheme.current)

                SettingsSliderItem(
                    icon = Icons.Outlined.Vibration,
                    iconTint = Color(0xFFFF9F0A),
                    title = "振动强度",
                    value = hapticStrength,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    enabled = hapticEnabled,
                    accentColor = accentColor,
                    onValueChange = onHapticStrengthChange,
                    previewHaptics = true,
                    previewStep = 1,
                    onPreviewHaptic = { HapticFeedback.lightTap(context) }
                )

                Divider(LocalIsDarkTheme.current)

                SettingsSwitchItem(
                    icon = Icons.Outlined.Vibration,
                    iconTint = Color(0xFFFF9F0A),
                    title = "滑动卡片振动",
                    textColor = textColor,
                    checked = swipeHapticsEnabled,
                    onCheckedChange = onSwipeHapticsEnabledChange,
                    enabled = hapticEnabled
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
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
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
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
            color = textColor.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TabulaColors.EyeGold,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE9E9EB)
                ),
                modifier = Modifier
                    .height(20.dp)
                    .scale(0.85f)
            )
        }
    }
}

@Composable
fun SettingsSliderItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: Int,
    textColor: Color,
    secondaryTextColor: Color,
    enabled: Boolean,
    accentColor: Color,
    onValueChange: (Int) -> Unit,
    previewHaptics: Boolean = false,
    previewStep: Int = 5,
    onPreviewHaptic: (() -> Unit)? = null
) {
    var lastHapticValue by remember { mutableIntStateOf(value.coerceIn(0, 100)) }
    val isDarkTheme = LocalIsDarkTheme.current
    val inactiveTrackColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE0E0E0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
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
                color = textColor.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.weight(1f)
            )

            Text(
                text = value.coerceIn(0, 100).toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }

        StyledSlider(
            value = value,
            enabled = enabled,
            activeColor = accentColor,
            inactiveColor = inactiveTrackColor,
            onValueChange = { newValue ->
                val clampedValue = newValue.coerceIn(0, 100)
                onValueChange(clampedValue)
                if (previewHaptics && enabled && onPreviewHaptic != null) {
                    if (kotlin.math.abs(clampedValue - lastHapticValue) >= previewStep) {
                        lastHapticValue = clampedValue
                        onPreviewHaptic()
                    }
                }
            }
        )
    }
}

@Composable
private fun StyledSlider(
    value: Int,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color = Color.White,
    onValueChange: (Int) -> Unit,
    trackHeight: androidx.compose.ui.unit.Dp = 10.dp,
    thumbSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    val density = LocalDensity.current
    val clampedValue = value.coerceIn(0, 100)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(trackHeight, thumbSize))
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { thumbSize.toPx() }
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val trackStartX = thumbPx / 2f
        val trackEndX = (widthPx - thumbPx / 2f).coerceAtLeast(trackStartX)
        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)
        val fraction = (clampedValue / 100f).coerceIn(0f, 1f)
        val activeEndX = trackStartX + trackWidth * fraction
        val centerY = trackHeightPx / 2f

        val effectiveActive = if (enabled) activeColor else activeColor.copy(alpha = 0.4f)
        val effectiveInactive = if (enabled) inactiveColor else inactiveColor.copy(alpha = 0.4f)

        fun updateFromX(x: Float) {
            if (!enabled) return
            val clampedX = x.coerceIn(trackStartX, trackEndX)
            val newFraction = if (trackWidth <= 0f) 0f else (clampedX - trackStartX) / trackWidth
            val newValue = (newFraction * 100f).roundToInt().coerceIn(0, 100)
            onValueChange(newValue)
        }

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, trackStartX, trackEndX, trackWidth) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> updateFromX(offset.x) }
                }
                .pointerInput(enabled, trackStartX, trackEndX, trackWidth) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        updateFromX(change.position.x)
                        change.consume()
                    }
                }
        ) {
            val y = size.height / 2f
            val top = y - trackHeightPx / 2f
            val radius = trackHeightPx / 2f

            drawRoundRect(
                color = effectiveInactive,
                topLeft = Offset(trackStartX, top),
                size = Size(trackWidth, trackHeightPx),
                cornerRadius = CornerRadius(radius, radius)
            )

            if (activeEndX > trackStartX) {
                drawRoundRect(
                    color = effectiveActive,
                    topLeft = Offset(trackStartX, top),
                    size = Size(activeEndX - trackStartX, trackHeightPx),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }

            val thumbRadius = thumbPx / 2f
            val thumbCenter = Offset(activeEndX, y)
            val borderColor = effectiveActive.copy(alpha = if (enabled) 1f else 0.6f)

            drawCircle(
                color = thumbColor.copy(alpha = if (enabled) 1f else 0.5f),
                radius = thumbRadius,
                center = thumbCenter
            )
            drawCircle(
                color = borderColor,
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = with(density) { 2.dp.toPx() })
            )
        }
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

/**
 * 带副标题的选项项
 */
@Composable
fun OptionItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val secondaryTextColor = textColor.copy(alpha = 0.6f)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
        }
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
