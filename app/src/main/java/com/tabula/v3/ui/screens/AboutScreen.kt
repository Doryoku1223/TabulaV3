package com.tabula.v3.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.BuildConfig
import com.tabula.v3.R
import com.tabula.v3.ui.components.LocalLiquidGlassEnabled
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关于 Tabula 页面
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val cardColor = if (isDarkTheme) TabulaColors.CatBlackLight.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f)
    val textColor = if (isDarkTheme) Color.White else TabulaColors.CatBlack
    val secondaryTextColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF666666)
    val accentColor = TabulaColors.EyeGold
    val scope = rememberCoroutineScope()
    val downloadManager = remember {
        context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var pendingDownloadId by remember { mutableStateOf<Long?>(null) }
    var updateDialogState by remember { mutableStateOf<UpdateDialogState?>(null) }
    val prefs = remember {
        context.getSharedPreferences("tabula_update_prefs", android.content.Context.MODE_PRIVATE)
    }
    val storedDownloadId = remember { prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1L) }

    DisposableEffect(pendingDownloadId) {
        if (pendingDownloadId == null) {
            return@DisposableEffect onDispose { }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId != pendingDownloadId) return
                isDownloading = false
                pendingDownloadId = null
                prefs.edit().remove(KEY_PENDING_DOWNLOAD_ID).apply()
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uri = downloadManager.getUriForDownloadedFile(downloadId)
                            if (uri != null) {
                                updateDialogState = UpdateDialogState.InstallReady(uri)
                            } else {
                                updateDialogState = UpdateDialogState.Error("安装文件不可用，请重试下载")
                            }
                            return
                        }
                        if (status == DownloadManager.STATUS_FAILED) {
                            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            updateDialogState = UpdateDialogState.Error(mapDownloadError(reason))
                            return
                        }
                    }
                }
                updateDialogState = UpdateDialogState.Error("下载状态未知，请稍后重试")
                prefs.edit().remove(KEY_PENDING_DOWNLOAD_ID).apply()
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun startUpdateCheck() {
        if (isCheckingUpdate) return
        if (isDownloading) {
            updateDialogState = UpdateDialogState.Error("更新包正在下载中，请稍后再试")
            return
        }
        updateDialogState = null
        isCheckingUpdate = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchLatestRelease()
            }
            isCheckingUpdate = false
            when (result) {
                is UpdateFetchResult.Success -> {
                    if (isNewerVersion(result.info.versionName, BuildConfig.VERSION_NAME)) {
                        updateDialogState = UpdateDialogState.NewVersion(result.info)
                    } else {
                        updateDialogState = UpdateDialogState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                }
                is UpdateFetchResult.Error -> {
                    updateDialogState = UpdateDialogState.Error(result.message)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (storedDownloadId > 0 && pendingDownloadId == null) {
            pendingDownloadId = storedDownloadId
            isDownloading = true
        }
    }

    fun startDownload(info: UpdateInfo) {
        updateDialogState = null
        val fileName = "Tabula-${info.versionName.ifBlank { "latest" }}.apk"
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Tabula ${info.versionName}")
            .setDescription("正在后台下载更新")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        try {
            pendingDownloadId = downloadManager.enqueue(request)
            isDownloading = true
            prefs.edit().putLong(KEY_PENDING_DOWNLOAD_ID, pendingDownloadId ?: -1L).apply()
        } catch (_: Exception) {
            updateDialogState = UpdateDialogState.Error("无法开始下载，请稍后重试")
        }
    }

    fun launchInstall(uri: Uri?) {
        if (uri == null) {
            updateDialogState = UpdateDialogState.Error("安装文件不可用")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                val settingsIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(settingsIntent)
                } catch (_: Exception) {
                    updateDialogState = UpdateDialogState.Error("请在系统设置中允许应用安装更新")
                }
                return
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            updateDialogState = UpdateDialogState.Error("无法打开安装器，请手动安装")
        }
    }
    
    updateDialogState?.let { state ->
        UpdateDialog(
            state = state,
            accentColor = accentColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            onDismiss = { updateDialogState = null },
            onConfirm = { action ->
                when (action) {
                    is UpdateDialogAction.StartDownload -> startDownload(action.info)
                    is UpdateDialogAction.Install -> launchInstall(action.uri)
                    UpdateDialogAction.Dismiss -> updateDialogState = null
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景图片
        Image(
            painter = painterResource(id = R.drawable.about_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // 半透明遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDarkTheme) Color.Black.copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.3f)
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶部导航栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
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
                        tint = textColor
                    )
                }
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // 内容
            // 使用 rememberSaveable 保存滚动位置
            val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Tabula Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 应用名称
                Text(
                    text = "Tabula",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = textColor
                )
                
                // 版本号
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryTextColor
                )

                Text(
                    text = "2026.1.30",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 介绍卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "简介",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = textColor
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Tabula 是一款优雅的本地照片整理工具。通过卡片式交互，让您轻松快速地清理手机相册中的冗余照片。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            lineHeight = 22.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "✨ 设计理念",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = textColor
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "• 简洁高效的卡片式交互\n• 优雅流畅的动画体验\n• 安全可靠的本地存储\n• 保护隐私，数据不上传",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            lineHeight = 24.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // 隐私声明卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "🔒 隐私保护",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = textColor
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Tabula 完全在本地运行，不会收集、上传或共享您的任何照片和数据。您的隐私是我们的首要关注。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 联系作者卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "联系作者",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = textColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 邮箱
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "email",
                                            "2922147939@qq.com"
                                        )
                                    )
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = "邮箱",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "作者邮箱",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = "2922147939@qq.com",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // GitHub
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/Doryoku1223/TabulaV3")
                                    ).also { context.startActivity(it) }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MarkEmailRead,
                                contentDescription = "GitHub",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GitHub 仓库",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = "github.com/Doryoku1223/TabulaV3",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // QQ 交流群
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "QQ群号",
                                            "1082340405"
                                        )
                                    )
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "QQ群",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "QQ 交流群",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = "1082340405",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 底部提示文字
                        Text(
                            text = "欢迎提交 issue 来帮助改进完善 Tabula!",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor.copy(alpha = 0.8f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        HapticFeedback.lightTap(context)
                        startUpdateCheck()
                    },
                    enabled = !isCheckingUpdate && !isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = TabulaColors.CatBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            color = TabulaColors.CatBlack,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "\u68c0\u67e5\u66f4\u65b0\u4e2d...")
                    } else {
                        Text(text = "\u68c0\u67e5\u66f4\u65b0")
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\u6b63\u5728\u540e\u53f0\u4e0b\u8f7d\u66f4\u65b0\uff0c\u5b8c\u6210\u540e\u5c06\u63d0\u793a\u5b89\u88c5",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private sealed class UpdateDialogState {
    data class NewVersion(val info: UpdateInfo) : UpdateDialogState()
    data class UpToDate(val currentVersion: String) : UpdateDialogState()
    data class Error(val message: String) : UpdateDialogState()
    data class InstallReady(val uri: Uri) : UpdateDialogState()
}

private sealed class UpdateDialogAction {
    data class StartDownload(val info: UpdateInfo) : UpdateDialogAction()
    data class Install(val uri: Uri) : UpdateDialogAction()
    object Dismiss : UpdateDialogAction()
}

@Composable
private fun UpdateDialog(
    state: UpdateDialogState,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (UpdateDialogAction) -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    // 液态玻璃模式下使用更不透明的背景
    val backgroundColor = when {
        isLiquidGlass -> if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7).copy(alpha = 0.98f)
        isDarkTheme -> Color(0xFF1C1C1E)
        else -> Color.White
    }
    val iconTint = when (state) {
        is UpdateDialogState.Error -> TabulaColors.DangerRed
        is UpdateDialogState.UpToDate -> TabulaColors.SuccessGreen
        else -> accentColor
    }
    val title = when (state) {
        is UpdateDialogState.NewVersion -> "\u53d1\u73b0\u65b0\u7248\u672c"
        is UpdateDialogState.UpToDate -> "\u5df2\u662f\u6700\u65b0\u7248\u672c"
        is UpdateDialogState.Error -> "\u66f4\u65b0\u5f02\u5e38"
        is UpdateDialogState.InstallReady -> "\u4e0b\u8f7d\u5b8c\u6210"
    }
    val message = when (state) {
        is UpdateDialogState.NewVersion ->
            "\u5f53\u524d\u7248\u672c ${BuildConfig.VERSION_NAME}\uff0c\u6700\u65b0\u7248\u672c ${state.info.versionName}\n\u662f\u5426\u7acb\u5373\u66f4\u65b0\uff1f"
        is UpdateDialogState.UpToDate ->
            "\u5f53\u524d\u5df2\u662f\u6700\u65b0\u7248\u672c\uff0c\u65e0\u9700\u66f4\u65b0\u3002"
        is UpdateDialogState.Error ->
            state.message
        is UpdateDialogState.InstallReady ->
            "\u66f4\u65b0\u5df2\u4e0b\u8f7d\u5b8c\u6210\uff0c\u662f\u5426\u7acb\u5373\u5b89\u88c5\uff1f"
    }
    val confirmText = when (state) {
        is UpdateDialogState.NewVersion -> "\u66f4\u65b0"
        is UpdateDialogState.InstallReady -> "\u5b89\u88c5"
        else -> "\u77e5\u9053\u4e86"
    }
    val dismissText = when (state) {
        is UpdateDialogState.NewVersion -> "\u53d6\u6d88"
        is UpdateDialogState.InstallReady -> "\u7a0d\u540e"
        is UpdateDialogState.Error -> null
        is UpdateDialogState.UpToDate -> null
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = backgroundColor,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (dismissText != null) {
                        Button(
                            onClick = { onConfirm(UpdateDialogAction.Dismiss) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                contentColor = textColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                        ) {
                            Text(text = dismissText, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Button(
                        onClick = {
                            when (state) {
                                is UpdateDialogState.NewVersion ->
                                    onConfirm(UpdateDialogAction.StartDownload(state.info))
                                is UpdateDialogState.InstallReady ->
                                    onConfirm(UpdateDialogAction.Install(state.uri))
                                else -> onConfirm(UpdateDialogAction.Dismiss)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = iconTint,
                            contentColor = if (state is UpdateDialogState.Error) Color.White else TabulaColors.CatBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                    ) {
                        Text(text = confirmText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class UpdateInfo(
    val versionName: String,
    val apkUrl: String
)

private sealed class UpdateFetchResult {
    data class Success(val info: UpdateInfo) : UpdateFetchResult()
    data class Error(val message: String) : UpdateFetchResult()
}

private fun parseVersionParts(version: String): List<Int> {
    return Regex("\\d+").findAll(version).mapNotNull { it.value.toIntOrNull() }.toList()
}

private fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = parseVersionParts(latest)
    val currentParts = parseVersionParts(current)
    val maxSize = maxOf(latestParts.size, currentParts.size)
    for (index in 0 until maxSize) {
        val latestValue = latestParts.getOrElse(index) { 0 }
        val currentValue = currentParts.getOrElse(index) { 0 }
        if (latestValue != currentValue) {
            return latestValue > currentValue
        }
    }
    return false
}

private fun fetchLatestRelease(): UpdateFetchResult {
    val url = java.net.URL("https://api.github.com/repos/Doryoku1223/TabulaV3/releases/latest")
    val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 6000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Tabula")
    }
    return try {
        val code = connection.responseCode
        when (code) {
            403, 429 -> return UpdateFetchResult.Error("\u8bf7\u6c42\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5")
            404 -> return UpdateFetchResult.Error("\u672a\u627e\u5230\u66f4\u65b0\u4fe1\u606f")
        }
        if (code !in 200..299) {
            return UpdateFetchResult.Error("\u66f4\u65b0\u670d\u52a1\u5f02\u5e38 ($code)")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(body)
        val tagName = json.optString("tag_name", "")
        val releaseName = json.optString("name", "")
        val versionName = (if (tagName.isNotBlank()) tagName else releaseName).trim()
        if (versionName.isBlank()) {
            return UpdateFetchResult.Error("\u672a\u83b7\u53d6\u5230\u7248\u672c\u4fe1\u606f")
        }
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val urlString = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && urlString.isNotBlank()) {
                    apkUrl = urlString
                    break
                }
            }
        }
        if (apkUrl.isNullOrBlank()) {
            return UpdateFetchResult.Error("\u672a\u627e\u5230\u66f4\u65b0\u5b89\u88c5\u5305")
        }
        UpdateFetchResult.Success(UpdateInfo(versionName = versionName, apkUrl = apkUrl))
    } catch (_: java.net.UnknownHostException) {
        UpdateFetchResult.Error("\u7f51\u7edc\u4e0d\u53ef\u7528\uff0c\u8bf7\u68c0\u67e5\u8fde\u63a5")
    } catch (_: java.net.SocketTimeoutException) {
        UpdateFetchResult.Error("\u8bf7\u6c42\u8d85\u65f6\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5")
    } catch (_: Exception) {
        UpdateFetchResult.Error("\u68c0\u67e5\u66f4\u65b0\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5")
    } finally {
        connection.disconnect()
    }
}

private fun mapDownloadError(reason: Int): String {
    return when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "\u65e0\u6cd5\u7ee7\u7eed\u4e0b\u8f7d\uff0c\u8bf7\u91cd\u8bd5"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "\u5b58\u50a8\u4e0d\u53ef\u7528\uff0c\u8bf7\u68c0\u67e5\u7a7a\u95f4"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "\u6587\u4ef6\u5df2\u5b58\u5728\uff0c\u8bf7\u79fb\u9664\u540e\u91cd\u8bd5"
        DownloadManager.ERROR_FILE_ERROR -> "\u6587\u4ef6\u5199\u5165\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "\u4e0b\u8f7d\u6570\u636e\u5f02\u5e38\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "\u7a7a\u95f4\u4e0d\u8db3\uff0c\u8bf7\u6e05\u7406\u5b58\u50a8\u7a7a\u95f4"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "\u4e0b\u8f7d\u5730\u5740\u8df3\u8f6c\u8fc7\u591a\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "\u4e0b\u8f7d\u94fe\u63a5\u5f02\u5e38\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
        DownloadManager.ERROR_UNKNOWN -> "\u672a\u77e5\u4e0b\u8f7d\u9519\u8bef\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
        else -> "\u4e0b\u8f7d\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
    }
}

private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
