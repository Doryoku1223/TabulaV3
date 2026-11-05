package com.tabula.v3.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.function.Supplier

/**
 * Shared fake MediaStore provider for T01 evidence/cleanup tests.
 * Verbatim adaptation of MediaStoreCopySafetyTest.CopyProvider so the original
 * 29 assertions keep their own private copy untouched.
 */
internal enum class EvidenceInsertBehavior {
    FRESH,
    NULL,
    SOURCE_URI,
    SOURCE_ALIAS,
    UNKNOWN_IDENTITY
}

internal class EvidenceTestProvider(
    private val context: Context,
    sourceFile: File,
    private val sourceUri: Uri,
    private val sourceAliasUri: Uri,
    private val sourceFilesCollectionAliasUri: Uri
) : ContentProvider() {
    private val streamResolver: ShadowContentResolver = shadowOf(context.contentResolver)
    private val syntheticTargetDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "Tabula/Target"
    ).apply { mkdirs() }
    private val additionalVolumeRows = mutableMapOf<Pair<String, Long>, EvidenceMediaRow>()

    val sourceRow = EvidenceMediaRow(
        id = 10L,
        file = sourceFile,
        displayName = sourceFile.name,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        dateModified = 1_700_000_000L
    )
    private val targetBucketRow = EvidenceMediaRow(
        id = 12L,
        file = File(syntheticTargetDirectory, "target-seed.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) },
        displayName = "target-seed.jpg",
        bucketName = "Target",
        relativePath = "Pictures/Tabula/Target/",
        dateModified = 1_700_000_001L
    )
    val rows = mutableMapOf<Long, EvidenceMediaRow>(10L to sourceRow, 12L to targetBucketRow)
    val unknownIdentityIds = mutableSetOf<Long>()
    val openReadUris = mutableListOf<Uri>()
    val openWriteUris = mutableListOf<Uri>()
    val updateUris = mutableListOf<Uri>()
    val publishUpdateUris = mutableListOf<Uri>()
    val dateModifiedUpdateUris = mutableListOf<Uri>()
    val deleteUris = mutableListOf<Uri>()
    val events = mutableListOf<String>()
    var insertCalls = 0
    var insertBehavior = EvidenceInsertBehavior.FRESH
    var insertedTargetUri: Uri? = null
        private set
    var failSourceReadOpen = false
    var failSourceReadAfterBytes: Int? = null
    var sourceEofAfterBytes: Int? = null
    var failSourceClose = false
    var returnNullSourceRead = false
    var failTargetWriteOpen = false
    var returnNullTargetWrite = false
    var failTargetWrite = false
    var dropTargetWritesAfterBytes: Int? = null
    var failTargetFlush = false
    var failTargetClose = false
    var failTargetReadback = false
    var returnNullTargetReadback = false
    var corruptTargetReadback = false
    var publishResult = 1
    var publishException: Exception? = null
    var deleteResult = 1
    var deleteException: Exception? = null
    var sourceSizeMissing = false
    var sourceSizeOverride: Long? = null
    var sourceReportsAggregateVolume = false
    var afterInsert: (() -> Unit)? = null
    var afterSourceRead: (() -> Unit)? = null
    var afterPublish: (() -> Unit)? = null
    private var nextId = 100L

    init {
        registerInputStream(sourceUri, sourceRow)
        registerInputStream(sourceAliasUri, sourceRow)
        registerInputStream(sourceFilesCollectionAliasUri, sourceRow)
        registerOutputStream(sourceUri, sourceRow)
        registerOutputStream(sourceAliasUri, sourceRow)
        registerOutputStream(sourceFilesCollectionAliasUri, sourceRow)
    }

    override fun onCreate() = true

    fun addTargetCandidate(
        displayName: String,
        bytes: ByteArray,
        idOverride: Long? = null,
        volumeName: String = "external_primary",
        bucketName: String = "Target",
        relativePath: String = "Pictures/Tabula/Target/"
    ): Uri {
        val id = idOverride ?: nextId++
        val file = File(syntheticTargetDirectory, "candidate-$volumeName-$id.jpg").apply { writeBytes(bytes) }
        val row = EvidenceMediaRow(
            id = id,
            file = file,
            volumeName = volumeName,
            displayName = displayName,
            bucketName = bucketName,
            relativePath = relativePath,
            dateModified = 1_700_000_002L
        )
        if (volumeName == "external_primary") {
            rows[id] = row
        } else {
            additionalVolumeRows[volumeName to id] = row
        }
        val uri = itemUri(id, volumeName)
        registerInputStream(uri, row)
        return uri
    }

    fun identityFor(uri: Uri): EvidenceMediaIdentity? {
        val row = rowFor(uri) ?: return null
        return EvidenceMediaIdentity(row.volumeName, row.id)
    }

    fun resetCopyState() {
        rows.entries.removeAll { it.key >= 100L }
        additionalVolumeRows.values.forEach { it.file.delete() }
        additionalVolumeRows.clear()
        insertedTargetUri = null
        insertCalls = 0
        insertBehavior = EvidenceInsertBehavior.FRESH
        failSourceReadOpen = false
        failSourceReadAfterBytes = null
        sourceEofAfterBytes = null
        failSourceClose = false
        returnNullSourceRead = false
        failTargetWriteOpen = false
        returnNullTargetWrite = false
        failTargetWrite = false
        dropTargetWritesAfterBytes = null
        failTargetFlush = false
        failTargetClose = false
        failTargetReadback = false
        returnNullTargetReadback = false
        corruptTargetReadback = false
        publishResult = 1
        publishException = null
        deleteResult = 1
        deleteException = null
        sourceSizeMissing = false
        sourceSizeOverride = null
        sourceReportsAggregateVolume = false
        afterInsert = null
        afterSourceRead = null
        afterPublish = null
        openReadUris.clear()
        openWriteUris.clear()
        updateUris.clear()
        publishUpdateUris.clear()
        dateModifiedUpdateUris.clear()
        deleteUris.clear()
        events.clear()
    }

    fun cleanSyntheticFiles() {
        syntheticTargetDirectory.listFiles()?.forEach { it.delete() }
        syntheticTargetDirectory.delete()
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
        val itemId = uri.lastPathSegment?.toLongOrNull()
        if (itemId != null) {
            val row = rowFor(uri) ?: return cursor
            if (itemId in unknownIdentityIds && projection?.contains(MediaStore.MediaColumns.VOLUME_NAME) == true) {
                return cursor
            }
            cursor.addRow(columns.map { column -> valueFor(row, column) }.toTypedArray())
            return cursor
        }

        var matches = (rows.values + additionalVolumeRows.values).toList()
        if (selection != null && selectionArgs != null) {
            val selectedColumns = listOf(
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
            ).filter { selection.contains(it) }.sortedBy(selection::indexOf)
            selectedColumns.forEachIndexed { index, column ->
                val expected = selectionArgs.getOrNull(index) ?: return@forEachIndexed
                matches = when (column) {
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> matches.filter { it.bucketName == expected }
                    MediaStore.Images.Media.DISPLAY_NAME -> matches.filter { it.displayName == expected }
                    MediaStore.Images.Media.RELATIVE_PATH -> matches.filter { it.relativePath == expected || it.relativePath.trimEnd('/') == expected.trimEnd('/') }
                    else -> matches
                }
            }
        }
        if (sortOrder?.contains(MediaStore.Images.Media.DATE_MODIFIED) == true && sortOrder.contains("DESC", ignoreCase = true)) {
            matches = matches.sortedByDescending { it.dateModified }
        }
        matches.forEach { row -> cursor.addRow(columns.map { column -> valueFor(row, column) }.toTypedArray()) }
        return cursor
    }

    private fun valueFor(row: EvidenceMediaRow, column: String): Any? = when (column) {
        MediaStore.Images.Media._ID -> row.id
        MediaStore.MediaColumns.VOLUME_NAME -> when {
            row.id in unknownIdentityIds -> null
            row.id == 10L && sourceReportsAggregateVolume -> "external"
            else -> row.volumeName
        }
        MediaStore.Images.Media.DISPLAY_NAME -> row.displayName
        MediaStore.Images.Media.DATE_TAKEN -> row.dateTaken
        MediaStore.Images.Media.DATE_ADDED -> row.dateAdded
        MediaStore.Images.Media.DATE_MODIFIED -> row.dateModified
        MediaStore.Images.Media.SIZE -> when {
            row.id == 10L && sourceSizeMissing -> null
            row.id == 10L && sourceSizeOverride != null -> sourceSizeOverride
            else -> row.file.length()
        }
        MediaStore.Images.Media.WIDTH -> 640
        MediaStore.Images.Media.HEIGHT -> 480
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> row.bucketName
        MediaStore.Images.Media.ORIENTATION -> 0
        MediaStore.Images.Media.DATA -> row.file.absolutePath
        MediaStore.Images.Media.RELATIVE_PATH -> row.relativePath
        MediaStore.Images.Media.IS_PENDING -> if (row.isPending) 1 else 0
        else -> null
    }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        insertCalls++
        if (insertBehavior == EvidenceInsertBehavior.NULL) return null
        if (insertBehavior == EvidenceInsertBehavior.SOURCE_URI) {
            insertedTargetUri = sourceUri
            afterInsert?.invoke()
            return sourceUri
        }
        if (insertBehavior == EvidenceInsertBehavior.SOURCE_ALIAS) {
            insertedTargetUri = sourceAliasUri
            afterInsert?.invoke()
            return sourceAliasUri
        }

        val id = nextId++
        val file = File(syntheticTargetDirectory, "inserted-$id.jpg").apply { writeBytes(byteArrayOf()) }
        val relativePath = values?.getAsString(MediaStore.Images.Media.RELATIVE_PATH) ?: "Pictures/Tabula/Target/"
        val row = EvidenceMediaRow(
            id = id,
            file = file,
            displayName = values?.getAsString(MediaStore.Images.Media.DISPLAY_NAME) ?: file.name,
            bucketName = relativePath.trimEnd('/').substringAfterLast('/'),
            relativePath = if (relativePath.endsWith('/')) relativePath else "$relativePath/",
            dateModified = values?.getAsLong(MediaStore.Images.Media.DATE_MODIFIED) ?: 1_700_000_003L,
            dateTaken = values?.getAsLong(MediaStore.Images.Media.DATE_TAKEN) ?: 1_700_000_003L,
            dateAdded = values?.getAsLong(MediaStore.Images.Media.DATE_ADDED) ?: 1_700_000_003L,
            isPending = values?.getAsInteger(MediaStore.Images.Media.IS_PENDING) == 1
        )
        rows[id] = row
        if (insertBehavior == EvidenceInsertBehavior.UNKNOWN_IDENTITY) unknownIdentityIds += id
        insertedTargetUri = itemUri(id)
        registerInputStream(insertedTargetUri!!, row)
        registerOutputStream(insertedTargetUri!!, row)
        events += "insert:$id"
        afterInsert?.invoke()
        return insertedTargetUri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deleteUris += uri
        val id = uri.lastPathSegment?.toLongOrNull()
        val row = rowFor(uri)
        deleteException?.let { throw it }
        if (deleteResult == 0) {
            events += "delete:$id"
            return 0
        }
        if (row != null) {
            if (additionalVolumeRows[row.volumeName to row.id] === row) {
                additionalVolumeRows.remove(row.volumeName to row.id)
            } else {
                rows.remove(row.id)
            }
            row.file.delete()
        }
        events += "delete:$id"
        return deleteResult
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        updateUris += uri
        val id = uri.lastPathSegment?.toLongOrNull()
        val row = rowFor(uri)
        val isPublish = values?.getAsInteger(MediaStore.Images.Media.IS_PENDING) == 0
        if (isPublish) {
            publishUpdateUris += uri
            events += "publish:$id"
            publishException?.let { throw it }
            if (publishResult > 0 && row != null) row.isPending = false
            afterPublish?.invoke()
            return publishResult
        }
        if (values?.containsKey(MediaStore.Images.Media.DATE_MODIFIED) == true) {
            dateModifiedUpdateUris += uri
            row?.dateModified = values.getAsLong(MediaStore.Images.Media.DATE_MODIFIED) ?: row.dateModified
        }
        values?.getAsString(MediaStore.Images.Media.RELATIVE_PATH)?.let { row?.relativePath = if (it.endsWith('/')) it else "$it/" }
        values?.getAsInteger(MediaStore.Images.Media.IS_PENDING)?.let { row?.isPending = it == 1 }
        events += "update:$id"
        return 1
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException("Missing media row: $uri")
        val row = rowFor(uri) ?: throw FileNotFoundException("Missing media row: $uri")
        if (mode.startsWith("r")) {
            openReadUris += uri
            events += "open:r:$id"
            if (id == 10L && failSourceReadOpen) throw FileNotFoundException("Synthetic source stream unavailable")
            if (id != 10L && id != 12L && failTargetReadback) throw FileNotFoundException("Synthetic readback unavailable")
            if (id != 10L && id != 12L && corruptTargetReadback) {
                row.file.writeBytes(ByteArray(row.file.length().toInt()) { (it + 17).toByte() })
                corruptTargetReadback = false
            }
        } else {
            openWriteUris += uri
            events += "open:w:$id"
            if (id != 10L && failTargetWriteOpen) throw FileNotFoundException("Synthetic destination stream unavailable")
        }

        val flags = when {
            mode.startsWith("r") -> ParcelFileDescriptor.MODE_READ_ONLY
            id != 10L && failTargetWriteOpen -> ParcelFileDescriptor.MODE_READ_ONLY
            else -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        }
        return ParcelFileDescriptor.open(row.file, flags)
    }

    private fun registerInputStream(uri: Uri, row: EvidenceMediaRow) {
        streamResolver.registerInputStreamSupplier(uri, Supplier {
            openReadUris += uri
            events += "open:r:${row.id}"
            if (row.id == 10L && returnNullSourceRead) {
                null
            } else if (row.id != 10L && row.id != 12L && returnNullTargetReadback) {
                null
            } else {
                createInputStream(uri, row)
            }
        })
    }

    private fun registerOutputStream(uri: Uri, row: EvidenceMediaRow) {
        streamResolver.registerOutputStreamSupplier(uri, Supplier {
            openWriteUris += uri
            events += "open:w:${row.id}"
            if (row.id != 10L && returnNullTargetWrite) null else createOutputStream(uri, row)
        })
    }

    private fun createInputStream(uri: Uri, row: EvidenceMediaRow): InputStream {
        val id = row.id
        if (id == 10L && failSourceReadOpen) throw FileNotFoundException("Synthetic source stream unavailable")
        if (id != 10L && id != 12L && failTargetReadback) throw FileNotFoundException("Synthetic readback unavailable")
        if (id != 10L && id != 12L && corruptTargetReadback) {
            row.file.writeBytes(ByteArray(row.file.length().toInt()) { (it + 17).toByte() })
            corruptTargetReadback = false
        }

        val delegate = FileInputStream(row.file)
        var bytesRead = 0
        return object : InputStream() {
            private fun failIfReadLimitReached() {
                val failAfter = failSourceReadAfterBytes
                if (id == 10L && failAfter != null && bytesRead >= failAfter) {
                    throw IOException("Synthetic source read failure")
                }
            }

            override fun read(): Int {
                if (id == 10L && sourceEofAfterBytes?.let { bytesRead >= it } == true) return -1
                failIfReadLimitReached()
                val value = delegate.read()
                if (value >= 0) {
                    bytesRead++
                    if (id == 10L) notifyAfterSourceRead()
                }
                return value
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (id == 10L && sourceEofAfterBytes?.let { bytesRead >= it } == true) return -1
                failIfReadLimitReached()
                val failAfter = failSourceReadAfterBytes
                val eofAfter = if (id == 10L) sourceEofAfterBytes else null
                val limits = listOfNotNull(failAfter, eofAfter)
                val allowedLength = limits.minOrNull()?.let { minOf(length, it - bytesRead) } ?: length
                if (allowedLength <= 0) throw IOException("Synthetic source read failure")
                val count = delegate.read(buffer, offset, allowedLength)
                if (count > 0) {
                    bytesRead += count
                    if (id == 10L) notifyAfterSourceRead()
                }
                return count
            }

            private fun notifyAfterSourceRead() {
                val callback = afterSourceRead ?: return
                afterSourceRead = null
                callback()
            }

            override fun close() {
                events += "close:r:$id"
                delegate.close()
                if (id == 10L && failSourceClose) throw IOException("Synthetic source close failure")
            }
        }
    }

    private fun createOutputStream(uri: Uri, row: EvidenceMediaRow): OutputStream {
        val id = row.id
        if (id != 10L && failTargetWriteOpen) throw FileNotFoundException("Synthetic destination stream unavailable")
        val delegate = FileOutputStream(row.file)
        var bytesWritten = 0
        return object : OutputStream() {
            override fun write(value: Int) {
                if (id != 10L && failTargetWrite) throw IOException("Synthetic destination write failure")
                val limit = if (id != 10L) dropTargetWritesAfterBytes else null
                if (limit == null || bytesWritten < limit) {
                    delegate.write(value)
                    bytesWritten++
                }
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                if (id != 10L && failTargetWrite) throw IOException("Synthetic destination write failure")
                val limit = if (id != 10L) dropTargetWritesAfterBytes else null
                val allowedLength = if (limit == null) length else minOf(length, (limit - bytesWritten).coerceAtLeast(0))
                if (allowedLength > 0) {
                    delegate.write(buffer, offset, allowedLength)
                    bytesWritten += allowedLength
                }
            }

            override fun flush() {
                events += "flush:w:$id"
                if (id != 10L && failTargetFlush) throw IOException("Synthetic destination flush failure")
                delegate.flush()
            }

            override fun close() {
                events += "close:w:$id"
                delegate.close()
                if (id != 10L && failTargetClose) throw IOException("Synthetic destination close failure")
            }
        }
    }

    private fun itemUri(id: Long, volumeName: String = "external") =
        Uri.parse("content://media/$volumeName/images/media/$id")

    private fun rowFor(uri: Uri): EvidenceMediaRow? {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val volume = uri.pathSegments.firstOrNull()
        return volume?.let { additionalVolumeRows[it to id] } ?: rows[id]
    }
}

internal data class EvidenceMediaIdentity(val volumeName: String, val id: Long)

internal data class EvidenceMediaRow(
    val id: Long,
    val file: File,
    val volumeName: String = "external_primary",
    var displayName: String,
    var bucketName: String,
    var relativePath: String,
    var dateModified: Long,
    var dateTaken: Long = 1_700_000_000_000L,
    var dateAdded: Long = 1_700_000_000L,
    var isPending: Boolean = false
)
