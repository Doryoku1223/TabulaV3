package com.tabula.v3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.resolveCoverRow
import com.tabula.v3.ui.util.coverImageRequest

import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * "其他图集"页面
 *
 * 显示被收纳的小图集列表。用户可以：
 * 1. 点击图集进入图集详情
 * 2. 点击"取出"将图集固定到主图集列表
 */
@Composable
fun OtherAlbumsScreen(
    albums: List<Album>,
    allImages: List<ImageFile>,
    onAlbumClick: (Album) -> Unit,
    onPinAlbum: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current

    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val cardColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = Color(0xFF8E8E93)
    val accentColor = Color(0xFF007AFF)

    // C1/T03-COVER：多值映射——跨卷同 ID 行不折叠。
    val imageMap = androidx.compose.runtime.remember(allImages) { allImages.groupBy { it.id } }

    // 按图片数量降序排序
    val sortedAlbums = androidx.compose.runtime.remember(albums) {
        albums.sortedByDescending { it.imageCount }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // ========== 顶部栏 ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                HapticFeedback.lightTap(context)
                onNavigateBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "其他图集",
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${sortedAlbums.size} 个图集 · ${sortedAlbums.sumOf { it.imageCount }} 张照片",
                    color = secondaryTextColor,
                    fontSize = 13.sp
                )
            }
        }

        // ========== 说明 ==========
        Text(
            text = "这些图集因照片较少被自动收纳。点击可查看，点击取出可固定到主图集列表。",
            color = secondaryTextColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ========== 图集列表 ==========
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            items(sortedAlbums, key = { it.id }) { album ->
                OtherAlbumRow(
                    album = album,
                    coverImage = album.coverImageId?.let { coverId ->
                        resolveCoverRow(album.coverVolumeName, coverId, imageMap[coverId].orEmpty())
                    },
                    onClick = { onAlbumClick(album) },
                    onPin = { onPinAlbum(album.name) },
                    cardColor = cardColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    accentColor = accentColor,
                    isDarkTheme = isDarkTheme
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun OtherAlbumRow(
    album: Album,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    onPin: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    // C1/T03-COVER：封面 URI = 解析行的卷限定 URI（解析失败 → 占位）。
    val coverUri = coverImage?.uri

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面缩略图
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
        ) {
            if (coverImage != null) {
                // C1/T03-COVERKEY：请求经生产构造器（身份+内容版本缓存键）。
                AsyncImage(
                    model = coverImageRequest(
                        context, coverImage, "other_albums_cover_",
                        size = coil.size.Size(120, 120), inexact = true, rgb565 = true
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 名称和数量
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.imageCount} 张照片",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // "取出"按钮
        TextButton(
            onClick = {
                HapticFeedback.mediumTap(context)
                onPin()
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "取出",
                color = accentColor,
                fontSize = 14.sp
            )
        }

        // 右箭头
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = secondaryTextColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
