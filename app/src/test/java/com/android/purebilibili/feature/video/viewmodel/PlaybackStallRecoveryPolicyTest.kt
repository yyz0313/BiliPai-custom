package com.android.purebilibili.feature.video.viewmodel

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStallRecoveryPolicyTest {

    @Test
    fun `stalled playback switches to the next untried CDN`() {
        val decision = resolvePlaybackStallRecoveryDecision(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            firstFrameRendered = true,
            forwardBufferDurationMs = 0L,
            currentCdnIndex = 0,
            cdnCandidateCount = 3,
            attemptedCdnIndexes = setOf(0),
            usesAdaptivePlayback = false
        )

        assertTrue(decision.shouldSwitchCdn)
        assertEquals(1, decision.nextCdnIndex)
    }

    @Test
    fun `each CDN candidate is selected at most once`() {
        val decision = resolvePlaybackStallRecoveryDecision(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            firstFrameRendered = true,
            forwardBufferDurationMs = 0L,
            currentCdnIndex = 1,
            cdnCandidateCount = 3,
            attemptedCdnIndexes = setOf(0, 1),
            usesAdaptivePlayback = false
        )

        assertEquals(2, decision.nextCdnIndex)
        assertFalse(
            resolvePlaybackStallRecoveryDecision(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                firstFrameRendered = true,
                forwardBufferDurationMs = 0L,
                currentCdnIndex = 2,
                cdnCandidateCount = 3,
                attemptedCdnIndexes = setOf(0, 1, 2),
                usesAdaptivePlayback = false
            ).shouldSwitchCdn
        )
    }

    @Test
    fun `ready paused ended or buffered playback does not recover`() {
        val common = { state: Int, playWhenReady: Boolean, forwardBufferMs: Long ->
            resolvePlaybackStallRecoveryDecision(
                playbackState = state,
                playWhenReady = playWhenReady,
                firstFrameRendered = true,
                forwardBufferDurationMs = forwardBufferMs,
                currentCdnIndex = 0,
                cdnCandidateCount = 2,
                attemptedCdnIndexes = setOf(0),
                usesAdaptivePlayback = false
            )
        }

        assertFalse(common(Player.STATE_READY, true, 0L).shouldSwitchCdn)
        assertFalse(common(Player.STATE_BUFFERING, false, 0L).shouldSwitchCdn)
        assertFalse(common(Player.STATE_ENDED, true, 0L).shouldSwitchCdn)
        assertFalse(common(Player.STATE_BUFFERING, true, 1L).shouldSwitchCdn)
    }

    @Test
    fun `startup without a first frame can recover but needs an alternate CDN`() {
        assertTrue(
            resolvePlaybackStallRecoveryDecision(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                firstFrameRendered = false,
                forwardBufferDurationMs = 0L,
                currentCdnIndex = 0,
                cdnCandidateCount = 2,
                attemptedCdnIndexes = setOf(0),
                usesAdaptivePlayback = false
            ).shouldSwitchCdn
        )
        assertFalse(
            resolvePlaybackStallRecoveryDecision(
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                firstFrameRendered = true,
                forwardBufferDurationMs = 0L,
                currentCdnIndex = 0,
                cdnCandidateCount = 1,
                attemptedCdnIndexes = setOf(0),
                usesAdaptivePlayback = false
            ).shouldSwitchCdn
        )
    }

    @Test
    fun `startup recovery handles a stalled short buffer but preserves a usable buffer`() {
        fun decision(bufferMs: Long, playWhenReady: Boolean = true) = resolvePlaybackStallRecoveryDecision(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = playWhenReady,
            firstFrameRendered = false,
            forwardBufferDurationMs = bufferMs,
            currentCdnIndex = 0,
            cdnCandidateCount = 2,
            attemptedCdnIndexes = setOf(0),
            usesAdaptivePlayback = false,
        )
        assertTrue(decision(400L).shouldSwitchCdn)
        assertFalse(decision(700L).shouldSwitchCdn)
        assertFalse(decision(0L, playWhenReady = false).shouldSwitchCdn)
    }

    @Test
    fun `AUTO adaptive playback remains with Media3 while locked quality can switch CDN`() {
        val adaptive = resolvePlaybackStallRecoveryDecision(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            firstFrameRendered = true,
            forwardBufferDurationMs = 0L,
            currentCdnIndex = 0,
            cdnCandidateCount = 2,
            attemptedCdnIndexes = setOf(0),
            usesAdaptivePlayback = true
        )
        val locked = resolvePlaybackStallRecoveryDecision(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            firstFrameRendered = true,
            forwardBufferDurationMs = 0L,
            currentCdnIndex = 0,
            cdnCandidateCount = 2,
            attemptedCdnIndexes = setOf(0),
            usesAdaptivePlayback = false
        )

        assertFalse(adaptive.shouldSwitchCdn)
        assertEquals(1, locked.nextCdnIndex)
    }
}
