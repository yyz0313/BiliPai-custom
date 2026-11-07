package com.android.purebilibili.feature.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeImageWorkBudgetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun scrollingCancelsSettlingAndEachStopRequiresANewQuietWindow() {
        rule.mainClock.autoAdvance = false
        var scrolling by mutableStateOf(false)
        rule.setContent {
            val allowed = rememberHomeCardAuxiliaryWorkAllowed(true, scrolling)
            BasicText(if (allowed) "ready" else "waiting")
        }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("waiting").assertExists()
        rule.runOnIdle { scrolling = true }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("waiting").assertExists()
        rule.runOnIdle { scrolling = false }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("waiting").assertExists()
        rule.mainClock.advanceTimeBy(240)
        rule.onNodeWithText("ready").assertExists()
        rule.runOnIdle { scrolling = true }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("waiting").assertExists()
    }

    @Test
    fun coveredPageCannotStartWorkAndMustSettleAgainOnReturn() {
        rule.mainClock.autoAdvance = false
        var active by mutableStateOf(false)
        rule.setContent {
            val allowed = rememberHomeCardAuxiliaryWorkAllowed(active, false)
            BasicText(if (allowed) "ready" else "waiting")
        }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("waiting").assertExists()
        rule.runOnIdle { active = true }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("waiting").assertExists()
        rule.mainClock.advanceTimeBy(240)
        rule.onNodeWithText("ready").assertExists()
        rule.runOnIdle { active = false }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("waiting").assertExists()
        rule.runOnIdle { active = true }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("waiting").assertExists()
        rule.mainClock.advanceTimeBy(240)
        rule.onNodeWithText("ready").assertExists()
    }
}
