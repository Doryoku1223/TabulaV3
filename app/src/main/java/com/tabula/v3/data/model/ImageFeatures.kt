package com.tabula.v3.data.model

/**
 * 图片媒体特性
 *
 * @param definitive 结果是否可信。阳性（HDR/动态）恒可信；阴性仅在
 * 未被快速模式门槛、检测信号量繁忙或异常打断时才可信（T11a）。
 * 不可信阴性不得写入任何持久缓存。
 */
data class ImageFeatures(
    val isHdr: Boolean,
    val motionPhotoInfo: MotionPhotoInfo?,
    val definitive: Boolean = true
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
