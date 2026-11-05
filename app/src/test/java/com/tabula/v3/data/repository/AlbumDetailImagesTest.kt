package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AlbumDetailImagesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: EvidenceTestProvider
    private lateinit var albumManager: AlbumManager

    @Before
    fun setUp() {
        resetSingleton(AlbumManager::class.java)
        resetSingleton(SystemAlbumSyncManager::class.java)
        resetSingleton(AlbumUsageTracker::class.java)
        File(context.filesDir, "source_images.json").delete()
        File(context.filesDir, "copy_evidence.json").delete()
        provider = EvidenceTestProvider(
            context,
            File(context.cacheDir, "album-detail-seed.jpg").apply { writeBytes(byteArrayOf(1)) },
            Uri.parse("content://media/external_primary/images/media/10"),
            Uri.parse("content://media/external_primary/images/media/10?alias=source"),
            Uri.parse("content://media/external_primary/files/10")
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_MEDIA_IMAGES)
        provider.rows.clear()
        albumManager = AlbumManager(context)
    }

    private fun row(id: Long, bucket: String, path: String, dateModifiedSeconds: Long) {
        val file = File(context.cacheDir, "album-detail-$id.jpg").apply { writeBytes(byteArrayOf(id.toByte())) }
        provider.rows[id] = EvidenceMediaRow(
            id = id,
            file = file,
            displayName = "row$id.jpg",
            bucketName = bucket,
            relativePath = path,
            dateModified = dateModifiedSeconds
        )
        // orientation 由 provider 的 valueFor 固定返回 0，不在此断言；毫秒与字段透传为本测试焦点。
    }

    @Test
    fun albumDetailImagesCarryMillisecondDatesAndFullFields() = runBlocking {
        // MediaStore DATE_MODIFIED 为秒；仓库契约要求毫秒（TC 时间单位断言）。
        row(1L, "Camera", "DCIM/Camera/", 1_700_000_000L)
        row(2L, "Camera", "DCIM/Camera/", 1_700_000_100L)

        val images = albumManager.getAlbumDetailImages("Camera")

        assertEquals(2, images.size)
        val first = images.first { it.id == 1L }
        assertEquals(1_700_000_000_000L, first.dateModified)
        assertEquals(1_700_000_100_000L, images.first { it.id == 2L }.dateModified)
        assertEquals("Camera", first.bucketDisplayName)
        assertEquals("DCIM/Camera/", first.relativePath)
        assertEquals("external_primary", first.volumeName)
        assertEquals("row1.jpg", first.displayName)
    }

    private fun resetSingleton(clazz: Class<*>) {
        val field = clazz.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
