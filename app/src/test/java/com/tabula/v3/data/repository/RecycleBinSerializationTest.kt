package com.tabula.v3.data.repository

import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RecycleBinSerializationTest {
    private fun image(id: Long, orientation: Int = 0) = ImageFile(
        id = id,
        uri = Uri.parse("content://media/external/images/media/$id"),
        displayName = "img$id",
        dateModified = 1_700_000_000L + id,
        size = 10L,
        width = 100,
        height = 200,
        bucketDisplayName = "Camera",
        orientation = orientation,
        deletedAt = 1_700_000_100L + id
    )

    @Test
    fun orientationRoundTripsThroughNewJson() = runBlocking {
        val storage = MemoryBinStorage()
        val manager = RecycleBinManager(storage, MemoryBinStorage())

        manager.addAllToRecycleBin(listOf(image(1L, orientation = 90), image(2L, orientation = 270)))

        val loaded = manager.loadRecycleBin()

        assertEquals(90, loaded.first { it.id == 1L }.orientation)
        assertEquals(270, loaded.first { it.id == 2L }.orientation)
    }

    @Test
    fun legacyJsonWithoutOrientationLoadsAndResaves() = runBlocking {
        val legacyJson = """[{"id":7,"uri":"content://media/external/images/media/7",""" +
            """"displayName":"old.jpg","dateModified":1700000000,"size":10,""" +
            """"width":100,"height":200,"bucketDisplayName":"Camera","deletedAt":1700000100}]"""
        val storage = MemoryBinStorage(legacyJson)
        val manager = RecycleBinManager(storage, MemoryBinStorage())

        val loaded = manager.loadRecycleBin()

        assertEquals(0, loaded.single().orientation)
        assertEquals("old.jpg", loaded.single().displayName)

        // 旧格式加载后再次保存必须保留全部既有字段（新 JSON 含 orientation）。
        manager.addAllToRecycleBin(listOf(image(8L, orientation = 45)))
        val reloaded = RecycleBinManager(storage, MemoryBinStorage()).loadRecycleBin()
        assertEquals(2, reloaded.size)
        assertEquals(0, reloaded.first { it.id == 7L }.orientation)
        assertEquals(45, reloaded.first { it.id == 8L }.orientation)
        assertEquals(1_700_000_100L, reloaded.first { it.id == 7L }.deletedAt)
    }

    @Test
    fun batchRemovePersistsAllRemovalsInOneTransaction() = runBlocking {
        val storage = MemoryBinStorage()
        val manager = RecycleBinManager(storage, MemoryBinStorage())
        manager.addAllToRecycleBin(listOf(image(1L), image(2L), image(3L)))

        manager.removeAllFromRecycleBin(listOf(image(1L), image(3L)))

        val loaded = manager.loadRecycleBin()
        assertEquals(listOf(2L), loaded.map { it.id })
        // add 一次写入 + 批量恢复单次事务写入；逐条恢复会是 3 次写入。
        assertEquals(2, storage.writes)
    }
}

internal class MemoryBinStorage(var content: String? = null) : RecycleBinStorage {
    var writes = 0
    override fun read(): String? = content
    override fun write(content: String) {
        writes++
        this.content = content
    }
}
