package com.tabula.v3.ui.util

import android.content.Context
import android.net.Uri
import coil.request.CachePolicy
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.cardMemoryCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C3/T06-VIEWERKEY：deck 查看器底层缩略图请求的缓存键一致性。
 *
 * deck 两种卡片模式的卡片位图由 cardMemoryCacheKey（卷限定 URI +
 * DATE_MODIFIED 内容版本）生产/消费（ImageCard 显示请求与 PreloadingManager
 * 预载共用）。查看器（ViewerOverlay）底层缩略图请求经
 * deckViewerThumbnailRequest 构造，本测试以真实生产请求断言：
 *
 * - 跨卷同数值 ID 行不得共享缩略图缓存键（查看器自写自读键下串图）；
 * - 同一照片编辑（DATE_MODIFIED 变化）后键必须变化（陈旧位图不再命中）;
 * - 请求键必须与卡片位图键一致（deck→查看器温命中，修复 card_/album_grid_
 *   键错配）——修复前该断言随错配缺陷红；
 * - 单卷控制组：同一条目重复构造键稳定。
 *
 * 证据边界：本测试只钉死生产请求的键与策略（可编译执行的真实 Coil
 * ImageRequest）；Compose 重组/实际缓存命中路径/真实解码无单测基建，
 * 由源码审读核验，不在此宣称。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DeckViewerThumbnailKeyTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun row(volume: String, id: Long, dateModifiedMs: Long = 1_000L) = ImageFile(
        id = id,
        uri = Uri.parse("content://media/$volume/images/media/$id"),
        displayName = "img-$volume-$id.jpg",
        dateModified = dateModifiedMs,
        size = 100L,
        width = 64,
        height = 64,
        bucketDisplayName = "Camera",
        volumeName = volume
    )

    @Test
    fun crossVolumeSameIdRowsGetDistinctViewerThumbnailKeys() {
        // 卷 A 与卷 B 的同数值 ID 是两张不同照片：查看器底层缩略图键必须
        // 彼此隔离——否则先看 A5 再看 B5 时，B5 的占位层命中 A5 的缓存位图。
        val a = deckViewerThumbnailRequest(context, row("external_primary", 5L))
        val b = deckViewerThumbnailRequest(context, row("external_secondary", 5L))

        assertNotEquals(a.memoryCacheKey, b.memoryCacheKey)
        assertNotEquals(a.diskCacheKey, b.diskCacheKey)
    }

    @Test
    fun contentEditChangesViewerThumbnailKeys() {
        // 同一照片编辑内容（DATE_MODIFIED 变化）：键必须变化——否则查看器
        // 占位层继续命中编辑前的陈旧位图。
        val before = deckViewerThumbnailRequest(context, row("external_primary", 5L, 1_000L))
        val after = deckViewerThumbnailRequest(context, row("external_primary", 5L, 2_000L))

        assertNotEquals(before.memoryCacheKey, after.memoryCacheKey)
        assertNotEquals(before.diskCacheKey, after.diskCacheKey)
    }

    @Test
    fun viewerThumbnailKeyMatchesDeckCardBitmapKey() {
        // 生产消费键匹配：查看器占位层请求键必须等于 deck 卡片位图键
        // （ImageCard 显示请求与 PreloadingManager 预载的 cardMemoryCacheKey），
        // 从卡片点开查看器时占位层温命中已解码的卡片位图。Coil 的
        // memoryCacheKey 是 MemoryCache.Key（取 .key 字符串比较），
        // diskCacheKey 本身就是 String，直接比较。
        val image = row("external_primary", 5L)
        val request = deckViewerThumbnailRequest(context, image)

        assertEquals(cardMemoryCacheKey(image), request.memoryCacheKey?.key)
        assertEquals(cardMemoryCacheKey(image), request.diskCacheKey)
    }

    @Test
    fun singleVolumeRowKeepsStableViewerThumbnailKey() {
        // 单卷控制组：同一条目重复构造，键稳定（无身份歧义时的正常行为）。
        val image = row("external_primary", 7L)
        val first = deckViewerThumbnailRequest(context, image)
        val second = deckViewerThumbnailRequest(context, row("external_primary", 7L))

        assertEquals(first.memoryCacheKey, second.memoryCacheKey)
        assertEquals(first.diskCacheKey, second.diskCacheKey)
    }

    @Test
    fun viewerThumbnailRequestKeepsPlaceholderDecodeSpec() {
        // 占位层解码规格保持：RGB_565、非硬件位图、INEXACT；内存只读借用
        //（不回写挤占卡片 720px 条目）、磁盘共享来源字节。请求尺寸（240×240）
        // 与无渐变在 Coil 2.7 的 ImageRequest 上不作为属性暴露，由函数源码
        // 审读钉住，不在本测试宣称。
        val request = deckViewerThumbnailRequest(context, row("external_primary", 5L))

        assertEquals(android.graphics.Bitmap.Config.RGB_565, request.bitmapConfig)
        assertEquals(false, request.allowHardware)
        assertEquals(coil.size.Precision.INEXACT, request.precision)
        assertEquals(CachePolicy.READ_ONLY, request.memoryCachePolicy)
        assertEquals(CachePolicy.ENABLED, request.diskCachePolicy)
    }
}
