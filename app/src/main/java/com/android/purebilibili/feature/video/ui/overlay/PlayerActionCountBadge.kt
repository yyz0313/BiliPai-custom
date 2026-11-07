package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.FullscreenActionCount
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import java.util.Locale

@Composable
internal fun rememberVisibleFullscreenCounts(): Set<FullscreenActionCount> {
    val context = LocalContext.current
    val visible by remember(context) { FullscreenActionCountSettings.getVisibleCounts(context) }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    return visible
}

internal fun formatPlayerActionCount(count: Long): String {
    val value = count.coerceAtLeast(0)
    return when {
        value >= 100_000_000L -> if (value >= 99_900_000_000L) "999亿+"
            else if (value >= 10_000_000_000L) "${value / 100_000_000L}亿"
            else String.format(Locale.ROOT, "%.1f亿", value / 100_000_000.0)
        value >= 1_000_000L -> "${value / 10_000L}万"
        value >= 10_000L -> String.format(Locale.ROOT, "%.1f万", value / 10_000.0)
        else -> value.toString()
    }
}

/** 角标覆盖固定按钮槽位，不参与按钮测量；未知数量保留为空，真实零值显示 0。 */
@Composable
internal fun CountedPlayerAction(
    count: Long?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        if (count != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 4.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                AppText(
                    text = formatPlayerActionCount(count),
                    modifier = Modifier.widthIn(max = 44.dp),
                    color = AppContentColors.accent(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    tapToCopyEnabled = false,
                )
            }
        }
    }
}
