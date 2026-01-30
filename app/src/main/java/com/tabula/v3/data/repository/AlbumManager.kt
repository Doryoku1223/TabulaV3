package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.AlbumActionType
import com.tabula.v3.data.model.AlbumMapping
import com.tabula.v3.data.model.PendingAlbumAction
import com.tabula.v3.data.model.SyncMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 相册管理器
 *
 * 负责相册的 CRUD 操作、图片归类管理、以及撤销功能。
 * 数据持久化到本地 JSON 文件。
 */
class AlbumManager(private val context: Context) {

    companion object {
        private const val TAG = "AlbumManager"
        private const val ALBUMS_FILE = "albums.json"
        private const val MAPPINGS_FILE = "album_mappings.json"
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

    private val albumsFile: File
        get() = File(context.filesDir, ALBUMS_FILE)

    private val mappingsFile: File
        get() = File(context.filesDir, MAPPINGS_FILE)

    // 内存缓存
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _mappings = MutableStateFlow<List<AlbumMapping>>(emptyList())
    val mappings: StateFlow<List<AlbumMapping>> = _mappings.asStateFlow()

    // 撤销队列
    private val undoQueue = mutableListOf<PendingAlbumAction>()

    // ========== 相册 CRUD ==========

    /**
     * 创建新相册
     */
    suspend fun createAlbum(
        name: String,
        color: Long? = null,
        emoji: String? = null
    ): Album = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val maxOrder = currentAlbums.maxOfOrNull { it.order } ?: 0

        val newAlbum = Album(
            name = name,
            color = color,
            emoji = emoji,
            order = maxOrder + 1
        )

        val updatedAlbums = currentAlbums + newAlbum
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums

        newAlbum
    }

    /**
     * 更新相册信息
     */
    suspend fun updateAlbum(album: Album) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.map {
            if (it.id == album.id) album else it
        }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums
    }

    /**
     * 删除相册（同时清理相关映射）
     */
    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        // 删除相册
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.filter { it.id != albumId }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums

        // 清理映射关系
        val currentMappings = loadMappings()
        val updatedMappings = currentMappings.mapNotNull { mapping ->
            val newAlbumIds = mapping.albumIds.filter { it != albumId }
            if (newAlbumIds.isEmpty()) null
            else mapping.copy(albumIds = newAlbumIds)
        }
        saveMappings(updatedMappings)
        _mappings.value = updatedMappings
    }

    /**
     * 获取所有相册（按 order 排序）
     */
    suspend fun getAllAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = loadAlbums().sortedBy { it.order }
        _albums.value = albums
        albums
    }

    /**
     * 重新排序相册
     */
    suspend fun reorderAlbums(albumIds: List<String>) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.map { album ->
            val newOrder = albumIds.indexOf(album.id)
            if (newOrder >= 0) album.copy(order = newOrder)
            else album
        }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums.sortedBy { it.order }
    }

    // ========== 图片归类 ==========

    /**
     * 将图片添加到相册
     */
    suspend fun addImageToAlbum(
        imageId: Long,
        imageUri: String,
        albumId: String,
        recordUndo: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val currentMappings = loadMappings()
        val existingMapping = currentMappings.find { it.imageId == imageId }

        val updatedMappings = if (existingMapping != null) {
            if (existingMapping.albumIds.contains(albumId)) {
                // 已经在该相册中
                currentMappings
            } else {
                // 添加到新相册
                currentMappings.map {
                    if (it.imageId == imageId) {
                        it.copy(
                            albumIds = it.albumIds + albumId,
                            addedAt = System.currentTimeMillis()
                        )
                    } else it
                }
            }
        } else {
            // 新建映射
            currentMappings + AlbumMapping(
                imageId = imageId,
                imageUri = imageUri,
                albumIds = listOf(albumId)
            )
        }

        saveMappings(updatedMappings)
        _mappings.value = updatedMappings

        // 更新相册图片数量和封面
        updateAlbumStats(albumId)

        // 注意：不再自动同步到系统相册
        // 用户需要手动点击同步按钮来同步图片到手机相册
        // 这样可以避免每次添加图片都立即出现在微信等应用的最近照片中

        // 记录撤销操作
        if (recordUndo) {
            addUndoAction(
                PendingAlbumAction(
                    id = UUID.randomUUID().toString(),
                    type = AlbumActionType.ADD,
                    imageId = imageId,
                    albumId = albumId
                )
            )
        }
    }

    /**
     * 从相册移除图片
     */
    suspend fun removeImageFromAlbum(
        imageId: Long,
        albumId: String,
        recordUndo: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val currentMappings = loadMappings()
        val updatedMappings = currentMappings.mapNotNull { mapping ->
            if (mapping.imageId == imageId) {
                val newAlbumIds = mapping.albumIds.filter { it != albumId }
                if (newAlbumIds.isEmpty()) null
                else mapping.copy(albumIds = newAlbumIds)
            } else mapping
        }

        saveMappings(updatedMappings)
        _mappings.value = updatedMappings

        // 更新相册统计
        updateAlbumStats(albumId)

        // 记录撤销操作
        if (recordUndo) {
            addUndoAction(
                PendingAlbumAction(
                    id = UUID.randomUUID().toString(),
                    type = AlbumActionType.REMOVE,
                    imageId = imageId,
                    albumId = albumId
                )
            )
        }
    }

    /**
     * 获取图片所属的相册列表
     */
    suspend fun getAlbumsForImage(imageId: Long): List<Album> = withContext(Dispatchers.IO) {
        val mapping = loadMappings().find { it.imageId == imageId }
        if (mapping == null) return@withContext emptyList()

        val allAlbums = loadAlbums()
        allAlbums.filter { it.id in mapping.albumIds }
    }

    /**
     * 获取相册内的所有图片 ID
     */
    suspend fun getImageIdsForAlbum(albumId: String): List<Long> = withContext(Dispatchers.IO) {
        loadMappings()
            .filter { mapping -> albumId in mapping.albumIds }
            .map { it.imageId }
    }

    /**
     * 获取图片所属的相册 ID 集合
     */
    suspend fun getAlbumIdsForImage(imageId: Long): Set<String> = withContext(Dispatchers.IO) {
        loadMappings()
            .find { it.imageId == imageId }
            ?.albumIds
            ?.toSet()
            ?: emptySet()
    }

    /**
     * 批量将图片移动到另一个图集
     * 会同时从当前图集移除并添加到目标图集
     */
    suspend fun moveImagesToAlbum(
        imageIds: List<Long>,
        fromAlbumId: String,
        toAlbumId: String
    ) = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) return@withContext
        
        val currentMappings = loadMappings()
        val updatedMappings = currentMappings.map { mapping ->
            if (mapping.imageId in imageIds) {
                // 从源图集移除，添加到目标图集
                val newAlbumIds = (mapping.albumIds - fromAlbumId + toAlbumId).distinct()
                mapping.copy(
                    albumIds = newAlbumIds,
                    addedAt = System.currentTimeMillis()
                )
            } else {
                mapping
            }
        }
        
        saveMappings(updatedMappings)
        _mappings.value = updatedMappings
        
        // 更新两个图集的统计信息
        updateAlbumStats(fromAlbumId)
        updateAlbumStats(toAlbumId)
        
        Log.d(TAG, "Moved ${imageIds.size} images from $fromAlbumId to $toAlbumId")
    }

    // ========== 撤销功能 ==========

    /**
     * 撤销上一个操作
     */
    suspend fun undoLastAction(): PendingAlbumAction? = withContext(Dispatchers.IO) {
        if (undoQueue.isEmpty()) return@withContext null

        val lastAction = undoQueue.removeAt(undoQueue.lastIndex)

        when (lastAction.type) {
            AlbumActionType.ADD -> {
                // 撤销添加 = 移除
                val mapping = loadMappings().find { it.imageId == lastAction.imageId }
                if (mapping != null) {
                    removeImageFromAlbum(lastAction.imageId, lastAction.albumId, recordUndo = false)
                }
            }
            AlbumActionType.REMOVE -> {
                // 撤销移除 = 添加回去
                val mapping = loadMappings().find { it.imageId == lastAction.imageId }
                val uri = mapping?.imageUri ?: "content://media/external/images/media/${lastAction.imageId}"
                addImageToAlbum(lastAction.imageId, uri, lastAction.albumId, recordUndo = false)
            }
            else -> { /* 批量操作暂不支持撤销 */ }
        }

        lastAction
    }

    /**
     * 获取最后一个可撤销的操作
     */
    fun getLastAction(): PendingAlbumAction? = undoQueue.lastOrNull()

    /**
     * 清空撤销队列
     */
    fun clearUndoQueue() {
        undoQueue.clear()
    }

    private fun addUndoAction(action: PendingAlbumAction) {
        undoQueue.add(action)
        // 限制队列长度
        while (undoQueue.size > MAX_UNDO_ACTIONS) {
            undoQueue.removeAt(0)
        }
    }

    // ========== 回收站协同 ==========

    /**
     * 清理已永久删除图片的映射关系
     */
    suspend fun cleanupMappingsForDeletedImages(imageIds: List<Long>) = withContext(Dispatchers.IO) {
        val currentMappings = loadMappings()
        val idsToRemove = imageIds.toSet()
        val updatedMappings = currentMappings.filter { it.imageId !in idsToRemove }

        if (updatedMappings.size != currentMappings.size) {
            saveMappings(updatedMappings)
            _mappings.value = updatedMappings

            // 更新所有相册的统计
            loadAlbums().forEach { album ->
                updateAlbumStats(album.id)
            }
        }
    }

    // ========== 系统相册同步 ==========

    private val syncManager: SystemAlbumSyncManager by lazy {
        SystemAlbumSyncManager.getInstance(context)
    }

    /**
     * 切换相册的系统同步状态
     */
    suspend fun toggleSyncEnabled(albumId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val album = currentAlbums.find { it.id == albumId } ?: return@withContext

        if (enabled) {
            // 启用同步：创建系统相册文件夹
            val systemPath = syncManager.createSystemAlbum(album.name)
            val updatedAlbum = album.copy(
                isSyncEnabled = true,
                systemAlbumPath = systemPath
            )
            updateAlbum(updatedAlbum)

            // 同步现有图片
            if (systemPath != null) {
                syncAlbumImagesToSystem(album.id)
            }
        } else {
            // 禁用同步
            val updatedAlbum = album.copy(
                isSyncEnabled = false
            )
            updateAlbum(updatedAlbum)
            // 注意：不删除系统相册中已复制的图片
        }
    }

    /**
     * 修改相册的同步模式
     */
    suspend fun changeSyncMode(albumId: String, mode: SyncMode) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val album = currentAlbums.find { it.id == albumId } ?: return@withContext

        val updatedAlbum = album.copy(syncMode = mode)
        updateAlbum(updatedAlbum)
        
        Log.d(TAG, "Changed sync mode for album '${album.name}' to $mode")
    }

    /**
     * 同步相册中的图片到系统相册
     */
    suspend fun syncAlbumImagesToSystem(
        albumId: String,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val album = loadAlbums().find { it.id == albumId } ?: return@withContext 0
        if (!album.isSyncEnabled) return@withContext 0

        val imageIds = getImageIdsForAlbum(albumId)
        val mappings = loadMappings()

        val imageUris = imageIds.mapNotNull { imageId ->
            mappings.find { it.imageId == imageId }?.imageUri?.let { Uri.parse(it) }
        }

        if (imageUris.isEmpty()) return@withContext 0

        Log.d(TAG, "Syncing ${imageUris.size} images to system album: ${album.name} (mode: ${album.syncMode})")
        val result = syncManager.syncAlbumToSystem(album.name, imageUris, album.syncMode, onProgress)
        result.newlySynced  // 返回新同步的图片数量
    }

    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val successCount: Int,
        val totalAlbums: Int,
        val newlySyncedImages: Int,    // 新同步的图片总数
        val skippedImages: Int,         // 已存在跳过的图片总数
        val albumResults: List<AlbumSyncResult>
    ) {
        // 兼容旧的 syncedImages 属性
        val syncedImages get() = newlySyncedImages
    }

    data class AlbumSyncResult(
        val albumName: String,
        val newlySynced: Int,    // 新同步的图片数
        val skipped: Int,        // 已存在跳过的图片数
        val success: Boolean
    ) {
        // 兼容旧的 imagesSynced 属性
        val imagesSynced get() = newlySynced
    }

    /**
     * 同步所有有图片的图集到系统相册
     * 注意：现在同步所有有图片的图集，不再依赖 isSyncEnabled 标志
     * isSyncEnabled 标志改为控制"实时自动同步"（添加图片时自动同步）
     * 
     * @param onProgress 进度回调，参数为 (当前图集索引, 总图集数, 当前图集已同步图片数, 当前图集总图片数)
     * @return 同步结果
     */
    suspend fun syncAllEnabledAlbumsToSystem(
        onProgress: ((albumIndex: Int, totalAlbums: Int, currentImages: Int, totalImages: Int) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val allAlbums = loadAlbums()
        // 同步所有有图片的图集（不再限制 isSyncEnabled）
        val albumsWithImages = allAlbums.filter { it.imageCount > 0 }
        
        // #region agent log
        Log.d("DEBUG_SYNC", "[A1,A2,B1] syncAllEnabledAlbumsToSystem:entry | albumsWithImagesCount=${albumsWithImages.size} | albumNames=${albumsWithImages.map { it.name }} | albumCoverIds=${albumsWithImages.map { "${it.name}:${it.coverImageId}" }}")
        // #endregion
        
        if (albumsWithImages.isEmpty()) {
            Log.d(TAG, "No albums with images to sync")
            return@withContext SyncResult(0, 0, 0, 0, emptyList())
        }

        Log.d(TAG, "Starting sync for ${albumsWithImages.size} albums")
        
        val albumResults = mutableListOf<AlbumSyncResult>()
        var totalSyncedImages = 0
        var totalSkippedImages = 0
        var successCount = 0

        albumsWithImages.forEachIndexed { index, album ->
            try {
                val imageIds = getImageIdsForAlbum(album.id)
                val mappings = loadMappings()
                
                // 确保封面图片排在最后同步，这样它的 DATE_MODIFIED 最新
                // 系统相册按 DATE_MODIFIED DESC 选择封面，所以最后同步的图片会成为封面
                val sortedImageIds = if (album.coverImageId != null && imageIds.contains(album.coverImageId)) {
                    imageIds.filter { it != album.coverImageId } + listOf(album.coverImageId)
                } else {
                    imageIds
                }
                
                val imageUris = sortedImageIds.mapNotNull { imageId ->
                    mappings.find { it.imageId == imageId }?.imageUri?.let { Uri.parse(it) }
                }

                // #region agent log
                Log.d("DEBUG_SYNC", "[A2,A3,B1] Processing album | name=${album.name} | coverImageId=${album.coverImageId} | imageIds=$sortedImageIds | imageUrisCount=${imageUris.size} | firstImageUri=${imageUris.firstOrNull()}")
                // #endregion

                if (imageUris.isEmpty()) {
                    Log.d(TAG, "Album '${album.name}' has no images to sync")
                    albumResults.add(AlbumSyncResult(album.name, 0, 0, true))
                    successCount++
                    onProgress?.invoke(index + 1, albumsWithImages.size, 0, 0)
                    return@forEachIndexed
                }

                // 确保系统相册文件夹存在（为所有图集创建，不限于 isSyncEnabled）
                // 注意：不再自动设置 isSyncEnabled = true，避免后续添加图片时自动同步
                if (album.systemAlbumPath.isNullOrBlank()) {
                    val systemPath = syncManager.createSystemAlbum(album.name)
                    if (systemPath != null) {
                        val updatedAlbum = album.copy(systemAlbumPath = systemPath)
                        updateAlbum(updatedAlbum)
                    }
                }

                Log.d(TAG, "Syncing album '${album.name}' with ${imageUris.size} images")
                
                val syncResult = syncManager.syncAlbumToSystem(
                    album.name, 
                    imageUris, 
                    album.syncMode
                ) { current, total ->
                    onProgress?.invoke(index + 1, albumsWithImages.size, current, total)
                }
                
                // #region agent log
                Log.d("DEBUG_SYNC", "[B1,B2] Album sync completed | albumName=${album.name} | newlySynced=${syncResult.newlySynced} | alreadyExists=${syncResult.alreadyExists} | totalImages=${imageUris.size}")
                // #endregion
                
                totalSyncedImages += syncResult.newlySynced
                totalSkippedImages += syncResult.alreadyExists
                albumResults.add(AlbumSyncResult(album.name, syncResult.newlySynced, syncResult.alreadyExists, true))
                successCount++
                
                Log.d(TAG, "Successfully synced album '${album.name}': ${syncResult.newlySynced} new, ${syncResult.alreadyExists} skipped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync album '${album.name}'", e)
                albumResults.add(AlbumSyncResult(album.name, 0, 0, false))
            }
        }

        Log.d(TAG, "Sync completed: $successCount/${albumsWithImages.size} albums, $totalSyncedImages new, $totalSkippedImages skipped")
        SyncResult(successCount, albumsWithImages.size, totalSyncedImages, totalSkippedImages, albumResults)
    }

    /**
     * 将单张图片同步到系统相册（当开启同步时）
     */
    private suspend fun syncImageToSystemIfEnabled(imageUri: String, albumId: String) {
        val album = loadAlbums().find { it.id == albumId } ?: return
        if (!album.isSyncEnabled) return

        try {
            val uri = Uri.parse(imageUri)
            syncManager.syncImage(uri, album.name, album.syncMode)
            Log.d(TAG, "Synced image to system album: ${album.name} (mode: ${album.syncMode})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync image to system album", e)
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 更新相册的图片数量和封面
     */
    private suspend fun updateAlbumStats(albumId: String) {
        val imageIds = getImageIdsForAlbum(albumId)
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.map { album ->
            if (album.id == albumId) {
                album.copy(
                    imageCount = imageIds.size,
                    coverImageId = imageIds.firstOrNull() ?: album.coverImageId
                )
            } else album
        }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums.sortedBy { it.order }
    }

    /**
     * 初始化（加载数据到内存）
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        _albums.value = loadAlbums().sortedBy { it.order }
        _mappings.value = loadMappings()
    }

    // ========== JSON 持久化 ==========

    private fun loadAlbums(): List<Album> {
        try {
            if (!albumsFile.exists()) return emptyList()

            val jsonString = albumsFile.readText()
            if (jsonString.isBlank()) return emptyList()

            val jsonArray = JSONArray(jsonString)
            val albums = mutableListOf<Album>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val syncModeStr = obj.optString("syncMode", "MOVE")
                val syncMode = try { SyncMode.valueOf(syncModeStr) } catch (e: Exception) { SyncMode.MOVE }
                
                albums.add(
                    Album(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        coverImageId = obj.optLong("coverImageId", -1).takeIf { it != -1L },
                        color = obj.optLong("color", -1).takeIf { it != -1L },
                        textColor = obj.optLong("textColor", -1).takeIf { it != -1L },
                        emoji = obj.optString("emoji", "").takeIf { it.isNotBlank() },
                        order = obj.optInt("order", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        imageCount = obj.optInt("imageCount", 0),
                        systemAlbumPath = obj.optString("systemAlbumPath", "").takeIf { it.isNotBlank() },
                        isSyncEnabled = obj.optBoolean("isSyncEnabled", false),
                        syncMode = syncMode
                    )
                )
            }

            return albums
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun saveAlbums(albums: List<Album>) {
        try {
            val jsonArray = JSONArray()
            albums.forEach { album ->
                val obj = JSONObject().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("coverImageId", album.coverImageId ?: -1)
                    put("color", album.color ?: -1)
                    put("textColor", album.textColor ?: -1)
                    put("emoji", album.emoji ?: "")
                    put("order", album.order)
                    put("createdAt", album.createdAt)
                    put("imageCount", album.imageCount)
                    put("systemAlbumPath", album.systemAlbumPath ?: "")
                    put("isSyncEnabled", album.isSyncEnabled)
                    put("syncMode", album.syncMode.name)
                }
                jsonArray.put(obj)
            }
            albumsFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadMappings(): List<AlbumMapping> {
        try {
            if (!mappingsFile.exists()) return emptyList()

            val jsonString = mappingsFile.readText()
            if (jsonString.isBlank()) return emptyList()

            val jsonArray = JSONArray(jsonString)
            val mappings = mutableListOf<AlbumMapping>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val albumIdsArray = obj.getJSONArray("albumIds")
                val albumIds = mutableListOf<String>()
                for (j in 0 until albumIdsArray.length()) {
                    albumIds.add(albumIdsArray.getString(j))
                }

                mappings.add(
                    AlbumMapping(
                        imageId = obj.getLong("imageId"),
                        imageUri = obj.getString("imageUri"),
                        albumIds = albumIds,
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }

            return mappings
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun saveMappings(mappings: List<AlbumMapping>) {
        try {
            val jsonArray = JSONArray()
            mappings.forEach { mapping ->
                val albumIdsArray = JSONArray().apply {
                    mapping.albumIds.forEach { put(it) }
                }
                val obj = JSONObject().apply {
                    put("imageId", mapping.imageId)
                    put("imageUri", mapping.imageUri)
                    put("albumIds", albumIdsArray)
                    put("addedAt", mapping.addedAt)
                }
                jsonArray.put(obj)
            }
            mappingsFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
