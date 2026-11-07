package com.android.purebilibili.core.util

/** 列表图片只调整受支持 CDN 的压缩质量，保留尺寸、裁切和查询参数边界。 */
object BiliImageQualityPolicy {
    const val HIGH_QUALITY = 100
    const val DATA_SAVER_QUALITY = 10
    private val qualityParameter = Regex("(?:^|_)\\d+q(?=_|$)")
    private val rasterExtension = Regex("\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE)

    fun resolveUrl(url: String, dataSaver: Boolean): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return url
        if (!listOf("hdslb.com", "biliimg.com").any { host == it || host.endsWith(".$it") }) return url
        // 签名和查询参数地址、动图与装饰素材保持服务端原有语义。
        if ('?' in url || '#' in url || "/bfs/emote/" in url || "/bfs/garb/" in url) return url
        val original = url.substringBefore('@')
        if (!rasterExtension.containsMatchIn(original)) return url
        val quality = if (dataSaver) DATA_SAVER_QUALITY else HIGH_QUALITY
        val suffix = url.substringAfter('@', "")
        // 命名预设由 CDN 定义，不在客户端拼接未知指令。
        if (suffix.startsWith('!')) return url
        val transform = suffix.substringBeforeLast('.', suffix)
            .replace(qualityParameter, "").trim('_')
        val parameters = if (transform.isEmpty()) "${quality}q" else "${transform}_${quality}q"
        return "$original@$parameters.webp"
    }
}
