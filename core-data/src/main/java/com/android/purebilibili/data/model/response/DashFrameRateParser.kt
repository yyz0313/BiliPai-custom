package com.android.purebilibili.data.model.response

/**
 * playurl 的 DASH 轨道帧率字符串解析。
 *
 * B 站 playurl 下发的 `DashVideo.frameRate` 是字符串，常见两种形态：
 * - 分数形态："16000/672"（= 23.809524 fps）、"30000/1001"（= 29.97 fps）
 * - 整数/小数形态："60"、"30"、"23.976"
 *
 * 播放器侧 MediaFormat 经常给不出帧率（诊断日志里的 fps=unknown），
 * 插帧（MEMC）等下游需要这里的解析结果做兜底，因此规则保持独立、无副作用：
 * - "a/b" → a / b；
 * - 纯数字 → 直接取值；
 * - 空串 / null / 非法形态 / 分母为 0 / 非正数 / 非有限值 → null；
 * - 任何输入都不抛异常。
 */
fun parseDashFrameRate(raw: String?): Float? {
    val normalized = raw?.trim().orEmpty()
    if (normalized.isEmpty()) return null

    val separatorIndex = normalized.indexOf('/')
    val parsed = if (separatorIndex < 0) {
        normalized.toFloatOrNull()
    } else {
        val numerator = normalized.substring(0, separatorIndex).trim().toFloatOrNull()
        val denominator = normalized.substring(separatorIndex + 1).trim().toFloatOrNull()
        if (numerator == null || denominator == null) null else numerator / denominator
    } ?: return null

    // 0 / 负数 / NaN / Infinity 都不是有效帧率，按"未知"处理。
    return parsed.takeIf { it.isFinite() && it > 0f }
}

/** 已解析的片源帧率（fps）；字段缺失或非法时为 null（未知）。 */
val DashVideo.sourceFrameRate: Float?
    get() = parseDashFrameRate(frameRate)
