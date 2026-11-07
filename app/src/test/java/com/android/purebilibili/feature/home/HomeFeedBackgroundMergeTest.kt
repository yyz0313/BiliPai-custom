package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeFeedBackgroundMergeTest {
    private fun videos(vararg ids: String) = ids.map { VideoItem(bvid = it) }

    @Test
    fun appendKeepsOrderAndRemovesCrossPageDuplicates() {
        val result = prepareHomeFeedMerge(
            videos("A", "B"), videos("B", "C", "C", "D"),
            isLoadMore = true, isIncrementalRefresh = false, keySelector = { it.bvid },
        )
        assertEquals(listOf("A", "B", "C", "D"), result.videos.map { it.bvid })
        assertEquals(listOf("C", "D"), result.incomingVideos.map { it.bvid })
    }

    @Test
    fun rebaseDoesNotRestoreACardRemovedWhileRequestWasRunning() {
        val result = prepareHomeFeedMerge(
            currentVideos = videos("B"), responseVideos = videos("A", "C"),
            isLoadMore = true, isIncrementalRefresh = false, keySelector = { it.bvid },
            requestVideos = videos("A", "B"),
        )
        assertEquals(listOf("B", "C"), result.videos.map { it.bvid })
    }

    @Test
    fun incrementalRefreshPreservesEvenBatchAndAddedCount() {
        val result = prepareHomeFeedMerge(
            videos("A", "B"), videos("B", "C", "D", "E"),
            isLoadMore = false, isIncrementalRefresh = true, keySelector = { it.bvid },
        )
        assertEquals(listOf("C", "D", "A", "B"), result.videos.map { it.bvid })
        assertEquals(2, result.addedCount)
    }

    @Test
    fun replacementRefreshKeepsExistingSelectionRules() {
        val result = prepareHomeFeedMerge(
            videos("A"), videos("A", "B", "B"),
            isLoadMore = false, isIncrementalRefresh = false, keySelector = { it.bvid },
        )
        assertEquals(listOf("A", "B"), result.videos.map { it.bvid })
    }
}
