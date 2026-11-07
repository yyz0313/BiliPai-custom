package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.data.model.response.DynamicInteractionItem

internal fun resolveVisibleDynamicInteractionItems(
    items: List<DynamicInteractionItem>,
    commentPreviewVisible: Boolean,
): List<DynamicInteractionItem> = items.filter { item ->
    item.desc?.let(::shouldRenderDynamicRichText) == true &&
        (commentPreviewVisible || item.type != 1)
}
