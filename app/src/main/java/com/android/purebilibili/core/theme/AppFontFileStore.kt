package com.android.purebilibili.core.theme

import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.Locale

private const val APP_FONT_DIR_NAME = "app_fonts"
private const val APP_FONT_FILE_PREFIX = "custom_app_font"
private val APP_FONT_ALLOWED_EXTENSIONS = setOf("ttf", "otf", "ttc")

data class ImportedAppFontFile(
    val fileName: String,
    val displayName: String,
    /** 导入字体是否覆盖 CJK 代表字形;false 时中文渲染回退系统字体。 */
    val coversCjk: Boolean = true,
)

/** 缺少中文字形时的用户提示;覆盖完整时返回 null,便于纯 JVM 断言。 */
internal fun resolveAppFontCoverageNotice(coversCjk: Boolean): String? =
    if (coversCjk) null else "该字体缺少中文字形，中文将回退系统字体"

/**
 * 探测导入字体的 CJK 覆盖情况;接口化以便策略测试不依赖 android.graphics。
 */
fun interface AppFontGlyphProbe {
    fun coversCjk(typeface: Typeface): Boolean
}

/** 用系统 Paint 对「哔哩」采样做逐字探测,探测本身失败时按覆盖处理。 */
val DefaultAppFontGlyphProbe = AppFontGlyphProbe { typeface ->
    val paint = android.graphics.Paint()
    paint.typeface = typeface
    "哔哩".all { paint.hasGlyph(it.toString()) }
}

internal fun sanitizeAppFontDisplayName(name: String?): String {
    val trimmed = name?.trim().orEmpty()
    return trimmed.ifBlank { "本地字体" }
}

internal fun resolveAppFontExtension(displayName: String?): String {
    val extension = displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    return extension.takeIf { it in APP_FONT_ALLOWED_EXTENSIONS } ?: "ttf"
}

internal fun buildStoredAppFontFileName(displayName: String?): String {
    return "$APP_FONT_FILE_PREFIX.${resolveAppFontExtension(displayName)}"
}

fun resolveStoredAppFontFile(context: Context, fileName: String): File? {
    val normalizedName = fileName.substringAfterLast('/').trim()
    if (normalizedName.isBlank()) return null
    return File(File(context.filesDir, APP_FONT_DIR_NAME), normalizedName)
        .takeIf { it.exists() && it.isFile }
}

fun loadStoredAppFontFamily(context: Context, fileName: String): FontFamily? {
    val fontFile = resolveStoredAppFontFile(context, fileName) ?: return null
    return runCatching {
        FontFamily(Typeface.createFromFile(fontFile))
    }.getOrNull()
}

fun importAppFontFromUri(
    context: Context,
    uri: Uri,
    glyphProbe: AppFontGlyphProbe = DefaultAppFontGlyphProbe,
): Result<ImportedAppFontFile> {
    return runCatching {
        val displayName = sanitizeAppFontDisplayName(queryDisplayName(context, uri))
        val storedFileName = buildStoredAppFontFileName(displayName)
        val fontDir = File(context.filesDir, APP_FONT_DIR_NAME).apply { mkdirs() }
        val targetFile = File(fontDir, storedFileName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取字体文件" }
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }

        // 先加载一次，避免把非字体文件保存为全局字体后导致后续启动回退。
        val typeface = Typeface.createFromFile(targetFile)
        val coversCjk = runCatching { glyphProbe.coversCjk(typeface) }.getOrDefault(true)
        ImportedAppFontFile(
            fileName = storedFileName,
            displayName = displayName,
            coversCjk = coversCjk,
        )
    }
}

fun deleteStoredAppFont(context: Context, fileName: String) {
    resolveStoredAppFontFile(context, fileName)?.delete()
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor: Cursor? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )
    return cursor.use {
        if (it != null && it.moveToFirst()) {
            it.getString(0)
        } else {
            uri.lastPathSegment
        }
    }
}
