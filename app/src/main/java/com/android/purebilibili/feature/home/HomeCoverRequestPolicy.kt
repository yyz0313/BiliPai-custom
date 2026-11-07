package com.android.purebilibili.feature.home

import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.cards.resolveVideoCardCoverCacheKey
import kotlin.math.ceil

internal data class HomeCoverRequestSpec(
    val widthPx: Int,
    val heightPx: Int,
) {
    val cacheKeySuffix: String = "${widthPx}x$heightPx"

    fun resolveUrl(url: String?): String =
        FormatUtils.buildSizedImageUrl(url, width = widthPx, height = heightPx)
}

/** Shared identity for the visible cover and its ahead-of-scroll preload. */
internal data class HomeCoverImageSource(val url: String, val cacheKey: String)

internal fun resolveHomeCoverImageSource(
    video: VideoItem,
    useLowQualityCover: Boolean,
    requestSpec: HomeCoverRequestSpec? = null,
): HomeCoverImageSource = HomeCoverImageSource(
    url = requestSpec?.resolveUrl(video.pic)
        ?: FormatUtils.resolveVideoCoverUrl(video.pic, useLowQuality = useLowQualityCover),
    cacheKey = resolveVideoCardCoverCacheKey(video, useLowQualityCover, requestSpec),
)

internal fun resolveHomeCoverRequestSpec(
    cardWidthDp: Float,
    density: Float,
    useLowQualityCover: Boolean,
): HomeCoverRequestSpec {
    if (useLowQualityCover) {
        return HomeCoverRequestSpec(widthPx = 240, heightPx = 150)
    }

    val sampledWidthPx = ceil(cardWidthDp.coerceAtLeast(0f) * density.coerceAtLeast(0f) * 1.25f)
        .toInt()
    val widthPx = HOME_COVER_WIDTH_TIERS.firstOrNull { it >= sampledWidthPx }
        ?: HOME_COVER_WIDTH_TIERS.last()
    return HomeCoverRequestSpec(
        widthPx = widthPx,
        heightPx = widthPx * 10 / 16,
    )
}

private val HOME_COVER_WIDTH_TIERS = intArrayOf(480, 640, 960, 1280)
