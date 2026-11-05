package com.tabula.v3.data.repository

import android.net.Uri
import com.tabula.v3.data.model.ImageFile

/**
 * C1-T03-LEGACY-AGGREGATE-DELETE（升级兼容）：主分支代回收记录为「已知卷名 +
 * 聚合 external URI」。两卷同数值 ID 记录并存时该 URI 无法唯一确认身份——
 * 永久删除与对账必须先分区：确认失败的 URI 保留记录并停止操作，不得双删。
 * 规则：卷限定 URI（新代际）直接确认；聚合 URI 按引用记录的已知卷解析比对，
 * 全部引用记录卷未知（T03 前数据）保持旧行为视为确认，解析失败或卷不一致
 * 视为未确认。不重写 URI 后宣称核验成功。
 */
internal fun isAggregateMediaUri(uri: Uri): Boolean =
    uri.toString().contains("/external/")

internal suspend fun partitionByConfirmedIdentity(
    uris: List<Uri>,
    records: List<ImageFile>,
    resolveVolume: suspend (Uri) -> String?
): Pair<List<Uri>, List<Uri>> {
    val confirmed = mutableListOf<Uri>()
    val unconfirmed = mutableListOf<Uri>()
    for (uri in uris.distinct()) {
        val knownVolumes = records.filter { it.uri == uri }.mapNotNull { it.volumeName }.distinct()
        if (!isAggregateMediaUri(uri) || knownVolumes.isEmpty()) {
            confirmed += uri
            continue
        }
        val resolved = resolveVolume(uri)
        if (resolved != null && knownVolumes.all { it == resolved }) {
            confirmed += uri
        } else {
            unconfirmed += uri
        }
    }
    return confirmed to unconfirmed
}

class RecycleBinCommitException(val physicallyDeleted: Set<Uri>, cause: Exception) :
    Exception("照片已处理，但回收站记录未保存；已停止后续删除，请重试核对", cause)

/** Recovery only queries and commits records: it must never launch another delete request. */
internal suspend fun reconcileRecycleBinDeletion(
    manager: RecycleBinManager,
    resolveVolume: suspend (Uri) -> String? = { null },
    verify: suspend (List<Uri>) -> DeletionVerification
): DeletionVerification {
    val pending = manager.pendingDeletionUris()
    if (pending.isEmpty()) return DeletionVerification(emptySet(), emptySet(), emptySet())
    // 升级兼容：聚合 URI 无法唯一确认身份（跨卷同 ID 记录共享同一 URI）时
    // 归入 unknown——保留记录并保留 journal，停止本次对账提交。
    val records = manager.loadRecycleBin()
    val (confirmed, unconfirmed) = partitionByConfirmedIdentity(pending, records, resolveVolume)
    val state = verify(confirmed)
    // unconfirmed 不在 covered 内：由 journal 保留公式自动留存（记录不动、
    // journal 保留），下次对账重新核验；unknown 仅上报已核验部分。
    manager.finishDeletion(state.deleted, state.unknown, covered = confirmed.toSet())
    return DeletionVerification(
        state.deleted, state.existing, state.unknown + unconfirmed.toSet()
    )
}

/** Shared by the recycle-bin UI and recovery regression tests. */
internal suspend fun deleteRecycleBinSelection(
    uris: List<Uri>,
    manager: RecycleBinManager,
    request: suspend (List<Uri>) -> FileOperationManager.DeleteResult,
    permission: suspend (FileOperationManager.DeleteResult.NeedsPermission) -> Boolean,
    verify: suspend (List<Uri>) -> DeletionVerification,
    resolveVolume: suspend (Uri) -> String? = { null },
    onRecovered: suspend (DeletionVerification) -> Unit = {},
    onDeleted: suspend (Set<Uri>) -> Unit = {}
): BatchDeleteSummary<Uri> {
    val recovered = reconcileRecycleBinDeletion(manager, resolveVolume, verify)
    onRecovered(recovered)
    // A pending UNKNOWN has an unresolved earlier request. Only reconciliation may
    // release it; another request could also clear its journal on error or denial.
    val summary = deleteInBatches(
        items = uris.filterNot { it in recovered.deleted || it in recovered.unknown },
        deleteBatch = { batch ->
            deleteRecycleBinBatch(batch, manager, request, permission, verify, resolveVolume)
        },
        onDeleted = onDeleted
    )
    // Report only selected unresolved items, rather than every historical UNKNOWN.
    return summary.copy(remaining = summary.remaining + uris.toSet().intersect(recovered.unknown))
}

internal suspend fun deleteRecycleBinBatch(
    uris: List<Uri>,
    manager: RecycleBinManager,
    request: suspend (List<Uri>) -> FileOperationManager.DeleteResult,
    permission: suspend (FileOperationManager.DeleteResult.NeedsPermission) -> Boolean,
    verify: suspend (List<Uri>) -> DeletionVerification,
    resolveVolume: suspend (Uri) -> String? = { null }
): BatchDeleteResult<Uri> {
    // 升级兼容：聚合 URI 身份无法唯一确认（跨卷同 ID 记录共享同一 URI）时
    // 保留全部记录并停止操作——不写日志、不发删除请求。
    val (confirmedUris, unconfirmedUris) =
        partitionByConfirmedIdentity(uris, manager.loadRecycleBin(), resolveVolume)
    if (unconfirmedUris.isNotEmpty()) {
        // canSplit=true：deleteInBatches 拆分隔离后，已确认项照常删除，未确认项
        // 单独成批再次失败并留在 remaining——避免一条无法确认的死记录永久阻断
        // 整批清空；未确认记录本身保留并停止操作。
        return BatchDeleteResult.Failed(
            "部分照片身份无法确认（可能来自其他存储卷的同名项），已保留记录并停止其删除",
            canSplit = true
        )
    }
    manager.beginDeletion(confirmedUris)
    val state = when (val result = request(confirmedUris)) {
        is FileOperationManager.DeleteResult.Error -> {
            manager.finishDeletion(emptySet(), covered = confirmedUris.toSet())
            return result.asBatchFailure()
        }
        is FileOperationManager.DeleteResult.Success ->
            DeletionVerification(result.deleted, confirmedUris.toSet() - result.deleted, emptySet())
        is FileOperationManager.DeleteResult.NeedsPermission -> {
            if (!permission(result)) {
                manager.finishDeletion(emptySet(), covered = confirmedUris.toSet())
                return BatchDeleteResult.Cancelled
            }
            verify(result.pendingUris)
        }
    }
    try {
        manager.finishDeletion(state.deleted, state.unknown, covered = confirmedUris.toSet())
    } catch (e: RecycleBinPersistenceException) {
        throw RecycleBinCommitException(state.deleted, e)
    }
    return BatchDeleteResult.Completed(
        state.deleted,
        if (state.unknown.isEmpty()) null else "部分照片无法确认删除结果，请检查完整照片访问权限和存储卷后重试核对"
    )
}
