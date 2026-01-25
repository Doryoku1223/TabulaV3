package com.tabula.v3.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tabula.v3.data.repository.LocalImageRepository

/**
 * 应用设置管理器
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 排序方式
     */
    var sortOrder: LocalImageRepository.SortOrder
        get() {
            val value = prefs.getString(KEY_SORT_ORDER, SortOrderValue.DATE_DESC.name)
            return when (SortOrderValue.valueOf(value ?: SortOrderValue.DATE_DESC.name)) {
                SortOrderValue.DATE_DESC -> LocalImageRepository.SortOrder.DATE_MODIFIED_DESC
                SortOrderValue.DATE_ASC -> LocalImageRepository.SortOrder.DATE_MODIFIED_ASC
                SortOrderValue.NAME_ASC -> LocalImageRepository.SortOrder.NAME_ASC
                SortOrderValue.NAME_DESC -> LocalImageRepository.SortOrder.NAME_DESC
                SortOrderValue.SIZE_DESC -> LocalImageRepository.SortOrder.SIZE_DESC
                SortOrderValue.SIZE_ASC -> LocalImageRepository.SortOrder.SIZE_ASC
            }
        }
        set(value) {
            val enumValue = when (value) {
                LocalImageRepository.SortOrder.DATE_MODIFIED_DESC -> SortOrderValue.DATE_DESC
                LocalImageRepository.SortOrder.DATE_MODIFIED_ASC -> SortOrderValue.DATE_ASC
                LocalImageRepository.SortOrder.NAME_ASC -> SortOrderValue.NAME_ASC
                LocalImageRepository.SortOrder.NAME_DESC -> SortOrderValue.NAME_DESC
                LocalImageRepository.SortOrder.SIZE_DESC -> SortOrderValue.SIZE_DESC
                LocalImageRepository.SortOrder.SIZE_ASC -> SortOrderValue.SIZE_ASC
            }
            prefs.edit().putString(KEY_SORT_ORDER, enumValue.name).apply()
        }

    /**
     * 主题模式
     */
    var themeMode: ThemeMode
        get() {
            val value = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            return ThemeMode.valueOf(value ?: ThemeMode.SYSTEM.name)
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    /**
     * 是否显示删除确认
     */
    var showDeleteConfirm: Boolean
        get() = prefs.getBoolean(KEY_DELETE_CONFIRM, true)
        set(value) = prefs.edit().putBoolean(KEY_DELETE_CONFIRM, value).apply()

    /**
     * 一组显示的照片数量（默认 15 张）
     */
    var batchSize: Int
        get() = prefs.getInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE)
        set(value) = prefs.edit().putInt(KEY_BATCH_SIZE, value.coerceIn(5, 50)).apply()

    /**
     * 顶部栏显示模式（索引 或 时间）
     */
    var topBarDisplayMode: TopBarDisplayMode
        get() {
            val value = prefs.getString(KEY_TOP_BAR_MODE, TopBarDisplayMode.INDEX.name)
            return TopBarDisplayMode.valueOf(value ?: TopBarDisplayMode.INDEX.name)
        }
        set(value) {
            prefs.edit().putString(KEY_TOP_BAR_MODE, value.name).apply()
        }

    /**
     * 累计已查看照片数
     */
    var totalReviewedCount: Long
        get() = prefs.getLong(KEY_TOTAL_REVIEWED, 0L)
        set(value) = prefs.edit().putLong(KEY_TOTAL_REVIEWED, value).apply()

    /**
     * 累计已删除照片数 (包括移入回收站)
     */
    var totalDeletedCount: Long
        get() = prefs.getLong(KEY_TOTAL_DELETED, 0L)
        set(value) = prefs.edit().putLong(KEY_TOTAL_DELETED, value).apply()

    companion object {
        private const val PREFS_NAME = "tabula_prefs"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DELETE_CONFIRM = "delete_confirm"
        private const val KEY_BATCH_SIZE = "batch_size"
        private const val KEY_TOP_BAR_MODE = "top_bar_mode"
        private const val KEY_TOTAL_REVIEWED = "total_reviewed"
        private const val KEY_TOTAL_DELETED = "total_deleted"


        const val DEFAULT_BATCH_SIZE = 15
        val BATCH_SIZE_OPTIONS = listOf(5, 10, 15, 20, 30, 50)
    }

    /**
     * 排序方式枚举（用于存储）
     */
    private enum class SortOrderValue {
        DATE_DESC, DATE_ASC,
        NAME_ASC, NAME_DESC,
        SIZE_DESC, SIZE_ASC
    }
}

/**
 * 主题模式枚举
 */
enum class ThemeMode {
    SYSTEM,  // 跟随系统
    LIGHT,   // 浅色
    DARK     // 深色
}

/**
 * 顶部栏显示模式枚举
 */
enum class TopBarDisplayMode {
    INDEX,    // 显示 x/xx 索引
    DATE      // 显示 2023 Jul 日期
}

/**
 * 记住 AppPreferences 实例
 */
@Composable
fun rememberAppPreferences(): AppPreferences {
    val context = LocalContext.current
    return remember { AppPreferences(context) }
}

