package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.CoreDataLog
import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import com.android.purebilibili.core.network.grpc.ProtoWire
import com.android.purebilibili.core.util.IdUtils
import com.android.purebilibili.core.util.normalizePlayUrlMediaScheme
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Dolby
import com.android.purebilibili.data.model.response.Flac
import com.android.purebilibili.data.model.response.FormatItem
import com.android.purebilibili.data.model.response.PlayUrlData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//  播放地址聚合 gRPC（Player/PlayViewUnite）为手机与 TV 共用的取流能力。
//  关键：VideoVod.is_need_trial=true 让服务端下发"试看"高画质轨道；
//  响应里 need_vip 轨道被本地放行，从而在不改账号 VIP 的前提下解锁会员画质。
object PlayViewUniteRepository {
    private const val TAG = "PlayViewUnite"
    const val PATH = "/bilibili.app.playerunite.v1.Player/PlayViewUnite"
    const val FNVAL_ALL = 4048

    //  v23 实验字段：官方 VideoVod.prefer_codec_type(9) 的 CodeType 枚举值
    //  （NOCODE=0 / CODE264=1 / CODE265=2 / CODEAV1=3）；默认与仓库编码偏好（hev1）一致取 HEVC。
    internal const val PREFER_CODEC_TYPE_HEVC = 2

    fun buildPlayViewUniteRequest(
        aid: Long,
        cid: Long,
        bvid: String,
        qn: Int,
        isNeedTrial: Boolean = true,
        experimentalFields: Boolean = false,
        officialWireShape: Boolean = false
    ): ByteArray {
        val vodFields = mutableListOf(
            ProtoWire.int64(2, cid),
            ProtoWire.int32(3, qn)
        )
        if (!officialWireShape) {
            //  v24 实验：官方 getFnVer() 恒 0（proto3 隐式零值不序列化）；官方字段形态下省略显式零字节。
            vodFields += ProtoWire.int32(4, 0)
        }
        vodFields += ProtoWire.int32(5, FNVAL_ALL)
        if (experimentalFields) {
            //  对齐官方 playview_d.java 的 VideoVod 形态：download(6)=0、force_host(7)=0。
            vodFields += ProtoWire.int32(6, 0)
            vodFields += ProtoWire.int32(7, 0)
        }
        vodFields += ProtoWire.bool(8, true)
        if (experimentalFields) {
            vodFields += ProtoWire.int32(9, PREFER_CODEC_TYPE_HEVC)
        }
        if (aid > 0L) {
            vodFields.add(0, ProtoWire.int64(1, aid))
        }
        if (isNeedTrial) {
            vodFields += ProtoWire.bool(11, true)
        }
        if (officialWireShape) {
            //  v24 实验：官方 isQualityAutoSwitch 默认 true → QN_POLICY_AUTO_QN_ENABLE=1。
            vodFields += ProtoWire.int32(12, 1)
        }
        return ProtoWire.message(
            ProtoWire.bytes(1, ProtoWire.message(*vodFields.toTypedArray())),
            ProtoWire.string(2, "main.ugc-video-detail.0.0"),
            ProtoWire.string(3, "video"),
            ProtoWire.string(5, bvid)
        )
    }

    //  番剧/影视（PGC）请求：官方客户端与哔哩漫游在 Unite 通道里用 extra_content 携带
    //  ep_id / season_id，spmid 走 PGC 值；vod 的 is_need_trial 与 UGC 完全一致。
    //  取流语义不变，只是把"试看"开关按下去，让服务端下发会员画质轨道。
    const val PGC_SPMID = "united.player-video-detail.0.0"
    const val PGC_FROM_SPMID = "0.0.0.0"

    fun buildPgcPlayViewUniteRequest(
        epId: Long,
        cid: Long,
        aid: Long,
        seasonId: Long,
        qn: Int,
        isNeedTrial: Boolean = true,
        experimentalFields: Boolean = false,
        officialWireShape: Boolean = false
    ): ByteArray {
        val vodFields = mutableListOf(
            ProtoWire.int64(2, cid),
            ProtoWire.int32(3, qn)
        )
        if (!officialWireShape) {
            vodFields += ProtoWire.int32(4, 0)
        }
        vodFields += ProtoWire.int32(5, FNVAL_ALL)
        if (experimentalFields) {
            vodFields += ProtoWire.int32(6, 0)
            vodFields += ProtoWire.int32(7, 0)
        }
        vodFields += ProtoWire.bool(8, true)
        if (experimentalFields) {
            vodFields += ProtoWire.int32(9, PREFER_CODEC_TYPE_HEVC)
        }
        if (aid > 0L) {
            vodFields.add(0, ProtoWire.int64(1, aid))
        }
        if (isNeedTrial) {
            vodFields += ProtoWire.bool(11, true)
        }
        if (officialWireShape) {
            vodFields += ProtoWire.int32(12, 1)
        }
        val fields = mutableListOf(
            ProtoWire.bytes(1, ProtoWire.message(*vodFields.toTypedArray())),
            ProtoWire.string(2, PGC_SPMID),
            ProtoWire.string(3, PGC_FROM_SPMID)
        )
        fields += extraContentEntry("is_need_view_info", "true")
        fields += extraContentEntry("security_level", "LEVEL_L1")
        if (seasonId > 0L) {
            fields += extraContentEntry("season_id", seasonId.toString())
        }
        if (epId > 0L) {
            fields += extraContentEntry("ep_id", epId.toString())
        }
        return ProtoWire.message(*fields.toTypedArray())
    }

    //  map<string,string> 的 wire 形态：每个 entry 是一条 repeated message（key=1, value=2）。
    private fun extraContentEntry(key: String, value: String): ByteArray =
        ProtoWire.bytes(
            4,
            ProtoWire.message(ProtoWire.string(1, key), ProtoWire.string(2, value))
        )

    //  playIdentity / alignDeviceProfile / experimentalFields 对应 v23 三个默认关闭的实验开关，
    //  officialWireShape 对应 v24 实验开关，spoofPlusDevice 对应 v25 实验开关；
    //  均由调用方从设置读取后显式传入，
    //  默认值保持 v22 成功路径的请求字节不变。
    suspend fun fetchPlayUrlData(
        bvid: String,
        cid: Long,
        qn: Int,
        isNeedTrial: Boolean = true,
        playIdentity: BiliGrpcClient.ClientIdentity = BiliGrpcClient.ClientIdentity.PINK,
        alignDeviceProfile: Boolean = false,
        experimentalFields: Boolean = false,
        officialWireShape: Boolean = false,
        spoofPlusDevice: Boolean = false,
        fpFields: Boolean = false
    ): PlayUrlData? {
        if (bvid.isBlank() || cid <= 0L || qn <= 0) return null
        val trialKey = bvid + ":" + cid + ":" + qn
        if (isTrialCoolingDown(trialKey)) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = buildPlayViewUniteRequest(
                    aid = IdUtils.bv2av(bvid),
                    cid = cid,
                    bvid = bvid,
                    qn = qn,
                    isNeedTrial = isNeedTrial,
                    experimentalFields = experimentalFields,
                    officialWireShape = officialWireShape
                )
                //  身份对齐 BiliRoamingX（fakeToClient=Pink）：漫游实测通道为粉版身份 +
                //  identify_v1 鉴权 + WIFI 网络类型；android_hd 身份与实测路径不一致。
                //  v23 实验：unlock_play_identity 开启时改用旧版 Play 身份（android_i 3.19.2）。
                CoreDataLog.w(TAG, "TrialIdentity: ${playIdentity.name}")
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: experimentalFields=${if (experimentalFields) "ON" else "OFF"}"
                )
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: officialWireShape=${if (officialWireShape) "ON" else "OFF"}"
                )
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: spoofPlusDevice=${if (spoofPlusDevice) "ON" else "OFF"}"
                )
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: fpFields=${if (fpFields) "ON" else "OFF"}"
                )
                val payload = BiliGrpcClient.request(
                    PATH,
                    request,
                    playIdentity,
                    alignDeviceProfile,
                    spoofPlusDevice,
                    fpFields
                )
                val parsed = parsePlayViewUniteReply(payload)
                logMediaSchemeSummary("ugc-trial", qn, parsed)
                val granted = parsed?.dash?.video.orEmpty()
                    .filter { it.getValidUrl().isNotEmpty() }
                    .map { it.id }
                    .distinct()
                    .sortedDescending()
                if (parsed == null || granted.none { it > HIGH_QUALITY_FLOOR }) {
                    //  该视频没有可试用的高画质（或服务端拒绝），短时间内不重复请求同一档位。
                    markTrialUnavailable(trialKey)
                    CoreDataLog.w(
                        TAG,
                        " trial denied: qn=$qn granted=$granted trialInfo=${hasQnTrialInfo(payload)}"
                    )
                    CoreDataLog.w(
                        TAG,
                        " ugc trial raw-summary: ${describeTrialRawSummary(payload)}"
                    )
                    null
                } else {
                    clearTrialCooldown(trialKey)
                    CoreDataLog.w(TAG, " trial granted: qn=$qn granted=$granted")
                    parsed
                }
            } catch (e: Exception) {
                markTrialUnavailable(trialKey)
                CoreDataLog.w(TAG, " request failed: qn=$qn msg=${e.message}")
                null
            }
        }
    }

    //  番剧试看：与 UGC 同一通道、同一解析（vod_info 对 PGC/UGC 同构），
    //  只换请求形态（extra_content 携带 ep_id/season_id + PGC spmid）。
    //  与 UGC 的差别：这里不设"必须高于 1080P"的门槛——番剧可能只有 ≤1080P 的档位，
    //  是否采用由调用方按"是否比 web 结果更好"决定。
    suspend fun fetchPgcPlayUrlData(
        epId: Long,
        cid: Long,
        aid: Long,
        seasonId: Long,
        qn: Int,
        isNeedTrial: Boolean = true,
        playIdentity: BiliGrpcClient.ClientIdentity = BiliGrpcClient.ClientIdentity.PINK,
        alignDeviceProfile: Boolean = false,
        experimentalFields: Boolean = false,
        officialWireShape: Boolean = false,
        spoofPlusDevice: Boolean = false,
        fpFields: Boolean = false
    ): PlayUrlData? {
        if (epId <= 0L || cid <= 0L || qn <= 0) return null
        val trialKey = "pgc:$epId:$cid:$qn"
        if (isTrialCoolingDown(trialKey)) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = buildPgcPlayViewUniteRequest(
                    epId = epId,
                    cid = cid,
                    aid = aid,
                    seasonId = seasonId,
                    qn = qn,
                    isNeedTrial = isNeedTrial,
                    experimentalFields = experimentalFields,
                    officialWireShape = officialWireShape
                )
                //  v23 实验：身份/字段开关与 UGC 试看一致（默认 PINK + 不补字段）。
                CoreDataLog.w(TAG, "TrialIdentity: ${playIdentity.name}")
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: experimentalFields=${if (experimentalFields) "ON" else "OFF"}"
                )
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: officialWireShape=${if (officialWireShape) "ON" else "OFF"}"
                )
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: spoofPlusDevice=${if (spoofPlusDevice) "ON" else "OFF"}"
                )
                CoreDataLog.w(
                    TAG,
                    "PlayViewUnite: fpFields=${if (fpFields) "ON" else "OFF"}"
                )
                val payload = BiliGrpcClient.request(
                    PATH,
                    request,
                    playIdentity,
                    alignDeviceProfile,
                    spoofPlusDevice,
                    fpFields
                )
                val parsed = parsePlayViewUniteReply(payload)
                logMediaSchemeSummary("pgc-trial", qn, parsed)
                val granted = parsed?.dash?.video.orEmpty()
                    .filter { it.getValidUrl().isNotEmpty() }
                    .map { it.id }
                    .distinct()
                    .sortedDescending()
                if (parsed == null || granted.isEmpty()) {
                    markTrialUnavailable(trialKey)
                    CoreDataLog.w(
                        TAG,
                        " pgc trial denied: qn=$qn granted=$granted trialInfo=${hasQnTrialInfo(payload)}"
                    )
                    CoreDataLog.w(
                        TAG,
                        " pgc trial raw-summary: ${describeTrialRawSummary(payload)}"
                    )
                    null
                } else {
                    clearTrialCooldown(trialKey)
                    CoreDataLog.w(TAG, " pgc trial granted: qn=$qn granted=$granted")
                    parsed
                }
            } catch (e: Exception) {
                markTrialUnavailable(trialKey)
                CoreDataLog.w(TAG, " pgc request failed: qn=$qn msg=${e.message}")
                null
            }
        }
    }

    //  诊断：把「这一批轨道最终用的是 http 还是 https」写进日志。实测口径：本机
    //  http 媒体地址 71 次全部拿不到首帧、https 媒体地址 16 次全部出画，因此这一行是
    //  判断「协议归一化是否真的生效」的最短路径（升级+镜像提升都在 parse 阶段完成）。
    private fun logMediaSchemeSummary(stage: String, qn: Int, data: PlayUrlData?) {
        if (data == null) return
        val urls = buildList {
            addAll(data.dash?.video.orEmpty().map { it.baseUrl })
            addAll(data.dash?.audio.orEmpty().map { it.baseUrl })
            addAll(data.dash?.dolby?.audio.orEmpty().map { it.baseUrl })
            data.dash?.flac?.audio?.let { add(it.baseUrl) }
            addAll(data.durl.orEmpty().map { it.url })
        }.filter { it.isNotBlank() }
        if (urls.isEmpty()) return
        val https = urls.count { it.startsWith("https://", ignoreCase = true) }
        val http = urls.count { it.startsWith("http://", ignoreCase = true) }
        CoreDataLog.w(TAG, " $stage media scheme: qn=$qn https=$https http=$http total=${urls.size}")
    }

    private const val HIGH_QUALITY_FLOOR = 80
    private const val TRIAL_COOLDOWN_MS = 3 * 60 * 1000L
    private val trialCooldownUntilMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun isTrialCoolingDown(key: String): Boolean {
        val until = trialCooldownUntilMs[key] ?: return false
        if (until <= System.currentTimeMillis()) {
            trialCooldownUntilMs.remove(key)
            return false
        }
        return true
    }

    private fun markTrialUnavailable(key: String) {
        trialCooldownUntilMs[key] = System.currentTimeMillis() + TRIAL_COOLDOWN_MS
    }

    private fun clearTrialCooldown(key: String) {
        trialCooldownUntilMs.remove(key)
    }

    //  PlayViewUniteReply.qn_trial_info = 7：服务端下发试用信息时才会出现，
    //  是判断"这次到底有没有给试用"最直接的证据。
    fun hasQnTrialInfo(payload: ByteArray): Boolean {
        if (payload.isEmpty()) return false
        val reply = runCatching { ProtoWire.parseFields(payload) }.getOrNull() ?: return false
        return firstBytes(reply, 7) != null
    }

    fun parsePlayViewUniteReply(payload: ByteArray): PlayUrlData? {
        if (payload.isEmpty()) return null
        val reply = runCatching { ProtoWire.parseFields(payload) }.getOrNull() ?: return null
        val vodInfoBytes = firstBytes(reply, 1) ?: return null
        val vodInfo = runCatching { ProtoWire.parseFields(vodInfoBytes) }.getOrNull() ?: return null
        return parseVodInfo(vodInfo)
    }

    private fun parseVodInfo(fields: List<ProtoWire.Field>): PlayUrlData? {
        val videos = mutableListOf<DashVideo>()
        val labels = mutableMapOf<Int, String>()
        val unplayable = mutableListOf<String>()
        val backupPromoted = mutableListOf<String>()
        for (streamBytes in allBytes(fields, 5)) {
            val stream = runCatching { ProtoWire.parseFields(streamBytes) }.getOrNull() ?: continue
            val streamInfoBytes = firstBytes(stream, 1) ?: continue
            val streamInfo = runCatching { ProtoWire.parseFields(streamInfoBytes) }.getOrNull() ?: continue
            val streamQn = varint(streamInfo, 1)?.toInt() ?: continue
            val streamLabelText = streamLabel(streamInfo)
            val dashVideoBytes = firstBytes(stream, 2)
            if (dashVideoBytes == null) {
                unplayable += describeUnplayableStream(streamQn, streamLabelText, streamInfo)
                continue
            }
            val dashVideo = runCatching { ProtoWire.parseFields(dashVideoBytes) }.getOrNull() ?: continue
            //  v25 兜底：服务端可能只下发 backup_url（base_url 为空），此前会被误判为
            //  "无可用地址"而整档丢弃；这里把第一个非空 backup_url 提升为 base_url。
            val rawBaseUrl = stringOf(dashVideo, 1)
            val baseUrl = rawBaseUrl.ifBlank {
                stringList(dashVideo, 2).firstOrNull { it.isNotBlank() }.orEmpty()
            }
            if (baseUrl.isBlank()) {
                unplayable += describeUnplayableStream(
                    streamQn,
                    streamLabelText,
                    streamInfo,
                    dashVideo
                )
                continue
            }
            if (rawBaseUrl.isBlank()) {
                backupPromoted += streamQn.toString()
            }
            val codecid = varint(dashVideo, 4)?.toInt() ?: 7
            videos += DashVideo(
                id = streamQn,
                baseUrl = baseUrl,
                backupUrl = stringList(dashVideo, 2).takeIf { it.isNotEmpty() },
                bandwidth = varint(dashVideo, 3)?.toInt() ?: 0,
                mimeType = "video/mp4",
                codecs = videoCodecs(codecid),
                width = varint(dashVideo, 10)?.toInt() ?: 0,
                height = varint(dashVideo, 11)?.toInt() ?: 0,
                frameRate = stringOf(dashVideo, 9),
                sar = "",
                startWithSap = null,
                segmentBase = null,
                codecid = codecid
            )
            labels[streamQn] = streamLabel(streamInfo)
        }

        if (unplayable.isNotEmpty()) {
            CoreDataLog.w(
                TAG,
                " unite unplayable tracks (server listed, no usable url): " + unplayable.joinToString(" ")
            )
        }
        if (backupPromoted.isNotEmpty()) {
            CoreDataLog.w(
                TAG,
                " unite backup-url promoted tracks: " + backupPromoted.joinToString(" ")
            )
        }
        if (videos.isEmpty()) return null
        val dolby = parseDolby(firstBytes(fields, 7))
        val qualities = videos.map { it.id }.distinct().sortedDescending()
        val timelength = varint(fields, 3) ?: 0L

        return normalizePlayUrlMediaScheme(PlayUrlData(
            quality = varint(fields, 1)?.toInt() ?: qualities.first(),
            format = stringOf(fields, 2),
            timelength = timelength,
            acceptFormat = "",
            acceptDescription = qualities.map { labels[it].orEmpty() },
            acceptQuality = qualities,
            videoCodecid = varint(fields, 4)?.toInt() ?: 0,
            durl = null,
            dash = Dash(
                duration = (timelength / 1000L).toInt(),
                minBufferTime = 1.5f,
                video = videos,
                audio = parseAudioList(fields, 6),
                dolby = dolby,
                flac = parseFlac(firstBytes(fields, 9))
            ),
            supportFormats = qualities.map { qn ->
                FormatItem(
                    quality = qn,
                    format = "",
                    newDescription = labels[qn].orEmpty(),
                    description = labels[qn].orEmpty(),
                    displayDesc = labels[qn].orEmpty(),
                    superscript = "",
                    codecs = null,
                    needLogin = false,
                    needVip = false
                )
            },
            dolbyType = dolby?.type
        ))
    }

    private fun parseAudioList(fields: List<ProtoWire.Field>, number: Int): List<DashAudio> =
        allBytes(fields, number).mapNotNull { bytes ->
            val item = runCatching { ProtoWire.parseFields(bytes) }.getOrNull() ?: return@mapNotNull null
            //  v25 兜底：与视频轨一致，base_url(2) 为空时用第一个非空 backup_url(3) 兜底。
            val baseUrl = stringOf(item, 2).ifBlank {
                stringList(item, 3).firstOrNull { it.isNotBlank() }.orEmpty()
            }
            if (baseUrl.isBlank()) return@mapNotNull null
            val audioId = varint(item, 1)?.toInt() ?: 0
            DashAudio(
                id = audioId,
                baseUrl = baseUrl,
                backupUrl = stringList(item, 3).takeIf { it.isNotEmpty() },
                bandwidth = varint(item, 4)?.toInt() ?: 0,
                mimeType = "audio/mp4",
                codecs = audioCodecs(audioId)
            )
        }

    private fun parseDolby(bytes: ByteArray?): Dolby? {
        if (bytes == null || bytes.isEmpty()) return null
        val item = runCatching { ProtoWire.parseFields(bytes) }.getOrNull() ?: return null
        val audio = parseAudioList(item, 2)
        if (audio.isEmpty()) return null
        return Dolby(type = varint(item, 1)?.toInt() ?: 0, audio = audio)
    }

    private fun parseFlac(bytes: ByteArray?): Flac? {
        if (bytes == null || bytes.isEmpty()) return null
        val item = runCatching { ProtoWire.parseFields(bytes) }.getOrNull() ?: return null
        val audio = parseAudioList(item, 2).firstOrNull() ?: return null
        return Flac(display = true, audio = audio)
    }

    //  v28 诊断：试看失败分支的 raw 响应摘要（只打结构与原因，不打印 base64/hex）。
    //  输出：payload 字节数 + 顶层字段号 + 每个 stream 的 qn / dash / durl / 不可用原因。
    private fun describeTrialRawSummary(payload: ByteArray): String {
        if (payload.isEmpty()) return "bytes=0 topFields=[] trialInfo=false streams=[]"
        val reply = runCatching { ProtoWire.parseFields(payload) }.getOrNull()
            ?: return "bytes=${payload.size} topFields=<unparsable> trialInfo=false streams=[]"
        val topFields = reply.map { it.number }.distinct().sorted()
        val vodFields = firstBytes(reply, 1)?.let { bytes ->
            runCatching { ProtoWire.parseFields(bytes) }.getOrNull()
        }
        val streams = vodFields?.let(::describeTrialRawStreams).orEmpty()
        return "bytes=${payload.size} topFields=${topFields.joinToString(",")} " +
            "trialInfo=${hasQnTrialInfo(payload)} streams=[${streams.joinToString(", ")}]"
    }

    //  Stream 的 dash_video(2) / segment_video(3, 分段流) 只判存在性；不可用原因复用
    //  describeUnplayableStream 的权益/地址摘要（needVip/needLogin/vipFree/baseLen/backup）。
    private fun describeTrialRawStreams(vodFields: List<ProtoWire.Field>): List<String> =
        allBytes(vodFields, 5).mapIndexed { index, streamBytes ->
            val stream = runCatching { ProtoWire.parseFields(streamBytes) }.getOrNull()
                ?: return@mapIndexed "stream#$index=<unparsable>"
            val streamInfo = firstBytes(stream, 1)?.let { bytes ->
                runCatching { ProtoWire.parseFields(bytes) }.getOrNull()
            }
            val streamQn = streamInfo?.let { varint(it, 1)?.toInt() } ?: -1
            val dashVideo = firstBytes(stream, 2)?.let { bytes ->
                runCatching { ProtoWire.parseFields(bytes) }.getOrNull()
            }
            val hasDash = firstBytes(stream, 2) != null
            val hasDurl = firstBytes(stream, 3) != null
            val usable = dashVideo != null && (
                stringOf(dashVideo, 1).isNotBlank() || stringList(dashVideo, 2).isNotEmpty()
                )
            val reason = when {
                usable -> "ok"
                streamInfo == null -> "no-stream-info"
                else -> describeUnplayableStream(
                    streamQn = streamQn,
                    label = streamLabel(streamInfo),
                    streamInfo = streamInfo,
                    dashVideo = dashVideo
                )
            }
            "qn=$streamQn dash=$hasDash durl=$hasDurl why=$reason"
        }

    //  服务端在 stream_list 里会列出「有权益门槛」的档位（126 杜比视界 / 120 4K / 112 1080P+），
    //  但只有账号真正有权限时才附带 dash_video。把「列出但没有可用地址」的档位连同
    //  need_vip(6) / need_login(7) / vip_free(14) 一起记录下来，用于区分三类失败：
    //    - 服务端根本没列该档位 → 视频本身没有这一档；
    //    - 列了但无地址且 need_vip=1 → 账号权益不足（需大会员，或该档位不在试看范围）；
    //    - 有地址且 vip_free=1 → 接口已放行，失败点在 CDN（首帧超时 / 403）。
    private fun describeUnplayableStream(
        streamQn: Int,
        label: String,
        streamInfo: List<ProtoWire.Field>,
        dashVideo: List<ProtoWire.Field>? = null
    ): String {
        val needVip = varint(streamInfo, 6) == 1L
        val needLogin = varint(streamInfo, 7) == 1L
        val vipFree = varint(streamInfo, 14) == 1L
        val baseUrlLen = dashVideo?.let { stringOf(it, 1).length } ?: 0
        val backupCount = dashVideo?.let { stringList(it, 2).size } ?: 0
        val noDash = dashVideo == null
        return "$streamQn(${label.ifBlank { "?" }},needVip=$needVip,needLogin=$needLogin," +
            "vipFree=$vipFree,baseLen=$baseUrlLen,backup=$backupCount,noDash=$noDash)"
    }

    private fun streamLabel(streamInfo: List<ProtoWire.Field>): String =
        stringOf(streamInfo, 11)
            .ifBlank { stringOf(streamInfo, 3) }
            .ifBlank { stringOf(streamInfo, 12) }
            .ifBlank { stringOf(streamInfo, 13) }

    private fun videoCodecs(codecid: Int): String = when (codecid) {
        12 -> "hev1.1.6.L120.90"
        13 -> "av01.0.08M.08"
        else -> "avc1.640028"
    }

    private fun audioCodecs(audioId: Int): String = when (audioId) {
        30250 -> "ec-3"
        30251 -> "fLaC"
        else -> "mp4a.40.2"
    }

    private fun firstBytes(fields: List<ProtoWire.Field>, number: Int): ByteArray? =
        fields.firstOrNull {
            it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED
        }?.bytes

    private fun allBytes(fields: List<ProtoWire.Field>, number: Int): List<ByteArray> =
        fields.filter { it.number == number && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }
            .map { it.bytes }
            .filter { it.isNotEmpty() }

    private fun varint(fields: List<ProtoWire.Field>, number: Int): Long? =
        fields.firstOrNull {
            it.number == number && it.wireType == ProtoWire.WIRE_VARINT
        }?.varint

    private fun stringOf(fields: List<ProtoWire.Field>, number: Int): String =
        firstBytes(fields, number)?.toString(Charsets.UTF_8).orEmpty()

    private fun stringList(fields: List<ProtoWire.Field>, number: Int): List<String> =
        allBytes(fields, number).map { it.toString(Charsets.UTF_8) }.filter { it.isNotBlank() }
}
