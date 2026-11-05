package com.tabula.v3.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.tabula.v3.BuildConfig
import com.tabula.v3.data.model.MediaItemKey
import com.tabula.v3.data.model.SyncMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * 系统相册同步管理器
 *
 * 负责将 Tabula 相册同步到系统照片库。
 *
 * ## Android 相册机制
 *
 * Android 系统中没有真正的"相册"概念，MediaStore 使用 BUCKET_DISPLAY_NAME
 * 来分组图片，这个值取决于图片所在的文件夹名称。
 *
 * ## 同步策略
 *
 * 1. **创建相册文件夹**: 在 Pictures 目录下创建子文件夹
 * 2. **复制或移动图片**: 根据用户选择的模式操作
 * 3. **更新 MediaStore**: 通知系统扫描新文件
 *
 * ## 同步模式
 *
 * - **COPY**: 复制图片到系统相册，保留原图位置
 * - **MOVE**: 移动图片到系统相册，原位置不再存在
 */
class SystemAlbumSyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SystemAlbumSyncManager"
        private const val TABULA_ALBUMS_FOLDER = "Tabula"

        /**
         * T01-UIFEEDBACK 探针接缝（仿 FileOperationManager.debugForceUnknownUris）：
         * 强制 copyImageWithEvidence 失败，供设备断言归档失败 Toast 与源保留。
         * 仅测试注入；默认 false，生产路径不读取。
         */
        @Volatile
        internal var debugForceArchiveCopyFailure: Boolean = false

        @Volatile
        private var instance: SystemAlbumSyncManager? = null

        fun getInstance(context: Context): SystemAlbumSyncManager {
            return instance ?: synchronized(this) {
                instance ?: SystemAlbumSyncManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val contentResolver: ContentResolver = context.contentResolver

    /** Verifiable evidence of a copy the transaction published or proved already present. */
    data class CopyResult(
        val uri: Uri,
        val byteCount: Long,
        val sha256Hex: String
    )

    private sealed class CopyOutcome {
        data class Created(val uri: Uri, val byteCount: Long, val sha256Hex: String) : CopyOutcome()
        data class Reused(val uri: Uri, val byteCount: Long, val sha256Hex: String) : CopyOutcome()
        object Failed : CopyOutcome()

        fun uriOrNull(): Uri? = when (this) {
            is Created -> uri
            is Reused -> uri
            Failed -> null
        }

        fun toCopyResultOrNull(): CopyResult? = when (this) {
            is Created -> CopyResult(uri, byteCount, sha256Hex)
            is Reused -> CopyResult(uri, byteCount, sha256Hex)
            Failed -> null
        }
    }


    private data class SourceCopyMetadata(
        val key: MediaItemKey,
        val size: Long?,
        val dateTaken: Long?,
        val dateAdded: Long?,
        val dateModified: Long?
    )

    private data class ContentDigest(val byteCount: Long, val sha256: ByteArray)

    private data class ExistingCopyCandidate(val uri: Uri, val key: MediaItemKey)

    private class OwnedCopyTargetHolder {
        private val target = AtomicReference<Uri?>(null)

        fun set(uri: Uri) {
            target.set(uri)
        }

        fun clear() {
            target.set(null)
        }

        fun hasTarget(): Boolean = target.get() != null

        fun take(): Uri? = target.getAndSet(null)
    }

    /**
     * 获取 Tabula 相册根目录
     */
    private fun getTabulaAlbumsRoot(): File {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File(picturesDir, TABULA_ALBUMS_FOLDER)
    }

    /**
     * 获取指定相册的文件夹
     * 
     * @param albumName 相册名称
     * @param useExistingPath 如果存在同名系统相册，是否使用其路径
     */
    private fun getAlbumFolder(albumName: String, useExistingPath: Boolean = true): File {
        // 如果启用融合模式，先检查是否存在同名系统相册
        if (useExistingPath) {
            val existingPath = findExistingSystemBucketPath(albumName)
            if (existingPath != null) {
                Log.d(TAG, "Found existing system album path for '$albumName': $existingPath")
                return File(existingPath)
            }
        }
        
        // 不存在同名相册，使用默认 Tabula 目录
        val root = getTabulaAlbumsRoot()
        // 清理文件名中的非法字符
        val safeName = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return File(root, safeName)
    }
    
    /**
     * 查找系统中是否存在同名的相册，返回其路径
     * 
     * 当 App 图集名称与手机相册名称相同时，返回现有相册的路径，
     * 用于将图片同步到现有相册而不是创建新的 Tabula 子目录。
     * 
     * @param albumName 相册名称
     * @return 现有相册的绝对路径，不存在则返回 null
     */
    private fun findExistingSystemBucketPath(albumName: String): String? {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            
            val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(albumName)
            
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // 优先使用 DATA 字段获取完整路径
                    val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    if (dataIndex >= 0) {
                        val dataPath = cursor.getString(dataIndex)
                        if (!dataPath.isNullOrBlank()) {
                            val parentDir = File(dataPath).parentFile
                            if (parentDir != null && parentDir.exists()) {
                                // 检查是否是 Tabula 创建的目录，如果是则不视为"现有系统相册"
                                val tabulaRoot = getTabulaAlbumsRoot().absolutePath
                                if (!parentDir.absolutePath.startsWith(tabulaRoot)) {
                                    return parentDir.absolutePath
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error finding existing system bucket path for: $albumName", e)
        }
        return null
    }
    
    /**
     * 获取相册的相对路径（用于 MediaStore）
     * 
     * @param albumName 相册名称
     * @return 相对路径，如 "DCIM/Camera" 或 "Pictures/Tabula/MyAlbum"
     */
    private fun getAlbumRelativePath(albumName: String): String {
        val existingPath = findExistingSystemBucketPath(albumName)
        if (existingPath != null) {
            // 从绝对路径提取相对路径
            val externalStorage = Environment.getExternalStorageDirectory().absolutePath
            val relativePath = existingPath.removePrefix(externalStorage).removePrefix("/")
            Log.d(TAG, "Using existing relative path for '$albumName': $relativePath")
            return relativePath
        }
        
        // 使用默认 Tabula 路径
        val safeName = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "${Environment.DIRECTORY_PICTURES}/$TABULA_ALBUMS_FOLDER/$safeName"
    }

    /**
     * 创建系统相册（文件夹）
     *
     * 如果系统中已存在同名相册，则不创建新文件夹，直接返回现有路径。
     * 这样可以实现 App 图集与同名手机相册的融合。
     *
     * @param albumName 相册名称
     * @return 相册文件夹路径，失败返回 null
     */
    suspend fun createSystemAlbum(albumName: String): String? = withContext(Dispatchers.IO) {
        try {
            // 先检查是否存在同名系统相册
            val existingPath = findExistingSystemBucketPath(albumName)
            if (existingPath != null) {
                Log.d(TAG, "Using existing system album: $existingPath (album: $albumName)")
                return@withContext existingPath
            }
            
            // 不存在同名相册，创建新的 Tabula 子目录
            val albumFolder = getAlbumFolder(albumName, useExistingPath = false)
            if (!albumFolder.exists()) {
                val created = albumFolder.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create album folder: ${albumFolder.absolutePath}")
                    return@withContext null
                }
            }

            // 创建一个占位文件确保文件夹存在
            val placeholder = File(albumFolder, ".tabula_album")
            if (!placeholder.exists()) {
                placeholder.createNewFile()
            }

            Log.d(TAG, "Created system album: ${albumFolder.absolutePath}")
            albumFolder.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating system album: $albumName", e)
            null
        }
    }

    /**
     * 删除系统相册（文件夹）
     */
    suspend fun deleteSystemAlbum(albumName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val albumFolder = getAlbumFolder(albumName)
            if (albumFolder.exists()) {
                val placeholder = File(albumFolder, ".tabula_album")
                if (placeholder.exists()) {
                    placeholder.delete()
                }

                if (albumFolder.listFiles()?.isEmpty() == true) {
                    albumFolder.delete()
                    Log.d(TAG, "Deleted system album: ${albumFolder.absolutePath}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting system album: $albumName", e)
            false
        }
    }

    /**
     * 删除空的系统相册（根据相对路径）
     * 
     * 仅当文件夹为空时才删除，用于删除空图集功能。
     * 
     * @param relativePath 相对路径（如 "Pictures/Tabula/MyAlbum"）
     * @return true 删除成功，false 删除失败（文件夹不为空或不存在）
     */
    suspend fun deleteBucket(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            val albumFolder = File(externalStorage, relativePath)
            
            if (!albumFolder.exists()) {
                Log.w(TAG, "Bucket folder does not exist: $relativePath")
                return@withContext false
            }
            
            // 检查文件夹是否为空（忽略隐藏文件如 .nomedia）
            val files = albumFolder.listFiles() ?: emptyArray()
            val nonHiddenFiles = files.filter { !it.name.startsWith(".") }
            
            if (nonHiddenFiles.isNotEmpty()) {
                Log.w(TAG, "Cannot delete non-empty bucket: $relativePath (${nonHiddenFiles.size} files)")
                return@withContext false
            }
            
            // 删除隐藏文件
            files.forEach { it.delete() }
            
            // 删除文件夹
            val deleted = albumFolder.delete()
            if (deleted) {
                Log.d(TAG, "Deleted empty bucket: $relativePath")
            } else {
                Log.e(TAG, "Failed to delete bucket: $relativePath")
            }
            
            return@withContext deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting bucket: $relativePath", e)
            false
        }
    }

    /**
     * 重命名系统相册
     */
    suspend fun renameSystemAlbum(oldName: String, newName: String): String? = withContext(Dispatchers.IO) {
        try {
            val oldFolder = getAlbumFolder(oldName)
            val newFolder = getAlbumFolder(newName)

            if (oldFolder.exists() && !newFolder.exists()) {
                val renamed = oldFolder.renameTo(newFolder)
                if (renamed) {
                    scanMediaFolder(newFolder)
                    Log.d(TAG, "Renamed album: $oldName -> $newName")
                    return@withContext newFolder.absolutePath
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming system album: $oldName -> $newName", e)
            null
        }
    }

    /**
     * 将图片添加到系统相册（复制模式）
     *
     * @param imageUri 源图片 URI
     * @param albumName 目标相册名称
     * @param displayName 图片显示名称（可选）
     * @return 新图片的 URI，失败返回 null
     */
    suspend fun addImageToSystemAlbum(
        imageUri: Uri,
        albumName: String,
        displayName: String? = null
    ): Uri? = copyImageWithEvidence(imageUri, albumName, displayName)?.uri

    /**
     * 复制并返回可核验的副本证据（目标 URI、字节数与回读 SHA-256）。
     * 失败返回 null；Reused 语义下证据来自验证通过的既有候选内容。
     */
    suspend fun copyImageWithEvidence(
        imageUri: Uri,
        albumName: String,
        displayName: String? = null
    ): CopyResult? {
        if (BuildConfig.DEBUG && debugForceArchiveCopyFailure) {
            Log.w(TAG, "debug: forced archive copy failure (T01-UIFEEDBACK probe)")
            return null
        }
        return copyImageToSystemAlbum(imageUri, albumName, displayName).toCopyResultOrNull()
    }

    /**
     * 轻量核验：两侧媒体项身份均可解析、分属不同项目且互非别名（零字节读取）。
     * 任何失败（行缺失、身份未知、异常）都按"不独立"处理，交由调用方保守保留源。
     */
    suspend fun isIndependentExistingCopy(sourceUri: Uri, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceKey = resolveMediaItemKey(sourceUri) ?: return@withContext false
            val targetKey = resolveMediaItemKey(destinationUri) ?: return@withContext false
            if (targetKey == sourceKey) return@withContext false
            if (areCanonicalAliases(sourceUri, destinationUri)) return@withContext false
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Lightweight copy verification failed for $sourceUri -> $destinationUri", e)
            false
        }
    }

    /**
     * 深度复核：身份独立 + 源与目标全文 SHA-256 一致且非空，删除源之前的最后一道闸门。
     * 任何失败（不可读、内容不一致、长度不符、异常）都按"未验证"处理；
     * 取消不吞——CancellationException 向上抛。
     */
    suspend fun verifyCopyIntegrity(sourceUri: Uri, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isIndependentExistingCopy(sourceUri, destinationUri)) return@withContext false
            val sourceDigest = readContentDigest(sourceUri, null) ?: return@withContext false
            if (sourceDigest.byteCount == 0L) return@withContext false
            val destinationDigest = readContentDigest(destinationUri, sourceDigest.byteCount)
                ?: return@withContext false
            sameDigest(sourceDigest, destinationDigest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Deep copy verification failed for $sourceUri -> $destinationUri", e)
            false
        }
    }

    private suspend fun copyImageToSystemAlbum(
        imageUri: Uri,
        albumName: String,
        displayName: String? = null
    ): CopyOutcome {
        val ownedTarget = OwnedCopyTargetHolder()
        try {
            val result = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val albumFolder = getAlbumFolder(albumName)
                if (!albumFolder.exists()) {
                    albumFolder.mkdirs()
                }

                val fileName = displayName ?: getFileNameFromUri(imageUri)
                    ?: "IMG_${System.currentTimeMillis()}.jpg"

                // minSdk 31 >= Q: every copy goes through the verified MediaStore transaction.
                copyImageUsingMediaStore(imageUri, albumFolder.name, fileName, ownedTarget)
            }
            // Keep ownership until withContext has delivered its result. Cancellation while
            // dispatching the result is handled by the outer catch below.
            ownedTarget.clear()
            return result
        } catch (e: CancellationException) {
            cleanupOwnedCopyTarget(ownedTarget)
            throw e
        } catch (e: Exception) {
            cleanupOwnedCopyTarget(ownedTarget)
            Log.e(TAG, "Error adding image to system album", e)
            return CopyOutcome.Failed
        }
    }

    /**
     * 将图片移动到系统相册（移动模式）
     *
     * 仅通过 MediaStore 成功更新源图片的 RELATIVE_PATH 完成移动；无法更新时保留原图并返回失败。
     *
     * @param imageUri 源图片 URI
     * @param albumName 目标相册名称
     * @return 新图片的 URI，失败返回 null
     */
    suspend fun moveImageToSystemAlbum(
        imageUri: Uri,
        albumName: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, "Cannot safely move image on Android versions below Q: $imageUri")
                return@withContext null
            }

            val albumFolder = getAlbumFolder(albumName)
            if (!albumFolder.exists()) {
                albumFolder.mkdirs()
            }

            val fileName = getFileNameFromUri(imageUri) ?: "IMG_${System.currentTimeMillis()}.jpg"

            return@withContext moveImageUsingMediaStore(imageUri, albumFolder.name, fileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error moving image to system album", e)
            null
        }
    }

    /**
     * 使用 MediaStore API 移动图片
     * 
     * 优先通过更新 RELATIVE_PATH 实现真正的移动，这样可以保留：
     * - 原始 _id（不会在"最近项目"中跳到最前）
     * - 所有时间戳（DATE_TAKEN, DATE_ADDED, DATE_MODIFIED）
     * - 所有元数据（EXIF, XMP, Live Photo 视频数据等）
     * 
     * 重要：对于 Live Photo，必须使用方法1（更新路径），否则可能丢失动态效果。
     * 
     * @param sourceUri 源图片 URI
     * @param albumName 相册名称
     * @param fileName 文件名
     */
    private fun moveImageUsingMediaStore(
        sourceUri: Uri,
        albumName: String,
        fileName: String
    ): Uri? {
        try {
            // 获取目标相对路径
            val targetRelativePath = getAlbumRelativePath(albumName)
            
            Log.d(TAG, "Moving image: $fileName -> $targetRelativePath (method: update RELATIVE_PATH)")
            
            // 方法1：通过更新 RELATIVE_PATH 来移动（真正的移动，保留所有元数据和 Live Photo 数据）
            // 这是最理想的方式，因为它只改变文件位置，不改变文件内容
            val updateValues = ContentValues().apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
                // 不改变 DISPLAY_NAME，保持原始文件名
            }
            
            val updated = contentResolver.update(sourceUri, updateValues, null, null)
            if (updated > 0) {
                Log.d(TAG, "✓ Moved image via update: $fileName -> $targetRelativePath (preserved all metadata, Live Photo safe)")
                return sourceUri
            } else {
                Log.w(TAG, "✗ Failed to move via update (returned 0): $fileName")
                return null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error moving image: $fileName", e)
            return null
        }
    }

    /**
     * 根据原始 URI 查找图片在系统相册中的新 URI
     * 
     * 当图片被同步（复制/移动）到系统相册后，原始 URI 可能已失效。
     * 此方法通过文件名在指定相册中查找同步后的新 URI。
     * 
     * @param originalUri 原始图片 URI
     * @param albumName 目标相册名称
     * @return 系统相册中的新 URI，未找到则返回 null
     */
    fun findNewUriInSystemAlbum(originalUri: Uri, albumName: String): Uri? {
        val fileName = getFileNameFromUri(originalUri)
        if (fileName == null) {
            Log.w(TAG, "Cannot get filename from URI: $originalUri")
            return null
        }
        return findExistingImageInAlbum(albumName, fileName)
    }

    /**
     * 批量查找图片在系统相册中的新 URI
     * 
     * @param originalUris 原始 URI 列表
     * @param albumName 目标相册名称
     * @return Map<原始URI字符串, 新URI>，只包含成功找到的映射
     */
    fun findNewUrisInSystemAlbum(originalUris: List<Uri>, albumName: String): Map<String, Uri> {
        val result = mutableMapOf<String, Uri>()
        var fileNameNotFound = 0
        var imageNotFound = 0
        
        for (uri in originalUris) {
            val fileName = getFileNameFromUri(uri)
            if (fileName == null) {
                fileNameNotFound++
                Log.w(TAG, "Cannot get filename from URI (might be deleted): $uri")
                continue
            }
            
            val newUri = findExistingImageInAlbum(albumName, fileName)
            if (newUri != null) {
                result[uri.toString()] = newUri
                Log.d(TAG, "Found new URI: $uri -> $newUri (file: $fileName)")
            } else {
                imageNotFound++
                Log.w(TAG, "Image not found in system album '$albumName': $fileName")
            }
        }
        
        Log.d(TAG, "findNewUrisInSystemAlbum summary for '$albumName': found=${result.size}, fileNameNotFound=$fileNameNotFound, imageNotFound=$imageNotFound, total=${originalUris.size}")
        return result
    }

    /**
     * 检查目标相册中是否已存在同名文件
     * 
     * 支持检查现有系统相册（如 DCIM/Camera）和 Tabula 创建的相册。
     * 同时检查文件名和文件大小，避免同步重复照片。
     * 
     * 注意：会同时查询原始相册名和清理后的相册名（safeName），
     * 因为 BUCKET_DISPLAY_NAME 是文件夹名称，可能与原始相册名不同。
     * 
     * @param albumName 相册名称
     * @param fileName 文件名
     * @param sourceSize 源文件大小（可选，用于更精确的重复检测）
     * @return 如果已存在，返回现有文件的 URI；否则返回 null
     */
    private fun findExistingImageInAlbum(
        albumName: String,
        fileName: String,
        sourceSize: Long? = null
    ): Uri? {
        try {
            // 清理相册名中的特殊字符，与 getAlbumFolder() 保持一致
            val safeName = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            // 需要查询的 BUCKET_DISPLAY_NAME 列表：
            // 1. 原始相册名（可能是现有系统相册）
            // 2. 清理后的相册名（Tabula 创建的相册）
            val bucketNames = if (safeName != albumName) {
                listOf(albumName, safeName)
            } else {
                listOf(albumName)
            }
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.SIZE
            )
            
            for (bucketName in bucketNames) {
                val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(bucketName, fileName)
                
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        
                        // 如果提供了源文件大小，进行额外验证
                        if (sourceSize != null) {
                            val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                            if (sizeIndex >= 0) {
                                val existingSize = cursor.getLong(sizeIndex)
                                // 文件大小相同，认为是同一张照片
                                if (existingSize == sourceSize) {
                                    Log.d(TAG, "Found existing image in bucket '$bucketName': $fileName")
                                    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                                }
                            }
                        } else {
                            // 没有提供大小，仅按文件名匹配
                            Log.d(TAG, "Found existing image in bucket '$bucketName': $fileName")
                            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        }
                    }
                }
            }
            
            // Android 9 及以下的额外检查：直接检查文件系统
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val albumFolder = getAlbumFolder(albumName)
                val targetFile = File(albumFolder, fileName)
                if (targetFile.exists()) {
                    if (sourceSize == null || targetFile.length() == sourceSize) {
                        return Uri.fromFile(targetFile)
                    }
                }
            }
            
            Log.d(TAG, "Image not found in album '$albumName' (also tried '$safeName'): $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding existing image in album: $albumName/$fileName", e)
        }
        return null
    }

    /**
     * 使用 MediaStore API 复制图片 (Android 10+)
     * 
     * 支持复制到现有系统相册（如 DCIM/Camera）或 Tabula 创建的相册。
     * 
     * @param sourceUri 源图片 URI
     * @param albumName 相册名称（用于查找现有路径或创建新路径）
     * @param fileName 目标文件名
     */
    private suspend fun copyImageUsingMediaStore(
        sourceUri: Uri,
        albumName: String,
        fileName: String,
        ownedTarget: OwnedCopyTargetHolder
    ): CopyOutcome {
        var targetUri: Uri? = null
        var insertAttempted = false
        try {
            currentCoroutineContext().ensureActive()
            val targetRelativePath = normalizeRelativePath(getAlbumRelativePath(albumName))
            val sourceMetadata = readSourceCopyMetadata(sourceUri)
                ?: return CopyOutcome.Failed.also { Log.w(TAG, "Cannot verify MediaStore source identity: $sourceUri") }

            val candidates = findExistingCopyCandidates(targetRelativePath, fileName)
            var sourceDigest: ContentDigest? = null
            for (candidate in candidates) {
                if (candidate.key == sourceMetadata.key || areCanonicalAliases(sourceUri, candidate.uri)) {
                    Log.w(TAG, "Source already occupies the requested copy path; refusing self-copy: $sourceUri")
                    return CopyOutcome.Failed
                }
            }

            if (candidates.isNotEmpty()) {
                val verifiedSourceDigest = readContentDigest(sourceUri, sourceMetadata.size)
                    ?: return CopyOutcome.Failed.also { Log.w(TAG, "Cannot read source content for verified reuse: $sourceUri") }
                sourceDigest = verifiedSourceDigest
                for (candidate in candidates) {
                    currentCoroutineContext().ensureActive()
                    val candidateDigest = try {
                        readContentDigest(candidate.uri, verifiedSourceDigest.byteCount)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot verify existing copy candidate: ${candidate.uri}", e)
                        null
                    }
                    if (candidateDigest != null && candidateDigest.byteCount == verifiedSourceDigest.byteCount &&
                        candidateDigest.sha256.contentEquals(verifiedSourceDigest.sha256)
                    ) {
                        return CopyOutcome.Reused(
                            candidate.uri,
                            verifiedSourceDigest.byteCount,
                            verifiedSourceDigest.sha256.toSha256Hex()
                        )
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            val effectiveDateTaken = when {
                sourceMetadata.dateTaken != null && sourceMetadata.dateTaken > 0 -> sourceMetadata.dateTaken
                sourceMetadata.dateModified != null && sourceMetadata.dateModified > 0 -> sourceMetadata.dateModified * 1000
                else -> System.currentTimeMillis()
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(fileName))
                put(MediaStore.Images.Media.DATE_TAKEN, effectiveDateTaken)
                sourceMetadata.dateAdded?.let { put(MediaStore.Images.Media.DATE_ADDED, it) }
                sourceMetadata.dateModified?.let { put(MediaStore.Images.Media.DATE_MODIFIED, it) }
                put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            insertAttempted = true
            targetUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (targetUri == null) {
                Log.w(TAG, "MediaStore did not return a destination for copy: $fileName")
                currentCoroutineContext().ensureActive()
                return CopyOutcome.Failed
            }

            // Resolve ownership before checking cancellation: insert may return a URI after
            // the caller's Job was cancelled, and a proven new row must still be rolled back.
            val targetKey = resolveMediaItemKey(targetUri!!)
            if (targetKey == null) {
                Log.w(TAG, "Inserted copy target has unknown identity; leaving it untouched: $targetUri")
                currentCoroutineContext().ensureActive()
                targetUri = null
                return CopyOutcome.Failed
            }
            if (targetKey == sourceMetadata.key || areCanonicalAliases(sourceUri, targetUri!!)) {
                Log.e(TAG, "MediaStore insert resolved to the source item; refusing to write or delete it: $targetUri")
                currentCoroutineContext().ensureActive()
                targetUri = null
                return CopyOutcome.Failed
            }

            ownedTarget.set(targetUri!!)
            currentCoroutineContext().ensureActive()

            val copyDigest = copySourceToOwnedTarget(
                sourceUri = sourceUri,
                targetUri = targetUri!!,
                expectedSize = sourceMetadata.size
            )
            val verifiedSourceDigest = sourceDigest
            if (verifiedSourceDigest != null && !sameDigest(verifiedSourceDigest, copyDigest)) {
                throw IOException("Source content changed between duplicate check and copy")
            }
            if (copyDigest.byteCount == 0L) {
                throw IOException("Refusing to publish an empty image copy")
            }

            currentCoroutineContext().ensureActive()
            val readbackDigest = readContentDigest(targetUri!!, copyDigest.byteCount)
                ?: throw IOException("Destination readback stream was unavailable")
            if (!sameDigest(copyDigest, readbackDigest)) {
                throw IOException("Destination content did not match source bytes")
            }

            currentCoroutineContext().ensureActive()
            val publishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
                put(MediaStore.Images.Media.DATE_TAKEN, effectiveDateTaken)
                sourceMetadata.dateModified?.let { put(MediaStore.Images.Media.DATE_MODIFIED, it) }
            }
            val published = contentResolver.update(targetUri!!, publishValues, null, null)
            if (published != 1) {
                throw IOException("Publishing copy affected $published rows; expected exactly one")
            }

            currentCoroutineContext().ensureActive()
            Log.d(TAG, "Copied and verified image: $fileName -> $targetRelativePath (${copyDigest.byteCount} bytes)")
            return CopyOutcome.Created(
                targetUri!!,
                readbackDigest.byteCount,
                readbackDigest.sha256.toSha256Hex()
            )
        } catch (e: CancellationException) {
            val hadOwnedTarget = ownedTarget.hasTarget()
            cleanupOwnedCopyTarget(ownedTarget)
            if (targetUri != null && !hadOwnedTarget) {
                Log.d(TAG, "Copy cancelled before a destination could be proven owned: $targetUri")
            } else if (insertAttempted && !hadOwnedTarget) {
                Log.w(TAG, "Copy cancelled before insert returned a URI; any provider side effect cannot be safely identified")
            }
            throw e
        } catch (e: Exception) {
            cleanupOwnedCopyTarget(ownedTarget)
            Log.e(TAG, "Error copying image using MediaStore", e)
            return CopyOutcome.Failed
        }
    }

    private suspend fun readSourceCopyMetadata(sourceUri: Uri): SourceCopyMetadata? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        for (queryUri in mediaStoreQueryUris(sourceUri)) {
            currentCoroutineContext().ensureActive()
            try {
                val metadata = contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val key = mediaStoreItemKey(cursor) ?: return@use null
                        SourceCopyMetadata(
                            key = key,
                            size = nullableLong(cursor, MediaStore.Images.Media.SIZE)?.takeIf { it >= 0L },
                            dateTaken = nullableLong(cursor, MediaStore.Images.Media.DATE_TAKEN),
                            dateAdded = nullableLong(cursor, MediaStore.Images.Media.DATE_ADDED),
                            dateModified = nullableLong(cursor, MediaStore.Images.Media.DATE_MODIFIED)
                        )
                    }
                }
                if (metadata != null) return metadata
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve source MediaStore identity through $queryUri", e)
            }
        }
        return null
    }

    private fun resolveMediaItemKey(uri: Uri): MediaItemKey? {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.MediaColumns.VOLUME_NAME)
        for (queryUri in mediaStoreQueryUris(uri)) {
            try {
                val key = contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) mediaStoreItemKey(cursor) else null
                }
                if (key != null) return key
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve MediaStore item identity through $queryUri", e)
            }
        }
        return null
    }

    private fun mediaStoreItemKey(cursor: android.database.Cursor): MediaItemKey? {
        val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val volumeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
        if (idIndex < 0 || volumeIndex < 0 || cursor.isNull(idIndex) || cursor.isNull(volumeIndex)) return null
        val id = cursor.getLong(idIndex)
        val volumeName = cursor.getString(volumeIndex)?.takeIf { it.isNotBlank() } ?: return null
        // "external" is an aggregate alias, not the actual storage volume identity.
        if (id < 0L || volumeName.equals("external", ignoreCase = true)) return null
        return MediaItemKey(volumeName, id)
    }

    private fun nullableLong(cursor: android.database.Cursor, column: String): Long? {
        val index = cursor.getColumnIndex(column)
        return if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
    }

    private fun mediaStoreQueryUris(uri: Uri): List<Uri> {
        if (uri.authority == "media") return listOf(uri)
        val candidates = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.getMediaUri(context, uri)
                    ?.takeIf { it.authority == "media" }
                    ?.let(candidates::add)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "MediaStore could not map URI to a media item: $uri", e)
            }
        }
        return candidates.distinct()
    }

    private fun areCanonicalAliases(first: Uri, second: Uri): Boolean {
        if (first == second) return true
        return try {
            val canonicalFirst = contentResolver.canonicalize(first) ?: return false
            val canonicalSecond = contentResolver.canonicalize(second) ?: return false
            canonicalFirst == canonicalSecond
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Could not canonicalize MediaStore aliases", e)
            false
        }
    }

    private fun findExistingCopyCandidates(relativePath: String, fileName: String): List<ExistingCopyCandidate> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.IS_PENDING
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
        val candidates = mutableListOf<ExistingCopyCandidate>()
        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(relativePath, fileName),
                null
            )?.use { cursor ->
                val relativePathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val pendingIndex = cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                while (cursor.moveToNext()) {
                    val key = mediaStoreItemKey(cursor) ?: continue
                    if (relativePathIndex < 0 || displayNameIndex < 0 || pendingIndex < 0 ||
                        cursor.isNull(relativePathIndex) || cursor.isNull(displayNameIndex) || cursor.isNull(pendingIndex)
                    ) continue
                    if (normalizeRelativePath(cursor.getString(relativePathIndex)) != relativePath ||
                        cursor.getString(displayNameIndex) != fileName || cursor.getInt(pendingIndex) != 0
                    ) continue
                    val itemUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.getContentUri(key.volumeName),
                        key.id.toString()
                    )
                    candidates += ExistingCopyCandidate(itemUri, key)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect existing copies in $relativePath", e)
        }
        return candidates
    }

    private fun normalizeRelativePath(path: String): String = path.trim('/') + "/"

    private suspend fun copySourceToOwnedTarget(
        sourceUri: Uri,
        targetUri: Uri,
        expectedSize: Long?
    ): ContentDigest {
        val input = contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Source input stream was unavailable")
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var byteCount = 0L
        var copyDigest: ContentDigest? = null
        input.use { source ->
            val output = contentResolver.openOutputStream(targetUri)
                ?: throw IOException("Destination output stream was unavailable")
            output.use { destination ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) throw IOException("Source stream returned no data before EOF")
                    destination.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    byteCount += count
                }
                if (byteCount == 0L) throw IOException("Source image was empty")
                if (expectedSize != null && byteCount != expectedSize) {
                    throw IOException("Source size mismatch: expected=${expectedSize} actual=${byteCount}")
                }
                copyDigest = ContentDigest(byteCount, digest.digest())
                destination.flush()
            }
        }
        return copyDigest ?: throw IOException("Image copy did not produce content")
    }

    private suspend fun readContentDigest(uri: Uri, expectedSize: Long?): ContentDigest? {
        val input = contentResolver.openInputStream(uri) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var byteCount = 0L
        input.use { stream ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) throw IOException("Content stream returned no data before EOF: $uri")
                digest.update(buffer, 0, count)
                byteCount += count
            }
        }
        if (byteCount == 0L) return null
        if (expectedSize != null && byteCount != expectedSize) {
            throw IOException("Content size mismatch for $uri: expected=$expectedSize actual=$byteCount")
        }
        return ContentDigest(byteCount, digest.digest())
    }

    private fun sameDigest(first: ContentDigest, second: ContentDigest): Boolean =
        first.byteCount == second.byteCount && first.sha256.contentEquals(second.sha256)

    private fun ByteArray.toSha256Hex(): String = joinToString("") { "%02x".format(it) }

    private suspend fun cleanupOwnedCopyTarget(ownedTarget: OwnedCopyTargetHolder) {
        val uri = ownedTarget.take() ?: return
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val deleted = contentResolver.delete(uri, null, null)
                if (deleted == 1) {
                    Log.d(TAG, "Rolled back owned copy target: $uri")
                } else {
                    Log.w(TAG, "Rollback deleted $deleted rows; the copy target may remain: $uri")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rollback failed; the copy target may remain: $uri", e)
            }
        }
    }

    /**
     * 根据文件名获取 MIME 类型
     */
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "jpg").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }
    }

    /**
     * 从 URI 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    /**
     * 扫描单个文件到媒体库
     */
    private fun scanMediaFile(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(file.name))
                put(MediaStore.Images.Media.SIZE, file.length())
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning file: ${file.absolutePath}", e)
        }
    }

    /**
     * 扫描文件夹到媒体库
     */
    private fun scanMediaFolder(folder: File) {
        folder.listFiles()?.forEach { file ->
            if (file.isFile && isImageFile(file)) {
                scanMediaFile(file)
            }
        }
    }

    /**
     * 判断是否为图片文件
     */
    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")
    }

    /**
     * 检查相册是否已同步到系统
     */
    suspend fun isAlbumSynced(albumName: String): Boolean = withContext(Dispatchers.IO) {
        val albumFolder = getAlbumFolder(albumName)
        albumFolder.exists() && albumFolder.isDirectory
    }

    /**
     * 获取系统相册中的图片数量
     */
    suspend fun getSystemAlbumImageCount(albumName: String): Int = withContext(Dispatchers.IO) {
        val albumFolder = getAlbumFolder(albumName)
        if (!albumFolder.exists()) return@withContext 0

        albumFolder.listFiles()?.count { file ->
            file.isFile && isImageFile(file)
        } ?: 0
    }

    /**
     * 同步结果详情
     */
    data class SyncResultDetail(
        val newlySynced: Int,      // 新同步的图片数
        val alreadyExists: Int,    // 已存在跳过的图片数
        val failed: Int,           // 失败的图片数
        val uriMapping: Map<String, Uri> = emptyMap()  // 原URI字符串 -> 新URI 映射
    ) {
        val total get() = newlySynced + alreadyExists + failed
        val success get() = newlySynced + alreadyExists
    }

    /**
     * 同步相册到系统
     *
     * @param albumName 相册名称
     * @param imageUris 图片 URI 列表
     * @param syncMode 同步模式：COPY 或 MOVE
     * @param onProgress 进度回调 (current, total)
     * @return 同步结果详情（包含 URI 映射，用于更新 AlbumMapping）
     */
    suspend fun syncAlbumToSystem(
        albumName: String,
        imageUris: List<Uri>,
        syncMode: SyncMode = SyncMode.MOVE,
        onProgress: ((Int, Int) -> Unit)? = null
    ): SyncResultDetail = withContext(Dispatchers.IO) {
        var newlySynced = 0
        var alreadyExists = 0
        var failed = 0
        val uriMapping = mutableMapOf<String, Uri>()  // 收集原URI -> 新URI 映射

        // 确保相册存在
        createSystemAlbum(albumName)
        
        // #region agent log
        Log.d("DEBUG_SYNC", "[B1,B2,B3] syncAlbumToSystem:entry | albumName=$albumName | imageCount=${imageUris.size} | syncMode=${syncMode.name} | firstUri=${imageUris.firstOrNull()}")
        // #endregion

        imageUris.forEachIndexed { index, uri ->
            onProgress?.invoke(index + 1, imageUris.size)
            
            val fileName = getFileNameFromUri(uri)
            
            // #region agent log
            Log.d("DEBUG_SYNC", "[B1,B2] Processing image | index=$index | fileName=$fileName | albumName=$albumName | uri=$uri")
            // #endregion

            if (syncMode == SyncMode.COPY) {
                when (val result = copyImageToSystemAlbum(uri, albumName)) {
                    is CopyOutcome.Created -> {
                        newlySynced++
                        uriMapping[uri.toString()] = result.uri
                        Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=synced | resultUri=${result.uri}")
                    }
                    is CopyOutcome.Reused -> {
                        alreadyExists++
                        uriMapping[uri.toString()] = result.uri
                        Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=verified-existing | resultUri=${result.uri}")
                    }
                    CopyOutcome.Failed -> {
                        failed++
                        Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=failed")
                    }
                }
            } else {
                // Keep the existing MOVE behavior; COPY must go through the transaction above.
                val existingUri = fileName?.let { findExistingImageInAlbum(albumName, it) }
                if (existingUri != null) {
                    alreadyExists++
                    uriMapping[uri.toString()] = existingUri
                    Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=skipped | existingUri=$existingUri")
                } else {
                    val result = moveImageToSystemAlbum(uri, albumName)
                    if (result != null) {
                        newlySynced++
                        uriMapping[uri.toString()] = result
                        Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=synced | resultUri=$result")
                    } else {
                        failed++
                        Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=failed")
                    }
                }
            }
        }

        val modeText = if (syncMode == SyncMode.MOVE) "moved" else "copied"
        Log.d(TAG, "Synced album '$albumName': newlySynced=$newlySynced, alreadyExists=$alreadyExists, failed=$failed, mappings=${uriMapping.size} ($modeText)")
        
        // 更新最后一张图片（封面）的 DATE_MODIFIED，确保它在系统相册中显示为封面
        // 系统相册按 DATE_MODIFIED DESC 选择封面
        if (syncMode == SyncMode.MOVE && imageUris.isNotEmpty()) {
            val lastFileName = getFileNameFromUri(imageUris.last())
            if (lastFileName != null) {
                updateImageDateModified(albumName, lastFileName)
            }
        }
        
        SyncResultDetail(newlySynced, alreadyExists, failed, uriMapping)
    }
    
    /**
     * 更新图片的 DATE_MODIFIED 为当前时间
     * 用于确保封面图片在系统相册中显示正确
     */
    private fun updateImageDateModified(albumName: String, fileName: String) {
        try {
            val existingUri = findExistingImageInAlbum(albumName, fileName)
            if (existingUri != null) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                }
                val updated = contentResolver.update(existingUri, updateValues, null, null)
                if (updated > 0) {
                    Log.d(TAG, "Updated DATE_MODIFIED for cover image: $fileName")
                }
            }
        } catch (e: Exception) {
            // 更新失败不影响同步结果，只记录日志
            Log.w(TAG, "Failed to update DATE_MODIFIED for cover image: $fileName", e)
        }
    }

    /**
     * 处理单张图片同步
     *
     * @param imageUri 图片 URI
     * @param albumName 相册名称
     * @param syncMode 同步模式
     * @return 新图片 URI，失败返回 null
     */
    suspend fun syncImage(
        imageUri: Uri,
        albumName: String,
        syncMode: SyncMode
    ): Uri? = when (syncMode) {
        SyncMode.COPY -> addImageToSystemAlbum(imageUri, albumName)
        SyncMode.MOVE -> moveImageToSystemAlbum(imageUri, albumName)
    }

    // ========== 直接操作系统相册（图库集成） ==========

    /**
     * 移动图片到指定的系统相册（bucket）
     * 
     * 这是图库与系统相册直接集成的核心方法。
     * 图片的 BUCKET_DISPLAY_NAME 由其所在文件夹决定，移动图片就是改变其所属相册。
     * 
     * @param imageUri 源图片 URI
     * @param targetBucketName 目标相册名称
     * @param targetRelativePath 目标相对路径（可选，如 "DCIM/Camera"，不提供则使用 Pictures/Tabula/{name}）
     * @return 移动后的新 URI，失败返回 null
     */
    suspend fun moveImageToBucket(
        imageUri: Uri,
        targetBucketName: String,
        targetRelativePath: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, "Cannot safely move image to bucket on Android versions below Q: $imageUri")
                return@withContext null
            }

            val fileName = getFileNameFromUri(imageUri) ?: "IMG_${System.currentTimeMillis()}.jpg"
            
            // 确定目标路径
            val finalRelativePath = targetRelativePath 
                ?: getAlbumRelativePath(targetBucketName)
            
            Log.d(TAG, "Moving image to bucket: $targetBucketName, path: $finalRelativePath")
            
            val updateValues = ContentValues().apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, finalRelativePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            }

            val updated = contentResolver.update(imageUri, updateValues, null, null)
            if (updated > 0) {
                Log.d(TAG, "Moved image using MediaStore update: $fileName -> $finalRelativePath")
                return@withContext imageUri
            }
            
            Log.w(TAG, "MediaStore update failed; retaining source image: $targetBucketName/$fileName")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error moving image to bucket: $targetBucketName", e)
            null
        }
    }

    /**
     * 创建新的系统相册（文件夹）并返回其相对路径
     * 
     * 使用 MediaStore API 创建占位文件来间接创建文件夹。
     * Scoped Storage 限制下，无法直接使用 File API 在公共目录创建文件/文件夹。
     * 
     * @param albumName 相册名称
     * @return 创建的相册相对路径（如 "Pictures/Tabula/旅行"）
     */
    suspend fun createBucket(albumName: String): String? {
        val ownedTarget = OwnedCopyTargetHolder()
        try {
            val result = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val relativePath = getAlbumRelativePath(albumName)
                // API 37 cannot delete the published dot-prefixed placeholder; use a normal name.
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "tabula_placeholder_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Bucket placeholder insert returned null")
                ownedTarget.set(uri)
                currentCoroutineContext().ensureActive()
                val minimalJpeg = byteArrayOf(
                    0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                    0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                    0xFF.toByte(), 0xD9.toByte()
                )
                val output = contentResolver.openOutputStream(uri)
                    ?: throw IOException("Bucket placeholder output stream unavailable: $uri")
                output.use { it.write(minimalJpeg); it.flush() }
                currentCoroutineContext().ensureActive()
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("Bucket placeholder readback unavailable: $uri")
                val readback = input.use { it.readBytes() }
                if (!readback.contentEquals(minimalJpeg)) {
                    throw IOException("Bucket placeholder readback mismatch: $uri")
                }
                currentCoroutineContext().ensureActive()
                val updated = contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                if (updated != 1) throw IOException("Bucket placeholder publish affected $updated rows: $uri")
                currentCoroutineContext().ensureActive()
                val deleted = contentResolver.delete(uri, null, null)
                if (deleted != 1) throw IOException("Bucket placeholder delete affected $deleted rows: $uri")
                Log.d(TAG, "Created bucket: $albumName at $relativePath")
                relativePath
            }
            // Retain ownership until withContext delivers the result, including cancellation on dispatch.
            ownedTarget.clear()
            return result
        } catch (e: CancellationException) {
            cleanupOwnedCopyTarget(ownedTarget)
            throw e
        } catch (e: Exception) {
            cleanupOwnedCopyTarget(ownedTarget)
            Log.e(TAG, "Error creating bucket: $albumName", e)
            return null
        }
    }

    /**
     * 获取图片当前所属的 bucket 名称
     */
    fun getImageBucketName(imageUri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        contentResolver.query(imageUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val bucketColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                if (bucketColumn >= 0) {
                    return cursor.getString(bucketColumn)
                }
            }
        }
        return null
    }
}
