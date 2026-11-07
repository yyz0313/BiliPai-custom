package com.android.purebilibili.feature.bangumi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.navigation3.BiliPaiNavKey

internal data class BangumiCardTransition(val modifier: Modifier, val onClick: () -> Unit, val enabled: Boolean)

@Composable
internal fun rememberBangumiCardTransition(seasonId: Long, epId: Long = 0L, onClick: () -> Unit): BangumiCardTransition {
    val transition = com.android.purebilibili.core.ui.rememberNativeCardContainerTransition(
        BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId), onClick,
    )
    return BangumiCardTransition(transition.modifier, transition.onClick, transition.enabled)
}

/** 详情由导航容器承接，频道自身保留列表、选集与播放回调。 */
@Composable
internal fun BangumiCardTransitionHost(
    onBangumiClick: (Long) -> Unit,
    onBangumiEpisodeClick: (Long, Long) -> Unit,
    onPlayEpisode: ((Long, BangumiEpisode) -> Unit)?,
    onReviewsClick: (Long, String) -> Unit,
    onUserClick: (Long) -> Unit,
    content: @Composable ((Long) -> Unit, (Long, Long) -> Unit, Boolean) -> Unit,
) {
    content(onBangumiClick, onBangumiEpisodeClick, true)
}
