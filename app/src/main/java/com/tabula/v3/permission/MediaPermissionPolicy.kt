package com.tabula.v3.permission

import android.Manifest

object MediaPermissionPolicy {

    /**
     * C4/T07：媒体访问级别。FULL=完整读取；PARTIAL=仅用户选中的照片
     * （API 34+ READ_MEDIA_VISUAL_USER_SELECTED）；NONE=无访问。
     * API 33- 无部分授权语义，二值为 FULL/NONE。
     */
    enum class MediaAccessLevel { NONE, PARTIAL, FULL }

    fun requiredPermissionsForSdk(sdkInt: Int): Array<String> {
        return when {
            sdkInt >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            sdkInt >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * C4/T07：当前访问级别。FULL 优先于 PARTIAL（两权限都在时以完整读取为
     * 准）；与 [hasMediaReadPermission] 的门控语义一致（NONE 等价 false）。
     */
    fun mediaAccessLevel(
        sdkInt: Int,
        isGranted: (permission: String) -> Boolean
    ): MediaAccessLevel {
        return when {
            sdkInt >= 34 -> when {
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccessLevel.FULL
                isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccessLevel.PARTIAL
                else -> MediaAccessLevel.NONE
            }
            sdkInt >= 33 -> if (isGranted(Manifest.permission.READ_MEDIA_IMAGES)) MediaAccessLevel.FULL else MediaAccessLevel.NONE
            else -> if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccessLevel.FULL else MediaAccessLevel.NONE
        }
    }

    fun hasMediaReadPermission(
        sdkInt: Int,
        isGranted: (permission: String) -> Boolean
    ): Boolean {
        return mediaAccessLevel(sdkInt, isGranted) != MediaAccessLevel.NONE
    }

    fun isAnyRequestedPermissionGranted(result: Map<String, Boolean>): Boolean {
        return result.values.any { granted -> granted }
    }
}
