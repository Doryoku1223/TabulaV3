package com.tabula.v3.ui.util

import android.content.Context
import android.net.Uri
import coil.size.Size
import com.tabula.v3.data.model.ImageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C1/T03-COVERKEY：封面请求构造器（OtherAlbumsScreen AlbumRow 与
 * CategorizedAlbumsView SystemBucketRow 等封面展示面的唯一生产入口）。
 *
 * 既有缺陷（本文件基线形状的忠实复刻）：请求仅以 URI 为数据源、无显式
 * memoryCacheKey/diskCacheKey——同 URI 不同内容版本（DATE_MODIFIED 变化）
 * 共享 Coil 默认键（陈旧封面）；跨卷同数值 ID 行即使 URI 卷限定，键也不含
 * 身份与版本语义。回归断言请求缓存键随内容版本与卷变化。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CoverImageRequestTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun row(volume: String, id: Long, dateModifiedMs: Long) = ImageFile(
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
    fun sameUriDifferentDateModifiedChangesRequestCacheKeys() {
        // 同 URI（同卷同 ID）、内容编辑（dateModified 变化）：请求的内存/磁盘
        // 缓存键必须变化——否则编辑后继续显示陈旧封面。
        val before = coverImageRequest(
            context, row("external_primary", 10L, 1_000L),
            "other_albums_cover_", size = Size(120, 120), rgb565 = true, inexact = true
        )
        val after = coverImageRequest(
            context, row("external_primary", 10L, 9_999L),
            "other_albums_cover_", size = Size(120, 120), rgb565 = true, inexact = true
        )
        assertNotEquals(
            "内容编辑后请求内存缓存键必须变化",
            before.memoryCacheKey, after.memoryCacheKey
        )
        assertNotEquals(
            "内容编辑后请求磁盘缓存键必须变化",
            before.diskCacheKey, after.diskCacheKey
        )
    }

    @Test
    fun crossVolumeSameIdRequestKeysDiffer() {
        val primary = coverImageRequest(
            context, row("external_primary", 10L, 1_000L),
            "system_bucket_cover_", crossfade = true
        )
        val secondary = coverImageRequest(
            context, row("external_secondary", 10L, 1_000L),
            "system_bucket_cover_", crossfade = true
        )
        assertNotEquals(
            "跨卷同数值 ID 行的请求缓存键必须隔离",
            primary.memoryCacheKey, secondary.memoryCacheKey
        )
        assertNotEquals(
            primary.diskCacheKey, secondary.diskCacheKey
        )
    }

    @Test
    fun identicalInputProducesStableKeys() {
        // 同输入稳定性控制：同身份同版本两次构造键一致（缓存命中）。
        val a = coverImageRequest(
            context, row("external_primary", 10L, 1_000L),
            "other_albums_cover_", size = Size(120, 120)
        )
        val b = coverImageRequest(
            context, row("external_primary", 10L, 1_000L),
            "other_albums_cover_", size = Size(120, 120)
        )
        assertEquals(a.memoryCacheKey, b.memoryCacheKey)
        assertEquals(a.diskCacheKey, b.diskCacheKey)
    }
}
