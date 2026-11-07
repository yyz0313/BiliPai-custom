package com.android.purebilibili.core.ui.transition

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardFloatingBackdropPolicyTest {
    @Test
    fun onlyPausesForCoveredVideoWithoutConsumers() {
        fun record(
            glass: Boolean = true,
            video: Boolean = true,
            exposure: VideoCardTransitionExposure = VideoCardTransitionExposure.SettledHidden,
            audio: Boolean = false,
            consumer: Boolean = false,
        ) = shouldRecordVideoCardFloatingBackdrop(glass, video, exposure, audio, consumer)

        assertFalse(record())
        assertTrue(record(glass = false))
        assertTrue(record(video = false))
        assertTrue(record(audio = true))
        assertTrue(record(consumer = true))
        for (exposure in VideoCardTransitionExposure.entries) {
            if (exposure != VideoCardTransitionExposure.SettledHidden) {
                assertTrue(record(exposure = exposure), "采样应在 $exposure 恢复")
            }
        }
    }
}
