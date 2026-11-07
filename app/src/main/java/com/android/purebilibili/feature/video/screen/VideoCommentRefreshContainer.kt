package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.AdaptivePullToRefreshDefaultIndicator
import com.android.purebilibili.core.ui.ComfortablePullToRefreshBox

/** Isolates the refresh indicator's composition-time inset read from the comment rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoCommentRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // Player collapse toggles refresh eligibility during a list gesture. Keep the list under
    // one stable Box; the Miuix adapter moves it between parents and blocks scroll while refreshing.
    val refreshState = rememberPullToRefreshState()
    ComfortablePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        enabled = enabled,
        state = refreshState,
        indicator = {
            AdaptivePullToRefreshDefaultIndicator(
                isRefreshing = isRefreshing,
                state = refreshState,
                indicatorTopInset = contentPadding.calculateTopPadding(),
            )
        },
        modifier = modifier,
        content = content,
    )
}
