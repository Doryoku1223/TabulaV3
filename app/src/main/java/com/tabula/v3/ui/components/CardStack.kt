package com.tabula.v3.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.memory.MemoryCache
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.SwipeStyle
import com.tabula.v3.di.PreloadingManager
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.preloadImageFeatures
import com.tabula.v3.ui.util.rememberImageFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow

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
    // 下滑归类相关参数
    albums: List<Album> = emptyList(),
    onClassifyToAlbum: ((ImageFile, Album) -> Unit)? = null,
    onCreateNewAlbum: ((ImageFile) -> Unit)? = null,
    // 归类模式状态回调
    onClassifyModeChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    // 标签位置映射（索引 -> 屏幕坐标）
    tagPositions: Map<Int, TagPosition> = emptyMap(),
    // 回收站按钮位置（用于上滑删除的Genie动画目标点）
    trashButtonBounds: Rect = Rect.Zero
) {
    if (images.isEmpty()) return
    
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
            albums = albums,
            onClassifyToAlbum = onClassifyToAlbum,
            onCreateNewAlbum = onCreateNewAlbum,
            onClassifyModeChange = onClassifyModeChange,
            onSelectedIndexChange = onSelectedIndexChange,
            tagPositions = tagPositions,
            trashButtonBounds = trashButtonBounds
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
    val tagSwitchDistanceXPx = with(density) { 16.dp.toPx() }  // 水平方向切换列的距离（更灵敏）
    val tagSwitchDistanceYPx = with(density) { 20.dp.toPx() }  // 垂直方向切换行的距离（更灵敏）
    val totalTags = albums.size + 1  // 总标签数（+1 是新建按钮）

    // 动画时长
    val shuffleAnimDuration = 120
    val genieAnimDuration = 520  // Genie动画时长（更丝滑的观感）

    // ========== 顶层卡片拖拽状态 ==========
    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val dragRotation = remember { Animatable(0f) }
    val dragAlpha = remember { Animatable(1f) }
    val dragScale = remember { Animatable(1f) }  // 新增：缩放动画

    // 手势方向锁定
    var lockedDirection by remember { mutableStateOf(SwipeDirection.NONE) }
    var isDragging by remember { mutableStateOf(false) }
    var hasDragged by remember { mutableStateOf(false) }  // 是否发生过拖动
    var swipeThresholdHapticTriggered by remember { mutableStateOf(false) }
    var deleteThresholdHapticTriggered by remember { mutableStateOf(false) }
    var classifyThresholdHapticTriggered by remember { mutableStateOf(false) }
    
    // 归类模式状态
    var isClassifyMode by remember { mutableStateOf(false) }
    var selectedAlbumIndex by remember { mutableIntStateOf(0) }
    var classifyStartX by remember { mutableFloatStateOf(0f) }  // 进入归类模式时的X位置
    var classifyStartY by remember { mutableFloatStateOf(0f) }  // 进入归类模式时的Y位置（用于2D选择）
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }  // 上次选中的索引，用于触发振动
    var lastSelectionChangeAt by remember { mutableStateOf(0L) }  // 记录上次切换标签的时间
    val latestTagPositions by rememberUpdatedState(tagPositions)

    // ========== Genie动画状态 ==========
    val genieController = rememberGenieAnimationController()
    
    // ========== Bitmap 预加载状态 ==========
    // 在下滑进入归类模式时预加载，而不是松手时才加载
    var preloadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloading by remember { mutableStateOf(false) }
    
    // 上滑删除的 Bitmap 预加载状态
    var preloadedDeleteBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloadingDelete by remember { mutableStateOf(false) }
    
    // 组件销毁时清理预加载的 bitmap
    DisposableEffect(Unit) {
        onDispose {
            preloadedBitmap?.recycle()
            preloadedBitmap = null
            preloadedDeleteBitmap?.recycle()
            preloadedDeleteBitmap = null
        }
    }
    
    // ========== 图片预加载 ==========
    // 预加载当前索引前后的图片，加快卡片显示速度
    val preloadingManager = remember { PreloadingManager(context, preloadRange = 3) }
    
    LaunchedEffect(currentIndex, images) {
        // 预加载当前索引周围的图片
        preloadingManager.preloadAround(
            images = images,
            currentIndex = currentIndex,
            cardSize = IntSize(1080, 1440)  // 与 ImageCard 的最大尺寸匹配
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

    // 获取三张卡的数据
    val currentImage = images.getOrNull(currentIndex)
    val nextImage = images.getOrNull((currentIndex + 1) % images.size)
    val prevImage = images.getOrNull((currentIndex - 1 + images.size) % images.size)

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
     */
    suspend fun resetDragState() {
        scope.launch { dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragRotation.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragAlpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
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
        onClassifyModeChange?.invoke(false)
        
        // 清理预加载的 bitmap
        preloadedBitmap?.recycle()
        preloadedBitmap = null
        isPreloading = false
        
        // 清理上滑删除的预加载 bitmap
        preloadedDeleteBitmap?.recycle()
        preloadedDeleteBitmap = null
        isPreloadingDelete = false
    }
    
    /**
     * 预加载 Bitmap（在下滑方向确定后立即调用）
     * 
     * 性能优化：
     * - 优先从 Coil 内存缓存获取（图片已被 ImageCard 加载过）
     * - 如果缓存未命中，使用 BitmapFactory 快速加载
     * - 避免重复打开文件导致系统后台处理
     */
    fun preloadBitmapForGenie() {
        if (isPreloading || preloadedBitmap != null) return
        val currentImg = currentImage ?: return
        
        isPreloading = true
        scope.launch(Dispatchers.Default) {
            try {
                var bitmap: Bitmap? = null
                
                // 方案1：尝试从 Coil 内存缓存获取（最快，不触发任何 IO）
                val cacheKey = "card_${currentImg.id}"
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    coil.memory.MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    // 从缓存获取成功，缩放到 Genie 动画需要的尺寸
                    val maxSize = 250
                    val scale = minOf(
                        maxSize.toFloat() / cachedBitmap.width,
                        maxSize.toFloat() / cachedBitmap.height,
                        1f
                    )
                    val targetWidth = (cachedBitmap.width * scale).toInt().coerceAtLeast(50)
                    val targetHeight = (cachedBitmap.height * scale).toInt().coerceAtLeast(50)
                    
                    // 创建缩放副本（不修改缓存中的原图）
                    bitmap = Bitmap.createScaledBitmap(cachedBitmap, targetWidth, targetHeight, true)
                }
                
                // 方案2：缓存未命中，使用快速文件加载
                if (bitmap == null) {
                    withContext(Dispatchers.IO) {
                        bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    // 只要还在下滑状态（方向锁定为 DOWN）就保存 bitmap
                    if (lockedDirection == SwipeDirection.DOWN) {
                        preloadedBitmap = bitmap
                    } else {
                        // 用户改变了方向，释放 bitmap
                        bitmap?.recycle()
                    }
                    isPreloading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isPreloading = false
                }
            }
        }
    }
    
    /**
     * 预加载上滑删除的 Bitmap（在上滑方向确定后立即调用）
     * 
     * 与下滑归类的预加载逻辑相同，但用于上滑删除的 Genie 动画
     */
    fun preloadBitmapForDelete() {
        if (isPreloadingDelete || preloadedDeleteBitmap != null) return
        val currentImg = currentImage ?: return
        
        isPreloadingDelete = true
        scope.launch(Dispatchers.Default) {
            try {
                var bitmap: Bitmap? = null
                
                // 方案1：尝试从 Coil 内存缓存获取
                val cacheKey = "card_${currentImg.id}"
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    coil.memory.MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    val maxSize = 250
                    val scale = minOf(
                        maxSize.toFloat() / cachedBitmap.width,
                        maxSize.toFloat() / cachedBitmap.height,
                        1f
                    )
                    val targetWidth = (cachedBitmap.width * scale).toInt().coerceAtLeast(50)
                    val targetHeight = (cachedBitmap.height * scale).toInt().coerceAtLeast(50)
                    bitmap = Bitmap.createScaledBitmap(cachedBitmap, targetWidth, targetHeight, true)
                }
                
                // 方案2：缓存未命中，使用快速文件加载
                if (bitmap == null) {
                    withContext(Dispatchers.IO) {
                        bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    // 只要还在上滑状态就保存 bitmap
                    if (lockedDirection == SwipeDirection.UP) {
                        preloadedDeleteBitmap = bitmap
                    } else {
                        bitmap?.recycle()
                    }
                    isPreloadingDelete = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isPreloadingDelete = false
                }
            }
        }
    }

    /**
     * 执行洗牌动画（插底 + 顶上）
     * 
     * 当滑到最后一张时，使用平滑的淡出过渡，避免回弹感
     */
    suspend fun executeShuffleAnimation(direction: Int) {
        isTransitioning = true
        pendingIndexChange = direction

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

            scope.launch { dragOffsetX.animateTo(targetX, tween(shuffleAnimDuration)) }
            scope.launch { dragOffsetY.animateTo(0f, tween(shuffleAnimDuration)) }
            scope.launch { dragRotation.animateTo(targetRotation, tween(shuffleAnimDuration)) }

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

        isTransitioning = false
        lockedDirection = SwipeDirection.NONE
        breathScale = 0f
    }

    /**
     * 执行删除动画 - macOS 神奇效果（向上吸入）
     * 
     * 使用 Genie 效果将卡片吸入回收站按钮
     * 效果为"下宽上窄"，目标点在回收站按钮下侧边框的中心点
     */
    suspend fun executeDeleteAnimation(playHaptic: Boolean) {
        val currentImg = currentImage ?: return
        
        // 触发震动反馈
        if (enableSwipeHaptics && playHaptic) {
            HapticFeedback.heavyTap(context)
        }
        
        // 计算目标位置（回收站按钮下侧边框的中心点）
        // 需要将绝对坐标转换为相对于容器的坐标
        val targetCenterX: Float
        val targetCenterY: Float
        
        if (trashButtonBounds != Rect.Zero && containerBounds != Rect.Zero) {
            // 使用回收站按钮下侧边框的中心点作为目标
            targetCenterX = trashButtonBounds.center.x - containerBounds.left
            targetCenterY = trashButtonBounds.bottom - containerBounds.top
        } else {
            // 回退到估算值（右上角区域）
            targetCenterX = containerBounds.width * 0.8f
            targetCenterY = with(density) { 60.dp.toPx() }
        }
        
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
        val relativeSourceBounds = if (containerBounds != Rect.Zero) {
            Rect(
                left = topCardBounds.left - containerBounds.left,
                top = topCardBounds.top - containerBounds.top,
                right = topCardBounds.right - containerBounds.left,
                bottom = topCardBounds.bottom - containerBounds.top
            )
        } else {
            topCardBounds
        }
        
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
            
            val finalOffsetX = targetCenterX - topCardBounds.center.x + containerBounds.left
            val finalOffsetY = targetCenterY - topCardBounds.center.y + containerBounds.top
            
            scope.launch { dragOffsetX.animateTo(finalOffsetX, tween(animDuration, easing = easeInOut)) }
            scope.launch { dragOffsetY.animateTo(finalOffsetY, tween(animDuration, easing = easeInOut)) }
            scope.launch { dragScale.animateTo(0.05f, tween(animDuration, easing = easeInOut)) }
            scope.launch { dragAlpha.animateTo(0f, tween(animDuration, easing = easeInOut)) }
            
            kotlinx.coroutines.delay(animDuration.toLong())
            onRemove(currentImg)
        }
        
        // 重置状态
        dragOffsetX.snapTo(0f)
        dragOffsetY.snapTo(0f)
        dragRotation.snapTo(0f)
        dragAlpha.snapTo(1f)
        dragScale.snapTo(1f)
        lockedDirection = SwipeDirection.NONE
        breathScale = 0f
    }

    /**
     * 等待并获取选中标签的最新测量位置
     */
    suspend fun resolveTagBounds(
        targetIndex: Int,
        timeoutMs: Long
    ): Rect? {
        val start = SystemClock.uptimeMillis()
        var lastRect: Rect? = null

        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val tagPosition = latestTagPositions[targetIndex]
            if (tagPosition != null && tagPosition.coordinates.isAttached) {
                val rect = tagPosition.coordinates.boundsInRoot()
                lastRect = rect
                val isFresh = tagPosition.updatedAt >= lastSelectionChangeAt
                if (rect != Rect.Zero && isFresh) {
                    return rect
                }
            }
            kotlinx.coroutines.delay(16)
        }

        return lastRect
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
     * @param targetIndex 目标标签索引
     */
    suspend fun executeGenieAnimation(targetIndex: Int) {
        val currentImg = currentImage ?: return
        
        // 获取目标相册（索引0是新建按钮，索引1+是相册）
        val isCreateNew = targetIndex == 0
        val targetAlbum = if (!isCreateNew && targetIndex > 0 && targetIndex <= albums.size) {
            albums[targetIndex - 1]
        } else null
        
        // 触发震动反馈
        if (enableSwipeHaptics) {
            HapticFeedback.heavyTap(context)
        }

        // 如果刚刚切换过标签，等待最新的测量位置再开始动画，避免快速松手时位置偏移
        val now = SystemClock.uptimeMillis()
        val shouldWaitLonger = now - lastSelectionChangeAt < 250L
        val tagBounds = resolveTagBounds(
            targetIndex = targetIndex,
            timeoutMs = if (shouldWaitLonger) 600L else 100L
        )
        
        // 计算目标位置
        // 重要：需要将绝对坐标（boundsInRoot）转换为相对于容器的坐标
        val targetCenterX: Float
        val targetCenterY: Float
        
        if (tagBounds != null && tagBounds != Rect.Zero && containerBounds != Rect.Zero) {
            // 使用标签上边的中点作为目标位置（更符合视觉效果）
            // 将绝对坐标转换为相对于容器的坐标
            // 
            // 重要：选中的标签有 scale(1.1) 缩放动画，但 boundsInRoot() 返回的是缩放前的位置
            // 缩放从中心进行，所以视觉上的 top 会向上偏移 height * 0.05
            // 需要补偿这个偏移才能让动画目标点对准视觉上的标签上边中点
            val scaleCompensation = tagBounds.height * 0.05f  // scale 1.1 = 中心向外扩展 5%
            targetCenterX = tagBounds.center.x - containerBounds.left
            targetCenterY = tagBounds.top - containerBounds.top - scaleCompensation  // 上边的Y坐标（补偿缩放）
        } else {
            // 回退到估算值（仅在无法获取实际位置时使用）
            val tagEstimatedWidth = with(density) { 65.dp.toPx() }
            val tagSpacing = with(density) { 12.dp.toPx() }
            val listPadding = with(density) { 24.dp.toPx() }
            val tagsStartX = listPadding
            targetCenterX = tagsStartX + targetIndex * (tagEstimatedWidth + tagSpacing) + tagEstimatedWidth / 2f
            // 回退时使用容器底部作为目标
            targetCenterY = containerBounds.height - with(density) { 80.dp.toPx() }
        }
        
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
        val relativeSourceBounds = if (containerBounds != Rect.Zero) {
            Rect(
                left = topCardBounds.left - containerBounds.left,
                top = topCardBounds.top - containerBounds.top,
                right = topCardBounds.right - containerBounds.left,
                bottom = topCardBounds.bottom - containerBounds.top
            )
        } else {
            topCardBounds
        }
        
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
        
        // 重置状态
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
        } else {
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
        } else {
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
                                    velocityTracker.resetTracking()
                                    swipeThresholdHapticTriggered = false
                                    deleteThresholdHapticTriggered = false
                                    classifyThresholdHapticTriggered = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    hasDragged = true
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                                    if (lockedDirection == SwipeDirection.NONE) {
                                        val totalDx = abs(dragOffsetX.value + dragAmount.x)
                                        val totalDy = abs(dragOffsetY.value + dragAmount.y)

                                        // 判断滑动方向
                                        if (totalDy > totalDx * 1.5f && dragAmount.y < 0) {
                                            lockedDirection = SwipeDirection.UP
                                        } else if (totalDy > totalDx * 1.5f && dragAmount.y > 0) {
                                            // 下滑归类（即使没有图集，也可以下滑触发新建）
                                            lockedDirection = SwipeDirection.DOWN
                                        } else if (totalDx > 20f || totalDy > 20f) {
                                            lockedDirection = SwipeDirection.HORIZONTAL
                                        }
                                    }

                                    scope.launch {
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
                                                    HapticFeedback.lightTap(context)
                                                }
                                            }
                                            SwipeDirection.DOWN -> {
                                                // 下滑归类模式
                                                val newY = (dragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                                dragOffsetY.snapTo(newY)
                                                dragOffsetX.snapTo(dragOffsetX.value + dragAmount.x * 0.7f)
                                                
                                                // 尽早开始预加载（方向锁定后立即开始）
                                                // 这样在用户达到归类阈值之前，bitmap 可能已经加载完成
                                                if (!isPreloading && preloadedBitmap == null) {
                                                    preloadBitmapForGenie()
                                                }
                                                
                                                // 进入归类模式（第一阈值）
                                                if (newY > classifyThresholdPx && !isClassifyMode) {
                                                    isClassifyMode = true
                                                    classifyStartX = dragOffsetX.value  // 记录进入归类模式时的X位置
                                                    classifyStartY = newY               // 记录进入归类模式时的Y位置
                                                    // 默认选中第一个相册（索引1），因为索引0是新建按钮
                                                    val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                    selectedAlbumIndex = defaultIndex
                                                    lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                    lastSelectedIndex = defaultIndex
                                                    onClassifyModeChange?.invoke(true)
                                                    onSelectedIndexChange?.invoke(defaultIndex)
                                                    if (enableSwipeHaptics) {
                                                        classifyThresholdHapticTriggered = true
                                                        HapticFeedback.mediumTap(context)
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
                                                    onClassifyModeChange?.invoke(false)
                                                    if (enableSwipeHaptics) {
                                                        HapticFeedback.lightTap(context)
                                                    }
                                                    // 清理预加载的 bitmap
                                                    preloadedBitmap?.recycle()
                                                    preloadedBitmap = null
                                                    isPreloading = false
                                                }
                                                
                                                // 归类模式下：2D 自由选择逻辑
                                                if (isClassifyMode) {
                                                    // 根据 X 偏移计算列偏移
                                                    val relativeX = dragOffsetX.value - classifyStartX
                                                    val colOffset = (relativeX / tagSwitchDistanceXPx).toInt()
                                                    
                                                    // 根据 Y 偏移计算行偏移
                                                    val relativeY = newY - classifyStartY
                                                    val rowOffset = (relativeY / tagSwitchDistanceYPx).toInt()
                                                    
                                                    // 默认起始位置：第一个相册（索引1）
                                                    val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                    val startRow = defaultIndex / TAGS_PER_ROW
                                                    val startCol = defaultIndex % TAGS_PER_ROW
                                                    
                                                    // 计算新的行和列
                                                    var newCol = startCol + colOffset
                                                    var newRow = startRow + rowOffset
                                                    
                                                    // 计算总行数
                                                    val totalRows = (totalTags + TAGS_PER_ROW - 1) / TAGS_PER_ROW
                                                    
                                                    // 限制行范围
                                                    newRow = newRow.coerceIn(0, totalRows - 1)
                                                    
                                                    // 限制列范围
                                                    newCol = newCol.coerceIn(0, TAGS_PER_ROW - 1)
                                                    
                                                    // 计算全局索引
                                                    var newIndex = newRow * TAGS_PER_ROW + newCol
                                                    
                                                    // 确保不超过最后一个标签
                                                    newIndex = newIndex.coerceIn(0, totalTags - 1)
                                                    
                                                    if (newIndex != selectedAlbumIndex) {
                                                        selectedAlbumIndex = newIndex
                                                        lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                        onSelectedIndexChange?.invoke(newIndex)
                                                        // 切换标签时触发振动
                                                        if (enableSwipeHaptics && newIndex != lastSelectedIndex) {
                                                            lastSelectedIndex = newIndex
                                                            HapticFeedback.lightTap(context)
                                                        }
                                                    }
                                                }
                                                
                                                // 轻微缩放效果
                                                val scaleProgress = (newY / (screenHeightPx * 0.2f)).coerceIn(0f, 1f)
                                                dragScale.snapTo(1f - scaleProgress * 0.1f)
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
                                    val velocity = velocityTracker.calculateVelocity()

                                    scope.launch {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                if (abs(dragOffsetY.value) > deleteThresholdPx ||
                                                    abs(velocity.y) > velocityThreshold
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
                                                        abs(velocity.x) > velocityThreshold

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
                                                resetDragState()
                                            }
                                        }
                                        // 重置拖动标记
                                        hasDragged = false
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    hasDragged = false
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
        
        // ========== Genie Effect 覆盖层 ==========
        if (genieController.isAnimating) {
            GenieEffectOverlay(
                bitmap = genieController.bitmap,
                sourceBounds = genieController.sourceBounds,
                targetX = genieController.targetX,
                targetY = genieController.targetY,
                progress = genieController.progress,
                screenHeight = genieController.screenHeight,
                direction = genieController.direction,
                modifier = Modifier.zIndex(10f)
            )
        }
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
    albums: List<Album> = emptyList(),
    onClassifyToAlbum: ((ImageFile, Album) -> Unit)? = null,
    onCreateNewAlbum: ((ImageFile) -> Unit)? = null,
    onClassifyModeChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    tagPositions: Map<Int, TagPosition> = emptyMap(),
    trashButtonBounds: Rect = Rect.Zero
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
    
    // 2D 标签选择参数
    val tagSwitchDistanceXPx = with(density) { 16.dp.toPx() }
    val tagSwitchDistanceYPx = with(density) { 20.dp.toPx() }
    val totalTags = albums.size + 1

    // 动画时长
    val drawAnimDuration = 200  // 发牌动画时长
    val recallAnimDuration = 250  // 收牌动画时长
    val genieAnimDuration = 520

    // ========== 摸牌样式状态 ==========
    var drawState by remember { mutableStateOf(DrawCardState(currentIndex = currentIndex)) }
    
    // 当外部 currentIndex 变化时同步状态（如删除图片后）
    LaunchedEffect(currentIndex, images.size) {
        if (drawState.currentIndex != currentIndex) {
            drawState = DrawCardState(currentIndex = currentIndex)
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
    var hasDragged by remember { mutableStateOf(false) }
    var swipeThresholdHapticTriggered by remember { mutableStateOf(false) }
    var deleteThresholdHapticTriggered by remember { mutableStateOf(false) }
    var classifyThresholdHapticTriggered by remember { mutableStateOf(false) }
    
    // 归类模式状态
    var isClassifyMode by remember { mutableStateOf(false) }
    var selectedAlbumIndex by remember { mutableIntStateOf(if (albums.isNotEmpty()) 1 else 0) }
    var classifyStartX by remember { mutableFloatStateOf(0f) }
    var classifyStartY by remember { mutableFloatStateOf(0f) }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }
    var lastSelectionChangeAt by remember { mutableStateOf(0L) }
    val latestTagPositions by rememberUpdatedState(tagPositions)

    // Genie 动画控制器
    val genieController = rememberGenieAnimationController()
    
    // Bitmap 预加载状态
    var preloadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloading by remember { mutableStateOf(false) }
    var preloadedDeleteBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreloadingDelete by remember { mutableStateOf(false) }
    
    // 清理
    DisposableEffect(Unit) {
        onDispose {
            preloadedBitmap?.recycle()
            preloadedDeleteBitmap?.recycle()
        }
    }
    
    // 图片预加载 - 扩大范围确保流畅
    val preloadingManager = remember { PreloadingManager(context, preloadRange = 5) }
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
    val previewReturningCard = remember(drawState.dealtStack) {
        if (drawState.canRecall()) {
            drawState.dealtStack.lastOrNull()?.let { images.getOrNull(it.originalIndex) }
        } else null
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
     */
    suspend fun resetDragState() {
        scope.launch { centerDragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { centerDragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { centerDragRotation.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { centerDragAlpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { centerDragScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        
        // 重置左卡跟手状态（带动画回弹）
        scope.launch { leftCardOffsetX.animateTo(-sideCardOffsetPx, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { leftCardScale.animateTo(0.88f, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { leftCardAlpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }  // 保持不透明
        scope.launch { leftCardRotation.animateTo(-5f, spring(stiffness = Spring.StiffnessMedium)) }
        
        // 重置返回牌预览状态
        scope.launch { returningCardOffsetX.animateTo(screenWidthPx, spring(stiffness = Spring.StiffnessMedium)) }
        scope.launch { returningCardAlpha.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        
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
        onClassifyModeChange?.invoke(false)
        
        // 清理预加载
        preloadedBitmap?.recycle()
        preloadedBitmap = null
        isPreloading = false
        preloadedDeleteBitmap?.recycle()
        preloadedDeleteBitmap = null
        isPreloadingDelete = false
    }

    // 保存动画期间需要显示的"旧卡片"
    var exitingCenterCard by remember { mutableStateOf<ImageFile?>(null) }
    var transitionLeftCard by remember { mutableStateOf<ImageFile?>(null) }
    
    /**
     * 执行发牌动画（中卡向右飞出，左卡补位到中间）
     * 
     * 优化：先更新状态让新图片提前加载，同时播放旧卡片的退场动画
     */
    suspend fun executeDrawAnimation() {
        if (!drawState.canDraw(images.size)) return
        
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
        val animDuration = 160  // 缩短动画时间
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
        
        // 6. 动画完成，清理状态
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

    // 收牌动画保存的卡片
    var recallTransitionCenter by remember { mutableStateOf<ImageFile?>(null) }
    var recallTransitionLeft by remember { mutableStateOf<ImageFile?>(null) }
    var recallTransitionRight by remember { mutableStateOf<ImageFile?>(null) }
    var recallReturningCard by remember { mutableStateOf<ImageFile?>(null) }
    
    /**
     * 执行收牌动画（返回牌从右侧飞入，当前中卡滑到左侧）
     */
    suspend fun executeRecallAnimation() {
        if (!drawState.canRecall()) return
        
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
        val animDuration = 180
        val animSpec = tween<Float>(animDuration, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
        val fastSpec = tween<Float>(animDuration / 2)
        
        // 并行执行动画
        scope.launch { returningCardOffsetX.animateTo(0f, animSpec) }
        scope.launch { outgoingLeftOffsetX.animateTo(-sideCardOffsetPx, animSpec) }
        scope.launch { outgoingLeftScale.animateTo(0.88f, animSpec) }
        scope.launch { outgoingLeftAlpha.animateTo(1f, animSpec) }  // 保持不透明
        // leftCardAlpha 保持 1f，不需要动画
        scope.launch { leftCardOffsetX.animateTo(-sideCardOffsetPx * 1.3f, animSpec) }
        
        // 右卡保持不透明，不需要淡出
        
        kotlinx.coroutines.delay(animDuration.toLong())
        
        // 5. 清除动画状态
        val finalState = drawCardReducer(drawState, DrawCardAction.AnimationComplete, images)
        drawState = finalState
        
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
    
    /**
     * 预加载 Bitmap（用于 Genie 动画）
     */
    fun preloadBitmapForGenie() {
        if (isPreloading || preloadedBitmap != null) return
        val currentImg = centerCard ?: return
        
        isPreloading = true
        scope.launch(Dispatchers.Default) {
            try {
                var bitmap: Bitmap? = null
                val cacheKey = "card_${currentImg.id}"
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    val maxSize = 250
                    val scale = minOf(
                        maxSize.toFloat() / cachedBitmap.width,
                        maxSize.toFloat() / cachedBitmap.height,
                        1f
                    )
                    val targetWidth = (cachedBitmap.width * scale).toInt().coerceAtLeast(50)
                    val targetHeight = (cachedBitmap.height * scale).toInt().coerceAtLeast(50)
                    bitmap = Bitmap.createScaledBitmap(cachedBitmap, targetWidth, targetHeight, true)
                }
                
                if (bitmap == null) {
                    withContext(Dispatchers.IO) {
                        bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (lockedDirection == SwipeDirection.DOWN) {
                        preloadedBitmap = bitmap
                    } else {
                        bitmap?.recycle()
                    }
                    isPreloading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isPreloading = false }
            }
        }
    }

    fun preloadBitmapForDelete() {
        if (isPreloadingDelete || preloadedDeleteBitmap != null) return
        val currentImg = centerCard ?: return
        
        isPreloadingDelete = true
        scope.launch(Dispatchers.Default) {
            try {
                var bitmap: Bitmap? = null
                val cacheKey = "card_${currentImg.id}"
                val cachedBitmap = context.imageLoader.memoryCache?.get(
                    MemoryCache.Key(cacheKey)
                )?.bitmap
                
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    val maxSize = 250
                    val scale = minOf(
                        maxSize.toFloat() / cachedBitmap.width,
                        maxSize.toFloat() / cachedBitmap.height,
                        1f
                    )
                    val targetWidth = (cachedBitmap.width * scale).toInt().coerceAtLeast(50)
                    val targetHeight = (cachedBitmap.height * scale).toInt().coerceAtLeast(50)
                    bitmap = Bitmap.createScaledBitmap(cachedBitmap, targetWidth, targetHeight, true)
                }
                
                if (bitmap == null) {
                    withContext(Dispatchers.IO) {
                        bitmap = createGenieBitmapFast(context, currentImg.uri, 250, 250)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (lockedDirection == SwipeDirection.UP) {
                        preloadedDeleteBitmap = bitmap
                    } else {
                        bitmap?.recycle()
                    }
                    isPreloadingDelete = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isPreloadingDelete = false }
            }
        }
    }

    /**
     * 执行删除动画（Genie 效果）
     */
    suspend fun executeDeleteAnimation(playHaptic: Boolean) {
        val currentImg = centerCard ?: return
        
        if (enableSwipeHaptics && playHaptic) {
            HapticFeedback.heavyTap(context)
        }
        
        val targetCenterX: Float
        val targetCenterY: Float
        
        if (trashButtonBounds != Rect.Zero && containerBounds != Rect.Zero) {
            targetCenterX = trashButtonBounds.center.x - containerBounds.left
            targetCenterY = trashButtonBounds.bottom - containerBounds.top
        } else {
            targetCenterX = containerBounds.width * 0.8f
            targetCenterY = with(density) { 60.dp.toPx() }
        }
        
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
        
        val relativeSourceBounds = if (containerBounds != Rect.Zero) {
            Rect(
                left = centerCardBounds.left - containerBounds.left,
                top = centerCardBounds.top - containerBounds.top,
                right = centerCardBounds.right - containerBounds.left,
                bottom = centerCardBounds.bottom - containerBounds.top
            )
        } else {
            centerCardBounds
        }
        
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
            
            val finalOffsetX = targetCenterX - centerCardBounds.center.x + containerBounds.left
            val finalOffsetY = targetCenterY - centerCardBounds.center.y + containerBounds.top
            
            scope.launch { centerDragOffsetX.animateTo(finalOffsetX, tween(animDuration, easing = easeInOut)) }
            scope.launch { centerDragOffsetY.animateTo(finalOffsetY, tween(animDuration, easing = easeInOut)) }
            scope.launch { centerDragScale.animateTo(0.05f, tween(animDuration, easing = easeInOut)) }
            scope.launch { centerDragAlpha.animateTo(0f, tween(animDuration, easing = easeInOut)) }
            
            kotlinx.coroutines.delay(animDuration.toLong())
            onRemove(currentImg)
        }
        
        centerDragOffsetX.snapTo(0f)
        centerDragOffsetY.snapTo(0f)
        centerDragRotation.snapTo(0f)
        centerDragAlpha.snapTo(1f)
        centerDragScale.snapTo(1f)
        lockedDirection = SwipeDirection.NONE
    }

    /**
     * 等待并获取选中标签的最新测量位置
     */
    suspend fun resolveTagBounds(targetIndex: Int, timeoutMs: Long): Rect? {
        val start = SystemClock.uptimeMillis()
        var lastRect: Rect? = null

        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val tagPosition = latestTagPositions[targetIndex]
            if (tagPosition != null && tagPosition.coordinates.isAttached) {
                val rect = tagPosition.coordinates.boundsInRoot()
                lastRect = rect
                val isFresh = tagPosition.updatedAt >= lastSelectionChangeAt
                if (rect != Rect.Zero && isFresh) {
                    return rect
                }
            }
            kotlinx.coroutines.delay(16)
        }
        return lastRect
    }

    /**
     * 执行归类动画
     */
    suspend fun executeGenieAnimation(targetIndex: Int) {
        val currentImg = centerCard ?: return
        
        val isCreateNew = targetIndex == 0
        val targetAlbum = if (!isCreateNew && targetIndex > 0 && targetIndex <= albums.size) {
            albums[targetIndex - 1]
        } else null
        
        if (enableSwipeHaptics) {
            HapticFeedback.heavyTap(context)
        }

        val now = SystemClock.uptimeMillis()
        val shouldWaitLonger = now - lastSelectionChangeAt < 250L
        val tagBounds = resolveTagBounds(
            targetIndex = targetIndex,
            timeoutMs = if (shouldWaitLonger) 600L else 100L
        )
        
        val targetCenterX: Float
        val targetCenterY: Float
        
        if (tagBounds != null && tagBounds != Rect.Zero && containerBounds != Rect.Zero) {
            val scaleCompensation = tagBounds.height * 0.05f
            targetCenterX = tagBounds.center.x - containerBounds.left
            targetCenterY = tagBounds.top - containerBounds.top - scaleCompensation
        } else {
            val tagEstimatedWidth = with(density) { 65.dp.toPx() }
            val tagSpacing = with(density) { 12.dp.toPx() }
            val listPadding = with(density) { 24.dp.toPx() }
            val tagsStartX = listPadding
            targetCenterX = tagsStartX + targetIndex * (tagEstimatedWidth + tagSpacing) + tagEstimatedWidth / 2f
            targetCenterY = containerBounds.height - with(density) { 80.dp.toPx() }
        }
        
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
        
        val relativeSourceBounds = if (containerBounds != Rect.Zero) {
            Rect(
                left = centerCardBounds.left - containerBounds.left,
                top = centerCardBounds.top - containerBounds.top,
                right = centerCardBounds.right - containerBounds.left,
                bottom = centerCardBounds.bottom - containerBounds.top
            )
        } else {
            centerCardBounds
        }
        
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
        // 收牌过渡时：左卡正在滑出，使用 leftCardAlpha 控制淡出
        if (leftCard != null && !isDrawTransitioning) {
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
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = rememberImageBadges(leftCard, showHdrBadges, showMotionBadges)
                )
            }
        } else if (leftCard == null && !isDrawTransitioning) {
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
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        },
                    cornerRadius = 16.dp,
                    elevation = 4.dp,
                    badges = rememberImageBadges(rightCard, showHdrBadges, showMotionBadges)
                )
            }
        } else if (rightCard == null && !isRecallTransitioning) {
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
                                onDragStart = {
                                    isDragging = true
                                    hasDragged = false
                                    velocityTracker.resetTracking()
                                    swipeThresholdHapticTriggered = false
                                    deleteThresholdHapticTriggered = false
                                    classifyThresholdHapticTriggered = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    hasDragged = true
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                                    if (lockedDirection == SwipeDirection.NONE) {
                                        val totalDx = abs(centerDragOffsetX.value + dragAmount.x)
                                        val totalDy = abs(centerDragOffsetY.value + dragAmount.y)

                                        // 更早锁定方向，提高响应速度
                                        if (totalDy > totalDx * 1.3f && dragAmount.y < 0) {
                                            lockedDirection = SwipeDirection.UP
                                        } else if (totalDy > totalDx * 1.3f && dragAmount.y > 0) {
                                            lockedDirection = SwipeDirection.DOWN
                                        } else if (totalDx > 12f || totalDy > 12f) {
                                            lockedDirection = SwipeDirection.HORIZONTAL
                                        }
                                    }

                                    scope.launch {
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
                                                    HapticFeedback.lightTap(context)
                                                }
                                            }
                                            SwipeDirection.DOWN -> {
                                                val newY = (centerDragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                                centerDragOffsetY.snapTo(newY)
                                                centerDragOffsetX.snapTo(centerDragOffsetX.value + dragAmount.x * 0.7f)
                                                
                                                if (!isPreloading && preloadedBitmap == null) {
                                                    preloadBitmapForGenie()
                                                }
                                                
                                                if (newY > classifyThresholdPx && !isClassifyMode) {
                                                    isClassifyMode = true
                                                    classifyStartX = centerDragOffsetX.value
                                                    classifyStartY = newY
                                                    val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                    selectedAlbumIndex = defaultIndex
                                                    lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                    lastSelectedIndex = defaultIndex
                                                    onClassifyModeChange?.invoke(true)
                                                    onSelectedIndexChange?.invoke(defaultIndex)
                                                    if (enableSwipeHaptics) {
                                                        classifyThresholdHapticTriggered = true
                                                        HapticFeedback.mediumTap(context)
                                                    }
                                                }
                                                
                                                if (isClassifyMode && newY < classifyExitThresholdPx) {
                                                    isClassifyMode = false
                                                    classifyThresholdHapticTriggered = false
                                                    selectedAlbumIndex = 0
                                                    classifyStartY = 0f
                                                    lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                    lastSelectedIndex = -1
                                                    onClassifyModeChange?.invoke(false)
                                                    if (enableSwipeHaptics) {
                                                        HapticFeedback.lightTap(context)
                                                    }
                                                    preloadedBitmap?.recycle()
                                                    preloadedBitmap = null
                                                    isPreloading = false
                                                }
                                                
                                                if (isClassifyMode) {
                                                    val relativeX = centerDragOffsetX.value - classifyStartX
                                                    val colOffset = (relativeX / tagSwitchDistanceXPx).toInt()
                                                    
                                                    val relativeY = newY - classifyStartY
                                                    val rowOffset = (relativeY / tagSwitchDistanceYPx).toInt()
                                                    
                                                    val defaultIndex = if (albums.isNotEmpty()) 1 else 0
                                                    val startRow = defaultIndex / TAGS_PER_ROW
                                                    val startCol = defaultIndex % TAGS_PER_ROW
                                                    
                                                    var newCol = startCol + colOffset
                                                    var newRow = startRow + rowOffset
                                                    
                                                    val totalRows = (totalTags + TAGS_PER_ROW - 1) / TAGS_PER_ROW
                                                    newRow = newRow.coerceIn(0, totalRows - 1)
                                                    newCol = newCol.coerceIn(0, TAGS_PER_ROW - 1)
                                                    
                                                    var newIndex = newRow * TAGS_PER_ROW + newCol
                                                    newIndex = newIndex.coerceIn(0, totalTags - 1)
                                                    
                                                    if (newIndex != selectedAlbumIndex) {
                                                        selectedAlbumIndex = newIndex
                                                        lastSelectionChangeAt = SystemClock.uptimeMillis()
                                                        onSelectedIndexChange?.invoke(newIndex)
                                                        if (enableSwipeHaptics && newIndex != lastSelectedIndex) {
                                                            lastSelectedIndex = newIndex
                                                            HapticFeedback.lightTap(context)
                                                        }
                                                    }
                                                }
                                                
                                                val scaleProgress = (newY / (screenHeightPx * 0.2f)).coerceIn(0f, 1f)
                                                centerDragScale.snapTo(1f - scaleProgress * 0.1f)
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
                                    val velocity = velocityTracker.calculateVelocity()

                                    scope.launch {
                                        when (lockedDirection) {
                                            SwipeDirection.UP -> {
                                                if (abs(centerDragOffsetY.value) > deleteThresholdPx ||
                                                    abs(velocity.y) > velocityThreshold
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
                                                val velocityX = velocity.x
                                                
                                                // 摸牌样式：右滑发牌，左滑收牌
                                                if (offsetX > 0) {
                                                    // 右滑 → 发牌
                                                    val triggered = offsetX > swipeThresholdPx || velocityX > velocityThreshold
                                                    if (triggered && drawState.canDraw(images.size)) {
                                                        executeDrawAnimation()
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
                                                resetDragState()
                                            }
                                        }
                                        hasDragged = false
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    hasDragged = false
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
        
        // ========== Genie Effect 覆盖层 ==========
        if (genieController.isAnimating) {
            GenieEffectOverlay(
                bitmap = genieController.bitmap,
                sourceBounds = genieController.sourceBounds,
                targetX = genieController.targetX,
                targetY = genieController.targetY,
                progress = genieController.progress,
                screenHeight = genieController.screenHeight,
                direction = genieController.direction,
                modifier = Modifier.zIndex(10f)
            )
        }
    }
}

