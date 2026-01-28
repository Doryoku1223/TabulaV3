package com.tabula.v3.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tabula.v3.MainActivity
import com.tabula.v3.R
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.repository.LocalImageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/**
 * Tabula 桌面小组件
 * 
 * 显示相册中待整理的照片数量，并提供快捷入口
 */
class TabulaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 更新所有小组件实例
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // 小组件首次添加时调用
    }

    override fun onDisabled(context: Context) {
        // 最后一个小组件被移除时调用
    }

    companion object {
        // 有趣的提示语列表
        private val MOTIVATIONAL_MESSAGES = listOf(
            "给相册做个健美操 💪",
            "清理一下，心情更好 ✨",
            "腾出空间，迎接新照片 📸",
            "整理一下，找照片更快 🔍",
            "让相册焕然一新 🌟",
            "来场照片马拉松 🏃",
            "碎片时间，整理相册 ⏰",
            "今天也要元气满满 🌈"
        )

        /**
         * 更新单个小组件
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val repository = LocalImageRepository(context)
                val preferences = AppPreferences.getInstance(context)
                
                val imageCount = repository.getAllImages().size
                val lastKnownCount = preferences.lastKnownImageCount
                val hasNewImages = imageCount > lastKnownCount && lastKnownCount > 0
                
                // 保存当前数量
                preferences.lastKnownImageCount = imageCount
                
                withContext(Dispatchers.Main) {
                    val views = RemoteViews(context.packageName, R.layout.widget_tabula)
                    
                    // 格式化数字（添加千分位）
                    val formattedCount = NumberFormat.getNumberInstance(Locale.getDefault())
                        .format(imageCount)
                    
                    views.setTextViewText(R.id.widget_count, formattedCount)
                    
                    // 根据状态设置不同的提示语
                    val (description, cta) = when {
                        hasNewImages -> {
                            val newCount = imageCount - lastKnownCount
                            "新增 $newCount 张照片" to "快来整理一下 📸"
                        }
                        imageCount == 0 -> {
                            "相册空空如也" to "拍些照片吧 📷"
                        }
                        imageCount < 100 -> {
                            "张照片" to "相册很整洁 ✨"
                        }
                        else -> {
                            "张照片等待整理" to MOTIVATIONAL_MESSAGES.random()
                        }
                    }
                    
                    views.setTextViewText(R.id.widget_description, description)
                    views.setTextViewText(R.id.widget_cta, cta)
                    
                    // 设置点击事件 - 打开主应用
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_count, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_cta, pendingIntent)
                    
                    // 更新小组件
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        /**
         * 手动触发所有小组件更新
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, TabulaWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
