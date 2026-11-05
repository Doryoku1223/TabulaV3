package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal interface RecycleBinStorage {
    fun read(): String?
    fun write(content: String)
}

internal class AtomicRecycleBinStorage(
    file: File,
    private val writeBytes: (java.io.FileOutputStream, ByteArray) -> Unit = { stream, bytes -> stream.write(bytes) },
    private val commit: (AtomicFile, java.io.FileOutputStream) -> Unit = { atomic, stream -> atomic.finishWrite(stream) }
) : RecycleBinStorage {
    private val atomic = AtomicFile(file)
    override fun read(): String? {
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        return atomic.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
    }
    override fun write(content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val stream = atomic.startWrite()
        try {
            writeBytes(stream, bytes)
            stream.fd.sync()
            commit(atomic, stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
        // Android AtomicFile can log rename failures rather than throw.
        if (read() != content) throw IOException("回收站文件提交失败")
    }
}

class RecycleBinPersistenceException(cause: Exception) : IOException("回收站记录未能读取或保存", cause)

/** All read-modify-write transactions, including migration and the recovery journal, share a lock. */
class RecycleBinManager internal constructor(
    private val storage: RecycleBinStorage,
    private val pendingStorage: RecycleBinStorage,
    private val beforeDiscard: (List<ImageFile>) -> Unit = {}
) {
    constructor(context: Context) : this(
        AtomicRecycleBinStorage(File(context.filesDir, "recycle_bin.json")),
        AtomicRecycleBinStorage(File(context.filesDir, "recycle_bin_pending.json")),
        { rows -> AppPreferences(context).acknowledgeCleanupMembers(rows.map { it.mediaIdentityKey() }.toSet()) }
    )

    companion object {
        @Volatile private var instance: RecycleBinManager? = null
        fun getInstance(context: Context): RecycleBinManager =
            instance ?: synchronized(this) {
                instance ?: RecycleBinManager(context.applicationContext).also { instance = it }
            }
    }

    private val mutex = Mutex()

    private suspend fun <T> transaction(action: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecycleBinPersistenceException) {
                throw e
            } catch (e: Exception) {
                throw RecycleBinPersistenceException(e)
            }
        }
    }

    suspend fun addToRecycleBin(image: ImageFile) = addAllToRecycleBin(listOf(image))

    suspend fun addAllToRecycleBin(images: List<ImageFile>) = transaction {
        val items = loadUnlocked().toMutableList()
        val now = System.currentTimeMillis()
        // T03：去重按媒体身份（同 ID 且同卷；任一侧卷未知退化纯 ID）——
        // 不同卷的同数值 ID 是不同照片，各自独立入站。
        images.forEach { image ->
            if (items.none { it.matchesSameMediaIdentity(image) }) {
                items.add(image.copy(deletedAt = now))
            }
        }
        saveUnlocked(items)
    }

    suspend fun removeFromRecycleBin(image: ImageFile) = removeAllFromRecycleBin(listOf(image))

    suspend fun removeAllFromRecycleBin(images: List<ImageFile>, permanentlyRemoved: Boolean = false) = transaction {
        // T03：按媒体身份移除，恢复一卷记录不得连带移除另一卷的同数值 ID 记录。
        val items = loadUnlocked()
        val removed = items.filter { record -> images.any { it.matchesSameMediaIdentity(record) } }
        if (permanentlyRemoved) beforeDiscard(removed)
        saveUnlocked(items - removed.toSet())
    }

    suspend fun clearRecycleBin() = transaction {
        val items = loadUnlocked() // A failed read must never be overwritten with an empty list.
        beforeDiscard(items)
        saveUnlocked(emptyList())
    }

    suspend fun loadRecycleBin(): List<ImageFile> = transaction { loadUnlocked() }
    suspend fun getRecycleBinCount(): Int = loadRecycleBin().size

    suspend fun pendingDeletionUris(): List<Uri> = transaction { pendingUnlocked() }

    /** Persist intent before handing any irreversible request to the system. */
    suspend fun beginDeletion(uris: List<Uri>) = transaction {
        loadUnlocked()
        // C1-T03-LEGACY-PENDING-ISOLATION：合并登记而非要求空日志——升级前
        // 遗留的未知条目保留在日志中，其他照片的删除照常进行且不覆盖旧日志。
        savePendingUnlocked((pendingUnlocked() + uris.distinct()).distinct())
    }

    /**
     * Commit the records first. If either write fails, recovery is safe to repeat.
     *
     * covered = 本次核验实际覆盖的 URI；null 视为全部 pending（旧调用语义）。
     * journal 结果 = 未被 covered 覆盖的条目（升级前遗留/未覆盖项，保留待
     * 重新核验）+ 本次 unknown（须 ⊆ covered，逐项裁决过）。核验为 existing
     * 的条目随 covered 一并移出 journal：记录保留、删除意图已解除，后续
     * 再删除会重新登记。
     */
    suspend fun finishDeletion(
        deleted: Set<Uri>,
        unknown: Set<Uri> = emptySet(),
        covered: Set<Uri>? = null
    ) = transaction {
        val pending = pendingUnlocked().toSet()
        require(deleted.all { it in pending } && unknown.all { it in pending })
        val coveredSet = covered ?: pending
        require(deleted.all { it in coveredSet } && unknown.all { it in coveredSet })
        val items = loadUnlocked()
        if (deleted.isNotEmpty()) {
            // Persist cleanup intent before removing the last recoverable identity record.
            // Failure retains both the recycle records and the pending recovery journal.
            beforeDiscard(items.filter { it.uri in deleted })
            saveUnlocked(items.filterNot { it.uri in deleted })
        }
        savePendingUnlocked(((pending - coveredSet) + unknown).distinct().toList())
    }

    private fun pendingUnlocked(): List<Uri> {
        val raw = pendingStorage.read() ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { Uri.parse(array.getString(it)) }.distinct()
    }

    private fun savePendingUnlocked(uris: List<Uri>) {
        pendingStorage.write(JSONArray(uris.map { it.toString() }).toString())
    }

    private fun loadUnlocked(): List<ImageFile> {
        val raw = storage.read() ?: return emptyList()
        // A present but truncated/blank file is corruption, not an empty recycle bin.
        val array = JSONArray(raw)
        val items = (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            ImageFile(
                id = obj.getLong("id"),
                uri = Uri.parse(obj.getString("uri")),
                displayName = obj.getString("displayName"),
                dateModified = obj.getLong("dateModified"),
                size = obj.getLong("size"),
                width = obj.getInt("width"),
                height = obj.getInt("height"),
                bucketDisplayName = obj.optString("bucketDisplayName", "").ifBlank { null },
                // 旧 JSON 无 orientation 键，按未旋转处理；新 JSON 往返保留。
                orientation = obj.optInt("orientation", 0),
                deletedAt = obj.optLong("deletedAt", 0L),
                volumeName = obj.optString("volumeName", "").ifBlank { null },
                relativePath = obj.optString("relativePath", "").ifBlank { null }
            )
        }
        if (items.none { it.deletedAt == 0L }) return items
        val migrated = items.mapIndexed { index, image ->
            if (image.deletedAt == 0L) image.copy(deletedAt = (index + 1).toLong()) else image
        }
        saveUnlocked(migrated)
        return migrated
    }

    private fun saveUnlocked(items: List<ImageFile>) {
        val array = JSONArray()
        items.forEach { image ->
            array.put(JSONObject().apply {
                put("id", image.id)
                put("uri", image.uri.toString())
                put("displayName", image.displayName)
                put("dateModified", image.dateModified)
                put("size", image.size)
                put("width", image.width)
                put("height", image.height)
                put("bucketDisplayName", image.bucketDisplayName ?: "")
                put("orientation", image.orientation)
                put("deletedAt", image.deletedAt)
                image.volumeName?.let { put("volumeName", it) }
                image.relativePath?.let { put("relativePath", it) }
            })
        }
        storage.write(array.toString())
    }
}
