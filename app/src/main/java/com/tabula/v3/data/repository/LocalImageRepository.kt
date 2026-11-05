package com.tabula.v3.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.tabula.v3.data.model.Album
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
     * MediaStore 查询投影 - 仅查询必要列；身份列（卷/路径）按 T03 契约统一携带。
     */
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.ORIENTATION,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.MediaColumns.VOLUME_NAME
    )

    /**
     * 获取所有图片
     *
     * @param sortOrder 排序方式，默认按修改时间倒序
     * @param excludeTabulaAlbums 是否排除 Tabula 图集内的照片（默认 false，保持兼容）
     * @return 图片列表
     */
    suspend fun getAllImages(
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC,
        excludeTabulaAlbums: Boolean = false
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        val images = queryImages(sortOrder = sortOrder)
        
        if (excludeTabulaAlbums) {
            // 排除 Pictures/Tabula/ 路径下的图片
            // 这些是用户已经归类到 Tabula 图集的照片，不应再次出现在待整理列表中
            images.filter { image ->
                // 获取图片的相对路径前缀
                // bucketDisplayName 是最后一级目录名，无法直接判断完整路径
                // 需要通过查询 RELATIVE_PATH 来判断
                // 但 queryImages 没有查询 RELATIVE_PATH，这里用 bucketDisplayName 近似判断
                // Tabula 图集的 bucket 名称来自用户创建，可能与系统相册重名
                // 更安全的方式是：只排除 "Tabula" 这个特定的 bucket
                // 因为 Tabula 图集都在 Pictures/Tabula/{name}/ 下，父目录也叫 Tabula
                // 但实际上每个图集的 bucketDisplayName 是图集名称，不是 "Tabula"
                // 
                // 正确的逻辑：查询时通过 RELATIVE_PATH 过滤
                true  // 暂时返回 true，下面用单独的方法实现
            }
        } else {
            images
        }
    }
    
    /**
     * 获取所有图片（排除 Tabula 图集内的照片）
     * 
     * 这是推荐用于主界面的方法，会排除已经归类到 Tabula 图集的照片，
     * 避免用户重复整理。
     *
     * @param sortOrder 排序方式，默认按修改时间倒序
     * @return 图片列表（不包含 Pictures/Tabula/ 路径下的照片）
     */
    suspend fun getAllImagesExcludingTabulaAlbums(
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        queryImagesExcludingTabulaAlbums(sortOrder = sortOrder)
    }
    
    /**
     * 查询图片（排除 Tabula 图集）
     */
    private fun queryImagesExcludingTabulaAlbums(
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): List<ImageFile> {
        val images = mutableListOf<ImageFile>()
        
        // T03：与 queryImages 一致，deck 待整理数据源同样携带身份字段（卷/路径），
        // 同数值 ID 的跨卷行依赖 volumeName 区分（C1-T03-DECKFEED-VOLUME）。
        val projectionWithPath = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME
        )

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projectionWithPath,
            null,
            null,
            sortOrder.toSqlString()
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val volumeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)

            while (cursor.moveToNext()) {
                // 检查是否在 Tabula 图集路径下
                val relativePath = if (relativePathColumn >= 0) {
                    cursor.getString(relativePathColumn) ?: ""
                } else {
                    ""
                }
                
                // 排除 Pictures/Tabula/ 路径下的图片
                // 这些是用户已经归类到 Tabula 图集的照片
                if (relativePath.startsWith("Pictures/Tabula/", ignoreCase = true)) {
                    continue
                }
                
                val id = cursor.getLong(idColumn)
                val volumeName = if (volumeColumn >= 0) {
                    cursor.getString(volumeColumn)?.takeIf { it.isNotBlank() }
                } else null

                images.add(
                    ImageFile(
                        id = id,
                        uri = imageItemUri(volumeName, id),
                        displayName = cursor.getString(nameColumn) ?: "unknown",
                        dateModified = cursor.getLong(dateColumn) * 1000, // 转换为毫秒
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketDisplayName = cursor.getString(bucketColumn),
                        orientation = cursor.getInt(orientationColumn),
                        volumeName = volumeName,
                        relativePath = relativePath.ifEmpty { null }
                    )
                )
            }
        }

        return images
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
     * 获取指定相对路径下的图片
     * 
     * 用于图集清理功能，通过图集的 systemAlbumPath 获取图片。
     *
     * @param relativePath 相对路径（如 "Pictures/Tabula/风景"）
     * @param sortOrder 排序方式
     * @return 图片列表
     */
    suspend fun getImagesByRelativePath(
        relativePath: String,
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        // 确保路径以 / 结尾（MediaStore 的 RELATIVE_PATH 格式）
        val normalizedPath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        
        queryImages(
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            selectionArgs = arrayOf(normalizedPath),
            sortOrder = sortOrder
        )
    }
    
    /**
     * 从已加载的图片列表中筛选指定图集的图片
     * 
     * 这是一个更高效的方法，避免重复查询 MediaStore。
     * 适用于已经加载了全部图片的场景。
     *
     * @param allImages 已加载的全部图片列表
     * @param albumPath 图集的系统路径（如 "Pictures/Tabula/风景"）
     * @return 属于该图集的图片列表
     */
    fun filterImagesByAlbumPath(
        allImages: List<ImageFile>,
        albumPath: String
    ): List<ImageFile> {
        // 从路径中提取 bucket 名称（最后一个目录名）
        val bucketName = albumPath.trimEnd('/').substringAfterLast('/')
        return allImages.filter { it.bucketDisplayName == bucketName }
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
     * 系统相册信息
     * 
     * @param name 相册名称（BUCKET_DISPLAY_NAME）
     * @param imageCount 图片数量
     * @param coverImageId 封面图片 ID（最新的一张）
     * @param relativePath 相对路径（如 "DCIM/Camera"），用于创建同名文件夹
     */
    data class SystemBucket(
        val name: String,
        val imageCount: Int,
        val coverImageId: Long? = null,
        val relativePath: String? = null,
        // T03 bucket 身份：(volumeName, relativePath) 才是真实身份；显示名可跨卷/跨目录重复。
        val volumeName: String? = null
    )

    /**
     * 获取所有系统相册及其图片数量
     *
     * @return 系统相册列表（包含名称、数量、封面、路径）
     */
    suspend fun getAllBucketsWithInfo(): List<SystemBucket> = withContext(Dispatchers.IO) {
        // bucket 身份 = (卷, 相对路径)；同名不同目录/卷保持分开（T03）。显示名随组携带。
        val bucketMap = linkedMapOf<BucketGroupKey, BucketGroupData>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME
        )

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            val pathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val volumeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)

            while (cursor.moveToNext()) {
                val bucketName = cursor.getString(bucketColumn) ?: continue
                val imageId = cursor.getLong(idColumn)
                val relativePath = if (pathColumn >= 0) cursor.getString(pathColumn) else null
                val volumeName = if (volumeColumn >= 0) {
                    cursor.getString(volumeColumn)?.takeIf { it.isNotBlank() }
                } else null

                val key = BucketGroupKey(volumeName ?: "", relativePath ?: "")
                val group = bucketMap[key]
                if (group != null) {
                    group.ids.add(imageId)
                } else {
                    bucketMap[key] = BucketGroupData(mutableListOf(imageId), relativePath, bucketName)
                }
            }
        }

        bucketMap.map { (key, group) ->
            SystemBucket(
                name = group.displayName,
                imageCount = group.ids.size,
                coverImageId = group.ids.firstOrNull(), // 最新的一张作为封面
                relativePath = group.relativePath?.trimEnd('/'), // 移除末尾斜杠
                volumeName = key.volumeName.ifEmpty { null }
            )
        }.sortedByDescending { it.imageCount }
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

        // T03/T05：所有仓库查询路径统一携带身份字段（卷/路径）并输出毫秒时间戳。
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME
        )

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
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val volumeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
                val volumeName = if (volumeColumn >= 0) {
                    cursor.getString(volumeColumn)?.takeIf { it.isNotBlank() }
                } else null

                images.add(
                    ImageFile(
                        id = id,
                        uri = imageItemUri(volumeName, id),
                        displayName = cursor.getString(nameColumn) ?: "unknown",
                        dateModified = cursor.getLong(dateColumn) * 1000, // 转换为毫秒
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketDisplayName = cursor.getString(bucketColumn),
                        orientation = cursor.getInt(orientationColumn),
                        volumeName = volumeName,
                        relativePath = relativePath
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
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val volumeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val volumeName = if (volumeColumn >= 0) {
                    cursor.getString(volumeColumn)?.takeIf { it.isNotBlank() }
                } else null
                val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null

                images.add(
                    ImageFile(
                        id = id,
                        uri = imageItemUri(volumeName, id),
                        displayName = cursor.getString(nameColumn) ?: "unknown",
                        dateModified = cursor.getLong(dateColumn) * 1000,
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketDisplayName = cursor.getString(bucketColumn),
                        orientation = cursor.getInt(orientationColumn),
                        volumeName = volumeName,
                        relativePath = relativePath
                    )
                )
            }
        }

        return images
    }

    /**
     * 一次查询加载所有数据的返回结果
     */
    data class LoadResult(
        val allImages: List<ImageFile>,
        val imagesExcludingTabula: List<ImageFile>,
        val systemBuckets: List<SystemBucket>
    )

    /**
     * 一次查询加载所有数据
     *
     * 通过单次 MediaStore 查询获取所有图片数据，然后在内存中派生出：
     * 1. 全部图片列表
     * 2. 排除 Tabula 图集的图片列表（用于主界面卡片整理）
     * 3. 系统相册信息（名称、数量、封面、路径）
     *
     * 性能优化：从原来的 3 次 MediaStore 查询减少到 1 次
     */
    suspend fun loadAllData(
        sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC
    ): LoadResult = withContext(Dispatchers.IO) {
        val allImages = mutableListOf<ImageFile>()
        val imagesExcludingTabula = mutableListOf<ImageFile>()
        // bucket 身份 = (卷, 相对路径)；显示名随组携带（T03）
        val bucketMap = linkedMapOf<BucketGroupKey, BucketGroupData>()

        val projectionWithPath = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME
        )

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projectionWithPath,
            null,
            null,
            sortOrder.toSqlString()
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val volumeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketDisplayName = cursor.getString(bucketColumn)
                val relativePath = if (relativePathColumn >= 0) {
                    cursor.getString(relativePathColumn) ?: ""
                } else ""
                val volumeName = if (volumeColumn >= 0) {
                    cursor.getString(volumeColumn)?.takeIf { it.isNotBlank() }
                } else null
                val uri = imageItemUri(volumeName, id)

                val imageFile = ImageFile(
                    id = id,
                    uri = uri,
                    displayName = cursor.getString(nameColumn) ?: "unknown",
                    dateModified = cursor.getLong(dateColumn) * 1000,
                    size = cursor.getLong(sizeColumn),
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    bucketDisplayName = bucketDisplayName,
                    orientation = cursor.getInt(orientationColumn),
                    volumeName = volumeName,
                    relativePath = relativePath.ifEmpty { null }
                )

                allImages.add(imageFile)

                // 排除 Tabula 图集的照片
                if (!relativePath.startsWith("Pictures/Tabula/", ignoreCase = true)) {
                    imagesExcludingTabula.add(imageFile)
                }

                // 构建 bucket 信息（已按 date desc 排序，首个 ID 即为封面）
                if (bucketDisplayName != null) {
                    val key = BucketGroupKey(volumeName ?: "", relativePath)
                    val group = bucketMap[key]
                    if (group != null) {
                        group.ids.add(id)
                    } else {
                        bucketMap[key] = BucketGroupData(mutableListOf(id), relativePath, bucketDisplayName)
                    }
                }
            }
        }

        val systemBuckets = bucketMap.map { (key, group) ->
            SystemBucket(
                name = group.displayName,
                imageCount = group.ids.size,
                coverImageId = group.ids.firstOrNull(),
                relativePath = group.relativePath?.trimEnd('/')?.ifEmpty { null },
                volumeName = key.volumeName.ifEmpty { null }
            )
        }.sortedByDescending { it.imageCount }

        LoadResult(allImages, imagesExcludingTabula, systemBuckets)
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

/**
 * T02 快照投影：浏览/推荐一律由"原始媒体快照 − 软删除集合"派生。
 *
 * 过滤 allImages 与 imagesExcludingTabula，并按快照日期序重算各相册
 * imageCount/coverImageId；bucket 名称与 relativePath 原样保留，
 * 全部照片被软删除的 bucket 保留为 count=0、cover=null。
 * 空软删除集合时原样返回快照（行为与开销零变化）。
 */
fun projectSnapshot(
    snapshot: LocalImageRepository.LoadResult,
    softDeleted: Collection<ImageFile>
): LocalImageRepository.LoadResult {
    if (softDeleted.isEmpty()) return snapshot
    // C1-T03-SNAPSHOT-CROSSVOLUME-DELETE：软删除按 (卷, 数值 ID) 消费身份——
    // 不同卷的同数值 ID 是不同照片。兼容规则：任一侧卷未知（T03 前旧数据）
    // 退化为纯 ID 匹配；两侧卷已知则必须同卷才命中。
    val allImages = snapshot.allImages.filterNot { image -> softDeleted.any { it.matchesSameMediaIdentity(image) } }
    val imagesExcludingTabula =
        snapshot.imagesExcludingTabula.filterNot { image -> softDeleted.any { it.matchesSameMediaIdentity(image) } }
    // 按真实身份 (卷, 路径) 重算 count/cover，避免同名桶互相污染；无身份信息的旧数据退化为显示名键。
    val covers = LinkedHashMap<BucketGroupKey, Long>()
    val counts = HashMap<BucketGroupKey, Int>()
    for (image in allImages) {
        val bucket = image.bucketDisplayName ?: continue
        val key = BucketGroupKey(image.volumeName ?: "", image.relativePath?.trimEnd('/') ?: bucket)
        counts.merge(key, 1, Int::plus)
        covers.putIfAbsent(key, image.id)
    }
    val systemBuckets = snapshot.systemBuckets.map { bucket ->
        val key = BucketGroupKey(bucket.volumeName ?: "", bucket.relativePath?.trimEnd('/') ?: bucket.name)
        bucket.copy(
            imageCount = counts[key] ?: 0,
            coverImageId = covers[key]
        )
    }.sortedByDescending { it.imageCount }
    return LocalImageRepository.LoadResult(allImages, imagesExcludingTabula, systemBuckets)
}

/** T03：bucket 真实身份 = (卷, 相对路径)；显示名可跨卷/跨目录重复。 */
internal data class BucketGroupKey(val volumeName: String, val relativePath: String)

/**
 * T03：媒体行 item URI = 卷限定地址（真实身份）。聚合 external URI 对跨卷同
 * 数值 ID 行不可区分，会使回收站日志/核验/清除按 URI 串到另一卷；卷未知的
 * 旧行为保持聚合 URI（兼容）。
 */
internal fun imageItemUri(volumeName: String?, id: Long): Uri =
    if (volumeName != null) {
        ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volumeName), id)
    } else {
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

/**
 * T03：同媒体身份 = 同数值 ID 且同卷——不同卷的同数值 ID 是不同照片。
 * 任一侧卷未知（T03 前旧序列化数据）退化为纯 ID 匹配，保持旧数据行为。
 * 已统一到本匹配/身份键口径的消费者：软删除投影（projectSnapshot）、
 * 回收站去重/移除（RecycleBinManager）、回收后即时 UI 状态
 * （MainActivity.onRemove）、恢复路径内存态过滤与回插（MainActivity）、
 * 物理删除链卷限定 URI 与旧聚合记录分区（RecycleBinDeletion）、
 * 图集批量永久删除选择集（resolveImagesByIdentitySelection）与
 * 图集详情列表 key/选择/回写剪裁（AlbumViewScreen）、查看器 Pager key。
 * 显示名/哈希缓存等 Album 层消费簇仍按名或纯 ID（显式产品语义或
 * 后续簇，见进度文件 C1/T03）；封面指针/展示/缓存已于 C1/T03-COVER
 * 迁移到完整媒体身份（Album.coverVolumeName + resolveAlbumCoverImage）。
 * 已知残留：AlbumManager.getImageUrisForIds（无生产调用者，死代码）
 * 与 metadata 按名键控（改名后旧条目被同名新图集继承）登记为后续。
 */
internal fun ImageFile.matchesSameMediaIdentity(other: ImageFile): Boolean =
    id == other.id && (volumeName == null || other.volumeName == null || volumeName == other.volumeName)

/** T03：UI 列表 key/选择集用的稳定身份键（卷|ID）；卷未知行退化为纯 ID 键。 */
internal fun ImageFile.mediaIdentityKey(): String = "${volumeName ?: ""}|$id"

/**
 * C1/T03-COVER：图集封面行解析——按完整媒体身份 (coverVolumeName, coverImageId)
 * 匹配。卷已知：精确匹配该卷行；卷未知（旧口径）：库内该 ID 唯一时兼容解析，
 * 跨卷歧义不猜选（返回 null，展示回退占位，封面指针保留）。封面行缺失/
 * 暂时不可读同样返回 null（占位展示，不抛错、不清指针）。
 */
internal fun resolveAlbumCoverImage(album: Album, images: List<ImageFile>): ImageFile? {
    val coverId = album.coverImageId ?: return null
    return resolveCoverRow(album.coverVolumeName, coverId, images.filter { it.id == coverId })
}

/**
 * C1/T03-COVER：封面行解析核心——同 ID 候选行 + 封面卷归属 → 命中行或 null。
 * 卷已知：精确匹配该卷行；卷未知（旧口径）：候选唯一才解析，跨卷歧义不猜选
 * （展示回退占位，封面指针保留）。缺失/暂时不可读同样返回 null。
 */
internal fun resolveCoverRow(
    coverVolumeName: String?,
    coverImageId: Long,
    sameIdRows: List<ImageFile>
): ImageFile? = when (coverVolumeName) {
    null -> if (sameIdRows.size == 1) sameIdRows.single() else null
    else -> sameIdRows.find { it.volumeName == coverVolumeName }
}

/**
 * O5③：图集批量永久删除（AlbumViewScreen 选择集 → MainActivity 删除请求）的
 * 确认行解析。选择集携带确认行的完整媒体身份键（mediaIdentityKey），
 * 从候选列表（MainActivity 级 allImages）解析出待删除行。
 */
internal fun resolveImagesByIdentitySelection(
    images: List<ImageFile>,
    selectedIdentityKeys: Collection<String>
): List<ImageFile> =
    // O5③：按完整媒体身份键匹配——不同卷的同数值 ID 行只有被确认选中的
    // 那一行进入删除请求；键内的卷段对活查询行恒有值（minSdk 31 起
    // VOLUME_NAME 列存在），卷未知退化键仅见于跨列表同源旧数据。
    images.filter { it.mediaIdentityKey() in selectedIdentityKeys }

internal class BucketGroupData(
    val ids: MutableList<Long>,
    val relativePath: String?,
    val displayName: String
)

/**
 * T03：Album 层消费者簇仍以显示名为身份（本函数显式合并，行为与旧版一致）。
 * 同名桶计数求和，封面/路径/卷取日期序首个非空，结果按 imageCount 降序（与查询排序一致）。
 * 后续迁移 Album/元数据/使用频率簇后，本函数应退役。
 */
fun mergeBucketsByName(
    buckets: List<LocalImageRepository.SystemBucket>
): List<LocalImageRepository.SystemBucket> {
    if (buckets.size <= 1) return buckets
    return buckets.groupBy { it.name }.flatMap { (_, group) ->
        if (group.size == 1) {
            listOf(group.first())
        } else {
            listOf(
                group.first().copy(
                    imageCount = group.sumOf { it.imageCount },
                    coverImageId = group.firstNotNullOfOrNull { it.coverImageId },
                    relativePath = group.firstNotNullOfOrNull { it.relativePath },
                    volumeName = group.firstNotNullOfOrNull { it.volumeName }
                )
            )
        }
    }.sortedByDescending { it.imageCount }
}
