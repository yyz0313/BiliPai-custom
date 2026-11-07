package com.android.purebilibili.feature.video.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailShapeStructureTest {

    @Test
    fun `detail shapes use theme semantics instead of hardcoded corner radii`() {
        val source = loadMainSource("feature/video/ui/VideoDetailShapes.kt")

        listOf("shapes.small", "shapes.medium", "shapes.large", "shapes.extraLarge")
            .forEach { token -> assertTrue(source.contains(token), "Missing MD3 token: $token") }
        assertTrue(source.contains("AppUiStyle.MATERIAL3"))
        assertTrue(source.contains("AppUiStyle.MIUIX"))
        assertTrue(source.contains("AppShapes.container"))
        assertFalse(source.contains("RoundedCornerShape"))
        assertFalse(source.contains(".dp"))
    }

    private fun loadMainSource(relativePath: String): String {
        return listOf(
            File("src/main/java/com/android/purebilibili/$relativePath"),
            File("app/src/main/java/com/android/purebilibili/$relativePath"),
        ).first { it.exists() }.readText()
    }
}
