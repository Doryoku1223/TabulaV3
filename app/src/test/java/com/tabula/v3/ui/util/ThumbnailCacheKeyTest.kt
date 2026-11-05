package com.tabula.v3.ui.util

import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C1/T03-THUMBKEY：图集/系统图集网格缩略图 Coil 缓存键必须携带完整媒体
 * 身份（卷|ID）与内容版本（DATE_MODIFIED 毫秒）。
 *
 * 旧口径 `"prefix_${image.id}"` 的两个缺陷（本用例的有效反例）：
 * 1. 不同卷的同数值 ID 行共享缓存条目 → 缩略图串图；
 * 2. 同一照片编辑后（内容版本提升）键不变 → 网格显示陈旧缩略图。
 *
 * 范围声明：本测试钉死键派生函数（两处 PhotoGridItem 的实际生产键源）。
 * LazyGrid key / 滚动复用 / 重组行为无 Compose 测试基建，由审读核验——
 * 键函数单测不能宣称已证明界面无重复 key 崩溃，该范围如实登记于 PR。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ThumbnailCacheKeyTest {
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
    fun crossVolumeSameIdRowsHaveDistinctKeys() {
        val primary = row("external_primary", 10L, 1_000L)
        val secondary = row("external_secondary", 10L, 1_000L)
        assertNotEquals(
            "跨卷同数值 ID 行的缩略图键必须可区分（不得串图）",
            gridThumbnailCacheKey("album_grid_", primary),
            gridThumbnailCacheKey("album_grid_", secondary)
        )
        assertNotEquals(
            "系统图集网格同样按身份区分",
            gridThumbnailCacheKey("sys_grid_", primary),
            gridThumbnailCacheKey("sys_grid_", secondary)
        )
    }

    @Test
    fun contentEditChangesKey() {
        // TC58 同族：同媒体 ID 改内容（DATE_MODIFIED 提升）→ 缩略图键必须
        // 变化，网格重新加载新内容而非显示陈旧条目。
        val before = row("external_primary", 10L, 1_000L)
        val after = row("external_primary", 10L, 9_999L)
        assertNotEquals(
            "内容版本变化必须改变缩略图键",
            gridThumbnailCacheKey("album_grid_", before),
            gridThumbnailCacheKey("album_grid_", after)
        )
    }

    @Test
    fun sameIdentityAndContentIsStable() {
        val a = row("external_primary", 10L, 1_000L)
        val b = row("external_primary", 10L, 1_000L)
        assertEquals(
            "同身份同版本的键必须稳定（滚动复用命中缓存）",
            gridThumbnailCacheKey("album_grid_", a),
            gridThumbnailCacheKey("album_grid_", b)
        )
        assertEquals(
            "前缀隔离不同网格的缓存命名空间",
            "album_grid_" + gridThumbnailCacheKey("", a),
            gridThumbnailCacheKey("album_grid_", a)
        )
    }
}
