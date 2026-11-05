package com.tabula.v3.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.size.Precision
import coil.size.Size
import com.tabula.v3.R
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.data.repository.resolveCoverRow
import com.tabula.v3.ui.util.gridThumbnailCacheKey

import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 图集选择弹层
 *
 * 使用 ModalBottomSheet 实现，支持下拉关闭。
 * 显示竖向滚动的图集列表，每个图集显示：
 * - 缩略图（封面）
 * - 图集名称
 * - 图集路径（用于区分同名图集）
 * - 图片数量
 *
 * @param albums 图集列表
 * @param allImages 所有图片（用于获取封面）
 * @param onAlbumSelected 选中图集回调
 * @param onCreateNewAlbum 新建图集回调
 * @param onDismiss 关闭弹层回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSelectionSheet(
    albums: List<Album>,
    allImages: List<ImageFile>,
    onAlbumSelected: (Album) -> Unit,
    onCreateNewAlbum: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // 颜色配置
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val dividerColor = if (isDarkTheme) Color(0xFF38383A) else Color(0xFFE5E5EA)
    
    // 图片映射
    // C1/T03-COVER：多值映射——跨卷同 ID 行不折叠。
    val imageMap = remember(allImages) { allImages.groupBy { it.id } }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = backgroundColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.3f)
                        else Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // 标题
            Text(
                text = "选择相册",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
            
            // 图集列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(bottom = 16.dp)
            ) {
                // 第一项：新建图集
                item(key = "create_new") {
                    CreateNewAlbumRow(
                        onClick = {
                            HapticFeedback.lightTap(context)
                            onCreateNewAlbum()
                        },
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        isDarkTheme = isDarkTheme
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 80.dp)
                    )
                }
                
                // 图集列表
                items(albums, key = { it.id }) { album ->
                    val coverImage = album.coverImageId?.let { coverId ->
                        resolveCoverRow(album.coverVolumeName, coverId, imageMap[coverId].orEmpty())
                    }
                    
                    AlbumSelectionRow(
                        album = album,
                        coverImage = coverImage,
                        onClick = {
                            HapticFeedback.lightTap(context)
                            onAlbumSelected(album)
                        },
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        isDarkTheme = isDarkTheme
                    )
                    
                    // 分隔线（最后一个不显示）
                    if (album != albums.lastOrNull()) {
                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 80.dp)
                        )
                    }
                }
                
                // 底部留白
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

/**
 * 新建图集行
 */
@Composable
private fun CreateNewAlbumRow(
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val iconBgColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    val iconColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF636366)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标区域
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "新建",
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 文字区域
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "新建图集",
                color = textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "创建新的图集来归类照片",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 图集选择行
 *
 * 显示：缩略图 | 名称+路径 | 数量
 */
@Composable
private fun AlbumSelectionRow(
    album: Album,
    coverImage: ImageFile?,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    
    // C1/T03-COVER：封面 URI = 解析行的卷限定 URI；解析失败 → null 占位
    // （聚合 URI fallback 为跨卷串图向量，已移除）。
    val coverUri = coverImage?.uri
    
    // 图片加载失败状态
    var loadFailed by remember { mutableStateOf(false) }
    
    // 计算显示的路径
    // 如果有 systemAlbumPath，显示路径（不含图集名）
    // 否则显示 "App 图集"
    val displayPath = album.systemAlbumPath?.let { path ->
        // 路径格式如：/storage/emulated/0/Pictures/AlbumName/
        // 需要移除末尾的图集名，只显示父路径
        val trimmed = path.trimEnd('/')
        val parentPath = trimmed.substringBeforeLast('/', "")
        if (parentPath.isNotEmpty()) "$parentPath/" else path
    } ?: "App 图集"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩略图
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
        ) {
            if (coverUri != null && !loadFailed) {
                // C1/T03-COVER：键 = 封面行身份 + 内容版本。
                val cacheKey = remember(coverImage?.mediaIdentityKey(), coverImage?.dateModified) {
                    gridThumbnailCacheKey("album_select_", coverImage)
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUri)
                        .size(Size(120, 120))
                        .precision(Precision.INEXACT)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { loadFailed = true }
                )
            } else {
                // 无封面或加载失败，显示默认图片
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.zpcat1)
                        .crossfade(80)
                        .build(),
                    contentDescription = "空图集封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 名称和路径
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = album.name,
                color = textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = displayPath,
                color = secondaryTextColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 图片数量
        Text(
            text = "${album.imageCount}",
            color = secondaryTextColor,
            fontSize = 15.sp
        )
    }
}
