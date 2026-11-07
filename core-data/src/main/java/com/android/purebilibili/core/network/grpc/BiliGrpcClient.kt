package com.android.purebilibili.core.network.grpc

import android.os.Build
import com.android.purebilibili.core.network.CoreDataLog
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.DeviceProfileStore
import com.android.purebilibili.core.store.TokenManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.UUID

// gRPC 直连客户端为共享网络能力，手机与 TV 的评论、私信分享均通过它调用。
object BiliGrpcClient {
    private const val APP_BASE_URL = "https://app.bilibili.com"
    private const val TRACE_ID = "11111111111111111111111111111111:1111111111111111:0:0"
    private val grpcMediaType = "application/grpc".toMediaType()

    //  客户端身份：BiliRoamingX 的「无限试用会员画质」在请求 PlayViewUnite 时统一换成
    //  粉版（android）身份；本客户端默认保持 android_hd（评论/弹幕等接口不变），
    //  仅试用取流显式传 PINK，以对齐漫游的试用放行行为。
    enum class ClientIdentity(
        val mobiApp: String,
        val build: Int,
        val appId: Int,
        val versionName: String
    ) {
        HD("android_hd", 2001100, 5, "2.0.1"),
        PINK("android", 7760700, 1, "7.76.0"),

        //  旧版 Play 客户端身份（BiliPai 3.19.2 实测：mobi_app=android_i / build=7750600）：
        //  v23 实验开关 unlock_play_identity 开启时用于试看取流；默认关闭时不参与任何请求。
        PLAY("android_i", 7750600, 2, "3.19.2"),

        //  官方平板版 9.2.0 身份（aapt dump badging 实测：versionCode=9020300 / versionName=9.2.0）：
        //  v24 实验开关 unlock_official_identity 开启时用于试看取流；默认关闭时不参与任何请求。
        OFFICIAL("android", 9020300, 1, "9.2.0")
    }

    internal fun userAgent(identity: ClientIdentity): String =
        "Mozilla/5.0 BiliDroid/${identity.versionName} (bbcallen@gmail.com) os/android model/${identity.mobiApp} " +
            "mobi_app/${identity.mobiApp} build/${identity.build} channel/master innerVer/${identity.build} osVer/15 network/2"

    //  alignDeviceProfile：v23 实验开关 unlock_device_profile_alignment 开启时，
    //  x-bili-device-bin 改用真实设备画像（仅试看取流调用点会传 true，其它 gRPC 调用不受影响）。
    fun request(
        path: String,
        message: ByteArray,
        identity: ClientIdentity = ClientIdentity.HD,
        alignDeviceProfile: Boolean = false,
        spoofPlusDevice: Boolean = false,
        fpFields: Boolean = false
    ): ByteArray {
        val url = "$APP_BASE_URL$path"
        //  v25 实验：设备伪装开启时复用"画像对齐"路径（稳定 buvid / 随机 trace-id / 真实 fts）。
        val effectiveAlign = alignDeviceProfile || spoofPlusDevice
        val buvid = resolveBuvid(effectiveAlign)
        val alignedProfile = if (effectiveAlign) DeviceProfileValues.fromSystem() else null
        if (spoofPlusDevice) {
            CoreDataLog.w(
                "BiliGrpc",
                "DeviceSpoofPlus: brand=OnePlus model=OPD2515 device=OPD2515"
            )
        }
        if (fpFields) {
            CoreDataLog.w(
                "BiliGrpc",
                "DeviceFp: fp_local/fp_remote/fp enabled (model=" +
                    (if (spoofPlusDevice) "OPD2515" else "real") + ")"
            )
        }
        alignedProfile?.let { profile ->
            CoreDataLog.w(
                "BiliGrpc",
                "DeviceProfile: aligned brand=${profile.brand} model=${profile.model} " +
                    "osver=${profile.osVer} fts=${profile.firstLaunchFts}"
            )
        }
        val request = Request.Builder()
            .url(url)
            .post(ProtoWire.frame(message).toRequestBody(grpcMediaType))
            .header("content-type", "application/grpc")
            .header("grpc-encoding", "gzip")
            .header("gzip-accept-encoding", "gzip,identity")
            .header("user-agent", userAgent(identity))
            .header("bili-http-engine", "cronet")
            .header("x-bili-trace-id", resolveTraceId(effectiveAlign))
            .header("buvid", buvid)
            .header("cookie", buildCookieHeader())
            .header("x-bili-metadata-bin", buildMetadataHeader(buvid, identity))
            .header(
                "x-bili-device-bin",
                buildDeviceHeader(
                    buvid,
                    identity,
                    effectiveAlign,
                    alignedProfile,
                    spoofPlusDevice,
                    fpFields
                )
            )
            .header("x-bili-fawkes-req-bin", buildFawkesHeader(identity))
            .header("x-bili-network-bin", buildNetworkHeader())
            .header("x-bili-locale-bin", buildLocaleHeader())
            .apply {
                TokenManager.accessTokenCache
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header("authorization", "identify_v1 $it") }
            }
            .build()

        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body.bytes()
            val trailers = runCatching { response.trailers() }.getOrNull()
            val grpcStatus = response.header("grpc-status")
                ?: trailers?.get("grpc-status")
            val grpcMessage = response.header("grpc-message")
                ?: trailers?.get("grpc-message")

            // HTTP 200 with standard gRPC status (0 or absent on HTTP success) is considered valid
            val isStatusOk = grpcStatus == null || grpcStatus == "0"
            if (!response.isSuccessful || !isStatusOk) {
                CoreDataLog.w(
                    "BiliGrpc",
                    "gRPC request failed: path=$path http=${response.code} grpc=$grpcStatus message=${grpcMessage.orEmpty()}"
                )
                error("请求失败：HTTP ${response.code}，gRPC $grpcStatus，${grpcMessage.orEmpty()}")
            }
            return ProtoWire.unframe(body)
        }
    }

    internal fun buildMetadataHeader(
        buvid: String = resolveBuvid(),
        identity: ClientIdentity = ClientIdentity.HD
    ): String {
        val accessKey = TokenManager.accessTokenCache?.takeIf { it.isNotBlank() }
        val fields = mutableListOf<ByteArray>()
        if (!accessKey.isNullOrBlank()) {
            fields += ProtoWire.string(1, accessKey)
        }
        fields += ProtoWire.string(2, identity.mobiApp)
        fields += ProtoWire.string(3, "android")
        fields += ProtoWire.int32(4, identity.build)
        fields += ProtoWire.string(5, "master")
        fields += ProtoWire.string(6, buvid)
        fields += ProtoWire.string(7, "android")
        return encodeBase64(ProtoWire.message(*fields.toTypedArray()))
    }

    //  设备画像（Device）头部。默认路径保持历史形态：device/brand/model=android、osver=15；
    //  alignDeviceProfile=true 时对齐官方客户端：brand/model/osver 用真实系统值、
    //  device(6) 留空（proto3 空串等价省略）、fts(15)=首次启动时间戳。
    //  spoofPlusDevice=true（v25 实验）时对齐平板版 padunlock：brand=OnePlus、model/device=OPD2515，
    //  osver 仍用真实系统值。
    //  fpFields=true（v26 实验）时补官方字段 fp_local(11)/fp_remote(12)/fp(14)：本地按官方
    //  biliid 算法生成（buvid+model+radio 的 MD5 + 时间戳 + 随机数 + 校验和，共 64 字符）；
    //  开关关闭时不产生任何字节，与 v22/v25 请求完全一致。
    internal fun buildDeviceHeader(
        buvid: String = resolveBuvid(),
        identity: ClientIdentity = ClientIdentity.HD,
        alignDeviceProfile: Boolean = false,
        alignedProfile: DeviceProfileValues? = null,
        spoofPlusDevice: Boolean = false,
        fpFields: Boolean = false
    ): String {
        val profile = if (alignDeviceProfile || spoofPlusDevice) {
            alignedProfile ?: DeviceProfileValues.fromSystem()
        } else {
            null
        }
        val model = if (spoofPlusDevice) "OPD2515" else (profile?.model ?: "android")
        val fpLocal = if (fpFields) resolveFpLocal(buvid, model) else ""
        val fields = mutableListOf(
            ProtoWire.int32(1, identity.appId),
            ProtoWire.int32(2, identity.build),
            ProtoWire.string(3, buvid),
            ProtoWire.string(4, identity.mobiApp),
            ProtoWire.string(5, "android"),
            ProtoWire.string(
                6,
                when {
                    spoofPlusDevice -> "OPD2515"
                    profile == null -> "android"
                    else -> ""
                }
            ),
            ProtoWire.string(7, "master"),
            ProtoWire.string(8, if (spoofPlusDevice) "OnePlus" else (profile?.brand ?: "android")),
            ProtoWire.string(9, model),
            ProtoWire.string(10, profile?.osVer ?: "15")
        )
        if (fpLocal.isNotEmpty()) {
            fields += ProtoWire.string(11, fpLocal)
            fields += ProtoWire.string(12, fpLocal)
        }
        fields += ProtoWire.string(13, identity.versionName)
        if (fpLocal.isNotEmpty()) {
            fields += ProtoWire.string(14, fpLocal)
        }
        if (profile != null && profile.firstLaunchFts > 0L) {
            fields += ProtoWire.int64(15, profile.firstLaunchFts)
        }
        return encodeBase64(ProtoWire.message(*fields.toTypedArray()))
    }

    //  v26 实验：fp_local 本地生成（官方 biliid Fingerprint.d() 算法复刻；仅 fpFields 开启时调用）。
    //  model 取当前请求身份（设备伪装开=OPD2515，否则真实值）；有持久化条件时复用同一值，
    //  与官方"brand/model 变化即重算"的行为一致。
    internal fun resolveFpLocal(buvid: String, model: String): String {
        val radioVersion = runCatching { Build.getRadioVersion() }.getOrNull().orEmpty()
        val context = NetworkModule.appContext
        return if (context != null) {
            DeviceProfileStore.getOrCreateFpLocal(context, buvid, model, radioVersion)
        } else {
            FpLocalGenerator.generateNow(buvid, model, radioVersion)
        }
    }

    internal fun buildFawkesHeader(identity: ClientIdentity = ClientIdentity.HD): String {
        val sessionId = UUID.randomUUID().toString().replace("-", "").take(8)
        val fields = arrayOf(
            ProtoWire.string(1, identity.mobiApp),
            ProtoWire.string(2, "prod"),
            ProtoWire.string(3, sessionId)
        )
        return encodeBase64(ProtoWire.message(*fields))
    }

    internal fun buildNetworkHeader(): String {
        return encodeBase64(ProtoWire.message(ProtoWire.int32(1, 1)))
    }

    internal fun buildLocaleHeader(): String {
        val localeIds = ProtoWire.message(
            ProtoWire.string(1, "zh"),
            ProtoWire.string(2, "Hans"),
            ProtoWire.string(3, "CN")
        )
        val locale = ProtoWire.message(
            ProtoWire.bytes(1, localeIds),
            ProtoWire.bytes(2, localeIds),
            ProtoWire.string(3, "Asia/Shanghai")
        )
        return encodeBase64(locale)
    }

    internal fun buildCookieHeader(): String {
        val cookies = mutableListOf("buvid3=${resolveBuvid()}")
        TokenManager.sessDataCache?.takeIf { it.isNotBlank() }?.let {
            cookies += "SESSDATA=$it"
        }
        TokenManager.csrfCache?.takeIf { it.isNotBlank() }?.let {
            cookies += "bili_jct=$it"
        }
        TokenManager.midCache?.takeIf { it > 0L }?.let {
            cookies += "DedeUserID=$it"
        }
        return cookies.joinToString("; ")
    }

    internal fun resolveBuvid(): String {
        return resolveBuvid(alignDeviceProfile = false)
    }

    //  v23 实验（开关1）：trace-id 每请求随机（32hex:16hex:0:0，与官方一致）；
    //  开关关闭时返回历史常量，确保请求字节与现状完全一致。
    internal fun resolveTraceId(alignDeviceProfile: Boolean = false): String {
        if (!alignDeviceProfile) return TRACE_ID
        val first = UUID.randomUUID().toString().replace("-", "")
        val second = UUID.randomUUID().toString().replace("-", "").take(16)
        return "$first:$second:0:0"
    }

    //  v23 实验（开关1）：稳定 buvid —— 生成一次并落盘（device_profile_sp），
    //  避免 TokenManager 异步恢复窗口 / SPI 覆盖导致试看请求使用未持久化的随机值。
    //  开关关闭时保持现状：读内存缓存，缺失则生成随机值（不落盘）。
    internal fun resolveBuvid(alignDeviceProfile: Boolean): String {
        val cached = TokenManager.buvid3Cache
        if (!alignDeviceProfile) {
            if (!cached.isNullOrBlank()) return cached
            val generated = UUID.randomUUID().toString().replace("-", "") + "infoc"
            TokenManager.buvid3Cache = generated
            return generated
        }
        val context = NetworkModule.appContext
        val stable = if (context != null) {
            DeviceProfileStore.getOrCreateAlignedBuvid(context, cached)
        } else {
            cached?.takeIf { it.isNotBlank() }
                ?: (UUID.randomUUID().toString().replace("-", "") + "infoc")
        }
        TokenManager.buvid3Cache = stable
        return stable
    }

    internal fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }
}

//  v23 实验：真实设备画像（仅 unlock_device_profile_alignment 开启时构造）。
internal data class DeviceProfileValues(
    val brand: String,
    val model: String,
    val osVer: String,
    val firstLaunchFts: Long
) {
    companion object {
        fun fromSystem(): DeviceProfileValues = DeviceProfileValues(
            brand = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            osVer = Build.VERSION.RELEASE.orEmpty(),
            firstLaunchFts = NetworkModule.appContext
                ?.let(DeviceProfileStore::getFirstLaunchFts)
                ?: 0L
        )
    }
}

