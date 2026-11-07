package com.android.purebilibili.core.network

import android.os.Build
import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import com.android.purebilibili.core.util.isAppSignedMediaUrl
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 试看（App 通道签名）媒体地址的「请求身份 A/B + 学习」拦截器。
 *
 * 背景（2026-10-09 真机审计 + 有效签名对照实验，见 research13/v19-cdn-gate-experiments.md）：
 * - 试看档（4K / 1080P+ / 1080P60 等）由 App 通道下发，URL query 带 `platform=android`；
 * - 同一分钟、同主机、同文件：网页通道（platform=pc）206 正常，App 通道 403 全灭（u18 日志 70/70）；
 * - CDN 对「身份与签名通道不匹配」回 `403 + Deny-Reason: 存在nbs参数`（该文案具有误导性）；
 * - 实测放行：短 `BiliDroid/9.13.0`、`okhttp/4.12.0`、截断 Chrome UA（pc 签名地址上）。
 *
 * 策略：对 App 签名地址不再先发一遍必然失败的透传请求，直接按候选身份逐个探测；
 * 命中「2xx 且响应像真实媒体数据」的候选才记入进程级缓存（learnedProfile）并复用；
 * 明文 http 候选（P8/P6）只做兜底、永不学习（http 媒体有「响应头正常但无 body」的历史问题）。
 * 全部候选失败后按签名（deadline/e）记耗尽状态，60 秒内不再逐档重试，直接透传。
 *
 * v21 增补：加入官方播放器形态候选（完整 App UA + 明文 http Referer，P7/P8），依据官方
 * 9.2.0 播放器默认 Referer 为 http://www.bilibili.com（IjkUtils，见 research13/v21-ref-impl-media-identity.md）。
 *
 * v22 增补（关键）：加入官方 ijk 播放器 UA 候选（P11/P12/P13，`Bilibili Freedoooooom/MarkII`）。
 * 官方 9.13.0 反编译证据显示：App 播放媒体时发的 UA 是播放器 UA 而非 API 的 `BiliDroid` UA，
 * 且官方 `IjkMediaConfigParams.mReferer` 默认为 null（即不发 Referer）。此前所有候选都不是这个身份。
 *
 * 只对 App 签名媒体地址生效（/upgcxcode/ + platform=android*），不触碰其它请求。
 */
internal class TrialMediaHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isTrialMediaRequest(request)) return chain.proceed(request)

        //  已学到可用身份：直接复用；若再次 403 也不清空学习结果（避免一次边缘 403 丢掉可用身份），
        //  继续走一轮重新发现，只有实测命中新身份时才替换。
        learnedProfile?.let { learned ->
            val response = chain.proceed(applyTrialMediaProfile(request, learned))
            logAttempt(learned, response, request)
            if (response.code != HTTP_FORBIDDEN && isPlausibleMediaResponse(response)) return response
            response.close()
        }

        val signatureKey = mediaSignatureKey(request.url)
        if (isDiscoveryExhaustedNow(signatureKey)) {
            return chain.proceed(applyTrialMediaProfile(request, TrialMediaProfile.PASSTHROUGH))
        }

        //  直接进入候选探测：App 通道已实测「桌面身份必被拒」，先发透传只是浪费一次往返。
        var last: Response? = null
        try {
            for (profile in discoveryOrder()) {
                if (profile.isPlainHttpDowngrade && !request.url.isHttps) continue
                //  v22 关键修复：必须在发起下一次 chain.proceed 之前关闭上一次未消费的响应。
                //  v20/v21 把 close 放在赋值之后，OkHttp 会抛 IllegalStateException
                //  (previous response is still open)，探测循环只跑完第一个候选就崩溃，
                //  其余候选（含与地址 build 完全一致的完整官方身份）从未被真正测试过。
                last?.close()
                last = null
                val response = chain.proceed(applyTrialMediaProfile(request, profile))
                logAttempt(profile, response, request)
                if (isPlausibleMediaResponse(response)) {
                    if (profile.isLearnable) {
                        learnedProfile = profile
                        CoreDataLog.w(TAG, "learned profile=${profile.label} host=${request.url.host}")
                    }
                    return response
                }
                last = response
            }
        } catch (error: Throwable) {
            //  探测中途出错（超时 / 连接失败）：先关掉上一次未消费的响应，再原样抛出。
            last?.close()
            throw error
        }

        discoveryExhaustedKey = signatureKey
        discoveryExhaustedAtMs = System.currentTimeMillis()
        CoreDataLog.w(TAG, "all trial-media profiles rejected; fall back to baseline headers")
        return last ?: chain.proceed(applyTrialMediaProfile(request, TrialMediaProfile.PASSTHROUGH))
    }

    private fun logAttempt(profile: TrialMediaProfile, response: Response, request: Request) {
        CoreDataLog.w(
            TAG,
            "profile=${profile.label} scheme=${request.url.scheme} code=${response.code} " +
                "deny=${response.header("Deny-Reason")} upsig=${response.header("X-Upsig-Version")} " +
                "server=${response.header("server")} host=${request.url.host} " +
                "file=${request.url.encodedPath.substringAfterLast('/')}"
        )
    }

    companion object {
        private const val TAG = "TrialMedia"
        private const val HTTP_FORBIDDEN = 403

        /** 耗尽状态在多久后允许重新探测（同一签名的地址批次内不重复放大请求）。 */
        private const val DISCOVERY_EXHAUSTED_TTL_MS = 60_000L

        /** 进程级记忆：一次会话里学到的可用身份；无可用身份时为 null。 */
        @Volatile
        internal var learnedProfile: TrialMediaProfile? = null

        /** 最近一次「全部候选失败」对应的签名键；用于按地址批次短路。 */
        @Volatile
        internal var discoveryExhaustedKey: String? = null

        /** 最近一次「全部候选失败」的时间戳。 */
        @Volatile
        internal var discoveryExhaustedAtMs: Long = 0L

        /** 单元测试用：清空进程级记忆。 */
        internal fun resetDiscoveryState() {
            learnedProfile = null
            discoveryExhaustedKey = null
            discoveryExhaustedAtMs = 0L
        }

        private fun isDiscoveryExhaustedNow(signatureKey: String): Boolean {
            val saved = discoveryExhaustedKey ?: return false
            if (saved != signatureKey) return false
            return System.currentTimeMillis() - discoveryExhaustedAtMs < DISCOVERY_EXHAUSTED_TTL_MS
        }
    }
}

/**
 * 媒体签名「批次键」：优先用 `deadline` / `e`（同一批 playurl 地址共享），
 * 缺失时退回主机名。签名变化即视为新批次，允许重新探测身份。
 */
internal fun mediaSignatureKey(url: HttpUrl): String {
    val deadline = url.queryParameter("deadline")
    val expiry = url.queryParameter("e")
    return listOfNotNull(deadline, expiry).joinToString("|").ifBlank { url.host }
}

/** 身份发现顺序（按实测放行证据排序）。 */
internal fun discoveryOrder(): List<TrialMediaProfile> = listOf(
    //  v22 重排（按「与官方真实行为的一致度」排序）：
    //  1) 官方 ijk 播放器 UA = `Bilibili Freedoooooom/MarkII`，且官方 `mReferer` 默认 null
    //     → 这是官方 App 播放会员媒体时的真实身份，理论上才是 CDN 认的身份。
    //  2) 完整 BiliDroid App 形态 UA（版本与地址 build 一致 = 7.76.0）。
    //  3) 短 BiliDroid UA（版本与地址一致）。
    //  4) 短 BiliDroid UA（历史版本号）。
    //  5) 明文 http 降级（旧客户端形态，只探测不学习）。
    TrialMediaProfile.PLAYER_UA_NO_REFERER,
    TrialMediaProfile.PLAYER_UA_REFERER,
    TrialMediaProfile.PLAYER_UA_HTTP_REFERER,
    TrialMediaProfile.APP_UA_REFERER,
    TrialMediaProfile.APP_UA_HTTP_REFERER,
    TrialMediaProfile.APP_UA_MATCHED_MINIMAL_REFERER,
    TrialMediaProfile.APP_UA_MATCHED_MINIMAL_HTTP_REFERER,
    TrialMediaProfile.APP_UA_MINIMAL_REFERER,
    TrialMediaProfile.APP_UA_NO_REFERER,
    TrialMediaProfile.OKHTTP_UA_REFERER,
    TrialMediaProfile.APP_UA_HTTP_REFERER_PLAIN_HTTP,
    TrialMediaProfile.APP_UA_REFERER_PLAIN_HTTP,
)

/** 只对「App 通道签名的媒体地址」生效（/upgcxcode/ + platform=android*）。 */
internal fun isTrialMediaRequest(request: Request): Boolean {
    if (request.method != "GET" && request.method != "HEAD") return false
    if (!request.url.encodedPath.contains("/upgcxcode/", ignoreCase = true)) return false
    return isAppSignedMediaUrl(request.url.toString())
}

/**
 * 响应是否像「真实媒体数据」：2xx 且（带 Content-Range 或 body 长度不为 0）。
 * 用于防止把「响应头正常但没有 body」的假成功学成可用身份。
 */
internal fun isPlausibleMediaResponse(response: Response): Boolean {
    if (response.code !in 200..299) return false
    if (response.header("Content-Range") != null) return true
    return response.body.contentLength() != 0L
}

/**
 * 试看媒体请求的候选身份：
 * - PASSTHROUGH：原样透传（现状 = 桌面 Chrome UA + 网页 Referer；仅用于兜底）
 * - APP_UA_*：官方 App UA（与取流身份一致的粉版 android / 7.76.0 形态）
 * - OKHTTP_UA_REFERER：okhttp/4.12.0（实测被 CDN 放行的写法）
 * - CHROME_UA_NO_REFERER：去掉 Referer 的对照
 * - APP_UA_HTTP_REFERER：官方播放器形态（完整 App UA + 明文 http Referer，IjkUtils 默认）
 * - APP_UA_*_PLAIN_HTTP：官方身份 + 明文 http scheme（旧平板客户端的形态，只探测不学习）
 */
internal enum class TrialMediaProfile(
    val label: String,
    val isLearnable: Boolean,
    /** 是否需要把 https 地址降级为明文 http（旧客户端形态）。 */
    val isPlainHttpDowngrade: Boolean = false,
) {
    PASSTHROUGH("P0", false),
    APP_UA_REFERER("P1", true),
    APP_UA_MINIMAL_REFERER("P2", true),
    APP_UA_NO_REFERER("P3", true),
    OKHTTP_UA_REFERER("P4", true),
    CHROME_UA_NO_REFERER("P5", true),
    APP_UA_REFERER_PLAIN_HTTP("P6", false, isPlainHttpDowngrade = true),
    APP_UA_HTTP_REFERER("P7", true),
    APP_UA_HTTP_REFERER_PLAIN_HTTP("P8", false, isPlainHttpDowngrade = true),
    /** 短官方 UA（版本与地址 build 一致）+ https Referer。 */
    APP_UA_MATCHED_MINIMAL_REFERER("P9", true),
    /** 短官方 UA（版本与地址 build 一致）+ 明文 http Referer（官方 ijk 默认形态）。 */
    APP_UA_MATCHED_MINIMAL_HTTP_REFERER("P10", true),
    /** 官方 ijk 播放器 UA（Bilibili Freedoooooom/MarkII）+ 不发 Referer：官方 ground truth，最高优先。 */
    PLAYER_UA_NO_REFERER("P11", true),
    /** 官方 ijk 播放器 UA + https Referer。 */
    PLAYER_UA_REFERER("P12", true),
    /** 官方 ijk 播放器 UA + 明文 http Referer。 */
    PLAYER_UA_HTTP_REFERER("P13", true),
}

internal const val TRIAL_MEDIA_REFERER = "https://www.bilibili.com"

/** 官方播放器（ijk）默认 Referer：明文 http（9.2.0 IjkUtils 默认值）。 */
internal const val TRIAL_MEDIA_HTTP_REFERER = "http://www.bilibili.com"
internal const val TRIAL_MEDIA_MINIMAL_APP_USER_AGENT = "Mozilla/5.0 BiliDroid/9.13.0 (bbcallen@gmail.com)"
internal const val TRIAL_MEDIA_OKHTTP_USER_AGENT = "okhttp/4.12.0"

/**
 * 官方 ijk 播放器媒体 UA（ground truth）。
 *
 * 证据（官方 9.13.0 反编译）：
 * - `tv.danmaku.ijk.media.player.IjkMediaConfigParams` 字段默认值
 *   `public String mUserAgent = "Bilibili Freedoooooom/MarkII"; public String mReferer = null;`
 * - UGC 取流拦截器 `DefaultFlashMediaResourceResolveInterceptorV2` 5 处
 *   `playIndex.mUserAgent = "Bilibili Freedoooooom/MarkII"`
 * - 番剧/OGV 取流拦截器 `OgvFlashMediaResourceResolveInterceptor` 同样赋值
 * - native `libijk.so` 内含字符串 `Freedoooooom/MarkII`
 *
 * 即：官方 App 播放媒体时发的 UA 不是 `BiliDroid`（那是 API 请求 UA），而是这个播放器 UA。
 */
internal const val TRIAL_MEDIA_PLAYER_USER_AGENT = "Bilibili Freedoooooom/MarkII"
internal const val TRIAL_MEDIA_CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

/** 短官方 UA，但版本号与取流身份（PINK = android / 7.76.0）一致，避免「UA 版本 vs 地址 build」不匹配。 */
internal fun buildTrialMediaMatchedMinimalAppUserAgent(): String =
    "Mozilla/5.0 BiliDroid/${BiliGrpcClient.ClientIdentity.PINK.versionName} (bbcallen@gmail.com)"

/** 与取流身份（PINK = android / 7.76.0 / build 7760700）一致的官方 App UA 模板。 */
internal fun buildTrialMediaAppUserAgent(): String {
    val identity = BiliGrpcClient.ClientIdentity.PINK
    val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "android"
    val osVer = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "15"
    return "Mozilla/5.0 BiliDroid/${identity.versionName} (bbcallen@gmail.com) os/android " +
        "model/$model mobi_app/${identity.mobiApp} build/${identity.build} channel/master " +
        "innerVer/${identity.build} osVer/$osVer network/2"
}

/** 按身份组合改写请求头（PASSTHROUGH 不改任何头）。 */
internal fun applyTrialMediaProfile(request: Request, profile: TrialMediaProfile): Request {
    val builder = request.newBuilder()
    return when (profile) {
        TrialMediaProfile.PASSTHROUGH -> request
        TrialMediaProfile.APP_UA_REFERER -> builder
            .header("User-Agent", buildTrialMediaAppUserAgent())
            .header("Referer", TRIAL_MEDIA_REFERER)
            .build()
        TrialMediaProfile.APP_UA_MINIMAL_REFERER -> builder
            .header("User-Agent", TRIAL_MEDIA_MINIMAL_APP_USER_AGENT)
            .header("Referer", TRIAL_MEDIA_REFERER)
            .build()
        TrialMediaProfile.APP_UA_MATCHED_MINIMAL_REFERER -> builder
            .header("User-Agent", buildTrialMediaMatchedMinimalAppUserAgent())
            .header("Referer", TRIAL_MEDIA_REFERER)
            .build()
        TrialMediaProfile.APP_UA_MATCHED_MINIMAL_HTTP_REFERER -> builder
            .header("User-Agent", buildTrialMediaMatchedMinimalAppUserAgent())
            .header("Referer", TRIAL_MEDIA_HTTP_REFERER)
            .build()
        TrialMediaProfile.PLAYER_UA_NO_REFERER -> builder
            .header("User-Agent", TRIAL_MEDIA_PLAYER_USER_AGENT)
            .removeHeader("Referer")
            .build()
        TrialMediaProfile.PLAYER_UA_REFERER -> builder
            .header("User-Agent", TRIAL_MEDIA_PLAYER_USER_AGENT)
            .header("Referer", TRIAL_MEDIA_REFERER)
            .build()
        TrialMediaProfile.PLAYER_UA_HTTP_REFERER -> builder
            .header("User-Agent", TRIAL_MEDIA_PLAYER_USER_AGENT)
            .header("Referer", TRIAL_MEDIA_HTTP_REFERER)
            .build()
        TrialMediaProfile.APP_UA_NO_REFERER -> builder
            .header("User-Agent", buildTrialMediaAppUserAgent())
            .removeHeader("Referer")
            .build()
        TrialMediaProfile.OKHTTP_UA_REFERER -> builder
            .header("User-Agent", TRIAL_MEDIA_OKHTTP_USER_AGENT)
            .header("Referer", TRIAL_MEDIA_REFERER)
            .build()
        TrialMediaProfile.CHROME_UA_NO_REFERER -> builder
            .header("User-Agent", TRIAL_MEDIA_CHROME_USER_AGENT)
            .removeHeader("Referer")
            .build()
        TrialMediaProfile.APP_UA_HTTP_REFERER -> builder
            .header("User-Agent", buildTrialMediaAppUserAgent())
            .header("Referer", TRIAL_MEDIA_HTTP_REFERER)
            .build()
        TrialMediaProfile.APP_UA_REFERER_PLAIN_HTTP -> {
            if (request.url.isHttps) {
                builder.url(request.url.newBuilder().scheme("http").build())
            }
            builder
                .header("User-Agent", buildTrialMediaAppUserAgent())
                .header("Referer", TRIAL_MEDIA_REFERER)
                .build()
        }
        TrialMediaProfile.APP_UA_HTTP_REFERER_PLAIN_HTTP -> {
            if (request.url.isHttps) {
                builder.url(request.url.newBuilder().scheme("http").build())
            }
            builder
                .header("User-Agent", buildTrialMediaAppUserAgent())
                .header("Referer", TRIAL_MEDIA_HTTP_REFERER)
                .build()
        }
    }
}
