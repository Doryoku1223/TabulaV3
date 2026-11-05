package com.tabula.v3.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.tabula.v3.data.cache.ImageHashCache
import com.tabula.v3.data.cache.toHashKey
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.ui.util.syncCleanupBatchToSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C4/T07 P2：前台刷新后，专清引擎的实际取批/断点恢复消费入口按最新快照
 * （完整媒体身份）校验——cachedGroups 的行对象停留在分析/恢复时刻，后续
 * 取批、重新预取、断点恢复不得返回已删除行或外部编辑前的旧版本对象。
 *
 * 夹具：真实 AlbumCleanupEngine + 真实 AppPreferences。组经分析结果持久化
 * 通道直接种子（与 analyzeAlbum 落盘同一组写入口：组目录 + 成员身份键），
 * getNextBatch 走真实 restoreGroupsFromPrefs 恢复路径；"后台变化"以刷新后
 * 的 allImages 参数表达（删除 = 行缺席、编辑 = 同身份新对象），组对象保持
 * 种子时刻——精确复现 in-memory cachedGroups 的陈旧行路径。
 *
 * 已知边界：DeckScreen 同步 effect 的空快照分支（isLoading 区分）为接线层，
 * 无 Compose 单测基建，由独立审计推演 + 设备套件覆盖（如实登记）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AlbumCleanupForegroundRefreshTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var preferences: AppPreferences
    private val albumId = "fg-refresh-album"

    @Before
    fun setUp() {
        preferences = AppPreferences.getInstance(context)
        preferences.clearAllAlbumCleanupState()
        runBlocking { RecycleBinManager.getInstance(context).clearRecycleBin() }
    }

    private fun row(id: Long, volume: String = "external_primary", dateModified: Long = 1_000L + id) = ImageFile(
        id = id,
        uri = Uri.parse("content://media/$volume/images/media/$id"),
        displayName = "img-$volume-$id.jpg",
        dateModified = dateModified,
        size = 100L,
        width = 64,
        height = 64,
        bucketDisplayName = "source",
        volumeName = volume,
        relativePath = "Pictures/source/"
    )

    /** 单成员组便于精确计数；大组用 11 行（> SMALL_GROUP_THRESHOLD，独占批次）。 */
    private fun groupIdOf(members: List<ImageFile>): String =
        SimilarGroup(images = members, startTime = members.minOf { it.dateModified }, endTime = members.maxOf { it.dateModified }).id

    /** 经 analyzeAlbum 同一组写入口种子持久化组（新格式身份键成员）。 */
    private fun seedGroups(
        albumRows: List<ImageFile>,
        groups: List<List<ImageFile>>,
        targetAlbumId: String = albumId
    ) {
        val ids = groups.map { groupIdOf(it) }
        preferences.saveAlbumAnalysisResult(targetAlbumId, groups.size, ids)
        preferences.saveGroupImageCounts(targetAlbumId, groups.associate { groupIdOf(it) to it.size })
        preferences.saveAlbumTotalImages(targetAlbumId, albumRows.size)
        preferences.saveGroupsImageIds(
            targetAlbumId,
            groups.associate { groupIdOf(it) to it.map { row -> row.mediaIdentityKey() } }
        )
    }

    private fun idOf(image: ImageFile) = image.mediaIdentityKey()

    @Test
    fun editedMemberMovingOutsideTargetMonthUsesFullSnapshotAndKeepsCheckpointIdentity() = runBlocking {
        val january = 1_767_225_600_000L
        val february = january + 31L * 24 * 60 * 60 * 1_000
        val members = listOf(row(1, dateModified = january), row(2, dateModified = january + 1_000))
        val originalId = groupIdOf(members)
        seedGroups(members, listOf(members))
        val engine = AlbumCleanupEngine(context)
        engine.getNextBatch(albumId, members)
        engine.saveCheckpoint(listOf(originalId), 1)
        val edited = members[1].copy(dateModified = february)
        val targetSubset = listOf(members[0])
        val latest = listOf(members[0], edited)

        val next = engine.getNextBatch(albumId, targetSubset, latestSnapshot = latest)!!
        assertEquals(listOf(originalId), next.groupIds)
        assertEquals(latest, next.images)
        assertSame(edited, next.images[1])
        val resumed = AlbumCleanupEngine(context).getCheckpointBatch(
            albumId, targetSubset, latestSnapshot = latest
        )!!
        assertEquals(1, resumed.second)
        assertEquals(listOf(originalId), resumed.first.groupIds)
        assertEquals(latest, resumed.first.images)
        assertSame(edited, resumed.first.images[1])
        assertEquals(members.map(::idOf), preferences.getGroupImageIds(albumId, originalId))
        assertTrue(preferences.getAlbumPermanentlyProcessedGroups(albumId).isEmpty())
    }

    @Test
    fun missingDirectoryFallbackDetectsOnlyTargetPoolDespiteFullLatestSnapshot() = runBlocking {
        val target = listOf(row(1001, dateModified = 1_000_000), row(1002, dateModified = 1_001_000))
        val outside = listOf(row(2001, dateModified = 90_000_000), row(2002, dateModified = 90_001_000))
        val hashCache = ImageHashCache.getInstance(context)
        (target + outside).forEach {
            hashCache.saveHash(it.toHashKey(), PerceptualHash.HashResult.Success(0L), it.dateModified)
        }
        assertFalse(preferences.hasPersistedGroupData(albumId))
        val next = AlbumCleanupEngine(context).getNextBatch(
            albumId, target, latestSnapshot = target + outside
        )!!
        assertEquals(target, next.images)
        assertEquals(listOf(groupIdOf(target)), next.groupIds)
        assertTrue(next.images.none { it.mediaIdentityKey() in outside.map(::idOf) })
    }

    @Test
    fun userRemovalSurvivesRecycleClearAndEngineRecreationWithoutChangingGroupIdentity() = runBlocking {
        val members = (1L..3L).map { row(it) }
        val originalId = groupIdOf(members)
        seedGroups(members, listOf(members))
        val engine = AlbumCleanupEngine(context)
        assertEquals(listOf(originalId), engine.getNextBatch(albumId, members)!!.groupIds)
        engine.saveCheckpoint(listOf(originalId), 1)
        val recycle = RecycleBinManager.getInstance(context)
        recycle.addToRecycleBin(members.first())
        engine.recordUserRemovedImage(members.first())
        recycle.clearRecycleBin()

        val freshEngine = AlbumCleanupEngine(context)
        val remaining = members.drop(1).map { it.copy(dateModified = 90_000L + it.id) }
        assertNull(freshEngine.getCheckpointBatch(albumId, remaining))
        assertNull(freshEngine.getCheckpoint(albumId)) // 删除改变成员数，安全重开旧索引。
        val resumed = freshEngine.getNextBatch(albumId, remaining)!!
        assertEquals(listOf(originalId), resumed.groupIds)
        assertEquals(remaining.map(::idOf), resumed.images.map(::idOf))
        assertEquals(remaining.map { it.dateModified }, resumed.images.map { it.dateModified })
        assertEquals(setOf(idOf(members.first())), preferences.getAlbumAcknowledgedCleanupMembers(albumId))
        assertEquals(members.map(::idOf), preferences.getGroupImageIds(albumId, originalId))
        assertFalse(preferences.isAlbumCleanupCompleted(albumId))
        freshEngine.markGroupsProcessed(resumed.groupIds)
        assertNull(freshEngine.getNextBatch(albumId, remaining))
        assertTrue(preferences.isAlbumCleanupCompleted(albumId))
        preferences.resetAlbumCleanupState(albumId)
        assertTrue(preferences.getAlbumAcknowledgedCleanupMembers(albumId).isEmpty())
    }

    @Test
    fun directRecycleClearAcknowledgesExistingRecordWithoutEngineBackfill() = runBlocking {
        val members = listOf(row(1), row(2))
        val originalId = groupIdOf(members)
        seedGroups(members, listOf(members))
        val recycle = RecycleBinManager.getInstance(context)
        recycle.addToRecycleBin(members.first())
        // 模拟旧版本记录或 add 成功后确认前崩溃；从未调用 engine/record 回填。
        assertTrue(preferences.getAlbumAcknowledgedCleanupMembers(albumId).isEmpty())
        recycle.clearRecycleBin()
        assertTrue(recycle.loadRecycleBin().isEmpty())
        val freshPreferences = AppPreferences(context)
        assertEquals(setOf(idOf(members.first())), freshPreferences.getAlbumAcknowledgedCleanupMembers(albumId))
        val resumed = AlbumCleanupEngine(context).getNextBatch(albumId, members.drop(1))!!
        assertEquals(listOf(originalId), resumed.groupIds)
        assertEquals(members.drop(1), resumed.images)
        assertEquals(members.map(::idOf), freshPreferences.getGroupImageIds(albumId, originalId))
        assertFalse(freshPreferences.isAlbumCleanupCompleted(albumId))
    }

    @Test
    fun failedAcknowledgementCommitIsRetriedBeforeRecycleClearAndRestart() = runBlocking {
        val members = listOf(row(1), row(2))
        val originalId = groupIdOf(members)
        seedGroups(members, listOf(members))
        val field = AppPreferences::class.java.getDeclaredField("albumCleanupPrefs").apply {
            isAccessible = true
        }
        val backing = field.get(preferences) as SharedPreferences
        fun snapshot(): Map<String, Any?> = backing.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        // 独立保存“磁盘”快照：首次 commit 已更新可读内存，但持久化失败。
        var durable = snapshot()
        var commitAttempts = 0
        val failingOnce = object : SharedPreferences by backing {
            override fun edit(): SharedPreferences.Editor {
                val editor = backing.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean {
                        check(editor.commit())
                        commitAttempts++
                        if (commitAttempts == 1) return false
                        durable = snapshot()
                        return true
                    }
                }
            }
        }
        field.set(preferences, failingOnce)
        try {
            val recycle = RecycleBinManager.getInstance(context)
            recycle.addToRecycleBin(members.first())
            val engine = AlbumCleanupEngine(context)
            var failed = false
            try {
                engine.recordUserRemovedImage(members.first())
            } catch (expected: IllegalStateException) {
                failed = true
            }
            assertTrue("首次持久化失败须上报", failed)
            assertEquals(setOf(idOf(members.first())), preferences.getAlbumAcknowledgedCleanupMembers(albumId))
            assertNull(durable["acknowledged_members_$albumId"])

            // 真实取批的回收记录回填必须重试，不能因内存已有同值而跳过落盘。
            val remaining = members.drop(1)
            assertEquals(remaining, engine.getNextBatch(albumId, remaining)!!.images)
            assertEquals(2, commitAttempts)
            assertEquals(setOf(idOf(members.first())), durable["acknowledged_members_$albumId"])
            recycle.clearRecycleBin()

            // 只从成功写入的快照重建偏好，丢弃首次失败后残留的内存状态。
            val restarted = context.getSharedPreferences("cleanup-ack-restart-fixture", Context.MODE_PRIVATE)
            val editor = restarted.edit().clear()
            durable.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> error("Unexpected fixture preference: $key")
                }
            }
            assertTrue(editor.commit())
            field.set(preferences, restarted)
            val resumed = AlbumCleanupEngine(context).getNextBatch(albumId, remaining)!!
            assertEquals(listOf(originalId), resumed.groupIds)
            assertEquals(remaining, resumed.images)
        } finally {
            field.set(preferences, backing)
        }
    }

    @Test
    fun existingRecycleRowsBackfillDurableAcknowledgementAndNeverAcknowledgeOtherVolume() = runBlocking {
        val a = row(5, "external_primary")
        val b = row(5, "1111-2222")
        val companion = row(6, "1111-2222")
        seedGroups(listOf(a, b, companion), listOf(listOf(a), listOf(b, companion)))
        val recycle = RecycleBinManager.getInstance(context)
        recycle.addToRecycleBin(a)
        val engine = AlbumCleanupEngine(context)
        assertNull(engine.getNextBatch(albumId, listOf(companion)))
        assertEquals(setOf(idOf(a)), preferences.getAlbumAcknowledgedCleanupMembers(albumId))
        assertEquals(setOf(groupIdOf(listOf(a))), preferences.getAlbumPermanentlyProcessedGroups(albumId))
        assertFalse(preferences.isAlbumCleanupCompleted(albumId))
        recycle.clearRecycleBin()
        val resumed = AlbumCleanupEngine(context).getNextBatch(albumId, listOf(b, companion))!!
        assertEquals(listOf(groupIdOf(listOf(b, companion))), resumed.groupIds)
        assertEquals(listOf(idOf(b), idOf(companion)), resumed.images.map(::idOf))
    }

    @Test
    fun legacyBareMembersRequireUniqueIdentityUpgradeBeforeAcknowledgement() = runBlocking {
        val a = row(5, "external_primary")
        val b = row(5, "1111-2222")
        val companion = row(6, "1111-2222")
        val originalId = groupIdOf(listOf(b, companion))
        preferences.saveAlbumAnalysisResult(albumId, 1, listOf(originalId))
        preferences.saveGroupImageIds(albumId, originalId, listOf(5L, 6L))
        val engine = AlbumCleanupEngine(context)
        engine.recordUserRemovedImage(a)
        assertTrue(preferences.getAlbumAcknowledgedCleanupMembers(albumId).isEmpty())
        // 唯一解析得到 B 卷后才升级目录；A 卷同数字 ID 仍不算用户处理 B。
        assertNotNull(engine.getNextBatch(albumId, listOf(b, companion)))
        engine.recordUserRemovedImage(a)
        assertTrue(preferences.getAlbumAcknowledgedCleanupMembers(albumId).isEmpty())
        engine.recordUserRemovedImage(b)
        assertEquals(setOf(idOf(b)), preferences.getAlbumAcknowledgedCleanupMembers(albumId))
        val fresh = AlbumCleanupEngine(context).getNextBatch(albumId, listOf(companion))!!
        assertEquals(listOf(originalId), fresh.groupIds)
        assertEquals(listOf(companion), fresh.images)
    }

    @Test
    fun subsequentBatchesServeFreshRowsAfterExternalDeleteAndEdit() = runBlocking {
        // 三组尺寸互异（12/11/10 > SMALL_GROUP_THRESHOLD=10 各自独占批次；
        // 恢复按组大小降序排列，尺寸互异使顺序确定——组目录在 prefs 中是
        // StringSet 无序，不能依赖种子顺序）。
        val g1 = (1L..12L).map { row(it) }
        val g2 = (13L..23L).map { row(it) }
        val g3 = (24L..33L).map { row(it) }
        seedGroups(g1 + g2 + g3, listOf(g1, g2, g3))
        val engine = AlbumCleanupEngine(context)

        val batch1 = engine.getNextBatch(albumId, g1 + g2 + g3)
        assertEquals(listOf(groupIdOf(g1)), batch1!!.groupIds)
        engine.markGroupsProcessed(batch1.groupIds)

        // 后台变化：13 号被外部删除（刷新池缺席）、24 号被编辑（同身份新对象）
        val edited24 = row(24L, dateModified = 9_000L)
        val refreshed = g1 + (14L..23L).map { row(it) } + listOf(edited24) + (25L..33L).map { row(it) }

        val batch2 = engine.getNextBatch(albumId, refreshed)
        // 缺少任一成员的 G2 整组持有；只有完整 G3 可交付。
        assertEquals(listOf(groupIdOf(g3)), batch2!!.groupIds)
        assertFalse("已删除的 13 号不得进入后续批次", batch2.images.any { it.id == 13L })
        assertEquals("持有组的可见子集也不得交付", 10, batch2.images.size)
        assertTrue("存活行必须是快照最新对象（被编辑的 24 号除外）", batch2.images.all { it.id == 24L || it.dateModified == 1_000L + it.id })
        assertSame("编辑后的 24 号必须是最新版本对象", edited24, batch2.images.first { it.id == 24L })
        engine.markGroupsProcessed(batch2.groupIds)

        assertNull("剩余组不可操作时取批必须为 null", engine.getNextBatch(albumId, refreshed))
        assertEquals(1, engine.getRemainingGroups(albumId))
        assertFalse(preferences.isAlbumCleanupCompleted(albumId))
        val restoredG2 = g2.map { row(it.id, dateModified = 10_000L + it.id) }
        val resumed = engine.getNextBatch(albumId, g1 + restoredG2 + g3)!!
        assertEquals(listOf(groupIdOf(g2)), resumed.groupIds)
        assertEquals(restoredG2, resumed.images)
        engine.markGroupsProcessed(resumed.groupIds)
        assertEquals(0, engine.getRemainingGroups(albumId))
    }

    @Test
    fun fullyVanishedGroupIsHeldNotMarkedAndResumesAfterAccessRestore() = runBlocking {
        val g1 = (1L..12L).map { row(it) }
        val g2 = listOf(row(13L), row(14L))
        val g3 = (15L..24L).map { row(it) }
        seedGroups(g1 + g2 + g3, listOf(g1, g2, g3))
        val engine = AlbumCleanupEngine(context)

        val batch1 = engine.getNextBatch(albumId, g1 + g2 + g3)
        engine.markGroupsProcessed(batch1!!.groupIds)
        val remainingBefore = engine.getRemainingGroups(albumId)

        // 后台：G2 两行不可见（外部删除或权限收窄——引擎无法区分，均不得
        // 永久完成；刷新池无 13/14 号）
        val narrowed = g1 + g3
        val batch2 = engine.getNextBatch(albumId, narrowed)

        assertFalse("不可操作组不得进入批次", batch2!!.groupIds.contains(groupIdOf(g2)))
        assertTrue(batch2.groupIds.contains(groupIdOf(g3)))
        assertEquals("持有组不得被标记（剩余组数不变，未处理意图保留）", remainingBefore, engine.getRemainingGroups(albumId))
        assertFalse("持有不得持久化完成标志", preferences.isAlbumCleanupCompleted(albumId))
        engine.markGroupsProcessed(batch2.groupIds)

        // 访问恢复（FULL）：13/14 号回到最新池——持有的 G2 按最新快照重新交付
        //（其余组已处理，不重复交付）
        val batch3 = engine.getNextBatch(albumId, g1 + g2.map { row(it.id, dateModified = 9_000L + it.id) } + g3)
        assertEquals(listOf(groupIdOf(g2)), batch3!!.groupIds)
        assertTrue("恢复后的行必须是最新版本对象", batch3.images.all { it.dateModified == 9_000L + it.id })
        engine.markGroupsProcessed(batch3.groupIds)
        assertNull("恢复后全部组处理完才真正完成", engine.getNextBatch(albumId, g1 + g2 + g3))
    }

    @Test
    fun checkpointRestoreServesUpdatedRowsAfterExternalEdit() = runBlocking {
        val g1 = (1L..11L).map { row(it) }
        seedGroups(g1, listOf(g1))
        val engine = AlbumCleanupEngine(context)

        val batch1 = engine.getNextBatch(albumId, g1)
        engine.saveCheckpoint(batch1!!.groupIds, 3)

        // 后台编辑断点批内 4 号（idx 3，同身份新对象）
        val edited = row(4L, dateModified = 9_000L)
        val refreshed = g1.map { if (it.id == 4L) edited else it }

        val restored = engine.getCheckpointBatch(albumId, refreshed)
        val (batch, index) = restored!!
        assertEquals("断点索引在行数不变时保持", 3, index)
        assertSame("恢复的行必须是编辑后最新对象", edited, batch.images[3])
        assertEquals(listOf(0), batch.groupBoundaries)
    }

    @Test
    fun checkpointRestoreHoldsWhenMemberIsMissingAndResumesWithSameIndex() = runBlocking {
        val g1 = (1L..11L).map { row(it) }
        seedGroups(g1, listOf(g1))
        val engine = AlbumCleanupEngine(context)

        val batch1 = engine.getNextBatch(albumId, g1)
        engine.saveCheckpoint(batch1!!.groupIds, 3)

        // 快照缺席不能证明永久删除；保留组与断点，恢复后按原成员顺序续接。
        val refreshed = g1.filter { it.id != 4L }
        assertNull("缺席成员的组不可交付", engine.getCheckpointBatch(albumId, refreshed))
        assertEquals(listOf(groupIdOf(g1)) to 3, engine.getCheckpoint(albumId))
        assertNull(engine.getNextBatch(albumId, refreshed))

        val restored = engine.getCheckpointBatch(albumId, g1)!!
        assertEquals(3, restored.second)
        assertEquals(g1, restored.first.images)
    }

    @Test
    fun crossVolumeSameIdRowsAreNotSubstitutedOrCollateralPruned() = runBlocking {
        val xAlbum = albumId + "-xvol"
        val b5 = row(5L, volume = "external_secondary")
        val b6 = row(6L, volume = "external_secondary")
        seedGroups(listOf(b5, b6), listOf(listOf(b5, b6)), targetAlbumId = xAlbum)
        val engine = AlbumCleanupEngine(context)

        val batch1 = engine.getNextBatch(xAlbum, listOf(b5, b6))
        assertEquals(listOf(b5, b6), batch1!!.images)

        // 后台：B 卷 5 号被删；A 卷出现同数值 ID 的新行——B5 不得被 A5 顶替，
        // 缺 B5 则整组持有，A5 不能补位或使 B6 子集被完成。
        val a5New = row(5L, volume = "external_primary", dateModified = 9_000L)
        val batch2 = engine.getNextBatch(xAlbum, listOf(a5New, b6))
        assertNull(batch2)
        assertEquals(1, engine.getRemainingGroups(xAlbum))
        assertFalse(preferences.isAlbumCleanupCompleted(xAlbum))

        // B 卷组被用户标记完成后：该组不再交付（A 卷同 ID 行不得误当作
        // 该组仍可操作）
        val batch3 = engine.getNextBatch(xAlbum, listOf(b5, b6))
        engine.markGroupsProcessed(batch3!!.groupIds)
        assertNull(engine.getNextBatch(xAlbum, listOf(a5New)))
    }

    @Test
    fun emptySnapshotHoldsIntentAndResumesWhenAccessRestores() = runBlocking {
        val g1 = (1L..12L).map { row(it) }
        val g2 = (13L..22L).map { row(it) }
        seedGroups(g1 + g2, listOf(g1, g2))
        val engine = AlbumCleanupEngine(context)

        val batch1 = engine.getNextBatch(albumId, g1 + g2)
        engine.markGroupsProcessed(batch1!!.groupIds)

        // 有效空快照（权限收窄至空/全部外部删除）：取批返回 null，不持久化
        // 完成标志、不丢未处理意图
        assertNull(engine.getNextBatch(albumId, emptyList()))
        assertFalse("空快照不得持久化完成标志", preferences.isAlbumCleanupCompleted(albumId))
        assertEquals("持有组不标记（剩余 = 总数 - 已处理）", 1, engine.getRemainingGroups(albumId))

        // 权限恢复（FULL）：G2 照常交付（行可为最新版本对象）
        val batch2 = engine.getNextBatch(albumId, g1 + g2.map { row(it.id, dateModified = 9_000L + it.id) })
        assertEquals(listOf(groupIdOf(g2)), batch2!!.groupIds)
        assertTrue(batch2.images.all { it.dateModified == 9_000L + it.id })
    }

    @Test
    fun syncedMergedBatchCompletesOnlyFullyVisibleGroupsAndRestoresHeldMembers() = runBlocking {
        val g1 = listOf(row(1L), row(2L))
        val g2 = listOf(row(3L), row(4L), row(5L))
        // 两种收窄：G2 整组不可见；G2 只有部分成员可见。
        for ((caseIndex, narrowed) in listOf(g1, g1 + g2.first()).withIndex()) {
            val targetAlbum = "$albumId-sync-$caseIndex"
            seedGroups(g1 + g2, listOf(g1, g2), targetAlbum)
            val engine = AlbumCleanupEngine(context)
            val batch = engine.getNextBatch(targetAlbum, g1 + g2)!!
            assertEquals(setOf(groupIdOf(g1), groupIdOf(g2)), batch.groupIds.toSet())

            val synced = syncCleanupBatchToSnapshot(batch, narrowed)
            assertEquals(listOf(groupIdOf(g1)), synced.groupIds)
            assertEquals(g1, synced.images)
            engine.markGroupsProcessed(synced.groupIds)
            assertEquals(setOf(groupIdOf(g1)), preferences.getAlbumPermanentlyProcessedGroups(targetAlbum))
            assertEquals(1, engine.getRemainingGroups(targetAlbum))
            assertFalse(preferences.isAlbumCleanupCompleted(targetAlbum))

            val restoredG2 = g2.map { row(it.id, dateModified = 9_000L + it.id) }
            val next = engine.getNextBatch(targetAlbum, g1 + restoredG2)!!
            assertEquals(listOf(groupIdOf(g2)), next.groupIds)
            assertEquals(restoredG2, next.images)
            engine.markGroupsProcessed(next.groupIds)
            assertNull(engine.getNextBatch(targetAlbum, g1 + restoredG2))
            assertTrue(preferences.isAlbumCleanupCompleted(targetAlbum))
        }
    }

    @Test
    fun emptyPartialRestoreRetainsDirectoryAndUsesSameEngineAfterFullAccess() = runBlocking {
        val members = listOf(row(1L), row(2L))
        seedGroups(members, listOf(members))
        val engine = AlbumCleanupEngine(context)
        assertNull(engine.getNextBatch(albumId, emptyList()))
        assertEquals(setOf(groupIdOf(members)), preferences.getAlbumGroupIds(albumId))
        assertEquals(1, engine.getRemainingGroups(albumId))
        assertFalse(preferences.isAlbumCleanupCompleted(albumId))
        val restored = engine.getNextBatch(albumId, members)!!
        assertEquals(listOf(groupIdOf(members)), restored.groupIds)
        assertEquals(members, restored.images)
    }

    @Test
    fun permissionNarrowRoundTripSurvivesEngineRecreationAndReentry() = runBlocking {
        // FULL → PARTIAL（取批/持有）→ 重建引擎（普通重新进入）→ FULL 恢复
        val g1 = (1L..12L).map { row(it) }
        val g2 = (13L..23L).map { row(it) }
        val g3 = (24L..32L).map { row(it) }
        seedGroups(g1 + g2 + g3, listOf(g1, g2, g3))
        val engine = AlbumCleanupEngine(context)

        // FULL：处理 G1，G2 断点留在中途
        val batch1 = engine.getNextBatch(albumId, g1 + g2 + g3)
        engine.markGroupsProcessed(batch1!!.groupIds)
        val batch2 = engine.getNextBatch(albumId, g1 + g2 + g3)
        engine.saveCheckpoint(batch2!!.groupIds, 4)

        // PARTIAL：G2/G3 全部不可见——取批持有（null、不标记、完成标志不落）；
        // 重建引擎（应用重建/普通重新进入）后语义一致，断点不因持有被清除
        val narrowed = g1
        val freshEngine = AlbumCleanupEngine(context)
        assertNull(freshEngine.getNextBatch(albumId, narrowed))
        assertNull(freshEngine.getCheckpointBatch(albumId, narrowed))
        assertFalse(preferences.isAlbumCleanupCompleted(albumId))
        assertNotNull("持有不得清除断点", preferences.getAlbumCleanupCheckpoint(albumId))
        assertEquals(2, engine.getRemainingGroups(albumId)) // G2/G3 未标记

        // FULL 恢复：断点批按最新快照恢复（行数不变 → 断点索引保持），G3
        // 照常排队；已处理 G1 不重复
        val restored = freshEngine.getCheckpointBatch(albumId, g1 + g2 + g3)
        assertNotNull(restored)
        assertEquals(4, restored!!.second)
        assertEquals(groupIdOf(g2), restored.first.groupIds.single())
        // 恢复后正常取批先交付断点持有的 G2（未完成组不丢失），G3 随后
        val next = freshEngine.getNextBatch(albumId, g1 + g2 + g3)
        assertEquals(listOf(groupIdOf(g2)), next!!.groupIds)
        freshEngine.markGroupsProcessed(next.groupIds)
        val last = freshEngine.getNextBatch(albumId, g1 + g2 + g3)
        assertEquals(listOf(groupIdOf(g3)), last!!.groupIds)
        assertFalse("已处理的 G1 不重复交付", (next.images + last.images).any { it.id in 1L..12L })
    }
}
