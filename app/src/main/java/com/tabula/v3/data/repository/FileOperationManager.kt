package com.tabula.v3.data.repository

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import com.tabula.v3.BuildConfig
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件操作管理器
 *
 * 专为 Android 14+ (API 34+) 设计的文件操作
 *
 * 主要功能：
 * - 删除图片（移到系统回收站）
 * - 彻底删除图片
 * - 处理 Scoped Storage 权限
 */
class FileOperationManager(
    private val context: Context,
    private val createDeleteRequest: (ContentResolver, List<Uri>) -> IntentSender = { resolver, uris ->
        MediaStore.createDeleteRequest(resolver, uris).intentSender
    },
    private val volumeAvailable: (Uri) -> Boolean = { uri ->
        val volume = uri.pathSegments.firstOrNull()
        val state = android.os.Environment.getExternalStorageState()
        uri.authority == "media" && canConfirmMissingOnVolume(
            volume,
            state == android.os.Environment.MEDIA_MOUNTED || state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY,
            MediaStore.getExternalVolumeNames(context),
            if (volume == "external") MediaStore.getRecentExternalVolumeNames(context) else emptySet()
        )
    }
) {

    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        /** 仅 BuildConfig.DEBUG 探针使用：这些 URI（字符串形式）将被核验为 UNKNOWN。 */
        @Volatile
        var debugForceUnknownUris: Set<String> = emptySet()

        private const val TAG = "FileOperationManager"
        const val REQUEST_DELETE_PERMISSION = 1001
    }

    /**
     * 删除操作结果
     */
    sealed class DeleteResult {
        /**
         * 删除成功
         * @param deleted 成功删除的 URI 集合（支持部分成功场景）
         */
        data class Success(val deleted: Set<Uri>) : DeleteResult()
        
        /**
         * 需要用户授权
         * @param intentSender 用于发起授权请求
         * @param pendingUris 待删除的 URI 列表（授权后需要重试删除）
         */
        data class NeedsPermission(
            val intentSender: IntentSender,
            val pendingUris: List<Uri>
        ) : DeleteResult()
        
        data class Error(val message: String, val kind: DeleteFailureKind = DeleteFailureKind.UNKNOWN) : DeleteResult()
    }

    /**
     * 删除单张图片
     *
     * Android 14+ 使用 MediaStore.createDeleteRequest 批量删除
     * 会弹出系统确认对话框
     *
     * @param uri 图片 URI
     * @return 删除结果
     */
    suspend fun deleteImage(uri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        try {
            deleteImages(listOf(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: ${e.message}")
            DeleteResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 批量删除图片
     *
     * @param uris 图片 URI 列表
     * @return 删除结果（Success 包含实际成功删除的 URI 集合）
     */
    suspend fun deleteImages(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext DeleteResult.Success(emptySet())
        }

        try {
            // Android 11+ (API 30+) 使用 createDeleteRequest
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uniqueUris = uris.distinct()
                val alreadyDeleted = findAlreadyDeleted(uniqueUris)
                val existingUris = uniqueUris.filterNot { it in alreadyDeleted }
                if (existingUris.isEmpty()) {
                    return@withContext DeleteResult.Success(alreadyDeleted)
                }
                val intentSender = createDeleteRequest(contentResolver, existingUris)
                // Keep the original set so verification also accounts for stale recycle-bin entries.
                return@withContext DeleteResult.NeedsPermission(intentSender, uniqueUris)
            } else {
                // Android 10 及以下直接删除，逐个尝试并记录成功的
                val deleted = mutableSetOf<Uri>()
                uris.forEach { uri ->
                    try {
                        val count = contentResolver.delete(uri, null, null)
                        if (count > 0) {
                            deleted.add(uri)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete $uri: ${e.message}")
                    }
                }
                return@withContext DeleteResult.Success(deleted)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecoverableSecurityException) {
            // 权限不足，需要用户确认
            Log.w(TAG, "RecoverableSecurityException: ${e.message}")
            return@withContext DeleteResult.NeedsPermission(e.userAction.actionIntent.intentSender, uris)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            return@withContext DeleteResult.Error("权限不足: ${e.message}", DeleteFailureKind.PERMISSION)
        } catch (e: IllegalArgumentException) {
            return@withContext DeleteResult.Error(e.message ?: "无效的删除请求", DeleteFailureKind.INVALID_REQUEST)
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}")
            return@withContext DeleteResult.Error(e.message ?: "删除服务不可用", DeleteFailureKind.SERVICE)
        }
    }
    
    /**
     * 验证删除结果
     * 
     * 在用户授权后调用，检查哪些 URI 实际被成功删除
     * 
     * @param uris 原本要删除的 URI 列表
     * @return 已被成功删除的 URI 集合
     */
    suspend fun verifyDeletion(uris: List<Uri>): Set<Uri> = verifyDeletionState(uris).deleted

    suspend fun verifyDeletionState(uris: List<Uri>): DeletionVerification = withContext(Dispatchers.IO) {
        // C0/T04 探针接缝（仅 debug 构建生效）：强制指定 URI 核验为 UNKNOWN，
        // 用于设备验证恢复路径的未知保留与反馈分支。
        if (BuildConfig.DEBUG && debugForceUnknownUris.isNotEmpty()) {
            val forced = uris.filter { it.toString() in debugForceUnknownUris }.toSet()
            if (forced.isNotEmpty()) {
                val rest = uris.filter { it.toString() !in debugForceUnknownUris }
                val base = if (rest.isEmpty()) DeletionVerification(emptySet(), emptySet(), emptySet())
                else verifyDeletionState(rest)
                return@withContext DeletionVerification(
                    deleted = base.deleted,
                    existing = base.existing,
                    unknown = base.unknown + forced
                )
            }
        }
        val states = uris.distinct().associateWith { queryPresence(it) }
        DeletionVerification(
            states.filterValues { it == UriPresence.MISSING }.keys,
            states.filterValues { it == UriPresence.PRESENT }.keys,
            states.filterValues { it == UriPresence.UNKNOWN }.keys
        )
    }

    private fun findAlreadyDeleted(uris: List<Uri>): Set<Uri> =
        uris.filter { queryPresence(it) == UriPresence.MISSING }.toSet()

    private fun hasFullReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
            else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    internal fun queryPresence(uri: Uri): UriPresence {
        return try {
            val couldConfirmMissing = hasFullReadPermission() && volumeAvailable(uri)
            val queryArgs = android.os.Bundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                    putBoolean(MediaStore.QUERY_ARG_INCLUDE_RECENTLY_UNMOUNTED_VOLUMES, true)
                }
            }
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID), queryArgs, null)?.use {
                when {
                    it.moveToFirst() -> UriPresence.PRESENT
                    couldConfirmMissing && hasFullReadPermission() && volumeAvailable(uri) -> UriPresence.MISSING
                    else -> UriPresence.UNKNOWN
                }
            } ?: UriPresence.UNKNOWN
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify deletion of $uri", e)
            UriPresence.UNKNOWN
        }
    }

    /**
     * 移动图片到回收站（Android 12+ 支持）
     *
     * @param uri 图片 URI
     * @return 是否成功
     */
    suspend fun moveToTrash(uri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createTrashRequest(
                    contentResolver,
                    listOf(uri),
                    true  // 移到回收站
                )
                return@withContext DeleteResult.NeedsPermission(pendingIntent.intentSender, listOf(uri))
            } else {
                // 旧版本直接删除
                return@withContext deleteImage(uri)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Move to trash failed: ${e.message}")
            return@withContext DeleteResult.Error(e.message ?: "移动失败")
        }
    }

    /**
     * 从回收站恢复图片（Android 12+ 支持）
     *
     * @param uri 图片 URI
     * @return 是否成功
     */
    suspend fun restoreFromTrash(uri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createTrashRequest(
                    contentResolver,
                    listOf(uri),
                    false  // 从回收站恢复
                )
                return@withContext DeleteResult.NeedsPermission(pendingIntent.intentSender, listOf(uri))
            } else {
                // 旧版本不支持
                return@withContext DeleteResult.Error("此 Android 版本不支持恢复功能")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Restore from trash failed: ${e.message}")
            return@withContext DeleteResult.Error(e.message ?: "恢复失败")
        }
    }

    /**
     * 检查 URI 是否仍然有效
     *
     * @param uri 图片 URI
     * @return 是否有效
     */
    suspend fun isUriValid(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}


enum class DeleteFailureKind { PERMISSION, INVALID_REQUEST, SERVICE, UNKNOWN }
enum class UriPresence { PRESENT, MISSING, UNKNOWN }
data class DeletionVerification(val deleted: Set<Uri>, val existing: Set<Uri>, val unknown: Set<Uri>)

internal fun FileOperationManager.DeleteResult.Error.asBatchFailure() =
    BatchDeleteResult.Failed(message, canSplit = kind == DeleteFailureKind.INVALID_REQUEST)

internal fun canConfirmMissingOnVolume(
    volume: String?, primaryMounted: Boolean, mounted: Set<String>, recentVolumes: Set<String>
): Boolean = when (volume) {
    // The recent API includes currently mounted volumes too. Only the difference is unmounted.
    // A merged URI remains ambiguous if an actually unmounted recent volume is present.
    "external" -> primaryMounted && "external_primary" in mounted && (recentVolumes - mounted).isEmpty()
    "external_primary" -> primaryMounted && "external_primary" in mounted
    null, "internal" -> false
    else -> volume in mounted
}
