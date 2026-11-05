package com.tabula.v3.ui.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tabula.v3.data.model.ImageFile
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun photoMimeType(reportedType: String?, displayName: String): String {
    if (reportedType?.startsWith("image/") == true) return reportedType
    val extension = displayName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?.takeIf { it.startsWith("image/") } ?: "image/*"
}

internal fun buildPhotoShareIntent(image: ImageFile, mimeType: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, image.uri)
        clipData = ClipData.newRawUri(image.displayName, image.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

private suspend fun preparePhotoShare(context: Context, image: ImageFile): Intent =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openAssetFileDescriptor(image.uri, "r")?.use { }
            ?: throw IOException("Photo is no longer readable")
        buildPhotoShareIntent(image, photoMimeType(resolver.getType(image.uri), image.displayName))
    }

/** Snapshot the clicked photo; sharing never changes sorting or deletion state. */
@Composable
fun rememberPhotoShareAction(): (ImageFile) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPreparing by remember { mutableStateOf(false) }
    return remember(context, scope) {
        { image ->
            if (!isPreparing) {
                isPreparing = true
                scope.launch {
                    try {
                        val intent = preparePhotoShare(context, image)
                        val chooser = Intent.createChooser(intent, "分享照片")
                        if (context.findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "没有可用的照片分享应用", Toast.LENGTH_LONG).show()
                    } catch (e: SecurityException) {
                        Toast.makeText(context, "无法读取照片，请检查照片访问权限", Toast.LENGTH_LONG).show()
                    } catch (e: IOException) {
                        Toast.makeText(context, "照片已不存在或无法读取", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "未能打开分享面板，请重试", Toast.LENGTH_LONG).show()
                    } finally {
                        isPreparing = false
                    }
                }
            }
        }
    }
}
