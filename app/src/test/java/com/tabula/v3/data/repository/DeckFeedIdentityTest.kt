package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

/**
 * C1-T03-DECKFEED-VOLUME：deck 待整理数据源（getAllImagesExcludingTabulaAlbums）
 * 必须与其余仓库查询路径一致地携带 T03 身份字段（volumeName/relativePath）。
 * 同名跨卷/跨目录的身份区分依赖这两个字段；deck 行缺失卷身份时，后续消费簇
 * 无法区分同数值 ID 的跨卷行（TC13）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DeckFeedIdentityTest {
    private val app: android.app.Application get() = RuntimeEnvironment.getApplication()
    private val context: Context get() = app
    private lateinit var provider: EvidenceTestProvider
    private lateinit var repository: LocalImageRepository

    @Before
    fun setUp() {
        provider = EvidenceTestProvider(
            context,
            File(context.cacheDir, "deck-feed-seed.jpg").apply { writeBytes(byteArrayOf(1)) },
            Uri.parse("content://media/external_primary/images/media/10"),
            Uri.parse("content://media/external_primary/images/media/10?alias=source"),
            Uri.parse("content://media/external_primary/files/10")
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(app).grantPermissions(android.Manifest.permission.READ_MEDIA_IMAGES)
        provider.rows.clear()
        repository = LocalImageRepository(context)
    }

    private fun row(id: Long, volume: String, bucket: String, path: String) {
        val file = File(context.cacheDir, "deck-feed-$id.jpg").apply { writeBytes(byteArrayOf(id.toByte())) }
        provider.rows[id] = EvidenceMediaRow(
            id = id,
            file = file,
            volumeName = volume,
            displayName = "row$id.jpg",
            bucketName = bucket,
            relativePath = path,
            dateModified = 1_700_000_000L + id
        )
    }

    @Test
    fun deckFeedRowsCarryVolumeIdentityAndKeepTabulaExclusion() = runBlocking {
        // 两卷同名 bucket + 一个 Tabula 路径行：卷身份必须逐行透传，Tabula 排除保持。
        row(1L, "external_primary", "Camera", "DCIM/Camera/")
        row(2L, "external_secondary", "Camera", "DCIM/Camera/")
        row(3L, "external_primary", "Private", "Pictures/Tabula/Private/")

        val deck = repository.getAllImagesExcludingTabulaAlbums()

        assertEquals(
            "Tabula 路径行必须保持被排除，其余行全部返回",
            setOf(1L, 2L), deck.map { it.id }.toSet()
        )
        val primary = deck.first { it.id == 1L }
        val secondary = deck.first { it.id == 2L }
        assertEquals("external_primary", primary.volumeName)
        assertEquals("DCIM/Camera/", primary.relativePath)
        assertEquals("external_secondary", secondary.volumeName)
        assertEquals("DCIM/Camera/", secondary.relativePath)
        assertTrue(
            "跨卷同名 bucket 的行不得因身份字段缺失而不可区分",
            primary.volumeName != secondary.volumeName || primary.relativePath != secondary.relativePath
        )
    }
}
