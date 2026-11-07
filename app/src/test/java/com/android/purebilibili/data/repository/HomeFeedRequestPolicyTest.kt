package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.CancellationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeFeedRequestPolicyTest {
    @Test
    fun legacyLoginRetainsPhoneRequestAndPageSize() {
        val initial = buildSignedHomePhoneFeedParams(0, 20, "test-token", TokenManager.ACCESS_TOKEN_PLATFORM_TV, 123L)
        val next = buildSignedHomePhoneFeedParams(3, 35, "test-token", TokenManager.ACCESS_TOKEN_PLATFORM_TV, 124L)
        assertEquals(AppSignUtils.TV_APP_KEY, initial["appkey"])
        assertEquals("android", initial["mobi_app"])
        assertEquals("android", initial["device"])
        assertEquals("8130300", initial["build"])
        assertEquals("test-token", initial["access_key"])
        assertEquals("1", initial["pull"])
        assertEquals("0", next["pull"])
        assertEquals("3", next["idx"])
        assertEquals("35", next["ps"])
        assertEquals("124", next["ts"])
    }

    @Test
    fun androidCredentialUsesItsOwnAppKeyAndSignature() {
        val android = buildSignedHomePhoneFeedParams(0, 20, "test-token", TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID, 123L)
        val tv = buildSignedHomePhoneFeedParams(0, 20, "test-token", TokenManager.ACCESS_TOKEN_PLATFORM_TV, 123L)
        assertEquals(AppSignUtils.ANDROID_APP_KEY, android["appkey"])
        assertNotEquals(tv["sign"], android["sign"])
        assertEquals(AppSignUtils.signForAndroidApi(android - "sign")["sign"], android["sign"])
    }

    @Test
    fun blankUnknownAndHdCredentialsCannotUsePhoneSigning() {
        assertFailsWith<IllegalArgumentException> {
            buildSignedHomePhoneFeedParams(0, 20, " ", TokenManager.ACCESS_TOKEN_PLATFORM_TV)
        }
        for (platform in listOf(TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD, "unknown")) {
            assertFailsWith<IllegalArgumentException> {
                buildSignedHomePhoneFeedParams(0, 20, "test-token", platform)
            }
        }
        assertEquals(HomeFeedSource.HD, homeMobileFeedSource(TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD))
    }

    @Test
    fun unavailableMobileCredentialProducesWebOnlyResultAndNotice() {
        val web = listOf(VideoItem(bvid = "web"))
        val page = mergeHomeFeedResults(Result.success(web), Result.failure(IllegalStateException("missing credential")), HomeFeedSource.MOBILE).getOrThrow()
        assertEquals(web, page.videos)
        assertEquals(setOf(HomeFeedSource.WEB), page.sources)
        assertNotNull(page.notice)
    }

    @Test
    fun mergedFeedsDeduplicateAndKeepTheirActualSources() {
        val web = listOf(VideoItem(bvid = "shared"), VideoItem(bvid = "web"))
        val mobile = listOf(VideoItem(bvid = "phone"), VideoItem(bvid = "shared"))
        val page = mergeHomeFeedResults(Result.success(web), Result.success(mobile), HomeFeedSource.MOBILE).getOrThrow()
        assertEquals(listOf("phone", "shared", "web"), page.videos.map { it.bvid })
        assertEquals(setOf(HomeFeedSource.WEB, HomeFeedSource.MOBILE), page.sources)
        assertNull(page.notice)
    }

    @Test
    fun webFailureAndHdCompatibilityAreVisible() {
        val mobile = Result.success(listOf(VideoItem(bvid = "hd")))
        val fallback = mergeHomeFeedResults(Result.failure(IllegalStateException("web failed")), mobile, HomeFeedSource.HD).getOrThrow()
        assertEquals(setOf(HomeFeedSource.HD), fallback.sources)
        assertTrue(fallback.notice.orEmpty().contains("网页端推荐不可用"))
        val merged = mergeHomeFeedResults(Result.success(listOf(VideoItem(bvid = "web"))), mobile, HomeFeedSource.HD).getOrThrow()
        assertTrue(merged.notice.orEmpty().contains("平板端"))
    }

    @Test
    fun emptyBranchesFailAndCancellationCannotBecomeFallback() {
        val empty = Result.success(emptyList<VideoItem>())
        assertTrue(mergeHomeFeedResults(empty, empty, HomeFeedSource.MOBILE).isFailure)
        val cancelled = Result.failure<List<VideoItem>>(CancellationException("cancelled"))
        val videos = Result.success(listOf(VideoItem(bvid = "web")))
        assertFailsWith<CancellationException> { mergeHomeFeedResults(videos, cancelled, HomeFeedSource.MOBILE) }
        assertFailsWith<CancellationException> { mergeHomeFeedResults(cancelled, videos, HomeFeedSource.MOBILE) }
    }
}
