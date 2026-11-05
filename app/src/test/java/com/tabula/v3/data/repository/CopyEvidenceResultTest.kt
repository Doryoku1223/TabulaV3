package com.tabula.v3.data.repository

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class CopyEvidenceResultTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceUri = Uri.parse("content://media/external/images/media/10")
    private val sourceAlias = Uri.parse("content://media/external_primary/images/media/10?alias=source")
    private val sourceFilesCollectionAlias = Uri.parse("content://media/external/files/10")
    private val sourceBytes = ByteArray(257) { (it * 31).toByte() }
    private lateinit var sourceFile: File
    private lateinit var provider: EvidenceTestProvider
    private lateinit var manager: SystemAlbumSyncManager

    @Before
    fun setUp() {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Tabula/Target")
            .listFiles()
            ?.forEach { it.delete() }
        sourceFile = File(context.cacheDir, "synthetic-evidence-source.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(sourceBytes)
        }
        provider = EvidenceTestProvider(context, sourceFile, sourceUri, sourceAlias, sourceFilesCollectionAlias)
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
    fun successfulCopyReturnsDigestMatchingSourceBytes() = runBlocking {
        val result = manager.copyImageWithEvidence(sourceUri, "Target")

        assertNotNull(result)
        val targetId = result!!.uri.lastPathSegment!!.toLong()
        assertEquals(sourceBytes.size.toLong(), result.byteCount)
        assertEquals(sha256Hex(sourceBytes), result.sha256Hex)
        assertFalse(provider.rows.getValue(targetId).isPending)
        assertArrayEquals(sourceBytes, provider.rows.getValue(targetId).file.readBytes())
        assertSourceUnchanged()
    }

    @Test
    fun copyFailureReturnsNullAndRollsBackOwnedTarget() = runBlocking {
        provider.failSourceReadOpen = true

        val result = manager.copyImageWithEvidence(sourceUri, "Target")

        assertNull(result)
        assertEquals(emptyList<Uri>(), provider.publishUpdateUris)
        assertEquals(listOf(provider.insertedTargetUri), provider.deleteUris)
        assertSourceUnchanged()
    }

    private fun assertSourceUnchanged() {
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
        assertFalse(provider.deleteUris.any { it.lastPathSegment == "10" })
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
