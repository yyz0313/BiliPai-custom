package com.android.purebilibili.feature.home

import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class HomeCardReflowStateTest {
    @Test
    fun unchangedKeysDoNotReadListAgain() {
        val keys = object : AbstractList<String>() {
            var reads = 0
            override val size = 2
            override fun get(index: Int): String {
                reads++
                return if (index == 0) "a" else "b"
            }
        }
        val state = HomeCardReflowState()
        val retained = state.card("a")
        state.retainKeys(keys)
        val initialReads = keys.reads
        repeat(10) { state.retainKeys(keys) }
        kotlin.test.assertEquals(initialReads, keys.reads)
        assertSame(retained, state.card("a"))
    }

    @Test
    fun changedKeysPreserveExistingMotionAndCancelRemovedMotion() {
        val state = HomeCardReflowState()
        val first = state.card("a")
        val removed = state.card("b")
        val removedJob = Job()
        removed.job = removedJob
        state.retainKeys(listOf("a", "b"))
        state.retainKeys(listOf("a", "c"))
        assertSame(first, state.card("a"))
        assertFalse(removedJob.isActive)
        assertNotSame(removed, state.card("b"))
    }
}
