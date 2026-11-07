package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.SegmentBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.net.URI

private const val TAG = "CdnDashHeadProbe"
private const val PROBE_WINDOW_BYTES = 64L * 1024L
private const val PROBE_REQUEST_TIMEOUT_MS = 4_000L
private const val PROBE_TOTAL_TIMEOUT_MS = 8_000L
private const val PROBE_MAX_WINDOWS = 3
private const val PROBE_MAX_WINDOW_START = 8L * 1024L * 1024L

/**
 * 试看通道 DASH 头部探测总开关。
 *
 * 关闭后 [probeTrialDashSegmentBase] 直接返回 null，所有试看轨道保持
 * `segmentBase = null` 的旧行为（legacy 渐进播放路径），可一键回退。
 */
internal fun isTrialDashHeadProbeEnabled(): Boolean = true

/**
 * 一次探测窗口的解析结果。
 *
 * - [moovEndExclusive]：`moov` box 的结束偏移（不含），用于合成 `initialization`。
 * - [sidxRange]：`sidx` box 的绝对字节范围，用于合成 `indexRange`。
 * - [nextBoxStart]：窗口内没能闭合扫描时，下一个 box 的绝对起点（补窗口的入口）。
 * - [sawMediaBox]：在找到 `sidx` 之前先遇到 `moof`/`mdat`，说明该文件没有前置索引。
 */
internal data class CdnDashHeadScan(
    val moovEndExclusive: Long? = null,
    val sidxRange: CdnByteRange? = null,
    val nextBoxStart: Long? = null,
    val sawMediaBox: Boolean = false
)

/**
 * 用 HTTP Range 探测一条 m4s 轨道的 ISO-BMFF 头部，合成 [SegmentBase]。
 *
 * 只使用首条候选 URL（与播放器最终使用的轨道一致）；任何失败都返回 null，
 * 由调用方静默回退到 `segmentBase = null`。单次 Range 请求 ≤4s，单条轨道总预算 ≤8s。
 */
internal suspend fun probeTrialDashSegmentBase(
    client: OkHttpClient,
    candidates: List<String>
): SegmentBase? {
    if (!isTrialDashHeadProbeEnabled()) return null
    val urls = candidates.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
    if (urls.isEmpty()) return null
    // 镜像之间的字节布局不保证一致：initialization/indexRange 来自首条 URL 时，
    // 后续分片也必须留在同一条 URL，不能在备份镜像上重新探测或切换。
    return withTimeoutOrNull(PROBE_TOTAL_TIMEOUT_MS) { probeSingleCandidate(client, urls.first()) }
}

private suspend fun probeSingleCandidate(client: OkHttpClient, url: String): SegmentBase? {
    val target = describeCdnUrlForLog(url)
    var windowStart = 0L
    var moovEndExclusive: Long? = null
    var sidxRange: CdnByteRange? = null
    repeat(PROBE_MAX_WINDOWS) { attempt ->
        val bytes = readProbeWindow(client, url, windowStart, target) ?: return null
        val scan = scanCdnDashHeadWindow(bytes, windowStart)
        if (moovEndExclusive == null) moovEndExclusive = scan.moovEndExclusive
        if (sidxRange == null) sidxRange = scan.sidxRange
        val moovEnd = moovEndExclusive
        val index = sidxRange
        if (moovEnd != null && index != null) {
            val initialization = "0-${moovEnd - 1}"
            val indexRange = "${index.start}-${index.endInclusive}"
            Logger.d(
                TAG,
                "probe ok host=$target initialization=$initialization indexRange=$indexRange windows=${attempt + 1}"
            )
            return SegmentBase(initialization = initialization, indexRange = indexRange)
        }
        if (scan.sawMediaBox) {
            Logger.w(TAG, "probe miss host=$target reason=no-sidx-before-media-box")
            return null
        }
        val next = scan.nextBoxStart ?: return null
        if (next <= windowStart || next > PROBE_MAX_WINDOW_START) {
            Logger.w(TAG, "probe miss host=$target reason=unexpected-box-layout next=$next")
            return null
        }
        windowStart = next
    }
    Logger.w(TAG, "probe miss host=$target reason=index-not-found-in-windows")
    return null
}

private suspend fun readProbeWindow(
    client: OkHttpClient,
    url: String,
    windowStart: Long,
    target: String
): ByteArray? {
    return try {
        withTimeout(PROBE_REQUEST_TIMEOUT_MS) {
            readExactCdnRange(
                client = client,
                url = url,
                range = CdnByteRange(windowStart, windowStart + PROBE_WINDOW_BYTES - 1L)
            ).bytes
        }
    } catch (error: CancellationException) {
        // withTimeout 自身的取消按探测失败处理；调用方真实取消时 ensureActive 会重新抛出。
        currentCoroutineContext().ensureActive()
        Logger.w(TAG, "probe window timeout host=$target start=$windowStart")
        null
    } catch (error: Exception) {
        Logger.w(
            TAG,
            "probe window failed host=$target start=$windowStart reason=${error.javaClass.simpleName}: ${error.message}"
        )
        null
    }
}

/**
 * 顺序扫描一个窗口内的 ISO-BMFF box 边界。只读取 box 头（size + type）：
 * `moov` 的结束位置与 `sidx` 的范围都可以在头部闭合后立即算出，不需要整段内容。
 */
internal fun scanCdnDashHeadWindow(bytes: ByteArray, windowStart: Long): CdnDashHeadScan {
    var absolute = windowStart
    val windowEndExclusive = windowStart + bytes.size
    var moovEndExclusive: Long? = null
    var sidxRange: CdnByteRange? = null
    var sawMediaBox = false
    var nextBoxStart: Long? = null
    var invalid = false

    while (absolute < windowEndExclusive) {
        val relative = (absolute - windowStart).toInt()
        if (relative + 8 > bytes.size) {
            nextBoxStart = absolute
            break
        }
        val size32 = readDashUInt32(bytes, relative)
        val type = String(bytes, relative + 4, 4, Charsets.US_ASCII)
        if (!isPrintableBoxType(type)) {
            invalid = true
            break
        }
        var headerSize = 8L
        var boxSize = size32
        if (size32 == 1L) {
            if (relative + 16 > bytes.size) {
                nextBoxStart = absolute
                break
            }
            boxSize = readDashUInt64(bytes, relative + 8)
            headerSize = 16L
        }
        if (boxSize == 0L || boxSize < headerSize) {
            // size=0 表示延伸到文件末尾：拿不到有限范围，不猜。
            invalid = true
            break
        }
        val boxEndExclusive = absolute + boxSize
        when (type) {
            "moov" -> if (moovEndExclusive == null) moovEndExclusive = boxEndExclusive
            "sidx" -> if (sidxRange == null) sidxRange = CdnByteRange(absolute, boxEndExclusive - 1L)
            "moof", "mdat" -> sawMediaBox = true
        }
        if (sawMediaBox) break
        if (moovEndExclusive != null && sidxRange != null) break
        if (boxEndExclusive <= absolute) {
            invalid = true
            break
        }
        absolute = boxEndExclusive
    }

    if (!invalid && nextBoxStart == null && !sawMediaBox &&
        !(moovEndExclusive != null && sidxRange != null) && absolute >= windowEndExclusive
    ) {
        // 扫描停在窗口边界：下一个 box 从 absolute 开始，补窗口从这里读。
        if (absolute > windowStart) nextBoxStart = absolute
    }
    return CdnDashHeadScan(
        moovEndExclusive = moovEndExclusive,
        sidxRange = sidxRange,
        nextBoxStart = nextBoxStart,
        sawMediaBox = sawMediaBox
    )
}

private fun isPrintableBoxType(type: String): Boolean =
    type.length == 4 && type.all { it.code in 0x20..0x7E }

private fun readDashUInt32(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset + 4 > bytes.size) return 0L
    return (0 until 4).fold(0L) { value, index ->
        (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
    }
}

private fun readDashUInt64(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset + 8 > bytes.size) return 0L
    return (0 until 8).fold(0L) { value, index ->
        (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
    }
}

/** 日志只打印 host + path，绝不打印带签名的完整 URL。 */
private fun describeCdnUrlForLog(url: String): String {
    return runCatching {
        val uri = URI(url)
        "${uri.host.orEmpty()}${uri.path.orEmpty()}"
    }.getOrDefault("invalid-url")
}
