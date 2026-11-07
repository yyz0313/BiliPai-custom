package com.android.purebilibili.feature.video.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoPlayerBufferPolicyTest {

    @Test
    fun resilientPolicyDeepensBufferWithoutIncreasingStartupThreshold() {
        for (wifi in listOf(true, false)) {
            val normal = resolvePlayerBufferPolicy(isOnWifi = wifi)
            val resilient = resolvePlayerBufferPolicy(isOnWifi = wifi, resilient = true)
            assertEquals(normal.bufferForPlaybackMs, resilient.bufferForPlaybackMs)
            assertEquals(15_000, resilient.earlyPlaybackMaxBufferMs)
            assertEquals(20_000, resilient.minBufferMs)
            assertEquals(50_000, resilient.maxBufferMs)
            assertEquals(2_500, resilient.bufferForPlaybackAfterRebufferMs)
            listOf(1f, 2f, 3f).forEach { speed ->
                val limitUs = resolveEarlyPlaybackBufferLimitUs(
                    resilient.earlyPlaybackMaxBufferMs, resilient.bufferForPlaybackMs,
                    resilient.bufferForPlaybackAfterRebufferMs, speed,
                )
                assertTrue(limitUs > resilient.bufferForPlaybackAfterRebufferMs * 1_000L * speed)
            }
        }
    }

    @Test
    fun wifiPolicyShouldUseLowerStartupBufferForFasterAutoplay() {
        val policy = resolvePlayerBufferPolicy(isOnWifi = true)

        assertEquals(10000, policy.minBufferMs)
        assertEquals(40000, policy.maxBufferMs)
        assertEquals(700, policy.bufferForPlaybackMs)
        assertEquals(1400, policy.bufferForPlaybackAfterRebufferMs)
        assertEquals(2000, policy.earlyPlaybackMaxBufferMs)
    }

    @Test
    fun mobilePolicyShouldUseFasterStartupWhileKeepingRebufferSafetyMargin() {
        val policy = resolvePlayerBufferPolicy(isOnWifi = false)

        assertEquals(12000, policy.minBufferMs)
        assertEquals(45000, policy.maxBufferMs)
        assertEquals(1000, policy.bufferForPlaybackMs)
        assertEquals(2200, policy.bufferForPlaybackAfterRebufferMs)
        assertEquals(2000, policy.earlyPlaybackMaxBufferMs)
        assertTrue(policy.bufferForPlaybackAfterRebufferMs >= policy.bufferForPlaybackMs)
    }

    @Test
    fun firstQuarterLimitsForwardBufferButLaterPlaybackDoesNot() {
        assertTrue(
            shouldLimitEarlyPlaybackBuffer(
                playbackPositionUs = 59_999_999L,
                mediaPeriodDurationUs = 240_000_000L
            )
        )
        assertTrue(
            !shouldLimitEarlyPlaybackBuffer(
                playbackPositionUs = 60_000_000L,
                mediaPeriodDurationUs = 240_000_000L
            )
        )
        assertTrue(
            !shouldLimitEarlyPlaybackBuffer(
                playbackPositionUs = 0L,
                mediaPeriodDurationUs = -1L
            )
        )
    }

    @Test
    fun missingMediaPeriodStillLimitsEarlyBufferToAvoidEntryBurst() {
        // prepare 一开始 period 未就绪（mediaPeriodId == null）也必须限流，
        // 否则进详情即按 maxBuffer 全力预缓冲
        assertTrue(
            shouldLimitEarlyPlaybackBufferForPeriod(
                hasKnownMediaPeriod = false,
                playbackPositionUs = 0L,
                mediaPeriodDurationUs = -1L
            )
        )
        // 有 period 且时长未知（直播等）维持常规策略，不强限
        assertTrue(
            !shouldLimitEarlyPlaybackBufferForPeriod(
                hasKnownMediaPeriod = true,
                playbackPositionUs = 0L,
                mediaPeriodDurationUs = -1L
            )
        )
        // 有 period 的普通点播按前 1/4 规则
        assertTrue(
            shouldLimitEarlyPlaybackBufferForPeriod(
                hasKnownMediaPeriod = true,
                playbackPositionUs = 59_999_999L,
                mediaPeriodDurationUs = 240_000_000L
            )
        )
    }
    @Test
    fun earlyLimitAlwaysAllowsStartupAndRebufferAtSupportedSpeeds() {
        listOf(true, false).forEach { wifi ->
            val policy = resolvePlayerBufferPolicy(wifi)
            listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { speed ->
                val limitUs = resolveEarlyPlaybackBufferLimitUs(
                    policy.earlyPlaybackMaxBufferMs,
                    policy.bufferForPlaybackMs,
                    policy.bufferForPlaybackAfterRebufferMs,
                    speed,
                )
                val startupUs = policy.bufferForPlaybackMs * 1_000.0 * speed
                val rebufferUs = policy.bufferForPlaybackAfterRebufferMs * 1_000.0 * speed
                assertTrue(limitUs > startupUs, "startup wifi=$wifi speed=$speed")
                assertTrue(limitUs > rebufferUs, "rebuffer wifi=$wifi speed=$speed")
                assertTrue(limitUs >= policy.earlyPlaybackMaxBufferMs * 1_000L)
            }
        }
    }

}
