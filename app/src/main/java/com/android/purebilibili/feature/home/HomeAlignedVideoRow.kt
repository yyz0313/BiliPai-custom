package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Keep the natural tallest-card height for every material. Slot modifiers go on the real
 * card root, avoiding an extra Box (and its intrinsic traversal) around every card.
 * Do not cache the maximum height: title, width and font changes must allow rows to shrink.
 */
@Composable
internal fun HomeAlignedVideoRow(
    indices: IntRange,
    itemKeys: List<String>,
    columns: Int,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    content: @Composable (Int, Modifier) -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Top,
    ) {
        indices.forEach { index ->
            key(itemKeys[index]) {
                content(index, Modifier.weight(1f).fillMaxHeight())
            }
        }
        repeat((columns - indices.count()).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
