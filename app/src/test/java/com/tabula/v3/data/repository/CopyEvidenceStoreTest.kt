package com.tabula.v3.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CopyEvidenceStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val file get() = File(context.filesDir, "copy_evidence.json")
    private val entryA = CopyEvidenceEntry(
        destinationUri = "content://media/external_primary/images/media/11",
        byteCount = 257L,
        sha256Hex = "aa".repeat(32),
        albumId = "album-1",
        recordedAt = 1_700_000_000L
    )
    private val entryB = CopyEvidenceEntry(
        destinationUri = "content://media/external_primary/images/media/12",
        byteCount = 10L,
        sha256Hex = "bb".repeat(32),
        albumId = "album-2",
        recordedAt = 1_700_000_001L
    )

    @Before
    fun setUp() {
        file.delete()
        File(file.path + ".new").delete()
        File(file.path + ".bak").delete()
    }

    @Test
    fun roundtripPutAndGet() = runBlocking {
        val store = CopyEvidenceStore(MemoryCopyEvidenceStorage())

        assertTrue(store.put("src-a", entryA))

        assertEquals(entryA, store.get("src-a"))
        assertEquals(mapOf("src-a" to entryA), store.loadAll())
    }

    @Test
    fun corruptFileReadsAsEmptyLedgerWithoutThrowing() = runBlocking {
        file.writeText("{not json")
        val store = CopyEvidenceStore(context)

        assertTrue(store.loadAll().isEmpty())
        assertNull(store.get("src-a"))
    }

    @Test
    fun readIoFailureIsConservativeEmptyButPutRefusesToOverwrite() = runBlocking {
        val store = CopyEvidenceStore(object : CopyEvidenceStorage {
            override fun read(): String? = throw IOException("injected read failure")
            override fun write(content: String) = throw IOException("injected write failure")
        })

        assertTrue(store.loadAll().isEmpty())
        assertNull(store.get("src-a"))
        assertFalse(store.put("src-b", entryB))
    }

    @Test
    fun writeFailureReturnsFalseAndPreservesPreviousContent() = runBlocking {
        val atomicStore = CopyEvidenceStore(AtomicCopyEvidenceStorage(file))
        assertTrue(atomicStore.put("src-a", entryA))
        val failingStore = CopyEvidenceStore(
            AtomicCopyEvidenceStorage(
                file,
                writeBytes = { _: FileOutputStream, _: ByteArray -> throw IOException("injected write failure") }
            )
        )

        assertFalse(failingStore.put("src-b", entryB))

        assertEquals(entryA, CopyEvidenceStore(AtomicCopyEvidenceStorage(file)).get("src-a"))
    }

    @Test
    fun atomicStoragePersistsAcrossInstances() = runBlocking {
        val store = CopyEvidenceStore(AtomicCopyEvidenceStorage(file))

        assertTrue(store.put("src-a", entryA))

        assertEquals(entryA, CopyEvidenceStore(AtomicCopyEvidenceStorage(file)).get("src-a"))
    }

    @Test
    fun removeAllDeletesOnlyRequestedEntries() = runBlocking {
        val store = CopyEvidenceStore(MemoryCopyEvidenceStorage())
        store.put("src-a", entryA)
        store.put("src-b", entryB)

        store.removeAll(setOf("src-a", "missing"))

        assertNull(store.get("src-a"))
        assertEquals(entryB, store.get("src-b"))
    }

    @Test
    fun removeAllWithEmptySetIsNoOp() = runBlocking {
        val store = CopyEvidenceStore(MemoryCopyEvidenceStorage())
        store.put("src-a", entryA)

        store.removeAll(emptySet())

        assertEquals(entryA, store.get("src-a"))
    }

    @Test
    fun removeForAlbumDeletesOnlyThatAlbumsEntries() = runBlocking {
        val store = CopyEvidenceStore(MemoryCopyEvidenceStorage())
        store.put("src-a", entryA)
        store.put("src-b", entryB)

        store.removeForAlbum("album-1")

        assertNull(store.get("src-a"))
        assertEquals(entryB, store.get("src-b"))
    }
}

internal class MemoryCopyEvidenceStorage(var content: String? = null) : CopyEvidenceStorage {
    override fun read(): String? = content
    override fun write(content: String) {
        this.content = content
    }
}
