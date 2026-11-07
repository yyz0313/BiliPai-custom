package com.android.purebilibili.core.util

import com.android.purebilibili.data.model.response.BangumiVideoInfo
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Durl
import com.android.purebilibili.data.model.response.PlayUrlData

/**
 * B 站媒体地址（`/upgcxcode/...` 的 DASH / durl 流）现在只接受 HTTPS。
 *
 * 用 `http://` 请求时连接与响应头都正常（OkHttp 能打出 `Network protocol: http/1.1`），
 * 但服务端不会下发任何 body：播放器表现为「一直缓冲、永远等不到首帧」，随后被首帧
 * 看门狗判超时并降档。App 侧通道（PlayViewUnite / playurl v2）默认下发 `http://` 地址，
 * 因此只有 App 通道才有的档位（4K / 1080P+ / 杜比视界）会稳定失败，而 Web 通道下发的
 * `https://` 档位正常。
 *
 * 媒体地址的防链签名（`upsig` + `uparams` + `e`）只覆盖 query 参数，不含 scheme，
 * 因此把 http 升级为 https 不会破坏签名。
 *
 * 裸 IP（PCDN 直连）没有匹配的 TLS 证书，保持原样交给既有回退逻辑处理。
 */
private const val HTTP_SCHEME_PREFIX = "http://"
private const val HTTPS_SCHEME_PREFIX = "https://"

/** 裸 IPv4（可带端口）。这类地址是 PCDN 直连，不能升级到 https。 */
private val bareIpv4Authority = Regex("""^\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?$""")

/** 只有 B 站自家的媒体域名才做升级，避免把其它 http 源改成不可用的 https。 */
private const val BILIVIDEO_HOST_MARKER = "bilivideo"

/**
 * App（试看）通道签名地址：URL query 带 `platform=android`（粉版）/ `android_i`（旧平板版）/
 * `android_hd` 等前缀，且属于媒体下载路径（`/upgcxcode/`）。
 *
 * 实测口径（2026-10-09 审计，见 v19 交付说明）：pc 与 android 两类签名地址走同一批 CDN
 * 主机，但授权口径不同——同一分钟、同主机、同文件，pc 签名 206、android 签名 403。
 * 官方客户端可正常播放这类地址，因此对它们采用「最小触碰」策略：只做协议升级，
 * 不做备份提升 / 稳定优选 / 主机偏好改写，保留服务端原始候选顺序。
 */
private val appSignedPlatformQueryRegex =
    Regex("""[?&]platform=android(?:[_-][A-Za-z0-9]*)?(?:&|$)""", RegexOption.IGNORE_CASE)

fun isAppSignedMediaUrl(url: String): Boolean {
    if (url.isBlank()) return false
    if (!url.contains("/upgcxcode/", ignoreCase = true)) return false
    return appSignedPlatformQueryRegex.containsMatchIn(url)
}

/**
 * 把 B 站媒体地址的 `http://` 升级为 `https://`；不满足条件时原样返回。
 *
 * 不做任何 query 改写，只替换 scheme 前缀，因此签名参数保持逐字节不变。
 */
fun upgradeMediaUrlToHttps(url: String): String {
    if (url.length <= HTTP_SCHEME_PREFIX.length) return url
    if (!url.startsWith(HTTP_SCHEME_PREFIX, ignoreCase = true)) return url
    val authorityStart = HTTP_SCHEME_PREFIX.length
    val pathStart = url.indexOf('/', authorityStart)
    val authorityEnd = if (pathStart < 0) url.length else pathStart
    val authority = url.substring(authorityStart, authorityEnd)
    if (authority.isEmpty()) return url
    val host = authority.substringBefore(':').substringAfter('@')
    if (host.isEmpty()) return url
    if (bareIpv4Authority.matches(host)) return url
    if (!host.contains(BILIVIDEO_HOST_MARKER, ignoreCase = true)) return url
    return HTTPS_SCHEME_PREFIX + url.substring(authorityStart)
}

/**
 * 把「可 https 化」的候选提升为主地址。
 *
 * 服务端给试用档（4K / 1080P+ / 1080P60 / 杜比视界）下发的主地址经常是裸 IP（PCDN）
 * 或第三方域名，这两类没有匹配证书、无法升级为 https，只能原样交给播放器；而同一批
 * `backup_url` 里通常带有一条 bilivideo 镜像地址，可以走 https。
 *
 * 实测口径：同一台设备、跨两天三个会话，`http://` 媒体请求 71 次全部拿不到首帧
 * （`Network protocol: http/1.1` 已打印、响应到手但没有数据），`https://` 媒体请求 16 次
 * 全部正常出画。因此主地址无法升级 https 时，直接把 backup 里的 https 地址提升为主
 * 地址，原主地址退回 backup 列表末尾（仍作为后续回退候选）。
 *
 * 防链签名（`upsig` / `uparams` / `e`）只覆盖 query 参数，不含 host 与 scheme，
 * 因此提升地址、升级协议都不会破坏签名。
 */
internal fun promoteHttpsCapableMediaUrl(
    baseUrl: String,
    backupUrls: List<String>?
): Pair<String, List<String>?> {
    if (baseUrl.isBlank()) return baseUrl to backupUrls
    //  试看（App 签名）地址：只做协议升级，不做备份提升——见 isAppSignedMediaUrl 注释。
    if (isAppSignedMediaUrl(baseUrl)) {
        return upgradeMediaUrlToHttps(baseUrl) to backupUrls?.map(::upgradeMediaUrlToHttps)
    }
    if (baseUrl.startsWith(HTTPS_SCHEME_PREFIX, ignoreCase = true)) {
        return baseUrl to backupUrls?.map(::upgradeMediaUrlToHttps)
    }
    val upgradedBase = upgradeMediaUrlToHttps(baseUrl)
    if (upgradedBase != baseUrl) {
        return upgradedBase to backupUrls?.map(::upgradeMediaUrlToHttps)
    }
    val upgradedBackups = backupUrls?.map(::upgradeMediaUrlToHttps)
    val promoted = upgradedBackups
        ?.firstOrNull { it.startsWith(HTTPS_SCHEME_PREFIX, ignoreCase = true) && it != baseUrl }
        ?: return baseUrl to upgradedBackups
    val remaining = (listOf(baseUrl) + upgradedBackups.filterNot { it == promoted }).distinct()
    return promoted to remaining
}

private fun normalizeDashVideoScheme(track: DashVideo): DashVideo {
    val (baseUrl, backupUrl) = promoteHttpsCapableMediaUrl(track.baseUrl, track.backupUrl)
    if (baseUrl == track.baseUrl && backupUrl == track.backupUrl) return track
    return track.copy(baseUrl = baseUrl, backupUrl = backupUrl)
}


private fun normalizeDashAudioScheme(track: DashAudio): DashAudio {
    val (baseUrl, backupUrl) = promoteHttpsCapableMediaUrl(track.baseUrl, track.backupUrl)
    if (baseUrl == track.baseUrl && backupUrl == track.backupUrl) return track
    return track.copy(baseUrl = baseUrl, backupUrl = backupUrl)
}


private fun normalizeDurlScheme(item: Durl): Durl {
    val (url, backupUrl) = promoteHttpsCapableMediaUrl(item.url, item.backupUrl)
    if (url == item.url && backupUrl == item.backupUrl) return item
    return item.copy(url = url, backupUrl = backupUrl)
}


/** 对 DASH 响应做 http -> https 归一化（视频 / 音频 / 杜比 / FLAC）。 */
fun normalizeDashMediaScheme(dash: Dash): Dash = dash.copy(
    video = dash.video.map(::normalizeDashVideoScheme),
    audio = dash.audio?.map(::normalizeDashAudioScheme),
    dolby = dash.dolby?.let { dolby -> dolby.copy(audio = dolby.audio?.map(::normalizeDashAudioScheme)) },
    flac = dash.flac?.let { flac ->
        flac.audio?.let { audio -> flac.copy(audio = normalizeDashAudioScheme(audio)) } ?: flac
    }
)

/**
 * 对整个 playurl 响应做 http -> https 归一化（DASH 视频/音频/杜比/FLAC + durl 兜底流）。
 *
 * 幂等：已经是 https 或没有可升级地址时返回原对象。
 */
fun normalizePlayUrlMediaScheme(data: PlayUrlData): PlayUrlData {
    val dash = data.dash
    val durl = data.durl
    if (dash == null && durl == null) return data
    val normalizedDash = dash?.let(::normalizeDashMediaScheme)
    val normalizedDurl = durl?.map(::normalizeDurlScheme)
    if (normalizedDash == dash && normalizedDurl == durl) return data
    return data.copy(dash = normalizedDash, durl = normalizedDurl)
}


/** 番剧（PGC）响应的 http -> https 归一化（DASH + durl / durls）。 */
fun normalizeBangumiMediaScheme(info: BangumiVideoInfo): BangumiVideoInfo {
    val dash = info.dash
    val durl = info.durl
    val durls = info.durls
    if (dash == null && durl == null && durls == null) return info
    return info.copy(
        dash = dash?.let(::normalizeDashMediaScheme),
        durl = durl?.map(::normalizeDurlScheme),
        durls = durls?.map(::normalizeDurlScheme)
    )
}
