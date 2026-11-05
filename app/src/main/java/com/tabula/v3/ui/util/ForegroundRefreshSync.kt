package com.tabula.v3.ui.util

import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.repository.AlbumCleanupBatch
import com.tabula.v3.data.repository.mediaIdentityKey
import com.tabula.v3.ui.components.MonthInfo
import com.tabula.v3.ui.components.buildMonthInfos

/** Rebuild membership from the authoritative snapshot, including rows whose access returned. */
internal fun refreshedCleanupMonth(month: MonthInfo, snapshot: List<ImageFile>): MonthInfo =
    buildMonthInfos(snapshot).firstOrNull { it.year == month.year && it.month == month.month }
        ?: month.copy(imageCount = 0, coverImageId = null, coverVolumeName = null, images = emptyList())

/**
 * C4/T07 前台刷新落地（P2 复审项）：回前台重查产生的新快照（MediaStore 全量
 * 重查结果）向各可见消费面同步的纯逻辑。
 *
 * 同一口径：完整媒体身份键（卷|ID，mediaIdentityKey）——跨卷同数值 ID 行
 * 互不替代（他卷的同 ID 新行不会顶替本行，也不会被误当作本行的最新版本）；
 * 存活行整体替换为快照中的最新 ImageFile（dateModified/尺寸等随外部编辑
 * 更新），消失行剔除，顺序保持。
 */

/** 按身份键把 rows 映射到 snapshot 的最新行：存活→最新对象，失效→剔除，顺序保持。 */
internal fun syncRowsToSnapshot(rows: List<ImageFile>, snapshot: List<ImageFile>): List<ImageFile> {
    if (rows.isEmpty()) return rows
    val byKey = snapshot.associateBy { it.mediaIdentityKey() }
    return rows.mapNotNull { byKey[it.mediaIdentityKey()] }
}

/**
 * 同步后在 deck 空间内重定位当前索引（C4/T07 P2 复审 C-1 修正）。
 *
 * 以"用户正看的当前行"为锚：锚行在同步后列表中按完整媒体身份重定位——
 * 身份仍存在 → 新位置（其前有行被剔除时视图跟随同一张卡）；锚行自身外部
 * 失效 → 回退到锚位起（含）第一个存活行；其后无存活行 → 钳到末张。
 *
 * 关键约束：[deckRows] 与 [syncedDeck] 必须同属 deck 空间（用户已处理的行
 * 已被 deck 自身路径剔除）。不得以清理空间的批次行作对照——清理批仍含
 * 已处理行，两空间索引互不可算（混算会在用户删除提交时双扣导致视图回跳、
 * 已删卡片复现）。
 */
internal fun relocatedIndexAfterSync(
    deckRows: List<ImageFile>,
    syncedDeck: List<ImageFile>,
    currentIndex: Int
): Int {
    if (syncedDeck.isEmpty()) return 0
    val current = deckRows.getOrNull(currentIndex)
    if (current != null) {
        val key = current.mediaIdentityKey()
        val at = syncedDeck.indexOfFirst { it.mediaIdentityKey() == key }
        if (at >= 0) return at
    }
    val keysAtOrAfter = deckRows.drop(currentIndex).map { it.mediaIdentityKey() }.toSet()
    return syncedDeck.indexOfFirst { it.mediaIdentityKey() in keysAtOrAfter }.takeIf { it >= 0 }
        ?: syncedDeck.lastIndex
}

/**
 * Only fully accessible pending groups may be completed. Withhold an entire group
 * when any pending member disappears; its persisted directory remains available
 * for access restoration. Already reviewed/deleted rows are not pending work.
 */
internal fun syncCleanupBatchToSnapshot(
    batch: AlbumCleanupBatch,
    snapshot: List<ImageFile>,
    pendingImages: List<ImageFile> = batch.images
): AlbumCleanupBatch {
    val byKey = snapshot.associateBy { it.mediaIdentityKey() }
    val pendingKeys = pendingImages.map { it.mediaIdentityKey() }.toSet()
    val syncedImages = ArrayList<ImageFile>(batch.images.size)
    val syncedGroupIds = ArrayList<String>(batch.groupIds.size)
    val newBoundaries = ArrayList<Int>(batch.groupBoundaries.size)
    for (g in batch.groupIds.indices) {
        val start = batch.groupBoundaries[g]
        val end = if (g + 1 < batch.groupBoundaries.size) batch.groupBoundaries[g + 1] else batch.images.size
        if ((start until end).any { i ->
                val key = batch.images[i].mediaIdentityKey()
                key in pendingKeys && key !in byKey
            }) continue
        syncedGroupIds.add(batch.groupIds[g])
        newBoundaries.add(syncedImages.size)
        for (i in start until end) {
            val refreshed = byKey[batch.images[i].mediaIdentityKey()]
            if (refreshed != null) syncedImages.add(refreshed)
        }
    }
    if (syncedGroupIds == batch.groupIds && syncedImages == batch.images) return batch
    return AlbumCleanupBatch(images = syncedImages, groupIds = syncedGroupIds, groupBoundaries = newBoundaries)
}

/**
 * 查看器打开中的图片按新快照校验：身份仍存在 → 返回最新行（更新
 * dateModified/尺寸，保持查看位置由调用方以 copy(image=...) 实现）；身份
 * 已不存在（删除/权限收窄/跨卷歧义不可替代）→ 返回 null，调用方关闭查看器。
 */
internal fun refreshedViewerImage(current: ImageFile, snapshot: List<ImageFile>): ImageFile? {
    val byKey = snapshot.associateBy { it.mediaIdentityKey() }
    return byKey[current.mediaIdentityKey()]
}
