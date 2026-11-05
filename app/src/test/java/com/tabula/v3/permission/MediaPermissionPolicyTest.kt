package com.tabula.v3.permission

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionPolicyTest {

    @Test
    fun requiredPermissions_sdk34_includesPartialAccessPermission() {
        val permissions = MediaPermissionPolicy.requiredPermissionsForSdk(34)
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ),
            permissions
        )
    }

    @Test
    fun hasMediaReadPermission_sdk34_trueWhenOnlySelectedPhotosGranted() {
        val result = MediaPermissionPolicy.hasMediaReadPermission(34) { permission ->
            permission == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        }

        assertTrue(result)
    }

    @Test
    fun hasMediaReadPermission_sdk33_requiresReadMediaImages() {
        val granted = MediaPermissionPolicy.hasMediaReadPermission(33) { permission ->
            permission == Manifest.permission.READ_MEDIA_IMAGES
        }
        val denied = MediaPermissionPolicy.hasMediaReadPermission(33) { _ -> false }

        assertTrue(granted)
        assertFalse(denied)
    }

    @Test
    fun isAnyRequestedPermissionGranted_returnsTrueWhenAtLeastOneGranted() {
        val result = MediaPermissionPolicy.isAnyRequestedPermissionGranted(
            mapOf(
                Manifest.permission.READ_MEDIA_IMAGES to false,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to true
            )
        )

        assertTrue(result)
    }

    @Test
    fun mediaAccessLevel_sdk34_distinguishesFullPartialNone() {
        // C4/T07：API 34+ 三态——完整读取优先于仅选中照片，两者皆无为 NONE。
        fun level(imagesGranted: Boolean, selectedGranted: Boolean) =
            MediaPermissionPolicy.mediaAccessLevel(34) { permission ->
                when (permission) {
                    Manifest.permission.READ_MEDIA_IMAGES -> imagesGranted
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED -> selectedGranted
                    else -> false
                }
            }

        assertEquals(MediaPermissionPolicy.MediaAccessLevel.FULL, level(true, false))
        assertEquals(MediaPermissionPolicy.MediaAccessLevel.FULL, level(true, true))
        assertEquals(MediaPermissionPolicy.MediaAccessLevel.PARTIAL, level(false, true))
        assertEquals(MediaPermissionPolicy.MediaAccessLevel.NONE, level(false, false))
    }

    @Test
    fun mediaAccessLevel_sdk33AndBelowAreBinaryFullOrNone() {
        // API 33- 无部分授权语义：二值 FULL/NONE。
        assertEquals(
            MediaPermissionPolicy.MediaAccessLevel.FULL,
            MediaPermissionPolicy.mediaAccessLevel(33) { it == Manifest.permission.READ_MEDIA_IMAGES }
        )
        assertEquals(
            MediaPermissionPolicy.MediaAccessLevel.NONE,
            MediaPermissionPolicy.mediaAccessLevel(33) { _ -> false }
        )
        assertEquals(
            MediaPermissionPolicy.MediaAccessLevel.FULL,
            MediaPermissionPolicy.mediaAccessLevel(29) { it == Manifest.permission.READ_EXTERNAL_STORAGE }
        )
        assertEquals(
            MediaPermissionPolicy.MediaAccessLevel.NONE,
            MediaPermissionPolicy.mediaAccessLevel(29) { _ -> false }
        )
    }

    @Test
    fun hasMediaReadPermissionAgreesWithAccessLevel() {
        // 门控语义保持：hasMediaReadPermission == (级别 != NONE)，部分授权仍过门控。
        fun granted(level: MediaPermissionPolicy.MediaAccessLevel) =
            MediaPermissionPolicy.hasMediaReadPermission(34) { permission ->
                when (level) {
                    MediaPermissionPolicy.MediaAccessLevel.FULL ->
                        permission == Manifest.permission.READ_MEDIA_IMAGES
                    MediaPermissionPolicy.MediaAccessLevel.PARTIAL ->
                        permission == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    MediaPermissionPolicy.MediaAccessLevel.NONE -> false
                }
            }

        assertTrue(granted(MediaPermissionPolicy.MediaAccessLevel.FULL))
        assertTrue(granted(MediaPermissionPolicy.MediaAccessLevel.PARTIAL))
        assertFalse(granted(MediaPermissionPolicy.MediaAccessLevel.NONE))
    }
}
