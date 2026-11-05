package com.tabula.v3.di

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.IntSize
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Size
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 图片预加载管理器
 *
 * 针对卡片堆叠滑动场景优化：
 * - 预加载当前索引的前后各 N 张图片
 * - 使用较小的卡片尺寸（720px）加快解码速度
 * - 支持暂停/恢复预加载（滑动时暂停）
 * - 使用防抖避免频繁触发预加载
 *
 * @param context 应用上下文
 * @param preloadRange 预加载范围（前后各多少张），默认 1（更保守）
 */
class PreloadingManager(
    private val context: Context,
    private val preloadRange: Int = 1,  // 从 2 降到 1，减少内存压力
    providedLoader: coil.ImageLoader? = null,
    // C4/T07 任务 E：测试注入确定性调度 scope（防抖时序不再依赖真实 IO
    // 调度与 Thread.sleep）；生产路径不传，默认 scope 行为不变
    providedScope: CoroutineScope? = null
) {
    private val scope: CoroutineScope = providedScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageLoader: coil.ImageLoader = providedLoader ?: CoilSetup.getImageLoader(context)

    private val lock = Any()
    private data class Active(val token: Long, val identity: String, val cacheResult: Boolean, var disposable: Disposable? = null)
    private val active = mutableMapOf<Int, Active>()
    private val completed = mutableSetOf<String>()
    private var nextToken = 0L
    private var generation = 0L
    private var disposed = false
    private val isPaused = AtomicBoolean(false)
    private var debounceJob: Job? = null
    private val debounceDelay = 100L

    fun pause() {
        isPaused.set(true)
        synchronized(lock) { generation++; debounceJob?.cancel() }
    }

    fun resume() { isPaused.set(false) }

    fun preloadAround(images: List<ImageFile>, currentIndex: Int, cardSize: IntSize) {
        if (isPaused.get()) return
        val snapshot = images.toList()
        synchronized(lock) {
            if (disposed || isPaused.get()) return
            val runGeneration = ++generation
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceDelay)
                synchronized(lock) {
                    if (disposed || isPaused.get() || runGeneration != generation) return@synchronized
                    val start = (currentIndex - preloadRange).coerceAtLeast(0)
                    val end = (currentIndex + preloadRange).coerceAtMost(snapshot.lastIndex)
                    val window = if (start <= end) (start..end).mapNotNull { index ->
                        snapshot.getOrNull(index)?.let { index to requestIdentity(it) }
                    }.toMap() else emptyMap()
                    val windowKeys = window.values.toSet()
                    completed.retainAll(windowKeys)
                    val stale = active.filter { (index, request) -> window[index] != request.identity }
                    stale.forEach { (index, request) -> active.remove(index); request.disposable?.dispose() }
                    window.forEach { (index, identity) ->
                        if (identity !in completed && active[index]?.identity != identity) {
                            enqueue(index, snapshot[index], identity)
                        }
                    }
                }
            }
        }
    }

    // cardSize 为兼容调用方保留；沿用固定 720px 上限，身份键记录实际解码尺寸。
    private fun loadSize(image: ImageFile): Size = if (!image.hasDimensionInfo) {
        Size(540, 720)
    } else if (image.aspectRatio < 1f) {
        Size((720 * image.aspectRatio).toInt().coerceAtLeast(270), 720)
    } else {
        Size(720, (720 / image.aspectRatio).toInt().coerceAtLeast(270))
    }

    private fun requestIdentity(image: ImageFile): String = "${cardMemoryCacheKey(image)}|${loadSize(image)}|hardware=false"

    // lock is held; callbacks may arrive synchronously during enqueue.
    private fun enqueue(index: Int, image: ImageFile, identity: String, cacheResult: Boolean = true) {
        val token = ++nextToken
        val requestState = Active(token, identity, cacheResult)
        active[index] = requestState
        try {
            val request = ImageRequest.Builder(context)
                .data(image.uri)
                .size(loadSize(image))
                .memoryCacheKey(cardMemoryCacheKey(image))
                .diskCacheKey(cardMemoryCacheKey(image))
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .allowHardware(false)
                .listener(
                    onSuccess = { _, _ -> finish(index, token, true) },
                    onError = { _, _ -> finish(index, token, false) }
                ).build()
            val disposable = imageLoader.enqueue(request)
            if (active[index] === requestState) requestState.disposable = disposable else disposable.dispose()
        } catch (e: Exception) {
            if (active[index] === requestState) active.remove(index)
            Log.w(TAG, "预加载失败: ${image.displayName}", e)
        }
    }

    private fun finish(index: Int, token: Long, success: Boolean) {
        synchronized(lock) {
            val request = active[index] ?: return
            if (request.token != token || disposed) return
            active.remove(index)
            if (success && request.cacheResult) completed.add(request.identity)
        }
    }

    fun preloadImmediate(images: List<ImageFile>, indices: List<Int>, cardSize: IntSize) {
        synchronized(lock) {
            if (disposed || isPaused.get()) return
            indices.forEach { index ->
                val image = images.getOrNull(index) ?: return@forEach
                val identity = requestIdentity(image)
                val previous = active.remove(index)
                previous?.disposable?.dispose()
                enqueue(index, image, identity, cacheResult = false)
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            generation++
            debounceJob?.cancel()
            val old = active.values.toList()
            active.clear()
            completed.clear()
            old.forEach { it.disposable?.dispose() }
        }
    }

    fun dispose() {
        synchronized(lock) {
            if (disposed) return
            disposed = true
            generation++
            debounceJob?.cancel()
            val old = active.values.toList()
            active.clear()
            completed.clear()
            old.forEach { it.disposable?.dispose() }
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "PreloadingManager"
    }
}

/**
 * T06b：卡片显示/预加载共用的缓存请求键，含内容版本（DATE_MODIFIED 毫秒，T03 契约）——
 * 同 id 内容编辑后自然失效。所有 "card_" 键的读写必须经此函数，避免各处键格式漂移。
 */
fun cardMemoryCacheKey(image: ImageFile): String = "card_${image.uri}_${image.dateModified}"
