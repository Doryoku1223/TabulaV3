package com.tabula.v3.ui.util

import android.content.Context
import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C4/T07 任务 D：查看器高清层请求的内容版本刷新。
 *
 * 原缺陷：高清请求无显式缓存键（Coil 默认内存键=URI 字符串，不含内容
 * 版本）——同 URI 原地编辑（DATE_MODIFIED 更新）后旧高清位图被持续命中，
 * 新图片被旧高清缓存覆盖。修复：显式键 = gridThumbnailCacheKey
 * （卷|ID 身份 + DATE_MODIFIED，viewer_hd_ 前缀）。
 *
 * 证据边界：本测试钉住请求属性（键/解码规格）；实际显示与缓存命中路径
 * （像素级刷新、Coil isSizeValid 尺寸维度）无单测基建，由调用链审计覆盖，
 * 不把键不同直接写成像素刷新已验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ViewerHdRequestTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun row(volume: String, id: Long, dateModified: Long = 1_000L) = ImageFile(
        id = id,
        uri = Uri.parse("content://media/$volume/images/media/$id"),
        displayName = "img-$volume-$id.jpg",
        dateModified = dateModified,
        size = 100L,
        width = 2000,
        height = 1500,
        bucketDisplayName = "Camera",
        volumeName = volume
    )

    private val hdSize = coil.size.Size(1080, 1080)

    @Test
    fun contentEditChangesHdRequestKeys() {
        // 同 URI（同卷同 ID）原地编辑（DATE_MODIFIED 更新）：高清请求键必须
        // 变化——旧高清位图不得被继续命中
        val before = viewerHdRequest(context, row("external_primary", 5L, 1_000L), hdSize)
        val after = viewerHdRequest(context, row("external_primary", 5L, 2_000L), hdSize)

        assertNotEquals(before.memoryCacheKey, after.memoryCacheKey)
        assertNotEquals(before.diskCacheKey, after.diskCacheKey)
    }

    @Test
    fun crossVolumeSameIdRowsGetDistinctHdKeys() {
        val a = viewerHdRequest(context, row("external_primary", 5L), hdSize)
        val b = viewerHdRequest(context, row("external_secondary", 5L), hdSize)

        assertNotEquals(a.memoryCacheKey, b.memoryCacheKey)
        assertNotEquals(a.diskCacheKey, b.diskCacheKey)
    }

    @Test
    fun unchangedImageKeepsHdKeysForCacheReuse() {
        // 未更新图片：同尺寸重复构造键不变（缓存照常复用，不逐帧失效）
        val first = viewerHdRequest(context, row("external_primary", 5L), hdSize)
        val second = viewerHdRequest(context, row("external_primary", 5L), hdSize)

        assertEquals(first.memoryCacheKey, second.memoryCacheKey)
        assertEquals(first.diskCacheKey, second.diskCacheKey)
    }

    @Test
    fun hdRequestKeepsDecodeSpecAndCarriesVersionedKey() {
        val request = viewerHdRequest(context, row("external_primary", 5L), hdSize)

        // 解码规格保持：ARGB_8888、硬件位图默认允许、INEXACT；请求尺寸为
        // 调用方动态目标（Coil 2.7 的 size 属性不暴露，由函数源码审读钉住）
        assertEquals(android.graphics.Bitmap.Config.ARGB_8888, request.bitmapConfig)
        assertEquals(true, request.allowHardware)
        assertEquals(coil.size.Precision.INEXACT, request.precision)
        // 键携带身份与内容版本（卷|ID_DATE_MODIFIED，viewer_hd_ 前缀）
        assertEquals(
            "viewer_hd_external_primary|5_1000",
            request.memoryCacheKey?.key
        )
        assertEquals("viewer_hd_external_primary|5_1000", request.diskCacheKey)
        // 与底层占位层键不同命名空间（不互挤）
        val placeholder = deckViewerThumbnailRequest(context, row("external_primary", 5L))
        assertFalse(request.memoryCacheKey == placeholder.memoryCacheKey)
    }
}
