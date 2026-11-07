package com.Android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.data.model.response.BgmDetailData
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.feature.video.ui.section.BgmDetailCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BgmDetailCardUiRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun longMusicInfo_keepsDetailActionUnclipped() {
        verifyDetailAction(fontScale = 1f)
    }

    @Test
    fun longMusicInfoWithLargeFont_keepsDetailActionUnclipped() {
        verifyDetailAction(fontScale = 1.5f)
    }

    private fun verifyDetailAction(fontScale: Float) {
        var opened = false
        composeTestRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        LazyColumn {
                            item {
                                BgmDetailCard(
                                    bgm = BgmInfo(
                                        musicTitle = "这是一首很长名字的背景音乐，包含现场演奏与特别版本",
                                        actor = "演唱者与制作团队的详细介绍，需要显示多行文字以覆盖详情入口被压缩的场景",
                                    ),
                                    detail = BgmDetailData(musicTitle = "这是一首很长名字的背景音乐，包含现场演奏与特别版本"),
                                    isLoading = false,
                                    statLine = "播放次数 123456 · 收藏 7890",
                                    onOpenMusic = { opened = true },
                                    modifier = Modifier.testTag("bgm_detail_card"),
                                )
                            }
                        }
                    }
                }
            }
        }
        val action = composeTestRule.onNodeWithText("打开音乐详情", useUnmergedTree = true)
        action.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        action.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse(layouts.single().hasVisualOverflow)
        val cardBounds = composeTestRule.onNodeWithTag("bgm_detail_card").fetchSemanticsNode().boundsInRoot
        val actionBounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue(actionBounds.height > 0f)
        assertTrue(actionBounds.bottom <= cardBounds.bottom)
        action.performClick()
        composeTestRule.runOnIdle { assertTrue(opened) }
    }
}
