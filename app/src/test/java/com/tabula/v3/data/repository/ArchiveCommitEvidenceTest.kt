package com.tabula.v3.data.repository

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.tabula.v3.data.repository.AlbumManager.ArchiveCommitOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ArchiveCommitEvidenceTest {
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
        sourceFile = File(context.cacheDir, "synthetic-archive-source.jpg").apply {
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

    @Test
    fun concurrentCommitsOfSameActionCopyExactlyOnce() = runBlocking {
        repeat(5) {
            provider.resetCopyState()
            val action = albumManager.queueImageToAlbum(1L, sourceUri.toString(), "Target", "Target")

            val first = async(Dispatchers.IO) { albumManager.commitPendingArchive(action.id) }
            val second = async(Dispatchers.IO) { albumManager.commitPendingArchive(action.id) }
            val outcomes = listOf(first.await(), second.await())

            assertEquals(1, provider.insertCalls)
            assertEquals(
                setOf(ArchiveCommitOutcome.COMMITTED, ArchiveCommitOutcome.NOT_CLAIMED),
                outcomes.toSet()
            )
        }
    }

    @Test
    fun successfulCommitWritesEvidenceAndSourceRecord() = runBlocking {
        val action = albumManager.queueImageToAlbum(1L, sourceUri.toString(), "Target", "Target")

        val outcome = albumManager.commitPendingArchive(action.id)

        assertEquals(ArchiveCommitOutcome.COMMITTED, outcome)
        assertNotNull(provider.insertedTargetUri)
        val evidence = albumManager.copyEvidenceStore.loadAll()
        assertEquals(1, evidence.size)
        val entry = evidence.getValue(sourceUri.toString())
        assertEquals(provider.insertedTargetUri!!.toString(), entry.destinationUri)
        assertEquals(257L, entry.byteCount)
        assertEquals(sha256Hex(sourceBytes), entry.sha256Hex)
        assertEquals("Target", entry.albumId)
        assertEquals(listOf(sourceUri.toString()), albumManager.getSourceImagesForAlbum("Target").map { it.toString() })
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
    }

    @Test
    fun copyFailureLeavesNoLedgerOrSourceRecord() = runBlocking {
        val action = albumManager.queueImageToAlbum(1L, sourceUri.toString(), "Target", "Target")
        provider.failSourceReadOpen = true

        val outcome = albumManager.commitPendingArchive(action.id)

        assertEquals(ArchiveCommitOutcome.COPY_FAILED, outcome)
        assertEquals(1, provider.insertCalls)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertTrue(albumManager.copyEvidenceStore.loadAll().isEmpty())
        assertTrue(albumManager.getSourceImagesForAlbum("Target").isEmpty())
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
    }

    @Test
    fun evidenceWriteFailureKeepsSourceUnverifiedButArchiveCommits() = runBlocking {
        albumManager.copyEvidenceStore = CopyEvidenceStore(object : CopyEvidenceStorage {
            override fun read(): String? = null
            override fun write(content: String) = throw java.io.IOException("injected ledger write failure")
        })
        val action = albumManager.queueImageToAlbum(1L, sourceUri.toString(), "Target", "Target")

        val outcome = albumManager.commitPendingArchive(action.id)

        assertEquals(ArchiveCommitOutcome.COMMITTED, outcome)
        assertNotNull(provider.insertedTargetUri)
        assertFalse(provider.rows.getValue(provider.insertedTargetUri!!.lastPathSegment!!.toLong()).isPending)
        assertTrue(albumManager.copyEvidenceStore.loadAll().isEmpty())
        assertEquals(listOf(sourceUri.toString()), albumManager.getSourceImagesForAlbum("Target").map { it.toString() })
    }

    @Test
    fun cancelThenCommitIsNotClaimed() = runBlocking {
        val action = albumManager.queueImageToAlbum(1L, sourceUri.toString(), "Target", "Target")
        assertNotNull(albumManager.cancelLastPendingArchive())

        val outcome = albumManager.commitPendingArchive(action.id)

        assertEquals(ArchiveCommitOutcome.NOT_CLAIMED, outcome)
        assertEquals(0, provider.insertCalls)
        assertTrue(albumManager.copyEvidenceStore.loadAll().isEmpty())
    }

    @Test
    fun queueRebuildDropsPendingActionWithoutSideEffects() = runBlocking {
        // TC57 排队后重建：队列仅在内存，进程重建后旧 action 不产生任何副作用。
        val action = albumManager.queueImageToAlbum(1L, sourceUri.toString(), "Target", "Target")
        resetSingleton(AlbumManager::class.java)
        resetSingleton(SystemAlbumSyncManager::class.java)
        resetSingleton(AlbumUsageTracker::class.java)
        val rebuilt = AlbumManager(context)

        val outcome = rebuilt.commitPendingArchive(action.id)

        assertEquals(ArchiveCommitOutcome.NOT_CLAIMED, outcome)
        assertEquals(0, provider.insertCalls)
        assertTrue(rebuilt.copyEvidenceStore.loadAll().isEmpty())
        assertTrue(rebuilt.getSourceImagesForAlbum("Target").isEmpty())
    }

    @Test
    fun commitOfUnknownActionIdIsNotClaimed() = runBlocking {
        val outcome = albumManager.commitPendingArchive("no-such-action")

        assertEquals(ArchiveCommitOutcome.NOT_CLAIMED, outcome)
        assertEquals(0, provider.insertCalls)
    }

    private fun resetSingleton(clazz: Class<*>) {
        val field = clazz.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
