package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 回收站持久化管理器
 * 
 * 将标记删除的图片信息保存到本地文件，
 * 应用重启后仍然可以访问回收站内容。
 */
class RecycleBinManager(private val context: Context) {
    
    companion object {
        private const val RECYCLE_BIN_FILE = "recycle_bin.json"
        
        @Volatile
        private var instance: RecycleBinManager? = null
        
        fun getInstance(context: Context): RecycleBinManager {
            return instance ?: synchronized(this) {
                instance ?: RecycleBinManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val recycleBinFile: File
        get() = File(context.filesDir, RECYCLE_BIN_FILE)
    
    /**
     * 添加图片到回收站
     */
    suspend fun addToRecycleBin(image: ImageFile) = withContext(Dispatchers.IO) {
        val currentItems = loadRecycleBin().toMutableList()
        // 避免重复添加
        if (currentItems.none { it.id == image.id }) {
            currentItems.add(image)
            saveRecycleBin(currentItems)
        }
    }
    
    /**
     * 批量添加图片到回收站
     */
    suspend fun addAllToRecycleBin(images: List<ImageFile>) = withContext(Dispatchers.IO) {
        val currentItems = loadRecycleBin().toMutableList()
        images.forEach { image ->
            if (currentItems.none { it.id == image.id }) {
                currentItems.add(image)
            }
        }
        saveRecycleBin(currentItems)
    }
    
    /**
     * 从回收站移除（恢复或永久删除后调用）
     */
    suspend fun removeFromRecycleBin(image: ImageFile) = withContext(Dispatchers.IO) {
        val currentItems = loadRecycleBin().toMutableList()
        currentItems.removeAll { it.id == image.id }
        saveRecycleBin(currentItems)
    }
    
    /**
     * 批量从回收站移除
     */
    suspend fun removeAllFromRecycleBin(images: List<ImageFile>) = withContext(Dispatchers.IO) {
        val currentItems = loadRecycleBin().toMutableList()
        val idsToRemove = images.map { it.id }.toSet()
        currentItems.removeAll { it.id in idsToRemove }
        saveRecycleBin(currentItems)
    }
    
    /**
     * 清空回收站
     */
    suspend fun clearRecycleBin() = withContext(Dispatchers.IO) {
        saveRecycleBin(emptyList())
    }
    
    /**
     * 加载回收站内容
     */
    suspend fun loadRecycleBin(): List<ImageFile> = withContext(Dispatchers.IO) {
        try {
            if (!recycleBinFile.exists()) {
                return@withContext emptyList()
            }
            
            val jsonString = recycleBinFile.readText()
            if (jsonString.isBlank()) {
                return@withContext emptyList()
            }
            
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<ImageFile>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val bucket = obj.optString("bucketDisplayName", "")
                val bucketDisplay = bucket.ifBlank { null }
                val imageFile = ImageFile(
                    id = obj.getLong("id"),
                    uri = Uri.parse(obj.getString("uri")),
                    displayName = obj.getString("displayName"),
                    dateModified = obj.getLong("dateModified"),
                    size = obj.getLong("size"),
                    width = obj.getInt("width"),
                    height = obj.getInt("height"),
                    bucketDisplayName = bucketDisplay
                )
                items.add(imageFile)
            }
            
            items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 保存回收站内容
     */
    private fun saveRecycleBin(items: List<ImageFile>) {
        try {
            val jsonArray = JSONArray()
            items.forEach { image ->
                val obj = JSONObject().apply {
                    put("id", image.id)
                    put("uri", image.uri.toString())
                    put("displayName", image.displayName)
                    put("dateModified", image.dateModified)
                    put("size", image.size)
                    put("width", image.width)
                    put("height", image.height)
                    put("bucketDisplayName", image.bucketDisplayName ?: "")
                }
                jsonArray.put(obj)
            }
            recycleBinFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 获取回收站数量
     */
    suspend fun getRecycleBinCount(): Int = withContext(Dispatchers.IO) {
        loadRecycleBin().size
    }
}
