package com.tabula.v3.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tabula.v3.data.model.ImageFile
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only images created by this test, on an explicitly opted-in emulator. */
@RunWith(AndroidJUnit4::class)
class VolumeDeletionDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val created = mutableListOf<Uri>()
    private val directories = mutableListOf<File>()
    private data class Fixture(val primary: Uri, val merged: Uri, val id: Long)

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    @Before fun prepare() {
        check(InstrumentationRegistry.getArguments().getString("allowSyntheticMediaProbe") == "true")
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Emulator-only probe" }
        shell("pm grant " + context.packageName + " " + Manifest.permission.READ_MEDIA_IMAGES)
        val deadline = SystemClock.uptimeMillis() + 5000
        while (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED &&
            SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES))
        val mounted = MediaStore.getExternalVolumeNames(context)
        val recent = MediaStore.getRecentExternalVolumeNames(context)
        assertTrue("Primary storage must be mounted", MediaStore.VOLUME_EXTERNAL_PRIMARY in mounted)
        val observation = JSONObject().put("sdk", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT)
            .put("mounted", JSONArray(mounted.toList())).put("recent", JSONArray(recent.toList()))
            .put("actuallyUnmounted", JSONArray((recent - mounted).toList()))
            .put("storageState", Environment.getExternalStorageState())
        File(context.filesDir, "volume-observations.jsonl").appendText(observation.toString() + "\n")
    }

    private fun fixture(): Fixture {
        val name = "TabulaVolumeProbe_" + UUID.randomUUID() + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TabulaVolumeProbe/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val primary = requireNotNull(context.contentResolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
        created.add(primary)
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.MAGENTA)
            context.contentResolver.openOutputStream(primary)!!.use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
            }
        } finally { bitmap.recycle() }
        context.contentResolver.update(primary, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
        val id = ContentUris.parseId(primary)
        return Fixture(primary, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), id)
    }

    private fun deleteOwnedFixture(image: Fixture): Int {
        val deleted = context.contentResolver.delete(image.primary, null, null)
        if (deleted > 0) created.remove(image.primary)
        return deleted
    }

    private fun Fixture.record() = ImageFile(id, merged, "synthetic.jpg", 1L, 100L, 32, 48, null, deletedAt = 1L)

    private fun isolatedManager(): RecycleBinManager {
        val directory = File(context.cacheDir, "volume-probe-" + UUID.randomUUID()).apply { mkdirs() }
        directories.add(directory)
        return RecycleBinManager(object : ContextWrapper(context) {
            override fun getFilesDir(): File = directory
        })
    }

    private fun verifier() = FileOperationManager(context,
        createDeleteRequest = { _, _ -> error("Recovery must not launch another delete request") })

    @Test fun actualProviderConfirmsLegacyUriMissingAfterOwnedPhotoDeletion() = runBlocking {
        val image = fixture()
        val service = verifier()
        assertEquals(setOf(image.merged), service.verifyDeletionState(listOf(image.merged)).existing)
        assertEquals(1, deleteOwnedFixture(image))
        try {
            context.contentResolver.openFileDescriptor(image.merged, "r")?.use {
                fail("Deleted fixture must no longer be readable")
            }
        } catch (_: java.io.FileNotFoundException) { }
        val result = service.verifyDeletionState(listOf(image.merged))
        assertEquals(setOf(image.merged), result.deleted)
        assertTrue(result.unknown.isEmpty())
    }

    @Test fun previousPendingRecordRecoversAndNewReadableRecordIsRetained() = runBlocking {
        val old = fixture()
        val added = fixture()
        val manager = isolatedManager()
        manager.addAllToRecycleBin(listOf(old.record(), added.record()))
        manager.beginDeletion(listOf(old.merged))
        manager.finishDeletion(emptySet(), setOf(old.merged))
        assertEquals(1, deleteOwnedFixture(old))
        val service = verifier()
        val result = reconcileRecycleBinDeletion(manager, verify = service::verifyDeletionState)
        assertEquals(setOf(old.merged), result.deleted)
        assertTrue(result.unknown.isEmpty())
        assertTrue(manager.pendingDeletionUris().isEmpty())
        assertEquals(listOf(added.id), manager.loadRecycleBin().map { it.id })
        assertEquals(setOf(added.merged), service.verifyDeletionState(listOf(added.merged)).existing)
        context.contentResolver.openInputStream(added.merged)!!.use { assertTrue(it.read() >= 0) }
        assertTrue(reconcileRecycleBinDeletion(manager, verify = service::verifyDeletionState).deleted.isEmpty())
    }

    @Test fun stillExistingPhotoIsNeverRemovedDuringReconciliation() = runBlocking {
        val image = fixture()
        val manager = isolatedManager()
        manager.addToRecycleBin(image.record())
        manager.beginDeletion(listOf(image.merged))
        val result = reconcileRecycleBinDeletion(manager, verify = verifier()::verifyDeletionState)
        assertEquals(setOf(image.merged), result.existing)
        assertTrue(result.deleted.isEmpty())
        assertEquals(listOf(image.id), manager.loadRecycleBin().map { it.id })
        assertTrue(manager.pendingDeletionUris().isEmpty())
    }

    @After fun cleanupOnlyOwnedFixtures() {
        created.forEach { context.contentResolver.delete(it, null, null) }
        directories.forEach { directory ->
            check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            directory.deleteRecursively()
        }
    }
}
