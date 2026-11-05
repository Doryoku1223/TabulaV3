package com.tabula.v3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.tabula.v3.data.repository.AlbumCleanupEngine
import com.tabula.v3.data.repository.AlbumCleanupInfo
import com.tabula.v3.data.repository.resolveCoverRow
import com.tabula.v3.ui.util.gridThumbnailCacheKey
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 清理模式枚举
 */
enum class CleanupMode {
    GLOBAL, // 全局整理
    ALBUM,  // 图集专清
    MONTH   // 月份专清
}

/**
 * 月份信息数据类
 */
data class MonthInfo(
    val year: Int,
    val month: Int,  // 1-12
    val imageCount: Int,
    val coverImageId: Long?,
    // C1/T03-COVER：封面行所属卷（封面完整媒体身份 = 卷+ID）。
    val coverVolumeName: String?,
    val images: List<ImageFile>
) {
    val id: String get() = "$year-$month"
    
    /** 用于清理引擎的虚拟 Album ID（以 month_ 前缀区分） */
    val cleanupAlbumId: String get() = "month_${year}_${month}"
    
    val displayName: String get() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return if (year == currentYear) {
            "${month}月"
        } else {
            "${year}年${month}月"
        }
    }
    
    /**
     * 创建用于清理引擎的虚拟 Album 对象
     */
    fun toVirtualAlbum(): Album {
        return Album(
            id = cleanupAlbumId,
            name = displayName,
            coverImageId = coverImageId,
            coverVolumeName = coverVolumeName,
            imageCount = imageCount
        )
    }
}

fun buildMonthInfos(allImages: List<ImageFile>): List<MonthInfo> {
    if (allImages.isEmpty()) return emptyList()

    val calendar = Calendar.getInstance()
    return allImages.groupBy { image ->
        calendar.timeInMillis = image.dateModified
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1  // Calendar.MONTH 是 0-11
        "$year-$month"
    }.map { (key, images) ->
        val parts = key.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        // 按时间倒序排列图片，取最新的作为封面
        val sortedImages = images.sortedByDescending { it.dateModified }
        MonthInfo(
            year = year,
            month = month,
            imageCount = images.size,
            coverImageId = sortedImages.firstOrNull()?.id,
            coverVolumeName = sortedImages.firstOrNull()?.volumeName,
            images = sortedImages
        )
    }.sortedWith(compareByDescending<MonthInfo> { it.year }.thenByDescending { it.month })
}

/**
 * 图集清理选择底部弹窗
 * 
 * 用于选择要进行清理的图集或月份，显示：
 * - 顶部"切换到全局整理"按钮
 * - 模式切换（图集专清 / 月份专清）
 * - 图集列表或月份列表
 * - 已完成的图集会显示勾选标记，选择时弹窗提醒
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCleanupBottomSheet(
    albums: List<Album>,
    albumCleanupInfos: List<AlbumCleanupInfo>,
    selectedAlbumId: String?,
    analyzingAlbumId: String?,
    analysisProgress: Float,
    onSelectAlbum: (Album) -> Unit,
    onSwitchToGlobal: () -> Unit,
    onDismiss: () -> Unit,
    onStartCleanup: () -> Unit,
    onResetAndStartCleanup: ((Album) -> Unit)? = null,  // 重置并重新开始清理（用于已完成的图集）
    // 月份专清相关参数
    allImages: List<ImageFile> = emptyList(),
    selectedMonthId: String? = null,
    onSelectMonth: ((MonthInfo) -> Unit)? = null,
    onStartMonthCleanup: (() -> Unit)? = null,
    onResetAndStartMonthCleanup: ((MonthInfo) -> Unit)? = null  // 重置并重新开始月份清理（用于已完成的月份）
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val containerColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val accentColor = Color(0xFF007AFF)
    
    // 清理模式状态
    var cleanupMode by remember { mutableStateOf(CleanupMode.ALBUM) }
    
    // 确认弹窗状态（用于已完成图集/月份的重新整理提醒）
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var pendingResetAlbum by remember { mutableStateOf<Album?>(null) }
    var pendingResetMonth by remember { mutableStateOf<MonthInfo?>(null) }
    
    // 获取清理引擎实例（用于获取月份的清理信息）
    val albumCleanupEngine = remember { AlbumCleanupEngine.getInstance(context) }
    
    // 预先构建 cleanupInfo Map，避免每次重组时 O(n) 查找
    val cleanupInfoMap = remember(albumCleanupInfos) {
        albumCleanupInfos.associateBy { it.album.id }
    }
    
    // 显示所有图集（包括已完成的），已完成的会有特殊标记
    // 排序规则：
    // 1. 普通图集（有照片、未完成）按照片数量降序
    // 2. 已完成的图集
    // 3. 空图集（照片数量为0）放到最后
    val displayAlbums = remember(albums, cleanupInfoMap) {
        albums.sortedWith(
            compareBy<Album> { album ->
                val cleanupInfo = cleanupInfoMap[album.id]
                val isCompleted = cleanupInfo?.isCompleted == true
                val isEmpty = album.imageCount == 0
                when {
                    isEmpty -> 2        // 空图集放最后
                    isCompleted -> 1    // 已完成的图集次之
                    else -> 0           // 普通图集在前面
                }
            }.thenByDescending { it.imageCount }  // 同类别内按照片数量降序排列
        )
    }
    
    // 从图片列表中提取月份分组（按时间倒序排列）
    // 使用 produceState 异步计算，避免大量图片时 UI 卡顿
    val monthInfos by produceState(initialValue = emptyList<MonthInfo>(), key1 = allImages) {
        value = withContext(Dispatchers.Default) {
            buildMonthInfos(allImages)
        }
    }
    
    // 为月份构建清理信息 Map（月份使用虚拟 Album）
    val monthCleanupInfoMap = remember(monthInfos, analyzingAlbumId) {
        monthInfos.associate { monthInfo ->
            val virtualAlbum = monthInfo.toVirtualAlbum()
            monthInfo.cleanupAlbumId to albumCleanupEngine.getAlbumCleanupInfo(virtualAlbum)
        }
    }
    
    // 获取导航栏高度，用于底部内边距
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },  // 沉浸式：延伸到导航栏区域
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
                .padding(bottom = maxOf(navigationBarPadding, 16.dp))  // 沉浸式：内容底部留出导航栏安全距离，最小16dp
        ) {
            // 标题
            Text(
                text = "选择图集进行整理",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 三合一模式切换分段控件（全局整理 / 图集专清 / 月份专清）
            CleanupModeToggle(
                currentMode = cleanupMode,
                onModeChange = { mode ->
                    HapticFeedback.lightTap(context)
                    if (mode == CleanupMode.GLOBAL) {
                        // 全局整理直接触发切换
                        onSwitchToGlobal()
                    } else {
                        cleanupMode = mode
                    }
                },
                isDarkTheme = isDarkTheme,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 根据模式显示不同的列表（全局模式会直接跳转，不在这里处理）
            when (cleanupMode) {
                CleanupMode.GLOBAL -> {
                    // 全局模式不需要显示列表，点击后会直接跳转
                }
                CleanupMode.ALBUM -> {
                    // 图集列表
                    if (displayAlbums.isEmpty()) {
                        // 空状态
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "所有图集已整理完毕",
                                    color = secondaryTextColor,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(displayAlbums, key = { it.id }) { album ->
                                val info = cleanupInfoMap[album.id]
                                val isSelected = selectedAlbumId == album.id
                                val isAnalyzing = analyzingAlbumId == album.id
                                
                                AlbumCleanupItem(
                                    album = album,
                                    allImages = allImages,
                                    info = info,
                                    isSelected = isSelected,
                                    isAnalyzing = isAnalyzing,
                                    analysisProgress = if (isAnalyzing) analysisProgress else (info?.analysisProgress ?: 0f),
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme,
                                    onClick = {
                                        HapticFeedback.lightTap(context)
                                        onSelectAlbum(album)
                                    }
                                )
                            }
                        }
                    }
                }
                
                CleanupMode.MONTH -> {
                    // 月份列表
                    if (monthInfos.isEmpty()) {
                        // 空状态
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "没有找到照片",
                                    color = secondaryTextColor,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(monthInfos, key = { it.id }) { monthInfo ->
                                val isSelected = selectedMonthId == monthInfo.id
                                // 从月份专用的清理信息 Map 获取
                                val monthCleanupInfo = monthCleanupInfoMap[monthInfo.cleanupAlbumId]
                                val isAnalyzing = analyzingAlbumId == monthInfo.cleanupAlbumId
                                
                                MonthCleanupItem(
                                    monthInfo = monthInfo,
                                    info = monthCleanupInfo,
                                    isSelected = isSelected,
                                    isAnalyzing = isAnalyzing,
                                    analysisProgress = if (isAnalyzing) analysisProgress else (monthCleanupInfo?.analysisProgress ?: 0f),
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    isDarkTheme = isDarkTheme,
                                    onClick = {
                                        HapticFeedback.lightTap(context)
                                        onSelectMonth?.invoke(monthInfo)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // 底部开始按钮 - 根据模式显示不同状态（全局模式不显示按钮）
            val showStartButton = when (cleanupMode) {
                CleanupMode.GLOBAL -> false  // 全局模式点击后直接跳转，不需要开始按钮
                CleanupMode.ALBUM -> selectedAlbumId != null && displayAlbums.isNotEmpty()
                CleanupMode.MONTH -> selectedMonthId != null && monthInfos.isNotEmpty()
            }
            
            if (showStartButton) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // 检查选中的图集/月份是否已完成
                val selectedAlbum = albums.find { it.id == selectedAlbumId }
                val selectedAlbumInfo = cleanupInfoMap[selectedAlbumId]
                val isAlbumCompleted = selectedAlbumInfo?.isCompleted == true
                
                // 获取选中的月份及其清理信息（从月份专用 Map 获取）
                val selectedMonth = monthInfos.find { it.id == selectedMonthId }
                val selectedMonthInfo = selectedMonth?.let { monthCleanupInfoMap[it.cleanupAlbumId] }
                val isMonthCompleted = selectedMonthInfo?.isCompleted == true
                
                Button(
                    onClick = {
                        HapticFeedback.mediumTap(context)
                        when (cleanupMode) {
                            CleanupMode.GLOBAL -> {
                                // 全局模式不会到达这里，因为 showStartButton 为 false
                            }
                            CleanupMode.ALBUM -> {
                                if (isAlbumCompleted && selectedAlbum != null) {
                                    // 已完成的图集，显示确认弹窗
                                    pendingResetAlbum = selectedAlbum
                                    showResetConfirmDialog = true
                                } else {
                                    onStartCleanup()
                                }
                            }
                            CleanupMode.MONTH -> {
                                if (isMonthCompleted && selectedMonth != null) {
                                    // 已完成的月份，显示确认弹窗
                                    pendingResetMonth = selectedMonth
                                    showResetConfirmDialog = true
                                } else {
                                    onStartMonthCleanup?.invoke()
                                }
                            }
                        }
                    },
                    enabled = analyzingAlbumId == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = accentColor.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(50.dp)
                ) {
                    if (analyzingAlbumId != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在分析...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "开始整理",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
    
    // 重新整理确认弹窗（支持图集和月份）
    if (showResetConfirmDialog && (pendingResetAlbum != null || pendingResetMonth != null)) {
        val targetName = pendingResetAlbum?.name ?: pendingResetMonth?.displayName ?: ""
        val isMonthReset = pendingResetMonth != null
        
        AlertDialog(
            onDismissRequest = {
                showResetConfirmDialog = false
                pendingResetAlbum = null
                pendingResetMonth = null
            },
            containerColor = containerColor,
            title = {
                Text(
                    text = "重新整理",
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "「$targetName」已经整理完毕。\n\n是否重新整理${if (isMonthReset) "这个月份" else "这个图集"}？",
                    color = if (isDarkTheme) Color(0xFFAEAEB2) else Color(0xFF3C3C43)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isMonthReset) {
                            pendingResetMonth?.let { month ->
                                onResetAndStartMonthCleanup?.invoke(month)
                            }
                        } else {
                            pendingResetAlbum?.let { album ->
                                onResetAndStartCleanup?.invoke(album)
                            }
                        }
                        showResetConfirmDialog = false
                        pendingResetAlbum = null
                        pendingResetMonth = null
                    }
                ) {
                    Text(
                        text = "重新整理",
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        pendingResetAlbum = null
                        pendingResetMonth = null
                    }
                ) {
                    Text(
                        text = "取消",
                        color = secondaryTextColor
                    )
                }
            }
        )
    }
}

/**
 * 图集清理列表项
 */
@Composable
private fun AlbumCleanupItem(
    album: Album,
    allImages: List<ImageFile>,
    info: AlbumCleanupInfo?,
    isSelected: Boolean,
    isAnalyzing: Boolean,
    analysisProgress: Float,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            accentColor.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        label = "ItemBackground"
    )
    
    // 计算进度显示
    val showProgress = info != null && info.totalGroups > 0
    val cleanupProgress = info?.let { 
        if (it.totalGroups > 0) it.processedGroups.toFloat() / it.totalGroups else 0f 
    } ?: 0f
    
    // 状态文本
    val statusText = when {
        isAnalyzing -> "分析中..."
        info?.isCompleted == true -> "已完成"
        info?.totalGroups ?: -1 < 0 -> "点击开始分析"
        info?.totalGroups == 0 -> null  // 无相似照片时不显示文字
        else -> "共 ${info?.totalGroups} 组 · 剩余 ${info?.remainingGroups} 组"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面图
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
        ) {
            // C1/T03-COVER：封面解析按完整媒体身份（卷已知精确匹配；旧口径
            // 唯一解析；歧义/缺失 → 占位，不猜卷）。
            album.coverImageId?.let { coverId ->
                val coverRow = resolveCoverRow(
                    album.coverVolumeName, coverId,
                    allImages.filter { it.id == coverId }
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverRow?.uri)
                        .crossfade(true)
                        .memoryCacheKey(coverRow?.let { gridThumbnailCacheKey("cleanup_cover_", it) })
                        .diskCacheKey(coverRow?.let { gridThumbnailCacheKey("cleanup_cover_", it) })
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } ?: run {
                // 无封面时显示颜色块
                val albumColor = album.color?.let { Color(it) } ?: Color(0xFF7986CB)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(albumColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = album.name.take(1),
                        color = albumColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 名称和状态
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = album.name,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "${album.imageCount} 张",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
            
            // 状态文本（移除进度条，进度显示在右侧圆环上）
            if (statusText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    color = if (info?.isCompleted == true) Color(0xFF34C759) else secondaryTextColor,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 右侧：带圆环进度的选择按钮
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (info?.isCompleted == true) {
                // 已完成的图集显示勾选图标
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "已完成",
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                // 带圆环进度的选择按钮
                CircularProgressRadioButton(
                    isSelected = isSelected,
                    isAnalyzing = isAnalyzing,
                    analysisProgress = analysisProgress,
                    cleanupProgress = cleanupProgress,
                    showProgress = showProgress,
                    accentColor = accentColor,
                    trackColor = secondaryTextColor.copy(alpha = 0.3f),
                    onClick = onClick
                )
            }
        }
    }
}

/**
 * 带圆环进度的选择按钮
 */
@Composable
private fun CircularProgressRadioButton(
    isSelected: Boolean,
    isAnalyzing: Boolean,
    analysisProgress: Float,
    cleanupProgress: Float,
    showProgress: Boolean,
    accentColor: Color,
    trackColor: Color,
    onClick: () -> Unit
) {
    val size = 28.dp
    val strokeWidth = 3.dp
    
    // 动画化的进度值
    val animatedProgress by animateFloatAsState(
        targetValue = when {
            isAnalyzing -> analysisProgress
            showProgress -> cleanupProgress
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CircularProgress"
    )
    
    // 选中状态的内圆动画
    val innerCircleScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "InnerCircle"
    )
    
    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasSize = size.toPx()
            val strokePx = strokeWidth.toPx()
            val radius = (canvasSize - strokePx) / 2
            
            // 绘制背景圆环（track）
            drawCircle(
                color = trackColor,
                radius = radius,
                style = Stroke(width = strokePx)
            )
            
            // 绘制进度圆环
            if (animatedProgress > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    size = androidx.compose.ui.geometry.Size(canvasSize - strokePx, canvasSize - strokePx),
                    topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2, strokePx / 2)
                )
            }
            
            // 绘制选中状态的内圆
            if (innerCircleScale > 0f) {
                val innerRadius = radius * 0.5f * innerCircleScale
                drawCircle(
                    color = accentColor,
                    radius = innerRadius
                )
            }
        }
    }
}

/**
 * 清理模式切换分段控件 - iOS风格三段式设计
 * 
 * 三个选项：全局整理 / 图集专清 / 月份专清
 * - 全局整理：点击后切换到全局整理页面
 * - 图集专清：按图集分组进行整理
 * - 月份专清：按月份分组进行整理
 */
@Composable
private fun CleanupModeToggle(
    currentMode: CleanupMode,
    onModeChange: (CleanupMode) -> Unit,
    isDarkTheme: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    // 容器背景色
    val containerColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    
    // 滑块背景色
    val selectedBgColor = if (isDarkTheme) Color(0xFF48484A) else Color.White
    
    // 文字颜色
    val selectedTextColor = if (isDarkTheme) Color.White else Color.Black
    val unselectedTextColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    
    // 三个按钮的配置
    data class ToggleItem(
        val mode: CleanupMode,
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector? = null
    )
    
    val toggleItems = listOf(
        ToggleItem(CleanupMode.GLOBAL, "全局", Icons.Rounded.Public),
        ToggleItem(CleanupMode.ALBUM, "图集"),
        ToggleItem(CleanupMode.MONTH, "月份")
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            toggleItems.forEach { item ->
                val isSelected = currentMode == item.mode
                // 全局模式永远不显示为选中状态（因为点击后会跳转）
                val showAsSelected = isSelected && item.mode != CleanupMode.GLOBAL
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (showAsSelected) selectedBgColor else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onModeChange(item.mode)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 全局模式显示图标
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = item.label,
                            fontSize = 14.sp,
                            fontWeight = if (showAsSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = when {
                                item.mode == CleanupMode.GLOBAL -> accentColor  // 全局模式始终用强调色
                                showAsSelected -> selectedTextColor
                                else -> unselectedTextColor
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 月份清理列表项
 */
@Composable
private fun MonthCleanupItem(
    monthInfo: MonthInfo,
    info: AlbumCleanupInfo?,
    isSelected: Boolean,
    isAnalyzing: Boolean,
    analysisProgress: Float,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            accentColor.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        label = "MonthItemBackground"
    )
    
    // 计算进度显示
    val showProgress = info != null && info.totalGroups > 0
    val cleanupProgress = info?.let { 
        if (it.totalGroups > 0) it.processedGroups.toFloat() / it.totalGroups else 0f 
    } ?: 0f
    
    // 状态文本
    val statusText = when {
        isAnalyzing -> "分析中..."
        info?.isCompleted == true -> "已完成"
        info?.totalGroups ?: -1 < 0 -> null  // 未分析，不显示状态
        info?.totalGroups == 0 -> null  // 无相似照片时不显示文字
        else -> "共 ${info?.totalGroups} 组 · 剩余 ${info?.remainingGroups} 组"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面图
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
        ) {
            // C1/T03-COVER：封面行在月份组成员内——按身份解析（卷已知精确/
            // 旧口径唯一/歧义占位）。
            monthInfo.coverImageId?.let { coverId ->
                val coverRow = resolveCoverRow(
                    monthInfo.coverVolumeName, coverId,
                    monthInfo.images.filter { it.id == coverId }
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverRow?.uri)
                        .crossfade(true)
                        .memoryCacheKey(coverRow?.let { gridThumbnailCacheKey("month_cover_", it) })
                        .diskCacheKey(coverRow?.let { gridThumbnailCacheKey("month_cover_", it) })
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } ?: run {
                // 无封面时显示日历图标
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 名称和数量
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = monthInfo.displayName,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "${monthInfo.imageCount} 张照片",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
            
            // 状态文本
            if (statusText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    color = if (info?.isCompleted == true) Color(0xFF34C759) else secondaryTextColor,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 右侧：带圆环进度的选择按钮（与图集专清一致）
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (info?.isCompleted == true) {
                // 已完成的月份显示勾选图标
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "已完成",
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                // 带圆环进度的选择按钮
                CircularProgressRadioButton(
                    isSelected = isSelected,
                    isAnalyzing = isAnalyzing,
                    analysisProgress = analysisProgress,
                    cleanupProgress = cleanupProgress,
                    showProgress = showProgress,
                    accentColor = accentColor,
                    trackColor = secondaryTextColor.copy(alpha = 0.3f),
                    onClick = onClick
                )
            }
        }
    }
}
