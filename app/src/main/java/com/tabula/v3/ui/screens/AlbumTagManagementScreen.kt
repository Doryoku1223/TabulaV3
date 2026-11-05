package com.tabula.v3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.resolveCoverRow
import com.tabula.v3.ui.util.gridThumbnailCacheKey

import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import androidx.compose.ui.draw.scale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Collections
import kotlin.math.roundToInt

/**
 * 图集标签管理页面
 *
 * 统一管理图集标签的排序、可见性、推荐屏蔽和智能排序设置。
 *
 * 功能：
 * 1. 标签排序与显示：拖拽调整图集标签的显示顺序，控制下滑归档时的可见性
 * 2. 推荐屏蔽：控制图集是否参与推荐流
 * 3. 智能排序：根据使用频率自动调整排序，可自定义权重参数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumTagManagementScreen(
    albums: List<Album>,
    allImages: List<ImageFile>,
    excludedAlbumIds: Set<String>,
    preferences: AppPreferences,
    onReorderAlbums: (List<String>) -> Unit,
    onToggleAlbumHidden: (String, Boolean) -> Unit,
    onToggleAlbumExcluded: (String, Boolean) -> Unit,
    onResetTagSorting: () -> Unit,
    onRefreshAlbums: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current

    val backgroundColor = if (isDarkTheme) Color.Black else Color(0xFFF2F2F7)
    val cardColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = Color(0xFF8E8E93)
    val accentBlue = Color(0xFF007AFF)
    val accentOrange = Color(0xFFFF9500)
    val accentGreen = Color(0xFF34C759)
    val accentRed = Color(0xFFFF3B30)
    val dividerColor = if (isDarkTheme) Color(0xFF38383A) else Color(0xFFE5E5EA)

    // ========== 本地排序状态 ==========
    // 使用 remember(albums) 在外部列表变化时同步
    var orderedAlbums by remember(albums) { mutableStateOf(albums) }

    // ========== 智能排序设置状态 ==========
    var smartSortEnabled by remember { mutableStateOf(preferences.smartSortEnabled) }
    var timeDecay by remember { mutableFloatStateOf(preferences.smartSortTimeDecay) }
    var consecutiveDecay by remember { mutableFloatStateOf(preferences.smartSortConsecutiveDecay) }
    var lockDays by remember { mutableIntStateOf(preferences.smartSortLockDays) }
    var showLockDaysSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    
    // ========== 区域折叠状态 ==========
    var isTagOrderExpanded by remember { mutableStateOf(true) }
    var isExcludeExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // ========== 顶部栏 ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = {
                    HapticFeedback.lightTap(context)
                    onNavigateBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "图集标签管理",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ========== 内容区域（使用 LazyColumn 实现懒加载，大幅提升首屏性能）==========
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            item(key = "spacer_top") {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // =============================================
            // Section 1: 标签排序与显示
            // =============================================
            item(key = "header_tag_order") {
                CollapsibleTagSectionHeader(
                    title = "标签排序与显示",
                    description = "调整图集标签顺序，影响下滑归档时的标签排列。\n关闭可见性的图集不会在归档时显示。",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    isExpanded = isTagOrderExpanded,
                    onToggleExpand = { isTagOrderExpanded = !isTagOrderExpanded }
                )
            }

            if (isTagOrderExpanded) {
                item(key = "tag_order_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (orderedAlbums.isEmpty()) {
                    item(key = "tag_order_empty") {
                        TagEmptyCard(
                            cardColor = cardColor,
                            textColor = secondaryTextColor,
                            message = "暂无图集",
                            description = "创建图集后可在此管理标签"
                        )
                    }
                } else {
                    // 标签排序列表 - 使用 itemsIndexed 实现懒加载
                    itemsIndexed(
                        items = orderedAlbums,
                        key = { _, album -> "tag_order_${album.id}" }
                    ) { index, album ->
                        val topShape = if (index == 0) 16.dp else 0.dp
                        val bottomShape = if (index == orderedAlbums.lastIndex) 16.dp else 0.dp
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(
                                    topStart = topShape, topEnd = topShape,
                                    bottomStart = bottomShape, bottomEnd = bottomShape
                                ))
                                .background(cardColor)
                        ) {
                            TagOrderItem(
                                album = album,
                                allImages = allImages,
                                isFirst = index == 0,
                                isLast = index == orderedAlbums.lastIndex,
                                isVisible = !album.isHidden,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentBlue = accentBlue,
                                onMoveUp = {
                                    if (index > 0) {
                                        HapticFeedback.lightTap(context)
                                        val newList = orderedAlbums.toMutableList()
                                        Collections.swap(newList, index, index - 1)
                                        orderedAlbums = newList
                                        onReorderAlbums(newList.map { it.id })
                                    }
                                },
                                onMoveDown = {
                                    if (index < orderedAlbums.lastIndex) {
                                        HapticFeedback.lightTap(context)
                                        val newList = orderedAlbums.toMutableList()
                                        Collections.swap(newList, index, index + 1)
                                        orderedAlbums = newList
                                        onReorderAlbums(newList.map { it.id })
                                    }
                                },
                                onToggleVisibility = {
                                    HapticFeedback.lightTap(context)
                                    val newHidden = !album.isHidden
                                    onToggleAlbumHidden(album.id, newHidden)
                                    orderedAlbums = orderedAlbums.map {
                                        if (it.id == album.id) it.copy(isHidden = newHidden) else it
                                    }
                                }
                            )
                            if (index < orderedAlbums.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 56.dp)
                                        .height(0.5.dp)
                                        .background(dividerColor)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "spacer_section1_2") {
                Spacer(modifier = Modifier.height(28.dp))
            }

            // =============================================
            // Section 2: 推荐屏蔽
            // =============================================
            item(key = "header_exclude") {
                CollapsibleTagSectionHeader(
                    title = "推荐屏蔽",
                    description = "已屏蔽的图集中的照片不会出现在推荐流中。\n适用于已整理好、不希望再次推荐的图集。",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    isExpanded = isExcludeExpanded,
                    onToggleExpand = { isExcludeExpanded = !isExcludeExpanded }
                )
            }

            if (isExcludeExpanded) {
                item(key = "exclude_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (orderedAlbums.isEmpty()) {
                    item(key = "exclude_empty") {
                        TagEmptyCard(
                            cardColor = cardColor,
                            textColor = secondaryTextColor,
                            message = "暂无图集",
                            description = "创建图集后可在此管理屏蔽"
                        )
                    }
                } else {
                    // 推荐屏蔽列表 - 使用 itemsIndexed 实现懒加载
                    itemsIndexed(
                        items = orderedAlbums,
                        key = { _, album -> "exclude_${album.id}" }
                    ) { index, album ->
                        val isExcluded = album.id in excludedAlbumIds
                        val topShape = if (index == 0) 16.dp else 0.dp
                        val bottomShape = if (index == orderedAlbums.lastIndex) 16.dp else 0.dp

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(
                                    topStart = topShape, topEnd = topShape,
                                    bottomStart = bottomShape, bottomEnd = bottomShape
                                ))
                                .background(cardColor)
                        ) {
                            ExcludeAlbumItem(
                                album = album,
                                allImages = allImages,
                                isExcluded = isExcluded,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentOrange = accentOrange,
                                onToggleExcluded = {
                                    HapticFeedback.lightTap(context)
                                    onToggleAlbumExcluded(album.id, !isExcluded)
                                }
                            )
                            if (index < orderedAlbums.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 56.dp)
                                        .height(0.5.dp)
                                        .background(dividerColor)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "spacer_section2_3") {
                Spacer(modifier = Modifier.height(28.dp))
            }

            // =============================================
            // Section 3: 智能排序设置
            // =============================================
            item(key = "smart_sort_section") {
                TagSectionHeader(
                    title = "智能排序",
                    description = "根据使用频率自动调整图集标签的排序。\n关闭后将始终使用手动排列的顺序。",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                ) {
                    SmartSortToggleItem(
                        enabled = smartSortEnabled,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        accentColor = accentGreen,
                        onToggle = {
                            HapticFeedback.lightTap(context)
                            smartSortEnabled = !smartSortEnabled
                            preferences.smartSortEnabled = smartSortEnabled
                            onRefreshAlbums()
                        }
                    )

                    AnimatedVisibility(
                        visible = smartSortEnabled,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(200))
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .height(0.5.dp)
                                    .background(dividerColor)
                            )

                            SmartSortSliderItem(
                                title = "时间衰减灵敏度",
                                description = "越高越侧重最近使用的图集",
                                value = timeDecay,
                                valueRange = 0.01f..0.20f,
                                labelLow = "缓慢",
                                labelHigh = "敏锐",
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentBlue,
                                onValueChange = { newValue ->
                                    timeDecay = newValue
                                },
                                onValueChangeFinished = {
                                    preferences.smartSortTimeDecay = timeDecay
                                    onRefreshAlbums()
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .height(0.5.dp)
                                    .background(dividerColor)
                            )

                            SmartSortSliderItem(
                                title = "连续归类抑制",
                                description = "越强越抑制同一图集连续归类的加权",
                                value = consecutiveDecay,
                                valueRange = 0.3f..1.0f,
                                labelLow = "强",
                                labelHigh = "弱",
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = accentBlue,
                                onValueChange = { newValue ->
                                    consecutiveDecay = newValue
                                },
                                onValueChangeFinished = {
                                    preferences.smartSortConsecutiveDecay = consecutiveDecay
                                    onRefreshAlbums()
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .height(0.5.dp)
                                    .background(dividerColor)
                            )

                            SmartSortLockDaysItem(
                                lockDays = lockDays,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                onClick = {
                                    HapticFeedback.lightTap(context)
                                    showLockDaysSheet = true
                                }
                            )
                        }
                    }
                }
            }

            item(key = "spacer_section3_4") {
                Spacer(modifier = Modifier.height(28.dp))
            }

            // =============================================
            // Section 4: 重置排序
            // =============================================
            item(key = "reset_section") {
                TagSectionHeader(
                    title = "重置",
                    description = "清除所有手动排序和使用记录，恢复默认排序。",
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            HapticFeedback.lightTap(context)
                            showResetConfirm = true
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "重置标签排序",
                        color = accentRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }

    // ========== 手动排序有效期选择器 ==========
    if (showLockDaysSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLockDaysSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = cardColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "手动排序有效期",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "手动拖拽排序后，排序将在有效期内保持不变",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AppPreferences.SMART_SORT_LOCK_DAYS_OPTIONS.forEach { days ->
                    val isSelected = days == lockDays
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) accentBlue.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                HapticFeedback.lightTap(context)
                                lockDays = days
                                preferences.smartSortLockDays = days
                                showLockDaysSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$days 天",
                            color = if (isSelected) accentBlue else textColor,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ========== 重置确认对话框 ==========
    if (showResetConfirm) {
        ModalBottomSheet(
            onDismissRequest = { showResetConfirm = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = cardColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = accentRed,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "重置标签排序",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "将清除所有手动排序记录，恢复为默认排序。\n此操作不会影响图集的隐藏和屏蔽状态。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            showResetConfirm = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "取消",
                            color = accentBlue,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                    TextButton(
                        onClick = {
                            HapticFeedback.lightTap(context)
                            showResetConfirm = false
                            onResetTagSorting()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "确认重置",
                            color = accentRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}


// ========================================================================
// 子组件
// ========================================================================

/**
 * 区域标题 + 描述（不带折叠功能）
 */
@Composable
private fun TagSectionHeader(
    title: String,
    description: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        ),
        color = secondaryTextColor,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        color = secondaryTextColor.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

/**
 * 可折叠的区域标题 + 描述
 */
@Composable
private fun CollapsibleTagSectionHeader(
    title: String,
    description: String,
    textColor: Color,
    secondaryTextColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val rotationAngle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "chevronRotation"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                HapticFeedback.lightTap(context)
                onToggleExpand()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                ),
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = secondaryTextColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        
        // 展开/折叠按钮
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = secondaryTextColor.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .graphicsLayer { rotationZ = rotationAngle }
        )
    }
}

/**
 * 空状态卡片
 */
@Composable
private fun TagEmptyCard(
    cardColor: Color,
    textColor: Color,
    message: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.3f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = textColor.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.4f)
        )
    }
}

/**
 * 标签排序项：上移/下移箭头 + 图集信息 + 可见性开关
 */
@Composable
private fun TagOrderItem(
    album: Album,
    allImages: List<ImageFile>,
    isFirst: Boolean,
    isLast: Boolean,
    isVisible: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    accentBlue: Color,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    val itemAlpha = if (isVisible) 1f else 0.45f
    val visibilityIconColor by animateColorAsState(
        targetValue = if (isVisible) accentBlue else secondaryTextColor.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "visibilityColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上下移动按钮
        Column(
            modifier = Modifier.width(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "上移",
                    tint = if (!isFirst) textColor.copy(alpha = 0.6f) else textColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "下移",
                    tint = if (!isLast) textColor.copy(alpha = 0.6f) else textColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 图集封面图或颜色指示器
        val context = LocalContext.current
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    album.color?.let { Color(it) }
                        ?: secondaryTextColor.copy(alpha = 0.15f)
                )
                .alpha(itemAlpha),
            contentAlignment = Alignment.Center
        ) {
            // C1/T03-COVER：封面解析按完整媒体身份——旧口径（卷未知）仅库内
            // 唯一才解析，歧义/缺失回退 Emoji 占位（不猜卷、不借用另一卷同 ID）。
            val tagCoverImage = album.coverImageId?.let { coverId ->
                resolveCoverRow(
                    album.coverVolumeName, coverId,
                    allImages.filter { it.id == coverId }
                )
            }
            if (tagCoverImage != null) {
                // 显示封面图 — 性能优化：指定小尺寸 + 启用硬件位图 + 缓存
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(tagCoverImage.uri)
                        .size(120, 120)
                        .crossfade(100)
                        .memoryCacheKey(gridThumbnailCacheKey("tag_cover_", tagCoverImage))
                        .diskCacheKey(gridThumbnailCacheKey("tag_cover_", tagCoverImage))
                        .allowHardware(true)
                        .build(),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (album.emoji != null) {
                Text(
                    text = album.emoji,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 图集信息
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(itemAlpha)
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.imageCount} 张",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = secondaryTextColor
            )
        }

        // 可见性开关
        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = if (isVisible) "隐藏" else "显示",
                tint = visibilityIconColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 推荐屏蔽项：图集信息 + 屏蔽开关
 */
@Composable
private fun ExcludeAlbumItem(
    album: Album,
    allImages: List<ImageFile>,
    isExcluded: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    accentOrange: Color,
    onToggleExcluded: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggleExcluded() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图集封面图或颜色指示器
        val context = LocalContext.current
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    album.color?.let { Color(it) }
                        ?: secondaryTextColor.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            // C1/T03-COVER：解析同 tag_cover_（身份精确/旧口径唯一/歧义占位）。
            val excludeCoverImage = album.coverImageId?.let { coverId ->
                resolveCoverRow(
                    album.coverVolumeName, coverId,
                    allImages.filter { it.id == coverId }
                )
            }
            if (excludeCoverImage != null) {
                // 显示封面图 — 性能优化：指定小尺寸 + 启用硬件位图 + 缓存
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(excludeCoverImage.uri)
                        .size(120, 120)
                        .crossfade(100)
                        .memoryCacheKey(gridThumbnailCacheKey("exclude_cover_", excludeCoverImage))
                        .diskCacheKey(gridThumbnailCacheKey("exclude_cover_", excludeCoverImage))
                        .allowHardware(true)
                        .build(),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (album.emoji != null) {
                Text(text = album.emoji, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 图集信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isExcluded) "已屏蔽 · ${album.imageCount} 张" else "${album.imageCount} 张",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (isExcluded) accentOrange else secondaryTextColor
            )
        }

        // 屏蔽开关
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = isExcluded,
                onCheckedChange = { onToggleExcluded() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TabulaColors.EyeGold,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE9E9EB)
                ),
                modifier = Modifier
                    .height(20.dp)
                    .scale(0.85f)
            )
        }
    }
}

/**
 * 智能排序主开关
 */
@Composable
private fun SmartSortToggleItem(
    enabled: Boolean,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "自动排序",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = textColor
            )
            Text(
                text = if (enabled) "根据使用频率自动调整排序" else "已关闭，使用手动排列顺序",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = secondaryTextColor
            )
        }

        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TabulaColors.EyeGold,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE9E9EB)
                ),
                modifier = Modifier
                    .height(20.dp)
                    .scale(0.85f)
            )
        }
    }
}

/**
 * 智能排序 Slider 设置项 - 美化版
 */
@Composable
private fun SmartSortSliderItem(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    labelLow: String,
    labelHigh: String,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val inactiveTrackColor = if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            ),
            color = textColor
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = secondaryTextColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = labelLow,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                fontSize = 11.sp
            )
            
            // 使用自定义美化滑块
            BeautifulRangeSlider(
                value = value,
                valueRange = valueRange,
                activeColor = accentColor,
                inactiveColor = inactiveTrackColor,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = labelHigh,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 美化的范围滑块组件 - 无圆形滑块，只有轨道
 */
@Composable
private fun BeautifulRangeSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color = Color.White,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 32.dp,
    thumbSize: Dp = 26.dp  // 保留参数但不使用，以保持兼容性
) {
    val density = LocalDensity.current
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
    
    BoxWithConstraints(
        modifier = modifier
            .height(trackHeight)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val cornerRadiusPx = trackHeightPx / 2f
        
        // 计算活动轨道的宽度（占满整个宽度的比例）
        val activeEndX = widthPx * fraction

        fun updateFromX(x: Float) {
            val clampedX = x.coerceIn(0f, widthPx)
            val newFraction = if (widthPx <= 0f) 0f else clampedX / widthPx
            val newValue = valueRange.start + newFraction * range
            onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(trackHeight / 2))
                .pointerInput(widthPx) {
                    detectTapGestures { offset -> 
                        updateFromX(offset.x)
                        onValueChangeFinished()
                    }
                }
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragEnd = { onValueChangeFinished() },
                        onDrag = { change, _ ->
                            updateFromX(change.position.x)
                            change.consume()
                        }
                    )
                }
        ) {
            // 绘制背景轨道（整个圆角矩形）
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, trackHeightPx),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )

            // 绘制活动轨道
            if (activeEndX > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeEndX, trackHeightPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            }
            
            // 不再绘制滑块，只保留轨道
        }
    }
}

/**
 * 手动排序有效期设置项
 */
@Composable
private fun SmartSortLockDaysItem(
    lockDays: Int,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "手动排序有效期",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = textColor
            )
            Text(
                text = "手动排序后，顺序将在有效期内保持",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = secondaryTextColor
            )
        }
        Text(
            text = "$lockDays 天",
            color = secondaryTextColor,
            fontSize = 14.sp
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = secondaryTextColor.copy(alpha = 0.5f),
            modifier = Modifier
                .size(18.dp)
                .padding(start = 2.dp)
        )
    }
}
