package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.ViewInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class VideoDetailUiStateTest {
    private val info = ViewInfo(bvid = "BV1detail", cid = 123L, title = "Title", desc = "Description")
    private val details = VideoDetailUiState(requestBvid = info.bvid, requestCid = info.cid, info = info)

    @Test
    fun detailsCanRenderWhilePlaybackIsLoading() {
        val content = details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(info, content?.info)
        assertEquals("", content?.playUrl)
        assertEquals(VideoPlaybackUiState.RelatedLoadState.LOADING, content?.relatedLoadState)
    }

    @Test
    fun metadataDurationUsesTheSelectedPage() {
        val multiPageInfo = info.copy(pages = listOf(
            Page(cid = 456L, duration = 90L),
            Page(cid = info.cid, duration = 30L)
        ))
        val content = details.copy(info = multiPageInfo)
            .contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(30_000L, content?.videoDurationMs)
    }

    @Test
    fun missingPageDurationDoesNotInventAnEstimate() {
        val content = details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(0L, content?.videoDurationMs)
    }

    @Test
    fun playbackFailureDoesNotHideAnAlreadyLoadedDescription() {
        val error = VideoPlaybackUiState.Error(VideoLoadError.PlayUrlEmpty)
        assertEquals(info.desc, details.contentSnapshot(error, info.bvid, info.cid)?.info?.desc)
    }

    @Test
    fun metadataFromAnotherVideoOrPageIsNeverDisplayed() {
        assertNull(details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, "BV1other", info.cid))
        assertNull(details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, 456L))
    }

    @Test
    fun readyPlaybackKeepsItsExistingContentAndActionState() {
        val playback = VideoPlaybackUiState.Success(info, "stream", isLiked = true)
        assertSame(playback, details.contentSnapshot(playback, info.bvid, info.cid))
    }

    @Test
    fun avRoutesKeepTheCanonicalPlaybackStateAfterDetailsResolve() {
        val avDetails = details.copy(requestBvid = "av123", requestCid = 0L)
        val playback = VideoPlaybackUiState.Success(info, "stream", isLiked = true)
        assertSame(playback, avDetails.contentSnapshot(playback, "av123", 0L))
    }

    @Test
    fun correctedCidCanRenderMetadataForTheOriginalRequest() {
        val requested = details.copy(requestCid = 999L)
        assertEquals(info.cid, requested.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, 999L)?.info?.cid)
    }

    @Test
    fun relatedFailureDoesNotHideMetadata() {
        val failed = details.copy(relatedLoadState = VideoPlaybackUiState.RelatedLoadState.FAILED)
        val content = failed.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(info, content?.info)
        assertEquals(VideoPlaybackUiState.RelatedLoadState.FAILED, content?.relatedLoadState)
    }
}
