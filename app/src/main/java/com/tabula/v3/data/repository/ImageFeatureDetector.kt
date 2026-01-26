package com.tabula.v3.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.tabula.v3.data.model.ImageFeatures
import com.tabula.v3.data.model.MotionPhotoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片特性检测器（HDR / 动态照片）
 */
object ImageFeatureDetector {
    private const val TAG = "ImageFeatureDetector"

    suspend fun detect(
        context: Context,
        uri: Uri,
        checkHdr: Boolean,
        checkMotion: Boolean
    ): ImageFeatures = withContext(Dispatchers.IO) {
        val xmp = if (checkHdr || checkMotion) readXmp(context, uri) else null
        val isHdr = if (checkHdr) detectHdr(context, uri, xmp) else false
        val motionInfo = if (checkMotion) detectMotionPhoto(context, uri, xmp) else null
        ImageFeatures(isHdr = isHdr, motionPhotoInfo = motionInfo)
    }

    private fun detectHdr(context: Context, uri: Uri, xmp: String?): Boolean {
        val resolver = context.contentResolver
        queryHdrFlag(resolver, uri)?.let { flag ->
            if (flag) return true
        }

        return try {
            val source = ImageDecoder.createSource(resolver, uri)
            var hdrColorSpace = false
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                hdrColorSpace = isHdrColorSpace(info.colorSpace)
            }
            if (bitmap.hasGainmap() || hdrColorSpace) {
                true
            } else {
                xmp?.let { containsHdrXmp(it) } ?: containsHdrExifHint(context, uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HDR detect failed: ${e.message}")
            xmp?.let { containsHdrXmp(it) } ?: containsHdrExifHint(context, uri)
        }
    }

    private fun detectMotionPhoto(context: Context, uri: Uri, xmp: String?): MotionPhotoInfo? {
        return try {
            val xmpData = xmp ?: return null
            val fileLength = queryContentLength(context.contentResolver, uri) ?: return null

            // Motion Photo 1.0: use Item:Semantic="MotionPhoto" + Item:Length
            val itemLength = extractMotionPhotoLength(xmpData)
            if (itemLength != null && itemLength > 0 && itemLength < fileLength) {
                val presentationTimestampUs = extractPresentationTimestampUs(xmpData)
                return MotionPhotoInfo(
                    videoStart = fileLength - itemLength,
                    videoLength = itemLength,
                    presentationTimestampUs = presentationTimestampUs
                )
            }

            val microVideoOffset = extractMicroVideoOffset(xmpData)
            if (microVideoOffset != null && microVideoOffset > 0 && microVideoOffset < fileLength) {
                val presentationTimestampUs = extractPresentationTimestampUs(xmpData)
                return MotionPhotoInfo(
                    videoStart = fileLength - microVideoOffset,
                    videoLength = microVideoOffset,
                    presentationTimestampUs = presentationTimestampUs
                )
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Motion photo detect failed: ${e.message}")
            null
        }
    }

    private fun readXmp(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttribute(ExifInterface.TAG_XMP)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractMotionPhotoLength(xmp: String): Long? {
        val liPattern = Regex("<rdf:li[^>]*>", RegexOption.IGNORE_CASE)
        for (match in liPattern.findAll(xmp)) {
            val tag = match.value
            val semantic = extractAttribute(tag, "Item:Semantic")
            val mime = extractAttribute(tag, "Item:Mime")
            val length = extractAttribute(tag, "Item:Length")?.toLongOrNull()
            if (length != null && length > 0) {
                val isMotion = semantic != null && (
                    semantic.equals("MotionPhoto", ignoreCase = true) ||
                        semantic.equals("MotionPhotoVideo", ignoreCase = true)
                    )
                val isVideo = mime?.contains("video", ignoreCase = true) == true
                if (isMotion || isVideo) {
                    return length
                }
            }
        }
        return null
    }

    private fun extractMicroVideoOffset(xmp: String): Long? {
        return extractAttribute(xmp, "GCamera:MicroVideoOffset")?.toLongOrNull()
            ?: extractAttribute(xmp, "Camera:MicroVideoOffset")?.toLongOrNull()
    }

    
    private fun isHdrColorSpace(colorSpace: ColorSpace?): Boolean {
        return colorSpace == ColorSpace.get(ColorSpace.Named.BT2020_PQ) ||
            colorSpace == ColorSpace.get(ColorSpace.Named.BT2020_HLG)
    }

    private fun queryHdrFlag(resolver: ContentResolver, uri: Uri): Boolean? {
        val columns = arrayOf("is_hdr", "is_hdr_image", "is_hdr_content", "hdr_type", "color_transfer")

        fun queryAndCheck(targetUri: Uri, selection: String? = null, selectionArgs: Array<String>? = null): Boolean? {
            return try {
                resolver.query(targetUri, columns, selection, selectionArgs, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    for (column in columns) {
                        val index = cursor.getColumnIndex(column)
                        if (index >= 0) {
                            val value = cursor.getInt(index)
                            if (column == "color_transfer") {
                                if (value >= 6) return@use true
                            } else if (value > 0) {
                                return@use true
                            }
                        }
                    }
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        val directResult = queryAndCheck(uri)
        if (directResult != null) return directResult

        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return queryAndCheck(mediaUri, "${MediaStore.Images.Media._ID}=?", arrayOf(id.toString()))
    }
    private fun containsHdrExifHint(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.lowercase()
                val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.lowercase()
                val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.lowercase()
                val hint = listOf(software, description, userComment).any { value ->
                    value?.contains("hdr") == true ||
                        value?.contains("ultra hdr") == true ||
                        value?.contains("pro xdr") == true ||
                        value?.contains("proxdr") == true ||
                        value?.contains("hdr10") == true
                }
                if (hint) return@use true

                val makerNote = exif.getAttribute(ExifInterface.TAG_MAKER_NOTE)
                if (!makerNote.isNullOrBlank() && containsHdrMakerNoteHint(makerNote)) {
                    return@use true
                }

                false
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun containsHdrMakerNoteHint(makerNote: String): Boolean {
        val candidates = mutableListOf<String>()
        candidates.add(makerNote)

        val trimmed = makerNote.trim()
        val base64Payload = trimmed.removePrefix("base64:").removePrefix("BASE64:")
        if (trimmed.startsWith("base64:", ignoreCase = true)) {
            decodeBase64ToString(base64Payload)?.let { candidates.add(it) }
        } else if (looksBase64(base64Payload)) {
            decodeBase64ToString(base64Payload)?.let { candidates.add(it) }
        }

        for (text in candidates) {
            val hdrStat = extractJsonValue(text, "hdrStat")?.toIntOrNull()
            if (hdrStat != null && hdrStat > 0) return true
            val hdrFusion = extractJsonValue(text, "hdrFusion")?.toIntOrNull()
            if (hdrFusion != null && hdrFusion > 0) return true
            val hdr = extractJsonValue(text, "hdr")?.toIntOrNull()
            if (hdr != null && hdr > 0) return true
            if (text.contains("\"hdr\"", ignoreCase = true) || text.contains("hdrstat", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun extractJsonValue(text: String, key: String): String? {
        val pattern = Regex(
            "\"" + Regex.escape(key) + "\"\\s*:\\s*\"?([^\",}\\]]+)",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(text) ?: return null
        return match.groups[1]?.value
    }

    private fun looksBase64(value: String): Boolean {
        val cleaned = value.trim().replace("\\s".toRegex(), "")
        if (cleaned.length < 16 || cleaned.length % 4 != 0) return false
        val base64Chars = cleaned.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        return base64Chars.toFloat() / cleaned.length.toFloat() > 0.95f
    }

    private fun decodeBase64ToString(value: String): String? {
        return try {
            val decoded = Base64.decode(value, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPresentationTimestampUs(xmp: String): Long? {
        return extractAttribute(xmp, "Camera:MotionPhotoPresentationTimestampUs")?.toLongOrNull()
            ?: extractAttribute(xmp, "GCamera:MicroVideoPresentationTimestampUs")?.toLongOrNull()
    }

    private fun containsHdrXmp(xmp: String): Boolean {
        val lower = xmp.lowercase()
        return lower.contains("hdrgm:") ||
            lower.contains("hdr-gainmap") ||
            lower.contains("gainmap") ||
            lower.contains("ultrahdr") ||
            lower.contains("proxdr") ||
            lower.contains("pro xdr") ||
            lower.contains("pro-xdr")
    }

    private fun extractAttribute(tag: String, name: String): String? {
        val pattern = Regex(
            "$name\\s*=\\s*\"([^\"]+)\"|$name\\s*=\\s*'([^']+)'",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(tag) ?: return null
        return match.groups[1]?.value ?: match.groups[2]?.value
    }

    private fun queryContentLength(resolver: ContentResolver, uri: Uri): Long? {
        try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) return afd.length
            }
        } catch (_: Exception) {
            // ignore
        }

        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            if (sizeColumn >= 0 && cursor.moveToFirst()) {
                val size = cursor.getLong(sizeColumn)
                if (size > 0) return size
            }
        }

        return null
    }

}
