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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.di.CoilSetup
import com.tabula.v3.ui.components.SourceRect
import com.tabula.v3.ui.components.SwipeableViewerOverlay
import com.tabula.v3.ui.components.SwipeableViewerState
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback
import com.tabula.v3.ui.util.gridThumbnailCacheKey
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

    var viewerState by remember { mutableStateOf<SwipeableViewerState?>(null) }
    val gridState = rememberLazyGridState()

    // 返回拦截
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
        // 注意：statusBarsPadding 移到 Column 内部，确保 ViewerOverlay 的坐标系与 boundsInRoot() 一致
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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

            // 获取导航栏高度，实现沉浸式效果
            val navBarHeight = WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
            
            // 照片网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 4.dp,
                    end = 4.dp,
                    top = 4.dp,
                    bottom = 4.dp + navBarHeight  // 底部留出导航栏空间
                ),
                state = gridState,
                modifier = Modifier.weight(1f)
            ) {
                // C1/T03-THUMBKEY：列表 key 用完整媒体身份键——bucket 视图
                // 跨卷同数值 ID 两行共存时不再重复 key 崩溃（与图集网格/
                // 回收站网格同修法；Compose 重组行为无单测基建，见 PR 范围声明）。
                items(images, key = { it.mediaIdentityKey() }) { image ->
                    PhotoGridItem(
                        image = image,
                        showHdrBadge = showHdrBadges,
                        showMotionBadge = showMotionBadges,
                        onClick = { sourceRect ->
                            val index = images.indexOf(image).coerceAtLeast(0)
                            viewerState = SwipeableViewerState(
                                images = images,
                                initialIndex = index,
                                sourceRect = sourceRect
                            )
                        }
                    )
                }
            }
        }

        // 查看器 - 支持左右滑动切换图片
        viewerState?.let { state ->
            SwipeableViewerOverlay(
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
    onClick: (SourceRect) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember { CoilSetup.getImageLoader(context) }
    val coordinatesHolder = remember { SystemLayoutCoordinatesHolder() }

    // 占位符背景色 - 在图片加载前显示，避免空白闪烁
    val isDarkTheme = com.tabula.v3.ui.theme.LocalIsDarkTheme.current
    val placeholderColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else androidx.compose.ui.graphics.Color(0xFFE5E5EA)

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
        // 缩略图缓存键：完整媒体身份 + 内容版本（C1/T03-THUMBKEY）。
        // 两网格与查看器共用 grid_ 命名空间（请求规格一致），查看器首开
        // 直接温命中网格缩略图。
        val cacheKey = remember(image.id, image.dateModified) { gridThumbnailCacheKey("grid_", image) }
        
        // 先绘制占位符背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(placeholderColor)
        )
        
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(image.uri)
                .size(Size(240, 240))  // 缩略图只需要小尺寸，大幅减少解码压力
                .precision(Precision.INEXACT)
                .bitmapConfig(Bitmap.Config.RGB_565)
                // 启用硬件位图加速（Android 8.0+）- 更快的渲染
                .allowHardware(true)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                // 禁用 crossfade 动画 - 直接显示，减少视觉延迟
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

private class SystemLayoutCoordinatesHolder(var value: LayoutCoordinates? = null)

/**
 * 记住照片网格项的标识
 * 使用 EXIF/XMP 数据进行真正的 HDR/Motion 检测
 */
@Composable
private fun rememberImageBadges(
    image: ImageFile,
    showHdr: Boolean,
    showMotion: Boolean
): List<String> {
    val features = com.tabula.v3.ui.util.rememberImageFeatures(
        image = image,
        enableHdr = showHdr,
        enableMotion = showMotion
    )
    
    val badges = mutableListOf<String>()
    if (showHdr && features?.isHdr == true) {
        badges.add("HDR")
    }
    if (showMotion && features?.isMotionPhoto == true) {
        badges.add("Live")
    }
    
    return badges
}
