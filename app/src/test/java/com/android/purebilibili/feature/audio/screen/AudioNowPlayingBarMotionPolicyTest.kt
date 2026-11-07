package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.core.ui.transition.NowPlayingBarHandoffState
import com.android.purebilibili.core.ui.transition.resolveNowPlayingBarReturnVisibility
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioNowPlayingBarMotionPolicyTest {

    @Test
    fun sourceHideRequiresTheRealSharedReturnOwner() {
        val returning = NowPlayingBarHandoffState.Returning(
            targetBvid = "BV123",
            isSourceOwner = true,
        )
        assertEquals(
            0f,
            resolveNowPlayingBarReturnVisibility(handoff = returning, currentBvid = "BV123"),
        )
        assertEquals(
            1f,
            resolveNowPlayingBarReturnVisibility(
                handoff = NowPlayingBarHandoffState.Idle,
                currentBvid = "BV123",
            ),
        )
    }

    @Test
    fun sourceCannotOpenUntilLayoutAndImeAreSettled() {
        assertFalse(canOpenAudioNowPlayingBarSource(layoutStable = false))
        assertFalse(canOpenAudioNowPlayingBarSource(layoutStable = true, imeSettled = false))
        assertFalse(canOpenAudioNowPlayingBarSource(layoutStable = true, sharedTransitionRunning = true))
        assertTrue(canOpenAudioNowPlayingBarSource(layoutStable = true))
    }

    @Test
    fun sourceBarUsesOneSharedGeometryTimeline() {
        val source = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/audio/screen/AudioNowPlayingBar.kt"
        )
        assertTrue(source.contains("onCompactClick"))
        assertTrue(source.contains("handoff"))
        assertFalse(source.contains("landingProgress"))
        assertFalse(source.contains("AudioNowPlayingBarLandingEasing"))
    }

    @Test
    fun expandedRowKeepsTitleBetweenCoverAndActions() {
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = 400,
            mergeProgress = 0f,
            searchProgress = 0f,
            density = 1f,
        )
        assertEquals(40, metrics.coverPx)
        assertEquals(10, metrics.horizontalPaddingPx)
        assertEquals(10, metrics.spacerPx)
        assertEquals(48, metrics.playWidthPx)
        assertEquals(48, metrics.extraWidthPx)
        assertEquals(186, metrics.titleWidthPx)
        assertEquals(10, metrics.contentStartPx)
        assertEquals(20, metrics.artistHeightPx)
    }

    @Test
    fun playbackRowDropsSupplementalWidthWithoutRecenteringCover() {
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = 400,
            mergeProgress = 1f,
            searchProgress = 0f,
            density = 1f,
        )
        assertEquals(32, metrics.coverPx)
        assertEquals(10, metrics.horizontalPaddingPx)
        assertEquals(6, metrics.spacerPx)
        assertEquals(48, metrics.playWidthPx)
        assertEquals(0, metrics.extraWidthPx)
        assertEquals(294, metrics.titleWidthPx)
        assertEquals(10, metrics.contentStartPx)
        assertEquals(0, metrics.artistHeightPx)
    }

    @Test
    fun searchRowCollapsesChromeAndCentersCover() {
        val metrics = resolveAudioNowPlayingBarRowMetrics(
            maxWidthPx = 400,
            mergeProgress = 1f,
            searchProgress = 1f,
            density = 1f,
        )
        assertEquals(32, metrics.coverPx)
        assertEquals(0, metrics.horizontalPaddingPx)
        assertEquals(0, metrics.spacerPx)
        assertEquals(0, metrics.playWidthPx)
        assertEquals(0, metrics.extraWidthPx)
        assertEquals(0, metrics.titleWidthPx)
        assertEquals(184, metrics.contentStartPx)
        assertEquals(0, metrics.artistHeightPx)
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        return listOf(File(path), File(normalizedPath)).firstOrNull(File::exists)?.readText()
            ?: error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
