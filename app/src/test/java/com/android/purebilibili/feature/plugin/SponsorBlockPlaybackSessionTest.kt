package com.android.purebilibili.feature.plugin

import android.app.Application
import android.content.Context
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.SponsorActionType
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.repository.SponsorBlockRepository
import com.android.purebilibili.plugin.sdk.SkipAction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class SponsorBlockPlaybackSessionTest {
    private val context = mockk<Context>()
    private val segment = SponsorSegment(
        segment = listOf(10f, 20f),
        UUID = "shared-segment",
        category = SponsorCategory.SPONSOR,
    )
    private var storedJson: String? = null

    @Before
    fun setUp() {
        mockkObject(PluginManager, PluginStore, SponsorBlockRepository, AnalyticsHelper, Logger)
        every { PluginManager.getContext() } returns context
        every { Logger.d(any(), any<String>()) } just Runs
        every { Logger.e(any(), any(), any()) } just Runs
        every { AnalyticsHelper.logSponsorBlockSkip(any(), any()) } just Runs
        storedJson = Json.encodeToString(SponsorBlockConfig(userId = "persistent-user"))
        coEvery { PluginStore.getConfigJson(context, BuiltinPluginIds.SPONSOR_BLOCK) } answers { storedJson }
        coEvery { PluginStore.setConfigJson(context, BuiltinPluginIds.SPONSOR_BLOCK, any()) } answers {
            storedJson = thirdArg()
        }
        coEvery { SponsorBlockRepository.loadSegments(any(), any(), any(), any()) } returns listOf(segment)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun factorySessionsAutoSkipIdenticalSegmentsIndependently() = runTest {
        val definition = SponsorBlockPlugin()
        val first = definition.createSession()
        val second = definition.createSession()
        assertNotSame(first, second)
        first.onVideoLoad("BV1", 1L)
        second.onVideoLoad("BV1", 1L)

        val expected = SkipAction.SkipTo(
            positionMs = 20_000L,
            reason = "已跳过: ${segment.categoryName}",
            segmentId = segment.UUID,
            startMs = 10_000L,
            category = segment.category,
            categoryName = segment.categoryName,
            showToast = true,
        )
        assertEquals(expected, first.onPositionUpdate(12_000L))
        assertEquals(expected, second.onPositionUpdate(12_000L))
        assertEquals(SkipAction.None, first.onPositionUpdate(12_500L))
        assertEquals(SkipAction.None, second.onPositionUpdate(12_500L))
        verify(exactly = 2) { AnalyticsHelper.logSponsorBlockSkip(segment.UUID, segment.categoryName) }
    }

    @Test
    fun manualMarkSkippedAndSeekRearmingAreIsolatedToTheirSession() = runTest {
        storedJson = Json.encodeToString(SponsorBlockConfig(autoSkip = false, userId = "persistent-user"))
        val first = SponsorBlockPlaybackSession()
        val second = SponsorBlockPlaybackSession()
        first.onVideoLoad("BV1", 1L)
        second.onVideoLoad("BV1", 1L)
        assertIs<SkipAction.ShowButton>(first.onPositionUpdate(12_000L))
        assertEquals(segment, first.getActiveSegment())

        assertEquals(segment, first.markAsSkipped(segment.UUID))
        assertEquals(SkipAction.None, first.onPositionUpdate(12_500L))
        assertIs<SkipAction.ShowButton>(second.onPositionUpdate(12_500L))
        assertEquals(segment, second.getActiveSegment())
        first.onUserSeek(12_000L)
        assertIs<SkipAction.ShowButton>(first.onPositionUpdate(12_000L))
    }

    @Test
    fun muteActionRetainsItsEndAndToastPreferenceWithoutSeeking() = runTest {
        val muted = segment.copy(actionType = SponsorActionType.MUTE)
        coEvery { SponsorBlockRepository.loadSegments(any(), any(), any(), any()) } returns listOf(muted)
        for (showToast in listOf(false, true)) {
            storedJson = Json.encodeToString(SponsorBlockConfig(userId = "persistent-user", skipToastEnabled = showToast))
            val session = SponsorBlockPlaybackSession()
            session.onVideoLoad("BV1", 1L)
            assertEquals(
                SkipAction.Mute(
                    untilMs = muted.endTimeMs,
                    reason = "已静音: ${muted.categoryName}",
                    segmentId = muted.UUID,
                    showToast = showToast,
                ),
                session.onPositionUpdate(12_000L),
            )
            assertEquals(SkipAction.None, session.onPositionUpdate(13_000L))
            assertNull(session.getActiveSegment())
        }
        verify(exactly = 0) { AnalyticsHelper.logSponsorBlockSkip(any(), any()) }
    }

    @Test
    fun endingOneSessionClearsOnlyItsMarkersSegmentsAndCursor() = runTest {
        val first = SponsorBlockPlaybackSession()
        val second = SponsorBlockPlaybackSession()
        first.onVideoLoad("BV1", 1L)
        second.onVideoLoad("BV1", 1L)
        assertEquals(1, first.getProgressMarkers().size)
        first.onVideoEnd()
        assertTrue(first.getSegments().isEmpty())
        assertTrue(first.getProgressMarkers().isEmpty())
        assertNull(first.getActiveSegment())
        assertEquals(SkipAction.None, first.onPositionUpdate(12_000L))
        assertEquals(listOf(segment), second.getSegments())
        assertEquals(1, second.getProgressMarkers().size)
        assertIs<SkipAction.SkipTo>(second.onPositionUpdate(12_000L))

        first.onVideoLoad("BV2", 2L)
        assertIs<SkipAction.SkipTo>(first.onPositionUpdate(12_000L))
    }

    @Test
    fun readFailureRetainsTheSessionsOwnNormalizedConfig() = runTest {
        storedJson = Json.encodeToString(SponsorBlockConfig(
            userId = "retained-user",
            serverBaseUrl = "https://community.example/api/",
            skipToastEnabled = false,
        ))
        val session = SponsorBlockPlaybackSession()
        session.onVideoLoad("BV1", 1L)
        coEvery { PluginStore.getConfigJson(context, BuiltinPluginIds.SPONSOR_BLOCK) } throws
            IllegalStateException("read failed")
        session.onVideoLoad("BV2", 2L)
        assertEquals("https://community.example/api", session.getCommunityServerBaseUrl())
        assertEquals(false, assertIs<SkipAction.SkipTo>(session.onPositionUpdate(12_000L)).showToast)
        coVerify { SponsorBlockRepository.loadSegments("BV2", 2L, any(), "https://community.example/api") }
    }

    @Test
    fun configurationCancellationPropagatesWithoutLoadingSegments() = runTest {
        coEvery { PluginStore.getConfigJson(context, BuiltinPluginIds.SPONSOR_BLOCK) } throws
            CancellationException("cancelled")
        assertFailsWith<CancellationException> { SponsorBlockPlaybackSession().onVideoLoad("BV1", 1L) }
        coVerify(exactly = 0) { SponsorBlockRepository.loadSegments(any(), any(), any(), any()) }
    }

    @Test
    fun repositoryFailuresReachTheOwnerForRetry() = runTest {
        coEvery { SponsorBlockRepository.loadSegments(any(), any(), any(), any()) } throws
            IllegalStateException("repository failed")
        val session = SponsorBlockPlaybackSession()
        assertFailsWith<IllegalStateException> { session.onVideoLoad("BV1", 1L) }
        assertTrue(session.getSegments().isEmpty())
        coEvery { SponsorBlockRepository.loadSegments(any(), any(), any(), any()) } returns listOf(segment)
        session.onVideoLoad("BV1", 1L)
        assertIs<SkipAction.SkipTo>(session.onPositionUpdate(12_000L))
    }

    @Test
    fun communityRequestsKeepTheirSessionsConsentAndIdentitySnapshot() = runTest {
        val optedOut = SponsorBlockPlaybackSession()
        optedOut.onVideoLoad("BV1", 1L)
        storedJson = Json.encodeToString(SponsorBlockConfig(
            userId = "contributor",
            serverBaseUrl = "https://community.example/api",
            communityTrackingEnabled = true,
            communityContributionEnabled = true,
        ))
        val optedIn = SponsorBlockPlaybackSession()
        optedIn.onVideoLoad("BV1", 1L)
        coEvery { SponsorBlockRepository.uploadViewedSegment(any(), any()) } returns Result.success(Unit)
        coEvery { SponsorBlockRepository.submitSegments(any(), any(), any(), any(), any(), any()) } returns
            Result.success(listOf(segment))
        coEvery { SponsorBlockRepository.voteOnSegment(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        optedOut.uploadViewedSegmentIfEnabled(segment.UUID)
        assertTrue(optedOut.submitCommunitySegment("BV1", 1L, 60f, 10_000L, 20_000L,
            SponsorCategory.SPONSOR, SponsorActionType.SKIP).isFailure)
        assertEquals(false, optedOut.isCommunityContributionEnabled())
        assertTrue(optedIn.isCommunityContributionEnabled())
        optedIn.uploadViewedSegmentIfEnabled(segment.UUID)
        assertEquals(listOf(segment), optedIn.submitCommunitySegment("BV1", 1L, 60f, 10_000L, 20_000L,
            SponsorCategory.SPONSOR, SponsorActionType.SKIP).getOrThrow())
        optedIn.voteOnCommunitySegment(segment.UUID, 1).getOrThrow()
        coVerify(exactly = 1) {
            SponsorBlockRepository.uploadViewedSegment("https://community.example/api", segment.UUID)
            SponsorBlockRepository.submitSegments(
                "https://community.example/api", "BV1", 1L, "contributor", 60f,
                listOf(SponsorBlockRepository.SegmentSubmission(listOf(10f, 20f), SponsorCategory.SPONSOR)),
            )
            SponsorBlockRepository.voteOnSegment("https://community.example/api", "contributor", segment.UUID, 1, null)
        }
    }
}
