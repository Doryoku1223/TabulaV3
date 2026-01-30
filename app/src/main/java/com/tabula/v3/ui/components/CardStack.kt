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
import coil.imageLoader
import coil.memory.MemoryCache
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.ui.util.HapticFeedback
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
    val tagSwitchDistancePx = with(density) { 18.dp.toPx() }  // 切换标签的拖动距离（更灵敏）

    // 动画时长
    val shuffleAnimDuration = 120
    val genieAnimDuration = 380  // Genie动画时长（与GenieAnimationController同步）

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
                                        } else if (totalDy > totalDx * 1.5f && dragAmount.y > 0 && albums.isNotEmpty()) {
                                            // 下滑且有图集可选
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
                                                
                                                // 进入归类模式
                                                if (newY > classifyThresholdPx && !isClassifyMode) {
                                                    isClassifyMode = true
                                                    classifyStartX = dragOffsetX.value  // 记录进入归类模式时的X位置
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
                                                
                                                // 归类模式下，根据X方向拖动切换标签
                                                if (isClassifyMode && albums.isNotEmpty()) {
                                                    // 计算相对于进入归类模式时的X偏移
                                                    val relativeX = dragOffsetX.value - classifyStartX
                                                    // 每移动一定距离切换一个标签
                                                    val indexOffset = (relativeX / tagSwitchDistancePx).toInt()
                                                    // 计算新的选中索引
                                                    // 索引0是新建，索引1到albums.size是相册
                                                    // 默认从索引1（第一个相册）开始
                                                    val maxIndex = albums.size  // 最大索引（最后一个相册）
                                                    val newIndex = (1 + indexOffset).coerceIn(0, maxIndex)
                                                    
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

