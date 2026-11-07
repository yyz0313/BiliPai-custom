package com.android.purebilibili.feature.comment

import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.CommentMainListRoute
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentPagingSessionTest {
    @Test fun mainCommentRetryRetainsRouteAndCursorAndPublishesEachPageOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val continuation = CommentMainListContinuation(CommentMainListRoute.GRPC, offset = "opaque")
            val requested = mutableListOf<CommentPageRequest>()
            val applied = mutableListOf<Int>()
            var fail = true
            var error: Throwable? = null
            var end = false
            val session = CommentPagingSession(mainList = true, load = { request ->
                requested += request
                if (request.page == 2 && fail) { fail = false; throw IllegalStateException("暂时失败") }
                ReplyData(cursor = ReplyCursor(isEnd = request.page == 2), mainListContinuation = continuation)
            }, onPage = { request, _ -> applied += request.page }, onState = { _, done, failure -> end = done; error = failure })
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            session.loadMore()
            runCurrent()
            assertNotNull(error)
            assertEquals(listOf(1), applied)
            session.loadMore()
            runCurrent()
            assertNull(error)
            assertTrue(end)
            assertEquals(listOf(1, 2), applied)
            assertEquals(listOf(CommentPageRequest(), CommentPageRequest(2, continuation), CommentPageRequest(2, continuation)), requested)
            job.cancelAndJoin()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun dialogResumesAtSavedCursorAndStopsOnRepeatedOffset() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requested = mutableListOf<CommentPageRequest>()
            var end = false
            val initial = CommentPageRequest(page = 4, offset = "saved")
            val session = CommentPagingSession(initial, mainList = false, load = { request ->
                requested += request
                ReplyData(grpcNextOffset = "saved")
            }, onPage = { _, _ -> }, onState = { _, done, _ -> end = done })
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            session.loadMore()
            runCurrent()
            assertTrue(end)
            assertEquals(listOf(initial), requested)
            job.cancelAndJoin()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun cancelledCollectionCannotPublishDelayedReply() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = kotlinx.coroutines.CompletableDeferred<ReplyData>()
            var applied = 0
            val session = CommentPagingSession(mainList = false, load = { response.await() },
                onPage = { _, _ -> applied++ }, onState = { _, _, _ -> })
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            job.cancelAndJoin()
            response.complete(ReplyData())
            runCurrent()
            assertEquals(0, applied)
        } finally { Dispatchers.resetMain() }
    }
}
