package com.tabula.v3.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class BatchDeletionTest {
    @Test fun clears140PhotosInBoundedBatches() = runBlocking {
        val requests = mutableListOf<List<Int>>()
        val persisted = mutableSetOf<Int>()
        val summary = deleteInBatches((1..140).toList(), deleteBatch = {
            requests.add(it)
            BatchDeleteResult.Completed(it.toSet())
        }, onDeleted = { persisted.addAll(it) })
        assertEquals(listOf(100, 40), requests.map { it.size })
        assertEquals((1..140).toSet(), persisted)
        assertTrue(summary.remaining.isEmpty())
    }

    @Test fun retriesRejectedRequestsWithSmallerBatches() = runBlocking {
        val summary = deleteInBatches((1..140).toList(), deleteBatch = {
            if (it.size > 10) BatchDeleteResult.Failed("Provider rejected batch", canSplit = true)
            else BatchDeleteResult.Completed(it.toSet())
        }, onDeleted = {})
        assertEquals((1..140).toSet(), summary.deleted)
        assertNull(summary.error)
    }

    @Test fun oneInvalidItemDoesNotBlockOtherPhotos() = runBlocking {
        val persisted = mutableSetOf<Int>()
        val summary = deleteInBatches((1..140).toList(), deleteBatch = {
            if (71 in it) BatchDeleteResult.Failed("Invalid media URI", canSplit = true)
            else BatchDeleteResult.Completed(it.toSet())
        }, onDeleted = { persisted.addAll(it) })
        assertEquals(setOf(71), summary.remaining)
        assertEquals((1..140).toSet() - 71, persisted)
        assertEquals("Invalid media URI", summary.error)
    }

    @Test fun partialSuccessRemovesOnlyConfirmedPhotos() = runBlocking {
        val persisted = mutableSetOf<Int>()
        val summary = deleteInBatches(listOf(1, 2, 3), deleteBatch = {
            BatchDeleteResult.Completed(setOf(1, 3))
        }, onDeleted = { persisted.addAll(it) })
        assertEquals(setOf(1, 3), persisted)
        assertEquals(setOf(2), summary.remaining)
    }

    @Test fun cancellationPreservesCompletedWorkAndStopsLaterRequests() = runBlocking {
        var calls = 0
        val persisted = mutableSetOf<Int>()
        val summary = deleteInBatches((1..250).toList(), deleteBatch = {
            calls++
            if (calls == 2) BatchDeleteResult.Cancelled
            else BatchDeleteResult.Completed(it.toSet())
        }, onDeleted = { persisted.addAll(it) })
        assertEquals(2, calls)
        assertTrue(summary.cancelled)
        assertEquals((1..100).toSet(), persisted)
        assertEquals((101..250).toSet(), summary.remaining)
    }

    @Test fun cancellationDuringSplitStopsSiblingRequests() = runBlocking {
        val requests = mutableListOf<List<Int>>()
        val summary = deleteInBatches(listOf(1, 2, 3, 4), deleteBatch = {
            requests.add(it)
            if (it.size == 4) BatchDeleteResult.Failed("Rejected", canSplit = true)
            else BatchDeleteResult.Cancelled
        }, onDeleted = { fail("Nothing was deleted") })
        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(1, 2)), requests)
        assertEquals(setOf(1, 2, 3, 4), summary.remaining)
    }

    @Test fun neverStartsNextRequestBeforePermissionResultAndPersistence() = runBlocking {
        val permission = CompletableDeferred<Unit>()
        val persistence = CompletableDeferred<Unit>()
        var calls = 0
        val job = async {
            deleteInBatches(listOf(1, 2), batchSize = 1, deleteBatch = {
                calls++
                permission.await()
                BatchDeleteResult.Completed(it.toSet())
            }, onDeleted = { persistence.await() })
        }
        yield()
        assertEquals(1, calls)
        permission.complete(Unit)
        yield()
        assertEquals(1, calls)
        persistence.complete(Unit)
        assertTrue(job.await().remaining.isEmpty())
        assertEquals(2, calls)
    }

    @Test fun deduplicatesRequestsAndIgnoresUnrequestedResults() = runBlocking {
        val summary = deleteInBatches(listOf(1, 1, 2), deleteBatch = {
            assertEquals(listOf(1, 2), it)
            BatchDeleteResult.Completed(setOf(1, 99))
        }, onDeleted = { assertEquals(setOf(1), it) })
        assertEquals(setOf(1), summary.deleted)
        assertEquals(setOf(2), summary.remaining)
    }

    @Test fun emptyRecycleBinDoesNotLaunchARequest() = runBlocking {
        val summary = deleteInBatches(emptyList<Int>(), deleteBatch = {
            error("Unexpected request")
        }, onDeleted = { fail("Unexpected update") })
        assertTrue(summary.deleted.isEmpty())
        assertTrue(summary.remaining.isEmpty())
    }

    @Test fun nonRetryableFailureDoesNotSplit() = runBlocking {
        var calls = 0
        val summary = deleteInBatches(listOf(1, 2), deleteBatch = {
            calls++
            BatchDeleteResult.Failed("Unable to launch system dialog")
        }, onDeleted = {})
        assertEquals(1, calls)
        assertEquals(setOf(1, 2), summary.remaining)
    }

    @Test fun globalFailureStopsAllLaterBatches() = runBlocking {
        var calls = 0
        val summary = deleteInBatches((1..10000).toList(), deleteBatch = {
            calls++
            BatchDeleteResult.Failed("Permission denied")
        }, onDeleted = { fail("No records may be removed") })
        assertEquals(1, calls)
        assertEquals(10000, summary.remaining.size)
        assertEquals("Permission denied", summary.error)
    }

    @Test fun globalFailureKeepsEarlierSuccessfulWork() = runBlocking {
        var calls = 0
        val summary = deleteInBatches((1..250).toList(), deleteBatch = {
            calls++
            if (calls == 1) BatchDeleteResult.Completed(it.toSet())
            else BatchDeleteResult.Failed("Service unavailable")
        }, onDeleted = {})
        assertEquals(2, calls)
        assertEquals((1..100).toSet(), summary.deleted)
        assertEquals((101..250).toSet(), summary.remaining)
    }

    @Test fun unknownVerificationCommitsKnownResultsThenStops() = runBlocking {
        var calls = 0
        val committed = mutableSetOf<Int>()
        val summary = deleteInBatches((1..250).toList(), deleteBatch = {
            calls++
            BatchDeleteResult.Completed(setOf(1), "Unknown results")
        }, onDeleted = { committed.addAll(it) })
        assertEquals(1, calls)
        assertEquals(setOf(1), committed)
        assertEquals(249, summary.remaining.size)
        assertEquals("Unknown results", summary.error)
    }

    @Test fun over2000ItemsStillUseBoundedRequests() = runBlocking {
        val sizes = mutableListOf<Int>()
        val summary = deleteInBatches((1..2001).toList(), deleteBatch = {
            sizes.add(it.size)
            BatchDeleteResult.Completed(it.toSet())
        }, onDeleted = {})
        assertEquals(21, sizes.size)
        assertTrue(sizes.all { it <= 100 })
        assertTrue(summary.remaining.isEmpty())
    }
}
