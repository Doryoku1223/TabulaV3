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
 * @param width 图片宽度（像素）- MediaStore 原始值，未考虑旋转
 * @param height 图片高度（像素）- MediaStore 原始值，未考虑旋转
 * @param bucketDisplayName 所属相册名称
 * @param orientation EXIF 旋转角度（0, 90, 180, 270）
 */
data class ImageFile(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketDisplayName: String?,
    val orientation: Int = 0
) {
    /**
     * 是否需要交换宽高（90度或270度旋转）
     */
    private val needSwapDimensions: Boolean
        get() = orientation == 90 || orientation == 270
    
    /**
     * 考虑旋转后的实际宽度
     */
    val actualWidth: Int
        get() = if (needSwapDimensions) height else width
    
    /**
     * 考虑旋转后的实际高度
     */
    val actualHeight: Int
        get() = if (needSwapDimensions) width else height

    /**
     * 判断是否为纵向图片（考虑旋转）
     */
    val isPortrait: Boolean
        get() = actualHeight > actualWidth

    /**
     * 宽高比（考虑旋转）
     * 当 width 或 height 无效时返回默认值 0.75 (3:4)
     */
    val aspectRatio: Float
        get() = if (actualWidth > 0 && actualHeight > 0) actualWidth.toFloat() / actualHeight else 0.75f
    
    /**
     * 检查尺寸信息是否有效
     */
    val hasDimensionInfo: Boolean
        get() = width > 0 && height > 0

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
            bucketDisplayName: String?,
            orientation: Int = 0
        ): ImageFile {
            return ImageFile(
                id = id,
                uri = Uri.parse("content://media/external/images/media/$id"),
                displayName = displayName,
                dateModified = dateModified,
                size = size,
                width = width,
                height = height,
                bucketDisplayName = bucketDisplayName,
                orientation = orientation
            )
        }
    }
}
