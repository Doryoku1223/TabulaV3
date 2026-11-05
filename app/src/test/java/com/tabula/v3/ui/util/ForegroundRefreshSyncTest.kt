package com.tabula.v3.ui.util

import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.AlbumCleanupBatch
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.ui.components.buildMonthInfos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C4/T07 P2 复审项：前台刷新新快照向可见消费面同步的纯逻辑。
 *
 * 覆盖：同身份外部编辑后的版本更新（dateModified/尺寸）；失效行剔除与
 * 顺序保持；跨卷同 ID 不误删不误换（卷|ID 键隔离）；清理批次的组边界
 * 重建/整组失效空段/完成标记 groupIds 语义；索引前移对齐；查看器身份
 * 失效关闭与存活更新。
 *
 * 证据边界：纯消费路径逻辑（DeckScreen 同步 effect 的内核）；Compose
 * 重组/实际滚动位置/设备端权限切换由审读与既有设备套件覆盖，不在此宣称。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ForegroundRefreshSyncTest {

    private fun row(volume: String, id: Long, dateModified: Long = 1_000L, width: Int = 64) = ImageFile(
        id = id,
        uri = Uri.parse("content://media/$volume/images/media/$id"),
        displayName = "img-$volume-$id.jpg",
        dateModified = dateModified,
        size = 100L,
        width = width,
        height = 64,
        bucketDisplayName = "Camera",
        volumeName = volume
    )

    @Test
    fun survivorIsReplacedByLatestSnapshotRowAfterExternalEdit() {
        // 同身份外部编辑（dateModified/尺寸变化）：批次中的存活行必须更新为
        // 快照最新对象——不能继续携带编辑前的旧版本信息。
        val old = row("external_primary", 5L, dateModified = 1_000L, width = 64)
        val edited = row("external_primary", 5L, dateModified = 2_000L, width = 128)
        val batch = listOf(row("external_primary", 1L), old, row("external_primary", 7L))
        val snapshot = listOf(row("external_primary", 1L), edited, row("external_primary", 7L))

        val synced = syncRowsToSnapshot(batch, snapshot)

        assertEquals(3, synced.size)
        assertSame(edited, synced[1])
        assertEquals(2_000L, synced[1].dateModified)
        assertEquals(128, synced[1].width)
    }

    @Test
    fun vanishedRowsAreRemovedAndOrderIsPreserved() {
        val snapshot = listOf(row("external_primary", 7L), row("external_primary", 1L))
        val batch = listOf(row("external_primary", 1L), row("external_primary", 5L), row("external_primary", 7L))

        val synced = syncRowsToSnapshot(batch, snapshot)

        assertEquals(2, synced.size)
        assertEquals("external_primary|1", synced[0].mediaIdentityKey())
        assertEquals("external_primary|7", synced[1].mediaIdentityKey())
    }

    @Test
    fun crossVolumeSameIdRowsStayIndependent() {
        // 跨卷同数值 ID：批次中的 B 卷 5 号行不因 A 卷 5 号行存在于快照而
        // 被误保留（顶替/误换），也不因 A 卷行被编辑而被误更新；两行同时在
        // 快照中时各自映射到本卷最新行。
        val a5Old = row("external_primary", 5L, dateModified = 1_000L)
        val b5 = row("external_secondary", 5L, dateModified = 1_000L)
        val a5New = row("external_primary", 5L, dateModified = 3_000L)

        // 快照只有 A 卷 5 号：B 卷批次行必须被剔除（不回退裸 ID 匹配）
        val syncedOnlyA = syncRowsToSnapshot(listOf(b5), listOf(a5New))
        assertEquals(0, syncedOnlyA.size)

        // 快照两卷都有：各自映射本卷行
        val syncedBoth = syncRowsToSnapshot(listOf(a5Old, b5), listOf(a5New, b5))
        assertEquals(2, syncedBoth.size)
        assertSame(a5New, syncedBoth[0])
        assertSame(b5, syncedBoth[1])
    }

    @Test
    fun cleanupBatchSyncWithholdsIncompleteGroupsAndRebuildsBoundaries() {
        val g1a = row("external_primary", 1L)
        val g1b = row("external_primary", 2L)
        val g2a = row("external_primary", 3L)
        val g2b = row("external_primary", 4L)
        val g3a = row("external_primary", 5L)
        val g3b = row("external_primary", 6L)
        val batch = AlbumCleanupBatch(
            images = listOf(g1a, g1b, g2a, g2b, g3a, g3b),
            groupIds = listOf("g1", "g2", "g3"),
            groupBoundaries = listOf(0, 2, 4)
        )
        // 新快照：1 号被编辑（版本更新）、3/4/5 号被外部删除、2/6 号存活
        val snapshot = listOf(
            row("external_primary", 1L, dateModified = 9_000L),
            g1b,
            g3b
        )

        val synced = syncCleanupBatchToSnapshot(batch, snapshot)

        assertEquals(listOf("g1"), synced.groupIds)
        // 组 1：1 号更新为最新版本、2 号存活
        assertEquals(9_000L, synced.images[0].dateModified)
        assertSame(g1b, synced.images[1])
        assertEquals(2, synced.images.size)
        assertEquals(listOf(0), synced.groupBoundaries)
        assertEquals("g1", synced.getGroupIdForIndex(0))
        assertEquals("g1", synced.getGroupIdForIndex(1))
        assertNull(synced.getGroupIdForIndex(2))
    }

    @Test
    fun alreadyReviewedOrDeletedMemberDoesNotHoldRemainingPendingWork() {
        val removed = row("external_primary", 1L)
        val kept = row("external_primary", 2L)
        val pending = row("external_secondary", 1L)
        val batch = AlbumCleanupBatch(listOf(removed, kept, pending), listOf("g1"), listOf(0))
        val synced = syncCleanupBatchToSnapshot(batch, listOf(kept, pending), listOf(pending))
        assertEquals(listOf("g1"), synced.groupIds)
        assertEquals(listOf(kept, pending), synced.images)
        assertEquals(listOf(0), synced.groupBoundaries)
    }

    @Test
    fun monthMembershipReturnsAfterPartialAccessWithoutRepeatingTheNarrowedSubset() {
        val a = row("external_primary", 1L)
        val b = row("external_secondary", 1L)
        val month = buildMonthInfos(listOf(a, b)).single()
        val partial = refreshedCleanupMonth(month, listOf(a))
        assertEquals(listOf(a), partial.images)
        assertEquals(1, partial.imageCount)

        val edited = b.copy(dateModified = 2_000L, width = 128)
        val restored = refreshedCleanupMonth(partial, listOf(a, edited))
        assertEquals(setOf(a.mediaIdentityKey(), b.mediaIdentityKey()), restored.images.map { it.mediaIdentityKey() }.toSet())
        assertSame(edited, restored.images.first { it.mediaIdentityKey() == b.mediaIdentityKey() })
        assertEquals(2, restored.imageCount)
        val empty = refreshedCleanupMonth(restored, emptyList())
        assertTrue(empty.images.isEmpty())
        assertEquals(0, empty.imageCount)
        assertNull(empty.coverImageId)
        assertEquals(restored, refreshedCleanupMonth(empty, listOf(a, edited)))
    }

    @Test
    fun mixedBatchOnlyCompletesGroupsWhosePendingMembersRemainVisible() {
        val a = row("external_primary", 1L)
        val b = row("external_secondary", 1L)
        val batch = AlbumCleanupBatch(listOf(a, b), listOf("g1", "g2"), listOf(0, 1))

        val synced = syncCleanupBatchToSnapshot(batch, listOf(a))

        assertEquals(listOf("g1"), synced.groupIds)
        assertEquals(listOf(a), synced.images)
        assertEquals(listOf(0), synced.groupBoundaries)
    }

    @Test
    fun partiallyVisibleGroupIsHeldInsteadOfCompletingItsUnseenMembers() {
        val a = row("external_primary", 1L)
        val b = row("external_secondary", 1L)
        val batch = AlbumCleanupBatch(listOf(a, b), listOf("g1"), listOf(0))

        val synced = syncCleanupBatchToSnapshot(batch, listOf(a))

        assertTrue(synced.groupIds.isEmpty())
        assertTrue(synced.images.isEmpty())
        assertTrue(synced.groupBoundaries.isEmpty())
    }

    @Test
    fun cleanupBatchSyncReturnsSameInstanceWhenNothingChanged() {
        val batch = AlbumCleanupBatch(
            images = listOf(row("external_primary", 1L), row("external_primary", 2L)),
            groupIds = listOf("g1"),
            groupBoundaries = listOf(0)
        )

        val synced = syncCleanupBatchToSnapshot(batch, listOf(row("external_primary", 1L), row("external_primary", 2L)))

        assertSame(batch, synced)
    }

    @Test
    fun relocatedIndexKeepsTheViewedCardStable() {
        // C-1 主场景（清理批内删 A 留 B 后提交 A，视图仍在 C）：deck 空间
        // 已剔除用户已处理行，同步以当前行为锚——当前行存活时视图不回跳、
        // 不复现已删卡片。
        val b = row("external_primary", 2L)
        val c = row("external_primary", 3L)
        val d = row("external_primary", 4L)
        val deckRows = listOf(b, c, d)          // deck 空间：A 已被用户删除
        val syncedDeck = listOf(b, c, d)        // 同步后内容不变（全部存活）

        assertEquals(1, relocatedIndexAfterSync(deckRows, syncedDeck, currentIndex = 1))
    }

    @Test
    fun relocatedIndexFollowsAnchorWhenEarlierRowsVanish() {
        // 锚行之前的行外部失效：视图跟随同一张卡（索引前移）
        val b = row("external_primary", 2L)
        val c = row("external_primary", 3L)
        val deckRows = listOf(row("external_primary", 1L), b, c)
        val syncedDeck = listOf(b, c)

        assertEquals(0, relocatedIndexAfterSync(deckRows, syncedDeck, currentIndex = 1))
    }

    @Test
    fun relocatedIndexFallsForwardWhenAnchorVanishes() {
        // 锚行自身外部失效：回退到锚位起（含）第一个存活行；其后无存活行钳到末张
        val a = row("external_primary", 1L)
        val c = row("external_primary", 3L)
        val d = row("external_primary", 4L)
        val deckRows = listOf(a, row("external_primary", 2L), c, d)
        val syncedDeck = listOf(a, c, d) // 2 号失效

        assertEquals(1, relocatedIndexAfterSync(deckRows, syncedDeck, currentIndex = 1)) // 锚=2 号失效 → C
        assertEquals(0, relocatedIndexAfterSync(deckRows, listOf(a), currentIndex = 1))  // 锚及其后全失效 → 末张
    }

    @Test
    fun viewerImageReturnsNullWhenIdentityGoneAndLatestRowWhenAlive() {
        val opened = row("external_primary", 5L, dateModified = 1_000L)
        val edited = row("external_primary", 5L, dateModified = 2_000L)
        val otherVolume = row("external_secondary", 5L, dateModified = 2_000L)

        // 身份已不存在（快照只有另一卷的同 ID 行）：关闭查看器，不误定位
        assertNull(refreshedViewerImage(opened, listOf(otherVolume)))
        // 身份仍存在：更新为最新对象（调用方 copy(image=...) 保持查看位置）
        assertSame(edited, refreshedViewerImage(opened, listOf(edited)))
        assertTrue(refreshedViewerImage(opened, listOf(row("external_primary", 5L)))!!.dateModified == 1_000L)
        assertFalse(refreshedViewerImage(opened, emptyList()) != null)
    }
}
