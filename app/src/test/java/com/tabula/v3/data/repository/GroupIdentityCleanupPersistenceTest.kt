package com.tabula.v3.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.util.function.Supplier

/**
 * C1/T03-GROUPID-CLEANUP：相似组身份 + 专清持久化/恢复闭环。
 *
 * 缺口（既有，非本轮引入）：
 * 1. SimilarGroup.id = "startTime_首张照片数字 ID"——不同卷的同数值 ID、
 *    相同时间的组共用冷却池与永久处理标记；成员重排（首张变化）产生无意义
 *    的组身份漂移。
 * 2. 专清成员持久化只存 Long ID（saveGroupsImageIds），恢复用
 *    associateBy(id)——跨卷同 ID 行折叠或恢复成错误照片。
 *
 * 夹具：真实 AlbumCleanupEngine + 真实 AppPreferences；图片为构造的
 * ImageFile（两卷、同秒时间），内容为真实渐变 JPEG——Robolectric 位图解码
 * 对任意字节都"成功"且哈希相同（探针实测 Success(0)，会使检测把四张连成
 * 一组），必须用真实编解码控制 dHash：主卷正向渐变（dHash 0）、副卷反向
 * 渐变（dHash 全 1，跨卷 Hamming 64 必不连接）。组集合确定：{p10,p11} 与
 * {s10,s12}，两组的旧 ID 同为毫秒 startTime 加首张数字 ID（T_10）。
 * 旧格式组 ID（既有构建写入）= "${startTime 毫秒}_${首张数字 ID}"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GroupIdentityCleanupPersistenceTest {
    private val app: android.app.Application get() = RuntimeEnvironment.getApplication()
    private val context: Context get() = app
    private lateinit var preferences: AppPreferences
    private lateinit var jpegFiles: Map<Uri, File>

    private val T = 1_700_000_000_000L
    private val legacyGroupId = "${T}_10"
    private val album = Album(id = "source", name = "source")

    private val p10 = row("external_primary", 10L, T, size = 100L)
    private val p11 = row("external_primary", 11L, T + 2_000L, size = 100L)
    private val s10 = row("external_secondary", 10L, T, size = 200L)
    private val s12 = row("external_secondary", 12L, T + 2_000L, size = 200L)
    private val allImages = listOf(p10, p11, s10, s12)

    @Before
    fun setUp() {
        preferences = AppPreferences.getInstance(context)
        preferences.clearAllAlbumCleanupState()
        com.tabula.v3.data.cache.ImageHashCache.getInstance(context).clearAll()
        // 真实渐变 JPEG + 流注册：主卷正向（dHash 0），副卷反向（dHash 全 1）。
        val forward = File(context.cacheDir, "grp-forward.jpg").also { writeGradientJpeg(it, leftToRight = true) }
        val reverse = File(context.cacheDir, "grp-reverse.jpg").also { writeGradientJpeg(it, leftToRight = false) }
        jpegFiles = mapOf(p10.uri to forward, p11.uri to forward, s10.uri to reverse, s12.uri to reverse)
        val streamResolver = shadowOf(context.contentResolver)
        jpegFiles.forEach { (uri, file) ->
            streamResolver.registerInputStreamSupplier(uri, Supplier { FileInputStream(file) })
        }
    }

    /** 灰度横向渐变；leftToRight=false 时方向反转，dHash 与正向近似全位不同。 */
    private fun writeGradientJpeg(target: File, leftToRight: Boolean) {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (x in 0 until 64) {
            for (y in 0 until 64) {
                val v = (if (leftToRight) x else 63 - x) * 4
                bitmap.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "JPEG compress failed" } }
        bitmap.recycle()
    }

    private fun row(volume: String, id: Long, dateModifiedMs: Long, size: Long) =
        ImageFile(
            id = id,
            uri = Uri.parse("content://media/$volume/images/media/$id"),
            displayName = "img-$volume-$id.jpg",
            dateModified = dateModifiedMs,
            size = size,
            width = 64,
            height = 64,
            bucketDisplayName = "source",
            volumeName = volume,
            relativePath = "Pictures/source/"
        )

    /** 与生产检测一致的预期组：按 dateModified 排序的成员。 */
    private fun expectedGroupA() = SimilarGroup(images = listOf(p10, p11), startTime = T, endTime = T + 2_000L)
    private fun expectedGroupB() = SimilarGroup(images = listOf(s10, s12), startTime = T, endTime = T + 2_000L)

    private suspend fun analyzeWithFreshEngine(): AlbumCleanupEngine {
        val engine = AlbumCleanupEngine(context)
        engine.analyzeAlbum(album, allImages).toList()
        return engine
    }

    /** 注入既有构建写下的旧格式数据（组集合 + 逗号分隔裸 ID 成员 + 可选旧标记）。 */
    private fun seedLegacyGroup(
        albumId: String,
        legacyId: String,
        memberIds: List<Long>,
        withCheckpoint: Boolean = true,
        processedMarker: Boolean = false
    ) {
        // 全部经由 AppPreferences 写入（与被测引擎同一读写层）：落盘字节与
        // 既有构建的旧格式逐字相同（逗号分隔裸数字 ID）。AppPreferences 单例
        // 绑定首个沙箱 Application，raw SharedPreferences 跨方法不可见。
        preferences.saveAlbumAnalysisResult(albumId, 1, listOf(legacyId))
        // 旧格式成员 = 裸数字字符串；joinToString 后与既有构建落盘字节相同。
        preferences.saveGroupsImageIds(albumId, mapOf(legacyId to memberIds.map { it.toString() }))
        if (withCheckpoint) {
            preferences.saveAlbumCleanupCheckpoint(albumId, listOf(legacyId), 0)
        }
        if (processedMarker) {
            preferences.markAlbumGroupPermanentlyProcessed(albumId, legacyId)
        }
    }

    // ========== 组身份语义 ==========

    @Test
    fun groupIdentityIsReorderStable() {
        // 同一成员集合重排不得产生无意义的组身份变化。
        val reordered = SimilarGroup(images = listOf(p11, p10), startTime = T, endTime = T + 2_000L)
        assertEquals(
            "成员重排后组身份必须一致",
            expectedGroupA().id, reordered.id
        )
    }

    @Test
    fun crossVolumeSameIdGroupsHaveDistinctIdentity() {
        // 不同卷的不同成员集合（旧 ID 同为 "${T}_10"）必须有可区分的组身份。
        assertNotEquals(
            "跨卷同数值 ID 的两组不得共用组身份",
            expectedGroupA().id, expectedGroupB().id
        )
    }

    // ========== 冷却消费者（真实 AppPreferences 读写） ==========

    @Test
    fun cooldownOfOneGroupDoesNotSuppressCrossVolumeGroup() {
        val groupAId = expectedGroupA().id
        val groupBId = expectedGroupB().id

        // 冷却池的真实写入口是 RecommendationEngine 经由的
        // AppPreferences.recordSimilarGroupProcessed（markGroupProcessed 写的是
        // 专清永久标记，不是冷却）——用同一真实读写者核对跨卷隔离。
        preferences.recordSimilarGroupProcessed(groupAId)
        assertTrue(
            "被处理的组必须进入冷却",
            preferences.isSimilarGroupInCooldown(groupAId)
        )
        assertFalse(
            "处理 A 组不得隐藏另一卷同数值 ID 的 B 组",
            preferences.isSimilarGroupInCooldown(groupBId)
        )
    }

    // ========== 永久处理标记 + 批次过滤（真实引擎全链） ==========

    @Test
    fun permanentMarkerOnOneGroupKeepsCrossVolumeGroupInBatch() = runBlocking {
        val engine = analyzeWithFreshEngine()
        val groupAId = expectedGroupA().id

        engine.markGroupProcessed(groupAId)

        val fresh = AlbumCleanupEngine(context)
        val batch = fresh.getNextBatch(album.id, allImages)
        assertNotNull("处理 A 组后必须仍有批次可清理", batch)
        val batchUris = batch!!.images.map { it.uri }.toSet()
        assertEquals(
            "剩余批次必须恰好是另一卷 B 组的成员（正确 URI，不串卷）",
            setOf(s10.uri, s12.uri), batchUris
        )
    }

    // ========== 专清成员持久化 + 重建引擎恢复（真实全链） ==========

    @Test
    fun restoreAfterEngineRebuildKeepsBothVolumeRows() = runBlocking {
        val engine = analyzeWithFreshEngine()
        val groupIds = preferences.getAlbumGroupIds(album.id).toList()
        engine.saveCheckpoint(groupIds, 0)

        // 重建引擎（cachedGroups 为空）→ 走持久化恢复路径。
        val fresh = AlbumCleanupEngine(context)
        val restored = fresh.getCheckpointBatch(album.id, allImages)
        assertNotNull("重建后必须能从持久化数据恢复批次", restored)
        val uris = restored!!.first.images.map { it.uri }
        assertEquals(
            "两卷照片都必须以正确 URI 恢复（不折叠、不串卷）",
            setOf(p10.uri, p11.uri, s10.uri, s12.uri), uris.toSet()
        )
        assertEquals(
            "每个成员恰好出现一次",
            4, uris.size
        )
    }

    // ========== 旧数据兼容（裸 ID 成员，经 AppPreferences 注入旧格式字节） ==========

    @Test
    fun legacySingleVolumeMembersRestoreUniquely() = runBlocking {
        // 旧单卷数据：裸 ID 在当前库中唯一 → 必须照常恢复（兼容保持）。
        seedLegacyGroup("legacy1", legacyGroupId, listOf(10L, 11L))
        val singleVolume = listOf(p10, p11)
        val engine = AlbumCleanupEngine(context)
        val restored = engine.getCheckpointBatch("legacy1", singleVolume)
        assertNotNull("旧单卷数据必须能恢复", restored)
        assertEquals(
            setOf(p10.uri, p11.uri),
            restored!!.first.images.map { it.uri }.toSet()
        )
    }

    @Test
    fun legacyAmbiguousCrossVolumeMembersRedetectInsteadOfGuess() = runBlocking {
        // 旧跨卷歧义数据：裸 ID 10 在两卷各有同行——不得猜选另一卷，
        // 必须放弃恢复转重算（组集合完整，两卷都在）。
        seedLegacyGroup("legacy2", legacyGroupId, listOf(10L), withCheckpoint = false)
        val engine = AlbumCleanupEngine(context)
        val batch = engine.getNextBatch("legacy2", allImages)
        assertNotNull("歧义旧数据放弃恢复后必须重算出批次", batch)
        val uris = batch!!.images.map { it.uri }
        assertEquals(
            "重算结果必须包含两卷各自的同 ID 行（不折叠、不猜卷）",
            setOf(p10.uri, p11.uri, s10.uri, s12.uri), uris.toSet()
        )
    }

    @Test
    fun memberMissingSinceSaveHoldsGroupAndResumesWhenVisible() = runBlocking {
        // 快照缺席不能证明用户已处理：旧成员键也必须持有整组及原断点。
        seedLegacyGroup("legacy3", legacyGroupId, listOf(10L, 11L))
        val onlyP10 = listOf(p10)
        val engine = AlbumCleanupEngine(context)
        val restored = engine.getCheckpointBatch("legacy3", onlyP10)
        assertNull(restored)
        assertEquals(listOf(legacyGroupId) to 0, engine.getCheckpoint("legacy3"))
        assertEquals(setOf(legacyGroupId), preferences.getAlbumGroupIds("legacy3"))
        assertNull(engine.getNextBatch("legacy3", onlyP10))
        assertFalse(preferences.isAlbumCleanupCompleted("legacy3"))
        val full = engine.getCheckpointBatch("legacy3", listOf(p10, p11))
        assertNotNull(full)
        assertEquals(
            setOf(p10.uri, p11.uri),
            full!!.first.images.map { it.uri }.toSet()
        )
        assertEquals(0, full.second)
        assertEquals(listOf(expectedGroupA().id), full.first.groupIds)
    }

    // ========== 永久处理标记的旧格式兼容（唯一映射迁移 / 歧义保留） ==========

    @Test
    fun legacyUniqueMarkerMigratesToNewGroupId() = runBlocking {
        // 单卷库中旧标记 (start, firstId) 能唯一映射到恢复/重检的组 → 迁移到
        // 新身份 ID，用户已处理意图保留（不得要求用户重复处理）。
        seedLegacyGroup(album.id, legacyGroupId, listOf(10L, 11L), withCheckpoint = false, processedMarker = true)
        val singleVolume = listOf(p10, p11)
        val engine = AlbumCleanupEngine(context)
        engine.getNextBatch(album.id, singleVolume)

        val processed = preferences.getAlbumPermanentlyProcessedGroups(album.id)
        assertTrue(
            "旧标记必须迁移到当前组的新身份 ID",
            processed.contains(expectedGroupA().id)
        )
        assertFalse(
            "已迁移的旧格式条目应被替换（幂等，不残留重复标记）",
            processed.contains(legacyGroupId)
        )
    }

    @Test
    fun legacyAmbiguousMarkerIsKeptNotGuessed() = runBlocking {
        // 跨卷库中旧标记 (start, firstId=10) 命中两个候选组 → 不得猜选；
        // 保留歧义记录原样（待解析），不扩大处理范围。
        seedLegacyGroup(album.id, legacyGroupId, listOf(10L), withCheckpoint = false, processedMarker = true)
        val engine = AlbumCleanupEngine(context)
        engine.getNextBatch(album.id, allImages)

        val processed = preferences.getAlbumPermanentlyProcessedGroups(album.id)
        assertTrue(
            "无法唯一映射的旧标记必须原样保留（不得清空用户标记）",
            processed.contains(legacyGroupId)
        )
        assertEquals(
            "歧义标记不得被猜测迁移到任一新组",
            setOf(legacyGroupId), processed
        )
    }

    @Test
    fun repeatedLoadAndMigrationIsIdempotent() = runBlocking {
        seedLegacyGroup(album.id, legacyGroupId, listOf(10L, 11L), withCheckpoint = false, processedMarker = true)
        val singleVolume = listOf(p10, p11)
        val engine = AlbumCleanupEngine(context)
        engine.getNextBatch(album.id, singleVolume)
        val afterFirst = preferences.getAlbumPermanentlyProcessedGroups(album.id)

        val engine2 = AlbumCleanupEngine(context)
        engine2.getNextBatch(album.id, singleVolume)
        val afterSecond = preferences.getAlbumPermanentlyProcessedGroups(album.id)

        assertEquals("重复加载/迁移必须幂等", afterFirst, afterSecond)
        assertTrue(afterFirst.contains(expectedGroupA().id))
        assertFalse(afterFirst.contains(legacyGroupId))
    }

    @Test
    fun checkpointWithMigratedMarkerDoesNotResurrectProcessedGroup() = runBlocking {
        // 复审边角闭环：升级窗口内断点记录旧口径 ID、标记已迁移到新身份 ID
        // ——断点恢复按新 ID 比对标记，不得复活已处理的组。
        seedLegacyGroup(album.id, legacyGroupId, listOf(10L, 11L), withCheckpoint = true, processedMarker = true)
        val singleVolume = listOf(p10, p11)
        val engine = AlbumCleanupEngine(context)
        val restored = engine.getCheckpointBatch(album.id, singleVolume)
        assertNull(
            "断点内唯一组已被处理（标记已迁移）时不得复活该组",
            restored
        )
        val processed = preferences.getAlbumPermanentlyProcessedGroups(album.id)
        assertTrue(
            "迁移后的新标记必须保留",
            processed.contains(expectedGroupA().id)
        )
    }

    // ========== 复验轮一：旧 checkpoint 歧义不得猜选组 ==========

    @Test
    fun ambiguousLegacyCheckpointAbandonsResumeWithoutGuessing() = runBlocking {
        // 两卷两组共用旧别名（T_10）、旧断点 index=1（order 含两次别名）：
        // 旧别名在重检组集合中有两个候选——不得取第一/最后一组、不得把旧
        // 索引套到任一候选。明确放弃续接并安全重算；不清除永久处理意图、
        // 不自动标记任一候选。
        seedLegacyGroup("ambig", legacyGroupId, listOf(10L), withCheckpoint = false)
        preferences.saveAlbumCleanupCheckpoint("ambig", listOf(legacyGroupId, legacyGroupId), 1)
        val engine = AlbumCleanupEngine(context)
        val restored = engine.getCheckpointBatch("ambig", allImages)
        assertNull(
            "歧义旧断点必须放弃续接，不得返回猜选组＋旧索引",
            restored
        )

        // 安全重算：完整成员可用（不跳过任何一卷的照片）。
        val batch = engine.getNextBatch("ambig", allImages)
        assertNotNull("放弃续接后必须能安全重算出完整批次", batch)
        assertEquals(
            "重算批次必须覆盖两卷全部照片（不跳过）",
            setOf(p10.uri, p11.uri, s10.uri, s12.uri),
            batch!!.images.map { it.uri }.toSet()
        )
        // 不自动标记任一候选已处理（无标记写入）。
        assertEquals(
            "放弃续接不得写入任何永久处理标记",
            emptySet<String>(),
            preferences.getAlbumPermanentlyProcessedGroups("ambig")
        )
    }

    // ========== 复验轮二：迁移后持久化主键和统计一致 ==========

    @Test
    fun legacyMigrationRewritesAllGroupKeysConsistently() = runBlocking {
        // 唯一可解析的旧数据迁移后：组目录（groups_）、成员（group_images_）、
        // 数量（group_size_）、永久标记（processed_）必须使用一致的新身份——
        // 否则已处理数 1→0、剩余数失真、完成状态错误。
        seedLegacyGroup(album.id, legacyGroupId, listOf(10L, 11L), withCheckpoint = false, processedMarker = true)
        preferences.saveGroupImageCounts(album.id, mapOf(legacyGroupId to 2))
        val singleVolume = listOf(p10, p11)
        val engine = AlbumCleanupEngine(context)
        engine.getNextBatch(album.id, singleVolume)

        val newId = expectedGroupA().id
        assertEquals(
            "永久标记必须迁移到新身份",
            setOf(newId), preferences.getAlbumPermanentlyProcessedGroups(album.id)
        )
        assertEquals(
            "组目录必须改写为新身份（旧 ID 不得残留）",
            setOf(newId), preferences.getAlbumGroupIds(album.id)
        )
        assertNull(
            "旧键下的成员数据必须迁移走",
            preferences.getGroupImageIds(album.id, legacyGroupId)
        )
        assertEquals(
            "成员数据必须落在新身份键下",
            listOf("external_primary|10", "external_primary|11"),
            preferences.getGroupImageIds(album.id, newId)
        )
        assertEquals(
            "组数量必须落在新身份键下",
            2, preferences.getGroupImageCount(album.id, newId)
        )
        assertEquals(
            "旧键的数量键必须清除",
            0, preferences.getGroupImageCount(album.id, legacyGroupId)
        )
        assertEquals(
            "已处理数必须一致（修复前组目录与标记口径分裂导致 1→0）",
            1, preferences.getAlbumProcessedGroups(album.id)
        )
        assertTrue(
            "全部组已处理时完成状态必须成立",
            preferences.isAlbumCleanupCompleted(album.id)
        )
        assertEquals(
            "剩余照片数必须一致",
            0, preferences.getAlbumRemainingImages(album.id)
        )
    }

    // ========== 复验轮三：断点位置有效性 ==========

    @Test
    fun checkpointIndexNotReusedWhenMemberDeletedFromGroup() = runBlocking {
        // 审计复现场景：A=[a1,a2]、B=[b1,b2]；断点 [A,B] index=1（下一个
        // 待处理 = a2）。删除 a2 后重新进入清理（入口先重分析、不清断点）：
        // A 变为单成员孤儿组 A′（成员摘要 ID 变化，旧 A 无法匹配被丢弃），
        // 批次只剩 [b1,b2]——旧实现仍用 index=1 从 b2 开始，b1 未浏览即被
        // 跳过，完成后整个 B 被永久标记。批次组成变化后不得沿用旧位置索引：
        // 放弃断点，从正常批次入口重新开始（孤儿照片经孤儿组照常进入批次，
        // 不遗漏）；仅清断点，不清除永久处理意图、不自动标记未浏览组。
        val engine1 = AlbumCleanupEngine(context)
        engine1.analyzeAlbum(album, allImages).toList()
        engine1.saveCheckpoint(listOf(expectedGroupA().id, expectedGroupB().id), 1)

        // 删除 a2 后重分析（真实入口顺序：analyzeAlbum → getCheckpointBatch）。
        val afterDelete = listOf(p10, s10, s12)
        engine1.analyzeAlbum(album, afterDelete).toList()

        val fresh = AlbumCleanupEngine(context)
        val restored = fresh.getCheckpointBatch(album.id, afterDelete)
        assertNull(
            "批次组成变化后不得沿用旧位置索引（b1 不得被跳过）",
            restored
        )

        // 实际回退路径：正常批次入口重开——孤儿 a1 与 B 组完整成员都不遗漏。
        val batch = fresh.getNextBatch(album.id, afterDelete)
        assertNotNull("放弃断点后必须能重开完整批次", batch)
        assertEquals(
            "重开批次必须覆盖孤儿照片与 B 组全部成员（不跳过、不遗漏）",
            setOf(p10.uri, s10.uri, s12.uri),
            batch!!.images.map { it.uri }.toSet()
        )
        assertFalse(
            "未浏览的 B 组不得被提前永久标记",
            preferences.getAlbumPermanentlyProcessedGroups(album.id).contains(expectedGroupB().id)
        )
    }

    @Test
    fun checkpointIndexNotReusedWhenContentEditedAfterSave() = runBlocking {
        // 同族边界：保存后 a1 内容被编辑（DATE_MODIFIED 提升到组内最新，
        // 组内排序移动）——成员集不变（组身份不变）但旧位置指向的图像已
        // 不可信：放弃断点安全重开。
        val engine1 = AlbumCleanupEngine(context)
        engine1.analyzeAlbum(album, allImages).toList()
        engine1.saveCheckpoint(listOf(expectedGroupA().id, expectedGroupB().id), 1)

        val edited = listOf(
            p10.copy(dateModified = T + 9_000L),
            p11,
            s10,
            s12
        )
        engine1.analyzeAlbum(album, edited).toList()

        val fresh = AlbumCleanupEngine(context)
        val restored = fresh.getCheckpointBatch(album.id, edited)
        assertNull(
            "保存后内容编辑使旧位置不可确认，必须放弃断点",
            restored
        )

        val batch = fresh.getNextBatch(album.id, edited)
        assertNotNull(batch)
        assertEquals(
            setOf(p10.uri, p11.uri, s10.uri, s12.uri),
            batch!!.images.map { it.uri }.toSet()
        )
    }
}
