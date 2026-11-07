package com.android.purebilibili.feature.audio.lyrics

/** A display-only projection shared by cover previews and both lyric renderers. */
internal fun resolveLyricsDisplayDocument(
    document: LyricDocument?,
    showTranslations: Boolean,
): LyricDocument? {
    if (document == null || showTranslations) return document
    return document.copy(lines = document.lines.map {
        it.copy(translations = emptyList(), romanization = null)
    })
}

/**
 * Last line whose start is at or before [positionMs].
 * Unlike [resolveActiveLyricIndex] this never fails on inter-line gaps.
 */
internal fun resolveLastStartedLyricIndex(
    lines: List<LyricLine>,
    positionMs: Long,
): Int {
    if (lines.isEmpty()) return -1
    if (positionMs < lines.first().startTimeMs) return 0
    var candidate = 0
    for (index in lines.indices) {
        if (lines[index].startTimeMs <= positionMs) {
            candidate = index
        } else {
            break
        }
    }
    return candidate
}

/**
 * Gap-safe active index with backward hysteresis so 10 Hz samples do not flicker
 * between neighboring rows. Forward seeks apply immediately.
 */
internal fun resolveStableLyricIndex(
    lines: List<LyricLine>,
    positionMs: Long,
    previousIndex: Int,
    backwardHysteresisMs: Long = 220L,
): Int {
    if (lines.isEmpty()) return -1
    val raw = resolveLastStartedLyricIndex(lines, positionMs)
    if (previousIndex < 0 || previousIndex > lines.lastIndex) return raw
    if (raw >= previousIndex) return raw
    // Moving backward: keep the previous row until playback is clearly before its start.
    val previousStart = lines[previousIndex].startTimeMs
    return if (positionMs + backwardHysteresisMs >= previousStart) {
        previousIndex
    } else {
        raw
    }
}

/**
 * Clamp word/line end times so karaoke fill cannot run past the next row.
 * Prevents "still singing" from bleeding into the following lyric.
 */
internal fun normalizeLyricWordTiming(
    words: List<com.android.purebilibili.feature.audio.lyrics.halcyon.LyricWord>,
    lineEndMs: Long,
): List<com.android.purebilibili.feature.audio.lyrics.halcyon.LyricWord> {
    if (words.isEmpty()) return words
    return words.mapIndexed { index, word ->
        val nextStart = words.getOrNull(index + 1)?.startMs
        val maxEnd = nextStart ?: lineEndMs
        val end = minOf(
            word.endMs.coerceAtLeast(word.startMs + 1L),
            maxEnd.coerceAtLeast(word.startMs + 1L),
        )
        word.copy(endMs = end)
    }
}

/** Chinese / Latin text does not need a phonetic under-line (Halcyon `needsPhoneticAnnotation`). */
internal fun needsPhoneticAnnotation(text: String): Boolean {
    return text.any { char ->
        val block = Character.UnicodeBlock.of(char)
        block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    }
}

/** Secondary rows wrapped in music notes or near-duplicates of the primary line. */
internal fun isPseudoSecondaryLine(primaryText: String, secondaryText: String): Boolean {
    val secondary = secondaryText.trim()
    if (secondary.isEmpty()) return true
    if (secondary.any { it == '♪' || it == '♫' }) {
        return true
    }
    val unwrapped = secondary
        .trimStart('♪', '♫', ' ', '\t')
        .trimEnd('♪', '♫', ' ', '\t')
    if (unwrapped.isNotBlank() && unwrapped.length < secondary.length) {
        return true
    }
    val primary = primaryText.trim()
    if (primary.isEmpty()) return false
    if (primary == secondary || primary == unwrapped) return true
    // Cheap near-duplicate: share a long prefix after stripping punctuation.
    fun normalize(value: String): String = value.filterNot {
        it.isWhitespace() || it in "♪♫，。？！、,.?!\"'“”‘’"
    }
    val a = normalize(primary)
    val b = normalize(secondary)
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    val shorter = minOf(a.length, b.length)
    if (shorter < 6) return false
    val prefixMatches = (0 until shorter).count { a[it] == b[it] }
    return prefixMatches.toFloat() / shorter >= 0.82f
}

/**
 * Display filters for under-line secondary rows.
 * Returns the translation / romanization that should actually be drawn.
 */
internal fun resolveDisplaySecondaryRows(
    primaryText: String,
    translation: String?,
    romanization: String?,
    showTranslation: Boolean,
): Pair<String?, String?> {
    if (!showTranslation) return null to null
    val displayTranslation = translation
        ?.trim()
        ?.takeIf { it.isNotBlank() && !isPseudoSecondaryLine(primaryText, it) }
    val displayRomanization = romanization
        ?.trim()
        ?.takeIf {
            it.isNotBlank() &&
                needsPhoneticAnnotation(primaryText) &&
                !isPseudoSecondaryLine(primaryText, it)
        }
    return displayTranslation to displayRomanization
}
