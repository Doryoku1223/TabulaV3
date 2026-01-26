package com.tabula.v3.data.model

/**
 * 图片媒体特性
 */
data class ImageFeatures(
    val isHdr: Boolean,
    val motionPhotoInfo: MotionPhotoInfo?
) {
    val isMotionPhoto: Boolean
        get() = motionPhotoInfo != null
}

/**
 * 动态照片视频片段信息
 *
 * @param videoStart 视频片段在文件中的起始字节位置
 * @param videoLength 视频片段字节长度
 * @param presentationTimestampUs 播放时间戳(可选)
 */
data class MotionPhotoInfo(
    val videoStart: Long,
    val videoLength: Long,
    val presentationTimestampUs: Long? = null
)
