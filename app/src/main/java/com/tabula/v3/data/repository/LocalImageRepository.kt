package com.tabula.v3.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地图片仓库
 *
 * 从 MediaStore 获取设备图片，返回稳定的领域模型列表
 *
 * 性能优化策略：
 * 1. 使用 Bundle 查询以支持分页
 * 2. 只查询必要的列
 * 3. 在 IO 调度器执行
 */
class LocalImageRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * MediaStore 查询投影 - 仅查询必要列
     */
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    /**
     * 获取所有图片
     *
     * @param sortOrder 排序方式，默认按修改时间倒序
     * @return 图片列表
     */
    suspend fun getAllImages(
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        queryImages(sortOrder = sortOrder)
    }

    /**
     * 获取指定相册的图片
     *
     * @param bucketName 相册名称
     * @param sortOrder 排序方式
     * @return 图片列表
     */
    suspend fun getImagesByBucket(
        bucketName: String,
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        queryImages(
            selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?",
            selectionArgs = arrayOf(bucketName),
            sortOrder = sortOrder
        )
    }

    /**
     * 分页获取图片
     *
     * @param limit 每页数量
     * @param offset 偏移量
     * @param sortOrder 排序方式
     * @return 图片列表
     */
    suspend fun getImagesPaged(
        limit: Int,
        offset: Int,
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        queryImagesWithBundle(limit, offset, sortOrder)
    }

    /**
     * 获取所有相册名称
     *
     * @return 相册名称列表（去重）
     */
    suspend fun getAllBuckets(): List<String> = withContext(Dispatchers.IO) {
        val buckets = mutableSetOf<String>()

        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val bucketColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )

            while (cursor.moveToNext()) {
                cursor.getString(bucketColumn)?.let { buckets.add(it) }
            }
        }

        buckets.toList().sorted()
    }

    /**
     * 基础查询方法
     */
    private fun queryImages(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): List<ImageFile> {
        val images = mutableListOf<ImageFile>()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder.toSqlString()
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(
                    ImageFile(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameColumn) ?: "unknown",
                        dateModified = cursor.getLong(dateColumn) * 1000, // 转换为毫秒
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketDisplayName = cursor.getString(bucketColumn)
                    )
                )
            }
        }

        return images
    }

    /**
     * 使用 Bundle 进行分页查询（Android 10+）
     */
    private fun queryImagesWithBundle(
        limit: Int,
        offset: Int,
        sortOrder: SortOrder
    ): List<ImageFile> {
        val images = mutableListOf<ImageFile>()

        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(sortOrder.column)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                if (sortOrder.ascending) {
                    ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                } else {
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                }
            )
        }

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            queryArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(
                    ImageFile(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameColumn) ?: "unknown",
                        dateModified = cursor.getLong(dateColumn) * 1000,
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketDisplayName = cursor.getString(bucketColumn)
                    )
                )
            }
        }

        return images
    }

    /**
     * 排序方式枚举
     */
    enum class SortOrder(val column: String, val ascending: Boolean) {
        DATE_MODIFIED_DESC(MediaStore.Images.Media.DATE_MODIFIED, false),
        DATE_MODIFIED_ASC(MediaStore.Images.Media.DATE_MODIFIED, true),
        NAME_ASC(MediaStore.Images.Media.DISPLAY_NAME, true),
        NAME_DESC(MediaStore.Images.Media.DISPLAY_NAME, false),
        SIZE_DESC(MediaStore.Images.Media.SIZE, false),
        SIZE_ASC(MediaStore.Images.Media.SIZE, true);

        fun toSqlString(): String {
            return "$column ${if (ascending) "ASC" else "DESC"}"
        }
    }
}
