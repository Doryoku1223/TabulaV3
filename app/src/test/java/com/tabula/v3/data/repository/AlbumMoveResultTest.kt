package com.tabula.v3.data.repository

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AlbumMoveResultTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val firstImageUri = Uri.parse("content://media/external/images/media/10")
    private val secondImageUri = Uri.parse("content://media/external/images/media/11")
    private lateinit var provider: AlbumMoveProvider
    private lateinit var manager: AlbumManager

    @Before
    fun setUp() {
        provider = AlbumMoveProvider(context)
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        manager = AlbumManager(context)
    }

    @Test
    fun unsuccessfulUpdateReportsOneUnmovedItemWithoutCopyOrDelete() = runBlocking {
        provider.updateResults[10L] = 0

        val result = manager.moveImagesToAlbum(listOf(10L), "source", "target")

        assertEquals(0, result.movedCount)
        assertEquals(1, result.retainedCount)
        assertEquals(listOf(firstImageUri), provider.updateUris)
        assertNoCopyOrDelete()
    }

    @Test
    fun mixedSuccessFailureAndMissingIdCountOnlySuccessfulUpdateAsMoved() = runBlocking {
        provider.updateResults[10L] = 1
        provider.updateResults[11L] = 0

        val result = manager.moveImagesToAlbum(listOf(10L, 11L, 999L), "source", "target")

        assertEquals(1, result.movedCount)
        // retainedCount means the requested item was not moved by this operation; it also includes missing IDs.
        assertEquals(2, result.retainedCount)
        assertEquals(setOf(firstImageUri, secondImageUri), provider.updateUris.toSet())
        assertNoCopyOrDelete()
    }

    private fun assertNoCopyOrDelete() {
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.openFileCalls)
    }

    private class AlbumMoveProvider(private val context: android.content.Context) : ContentProvider() {
        private val firstFile = File(context.cacheDir, "batch-source-10.jpg").apply {
            parentFile?.mkdirs()
            writeBytes("synthetic-first-photo".toByteArray())
        }
        private val secondFile = File(context.cacheDir, "batch-source-11.jpg").apply {
            parentFile?.mkdirs()
            writeBytes("synthetic-second-photo".toByteArray())
        }
        val updateResults = mutableMapOf<Long, Int>()
        val updateUris = mutableListOf<Uri>()
        var deleteCalls = 0
        var insertCalls = 0
        var openFileCalls = 0

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            val columns = projection ?: arrayOf(MediaStore.Images.Media._ID)
            val cursor = MatrixCursor(columns)
            val sourceId = uri.lastPathSegment?.toLongOrNull()

            if (sourceId == 10L || sourceId == 11L) {
                val file = if (sourceId == 10L) firstFile else secondFile
                cursor.addRow(columns.map { column -> imageValue(column, sourceId, file, "source") }.toTypedArray())
                return cursor
            }

            if (uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
                if (selection?.contains(MediaStore.Images.Media.BUCKET_DISPLAY_NAME) == true) {
                    val bucketName = selectionArgs?.firstOrNull() ?: return cursor
                    if (bucketName == "target") {
                        val targetDir = File(context.cacheDir, "batch-target-bucket").apply { mkdirs() }
                        cursor.addRow(columns.map { column ->
                            imageValue(column, 200L, targetDir.resolve("placeholder.jpg"), bucketName)
                        }.toTypedArray())
                    }
                    return cursor
                }

                cursor.addRow(columns.map { column -> imageValue(column, 10L, firstFile, "source") }.toTypedArray())
                cursor.addRow(columns.map { column -> imageValue(column, 11L, secondFile, "source") }.toTypedArray())
            }
            return cursor
        }

        private fun imageValue(column: String, id: Long, file: File, bucket: String): Any? = when (column) {
            MediaStore.Images.Media._ID -> id
            MediaStore.Images.Media.DISPLAY_NAME -> file.name
            MediaStore.Images.Media.DATE_MODIFIED -> 1_700_000_000L
            MediaStore.Images.Media.SIZE -> file.length()
            MediaStore.Images.Media.WIDTH -> 640
            MediaStore.Images.Media.HEIGHT -> 480
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> bucket
            MediaStore.Images.Media.ORIENTATION -> 0
            MediaStore.Images.Media.DATA -> file.absolutePath
            MediaStore.Images.Media.RELATIVE_PATH -> if (bucket == "target") "Pictures/Tabula/target/" else "Pictures/source/"
            else -> null
        }

        override fun getType(uri: Uri) = "image/jpeg"

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            insertCalls++
            return Uri.parse("content://media/external/images/media/200")
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 1.also { deleteCalls++ }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            updateUris += uri
            return uri.lastPathSegment?.toLongOrNull()?.let(updateResults::get) ?: 0
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openFileCalls++
            val file = when (uri.lastPathSegment?.toLongOrNull()) {
                10L -> firstFile
                11L -> secondFile
                else -> File(context.cacheDir, "batch-copy.jpg")
            }
            val flags = if (mode.startsWith("r")) {
                ParcelFileDescriptor.MODE_READ_ONLY
            } else {
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            }
            return ParcelFileDescriptor.open(file, flags)
        }
    }
}
