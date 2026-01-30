package com.tabula.v3

import android.Manifest
import android.graphics.Color as AndroidColor
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.CardStyleMode
import com.tabula.v3.data.preferences.ThemeMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.data.repository.FileOperationManager
import com.tabula.v3.data.repository.LocalImageRepository
import com.tabula.v3.data.repository.RecycleBinManager
import com.tabula.v3.data.repository.RecommendationEngine
import com.tabula.v3.ui.navigation.AppScreen
import com.tabula.v3.ui.navigation.PredictiveBackContainer
import com.tabula.v3.ui.screens.AboutScreen
import com.tabula.v3.ui.screens.DeckScreen
import com.tabula.v3.ui.screens.RecycleBinScreen
import com.tabula.v3.ui.screens.SettingsScreen
import com.tabula.v3.ui.screens.SupportScreen
import com.tabula.v3.ui.screens.VibrationSoundScreen
import com.tabula.v3.ui.screens.LabScreen
import com.tabula.v3.ui.screens.ImageDisplayScreen
import com.tabula.v3.ui.screens.StatisticsScreen
import com.tabula.v3.ui.screens.AlbumViewScreen
import com.tabula.v3.ui.screens.SystemAlbumViewScreen
import com.tabula.v3.data.repository.AlbumManager
import com.tabula.v3.data.model.Album
import androidx.compose.runtime.collectAsState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.theme.TabulaTheme
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.service.FluidCloudService
import android.widget.Toast
import com.tabula.v3.ui.components.isBackdropLiquidGlassSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity - Tabula 应用入口
 *
 * 核心功能：
 * 1. 沉浸式 Edge-to-Edge UI
 * 2. 权限管理（READ_MEDIA_IMAGES）
 * 3. 简单路由管理
 * 4. ColorOS 16 风格预测性返回
 */
class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)
    
    // 流体云：跟踪当前批次剩余数量
    private var currentBatchRemaining = 0
    private var isFluidCloudEnabled = false

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // 删除权限请求（Android 11+ MediaStore 删除确认）
    private lateinit var deletePermissionLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var onDeletePermissionResult: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 使用完全透明的导航栏样式，避免底部小白条区域有背景色
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        checkPermission()

        // 初始化删除权限启动器
        deletePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            onDeletePermissionResult?.invoke(success)
            onDeletePermissionResult = null
        }

        setContent {
            val context = LocalContext.current
            val preferences = remember { AppPreferences(context) }
            var currentTheme by remember { mutableStateOf(preferences.themeMode) }
            
            // 液态玻璃实验室功能（独立于主题）
            var liquidGlassLabEnabled by remember { mutableStateOf(preferences.liquidGlassLabEnabled) }

            // 根据设置决定深色模式
            val darkTheme = when (currentTheme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.LIQUID_GLASS -> false  // 兼容旧数据，降级为 SYSTEM 行为
            }
            
            // 是否启用液态玻璃效果（来自实验室设置，而非主题）
            val liquidGlassEnabled = liquidGlassLabEnabled && android.os.Build.VERSION.SDK_INT >= 35

            TabulaTheme(darkTheme = darkTheme, liquidGlassEnabled = liquidGlassEnabled) {
                if (hasPermission) {
                    TabulaApp(
                        preferences = preferences,
                        onThemeChange = { theme -> currentTheme = theme },
                        onRequestDeletePermission = { intentSender, callback ->
                            onDeletePermissionResult = callback
                            deletePermissionLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        },
                        liquidGlassLabEnabled = liquidGlassLabEnabled,
                        onLiquidGlassLabEnabledChange = { enabled ->
                            liquidGlassLabEnabled = enabled
                            preferences.liquidGlassLabEnabled = enabled
                        }
                    )
                } else {
                    PermissionRequestScreen(
                        onRequestPermission = { requestPermission() }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 返回应用时隐藏流体云
        FluidCloudService.hide(this)
    }
    
    override fun onStop() {
        super.onStop()
        // 离开应用时，如果有未完成的批次且流体云已启用，则显示流体云
        if (isFluidCloudEnabled && currentBatchRemaining > 0) {
            FluidCloudService.show(this, currentBatchRemaining)
        }
    }
    
    /**
     * 更新批次剩余数量（供 Composable 调用）
     */
    fun updateBatchRemaining(remaining: Int, fluidCloudEnabled: Boolean) {
        currentBatchRemaining = remaining
        isFluidCloudEnabled = fluidCloudEnabled
    }

    private fun checkPermission() {
        hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
    }
    
}

/**
 * Tabula 应用主体
 *
 * 包含路由管理和预测性返回动画
 */
@Composable
fun TabulaApp(
    preferences: AppPreferences,
    onThemeChange: (ThemeMode) -> Unit,
    onRequestDeletePermission: (android.content.IntentSender, (Boolean) -> Unit) -> Unit,
    liquidGlassLabEnabled: Boolean = false,
    onLiquidGlassLabEnabledChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ========== 设置 ==========
    var currentBatchSize by remember { mutableIntStateOf(preferences.batchSize) }
    var currentTopBarMode by remember { mutableStateOf(preferences.topBarDisplayMode) }
    var showHdrBadges by remember { mutableStateOf(preferences.showHdrBadges) }
    var showMotionBadges by remember { mutableStateOf(preferences.showMotionBadges) }
    var cardStyleMode by remember { mutableStateOf(preferences.cardStyleMode) }
    var playMotionSound by remember { mutableStateOf(preferences.playMotionSound) }
    var motionSoundVolume by remember { mutableIntStateOf(preferences.motionSoundVolume) }
    var hapticEnabled by remember { mutableStateOf(preferences.hapticEnabled) }
    var hapticStrength by remember { mutableIntStateOf(preferences.hapticStrength) }
    var swipeHapticsEnabled by remember { mutableStateOf(preferences.swipeHapticsEnabled) }
    var fluidCloudEnabled by remember { mutableStateOf(preferences.fluidCloudEnabled) }

    // ========== 路由状态 ==========
    var currentScreen by remember { mutableStateOf(AppScreen.DECK) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedBucketName by remember { mutableStateOf<String?>(null) }
    var isAlbumMode by remember { mutableStateOf(false) }

    // ========== 图片数据状态 ==========
    var allImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var deletedImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var systemBuckets by remember { mutableStateOf<List<LocalImageRepository.SystemBucket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 管理器
    val fileOperationManager = remember { FileOperationManager(context) }
    val recycleBinManager = remember { RecycleBinManager.getInstance(context) }
    val albumManager = remember { AlbumManager.getInstance(context) }
    val recommendationEngine = remember { RecommendationEngine.getInstance(context) }
    val albums by albumManager.albums.collectAsState()
    val albumMappings by albumManager.mappings.collectAsState()

    // ========== 同步状态 ==========
    var isSyncing by remember { mutableStateOf(false) }

    // ========== 推荐模式刷新触发器 ==========
    // 用于在设置中切换推荐模式后，返回主页时刷新批次
    var recommendModeRefreshKey by remember { mutableIntStateOf(0) }

    // ========== 各页面的滚动状态（在 TabulaApp 级别保存，导航返回时保持位置） ==========
    val settingsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    // 加载数据
    LaunchedEffect(Unit) {
        val loadedData = withContext(Dispatchers.IO) {
            val repository = LocalImageRepository(context)
            val images = repository.getAllImages()
            val buckets = repository.getAllBucketsWithInfo()
            val deleted = recycleBinManager.loadRecycleBin()
            albumManager.initialize()
            Triple(images, buckets, deleted)
        }
        // 在主线程更新状态
        allImages = loadedData.first
        systemBuckets = loadedData.second
        deletedImages = loadedData.third
        isLoading = false
    }

    /**
     * 执行删除操作
     */
    fun performDelete(image: ImageFile, onResult: (Boolean) -> Unit) {
        scope.launch {
            val result = fileOperationManager.deleteImage(image.uri)
            when (result) {
                is FileOperationManager.DeleteResult.Success -> {
                    Log.d("TabulaApp", "Deleted: ${image.displayName}")
                    onResult(true)
                }
                is FileOperationManager.DeleteResult.NeedsPermission -> {
                    onRequestDeletePermission(result.intentSender) { granted ->
                        if (granted) {
                            Log.d("TabulaApp", "Delete permission granted for: ${image.displayName}")
                        } else {
                            Log.w("TabulaApp", "Delete permission denied for: ${image.displayName}")
                        }
                        onResult(granted)
                    }
                }
                is FileOperationManager.DeleteResult.Error -> {
                    Log.e("TabulaApp", "Delete error: ${result.message}")
                    onResult(false)
                }
            }
        }
    }
    
    /**
     * Batch delete
     */
    fun performDeleteBatch(images: List<ImageFile>, onResult: (Boolean) -> Unit) {
        if (images.isEmpty()) {
            onResult(true)
            return
        }
        
        scope.launch {
            val result = fileOperationManager.deleteImages(images.map { it.uri })
            when (result) {
                is FileOperationManager.DeleteResult.Success -> {
                    Log.d("TabulaApp", "Deleted batch: ${images.size}")
                    onResult(true)
                }
                is FileOperationManager.DeleteResult.NeedsPermission -> {
                    onRequestDeletePermission(result.intentSender) { granted ->
                        if (granted) {
                            Log.d("TabulaApp", "Delete permission granted for batch: ${images.size}")
                        } else {
                            Log.w("TabulaApp", "Delete permission denied for batch: ${images.size}")
                        }
                        onResult(granted)
                    }
                }
                is FileOperationManager.DeleteResult.Error -> {
                    Log.e("TabulaApp", "Delete error: ${result.message}")
                    onResult(false)
                }
            }
        }
    }

    // ========== 屏幕内容定义 ==========
    LaunchedEffect(hapticEnabled, hapticStrength) {
        com.tabula.v3.ui.util.HapticFeedback.updateSettings(
            enabled = hapticEnabled,
            strength = hapticStrength
        )
    }

    val deckContent: @Composable () -> Unit = {
        DeckScreen(
            allImages = allImages,
            batchSize = currentBatchSize,
            isLoading = isLoading,
            topBarDisplayMode = currentTopBarMode,
            onKeep = {
                preferences.totalReviewedCount++
            },
            onRemove = { image ->
                val newImages = allImages.toMutableList().apply { remove(image) }
                allImages = newImages
                deletedImages = deletedImages + image
                
                preferences.totalReviewedCount++
                preferences.totalDeletedCount++
                
                scope.launch {
                    recycleBinManager.addToRecycleBin(image)
                }
                Log.d("TabulaApp", "Moved to trash: ${image.displayName}")
            },
            onNavigateToTrash = {
                currentScreen = AppScreen.RECYCLE_BIN
            },
            onNavigateToSettings = {
                currentScreen = AppScreen.SETTINGS
            },
            onNavigateToAlbumDetail = { album ->
                selectedAlbumId = album.id
                currentScreen = AppScreen.ALBUM_VIEW
            },
            albums = albums,
            systemBuckets = systemBuckets,
            onAlbumSelect = { imageId, imageUri, albumId ->
                 scope.launch {
                     albumManager.addImageToAlbum(imageId, imageUri, albumId)
                 }
            },
            onCreateAlbum = { name, color, emoji ->
                scope.launch {
                    albumManager.createAlbum(name, color, emoji)
                }
            },
            onUndoAlbumAction = {
                scope.launch {
                    albumManager.undoLastAction()
                }
            },
            onReorderAlbums = { newOrder ->
                scope.launch {
                    albumManager.reorderAlbums(newOrder)
                }
            },
            onSystemBucketClick = { bucketName ->
                selectedBucketName = bucketName
                currentScreen = AppScreen.SYSTEM_ALBUM_VIEW
            },
            // 推荐算法回调 - 使用 RecommendationEngine
            getRecommendedBatch = { images, size ->
                recommendationEngine.getRecommendedBatch(images, size)
            },
            // 同步按钮回调
            onSyncClick = {
                if (!isSyncing) {
                    isSyncing = true
                    Toast.makeText(context, "正在同步图集到系统相册...", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        try {
                            val result = albumManager.syncAllEnabledAlbumsToSystem()
                            withContext(Dispatchers.Main) {
                                val message = when {
                                    result.totalAlbums == 0 -> "没有可同步的图集\n请先创建图集并添加图片"
                                    result.newlySyncedImages == 0 && result.skippedImages > 0 -> 
                                        "所有 ${result.totalAlbums} 个图集已是最新\n共 ${result.skippedImages} 张图片无需更新"
                                    result.newlySyncedImages > 0 && result.skippedImages > 0 ->
                                        "同步完成！\n新增 ${result.newlySyncedImages} 张，${result.skippedImages} 张已存在"
                                    result.newlySyncedImages > 0 -> 
                                        "同步完成！\n${result.totalAlbums} 个图集，共 ${result.newlySyncedImages} 张新图片"
                                    else -> 
                                        "同步完成\n成功 ${result.successCount}/${result.totalAlbums} 个图集"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                
                                // 刷新系统相册列表和图片列表
                                val repository = LocalImageRepository(context)
                                val refreshedData = withContext(Dispatchers.IO) {
                                    Pair(
                                        repository.getAllBucketsWithInfo(),
                                        repository.getAllImages()
                                    )
                                }
                                systemBuckets = refreshedData.first
                                allImages = refreshedData.second
                            }
                        } catch (e: Exception) {
                            Log.e("TabulaApp", "Sync failed", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            isSyncing = false
                        }
                    }
                } else {
                    Toast.makeText(context, "同步进行中，请稍候...", Toast.LENGTH_SHORT).show()
                }
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            enableSwipeHaptics = swipeHapticsEnabled,
            isAdaptiveCardStyle = cardStyleMode == CardStyleMode.ADAPTIVE,
            isAlbumMode = isAlbumMode,
            onModeChange = { isAlbumMode = it },
            onBatchRemainingChange = { remaining ->
                // 更新流体云的批次剩余数量
                (context as? MainActivity)?.updateBatchRemaining(remaining, fluidCloudEnabled)
            }
        )
    }

    val recycleBinContent: @Composable () -> Unit = {
        RecycleBinScreen(
            deletedImages = deletedImages,
            onRestore = { image ->
                deletedImages = deletedImages.toMutableList().apply { remove(image) }
                allImages = allImages + image
                scope.launch {
                    recycleBinManager.removeFromRecycleBin(image)
                }
                Log.d("TabulaApp", "Restored: ${image.displayName}")
            },
            onPermanentDelete = { image ->
                performDelete(image) { success ->
                    if (success) {
                        deletedImages = deletedImages.toMutableList().apply { remove(image) }
                        scope.launch {
                            recycleBinManager.removeFromRecycleBin(image)
                        }
                    }
                }
            },
            onPermanentDeleteBatch = { images ->
                performDeleteBatch(images) { success ->
                    if (success) {
                        deletedImages = deletedImages.toMutableList().apply { removeAll(images) }
                        scope.launch {
                            recycleBinManager.removeAllFromRecycleBin(images)
                        }
                    }
                }
            },
            onClearAll = {
                val imagesToDelete = deletedImages
                performDeleteBatch(imagesToDelete) { success ->
                    if (success) {
                        scope.launch {
                            recycleBinManager.clearRecycleBin()
                        }
                        deletedImages = emptyList()
                    }
                }
            },
            onNavigateBack = {
                currentScreen = AppScreen.DECK
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume
        )
    }

    val settingsContent: @Composable () -> Unit = {
        SettingsScreen(
            preferences = preferences,
            imageCount = allImages.size,
            trashCount = deletedImages.size,
            onThemeChange = onThemeChange,
            onBatchSizeChange = { size -> currentBatchSize = size },
            onTopBarModeChange = { mode -> currentTopBarMode = mode },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            hapticEnabled = hapticEnabled,
            hapticStrength = hapticStrength,
            swipeHapticsEnabled = swipeHapticsEnabled,
            onShowHdrBadgesChange = { enabled ->
                showHdrBadges = enabled
                preferences.showHdrBadges = enabled
            },
            onShowMotionBadgesChange = { enabled ->
                showMotionBadges = enabled
                preferences.showMotionBadges = enabled
            },
            onPlayMotionSoundChange = { enabled ->
                playMotionSound = enabled
                preferences.playMotionSound = enabled
            },
            onMotionSoundVolumeChange = { volume ->
                motionSoundVolume = volume
                preferences.motionSoundVolume = volume
            },
            onHapticEnabledChange = { enabled ->
                hapticEnabled = enabled
                preferences.hapticEnabled = enabled
            },
            onHapticStrengthChange = { strength ->
                hapticStrength = strength
                preferences.hapticStrength = strength
            },
            onSwipeHapticsEnabledChange = { enabled ->
                swipeHapticsEnabled = enabled
                preferences.swipeHapticsEnabled = enabled
            },
            onNavigateToAbout = { currentScreen = AppScreen.ABOUT },
            onNavigateBack = { currentScreen = AppScreen.DECK },
            onNavigateToStatistics = { currentScreen = AppScreen.STATISTICS },
            onRecommendModeChange = { 
                // 切换推荐模式后，增加刷新触发器以刷新批次
                recommendModeRefreshKey++
            },
            fluidCloudEnabled = fluidCloudEnabled,
            onFluidCloudEnabledChange = { enabled ->
                fluidCloudEnabled = enabled
                preferences.fluidCloudEnabled = enabled
            },
            onNavigateToVibrationSound = { currentScreen = AppScreen.VIBRATION_SOUND },
            onNavigateToImageDisplay = { currentScreen = AppScreen.IMAGE_DISPLAY },
            onNavigateToLab = { currentScreen = AppScreen.LAB },
            onNavigateToSupport = { currentScreen = AppScreen.SUPPORT },
            scrollState = settingsScrollState
        )
    }

    val vibrationSoundContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        VibrationSoundScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            accentColor = TabulaColors.EyeGold,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            hapticEnabled = hapticEnabled,
            hapticStrength = hapticStrength,
            swipeHapticsEnabled = swipeHapticsEnabled,
            onPlayMotionSoundChange = { enabled ->
                playMotionSound = enabled
                preferences.playMotionSound = enabled
            },
            onMotionSoundVolumeChange = { volume ->
                motionSoundVolume = volume
                preferences.motionSoundVolume = volume
            },
            onHapticEnabledChange = { enabled ->
                hapticEnabled = enabled
                preferences.hapticEnabled = enabled
                HapticFeedback.updateSettings(enabled, hapticStrength)
            },
            onHapticStrengthChange = { strength ->
                hapticStrength = strength
                preferences.hapticStrength = strength
                HapticFeedback.updateSettings(hapticEnabled, strength)
            },
            onSwipeHapticsEnabledChange = { enabled ->
                swipeHapticsEnabled = enabled
                preferences.swipeHapticsEnabled = enabled
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val labContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        LabScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            fluidCloudEnabled = fluidCloudEnabled,
            onFluidCloudEnabledChange = { enabled ->
                fluidCloudEnabled = enabled
                preferences.fluidCloudEnabled = enabled
            },
            liquidGlassLabEnabled = liquidGlassLabEnabled,
            onLiquidGlassLabEnabledChange = onLiquidGlassLabEnabledChange,
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val imageDisplayContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        ImageDisplayScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            cardStyleMode = cardStyleMode,
            onShowHdrBadgesChange = { enabled ->
                showHdrBadges = enabled
                preferences.showHdrBadges = enabled
            },
            onShowMotionBadgesChange = { enabled ->
                showMotionBadges = enabled
                preferences.showMotionBadges = enabled
            },
            onCardStyleModeChange = { mode ->
                cardStyleMode = mode
                preferences.cardStyleMode = mode
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val aboutContent: @Composable () -> Unit = {
        AboutScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val supportContent: @Composable () -> Unit = {
        SupportScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val statisticsContent: @Composable () -> Unit = {
        // 计算剩余整理数量：还没有被推荐刷到的图片
        val cooldownIds = preferences.getCooldownImageIds()
        val seenCount = allImages.count { it.id in cooldownIds }
        val remainingToOrganize = allImages.size - seenCount
        
        StatisticsScreen(
            reviewedCount = preferences.totalReviewedCount.toInt(),
            totalImages = allImages.size + preferences.totalReviewedCount.toInt(),
            deletedCount = preferences.totalDeletedCount.toInt(),
            markedCount = deletedImages.size,
            remainingCount = remainingToOrganize,
            onBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val albumViewContent: @Composable () -> Unit = {
        AlbumViewScreen(
            albums = albums,
            allImages = allImages,
            getImagesForAlbum = { albumId -> albumManager.getImageIdsForAlbum(albumId) },
            onCreateAlbum = { name, color, emoji ->
                scope.launch { albumManager.createAlbum(name, color, emoji) }
            },
            onUpdateAlbum = { album ->
                scope.launch { albumManager.updateAlbum(album) }
            },
            onDeleteAlbum = { albumId ->
                scope.launch { albumManager.deleteAlbum(albumId) }
            },
            onToggleSync = { albumId, enabled ->
                scope.launch { albumManager.toggleSyncEnabled(albumId, enabled) }
            },
            onChangeSyncMode = { albumId, mode ->
                scope.launch { albumManager.changeSyncMode(albumId, mode) }
            },
            onNavigateBack = { currentScreen = AppScreen.DECK },
            initialAlbumId = selectedAlbumId,
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            onMoveToAlbum = { imageIds, targetAlbumId ->
                scope.launch {
                    selectedAlbumId?.let { currentAlbumId ->
                        albumManager.moveImagesToAlbum(imageIds, currentAlbumId, targetAlbumId)
                    }
                }
            }
        )
    }

    val systemAlbumViewContent: @Composable () -> Unit = {
        var bucketImages by remember(selectedBucketName) { mutableStateOf<List<ImageFile>>(emptyList()) }
        LaunchedEffect(selectedBucketName) {
             if (selectedBucketName != null) {
                 bucketImages = withContext(Dispatchers.IO) {
                     LocalImageRepository(context).getImagesByBucket(selectedBucketName!!)
                 }
             }
        }
        
        SystemAlbumViewScreen(
            albumName = selectedBucketName ?: "",
            images = bucketImages,
            onNavigateBack = { currentScreen = AppScreen.DECK },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume
        )
    }

    // 根据当前屏幕决定 前景 和 背景
    val (backgroundContent, foregroundContent) = when (currentScreen) {
        AppScreen.DECK -> deckContent to null
        AppScreen.RECYCLE_BIN -> deckContent to recycleBinContent
        AppScreen.SETTINGS -> deckContent to settingsContent
        AppScreen.ABOUT -> settingsContent to aboutContent
        AppScreen.SUPPORT -> settingsContent to supportContent
        AppScreen.STATISTICS -> settingsContent to statisticsContent
        AppScreen.ALBUM_VIEW -> deckContent to albumViewContent
        AppScreen.SYSTEM_ALBUM_VIEW -> deckContent to systemAlbumViewContent
        AppScreen.VIBRATION_SOUND -> settingsContent to vibrationSoundContent
        AppScreen.IMAGE_DISPLAY -> settingsContent to imageDisplayContent
        AppScreen.LAB -> settingsContent to labContent
    }
    
    // ========== 渲染容器 ==========
    // 液态玻璃效果现在由 AGSL 着色器在各个组件内部处理
    // 不再需要全局的 Backdrop Provider
    PredictiveBackContainer(
        currentScreen = currentScreen,
        onNavigateBack = {
            currentScreen = when (currentScreen) {
                AppScreen.RECYCLE_BIN -> AppScreen.DECK
                AppScreen.SETTINGS -> AppScreen.DECK
                AppScreen.ABOUT -> AppScreen.SETTINGS
                AppScreen.SUPPORT -> AppScreen.SETTINGS
                AppScreen.STATISTICS -> AppScreen.SETTINGS
                AppScreen.ALBUM_VIEW -> AppScreen.DECK
                AppScreen.SYSTEM_ALBUM_VIEW -> AppScreen.DECK
                AppScreen.VIBRATION_SOUND -> AppScreen.SETTINGS
                AppScreen.IMAGE_DISPLAY -> AppScreen.SETTINGS
                AppScreen.LAB -> AppScreen.SETTINGS
                AppScreen.DECK -> AppScreen.DECK
            }
        },
        backgroundContent = backgroundContent,
        foregroundContent = foregroundContent ?: {}
    )
}

/**
 * 权限请求界面 - 黑猫风格
 */
@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val backgroundColor = if (isDarkTheme) TabulaColors.CatBlack else TabulaColors.WarmWhite
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val buttonBgColor = if (isDarkTheme) TabulaColors.EyeGold else TabulaColors.CatBlack
    val buttonTextColor = if (isDarkTheme) TabulaColors.CatBlack else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Tabula Logo",
                modifier = Modifier.size(150.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "欢迎使用 Tabula",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "高效整理你的照片库",
                fontSize = 16.sp,
                color = textColor.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .size(width = 200.dp, height = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBgColor,
                    contentColor = buttonTextColor
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "授权访问照片",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
