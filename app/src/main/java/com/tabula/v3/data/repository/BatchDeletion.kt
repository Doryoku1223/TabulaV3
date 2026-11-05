package com.tabula.v3.data.repository

/** A rejected request has not deleted anything and can safely be split. */
internal sealed class BatchDeleteResult<out T> {
    data class Completed<T>(val deleted: Set<T>, val stopReason: String? = null) : BatchDeleteResult<T>()
    data class Failed(val message: String, val canSplit: Boolean = false) : BatchDeleteResult<Nothing>()
    data object Cancelled : BatchDeleteResult<Nothing>()
}

internal data class BatchDeleteSummary<T>(
    val deleted: Set<T>,
    val remaining: Set<T>,
    val cancelled: Boolean,
    val error: String?
)

/** Serial requests keep the system permission callback associated with its own batch. */
internal suspend fun <T> deleteInBatches(
    items: List<T>,
    batchSize: Int = 100,
    deleteBatch: suspend (List<T>) -> BatchDeleteResult<T>,
    onDeleted: suspend (Set<T>) -> Unit
): BatchDeleteSummary<T> {
    require(batchSize in 1..2000)
    val requested = items.distinct()
    val deleted = linkedSetOf<T>()
    var cancelled = false
    var error: String? = null
    var stopped = false

    suspend fun process(batch: List<T>) {
        if (cancelled || stopped) return
        when (val result = deleteBatch(batch)) {
            is BatchDeleteResult.Completed -> {
                val confirmed = result.deleted.intersect(batch.toSet())
                if (confirmed.isNotEmpty()) {
                    onDeleted(confirmed)
                    deleted.addAll(confirmed)
                }
                result.stopReason?.let { error = it; stopped = true }
            }
            is BatchDeleteResult.Failed -> {
                if (result.canSplit && batch.size > 1) {
                    val middle = batch.size / 2
                    process(batch.take(middle))
                    process(batch.drop(middle))
                } else {
                    error = result.message
                    if (!result.canSplit) stopped = true
                }
            }
            BatchDeleteResult.Cancelled -> cancelled = true
        }
    }

    for (batch in requested.chunked(batchSize)) {
        if (cancelled || stopped) break
        process(batch)
    }
    return BatchDeleteSummary(deleted, requested.toSet() - deleted, cancelled, error)
}
