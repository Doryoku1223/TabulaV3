package com.tabula.v3.data.repository

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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
class MoveSourceSafetyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceUri = Uri.parse("content://media/external/images/media/10")
    private val sourceBytes = "synthetic-source-photo-bytes".toByteArray()
    private lateinit var provider: MoveProvider
    private lateinit var sourceFile: File
    private lateinit var manager: SystemAlbumSyncManager

    @Before
    fun setUp() {
        sourceFile = File(context.cacheDir, "synthetic-source.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(sourceBytes)
        }
        provider = MoveProvider(context, sourceFile)
        ShadowContentResolver.registerProviderInternal("media", provider)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            shadowOf(context).grantPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        val constructor = SystemAlbumSyncManager::class.java.getDeclaredConstructor(android.content.Context::class.java)
        constructor.isAccessible = true
        manager = constructor.newInstance(context)
    }

    @Test
    fun zeroUpdateIsFailureWithoutCopyOrDeleteForEitherMoveEntryPoint() = runBlocking {
        provider.updateResult = 0

        assertNull(manager.moveImageToSystemAlbum(sourceUri, "system-entry"))
        assertNoCopyOrDelete()

        provider.resetCalls()
        assertNull(manager.moveImageToBucket(sourceUri, "bucket-entry", "Pictures/Tabula/bucket-entry"))
        assertNoCopyOrDelete()
        assertEquals(sourceBytes.toList(), sourceFile.readBytes().toList())
    }

    @Test
    fun updateExceptionDoesNotStartCopyOrDelete() = runBlocking {
        provider.updateException = IllegalStateException("Synthetic MediaStore update failure")

        assertNull(manager.moveImageToSystemAlbum(sourceUri, "system-entry"))
        assertEquals(1, provider.updateCalls)
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)

        provider.resetCalls()
        provider.updateException = IllegalStateException("Synthetic MediaStore update failure")
        assertNull(manager.moveImageToBucket(sourceUri, "bucket-entry", "Pictures/Tabula/bucket-entry"))
        assertEquals(1, provider.updateCalls)
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    @Test
    fun successfulRelativePathUpdatesStillReturnTheOriginalUri() = runBlocking {
        provider.updateResult = 1

        assertEquals(sourceUri, manager.moveImageToSystemAlbum(sourceUri, "system-entry"))
        assertNoCopyOrDelete()

        provider.resetCalls()
        assertEquals(sourceUri, manager.moveImageToBucket(sourceUri, "bucket-entry", "Pictures/Tabula/bucket-entry"))
        assertNoCopyOrDelete()
    }

    @Test
    fun syncImageMoveReturnsFailureAfterUnsuccessfulUpdateWithoutCopying() = runBlocking {
        provider.updateResult = 0

        assertNull(manager.syncImage(sourceUri, "sync-entry", com.tabula.v3.data.model.SyncMode.MOVE))

        assertEquals(1, provider.updateCalls)
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    @Test
    fun batchMoveCountsAnUnsuccessfulUpdateAsFailureWithoutCopying() = runBlocking {
        provider.updateResult = 0

        val result = manager.syncAlbumToSystem("batch-entry", listOf(sourceUri), com.tabula.v3.data.model.SyncMode.MOVE)

        assertEquals(0, result.newlySynced)
        assertEquals(0, result.alreadyExists)
        assertEquals(1, result.failed)
        assertFalse(result.uriMapping.containsKey(sourceUri.toString()))
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    @Test
    fun cancellationFromUpdatePropagatesWithoutRetryingCopy() = runBlocking {
        provider.updateException = CancellationException("Synthetic move cancellation")

        try {
            manager.moveImageToSystemAlbum(sourceUri, "cancel-entry")
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("Synthetic move cancellation", expected.message)
        }

        assertEquals(1, provider.updateCalls)
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    @Test
    fun cancellationFromBucketUpdatePropagatesWithoutRetryingCopy() = runBlocking {
        provider.updateException = CancellationException("Synthetic bucket move cancellation")

        try {
            manager.moveImageToBucket(sourceUri, "cancel-bucket-entry", "Pictures/Tabula/cancel-bucket-entry")
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("Synthetic bucket move cancellation", expected.message)
        }

        assertEquals(1, provider.updateCalls)
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    @Test
    @Config(sdk = [28], manifest = Config.NONE)
    fun preQMoveEntrypointsFailWithoutCopyingOrDeleting() = runBlocking {
        assertNull(manager.moveImageToSystemAlbum(sourceUri, "legacy-system-entry"))
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)

        provider.resetCalls()
        assertNull(manager.moveImageToBucket(sourceUri, "legacy-bucket-entry", "Pictures/Tabula/legacy-bucket-entry"))
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    private fun assertNoCopyOrDelete() {
        assertEquals(1, provider.updateCalls)
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    private class MoveProvider(
        private val context: android.content.Context,
        private val sourceFile: File
    ) : ContentProvider() {
        var updateResult = 0
        var updateException: Exception? = null
        var updateCalls = 0
        var deleteCalls = 0
        var insertCalls = 0
        var openFileCalls = 0

        override fun onCreate() = true

        fun resetCalls() {
            updateCalls = 0
            deleteCalls = 0
            insertCalls = 0
            openFileCalls = 0
            updateException = null
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
            if (uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
                if (selection?.contains(MediaStore.Images.Media.DISPLAY_NAME) == true) return cursor
                if (selection?.contains(MediaStore.Images.Media.BUCKET_DISPLAY_NAME) == true) {
                    val bucketName = selectionArgs?.firstOrNull() ?: "target"
                    val bucketDir = File(context.cacheDir, "synthetic-bucket-$bucketName").apply { mkdirs() }
                    cursor.addRow(columns.map { column -> valueFor(column, 77L, bucketDir.resolve("existing.jpg"), "existing.jpg", bucketName) }.toTypedArray())
                }
                return cursor
            }

            if (uri.lastPathSegment == "10") {
                cursor.addRow(columns.map { column -> valueFor(column, 10L, sourceFile, sourceFile.name, "Camera") }.toTypedArray())
            }
            return cursor
        }

        private fun valueFor(column: String, id: Long, file: File, name: String, bucket: String): Any? = when (column) {
            MediaStore.Images.Media._ID -> id
            MediaStore.Images.Media.DISPLAY_NAME -> name
            MediaStore.Images.Media.SIZE -> file.length()
            MediaStore.Images.Media.DATA -> file.absolutePath
            MediaStore.Images.Media.RELATIVE_PATH -> if (bucket == "Camera") "DCIM/Camera/" else "Pictures/$bucket/"
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> bucket
            MediaStore.Images.Media.DATE_TAKEN -> 1_700_000_000_000L
            MediaStore.Images.Media.DATE_ADDED -> 1_700_000_000L
            MediaStore.Images.Media.DATE_MODIFIED -> 1_700_000_000L
            else -> null
        }

        override fun getType(uri: Uri) = "image/jpeg"

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            insertCalls++
            return Uri.parse("content://media/external/images/media/200")
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 1.also { deleteCalls++ }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            updateCalls++
            updateException?.let { throw it }
            return updateResult
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openFileCalls++
            val file = if (uri.lastPathSegment == "10") sourceFile else File(context.cacheDir, "synthetic-copy-${uri.lastPathSegment}.jpg")
            val flags = if (mode.startsWith("r")) {
                ParcelFileDescriptor.MODE_READ_ONLY
            } else {
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            }
            return ParcelFileDescriptor.open(file, flags)
        }
    }
}
