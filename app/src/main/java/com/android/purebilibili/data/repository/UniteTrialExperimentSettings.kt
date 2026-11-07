// 文件路径: data/repository/UniteTrialExperimentSettings.kt
package com.android.purebilibili.data.repository

import android.content.Context
import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import com.android.purebilibili.core.store.player.PlayerSettingsStore

/**
 * v23/v24/v25/v26 试看取流实验开关解析（默认关闭，仅作用于 UGC/PGC 试看调用点）。
 *
 * 关闭时返回 v22 既有行为：PINK 身份、android/android/15 设备头、不补请求字段，
 * 因此请求字节与现状完全一致；开关仅用于排查杜比视界/HDR 等档位不下发 URL 的问题。
 */
internal fun resolveUniteTrialIdentity(context: Context?): BiliGrpcClient.ClientIdentity {
    //  v24 实验：身份优先级 OFFICIAL > PLAY > PINK。
    val officialIdentityEnabled = context
        ?.let { PlayerSettingsStore.getUnlockOfficialIdentitySync(it) }
        ?: false
    if (officialIdentityEnabled) {
        return BiliGrpcClient.ClientIdentity.OFFICIAL
    }
    val playIdentityEnabled = context
        ?.let { PlayerSettingsStore.getUnlockPlayIdentitySync(it) }
        ?: false
    return if (playIdentityEnabled) {
        BiliGrpcClient.ClientIdentity.PLAY
    } else {
        BiliGrpcClient.ClientIdentity.PINK
    }
}

internal fun resolveUniteTrialDeviceProfileAlignment(context: Context?): Boolean =
    context?.let { PlayerSettingsStore.getUnlockDeviceProfileAlignmentSync(it) } ?: false

internal fun resolveUniteTrialDeviceSpoofPlus(context: Context?): Boolean =
    context?.let { PlayerSettingsStore.getUnlockDeviceSpoofPlusSync(it) } ?: false

internal fun resolveUniteTrialExperimentalFields(context: Context?): Boolean =
    context?.let { PlayerSettingsStore.getUnlockExperimentalRequestFieldsSync(it) } ?: false

internal fun resolveUniteTrialOfficialWireShape(context: Context?): Boolean =
    context?.let { PlayerSettingsStore.getUnlockOfficialWireShapeSync(it) } ?: false

//  v26 实验（默认关闭）：试看请求补设备指纹 fp_local(11)/fp_remote(12)/fp(14)。
internal fun resolveUniteTrialFpFields(context: Context?): Boolean =
    context?.let { PlayerSettingsStore.getUnlockDeviceFpFieldsSync(it) } ?: false

//  v26 实验（默认关闭）：试看通道首档直发 qn=126（杜比视界），验证服务端是否按请求档位下发。
internal fun resolveUniteTrialDolbyDirectQn(context: Context?): Boolean =
    context?.let { PlayerSettingsStore.getUnlockDolbyDirectQnSync(it) } ?: false

//  v28 实验（默认关闭）：PGC 试看请求不带 is_need_trial(11)，验证服务端在该形态下的下发；
//  默认关闭时请求字节与现状一致。
internal fun resolveUnitePgcTrialWithoutNeedTrial(context: Context?): Boolean =
    context?.let { PlayerSettingsStore.getUnlockPgcTrialWithoutNeedTrialSync(it) } ?: false
