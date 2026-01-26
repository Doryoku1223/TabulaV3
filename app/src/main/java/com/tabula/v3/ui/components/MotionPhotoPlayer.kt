package com.tabula.v3.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tabula.v3.data.model.MotionPhotoInfo
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.min

@Composable
fun MotionPhotoPlayer(
    imageUri: Uri,
    motionInfo: MotionPhotoInfo,
    modifier: Modifier = Modifier,
    playWhen: Boolean = false,
    playAudio: Boolean = true,
    volumePercent: Int = 100
) {
    val context = LocalContext.current
    var surface by remember { mutableStateOf<Surface?>(null) }
    val mediaPlayer = remember { MediaPlayer() }
    var dataSource by remember { mutableStateOf<MotionPhotoDataSource?>(null) }
    var isPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(surface, imageUri, motionInfo) {
        val targetSurface = surface ?: return@LaunchedEffect

        mediaPlayer.reset()
        isPrepared = false
        dataSource?.close()
        dataSource = MotionPhotoDataSource(
            context = context,
            uri = imageUri,
            videoStart = motionInfo.videoStart,
            videoLength = motionInfo.videoLength
        )
        val source = dataSource
        if (source == null || !source.isValid) {
            return@LaunchedEffect
        }
        mediaPlayer.setDataSource(source)
        mediaPlayer.setSurface(targetSurface)
        mediaPlayer.isLooping = true
        val volume = if (playAudio) (volumePercent.coerceIn(0, 100) / 100f) else 0f
        mediaPlayer.setVolume(volume, volume)
        mediaPlayer.setOnPreparedListener { player ->
            isPrepared = true
            if (playWhen) {
                player.start()
            }
        }
        mediaPlayer.setOnErrorListener { _, _, _ -> true }
        mediaPlayer.prepareAsync()
    }

    LaunchedEffect(playWhen, isPrepared) {
        if (!isPrepared) return@LaunchedEffect
        if (playWhen) {
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.start()
            }
        } else {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
            mediaPlayer.seekTo(0)
        }
    }

    LaunchedEffect(playAudio, volumePercent, isPrepared) {
        if (!isPrepared) return@LaunchedEffect
        val volume = if (playAudio) (volumePercent.coerceIn(0, 100) / 100f) else 0f
        mediaPlayer.setVolume(volume, volume)
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.reset()
            isPrepared = false
            mediaPlayer.release()
            dataSource?.close()
            dataSource = null
            surface?.release()
            surface = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        surface = Surface(surfaceTexture)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        }
    )
}

private class MotionPhotoDataSource(
    context: Context,
    uri: Uri,
    private val videoStart: Long,
    private val videoLength: Long
) : MediaDataSource() {
    private val parcelFd = context.contentResolver.openFileDescriptor(uri, "r")
    private val channel = parcelFd?.let { FileInputStream(it.fileDescriptor).channel }
    val isValid: Boolean = channel != null

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= videoLength) return -1
        val available = min(size.toLong(), videoLength - position).toInt()
        val byteBuffer = ByteBuffer.wrap(buffer, offset, available)
        val actualPos = videoStart + position
        val read = channel?.read(byteBuffer, actualPos) ?: -1
        return read
    }

    override fun getSize(): Long = videoLength

    override fun close() {
        channel?.close()
        parcelFd?.close()
    }
}
