package com.tabula.v3

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import com.tabula.v3.BuildConfig
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.CardStyleMode
import com.tabula.v3.data.preferences.SourceImageDeletionStrategy
import com.tabula.v3.data.preferences.SwipeStyle
import com.tabula.v3.data.preferences.TagSelectionMode
import com.tabula.v3.data.preferences.ThemeMode
import com.tabula.v3.data.preferences.TopBarDisplayMode
import com.tabula.v3.data.repository.DeletionVerification
import com.tabula.v3.data.repository.FileOperationManager
import com.tabula.v3.data.repository.LocalImageRepository
import com.tabula.v3.data.repository.matchesSameMediaIdentity
import com.tabula.v3.data.repository.partitionByConfirmedIdentity
import com.tabula.v3.data.repository.projectSnapshot
import com.tabula.v3.data.repository.RecycleBinManager
import com.tabula.v3.data.repository.RecommendationEngine
import com.tabula.v3.data.repository.AlbumCleanupEngine
import com.tabula.v3.data.repository.AlbumCleanupInfo
import com.tabula.v3.permission.MediaPermissionPolicy
import com.tabula.v3.permission.DeletePermissionSession
import com.tabula.v3.data.repository.RecycleBinPersistenceException
import com.tabula.v3.data.repository.RecycleBinCommitException
import com.tabula.v3.data.repository.deleteRecycleBinSelection
import com.tabula.v3.data.repository.reconcileRecycleBinDeletion
import com.tabula.v3.ui.navigation.AppScreen
import com.tabula.v3.ui.navigation.PredictiveBackContainer
import com.tabula.v3.ui.screens.AboutScreen
import com.tabula.v3.ui.screens.DeckScreen
import com.tabula.v3.ui.screens.RecycleBinScreen
import com.tabula.v3.ui.screens.SettingsScreen
import com.tabula.v3.ui.screens.SupportScreen
import com.tabula.v3.ui.screens.VibrationSoundScreen
import com.tabula.v3.ui.screens.LabScreen
import com.tabula.v3.ui.screens.DisplaySettingsScreen
import com.tabula.v3.ui.screens.ReminderSettingsScreen
import com.tabula.v3.ui.screens.StatisticsScreen
import com.tabula.v3.ui.screens.AlbumViewScreen
import com.tabula.v3.ui.screens.SystemAlbumViewScreen
import com.tabula.v3.ui.screens.OnboardingScreen
import com.tabula.v3.ui.screens.PersonalizationScreen
import com.tabula.v3.ui.screens.DisclaimerScreen
import com.tabula.v3.ui.screens.PrivacyPolicyScreen
import com.tabula.v3.ui.screens.TutorialScreen
import com.tabula.v3.ui.screens.OtherAlbumsScreen
import com.tabula.v3.data.repository.AlbumManager
import com.tabula.v3.data.model.Album
import androidx.compose.runtime.collectAsState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.screens.AlbumTagManagementScreen
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.theme.TabulaTheme
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.service.FluidCloudService
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import com.tabula.v3.ui.components.isBackdropLiquidGlassSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlin.coroutines.resume
import com.tabula.v3.data.repository.BatchDeleteResult
import com.tabula.v3.data.repository.deleteInBatches
import com.tabula.v3.data.repository.deleteMediaUrisBatch
import com.tabula.v3.data.repository.BatchDeleteSummary
import com.tabula.v3.data.repository.mediaDeletionMessage
import com.tabula.v3.data.repository.resolveImagesByIdentitySelection

/**
 * T02b 恢复核验决策：未知保留在回收站，缺失清除死记录且不入图库，仅 PRESENT 继续恢复。
 */
enum class RestoreDecision { PROCEED_RESTORE, KEEP_UNKNOWN, DROP_MISSING }

fun decideRestoreAction(uri: Uri, verification: DeletionVerification): RestoreDecision = when {
    uri in verification.unknown -> RestoreDecision.KEEP_UNKNOWN
    uri in verification.deleted -> RestoreDecision.DROP_MISSING
    else -> RestoreDecision.PROCEED_RESTORE
}

/**
 * MainActivity - Tabula 应用入口
 *
 * 核心功能：
 * 1. 沉浸式 Edge-to-Edge UI
 * 2. 权限管理（READ_MEDIA_IMAGES）
 * 3. 简单路由管理
 * 4. ColorOS 16 风格预测性返回
 */
/** T03 升级兼容：解析 URI 当前指向的卷。查询返回多行（跨卷同 ID 的聚合
 *  二义）或失败时返回 null → 分区判为未确认（保留记录并停止）。 */
private suspend fun resolveMediaVolumeName(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.VOLUME_NAME), null, null, null
            )?.use { c ->
                when {
                    c.count != 1 -> null
                    c.moveToFirst() -> c.getString(0)?.takeIf { it.isNotBlank() }
                    else -> null
                }
            }
        }.getOrNull()
    }

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    // C4/T07：前台权限/快照刷新触发器——访问级别变化或带权限回前台时递增，
    // 作为 TabulaApp 加载 LaunchedEffect 的键（重启即取消在途扫描，晚到结果
    // 不覆盖新权限态）。
    private var mediaAccessRevision by mutableStateOf(0)
    private var lastAccessLevel: MediaPermissionPolicy.MediaAccessLevel? = null
    
    // 流体云：跟踪当前批次剩余数量
    private var currentBatchRemaining = 0
    private var isFluidCloudEnabled = false

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val sdkInt = android.os.Build.VERSION.SDK_INT
        val level = MediaPermissionPolicy.mediaAccessLevel(sdkInt) { permission ->
            grantResults[permission] == true ||
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        applyAccessLevel(level, foregroundRefresh = false)

        if (!hasPermission) {
            val requestedPermissions = MediaPermissionPolicy.requiredPermissionsForSdk(sdkInt)
            val shouldShowRationale = requestedPermissions.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }
            if (!shouldShowRationale) {
                Log.w("MainActivity", "Permission permanently denied, user should enable it in settings")
            }
        }
    }

    private lateinit var deletePermissionLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var deletePermissionSession: DeletePermissionSession

    override fun onSaveInstanceState(outState: Bundle) {
        deletePermissionSession.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 使用完全透明的导航栏样式，实现沉浸式体验
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        deletePermissionSession = DeletePermissionSession(savedInstanceState)

        checkPermission()

        // 初始化删除权限启动器
        deletePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            deletePermissionSession.complete(success)
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
                        mediaAccessRevision = mediaAccessRevision,
                        onThemeChange = { theme -> currentTheme = theme },
                        onRequestDeletePermission = { intentSender, callback ->
                            deletePermissionSession.launch(callback) {
                                deletePermissionLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            }
                        },
                        deletePermissionInFlight = deletePermissionSession.inFlight,
                        deletionRecoveryRevision = deletePermissionSession.recoveryRevision,
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
        // 返回应用时重新核对媒体访问权限（用户可能在系统设置中撤销了权限、
        // 修改了允许访问的照片选择，或后台期间外部增删改了媒体）——级别变化
        // 或带权限回前台都会触发按现有快照契约的刷新。
        checkPermission(foregroundRefresh = true)
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

    private fun checkPermission(foregroundRefresh: Boolean = false) {
        val sdkInt = android.os.Build.VERSION.SDK_INT
        val level = MediaPermissionPolicy.mediaAccessLevel(sdkInt) { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        applyAccessLevel(level, foregroundRefresh)
    }

    /**
     * C4/T07：应用新的访问级别并按需触发刷新。
     *
     * 状态转换约定：
     * - 级别变化（NONE/PARTIAL/FULL 互切，含系统设置中的撤销与部分选择集
     *   变更）：更新门控；新级别非 NONE 时递增 [mediaAccessRevision] 触发
     *   按现有快照契约（loadAllData + projectSnapshot）的整表刷新——失去访问
     *   权的旧条目随 MediaStore 重查自然消失，恢复/升级后重新加载。
     * - 级别不变且带权限回前台（[foregroundRefresh]）：同样递增——后台期间
     *   的外部删除/修改在返回时按快照契约刷新。
     * - NONE：不加载（权限门控屏接管；重新授权后经本约定刷新/全新加载）。
     * - 首次检查（previous == null，onCreate 路径）不触发：组合后的首次
     *   加载已覆盖。
     * - 在途扫描防护：revision 作为 LaunchedEffect 键，重启即取消旧协程，
     *   较早启动的扫描结果不会写入较新的权限状态。
     *
     * 失败约定：刷新走既有加载路径的失败处理（回收站读取失败对话框 + 重试），
     * 权限拒绝由门控屏呈现，本函数不新增失败通道。
     */
    private fun applyAccessLevel(
        level: MediaPermissionPolicy.MediaAccessLevel,
        foregroundRefresh: Boolean
    ) {
        val previous = lastAccessLevel
        lastAccessLevel = level
        hasPermission = level != MediaPermissionPolicy.MediaAccessLevel.NONE
        if (level == MediaPermissionPolicy.MediaAccessLevel.NONE) return
        if (previous == null) return
        if (level != previous || foregroundRefresh) {
            mediaAccessRevision++
        }
    }

    private fun requestPermission() {
        val permissions = MediaPermissionPolicy.requiredPermissionsForSdk(android.os.Build.VERSION.SDK_INT)
        permissionLauncher.launch(permissions)
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
    mediaAccessRevision: Int = 0,
    onThemeChange: (ThemeMode) -> Unit,
    onRequestDeletePermission: (android.content.IntentSender, (Boolean) -> Unit) -> Unit,
    deletePermissionInFlight: Boolean = false,
    deletionRecoveryRevision: Int = 0,
    liquidGlassLabEnabled: Boolean = false,
    onLiquidGlassLabEnabledChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ========== 使用前须知状态 ==========
    // 只在首次启动时显示，确认后不再弹出
    var showDisclaimer by rememberSaveable { mutableStateOf(!preferences.hasAcknowledgedDisclaimer) }

    // ========== 引导流程状态 ==========
    // 只在首次启动时显示引导流程
    // 使用 rememberSaveable 保存状态，避免配置变更时丢失
    var showOnboarding by rememberSaveable { mutableStateOf(!preferences.hasCompletedOnboarding) }
    
    // 监听 preferences.hasCompletedOnboarding 变化，保持状态同步
    LaunchedEffect(preferences.hasCompletedOnboarding) {
        if (preferences.hasCompletedOnboarding) {
            showOnboarding = false
        }
    }

    // ========== 设置 ==========
    var currentBatchSize by remember { mutableIntStateOf(preferences.batchSize) }
    var currentTopBarMode by remember { mutableStateOf(preferences.topBarDisplayMode) }
    var showDeleteConfirm by remember { mutableStateOf(preferences.showDeleteConfirm) }
    var showHdrBadges by remember { mutableStateOf(preferences.showHdrBadges) }
    var showMotionBadges by remember { mutableStateOf(preferences.showMotionBadges) }
    var cardStyleMode by remember { mutableStateOf(preferences.cardStyleMode) }
    var swipeStyle by remember { mutableStateOf(preferences.swipeStyle) }
    // 标签收纳设置
    var tagSelectionMode by remember { mutableStateOf(preferences.tagSelectionMode) }
    var tagSwitchSpeed by remember { mutableFloatStateOf(preferences.tagSwitchSpeed) }
    var tagsPerRow by remember { mutableIntStateOf(preferences.tagsPerRow) }
    var playMotionSound by remember { mutableStateOf(preferences.playMotionSound) }
    var motionSoundVolume by remember { mutableIntStateOf(preferences.motionSoundVolume) }
    var hapticEnabled by remember { mutableStateOf(preferences.hapticEnabled) }
    var hapticStrength by remember { mutableIntStateOf(preferences.hapticStrength) }
    var swipeHapticsEnabled by remember { mutableStateOf(preferences.swipeHapticsEnabled) }
    var fluidCloudEnabled by remember { mutableStateOf(preferences.fluidCloudEnabled) }
    
    // ========== 快捷操作按钮状态 ==========
    var quickActionEnabled by remember { mutableStateOf(preferences.quickActionButtonEnabled) }
    var quickActionButtonX by remember { mutableFloatStateOf(preferences.quickActionButtonX) }
    var quickActionButtonY by remember { mutableFloatStateOf(preferences.quickActionButtonY) }

    // ========== 路由状态 ==========
    // 启动顺序：引导页 -> 个性化设置 -> 使用前须知 -> 主界面
    var currentScreen by remember {
        mutableStateOf(
            when {
                showOnboarding -> AppScreen.ONBOARDING
                showDisclaimer -> AppScreen.DISCLAIMER
                else -> AppScreen.DECK
            }
        )
    }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedBucketName by remember { mutableStateOf<String?>(null) }
    var isAlbumMode by remember { mutableStateOf(false) }
    var showHiddenAlbums by remember { mutableStateOf(false) }
    var otherAlbumsForNavigation by remember { mutableStateOf<List<Album>>(emptyList()) }

    // ========== 图片数据状态 ==========
    var allImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    // 用于主界面卡片整理的图片列表（排除已归类到 Tabula 图集的照片）
    // 这样已归类的照片不会再次出现在待整理列表中
    var imagesForSorting by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var deletedImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var isDeletingRecycleBin by remember { mutableStateOf(false) }
    var recycleBinLoadError by remember { mutableStateOf(false) }
    var recycleBinLoadRevision by remember { mutableIntStateOf(0) }
    var systemBuckets by remember { mutableStateOf<List<LocalImageRepository.SystemBucket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 管理器
    val fileOperationManager = remember { FileOperationManager(context) }
    val recycleBinManager = remember { RecycleBinManager.getInstance(context) }
    val albumManager = remember { AlbumManager.getInstance(context) }
    val recommendationEngine = remember { RecommendationEngine.getInstance(context) }
    val albumCleanupEngine = remember { AlbumCleanupEngine.getInstance(context) }
    val albums by albumManager.albums.collectAsState()
    val albumMappings by albumManager.mappings.collectAsState()
    val excludedAlbumCount by albumManager.excludedAlbumCount.collectAsState()
    val hiddenAlbumCount by albumManager.hiddenAlbumCount.collectAsState()
    
    // ========== 图集清理状态 ==========
    var albumCleanupInfos by remember { mutableStateOf<List<AlbumCleanupInfo>>(emptyList()) }
    
    // 图集清理模式状态（提升到 TabulaApp 级别，避免导航时丢失）
    var isAlbumCleanupMode by remember { mutableStateOf(false) }
    var selectedCleanupAlbum by remember { mutableStateOf<Album?>(null) }
    
    // 刷新图集清理信息的回调
    fun refreshAlbumCleanupInfos() {
        albumCleanupInfos = albums.map { album ->
            albumCleanupEngine.getAlbumCleanupInfo(album)
        }
    }
    
    // 当 albums 变化时刷新清理信息
    LaunchedEffect(albums) {
        refreshAlbumCleanupInfos()
    }

    // ========== 同步状态 ==========
    var isSyncing by remember { mutableStateOf(false) }

    // ========== 推荐模式刷新触发器 ==========
    // 用于在设置中切换推荐模式后，返回主页时刷新批次
    var recommendModeRefreshKey by remember { mutableIntStateOf(0) }
    
    // ========== 原图删除提醒状态 ==========
    var showSourceImageDeletionDialog by remember { mutableStateOf(false) }
    var pendingSourceImageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var dialogSourceImageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var unverifiedSourceImageCount by remember { mutableIntStateOf(0) }
    var sourceImageDeletionStrategy by remember { mutableStateOf(preferences.sourceImageDeletionStrategy) }
    
    // 监听图集模式切换，当切换到图集界面时检查是否需要提醒删除原图
    LaunchedEffect(isAlbumMode, sourceImageDeletionStrategy) {
        if (isAlbumMode) {
            val cleanableResult = albumManager.getCleanableUrisForAllAlbums()
            pendingSourceImageUris = cleanableResult.sourceUris
            unverifiedSourceImageCount = cleanableResult.unverifiedSourceCount
            if (cleanableResult.sourceUris.isNotEmpty() &&
                sourceImageDeletionStrategy == SourceImageDeletionStrategy.ASK_EVERY_TIME
            ) {
                dialogSourceImageUris = cleanableResult.sourceUris.toList()
                showSourceImageDeletionDialog = true
            } else if (cleanableResult.sourceUris.isEmpty()) {
                // 清理候选变化后不能留下过期列表或确认框。
                showSourceImageDeletionDialog = false
            }
        }
    }

    // ========== 各页面的滚动状态（在 TabulaApp 级别保存，导航返回时保持位置） ==========
    val settingsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    fun removeConfirmedFromState(uris: Set<android.net.Uri>) {
        deletedImages = deletedImages.filterNot { it.uri in uris }
        allImages = allImages.filterNot { it.uri in uris }
        imagesForSorting = imagesForSorting.filterNot { it.uri in uris }
    }

    // A restored system request must finish before we classify its pending journal.
    // mediaAccessRevision（C4/T07）：回前台权限级别变化或带权限回前台时递增——
    // 作为键重启本 effect 取消在途扫描（晚到结果不覆盖新权限态），并按现有
    // 快照契约（loadAllData + projectSnapshot）整表刷新：失去访问权的旧条目
    // 随 MediaStore 重查消失，外部删除/修改与部分选择集变更在返回时更新。
    LaunchedEffect(
        mediaAccessRevision,
        deletionRecoveryRevision,
        deletePermissionInFlight,
        recycleBinLoadRevision,
        isDeletingRecycleBin
    ) {
        if (isDeletingRecycleBin) return@LaunchedEffect
        try {
            if (!deletePermissionInFlight) {
                val recovered = reconcileRecycleBinDeletion(
                    recycleBinManager,
                    resolveVolume = { resolveMediaVolumeName(context, it) },
                    verify = fileOperationManager::verifyDeletionState
                )
                if (recovered.unknown.isNotEmpty()) {
                    Toast.makeText(context, "上次删除尚有无法核对的照片，请检查照片访问权限或存储卷后重试", Toast.LENGTH_LONG).show()
                }
            }
            val loadedData = withContext(Dispatchers.IO) {
                val loadResult = LocalImageRepository(context).loadAllData()
                val deleted = recycleBinManager.loadRecycleBin()
                // 浏览/推荐由"快照 − 软删除集合"派生，相册计数与封面同步重算。
                val projected = projectSnapshot(loadResult, deleted)
                albumManager.initialize(preloadedBuckets = projected.systemBuckets)
                Pair(projected, deleted)
            }
            allImages = loadedData.first.allImages
            imagesForSorting = loadedData.first.imagesExcludingTabula
            systemBuckets = loadedData.first.systemBuckets
            deletedImages = loadedData.second
            recycleBinLoadError = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recycleBinLoadError = true
            Log.e("TabulaApp", "Could not load/reconcile recycle bin", e)
            Toast.makeText(context, "回收站记录未能读取或核对，原记录已保留，请重试", Toast.LENGTH_LONG).show()
        } finally {
            isLoading = false
        }
    }

    if (recycleBinLoadError) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { recycleBinLoadError = false },
            title = { Text("回收站记录未能读取") },
            text = { Text("无法读取或保存回收站记录。原有记录未清空，请重试。") },
            confirmButton = {
                Button(onClick = { recycleBinLoadError = false; recycleBinLoadRevision++ }) { Text("重试") }
            }
        )
    }

    fun deleteRecycleBinImages(images: List<ImageFile>) {
        if (isDeletingRecycleBin || deletePermissionInFlight || images.isEmpty()) return
        val snapshot = images.toList()
        isDeletingRecycleBin = true
        scope.launch {
            try {
                var recoveredCount = 0
                val summary = deleteRecycleBinSelection(
                    snapshot.map { it.uri }, recycleBinManager,
                    request = fileOperationManager::deleteImages,
                    resolveVolume = { resolveMediaVolumeName(context, it) },
                    permission = { result ->
                        suspendCancellableCoroutine { continuation ->
                            onRequestDeletePermission(result.intentSender) { allowed ->
                                if (continuation.isActive) continuation.resume(allowed)
                            }
                        }
                    },
                    verify = fileOperationManager::verifyDeletionState,
                    onRecovered = { recovered ->
                        recoveredCount = recovered.deleted.size
                        removeConfirmedFromState(recovered.deleted)
                        if (recovered.unknown.isNotEmpty()) {
                            Toast.makeText(context, "上次删除尚有无法核对的照片，已保留；其余照片将继续处理", Toast.LENGTH_LONG).show()
                        }
                    },
                    onDeleted = { removeConfirmedFromState(it) }
                )
                val processed = summary.deleted.size + recoveredCount
                val message = when {
                    summary.cancelled -> "已取消，已处理 ${processed} 张，剩余 ${summary.remaining.size} 张"
                    summary.error != null -> "已处理 ${processed} 张，剩余 ${summary.remaining.size} 张；${summary.error}"
                    summary.remaining.isEmpty() -> "已处理 ${processed} 张照片"
                    else -> "已处理 ${processed} 张，${summary.remaining.size} 张未能删除，请重试"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e // Recovery queries the journal instead of re-deleting.
            } catch (e: RecycleBinCommitException) {
                allImages = allImages.filterNot { it.uri in e.physicallyDeleted }
                imagesForSorting = imagesForSorting.filterNot { it.uri in e.physicallyDeleted }
                Log.e("TabulaApp", "Deletion records require reconciliation", e)
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("TabulaApp", "Recycle bin deletion failed", e)
                Toast.makeText(context, "未能完成删除或保存记录，已停止；保留的记录将于重试时核对", Toast.LENGTH_LONG).show()
            } finally {
                isDeletingRecycleBin = false
            }
        }
    }

    /**
     * T02c：恢复（单条/批量共用）——单次对账、单次核验、逐项决策、共享记录事务。
     * UNKNOWN 保留在回收站，MISSING 清除死记录且不入图库，仅 PRESENT 恢复进图库。
     */
    fun restoreImagesInOneTransaction(images: List<ImageFile>) {
        if (images.isEmpty() || isDeletingRecycleBin || deletePermissionInFlight) return
        scope.launch {
            try {
                val recovered = reconcileRecycleBinDeletion(
                    recycleBinManager,
                    resolveVolume = { resolveMediaVolumeName(context, it) },
                    verify = fileOperationManager::verifyDeletionState
                )
                removeConfirmedFromState(recovered.deleted)
                val candidates = images.filter { it.uri !in recovered.unknown && it.uri !in recovered.deleted }
                // 升级兼容：聚合 URI 无法唯一确认身份的候选（跨卷同 ID 混存）
                // 归入保留（UNKNOWN 语义），不参与缺失裁决。
                val (confirmedUris, unconfirmedUris) = partitionByConfirmedIdentity(
                    candidates.map { it.uri }, candidates
                ) { resolveMediaVolumeName(context, it) }
                val confirmedSet = confirmedUris.toSet()
                // 未确认身份的候选不参与核验与裁决：保留在回收站（UNKNOWN 语义）。
                val verifiable = candidates.filter { it.uri in confirmedSet }
                val verification = withContext(Dispatchers.IO) {
                    fileOperationManager.verifyDeletionState(verifiable.map { it.uri })
                }
                val restorable = mutableListOf<ImageFile>()
                val missing = mutableListOf<ImageFile>()
                var keptUnknown = images.count { it.uri in recovered.unknown } + unconfirmedUris.size
                candidates.forEach { image ->
                    if (image.uri !in confirmedSet) return@forEach
                    when (decideRestoreAction(image.uri, verification)) {
                        RestoreDecision.PROCEED_RESTORE -> restorable += image
                        RestoreDecision.DROP_MISSING -> missing += image
                        RestoreDecision.KEEP_UNKNOWN -> keptUnknown++
                    }
                }
                // 单一记录事务：只移除核验通过的条目；未知项原样保留。
                if (restorable.isNotEmpty()) recycleBinManager.removeAllFromRecycleBin(restorable)
                if (missing.isNotEmpty()) recycleBinManager.removeAllFromRecycleBin(missing, permanentlyRemoved = true)
                // T03：内存态与持久层同口径——按媒体身份移除/回插，跨卷同数值
                // ID 记录互不影响。
                val removed = restorable + missing
                deletedImages =
                    deletedImages.filterNot { row -> removed.any { it.matchesSameMediaIdentity(row) } }
                restorable.forEach { image ->
                    if (allImages.none { it.matchesSameMediaIdentity(image) }) allImages = allImages + image
                    val isInTabulaAlbum = albums.any { album ->
                        album.systemAlbumPath?.trimEnd('/')?.substringAfterLast('/') == image.bucketDisplayName
                    }
                    if (!isInTabulaAlbum && imagesForSorting.none { it.matchesSameMediaIdentity(image) }) {
                        imagesForSorting = imagesForSorting + image
                    }
                }
                // 恢复改变软删除集合，推荐派生集合同步失效（与移除路径对称）。
                if (restorable.isNotEmpty()) recommendationEngine.invalidateSimilarGroupCache()
                // 重新查询媒体与持久回收站，统一刷新照片、相册数量和封面。
                if (restorable.isNotEmpty()) recycleBinLoadRevision++
                val message = buildString {
                    if (restorable.isNotEmpty()) append("已恢复 ${restorable.size} 张")
                    if (missing.isNotEmpty()) append(if (isEmpty()) "${missing.size} 张已不在媒体库" else "，${missing.size} 张已不在媒体库")
                    if (keptUnknown > 0) append(if (isEmpty()) "${keptUnknown} 张状态未核对已保留" else "，${keptUnknown} 张状态未核对已保留")
                }
                if (images.size > 1 && message.isNotEmpty()) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } else if (missing.isNotEmpty()) {
                    Toast.makeText(context, "照片已不在媒体库中，已清除对应回收站记录", Toast.LENGTH_LONG).show()
                } else if (keptUnknown > 0) {
                    Toast.makeText(context, "照片状态尚未核对，暂不能恢复，请检查照片访问权限或存储卷后重试", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TabulaApp", "Could not restore recycle bin record", e)
                Toast.makeText(context, "未能保存恢复记录，照片仍保留在回收站", Toast.LENGTH_LONG).show()
            }
        }
    }


    /**
     * Batch delete
     */
    fun performDeleteBatch(images: List<ImageFile>, onResult: (BatchDeleteSummary<android.net.Uri>) -> Unit) {
        if (images.isEmpty()) {
            onResult(BatchDeleteSummary(emptySet(), emptySet(), false, null))
            return
        }
        
        scope.launch {
            // C2/T04：详情删除入口接入串行按项批处理（分批/取消不请求后续批/部分成功/UNKNOWN 隔离）。
            val summary = deleteInBatches(
                items = images.map { it.uri },
                deleteBatch = { batch ->
                    deleteMediaUrisBatch(
                        batch,
                        request = fileOperationManager::deleteImages,
                        permission = { result ->
                            suspendCancellableCoroutine { continuation ->
                                onRequestDeletePermission(result.intentSender) { allowed ->
                                    if (continuation.isActive) continuation.resume(allowed)
                                }
                            }
                        },
                        verify = fileOperationManager::verifyDeletionState
                    )
                },
                onDeleted = { confirmed ->
                    withContext(NonCancellable) {
                        val keys = confirmed.map { it.toString() }.toSet()
                        allImages = allImages.filter { it.uri.toString() !in keys }
                        imagesForSorting = imagesForSorting.filter { it.uri.toString() !in keys }
                    }
                }
            )
            val deletedUris = summary.deleted.map { it.toString() }.toSet()
            Log.d("TabulaApp", "Deleted batch: ${deletedUris.size}/${images.size}")
            onResult(summary)
        }
    }
    
    /**
     * 批量删除原图（清理功能）
     * 
     * @param uris 待删除的 URI 列表
     * @param onResult 回调，返回成功删除的 URI 列表
     */
    fun performDeleteUris(uris: List<android.net.Uri>, onResult: (BatchDeleteSummary<android.net.Uri>) -> Unit) {
        if (uris.isEmpty()) {
            onResult(BatchDeleteSummary(emptySet(), emptySet(), false, null))
            return
        }
        
        scope.launch {
            // C2/T04：原图清理入口接入串行按项批处理（分批/取消不请求后续批/部分成功/UNKNOWN 隔离）。
            val summary = deleteInBatches(
                items = uris,
                deleteBatch = { batch ->
                    deleteMediaUrisBatch(
                        batch,
                        request = fileOperationManager::deleteImages,
                        permission = { result ->
                            suspendCancellableCoroutine { continuation ->
                                onRequestDeletePermission(result.intentSender) { allowed ->
                                    if (continuation.isActive) continuation.resume(allowed)
                                }
                            }
                        },
                        verify = fileOperationManager::verifyDeletionState
                    )
                },
                onDeleted = { confirmed ->
                    withContext(NonCancellable) {
                        val keys = confirmed.map { it.toString() }.toSet()
                        albumManager.clearDeletedUris(keys)
                        allImages = allImages.filter { it.uri.toString() !in keys }
                        imagesForSorting = imagesForSorting.filter { it.uri.toString() !in keys }
                        pendingSourceImageUris = pendingSourceImageUris.filter { it !in confirmed }
                    }
                }
            )
            val deletedUris = summary.deleted.map { it.toString() }.toSet()
            Log.d("TabulaApp", "Cleanup deleted: ${deletedUris.size}/${uris.size}")
            onResult(summary)
        }
    }

    // ========== 屏幕内容定义 ==========
    LaunchedEffect(hapticEnabled, hapticStrength) {
        com.tabula.v3.ui.util.HapticFeedback.updateSettings(
            enabled = hapticEnabled,
            strength = hapticStrength
        )
    }

    fun commitPendingArchiveWithFeedback(actionId: String) {
        scope.launch {
            when (albumManager.commitPendingArchive(actionId)) {
                AlbumManager.ArchiveCommitOutcome.COPY_FAILED ->
                    Toast.makeText(context, "归档失败，照片未复制", Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    val deckContent: @Composable () -> Unit = {
        DeckScreen(
            // 使用 imagesForSorting（排除已归类到 Tabula 图集的照片）
            // 这样已归类的照片不会再次出现在待整理列表中
            allImages = imagesForSorting,
            allImagesForCleanup = allImages,
            batchSize = currentBatchSize,
            isLoading = isLoading,
            topBarDisplayMode = currentTopBarMode,
            onKeep = {
                preferences.totalReviewedCount++
            },
            onRemove = { image ->
                scope.launch {
                    try {
                        recycleBinManager.addToRecycleBin(image)
                        albumCleanupEngine.recordUserRemovedImage(image)
                        val persisted = recycleBinManager.loadRecycleBin()
                        // 即时状态与刷新后的投影同口径：按媒体身份（卷, ID）过滤，
                        // 跨卷同数值 ID 的其他照片不受本次回收影响。
                        allImages = allImages.filterNot { row -> persisted.any { it.matchesSameMediaIdentity(row) } }
                        imagesForSorting =
                            imagesForSorting.filterNot { row -> persisted.any { it.matchesSameMediaIdentity(row) } }
                        deletedImages = persisted
                        preferences.totalReviewedCount++
                        preferences.totalDeletedCount++
                        recommendationEngine.invalidateSimilarGroupCache()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Could not mark photo for deletion", e)
                        Toast.makeText(context, "未能保存回收站记录，照片仍保留在待整理照片中", Toast.LENGTH_LONG).show()
                    }
                }
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
                     // 先提交之前的 pending action（如果有）
                     // 这样快速连续归档时，前一个会被自动提交
                     albumManager.getLastPendingArchive()?.let {
                         commitPendingArchiveWithFeedback(it.id)
                     }
                     
                     // 加入新的 pending action（不立即复制）
                     val album = albums.find { it.id == albumId }
                     albumManager.queueImageToAlbum(imageId, imageUri, albumId, album?.name ?: albumId)
                 }
            },
            onCreateAlbum = { name, color, emoji ->
                scope.launch {
                    try {
                        albumManager.createAlbum(name, color, emoji)
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Failed to create album: $name", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "创建图集失败: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onCreateAlbumAndClassify = { name, color, emoji, image ->
                scope.launch {
                    try {
                        // 先提交之前的 pending action（如果有）
                        albumManager.getLastPendingArchive()?.let {
                            commitPendingArchiveWithFeedback(it.id)
                        }
                        
                        // 1. 创建图集
                        val newAlbum = albumManager.createAlbum(name, color, emoji)
                        // 2. 将图片加入待归档队列
                        albumManager.queueImageToAlbum(image.id, image.uri.toString(), newAlbum.id, newAlbum.name)
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Failed to create album and classify: $name", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "创建图集失败: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onUndoAlbumAction = {
                scope.launch {
                    // 取消待执行的归档操作（图片还没被复制，所以无需删除）
                    albumManager.cancelLastPendingArchive()
                }
            },
            onCommitArchive = {
                scope.launch {
                    // Snackbar 消失时提交归档，执行真正的复制
                    albumManager.getLastPendingArchive()?.let {
                        commitPendingArchiveWithFeedback(it.id)
                    }
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
                recommendationEngine.getRecommendedBatch(
                    images, size,
                    identityCandidates = allImages + deletedImages
                )
            },
            // 推荐模式刷新触发器（切换算法时刷新批次）
            recommendModeRefreshKey = recommendModeRefreshKey,
            // 从冷却池移除照片的回调（切换算法时移除未浏览的照片；
            // C1/T03-DECKCOOLDOWN：按完整媒体身份键移除）
            onRemoveFromCooldown = { unviewedImages ->
                recommendationEngine.removeFromCooldown(unviewedImages)
            },
            // 刷新按钮回调（系统相册集成后，不需要同步，只需刷新）
            onSyncClick = {
                scope.launch {
                    Toast.makeText(context, "正在刷新图库...", Toast.LENGTH_SHORT).show()
                    try {
                        // 刷新图集列表和图片列表
                        val repository = LocalImageRepository(context)
                        val refreshedData = withContext(Dispatchers.IO) {
                            val loadResult = repository.loadAllData()
                            val projected = projectSnapshot(loadResult, deletedImages)
                            albumManager.refreshAlbumsFromSystem(projected.systemBuckets)
                            projected
                        }
                        systemBuckets = refreshedData.systemBuckets
                        allImages = refreshedData.allImages
                        imagesForSorting = refreshedData.imagesExcludingTabula
                        Toast.makeText(context, "刷新完成", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Refresh failed", e)
                        Toast.makeText(context, "刷新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            enableSwipeHaptics = swipeHapticsEnabled,
            isAdaptiveCardStyle = cardStyleMode == CardStyleMode.ADAPTIVE,
            swipeStyle = swipeStyle,
            tagSelectionMode = tagSelectionMode,
            tagsPerRow = tagsPerRow,
            tagSwitchSpeed = tagSwitchSpeed,
            isAlbumMode = isAlbumMode,
            onModeChange = { isAlbumMode = it },
            // 显示/隐藏 隐藏的图集
            showHiddenAlbums = showHiddenAlbums,
            onToggleShowHidden = { showHiddenAlbums = !showHiddenAlbums },
            // 图集操作回调
            onHideAlbum = { album ->
                scope.launch {
                    albumManager.hideAlbum(album.id)
                    Toast.makeText(context, "相册已隐藏", Toast.LENGTH_SHORT).show()
                }
            },
            onExcludeAlbum = { album, excluded ->
                scope.launch {
                    albumManager.setAlbumExcludedFromRecommend(album.id, excluded)
                    val message = if (excluded) "已屏蔽，不再推荐此图集的照片" else "已取消屏蔽"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            isAlbumExcluded = { album ->
                albumManager.isAlbumExcludedFromRecommend(album.id)
            },
            onBatchRemainingChange = { remaining ->
                // 更新流体云的批次剩余数量
                (context as? MainActivity)?.updateBatchRemaining(remaining, fluidCloudEnabled)
            },
            // 快捷操作按钮
            quickActionEnabled = quickActionEnabled,
            leftHandButtonX = quickActionButtonX,
            leftHandButtonY = quickActionButtonY,
            onQuickActionPositionChanged = { _, x, y ->
                quickActionButtonX = x
                quickActionButtonY = y
                preferences.quickActionButtonX = x
                preferences.quickActionButtonY = y
            },
            // 图集清理模式
            albumCleanupEngine = albumCleanupEngine,
            albumCleanupInfos = albumCleanupInfos,
            onRefreshAlbumCleanupInfos = { refreshAlbumCleanupInfos() },
            // 图集清理模式状态（提升到 TabulaApp 级别）
            isAlbumCleanupMode = isAlbumCleanupMode,
            onAlbumCleanupModeChange = { isAlbumCleanupMode = it },
            selectedCleanupAlbum = selectedCleanupAlbum,
            onSelectedCleanupAlbumChange = { selectedCleanupAlbum = it },
            onNavigateToOtherAlbums = { otherAlbumsList ->
                otherAlbumsForNavigation = otherAlbumsList
                currentScreen = AppScreen.OTHER_ALBUMS
            },
            // 原图清理
            pendingCleanupCount = pendingSourceImageUris.size + unverifiedSourceImageCount,
            onCleanupSourceImages = {
                scope.launch {
                    // 重新获取最新的待清理原图（用户可能又归类了新照片）
                    val cleanableResult = albumManager.getCleanableUrisForAllAlbums()
                    pendingSourceImageUris = cleanableResult.sourceUris
                    unverifiedSourceImageCount = cleanableResult.unverifiedSourceCount
                    if (cleanableResult.sourceUris.isNotEmpty()) {
                        dialogSourceImageUris = cleanableResult.sourceUris.toList()
                        showSourceImageDeletionDialog = true
                    } else {
                        showSourceImageDeletionDialog = false
                        if (cleanableResult.unverifiedSourceCount > 0) {
                            Toast.makeText(
                                context,
                                "副本尚未验证，已保留 ${cleanableResult.unverifiedSourceCount} 张原图",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "没有需要清理的原图", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    val recycleBinContent: @Composable () -> Unit = {
        RecycleBinScreen(
            deletedImages = deletedImages,
            onRestore = { image ->
                restoreImagesInOneTransaction(listOf(image))
            },
            onRestoreBatch = { images ->
                restoreImagesInOneTransaction(images)
            },
            onPermanentDelete = { image ->
                deleteRecycleBinImages(listOf(image))
            },
            onPermanentDeleteBatch = { images ->
                deleteRecycleBinImages(images)
            },
            onClearAll = {
                deleteRecycleBinImages(deletedImages)
            },
            onNavigateBack = {
                currentScreen = AppScreen.DECK
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume
        )
        if (isDeletingRecycleBin || deletePermissionInFlight) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("正在处理回收站") },
                text = {
                    Column {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("请在系统提示中确认删除。取消系统提示可停止后续删除。")
                    }
                }
            )
        }
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
            onNavigateToSupport = { currentScreen = AppScreen.SUPPORT },
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
            onNavigateToDisplaySettings = { currentScreen = AppScreen.DISPLAY_SETTINGS },
            onNavigateToLab = { currentScreen = AppScreen.LAB },
            onNavigateToReminderSettings = { currentScreen = AppScreen.REMINDER_SETTINGS },
            excludedAlbumCount = excludedAlbumCount,
            hiddenAlbumCount = hiddenAlbumCount,
            onNavigateToHiddenAlbums = { currentScreen = AppScreen.HIDDEN_ALBUMS },
            onNavigateToPrivacyPolicy = { currentScreen = AppScreen.PRIVACY_POLICY },
            onNavigateToTutorial = { currentScreen = AppScreen.TUTORIAL },
            scrollState = settingsScrollState
        )
    }
    
    // 使用教程页面
    val tutorialContent: @Composable () -> Unit = {
        TutorialScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
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
            // 标签收纳设置
            tagSelectionMode = tagSelectionMode,
            tagSwitchSpeed = tagSwitchSpeed,
            tagsPerRow = tagsPerRow,
            onTagSelectionModeChange = { mode ->
                tagSelectionMode = mode
                preferences.tagSelectionMode = mode
            },
            onTagSwitchSpeedChange = { speed ->
                tagSwitchSpeed = speed
                preferences.tagSwitchSpeed = speed
            },
            onTagsPerRowChange = { count ->
                tagsPerRow = count
                preferences.tagsPerRow = count
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }
    
    // 提醒设置页面
    val reminderSettingsContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        val context = LocalContext.current
        ReminderSettingsScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            accentColor = TabulaColors.EyeGold,
            showDeleteConfirm = showDeleteConfirm,
            onShowDeleteConfirmChange = { enabled ->
                showDeleteConfirm = enabled
                preferences.showDeleteConfirm = enabled
            },
            sourceImageDeletionStrategy = sourceImageDeletionStrategy,
            onSourceImageDeletionStrategyChange = { strategy ->
                sourceImageDeletionStrategy = strategy
                preferences.sourceImageDeletionStrategy = strategy
                val message = if (strategy == SourceImageDeletionStrategy.ASK_EVERY_TIME) "归档提醒已开启" else "归档提醒已关闭"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }
    
    // 隐藏与屏蔽图集管理页面
    // 隐藏与屏蔽图集管理页面 (实际上是图集标签管理)
    val hiddenAlbumsContent: @Composable () -> Unit = {
        // 使用 count 作为 key 触发列表刷新，实现响应式更新
        // 获取排除的相册 ID 集合
        val excludedIds = remember(excludedAlbumCount) {
             albumManager.getExcludedAlbumIds()
        }
        
        AlbumTagManagementScreen(
            albums = albums,
            allImages = allImages,
            excludedAlbumIds = excludedIds,
            preferences = preferences,
            onReorderAlbums = { newOrder ->
                scope.launch { albumManager.reorderAlbums(newOrder) }
            },
            onToggleAlbumHidden = { albumId, isHidden ->
                scope.launch {
                     if (isHidden) albumManager.hideAlbum(albumId) else albumManager.unhideAlbum(albumId)
                }
            },
            onToggleAlbumExcluded = { albumId, isExcluded ->
                scope.launch {
                    albumManager.setAlbumExcludedFromRecommend(albumId, isExcluded)
                }
            },
            onResetTagSorting = {
                // 如果需要重置排序，尝试传入空列表或待实现的方法
                // 目前暂时提示用户
                Toast.makeText(context, "排序已重置", Toast.LENGTH_SHORT).show()
                // scope.launch { albumManager.resetAlbumOrder() } // 方法不存在
            },
            onRefreshAlbums = {
                 scope.launch { albumManager.refreshAlbumsFromSystem() }
            },
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }
    
    // 隐私政策页面
    val privacyPolicyContent: @Composable () -> Unit = {
        PrivacyPolicyScreen(
            onNavigateBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    // 显示设置页面
    val displaySettingsContent: @Composable () -> Unit = {
        val isDark = LocalIsDarkTheme.current
        DisplaySettingsScreen(
            backgroundColor = if (isDark) Color.Black else Color(0xFFF2F2F7),
            cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            textColor = if (isDark) Color.White else Color.Black,
            secondaryTextColor = Color(0xFF8E8E93),
            accentColor = TabulaColors.EyeGold,
            batchSize = currentBatchSize,
            onBatchSizeChange = { size ->
                currentBatchSize = size
                preferences.batchSize = size
            },
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            cardStyleMode = cardStyleMode,
            swipeStyle = swipeStyle,
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
            onSwipeStyleChange = { style ->
                swipeStyle = style
                preferences.swipeStyle = style
            },
            // 快捷操作按钮
            quickActionEnabled = quickActionEnabled,
            onQuickActionEnabledChange = { enabled ->
                quickActionEnabled = enabled
                preferences.quickActionButtonEnabled = enabled
            },
            onResetButtonPosition = {
                preferences.resetQuickActionButtonPosition()
                quickActionButtonX = preferences.quickActionButtonX
                quickActionButtonY = preferences.quickActionButtonY
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
        StatisticsScreen(
            onBack = { currentScreen = AppScreen.SETTINGS }
        )
    }

    val albumViewContent: @Composable () -> Unit = {
        AlbumViewScreen(
            albums = albums,
            allImages = allImages,
            getImagesForAlbum = { albumId -> albumManager.getImageIdsForAlbum(albumId) },
            getAlbumImages = { albumId ->
                // C0-DETAIL-EMPTY-RETRY 探针接缝（仅 debug 构建）：强制详情加载失败。
                if (BuildConfig.DEBUG && preferences.debugForceAlbumLoadFailure) {
                    throw java.io.IOException("probe: forced album load failure")
                }
                val images = albumManager.getAlbumDetailImages(albumId)
                if (BuildConfig.DEBUG) {
                    com.tabula.v3.ui.util.DebugAlbumLoadGate.awaitIfArmed(albumId)
                    // Keep the existing cancellable slow-load probe compatible.
                    val delayMs = preferences.debugAlbumLoadDelayMs
                    if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                }
                images
            },
            onCreateAlbum = { name, color, emoji ->
                scope.launch {
                    try {
                        albumManager.createAlbum(name, color, emoji)
                    } catch (e: Exception) {
                        Log.e("TabulaApp", "Failed to create album in AlbumView: $name", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "创建图集失败: ${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onUpdateAlbum = { album ->
                scope.launch { albumManager.updateAlbum(album) }
            },
            onSetCover = { albumName, image ->
                scope.launch { albumManager.setAlbumCover(albumName, image) }
            },
            onDeleteAlbum = { albumId ->
                scope.launch { albumManager.deleteAlbum(albumId) }
            },
            onHideAlbum = { albumId ->
                scope.launch { 
                    albumManager.hideAlbum(albumId)
                    Toast.makeText(context, "相册已隐藏", Toast.LENGTH_SHORT).show()
                }
            },
            onUnhideAlbum = { albumId ->
                scope.launch {
                    albumManager.unhideAlbum(albumId)
                    Toast.makeText(context, "相册已取消隐藏", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteEmptyAlbum = { albumId ->
                scope.launch {
                    val success = albumManager.deleteEmptyAlbum(albumId)
                    if (success) {
                        Toast.makeText(context, "相册已删除", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onExcludeFromRecommend = { albumId, excluded ->
                scope.launch {
                    albumManager.setAlbumExcludedFromRecommend(albumId, excluded)
                    val message = if (excluded) "已屏蔽，不再推荐此图集的照片" else "已取消屏蔽"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            isAlbumExcludedFromRecommend = { albumId ->
                albumManager.isAlbumExcludedFromRecommend(albumId)
            },
            onNavigateBack = { currentScreen = AppScreen.DECK },
            initialAlbumId = selectedAlbumId,
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            playMotionSound = playMotionSound,
            motionSoundVolume = motionSoundVolume,
            onMoveToAlbum = { identityKeys, targetAlbumId ->
                scope.launch {
                    selectedAlbumId?.let { currentAlbumId ->
                        // C1/T03-MOVECOPY-IDENTITY：完整身份选择键直达消费端，
                        // AlbumManager 从当前媒体集合按身份重新解析。
                        val result = albumManager.moveImagesByIdentity(identityKeys, currentAlbumId, targetAlbumId)
                        val message = if (result.retainedCount > 0) {
                            "已移动 ${result.movedCount} 张，${result.retainedCount} 张未移动"
                        } else {
                            "已移动 ${result.movedCount} 张图片"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCopyToAlbum = { identityKeys, targetAlbumId ->
                scope.launch {
                    albumManager.copyImagesByIdentity(identityKeys, targetAlbumId)
                }
            },
            onDeleteImages = { identityKeys ->
                // O5③：按完整媒体身份（卷|ID）解析确认行——不同卷的同数值 ID
                // 只删确认选中的那一行，另一卷同 ID 行不进入删除请求。
                val imagesToDelete = resolveImagesByIdentitySelection(allImages, identityKeys)
                if (imagesToDelete.isNotEmpty()) {
                    // 执行永久删除操作
                    performDeleteBatch(imagesToDelete) { summary ->
                        if (summary.deleted.isNotEmpty()) {
                            scope.launch { albumManager.refreshAlbumsFromSystem() }
                        }
                        Toast.makeText(context, mediaDeletionMessage(summary, "图片"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    val systemAlbumViewContent: @Composable () -> Unit = {
        var bucketImages by remember(selectedBucketName) { mutableStateOf<List<ImageFile>>(emptyList()) }
        LaunchedEffect(selectedBucketName) {
            val bucketName = selectedBucketName
            if (bucketName.isNullOrBlank()) {
                bucketImages = emptyList()
                return@LaunchedEffect
            }

            bucketImages = runCatching {
                withContext(Dispatchers.IO) {
                    LocalImageRepository(context).getImagesByBucket(bucketName)
                }
            }.onFailure { error ->
                Log.e("TabulaApp", "Failed to load system bucket: $bucketName", error)
            }.getOrDefault(emptyList())
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
    
    val otherAlbumsContent: @Composable () -> Unit = {
        OtherAlbumsScreen(
            albums = otherAlbumsForNavigation,
            allImages = allImages,
            onAlbumClick = { album ->
                selectedAlbumId = album.id
                currentScreen = AppScreen.ALBUM_VIEW
            },
            onPinAlbum = { albumId ->
                // TODO: Implement pin album
                Toast.makeText(context, "固定相册: $albumId", Toast.LENGTH_SHORT).show()
            },
            onNavigateBack = { currentScreen = AppScreen.DECK }
        )
    }

    // ========== 引导流程处理（全屏，独立于主导航） ==========
    if (currentScreen == AppScreen.ONBOARDING) {
        OnboardingScreen(
            onSkipToMain = {
                preferences.hasCompletedOnboarding = true
                showOnboarding = false
                // 跳过个性化，直接进入须知页
                currentScreen = AppScreen.DISCLAIMER
            },
            onPersonalize = {
                currentScreen = AppScreen.PERSONALIZATION
            }
        )
        return
    }
    
    if (currentScreen == AppScreen.PERSONALIZATION) {
        PersonalizationScreen(
            preferences = preferences,
            onComplete = {
                preferences.hasCompletedOnboarding = true
                showOnboarding = false
                // 从 preferences 刷新所有个性化设置到 UI 状态
                currentTopBarMode = preferences.topBarDisplayMode
                cardStyleMode = preferences.cardStyleMode
                swipeStyle = preferences.swipeStyle
                showHdrBadges = preferences.showHdrBadges
                showMotionBadges = preferences.showMotionBadges
                quickActionEnabled = preferences.quickActionButtonEnabled
                // 刷新推荐模式
                recommendModeRefreshKey++
                // 个性化完成后进入须知页
                currentScreen = AppScreen.DISCLAIMER
            },
            onSkip = {
                preferences.hasCompletedOnboarding = true
                showOnboarding = false
                // 从 preferences 刷新所有个性化设置到 UI 状态
                currentTopBarMode = preferences.topBarDisplayMode
                cardStyleMode = preferences.cardStyleMode
                swipeStyle = preferences.swipeStyle
                showHdrBadges = preferences.showHdrBadges
                showMotionBadges = preferences.showMotionBadges
                quickActionEnabled = preferences.quickActionButtonEnabled
                // 刷新推荐模式
                recommendModeRefreshKey++
                // 跳过个性化也进入须知页
                currentScreen = AppScreen.DISCLAIMER
            }
        )
        return
    }

    // ========== 使用前须知处理（全屏，个性化设置之后、进入主界面之前） ==========
    if (currentScreen == AppScreen.DISCLAIMER) {
        DisclaimerScreen(
            onAcknowledged = {
                preferences.hasAcknowledgedDisclaimer = true
                showDisclaimer = false
                currentScreen = AppScreen.DECK
            }
        )
        return
    }

    // 根据当前屏幕决定 前景 和 背景
    val (backgroundContent, foregroundContent) = when (currentScreen) {
        AppScreen.DISCLAIMER -> deckContent to null  // 不会到达这里，但需要完整的 when
        AppScreen.ONBOARDING -> deckContent to null  // 不会到达这里
        AppScreen.PERSONALIZATION -> deckContent to null  // 不会到达这里
        AppScreen.DECK -> deckContent to null
        AppScreen.RECYCLE_BIN -> deckContent to recycleBinContent
        AppScreen.SETTINGS -> deckContent to settingsContent
        AppScreen.ABOUT -> settingsContent to aboutContent
        AppScreen.SUPPORT -> settingsContent to supportContent
        AppScreen.STATISTICS -> settingsContent to statisticsContent
        AppScreen.ALBUM_VIEW -> deckContent to albumViewContent
        AppScreen.SYSTEM_ALBUM_VIEW -> deckContent to systemAlbumViewContent
        AppScreen.OTHER_ALBUMS -> deckContent to otherAlbumsContent
        AppScreen.VIBRATION_SOUND -> settingsContent to vibrationSoundContent
        AppScreen.DISPLAY_SETTINGS -> settingsContent to displaySettingsContent
        AppScreen.LAB -> settingsContent to labContent
        AppScreen.REMINDER_SETTINGS -> settingsContent to reminderSettingsContent
        AppScreen.HIDDEN_ALBUMS -> settingsContent to hiddenAlbumsContent
        AppScreen.PRIVACY_POLICY -> settingsContent to privacyPolicyContent
        AppScreen.TUTORIAL -> settingsContent to tutorialContent
    }
    
    // ========== 渲染容器 ==========
    // 液态玻璃效果现在由 AGSL 着色器在各个组件内部处理
    // 不再需要全局的 Backdrop Provider
    Box(modifier = Modifier.fillMaxSize()) {
        PredictiveBackContainer(
            currentScreen = currentScreen,
            onNavigateBack = {
                currentScreen = when (currentScreen) {
                    AppScreen.DISCLAIMER -> AppScreen.DISCLAIMER  // 须知页不支持返回
                    AppScreen.ONBOARDING -> AppScreen.ONBOARDING  // 引导页不支持返回
                    AppScreen.PERSONALIZATION -> AppScreen.ONBOARDING  // 个性化返回到引导页
                    AppScreen.RECYCLE_BIN -> AppScreen.DECK
                    AppScreen.SETTINGS -> AppScreen.DECK
                    AppScreen.ABOUT -> AppScreen.SETTINGS
                    AppScreen.SUPPORT -> AppScreen.SETTINGS
                    AppScreen.STATISTICS -> AppScreen.SETTINGS
                    AppScreen.ALBUM_VIEW -> AppScreen.DECK
                    AppScreen.SYSTEM_ALBUM_VIEW -> AppScreen.DECK
                    AppScreen.OTHER_ALBUMS -> AppScreen.DECK
                    AppScreen.VIBRATION_SOUND -> AppScreen.SETTINGS
                    AppScreen.DISPLAY_SETTINGS -> AppScreen.SETTINGS
                    AppScreen.LAB -> AppScreen.SETTINGS
                    AppScreen.REMINDER_SETTINGS -> AppScreen.SETTINGS
                    AppScreen.HIDDEN_ALBUMS -> AppScreen.SETTINGS
                    AppScreen.PRIVACY_POLICY -> AppScreen.SETTINGS
                    AppScreen.TUTORIAL -> AppScreen.SETTINGS
                    AppScreen.DECK -> AppScreen.DECK
                }
            },
            backgroundContent = backgroundContent,
            foregroundContent = foregroundContent ?: {}
        )
        
        // ========== 原图删除提醒对话框 ==========
        if (showSourceImageDeletionDialog) {
            SourceImageDeletionDialog(
                imageCount = dialogSourceImageUris.size,
                onConfirm = {
                    // 只允许删除用户在弹窗中确认的候选；新出现的候选留待下次确认。
                    val confirmedUris = dialogSourceImageUris.toSet()
                    showSourceImageDeletionDialog = false
                    scope.launch {
                        // 仍对候选逐项深度复核，并限制到弹窗确认时的集合。
                        val cleanableResult = albumManager.getReverifiedCleanableUrisForAllAlbums()
                        val urisToDelete = cleanableResult.sourceUris.filter { it in confirmedUris }
                        pendingSourceImageUris = urisToDelete
                        unverifiedSourceImageCount = cleanableResult.unverifiedSourceCount
                        if (urisToDelete.isEmpty()) {
                            if (cleanableResult.unverifiedSourceCount > 0) {
                                Toast.makeText(
                                    context,
                                    "副本尚未验证，已保留 ${cleanableResult.unverifiedSourceCount} 张原图",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "没有需要清理的原图", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        pendingSourceImageUris = urisToDelete
                        Toast.makeText(context, "正在删除 ${urisToDelete.size} 张原图...", Toast.LENGTH_SHORT).show()
                        performDeleteUris(urisToDelete) { summary ->
                            pendingSourceImageUris = urisToDelete.filter { it in summary.remaining }
                            Toast.makeText(context, mediaDeletionMessage(summary, "原图"), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDismiss = {
                    showSourceImageDeletionDialog = false
                },
                isDarkTheme = LocalIsDarkTheme.current
            )
        }
    }
}

/**
 * 原图删除提醒对话框
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SourceImageDeletionDialog(
    imageCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    val containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
    val accentColor = Color(0xFF007AFF)
    val warningColor = Color(0xFFFF9F0A)
    val context = LocalContext.current
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = warningColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "删除原位置的照片",
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "检测到有 $imageCount 张照片已复制到图集，原位置的照片可以删除了。",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(warningColor.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "只会删除已确认存在副本的原图；副本未验证的原图会保留。",
                            color = textColor.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    HapticFeedback.mediumTap(context)
                    onConfirm()
                }
            ) {
                Text(
                    text = "删除原图",
                    color = Color(0xFFFF3B30),  // 红色
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onDismiss()
                }
            ) {
                Text(
                    text = "稍后处理",
                    color = secondaryTextColor
                )
            }
        }
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
