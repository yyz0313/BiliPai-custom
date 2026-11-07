package com.android.purebilibili.feature.search

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchLandingUiPolicyTest {

    @Test
    fun `visibility toggle requires a handler`() {
        assertTrue(shouldShowSearchKeywordSectionVisibilityToggle(hasToggleHandler = true))
        assertFalse(shouldShowSearchKeywordSectionVisibilityToggle(hasToggleHandler = false))
    }

    @Test
    fun `normal phone keeps labeled section actions`() {
        assertFalse(shouldUseCompactSearchSectionActions(widthDp = 412, fontScale = 1f))
    }

    @Test
    fun `narrow phone keeps section actions reachable as icons`() {
        assertTrue(shouldUseCompactSearchSectionActions(widthDp = 320, fontScale = 1f))
    }

    @Test
    fun `large font avoids wrapping section action labels`() {
        assertTrue(shouldUseCompactSearchSectionActions(widthDp = 412, fontScale = 2f))
    }

    @Test
    fun `search discovery section uses original style when trending action is absent`() {
        assertTrue(shouldUseOriginalSearchDiscoverStyle(showTrendingAction = false))
        assertFalse(shouldUseOriginalSearchDiscoverStyle(showTrendingAction = true))
    }

    @Test
    fun `search discovery section keeps two columns to match original layout`() {
        assertEquals(2, resolveSearchKeywordSectionColumns(requestedColumns = 1, showTrendingAction = false))
        assertEquals(2, resolveSearchKeywordSectionColumns(requestedColumns = 4, showTrendingAction = false))
        assertEquals(2, resolveSearchKeywordSectionColumns(requestedColumns = 3, showTrendingAction = true))
    }

    @Test
    fun `search landing section order matches bilipai portrait layout`() {
        assertEquals(
            listOf(
                SearchLandingSection.TRENDING,
                SearchLandingSection.HISTORY,
                SearchLandingSection.DISCOVER
            ),
            resolveSearchLandingSectionOrder()
        )
    }

    @Test
    fun `search discovery original cell colors stay neutral without theme primary`() {
        val lightScheme = lightColorScheme()
        val darkScheme = darkColorScheme()
        val light = resolveSearchDiscoverOriginalCellColors(lightScheme)
        val dark = resolveSearchDiscoverOriginalCellColors(darkScheme)

        assertTrue(light.containerColor.alpha > 0f)
        assertTrue(light.borderColor.alpha > 0f)
        // Must not tint with brand primary (搜索发现 stays neutral).
        assertTrue(light.containerColor.red != lightScheme.primary.red || light.containerColor.alpha < 0.3f)
        assertEquals(lightScheme.onSurface, light.titleColor)
        assertEquals(darkScheme.onSurface, dark.titleColor)
    }

    @Test
    fun `search discovery original subtitle keeps all non blank metadata`() {
        assertEquals("15小时前更新", resolveSearchDiscoverOriginalSubtitle("15小时前更新"))
        assertEquals("47分钟前更新", resolveSearchDiscoverOriginalSubtitle("47分钟前更新"))
        assertEquals("关注的 UP 主", resolveSearchDiscoverOriginalSubtitle("关注的 UP 主"))
        assertEquals("与最近搜索相关", resolveSearchDiscoverOriginalSubtitle("与最近搜索相关"))
        assertEquals(null, resolveSearchDiscoverOriginalSubtitle("   "))
    }
}
