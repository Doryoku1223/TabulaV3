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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.outlined.Favorite
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
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Widgets
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.tabula.v3.widget.TabulaWidgetProvider
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tabula.v3.BuildConfig
import com.tabula.v3.R
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.CardStyleMode
import com.tabula.v3.data.preferences.RecommendMode
import com.tabula.v3.data.preferences.ThemeMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.ui.components.GlassBottomSheet
import com.tabula.v3.ui.components.GlassDivider
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import androidx.compose.ui.graphics.Brush
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
    onFluidCloudEnabledChange: (Boolean) -> Unit = {},
    onNavigateToVibrationSound: () -> Unit = {},
    onNavigateToImageDisplay: () -> Unit = {},
    onNavigateToLab: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    scrollState: ScrollState? = null  // 外部传入的滚动状态，用于保持导航返回时的位置
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    
    // 主题色配置 - 根据主题类型调整
    // 液态玻璃主题使用纯白/纯黑背景，只有卡片有玻璃效果
    val backgroundColor = when {
        isDarkTheme -> Color.Black
        else -> Color(0xFFF2F2F7) // iOS 风格背景灰
    }
    val cardColor = when {
        isLiquidGlass -> TabulaColors.LiquidGlass.GlassSurface
        isDarkTheme -> Color(0xFF1C1C1E)
        else -> Color.White
    }
    val textColor = when {
        isLiquidGlass -> TabulaColors.LiquidGlass.TextPrimary
        isDarkTheme -> Color.White
        else -> Color.Black
    }
    val secondaryTextColor = when {
        isLiquidGlass -> TabulaColors.LiquidGlass.TextSecondary
        isDarkTheme -> Color(0xFF8E8E93)
        else -> Color(0xFF8E8E93)
    }
    
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
    var showRecommendModeSheet by remember { mutableStateOf(false) }

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
            // 使用外部传入的滚动状态（如果有），否则使用本地状态
            val effectiveScrollState = scrollState ?: rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(effectiveScrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

            // ========== 外观 (带小黑猫彩蛋) ==========
            SectionHeader("外观", textColor)
            
            Box(Modifier.fillMaxWidth()) {
                // 小黑猫彩蛋 (趴在卡片上方)
                Image(
                    painter = painterResource(id = R.drawable.cat3),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 24.dp)
                        .offset(y = (-45).dp)
                        .size(72.dp)
                        .zIndex(1f)
                )

                SettingsGroup(cardColor) {
                    SettingsItem(
                        icon = Icons.Outlined.DarkMode,
                        iconTint = Color(0xFF5E5CE6), // Indigo
                        title = "主题模式",
                        value = when (currentTheme) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                            ThemeMode.LIQUID_GLASS -> "跟随系统" // 已移至实验室，显示降级后的值
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

                SettingsItem(
                    icon = Icons.Outlined.Image,
                    iconTint = Color(0xFF0A84FF), // Blue
                    title = "卡片显示",
                    value = "",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onNavigateToImageDisplay()
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
                        onNavigateToVibrationSound()
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            
            // ========== 实验室 ==========
            SectionHeader("实验室", textColor)
            
            SettingsGroup(cardColor) {
                SettingsItem(
                    icon = Icons.Outlined.Science,
                    iconTint = Color(0xFFBF5AF2), // Purple
                    title = "实验室",
                    value = "",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onNavigateToLab()
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

            // ========== 关于与支持 ==========
            SectionHeader("关于与支持", textColor)
            
            SettingsGroup(cardColor) {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    iconTint = accentColor,
                    title = "关于 Tabula",
                    value = "v${BuildConfig.VERSION_NAME}",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onNavigateToAbout()
                    }
                )
                
                Divider(isDarkTheme)
                
                SettingsItem(
                    icon = Icons.Outlined.Favorite,
                    iconTint = Color(0xFFFF2D55), // 粉红色
                    title = "支持开发者",
                    value = "",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onNavigateToSupport()
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
                // 液态玻璃已移至实验室
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

@Composable
fun VibrationSoundScreen(
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

        // 使用 rememberSaveable 保存滚动位置
        val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
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

@Composable
fun ImageDisplayScreen(
    backgroundColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    showHdrBadges: Boolean,
    showMotionBadges: Boolean,
    cardStyleMode: CardStyleMode,
    onShowHdrBadgesChange: (Boolean) -> Unit,
    onShowMotionBadgesChange: (Boolean) -> Unit,
    onCardStyleModeChange: (CardStyleMode) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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
                text = "卡片显示",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 使用 rememberSaveable 保存滚动位置
        val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 卡片样式选择
            SectionHeader("卡片样式", textColor)
            SettingsGroup(cardColor) {
                CardStyleOptionItem(
                    title = "固定样式",
                    description = "所有卡片统一 3:4 比例",
                    isSelected = cardStyleMode == CardStyleMode.FIXED,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onCardStyleModeChange(CardStyleMode.FIXED)
                    }
                )
                
                Divider(isDarkTheme)
                
                CardStyleOptionItem(
                    title = "自适应样式",
                    description = "根据图片比例动态调整卡片大小",
                    isSelected = cardStyleMode == CardStyleMode.ADAPTIVE,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        onCardStyleModeChange(CardStyleMode.ADAPTIVE)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("标识显示", textColor)
            SettingsGroup(cardColor) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Image,
                    iconTint = Color(0xFF0A84FF), // Blue
                    title = "显示 HDR 标识",
                    textColor = textColor,
                    checked = showHdrBadges,
                    onCheckedChange = {
                        HapticFeedback.lightTap(context)
                        onShowHdrBadgesChange(it)
                    }
                )

                Divider(isDarkTheme)

                SettingsSwitchItem(
                    icon = Icons.Outlined.Movie,
                    iconTint = Color(0xFF30D158), // Green
                    title = "显示 Live 照片",
                    textColor = textColor,
                    checked = showMotionBadges,
                    onCheckedChange = {
                        HapticFeedback.lightTap(context)
                        onShowMotionBadgesChange(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * 卡片样式选项项
 */
@Composable
private fun CardStyleOptionItem(
    title: String,
    description: String,
    isSelected: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
        }
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已选择",
                tint = Color(0xFF0A84FF),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun LabScreen(
    backgroundColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    fluidCloudEnabled: Boolean,
    onFluidCloudEnabledChange: (Boolean) -> Unit,
    liquidGlassLabEnabled: Boolean = false,
    onLiquidGlassLabEnabledChange: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val accentColor = TabulaColors.EyeGold
    
    // 小组件引导对话框状态
    var showWidgetGuideSheet by remember { mutableStateOf(false) }
    
    // 检测是否为 Android 15+ (API 35+)
    val isAndroid15OrAbove = Build.VERSION.SDK_INT >= 35
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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
                text = "实验室",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 使用 rememberSaveable 保存滚动位置
        val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // 实验室说明
            Text(
                text = "这里是一些实验性功能，可能不稳定或在未来版本中更改。",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ========== 液态玻璃主题 (Android 15+) ==========
            SectionHeader("液态玻璃", textColor)
            SettingsGroup(cardColor) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.DarkMode,
                    iconTint = Color(0xFF667EEA), // 靛蓝紫
                    title = "液态玻璃主题",
                    textColor = textColor,
                    checked = false, // 功能未完善，强制关闭
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // 功能未完善，显示提示
                            HapticFeedback.lightTap(context)
                            Toast.makeText(context, "该功能还未完善", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = true // 允许点击以显示提示
                )
                
                // 液态玻璃说明
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "该功能还在开发中，敬请期待",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9F0A).copy(alpha = 0.8f) // 警告色
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            SectionHeader("模拟流体云", textColor)
            SettingsGroup(cardColor) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Cloud,
                    iconTint = Color(0xFF5E5CE6), // Indigo
                    title = "启用模拟流体云",
                    textColor = textColor,
                    checked = fluidCloudEnabled,
                    onCheckedChange = onFluidCloudEnabledChange
                )
                
                // 流体云说明
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "退出应用时在状态栏显示剩余照片数量（模拟流体云效果）",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ========== 桌面小组件 ==========
            SectionHeader("桌面小组件", textColor)
            SettingsGroup(cardColor) {
                // 检测小组件是否已添加
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetComponent = ComponentName(context, TabulaWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                val isWidgetAdded = widgetIds.isNotEmpty()
                
                SettingsItem(
                    icon = Icons.Outlined.Widgets,
                    iconTint = Color(0xFF30D158), // Green
                    title = "添加桌面小组件",
                    value = if (isWidgetAdded) "已添加 (${widgetIds.size})" else "去添加",
                    textColor = textColor,
                    secondaryTextColor = if (isWidgetAdded) Color(0xFF30D158) else secondaryTextColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        if (isWidgetAdded) {
                            Toast.makeText(context, "小组件已添加到桌面", Toast.LENGTH_SHORT).show()
                        } else {
                            // 显示引导对话框
                            showWidgetGuideSheet = true
                        }
                    }
                )
                
                // 小组件说明
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "在桌面添加小组件，快速查看待整理照片数量",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
    
    // 小组件添加引导对话框
    if (showWidgetGuideSheet) {
        WidgetGuideBottomSheet(
            onDismiss = { showWidgetGuideSheet = false },
            containerColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            accentColor = accentColor
        )
    }
}

/**
 * 小组件添加引导底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetGuideBottomSheet(
    onDismiss: () -> Unit,
    containerColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    val isColorOSDevice = isColorOS()
    val isDarkTheme = LocalIsDarkTheme.current
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    
    // 液态玻璃模式下使用更不透明的背景
    val effectiveContainerColor = if (isLiquidGlassEnabled) {
        if (isDarkTheme) Color(0xFF1C1C1E).copy(alpha = 0.95f)
        else Color(0xFFF2F2F7).copy(alpha = 0.95f)
    } else {
        containerColor
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = effectiveContainerColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题
            Text(
                text = "添加桌面小组件",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // ColorOS 设备提示
            if (isColorOSDevice) {
                Text(
                    text = "检测到 ColorOS 系统，建议使用手动添加方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9F0A),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 非 ColorOS 设备显示自动添加按钮
            if (!isColorOSDevice) {
                WidgetGuideButton(
                    title = "自动添加",
                    subtitle = "请求系统添加小组件到桌面",
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        tryRequestPinWidget(context, onDismiss)
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 打开小组件选择器
                WidgetGuideButton(
                    title = "打开小组件列表",
                    subtitle = "跳转到系统小组件选择界面",
                    accentColor = Color(0xFF5E5CE6),
                    textColor = textColor,
                    onClick = {
                        HapticFeedback.lightTap(context)
                        tryOpenWidgetPicker(context, onDismiss)
                    }
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "如果以上方式无效，请手动添加：",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = textColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Text(
                    text = "请按以下步骤手动添加：",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = textColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            // 步骤说明 - ColorOS 特定
            val steps = if (isColorOSDevice) {
                listOf(
                    "1. 返回桌面",
                    "2. 双指捏合 或 长按空白区域",
                    "3. 点击底部「卡片」或「小组件」",
                    "4. 搜索或滚动找到「Tabula」",
                    "5. 长按小组件拖动到桌面"
                )
            } else {
                listOf(
                    "1. 返回桌面，长按空白区域",
                    "2. 选择「小组件」或「添加工具」",
                    "3. 找到「Tabula」并添加"
                )
            }
            
            steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 关闭按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .clickable {
                        HapticFeedback.lightTap(context)
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "知道了",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = accentColor
                )
            }
        }
    }
}

/**
 * 引导按钮组件
 */
@Composable
private fun WidgetGuideButton(
    title: String,
    subtitle: String,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = textColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 检测是否是 ColorOS / OPPO / OnePlus / realme 设备
 */
private fun isColorOS(): Boolean {
    return try {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        manufacturer.contains("oppo") || 
        manufacturer.contains("oneplus") || 
        manufacturer.contains("realme") ||
        brand.contains("oppo") || 
        brand.contains("oneplus") || 
        brand.contains("realme")
    } catch (e: Exception) {
        false
    }
}

/**
 * 尝试通过系统 API 请求添加小组件
 */
private fun tryRequestPinWidget(context: android.content.Context, onSuccess: () -> Unit) {
    val tag = "WidgetPin"
    
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Log.w(tag, "Android 版本低于 8.0，不支持 requestPinAppWidget")
        Toast.makeText(context, "您的系统版本不支持自动添加，请手动添加", Toast.LENGTH_LONG).show()
        return
    }
    
    // ColorOS 设备对 requestPinAppWidget 支持有问题，直接提示手动添加
    if (isColorOS()) {
        Log.w(tag, "检测到 ColorOS 设备，requestPinAppWidget 可能无效")
        Toast.makeText(context, "ColorOS 系统请使用「打开小组件列表」或手动添加", Toast.LENGTH_LONG).show()
        return
    }
    
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val widgetComponent = ComponentName(context, TabulaWidgetProvider::class.java)
    
    Log.d(tag, "检查 requestPinAppWidget 支持状态...")
    Log.d(tag, "isRequestPinAppWidgetSupported: ${appWidgetManager.isRequestPinAppWidgetSupported}")
    Log.d(tag, "设备信息: ${Build.MANUFACTURER} / ${Build.BRAND} / ${Build.MODEL}")
    
    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        try {
            Log.d(tag, "调用 requestPinAppWidget...")
            val success = appWidgetManager.requestPinAppWidget(widgetComponent, null, null)
            Log.d(tag, "requestPinAppWidget 返回: $success")
            
            if (success) {
                Toast.makeText(context, "请在弹出的对话框中确认添加", Toast.LENGTH_LONG).show()
                onSuccess()
            } else {
                Toast.makeText(context, "添加请求失败，请尝试手动添加", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "requestPinAppWidget 异常: ${e.message}", e)
            Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    } else {
        Log.w(tag, "启动器不支持 requestPinAppWidget")
        Toast.makeText(context, "您的桌面启动器不支持自动添加，请手动添加", Toast.LENGTH_LONG).show()
    }
}

/**
 * 尝试打开系统小组件选择器
 */
private fun tryOpenWidgetPicker(context: android.content.Context, onSuccess: () -> Unit) {
    val tag = "WidgetPicker"
    
    Log.d(tag, "设备信息: ${Build.MANUFACTURER} / ${Build.BRAND} / ${Build.MODEL}")
    
    // 获取默认启动器包名
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val defaultLauncher = context.packageManager.resolveActivity(launcherIntent, 0)?.activityInfo?.packageName
    Log.d(tag, "默认启动器: $defaultLauncher")
    
    // 构建 Intent 列表
    val intentsToTry = mutableListOf<Intent>()
    
    // 1. 根据默认启动器动态添加 Intent
    defaultLauncher?.let { launcher ->
        // 常见的小组件选择器 Activity 名称
        val widgetActivities = listOf(
            "$launcher.widget.WidgetContainerActivity",
            "$launcher.widget.WidgetPickerActivity",
            "$launcher.WidgetPicker",
            "$launcher.widget.WidgetsFullSheet",
            "com.android.launcher3.widget.WidgetsFullSheet",
            "com.android.launcher3.WidgetPicker"
        )
        widgetActivities.forEach { activityName ->
            intentsToTry.add(Intent().apply {
                setClassName(launcher, activityName)
            })
        }
    }
    
    // 2. ColorOS / OPPO 特定
    if (isColorOS()) {
        intentsToTry.addAll(listOf(
            // ColorOS 14+ 卡片中心
            Intent().apply {
                setClassName("com.heytap.cardcenter", "com.heytap.cardcenter.ui.CardCenterActivity")
            },
            Intent().apply {
                setClassName("com.heytap.cardcenter", "com.heytap.cardcenter.MainActivity")
            },
            // OPPO Launcher
            Intent().apply {
                setClassName("com.oppo.launcher", "com.oppo.launcher.widget.WidgetContainerActivity")
            },
            Intent().apply {
                setClassName("com.oppo.launcher", "com.oppo.launcher.WidgetPicker")
            },
            // com.android.launcher（ColorOS 定制）
            Intent().apply {
                setClassName("com.android.launcher", "com.android.launcher.widget.WidgetContainerActivity")
            },
            Intent().apply {
                setClassName("com.android.launcher", "com.android.launcher.widget.WidgetPickerActivity")
            },
            Intent().apply {
                setClassName("com.android.launcher", "com.android.launcher.WidgetPicker")
            },
            Intent().apply {
                setClassName("com.android.launcher", "com.oppo.launcher.widget.WidgetContainerActivity")
            }
        ))
    }
    
    // 3. 其他厂商启动器
    intentsToTry.addAll(listOf(
        // MIUI
        Intent().apply {
            setClassName("com.miui.home", "com.miui.home.launcher.widget.WidgetPickerActivity")
        },
        // 华为 EMUI / HarmonyOS
        Intent().apply {
            setClassName("com.huawei.android.launcher", "com.huawei.android.launcher.widgetmanager.WidgetManagerActivity")
        },
        // 三星 One UI
        Intent().apply {
            setClassName("com.sec.android.app.launcher", "com.android.launcher3.widget.WidgetsFullSheet")
        },
        // Pixel Launcher
        Intent().apply {
            setClassName("com.google.android.apps.nexuslauncher", "com.android.launcher3.widget.WidgetsFullSheet")
        },
        // 通用 AOSP Launcher3
        Intent().apply {
            setClassName("com.android.launcher3", "com.android.launcher3.widget.WidgetsFullSheet")
        }
    ))
    
    // 4. 尝试启动各个 Intent
    for (intent in intentsToTry) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                Log.d(tag, "尝试启动: ${intent.component}")
                context.startActivity(intent)
                Toast.makeText(context, "请在列表中找到「Tabula」小组件", Toast.LENGTH_LONG).show()
                onSuccess()
                return
            } else {
                Log.d(tag, "无法解析: ${intent.component}")
            }
        } catch (e: Exception) {
            Log.d(tag, "Intent 失败: ${intent.component}, 错误: ${e.message}")
        }
    }
    
    // 5. 所有方式都失败，不再跳转到桌面设置，直接提示手动操作
    Log.w(tag, "无法打开小组件选择器，所有 Intent 均失败")
    Toast.makeText(
        context, 
        "无法自动打开小组件列表\n请返回桌面 → 长按空白处 → 选择小组件 → 找到 Tabula", 
        Toast.LENGTH_LONG
    ).show()
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
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    
    if (isLiquidGlass) {
        // 液态玻璃模式下使用 Backdrop 液态玻璃效果
        com.tabula.v3.ui.components.BackdropLiquidGlassSettings(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                content()
            }
        }
    } else {
        // 普通模式
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
        ) {
            content()
        }
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

/**
 * 玻璃风格分割线
 * 使用 GlassDivider 组件，带两端淡出效果
 */
@Composable
fun Divider(isDarkTheme: Boolean) {
    GlassDivider(
        isDarkTheme = isDarkTheme,
        startPadding = 52.dp  // 图标宽度 + 间距
    )
}

/**
 * 玻璃风格 BottomSheet（适配液态玻璃主题）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    containerColor: Color,
    textColor: Color,
    content: @Composable () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    
    GlassBottomSheet(
        title = title,
        onDismiss = onDismiss,
        containerColor = containerColor,
        textColor = textColor,
        isDarkTheme = isDarkTheme,
        content = content
    )
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
