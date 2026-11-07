package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.usecase.resolveVideoLoadDurationMs

data class VideoDetailUiState(
    val requestBvid: String = "",
    val requestCid: Long = 0L,
    val requestToken: Long = 0L,
    val info: ViewInfo? = null,
    val related: List<RelatedVideo> = emptyList(),
    val relatedLoadState: VideoPlaybackUiState.RelatedLoadState = VideoPlaybackUiState.RelatedLoadState.LOADING,
    val isLoggedIn: Boolean = false,
    val isVip: Boolean = false
) {
    fun contentSnapshot(
        playbackState: VideoPlaybackUiState,
        bvid: String,
        cid: Long
    ): VideoPlaybackUiState.Success? {
        val playback = (playbackState as? VideoPlaybackUiState.Success)?.takeIf {
            val matchesVideo = it.info.bvid == bvid ||
                (requestBvid == bvid && info?.bvid == it.info.bvid)
            matchesVideo && (cid <= 0L || it.info.cid == cid)
        }
        if (playback != null) return playback
        if (requestBvid != bvid || requestCid != cid) return null
        val detail = info ?: return null
        return VideoPlaybackUiState.Success(
            info = detail,
            playUrl = "",
            related = related,
            relatedLoadState = relatedLoadState,
            isLoggedIn = isLoggedIn,
            isVip = isVip,
            videoDurationMs = resolveVideoLoadDurationMs(0L, detail)
        )
    }
}
