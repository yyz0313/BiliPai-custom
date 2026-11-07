package com.Android.purebilibili.feature.video.ui.components

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.feature.video.ui.components.COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SubReplyPreviewActionUiRegressionTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalLimit = 0

    @Before
    fun configurePreviewLimit() = runBlocking {
        originalLimit = SettingsManager.getCommentCollapsedReplyPreviewLimit(context).first()
        SettingsManager.setCommentCollapsedReplyPreviewLimit(context, 3)
    }

    @After
    fun restorePreviewLimit() = runBlocking {
        SettingsManager.setCommentCollapsedReplyPreviewLimit(context, originalLimit)
    }

    @Test
    fun completePreviewOnlyTogglesLocallyAcrossExpandAndCollapse() {
        var openedThreads = 0
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = reply(total = 6, loaded = 6),
                    onClick = {},
                    onSubClick = { _, _ -> openedThreads++ },
                    onAvatarClick = {},
                )
            }
        }

        rule.onNodeWithText("展开回复").assertIsDisplayed()
        viewAll().assertDoesNotExist()
        fourthPreview().assertDoesNotExist()
        rule.onNodeWithText("展开回复").performClick()
        rule.onNodeWithText("收起回复").assertIsDisplayed()
        fourthPreview().assertIsDisplayed()
        viewAll().assertDoesNotExist()
        rule.onNodeWithText("收起回复").performClick()
        fourthPreview().assertDoesNotExist()
        viewAll().assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, openedThreads) }
    }

    @Test
    fun incompletePreviewOnlyOpensTheFullThread() {
        var openedThread: Pair<Long, Long>? = null
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = reply(total = 6, loaded = 4),
                    onClick = {},
                    onSubClick = { root, target -> openedThread = root.rpid to target },
                    onAvatarClick = {},
                )
            }
        }

        rule.onNodeWithText("展开回复").assertDoesNotExist()
        rule.onNodeWithText("收起回复").assertDoesNotExist()
        fourthPreview().assertDoesNotExist()
        viewAll().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(100L to 0L, openedThread) }
    }

    @Test
    fun previewUpdatesSwitchBetweenActionsWithoutShowingBoth() {
        val current = mutableStateOf(reply(total = 6, loaded = 4))
        rule.setContent {
            MaterialTheme {
                ReplyItemView(
                    item = current.value,
                    onClick = {},
                    onSubClick = { _, _ -> },
                    onAvatarClick = {},
                )
            }
        }

        viewAll().assertIsDisplayed()
        rule.onNodeWithText("展开回复").assertDoesNotExist()
        rule.runOnIdle { current.value = reply(total = 6, loaded = 6) }
        rule.onNodeWithText("展开回复").assertIsDisplayed()
        viewAll().assertDoesNotExist()
        rule.runOnIdle { current.value = current.value.copy(rcount = 7) }
        viewAll().assertIsDisplayed()
        rule.onNodeWithText("展开回复").assertDoesNotExist()
        rule.runOnIdle { current.value = reply(total = 2, loaded = 2) }
        viewAll().assertDoesNotExist()
        rule.onNodeWithText("展开回复").assertDoesNotExist()
    }

    private fun viewAll() = rule.onNodeWithTag("${COMMENT_VIEW_ALL_REPLIES_TAG_PREFIX}100")
    private fun fourthPreview() = rule.onNodeWithTag("${COMMENT_SUB_REPLY_PREVIEW_TAG_PREFIX}304")

    private fun reply(total: Int, loaded: Int) = ReplyItem(
        rpid = 100L,
        mid = 10L,
        rcount = total,
        member = ReplyMember(mid = "10", uname = "评论作者"),
        content = ReplyContent(message = "测试楼中楼入口"),
        replies = (1..loaded).map { index ->
            ReplyItem(
                rpid = 300L + index,
                root = 100L,
                mid = 30L + index,
                member = ReplyMember(mid = (30L + index).toString(), uname = "回复者$index"),
                content = ReplyContent(message = "预览回复$index"),
            )
        },
    )
}
