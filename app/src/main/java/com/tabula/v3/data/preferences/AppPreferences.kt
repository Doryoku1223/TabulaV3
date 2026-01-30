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
     * 注意：LIQUID_GLASS 已移至实验室，如果用户之前选择了此项，自动降级为 SYSTEM
     */
    var themeMode: ThemeMode
        get() {
            val value = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            val mode = try {
                ThemeMode.valueOf(value ?: ThemeMode.SYSTEM.name)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
            // LIQUID_GLASS 已移至实验室，降级为 SYSTEM
            return if (mode == ThemeMode.LIQUID_GLASS) ThemeMode.SYSTEM else mode
        }
        set(value) {
            // 不允许直接设置 LIQUID_GLASS，使用 liquidGlassLabEnabled 代替
            val safeValue = if (value == ThemeMode.LIQUID_GLASS) ThemeMode.SYSTEM else value
            prefs.edit().putString(KEY_THEME_MODE, safeValue.name).apply()
        }

    /**
     * 是否显示删除确认
     */
    var showDeleteConfirm: Boolean
        get() = prefs.getBoolean(KEY_DELETE_CONFIRM, true)
        set(value) = prefs.edit().putBoolean(KEY_DELETE_CONFIRM, value).apply()

    /**
     * Show HDR badges
     */
    var showHdrBadges: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HDR_BADGES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HDR_BADGES, value).apply()

    /**
     * Show Live photo badges
     */
    var showMotionBadges: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MOTION_BADGES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MOTION_BADGES, value).apply()

    /**
     * Play Live photo sound
     */
    var playMotionSound: Boolean
        get() = prefs.getBoolean(KEY_PLAY_MOTION_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_PLAY_MOTION_SOUND, value).apply()

    /**
     * Live photo sound volume (0-100)
     */
    var motionSoundVolume: Int
        get() = prefs.getInt(KEY_MOTION_SOUND_VOLUME, 100)
        set(value) = prefs.edit().putInt(KEY_MOTION_SOUND_VOLUME, value.coerceIn(0, 100)).apply()

    /**
     * Global haptic enable
     */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    /**
     * Haptic strength (0-100)
     */
    var hapticStrength: Int
        get() = prefs.getInt(KEY_HAPTIC_STRENGTH, 70)
        set(value) = prefs.edit().putInt(KEY_HAPTIC_STRENGTH, value.coerceIn(0, 100)).apply()

    /**
     * Swipe card haptics enable
     */
    var swipeHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_HAPTICS_ENABLED, value).apply()

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

    /**
     * 推荐模式（随机漫步 / 相似推荐）
     */
    var recommendMode: RecommendMode
        get() {
            val value = prefs.getString(KEY_RECOMMEND_MODE, RecommendMode.RANDOM_WALK.name)
            return RecommendMode.valueOf(value ?: RecommendMode.RANDOM_WALK.name)
        }
        set(value) {
            prefs.edit().putString(KEY_RECOMMEND_MODE, value.name).apply()
        }

    /**
     * 上次已知的图片数量（用于小组件检测新增照片）
     */
    var lastKnownImageCount: Int
        get() = prefs.getInt(KEY_LAST_KNOWN_IMAGE_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_KNOWN_IMAGE_COUNT, value).apply()

    /**
     * 流体云（灵动岛）功能开关
     */
    var fluidCloudEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLUID_CLOUD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FLUID_CLOUD_ENABLED, value).apply()

    /**
     * 未完成的批次剩余数量（用于流体云显示）
     */
    var pendingBatchRemaining: Int
        get() = prefs.getInt(KEY_PENDING_BATCH_REMAINING, 0)
        set(value) = prefs.edit().putInt(KEY_PENDING_BATCH_REMAINING, value).apply()

    /**
     * 卡片样式模式（固定 / 自适应）
     */
    var cardStyleMode: CardStyleMode
        get() {
            val value = prefs.getString(KEY_CARD_STYLE_MODE, CardStyleMode.FIXED.name)
            return CardStyleMode.valueOf(value ?: CardStyleMode.FIXED.name)
        }
        set(value) {
            prefs.edit().putString(KEY_CARD_STYLE_MODE, value.name).apply()
        }

    /**
     * 液态玻璃实验室功能开关（仅在 Android 15+ 生效）
     * 注意：该功能还未完善，暂时强制关闭
     */
    var liquidGlassLabEnabled: Boolean
        get() = false // 功能未完善，强制返回 false
        set(value) {
            // 功能未完善，不保存启用状态
            // prefs.edit().putBoolean(KEY_LIQUID_GLASS_LAB_ENABLED, value).apply()
        }

    // 照片抽取时间戳和冷却天数记录（用于冷却期逻辑）
    private val pickTimestampsPrefs: SharedPreferences = context.getSharedPreferences(
        PICK_TIMESTAMPS_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 可选的冷却天数（随机分配）
     */
    private val cooldownOptions = listOf(7, 12, 24)

    /**
     * 记录照片被抽取的时间，并随机分配冷却天数
     */
    fun recordImagePicked(imageId: Long) {
        val randomCooldownDays = cooldownOptions.random()
        pickTimestampsPrefs.edit()
            .putLong("time_$imageId", System.currentTimeMillis())
            .putInt("days_$imageId", randomCooldownDays)
            .apply()
    }

    /**
     * 获取照片上次被抽取的时间，如果从未被抽取则返回0
     */
    fun getImagePickedTimestamp(imageId: Long): Long {
        return pickTimestampsPrefs.getLong("time_$imageId", 0L)
    }

    /**
     * 获取照片的冷却天数
     */
    fun getImageCooldownDays(imageId: Long): Int {
        return pickTimestampsPrefs.getInt("days_$imageId", cooldownOptions.first())
    }

    /**
     * 检查照片是否在冷却期内
     */
    fun isImageInCooldown(imageId: Long): Boolean {
        val pickedTime = getImagePickedTimestamp(imageId)
        if (pickedTime == 0L) return false
        val cooldownDays = getImageCooldownDays(imageId)
        val cooldownMillis = cooldownDays.toLong() * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - pickedTime < cooldownMillis
    }

    /**
     * 清理过期的抽取记录
     * 使用最大冷却期（24天）来判断是否过期
     */
    fun cleanupExpiredPickRecords() {
        val maxCooldownMillis = 24L * 24 * 60 * 60 * 1000 // 24天
        val now = System.currentTimeMillis()
        val editor = pickTimestampsPrefs.edit()
        val keysToRemove = mutableListOf<String>()
        
        pickTimestampsPrefs.all.forEach { (key, value) ->
            if (key.startsWith("time_") && value is Long && now - value > maxCooldownMillis) {
                val imageId = key.removePrefix("time_")
                keysToRemove.add("time_$imageId")
                keysToRemove.add("days_$imageId")
            }
        }
        
        keysToRemove.forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * 批量获取当前在冷却期内的图片ID集合
     * 一次性读取所有记录，避免多次单独读取 SharedPreferences
     */
    fun getCooldownImageIds(): Set<Long> {
        val now = System.currentTimeMillis()
        val cooldownIds = mutableSetOf<Long>()
        
        pickTimestampsPrefs.all.forEach { (key, value) ->
            if (key.startsWith("time_") && value is Long) {
                val imageIdStr = key.removePrefix("time_")
                val imageId = imageIdStr.toLongOrNull() ?: return@forEach
                val cooldownDays = pickTimestampsPrefs.getInt("days_$imageIdStr", cooldownOptions.first())
                val cooldownMillis = cooldownDays.toLong() * 24 * 60 * 60 * 1000
                
                if (now - value < cooldownMillis) {
                    cooldownIds.add(imageId)
                }
            }
        }
        
        return cooldownIds
    }

    /**
     * 批量记录照片被抽取的时间
     */
    fun recordImagesPicked(imageIds: List<Long>) {
        if (imageIds.isEmpty()) return
        val editor = pickTimestampsPrefs.edit()
        val now = System.currentTimeMillis()
        
        imageIds.forEach { imageId ->
            val randomCooldownDays = cooldownOptions.random()
            editor.putLong("time_$imageId", now)
            editor.putInt("days_$imageId", randomCooldownDays)
        }
        
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "tabula_prefs"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DELETE_CONFIRM = "delete_confirm"
        private const val KEY_SHOW_HDR_BADGES = "show_hdr_badges"
        private const val KEY_SHOW_MOTION_BADGES = "show_motion_badges"
        private const val KEY_PLAY_MOTION_SOUND = "play_motion_sound"
        private const val KEY_MOTION_SOUND_VOLUME = "motion_sound_volume"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        private const val KEY_SWIPE_HAPTICS_ENABLED = "swipe_haptics_enabled"
        private const val KEY_BATCH_SIZE = "batch_size"
        private const val KEY_TOP_BAR_MODE = "top_bar_mode"
        private const val KEY_TOTAL_REVIEWED = "total_reviewed"
        private const val KEY_TOTAL_DELETED = "total_deleted"
        private const val KEY_RECOMMEND_MODE = "recommend_mode"
        private const val KEY_LAST_KNOWN_IMAGE_COUNT = "last_known_image_count"
        private const val KEY_FLUID_CLOUD_ENABLED = "fluid_cloud_enabled"
        private const val KEY_PENDING_BATCH_REMAINING = "pending_batch_remaining"
        private const val KEY_CARD_STYLE_MODE = "card_style_mode"
        private const val KEY_LIQUID_GLASS_LAB_ENABLED = "liquid_glass_lab_enabled"

        private const val PICK_TIMESTAMPS_PREFS_NAME = "tabula_pick_timestamps"

        const val DEFAULT_BATCH_SIZE = 15
        val BATCH_SIZE_OPTIONS = listOf(5, 10, 15, 20, 30, 50)
        
        @Volatile
        private var instance: AppPreferences? = null
        
        /**
         * 获取单例实例 (用于小组件等后台服务)
         */
        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
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
    SYSTEM,       // 跟随系统
    LIGHT,        // 浅色
    DARK,         // 深色
    LIQUID_GLASS  // 液态玻璃
}

/**
 * 顶部栏显示模式枚举
 */
enum class TopBarDisplayMode {
    INDEX,    // 显示 x/xx 索引
    DATE      // 显示 2023 Jul 日期
}

/**
 * 推荐模式枚举
 */
enum class RecommendMode {
    RANDOM_WALK,    // 随机漫步 - 真正随机，带冷却期
    SIMILAR         // 相似推荐 - 优先推荐相似照片
}

/**
 * 卡片样式模式枚举
 */
enum class CardStyleMode {
    FIXED,      // 固定样式 - 3:4 比例，Crop 填充
    ADAPTIVE    // 自适应样式 - 根据图片比例动态调整
}

/**
 * 记住 AppPreferences 实例
 */
@Composable
fun rememberAppPreferences(): AppPreferences {
    val context = LocalContext.current
    return remember { AppPreferences(context) }
}

