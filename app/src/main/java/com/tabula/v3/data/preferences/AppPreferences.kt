package com.tabula.v3.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tabula.v3.data.repository.LocalImageRepository

/** 一条抽取冷却记录：抽取时间 + 随机冷却天数。 */
data class PickCooldownEntry(val pickedAt: Long, val days: Int)

/**
 * C1/T03-DECKCOOLDOWN：随机抽取冷却池索引——完整媒体身份键（卷|ID）与
 * 旧裸数值 ID 双视图。新格式键形如 "external_primary|42"（非纯数字），
 * 旧格式键为纯数字 ID（升级前数据）。
 */
data class PickCooldownIndex(
    val byIdentityKey: Map<String, PickCooldownEntry>,
    val byLegacyBareId: Map<Long, PickCooldownEntry>
)

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
     * 仅调试探针使用（BuildConfig.DEBUG 门控）：强制图集详情加载失败，
     * 用于设备验证 C0-DETAIL-EMPTY-RETRY 的错误/重试 UI。生产构建不读取。
     */
    var debugForceAlbumLoadFailure: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_FORCE_ALBUM_LOAD_FAILURE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_FORCE_ALBUM_LOAD_FAILURE, value).apply()

    /**
     * 仅调试探针使用（BuildConfig.DEBUG 门控）：详情查询完成后的返回延迟毫秒，
     * 制造慢加载窗口，用于设备验证切图集/取消后旧结果不覆盖新页面（C0/T05）。
     * 0 表示无延迟；生产构建不读取。
     */
    var debugAlbumLoadDelayMs: Long
        get() = prefs.getLong(KEY_DEBUG_ALBUM_LOAD_DELAY_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_DEBUG_ALBUM_LOAD_DELAY_MS, value).apply()

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
            val value = prefs.getString(KEY_TOP_BAR_MODE, TopBarDisplayMode.DATE.name)
            return TopBarDisplayMode.valueOf(value ?: TopBarDisplayMode.DATE.name)
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
     * 卡片切换样式（切牌 / 摸牌）
     */
    var swipeStyle: SwipeStyle
        get() {
            val value = prefs.getString(KEY_SWIPE_STYLE, SwipeStyle.SHUFFLE.name)
            return try {
                SwipeStyle.valueOf(value ?: SwipeStyle.SHUFFLE.name)
            } catch (e: Exception) {
                SwipeStyle.SHUFFLE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SWIPE_STYLE, value.name).apply()
        }

    // ==================== 标签收纳设置 ====================

    /**
     * 标签选择模式（下滑自动选择 / 固定标签点击）
     */
    var tagSelectionMode: TagSelectionMode
        get() {
            val value = prefs.getString(KEY_TAG_SELECTION_MODE, TagSelectionMode.SWIPE_AUTO.name)
            return try {
                TagSelectionMode.valueOf(value ?: TagSelectionMode.SWIPE_AUTO.name)
            } catch (e: Exception) {
                TagSelectionMode.SWIPE_AUTO
            }
        }
        set(value) {
            prefs.edit().putString(KEY_TAG_SELECTION_MODE, value.name).apply()
        }

    /**
     * 标签切换速度（仅下滑自动选择模式生效）
     * 范围：0.5 ~ 2.0，默认 1.0
     * 数值越大切换越灵敏
     */
    var tagSwitchSpeed: Float
        get() = prefs.getFloat(KEY_TAG_SWITCH_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TAG_SWITCH_SPEED, value.coerceIn(0.5f, 2.0f)).apply()

    /**
     * 每行显示的标签数量（仅下滑自动选择模式生效）
     * 范围：4 ~ 10，默认 7
     */
    var tagsPerRow: Int
        get() = prefs.getInt(KEY_TAGS_PER_ROW, DEFAULT_TAGS_PER_ROW)
        set(value) = prefs.edit().putInt(KEY_TAGS_PER_ROW, value.coerceIn(4, 10)).apply()

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

    /**
     * 是否已完成引导流程
     * 首次启动时为 false，完成引导后设为 true
     */
    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value).apply()

    /**
     * 是否已确认使用前须知
     * 首次启动时为 false，确认后设为 true，不再弹出
     */
    var hasAcknowledgedDisclaimer: Boolean
        get() = prefs.getBoolean(KEY_HAS_ACKNOWLEDGED_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_ACKNOWLEDGED_DISCLAIMER, value).apply()

    // ==================== 智能排序设置 ====================

    /**
     * 智能排序总开关
     * 启用后根据使用频率自动调整图集标签排序
     * 关闭后始终使用手动排序
     */
    var smartSortEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_SORT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_SORT_ENABLED, value).apply()

    /**
     * 智能排序 - 时间衰减因子
     * 控制旧使用记录的影响力衰减速度
     * 范围：0.01（极慢衰减）~ 0.20（快速衰减），默认 0.05
     * 值越大，最近的使用行为权重越高
     */
    var smartSortTimeDecay: Float
        get() = prefs.getFloat(KEY_SMART_SORT_TIME_DECAY, 0.05f)
        set(value) = prefs.edit().putFloat(KEY_SMART_SORT_TIME_DECAY, value.coerceIn(0.01f, 0.20f)).apply()

    /**
     * 智能排序 - 连续归类抑制因子
     * 控制连续向同一图集归类时的权重衰减
     * 范围：0.3（强抑制）~ 1.0（无抑制），默认 0.7
     * 值越小，连续归类同一图集时排名提升越缓慢
     */
    var smartSortConsecutiveDecay: Float
        get() = prefs.getFloat(KEY_SMART_SORT_CONSECUTIVE_DECAY, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_SMART_SORT_CONSECUTIVE_DECAY, value.coerceIn(0.3f, 1.0f)).apply()

    /**
     * 智能排序 - 手动排序锁定天数
     * 用户手动拖拽排序后，锁定期内保持用户排序不受智能排序影响
     * 范围：1 ~ 30 天，默认 7 天
     */
    var smartSortLockDays: Int
        get() = prefs.getInt(KEY_SMART_SORT_LOCK_DAYS, 7)
        set(value) = prefs.edit().putInt(KEY_SMART_SORT_LOCK_DAYS, value.coerceIn(1, 30)).apply()

    // ==================== 图集标签设置 ====================

    /**
     * 归档后原图删除策略
     */
    var sourceImageDeletionStrategy: SourceImageDeletionStrategy
        get() {
            val value = prefs.getString(KEY_SOURCE_IMAGE_DELETION_STRATEGY, SourceImageDeletionStrategy.ASK_EVERY_TIME.name)
            return try {
                SourceImageDeletionStrategy.valueOf(value ?: SourceImageDeletionStrategy.ASK_EVERY_TIME.name)
            } catch (e: Exception) {
                SourceImageDeletionStrategy.ASK_EVERY_TIME
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SOURCE_IMAGE_DELETION_STRATEGY, value.name).apply()
        }

    // ==================== 快捷操作按钮设置 ====================

    /**
     * 快捷操作按钮功能开关
     */
    var quickActionButtonEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUICK_ACTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUICK_ACTION_ENABLED, value).apply()

    /**
     * 快捷按钮 X 位置（0-1 百分比）
     */
    var quickActionButtonX: Float
        get() = prefs.getFloat(KEY_QUICK_ACTION_BTN_X, 0.70f)
        set(value) = prefs.edit().putFloat(KEY_QUICK_ACTION_BTN_X, value.coerceIn(0f, 1f)).apply()

    /**
     * 快捷按钮 Y 位置（0-1 百分比）
     */
    var quickActionButtonY: Float
        get() = prefs.getFloat(KEY_QUICK_ACTION_BTN_Y, 0.55f)
        set(value) = prefs.edit().putFloat(KEY_QUICK_ACTION_BTN_Y, value.coerceIn(0f, 1f)).apply()

    /**
     * 重置快捷按钮位置为默认值
     */
    fun resetQuickActionButtonPosition() {
        quickActionButtonX = 0.70f
        quickActionButtonY = 0.55f
    }

    // 照片抽取时间戳和冷却天数记录（用于随机漫步模式冷却期逻辑）
    private val pickTimestampsPrefs: SharedPreferences = context.getSharedPreferences(
        PICK_TIMESTAMPS_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    // 相似组冷却记录（用于相似推荐模式，独立于随机漫步模式）
    private val similarGroupsPrefs: SharedPreferences = context.getSharedPreferences(
        SIMILAR_GROUPS_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    // 图集清理状态记录
    private val albumCleanupPrefs: SharedPreferences = context.getSharedPreferences(
        ALBUM_CLEANUP_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 可选的冷却天数（随机分配）- 用于随机漫步模式
     */
    private val cooldownOptions = listOf(7, 12, 24)
    
    /**
     * 相似组冷却天数选项 - 相似模式的冷却期较短
     */
    private val similarGroupCooldownOptions = listOf(3, 5, 7)

    /**
     * 批量记录照片被抽取的时间（C1/T03-DECKCOOLDOWN：完整媒体身份键），
     * 并随机分配冷却天数
     */
    fun recordImagesPickedByIdentity(identityKeys: Collection<String>) {
        if (identityKeys.isEmpty()) return
        val editor = pickTimestampsPrefs.edit()
        val now = System.currentTimeMillis()

        identityKeys.forEach { key ->
            val randomCooldownDays = cooldownOptions.random()
            editor.putLong("time_$key", now)
            editor.putInt("days_$key", randomCooldownDays)
        }

        editor.apply()
    }

    /**
     * C1/T03-DECKCOOLDOWN 旧数据迁移：裸数值 ID 记录在调用方确认当前媒体
     * 集合内唯一可解析后，改写为完整媒体身份键。put+remove 同一 editor
     * 单事务，中断后重跑幂等；身份键已存在时保留新记录、仅清裸键。
     *
     * @return 是否发生改写
     */
    fun migratePickRecordToIdentity(bareId: Long, identityKey: String): Boolean {
        val pickedAt = pickTimestampsPrefs.getLong("time_$bareId", 0L)
        if (pickedAt == 0L) return false
        val days = pickTimestampsPrefs.getInt("days_$bareId", cooldownOptions.first())
        val editor = pickTimestampsPrefs.edit()
        if (!pickTimestampsPrefs.contains("time_$identityKey")) {
            editor.putLong("time_$identityKey", pickedAt)
            editor.putInt("days_$identityKey", days)
        }
        editor.remove("time_$bareId")
        editor.remove("days_$bareId")
        editor.apply()
        return true
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
     * 批量读取当前冷却池（C1/T03-DECKCOOLDOWN）。
     * 一次性读取所有记录，避免多次单独读取 SharedPreferences；
     * 新格式身份键与旧裸数值键分别归入双视图，过期记录不返回。
     */
    fun getPickCooldownIndex(): PickCooldownIndex {
        val now = System.currentTimeMillis()
        val byIdentity = mutableMapOf<String, PickCooldownEntry>()
        val byLegacy = mutableMapOf<Long, PickCooldownEntry>()

        pickTimestampsPrefs.all.forEach { (key, value) ->
            if (!key.startsWith("time_") || value !is Long) return@forEach
            val suffix = key.removePrefix("time_")
            val cooldownDays = pickTimestampsPrefs.getInt("days_$suffix", cooldownOptions.first())
            val cooldownMillis = cooldownDays.toLong() * 24 * 60 * 60 * 1000
            if (now - value >= cooldownMillis) return@forEach
            val entry = PickCooldownEntry(value, cooldownDays)
            val bareId = suffix.toLongOrNull()
            if (bareId != null) byLegacy[bareId] = entry else byIdentity[suffix] = entry
        }

        return PickCooldownIndex(byIdentity, byLegacy)
    }

    /**
     * 从冷却池中移除指定身份键的照片
     * 用于切换推荐算法时，将未浏览的照片从冷却池中移除
     *
     * 注意：使用 commit() 而非 apply() 以确保移除操作在返回前完成，
     * 避免与后续的批次获取产生竞态条件。
     *
     * @param identityKeys 要移除的照片完整媒体身份键列表
     */
    fun removeImagesFromCooldownByIdentity(identityKeys: Collection<String>) {
        if (identityKeys.isEmpty()) return
        val editor = pickTimestampsPrefs.edit()

        identityKeys.forEach { key ->
            editor.remove("time_$key")
            editor.remove("days_$key")
        }

        // 使用 commit() 同步执行，确保移除完成后再返回
        // 这样后续获取新批次时，这些照片不会被排除
        editor.commit()
    }
    
    /**
     * 清除所有照片冷却记录
     * 
     * 当总照片数量 <= batchSize 时调用，避免少量照片场景下
     * 所有照片都进入冷却池导致无限循环推送同一批照片。
     */
    fun clearAllPickRecords() {
        pickTimestampsPrefs.edit().clear().commit()
    }
    
    // ==================== 相似组冷却机制（独立于随机漫步模式）====================
    
    /**
     * 记录相似组已被处理
     * 
     * @param groupId 相似组的唯一标识
     */
    fun recordSimilarGroupProcessed(groupId: String) {
        val randomCooldownDays = similarGroupCooldownOptions.random()
        similarGroupsPrefs.edit()
            .putLong("time_$groupId", System.currentTimeMillis())
            .putInt("days_$groupId", randomCooldownDays)
            .apply()
    }
    
    /**
     * 检查相似组是否在冷却期内
     * 
     * @param groupId 相似组的唯一标识
     * @return true 如果在冷却期内
     */
    fun isSimilarGroupInCooldown(groupId: String): Boolean {
        val processedTime = similarGroupsPrefs.getLong("time_$groupId", 0L)
        if (processedTime == 0L) return false
        
        val cooldownDays = similarGroupsPrefs.getInt("days_$groupId", similarGroupCooldownOptions.first())
        val cooldownMillis = cooldownDays.toLong() * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - processedTime < cooldownMillis
    }
    
    /**
     * 获取当前在冷却期内的相似组ID集合
     * 
     * @return 冷却中的组ID集合
     */
    fun getSimilarGroupCooldownIds(): Set<String> {
        val now = System.currentTimeMillis()
        val cooldownIds = mutableSetOf<String>()
        
        similarGroupsPrefs.all.forEach { (key, value) ->
            if (key.startsWith("time_") && value is Long) {
                val groupId = key.removePrefix("time_")
                val cooldownDays = similarGroupsPrefs.getInt("days_$groupId", similarGroupCooldownOptions.first())
                val cooldownMillis = cooldownDays.toLong() * 24 * 60 * 60 * 1000
                
                if (now - value < cooldownMillis) {
                    cooldownIds.add(groupId)
                }
            }
        }
        
        return cooldownIds
    }
    
    /**
     * 清理过期的相似组冷却记录
     * 使用最大冷却期（7天）来判断是否过期
     */
    fun cleanupExpiredSimilarGroupRecords() {
        val maxCooldownMillis = 7L * 24 * 60 * 60 * 1000 // 7天
        val now = System.currentTimeMillis()
        val editor = similarGroupsPrefs.edit()
        val keysToRemove = mutableListOf<String>()
        
        similarGroupsPrefs.all.forEach { (key, value) ->
            if (key.startsWith("time_") && value is Long && now - value > maxCooldownMillis) {
                val groupId = key.removePrefix("time_")
                keysToRemove.add("time_$groupId")
                keysToRemove.add("days_$groupId")
            }
        }
        
        keysToRemove.forEach { editor.remove(it) }
        editor.apply()
    }
    
    // ==================== 图集清理模式 ====================
    
    /**
     * 图集清理显示模式（组 / 张）
     */
    var albumCleanupDisplayMode: AlbumCleanupDisplayMode
        get() {
            val value = prefs.getString(KEY_ALBUM_CLEANUP_DISPLAY_MODE, AlbumCleanupDisplayMode.GROUPS.name)
            return try {
                AlbumCleanupDisplayMode.valueOf(value ?: AlbumCleanupDisplayMode.GROUPS.name)
            } catch (e: Exception) {
                AlbumCleanupDisplayMode.GROUPS
            }
        }
        set(value) {
            prefs.edit().putString(KEY_ALBUM_CLEANUP_DISPLAY_MODE, value.name).apply()
        }
    
    /**
     * 当前正在清理的图集ID
     * null 表示全局整理模式
     */
    var currentCleanupAlbumId: String?
        get() = prefs.getString(KEY_CURRENT_CLEANUP_ALBUM_ID, null)
        set(value) {
            if (value != null) {
                prefs.edit().putString(KEY_CURRENT_CLEANUP_ALBUM_ID, value).apply()
            } else {
                prefs.edit().remove(KEY_CURRENT_CLEANUP_ALBUM_ID).apply()
            }
        }
    
    /**
     * 保存图集分析结果
     * 
     * @param albumId 图集ID
     * @param totalGroups 总组数
     * @param groupIds 所有组的ID列表（JSON数组格式）
     */
    fun saveAlbumAnalysisResult(albumId: String, totalGroups: Int, groupIds: List<String>) {
        albumCleanupPrefs.edit()
            .putInt("total_$albumId", totalGroups)
            .putStringSet("groups_$albumId", groupIds.toSet())
            .putLong("analyzed_$albumId", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * 保存图集的总照片数（涉及相似组的照片）
     * 
     * @param albumId 图集ID
     * @param totalImages 总照片数
     */
    fun saveAlbumTotalImages(albumId: String, totalImages: Int) {
        albumCleanupPrefs.edit()
            .putInt("total_images_$albumId", totalImages)
            .apply()
    }
    
    /**
     * 获取图集的总照片数
     * 
     * @param albumId 图集ID
     * @return 总照片数，未分析返回 0
     */
    fun getAlbumTotalImages(albumId: String): Int {
        return albumCleanupPrefs.getInt("total_images_$albumId", 0)
    }
    
    /**
     * 保存组的照片数量
     * 
     * @param albumId 图集ID
     * @param groupId 组ID
     * @param imageCount 照片数量
     */
    fun saveGroupImageCount(albumId: String, groupId: String, imageCount: Int) {
        albumCleanupPrefs.edit()
            .putInt("group_size_${albumId}_$groupId", imageCount)
            .apply()
    }
    
    /**
     * 批量保存组的照片数量
     * 
     * @param albumId 图集ID
     * @param groupImageCounts 组ID到照片数量的映射
     */
    fun saveGroupImageCounts(albumId: String, groupImageCounts: Map<String, Int>) {
        val editor = albumCleanupPrefs.edit()
        groupImageCounts.forEach { (groupId, count) ->
            editor.putInt("group_size_${albumId}_$groupId", count)
        }
        editor.apply()
    }
    
    /**
     * 获取组的照片数量
     * 
     * @param albumId 图集ID
     * @param groupId 组ID
     * @return 照片数量
     */
    fun getGroupImageCount(albumId: String, groupId: String): Int {
        return albumCleanupPrefs.getInt("group_size_${albumId}_$groupId", 0)
    }
    
    /**
     * 获取图集的剩余照片数
     * 
     * @param albumId 图集ID
     * @return 剩余未处理的照片数
     */
    fun getAlbumRemainingImages(albumId: String): Int {
        val groupIds = getAlbumGroupIds(albumId)
        val permanentlyProcessed = getAlbumPermanentlyProcessedGroups(albumId)
        
        // 计算未处理组的照片数之和
        return groupIds
            .filter { it !in permanentlyProcessed }
            .sumOf { groupId -> getGroupImageCount(albumId, groupId) }
    }
    
    /**
     * 获取图集的总组数
     * 
     * @param albumId 图集ID
     * @return 总组数，未分析返回 -1
     */
    fun getAlbumTotalGroups(albumId: String): Int {
        return albumCleanupPrefs.getInt("total_$albumId", -1)
    }
    
    /**
     * 获取图集的所有组ID
     * 
     * @param albumId 图集ID
     * @return 组ID集合
     */
    fun getAlbumGroupIds(albumId: String): Set<String> {
        return albumCleanupPrefs.getStringSet("groups_$albumId", emptySet()) ?: emptySet()
    }
    
    /**
     * 获取图集的已处理组数
     * 
     * @param albumId 图集ID
     * @return 已处理的组数
     */
    fun getAlbumProcessedGroups(albumId: String): Int {
        val groupIds = getAlbumGroupIds(albumId)
        val permanentlyProcessed = getAlbumPermanentlyProcessedGroups(albumId)
        // 使用永久标记计算已处理的组数
        return groupIds.count { it in permanentlyProcessed }
    }
    
    /**
     * 获取图集中已永久处理的组ID集合
     * 
     * @param albumId 图集ID
     * @return 已永久处理的组ID集合
     */
    fun getAlbumPermanentlyProcessedGroups(albumId: String): Set<String> {
        return albumCleanupPrefs.getStringSet("processed_$albumId", emptySet()) ?: emptySet()
    }
    
    /**
     * 永久标记图集中的组为已处理
     * 
     * @param albumId 图集ID
     * @param groupId 组ID
     */
    fun markAlbumGroupPermanentlyProcessed(albumId: String, groupId: String) {
        val processed = getAlbumPermanentlyProcessedGroups(albumId).toMutableSet()
        processed.add(groupId)
        albumCleanupPrefs.edit()
            .putStringSet("processed_$albumId", processed)
            .apply()
    }
    
    /**
     * 整体替换图集的永久处理标记集合（C1/T03-GROUPID 旧格式标记唯一
     * 映射迁移的写回通道；歧义旧条目由调用方原样保留）。
     */
    fun replaceAlbumPermanentlyProcessedGroups(albumId: String, groupIds: Set<String>) {
        albumCleanupPrefs.edit()
            .putStringSet("processed_$albumId", groupIds)
            .apply()
    }

    /**
     * 整体替换图集的组目录（C1/T03-GROUPID 复验轮：旧格式组 ID 唯一映射
     * 迁移的组目录写回通道——groups_ 与 processed_ 口径必须一致，否则
     * 已处理数/剩余数/完成状态失真）。
     */
    fun replaceAlbumGroupIds(albumId: String, groupIds: Set<String>) {
        albumCleanupPrefs.edit()
            .putStringSet("groups_$albumId", groupIds)
            .apply()
    }

    /**
     * 组成员持久化键改名（C1/T03-GROUPID 复验轮：group_images_ 与组目录
     * 同步迁移；源键不存在时为无操作）。
     */
    fun renameGroupMembersKey(albumId: String, fromGroupId: String, toGroupId: String) {
        val members = albumCleanupPrefs.getString("group_images_${albumId}_$fromGroupId", null)
            ?: return
        albumCleanupPrefs.edit()
            .putString("group_images_${albumId}_$toGroupId", members)
            .remove("group_images_${albumId}_$fromGroupId")
            .apply()
    }

    /**
     * 组数量持久化键改名（C1/T03-GROUPID 复验轮：group_size_ 与组目录
     * 同步迁移；源键不存在时为无操作）。
     */
    fun renameGroupSizeKey(albumId: String, fromGroupId: String, toGroupId: String) {
        if (!albumCleanupPrefs.contains("group_size_${albumId}_$fromGroupId")) return
        val count = albumCleanupPrefs.getInt("group_size_${albumId}_$fromGroupId", 0)
        albumCleanupPrefs.edit()
            .putInt("group_size_${albumId}_$toGroupId", count)
            .remove("group_size_${albumId}_$fromGroupId")
            .apply()
    }

    /**
     * 批量永久标记图集中的组为已处理
     *
     * @param albumId 图集ID
     * @param groupIds 组ID列表
     */
    fun markAlbumGroupsPermanentlyProcessed(albumId: String, groupIds: List<String>) {
        val processed = getAlbumPermanentlyProcessedGroups(albumId).toMutableSet()
        processed.addAll(groupIds)
        albumCleanupPrefs.edit()
            .putStringSet("processed_$albumId", processed)
            .apply()
    }
    
    /**
     * 获取图集的剩余组数
     * 
     * @param albumId 图集ID
     * @return 剩余未处理的组数
     */
    fun getAlbumRemainingGroups(albumId: String): Int {
        val total = getAlbumTotalGroups(albumId)
        if (total <= 0) return 0
        return (total - getAlbumProcessedGroups(albumId)).coerceAtLeast(0)
    }
    
    /**
     * 检查图集是否已完成清理
     * 
     * @param albumId 图集ID
     * @return true 如果已完成
     */
    fun isAlbumCleanupCompleted(albumId: String): Boolean {
        val total = getAlbumTotalGroups(albumId)
        if (total <= 0) return false
        return getAlbumRemainingGroups(albumId) == 0
    }
    
    /**
     * 标记图集清理完成
     * 
     * @param albumId 图集ID
     */
    fun markAlbumCleanupCompleted(albumId: String) {
        val completedSet = getCompletedCleanupAlbumIds().toMutableSet()
        completedSet.add(albumId)
        albumCleanupPrefs.edit()
            .putStringSet(KEY_COMPLETED_CLEANUP_ALBUMS, completedSet)
            .apply()
    }
    
    /**
     * 获取已完成清理的图集ID集合
     * 
     * @return 已完成的图集ID集合
     */
    fun getCompletedCleanupAlbumIds(): Set<String> {
        return albumCleanupPrefs.getStringSet(KEY_COMPLETED_CLEANUP_ALBUMS, emptySet()) ?: emptySet()
    }
    
    /**
     * 重置图集的清理状态（用于重新清理）
     * 
     * @param albumId 图集ID
     */
    fun resetAlbumCleanupState(albumId: String) {
        val completedSet = getCompletedCleanupAlbumIds().toMutableSet()
        completedSet.remove(albumId)
        
        albumCleanupPrefs.edit()
            .remove("total_$albumId")
            .remove("groups_$albumId")
            .remove("analyzed_$albumId")
            .remove("processed_$albumId")  // 清除永久处理记录
            .remove("acknowledged_members_$albumId")
            .putStringSet(KEY_COMPLETED_CLEANUP_ALBUMS, completedSet)
            .apply()
    }
    
    /**
     * 清除所有图集清理状态
     */
    fun clearAllAlbumCleanupState() {
        albumCleanupPrefs.edit().clear().apply()
        currentCleanupAlbumId = null
    }
    
    // ==================== 图集清理断点续传 ====================
    
    /**
     * 保存图集清理断点
     * 
     * @param albumId 图集ID
     * @param groupIds 当前批次的组ID列表
     * @param currentIndex 当前在批次中的索引位置
     */
    fun saveAlbumCleanupCheckpoint(albumId: String, groupIds: List<String>, currentIndex: Int) {
        albumCleanupPrefs.edit()
            .putStringSet("checkpoint_groups_$albumId", groupIds.toSet())
            .putString("checkpoint_groups_order_$albumId", groupIds.joinToString(","))  // 保持顺序
            .putInt("checkpoint_index_$albumId", currentIndex)
            .putLong("checkpoint_time_$albumId", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * 获取图集清理断点的保存时刻（毫秒；C1/T03-GROUPID 复验轮三：用于
     * 判断断点保存后分析是否重跑过——重跑使旧位置索引不可确认）。
     *
     * @param albumId 图集ID
     * @return 保存时刻毫秒，无断点返回 0
     */
    fun getAlbumCleanupCheckpointSavedAt(albumId: String): Long {
        return albumCleanupPrefs.getLong("checkpoint_time_$albumId", 0L)
    }

    /**
     * 整体替换图集清理断点并**保留原保存时刻**（C1/T03-GROUPID 复验轮三：
     * 旧格式断点引用迁移的写回通道——重写引用时若刷新保存时刻，会使
     * "分析晚于断点"的时序判据在迁移入口失效）。
     */
    fun replaceAlbumCleanupCheckpoint(
        albumId: String,
        groupIds: List<String>,
        currentIndex: Int,
        savedAtMs: Long
    ) {
        albumCleanupPrefs.edit()
            .putStringSet("checkpoint_groups_$albumId", groupIds.toSet())
            .putString("checkpoint_groups_order_$albumId", groupIds.joinToString(","))
            .putInt("checkpoint_index_$albumId", currentIndex)
            .putLong("checkpoint_time_$albumId", savedAtMs)
            .apply()
    }

    /**
     * 获取图集最近一次相似组分析的时刻（毫秒；C1/T03-GROUPID 复验轮三：
     * 晚于断点保存时刻说明分析重跑过，批次组成/排序可能已变）。
     *
     * @param albumId 图集ID
     * @return 分析时刻毫秒，从未分析返回 0
     */
    fun getAlbumAnalyzedAt(albumId: String): Long {
        return albumCleanupPrefs.getLong("analyzed_$albumId", 0L)
    }

    /**
     * 获取图集清理断点
     *
     * @param albumId 图集ID
     * @return 断点信息 (组ID列表, 当前索引)，如果没有断点返回 null
     */
    fun getAlbumCleanupCheckpoint(albumId: String): Pair<List<String>, Int>? {
        val groupsOrder = albumCleanupPrefs.getString("checkpoint_groups_order_$albumId", null)
            ?: return null
        val currentIndex = albumCleanupPrefs.getInt("checkpoint_index_$albumId", 0)
        
        // 检查断点是否过期（超过7天）
        val checkpointTime = albumCleanupPrefs.getLong("checkpoint_time_$albumId", 0L)
        val maxAge = 7L * 24 * 60 * 60 * 1000  // 7天
        if (System.currentTimeMillis() - checkpointTime > maxAge) {
            clearAlbumCleanupCheckpoint(albumId)
            return null
        }
        
        val groupIds = groupsOrder.split(",").filter { it.isNotEmpty() }
        if (groupIds.isEmpty()) return null
        
        return Pair(groupIds, currentIndex)
    }
    
    /**
     * 清除图集清理断点
     * 
     * @param albumId 图集ID
     */
    fun clearAlbumCleanupCheckpoint(albumId: String) {
        albumCleanupPrefs.edit()
            .remove("checkpoint_groups_$albumId")
            .remove("checkpoint_groups_order_$albumId")
            .remove("checkpoint_index_$albumId")
            .remove("checkpoint_time_$albumId")
            .apply()
    }
    
    /** 仅记录用户明确移入回收站的完整媒体身份；权限快照缺席不得调用此接口。 */
    fun acknowledgeCleanupMember(identityKey: String) {
        acknowledgeCleanupMembers(setOf(identityKey))
    }

    fun acknowledgeCleanupMembers(identityKeys: Set<String>) {
        if (identityKeys.isEmpty()) return
        // 不同 AppPreferences 实例仍共享此对象，读并集与写入必须共用同一把锁。
        synchronized(albumCleanupPrefs) {
            val albumIds = albumCleanupPrefs.all.keys.filter { it.startsWith("groups_") }
                .map { it.removePrefix("groups_") }
            val editor = albumCleanupPrefs.edit()
            var hasConfirmedMembers = false
            for (albumId in albumIds) {
                // 不按裸 ID 猜卷；旧目录在唯一身份迁移后才能加入确认集。
                val members = getAlbumGroupIds(albumId).flatMap { groupId ->
                    getGroupImageIds(albumId, groupId).orEmpty()
                }.toSet()
                val confirmed = identityKeys.intersect(members)
                if (confirmed.isEmpty()) continue
                val acknowledged = getAlbumAcknowledgedCleanupMembers(albumId)
                editor.putStringSet("acknowledged_members_$albumId", acknowledged + confirmed)
                hasConfirmedMembers = true
            }
            // commit=false 也可能先改变内存；同值重试仍须同步落盘，不能据内存跳过。
            if (hasConfirmedMembers && !editor.commit()) {
                throw IllegalStateException("Could not persist acknowledged cleanup member")
            }
        }
    }

    fun getAlbumAcknowledgedCleanupMembers(albumId: String): Set<String> =
        albumCleanupPrefs.getStringSet("acknowledged_members_$albumId", emptySet())?.toSet() ?: emptySet()

    // ==================== 相似组图片 ID 持久化（性能优化）====================
    
    /**
     * 保存单个组的图片 ID 列表
     * 
     * @param albumId 图集ID
     * @param groupId 组ID
     * @param imageIds 组内图片 ID 列表（按顺序）
     */
    fun saveGroupImageIds(albumId: String, groupId: String, imageIds: List<Long>) {
        albumCleanupPrefs.edit()
            .putString("group_images_${albumId}_$groupId", imageIds.joinToString(","))
            .apply()
    }
    
    /**
     * 批量保存多个组的成员列表（C1/T03-GROUPID：值为完整媒体身份键
     * "卷|ID"；既有构建写入的旧格式为逗号分隔裸数字 ID，读取侧按
     * 唯一性解析兼容，见 AlbumCleanupEngine.restoreGroupsFromPrefs）。
     *
     * @param albumId 图集ID
     * @param groupImageIds 组ID到成员身份键列表的映射
     */
    fun saveGroupsImageIds(albumId: String, groupImageIds: Map<String, List<String>>) {
        val editor = albumCleanupPrefs.edit()
        groupImageIds.forEach { (groupId, memberKeys) ->
            editor.putString("group_images_${albumId}_$groupId", memberKeys.joinToString(","))
        }
        editor.apply()
    }

    /**
     * 获取单个组的成员列表（原始字符串：新格式为身份键"卷|ID"，
     * 旧格式为裸数字 ID——由调用方按身份解析，此处不做语义过滤）。
     *
     * @param albumId 图集ID
     * @param groupId 组ID
     * @return 组内成员键列表，如果不存在返回 null
     */
    fun getGroupImageIds(albumId: String, groupId: String): List<String>? {
        val idsString = albumCleanupPrefs.getString("group_images_${albumId}_$groupId", null)
            ?: return null
        return idsString.split(",").filter { it.isNotBlank() }
    }
    
    /**
     * 检查图集是否有持久化的分组数据
     * 
     * @param albumId 图集ID
     * @return true 如果有分组数据可以恢复
     */
    fun hasPersistedGroupData(albumId: String): Boolean {
        val groupIds = getAlbumGroupIds(albumId)
        if (groupIds.isEmpty()) return false
        // 检查第一个组是否有图片数据
        val firstGroupId = groupIds.firstOrNull() ?: return false
        return albumCleanupPrefs.contains("group_images_${albumId}_$firstGroupId")
    }

    // ==================== "其他图集"收纳设置 ====================

    /**
     * 用户手动从"其他图集"中取出的图集名称集合（pinned）
     * 这些图集始终显示在主图集列表中，不会被收纳
     */
    var pinnedAlbumNames: Set<String>
        get() = prefs.getStringSet(KEY_PINNED_ALBUM_NAMES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PINNED_ALBUM_NAMES, value).apply()

    /**
     * 将图集从"其他图集"中取出（pin）
     */
    fun pinAlbum(name: String) {
        val current = pinnedAlbumNames.toMutableSet()
        current.add(name)
        pinnedAlbumNames = current
    }

    /**
     * 将图集放回"其他图集"中（unpin）
     */
    fun unpinAlbum(name: String) {
        val current = pinnedAlbumNames.toMutableSet()
        current.remove(name)
        pinnedAlbumNames = current
    }

    companion object {
        private const val PREFS_NAME = "tabula_prefs"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEBUG_FORCE_ALBUM_LOAD_FAILURE = "debug_force_album_load_failure"
        private const val KEY_DEBUG_ALBUM_LOAD_DELAY_MS = "debug_album_load_delay_ms"
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
        private const val KEY_SWIPE_STYLE = "swipe_style"
        private const val KEY_LIQUID_GLASS_LAB_ENABLED = "liquid_glass_lab_enabled"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_HAS_ACKNOWLEDGED_DISCLAIMER = "has_acknowledged_disclaimer"
        
        // 快捷操作按钮相关
        private const val KEY_QUICK_ACTION_ENABLED = "quick_action_enabled"
        private const val KEY_QUICK_ACTION_BTN_X = "quick_action_btn_x"
        private const val KEY_QUICK_ACTION_BTN_Y = "quick_action_btn_y"
        
        // 标签收纳相关
        private const val KEY_TAG_SELECTION_MODE = "tag_selection_mode"
        private const val KEY_TAG_SWITCH_SPEED = "tag_switch_speed"
        private const val KEY_TAGS_PER_ROW = "tags_per_row"
        
        // 智能排序相关
        private const val KEY_SMART_SORT_ENABLED = "smart_sort_enabled"
        private const val KEY_SMART_SORT_TIME_DECAY = "smart_sort_time_decay"
        private const val KEY_SMART_SORT_CONSECUTIVE_DECAY = "smart_sort_consecutive_decay"
        private const val KEY_SMART_SORT_LOCK_DAYS = "smart_sort_lock_days"
        
        // 图集标签设置相关
        private const val KEY_SOURCE_IMAGE_DELETION_STRATEGY = "source_image_deletion_strategy"
        
        // "其他图集"收纳相关
        private const val KEY_PINNED_ALBUM_NAMES = "pinned_album_names"

        // 收纳阈值常量
        /** 系统图集总数超过此值时启用收纳 */
        const val OTHER_ALBUMS_TRIGGER_THRESHOLD = 30
        /** 图片数 <= 此值的系统图集会被收纳到"其他" */
        const val OTHER_ALBUMS_SMALL_THRESHOLD = 5
        /** 收纳比例上限：如果被收纳的图集超过总数的此比例，则提高 SMALL_THRESHOLD 直到满足 */
        const val OTHER_ALBUMS_MAX_RATIO = 0.80f

        private const val PICK_TIMESTAMPS_PREFS_NAME = "tabula_pick_timestamps"
        private const val SIMILAR_GROUPS_PREFS_NAME = "tabula_similar_groups"
        private const val ALBUM_CLEANUP_PREFS_NAME = "tabula_album_cleanup"
        
        // 图集清理相关 KEY
        private const val KEY_CURRENT_CLEANUP_ALBUM_ID = "current_cleanup_album_id"
        private const val KEY_COMPLETED_CLEANUP_ALBUMS = "completed_cleanup_albums"
        private const val KEY_ALBUM_CLEANUP_DISPLAY_MODE = "album_cleanup_display_mode"

        const val DEFAULT_BATCH_SIZE = 15
        const val DEFAULT_TAGS_PER_ROW = 7
        val BATCH_SIZE_OPTIONS = listOf(5, 10, 15, 20, 30, 50)
        val TAGS_PER_ROW_OPTIONS = listOf(4, 5, 6, 7, 8, 9, 10)
        val SMART_SORT_LOCK_DAYS_OPTIONS = listOf(1, 3, 5, 7, 14, 30)
        
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
 * 卡片切换样式枚举
 */
enum class SwipeStyle {
    SHUFFLE,    // 切牌样式 - 左右滑动循环切换，牌插入底部
    DRAW        // 摸牌样式 - 右滑发牌飞出，左滑收牌飞回
}

/**
 * 标签选择模式枚举
 */
enum class TagSelectionMode {
    SWIPE_AUTO,    // 下滑自动选择 - 下滑时标签自动切换，松手归类
    FIXED_TAP,     // 固定标签点击 - 标签固定显示，点击即归类
    LIST_POPUP     // 弹层列表选择 - 下滑弹出图集列表，点击选择
}

/**
 * 图集清理显示模式枚举
 */
enum class AlbumCleanupDisplayMode {
    GROUPS,  // 显示组数：共 X 组 · 剩余 X 组
    PHOTOS   // 显示照片数：共 X 张 · 剩余 X 张
}

/**
 * 归档后原图删除策略枚举
 */
enum class SourceImageDeletionStrategy {
    MANUAL_IN_ALBUMS,  // 老办法：在图库界面手动删除原图
    ASK_EVERY_TIME     // 每次提醒：切换到图库界面时询问是否删除原图
}

/**
 * 记住 AppPreferences 实例
 */
@Composable
fun rememberAppPreferences(): AppPreferences {
    val context = LocalContext.current
    return remember { AppPreferences(context) }
}

