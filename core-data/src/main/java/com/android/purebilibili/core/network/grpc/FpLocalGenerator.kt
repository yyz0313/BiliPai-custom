package com.android.purebilibili.core.network.grpc

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

/**
 * v26 实验：设备指纹 fp_local 本地生成器（复刻官方 biliid 算法）。
 *
 * 算法来源（双证据）：
 * - 官方 9.2.0 classes8.dex `com.bilibili.lib.biliid.internal.fingerprint.Fingerprint.d()`；
 * - 旧版 3.19.2 同构实现（v01.a.a）；
 * - 公开逆向文档 apicoll device_identity.md「设备指纹 fp」。
 *
 * fp_local 共 64 字符，构成：
 * 1. md5Hex(buvid + model + radioVersion)  → 32 位小写 hex；
 * 2. yyyyMMddHHmmss（Locale.US）           → 14 位时间戳；
 * 3. 随机 8 字节的 hex                     → 16 位；
 * 4. 校验和：前 62 字符按 2 字符一组（索引 0,2,...,60 共 31 组）逐组按 16 进制
 *    求和，%256 后 %02x 格式化              → 2 位小写 hex。
 *
 * 纯 JVM 实现（不依赖 Android API），可在单元测试中直接验证；
 * 仅在 v26 实验开关（unlock_device_fp_fields）开启时被 BiliGrpcClient 调用。
 */
object FpLocalGenerator {
    private const val HEX_CHARS = "0123456789abcdef"

    /** 生成完整 fp_local = md5(32) + 时间戳(14) + 随机数(16) + 校验和(2)。 */
    fun generate(
        buvid: String,
        model: String,
        radioVersion: String,
        timestamp: String,
        randomHex16: String
    ): String {
        val raw = md5Hex(buvid + model + radioVersion) + timestamp + randomHex16
        return raw + checksumOf(raw)
    }

    /** 以当前时间与随机数生成 fp_local（BiliGrpcClient/DeviceProfileStore 的入口）。 */
    fun generateNow(buvid: String, model: String, radioVersion: String): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        return generate(buvid, model, radioVersion, timestamp, randomHex16())
    }

    /**
     * 校验和：对 fp 前 62 字符按 2 字符一组（索引 0,2,...,60，共 31 组）逐组
     * `parseInt(group, 16)` 求和（解析失败按 0），`sum % 256` 格式化为 2 位小写 hex。
     */
    internal fun checksumOf(fpRaw62: String): String {
        var sum = 0
        var index = 0
        val limit = minOf(fpRaw62.length, 62)
        while (index < limit) {
            val group = fpRaw62.substring(index, minOf(index + 2, fpRaw62.length))
            sum += group.toIntOrNull(16) ?: 0
            index += 2
        }
        return String.format(Locale.US, "%02x", sum % 256)
    }

    /** MD5 的 32 位小写 hex；异常时返回 32 个 '0'（与官方兜底一致）。 */
    internal fun md5Hex(input: String): String = try {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        buildString(digest.size * 2) {
            for (byte in digest) {
                append(HEX_CHARS[(byte.toInt() ushr 4) and 0x0f])
                append(HEX_CHARS[byte.toInt() and 0x0f])
            }
        }
    } catch (e: Exception) {
        "0".repeat(32)
    }

    private fun randomHex16(): String {
        val bytes = ByteArray(8)
        ThreadLocalRandom.current().nextBytes(bytes)
        return buildString(16) {
            for (byte in bytes) {
                append(HEX_CHARS[(byte.toInt() ushr 4) and 0x0f])
                append(HEX_CHARS[byte.toInt() and 0x0f])
            }
        }
    }
}
