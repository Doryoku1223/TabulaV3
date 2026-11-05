package com.tabula.v3.data.repository

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
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
class CleanupEvidenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceUri = Uri.parse("content://media/external/images/media/10")
    private val sourceAlias = Uri.parse("content://media/external_primary/images/media/10?alias=source")
    private val sourceFilesCollectionAlias = Uri.parse("content://media/external/files/10")
    private val sourceBytes = ByteArray(257) { (it * 31).toByte() }
    private lateinit var sourceFile: File
    private lateinit var provider: EvidenceTestProvider
    private lateinit var albumManager: AlbumManager

    @Before
    fun setUp() {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Tabula/Target")
            .listFiles()
            ?.forEach { it.delete() }
        resetSingleton(AlbumManager::class.java)
        resetSingleton(SystemAlbumSyncManager::class.java)
        resetSingleton(AlbumUsageTracker::class.java)
        File(context.filesDir, "source_images.json").delete()
        File(context.filesDir, "copy_evidence.json").delete()
        sourceFile = File(context.cacheDir, "synthetic-cleanup-source.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(sourceBytes)
        }
        provider = EvidenceTestProvider(context, sourceFile, sourceUri, sourceAlias, sourceFilesCollectionAlias)
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        albumManager = AlbumManager(context)
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) provider.cleanSyntheticFiles()
        if (::sourceFile.isInitialized) sourceFile.delete()
    }

    private fun seedVerifiedDestination(): Uri {
        val destination = provider.addTargetCandidate("copy-source.jpg", sourceBytes)
        runBlocking {
            albumManager.copyEvidenceStore.put(
                sourceUri.toString(),
                CopyEvidenceEntry(
                    destinationUri = destination.toString(),
                    byteCount = sourceBytes.size.toLong(),
                    sha256Hex = sha256Hex(sourceBytes),
                    albumId = "Target",
                    recordedAt = 1_700_000_000L
                )
            )
        }
        seedSourceRecord()
        return destination
    }

    private fun seedSourceRecord() {
        // 旧 source_images.json 只有源 URI、无任何证据：直接落盘并重载。
        val json = """{"Target":["$sourceUri"]}"""
        File(context.filesDir, "source_images.json").writeText(json)
        runBlocking { albumManager.initialize() }
    }

    @Test
    fun verifiedEvidenceWithExistingDestinationYieldsCleanableSource() = runBlocking {
        seedVerifiedDestination()

        val result = albumManager.getCleanableUrisForAllAlbums()

        assertEquals(listOf(sourceUri), result.sourceUris)
        assertEquals(1, result.sourceTotalCount)
        assertEquals(0, result.unverifiedSourceCount)
    }

    @Test
    fun perAlbumQueryAlsoUsesLedgerEvidence() = runBlocking {
        seedVerifiedDestination()

        val result = albumManager.getCleanableUrisForAlbum("Target")

        assertEquals(listOf(sourceUri), result.sourceUris)
        assertEquals(0, result.unverifiedSourceCount)
    }

    @Test
    fun sourceWithoutLedgerStaysUnverified() = runBlocking {
        seedSourceRecord()

        val result = albumManager.getCleanableUrisForAllAlbums()

        assertTrue(result.sourceUris.isEmpty())
        assertEquals(1, result.sourceTotalCount)
        assertEquals(1, result.unverifiedSourceCount)
    }

    @Test
    fun missingDestinationMakesSourceUnverified() = runBlocking {
        seedSourceRecord()
        albumManager.copyEvidenceStore.put(
            sourceUri.toString(),
            CopyEvidenceEntry(
                destinationUri = "content://media/external_primary/images/media/999",
                byteCount = sourceBytes.size.toLong(),
                sha256Hex = sha256Hex(sourceBytes),
                albumId = "Target",
                recordedAt = 1_700_000_000L
            )
        )

        val result = albumManager.getCleanableUrisForAllAlbums()

        assertTrue(result.sourceUris.isEmpty())
        assertEquals(1, result.unverifiedSourceCount)
    }

    @Test
    fun destinationIdenticalToSourceIsUnverified() = runBlocking {
        seedSourceRecord()
        albumManager.copyEvidenceStore.put(
            sourceUri.toString(),
            CopyEvidenceEntry(
                destinationUri = sourceUri.toString(),
                byteCount = sourceBytes.size.toLong(),
                sha256Hex = sha256Hex(sourceBytes),
                albumId = "Target",
                recordedAt = 1_700_000_000L
            )
        )

        val result = albumManager.getCleanableUrisForAllAlbums()

        assertTrue(result.sourceUris.isEmpty())
        assertEquals(1, result.unverifiedSourceCount)
    }

    @Test
    fun deepVerificationPassesForContentMatchingDestination() = runBlocking {
        seedVerifiedDestination()

        val result = albumManager.getReverifiedCleanableUrisForAllAlbums()

        assertEquals(listOf(sourceUri), result.sourceUris)
        assertEquals(0, result.unverifiedSourceCount)
    }

    @Test
    fun destinationWithSameSizeButDifferentContentIsRejected() = runBlocking {
        seedSourceRecord()
        val tampered = provider.addTargetCandidate("copy-source.jpg", ByteArray(257) { (it + 17).toByte() })
        albumManager.copyEvidenceStore.put(
            sourceUri.toString(),
            CopyEvidenceEntry(
                destinationUri = tampered.toString(),
                byteCount = 257L,
                sha256Hex = sha256Hex(sourceBytes),
                albumId = "Target",
                recordedAt = 1_700_000_000L
            )
        )

        val result = albumManager.getReverifiedCleanableUrisForAllAlbums()

        assertTrue(result.sourceUris.isEmpty())
        assertEquals(1, result.unverifiedSourceCount)
    }

    @Test
    fun unreadableDestinationFallsBackToUnverifiedWithoutCrashing() = runBlocking {
        seedVerifiedDestination()
        provider.returnNullTargetReadback = true

        val result = albumManager.getReverifiedCleanableUrisForAllAlbums()

        assertTrue(result.sourceUris.isEmpty())
        assertEquals(1, result.unverifiedSourceCount)
    }

    @Test
    fun clearDeletedUrisPurgesLedgerEntries() = runBlocking {
        // Robolectric 的 AtomicFile 只保证一次落地写入；多写逻辑用内存存储验证。
        albumManager.copyEvidenceStore = CopyEvidenceStore(MemoryCopyEvidenceStorage())
        seedVerifiedDestination()

        albumManager.clearDeletedUris(setOf(sourceUri.toString()))

        assertTrue(albumManager.copyEvidenceStore.loadAll().isEmpty())
    }

    @Test
    fun cancellationFromDeepVerificationPropagates() = runBlocking {
        val destination = seedVerifiedDestination()
        val syncManager = SystemAlbumSyncManager.getInstance(context)
        val job = launch(Dispatchers.IO) {
            cancel()
            syncManager.verifyCopyIntegrity(sourceUri, destination)
        }

        job.join()

        assertTrue(job.isCancelled)
    }

    private fun resetSingleton(clazz: Class<*>) {
        val field = clazz.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
