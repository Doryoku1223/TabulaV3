package com.tabula.v3.data.repository

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class MediaDeletionBatchTest {
    private fun uris(count: Int, start: Int = 1): List<Uri> =
        (start until start + count).map { Uri.parse("content://media/external/images/media/$it") }

    private fun verification(
        deleted: Set<Uri>,
        unknown: Set<Uri> = emptySet()
    ): DeletionVerification = DeletionVerification(
        deleted = deleted,
        existing = emptySet(),
        unknown = unknown
    )

    @Test
    fun partialUnknownSurvivesSummaryAndProducesActionableFeedback() = runBlocking {
        val batch = uris(3)
        val summary = deleteInBatches(batch, deleteBatch = { current ->
            deleteMediaUrisBatch(current,
                request = { FileOperationManager.DeleteResult.NeedsPermission(createIntentSender(), current) },
                permission = { true },
                verify = { verification(setOf(batch[0]), setOf(batch[1])) })
        }, onDeleted = {})
        assertEquals(setOf(batch[0]), summary.deleted)
        assertEquals(setOf(batch[1], batch[2]), summary.remaining)
        assertTrue(mediaDeletionMessage(summary, "原图").contains("2 张尚未确认删除"))
        assertTrue(mediaDeletionMessage(summary, "原图").contains("无法确认删除结果"))
    }

    @Test
    fun unknownStopsLaterBatchesAndDeliversConfirmedIncrementally() = runBlocking {
        val items = uris(5)
        val requested = mutableListOf<List<Uri>>()
        val delivered = mutableListOf<Set<Uri>>()
        val summary = deleteInBatches(items, batchSize = 2, deleteBatch = { batch ->
            requested += batch
            deleteMediaUrisBatch(batch,
                request = { FileOperationManager.DeleteResult.NeedsPermission(createIntentSender(), batch) },
                permission = { true },
                verify = {
                    if (batch.first() == items[0]) verification(batch.toSet())
                    else verification(setOf(items[2]), setOf(items[3]))
                })
        }, onDeleted = { delivered += it })
        assertEquals(listOf(items.take(2), items.subList(2, 4)), requested)
        assertEquals(listOf(items.take(2).toSet(), setOf(items[2])), delivered)
        assertEquals(items.take(3).toSet(), summary.deleted)
        assertEquals(items.drop(3).toSet(), summary.remaining)
        assertTrue(mediaDeletionMessage(summary, "原图").contains("含未处理项"))
    }

    @Test
    fun cancelledSummaryReportsCancelledAndRetainsAll() {
        val batch = uris(2)
        val summary = BatchDeleteSummary(emptySet<Uri>(), batch.toSet(), true, null)
        assertTrue(mediaDeletionMessage(summary, "图片").contains("操作已取消"))
        assertEquals(batch.toSet(), summary.remaining)
    }

    @Test
    fun grantedPermissionCompletesAllVerifiedMissing() = runBlocking {
        val batch = uris(3)
        val result = deleteMediaUrisBatch(
            batch,
            request = { FileOperationManager.DeleteResult.NeedsPermission(createIntentSender(), batch) },
            permission = { true },
            verify = { verification(batch.toSet()) }
        )

        assertTrue(result is BatchDeleteResult.Completed)
        assertEquals(batch.toSet(), (result as BatchDeleteResult.Completed).deleted)
        assertNull(result.stopReason)
    }

    @Test
    fun permissionDeniedCancelsWithoutSideEffects() = runBlocking {
        var requestCount = 0
        val result = deleteMediaUrisBatch(
            uris(2),
            request = { requestCount++; FileOperationManager.DeleteResult.NeedsPermission(createIntentSender(), emptyList()) },
            permission = { false },
            verify = { fail("verify must not run when permission denied"); verification(emptySet()) }
        )

        assertTrue(result is BatchDeleteResult.Cancelled)
        assertEquals(1, requestCount)
    }

    @Test
    fun unknownItemsAreExcludedAndReportedInStopReason() = runBlocking {
        val batch = uris(3)
        val unknown = setOf(batch[2])

        val result = deleteMediaUrisBatch(
            batch,
            request = { FileOperationManager.DeleteResult.NeedsPermission(createIntentSender(), batch) },
            permission = { true },
            verify = { verification(batch.toSet() - unknown, unknown) }
        )

        val completed = result as BatchDeleteResult.Completed
        assertEquals(setOf(batch[0], batch[1]), completed.deleted)
        assertNotNull(completed.stopReason)
    }

    @Test
    fun preRStyleSuccessReportsOnlyConfirmedItems() = runBlocking {
        val batch = uris(3)
        val confirmed = setOf(batch[0], batch[1])

        val result = deleteMediaUrisBatch(
            batch,
            request = { FileOperationManager.DeleteResult.Success(confirmed) },
            permission = { fail("permission must not run on direct-delete success"); true },
            verify = { fail("verify must not run on direct-delete success"); verification(emptySet()) }
        )

        assertEquals(confirmed, (result as BatchDeleteResult.Completed).deleted)
    }

    @Test
    fun onlyInvalidRequestFailuresAreSplittable() = runBlocking {
        val invalid = deleteMediaUrisBatch(
            uris(1),
            request = { FileOperationManager.DeleteResult.Error("bad request", DeleteFailureKind.INVALID_REQUEST) },
            permission = { true },
            verify = { verification(emptySet()) }
        )
        val service = deleteMediaUrisBatch(
            uris(1),
            request = { FileOperationManager.DeleteResult.Error("service down", DeleteFailureKind.SERVICE) },
            permission = { true },
            verify = { verification(emptySet()) }
        )

        assertTrue((invalid as BatchDeleteResult.Failed).canSplit)
        assertTrue(!(service as BatchDeleteResult.Failed).canSplit)
    }

    @Test
    fun serialBatchesPreserveConfirmedItemsWhenLaterBatchFails() = runBlocking {
        // TC17/TC19：200+ 项分批，第二批服务错误 → 已确认第一批保留，不盲目重试。
        val all = uris(250)
        val confirmedFirstBatch = all.take(100).toSet()
        var requestCount = 0

        val summary = deleteInBatches(
            items = all,
            batchSize = 100,
            deleteBatch = { batch ->
                requestCount++
                if (requestCount == 1) {
                    BatchDeleteResult.Completed(batch.toSet())
                } else {
                    BatchDeleteResult.Failed("service down", canSplit = false)
                }
            },
            onDeleted = { }
        )

        assertEquals(2, requestCount)
        assertEquals(confirmedFirstBatch, summary.deleted)
        assertNotNull(summary.error)
        assertEquals(150, summary.remaining.size)
    }

    @Test
    fun cancelledBatchStopsRequestingFurtherBatches() = runBlocking {
        val all = uris(250)
        var requestCount = 0

        val summary = deleteInBatches(
            items = all,
            batchSize = 100,
            deleteBatch = { batch ->
                requestCount++
                if (requestCount == 1) {
                    deleteMediaUrisBatch(
                        batch,
                        request = { FileOperationManager.DeleteResult.NeedsPermission(createIntentSender(), emptyList()) },
                        permission = { false },
                        verify = { verification(emptySet()) }
                    )
                } else {
                    fail("must not request batches after cancellation")
                    BatchDeleteResult.Cancelled
                }
            },
            onDeleted = { }
        )

        assertEquals(1, requestCount)
        assertTrue(summary.cancelled)
        assertEquals(250, summary.remaining.size)
    }

    @Test
    fun twoThousandOneItemsSplitThroughProductionAdapterKeepingPartialSuccess() = runBlocking {
        // TC17：2001 项经生产协调组合（deleteInBatches + deleteMediaUrisBatch，
        // 即 performDeleteBatch/performDeleteUris 的实际形态）分批：每请求 ≤100 项、
        // 逐批授权核验、已确认 2000 项按批保留，不把整体结果当每项结果。
        // 本测试失败批即末批，"SERVICE 错误停止后续批"与"SERVICE 不可拆分"
        // 分别由 BatchDeletionTest.globalFailureStopsAllLaterBatches（10000 项
        // calls==1）与 onlyInvalidRequestFailuresAreSplittable /
        // RecycleBinDeletionTest.globalErrorsAreNotSplitAndStopAfterOneRequest
        // 在套件层钉死，此处不再空真重复。
        val all = uris(2001)
        val requests = mutableListOf<List<Uri>>()
        val delivered = mutableListOf<Set<Uri>>()

        val summary = deleteInBatches(
            items = all,
            deleteBatch = { batch ->
                deleteMediaUrisBatch(
                    batch,
                    request = { uris ->
                        requests += uris
                        if (uris.size == 1) {
                            // 末批（1 项）服务错误：分类为不可拆分失败并停止。
                            FileOperationManager.DeleteResult.Error("删除服务不可用", DeleteFailureKind.SERVICE)
                        } else {
                            FileOperationManager.DeleteResult.NeedsPermission(createIntentSender(), uris)
                        }
                    },
                    permission = { true },
                    verify = { verification(it.toSet()) }
                )
            },
            onDeleted = { delivered += it }
        )

        assertEquals(21, requests.size)
        assertEquals((1..20).map { 100 }, requests.take(20).map { it.size })
        assertEquals(1, requests.last().size)
        assertEquals(all, requests.flatten())
        assertEquals(all.dropLast(1).toSet(), summary.deleted)
        assertEquals(setOf(all[2000]), summary.remaining)
        assertEquals(false, summary.cancelled)
        assertEquals("删除服务不可用", summary.error)
        // 按项结果逐批更新 UI/持久状态：20 次增量交付，合计前 2000 项。
        assertEquals(20, delivered.size)
        assertEquals(all.dropLast(1), delivered.flatten())
        assertEquals(2000, delivered.flatten().toSet().size)
        assertTrue(mediaDeletionMessage(summary, "图片").contains("已确认删除 2000 张图片"))
        assertTrue(mediaDeletionMessage(summary, "图片").contains("删除服务不可用"))
    }

    private fun createIntentSender(): android.content.IntentSender {
        val pending = android.app.PendingIntent.getActivity(
            RuntimeEnvironment.getApplication(),
            0,
            android.content.Intent("android.intent.action.VIEW"),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return pending.intentSender
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)
}
