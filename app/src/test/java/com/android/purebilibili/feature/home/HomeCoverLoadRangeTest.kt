package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeCoverLoadRangeTest {
    @Test
    fun windowIncludesOneRowOnBothSides() {
        assertEquals(6..15, resolveHomeCoverLoadRange(8, 13, 100, 2, false))
        assertEquals(4..19, resolveHomeCoverLoadRange(8, 15, 100, 4, false))
    }

    @Test
    fun windowClampsToFeedEdges() {
        assertEquals(0..5, resolveHomeCoverLoadRange(0, 3, 100, 2, false))
        assertEquals(94..99, resolveHomeCoverLoadRange(96, 99, 100, 2, false))
    }

    @Test
    fun dataSaverOnlyAllowsVisibleCards() {
        assertEquals(8..13, resolveHomeCoverLoadRange(8, 13, 100, 2, true))
    }

    @Test
    fun headersAndUnknownLayoutDoNotInventAVideoWindow() {
        assertNull(resolveHomeCoverLoadRange(null, null, 100, 2, false))
        assertNull(resolveHomeCoverLoadRange(0, 0, 0, 2, false))
        assertNull(resolveHomeCoverLoadRange(10, 8, 100, 2, false))
    }
}
