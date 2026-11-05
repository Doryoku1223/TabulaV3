package com.tabula.v3.data.repository

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
import java.io.FileNotFoundException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.function.Supplier

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class MediaStoreCopySafetyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceUri = Uri.parse("content://media/external/images/media/10")
    private val sourceAlias = Uri.parse("content://media/external_primary/images/media/10?alias=source")
    private val sourceFilesCollectionAlias = Uri.parse("content://media/external/files/10")
    private val sourceBytes = ByteArray(257) { (it * 31).toByte() }
    private lateinit var sourceFile: File
    private lateinit var provider: CopyProvider
    private lateinit var manager: SystemAlbumSyncManager

    @Before
    fun setUp() {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Tabula/Target")
            .listFiles()
            ?.forEach { it.delete() }
        sourceFile = File(context.cacheDir, "synthetic-copy-source.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(sourceBytes)
        }
        provider = CopyProvider(context, sourceFile, sourceUri, sourceAlias, sourceFilesCollectionAlias)
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val constructor = SystemAlbumSyncManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        manager = constructor.newInstance(context)
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) provider.cleanSyntheticFiles()
        if (::sourceFile.isInitialized) sourceFile.delete()
    }

    @Test
    fun successfulCopyPublishesOnlyAfterStreamsCloseAndContentMatches() = runBlocking {
        val result = manager.addImageToSystemAlbum(sourceUri, "Target")

        assertNotNull(result)
        val targetId = result!!.lastPathSegment!!.toLong()
        assertArrayEquals(sourceBytes, provider.rows.getValue(targetId).file.readBytes())
        assertFalse(provider.rows.getValue(targetId).isPending)
        assertEquals(1, provider.publishUpdateUris.size)
        assertEquals(targetId, provider.publishUpdateUris.single().lastPathSegment!!.toLong())
        val publishIndex = provider.events.indexOf("publish:$targetId")
        assertTrue(provider.events.indexOf("open:r:$targetId") in 0 until publishIndex)
        assertTrue(provider.events.indexOf("close:r:10") in 0 until publishIndex)
        assertTrue(provider.events.indexOf("close:w:$targetId") in 0 until publishIndex)
        assertTrue(provider.events.indexOf("close:r:$targetId") in 0 until publishIndex)
        assertSourceUnchanged()
    }

    @Test
    fun missingInputStreamFailsAndRollsBackOnlyTheNewTarget() = runBlocking {
        provider.failSourceReadOpen = true

        val result = manager.addImageToSystemAlbum(sourceUri, "Target")

        assertNull(result)
        assertEquals(1, provider.insertCalls)
        assertEquals(emptyList<Uri>(), provider.publishUpdateUris)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    @Test
    fun missingOutputStreamFailsAndRollsBackOnlyTheNewTarget() = runBlocking {
        provider.failTargetWriteOpen = true

        val result = manager.addImageToSystemAlbum(sourceUri, "Target")

        assertNull(result)
        assertEquals(emptyList<Uri>(), provider.publishUpdateUris)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    @Test
    fun nullInputAndOutputStreamsFailAndRollBackOwnedTarget() = runBlocking {
        provider.returnNullSourceRead = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertTrue(provider.openReadUris.contains(sourceUri))
        assertTrue(provider.openWriteUris.isEmpty())
        assertSourceUnchanged()

        provider.resetCopyState()
        provider.returnNullTargetWrite = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertTrue(provider.openWriteUris.contains(provider.insertedTargetUri))
        assertSourceUnchanged()
    }

    @Test
    fun readWriteFlushAndCloseFailuresNeverPublishAndRollbackOwnedTarget() = runBlocking {
        provider.failSourceReadAfterBytes = 31
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertNoPublishedTargetWasKept()

        provider.resetCopyState()
        provider.failTargetWrite = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertNoPublishedTargetWasKept()

        provider.resetCopyState()
        provider.failTargetFlush = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertNoPublishedTargetWasKept()

        provider.resetCopyState()
        provider.failTargetClose = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertNoPublishedTargetWasKept()

        provider.resetCopyState()
        provider.failSourceClose = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertNoPublishedTargetWasKept()
    }

    @Test
    fun shortSourceEofAndShortDestinationWriteNeverPublish() = runBlocking {
        provider.sourceEofAfterBytes = 31
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertNoPublishedTargetWasKept()

        provider.resetCopyState()
        provider.dropTargetWritesAfterBytes = 31
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertNoPublishedTargetWasKept()
    }

    @Test
    fun unavailableReadbackAndSameLengthContentMismatchFailAndRollback() = runBlocking {
        provider.failTargetReadback = true

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertEquals(emptyList<Uri>(), provider.publishUpdateUris)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()

        provider.resetCopyState()
        provider.corruptTargetReadback = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertEquals(emptyList<Uri>(), provider.publishUpdateUris)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()

        provider.resetCopyState()
        provider.returnNullTargetReadback = true
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    @Test
    fun publishMustAffectExactlyOneRowAndFailureRollsBack() = runBlocking {
        provider.publishResult = 0

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(1, provider.publishUpdateUris.size)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()

        provider.resetCopyState()
        provider.publishResult = 2
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        assertEquals(1, provider.publishUpdateUris.size)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    @Test
    fun publishExceptionFailsAndRollsBackOwnedTarget() = runBlocking {
        provider.publishException = IllegalStateException("Synthetic publish failure")

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    @Test
    fun insertedUriEqualToSourceIsRejectedBeforeOpeningOutput() = runBlocking {
        provider.insertBehavior = InsertBehavior.SOURCE_URI

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(1, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun insertedUriThatAliasesSourceIsRejectedBeforeOpeningOutput() = runBlocking {
        provider.insertBehavior = InsertBehavior.SOURCE_ALIAS

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(1, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun unknownInsertedIdentityIsNeverWrittenOrDeleted() = runBlocking {
        provider.insertBehavior = InsertBehavior.UNKNOWN_IDENTITY

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(1, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertTrue(provider.rows.getValue(provider.insertedTargetUri!!.lastPathSegment!!.toLong()).isPending)
        assertSourceUnchanged()
    }

    @Test
    fun unknownSourceIdentityFailsBeforeInsert() = runBlocking {
        provider.unknownIdentityIds += 10L

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(0, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun aggregateExternalVolumeNameIsNotAcceptedAsActualVolumeIdentity() = runBlocking {
        provider.sourceReportsAggregateVolume = true

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(0, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun unknownSourceSizeIsNotTreatedAsZero() = runBlocking {
        provider.sourceSizeMissing = true

        val result = manager.addImageToSystemAlbum(sourceUri, "Target")

        assertNotNull(result)
        assertArrayEquals(sourceBytes, provider.rows.getValue(result!!.lastPathSegment!!.toLong()).file.readBytes())
        assertSourceUnchanged()
    }

    @Test
    fun filesCollectionAliasResolvesToSameMediaStoreItemAndCanBeCopiedSafely() = runBlocking {
        val result = manager.addImageToSystemAlbum(sourceFilesCollectionAlias, "Target")

        assertNotNull(result)
        assertArrayEquals(sourceBytes, provider.rows.getValue(result!!.lastPathSegment!!.toLong()).file.readBytes())
        assertEquals(1, provider.insertCalls)
        assertFalse(provider.deleteUris.any { provider.identityFor(it)?.id == 10L })
        assertSourceUnchanged()
    }

    @Test
    fun syncImageCopyUsesTheVerifiedTransaction() = runBlocking {
        val result = manager.syncImage(
            imageUri = sourceUri,
            albumName = "Target",
            syncMode = com.tabula.v3.data.model.SyncMode.COPY
        )

        assertNotNull(result)
        assertArrayEquals(sourceBytes, provider.rows.getValue(result!!.lastPathSegment!!.toLong()).file.readBytes())
        assertEquals(1, provider.publishUpdateUris.size)
        assertSourceUnchanged()
    }

    @Test
    fun sameNumericIdOnDifferentActualVolumeIsIndependent() = runBlocking {
        val candidateUri = provider.addTargetCandidate(
            displayName = "synthetic-copy-source.jpg",
            bytes = sourceBytes.copyOf(),
            idOverride = 10L,
            volumeName = "external_secondary"
        )

        val result = manager.addImageToSystemAlbum(sourceUri, "Target")

        assertNotNull(result)
        assertEquals(provider.identityFor(candidateUri), provider.identityFor(result!!))
        assertEquals(MediaIdentity("external_secondary", 10L), provider.identityFor(result))
        assertEquals(0, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun sameNameAndSizeButDifferentContentIsNotReused() = runBlocking {
        val differentBytes = ByteArray(sourceBytes.size) { (sourceBytes[it] + 1).toByte() }
        val existingUri = provider.addTargetCandidate("synthetic-copy-source.jpg", differentBytes)
        val existingBytes = provider.rows.getValue(existingUri.lastPathSegment!!.toLong()).file.readBytes()

        val result = manager.addImageToSystemAlbum(sourceUri, "Target")

        assertEquals(provider.insertedTargetUri, result)
        assertEquals(1, provider.insertCalls)
        assertArrayEquals(sourceBytes, provider.rows.getValue(result!!.lastPathSegment!!.toLong()).file.readBytes())
        assertArrayEquals(existingBytes, provider.rows.getValue(existingUri.lastPathSegment!!.toLong()).file.readBytes())
        assertSourceUnchanged()
    }

    @Test
    fun independentExistingCandidateWithMatchingFullContentCanBeReused() = runBlocking {
        val candidateUri = provider.addTargetCandidate("synthetic-copy-source.jpg", sourceBytes.copyOf())

        val result = manager.addImageToSystemAlbum(sourceUri, "Target")

        assertEquals(provider.identityFor(candidateUri), provider.identityFor(result!!))
        assertEquals(0, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun mismatchedKnownSourceSizeFailsAndRollsBack() = runBlocking {
        provider.sourceSizeOverride = sourceBytes.size.toLong() + 1L

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertNoPublishedTargetWasKept()
    }

    @Test
    fun nullInsertResultFailsWithoutOpeningStreams() = runBlocking {
        provider.insertBehavior = InsertBehavior.NULL

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(1, provider.insertCalls)
        assertTrue(provider.openReadUris.isEmpty())
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun sameMediaItemCandidateIsNotReportedAsIndependentCopy() = runBlocking {
        provider.sourceRow.bucketName = "Target"
        provider.sourceRow.relativePath = "Pictures/Tabula/Target/"

        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))

        assertEquals(0, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged(expectedRelativePath = "Pictures/Tabula/Target/")
    }

    @Test
    fun batchCopyDoesNotUseFilenameOnlyShortcutOrTouchWrongCandidateMetadata() = runBlocking {
        val differentBytes = ByteArray(sourceBytes.size) { (sourceBytes[it] + 1).toByte() }
        val existingUri = provider.addTargetCandidate("synthetic-copy-source.jpg", differentBytes)
        val existingBytes = provider.rows.getValue(existingUri.lastPathSegment!!.toLong()).file.readBytes()

        val result = manager.syncAlbumToSystem(
            albumName = "Target",
            imageUris = listOf(sourceUri),
            syncMode = com.tabula.v3.data.model.SyncMode.COPY
        )

        assertEquals(1, result.newlySynced)
        assertEquals(0, result.alreadyExists)
        assertEquals(0, result.failed)
        assertEquals(provider.insertedTargetUri, result.uriMapping[sourceUri.toString()])
        assertArrayEquals(sourceBytes, provider.rows.getValue(provider.insertedTargetUri!!.lastPathSegment!!.toLong()).file.readBytes())
        assertArrayEquals(existingBytes, provider.rows.getValue(existingUri.lastPathSegment!!.toLong()).file.readBytes())
        assertTrue("COPY must not re-query by filename to alter an unverified candidate", provider.dateModifiedUpdateUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun batchCopyCountsOnlyFullyVerifiedExistingContentAsAlreadyPresent() = runBlocking {
        val candidateUri = provider.addTargetCandidate("synthetic-copy-source.jpg", sourceBytes.copyOf())

        val result = manager.syncAlbumToSystem(
            albumName = "Target",
            imageUris = listOf(sourceUri),
            syncMode = com.tabula.v3.data.model.SyncMode.COPY
        )

        assertEquals(0, result.newlySynced)
        assertEquals(1, result.alreadyExists)
        assertEquals(0, result.failed)
        assertEquals(provider.identityFor(candidateUri), provider.identityFor(result.uriMapping[sourceUri.toString()]!!))
        assertEquals(0, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertTrue(provider.updateUris.isEmpty())
        assertTrue(provider.deleteUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun cancellationAfterInsertReturnRollsBackOwnedTargetAndPropagates() = runBlocking {
        val operationScope = CoroutineScope(Job() + Dispatchers.IO)
        val operationJob = operationScope.coroutineContext[Job]!!
        provider.afterInsert = { operationJob.cancel(CancellationException("Synthetic cancellation after insert")) }
        val operation = operationScope.async { manager.addImageToSystemAlbum(sourceUri, "Target") }

        try {
            operation.await()
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertTrue(expected.message.orEmpty().contains("Synthetic cancellation"))
        } finally {
            operationScope.cancel()
        }

        assertEquals(1, provider.insertCalls)
        assertTrue(provider.openWriteUris.isEmpty())
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    @Test
    fun cancellationDuringSourceCopyClosesStreamsRollsBackAndPropagates() = runBlocking {
        val operationScope = CoroutineScope(Job() + Dispatchers.IO)
        val operationJob = operationScope.coroutineContext[Job]!!
        provider.afterSourceRead = { operationJob.cancel(CancellationException("Synthetic cancellation during copy")) }
        val operation = operationScope.async { manager.addImageToSystemAlbum(sourceUri, "Target") }

        try {
            operation.await()
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertTrue(expected.message.orEmpty().contains("Synthetic cancellation"))
        } finally {
            operationScope.cancel()
        }

        assertTrue(provider.events.any { it.startsWith("close:r:10") })
        assertTrue(provider.events.any { it.startsWith("close:w:") })
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertTrue(provider.publishUpdateUris.isEmpty())
        assertSourceUnchanged()
    }

    @Test
    fun cancellationWhenPublishReturnsStillRollsBackAndPropagates() = runBlocking {
        val operationScope = CoroutineScope(Job() + Dispatchers.IO)
        val operationJob = operationScope.coroutineContext[Job]!!
        provider.afterPublish = { operationJob.cancel(CancellationException("Synthetic cancellation after publish")) }
        val operation = operationScope.async { manager.addImageToSystemAlbum(sourceUri, "Target") }

        try {
            operation.await()
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertTrue(expected.message.orEmpty().contains("Synthetic cancellation"))
        } finally {
            operationScope.cancel()
        }

        assertEquals(1, provider.publishUpdateUris.size)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    @Test
    fun rollbackFailureIsLoggedAsPossibleResidualAndNeverTouchesSource() = runBlocking {
        provider.publishResult = 0
        provider.deleteResult = 0
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        val firstTargetId = provider.insertedTargetUri!!.lastPathSegment!!.toLong()
        assertTrue(provider.rows.getValue(firstTargetId).isPending)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()

        provider.resetCopyState()
        provider.publishResult = 0
        provider.deleteException = IllegalStateException("Synthetic rollback failure")
        assertNull(manager.addImageToSystemAlbum(sourceUri, "Target"))
        val secondTargetId = provider.insertedTargetUri!!.lastPathSegment!!.toLong()
        assertTrue(provider.rows.getValue(secondTargetId).isPending)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    private fun assertSourceUnchanged(expectedRelativePath: String = "DCIM/Camera/") {
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
        assertEquals(1_700_000_000L, provider.sourceRow.dateModified)
        assertEquals(expectedRelativePath, provider.sourceRow.relativePath)
        assertEquals("synthetic-copy-source.jpg", provider.sourceRow.displayName)
        assertFalse(provider.sourceRow.isPending)
        assertFalse(provider.deleteUris.any { provider.identityFor(it)?.id == 10L })
        assertFalse(provider.updateUris.any { provider.identityFor(it)?.id == 10L })
    }

    private fun assertNoPublishedTargetWasKept() {
        assertEquals(emptyList<Uri>(), provider.publishUpdateUris)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    private enum class InsertBehavior {
        FRESH,
        NULL,
        SOURCE_URI,
        SOURCE_ALIAS,
        UNKNOWN_IDENTITY
    }

    private class CopyProvider(
        private val context: Context,
        sourceFile: File,
        private val sourceUri: Uri,
        private val sourceAliasUri: Uri,
        private val sourceFilesCollectionAliasUri: Uri
    ) : ContentProvider() {
        private val streamResolver: ShadowContentResolver = shadowOf(context.contentResolver)
        private val syntheticTargetDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Tabula/Target"
        ).apply { mkdirs() }
        private val additionalVolumeRows = mutableMapOf<Pair<String, Long>, MediaRow>()

        val sourceRow = MediaRow(
            id = 10L,
            file = sourceFile,
            displayName = sourceFile.name,
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            dateModified = 1_700_000_000L
        )
        private val targetBucketRow = MediaRow(
            id = 12L,
            file = File(syntheticTargetDirectory, "target-seed.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) },
            displayName = "target-seed.jpg",
            bucketName = "Target",
            relativePath = "Pictures/Tabula/Target/",
            dateModified = 1_700_000_001L
        )
        val rows = mutableMapOf<Long, MediaRow>(10L to sourceRow, 12L to targetBucketRow)
        val unknownIdentityIds = mutableSetOf<Long>()
        val openReadUris = mutableListOf<Uri>()
        val openWriteUris = mutableListOf<Uri>()
        val updateUris = mutableListOf<Uri>()
        val publishUpdateUris = mutableListOf<Uri>()
        val dateModifiedUpdateUris = mutableListOf<Uri>()
        val deleteUris = mutableListOf<Uri>()
        val events = mutableListOf<String>()
        var insertCalls = 0
        var insertBehavior = InsertBehavior.FRESH
        var insertedTargetUri: Uri? = null
            private set
        var failSourceReadOpen = false
        var failSourceReadAfterBytes: Int? = null
        var sourceEofAfterBytes: Int? = null
        var failSourceClose = false
        var returnNullSourceRead = false
        var failTargetWriteOpen = false
        var returnNullTargetWrite = false
        var failTargetWrite = false
        var dropTargetWritesAfterBytes: Int? = null
        var failTargetFlush = false
        var failTargetClose = false
        var failTargetReadback = false
        var returnNullTargetReadback = false
        var corruptTargetReadback = false
        var publishResult = 1
        var publishException: Exception? = null
        var deleteResult = 1
        var deleteException: Exception? = null
        var sourceSizeMissing = false
        var sourceSizeOverride: Long? = null
        var sourceReportsAggregateVolume = false
        var afterInsert: (() -> Unit)? = null
        var afterSourceRead: (() -> Unit)? = null
        var afterPublish: (() -> Unit)? = null
        private var nextId = 100L

        init {
            registerInputStream(sourceUri, sourceRow)
            registerInputStream(sourceAliasUri, sourceRow)
            registerInputStream(sourceFilesCollectionAliasUri, sourceRow)
            registerOutputStream(sourceUri, sourceRow)
            registerOutputStream(sourceAliasUri, sourceRow)
            registerOutputStream(sourceFilesCollectionAliasUri, sourceRow)
        }

        override fun onCreate() = true

        fun addTargetCandidate(
            displayName: String,
            bytes: ByteArray,
            idOverride: Long? = null,
            volumeName: String = "external_primary"
        ): Uri {
            val id = idOverride ?: nextId++
            val file = File(syntheticTargetDirectory, "candidate-$volumeName-$id.jpg").apply { writeBytes(bytes) }
            val row = MediaRow(
                id = id,
                file = file,
                volumeName = volumeName,
                displayName = displayName,
                bucketName = "Target",
                relativePath = "Pictures/Tabula/Target/",
                dateModified = 1_700_000_002L
            )
            if (volumeName == "external_primary") {
                rows[id] = row
            } else {
                additionalVolumeRows[volumeName to id] = row
            }
            val uri = itemUri(id, volumeName)
            registerInputStream(uri, row)
            return uri
        }

        fun identityFor(uri: Uri): MediaIdentity? {
            val row = rowFor(uri) ?: return null
            return MediaIdentity(row.volumeName, row.id)
        }

        fun resetCopyState() {
            rows.entries.removeAll { it.key >= 100L }
            additionalVolumeRows.values.forEach { it.file.delete() }
            additionalVolumeRows.clear()
            insertedTargetUri = null
            insertCalls = 0
            insertBehavior = InsertBehavior.FRESH
            failSourceReadOpen = false
            failSourceReadAfterBytes = null
            sourceEofAfterBytes = null
            failSourceClose = false
            returnNullSourceRead = false
            failTargetWriteOpen = false
            returnNullTargetWrite = false
            failTargetWrite = false
            dropTargetWritesAfterBytes = null
            failTargetFlush = false
            failTargetClose = false
            failTargetReadback = false
            returnNullTargetReadback = false
            corruptTargetReadback = false
            publishResult = 1
            publishException = null
            deleteResult = 1
            deleteException = null
            sourceSizeMissing = false
            sourceSizeOverride = null
            sourceReportsAggregateVolume = false
            afterInsert = null
            afterSourceRead = null
            afterPublish = null
            openReadUris.clear()
            openWriteUris.clear()
            updateUris.clear()
            publishUpdateUris.clear()
            dateModifiedUpdateUris.clear()
            deleteUris.clear()
            events.clear()
        }

        fun cleanSyntheticFiles() {
            syntheticTargetDirectory.listFiles()?.forEach { it.delete() }
            syntheticTargetDirectory.delete()
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            val columns = projection ?: arrayOf(MediaStore.Images.Media._ID)
            val cursor = MatrixCursor(columns)
            val itemId = uri.lastPathSegment?.toLongOrNull()
            if (itemId != null) {
                val row = rowFor(uri) ?: return cursor
                if (itemId in unknownIdentityIds && projection?.contains(MediaStore.MediaColumns.VOLUME_NAME) == true) {
                    return cursor
                }
                cursor.addRow(columns.map { column -> valueFor(row, column) }.toTypedArray())
                return cursor
            }

            var matches = (rows.values + additionalVolumeRows.values).toList()
            if (selection != null && selectionArgs != null) {
                val selectedColumns = listOf(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                ).filter { selection.contains(it) }.sortedBy(selection::indexOf)
                selectedColumns.forEachIndexed { index, column ->
                    val expected = selectionArgs.getOrNull(index) ?: return@forEachIndexed
                    matches = when (column) {
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> matches.filter { it.bucketName == expected }
                        MediaStore.Images.Media.DISPLAY_NAME -> matches.filter { it.displayName == expected }
                        MediaStore.Images.Media.RELATIVE_PATH -> matches.filter { it.relativePath == expected || it.relativePath.trimEnd('/') == expected.trimEnd('/') }
                        else -> matches
                    }
                }
            }
            if (sortOrder?.contains(MediaStore.Images.Media.DATE_MODIFIED) == true && sortOrder.contains("DESC", ignoreCase = true)) {
                matches = matches.sortedByDescending { it.dateModified }
            }
            matches.forEach { row -> cursor.addRow(columns.map { column -> valueFor(row, column) }.toTypedArray()) }
            return cursor
        }

        private fun valueFor(row: MediaRow, column: String): Any? = when (column) {
            MediaStore.Images.Media._ID -> row.id
            MediaStore.MediaColumns.VOLUME_NAME -> when {
                row.id in unknownIdentityIds -> null
                row.id == 10L && sourceReportsAggregateVolume -> "external"
                else -> row.volumeName
            }
            MediaStore.Images.Media.DISPLAY_NAME -> row.displayName
            MediaStore.Images.Media.DATE_TAKEN -> row.dateTaken
            MediaStore.Images.Media.DATE_ADDED -> row.dateAdded
            MediaStore.Images.Media.DATE_MODIFIED -> row.dateModified
            MediaStore.Images.Media.SIZE -> when {
                row.id == 10L && sourceSizeMissing -> null
                row.id == 10L && sourceSizeOverride != null -> sourceSizeOverride
                else -> row.file.length()
            }
            MediaStore.Images.Media.WIDTH -> 640
            MediaStore.Images.Media.HEIGHT -> 480
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> row.bucketName
            MediaStore.Images.Media.ORIENTATION -> 0
            MediaStore.Images.Media.DATA -> row.file.absolutePath
            MediaStore.Images.Media.RELATIVE_PATH -> row.relativePath
            MediaStore.Images.Media.IS_PENDING -> if (row.isPending) 1 else 0
            else -> null
        }

        override fun getType(uri: Uri) = "image/jpeg"

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            insertCalls++
            if (insertBehavior == InsertBehavior.NULL) return null
            if (insertBehavior == InsertBehavior.SOURCE_URI) {
                insertedTargetUri = sourceUri
                afterInsert?.invoke()
                return sourceUri
            }
            if (insertBehavior == InsertBehavior.SOURCE_ALIAS) {
                insertedTargetUri = sourceAliasUri
                afterInsert?.invoke()
                return sourceAliasUri
            }

            val id = nextId++
            val file = File(syntheticTargetDirectory, "inserted-$id.jpg").apply { writeBytes(byteArrayOf()) }
            val relativePath = values?.getAsString(MediaStore.Images.Media.RELATIVE_PATH) ?: "Pictures/Tabula/Target/"
            val row = MediaRow(
                id = id,
                file = file,
                displayName = values?.getAsString(MediaStore.Images.Media.DISPLAY_NAME) ?: file.name,
                bucketName = relativePath.trimEnd('/').substringAfterLast('/'),
                relativePath = if (relativePath.endsWith('/')) relativePath else "$relativePath/",
                dateModified = values?.getAsLong(MediaStore.Images.Media.DATE_MODIFIED) ?: 1_700_000_003L,
                dateTaken = values?.getAsLong(MediaStore.Images.Media.DATE_TAKEN) ?: 1_700_000_003L,
                dateAdded = values?.getAsLong(MediaStore.Images.Media.DATE_ADDED) ?: 1_700_000_003L,
                isPending = values?.getAsInteger(MediaStore.Images.Media.IS_PENDING) == 1
            )
            rows[id] = row
            if (insertBehavior == InsertBehavior.UNKNOWN_IDENTITY) unknownIdentityIds += id
            insertedTargetUri = itemUri(id)
            registerInputStream(insertedTargetUri!!, row)
            registerOutputStream(insertedTargetUri!!, row)
            events += "insert:$id"
            afterInsert?.invoke()
            return insertedTargetUri
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleteUris += uri
            val id = uri.lastPathSegment?.toLongOrNull()
            val row = rowFor(uri)
            deleteException?.let { throw it }
            if (deleteResult == 0) {
                events += "delete:$id"
                return 0
            }
            if (row != null) {
                if (additionalVolumeRows[row.volumeName to row.id] === row) {
                    additionalVolumeRows.remove(row.volumeName to row.id)
                } else {
                    rows.remove(row.id)
                }
                row.file.delete()
            }
            events += "delete:$id"
            return deleteResult
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            updateUris += uri
            val id = uri.lastPathSegment?.toLongOrNull()
            val row = rowFor(uri)
            val isPublish = values?.getAsInteger(MediaStore.Images.Media.IS_PENDING) == 0
            if (isPublish) {
                publishUpdateUris += uri
                events += "publish:$id"
                publishException?.let { throw it }
                if (publishResult > 0 && row != null) row.isPending = false
                afterPublish?.invoke()
                return publishResult
            }
            if (values?.containsKey(MediaStore.Images.Media.DATE_MODIFIED) == true) {
                dateModifiedUpdateUris += uri
                row?.dateModified = values.getAsLong(MediaStore.Images.Media.DATE_MODIFIED) ?: row.dateModified
            }
            values?.getAsString(MediaStore.Images.Media.RELATIVE_PATH)?.let { row?.relativePath = if (it.endsWith('/')) it else "$it/" }
            values?.getAsInteger(MediaStore.Images.Media.IS_PENDING)?.let { row?.isPending = it == 1 }
            events += "update:$id"
            return 1
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val id = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException("Missing media row: $uri")
            val row = rowFor(uri) ?: throw FileNotFoundException("Missing media row: $uri")
            if (mode.startsWith("r")) {
                openReadUris += uri
                events += "open:r:$id"
                if (id == 10L && failSourceReadOpen) throw FileNotFoundException("Synthetic source stream unavailable")
                if (id != 10L && id != 12L && failTargetReadback) throw FileNotFoundException("Synthetic readback unavailable")
                if (id != 10L && id != 12L && corruptTargetReadback) {
                    row.file.writeBytes(ByteArray(row.file.length().toInt()) { (it + 17).toByte() })
                    corruptTargetReadback = false
                }
            } else {
                openWriteUris += uri
                events += "open:w:$id"
                if (id != 10L && failTargetWriteOpen) throw FileNotFoundException("Synthetic destination stream unavailable")
            }

            val flags = when {
                mode.startsWith("r") -> ParcelFileDescriptor.MODE_READ_ONLY
                id != 10L && failTargetWriteOpen -> ParcelFileDescriptor.MODE_READ_ONLY
                else -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            }
            return ParcelFileDescriptor.open(row.file, flags)
        }

        private fun registerInputStream(uri: Uri, row: MediaRow) {
            streamResolver.registerInputStreamSupplier(uri, Supplier {
                openReadUris += uri
                events += "open:r:${row.id}"
                if (row.id == 10L && returnNullSourceRead) {
                    null
                } else if (row.id != 10L && row.id != 12L && returnNullTargetReadback) {
                    null
                } else {
                    createInputStream(uri, row)
                }
            })
        }

        private fun registerOutputStream(uri: Uri, row: MediaRow) {
            streamResolver.registerOutputStreamSupplier(uri, Supplier {
                openWriteUris += uri
                events += "open:w:${row.id}"
                if (row.id != 10L && returnNullTargetWrite) null else createOutputStream(uri, row)
            })
        }

        private fun createInputStream(uri: Uri, row: MediaRow): InputStream {
            val id = row.id
            if (id == 10L && failSourceReadOpen) throw FileNotFoundException("Synthetic source stream unavailable")
            if (id != 10L && id != 12L && failTargetReadback) throw FileNotFoundException("Synthetic readback unavailable")
            if (id != 10L && id != 12L && corruptTargetReadback) {
                row.file.writeBytes(ByteArray(row.file.length().toInt()) { (it + 17).toByte() })
                corruptTargetReadback = false
            }

            val delegate = FileInputStream(row.file)
            var bytesRead = 0
            return object : InputStream() {
                private fun failIfReadLimitReached() {
                    val failAfter = failSourceReadAfterBytes
                    if (id == 10L && failAfter != null && bytesRead >= failAfter) {
                        throw IOException("Synthetic source read failure")
                    }
                }

                override fun read(): Int {
                    if (id == 10L && sourceEofAfterBytes?.let { bytesRead >= it } == true) return -1
                    failIfReadLimitReached()
                    val value = delegate.read()
                    if (value >= 0) {
                        bytesRead++
                        if (id == 10L) notifyAfterSourceRead()
                    }
                    return value
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (length == 0) return 0
                    if (id == 10L && sourceEofAfterBytes?.let { bytesRead >= it } == true) return -1
                    failIfReadLimitReached()
                    val failAfter = failSourceReadAfterBytes
                    val eofAfter = if (id == 10L) sourceEofAfterBytes else null
                    val limits = listOfNotNull(failAfter, eofAfter)
                    val allowedLength = limits.minOrNull()?.let { minOf(length, it - bytesRead) } ?: length
                    if (allowedLength <= 0) throw IOException("Synthetic source read failure")
                    val count = delegate.read(buffer, offset, allowedLength)
                    if (count > 0) {
                        bytesRead += count
                        if (id == 10L) notifyAfterSourceRead()
                    }
                    return count
                }

                private fun notifyAfterSourceRead() {
                    val callback = afterSourceRead ?: return
                    afterSourceRead = null
                    callback()
                }

                override fun close() {
                    events += "close:r:$id"
                    delegate.close()
                    if (id == 10L && failSourceClose) throw IOException("Synthetic source close failure")
                }
            }
        }

        private fun createOutputStream(uri: Uri, row: MediaRow): OutputStream {
            val id = row.id
            if (id != 10L && failTargetWriteOpen) throw FileNotFoundException("Synthetic destination stream unavailable")
            val delegate = FileOutputStream(row.file)
            var bytesWritten = 0
            return object : OutputStream() {
                override fun write(value: Int) {
                    if (id != 10L && failTargetWrite) throw IOException("Synthetic destination write failure")
                    val limit = if (id != 10L) dropTargetWritesAfterBytes else null
                    if (limit == null || bytesWritten < limit) {
                        delegate.write(value)
                        bytesWritten++
                    }
                }

                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (id != 10L && failTargetWrite) throw IOException("Synthetic destination write failure")
                    val limit = if (id != 10L) dropTargetWritesAfterBytes else null
                    val allowedLength = if (limit == null) length else minOf(length, (limit - bytesWritten).coerceAtLeast(0))
                    if (allowedLength > 0) {
                        delegate.write(buffer, offset, allowedLength)
                        bytesWritten += allowedLength
                    }
                }

                override fun flush() {
                    events += "flush:w:$id"
                    if (id != 10L && failTargetFlush) throw IOException("Synthetic destination flush failure")
                    delegate.flush()
                }

                override fun close() {
                    events += "close:w:$id"
                    delegate.close()
                    if (id != 10L && failTargetClose) throw IOException("Synthetic destination close failure")
                }
            }
        }

        private fun itemUri(id: Long, volumeName: String = "external") =
            Uri.parse("content://media/$volumeName/images/media/$id")

        private fun rowFor(uri: Uri): MediaRow? {
            val id = uri.lastPathSegment?.toLongOrNull() ?: return null
            val volume = uri.pathSegments.firstOrNull()
            return volume?.let { additionalVolumeRows[it to id] } ?: rows[id]
        }
    }

    private data class MediaIdentity(val volumeName: String, val id: Long)

    private data class MediaRow(
        val id: Long,
        val file: File,
        val volumeName: String = "external_primary",
        var displayName: String,
        var bucketName: String,
        var relativePath: String,
        var dateModified: Long,
        var dateTaken: Long = 1_700_000_000_000L,
        var dateAdded: Long = 1_700_000_000L,
        var isPending: Boolean = false
    )
}
