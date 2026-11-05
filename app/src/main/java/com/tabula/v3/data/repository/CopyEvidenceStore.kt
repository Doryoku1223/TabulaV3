package com.tabula.v3.data.repository

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 一条已核验的副本证据：源图（键）在 [destinationUri] 处存在内容一致的独立副本，
 * 写入时机是复制事务发布成功之后；清理前仍须按当前内容重新核验。
 */
data class CopyEvidenceEntry(
    val destinationUri: String,
    val byteCount: Long,
    val sha256Hex: String,
    val albumId: String,
    val recordedAt: Long
)

internal interface CopyEvidenceStorage {
    fun read(): String?
    fun write(content: String)
}

internal class AtomicCopyEvidenceStorage(
    file: File,
    private val writeBytes: (FileOutputStream, ByteArray) -> Unit = { stream, bytes -> stream.write(bytes) },
    private val commit: (AtomicFile, FileOutputStream) -> Unit = { atomic, stream -> atomic.finishWrite(stream) }
) : CopyEvidenceStorage {
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
        if (read() != content) throw IOException("副本证据文件提交失败")
    }
}

/**
 * 原子持久化的 source→verified destination 账本。
 *
 * 读失败/解析失败一律按"无证据"处理（保守：不会让任何源变成可清理）；
 * 写失败只报告失败并保留既有内容，绝不以空账本覆盖。
 */
class CopyEvidenceStore internal constructor(private val storage: CopyEvidenceStorage) {

    constructor(context: Context) : this(
        AtomicCopyEvidenceStorage(File(context.filesDir, COPY_EVIDENCE_FILE))
    )

    private val mutex = Mutex()

    suspend fun get(sourceUri: String): CopyEvidenceEntry? = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked()[sourceUri] }
    }

    suspend fun put(sourceUri: String, entry: CopyEvidenceEntry): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readForWriteUnlocked() ?: return@withContext false
            try {
                storage.write(toJson(current + (sourceUri to entry)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist copy evidence for $sourceUri", e)
                return@withContext false
            }
            true
        }
    }

    suspend fun removeAll(sourceUris: Set<String>): Unit = withContext(Dispatchers.IO) {
        if (sourceUris.isEmpty()) return@withContext
        mutex.withLock {
            val current = readForWriteUnlocked() ?: return@withLock
            val updated = current.filterKeys { it !in sourceUris }
            if (updated.size == current.size) return@withLock
            try {
                storage.write(toJson(updated))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove copy evidence for ${sourceUris.size} sources", e)
            }
        }
    }

    suspend fun removeForAlbum(albumId: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readForWriteUnlocked() ?: return@withLock
            val updated = current.filterValues { it.albumId != albumId }
            if (updated.size == current.size) return@withLock
            try {
                storage.write(toJson(updated))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove copy evidence for album $albumId", e)
            }
        }
    }

    suspend fun loadAll(): Map<String, CopyEvidenceEntry> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
    }

    /** Read failure must never be reported as a populated ledger. */
    private fun readUnlocked(): Map<String, CopyEvidenceEntry> {
        val raw = try {
            storage.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Copy evidence ledger unreadable; treating as empty", e)
            return emptyMap()
        } ?: return emptyMap()
        return try {
            parseEntries(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Copy evidence ledger corrupt; treating as empty", e)
            emptyMap()
        }
    }

    /** Unlike [readUnlocked], a read I/O failure blocks the caller from writing an empty ledger. */
    private fun readForWriteUnlocked(): Map<String, CopyEvidenceEntry>? {
        val raw = try {
            storage.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Copy evidence ledger unreadable; refusing to overwrite", e)
            return null
        }
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            parseEntries(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Copy evidence ledger corrupt; recovering from empty", e)
            emptyMap()
        }
    }

    private fun parseEntries(raw: String): Map<String, CopyEvidenceEntry> {
        val root = JSONObject(raw)
        val entries = root.optJSONObject("entries") ?: return emptyMap()
        val result = mutableMapOf<String, CopyEvidenceEntry>()
        entries.keys().forEach { key ->
            val obj = entries.optJSONObject(key) ?: return@forEach
            result[key] = CopyEvidenceEntry(
                destinationUri = obj.getString("destinationUri"),
                byteCount = obj.getLong("byteCount"),
                sha256Hex = obj.getString("sha256Hex"),
                albumId = obj.optString("albumId"),
                recordedAt = obj.optLong("recordedAt", 0L)
            )
        }
        return result
    }

    private fun toJson(entries: Map<String, CopyEvidenceEntry>): String {
        val entriesJson = JSONObject()
        entries.forEach { (sourceUri, entry) ->
            entriesJson.put(
                sourceUri,
                JSONObject()
                    .put("destinationUri", entry.destinationUri)
                    .put("byteCount", entry.byteCount)
                    .put("sha256Hex", entry.sha256Hex)
                    .put("albumId", entry.albumId)
                    .put("recordedAt", entry.recordedAt)
            )
        }
        return JSONObject().put("version", 1).put("entries", entriesJson).toString()
    }

    companion object {
        private const val TAG = "CopyEvidenceStore"
        private const val COPY_EVIDENCE_FILE = "copy_evidence.json"
    }
}
