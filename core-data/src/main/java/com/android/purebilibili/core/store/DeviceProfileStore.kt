// 文件路径: core/store/DeviceProfileStore.kt
package com.android.purebilibili.core.store

import android.content.Context
import com.android.purebilibili.core.network.grpc.FpLocalGenerator

/**
 * v23 实验用设备画像持久化（仅 unlock_device_profile_alignment 开启时读写）。
 *
 * 对齐官方 x-bili-device-bin 的 Device.fts(15)：首次启动毫秒时间戳，跨启动保持稳定。
 * 持久化方式参考 TokenManager 的 SharedPreferences 备份模式；fts 非敏感信息，不做加密。
 * 开关默认关闭时本存储不会被访问，不影响 v22 成功路径。
 */
object DeviceProfileStore {
    private const val SP_NAME = "device_profile_sp"
    private const val SP_KEY_FIRST_LAUNCH_FTS = "first_launch_fts"
    private const val SP_KEY_ALIGNED_BUVID = "aligned_buvid"
    private const val SP_KEY_FP_LOCAL = "fp_local"
    private const val SP_KEY_FP_LOCAL_MODEL = "fp_local_model"
    private const val SP_KEY_FP_LOCAL_BUVID = "fp_local_buvid"

    fun getFirstLaunchFts(context: Context): Long {
        val preferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getLong(SP_KEY_FIRST_LAUNCH_FTS, 0L)
        if (stored > 0L) return stored
        val now = System.currentTimeMillis()
        preferences.edit().putLong(SP_KEY_FIRST_LAUNCH_FTS, now).apply()
        return now
    }

    /**
     * v23 实验（开关1）：稳定 buvid —— 优先落盘值，其次使用当前内存值（避免同一启动内变更），
     * 都没有时生成一个并落盘。返回最终使用的 buvid。
     */
    fun getOrCreateAlignedBuvid(context: Context, current: String?): String {
        val preferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(SP_KEY_ALIGNED_BUVID, null)
        if (!stored.isNullOrBlank()) return stored
        val stable = current?.takeIf { it.isNotBlank() }
            ?: (java.util.UUID.randomUUID().toString().replace("-", "") + "infoc")
        preferences.edit().putString(SP_KEY_ALIGNED_BUVID, stable).apply()
        return stable
    }

    /**
     * v26 实验：设备指纹 fp_local 的持久化读写（官方 biliid Fingerprint.d() 同构算法）。
     *
     * 与官方行为对应：官方在 buvid 或 brand/model 变化时清空重算；这里以
     * 「已存值长度 64 + model 一致 + buvid 一致」作为复用条件，任何一项变化都会
     * 重新生成并覆盖落盘。fp_local 为设备指纹（非敏感凭据），沿用本存储的明文
     * SharedPreferences；仅在 unlock_device_fp_fields 开关开启时被调用。
     */
    fun getOrCreateFpLocal(
        context: Context,
        buvid: String,
        model: String,
        radioVersion: String
    ): String {
        val preferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(SP_KEY_FP_LOCAL, null)
        val storedModel = preferences.getString(SP_KEY_FP_LOCAL_MODEL, null)
        val storedBuvid = preferences.getString(SP_KEY_FP_LOCAL_BUVID, null)
        if (stored != null && stored.length == 64 && storedModel == model && storedBuvid == buvid) {
            return stored
        }
        val fp = FpLocalGenerator.generateNow(buvid, model, radioVersion)
        preferences.edit()
            .putString(SP_KEY_FP_LOCAL, fp)
            .putString(SP_KEY_FP_LOCAL_MODEL, model)
            .putString(SP_KEY_FP_LOCAL_BUVID, buvid)
            .apply()
        return fp
    }
}
