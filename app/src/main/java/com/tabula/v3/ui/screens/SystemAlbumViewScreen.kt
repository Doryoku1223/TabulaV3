package com.tabula.v3.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.ViewerOverlay
import com.tabula.v3.ui.components.ViewerState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler

/**
 * 系统相册详情页
 */
@Composable
fun SystemAlbumViewScreen(
    albumName: String,
    images: List<ImageFile>,
    onNavigateBack: () -> Unit,
    showHdrBadges: Boolean = false,
    showMotionBadges: Boolean = false,
    playMotionSound: Boolean = false,
    motionSoundVolume: Int = 100
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    var viewerState by remember { mutableStateOf<ViewerState?>(null) }
    val gridState = rememberLazyGridState()

    // 返回拦截
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = textColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = albumName,
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${images.size} 张照片",
                        color = secondaryTextColor,
                        fontSize = 13.sp
                    )
                }
            }

            // 照片网格
            val isScrolling = gridState.isScrollInProgress
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                items(images, key = { it.id }) { image ->
                    PhotoGridItem(
                        image = image,
                        showHdrBadge = showHdrBadges,
                        showMotionBadge = showMotionBadges,
                        isScrolling = isScrolling,
                        onClick = { sourceRect ->
                            viewerState = ViewerState(image, sourceRect)
                        }
                    )
                }
            }
        }

        // 查看器
        viewerState?.let { state ->
            ViewerOverlay(
                viewerState = state,
                onDismiss = { viewerState = null },
                showHdr = showHdrBadges,
                showMotionPhoto = showMotionBadges,
                playMotionSound = playMotionSound,
                motionSoundVolume = motionSoundVolume
            )
        }
    }
}

/**
 * 简单的照片网格项（复用）
 * 包含 HDR/Live 标识
 */
@Composable
private fun PhotoGridItem(
    image: ImageFile,
    showHdrBadge: Boolean,
    showMotionBadge: Boolean,
    isScrolling: Boolean,
    onClick: (SourceRect) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember { CoilSetup.getImageLoader(context) }
    val coordinatesHolder = remember { SystemLayoutCoordinatesHolder() }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .onGloballyPositioned { coordinates ->
                coordinatesHolder.value = coordinates
            }
            .clickable {
                HapticFeedback.lightTap(context)
                val rect = coordinatesHolder.value?.takeIf { it.isAttached }?.boundsInRoot()
                val sourceRect = if (rect != null) {
                    SourceRect(
                        x = rect.left,
                        y = rect.top,
                        width = rect.width,
                        height = rect.height,
                        cornerRadius = 4f  // 与 RoundedCornerShape 一致
                    )
                } else {
                    SourceRect()
                }
                onClick(sourceRect)
            }
    ) {
        if (isScrolling) {
            // 快速滚动时不加载图片，避免解码风暴导致卡顿/ANR
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f))
            )
        } else {
            // 使用稳定的缓存键，基于图片 ID
            val cacheKey = remember(image.id) { "sys_grid_${image.id}" }
            
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.uri)
                    .size(Size(240, 240))  // 缩略图只需要小尺寸，大幅减少解码压力
                    .precision(Precision.INEXACT)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .allowHardware(false)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build(),
                contentDescription = image.displayName,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // HDR / Live 标识
            val badges = rememberImageBadges(
                image = image, 
                showHdr = showHdrBadge, 
                showMotion = showMotionBadge
            )
            
            if (badges.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                ) {
                    badges.forEach { badge ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private class SystemLayoutCoordinatesHolder(var value: LayoutCoordinates? = null)

// 辅助函数：根据文件名判断徽章
@Composable
private fun rememberImageBadges(
    image: ImageFile,
    showHdr: Boolean,
    showMotion: Boolean
): List<String> {
    val badges = mutableListOf<String>()
    
    val name = image.displayName.lowercase()
    if (showHdr && (name.contains("hdr") || name.contains("_hdr"))) {
        badges.add("HDR")
    }
    if (showMotion && (name.contains("mvimg") || name.contains("motion") || name.contains("live"))) {
        badges.add("Live")
    }
    
    return badges
}
