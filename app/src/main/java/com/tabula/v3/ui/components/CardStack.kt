package com.tabula.v3.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.memory.MemoryCache
import com.tabula.v3.BuildConfig
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.SwipeStyle
import com.tabula.v3.di.PreloadingManager
import com.tabula.v3.di.cardMemoryCacheKey
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.ownedScaledCopyOrNull
import com.tabula.v3.ui.util.preloadImageFeatures
import com.tabula.v3.ui.util.rememberImageFeatures
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.pow

private const val CARD_STACK_DEBUG_TAG = "CardStackDebug"

private fun debugCardStackLog(message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(CARD_STACK_DEBUG_TAG, message)
    }
}

/**
 * 手势方向判定
 */
private enum class SwipeDirection {
    NONE,
    HORIZONTAL,  // 左右滑 → 洗牌
    UP,          // 上滑 → 删除
    DOWN         // 下滑 → 归类到图集
}

@Composable
private fun rememberImageBadges(
    image: ImageFile?,
    showHdr: Boolean,
    showMotion: Boolean
): List<String> {
    if (image == null) return emptyList()

    val features = rememberImageFeatures(
        image = image,
        enableHdr = showHdr,
        enableMotion = showMotion
    )

    val badges = mutableListOf<String>()
    if (showHdr && features?.isHdr == true) {
        badges.add("HDR")
    }
    if (showMotion && features?.isMotionPhoto == true) {
        badges.add("Live")
    }

    return badges
}

/**
 * 三层卡片堆叠组件 - 无限洗牌手势引擎
 *
 * 核心交互：
 * - 左右滑：洗牌（抽走当前卡插到底部）
 * - 上滑：删除（飞出屏幕）
 * - 点击：打开查看器
 *
 * @param images 完整图片列表
 * @param currentIndex 当前索引
 * @param onIndexChange 索引变化回调
 * @param onRemove 删除回调
 * @param onCardClick 卡片点击回调（传递图片和源位置）
 * @param modifier 外部修饰符
 */
/**
 * 根据图片比例和卡片样式模式计算卡片宽高比
 * 
 * @param imageAspectRatio 图片的宽高比 (width / height)
 * @param isAdaptive 是否为自适应模式
 * @return 卡片的宽高比
 */
private fun calculateCardAspectRatio(imageAspectRatio: Float, isAdaptive: Boolean): Float {
    if (!isAdaptive) return 3f / 4f  // 固定模式：3:4
    
    // 自适应模式：根据图片比例分档
    // 分档原则：卡片比例尽量接近图片比例，减少裁剪
    return when {
        imageAspectRatio < 0.5f -> 9f / 16f    // 超长竖图（手机截屏）→ 9:16 卡片 (0.5625)
        imageAspectRatio < 0.67f -> 2f / 3f    // 长竖图 → 2:3 卡片 (0.67)
        imageAspectRatio < 0.9f -> 3f / 4f     // 标准竖图 → 3:4 卡片 (0.75)
        imageAspectRatio < 1.1f -> 1f          // 接近正方形 → 1:1 卡片 (1.0)
        imageAspectRatio < 1.4f -> 4f / 3f     // 标准横图 → 4:3 卡片 (1.33)
        else -> 16f / 9f                        // 宽横图（横屏拍照）→ 16:9 卡片 (1.78)
    }
}

@Composable
fun SwipeableCardStack(
    images: List<ImageFile>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onRemove: (ImageFile) -> Unit,
    onCardClick: ((ImageFile, SourceRect) -> Unit)? = null,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    enableSwipeHaptics: Boolean = true,
    modifier: Modifier = Modifier,
    cardAspectRatio: Float = 3f / 4f,
    // 卡片样式模式（固定/自适应）
    isAdaptiveCardStyle: Boolean = false,
    // 卡片切换样式（切牌/摸牌）
    swipeStyle: SwipeStyle = SwipeStyle.SHUFFLE,
    // 是否启用下滑归类手势（固定标签点击模式下禁用）
    enableDownSwipeClassify: Boolean = true,
    // 下滑触发弹层回调（LIST_POPUP 模式使用）
    // 当 enableDownSwipeClassify = false 但此回调非空时，下滑会触发此回调
    onDownSwipeTrigger: (() -> Unit)? = null,
    // 下滑归类相关参数
    albums: List<Album> = emptyList(),
    onClassifyToAlbum: ((ImageFile, Album) -> Unit)? = null,
    onCreateNewAlbum: ((ImageFile) -> Unit)? = null,
    // 归类模式状态回调
    onClassifyModeChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    // 标签位置映射（索引 -> 屏幕坐标）
    tagPositions: Map<Int, TagPosition> = emptyMap(),
    // 每行标签数量（用于下滑归类的2D选择）
    tagsPerRow: Int = TAGS_PER_ROW,
    // 标签切换速度（数值越大越灵敏，默认1.0）
    tagSwitchSpeed: Float = 1.0f,
    // 回收站按钮位置（用于上滑删除的Genie动画目标点）
    trashButtonBounds: Rect = Rect.Zero,
    // 滑动起点回调（用于快捷按钮的拇指侧检测）
    onSwipeStart: ((xRatio: Float, yRatio: Float) -> Unit)? = null,
    // 固定标签点击模式：外部触发 Genie 动画的目标索引（非 null 时触发）
    fixedTagTriggerIndex: Int? = null,
    // 固定标签 Genie 动画完成回调
    onFixedTagAnimationComplete: (() -> Unit)? = null,
    genieController: GenieAnimationController,
    shareState: CardShareState? = null
) {
    DisposableEffect(shareState) { onDispose { shareState?.clear() } }
    if (images.isEmpty()) {
        SideEffect { shareState?.clear() }
        return
    }
    
    // 根据切换样式分发到不同的实现
    if (swipeStyle == SwipeStyle.DRAW) {
        DrawModeCardStack(
            images = images,
            currentIndex = currentIndex,
            onIndexChange = onIndexChange,
            onRemove = onRemove,
            onCardClick = onCardClick,
            showHdrBadges = showHdrBadges,
            showMotionBadges = showMotionBadges,
            enableSwipeHaptics = enableSwipeHaptics,
            modifier = modifier,
            cardAspectRatio = cardAspectRatio,
            isAdaptiveCardStyle = isAdaptiveCardStyle,
            enableDownSwipeClassify = enableDownSwipeClassify,
            onDownSwipeTrigger = onDownSwipeTrigger,
            albums = albums,
            onClassifyToAlbum = onClassifyToAlbum,
            onCreateNewAlbum = onCreateNewAlbum,
            onClassifyModeChange = onClassifyModeChange,
            onSelectedIndexChange = onSelectedIndexChange,
            tagPositions = tagPositions,
            tagsPerRow = tagsPerRow,
            tagSwitchSpeed = tagSwitchSpeed,
            trashButtonBounds = trashButtonBounds,
            onSwipeStart = onSwipeStart,
            fixedTagTriggerIndex = fixedTagTriggerIndex,
            onFixedTagAnimationComplete = onFixedTagAnimationComplete,
            genieController = genieController,
            shareState = shareState
        )
        return
    }
    
    // ========== 以下是切牌样式 (SHUFFLE) 的实现 ==========

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    // 屏幕尺寸
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 基础偏移量
    val baseOffsetPx = with(density) { 24.dp.toPx() }

    // 阈值配置
    val swipeThresholdPx = screenWidthPx * 0.25f
    val velocityThreshold = 800f
    val deleteThresholdPx = screenHeightPx * 0.08f  // 上滑删除阈值（与下滑归类接近）
    val classifyThresholdPx = screenHeightPx * 0.05f  // 下滑归类阈值（约40-50px）
    val classifyExitThresholdPx = screenHeightPx * 0.03f  // 退出归类模式的阈值
    
    // 2D 标签选择参数（支持斜向滑动选择任意标签）
    // 切换距离受 tagSwitchSpeed 影响：速度越大，距离越小，切换越灵敏
    val baseDistanceX = with(density) { 16.dp.toPx() }
    val baseDistanceY = with(density) { 20.dp.toPx() }
    val tagSwitchDistanceXPx = baseDistanceX / tagSwitchSpeed.coerceIn(0.5f, 2.0f)
    val tagSwitchDistanceYPx = baseDistanceY / tagSwitchSpeed.coerceIn(0.5f, 2.0f)
    val totalTags = albums.size + 1  // 总标签数（+1 是新建按钮）

    // 动画时长（优化：增加时长让动画更丝滑）
    val shuffleAnimDuration = 180  // 从 120ms 增加到 180ms，更平滑的卡片切换
    val genieAnimDuration = 440  // Genie动画时长（380ms - 更丝滑的吸入效果）

    // ========== 顶层卡片拖拽状态 ==========
    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val dragRotation = remember { Animatable(0f) }
    val dragAlpha = remember { Animatable(1f) }
    val dragScale = remember { Animatable(1f) }  // 新增：缩放动画

    // 手势方向锁定
    var lockedDirection by remember { mutableStateOf(SwipeDirection.NONE) }
    var isDragging by remember { mutableStateOf(false) }
    var hasDragged by remember { mutableStateOf(false) }  // 是否发生过有效拖动
    var accumulatedDragDistance by remember { mutableFloatStateOf(0f) }  // 累计拖动距离
    val minDragDistance = 8f  // 最小拖动距离阈值，小于此值视为点击
    var swipeThresholdHapticTriggered by remember { mutableStateOf(false) }
    var deleteThresholdHapticTriggered by remember { mutableStateOf(false) }
    var classifyThresholdHapticTriggered by remember { mutableStateOf(false) }
    
    // 自定义速度计算（解决 VelocityTracker 快速滑动时 speed rezero 的问题）
    var dragStartTime by remember { mutableStateOf(0L) }
    var dragStartOffsetX by remember { mutableFloatStateOf(0f) }
    var dragStartOffsetY by remember { mutableFloatStateOf(0f) }
    
    // 归类模式状态
    var isClassifyMode by remember { mutableStateOf(false) }
    var selectedAlbumIndex by remember { mutableIntStateOf(0) }
    var classifyStartX by remember { mutableFloatStateOf(0f) }  // 进入归类模式时的X位置
    var classifyStartY by remember { mutableFloatStateOf(0f) }  // 进入归类模式时的Y位置（用于2D选择）
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }  // 上次选中的索引，用于触发振动
    var lastSelectionChangeAt by remember { mutableStateOf(0L) }  // 记录上次切换标签的时间
    val latestTagPositions by rememberUpdatedState(tagPositions)
    
    // 性能优化：缓存 2D 选择的计算参数，避免每帧重复计算
    var lastCalculatedIndex by remember { mutableIntStateOf(-1) }  // 缓存上次计算的索引
    var lastIndexUpdateTime by remember { mutableStateOf(0L) }  // 上次索引更新时间（节流用）
    val indexUpdateThrottleMs = 16L  // 索引更新节流间隔（约60fps）

    // ========== Genie动画状态 ==========
    
    // ========== Bitmap 预加载状态 ==========
    // 在下滑进入归类模式时预加载，而不是松手时才加载
    var preloadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloading by remember { mutableStateOf(false) }
    // 追踪预加载任务，用于取消
    var preloadGenieJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 记录开始预加载时的图片 ID，用于验证
    var preloadingImageId by remember { mutableStateOf<Long?>(null) }
    
    // 上滑删除的 Bitmap 预加载状态
    var preloadedDeleteBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloadingDelete by remember { mutableStateOf(false) }
    var preloadDeleteJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var preloadingDeleteImageId by remember { mutableStateOf<Long?>(null) }
    
    // 组件销毁时清理预加载的 bitmap 和取消任务
    DisposableEffect(Unit) {
        onDispose {
            // 取消正在进行的预加载任务
            preloadGenieJob?.cancel()
            preloadDeleteJob?.cancel()
            
            // 安全回收 bitmap，检查是否已回收
            preloadedBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            preloadedBitmap = null
            preloadedDeleteBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            preloadedDeleteBitmap = null
        }
    }
    
    // ========== 图片预加载 ==========
    // 预加载当前索引前后的图片，加快卡片显示速度
    // 优化：减少预加载范围（从 3 降到 1），减少内存压力
    val preloadingManager = remember { PreloadingManager(context, preloadRange = 1) }
    DisposableEffect(Unit) { onDispose { preloadingManager.dispose() } }
    
    LaunchedEffect(currentIndex, images) {
        // 预加载当前索引周围的图片
        // 优化：使用更小的尺寸（720x960）以匹配优化后的 ImageCard
        preloadingManager.preloadAround(
            images = images,
            currentIndex = currentIndex,
            cardSize = IntSize(720, 960)  // 从 1080x1440 降到 720x960
        )
    }

    // ========== 背景卡片呼吸感响应 ==========
    var breathScale by remember { mutableFloatStateOf(0f) }

    // ========== 过渡动画状态 ==========
    var isTransitioning by remember { mutableStateOf(false) }
    var pendingIndexChange by remember { mutableIntStateOf(0) }

    // ========== 卡片位置记录（用于容器变换）==========
    var topCardBounds by remember { mutableStateOf(Rect.Zero) }
    
    // ========== Box容器位置记录（用于坐标转换）==========
    var containerBounds by remember { mutableStateOf(Rect.Zero) }

    // 获取三张卡的数据（不使用取模，避免首尾卡片重复显示）
    val currentImage = images.getOrNull(currentIndex)
    val nextImage = images.getOrNull(currentIndex + 1)
    val prevImage = images.getOrNull(currentIndex - 1)

    // 检查边界
    val hasNext = currentIndex < images.lastIndex
    val hasPrev = currentIndex > 0
    
    // 计算实际卡片比例（自适应模式下根据当前图片比例计算，三层卡片统一使用）
    // 当图片没有有效尺寸信息时，即使在自适应模式下也使用固定比例
    val actualCardAspectRatio = remember(currentImage?.id, isAdaptiveCardStyle) {
        if (isAdaptiveCardStyle && currentImage != null && currentImage.hasDimensionInfo) {
            calculateCardAspectRatio(currentImage.aspectRatio, true)
        } else {
            cardAspectRatio
        }
    }

    /**
     * 重置拖拽状态
     * 
     * 使用 coroutineScope 确保所有动画完成后再继续执行后续逻辑，
     * 避免状态不一致问题
     */
    suspend fun resetDragState() = coroutineScope {
        // 并行执行所有动画，coroutineScope 会等待全部完成
        // 使用更丝滑的弹簧参数：适中的阻尼比和较低的刚度
        val smoothSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,  // 0.75，微弹跳更有活力
            stiffness = Spring.StiffnessLow  // 200，更平滑的过渡
        )
        launch { dragOffsetX.animateTo(0f, smoothSpring) }
        launch { dragOffsetY.animateTo(0f, smoothSpring) }
        launch { dragRotation.animateTo(0f, smoothSpring) }
        launch { dragAlpha.animateTo(1f, smoothSpring) }
        launch { dragScale.animateTo(1f, smoothSpring) }
        
        // 动画完成后重置状态
        lockedDirection = SwipeDirection.NONE
        breathScale = 0f
        swipeThresholdHapticTriggered = false
        deleteThresholdHapticTriggered = false
        classifyThresholdHapticTriggered = false
        
        // 重置归类模式
        // 默认选中第一个相册（索引1，因为索引0是新建按钮）
        isClassifyMode = false
        selectedAlbumIndex = if (albums.isNotEmpty()) 1 else 0
        lastSelectionChangeAt = SystemClock.uptimeMillis()
        classifyStartX = 0f
        classifyStartY = 0f
        lastSelectedIndex = -1
        lastCalculatedIndex = -1  // 重置缓存的计算索引
        lastIndexUpdateTime = 0L  // 重置节流时间
        onClassifyModeChange?.invoke(false)
        
        // 先取消正在进行的预加载任务，再清理 bitmap
        preloadGenieJob?.cancel()
        preloadGenieJob = null
        preloadDeleteJob?.cancel()
        preloadDeleteJob = null
        
        // 清理预加载的 bitmap（检查是否已回收，避免重复回收）
        preloadedBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        preloadedBitmap = null
        isPreloading = false
        preloadingImageId = null
        
        // 清理上滑删除的预加载 bitmap
        preloadedDeleteBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        preloadedDeleteBitmap = null
        isPreloadingDelete = false
        preloadingDeleteImageId = null
    }
    /**
     * 预加载 Genie 动画所需的 Bitmap
     * 
     * 改进：使用 Job 追踪和图片 ID 验证，避免竞态条件
     */
    fun preloadBitmapForGenie() {
        if (isPreloading || preloadedBitmap != null) return
        val currentImg = currentImage ?: return
        
        // 记录开始预加载时的图片 ID
        val targetImageId = currentImg.id
        preloadingImageId = targetImageId
        isPreloading = true
        
        // 取消之前的预加载任务（如果有）
        preloadGenieJob?.cancel()
        
        // 使用 LAZY 启动模式，避免在 drag 事件中同步启动协程的开销
        preloadGenieJob = scope.launch(Dispatchers.IO, start = CoroutineStart.DEFAULT) {
            try {
                var bitmap: Bitmap? = null
                
                // 方案1：尝试从 Coil 内存缓存获取（在 IO 线程安全访问）
                val cacheKey = cardMemoryCacheKey(currentImg)
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    coil.memory.MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    // 从缓存获取成功，缩放到 Genie 动画需要的尺寸
                    // 注意：Bitmap.createScaledBitmap 是 CPU 密集型操作，保持在 IO 线程避免影响 UI
                    val maxSize = 250
                    bitmap = ownedScaledCopyOrNull(cachedBitmap, 250)
                }
                
                // 方案2：缓存未命中，使用快速文件加载
                if (bitmap == null) {
                    bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                }
                
                withContext(Dispatchers.Main.immediate) {
                    // 三重检查：方向、图片 ID、协程是否被取消
                    if (lockedDirection == SwipeDirection.DOWN && 
                        preloadingImageId == targetImageId &&
                        coroutineContext.isActive) {
                        preloadedBitmap = bitmap
                    } else {
                        // 条件不满足，释放 bitmap
                        bitmap?.recycle()
                    }
                    isPreloading = false
                    preloadGenieJob = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    isPreloading = false
                    preloadGenieJob = null
                }
            }
        }
    }
    
    /**
     * 预加载上滑删除的 Bitmap（在上滑方向确定后立即调用）
     * 
     * 改进：使用 Job 追踪和图片 ID 验证，避免竞态条件
     */
    fun preloadBitmapForDelete() {
        if (isPreloadingDelete || preloadedDeleteBitmap != null) return
        val currentImg = currentImage ?: return
        
        // 记录开始预加载时的图片 ID
        val targetImageId = currentImg.id
        preloadingDeleteImageId = targetImageId
        isPreloadingDelete = true
        
        // 取消之前的预加载任务（如果有）
        preloadDeleteJob?.cancel()
        
        // 使用 IO 线程执行所有 Bitmap 操作，避免影响 UI 线程
        preloadDeleteJob = scope.launch(Dispatchers.IO) {
            try {
                var bitmap: Bitmap? = null
                
                // 方案1：尝试从 Coil 内存缓存获取
                val cacheKey = cardMemoryCacheKey(currentImg)
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    coil.memory.MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
bitmap = ownedScaledCopyOrNull(cachedBitmap, 250)
                }
                
                // 方案2：缓存未命中，使用快速文件加载
                if (bitmap == null) {
                    bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                }
                
                withContext(Dispatchers.Main.immediate) {
                    // 三重检查：方向、图片 ID、协程是否被取消
                    if (lockedDirection == SwipeDirection.UP && 
                        preloadingDeleteImageId == targetImageId &&
                        coroutineContext.isActive) {
                        preloadedDeleteBitmap = bitmap
                    } else {
                        bitmap?.recycle()
                    }
                    isPreloadingDelete = false
                    preloadDeleteJob = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    isPreloadingDelete = false
                    preloadDeleteJob = null
                }
            }
        }
    }

    /**
     * 执行洗牌动画（插底 + 顶上）
     * 
     * 当滑到最后一张时，使用平滑的淡出过渡，避免回弹感
     * 
     * 使用 try-finally 确保即使发生异常也能正确重置状态，
     * 避免动画状态锁死导致后续滑动失效
     */
    suspend fun executeShuffleAnimation(direction: Int) {
        // 防止快速连续滑动时重复触发动画
        if (isTransitioning) {
            debugCardStackLog(
                "shuffleBlocked idx=$currentIndex direction=$direction pending=$pendingIndexChange"
            )
            return
        }
        isTransitioning = true
        pendingIndexChange = direction

        try {
            // 检查是否滑到最后一张
            val isLastCard = direction > 0 && currentIndex >= images.lastIndex

            if (isLastCard) {
                // 滑动到完成页面：使用平滑的淡出效果，无需显示斜着的卡片
                // 继续跟随手指拖动的方向，但更平滑
                val currentX = dragOffsetX.value
                val targetX = if (currentX < 0) -screenWidthPx * 0.3f else screenWidthPx * 0.3f
                
                // 更短、更平滑的过渡动画
                scope.launch { 
                    dragOffsetX.animateTo(
                        targetX, 
                        tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) 
                }
                scope.launch { 
                    dragAlpha.animateTo(
                        0f, 
                        tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) 
                }
                scope.launch { 
                    dragScale.animateTo(
                        0.95f, 
                        tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) 
                }
                // 保持当前旋转，不额外旋转
                
                kotlinx.coroutines.delay(100)

                // 通知进入完成页面
                onIndexChange(currentIndex + 1)

                // 重置状态
                dragOffsetX.snapTo(0f)
                dragOffsetY.snapTo(0f)
                dragRotation.snapTo(0f)
                dragAlpha.snapTo(1f)
                dragScale.snapTo(1f)
            } else {
                // 正常的洗牌动画
                val targetX = if (direction > 0) -baseOffsetPx else baseOffsetPx
                val targetRotation = if (direction > 0) -8f else 8f
                
                // 使用更丝滑的缓动曲线
                val smoothEasing = androidx.compose.animation.core.FastOutSlowInEasing
                scope.launch { dragOffsetX.animateTo(targetX, tween(shuffleAnimDuration, easing = smoothEasing)) }
                scope.launch { dragOffsetY.animateTo(0f, tween(shuffleAnimDuration, easing = smoothEasing)) }
                scope.launch { dragRotation.animateTo(targetRotation, tween(shuffleAnimDuration, easing = smoothEasing)) }

                kotlinx.coroutines.delay(shuffleAnimDuration.toLong() / 2)

                // 计算新索引
                val newIndex = when {
                    direction > 0 -> currentIndex + 1
                    direction < 0 && hasPrev -> currentIndex - 1
                    else -> currentIndex
                }

                if (newIndex != currentIndex) {
                    onIndexChange(newIndex)
                }

                dragOffsetX.snapTo(0f)
                dragOffsetY.snapTo(0f)
                dragRotation.snapTo(0f)
                dragAlpha.snapTo(1f)
            }
        } finally {
            // 确保无论如何都重置状态，避免动画锁死
            isTransitioning = false
            lockedDirection = SwipeDirection.NONE
            breathScale = 0f
        }
    }

    /**
     * 执行删除动画 - macOS 神奇效果（向上吸入）
     * 
     * 使用 Genie 效果将卡片吸入回收站按钮
     * 效果为"下宽上窄"，目标点在回收站按钮下侧边框的中心点
     * 
     * 使用 try-finally 确保状态正确重置
     */
    suspend fun executeDeleteAnimation(playHaptic: Boolean) {
        val currentImg = currentImage ?: return
        
        try {
            // 触发震动反馈
            if (enableSwipeHaptics && playHaptic) {
                HapticFeedback.heavyTap(context)
            }
            
            // 计算目标位置（回收站按钮中心点）
            // 需要将绝对坐标转换为相对于容器的坐标
            val fallbackTarget = if (containerBounds != Rect.Zero) {
                Offset(
                    x = containerBounds.left + containerBounds.width * 0.8f,
                    y = containerBounds.top + with(density) { 50.dp.toPx() }
                )
            } else {
                Offset.Zero
            }
            val target = computeTrashTargetInRoot(trashButtonBounds, fallbackTarget)
            val targetCenterX = target.x
            val targetCenterY = target.y
            
            // 使用预加载的 Bitmap（在上滑方向锁定时已开始加载）
            var bitmap = preloadedDeleteBitmap
            if (bitmap == null && isPreloadingDelete) {
                // 等待预加载完成（最多 100ms）
                val startWait = SystemClock.uptimeMillis()
                while (bitmap == null && isPreloadingDelete && SystemClock.uptimeMillis() - startWait < 100) {
                    kotlinx.coroutines.delay(10)
                    bitmap = preloadedDeleteBitmap
                }
            }
            
            // 如果预加载失败或超时，快速加载一个
            if (bitmap == null) {
                val maxSize = 300
                val scale = minOf(maxSize / topCardBounds.width.coerceAtLeast(1f), maxSize / topCardBounds.height.coerceAtLeast(1f), 1f)
                val bitmapWidth = (topCardBounds.width * scale).toInt().coerceAtLeast(50)
                val bitmapHeight = (topCardBounds.height * scale).toInt().coerceAtLeast(50)
                
                bitmap = withContext(Dispatchers.IO) {
                    createGenieBitmapFast(context, currentImg.uri, bitmapWidth, bitmapHeight)
                }
            }
            
            // 清除预加载状态
            preloadedDeleteBitmap = null
            isPreloadingDelete = false
            
            // 将卡片的绝对坐标转换为相对于容器的坐标
            // 重要：加上当前的拖动偏移量，让 Genie 动画从实际松手位置开始，而非卡片原始位置
            val currentDragX = dragOffsetX.value
            val currentDragY = dragOffsetY.value
            val relativeSourceBounds = Rect(
                left = topCardBounds.left + currentDragX,
                top = topCardBounds.top + currentDragY,
                right = topCardBounds.right + currentDragX,
                bottom = topCardBounds.bottom + currentDragY
            )
            
            if (bitmap != null) {
                // 隐藏原始卡片
                dragAlpha.snapTo(0f)
                
                // 启动Genie网格变形动画（向上吸入，下宽上窄）
                genieController.startAnimation(
                    bitmap = bitmap,
                    sourceBounds = relativeSourceBounds,
                    targetX = targetCenterX,
                    targetY = targetCenterY,
                    screenHeight = screenHeightPx,
                    direction = GenieDirection.UP,  // 向上吸入
                    durationMs = genieAnimDuration,
                    onComplete = {
                        // 执行删除回调
                        onRemove(currentImg)
                    }
                )
            } else {
                // 如果加载Bitmap失败，使用简单的缩放淡出动画作为fallback
                val animDuration = 400
                val easeInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
                
                val finalOffsetX = targetCenterX - topCardBounds.center.x
                val finalOffsetY = targetCenterY - topCardBounds.center.y
                
                scope.launch { dragOffsetX.animateTo(finalOffsetX, tween(animDuration, easing = easeInOut)) }
                scope.launch { dragOffsetY.animateTo(finalOffsetY, tween(animDuration, easing = easeInOut)) }
                scope.launch { dragScale.animateTo(0.05f, tween(animDuration, easing = easeInOut)) }
                scope.launch { dragAlpha.animateTo(0f, tween(animDuration, easing = easeInOut)) }
                
                kotlinx.coroutines.delay(animDuration.toLong())
                onRemove(currentImg)
            }
        } finally {
            // 确保状态正确重置
            dragOffsetX.snapTo(0f)
            dragOffsetY.snapTo(0f)
            dragRotation.snapTo(0f)
            dragAlpha.snapTo(1f)
            dragScale.snapTo(1f)
            lockedDirection = SwipeDirection.NONE
            breathScale = 0f
        }
    }

    /**
     * 等待并获取选中标签的最新测量位置
     */
    /**
     * 等待并获取选中标签的位置
     * 
     * 优化策略：
     * - 优先返回"新鲜"的位置（在最后一次切换标签之后更新的）
     * - 如果位置有效但不新鲜，最多等待 50ms 后返回
     * - 避免因 isFresh 条件永远不满足而导致的长时间卡顿
     */
    suspend fun resolveTagBounds(
        targetIndex: Int,
        timeoutMs: Long
    ): Rect? {
        val start = SystemClock.uptimeMillis()
        var lastValidRect: Rect? = null
        var foundValidRectAt: Long? = null

        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val tagPosition = latestTagPositions[targetIndex]
            if (tagPosition != null && tagPosition.coordinates.isAttached) {
                val rect = tagPosition.coordinates.boundsInRoot()
                if (rect != Rect.Zero) {
                    val isFresh = tagPosition.updatedAt >= lastSelectionChangeAt
                    // 如果位置新鲜，立即返回
                    if (isFresh) {
                        return rect
                    }
                    // 位置有效但不新鲜，记录下来
                    if (lastValidRect == null) {
                        lastValidRect = rect
                        foundValidRectAt = SystemClock.uptimeMillis()
                    } else {
                        lastValidRect = rect
                    }
                    // 如果已经找到有效位置超过 50ms，直接返回
                    // 避免因 isFresh 条件一直不满足而导致的长时间等待
                    if (foundValidRectAt != null && SystemClock.uptimeMillis() - foundValidRectAt > 50L) {
                        return lastValidRect
                    }
                }
            }
            kotlinx.coroutines.delay(16)
        }

        return lastValidRect
    }

    /**
     * 执行归类动画 - macOS Genie 液体吸入效果
     * 
     * 使用Canvas.drawBitmapMesh实现真正的网格变形效果：
     * 图像底部收缩为一个点，呈现漏斗状并带有S形弯曲
     * 
     * 索引说明：
     * - 索引0：新建图集按钮
     * - 索引1+：对应 albums[index - 1]
     * 
     * 使用 try-finally 确保状态正确重置
     * 
     * @param targetIndex 目标标签索引
     */
    suspend fun executeGenieAnimation(targetIndex: Int) {
        try {
            val currentImg = currentImage
            if (currentImg == null) {
                // 没有图片时直接返回，finally 块会重置状态
                return
            }
            
            // 获取目标相册（索引0是新建按钮，索引1+是相册）
            val isCreateNew = targetIndex == 0
            val targetAlbum = if (!isCreateNew && targetIndex > 0 && targetIndex <= albums.size) {
                albums[targetIndex - 1]
            } else null
            // 触发震动反馈
            if (enableSwipeHaptics) {
                HapticFeedback.heavyTap(context)
            }

            // 获取标签位置，使用较短的超时时间避免卡顿
            // 如果刚切换过标签，稍微等久一点让位置更新
            val now = SystemClock.uptimeMillis()
            val shouldWaitLonger = now - lastSelectionChangeAt < 100L
            val tagBounds = resolveTagBounds(
                targetIndex = targetIndex,
                timeoutMs = if (shouldWaitLonger) 150L else 80L
            )
            
            // 计算目标位置
            // 重要：需要将绝对坐标（boundsInRoot）转换为相对于容器的坐标
            val fallbackTarget = if (containerBounds != Rect.Zero) {
                val tagEstimatedWidth = with(density) { 65.dp.toPx() }
                val tagSpacing = with(density) { 12.dp.toPx() }
                val listPadding = with(density) { 24.dp.toPx() }
                val tagsStartX = containerBounds.left + listPadding
                Offset(
                    x = tagsStartX + targetIndex * (tagEstimatedWidth + tagSpacing) + tagEstimatedWidth / 2f,
                    y = containerBounds.bottom - with(density) { 80.dp.toPx() }
                )
            } else {
                Offset.Zero
            }
            val target = computeTagTargetInRoot(tagBounds, fallbackTarget)
            val targetCenterX = target.x
            val targetCenterY = target.y
            
            // 使用预加载的 Bitmap（在进入归类模式时已开始加载）
            // 如果预加载还没完成，等待一小段时间；如果仍未完成则回退到快速加载
            var bitmap = preloadedBitmap
            if (bitmap == null && isPreloading) {
                // 等待预加载完成（最多 100ms）
                val startWait = SystemClock.uptimeMillis()
                while (bitmap == null && isPreloading && SystemClock.uptimeMillis() - startWait < 100) {
                    kotlinx.coroutines.delay(10)
                    bitmap = preloadedBitmap
                }
            }
            
            // 如果预加载失败或超时，快速加载一个
            if (bitmap == null) {
                val maxSize = 300
                val scale = minOf(maxSize / topCardBounds.width.coerceAtLeast(1f), maxSize / topCardBounds.height.coerceAtLeast(1f), 1f)
                val bitmapWidth = (topCardBounds.width * scale).toInt().coerceAtLeast(50)
                val bitmapHeight = (topCardBounds.height * scale).toInt().coerceAtLeast(50)
                
                bitmap = withContext(Dispatchers.IO) {
                    createGenieBitmapFast(context, currentImg.uri, bitmapWidth, bitmapHeight)
                }
            }
            
            // 清除预加载状态（bitmap 已被使用）
            preloadedBitmap = null
            isPreloading = false
            
            // 将卡片的绝对坐标转换为相对于容器的坐标
            // 重要：加上当前的拖动偏移量，让 Genie 动画从实际松手位置开始，而非卡片原始位置
            val currentDragX = dragOffsetX.value
            val currentDragY = dragOffsetY.value
            val relativeSourceBounds = Rect(
                left = topCardBounds.left + currentDragX,
                top = topCardBounds.top + currentDragY,
                right = topCardBounds.right + currentDragX,
                bottom = topCardBounds.bottom + currentDragY
            )
            
            if (bitmap != null) {
                // 隐藏原始卡片
                dragAlpha.snapTo(0f)
                
                // 启动Genie网格变形动画
                genieController.startAnimation(
                    bitmap = bitmap,
                    sourceBounds = relativeSourceBounds,
                    targetX = targetCenterX,
                    targetY = targetCenterY,
                    screenHeight = screenHeightPx,
                    durationMs = genieAnimDuration,
                    onComplete = {
                        // 执行归类回调
                        if (!isCreateNew && targetAlbum != null) {
                            onClassifyToAlbum?.invoke(currentImg, targetAlbum)
                        } else {
                            onCreateNewAlbum?.invoke(currentImg)
                        }
                    }
                )
            } else {
                // 如果加载Bitmap失败，使用简单的缩放淡出动画作为fallback
                val finalOffsetX = targetCenterX - topCardBounds.center.x
                val finalOffsetY = targetCenterY - topCardBounds.center.y
                
                scope.launch { dragOffsetX.animateTo(finalOffsetX, tween(genieAnimDuration)) }
                scope.launch { dragOffsetY.animateTo(finalOffsetY, tween(genieAnimDuration)) }
                scope.launch { dragScale.animateTo(0.05f, tween(genieAnimDuration)) }
                scope.launch { dragAlpha.animateTo(0f, tween(genieAnimDuration)) }
                
                kotlinx.coroutines.delay(genieAnimDuration.toLong())
                
                if (!isCreateNew && targetAlbum != null) {
                    onClassifyToAlbum?.invoke(currentImg, targetAlbum)
                } else {
                    onCreateNewAlbum?.invoke(currentImg)
                }
            }
        } finally {
            // 确保状态正确重置
            dragOffsetX.snapTo(0f)
            dragOffsetY.snapTo(0f)
            dragRotation.snapTo(0f)
            dragAlpha.snapTo(1f)
            dragScale.snapTo(1f)
            lockedDirection = SwipeDirection.NONE
            breathScale = 0f
            isClassifyMode = false
            selectedAlbumIndex = 0
            lastSelectionChangeAt = SystemClock.uptimeMillis()
            classifyStartX = 0f
            lastSelectedIndex = -1
            onClassifyModeChange?.invoke(false)
        }
    }

    fun resolveSharePhoto(): ImageFile? {
        return if (isDragging || isClassifyMode || isTransitioning || genieController.isAnimating ||
                fixedTagTriggerIndex != null || dragOffsetX.isRunning || dragOffsetY.isRunning ||
                dragScale.isRunning || dragAlpha.isRunning) null else currentImage
    }
    val sharingAvailable = resolveSharePhoto() != null
    SideEffect { shareState?.update(::resolveSharePhoto, sharingAvailable) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .onGloballyPositioned { coordinates ->
                containerBounds = coordinates.boundsInRoot()
            },
        contentAlignment = Alignment.Center
    ) {
        // ========== 底层卡片 (Prev) ==========
        // 只有当有前一张图片时才显示，少量图片时不显示 Placeholder 避免视觉混乱
        if (prevImage != null && hasPrev) {
            key(prevImage.id) {
                ImageCard(
                    imageFile = prevImage,
                    modifier = Modifier
                        .zIndex(0f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = 0.90f + breathScale * 0.01f
                            scaleY = 0.90f + breathScale * 0.01f
                            translationX = -baseOffsetPx
                            rotationZ = -8f
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = rememberImageBadges(prevImage, showHdrBadges, showMotionBadges)
                )
            }
        } else if (images.size >= 3) {
            // 只有当图片数量 >= 3 时才显示 Placeholder，营造堆叠效果
            ImageCardPlaceholder(
                modifier = Modifier
                    .zIndex(0f)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(actualCardAspectRatio)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = 0.90f
                        scaleY = 0.90f
                        translationX = -baseOffsetPx
                        rotationZ = -8f
                    },
                cornerRadius = 16.dp,
                elevation = 4.dp
            )
        }

        // ========== 中层卡片 (Next) ==========
        // 只有当有下一张图片时才显示，少量图片时不显示 Placeholder
        if (nextImage != null && hasNext) {
            key(nextImage.id) {
                ImageCard(
                    imageFile = nextImage,
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = 0.95f + breathScale * 0.01f
                            scaleY = 0.95f + breathScale * 0.01f
                            translationX = baseOffsetPx
                            rotationZ = 8f
                        },
                    cornerRadius = 16.dp,
                    elevation = 6.dp,
                    badges = rememberImageBadges(nextImage, showHdrBadges, showMotionBadges)
                )
            }
        } else if (images.size >= 3) {
            // 只有当图片数量 >= 3 时才显示 Placeholder
            ImageCardPlaceholder(
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(actualCardAspectRatio)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = 0.95f
                        scaleY = 0.95f
                        translationX = baseOffsetPx
                        rotationZ = 8f
                    },
                cornerRadius = 16.dp,
                elevation = 6.dp
            )
        }

        // ========== 顶层卡片 (Current) - 可交互 ==========
        if (currentImage != null) {
            key(currentImage.id) {
                val velocityTracker = remember { VelocityTracker() }

                ImageCard(
                    imageFile = currentImage,
                    modifier = Modifier
                        .zIndex(if (isTransitioning && pendingIndexChange != 0) -1f else 2f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .onGloballyPositioned { coordinates ->
                            topCardBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = dragOffsetX.value
                            translationY = dragOffsetY.value
                            rotationZ = dragRotation.value
                            alpha = dragAlpha.value
                            scaleX = dragScale.value
                            scaleY = dragScale.value
                        }
                        .pointerInput(currentIndex) {
                            detectTapGestures(
                                onTap = {
                                    // 只有没有发生拖动时才触发点击
                                    if (!hasDragged && onCardClick != null) {
                                        val sourceRect = SourceRect(
                                            x = topCardBounds.left,
                                            y = topCardBounds.top,
                                            width = topCardBounds.width,
                                            height = topCardBounds.height,
                                            cornerRadius = 16f
                                        )
                                        onCardClick(currentImage, sourceRect)
                                    }
                                }
                            )
                        }
                        .pointerInput(currentIndex, albums) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    isDragging = true
                                    hasDragged = false
                                    accumulatedDragDistance = 0f  // 重置累计距离
                                    velocityTracker.resetTracking()
                                    swipeThresholdHapticTriggered = false
                                    deleteThresholdHapticTriggered = false
                                    classifyThresholdHapticTriggered = false
                                    
                                    // 记录滑动起始时间和位置（用于自定义速度计算）
                                    dragStartTime = System.currentTimeMillis()
                                    dragStartOffsetX = startOffset.x
                                    dragStartOffsetY = startOffset.y
                                    
                                    // 性能优化：滑动开始时暂停预加载和启用快速检测模式
                                    preloadingManager.pause()
                                    com.tabula.v3.data.repository.ImageFeatureDetector.setFastMode(true)
                                    
                                    // 采集滑动起点（用于拇指侧检测）
                                    onSwipeStart?.invoke(
                                        startOffset.x / size.width.toFloat(),
                                        startOffset.y / size.height.toFloat()
                                    )
                                    debugCardStackLog(
                                        "dragStart shuffle idx=$currentIndex img=${currentImage?.id} " +
                                            "trans=$isTransitioning locked=$lockedDirection hasPrev=$hasPrev hasNext=$hasNext"
                                    )
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    
                                    // 累计拖动距离，超过阈值才标记为有效拖动
                                    accumulatedDragDistance += kotlin.math.sqrt(
                                        dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                    )
                                    if (accumulatedDragDistance > minDragDistance) {
                                        hasDragged = true
                                    }

                                    if (lockedDirection == SwipeDirection.NONE) {
                                        val totalDx = abs(dragOffsetX.value + dragAmount.x)
                                        val totalDy = abs(dragOffsetY.value + dragAmount.y)
                                        val totalDistance = kotlin.math.sqrt(totalDx * totalDx + totalDy * totalDy)

                                        // 改进：需要最小滑动距离才开始判定方向，避免微小抖动误判
                                        val minLockDistance = 15f  // 最小锁定距离
                                        
                                        // 判断滑动方向：使用角度判定，更精确
                                        // 垂直方向：Y 位移 > X 位移 * 1.73（约 60° 角内）
                                        // 水平方向：X 位移 > Y 位移 * 1.73（约 30° 角内）
                                        if (totalDistance > minLockDistance) {
                                            val verticalRatio = 1.73f  // tan(60°) ≈ 1.73
                                            
                                            if (totalDy > totalDx * verticalRatio && dragOffsetY.value + dragAmount.y < 0) {
                                                // 明显向上滑动
                                                lockedDirection = SwipeDirection.UP
                                            } else if (totalDy > totalDx * verticalRatio && 
                                                       dragOffsetY.value + dragAmount.y > 0 && 
                                                       (enableDownSwipeClassify || onDownSwipeTrigger != null)) {
                                                // 明显向下滑动，且启用下滑功能
                                                lockedDirection = SwipeDirection.DOWN
                                            } else if (totalDx > totalDy * 0.58f) {
                                                // X 位移占优势（tan(30°) ≈ 0.58），锁定为水平
                                                lockedDirection = SwipeDirection.HORIZONTAL
                                            }
                                        }
                                    }

                                    // 使用 UNDISPATCHED 避免每帧创建协程的调度开销
                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                val newY = (dragOffsetY.value + dragAmount.y).coerceAtMost(0f)
                                                dragOffsetY.snapTo(newY)
                                                dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x * 0.3f)
                                                
                                                // 尽早开始预加载（方向锁定后立即开始）
                                                if (!isPreloadingDelete && preloadedDeleteBitmap == null) {
                                                    preloadBitmapForDelete()
                                                }
                                                
                                                // 达到删除阈值时触发振动
                                                if (enableSwipeHaptics &&
                                                    !deleteThresholdHapticTriggered &&
                                                    -newY > deleteThresholdPx
                                                ) {
                                                    deleteThresholdHapticTriggered = true
                                                    HapticFeedback.heavyTap(context)
                                                }
                                                
                                                // 取消删除时触发振动（向下滑动回到阈值以下）
                                                if (enableSwipeHaptics &&
                                                    deleteThresholdHapticTriggered &&
                                                    -newY <= deleteThresholdPx
                                                ) {
                                                    deleteThresholdHapticTriggered = false
                                                    HapticFeedback.lightTap(context, throttle = true)
                                                }
                                            }
                                            SwipeDirection.DOWN -> {
                                                // 下滑归类模式
                                                val newY = (dragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                                dragOffsetY.snapTo(newY)
                                                dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x * 0.7f)
                                                
                                                // LIST_POPUP 模式：下滑触发弹层
                                                if (onDownSwipeTrigger != null && !enableDownSwipeClassify) {
                                                    // 达到阈值时触发弹层并重置卡片
                                                    if (newY > classifyThresholdPx && !classifyThresholdHapticTriggered) {
                                                        classifyThresholdHapticTriggered = true
                                                        if (enableSwipeHaptics) {
                                                            HapticFeedback.mediumTap(context)
                                                        }
                                                        // 触发弹层
                                                        onDownSwipeTrigger.invoke()
                                                        // 重置卡片位置（不重置 classifyThresholdHapticTriggered，避免持续触发振动）
                                                        scope.launch {
                                                            lockedDirection = SwipeDirection.NONE
                                                            dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                                        }
                                                        scope.launch {
                                                            dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                                        }
                                                    }
                                                    return@launch
                                                }
                                                
                                                // 尽早开始预加载（方向锁定后立即开始）
                                                // 这样在用户达到归类阈值之前，bitmap 可能已经加载完成
                                                if (!isPreloading && preloadedBitmap == null) {
                                                    preloadBitmapForGenie()
                                                }
                                                
                                                // 进入归类模式（第一阈值）
                                                // 注意：不在这里触发 onSelectedIndexChange，由下面的 2D 选择逻辑统一处理
                                                // 避免快速滑动时连续触发两次回调导致卡顿
                                                val justEnteredClassifyMode = newY > classifyThresholdPx && !isClassifyMode
                                                if (justEnteredClassifyMode) {
                                                    // 立即更新内部状态
                                                    isClassifyMode = true
                                                    classifyStartX = dragOffsetX.value  // 记录进入归类模式时的X位置
                                                    classifyStartY = newY               // 记录进入归类模式时的Y位置
                                                    // 默认选中第一个相册（索引1），因为索引0是新建按钮
                                                    val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                    selectedAlbumIndex = defaultIndex
                                                    lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                    lastSelectedIndex = defaultIndex
                                                    classifyThresholdHapticTriggered = true
                                                    
                                                    // 只触发归类模式变化，不触发索引变化
                                                    // 索引变化由下面的 2D 选择逻辑统一处理
                                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                        onClassifyModeChange?.invoke(true)
                                                        if (enableSwipeHaptics) {
                                                            HapticFeedback.mediumTap(context)
                                                        }
                                                    }
                                                }
                                                
                                                // 允许用户向上拖动取消归类模式
                                                if (isClassifyMode && newY < classifyExitThresholdPx) {
                                                    isClassifyMode = false
                                                    classifyThresholdHapticTriggered = false
                                                    selectedAlbumIndex = 0
                                                    classifyStartY = 0f
                                                    lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                    lastSelectedIndex = -1
                                                    
                                                    // 异步触发外部回调和振动（使用节流的振动）
                                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                        onClassifyModeChange?.invoke(false)
                                                        if (enableSwipeHaptics) {
                                                            HapticFeedback.lightTap(context, throttle = true)
                                                        }
                                                    }
                                                    
                                                    // 清理预加载的 bitmap（检查是否已回收，避免重复回收）
                                                    preloadedBitmap?.let { bitmap ->
                                                        if (!bitmap.isRecycled) {
                                                            bitmap.recycle()
                                                        }
                                                    }
                                                    preloadedBitmap = null
                                                    isPreloading = false
                                                }
                                                
                                                // 归类模式下：2D 自由选择逻辑（性能优化版）
                                                if (isClassifyMode) {
                                                    // 性能优化：使用节流机制，避免每帧都更新索引
                                                    val currentTime = SystemClock.uptimeMillis()
                                                    val shouldCalculate = justEnteredClassifyMode || 
                                                        (currentTime - lastIndexUpdateTime >= indexUpdateThrottleMs)
                                                    
                                                    if (shouldCalculate) {
                                                        // 快速计算新索引（内联计算，避免函数调用开销）
                                                        val relativeX = dragOffsetX.value - classifyStartX
                                                        val relativeY = newY - classifyStartY
                                                        val colOffset = (relativeX / tagSwitchDistanceXPx).toInt()
                                                        val rowOffset = (relativeY / tagSwitchDistanceYPx).toInt()
                                                        
                                                        // 默认起始位置：第一个相册（索引1）
                                                        val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                        val startRow = defaultIndex / tagsPerRow
                                                        val startCol = defaultIndex % tagsPerRow
                                                        
                                                        // 计算新的行列并限制范围
                                                        val totalRows = (totalTags + tagsPerRow - 1) / tagsPerRow
                                                        val newCol = (startCol + colOffset).coerceIn(0, tagsPerRow - 1)
                                                        val newRow = (startRow + rowOffset).coerceIn(0, totalRows - 1)
                                                        val newIndex = (newRow * tagsPerRow + newCol).coerceIn(0, totalTags - 1)
                                                        
                                                        // 只有索引真正变化时才更新状态
                                                        if (newIndex != lastCalculatedIndex || justEnteredClassifyMode) {
                                                            lastCalculatedIndex = newIndex
                                                            lastIndexUpdateTime = currentTime
                                                            
                                                            // 检查是否需要更新外部状态
                                                            if (newIndex != selectedAlbumIndex || justEnteredClassifyMode) {
                                                                val shouldVibrate = enableSwipeHaptics && 
                                                                    newIndex != lastSelectedIndex && 
                                                                    !justEnteredClassifyMode
                                                                selectedAlbumIndex = newIndex
                                                                lastSelectionChangeAt = currentTime
                                                                lastSelectedIndex = newIndex
                                                                
                                                                // 异步触发外部回调和振动（使用节流的振动）
                                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                                    onSelectedIndexChange?.invoke(newIndex)
                                                                    if (shouldVibrate) {
                                                                        HapticFeedback.lightTap(context, throttle = true)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                // 移除下滑缩放效果，避免性能开销
                                                // val scaleProgress = (newY / (screenHeightPx * 0.2f)).coerceIn(0f, 1f)
                                                // dragScale.snapTo(1f - scaleProgress * 0.1f)
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                val newX = dragOffsetX.value + dragAmount.x
                                                dragOffsetX.snapTo(newX)
                                                dragOffsetY.snapTo(dragOffsetY.value + dragAmount.y * 0.2f)
                                                if (enableSwipeHaptics &&
                                                    !swipeThresholdHapticTriggered &&
                                                    abs(newX) > swipeThresholdPx
                                                ) {
                                                    swipeThresholdHapticTriggered = true
                                                    HapticFeedback.mediumTap(context)
                                                }
                                            }
                                            else -> {
                                                dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x)
                                                dragOffsetY.snapTo(dragOffsetY.value + dragAmount.y)
                                            }
                                        }

                                        // 旋转效果（归类模式下减弱）
                                        val rotationFactor = if (lockedDirection == SwipeDirection.DOWN) 0.3f else 1f
                                        val rotation = (dragOffsetX.value / screenWidthPx) * 15f * rotationFactor
                                        dragRotation.snapTo(rotation.coerceIn(-20f, 20f))

                                        breathScale = (abs(dragOffsetX.value) / swipeThresholdPx).coerceIn(0f, 1f)
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val trackerVelocity = velocityTracker.calculateVelocity()
                                    
                                    // 自定义速度计算（解决 VelocityTracker 快速滑动时 speed rezero 的问题）
                                    val elapsedTime = System.currentTimeMillis() - dragStartTime
                                    val customVelocityX = if (elapsedTime > 0) {
                                        (dragOffsetX.value * 1000f) / elapsedTime  // 转换为 px/s
                                    } else 0f
                                    val customVelocityY = if (elapsedTime > 0) {
                                        (dragOffsetY.value * 1000f) / elapsedTime
                                    } else 0f
                                    
                                    // 取 VelocityTracker 和自定义计算的较大值
                                    val velocityX = if (abs(customVelocityX) > abs(trackerVelocity.x)) customVelocityX else trackerVelocity.x
                                    val velocityY = if (abs(customVelocityY) > abs(trackerVelocity.y)) customVelocityY else trackerVelocity.y

                                    debugCardStackLog(
                                        "dragEnd shuffle idx=$currentIndex locked=$lockedDirection " +
                                            "dx=${dragOffsetX.value} dy=${dragOffsetY.value} " +
                                            "vX=$velocityX vY=$velocityY trans=$isTransitioning classify=$isClassifyMode"
                                    )
                                    
                                    // 性能优化：滑动结束时恢复预加载和禁用快速检测模式
                                    preloadingManager.resume()
                                    com.tabula.v3.data.repository.ImageFeatureDetector.setFastMode(false)

                                    scope.launch {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                if (abs(dragOffsetY.value) > deleteThresholdPx ||
                                                    abs(velocityY) > velocityThreshold
                                                ) {
                                                    executeDeleteAnimation(playHaptic = !deleteThresholdHapticTriggered)
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                            SwipeDirection.DOWN -> {
                                                // 下滑归类处理
                                                if (isClassifyMode) {
                                                    // 执行Genie动画，归类到选中的标签
                                                    executeGenieAnimation(selectedAlbumIndex)
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                val triggered = abs(dragOffsetX.value) > swipeThresholdPx ||
                                                        abs(velocityX) > velocityThreshold

                                                if (triggered) {
                                                    // dragOffsetX > 0 表示卡片向右移动（手指向右滑），对应"上一张"
                                                    // dragOffsetX < 0 表示卡片向左移动（手指向左滑），对应"下一张"
                                                    val direction = if (dragOffsetX.value > 0) -1 else 1
                                                    
                                                    // 向前滑(direction > 0)始终允许，即使超出边界（进入完成页）
                                                    // 向后滑(direction < 0)需要有上一张
                                                    if (direction > 0 || (direction < 0 && hasPrev)) {
                                                        if (enableSwipeHaptics && !swipeThresholdHapticTriggered) {
                                                            HapticFeedback.mediumTap(context)
                                                        }
                                                        executeShuffleAnimation(direction)
                                                    } else {
                                                        resetDragState()
                                                    }
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                            else -> {
                                                // 方向未锁定，但仍检查是否有快速滑动手势
                                                // 如果速度够快或位移够大，根据主要方向决定动作
                                                val absVelocityX = abs(velocityX)
                                                val absVelocityY = abs(velocityY)
                                                val absOffsetX = abs(dragOffsetX.value)
                                                val absOffsetY = abs(dragOffsetY.value)
                                                
                                                // 水平方向占优势且达到阈值（位移或速度）
                                                if ((absVelocityX > velocityThreshold || absOffsetX > swipeThresholdPx) &&
                                                    absVelocityX > absVelocityY && absOffsetX > absOffsetY) {
                                                    val direction = if (dragOffsetX.value > 0) -1 else 1
                                                    if (direction > 0 || (direction < 0 && hasPrev)) {
                                                        if (enableSwipeHaptics && !swipeThresholdHapticTriggered) {
                                                            HapticFeedback.mediumTap(context)
                                                        }
                                                        executeShuffleAnimation(direction)
                                                    } else {
                                                        resetDragState()
                                                    }
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                        }
                                        // 重置拖动标记
                                        hasDragged = false
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    hasDragged = false
                                    debugCardStackLog(
                                        "dragCancel shuffle idx=$currentIndex locked=$lockedDirection " +
                                            "dx=${dragOffsetX.value} dy=${dragOffsetY.value} trans=$isTransitioning"
                                    )
                                    scope.launch { resetDragState() }
                                }
                            )
                        },
                    cornerRadius = 16.dp,
                    elevation = 8.dp,
                    badges = rememberImageBadges(currentImage, showHdrBadges, showMotionBadges)
                )
            }
        }
        
        // ========== 固定标签点击模式：外部触发 Genie 动画 ==========
        LaunchedEffect(fixedTagTriggerIndex) {
            if (fixedTagTriggerIndex != null && !genieController.isAnimating) {
                // 预加载 bitmap（如果还没有）
                if (preloadedBitmap == null) {
                    preloadBitmapForGenie()
                    // 等待预加载完成（最多100ms）
                    var waitTime = 0
                    while (isPreloading && waitTime < 100) {
                        kotlinx.coroutines.delay(10)
                        waitTime += 10
                    }
                }
                // 执行 Genie 动画
                executeGenieAnimation(fixedTagTriggerIndex)
                // 通知外部动画完成
                onFixedTagAnimationComplete?.invoke()
            }
        }
        
        // ========== Genie Effect 覆盖层 ==========
    }
}

// ========== 摸牌样式组件 ==========

/**
 * 摸牌样式卡片堆叠组件
 * 
 * 显示映射：
 * - 左卡 = deck[i+1] (下一张，预览露出，不可滑)
 * - 中卡 = deck[i] (当前牌，唯一可滑)
 * - 右卡 = deck[i+2] (下下张，预览露出，不可滑)
 * 
 * 交互：
 * - 右滑发牌：中卡向右飞出屏幕
 * - 左滑收牌：最后发出的牌从右侧飞回
 */
@Composable
private fun DrawModeCardStack(
    images: List<ImageFile>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onRemove: (ImageFile) -> Unit,
    onCardClick: ((ImageFile, SourceRect) -> Unit)? = null,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    enableSwipeHaptics: Boolean = true,
    modifier: Modifier = Modifier,
    cardAspectRatio: Float = 3f / 4f,
    isAdaptiveCardStyle: Boolean = false,
    enableDownSwipeClassify: Boolean = true,
    onDownSwipeTrigger: (() -> Unit)? = null,
    albums: List<Album> = emptyList(),
    onClassifyToAlbum: ((ImageFile, Album) -> Unit)? = null,
    onCreateNewAlbum: ((ImageFile) -> Unit)? = null,
    onClassifyModeChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    tagPositions: Map<Int, TagPosition> = emptyMap(),
    tagsPerRow: Int = TAGS_PER_ROW,
    tagSwitchSpeed: Float = 1.0f,
    trashButtonBounds: Rect = Rect.Zero,
    onSwipeStart: ((xRatio: Float, yRatio: Float) -> Unit)? = null,
    fixedTagTriggerIndex: Int? = null,
    onFixedTagAnimationComplete: (() -> Unit)? = null,
    genieController: GenieAnimationController,
    shareState: CardShareState? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    // 屏幕尺寸
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 摸牌样式的偏移量（三张卡错开显示）
    val sideCardOffsetPx = with(density) { 40.dp.toPx() }

    // 阈值配置 - 更灵敏的触发
    val swipeThresholdPx = screenWidthPx * 0.12f  // 发牌/收牌阈值（降低到12%）
    val velocityThreshold = 350f  // 降低速度阈值，更容易触发
    val deleteThresholdPx = screenHeightPx * 0.08f
    val classifyThresholdPx = screenHeightPx * 0.05f
    val classifyExitThresholdPx = screenHeightPx * 0.03f
    
    // 2D 标签选择参数（受 tagSwitchSpeed 影响）
    val baseDistanceX = with(density) { 16.dp.toPx() }
    val baseDistanceY = with(density) { 20.dp.toPx() }
    val tagSwitchDistanceXPx = baseDistanceX / tagSwitchSpeed.coerceIn(0.5f, 2.0f)
    val tagSwitchDistanceYPx = baseDistanceY / tagSwitchSpeed.coerceIn(0.5f, 2.0f)
    val totalTags = albums.size + 1

    // 动画时长（优化：增加时长让动画更丝滑）
    val drawAnimDuration = 160  // 发牌动画时长（从 120ms 增加到 160ms，更平滑）
    val recallAnimDuration = 180  // 收牌动画时长（从 140ms 增加到 180ms，更平滑）
    val genieAnimDuration = 440  // Genie动画时长（380ms - 更丝滑的吸入效果）

    // ========== 摸牌样式状态 ==========
    var drawState by remember { mutableStateOf(DrawCardState(currentIndex = currentIndex)) }
    
    // 当外部 currentIndex 变化时同步状态（如删除图片后）
    // 重校验按发牌时捕获的完整媒体身份 (卷,ID) 定位（revalidateDealtStack），
    // 不同卷的同数值 ID 行不会互串；缺失/歧义按缺失丢弃，不回退裸 ID 匹配
    LaunchedEffect(currentIndex, images.size) {
        if (drawState.currentIndex != currentIndex) {
            drawState = drawState.copy(
                currentIndex = currentIndex,
                dealtStack = revalidateDealtStack(drawState.dealtStack, images, currentIndex),
                // 清除动画状态
                returningCard = null,
                exitingRightCard = null
            )
        }
    }
    
    // 预加载当前可见卡片的特征（避免 badge 闪烁）
    LaunchedEffect(drawState.currentIndex, images.size, showHdrBadges, showMotionBadges) {
        if (!showHdrBadges && !showMotionBadges) return@LaunchedEffect
        
        val idx = drawState.currentIndex
        withContext(Dispatchers.IO) {
            // 预加载中卡特征
            if (idx in images.indices) {
                preloadImageFeatures(context, images[idx], showHdrBadges, showMotionBadges)
            }
            // 预加载左卡特征
            if (idx + 1 in images.indices) {
                preloadImageFeatures(context, images[idx + 1], showHdrBadges, showMotionBadges)
            }
            // 预加载右卡特征
            if (idx + 2 in images.indices) {
                preloadImageFeatures(context, images[idx + 2], showHdrBadges, showMotionBadges)
            }
        }
    }

    // ========== 中卡拖拽状态 ==========
    val centerDragOffsetX = remember { Animatable(0f) }
    val centerDragOffsetY = remember { Animatable(0f) }
    val centerDragRotation = remember { Animatable(0f) }
    val centerDragAlpha = remember { Animatable(1f) }
    val centerDragScale = remember { Animatable(1f) }
    
    // ========== 收牌动画状态（正在返回的牌）==========
    val returningCardOffsetX = remember { Animatable(screenWidthPx) }  // 从右侧开始
    val returningCardAlpha = remember { Animatable(0f) }
    
    // ========== 侧卡位置动画（用于层级重排）==========
    val leftCardOffsetX = remember { Animatable(-sideCardOffsetPx) }
    val leftCardScale = remember { Animatable(0.88f) }
    val leftCardAlpha = remember { Animatable(1f) }  // 背景卡片不透明
    val leftCardRotation = remember { Animatable(-5f) }  // 左卡旋转，初始 -5 度
    val rightCardOffsetX = remember { Animatable(sideCardOffsetPx) }
    val rightCardScale = remember { Animatable(0.88f) }
    val rightCardAlpha = remember { Animatable(1f) }  // 背景卡片不透明
    
    // ========== 补位动画状态 ==========
    // 发牌时：左卡需要滑入中间成为新中卡
    var isDrawTransitioning by remember { mutableStateOf(false) }
    val incomingCenterOffsetX = remember { Animatable(-sideCardOffsetPx) }  // 从左侧滑入
    val incomingCenterScale = remember { Animatable(0.88f) }
    val incomingCenterAlpha = remember { Animatable(1f) }  // 背景卡片不透明
    
    // 收牌时：当前中卡需要滑到左侧
    var isRecallTransitioning by remember { mutableStateOf(false) }
    val outgoingLeftOffsetX = remember { Animatable(0f) }  // 从中间滑到左侧
    val outgoingLeftScale = remember { Animatable(1f) }
    val outgoingLeftAlpha = remember { Animatable(1f) }

    // 手势方向锁定
    var lockedDirection by remember { mutableStateOf(SwipeDirection.NONE) }
    var isDragging by remember { mutableStateOf(false) }
    var hasDragged by remember { mutableStateOf(false) }  // 是否发生过有效拖动
    var accumulatedDragDistance by remember { mutableFloatStateOf(0f) }  // 累计拖动距离
    val minDragDistance = 8f  // 最小拖动距离阈值
    var swipeThresholdHapticTriggered by remember { mutableStateOf(false) }
    var deleteThresholdHapticTriggered by remember { mutableStateOf(false) }
    var classifyThresholdHapticTriggered by remember { mutableStateOf(false) }
    
    // 自定义速度计算（解决 VelocityTracker 快速滑动时 speed rezero 的问题）
    var dragStartTime by remember { mutableStateOf(0L) }
    var dragStartOffsetX by remember { mutableFloatStateOf(0f) }
    var dragStartOffsetY by remember { mutableFloatStateOf(0f) }
    
    // 归类模式状态
    var isClassifyMode by remember { mutableStateOf(false) }
    var selectedAlbumIndex by remember { mutableIntStateOf(if (albums.isNotEmpty()) 1 else 0) }
    var classifyStartX by remember { mutableFloatStateOf(0f) }
    var classifyStartY by remember { mutableFloatStateOf(0f) }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }
    var lastSelectionChangeAt by remember { mutableStateOf(0L) }
    val latestTagPositions by rememberUpdatedState(tagPositions)
    
    // 性能优化：缓存 2D 选择的计算参数，避免每帧重复计算
    var lastCalculatedIndex by remember { mutableIntStateOf(-1) }
    var lastIndexUpdateTime by remember { mutableStateOf(0L) }
    val indexUpdateThrottleMs = 16L  // 索引更新节流间隔（约60fps）

    // Genie 动画控制器
    
    // Bitmap 预加载状态
    var preloadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloading by remember { mutableStateOf(false) }
    var preloadGenieJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var preloadingImageId by remember { mutableStateOf<Long?>(null) }
    
    var preloadedDeleteBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloadingDelete by remember { mutableStateOf(false) }
    var preloadDeleteJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var preloadingDeleteImageId by remember { mutableStateOf<Long?>(null) }
    
    // 清理预加载任务和 bitmap（检查是否已回收，避免重复回收）
    DisposableEffect(Unit) {
        onDispose {
            // 取消正在进行的预加载任务
            preloadGenieJob?.cancel()
            preloadDeleteJob?.cancel()
            
            preloadedBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            preloadedDeleteBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }
    
    // 图片预加载 - 扩大范围确保流畅
    val preloadingManager = remember { PreloadingManager(context, preloadRange = 5) }
    DisposableEffect(Unit) { onDispose { preloadingManager.dispose() } }
    LaunchedEffect(drawState.currentIndex, images) {
        preloadingManager.preloadAround(
            images = images,
            currentIndex = drawState.currentIndex,
            cardSize = IntSize(1080, 1440)
        )
    }
    
    // 手动预加载函数 - 在动画开始前调用
    fun preloadNextCards(fromIndex: Int) {
        scope.launch {
            // 预加载接下来的几张卡片
            for (offset in 1..4) {
                val index = fromIndex + offset
                if (index < images.size) {
                    preloadingManager.preloadAround(
                        images = images,
                        currentIndex = index,
                        cardSize = IntSize(1080, 1440)
                    )
                }
            }
        }
    }

    // 卡片位置记录
    var centerCardBounds by remember { mutableStateOf(Rect.Zero) }
    var containerBounds by remember { mutableStateOf(Rect.Zero) }

    // 获取三张卡的数据（摸牌样式映射：左=i+1, 中=i, 右=i+2）
    val leftCard = drawState.getLeftCard(images)    // deck[i+1]
    val centerCard = drawState.getCenterCard(images) // deck[i]
    val rightCard = drawState.getRightCard(images)   // deck[i+2]
    
    // 预览返回牌（左滑收牌时显示）
    // C4/T07 任务 B：预览必须随快照替换刷新——dealtStack 与 images 双键
    //（同身份行更新为最新对象时 dealtStack 不变，单键会停留旧版本；实际
    // 收牌在 recall 时现取 images[originalIndex]，预览须与之一致）
    val previewReturningCard = remember(drawState.dealtStack, images) {
        if (drawState.canRecall()) previewReturningCardOf(drawState.dealtStack, images) else null
    }
    
    // 计算卡片比例
    val actualCardAspectRatio = remember(centerCard?.id, isAdaptiveCardStyle) {
        if (isAdaptiveCardStyle && centerCard != null && centerCard.hasDimensionInfo) {
            calculateCardAspectRatio(centerCard.aspectRatio, true)
        } else {
            cardAspectRatio
        }
    }

    /**
     * 重置拖拽状态
     * 
     * 使用 coroutineScope 确保所有动画完成后再继续执行后续逻辑，
     * 避免状态不一致问题
     */
    suspend fun resetDragState() = coroutineScope {
        // 并行执行所有动画，coroutineScope 会等待全部完成
        // 使用更丝滑的弹簧参数：适中的阻尼比和较低的刚度
        val smoothSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,  // 0.75，微弹跳更有活力
            stiffness = Spring.StiffnessLow  // 200，更平滑的过渡
        )
        launch { centerDragOffsetX.animateTo(0f, smoothSpring) }
        launch { centerDragOffsetY.animateTo(0f, smoothSpring) }
        launch { centerDragRotation.animateTo(0f, smoothSpring) }
        launch { centerDragAlpha.animateTo(1f, smoothSpring) }
        launch { centerDragScale.animateTo(1f, smoothSpring) }
        
        // 重置左卡跟手状态（带动画回弹）
        launch { leftCardOffsetX.animateTo(-sideCardOffsetPx, smoothSpring) }
        launch { leftCardScale.animateTo(0.88f, smoothSpring) }
        launch { leftCardAlpha.animateTo(1f, smoothSpring) }  // 保持不透明
        launch { leftCardRotation.animateTo(-5f, smoothSpring) }
        
        // 重置返回牌预览状态
        launch { returningCardOffsetX.animateTo(screenWidthPx, smoothSpring) }
        launch { returningCardAlpha.animateTo(0f, smoothSpring) }
        
        // 动画完成后重置状态
        lockedDirection = SwipeDirection.NONE
        swipeThresholdHapticTriggered = false
        deleteThresholdHapticTriggered = false
        classifyThresholdHapticTriggered = false
        
        // 重置归类模式
        isClassifyMode = false
        selectedAlbumIndex = if (albums.isNotEmpty()) 1 else 0
        lastSelectionChangeAt = SystemClock.uptimeMillis()
        classifyStartX = 0f
        classifyStartY = 0f
        lastSelectedIndex = -1
        lastCalculatedIndex = -1  // 重置缓存的计算索引
        lastIndexUpdateTime = 0L  // 重置节流时间
        onClassifyModeChange?.invoke(false)
        
        // 先取消正在进行的预加载任务，再清理 bitmap
        preloadGenieJob?.cancel()
        preloadGenieJob = null
        preloadDeleteJob?.cancel()
        preloadDeleteJob = null
        
        // 清理预加载（检查是否已回收，避免重复回收）
        preloadedBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        preloadedBitmap = null
        isPreloading = false
        preloadingImageId = null
        
        preloadedDeleteBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        preloadedDeleteBitmap = null
        isPreloadingDelete = false
        preloadingDeleteImageId = null
    }

    // 保存动画期间需要显示的"旧卡片"
    var exitingCenterCard by remember { mutableStateOf<ImageFile?>(null) }
    var transitionLeftCard by remember { mutableStateOf<ImageFile?>(null) }
    
    /**
     * 执行发牌动画（中卡向右飞出，左卡补位到中间）
     * 
     * 优化：先更新状态让新图片提前加载，同时播放旧卡片的退场动画
     * 
     * 使用 try-finally 确保状态正确重置
     */
    suspend fun executeDrawAnimation() {
        if (!drawState.canDraw(images.size)) return
        
        try {
            // 注意：不在这里触发振动，因为 onDrag 中已经触发过了
            
            // 1. 保存当前卡片用于动画（必须在状态更新前）
            exitingCenterCard = centerCard
            transitionLeftCard = leftCard
            
            // 2. 初始化动画状态（从当前跟手位置开始，避免闪烁）
            // centerDragOffsetX 保持当前值，从拖动位置继续动画
            // centerDragAlpha 保持当前值
            // centerDragRotation 保持当前值
            // 左卡过渡动画从当前跟手位置开始
            incomingCenterOffsetX.snapTo(leftCardOffsetX.value)
            incomingCenterScale.snapTo(leftCardScale.value)
            incomingCenterAlpha.snapTo(leftCardAlpha.value)
            
            // 3. 设置过渡标志（必须在状态更新前！避免闪烁）
            isDrawTransitioning = true
            
            // 4. 预加载后续图片和特征（避免 badge 闪烁）
            preloadNextCards(drawState.currentIndex + 1)
            val nextIndex = drawState.currentIndex + 1
            if (nextIndex < images.size) {
                scope.launch(Dispatchers.IO) {
                    // 预加载新中卡的特征
                    preloadImageFeatures(context, images[nextIndex], showHdrBadges, showMotionBadges)
                    // 预加载新左卡的特征
                    if (nextIndex + 1 < images.size) {
                        preloadImageFeatures(context, images[nextIndex + 1], showHdrBadges, showMotionBadges)
                    }
                }
            }
            
            // 5. 更新状态，让新图片开始加载
            val newState = drawCardReducer(drawState, DrawCardAction.Draw, images)
            drawState = newState
            onIndexChange(newState.currentIndex)
            
            // 动画参数 - 更快更流畅
            val animDuration = drawAnimDuration
            val animSpec = tween<Float>(animDuration, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
            val fastSpec = tween<Float>(animDuration * 2 / 3, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
            
            // 并行执行动画
            val targetX = screenWidthPx * 1.05f
            scope.launch { centerDragOffsetX.animateTo(targetX, animSpec) }
            scope.launch { centerDragRotation.animateTo(8f, animSpec) }
            scope.launch { centerDragAlpha.animateTo(0f, fastSpec) }
            
            // 左卡补位动画（滑入中间）
            scope.launch { incomingCenterOffsetX.animateTo(0f, animSpec) }
            scope.launch { incomingCenterScale.animateTo(1f, animSpec) }
            scope.launch { incomingCenterAlpha.animateTo(1f, animSpec) }
            
            kotlinx.coroutines.delay(animDuration.toLong())
        } finally {
            // 确保状态正确重置
            isDrawTransitioning = false
            exitingCenterCard = null
            transitionLeftCard = null
            
            centerDragOffsetX.snapTo(0f)
            centerDragOffsetY.snapTo(0f)
            centerDragRotation.snapTo(0f)
            centerDragAlpha.snapTo(1f)
            centerDragScale.snapTo(1f)
            incomingCenterOffsetX.snapTo(-sideCardOffsetPx)
            incomingCenterScale.snapTo(0.88f)
            incomingCenterAlpha.snapTo(1f)  // 背景卡片不透明
            // 重置左卡跟手状态
            leftCardOffsetX.snapTo(-sideCardOffsetPx)
            leftCardScale.snapTo(0.88f)
            leftCardAlpha.snapTo(1f)  // 保持不透明
            leftCardRotation.snapTo(-5f)
            lockedDirection = SwipeDirection.NONE
            swipeThresholdHapticTriggered = false
        }
    }

    // 收牌动画保存的卡片
    var recallTransitionCenter by remember { mutableStateOf<ImageFile?>(null) }
    var recallTransitionLeft by remember { mutableStateOf<ImageFile?>(null) }
    var recallTransitionRight by remember { mutableStateOf<ImageFile?>(null) }
    var recallReturningCard by remember { mutableStateOf<ImageFile?>(null) }
    
    /**
     * 执行收牌动画（返回牌从右侧飞入，当前中卡滑到左侧）
     * 
     * 使用 try-finally 确保状态正确重置
     */
    suspend fun executeRecallAnimation() {
        if (!drawState.canRecall()) return
        
        try {
            // 注意：不在这里触发振动，因为 onDrag 中已经触发过了
            
            // 1. 保存当前卡片用于动画（必须在状态更新前）
            recallTransitionCenter = centerCard
            recallTransitionLeft = leftCard
            recallTransitionRight = rightCard
            
            // 获取返回的牌
            val returningCardData = drawState.dealtStack.lastOrNull()
            recallReturningCard = returningCardData?.let { images.getOrNull(it.originalIndex) }
            
            // 2. 初始化动画状态（必须在设置标志前）
            outgoingLeftOffsetX.snapTo(0f)
            outgoingLeftScale.snapTo(1f)
            outgoingLeftAlpha.snapTo(1f)
            returningCardOffsetX.snapTo(screenWidthPx * 1.05f)
            returningCardAlpha.snapTo(1f)
            leftCardAlpha.snapTo(1f)  // 新左卡保持不透明
            rightCardAlpha.snapTo(1f)  // 新右卡保持不透明
            
            // 3. 设置过渡标志（必须在状态更新前！避免闪烁）
            isRecallTransitioning = true
            
            // 4. 预加载返回牌的特征（避免 badge 闪烁）
            if (recallReturningCard != null) {
                scope.launch(Dispatchers.IO) {
                    preloadImageFeatures(context, recallReturningCard!!, showHdrBadges, showMotionBadges)
                }
            }
            
            // 5. 更新状态，让新图片开始加载
            val newState = drawCardReducer(drawState, DrawCardAction.Recall, images)
            drawState = newState
            onIndexChange(newState.currentIndex)
            
            // 动画参数 - 更快更流畅
            val animDuration = recallAnimDuration
            val animSpec = tween<Float>(animDuration, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
            val fastSpec = tween<Float>(animDuration / 2)
            
            // 并行执行动画
            scope.launch { returningCardOffsetX.animateTo(0f, animSpec) }
            scope.launch { outgoingLeftOffsetX.animateTo(-sideCardOffsetPx, animSpec) }
            scope.launch { outgoingLeftScale.animateTo(0.88f, animSpec) }
            scope.launch { outgoingLeftAlpha.animateTo(1f, animSpec) }  // 保持不透明
            // 旧左卡快速淡出，避免与移动过来的旧中卡重叠
            scope.launch { leftCardAlpha.animateTo(0f, fastSpec) }
            scope.launch { leftCardOffsetX.animateTo(-sideCardOffsetPx * 1.3f, animSpec) }
            
            // 右卡保持不透明，不需要淡出
            
            kotlinx.coroutines.delay(animDuration.toLong())
            
            // 5. 清除动画状态
            val finalState = drawCardReducer(drawState, DrawCardAction.AnimationComplete, images)
            drawState = finalState
        } finally {
            // 确保状态正确重置
            isRecallTransitioning = false
            recallTransitionCenter = null
            recallTransitionLeft = null
            recallTransitionRight = null
            recallReturningCard = null
            
            centerDragOffsetX.snapTo(0f)
            centerDragOffsetY.snapTo(0f)
            centerDragRotation.snapTo(0f)
            centerDragAlpha.snapTo(1f)
            centerDragScale.snapTo(1f)
            returningCardOffsetX.snapTo(screenWidthPx)
            returningCardAlpha.snapTo(0f)
            outgoingLeftOffsetX.snapTo(0f)
            outgoingLeftScale.snapTo(1f)
            outgoingLeftAlpha.snapTo(1f)
            leftCardOffsetX.snapTo(-sideCardOffsetPx)
            leftCardAlpha.snapTo(1f)  // 保持不透明
            leftCardScale.snapTo(0.88f)
            leftCardRotation.snapTo(-5f)
            rightCardAlpha.snapTo(1f)  // 保持不透明
            lockedDirection = SwipeDirection.NONE
            swipeThresholdHapticTriggered = false
        }
    }
    
    /**
     * 执行最后一张卡片的淡出动画（进入总结页面）
     * 当用户在最后一张卡片上右滑时调用
     * 
     * 使用 try-finally 确保状态正确重置
     */
    suspend fun executeLastCardAnimation() {
        try {
            // 播放淡出动画
            val animDuration = 150
            val animSpec = tween<Float>(animDuration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            
            val currentX = centerDragOffsetX.value
            val targetX = if (currentX > 0) screenWidthPx * 0.3f else -screenWidthPx * 0.3f
            
            scope.launch { centerDragOffsetX.animateTo(targetX, animSpec) }
            scope.launch { centerDragAlpha.animateTo(0f, animSpec) }
            scope.launch { centerDragScale.animateTo(0.95f, animSpec) }
            
            kotlinx.coroutines.delay(100)
            
            // 通知进入总结页面
            onIndexChange(drawState.currentIndex + 1)
        } finally {
            // 确保状态正确重置
            centerDragOffsetX.snapTo(0f)
            centerDragOffsetY.snapTo(0f)
            centerDragRotation.snapTo(0f)
            centerDragAlpha.snapTo(1f)
            centerDragScale.snapTo(1f)
            lockedDirection = SwipeDirection.NONE
            swipeThresholdHapticTriggered = false
        }
    }
    
    /**
     * 预加载 Bitmap（用于 Genie 动画）
     * 
     * 改进：使用 Job 追踪和图片 ID 验证，避免竞态条件
     * 性能优化：全部在 IO 线程执行，避免影响 UI 流畅度
     */
    fun preloadBitmapForGenie() {
        if (isPreloading || preloadedBitmap != null) return
        val currentImg = centerCard ?: return
        
        // 记录开始预加载时的图片 ID
        val targetImageId = currentImg.id
        preloadingImageId = targetImageId
        isPreloading = true
        
        // 取消之前的预加载任务（如果有）
        preloadGenieJob?.cancel()
        
        // 使用 IO 线程执行所有 Bitmap 操作
        preloadGenieJob = scope.launch(Dispatchers.IO) {
            try {
                var bitmap: Bitmap? = null
                val cacheKey = cardMemoryCacheKey(currentImg)
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
bitmap = ownedScaledCopyOrNull(cachedBitmap, 250)
                }
                
                if (bitmap == null) {
                    bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                }
                
                withContext(Dispatchers.Main.immediate) {
                    // 三重检查：方向、图片 ID、协程是否被取消
                    if (lockedDirection == SwipeDirection.DOWN && 
                        preloadingImageId == targetImageId &&
                        coroutineContext.isActive) {
                        preloadedBitmap = bitmap
                    } else {
                        bitmap?.recycle()
                    }
                    isPreloading = false
                    preloadGenieJob = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) { 
                    isPreloading = false 
                    preloadGenieJob = null
                }
            }
        }
    }

    /**
     * 预加载上滑删除的 Bitmap
     * 
     * 改进：使用 Job 追踪和图片 ID 验证，避免竞态条件
     * 性能优化：全部在 IO 线程执行，避免影响 UI 流畅度
     */
    fun preloadBitmapForDelete() {
        if (isPreloadingDelete || preloadedDeleteBitmap != null) return
        val currentImg = centerCard ?: return
        
        // 记录开始预加载时的图片 ID
        val targetImageId = currentImg.id
        preloadingDeleteImageId = targetImageId
        isPreloadingDelete = true
        
        // 取消之前的预加载任务（如果有）
        preloadDeleteJob?.cancel()
        
        // 使用 IO 线程执行所有 Bitmap 操作
        preloadDeleteJob = scope.launch(Dispatchers.IO) {
            try {
                var bitmap: Bitmap? = null
                val cacheKey = cardMemoryCacheKey(currentImg)
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
bitmap = ownedScaledCopyOrNull(cachedBitmap, 250)
                }
                
                if (bitmap == null) {
                    bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                }
                
                withContext(Dispatchers.Main.immediate) {
                    // 三重检查：方向、图片 ID、协程是否被取消
                    if (lockedDirection == SwipeDirection.UP && 
                        preloadingDeleteImageId == targetImageId &&
                        coroutineContext.isActive) {
                        preloadedDeleteBitmap = bitmap
                    } else {
                        bitmap?.recycle()
                    }
                    isPreloadingDelete = false
                    preloadDeleteJob = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) { 
                    isPreloadingDelete = false 
                    preloadDeleteJob = null
                }
            }
        }
    }

    /**
     * 执行删除动画（Genie 效果）
     * 
     * 使用 try-finally 确保状态正确重置
     */
    suspend fun executeDeleteAnimation(playHaptic: Boolean) {
        val currentImg = centerCard ?: return
        
        try {
            if (enableSwipeHaptics && playHaptic) {
                HapticFeedback.heavyTap(context)
            }
            
            val fallbackTarget = if (containerBounds != Rect.Zero) {
                Offset(
                    x = containerBounds.left + containerBounds.width * 0.8f,
                    y = containerBounds.top + with(density) { 50.dp.toPx() }
                )
            } else {
                Offset.Zero
            }
            val target = computeTrashTargetInRoot(trashButtonBounds, fallbackTarget)
            val targetCenterX = target.x
            val targetCenterY = target.y
            
            var bitmap = preloadedDeleteBitmap
            if (bitmap == null && isPreloadingDelete) {
                val startWait = SystemClock.uptimeMillis()
                while (bitmap == null && isPreloadingDelete && SystemClock.uptimeMillis() - startWait < 100) {
                    kotlinx.coroutines.delay(10)
                    bitmap = preloadedDeleteBitmap
                }
            }
            
            if (bitmap == null) {
                val maxSize = 300
                val scale = minOf(maxSize / centerCardBounds.width.coerceAtLeast(1f), maxSize / centerCardBounds.height.coerceAtLeast(1f), 1f)
                val bitmapWidth = (centerCardBounds.width * scale).toInt().coerceAtLeast(50)
                val bitmapHeight = (centerCardBounds.height * scale).toInt().coerceAtLeast(50)
                
                bitmap = withContext(Dispatchers.IO) {
                    createGenieBitmapFast(context, currentImg.uri, bitmapWidth, bitmapHeight)
                }
            }
            
            preloadedDeleteBitmap = null
            isPreloadingDelete = false
            
            // 重要：加上当前的拖动偏移量，让 Genie 动画从实际松手位置开始，而非卡片原始位置
            val currentDragX = centerDragOffsetX.value
            val currentDragY = centerDragOffsetY.value
            val relativeSourceBounds = Rect(
                left = centerCardBounds.left + currentDragX,
                top = centerCardBounds.top + currentDragY,
                right = centerCardBounds.right + currentDragX,
                bottom = centerCardBounds.bottom + currentDragY
            )
            
            if (bitmap != null) {
                centerDragAlpha.snapTo(0f)
                
                genieController.startAnimation(
                    bitmap = bitmap,
                    sourceBounds = relativeSourceBounds,
                    targetX = targetCenterX,
                    targetY = targetCenterY,
                    screenHeight = screenHeightPx,
                    direction = GenieDirection.UP,
                    durationMs = genieAnimDuration,
                    onComplete = {
                        onRemove(currentImg)
                    }
                )
            } else {
                val animDuration = 400
                val easeInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
                
                val finalOffsetX = targetCenterX - centerCardBounds.center.x
                val finalOffsetY = targetCenterY - centerCardBounds.center.y
                
                scope.launch { centerDragOffsetX.animateTo(finalOffsetX, tween(animDuration, easing = easeInOut)) }
                scope.launch { centerDragOffsetY.animateTo(finalOffsetY, tween(animDuration, easing = easeInOut)) }
                scope.launch { centerDragScale.animateTo(0.05f, tween(animDuration, easing = easeInOut)) }
                scope.launch { centerDragAlpha.animateTo(0f, tween(animDuration, easing = easeInOut)) }
                
                kotlinx.coroutines.delay(animDuration.toLong())
                onRemove(currentImg)
            }
        } finally {
            // 确保状态正确重置
            centerDragOffsetX.snapTo(0f)
            centerDragOffsetY.snapTo(0f)
            centerDragRotation.snapTo(0f)
            centerDragAlpha.snapTo(1f)
            centerDragScale.snapTo(1f)
            lockedDirection = SwipeDirection.NONE
        }
    }

    /**
     * 等待并获取选中标签的位置
     * 
     * 优化策略：
     * - 优先返回"新鲜"的位置（在最后一次切换标签之后更新的）
     * - 如果位置有效但不新鲜，最多等待 50ms 后返回
     * - 避免因 isFresh 条件永远不满足而导致的长时间卡顿
     */
    suspend fun resolveTagBounds(targetIndex: Int, timeoutMs: Long): Rect? {
        val start = SystemClock.uptimeMillis()
        var lastValidRect: Rect? = null
        var foundValidRectAt: Long? = null

        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val tagPosition = latestTagPositions[targetIndex]
            if (tagPosition != null && tagPosition.coordinates.isAttached) {
                val rect = tagPosition.coordinates.boundsInRoot()
                if (rect != Rect.Zero) {
                    val isFresh = tagPosition.updatedAt >= lastSelectionChangeAt
                    // 如果位置新鲜，立即返回
                    if (isFresh) {
                        return rect
                    }
                    // 位置有效但不新鲜，记录下来
                    if (lastValidRect == null) {
                        lastValidRect = rect
                        foundValidRectAt = SystemClock.uptimeMillis()
                    } else {
                        lastValidRect = rect
                    }
                    // 如果已经找到有效位置超过 50ms，直接返回
                    // 避免因 isFresh 条件一直不满足而导致的长时间等待
                    if (foundValidRectAt != null && SystemClock.uptimeMillis() - foundValidRectAt > 50L) {
                        return lastValidRect
                    }
                }
            }
            kotlinx.coroutines.delay(16)
        }
        return lastValidRect
    }

    /**
     * 执行归类动画
     * 
     * 使用 try-finally 确保状态正确重置
     */
    suspend fun executeGenieAnimation(targetIndex: Int) {
        try {
            val currentImg = centerCard
            if (currentImg == null) {
                // 没有图片时直接返回，finally 块会重置状态
                return
            }
            
            val isCreateNew = targetIndex == 0
            val targetAlbum = if (!isCreateNew && targetIndex > 0 && targetIndex <= albums.size) {
                albums[targetIndex - 1]
            } else null
            if (enableSwipeHaptics) {
                HapticFeedback.heavyTap(context)
            }

            // 获取标签位置，使用较短的超时时间避免卡顿
            // 如果刚切换过标签，稍微等久一点让位置更新
            val now = SystemClock.uptimeMillis()
            val shouldWaitLonger = now - lastSelectionChangeAt < 100L
            val tagBounds = resolveTagBounds(
                targetIndex = targetIndex,
                timeoutMs = if (shouldWaitLonger) 150L else 80L
            )
            
            val fallbackTarget = if (containerBounds != Rect.Zero) {
                val tagEstimatedWidth = with(density) { 65.dp.toPx() }
                val tagSpacing = with(density) { 12.dp.toPx() }
                val listPadding = with(density) { 24.dp.toPx() }
                val tagsStartX = containerBounds.left + listPadding
                Offset(
                    x = tagsStartX + targetIndex * (tagEstimatedWidth + tagSpacing) + tagEstimatedWidth / 2f,
                    y = containerBounds.bottom - with(density) { 80.dp.toPx() }
                )
            } else {
                Offset.Zero
            }
            val target = computeTagTargetInRoot(tagBounds, fallbackTarget)
            val targetCenterX = target.x
            val targetCenterY = target.y
            
            var bitmap = preloadedBitmap
            if (bitmap == null && isPreloading) {
                val startWait = SystemClock.uptimeMillis()
                while (bitmap == null && isPreloading && SystemClock.uptimeMillis() - startWait < 100) {
                    kotlinx.coroutines.delay(10)
                    bitmap = preloadedBitmap
                }
            }
            
            if (bitmap == null) {
                val maxSize = 300
                val scale = minOf(maxSize / centerCardBounds.width.coerceAtLeast(1f), maxSize / centerCardBounds.height.coerceAtLeast(1f), 1f)
                val bitmapWidth = (centerCardBounds.width * scale).toInt().coerceAtLeast(50)
                val bitmapHeight = (centerCardBounds.height * scale).toInt().coerceAtLeast(50)
                
                bitmap = withContext(Dispatchers.IO) {
                    createGenieBitmapFast(context, currentImg.uri, bitmapWidth, bitmapHeight)
                }
            }
            
            preloadedBitmap = null
            isPreloading = false
            
            // 重要：加上当前的拖动偏移量，让 Genie 动画从实际松手位置开始，而非卡片原始位置
            val currentDragX = centerDragOffsetX.value
            val currentDragY = centerDragOffsetY.value
            val relativeSourceBounds = Rect(
                left = centerCardBounds.left + currentDragX,
                top = centerCardBounds.top + currentDragY,
                right = centerCardBounds.right + currentDragX,
                bottom = centerCardBounds.bottom + currentDragY
            )
            
            if (bitmap != null) {
                centerDragAlpha.snapTo(0f)
                
                genieController.startAnimation(
                    bitmap = bitmap,
                    sourceBounds = relativeSourceBounds,
                    targetX = targetCenterX,
                    targetY = targetCenterY,
                    screenHeight = screenHeightPx,
                    durationMs = genieAnimDuration,
                    onComplete = {
                        if (!isCreateNew && targetAlbum != null) {
                            onClassifyToAlbum?.invoke(currentImg, targetAlbum)
                        } else {
                            onCreateNewAlbum?.invoke(currentImg)
                        }
                    }
                )
            } else {
                val finalOffsetX = targetCenterX - centerCardBounds.center.x
                val finalOffsetY = targetCenterY - centerCardBounds.center.y
                
                scope.launch { centerDragOffsetX.animateTo(finalOffsetX, tween(genieAnimDuration)) }
                scope.launch { centerDragOffsetY.animateTo(finalOffsetY, tween(genieAnimDuration)) }
                scope.launch { centerDragScale.animateTo(0.05f, tween(genieAnimDuration)) }
                scope.launch { centerDragAlpha.animateTo(0f, tween(genieAnimDuration)) }
                
                kotlinx.coroutines.delay(genieAnimDuration.toLong())
                
                if (!isCreateNew && targetAlbum != null) {
                    onClassifyToAlbum?.invoke(currentImg, targetAlbum)
                } else {
                    onCreateNewAlbum?.invoke(currentImg)
                }
            }
        } finally {
            // 确保状态正确重置
            centerDragOffsetX.snapTo(0f)
            centerDragOffsetY.snapTo(0f)
            centerDragRotation.snapTo(0f)
            centerDragAlpha.snapTo(1f)
            centerDragScale.snapTo(1f)
            lockedDirection = SwipeDirection.NONE
            isClassifyMode = false
            selectedAlbumIndex = 0
            lastSelectionChangeAt = SystemClock.uptimeMillis()
            classifyStartX = 0f
            lastSelectedIndex = -1
            onClassifyModeChange?.invoke(false)
        }
    }

    fun resolveSharePhoto(): ImageFile? {
        return if (isDragging || isClassifyMode || isDrawTransitioning || isRecallTransitioning ||
                drawState.returningCard != null || genieController.isAnimating || fixedTagTriggerIndex != null ||
                centerDragOffsetX.isRunning || centerDragOffsetY.isRunning ||
                centerDragScale.isRunning || centerDragAlpha.isRunning) null else centerCard
    }
    val sharingAvailable = resolveSharePhoto() != null
    SideEffect { shareState?.update(::resolveSharePhoto, sharingAvailable) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .onGloballyPositioned { coordinates ->
                containerBounds = coordinates.boundsInRoot()
            },
        contentAlignment = Alignment.Center
    ) {
        // ========== 左卡 (deck[i+1]) - 不可滑，消费 pointer 事件 ==========
        // 发牌过渡时：左卡正在补位到中间，由 incomingCenter 动画控制
        // 收牌过渡时：中卡正在滑到左侧，由 recallTransitionCenter 控制，此时新左卡（旧中卡）不显示
        if (leftCard != null && !isDrawTransitioning && !isRecallTransitioning) {
            key(leftCard.id) {
                ImageCard(
                    imageFile = leftCard,
                    modifier = Modifier
                        .zIndex(0.3f)  // 高于右卡(0f)，确保下一张在下下张上面
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = leftCardScale.value
                            scaleY = leftCardScale.value
                            translationX = leftCardOffsetX.value
                            rotationZ = leftCardRotation.value  // 跟手旋转，从 -5° 到 0°
                            alpha = leftCardAlpha.value
                        }
                        // 消费 pointer 事件，防止穿透到中卡
                        // 性能优化：使用 detectTapGestures 替代无限循环，减少空闲CPU开销
                        .pointerInput(Unit) {
                            detectTapGestures { /* consume tap, do nothing */ }
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = rememberImageBadges(leftCard, showHdrBadges, showMotionBadges)
                )
            }
        } else if (leftCard == null && !isDrawTransitioning && !isRecallTransitioning && images.size >= 3) {
            // 只有当图片数量 >= 3 时才显示 Placeholder，少量图片时不显示避免视觉混乱
            ImageCardPlaceholder(
                modifier = Modifier
                    .zIndex(0.3f)  // 高于右卡(0f)，确保下一张在下下张上面
                    .fillMaxWidth(0.85f)
                    .aspectRatio(actualCardAspectRatio)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = 0.88f
                        scaleY = 0.88f
                        translationX = -sideCardOffsetPx
                        rotationZ = -5f
                        alpha = 0.5f
                    },
                cornerRadius = 16.dp,
                elevation = 4.dp
            )
        }
        
        // ========== 发牌过渡时：旧左卡正在补位到中间 ==========
        if (isDrawTransitioning && transitionLeftCard != null) {
            key("incoming_${transitionLeftCard!!.id}") {
                ImageCard(
                    imageFile = transitionLeftCard!!,
                    modifier = Modifier
                        .zIndex(1.5f)  // 高于左卡，低于飞出的中卡
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = incomingCenterScale.value
                            scaleY = incomingCenterScale.value
                            translationX = incomingCenterOffsetX.value
                            rotationZ = -5f * (1f - (incomingCenterOffsetX.value + sideCardOffsetPx) / sideCardOffsetPx).coerceIn(0f, 1f)
                            alpha = incomingCenterAlpha.value
                        },
                    cornerRadius = 16.dp,
                    elevation = 6.dp,
                    badges = rememberImageBadges(transitionLeftCard!!, showHdrBadges, showMotionBadges)
                )
            }
        }
        
        // ========== 发牌过渡时：旧中卡正在飞出 ==========
        if (isDrawTransitioning && exitingCenterCard != null) {
            key("exiting_center_${exitingCenterCard!!.id}") {
                ImageCard(
                    imageFile = exitingCenterCard!!,
                    modifier = Modifier
                        .zIndex(2f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = centerDragOffsetX.value
                            rotationZ = centerDragRotation.value
                            alpha = centerDragAlpha.value
                        },
                    cornerRadius = 16.dp,
                    elevation = 8.dp,
                    badges = rememberImageBadges(exitingCenterCard!!, showHdrBadges, showMotionBadges)
                )
            }
        }
        
        // ========== 收牌过渡时：旧中卡正在滑到左侧 ==========
        if (isRecallTransitioning && recallTransitionCenter != null) {
            key("outgoing_${recallTransitionCenter!!.id}") {
                ImageCard(
                    imageFile = recallTransitionCenter!!,
                    modifier = Modifier
                        .zIndex(0.5f)  // 低于返回的牌
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = outgoingLeftScale.value
                            scaleY = outgoingLeftScale.value
                            translationX = outgoingLeftOffsetX.value
                            rotationZ = -5f * (-outgoingLeftOffsetX.value / sideCardOffsetPx).coerceIn(0f, 1f)
                            alpha = outgoingLeftAlpha.value
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = rememberImageBadges(recallTransitionCenter!!, showHdrBadges, showMotionBadges)
                )
            }
        }

        // ========== 右卡 (deck[i+2]) - 不可滑，消费 pointer 事件 ==========
        if (rightCard != null && drawState.exitingRightCard != rightCard.id && !isRecallTransitioning) {
            key(rightCard.id) {
                ImageCard(
                    imageFile = rightCard,
                    modifier = Modifier
                        .zIndex(0f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = rightCardScale.value
                            scaleY = rightCardScale.value
                            translationX = rightCardOffsetX.value
                            rotationZ = 5f
                            alpha = rightCardAlpha.value
                        }
                        // 消费 pointer 事件，防止穿透到中卡
                        // 性能优化：使用 detectTapGestures 替代无限循环，减少空闲CPU开销
                        .pointerInput(Unit) {
                            detectTapGestures { /* consume tap, do nothing */ }
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = rememberImageBadges(rightCard, showHdrBadges, showMotionBadges)
                )
            }
        } else if (rightCard == null && !isRecallTransitioning && images.size >= 3) {
            // 只有当图片数量 >= 3 时才显示 Placeholder，少量图片时不显示避免视觉混乱
            ImageCardPlaceholder(
                modifier = Modifier
                    .zIndex(0f)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(actualCardAspectRatio)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = 0.88f
                        scaleY = 0.88f
                        translationX = sideCardOffsetPx
                        rotationZ = 5f
                        alpha = 0.5f
                    },
                cornerRadius = 16.dp,
                elevation = 4.dp
            )
        }
        
        // ========== 正在消失的右卡（收牌时）淡出动画 ==========
        if (isRecallTransitioning && recallTransitionRight != null) {
            key("exiting_right_${recallTransitionRight!!.id}") {
                ImageCard(
                    imageFile = recallTransitionRight!!,
                    modifier = Modifier
                        .zIndex(-1f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = 0.88f
                            scaleY = 0.88f
                            translationX = sideCardOffsetPx
                            rotationZ = 5f
                            alpha = rightCardAlpha.value
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = emptyList()
                )
            }
        }
        
        // ========== 收牌过渡时：旧左卡正在淡出 ==========
        if (isRecallTransitioning && recallTransitionLeft != null) {
            key("exiting_left_${recallTransitionLeft!!.id}") {
                ImageCard(
                    imageFile = recallTransitionLeft!!,
                    modifier = Modifier
                        .zIndex(-1f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = 0.88f
                            scaleY = 0.88f
                            translationX = leftCardOffsetX.value
                            rotationZ = -5f
                            alpha = leftCardAlpha.value
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = emptyList()
                )
            }
        }

        // ========== 收牌预览：拖动左滑时显示将要返回的牌 ==========
        if (!isRecallTransitioning && previewReturningCard != null && returningCardAlpha.value > 0.01f) {
            key("preview_returning_${previewReturningCard.id}") {
                ImageCard(
                    imageFile = previewReturningCard,
                    modifier = Modifier
                        .zIndex(1.8f)  // 略低于中卡动画时的层级
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = returningCardOffsetX.value
                            alpha = returningCardAlpha.value
                        },
                    cornerRadius = 16.dp,
                    elevation = 6.dp,
                    badges = rememberImageBadges(previewReturningCard, showHdrBadges, showMotionBadges)
                )
            }
        }
        
        // ========== 正在返回的牌（收牌动画）==========
        if (isRecallTransitioning && recallReturningCard != null) {
            key("returning_${recallReturningCard!!.id}") {
                ImageCard(
                    imageFile = recallReturningCard!!,
                    modifier = Modifier
                        .zIndex(2f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = returningCardOffsetX.value
                            alpha = returningCardAlpha.value
                        },
                    cornerRadius = 16.dp,
                    elevation = 8.dp,
                    badges = rememberImageBadges(recallReturningCard!!, showHdrBadges, showMotionBadges)
                )
            }
        }

        // ========== 中卡 (deck[i]) - 唯一可交互 ==========
        // 发牌过渡时：中卡由 exitingCenterCard 显示，这里不显示
        // 收牌过渡时：中卡由 recallTransitionCenter 控制，不显示此卡
        if (centerCard != null && !isDrawTransitioning && !isRecallTransitioning) {
            key(centerCard.id) {
                val velocityTracker = remember { VelocityTracker() }

                ImageCard(
                    imageFile = centerCard,
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth(0.85f)
                        .aspectRatio(actualCardAspectRatio)
                        .onGloballyPositioned { coordinates ->
                            centerCardBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = centerDragOffsetX.value
                            translationY = centerDragOffsetY.value
                            rotationZ = centerDragRotation.value
                            alpha = centerDragAlpha.value
                            scaleX = centerDragScale.value
                            scaleY = centerDragScale.value
                        }
                        .pointerInput(drawState.currentIndex) {
                            detectTapGestures(
                                onTap = {
                                    if (!hasDragged && onCardClick != null) {
                                        val sourceRect = SourceRect(
                                            x = centerCardBounds.left,
                                            y = centerCardBounds.top,
                                            width = centerCardBounds.width,
                                            height = centerCardBounds.height,
                                            cornerRadius = 16f
                                        )
                                        onCardClick(centerCard, sourceRect)
                                    }
                                }
                            )
                        }
                        .pointerInput(drawState.currentIndex, albums) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    isDragging = true
                                    hasDragged = false
                                    accumulatedDragDistance = 0f  // 重置累计距离
                                    velocityTracker.resetTracking()
                                    swipeThresholdHapticTriggered = false
                                    deleteThresholdHapticTriggered = false
                                    classifyThresholdHapticTriggered = false
                                    
                                    // 记录滑动起始时间和位置（用于自定义速度计算）
                                    dragStartTime = System.currentTimeMillis()
                                    dragStartOffsetX = startOffset.x
                                    dragStartOffsetY = startOffset.y
                                    
                                    // 性能优化：滑动开始时暂停预加载和启用快速检测模式
                                    preloadingManager.pause()
                                    com.tabula.v3.data.repository.ImageFeatureDetector.setFastMode(true)
                                    
                                    // 采集滑动起点（用于拇指侧检测）
                                    onSwipeStart?.invoke(
                                        startOffset.x / size.width.toFloat(),
                                        startOffset.y / size.height.toFloat()
                                    )
                                    debugCardStackLog(
                                        "dragStart draw idx=${drawState.currentIndex} img=${centerCard?.id} " +
                                            "drawTrans=$isDrawTransitioning recallTrans=$isRecallTransitioning " +
                                            "locked=$lockedDirection"
                                    )
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    
                                    // 累计拖动距离，超过阈值才标记为有效拖动
                                    accumulatedDragDistance += kotlin.math.sqrt(
                                        dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                    )
                                    if (accumulatedDragDistance > minDragDistance) {
                                        hasDragged = true
                                    }

                                    if (lockedDirection == SwipeDirection.NONE) {
                                        val totalDx = abs(centerDragOffsetX.value + dragAmount.x)
                                        val totalDy = abs(centerDragOffsetY.value + dragAmount.y)
                                        val totalDistance = kotlin.math.sqrt(totalDx * totalDx + totalDy * totalDy)

                                        // 改进：需要最小滑动距离才开始判定方向
                                        val minLockDistance = 12f  // 摸牌模式响应更灵敏
                                        
                                        // 判断滑动方向：使用角度判定
                                        if (totalDistance > minLockDistance) {
                                            val verticalRatio = 1.73f  // tan(60°) ≈ 1.73
                                            
                                            if (totalDy > totalDx * verticalRatio && centerDragOffsetY.value + dragAmount.y < 0) {
                                                // 明显向上滑动
                                                lockedDirection = SwipeDirection.UP
                                            } else if (totalDy > totalDx * verticalRatio && 
                                                       centerDragOffsetY.value + dragAmount.y > 0 && 
                                                       (enableDownSwipeClassify || onDownSwipeTrigger != null)) {
                                                // 明显向下滑动，且启用下滑功能
                                                lockedDirection = SwipeDirection.DOWN
                                            } else if (totalDx > totalDy * 0.58f) {
                                                // X 位移占优势，锁定为水平
                                                lockedDirection = SwipeDirection.HORIZONTAL
                                            }
                                        }
                                    }

                                    // 使用 UNDISPATCHED 避免每帧创建协程的调度开销
                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                val newY = (centerDragOffsetY.value + dragAmount.y).coerceAtMost(0f)
                                                centerDragOffsetY.snapTo(newY)
                                                centerDragOffsetX.snapTo(centerDragOffsetX.value + dragAmount.x * 0.3f)
                                                
                                                if (!isPreloadingDelete && preloadedDeleteBitmap == null) {
                                                    preloadBitmapForDelete()
                                                }
                                                
                                                if (enableSwipeHaptics && !deleteThresholdHapticTriggered && -newY > deleteThresholdPx) {
                                                    deleteThresholdHapticTriggered = true
                                                    HapticFeedback.heavyTap(context)
                                                }
                                                if (enableSwipeHaptics && deleteThresholdHapticTriggered && -newY <= deleteThresholdPx) {
                                                    deleteThresholdHapticTriggered = false
                                                    HapticFeedback.lightTap(context, throttle = true)
                                                }
                                            }
                                            SwipeDirection.DOWN -> {
                                                val newY = (centerDragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                                centerDragOffsetY.snapTo(newY)
                                                centerDragOffsetX.snapTo(centerDragOffsetX.value + dragAmount.x * 0.7f)
                                                
                                                // LIST_POPUP 模式：下滑触发弹层
                                                if (onDownSwipeTrigger != null && !enableDownSwipeClassify) {
                                                    // 达到阈值时触发弹层并重置卡片
                                                    if (newY > classifyThresholdPx && !classifyThresholdHapticTriggered) {
                                                        classifyThresholdHapticTriggered = true
                                                        if (enableSwipeHaptics) {
                                                            HapticFeedback.mediumTap(context)
                                                        }
                                                        // 触发弹层
                                                        onDownSwipeTrigger.invoke()
                                                        // 重置卡片位置（不重置 classifyThresholdHapticTriggered，避免持续触发振动）
                                                        scope.launch {
                                                            lockedDirection = SwipeDirection.NONE
                                                            centerDragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                                        }
                                                        scope.launch {
                                                            centerDragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                                        }
                                                    }
                                                    return@launch
                                                }
                                                
                                                if (!isPreloading && preloadedBitmap == null) {
                                                    preloadBitmapForGenie()
                                                }
                                                
                                                // 进入归类模式（第一阈值）
                                                // 注意：不在这里触发 onSelectedIndexChange，由下面的 2D 选择逻辑统一处理
                                                // 避免快速滑动时连续触发两次回调导致卡顿
                                                val justEnteredClassifyMode = newY > classifyThresholdPx && !isClassifyMode
                                                if (justEnteredClassifyMode) {
                                                    // 立即更新内部状态
                                                    isClassifyMode = true
                                                    classifyStartX = centerDragOffsetX.value
                                                    classifyStartY = newY
                                                    val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                    selectedAlbumIndex = defaultIndex
                                                    lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                    lastSelectedIndex = defaultIndex
                                                    classifyThresholdHapticTriggered = true
                                                    
                                                    // 只触发归类模式变化，不触发索引变化
                                                    // 索引变化由下面的 2D 选择逻辑统一处理
                                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                        onClassifyModeChange?.invoke(true)
                                                        if (enableSwipeHaptics) {
                                                            HapticFeedback.mediumTap(context)
                                                        }
                                                    }
                                                }
                                                
                                                // 允许用户向上拖动取消归类模式
                                                if (isClassifyMode && newY < classifyExitThresholdPx) {
                                                    isClassifyMode = false
                                                    classifyThresholdHapticTriggered = false
                                                    selectedAlbumIndex = 0
                                                    classifyStartY = 0f
                                                    lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                    lastSelectedIndex = -1
                                                    
                                                    // 异步触发外部回调和振动（使用节流的振动）
                                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                        onClassifyModeChange?.invoke(false)
                                                        if (enableSwipeHaptics) {
                                                            HapticFeedback.lightTap(context, throttle = true)
                                                        }
                                                    }
                                                    
                                                    // 清理预加载（检查是否已回收，避免重复回收）
                                                    preloadedBitmap?.let { bitmap ->
                                                        if (!bitmap.isRecycled) {
                                                            bitmap.recycle()
                                                        }
                                                    }
                                                    preloadedBitmap = null
                                                    isPreloading = false
                                                }
                                                
                                                // 归类模式下：2D 自由选择逻辑（性能优化版）
                                                if (isClassifyMode) {
                                                    // 性能优化：使用节流机制，避免每帧都更新索引
                                                    val currentTime = SystemClock.uptimeMillis()
                                                    val shouldCalculate = justEnteredClassifyMode || 
                                                        (currentTime - lastIndexUpdateTime >= indexUpdateThrottleMs)
                                                    
                                                    if (shouldCalculate) {
                                                        // 快速计算新索引（内联计算，避免函数调用开销）
                                                        val relativeX = centerDragOffsetX.value - classifyStartX
                                                        val relativeY = newY - classifyStartY
                                                        val colOffset = (relativeX / tagSwitchDistanceXPx).toInt()
                                                        val rowOffset = (relativeY / tagSwitchDistanceYPx).toInt()
                                                        
                                                        // 默认起始位置：第一个相册（索引1）
                                                        val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                        val startRow = defaultIndex / tagsPerRow
                                                        val startCol = defaultIndex % tagsPerRow
                                                        
                                                        // 计算新的行列并限制范围
                                                        val totalRows = (totalTags + tagsPerRow - 1) / tagsPerRow
                                                        val newCol = (startCol + colOffset).coerceIn(0, tagsPerRow - 1)
                                                        val newRow = (startRow + rowOffset).coerceIn(0, totalRows - 1)
                                                        val newIndex = (newRow * tagsPerRow + newCol).coerceIn(0, totalTags - 1)
                                                        
                                                        // 只有索引真正变化时才更新状态
                                                        if (newIndex != lastCalculatedIndex || justEnteredClassifyMode) {
                                                            lastCalculatedIndex = newIndex
                                                            lastIndexUpdateTime = currentTime
                                                            
                                                            // 检查是否需要更新外部状态
                                                            if (newIndex != selectedAlbumIndex || justEnteredClassifyMode) {
                                                                val shouldVibrate = enableSwipeHaptics && 
                                                                    newIndex != lastSelectedIndex && 
                                                                    !justEnteredClassifyMode
                                                                selectedAlbumIndex = newIndex
                                                                lastSelectionChangeAt = currentTime
                                                                lastSelectedIndex = newIndex
                                                                
                                                                // 异步触发外部回调和振动（使用节流的振动）
                                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                                    onSelectedIndexChange?.invoke(newIndex)
                                                                    if (shouldVibrate) {
                                                                        HapticFeedback.lightTap(context, throttle = true)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                // 移除下滑缩放效果，避免性能开销
                                                // val scaleProgress = (newY / (screenHeightPx * 0.2f)).coerceIn(0f, 1f)
                                                // centerDragScale.snapTo(1f - scaleProgress * 0.1f)
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                val newX = centerDragOffsetX.value + dragAmount.x
                                                centerDragOffsetX.snapTo(newX)
                                                centerDragOffsetY.snapTo(centerDragOffsetY.value + dragAmount.y * 0.2f)
                                                
                                                // 摸牌样式：右滑发牌，左滑收牌
                                                // 跟手同步动画：根据拖动进度同步更新左卡/右卡状态
                                                // 使用屏幕宽度的 50% 作为基准，使动画同步更自然
                                                val syncDistance = screenWidthPx * 0.5f
                                                if (newX > 0 && drawState.canDraw(images.size)) {
                                                    // 右滑发牌：左卡跟手向中间移动，同时旋转归正
                                                    val drawProgress = (newX / syncDistance).coerceIn(0f, 1f)
                                                    leftCardOffsetX.snapTo(-sideCardOffsetPx + sideCardOffsetPx * drawProgress)
                                                    leftCardScale.snapTo(0.88f + 0.12f * drawProgress)
                                                    // leftCardAlpha 保持 1f，背景卡片不需要透明效果
                                                    leftCardRotation.snapTo(-5f + 5f * drawProgress)  // 从 -5° 旋转到 0°
                                                } else if (newX < 0 && drawState.canRecall()) {
                                                    // 左滑收牌：返回牌跟手从右侧进入预览
                                                    val recallProgress = (-newX / syncDistance).coerceIn(0f, 1f)
                                                    returningCardOffsetX.snapTo(screenWidthPx * (1f - recallProgress * 0.3f))
                                                    returningCardAlpha.snapTo(recallProgress * 0.5f)
                                                } else {
                                                    // 重置到初始状态
                                                    leftCardOffsetX.snapTo(-sideCardOffsetPx)
                                                    leftCardScale.snapTo(0.88f)
                                                    leftCardAlpha.snapTo(1f)
                                                    leftCardRotation.snapTo(-5f)
                                                    returningCardOffsetX.snapTo(screenWidthPx)
                                                    returningCardAlpha.snapTo(0f)
                                                }
                                                
                                                // 发牌阈值触发振动
                                                if (enableSwipeHaptics && !swipeThresholdHapticTriggered) {
                                                    val canTrigger = if (newX > 0) {
                                                        // 右滑发牌：检查是否可以发牌
                                                        drawState.canDraw(images.size) && newX > swipeThresholdPx
                                                    } else {
                                                        // 左滑收牌：检查是否可以收牌
                                                        drawState.canRecall() && -newX > swipeThresholdPx
                                                    }
                                                    if (canTrigger) {
                                                        swipeThresholdHapticTriggered = true
                                                        HapticFeedback.mediumTap(context)
                                                    }
                                                }
                                            }
                                            else -> {
                                                centerDragOffsetX.snapTo(centerDragOffsetX.value + dragAmount.x)
                                                centerDragOffsetY.snapTo(centerDragOffsetY.value + dragAmount.y)
                                            }
                                        }

                                        // 旋转效果（归类模式下减弱）
                                        val rotationFactor = if (lockedDirection == SwipeDirection.DOWN) 0.3f else 1f
                                        val rotation = (centerDragOffsetX.value / screenWidthPx) * 15f * rotationFactor
                                        centerDragRotation.snapTo(rotation.coerceIn(-20f, 20f))
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val trackerVelocity = velocityTracker.calculateVelocity()
                                    
                                    // 自定义速度计算（解决 VelocityTracker 快速滑动时 speed rezero 的问题）
                                    val elapsedTime = System.currentTimeMillis() - dragStartTime
                                    val customVelocityX = if (elapsedTime > 0) {
                                        (centerDragOffsetX.value * 1000f) / elapsedTime  // 转换为 px/s
                                    } else 0f
                                    val customVelocityY = if (elapsedTime > 0) {
                                        (centerDragOffsetY.value * 1000f) / elapsedTime
                                    } else 0f
                                    
                                    // 取 VelocityTracker 和自定义计算的较大值
                                    val velocityX = if (abs(customVelocityX) > abs(trackerVelocity.x)) customVelocityX else trackerVelocity.x
                                    val velocityY = if (abs(customVelocityY) > abs(trackerVelocity.y)) customVelocityY else trackerVelocity.y

                                    debugCardStackLog(
                                        "dragEnd draw idx=${drawState.currentIndex} locked=$lockedDirection " +
                                            "dx=${centerDragOffsetX.value} dy=${centerDragOffsetY.value} " +
                                            "vX=$velocityX vY=$velocityY drawTrans=$isDrawTransitioning recallTrans=$isRecallTransitioning"
                                    )
                                    
                                    // 性能优化：滑动结束时恢复预加载和禁用快速检测模式
                                    preloadingManager.resume()
                                    com.tabula.v3.data.repository.ImageFeatureDetector.setFastMode(false)

                                    scope.launch {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                if (abs(centerDragOffsetY.value) > deleteThresholdPx ||
                                                    abs(velocityY) > velocityThreshold
                                                ) {
                                                    executeDeleteAnimation(playHaptic = !deleteThresholdHapticTriggered)
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                            SwipeDirection.DOWN -> {
                                                if (isClassifyMode) {
                                                    executeGenieAnimation(selectedAlbumIndex)
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                val offsetX = centerDragOffsetX.value
                                                
                                                // 摸牌样式：右滑发牌，左滑收牌
                                                if (offsetX > 0) {
                                                    // 右滑 → 发牌
                                                    val triggered = offsetX > swipeThresholdPx || velocityX > velocityThreshold
                                                    if (triggered) {
                                                        if (drawState.canDraw(images.size)) {
                                                            executeDrawAnimation()
                                                        } else {
                                                            // 最后一张卡片，进入总结页面
                                                            executeLastCardAnimation()
                                                        }
                                                    } else {
                                                        resetDragState()
                                                    }
                                                } else {
                                                    // 左滑 → 收牌
                                                    val triggered = -offsetX > swipeThresholdPx || -velocityX > velocityThreshold
                                                    if (triggered && drawState.canRecall()) {
                                                        executeRecallAnimation()
                                                    } else {
                                                        resetDragState()
                                                    }
                                                }
                                            }
                                            else -> {
                                                // 方向未锁定，但仍检查是否有快速滑动手势
                                                val absVelocityX = abs(velocityX)
                                                val absVelocityY = abs(velocityY)
                                                val absOffsetX = abs(centerDragOffsetX.value)
                                                val absOffsetY = abs(centerDragOffsetY.value)
                                                
                                                // 水平方向占优势且达到阈值
                                                if ((absVelocityX > velocityThreshold || absOffsetX > swipeThresholdPx) &&
                                                    absVelocityX > absVelocityY && absOffsetX > absOffsetY) {
                                                    val offsetX = centerDragOffsetX.value
                                                    
                                                    if (offsetX > 0) {
                                                        // 右滑 → 发牌
                                                        if (drawState.canDraw(images.size)) {
                                                            executeDrawAnimation()
                                                        } else {
                                                            executeLastCardAnimation()
                                                        }
                                                    } else {
                                                        // 左滑 → 收牌
                                                        if (drawState.canRecall()) {
                                                            executeRecallAnimation()
                                                        } else {
                                                            resetDragState()
                                                        }
                                                    }
                                                } else {
                                                    resetDragState()
                                                }
                                            }
                                        }
                                        hasDragged = false
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    hasDragged = false
                                    debugCardStackLog(
                                        "dragCancel draw idx=${drawState.currentIndex} locked=$lockedDirection " +
                                            "dx=${centerDragOffsetX.value} dy=${centerDragOffsetY.value} " +
                                            "drawTrans=$isDrawTransitioning recallTrans=$isRecallTransitioning"
                                    )
                                    scope.launch { resetDragState() }
                                }
                            )
                        },
                    cornerRadius = 16.dp,
                    elevation = 8.dp,
                    badges = rememberImageBadges(centerCard, showHdrBadges, showMotionBadges)
                )
            }
        }
        
        // ========== 固定标签点击模式：外部触发 Genie 动画 ==========
        LaunchedEffect(fixedTagTriggerIndex) {
            if (fixedTagTriggerIndex != null && !genieController.isAnimating) {
                // 预加载 bitmap（如果还没有）
                if (preloadedBitmap == null) {
                    preloadBitmapForGenie()
                    // 等待预加载完成（最多100ms）
                    var waitTime = 0
                    while (isPreloading && waitTime < 100) {
                        kotlinx.coroutines.delay(10)
                        waitTime += 10
                    }
                }
                // 执行 Genie 动画
                executeGenieAnimation(fixedTagTriggerIndex)
                // 通知外部动画完成
                onFixedTagAnimationComplete?.invoke()
            }
        }
        
        // ========== Genie Effect 覆盖层 ==========
    }
}
