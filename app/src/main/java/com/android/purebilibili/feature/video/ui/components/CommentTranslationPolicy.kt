package com.android.purebilibili.feature.video.ui.components

private val commentTranslationUrl = Regex("(?i)\\b(?:https?://|www\\.)\\S+")
private val commentTranslationMention = Regex("@[^\\s@]+")
private val commentTranslationEmote = Regex("\\[[^\\]\\n]{1,30}]")

/** A missing server switch is common on some comment routes; keep explicit switches authoritative. */
internal fun shouldOfferCommentTranslation(
    switch: Int?,
    message: String,
    oid: Long,
    rpid: Long,
): Boolean {
    if (switch == 2) return true
    if (switch != null && switch != 0) return false
    if (oid <= 0 || rpid <= 0) return false

    val text = message.replace(commentTranslationUrl, " ")
        .replace(commentTranslationMention, " ")
        .replace(commentTranslationEmote, " ")
    var foreignLetters = 0
    var hanLetters = 0
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        index += Character.charCount(codePoint)
        if (!Character.isLetter(codePoint)) continue
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN -> hanLetters++
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.ARABIC,
            Character.UnicodeScript.HEBREW,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.DEVANAGARI -> foreignLetters++
            else -> Unit
        }
    }
    return foreignLetters >= 3 && foreignLetters >= hanLetters
}
