package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Page

enum class PlayUrlSource {
    APP,
    DASH,
    HTML5,
    LEGACY,
    GUEST
}

enum class PlayUrlRequestKind {
    INITIAL,
    PLAYBACK_TRANSITION,
    EXPLICIT
}

fun isStrictPremiumQualityRequest(
    requestKind: PlayUrlRequestKind,
    targetQn: Int
): Boolean {
    return requestKind == PlayUrlRequestKind.EXPLICIT && (targetQn == 100 || targetQn >= 112)
}

data class VideoInfoLookupInput(
    val bvid: String,
    val aid: Long
)

fun resolveVideoInfoLookupInput(rawBvid: String, aid: Long): VideoInfoLookupInput? {
    val normalizedBvid = rawBvid.trim()
    if (normalizedBvid.startsWith("BV", ignoreCase = true)) {
        return VideoInfoLookupInput(bvid = normalizedBvid, aid = 0L)
    }

    if (aid > 0L) {
        return VideoInfoLookupInput(bvid = "", aid = aid)
    }

    val normalizedAv = normalizedBvid.lowercase()
    if (normalizedAv.startsWith("av")) {
        val parsedAid = normalizedAv.removePrefix("av").toLongOrNull()
        if (parsedAid != null && parsedAid > 0L) {
            return VideoInfoLookupInput(bvid = "", aid = parsedAid)
        }
    }

    normalizedBvid.toLongOrNull()?.takeIf { it > 0L }?.let { parsedAid ->
        return VideoInfoLookupInput(bvid = "", aid = parsedAid)
    }

    return null
}

fun resolveRequestedVideoCid(
    requestCid: Long,
    infoCid: Long,
    pages: List<Page>
): Long {
    val normalizedRequestCid = requestCid.takeIf { it > 0L }
    val normalizedInfoCid = infoCid.takeIf { it > 0L }

    if (normalizedRequestCid != null) {
        if (pages.isEmpty() || pages.any { it.cid == normalizedRequestCid }) {
            return normalizedRequestCid
        }
    }

    return normalizedInfoCid ?: normalizedRequestCid ?: 0L
}

/** 低清目标直接取流；其余目标先发现画质目录，再由播放层选择偏好。 */
fun resolveInitialStartQuality(

    targetQuality: Int?,

    isAutoHighestQuality: Boolean,

    isLogin: Boolean,

    isVip: Boolean,

    auto1080pEnabled: Boolean,

    unlockPremiumQuality: Boolean = false,

    autoHighestQualityCap: Int = 0

): Int {

    val resolved = when {

        // The API documents 127 as the highest qn and 126/125 as separate Dolby/HDR

        // tiers. Requesting 125 here can omit the Dolby track until a manual switch.

        isAutoHighestQuality && isVip -> 127

        //  试用解锁 + 自动画质上限=杜比视界(126)：首播直接精确请求 126

        //  （与手动切换走同一条正常通道），视频没有杜比流时由请求层逐级降档，

        //  不会卡住起播；这样"设置杜比 → 进去就是杜比"才成立。

        isAutoHighestQuality && isLogin && unlockPremiumQuality && autoHighestQualityCap == 126 -> 126

        //  试用解锁 + 自动画质上限=4K HDR(125)：与杜比视界(126) 同理，

        //  125 不做精确请求就不会随 120 响应下发，HDR 轨会拿不到。

        isAutoHighestQuality && isLogin && unlockPremiumQuality && autoHighestQualityCap == 125 -> 125

        //  试用解锁开启时，非会员的"自动最高"直接请求 4K，实际档位由试用流决定。

        isAutoHighestQuality && isLogin && unlockPremiumQuality -> 120

        isAutoHighestQuality && isLogin -> 80

        isAutoHighestQuality -> 64

        //  只接受合法正数档位；0 / 负数视为「未指定」，交给下面的默认策略兜底。
        targetQuality != null && targetQuality > 0 -> targetQuality
        isVip -> 116

        isLogin && auto1080pEnabled -> 80

        isLogin -> 64

        else -> 32

    }

    //  自动最高画质上限：用户可限制自动模式的起播档位（默认 1080P）。

    //  杜比视界(126)/HDR(125)/8K(127) 是独立通道，直接请求会让服务端整体降档甚至

    //  返回不可播放的轨道；限制在稳定档位后默认就能正常播放，手动切换不受影响。

    return clampAutoHighestStartQuality(

        resolved = resolved,

        isAutoHighestQuality = isAutoHighestQuality,

        autoHighestQualityCap = autoHighestQualityCap

    )

}



/**

 * 把「自动最高画质」解析出的起播档位收敛到用户设置的画质上限之内。

 *

 * @param autoHighestQualityCap 0 表示不限制（保留旧行为）；>0 时作为自动模式的档位上限。

 *   非自动模式（用户显式指定画质）不做任何收敛，手动选择必须原样请求。

 */

fun clampAutoHighestStartQuality(

    resolved: Int,

    isAutoHighestQuality: Boolean,

    autoHighestQualityCap: Int

): Int {

    if (!isAutoHighestQuality) return resolved

    if (autoHighestQualityCap <= 0) return resolved

    return minOf(resolved, autoHighestQualityCap)

}


fun isPlayUrlRiskControlResponse(code: Int, hasVoucher: Boolean): Boolean =
    hasVoucher || code in setOf(-351, -352, -412)

fun resolveVideoPlaybackAuthState(
    hasSessionCookie: Boolean,
    hasAccessToken: Boolean
): Boolean {
    return hasSessionCookie
}

fun shouldSkipPlayUrlCache(
    isAutoHighestQuality: Boolean,
    isVip: Boolean,
    audioLang: String?
): Boolean {
    // Language-specific streams must not reuse the default-language cache entry.
    // Highest-tier discovery caches the account's returned catalogue, including
    // legitimate lower-quality results for videos without premium representations.
    return audioLang != null
}

/** 同一播放请求只查询目标画质，不按档位放大失败请求。 */
fun buildDashAttemptQualities(targetQn: Int): List<Int> = listOf(targetQn)

fun resolveDashRetryDelays(targetQn: Int, isPrimaryAttempt: Boolean = false): List<Long> = listOf(0L)

fun shouldRetryOnlyTransientEmptyDashResponse(targetQn: Int, isPrimaryAttempt: Boolean): Boolean = false

fun shouldRetryDashTrackRecovery(
    targetQn: Int,
    returnedQuality: Int,
    acceptQualities: List<Int>,
    dashVideoIds: List<Int>
): Boolean {
    if (targetQn <= 0 || targetQn > 80) return false
    if (returnedQuality >= targetQn) return false
    if (targetQn !in acceptQualities) return false
    return targetQn !in dashVideoIds
}

fun buildStartQualityDecisionSummary(
    bvid: String,
    cid: Long,
    userSettingQuality: Int?,
    startQuality: Int,
    isAutoHighestQuality: Boolean,
    isLoggedIn: Boolean,
    isVip: Boolean,
    auto1080pEnabled: Boolean,
    audioLang: String?
): String {
    return "PLAY_DIAG start_quality bvid=$bvid cid=$cid userSetting=${userSettingQuality ?: "null"} " +
        "start=$startQuality autoHighest=$isAutoHighestQuality " +
        "isLoggedIn=$isLoggedIn isVip=$isVip auto1080p=$auto1080pEnabled " +
        "audioLang=${audioLang ?: "default"}"
}

fun buildPlayUrlFetchSummary(
    bvid: String,
    cid: Long,
    source: PlayUrlSource,
    requestedQuality: Int,
    returnedQuality: Int,
    acceptQualities: List<Int>,
    dashVideoIds: List<Int>,
    hasDurl: Boolean,
    isLoggedIn: Boolean,
    isVip: Boolean,
    audioLang: String?
): String {
    return "PLAY_DIAG fetch_result bvid=$bvid cid=$cid source=$source requested=$requestedQuality " +
        "returned=$returnedQuality accept=$acceptQualities dash=$dashVideoIds " +
        "hasDurl=$hasDurl isLoggedIn=$isLoggedIn isVip=$isVip " +
        "audioLang=${audioLang ?: "default"}"
}

fun shouldCallAccessTokenApi(
    nowMs: Long,
    cooldownUntilMs: Long,
    hasAccessToken: Boolean
): Boolean {
    return hasAccessToken && nowMs >= cooldownUntilMs
}

fun shouldTryAppApiForTargetQuality(
    targetQn: Int,
    hasSessionCookie: Boolean = true,
    directedTrafficMode: Boolean = false
): Boolean {
    // BiliPai parity: playback stays on the Web/WBI playurl path instead of
    // prioritizing the APP access_token endpoint for 1080P and premium tiers.
    return false
}

fun shouldEnableDirectedTrafficMode(
    directedTrafficEnabled: Boolean,
    isOnMobileData: Boolean
): Boolean {
    return directedTrafficEnabled && isOnMobileData
}

fun buildDirectedTrafficWbiOverrides(
    directedTrafficEnabled: Boolean,
    isOnMobileData: Boolean
): Map<String, String> {
    // 定向流量设置只影响媒体地址处理，不能改变 Web 签名请求的平台身份。
    return emptyMap()
}

fun buildPlayUrlWbiBaseParams(
    bvid: String,
    cid: Long,
    qn: Int,
    audioLang: String? = null,
    tryLook: Boolean = false
): MutableMap<String, String> {
    val params = linkedMapOf(
        "bvid" to bvid,
        "cid" to cid.toString(),
        "qn" to qn.toString(),
        "fnval" to "4048",
        "fnver" to "0",
        "fourk" to "1",
        "voice_balance" to "1",
        "gaia_source" to "pre-load",
        "isGaiaAvoided" to "true",
        "web_location" to "1315873",
        "dm_img_list" to "[]",
        "dm_img_str" to randomPlayUrlFingerprint(16, 64),
        "dm_cover_img_str" to randomPlayUrlFingerprint(32, 128),
        "dm_img_inter" to "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}"
    )
    if (tryLook) {
        params["try_look"] = "1"
    }
    if (!audioLang.isNullOrEmpty()) {
        params["cur_language"] = audioLang
    }
    return params
}

fun shouldRequestPlayUrlTryLook(
    isLoggedIn: Boolean,
    auto1080pEnabled: Boolean
): Boolean {
    return !isLoggedIn && auto1080pEnabled
}

private fun randomPlayUrlFingerprint(minLength: Int, maxLength: Int): String {
    val bytes = ByteArray(kotlin.random.Random.nextInt(minLength, maxLength + 1)) {
        kotlin.random.Random.nextInt(0x26, 0x7f).toByte()
    }
    return java.util.Base64.getEncoder().encodeToString(bytes).dropLast(2)
}

fun buildLoggedInPlaybackFallbackOrder(): List<PlayUrlSource> = listOf(PlayUrlSource.DASH)

fun buildGuestPlaybackFallbackOrder(): List<PlayUrlSource> = listOf(PlayUrlSource.DASH)

fun shouldAcceptAppApiResultForTargetQuality(
    requestKind: PlayUrlRequestKind,
    targetQn: Int,
    returnedQuality: Int,
    dashVideoIds: List<Int>
): Boolean {
    if (requestKind != PlayUrlRequestKind.EXPLICIT) {
        // Startup and media transitions should keep any playable payload to avoid a hard
        // failure when the service downgrades or the next part lacks the previous part's tier.
        return returnedQuality > 0 || dashVideoIds.isNotEmpty()
    }

    if (isStrictPremiumQualityRequest(requestKind, targetQn)) {
        return targetQn in dashVideoIds
    }

    // Explicit quality selection must respect the requested target for both VIP
    // and non-VIP users; otherwise the UI reports a successful switch while the
    // backend silently returns a lower tier.
    if (targetQn < 80) return true
    // In DASH responses `quality` is response metadata, not proof that the
    // requested representation is playable. Only an exact playable track may
    // finish an explicit high-quality request; otherwise report the unavailable tier.
    return targetQn in dashVideoIds
}

fun buildGuestFallbackQualities(): List<Int> {
    return listOf(80, 64, 32)
}

fun shouldCachePlayUrlResult(
    source: PlayUrlSource,
    audioLang: String?,
    requestedQuality: Int = 0,
    returnedQuality: Int = 0,
    dashVideoIds: List<Int> = emptyList(),
    requestKind: PlayUrlRequestKind = PlayUrlRequestKind.EXPLICIT
): Boolean {
    if (audioLang != null) return false
    if (source == PlayUrlSource.GUEST) return false
    // A discovery request may legitimately return a lower tier for this account/video.
    if (requestKind == PlayUrlRequestKind.EXPLICIT && requestedQuality >= 80 && !isRequestedQualitySatisfied(
            requestedQuality = requestedQuality,
            returnedQuality = returnedQuality,
            dashVideoIds = dashVideoIds
        )
    ) {
        return false
    }
    return true
}

fun isRequestedQualitySatisfied(
    requestedQuality: Int,
    returnedQuality: Int,
    dashVideoIds: List<Int>
): Boolean {
    if (requestedQuality < 80) return true
    return requestedQuality in dashVideoIds
}

/**
 * Returns true when the DASH manifest contains a playable exact track the user requested.
 *
 * Premium qualities (125 HDR, 126 Dolby Vision, 127 8K) must not be considered
 * satisfied by a lower-tier response or by an exact-ID track without a URL.
 */
fun hasExactPlayableRequestedTrack(
    requestedTargetQn: Int,
    dashVideos: List<DashVideo>
): Boolean {
    return dashVideos.any { video ->
        video.id == requestedTargetQn && video.getValidUrl().isNotEmpty()
    }
}

fun isExactRequestedQualitySelected(
    requestedTargetQn: Int,
    actualQuality: Int
): Boolean = actualQuality == requestedTargetQn

/**
 * Determines whether a non-blocking HDR auto-upgrade should be scheduled after
 * an INITIAL SDR fast-start playback.
 *
 * All conditions must be met:
 * 1. This is an INITIAL (first-load) request
 * 2. User has auto-highest quality enabled
 * 3. User is VIP (premium account)
 * 4. Not on mobile data (Wi-Fi only)
 * 5. Valid access_token available
 * 6. Current DASH does not already include HDR or a higher tier (125–129)
 * 7. User hasn't made an explicit quality selection
 * 8. Upgrade hasn't already been attempted for this playback session
 */
fun shouldScheduleHdrAutoUpgrade(
    isInitialRequest: Boolean,
    isAutoHighestQuality: Boolean,
    isVip: Boolean,
    isMobileData: Boolean,
    hasAccessToken: Boolean,
    currentPlayableDashVideoIds: List<Int>,
    userHasExplicitQualitySelection: Boolean,
    upgradeAlreadyAttempted: Boolean
): Boolean {
    return isInitialRequest &&
        isAutoHighestQuality &&
        isVip &&
        !isMobileData &&
        hasAccessToken &&
        currentPlayableDashVideoIds.none { it >= 125 } &&
        !userHasExplicitQualitySelection &&
        !upgradeAlreadyAttempted
}

fun shouldFetchCommentEmoteMapOnVideoLoad(): Boolean {
    return false
}

fun shouldRefreshVipStatusOnVideoLoad(): Boolean {
    return false
}

fun shouldFetchInteractionStatusOnVideoLoad(): Boolean {
    return false
}
