package com.tabula.v3.data.repository

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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
class FileOperationManager(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "FileOperationManager"
        const val REQUEST_DELETE_PERMISSION = 1001
    }

    /**
     * 删除操作结果
     */
    sealed class DeleteResult {
        data object Success : DeleteResult()
        data class NeedsPermission(val intentSender: IntentSender) : DeleteResult()
        data class Error(val message: String) : DeleteResult()
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: ${e.message}")
            DeleteResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 批量删除图片
     *
     * @param uris 图片 URI 列表
     * @return 删除结果
     */
    suspend fun deleteImages(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext DeleteResult.Success
        }

        try {
            // Android 11+ (API 30+) 使用 createDeleteRequest
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                return@withContext DeleteResult.NeedsPermission(pendingIntent.intentSender)
            } else {
                // Android 10 及以下直接删除
                uris.forEach { uri ->
                    contentResolver.delete(uri, null, null)
                }
                return@withContext DeleteResult.Success
            }
        } catch (e: RecoverableSecurityException) {
            // 权限不足，需要用户确认
            Log.w(TAG, "RecoverableSecurityException: ${e.message}")
            return@withContext DeleteResult.NeedsPermission(e.userAction.actionIntent.intentSender)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            return@withContext DeleteResult.Error("权限不足: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}")
            return@withContext DeleteResult.Error(e.message ?: "删除失败")
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
                return@withContext DeleteResult.NeedsPermission(pendingIntent.intentSender)
            } else {
                // 旧版本直接删除
                return@withContext deleteImage(uri)
            }
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
                return@withContext DeleteResult.NeedsPermission(pendingIntent.intentSender)
            } else {
                // 旧版本不支持
                return@withContext DeleteResult.Error("此 Android 版本不支持恢复功能")
            }
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
