package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.model.AlbumActionType
import com.tabula.v3.data.model.AlbumMapping
import com.tabula.v3.data.model.PendingAlbumAction
import com.tabula.v3.data.model.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 相册管理器（系统相册集成版）
 *
 * 直接与系统相册集成，不再维护独立的图片映射关系。
 * 
 * ## 核心逻辑
 * - 图集列表 = 系统相册 (bucket) 列表
 * - 创建图集 = 在系统中创建文件夹
 * - 归类图片 = 移动图片到目标 bucket
 * - 图片所属图集 = 图片的 BUCKET_DISPLAY_NAME
 * 
 * ## 数据存储
 * - albums_metadata.json: 仅存储元数据（颜色、排序），不是图集的真相来源
 * - 图集真相来源 = 系统相册
 */
class AlbumManager(private val context: Context) {

    companion object {
        private const val TAG = "AlbumManager"
        private const val ALBUMS_METADATA_FILE = "albums_metadata.json"  // 仅元数据
        private const val SOURCE_IMAGES_FILE = "source_images.json"  // 原图追踪（用于清理）
        private const val MAX_UNDO_ACTIONS = 10

        @Volatile
        private var instance: AlbumManager? = null

        fun getInstance(context: Context): AlbumManager {
            return instance ?: synchronized(this) {
                instance ?: AlbumManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val metadataFile: File
        get() = File(context.filesDir, ALBUMS_METADATA_FILE)
    
    private val sourceImagesFile: File
        get() = File(context.filesDir, SOURCE_IMAGES_FILE)

    // 内存缓存 - 图集列表（合并系统相册数据 + 元数据）
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    // 元数据缓存（颜色、排序等）- 按图集名称索引
    private val _metadata = MutableStateFlow<Map<String, AlbumMetadata>>(emptyMap())
    
    // ========== 隐藏/屏蔽图集数量（响应式） ==========
    // 供外部订阅，用于设置页面显示
    private val _excludedAlbumCount = MutableStateFlow(0)
    val excludedAlbumCount: StateFlow<Int> = _excludedAlbumCount.asStateFlow()
    
    private val _hiddenAlbumCount = MutableStateFlow(0)
    val hiddenAlbumCount: StateFlow<Int> = _hiddenAlbumCount.asStateFlow()
    
    // 隐藏+屏蔽总数
    private val _hiddenAndExcludedCount = MutableStateFlow(0)
    val hiddenAndExcludedCount: StateFlow<Int> = _hiddenAndExcludedCount.asStateFlow()
    
    // 兼容性：保留 mappings StateFlow（但不再使用）
    @Deprecated("不再使用，图片归属由系统相册决定")
    private val _mappings = MutableStateFlow<List<AlbumMapping>>(emptyList())
    @Deprecated("不再使用，图片归属由系统相册决定")
    val mappings: StateFlow<List<AlbumMapping>> = _mappings.asStateFlow()

    // ========== 延迟执行队列（撤销功能核心） ==========
    // 
    // 设计思路：
    // - 归档操作不立即执行复制，而是加入待执行队列
    // - 用户点击撤销 → 从队列移除，无任何文件操作
    // - Snackbar 超时消失 → 执行真正的复制
    // 
    // 这样撤销就是"真正的撤销"，不会产生孤儿文件
    
    /**
     * 待执行的归档操作
     */
    data class PendingArchive(
        val id: String,
        val imageId: Long,
        val imageUri: String,
        val albumId: String,
        val albumName: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // 待执行队列（栈结构，后进先撤销）
    // 使用 synchronizedList 保证线程安全，因为 queueImageToAlbum、cancelLastPendingArchive、commitPendingArchive 可能并发调用
    private val pendingArchiveQueue = java.util.Collections.synchronizedList(mutableListOf<PendingArchive>())
    
    // 旧的撤销队列（保留兼容性，但不再使用）
    @Deprecated("使用 pendingArchiveQueue 替代")
    private val undoQueue = mutableListOf<PendingAlbumAction>()
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 图集元数据（仅存储自定义信息，不含图片列表）
     */
    data class AlbumMetadata(
        val name: String,          // 图集名称（与系统 bucket 名称对应）
        val color: Long? = null,
        val textColor: Long? = null,
        val order: Int = 0,
        val createdAt: Long = System.currentTimeMillis(),
        val isHidden: Boolean = false,  // 是否隐藏此图集
        val isExcludedFromRecommend: Boolean = false,  // 是否从推荐中排除（屏蔽图集）
        // C1/T03-COVER：用户设置的封面完整媒体身份（卷+数值 ID）。
        // coverVolumeName=null 为旧口径（卷未知，展示端唯一解析/歧义回退）。
        val coverImageId: Long? = null,
        val coverVolumeName: String? = null
    )

    // ========== 系统相册同步管理器 ==========

    private val syncManager: SystemAlbumSyncManager by lazy {
        SystemAlbumSyncManager.getInstance(context)
    }

    /** source→verified destination 证据账本；internal 以便测试注入故障存储。 */
    internal var copyEvidenceStore: CopyEvidenceStore = CopyEvidenceStore(context)

    private val imageRepository: LocalImageRepository by lazy {
        LocalImageRepository(context)
    }
    
    // ========== 智能排序：使用频率追踪 ==========
    
    private val usageTracker: AlbumUsageTracker by lazy {
        AlbumUsageTracker.getInstance(context)
    }

    // ========== 相册 CRUD ==========

    /**
     * 创建新相册（在系统中创建文件夹）
     * 
     * 图集直接在系统相册中创建，不存储到 App 私有数据。
     */
    suspend fun createAlbum(
        name: String,
        color: Long? = null,
        emoji: String? = null
    ): Album = withContext(Dispatchers.IO) {
        // 1. 在系统中创建文件夹
        val relativePath = syncManager.createBucket(name)
        if (relativePath == null) {
            // 系统文件夹创建失败，不保存元数据，避免数据不一致
            Log.e(TAG, "Failed to create system bucket for $name, aborting album creation")
            throw IllegalStateException("无法创建图集文件夹，请检查存储权限")
        }

        // 2. 保存元数据（颜色、排序）- 只有系统文件夹创建成功才保存
        val currentMetadata = loadMetadata()
        val maxOrder = currentMetadata.values.maxOfOrNull { it.order } ?: _albums.value.size
        
        val metadata = AlbumMetadata(
            name = name,
            color = color,
            order = maxOrder + 1
        )
        val updatedMetadata = currentMetadata + (name to metadata)
        saveMetadata(updatedMetadata)
        _metadata.value = updatedMetadata

        // 3. 构建 Album 对象返回
        val newAlbum = Album(
            id = name,  // 使用名称作为 ID（与系统 bucket 对应）
            name = name,
            color = color,
            emoji = emoji,
            order = maxOrder + 1,
            isSyncEnabled = true,
            systemAlbumPath = relativePath,
            imageCount = 0
        )

        // 4. 刷新图集列表
        refreshAlbumsFromSystem()

        Log.d(TAG, "Created album '$name' with path: $relativePath")
        newAlbum
    }

    /**
     * 更新相册元数据（颜色等）
     */
    suspend fun updateAlbum(album: Album) = withContext(Dispatchers.IO) {
        // 更新元数据。C1/T03-COVER 复验轮四：名称/颜色等属性编辑**不得**把
        // 显示态的桶默认封面持久化为用户封面（否则自动封面被冻结）——封面
        // 字段保留元数据中已持久化的用户设置（无则继续走桶默认）。
        // 显式设置封面走 setAlbumCover。
        val currentMetadata = loadMetadata()
        val existing = currentMetadata[album.name]
        val metadata = AlbumMetadata(
            name = album.name,
            color = album.color,
            textColor = album.textColor,
            order = album.order,
            createdAt = album.createdAt,
            isHidden = existing?.isHidden ?: false,
            isExcludedFromRecommend = existing?.isExcludedFromRecommend ?: false,
            coverImageId = existing?.coverImageId,
            coverVolumeName = existing?.coverVolumeName
        )
        val updatedMetadata = currentMetadata + (album.name to metadata)
        saveMetadata(updatedMetadata)
        _metadata.value = updatedMetadata

        // 刷新图集列表
        refreshAlbumsFromSystem()
    }

    /**
     * 显式设置图集封面（C1/T03-COVER 复验轮四）：完整媒体身份（卷+ID）随
     * 元数据持久化，其余属性保留既有值；刷新后用户封面优先于桶默认封面，
     * 重启/刷新均保持。升级前无此通道（设置即被桶默认覆盖，从不生效）。
     */
    suspend fun setAlbumCover(albumName: String, image: ImageFile) = withContext(Dispatchers.IO) {
        val currentMetadata = loadMetadata()
        val existing = currentMetadata[albumName]
        val metadata = AlbumMetadata(
            name = albumName,
            color = existing?.color,
            textColor = existing?.textColor,
            order = existing?.order ?: Int.MAX_VALUE,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            isHidden = existing?.isHidden ?: false,
            isExcludedFromRecommend = existing?.isExcludedFromRecommend ?: false,
            coverImageId = image.id,
            coverVolumeName = image.volumeName
        )
        saveMetadata(currentMetadata + (albumName to metadata))
        _metadata.value = currentMetadata + (albumName to metadata)

        // 刷新图集列表（用户封面优先于桶默认封面）
        refreshAlbumsFromSystem()
    }

    /**
     * 删除相册元数据（注意：不删除系统文件夹和图片）
     */
    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        // 只删除元数据，不删除系统文件夹
        val currentMetadata = loadMetadata()
        val updatedMetadata = currentMetadata - albumId
        saveMetadata(updatedMetadata)
        _metadata.value = updatedMetadata
        
        // 刷新图集列表
        refreshAlbumsFromSystem()
        
        Log.d(TAG, "Removed album metadata: $albumId")
    }

    /**
     * 隐藏图集（不删除系统文件夹和图片，只是不显示在列表中）
     */
    suspend fun hideAlbum(albumId: String) = withContext(Dispatchers.IO) {
        val currentMetadata = loadMetadata().toMutableMap()
        val existing = currentMetadata[albumId]
        if (existing != null) {
            currentMetadata[albumId] = existing.copy(isHidden = true)
        } else {
            // 创建新的元数据
            currentMetadata[albumId] = AlbumMetadata(name = albumId, isHidden = true)
        }
        saveMetadata(currentMetadata)
        _metadata.value = currentMetadata
        
        // 更新隐藏/屏蔽图集数量
        updateHiddenAndExcludedCounts(currentMetadata)
        
        // 刷新图集列表
        refreshAlbumsFromSystem()
        
        Log.d(TAG, "Hidden album: $albumId")
    }

    /**
     * 取消隐藏图集
     */
    suspend fun unhideAlbum(albumId: String) = withContext(Dispatchers.IO) {
        val currentMetadata = loadMetadata().toMutableMap()
        val existing = currentMetadata[albumId]
        if (existing != null) {
            currentMetadata[albumId] = existing.copy(isHidden = false)
            saveMetadata(currentMetadata)
            _metadata.value = currentMetadata
            
            // 更新隐藏/屏蔽图集数量
            updateHiddenAndExcludedCounts(currentMetadata)
            
            // 刷新图集列表
            refreshAlbumsFromSystem()
            
            Log.d(TAG, "Unhidden album: $albumId")
        }
    }

    // ========== 屏蔽图集（从推荐中排除） ==========

    /**
     * 设置图集是否从推荐中排除
     * 
     * 被屏蔽的图集中的照片不会出现在推荐流中。
     * 适用于用户已经整理好、不希望再次推荐的图集。
     * 
     * @param albumId 图集ID（bucket 名称）
     * @param excluded true 表示屏蔽，false 表示取消屏蔽
     */
    suspend fun setAlbumExcludedFromRecommend(albumId: String, excluded: Boolean) = withContext(Dispatchers.IO) {
        val currentMetadata = loadMetadata().toMutableMap()
        val existing = currentMetadata[albumId]
        if (existing != null) {
            currentMetadata[albumId] = existing.copy(isExcludedFromRecommend = excluded)
        } else {
            // 创建新的元数据
            currentMetadata[albumId] = AlbumMetadata(name = albumId, isExcludedFromRecommend = excluded)
        }
        saveMetadata(currentMetadata)
        _metadata.value = currentMetadata
        
        // 更新隐藏/屏蔽图集数量
        updateHiddenAndExcludedCounts(currentMetadata)
        
        Log.d(TAG, "Set album '$albumId' excludedFromRecommend=$excluded")
    }

    /**
     * 检查图集是否被屏蔽（从推荐中排除）
     * 
     * @param albumId 图集ID（bucket 名称）
     * @return true 如果被屏蔽
     */
    fun isAlbumExcludedFromRecommend(albumId: String): Boolean {
        return _metadata.value[albumId]?.isExcludedFromRecommend ?: false
    }

    /**
     * 获取所有被屏蔽的图集ID
     * 
     * @return 被屏蔽的图集ID集合
     */
    fun getExcludedAlbumIds(): Set<String> {
        return _metadata.value
            .filter { it.value.isExcludedFromRecommend }
            .keys
    }
    
    /**
     * 更新隐藏/屏蔽图集数量（内部方法）
     */
    private fun updateHiddenAndExcludedCounts(metadata: Map<String, AlbumMetadata>) {
        val hiddenCount = metadata.count { it.value.isHidden }
        val excludedCount = metadata.count { it.value.isExcludedFromRecommend }
        _hiddenAlbumCount.value = hiddenCount
        _excludedAlbumCount.value = excludedCount
        _hiddenAndExcludedCount.value = hiddenCount + excludedCount
    }
    
    /**
     * 获取所有隐藏的图集列表
     * 
     * @return 隐藏的图集列表
     */
    fun getHiddenAlbums(): List<Album> {
        val hiddenIds = _metadata.value
            .filter { it.value.isHidden }
            .keys
        return _albums.value.filter { it.id in hiddenIds }
    }

    /**
     * 获取所有被屏蔽的图集列表
     * 
     * @return 被屏蔽的图集列表
     */
    fun getExcludedAlbums(): List<Album> {
        val excludedIds = getExcludedAlbumIds()
        return _albums.value.filter { it.id in excludedIds }
    }

    /**
     * 删除空图集（删除系统文件夹，仅当图集内没有图片时允许）
     * 
     * @return true 删除成功，false 删除失败（图集不为空或系统限制）
     */
    suspend fun deleteEmptyAlbum(albumId: String): Boolean = withContext(Dispatchers.IO) {
        // 1. 检查图集是否为空
        val album = _albums.value.find { it.id == albumId }
        if (album == null) {
            Log.w(TAG, "Album not found: $albumId")
            return@withContext false
        }
        
        if (album.imageCount > 0) {
            Log.w(TAG, "Cannot delete non-empty album: $albumId (${album.imageCount} images)")
            return@withContext false
        }
        
        // 2. 尝试删除系统文件夹
        val deleted = album.systemAlbumPath?.let { path ->
            syncManager.deleteBucket(path)
        } ?: false
        
        if (deleted) {
            // 3. 删除元数据
            val currentMetadata = loadMetadata()
            val updatedMetadata = currentMetadata - albumId
            saveMetadata(updatedMetadata)
            _metadata.value = updatedMetadata
            
            // 4. 刷新图集列表
            refreshAlbumsFromSystem()
            
            Log.d(TAG, "Deleted empty album: $albumId")
        } else {
            Log.e(TAG, "Failed to delete album folder: $albumId")
        }
        
        return@withContext deleted
    }

    /**
     * 获取所有相册（从系统相册读取，合并元数据）
     */
    suspend fun getAllAlbums(): List<Album> = withContext(Dispatchers.IO) {
        refreshAlbumsFromSystem()
        _albums.value
    }

    /**
     * 重新排序相册
     * 
     * 当用户手动拖拽排序时调用。会设置手动排序锁定，
     * 在锁定期内保持用户设定的顺序，不受智能排序影响。
     */
    suspend fun reorderAlbums(albumIds: List<String>) {
        // 1. 更新元数据中的排序
        val currentMetadata = _metadata.value.toMutableMap()
        albumIds.forEachIndexed { index, albumName ->
            val existing = currentMetadata[albumName]
            if (existing != null) {
                currentMetadata[albumName] = existing.copy(order = index)
            } else {
                // 新图集，创建元数据
                currentMetadata[albumName] = AlbumMetadata(name = albumName, order = index)
            }
        }
        
        // 2. 保存元数据
        withContext(Dispatchers.IO) {
            saveMetadata(currentMetadata)
        }
        _metadata.value = currentMetadata
        
        // 3. 更新内存中的图集列表排序
        val sortedAlbums = _albums.value.map { album ->
            val newOrder = albumIds.indexOf(album.name)
            if (newOrder >= 0) album.copy(order = newOrder) else album
        }.sortedBy { it.order }
        _albums.value = sortedAlbums
        
        // 4. 设置手动排序锁定（尊重用户的主动排序意愿）
        usageTracker.setManualOrderLock(albumIds)
        Log.d(TAG, "Reordered ${albumIds.size} albums and set manual order lock")
    }

    // ========== 图片归类（直接操作系统相册） ==========

    /**
     * 将图片添加到相册（复制图片到目标 bucket）
     * 
     * 注意：此方法会立即执行复制，用于批量操作等场景。
     * 对于用户交互式归档，应使用 queueImageToAlbum() + commitPendingArchive() 组合，
     * 以支持撤销功能。
     * 
     * 为什么用复制而不是移动？
     * - Android 10+ 对非本应用创建的文件有写入限制
     * - 移动操作需要用户每次授权，体验差
     * - 复制操作（创建新文件）不需要额外授权
     * - 原图通过"清理原图"功能批量删除，一次授权即可
     */
    suspend fun addImageToAlbum(
        imageId: Long,
        imageUri: String,
        albumId: String  // albumId 就是 bucket 名称
    ) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(imageUri)
        
        Log.d(TAG, "Copying image $imageId to bucket: $albumId")
        
        // 复制图片到目标 bucket（不是移动）
        val newUri = syncManager.addImageToSystemAlbum(uri, albumId)
        
        if (newUri != null) {
            Log.d(TAG, "Successfully copied image to $albumId, newUri: $newUri")
            
            // 记录原图位置，用于后续清理
            addSourceImageRecord(albumId, imageUri)
            
            // 记录使用频率（用于智能排序）
            usageTracker.recordUsage(albumId)
            
            // 刷新图集列表以更新图片数量
            refreshAlbumsFromSystem()
        } else {
            Log.e(TAG, "Failed to copy image to $albumId")
        }
    }

    /**
     * 从相册移除图片（系统相册集成版：暂不支持，需要决定移动到哪里）
     */
    @Deprecated("系统相册集成后，移除图片需要指定目标位置")
    suspend fun removeImageFromAlbum(
        imageId: Long,
        albumId: String,
        recordUndo: Boolean = true
    ) = withContext(Dispatchers.IO) {
        // 系统相册集成后，移除图片 = 移动到其他位置
        // 暂时不实现，等待产品决策
        Log.w(TAG, "removeImageFromAlbum is deprecated in system album integration mode")
    }

    /**
     * 获取图片所属的相册（通过 BUCKET_DISPLAY_NAME）
     */
    suspend fun getAlbumsForImage(imageId: Long): List<Album> = withContext(Dispatchers.IO) {
        // 从系统相册查询图片的 bucket
        val images = imageRepository.getAllImages()
        val image = images.find { it.id == imageId }
        val bucketName = image?.bucketDisplayName ?: return@withContext emptyList()
        
        _albums.value.filter { it.name == bucketName }
    }

    /**
     * 获取相册内的所有图片 ID（从系统相册读取）
     */
    suspend fun getImageIdsForAlbum(albumId: String): List<Long> = withContext(Dispatchers.IO) {
        val images = imageRepository.getImagesByBucket(albumId)
        images.map { it.id }
    }

    /**
     * 获取相册详情的完整图片列表（T05：详情直接消费仓库的完整 ImageFile，
     * 不再把查询结果压平成 (id, uri) 映射导致屏幕层逐 URI N+1 补读）。
     */
    suspend fun getAlbumDetailImages(albumId: String): List<ImageFile> = withContext(Dispatchers.IO) {
        imageRepository.getImagesByBucket(albumId)
    }

    /**
     * 获取图片所属的相册名称
     */
    suspend fun getAlbumIdsForImage(imageId: Long): Set<String> = withContext(Dispatchers.IO) {
        val images = imageRepository.getAllImages()
        val image = images.find { it.id == imageId }
        val bucketName = image?.bucketDisplayName
        if (bucketName != null) setOf(bucketName) else emptySet()
    }

    /**
     * 根据图片 ID 列表获取对应的 URI 列表
     */
    suspend fun getImageUrisForIds(imageIds: List<Long>): List<Uri> = withContext(Dispatchers.IO) {
        val images = imageRepository.getAllImages()
        val imageMap = images.associateBy { it.id }
        imageIds.mapNotNull { imageMap[it]?.uri }
    }

    // ========== 移动/复制图片到其他图集（系统相册集成版） ==========

    /**
     * 按完整媒体身份（卷|ID 选择键）批量移动到另一个图集。
     *
     * C1/T03-MOVECOPY-IDENTITY：执行前按完整身份从当前媒体集合重新解析；
     * 所选身份已失效（行已不在库中）时跳过计数，绝不回退到同数值 ID 的
     * 另一卷照片。图集目标仍按名称定位（按名合并是显式产品语义）。
     */
    suspend fun moveImagesByIdentity(
        selectedIdentityKeys: Collection<String>,
        fromAlbumId: String,
        toAlbumId: String
    ): MoveImagesResult = withContext(Dispatchers.IO) {
        if (selectedIdentityKeys.isEmpty()) return@withContext MoveImagesResult(0, 0)

        val targetBucket = _albums.value.find { it.name == toAlbumId }
        val targetPath = targetBucket?.systemAlbumPath

        Log.d(TAG, "Moving ${selectedIdentityKeys.size} identity-selected images from $fromAlbumId to $toAlbumId")

        // C1/T03-MOVECOPY-IDENTITY：执行前按完整媒体身份从当前媒体集合重新
        // 解析确认行——只操作身份键精确匹配的行；选择已失效（行已不在库中）
        // 跳过计数，绝不回退到同数值 ID 的另一卷照片。
        val keySet = selectedIdentityKeys.toSet()
        val resolvedRows = imageRepository.getAllImages().filter { it.mediaIdentityKey() in keySet }

        var movedCount = 0
        // Includes failed moves, stale selections and missing rows; this count does not
        // assert their URIs remain readable.
        var retainedCount = selectedIdentityKeys.size - resolvedRows.size
        for (image in resolvedRows) {
            if (syncManager.moveImageToBucket(image.uri, toAlbumId, targetPath) != null) {
                movedCount++
            } else {
                retainedCount++
            }
        }

        if (movedCount > 0) {
            // 只在有成功移动时刷新图集列表，失败项仍保留在原图集中。
            refreshAlbumsFromSystem()
        }

        Log.d(TAG, "Moved $movedCount identity-selected images to $toAlbumId; retained $retainedCount at source")
        MoveImagesResult(movedCount = movedCount, retainedCount = retainedCount)
    }

    /**
     * 按完整媒体身份（卷|ID 选择键）批量复制到另一个图集。
     * 语义同 moveImagesByIdentity：当前媒体集合按完整身份重新解析，
     * 失效选择跳过、不回退同数值 ID 的另一卷。
     */
    suspend fun copyImagesByIdentity(
        selectedIdentityKeys: Collection<String>,
        toAlbumId: String
    ) = withContext(Dispatchers.IO) {
        if (selectedIdentityKeys.isEmpty()) return@withContext

        Log.d(TAG, "Copying ${selectedIdentityKeys.size} identity-selected images to $toAlbumId")

        // C1/T03-MOVECOPY-IDENTITY：与移动同口径——按完整身份解析确认行，
        // 每行复制各自的卷限定源；失效选择跳过，不回退同数值 ID 的另一卷。
        val keySet = selectedIdentityKeys.toSet()
        val resolvedRows = imageRepository.getAllImages().filter { it.mediaIdentityKey() in keySet }
        for (image in resolvedRows) {
            syncManager.addImageToSystemAlbum(image.uri, toAlbumId)
        }

        // 刷新图集列表
        refreshAlbumsFromSystem()

        Log.d(TAG, "Copied ${resolvedRows.size}/${selectedIdentityKeys.size} identity-selected images to $toAlbumId")
    }

    /**
     * 批量将图片移动到另一个图集
     */
    suspend fun moveImagesToAlbum(
        imageIds: List<Long>,
        fromAlbumId: String,
        toAlbumId: String
    ): MoveImagesResult = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) return@withContext MoveImagesResult(0, 0)
        
        val targetBucket = _albums.value.find { it.name == toAlbumId }
        val targetPath = targetBucket?.systemAlbumPath
        
        Log.d(TAG, "Moving ${imageIds.size} images from $fromAlbumId to $toAlbumId")
        
        val images = imageRepository.getAllImages()
        var movedCount = 0
        // Includes failed moves and missing IDs; this count does not assert their URIs remain readable.
        var retainedCount = 0
        for (imageId in imageIds) {
            val image = images.find { it.id == imageId }
            if (image == null) {
                retainedCount++
                continue
            }

            if (syncManager.moveImageToBucket(image.uri, toAlbumId, targetPath) != null) {
                movedCount++
            } else {
                retainedCount++
            }
        }
        
        if (movedCount > 0) {
            // 只在有成功移动时刷新图集列表，失败项仍保留在原图集中。
            refreshAlbumsFromSystem()
        }
        
        Log.d(TAG, "Moved $movedCount images to $toAlbumId; retained $retainedCount at source")
        MoveImagesResult(movedCount = movedCount, retainedCount = retainedCount)
    }

    /**
     * 批量将图片复制到另一个图集
     */
    suspend fun copyImagesToAlbum(
        imageIds: List<Long>,
        toAlbumId: String
    ) = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) return@withContext
        
        Log.d(TAG, "Copying ${imageIds.size} images to $toAlbumId")
        
        val images = imageRepository.getAllImages()
        for (imageId in imageIds) {
            val image = images.find { it.id == imageId } ?: continue
            syncManager.addImageToSystemAlbum(image.uri, toAlbumId)
        }
        
        // 刷新图集列表
        refreshAlbumsFromSystem()
        
        Log.d(TAG, "Copied ${imageIds.size} images to $toAlbumId")
    }

    // ========== 核心方法：从系统相册刷新图集列表 ==========

    /**
     * 从系统相册刷新图集列表
     * 
     * 这是核心方法：图集列表 = 系统相册 bucket 列表 + 元数据中的空图集
     * 
     * 重要：MediaStore 只返回包含图片的 bucket，所以空图集需要从元数据中恢复。
     */
    suspend fun refreshAlbumsFromSystem(
        preloadedBuckets: List<LocalImageRepository.SystemBucket>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. 从系统相册获取所有 bucket（优先使用预加载数据，避免重复查询）
            // T03：bucket 层身份为 (卷, 路径)；Album 消费者簇未迁移前在此显式按显示名合并。
            val systemBuckets = mergeBucketsByName(preloadedBuckets ?: imageRepository.getAllBucketsWithInfo())
            val systemBucketNames = systemBuckets.map { it.name }.toSet()
            
            // 2. 加载本地元数据
            val metadata = loadMetadata()
            
            // 3. 从系统 bucket 创建 Album 列表
            // C1/T03-COVER：封面身份来源优先级 = 用户设置（metadata 持久化，
            // 卷+ID）> 桶默认封面（该卷最新照片，卷随桶携带）。旧口径（metadata
            // 无封面字段）行为与升级前一致。
            val albumsFromSystem = systemBuckets.map { bucket ->
                val meta = metadata[bucket.name]
                val userCover = meta?.coverImageId
                Album(
                    id = bucket.name,  // 使用 bucket 名称作为 ID
                    name = bucket.name,
                    coverImageId = userCover ?: bucket.coverImageId,
                    coverVolumeName = if (userCover != null) {
                        meta.coverVolumeName
                    } else {
                        bucket.volumeName
                    },
                    color = meta?.color,
                    textColor = meta?.textColor,
                    order = meta?.order ?: Int.MAX_VALUE,  // 无元数据的排在最后
                    createdAt = meta?.createdAt ?: System.currentTimeMillis(),
                    imageCount = bucket.imageCount,
                    systemAlbumPath = bucket.relativePath,
                    isSyncEnabled = true,  // 系统相册集成后，所有都是"已同步"状态
                    syncMode = SyncMode.MOVE,
                    isHidden = meta?.isHidden ?: false
                )
            }
            
            // 4. 从元数据中恢复空图集（在系统 bucket 中不存在的图集）
            // 这解决了新建空图集不显示的问题
            val emptyAlbumsFromMetadata = metadata.values
                .filter { meta -> meta.name !in systemBucketNames }
                .map { meta ->
                    // 构建相对路径（与 createBucket 保持一致）
                    val safeName = meta.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val relativePath = "${android.os.Environment.DIRECTORY_PICTURES}/Tabula/$safeName"
                    
                    Album(
                        id = meta.name,
                        name = meta.name,
                        coverImageId = null,  // 空图集没有封面
                        color = meta.color,
                        textColor = meta.textColor,
                        order = meta.order,
                        createdAt = meta.createdAt,
                        imageCount = 0,  // 空图集
                        systemAlbumPath = relativePath,
                        isSyncEnabled = true,
                        syncMode = SyncMode.MOVE,
                        isHidden = meta.isHidden
                    )
                }
            
            // 5. 合并两个来源的图集列表
            val allAlbums = albumsFromSystem + emptyAlbumsFromMetadata
            
            // 6. 智能排序：优先使用频率得分，兼顾手动排序锁定
            val usageScores = usageTracker.getAlbumScores()
            val hasManualLock = usageTracker.hasAnyManualOrderLock()
            
            val albums = if (hasManualLock) {
                // 有手动排序锁定：优先按锁定状态排序，然后按 order
                allAlbums.sortedWith(compareBy(
                    { album -> 
                        // 锁定中的图集排在前面（锁定值越大越靠前）
                        val score = usageScores[album.id]
                        if (score == null || score < Double.MAX_VALUE / 2) {
                            // 未锁定或普通得分，使用负数让其排在后面
                            -(score ?: 0.0)
                        } else {
                            // 锁定中，使用极大负数让其排在最前面（值越大越靠前）
                            -score
                        }
                    },
                    { it.order },
                    { -it.imageCount }
                ))
            } else if (usageScores.isNotEmpty()) {
                // 无手动锁定，有使用记录：按使用频率得分排序
                allAlbums.sortedWith(compareByDescending<Album> { album ->
                    // 使用得分，未使用过的图集给予基础分
                    usageScores[album.id] ?: AlbumUsageTracker.NEW_ALBUM_BASE_SCORE
                }.thenBy { it.order }.thenByDescending { it.imageCount })
            } else {
                // 无使用记录：按传统方式排序（order + 图片数量）
                allAlbums.sortedWith(compareBy({ it.order }, { -it.imageCount }))
            }
            
            _albums.value = albums
            Log.d(TAG, "Refreshed ${albums.size} albums from system (${albumsFromSystem.size} with images, ${emptyAlbumsFromMetadata.size} empty), hasManualLock=$hasManualLock, usageScores=${usageScores.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing albums from system", e)
        }
    }

    /**
     * 初始化（从系统相册加载图集）
     */
    suspend fun initialize(
        preloadedBuckets: List<LocalImageRepository.SystemBucket>? = null
    ) = withContext(Dispatchers.IO) {
        val loadedMetadata = loadMetadata()
        _metadata.value = loadedMetadata
        _sourceImages.value = loadSourceImages()
        
        // 初始化隐藏/屏蔽图集数量
        updateHiddenAndExcludedCounts(loadedMetadata)
        
        // 初始化使用频率追踪器（用于智能排序）
        usageTracker.initialize()
        
        refreshAlbumsFromSystem(preloadedBuckets)
        Log.d(TAG, "AlbumManager initialized with system album integration, sourceImages=${_sourceImages.value.size} albums, excludedAlbums=${_excludedAlbumCount.value}")
    }

    /**
     * 验证并修复图集（系统相册集成版：直接刷新即可）
     */
    suspend fun validateAndFixCoverImages(validImageIds: Set<Long>) = withContext(Dispatchers.IO) {
        // 系统相册集成后，只需要刷新即可，封面和数量都来自系统
        refreshAlbumsFromSystem()
    }

    // ========== 原图追踪（用于清理功能） ==========
    
    // 内存缓存：albumId -> 原图 URI 列表
    private val _sourceImages = MutableStateFlow<Map<String, MutableSet<String>>>(emptyMap())
    
    /**
     * 记录原图位置（归类时调用）
     * 
     * 注意：如果图片已经在 Tabula 图集路径下（Pictures/Tabula/），则不记录。
     * 这是为了防止用户将已归类的照片再次归类到同一/其他图集时，
     * 导致清理功能误删图集内的照片。
     */
    private fun addSourceImageRecord(albumId: String, sourceUri: String) {
        // 检查是否是 Tabula 图集内的照片
        // Tabula 图集的 URI 通常包含 Pictures/Tabula/ 路径
        // 通过查询 MediaStore 获取图片的相对路径来判断
        val uri = try {
            Uri.parse(sourceUri)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid source URI: $sourceUri", e)
            return
        }
        
        // 查询图片的相对路径
        val relativePath = try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.Images.Media.RELATIVE_PATH),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val pathIndex = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
                    if (pathIndex >= 0) cursor.getString(pathIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying relative path for $sourceUri", e)
            null
        }
        
        // 如果图片已经在 Tabula 图集路径下，不记录为待清理原图
        if (relativePath?.startsWith("Pictures/Tabula/", ignoreCase = true) == true) {
            Log.d(TAG, "Skipping source image record for '$albumId': image is already in Tabula album path ($relativePath)")
            return
        }
        
        // 使用 update 保证原子性更新，避免并发写入时数据丢失
        _sourceImages.update { current ->
            val newMap = current.toMutableMap()
            val uris = newMap.getOrPut(albumId) { mutableSetOf() }
            uris.add(sourceUri)
            newMap[albumId] = uris
            // 持久化
            saveSourceImages(newMap)
            Log.d(TAG, "Recorded source image for album '$albumId': $sourceUri")
            newMap
        }
    }
    
    /**
     * 获取指定图集的待清理原图 URI 列表
     */
    fun getSourceImagesForAlbum(albumId: String): List<Uri> {
        return _sourceImages.value[albumId]?.mapNotNull { 
            try { Uri.parse(it) } catch (e: Exception) { null }
        } ?: emptyList()
    }
    
    /**
     * 获取所有待清理的原图 URI
     */
    fun getAllSourceImages(): List<Uri> {
        return _sourceImages.value.values.flatten().mapNotNull {
            try { Uri.parse(it) } catch (e: Exception) { null }
        }
    }
    
    /**
     * 清除已删除的原图记录
     */
    fun clearSourceImageRecords(albumId: String? = null, deletedUris: Set<String> = emptySet()) {
        // 使用 update 保证原子性更新，避免并发写入时数据丢失
        _sourceImages.update { current ->
            val newMap = current.toMutableMap()
            
            if (albumId != null && deletedUris.isEmpty()) {
                // 清除指定图集的所有记录
                newMap.remove(albumId)
            } else if (deletedUris.isNotEmpty()) {
                // 清除指定的 URI
                newMap.forEach { (_, uris) ->
                    uris.removeAll(deletedUris)
                }
                // 移除空的图集
                newMap.entries.removeIf { it.value.isEmpty() }
            }
            
            saveSourceImages(newMap)
            Log.d(TAG, "Cleared source image records: albumId=$albumId, deletedCount=${deletedUris.size}")
            newMap
        }
    }
    
    /**
     * 加载原图追踪数据
     */
    private fun loadSourceImages(): Map<String, MutableSet<String>> {
        try {
            if (!sourceImagesFile.exists()) return emptyMap()
            
            val jsonString = sourceImagesFile.readText()
            if (jsonString.isBlank()) return emptyMap()
            
            val jsonObject = JSONObject(jsonString)
            val result = mutableMapOf<String, MutableSet<String>>()
            
            jsonObject.keys().forEach { albumId ->
                val urisArray = jsonObject.getJSONArray(albumId)
                val uris = mutableSetOf<String>()
                for (i in 0 until urisArray.length()) {
                    uris.add(urisArray.getString(i))
                }
                result[albumId] = uris
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading source images", e)
            return emptyMap()
        }
    }
    
    /**
     * 保存原图追踪数据
     */
    private fun saveSourceImages(data: Map<String, MutableSet<String>>) {
        try {
            val jsonObject = JSONObject()
            data.forEach { (albumId, uris) ->
                val urisArray = JSONArray()
                uris.forEach { urisArray.put(it) }
                jsonObject.put(albumId, urisArray)
            }
            sourceImagesFile.writeText(jsonObject.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving source images", e)
        }
    }

    // ========== 元数据持久化（仅颜色、排序等自定义信息） ==========

    private fun loadMetadata(): Map<String, AlbumMetadata> {
        try {
            if (!metadataFile.exists()) return emptyMap()

            val jsonString = metadataFile.readText()
            if (jsonString.isBlank()) return emptyMap()

            val jsonArray = JSONArray(jsonString)
            val metadata = mutableMapOf<String, AlbumMetadata>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                metadata[name] = AlbumMetadata(
                    name = name,
                    color = obj.optLong("color", -1).takeIf { it != -1L },
                    textColor = obj.optLong("textColor", -1).takeIf { it != -1L },
                    order = obj.optInt("order", Int.MAX_VALUE),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    isHidden = obj.optBoolean("isHidden", false),
                    isExcludedFromRecommend = obj.optBoolean("isExcludedFromRecommend", false),
                    // C1/T03-COVER：旧 JSON 无封面字段 → opt 兜底 null（升级
                    // 前行为=桶默认封面，兼容）；卷缺失为旧口径（展示端唯一
                    // 解析/歧义回退）。
                    coverImageId = obj.optLong("coverImageId", -1).takeIf { it != -1L },
                    coverVolumeName = obj.optString("coverVolumeName", "").takeIf { it.isNotEmpty() }
                )
            }

            return metadata
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata", e)
            return emptyMap()
        }
    }

    private fun saveMetadata(metadata: Map<String, AlbumMetadata>) {
        try {
            val jsonArray = JSONArray()
            metadata.values.forEach { meta ->
                val obj = JSONObject().apply {
                    put("name", meta.name)
                    put("color", meta.color ?: -1)
                    put("textColor", meta.textColor ?: -1)
                    put("order", meta.order)
                    put("createdAt", meta.createdAt)
                    put("isHidden", meta.isHidden)
                    put("isExcludedFromRecommend", meta.isExcludedFromRecommend)
                    // C1/T03-COVER：封面完整媒体身份持久化（-1/空串 = 无封面）。
                    put("coverImageId", meta.coverImageId ?: -1)
                    put("coverVolumeName", meta.coverVolumeName ?: "")
                }
                jsonArray.put(obj)
            }
            metadataFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata", e)
        }
    }

    // ========== 兼容性方法（标记为废弃，保持编译通过） ==========

    @Deprecated("系统相册集成后不再需要同步")
    suspend fun syncAllEnabledAlbumsToSystem(
        onProgress: ((albumIndex: Int, totalAlbums: Int, currentImages: Int, totalImages: Int) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        // 系统相册集成后，不需要同步，直接返回成功
        Log.d(TAG, "syncAllEnabledAlbumsToSystem is deprecated - albums are already in system")
        SyncResult(
            successCount = _albums.value.size,
            totalAlbums = _albums.value.size,
            newlySyncedImages = 0,
            skippedImages = 0,
            albumResults = emptyList()
        )
    }

    /**
     * 获取所有图集的待清理原图。
     *
     * 候选 = 账本中存在 source→verified destination 证据、且目标当前仍存在且与源
     * 身份独立的源。旧记录（source_images.json）没有证据，一律按未验证保留，
     * 不列为候选；最终删除前还有逐字节深度复核（见 getReverifiedCleanableUris*）。
     */
    suspend fun getCleanableUrisForAllAlbums(): CleanableUrisResult = withContext(Dispatchers.IO) {
        computeCleanableUris(getAllSourceImages())
    }

    /**
     * 获取指定图集的待清理原图（口径同 [getCleanableUrisForAllAlbums]）。
     */
    suspend fun getCleanableUrisForAlbum(albumId: String): CleanableUrisResult = withContext(Dispatchers.IO) {
        computeCleanableUris(getSourceImagesForAlbum(albumId))
    }

    private suspend fun computeCleanableUris(sources: List<Uri>): CleanableUrisResult {
        val ledger = copyEvidenceStore.loadAll()
        val cleanable = mutableListOf<Uri>()
        for (sourceUri in sources) {
            val entry = ledger[sourceUri.toString()] ?: continue
            if (syncManager.isIndependentExistingCopy(sourceUri, Uri.parse(entry.destinationUri))) {
                cleanable.add(sourceUri)
            }
        }
        return CleanableUrisResult(
            sourceUris = cleanable,
            orphanUris = emptyList(),
            sourceTotalCount = sources.size,
            orphanTotalCount = 0
        )
    }

    /**
     * 清理确认时刻使用：对账本候选逐项做全文深度复核（源/目标 SHA-256 一致且身份独立）。
     * 复核不过的源落回"未验证"，绝不进入删除集合。
     */
    suspend fun getReverifiedCleanableUrisForAllAlbums(): CleanableUrisResult = withContext(Dispatchers.IO) {
        val sources = getAllSourceImages()
        val ledger = copyEvidenceStore.loadAll()
        val cleanable = mutableListOf<Uri>()
        for (sourceUri in sources) {
            val entry = ledger[sourceUri.toString()] ?: continue
            val destination = try {
                Uri.parse(entry.destinationUri)
            } catch (e: Exception) {
                Log.w(TAG, "Ledger destination unparsable for $sourceUri", e)
                continue
            }
            if (syncManager.verifyCopyIntegrity(sourceUri, destination)) {
                cleanable.add(sourceUri)
            }
        }
        CleanableUrisResult(
            sourceUris = cleanable,
            orphanUris = emptyList(),
            sourceTotalCount = sources.size,
            orphanTotalCount = 0
        )
    }

    /**
     * 清除已删除的原图记录
     *
     * 当用户清理原图后调用，从追踪列表与证据账本中移除已删除的 URI
     */
    suspend fun clearDeletedUris(deletedUris: Set<String>) = withContext(Dispatchers.IO) {
        clearSourceImageRecords(deletedUris = deletedUris)
        copyEvidenceStore.removeAll(deletedUris)
    }

    // ========== 数据类（保持兼容性） ==========

    data class SyncResult(
        val successCount: Int,
        val totalAlbums: Int,
        val newlySyncedImages: Int,
        val skippedImages: Int,
        val albumResults: List<AlbumSyncResult>
    ) {
        val syncedImages get() = newlySyncedImages
    }

    data class AlbumSyncResult(
        val albumName: String,
        val newlySynced: Int,
        val skipped: Int,
        val success: Boolean
    ) {
        val imagesSynced get() = newlySynced
    }

    data class CleanableUrisResult(
        val sourceUris: List<Uri>,
        val orphanUris: List<Uri>,
        val sourceTotalCount: Int,
        val orphanTotalCount: Int
    ) {
        val allUris: List<Uri> get() = sourceUris + orphanUris
        val isEmpty: Boolean get() = sourceUris.isEmpty() && orphanUris.isEmpty()
        val totalValidCount: Int get() = sourceUris.size + orphanUris.size
        val unverifiedSourceCount: Int get() = (sourceTotalCount - sourceUris.size).coerceAtLeast(0)
    }

    data class MoveImagesResult(
        val movedCount: Int,
        val retainedCount: Int
    )

    // ========== 延迟执行归档功能（撤销核心） ==========

    /**
     * 将图片加入待归档队列（不立即复制）
     * 
     * 用户归档图片时调用此方法，图片不会立即复制到目标相册。
     * 需要等待 commitPendingArchive() 被调用后才会真正执行复制。
     * 
     * @return 返回待执行操作的信息，用于 UI 显示和后续提交/撤销
     */
    fun queueImageToAlbum(
        imageId: Long,
        imageUri: String,
        albumId: String,
        albumName: String
    ): PendingArchive {
        val action = PendingArchive(
            id = UUID.randomUUID().toString(),
            imageId = imageId,
            imageUri = imageUri,
            albumId = albumId,
            albumName = albumName
        )
        pendingArchiveQueue.add(action)
        Log.d(TAG, "Queued image for archive: imageId=$imageId -> album='$albumName' (pending, not copied yet)")
        return action
    }

    /**
     * 撤销最后一个待归档操作
     * 
     * 用户点击撤销按钮时调用。由于图片还没有被复制，
     * 只需要从队列中移除即可，无需删除任何文件。
     * 
     * @return 被撤销的操作信息，如果队列为空返回 null
     */
    fun cancelLastPendingArchive(): PendingArchive? {
        // 使用 synchronized 保证 isEmpty 检查和 removeAt 操作的原子性
        synchronized(pendingArchiveQueue) {
            if (pendingArchiveQueue.isEmpty()) {
                Log.d(TAG, "No pending archive to cancel")
                return null
            }
            val cancelled = pendingArchiveQueue.removeAt(pendingArchiveQueue.lastIndex)
            Log.d(TAG, "Cancelled pending archive: imageId=${cancelled.imageId} -> album='${cancelled.albumName}'")
            return cancelled
        }
    }

    /**
     * 归档提交结果。
     * COMMITTED：复制成功（可能附证据写入失败，源按未验证保留）；
     * COPY_FAILED：已领取但复制失败，未产生任何记录；
     * NOT_CLAIMED：action 已被提交/撤销过，属于良性重复调用，无副作用。
     */
    enum class ArchiveCommitOutcome { COMMITTED, COPY_FAILED, NOT_CLAIMED }

    /**
     * 提交待归档操作，执行真正的复制
     *
     * Snackbar 超时消失时调用，表示用户确认了归档操作。
     * 此时才执行真正的图片复制。
     *
     * 原子领取：同一 action 的并发/重复提交至多产生一次复制副作用。
     *
     * @param actionId 要提交的操作 ID
     */
    suspend fun commitPendingArchive(actionId: String): ArchiveCommitOutcome = withContext(Dispatchers.IO) {
        val action = claimPendingArchive(actionId)
        if (action == null) {
            Log.w(TAG, "Pending archive not claimed: $actionId (might have been committed or cancelled)")
            return@withContext ArchiveCommitOutcome.NOT_CLAIMED
        }

        Log.d(TAG, "Committing archive: imageId=${action.imageId} -> album='${action.albumName}'")

        val uri = Uri.parse(action.imageUri)
        val evidence = syncManager.copyImageWithEvidence(uri, action.albumId)

        if (evidence == null) {
            Log.e(TAG, "Failed to copy image to ${action.albumName}; source retained")
            return@withContext ArchiveCommitOutcome.COPY_FAILED
        }

        val recorded = copyEvidenceStore.put(
            sourceUri = action.imageUri,
            entry = CopyEvidenceEntry(
                destinationUri = evidence.uri.toString(),
                byteCount = evidence.byteCount,
                sha256Hex = evidence.sha256Hex,
                albumId = action.albumId,
                recordedAt = System.currentTimeMillis()
            )
        )
        if (!recorded) {
            Log.w(TAG, "Copy succeeded but evidence write failed; source stays unverified: ${action.imageUri}")
        }

        // 记录原图位置，用于后续清理
        addSourceImageRecord(action.albumId, action.imageUri)

        // 记录使用频率（用于智能排序）
        usageTracker.recordUsage(action.albumId)

        // 刷新图集列表以更新图片数量
        refreshAlbumsFromSystem()
        return@withContext ArchiveCommitOutcome.COMMITTED
    }

    /** 在队列锁内一次性完成查找与移除，保证同一 action 只被领取一次。 */
    private fun claimPendingArchive(actionId: String): PendingArchive? {
        synchronized(pendingArchiveQueue) {
            val index = pendingArchiveQueue.indexOfFirst { it.id == actionId }
            if (index < 0) return null
            return pendingArchiveQueue.removeAt(index)
        }
    }

    /**
     * 获取最后一个待归档操作（用于 UI 显示）
     */
    fun getLastPendingArchive(): PendingArchive? = pendingArchiveQueue.lastOrNull()

    // ========== 旧的撤销功能（保留兼容性） ==========

    @Deprecated("使用 cancelLastPendingArchive() 替代")
    private fun addUndoAction(action: PendingAlbumAction) {
        undoQueue.add(action)
        while (undoQueue.size > MAX_UNDO_ACTIONS) {
            undoQueue.removeAt(0)
        }
    }

    @Deprecated("使用 cancelLastPendingArchive() 替代")
    suspend fun undoLastAction(): PendingAlbumAction? = withContext(Dispatchers.IO) {
        // 新逻辑：调用 cancelLastPendingArchive
        val cancelled = cancelLastPendingArchive()
        if (cancelled != null) {
            Log.d(TAG, "undoLastAction: cancelled pending archive for image ${cancelled.imageId}")
        }
        
        // 保持返回类型兼容
        if (undoQueue.isEmpty()) return@withContext null
        undoQueue.removeAt(undoQueue.lastIndex)
    }

    @Deprecated("使用 getLastPendingArchive() 替代")
    fun getLastAction(): PendingAlbumAction? = undoQueue.lastOrNull()

    @Deprecated("不再需要")
    fun clearUndoQueue() {
        undoQueue.clear()
        pendingArchiveQueue.clear()
    }
}
