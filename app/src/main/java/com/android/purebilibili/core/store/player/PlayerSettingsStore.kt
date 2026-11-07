package com.android.purebilibili.core.store.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.resolvePreferredPlaybackSpeed as resolvePreferredPlaybackSpeedPolicy
import com.android.purebilibili.core.store.DEFAULT_LONG_PRESS_SPEED
import com.android.purebilibili.core.store.nearestPlaybackSpeed
import com.android.purebilibili.core.store.normalizeLongPressSpeed
import com.android.purebilibili.core.store.normalizePlaybackSpeedOptions
import com.android.purebilibili.core.store.resolvePlaybackSpeedOptions
import com.android.purebilibili.core.store.normalizePlaybackSpeed as normalizePlaybackSpeedPolicy
import com.android.purebilibili.core.store.settingsDataStore
import com.android.purebilibili.feature.video.playback.audio.resolveRequestedAudioQuality as resolveRequestedAudioQualityPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

const val DEFAULT_AUDIO_QUALITY_FOLLOW_LAST = -2

internal val defaultAudioQualityPreferenceKey = intPreferencesKey("default_audio_quality")
internal val longPressSpeedPreferenceKey = floatPreferencesKey("long_press_speed")
internal val playbackSpeedOptionsPreferenceKey = stringPreferencesKey("playback_speed_options")

object PlayerSettingsStore {
    enum class PlayerInsightMode {
        OFF,
        SMART,
        ALWAYS
    }

    private val keyDefaultPlaybackSpeed = floatPreferencesKey("default_playback_speed")
    private val keyRememberLastPlaybackSpeed = booleanPreferencesKey("remember_last_playback_speed")
    private val keyLastPlaybackSpeed = floatPreferencesKey("last_playback_speed")
    private val keyPreferredPlayerVolume = floatPreferencesKey("preferred_player_volume")
    private val keyPlayerInsightMode = stringPreferencesKey("player_insight_mode")
    private val keyNativeMiuixPlayerPopups = booleanPreferencesKey("native_miuix_player_popups")
    private val keyUnlockVipQuality = booleanPreferencesKey("unlock_vip_quality")
    private val keyUnlockDeviceProfileAlignment =
        booleanPreferencesKey("unlock_device_profile_alignment")
    private val keyUnlockPlayIdentity = booleanPreferencesKey("unlock_play_identity")
    private val keyUnlockExperimentalRequestFields =
        booleanPreferencesKey("unlock_experimental_request_fields")
    private val keyUnlockOfficialIdentity = booleanPreferencesKey("unlock_official_identity")
    private val keyUnlockOfficialWireShape = booleanPreferencesKey("unlock_official_wire_shape")
    private val keyUnlockDeviceSpoofPlus = booleanPreferencesKey("unlock_device_spoof_plus")
    private val keyUnlockDeviceFpFields = booleanPreferencesKey("unlock_device_fp_fields")
    private val keyUnlockDolbyDirectQn = booleanPreferencesKey("unlock_dolby_direct_qn")
    private val keyUnlockPgcTrialWithoutNeedTrial =
        booleanPreferencesKey("unlock_pgc_trial_without_need_trial")
    private const val playbackSpeedCachePrefs = "playback_speed_cache"
    private const val cacheKeyDefaultPlaybackSpeed = "default_speed"
    private const val cacheKeyRememberLastSpeed = "remember_last_speed"
    private const val cacheKeyLastPlaybackSpeed = "last_speed"
    private const val cacheKeyPreferredPlayerVolume = "preferred_player_volume"
    private const val audioQualityCachePrefs = "quality_settings"
    private const val cacheKeyAudioQuality = "audio_quality"
    private const val cacheKeyDefaultAudioQuality = "default_audio_quality"
    private const val cacheKeyUnlockVipQuality = "unlock_vip_quality"
    private const val cacheKeyUnlockDeviceProfileAlignment = "unlock_device_profile_alignment"
    private const val cacheKeyUnlockPlayIdentity = "unlock_play_identity"
    private const val cacheKeyUnlockExperimentalRequestFields = "unlock_experimental_request_fields"
    private const val cacheKeyUnlockOfficialIdentity = "unlock_official_identity"
    private const val cacheKeyUnlockOfficialWireShape = "unlock_official_wire_shape"
    private const val cacheKeyUnlockDeviceSpoofPlus = "unlock_device_spoof_plus"
    private const val cacheKeyUnlockDeviceFpFields = "unlock_device_fp_fields"
    private const val cacheKeyUnlockDolbyDirectQn = "unlock_dolby_direct_qn"
    private const val cacheKeyUnlockPgcTrialWithoutNeedTrial = "unlock_pgc_trial_without_need_trial"
    private const val legacyAppPrefs = "app_prefs"
    private const val legacyShowStatsKey = "show_stats"
    private const val cachePlayerInsightModeKey = "player_insight_mode_cache"

    const val PLAYER_VOLUME_STEP = 0.02f

    fun getNativeMiuixPlayerPopups(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyNativeMiuixPlayerPopups] ?: true
        }

    suspend fun setNativeMiuixPlayerPopups(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyNativeMiuixPlayerPopups] = enabled
        }
    }

    fun normalizePlayerVolume(volume: Float): Float {
        val stepCount = (volume.coerceIn(0f, 1f) / PLAYER_VOLUME_STEP).toInt()
        val lower = stepCount * PLAYER_VOLUME_STEP
        val upper = ((stepCount + 1) * PLAYER_VOLUME_STEP).coerceAtMost(1f)
        return if (volume - lower < upper - volume) lower else upper
    }

    fun normalizePlaybackSpeed(speed: Float): Float {
        return normalizePlaybackSpeedPolicy(speed)
    }

    fun resolvePreferredPlaybackSpeed(
        defaultSpeed: Float,
        rememberLastSpeed: Boolean,
        lastSpeed: Float
    ): Float {
        return resolvePreferredPlaybackSpeedPolicy(
            defaultSpeed = defaultSpeed,
            rememberLastSpeed = rememberLastSpeed,
            lastSpeed = lastSpeed
        )
    }

    internal fun playbackSpeedOptions(preferences: Preferences): List<Float> =
        resolvePlaybackSpeedOptions(
            storedValues = preferences[playbackSpeedOptionsPreferenceKey],
            legacyDefaultSpeed = preferences[keyDefaultPlaybackSpeed] ?: 1f,
            legacyLastSpeed = preferences[keyLastPlaybackSpeed] ?: 1f
        )

    fun getPlaybackSpeedOptions(context: Context): Flow<List<Float>> =
        context.settingsDataStore.data.map(::playbackSpeedOptions)

    suspend fun addPlaybackSpeedOption(context: Context, speed: Float) {
        updatePlaybackSpeedOptions(context) { options -> options + speed }
    }

    suspend fun removePlaybackSpeedOption(context: Context, speed: Float) {
        updatePlaybackSpeedOptions(context) { options -> options - speed }
    }

    private suspend fun updatePlaybackSpeedOptions(
        context: Context,
        change: (List<Float>) -> List<Float>
    ) {
        val preferences = context.settingsDataStore.edit { values ->
            val options = normalizePlaybackSpeedOptions(change(playbackSpeedOptions(values)))
            values[playbackSpeedOptionsPreferenceKey] = options.joinToString(",")
            reconcilePlaybackSpeedSelections(values, options)
        }
        syncPlaybackSpeedCache(context, preferences)
    }

    internal fun reconcilePlaybackSpeedSelections(
        values: MutablePreferences,
        options: List<Float> = playbackSpeedOptions(values)
    ) {
        values[keyDefaultPlaybackSpeed] =
            nearestPlaybackSpeed(values[keyDefaultPlaybackSpeed] ?: 1f, options)
        values[longPressSpeedPreferenceKey] =
            normalizeLongPressSpeed(values[longPressSpeedPreferenceKey] ?: DEFAULT_LONG_PRESS_SPEED)
        values[keyLastPlaybackSpeed] =
            nearestPlaybackSpeed(values[keyLastPlaybackSpeed] ?: 1f, options)
    }

    fun getLongPressSpeed(context: Context): Flow<Float> =
        context.settingsDataStore.data.map { preferences ->
            normalizeLongPressSpeed(preferences[longPressSpeedPreferenceKey] ?: DEFAULT_LONG_PRESS_SPEED)
        }

    suspend fun setLongPressSpeed(context: Context, speed: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[longPressSpeedPreferenceKey] = normalizeLongPressSpeed(speed)
        }
    }

    fun getDefaultPlaybackSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            nearestPlaybackSpeed(
                preferences[keyDefaultPlaybackSpeed] ?: 1f, playbackSpeedOptions(preferences)
            )
        }

    suspend fun setDefaultPlaybackSpeed(context: Context, speed: Float) {
        val preferences = context.settingsDataStore.edit { values ->
            values[keyDefaultPlaybackSpeed] = nearestPlaybackSpeed(speed, playbackSpeedOptions(values))
        }
        syncPlaybackSpeedCache(context, preferences)
    }

    internal fun syncPlaybackSpeedCache(context: Context, preferences: Preferences) {
        val options = playbackSpeedOptions(preferences)
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putFloat(
                cacheKeyDefaultPlaybackSpeed,
                nearestPlaybackSpeed(preferences[keyDefaultPlaybackSpeed] ?: 1f, options)
            )
            .putBoolean(cacheKeyRememberLastSpeed, preferences[keyRememberLastPlaybackSpeed] ?: false)
            .putFloat(
                cacheKeyLastPlaybackSpeed,
                nearestPlaybackSpeed(preferences[keyLastPlaybackSpeed] ?: 1f, options)
            )
            .apply()
    }

    fun getRememberLastPlaybackSpeed(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[keyRememberLastPlaybackSpeed] ?: false }

    suspend fun setRememberLastPlaybackSpeed(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyRememberLastPlaybackSpeed] = enabled
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyRememberLastSpeed, enabled)
            .apply()
    }

    fun getLastPlaybackSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            nearestPlaybackSpeed(
                preferences[keyLastPlaybackSpeed] ?: 1f, playbackSpeedOptions(preferences)
            )
        }

    suspend fun setLastPlaybackSpeed(context: Context, speed: Float) {
        val preferences = context.settingsDataStore.edit { values ->
            values[keyLastPlaybackSpeed] = nearestPlaybackSpeed(speed, playbackSpeedOptions(values))
        }
        syncPlaybackSpeedCache(context, preferences)
    }

    fun getPreferredPlaybackSpeed(context: Context): Flow<Float> = combine(
        getDefaultPlaybackSpeed(context),
        getRememberLastPlaybackSpeed(context),
        getLastPlaybackSpeed(context)
    ) { defaultSpeed, rememberLast, lastSpeed ->
        resolvePreferredPlaybackSpeed(
            defaultSpeed = defaultSpeed,
            rememberLastSpeed = rememberLast,
            lastSpeed = lastSpeed
        )
    }

    fun getPreferredPlaybackSpeedSync(context: Context): Float {
        val prefs = context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
        val defaultSpeed = normalizePlaybackSpeed(prefs.getFloat(cacheKeyDefaultPlaybackSpeed, 1.0f))
        val rememberLast = prefs.getBoolean(cacheKeyRememberLastSpeed, false)
        val lastSpeed = normalizePlaybackSpeed(prefs.getFloat(cacheKeyLastPlaybackSpeed, 1.0f))
        return resolvePreferredPlaybackSpeed(
            defaultSpeed = defaultSpeed,
            rememberLastSpeed = rememberLast,
            lastSpeed = lastSpeed
        )
    }

    fun getPreferredPlayerVolume(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizePlayerVolume(preferences[keyPreferredPlayerVolume] ?: 1.0f)
        }

    suspend fun setPreferredPlayerVolume(context: Context, volume: Float) {
        val normalized = normalizePlayerVolume(volume)
        context.settingsDataStore.edit { preferences ->
            preferences[keyPreferredPlayerVolume] = normalized
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putFloat(cacheKeyPreferredPlayerVolume, normalized)
            .apply()
    }

    fun getPreferredPlayerVolumeSync(context: Context): Float {
        return normalizePlayerVolume(
            context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
                .getFloat(cacheKeyPreferredPlayerVolume, 1.0f)
        )
    }

    fun getDefaultAudioQuality(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[defaultAudioQualityPreferenceKey] ?: DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
        }

    suspend fun setDefaultAudioQuality(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[defaultAudioQualityPreferenceKey] = value
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putInt(cacheKeyDefaultAudioQuality, value)
            .commit()
    }

    fun getCachedDefaultAudioQuality(context: Context): Int {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getInt(cacheKeyDefaultAudioQuality, DEFAULT_AUDIO_QUALITY_FOLLOW_LAST)
    }

    fun getCachedLastSelectedAudioQuality(context: Context): Int {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getInt(cacheKeyAudioQuality, -1)
    }

    /**
     * 解析「下一个视频沿用」的请求音质：默认档为「跟随上次选择」时取上次手动记忆，
     * 否则取默认档；非法/未知值统一回落 AUTO。
     *
     * 竖屏播放器切到下一个视频、详情页重新进入播放都应与设置页保持同一口径，
     * 避免各入口各写一套跟随逻辑，导致「记忆了但没沿用」看起来失效。
     */
    fun resolveAudioQualityForNextPlayback(context: Context): Int {
        return resolveRequestedAudioQualityPolicy(
            defaultAudioQuality = getCachedDefaultAudioQuality(context),
            rememberedAudioQuality = getCachedLastSelectedAudioQuality(context)
        )
    }

    fun getUnlockVipQuality(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[keyUnlockVipQuality] ?: true
        }

    suspend fun setUnlockVipQuality(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockVipQuality] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockVipQuality, enabled)
            .apply()
    }

    fun getUnlockVipQualitySync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockVipQuality, true)
    }

    //  v23 实验开关：仅用于排查杜比视界/HDR 档位不下发 URL 的问题。设备画像对齐默认开启（v26 起合并为默认）；旧版播放身份仍默认关闭。
    fun getUnlockDeviceProfileAlignment(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockDeviceProfileAlignment] ?: true
        }

    suspend fun setUnlockDeviceProfileAlignment(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockDeviceProfileAlignment] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockDeviceProfileAlignment, enabled)
            .apply()
    }

    fun getUnlockDeviceProfileAlignmentSync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockDeviceProfileAlignment, true)
    }

    fun getUnlockPlayIdentity(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockPlayIdentity] ?: false
        }

    suspend fun setUnlockPlayIdentity(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockPlayIdentity] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockPlayIdentity, enabled)
            .apply()
    }

    fun getUnlockPlayIdentitySync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockPlayIdentity, false)
    }

    fun getUnlockExperimentalRequestFields(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockExperimentalRequestFields] ?: true
        }

    suspend fun setUnlockExperimentalRequestFields(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockExperimentalRequestFields] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockExperimentalRequestFields, enabled)
            .apply()
    }

    fun getUnlockExperimentalRequestFieldsSync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockExperimentalRequestFields, true)
    }

    //  v24 实验开关（默认开启，v26 起合并为默认）：仅用于排查杜比视界/HDR 档位不下发 URL 的问题。
    fun getUnlockOfficialIdentity(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockOfficialIdentity] ?: true
        }

    suspend fun setUnlockOfficialIdentity(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockOfficialIdentity] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockOfficialIdentity, enabled)
            .apply()
    }

    fun getUnlockOfficialIdentitySync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockOfficialIdentity, true)
    }

    fun getUnlockOfficialWireShape(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockOfficialWireShape] ?: true
        }

    suspend fun setUnlockOfficialWireShape(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockOfficialWireShape] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockOfficialWireShape, enabled)
            .apply()
    }

    fun getUnlockOfficialWireShapeSync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockOfficialWireShape, true)
    }

    //  v25 实验开关（默认开启，v26 起合并为默认）：试看请求的设备身份伪装为 OnePlus/OPD2515（对齐平板版 padunlock 模块）。
    fun getUnlockDeviceSpoofPlus(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockDeviceSpoofPlus] ?: true
        }

    suspend fun setUnlockDeviceSpoofPlus(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockDeviceSpoofPlus] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockDeviceSpoofPlus, enabled)
            .apply()
    }

    fun getUnlockDeviceSpoofPlusSync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockDeviceSpoofPlus, true)
    }

    //  v26 实验开关（默认关闭）：试看请求补设备指纹字段 fp_local(11)/fp_remote(12)/fp(14)。
    fun getUnlockDeviceFpFields(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockDeviceFpFields] ?: false
        }

    suspend fun setUnlockDeviceFpFields(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockDeviceFpFields] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockDeviceFpFields, enabled)
            .apply()
    }

    fun getUnlockDeviceFpFieldsSync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockDeviceFpFields, false)
    }

    //  v26 实验开关（默认关闭）：试看通道首档直接请求 126，观察服务端是否下发杜比流。
    fun getUnlockDolbyDirectQn(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockDolbyDirectQn] ?: false
        }

    suspend fun setUnlockDolbyDirectQn(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockDolbyDirectQn] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockDolbyDirectQn, enabled)
            .apply()
    }

    fun getUnlockDolbyDirectQnSync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockDolbyDirectQn, false)
    }

    //  v28 实验开关（默认关闭）：PGC 试看请求不带 is_need_trial（isNeedTrial=false）。
    fun getUnlockPgcTrialWithoutNeedTrial(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyUnlockPgcTrialWithoutNeedTrial] ?: false
        }

    suspend fun setUnlockPgcTrialWithoutNeedTrial(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUnlockPgcTrialWithoutNeedTrial] = enabled
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyUnlockPgcTrialWithoutNeedTrial, enabled)
            .apply()
    }

    fun getUnlockPgcTrialWithoutNeedTrialSync(context: Context): Boolean {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getBoolean(cacheKeyUnlockPgcTrialWithoutNeedTrial, false)
    }

    fun getPlayerInsightMode(context: Context): Flow<PlayerInsightMode> = context.settingsDataStore.data
        .map { preferences ->
            val legacyPreferences = context.getSharedPreferences(legacyAppPrefs, Context.MODE_PRIVATE)
            resolvePlayerInsightMode(
                storedMode = preferences[keyPlayerInsightMode],
                legacyPreferencePresent = legacyPreferences.contains(legacyShowStatsKey),
                legacyStatsEnabled = legacyPreferences.getBoolean(legacyShowStatsKey, false)
            )
        }

    suspend fun setPlayerInsightMode(context: Context, mode: PlayerInsightMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyPlayerInsightMode] = mode.name
        }
        context.getSharedPreferences(legacyAppPrefs, Context.MODE_PRIVATE)
            .edit()
            .putString(cachePlayerInsightModeKey, mode.name)
            .putBoolean(legacyShowStatsKey, mode != PlayerInsightMode.OFF)
            .apply()
    }

    fun getPlayerInsightModeSync(context: Context): PlayerInsightMode {
        val preferences = context.getSharedPreferences(legacyAppPrefs, Context.MODE_PRIVATE)
        return resolvePlayerInsightMode(
            storedMode = preferences.getString(cachePlayerInsightModeKey, null),
            legacyPreferencePresent = preferences.contains(legacyShowStatsKey),
            legacyStatsEnabled = preferences.getBoolean(legacyShowStatsKey, false)
        )
    }

    internal fun resolvePlayerInsightMode(
        storedMode: String?,
        legacyPreferencePresent: Boolean,
        legacyStatsEnabled: Boolean
    ): PlayerInsightMode {
        PlayerInsightMode.entries.firstOrNull { it.name == storedMode }?.let { return it }
        // 全新安装 / 无历史偏好：默认关闭，避免首播叠加洞察浮层
        if (!legacyPreferencePresent) return PlayerInsightMode.OFF
        return if (legacyStatsEnabled) {
            PlayerInsightMode.ALWAYS
        } else {
            PlayerInsightMode.OFF
        }
    }
}

