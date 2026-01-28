package com.tabula.v3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.LocalImageRepository
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 分类相册网格
 * 
 * 显示两个分类：
 * 1. App图集 - 软件内创建的相册
 * 2. 手机相册 - 系统自带的相册（来自不同文件夹）
 */
@Composable
fun CategorizedAlbumsView(
    appAlbums: List<Album>?,
    systemBuckets: List<LocalImageRepository.SystemBucket>?,
    allImages: List<ImageFile>,
    onAppAlbumClick: (Album) -> Unit,
    onSystemBucketClick: (String) -> Unit,
    onReorderAlbums: (List<String>) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    hideHeaders: Boolean = false, // 是否隐藏分节标题
    listState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 100.dp, // 顶部内边距
    headerContent: (@Composable () -> Unit)? = null,
    userScrollEnabled: Boolean = true
) {
    val context = LocalContext.current
    val imageMap = remember(allImages) { allImages.associateBy { it.id } }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = 100.dp),
        userScrollEnabled = userScrollEnabled
    ) {
        if (headerContent != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    headerContent()
                }
            }
        }
        // App 图集区域 (仅当 appAlbums 不为 null 时显示)
        if (appAlbums != null) {
            if (!hideHeaders) {
                item {
                    SectionHeader(
                        title = "App 图集",
                        subtitle = "${appAlbums.size} 个",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }

            if (appAlbums.isEmpty()) {
                item {
                    EmptyAlbumHint(
                        text = "还没有创建图集\n在滑一滑界面归类照片即可创建",
                        textColor = secondaryTextColor
                    )
                }
            } else if (hideHeaders) {
                // Tabbed视图下使用网格布局（一行两个）
                val chunkedAlbums = appAlbums.chunked(2)
                items(chunkedAlbums.size) { rowIndex ->
                    val rowAlbums = chunkedAlbums[rowIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowAlbums.forEach { album ->
                            val coverImage = album.coverImageId?.let { imageMap[it] }
                            AppAlbumGridCard(
                                album = album,
                                coverImage = coverImage,
                                onClick = { onAppAlbumClick(album) },
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                isDarkTheme = isDarkTheme,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 如果只有一个元素，添加空占位
                        if (rowAlbums.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                item {
                    // 横向滚动的App图集卡片
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(appAlbums, key = { it.id }) { album ->
                            val coverImage = album.coverImageId?.let { imageMap[it] }
                            AppAlbumCard(
                                album = album,
                                coverImage = coverImage,
                                onClick = { onAppAlbumClick(album) },
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            }

            // 分隔 (如果下方还有内容)
            if (systemBuckets != null) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // 手机相册区域 (仅当 systemBuckets 不为 null 时显示)
        if (systemBuckets != null) {
            if (!hideHeaders) {
                item {
                    SectionHeader(
                        title = "手机相册",
                        subtitle = "${systemBuckets.size} 个",
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }

            if (systemBuckets.isEmpty()) {
                item {
                    EmptyAlbumHint(
                        text = "没有找到手机相册",
                        textColor = secondaryTextColor
                    )
                }
            } else {
                items(systemBuckets, key = { it.name }) { bucket ->
                    val coverImage = bucket.coverImageId?.let { imageMap[it] }
                    SystemBucketRow(
                        bucket = bucket,
                        coverImage = coverImage,
                        onClick = { onSystemBucketClick(bucket.name) },
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }

        // 底部留白
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 分区标题
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = secondaryTextColor,
            fontSize = 14.sp
        )
    }
}

/**
 * 空状态提示
 */
@Composable
private fun EmptyAlbumHint(
    text: String,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * App图集卡片（横向滚动版）
 */
@Composable
private fun AppAlbumCard(
    album: Album,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
        ) {
            if (coverImage != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverImage.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val albumColor = album.color?.let { Color(it) } ?: Color(0xFF7986CB)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(albumColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = album.emoji ?: "📁",
                        fontSize = 32.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 名称
        Text(
            text = album.name,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 数量
        Text(
            text = "${album.imageCount} 张",
            color = secondaryTextColor,
            fontSize = 12.sp
        )
    }
}

/**
 * App图集卡片（网格布局版，一行两个）
 */
@Composable
private fun AppAlbumGridCard(
    album: Album,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
    ) {
        // 封面 - 使用宽度自适应，保持正方形比例
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color.White)
        ) {
            if (coverImage != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverImage.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val albumColor = album.color?.let { Color(it) } ?: Color(0xFF7986CB)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(albumColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = album.emoji ?: "📁",
                        fontSize = 40.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 名称
        Text(
            text = album.name,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // 数量
        Text(
            text = "${album.imageCount} 张",
            color = secondaryTextColor,
            fontSize = 13.sp
        )
    }
}

/**
 * 系统相册行（列表样式）
 */
@Composable
private fun SystemBucketRow(
    bucket: LocalImageRepository.SystemBucket,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                HapticFeedback.lightTap(context)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩略图
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
        ) {
            if (coverImage != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverImage.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📷", fontSize = 24.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 名称和数量
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bucket.name,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${bucket.imageCount} 张",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // 箭头
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = secondaryTextColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
