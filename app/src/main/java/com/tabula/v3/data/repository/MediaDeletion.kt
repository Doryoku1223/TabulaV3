package com.tabula.v3.data.repository

import android.net.Uri

/**
 * T04/C2：媒体 URI 批次适配器（无回收站记录的删除入口用）。
 *
 * 与 deleteRecycleBinBatch 同构：错误分类透传（仅 INVALID_REQUEST 可拆分）、
 * 授权拒绝 → Cancelled（不请求后续批次）、授权后逐项核验 PRESENT/MISSING/UNKNOWN，
 * UNKNOWN 不冒充已删除，经 stopReason 如实反馈。
 */
internal suspend fun deleteMediaUrisBatch(
    uris: List<Uri>,
    request: suspend (List<Uri>) -> FileOperationManager.DeleteResult,
    permission: suspend (FileOperationManager.DeleteResult.NeedsPermission) -> Boolean,
    verify: suspend (List<Uri>) -> DeletionVerification
): BatchDeleteResult<Uri> {
    if (uris.isEmpty()) return BatchDeleteResult.Completed(emptySet())
    return when (val result = request(uris)) {
        is FileOperationManager.DeleteResult.Error -> result.asBatchFailure()
        is FileOperationManager.DeleteResult.Success ->
            // API<30 逐项直接删除：只上报确认删除的项，未确认项保留原位。
            BatchDeleteResult.Completed(result.deleted)
        is FileOperationManager.DeleteResult.NeedsPermission -> {
            if (!permission(result)) {
                return BatchDeleteResult.Cancelled
            }
            val state = verify(result.pendingUris)
            BatchDeleteResult.Completed(
                state.deleted,
                if (state.unknown.isEmpty()) null
                else "部分照片无法确认删除结果，请检查完整照片访问权限和存储卷后重试核对"
            )
        }
    }
}

/** 汇报已确认结果，同时保留取消/未知/失败的原因。 */
internal fun mediaDeletionMessage(summary: BatchDeleteSummary<Uri>, label: String): String {
    val done = "已确认删除 ${summary.deleted.size} 张$label"
    val remaining = if (summary.remaining.isEmpty()) "" else "，${summary.remaining.size} 张尚未确认删除（含未处理项）"
    val reason = when {
        summary.cancelled -> "；操作已取消"
        summary.error != null -> "；${summary.error}"
        else -> ""
    }
    return done + remaining + reason
}
