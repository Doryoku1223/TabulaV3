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
import org.junit.Assert.assertArrayEquals
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
class SourceImageSafetyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val source = Uri.parse("content://media/external/images/media/10")
    private val sourceAlias = Uri.parse("content://media/external/images/media/10?alias=1")
    private val unreadableSource = Uri.parse("content://media/external/images/media/11")
    private lateinit var provider: SourceProvider
    private lateinit var sourceFile: File
    private lateinit var sourceRecordsFile: File
    private val sourceBytes = "synthetic-source-photo-bytes".toByteArray()

    @Before
    fun setUp() {
        sourceFile = File(context.cacheDir, "synthetic-source.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(sourceBytes)
        }
        sourceRecordsFile = File(context.filesDir, "source_images.json").apply {
            writeText("""{"album":["$source","$sourceAlias","$unreadableSource"]}""")
        }
        provider = SourceProvider(sourceFile)
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    }

    @Test
    fun sourcesWithoutVerifiedDistinctDestinationAreRetainedAndNeverCleanable() = runBlocking {
        val originalRecord = sourceRecordsFile.readText()
        val originalBytes = sourceFile.readBytes()
        val manager = AlbumManager(context)
        manager.initialize(emptyList())

        val albumResult = manager.getCleanableUrisForAlbum("album")
        val allResult = manager.getCleanableUrisForAllAlbums()

        assertTrue("No destination evidence exists for these tracked sources", albumResult.sourceUris.isEmpty())
        assertTrue(albumResult.orphanUris.isEmpty())
        assertEquals("Tracked but unverified records remain visible to the UI", 3, albumResult.sourceTotalCount)
        assertTrue(allResult.sourceUris.isEmpty())
        assertTrue(allResult.orphanUris.isEmpty())
        assertEquals(3, allResult.sourceTotalCount)
        assertEquals(setOf(source, sourceAlias, unreadableSource), manager.getSourceImagesForAlbum("album").toSet())

        val restartedManager = AlbumManager(context)
        restartedManager.initialize(emptyList())
        assertEquals(setOf(source, sourceAlias, unreadableSource), restartedManager.getSourceImagesForAlbum("album").toSet())
        assertEquals(originalRecord, sourceRecordsFile.readText())
        assertArrayEquals(originalBytes, sourceFile.readBytes())
        assertEquals(0, provider.deleteCalls)
        assertEquals(0, provider.insertCalls)
        assertEquals(0, provider.updateCalls)
        assertEquals(0, provider.openFileCalls)
    }

    private class SourceProvider(private val sourceFile: File) : ContentProvider() {
        var deleteCalls = 0
        var insertCalls = 0
        var updateCalls = 0
        var openFileCalls = 0

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            if (uri.lastPathSegment == "11") throw SecurityException("Synthetic source is unreadable")
            val columns = projection ?: arrayOf(MediaStore.Images.Media._ID)
            val cursor = MatrixCursor(columns)
            if (uri.lastPathSegment == "10") {
                cursor.addRow(columns.map(::valueFor).toTypedArray())
            }
            return cursor
        }

        private fun valueFor(column: String): Any? = when (column) {
            MediaStore.Images.Media._ID -> 10L
            MediaStore.Images.Media.DISPLAY_NAME -> sourceFile.name
            MediaStore.Images.Media.SIZE -> sourceFile.length()
            MediaStore.Images.Media.DATA -> sourceFile.absolutePath
            MediaStore.Images.Media.RELATIVE_PATH -> "DCIM/Camera/"
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> "Camera"
            else -> null
        }

        override fun getType(uri: Uri) = "image/jpeg"

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            insertCalls++
            return null
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0.also { deleteCalls++ }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
            0.also { updateCalls++ }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openFileCalls++
            return ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }
}
