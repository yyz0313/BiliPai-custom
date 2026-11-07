package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.RichTextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicCommentPreviewPolicyTest {
    private val comment = DynamicInteractionItem(type = 1, desc = DynamicDesc(text = "相关评论"))
    private val like = DynamicInteractionItem(type = 2, desc = DynamicDesc(text = "UP 主觉得很赞"))

    @Test
    fun disabledCommentPreviewKeepsLikeHints() {
        assertEquals(
            listOf(like),
            resolveVisibleDynamicInteractionItems(listOf(comment, like), commentPreviewVisible = false),
        )
    }

    @Test
    fun enabledCommentPreviewKeepsOriginalOrder() {
        val items = listOf(like, comment)

        assertEquals(items, resolveVisibleDynamicInteractionItems(items, commentPreviewVisible = true))
    }

    @Test
    fun commentOnlyBarHasNoVisibleItemsWhenDisabled() {
        assertTrue(
            resolveVisibleDynamicInteractionItems(listOf(comment), commentPreviewVisible = false).isEmpty(),
        )
    }

    @Test
    fun missingAndBlankDescriptionsDoNotLeaveEmptyBars() {
        val items = listOf(
            DynamicInteractionItem(type = 1),
            DynamicInteractionItem(type = 2, desc = DynamicDesc(text = " ")),
        )

        assertTrue(resolveVisibleDynamicInteractionItems(items, commentPreviewVisible = true).isEmpty())
        assertTrue(resolveVisibleDynamicInteractionItems(items, commentPreviewVisible = false).isEmpty())
    }

    @Test
    fun nodeOnlyCommentsCanBeShown() {
        val nodeOnlyComment = DynamicInteractionItem(
            type = 1,
            desc = DynamicDesc(rich_text_nodes = listOf(RichTextNode(type = "TEXT", text = "评论内容"))),
        )

        assertEquals(
            listOf(nodeOnlyComment),
            resolveVisibleDynamicInteractionItems(listOf(nodeOnlyComment), commentPreviewVisible = true),
        )
    }
}
