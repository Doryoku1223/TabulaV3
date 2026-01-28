package com.tabula.v3.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.tabula.v3.MainActivity
import com.tabula.v3.R
import com.tabula.v3.data.preferences.AppPreferences

/**
 * 流体云服务 - ColorOS 流体云适配
 * 
 * 使用 MediaSession 来触发 ColorOS 流体云显示
 */
class FluidCloudService : Service() {

    private var mediaSession: MediaSessionCompat? = null
    private var notificationManager: NotificationManager? = null
    private var remainingCount = 0
    private var appIcon: Bitmap? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tabula_fluid_cloud"
        private const val EXTRA_REMAINING_COUNT = "remaining_count"
        private const val ACTION_OPEN = "com.tabula.v3.OPEN_APP"
        private const val ACTION_DISMISS = "com.tabula.v3.DISMISS"
        
        fun show(context: Context, remainingCount: Int) {
            val preferences = AppPreferences.getInstance(context)
            if (!preferences.fluidCloudEnabled || remainingCount <= 0) {
                return
            }
            
            preferences.pendingBatchRemaining = remainingCount
            
            val intent = Intent(context, FluidCloudService::class.java).apply {
                putExtra(EXTRA_REMAINING_COUNT, remainingCount)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun hide(context: Context) {
            val preferences = AppPreferences.getInstance(context)
            preferences.pendingBatchRemaining = 0
            context.stopService(Intent(context, FluidCloudService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        // 加载应用图标
        appIcon = BitmapFactory.decodeResource(resources, R.drawable.logo)
        
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_OPEN -> {
                openApp()
                return START_NOT_STICKY
            }
            ACTION_DISMISS -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        remainingCount = intent?.getIntExtra(EXTRA_REMAINING_COUNT, 0) ?: 0
        
        if (remainingCount <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        updateMediaSession()
        startForeground(NOTIFICATION_ID, createNotification())
        
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
        appIcon?.recycle()
        appIcon = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tabula 照片整理",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示剩余照片数量"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "Tabula").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    openApp()
                }
                override fun onPause() {
                    // 不做任何事，保持显示
                }
                override fun onStop() {
                    stopSelf()
                }
            })
            isActive = true
        }
    }
    
    private fun updateMediaSession() {
        mediaSession?.let { session ->
            val metadataBuilder = MediaMetadataCompat.Builder()
                // 主标题 - 显示在流体云 - 使用简洁的文字
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "还剩 $remainingCount 张")
                // 副标题 - 使用应用名
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Tabula 照片整理")
                // 专辑名为空，避免显示多余信息
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "")
                // 显示标题
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "还剩 $remainingCount 张")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "点击继续")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "")
                // 关键：不设置时长，这样流体云不会显示进度条
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            
            // 设置图标 - 这会显示在流体云中
            appIcon?.let { bitmap ->
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
            }
            
            session.setMetadata(metadataBuilder.build())
            
            // 必须使用 STATE_PLAYING 状态，ColorOS 才会显示流体云
            // 设置 position 为 0，speed 为 0 来避免进度动画
            val playbackState = PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    0L,
                    0f  // speed = 0，进度不会移动
                )
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
                .build()
            
            session.setPlaybackState(playbackState)
        }
    }
    
    private fun createNotification(): Notification {
        // 点击打开应用
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 关闭按钮
        val dismissIntent = Intent(this, FluidCloudService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(appIcon)
            .setContentTitle("还剩 $remainingCount 张")
            .setContentText("点击继续")
            .setSubText("Tabula 照片整理")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // MediaStyle 让 ColorOS 识别为流体云
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            // 只保留一个操作按钮，简化显示
            .addAction(
                R.drawable.ic_quick_start,
                "继续",
                openPendingIntent
            )
            .build()
    }
    
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        stopSelf()
    }
}
