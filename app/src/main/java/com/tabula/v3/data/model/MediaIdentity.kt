package com.tabula.v3.data.model

import android.net.Uri

/**
 * T03 媒体身份契约：一个媒体项由 (存储卷名, MediaStore _ID) 唯一标识。
 *
 * - 聚合 URI 的卷名 "external" 不是身份，[mediaItemKeyFromUri] 对其返回 null。
 * - 别名 URI（documents provider、canonicalize 产物）无法从字符串解析出具体卷，
 *   同样返回 null；调用方应先经 MediaStore.getMediaUri / canonicalize 归一。
 * - 内容版本不在本契约内：调用方持有 MediaStore DATE_MODIFIED（毫秒）作为内容版本。
 *
 * T01 副本证据账本以源 URI 字符串为键：账本条目可用 [mediaItemKeyFromUri]
 * 解析出身份用于核验，账本存储格式不受本契约影响。
 */
data class MediaItemKey(
    val volumeName: String,
    val id: Long
)

/**
 * 从 MediaStore 媒体 URI 解析身份。支持：
 * - content://media/<volume>/images/media/<id>
 * - content://media/<volume>/files/<id>
 * 其余（documents provider、聚合卷 "external"、缺段/非数字 id）返回 null。
 */
fun mediaItemKeyFromUri(uri: Uri): MediaItemKey? {
    if (uri.authority != "media") return null
    val segments = uri.pathSegments
    if (segments.size < 3) return null
    val volumeName = segments[0]
    if (volumeName.isBlank() || volumeName.equals("external", ignoreCase = true)) return null
    val id = when (segments[1]) {
        "images" -> {
            if (segments.size != 4 || segments[2] != "media") return null
            segments[3]
        }
        "files" -> {
            if (segments.size != 3) return null
            segments[2]
        }
        else -> return null
    }
    val parsedId = id.toLongOrNull() ?: return null
    if (parsedId < 0L) return null
    return MediaItemKey(volumeName, parsedId)
}
