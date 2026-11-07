package com.android.purebilibili.core.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeNativeSelectionStructureTest {

    @Test
    fun sharedMediaCardsUseNativeCheckboxesInsteadOfPaintedSelectionOverlays() {
        val source = loadAppSource("feature/personal/PersonalMediaCard.kt")

        assertTrue(source.contains("AppCheckbox("))
        assertFalse(source.contains("PersonalMediaCardDefaults.selectionOverlayColor"))
        assertFalse(source.contains("color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)"))
    }

    private fun loadAppSource(relativePath: String): String {
        val path = "src/main/java/com/android/purebilibili/$relativePath"
        return listOf(File(path), File("app/$path"))
            .firstOrNull(File::exists)
            ?.readText()
            ?: error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
