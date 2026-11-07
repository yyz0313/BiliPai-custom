package com.android.purebilibili.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 媒体域名系统解析失败时的 DoH 兜底解析器。
 *
 * 只在 okhttp3.Dns.lookup 抛异常后被调用，属于救援路径：
 * - 系统解析正常时零开销；
 * - 最多消耗 [TOTAL_BUDGET_MS] 毫秒，失败后负缓存，避免每次建连都重试；
 * - 全部失败时返回 null，调用方继续抛原始异常（行为与改造前一致）。
 *
 * 硬编码的是公共 DNS 服务的长期稳定 IP（阿里 / 腾讯 / Cloudflare），不是随时会变的业务 CDN 地址。
 */
internal object DohMediaDnsFallback {

    private val mediaHostSuffixes = listOf(
        ".bilivideo.com",
        ".bilivideo.cn",
        ".hdslb.com"
    )

    private const val ATTEMPT_TIMEOUT_MS = 1_200L
    private const val TOTAL_BUDGET_MS = 2_500L
    private const val MIN_CACHE_MS = 30_000L
    private const val MAX_CACHE_MS = 300_000L
    private const val NEGATIVE_CACHE_MS = 20_000L
    private const val MIN_REMAINING_BUDGET_MS = 300L

    private data class Endpoint(
        val host: String,
        val fixedIp: String,
        val path: String
    )

    private val endpoints = listOf(
        Endpoint("dns.alidns.com", "223.5.5.5", "/resolve"),
        Endpoint("dns.alidns.com", "223.6.6.6", "/resolve"),
        Endpoint("doh.pub", "1.12.12.12", "/dns-query"),
        Endpoint("doh.pub", "120.53.53.53", "/dns-query"),
        Endpoint("cloudflare-dns.com", "1.1.1.1", "/dns-query")
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    private class CacheEntry(val ips: List<String>, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val negativeUntil = ConcurrentHashMap<String, Long>()

    /**
     * DoH 专用客户端缓存，键为「端点 IP @ 单次超时档位」。
     *
     * 为什么要按端点 IP 分开：DNS 必须把「该端点自己的域名」映射到「该端点自己的 IP」。
     * 若共用一个客户端并按域名查表，同一个域名的多个备用 IP（如 dns.alidns.com 的 223.5.5.5 / 223.6.6.6）
     * 会永远只命中列表里的第一个，备用端点形同虚设。
     *
     * 为什么要按超时档位分开：单次超时会随剩余预算收缩（见 [resolve]），
     * 超时是 OkHttpClient 的构造期参数，无法逐次调整，因此不同档位各留一个实例；
     * 常见路径只有 1.2s 一档，最坏情况也只是多出几个轻量实例。
     */
    private val clientByEndpointIp = ConcurrentHashMap<String, OkHttpClient>()

    /**
     * 按「端点 IP + 单次超时」构造客户端：连接目标固定为端点 IP，但 URL 仍写域名，
     * 这样 TLS 的 SNI/Host 正确、证书按域名校验，同时绕开已经坏掉的系统 DNS。
     */
    private fun clientFor(endpoint: Endpoint, attemptTimeoutMs: Long): OkHttpClient =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (hostname.equals(endpoint.host, ignoreCase = true)) {
                        return listOf(InetAddress.getByName(endpoint.fixedIp))
                    }
                    throw java.net.UnknownHostException(hostname)
                }
            })
            .connectTimeout(attemptTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(attemptTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(attemptTimeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()

    /** 复用已建好的客户端，避免每次兜底都重建连接池与线程池。 */
    private fun clientForReuse(endpoint: Endpoint, attemptTimeoutMs: Long): OkHttpClient =
        clientByEndpointIp.getOrPut("${endpoint.fixedIp}@$attemptTimeoutMs") {
            clientFor(endpoint, attemptTimeoutMs)
        }

    fun isMediaHost(hostname: String): Boolean {
        val host = hostname.lowercase()
        return mediaHostSuffixes.any { host.endsWith(it) }
    }

    /** 返回 null 表示兜底失败，调用方应继续抛原始异常。 */
    fun resolve(hostname: String): List<InetAddress>? {
        val host = hostname.lowercase()
        if (!isMediaHost(host)) return null

        cache[host]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) {
                return entry.ips.toInetAddresses().takeIf { it.isNotEmpty() }
            }
            cache.remove(host)
        }
        negativeUntil[host]?.let {
            if (it > System.currentTimeMillis()) return null
            negativeUntil.remove(host)
        }

        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        for (endpoint in endpoints) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining < MIN_REMAINING_BUDGET_MS) break
            //  单次超时必须跟着剩余预算收缩：否则前两个端点各耗 1.2s 就会把预算用光，
            //  后面的端点（含 doh.pub / Cloudflare 备用 IP）永远没机会被尝试。
            val attemptTimeoutMs = minOf(ATTEMPT_TIMEOUT_MS, remaining)
            val answer = queryEndpoint(endpoint, host, attemptTimeoutMs) ?: continue
            if (answer.ips.isEmpty()) continue
            cache[host] = CacheEntry(answer.ips, System.currentTimeMillis() + answer.ttlMs)
            CoreDataLog.w(
                "ApiClient",
                "DoH fallback resolved $host via ${endpoint.host} -> ${answer.ips}"
            )
            return answer.ips.toInetAddresses().takeIf { it.isNotEmpty() }
        }

        negativeUntil[host] = System.currentTimeMillis() + NEGATIVE_CACHE_MS
        CoreDataLog.w("ApiClient", "DoH fallback failed for $host")
        return null
    }

    internal data class DohAnswer(val ips: List<String>, val ttlMs: Long)

    private fun queryEndpoint(endpoint: Endpoint, host: String, attemptTimeoutMs: Long): DohAnswer? {
        val url = ("https://" + endpoint.host + endpoint.path).toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("name", host)
            ?.addQueryParameter("type", "A")
            ?.build()
            ?: return null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()
        return try {
            clientForReuse(endpoint, attemptTimeoutMs).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                parseDohJson(response.body.string())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    internal fun parseDohJson(body: String): DohAnswer? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val status = root["Status"]?.jsonPrimitive?.intOrNull ?: return null
        if (status != 0) return null
        val answers = root["Answer"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return null

        val ips = mutableListOf<String>()
        var minTtlSeconds = Long.MAX_VALUE
        for (element in answers) {
            val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
            val type = obj["type"]?.jsonPrimitive?.intOrNull ?: continue
            if (type != 1) continue
            val ip = obj["data"]?.jsonPrimitive?.contentOrNull ?: continue
            if (!ipv4Regex.matches(ip)) continue
            ips += ip
            val ttl = obj["TTL"]?.jsonPrimitive?.intOrNull?.toLong() ?: 60L
            if (ttl in 1 until minTtlSeconds) minTtlSeconds = ttl
        }
        if (ips.isEmpty()) return null
        val ttlMs = if (minTtlSeconds == Long.MAX_VALUE) {
            MIN_CACHE_MS
        } else {
            (minTtlSeconds * 1000L).coerceIn(MIN_CACHE_MS, MAX_CACHE_MS)
        }
        return DohAnswer(ips.distinct(), ttlMs)
    }

    private fun List<String>.toInetAddresses(): List<InetAddress> =
        mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
}
