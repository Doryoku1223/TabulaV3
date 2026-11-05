package com.tabula.v3.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.function.Supplier

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CreateBucketTransactionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: BucketProvider
    private lateinit var manager: SystemAlbumSyncManager

    @Before fun setup() {
        provider = BucketProvider(context)
        ShadowContentResolver.registerProviderInternal("media", provider)
        val constructor = SystemAlbumSyncManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        manager = constructor.newInstance(context)
    }

    @Test fun successPublishesAndDeletesOnlyItsPlaceholder() = runBlocking {
        assertEquals("Pictures/Tabula/Transaction", manager.createBucket("Transaction"))
        assertFalse(provider.displayName.startsWith("."))
        assertEquals(listOf(provider.item), provider.updates)
        assertEquals(listOf(provider.item), provider.deletes)
        assertFalse(provider.exists)
    }

    @Test fun failedWritesAndReadbackNeverPublishAndRemoveOnlyOwnedUri() = runBlocking {
        for (failure in listOf("null-output", "write", "short", "null-input")) {
            provider.reset(failure)
            assertNull(failure, manager.createBucket("Transaction"))
            assertTrue(failure, provider.updates.isEmpty())
            assertEquals(failure, listOf(provider.item), provider.deletes)
            assertFalse(failure, provider.exists)
        }
    }

    @Test fun publishOrDeleteFailureNeverReportsSuccess() = runBlocking {
        for (failure in listOf("publish-zero", "publish-throw", "delete-zero", "delete-throw")) {
            provider.reset(failure)
            assertNull(failure, manager.createBucket("Transaction"))
            assertTrue(failure, provider.deletes.isNotEmpty())
            assertTrue(failure, provider.deletes.all { it == provider.item })
            if (failure.startsWith("delete")) assertTrue(failure, provider.exists)
            else assertFalse(failure, provider.exists)
        }
    }

    @Test fun cancellationAfterInsertPropagatesAndCleansOwnedUri() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        provider.afterInsert = { scope.cancel("synthetic cancellation") }
        val result = scope.async { manager.createBucket("Transaction") }
        try {
            result.await()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf(provider.item), provider.deletes)
            assertFalse(provider.exists)
        } finally { scope.cancel() }
    }

    private class BucketProvider(context: Context) : ContentProvider() {
        val item: Uri = Uri.parse("content://media/external/images/media/4711")
        private val resolver = shadowOf(context.contentResolver)
        var failure = ""
        var exists = false
        var displayName = ""
        var afterInsert: (() -> Unit)? = null
        val updates = mutableListOf<Uri>()
        val deletes = mutableListOf<Uri>()
        private var data = byteArrayOf()

        fun reset(mode: String) {
            failure = mode; exists = false; updates.clear(); deletes.clear(); data = byteArrayOf()
        }
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/jpeg"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(projection ?: arrayOf(MediaStore.Images.Media._ID))
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            exists = true
            displayName = values!!.getAsString(MediaStore.Images.Media.DISPLAY_NAME)
            resolver.registerOutputStreamSupplier(item, Supplier {
                if (failure == "null-output") null else object : OutputStream() {
                    val buffer = ByteArrayOutputStream()
                    override fun write(b: Int) { if (failure == "write") throw IOException("write"); buffer.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        if (failure == "write") throw IOException("write")
                        buffer.write(b, off, if (failure == "short") 1 else len)
                    }
                    override fun close() { data = buffer.toByteArray() }
                }
            })
            resolver.registerInputStreamSupplier(item, Supplier {
                if (failure == "null-input") null else ByteArrayInputStream(data)
            })
            afterInsert?.invoke()
            return item
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            updates += uri
            if (failure == "publish-throw") throw IllegalStateException("publish")
            return if (failure == "publish-zero") 0 else 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deletes += uri
            if (failure == "delete-throw") throw IllegalStateException("delete")
            if (failure == "delete-zero") return 0
            exists = false
            return 1
        }
    }
}
