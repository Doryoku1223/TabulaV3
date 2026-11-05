package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.RecommendMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C1/T03-DECKCOOLDOWN：随机抽取冷却池按完整媒体身份（卷|ID）。
 *
 * 修复前缺陷（裸数值 ID 键 time_<id>/days_<id>）：
 * ① 卷 A 照片被看过进冷却后，卷 B 同数值 ID 的未看照片被错误抑制；
 * ② 未看照片回填按裸 ID 移除冷却，清掉他卷同 ID 已看照片的记录。
 * 修复后：新记录按身份键写入；旧裸 ID 记录唯一可解析时迁移（幂等），
 * 歧义保留并按行安全抑制，不猜卷、不丢记录。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PickCooldownIdentityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val preferences: AppPreferences get() = AppPreferences(context)
    private val engine: RecommendationEngine get() = RecommendationEngine(context)
    private val albumManager: AlbumManager get() = AlbumManager.getInstance(context)

    /** 旧格式裸键种子（升级前数据；AppPreferences 已不提供裸键写入口）。 */
    private fun seedLegacyPickRecord(bareId: Long, pickedAt: Long, days: Int) {
        // 与 AppPreferences.PICK_TIMESTAMPS_PREFS_NAME 同一文件。
        context.getSharedPreferences("tabula_pick_timestamps", Context.MODE_PRIVATE)
            .edit()
            .putLong("time_$bareId", pickedAt)
            .putInt("days_$bareId", days)
            .commit()
    }

    private fun legacyKeyExists(bareId: Long): Boolean =
        context.getSharedPreferences("tabula_pick_timestamps", Context.MODE_PRIVATE)
            .contains("time_$bareId")

    private fun row(volume: String, id: Long) = rowInBucket(volume, id, "Camera")

    private fun rowInBucket(volume: String, id: Long, bucket: String) = ImageFile(
        id = id,
        uri = Uri.parse("content://media/$volume/images/media/$id"),
        displayName = "img-$volume-$id.jpg",
        dateModified = 1_000L,
        size = 100L,
        width = 64,
        height = 64,
        bucketDisplayName = bucket,
        volumeName = volume
    )

    @Before
    fun setUp() {
        preferences.clearAllPickRecords()
        preferences.recommendMode = RecommendMode.RANDOM_WALK
    }

    @Test
    fun crossVolumeSameIdViewedOnOneVolumeDoesNotSuppressOtherVolume() = runBlocking {
        // 卷 A 的 42 已看进冷却；卷 B 同 ID 的 42 从未看过，不得被抑制
        // （batchSize=2 恰好取走全部可用行，结果确定）。
        val a = row("external_primary", 42L)
        val b = row("external_secondary", 42L)
        val c = row("external_primary", 43L)
        preferences.recordImagesPickedByIdentity(listOf(a.mediaIdentityKey()))

        val batch = engine.getRecommendedBatch(listOf(a, b, c), batchSize = 2)

        assertTrue("同卷未看照片保持可选", c in batch)
        assertTrue("另一卷同 ID 未看照片不得被抑制", b in batch)
        assertTrue("已看照片仍在冷却", a !in batch)
    }

    @Test
    fun unviewedRemovalDoesNotEraseOtherVolumeViewedRecord() = runBlocking {
        // 未看回填（DeckScreen 传行）：移除 B 行的冷却不得清除他卷同 ID
        // （A 行）的已看记录。
        val a = row("external_primary", 42L)
        val b = row("external_secondary", 42L)
        preferences.recordImagesPickedByIdentity(listOf(a.mediaIdentityKey()))

        engine.removeFromCooldown(listOf(b))

        val index = preferences.getPickCooldownIndex()
        assertTrue("他卷已看记录必须幸存", index.byIdentityKey.containsKey(a.mediaIdentityKey()))
        assertFalse("未看行自身不得残留冷却", index.byIdentityKey.containsKey(b.mediaIdentityKey()))
        // 冷却判定仍按行生效：批次只含未看的 B、C。
        val c = row("external_primary", 43L)
        val batch = engine.getRecommendedBatch(listOf(a, b, c), batchSize = 2)
        assertTrue(a !in batch)
        assertEquals(setOf(b, c), batch.toSet())
    }

    @Test
    fun legacyUniqueRecordMigratesToIdentityKeyAndStaysCool() = runBlocking {
        // 旧裸 ID 记录 + 当前集合内该 ID 唯一行 → 迁移为身份键（幂等），
        // 冷却语义保持：该行仍被抑制，另一行可选。
        val a = row("external_primary", 42L)
        val b = row("external_primary", 43L)
        seedLegacyPickRecord(42L, pickedAt = System.currentTimeMillis(), days = 24)

        val first = engine.getRecommendedBatch(listOf(a, b), batchSize = 1)
        assertEquals(listOf(b), first)

        assertFalse("裸键已迁移清除", legacyKeyExists(42L))
        val index = preferences.getPickCooldownIndex()
        assertTrue("身份键记录建立", index.byIdentityKey.containsKey(a.mediaIdentityKey()))
        assertTrue("旧天数保留", index.byIdentityKey[a.mediaIdentityKey()]!!.days == 24)

        // 重复迁移（重启/再入）：幂等——裸键不复活、身份键不被改写、
        // 批次组成不影响存储状态（第二批走冷却补充路径属既有行为）。
        engine.getRecommendedBatch(listOf(a, b), batchSize = 1)
        assertFalse(legacyKeyExists(42L))
        val after = preferences.getPickCooldownIndex()
        assertTrue(after.byIdentityKey.containsKey(a.mediaIdentityKey()))
        assertEquals(24, after.byIdentityKey[a.mediaIdentityKey()]!!.days)
    }

    @Test
    fun legacyAmbiguousRecordKeptAndSafelySuppressesBothVolumes() = runBlocking {
        // 旧裸 ID 记录 + 跨卷同 ID 多行：歧义不猜卷——裸键保留（不丢记录），
        // 两行均被抑制（安全方向），不迁移出具体卷的身份键。
        val a = row("external_primary", 42L)
        val b = row("external_secondary", 42L)
        seedLegacyPickRecord(42L, pickedAt = System.currentTimeMillis(), days = 24)

        val batch = engine.getRecommendedBatch(listOf(a, b), batchSize = 1)

        assertTrue("歧义记录保留", legacyKeyExists(42L))
        val index = preferences.getPickCooldownIndex()
        assertTrue(index.byLegacyBareId.containsKey(42L))
        assertTrue("不猜卷", index.byIdentityKey.isEmpty())
        assertEquals("两行均被安全抑制（冷却补充路径），无可用行误放行", 1, batch.size)
        assertTrue(batch.single().id == 42L)
    }

    @Test
    fun legacyRecordForMissingRowIsLeftUntouched() = runBlocking {
        // 旧裸 ID 记录指向的行不在当前集合：不猜、不清（保留待该行回归）。
        // 两行使小图库清空路径（allImages<=batchSize）不触发。
        val present1 = row("external_primary", 43L)
        val present2 = row("external_primary", 44L)
        seedLegacyPickRecord(99L, pickedAt = System.currentTimeMillis(), days = 24)

        engine.getRecommendedBatch(listOf(present1, present2), batchSize = 1)

        assertTrue(legacyKeyExists(99L))
    }

    @Test
    fun legacyBareRecordStillSuppressesSingleVolumeRow() = runBlocking {
        // 控制组：旧裸 ID 记录对单卷行仍然有效（兼容语义不回退）。
        val a = row("external_primary", 42L)
        val b = row("external_primary", 43L)
        seedLegacyPickRecord(42L, pickedAt = System.currentTimeMillis(), days = 24)

        val batch = engine.getRecommendedBatch(listOf(a, b), batchSize = 1)

        assertTrue("冷却中的照片不进批次", a !in batch)
        assertEquals(listOf(b), batch)
    }

    @Test
    fun singleVolumeCooldownFlowUnchanged() = runBlocking {
        // 控制组：单卷常规流——抽取后进冷却，下一批不再出现（batchSize=1）。
        val a = row("external_primary", 42L)
        val b = row("external_primary", 43L)

        val first = engine.getRecommendedBatch(listOf(a, b), batchSize = 1)
        assertEquals(1, first.size)
        val picked = first.single()

        val second = engine.getRecommendedBatch(listOf(a, b), batchSize = 1)
        assertEquals("刚抽取的照片进冷却，另一张可选", listOf(if (picked == a) b else a), second)
        // 新记录以身份键落盘。
        assertTrue(
            preferences.getPickCooldownIndex().byIdentityKey.containsKey(picked.mediaIdentityKey())
        )
    }

    @Test
    fun legacyCooldownSurvivesRecycleBinProjectionAndRestore() = runBlocking {
        val a = row("external_primary", 42L)
        val b = row("external_secondary", 42L)
        val c = row("external_primary", 43L)
        val d = row("external_primary", 44L)
        val snapshot = LocalImageRepository.LoadResult(listOf(a, b, c, d), listOf(a, b, c, d), emptyList())
        val deleted = listOf(a)
        val projected = projectSnapshot(snapshot, deleted)
        val pickedAt = System.currentTimeMillis()
        seedLegacyPickRecord(42L, pickedAt, days = 24)
        seedLegacyPickRecord(99L, pickedAt, days = 12)

        // Match MainActivity: the deck uses the projected pool, but identities
        // include all visible albums and recoverable recycle-bin records.
        val first = engine.getRecommendedBatch(
            projected.imagesExcludingTabula, batchSize = 1,
            identityCandidates = projected.allImages + deleted
        )
        assertTrue(a !in first && b !in first)
        assertTrue("Recoverable cross-volume ambiguity must retain the bare key", legacyKeyExists(42L))
        assertTrue(preferences.getPickCooldownIndex().byIdentityKey.keys.none { it.endsWith("|42") })

        val restored = projectSnapshot(snapshot, emptyList())
        val second = engine.getRecommendedBatch(
            restored.imagesExcludingTabula, batchSize = 1,
            identityCandidates = restored.allImages
        )
        assertTrue("Both volumes remain cooled after restoration", second.single().id != 42L)
        val legacy = preferences.getPickCooldownIndex().byLegacyBareId
        assertEquals(pickedAt, legacy[42L]!!.pickedAt)
        assertEquals(24, legacy[42L]!!.days)
        assertEquals("Unrelated missing record is preserved", pickedAt, legacy[99L]!!.pickedAt)
    }

    @Test
    fun classifiedRowOutsideDeckStillMakesLegacyIdAmbiguous() = runBlocking {
        val classified = rowInBucket("external_primary", 42L, "Tabula/Sorted")
        val visible = row("external_secondary", 42L)
        val c = row("external_primary", 43L)
        val d = row("external_primary", 44L)
        seedLegacyPickRecord(42L, System.currentTimeMillis(), days = 24)

        val batch = engine.getRecommendedBatch(
            listOf(visible, c, d), batchSize = 1,
            identityCandidates = listOf(classified, visible, c, d)
        )

        assertTrue(legacyKeyExists(42L))
        assertTrue(classified !in batch && visible !in batch)
        assertTrue(preferences.getPickCooldownIndex().byIdentityKey.keys.none { it.endsWith("|42") })
    }

    @Test
    fun repeatedIdentityAcrossSnapshotsDoesNotPreventUniqueMigration() = runBlocking {
        val a = row("external_primary", 42L)
        val b = row("external_primary", 43L)
        val offDeck = row("external_primary", 44L)
        seedLegacyPickRecord(42L, System.currentTimeMillis(), days = 24)

        val batch = engine.getRecommendedBatch(
            listOf(a, b), batchSize = 1,
            identityCandidates = listOf(a, a.copy(dateModified = 2_000L), b, offDeck)
        )

        assertEquals("Extra identity candidates must never enter the sample", listOf(b), batch)
        assertFalse(legacyKeyExists(42L))
        assertEquals(24, preferences.getPickCooldownIndex().byIdentityKey[a.mediaIdentityKey()]!!.days)
    }

    @Test
    fun excludedRowDoesNotMakeCrossVolumeLegacyRecordMigratable() = runBlocking {
        // 复验轮：跨卷同 ID、其中一行在屏蔽图集。迁移唯一性必须用屏蔽
        // 过滤前的完整候选集判定——过滤后"看似唯一"不得猜卷迁移，裸键保留。
        // 池内另备两张可见行，避开小图库清空路径（filtered<=batchSize）。
        val hidden = rowInBucket("external_primary", 42L, "HiddenAlbum")
        val visible = rowInBucket("external_secondary", 42L, "Camera")
        val c = row("external_primary", 43L)
        val d = row("external_primary", 44L)
        seedLegacyPickRecord(42L, pickedAt = System.currentTimeMillis(), days = 24)
        albumManager.setAlbumExcludedFromRecommend("HiddenAlbum", true)

        val batch = engine.getRecommendedBatch(listOf(hidden, visible, c, d), batchSize = 1)

        assertTrue("歧义记录不得因屏蔽过滤被猜卷迁移", legacyKeyExists(42L))
        assertTrue(
            "未为 42 猜卷写出身份键（池内他行正常记录不在禁列）",
            preferences.getPickCooldownIndex().byIdentityKey.keys.none { it.endsWith("|42") }
        )
        assertTrue("可见同 ID 行仍被裸键冷却抑制", visible !in batch)
        assertTrue("推荐抽样仍按原规则过滤：屏蔽行不可入选", hidden !in batch)
    }

    @Test
    fun cooldownSurvivesExcludeToggleForCrossVolumeLegacyRecord() = runBlocking {
        // 屏蔽期间不迁移；解除屏蔽后完整候选集仍歧义——裸键保留，
        // 冷却对两行继续有效（两行均不出现在抽样批次）。
        val hidden = rowInBucket("external_primary", 42L, "HiddenAlbum")
        val visible = rowInBucket("external_secondary", 42L, "Camera")
        val c = row("external_primary", 43L)
        val d = row("external_primary", 44L)
        seedLegacyPickRecord(42L, pickedAt = System.currentTimeMillis(), days = 24)
        albumManager.setAlbumExcludedFromRecommend("HiddenAlbum", true)
        engine.getRecommendedBatch(listOf(hidden, visible, c, d), batchSize = 1)

        albumManager.setAlbumExcludedFromRecommend("HiddenAlbum", false)
        val batch = engine.getRecommendedBatch(listOf(hidden, visible, c, d), batchSize = 1)

        assertTrue("解除屏蔽后裸键仍在", legacyKeyExists(42L))
        assertTrue(
            "未为 42 发生身份键迁移（池内他行正常记录不在禁列）",
            preferences.getPickCooldownIndex().byIdentityKey.keys.none { it.endsWith("|42") }
        )
        assertTrue("冷却仍有效：同 ID 两行均被抑制", batch.single().id != 42L)
    }
}
