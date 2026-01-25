package com.tabula.v3.data.model

import android.net.Uri

/**
 * 图片文件领域模型
 *
 * @param id MediaStore 唯一标识符
 * @param uri 内容提供者 URI
 * @param displayName 显示名称
 * @param dateModified 修改时间戳（毫秒）
 * @param size 文件大小（字节）
 * @param width 图片宽度（像素）
 * @param height 图片高度（像素）
 * @param bucketDisplayName 所属相册名称
 */
data class ImageFile(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketDisplayName: String?
) {
    /**
     * 判断是否为纵向图片
     */
    val isPortrait: Boolean
        get() = height > width

    /**
     * 宽高比
     */
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 1f

    companion object {
        /**
         * 从 MediaStore cursor 创建 ImageFile
         */
        fun fromCursor(
            id: Long,
            displayName: String,
            dateModified: Long,
            size: Long,
            width: Int,
            height: Int,
            bucketDisplayName: String?
        ): ImageFile {
            return ImageFile(
                id = id,
                uri = Uri.parse("content://media/external/images/media/$id"),
                displayName = displayName,
                dateModified = dateModified,
                size = size,
                width = width,
                height = height,
                bucketDisplayName = bucketDisplayName
            )
        }
    }
}
