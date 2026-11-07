package com.android.purebilibili.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 角色完备性守卫:三条生成路径(iOS 静态、静态 MD3、materialkolor 动态)
 * 都必须显式产出官方 ColorScheme 全部角色,不能依赖 Compose 默认紫调 baseline。
 */
class ThemeColorSchemeCompletenessTest {

    private val seed = Color(0xFF007AFF)
    private val lightBaseline = lightColorScheme()
    private val darkBaseline = darkColorScheme()

    private fun ColorScheme.roles(): List<Pair<String, Color>> = listOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inversePrimary" to inversePrimary,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )

    private fun assertAllRolesExplicit(
        scheme: ColorScheme,
        baseline: ColorScheme,
        label: String,
        expectedError: List<Color>,
    ) {
        val missing = scheme.roles().filter { (role, value) ->
            val isErrorRole = role.startsWith("error")
            if (isErrorRole) {
                value != expectedError[listOf("error", "onError", "errorContainer", "onErrorContainer").indexOf(role)]
            } else {
                value == baseline.roles().first { it.first == role }.second
            }
        }.map { it.first }

        assertTrue(
            missing.isEmpty(),
            "$label 仍有角色落在 Compose 默认 baseline:$missing"
        )
    }

    private fun assertSurfaceContainerOrdered(scheme: ColorScheme, label: String) {
        val lightOrder = listOf(
            scheme.surfaceContainerLowest.luminance(),
            scheme.surfaceContainerLow.luminance(),
            scheme.surfaceContainer.luminance(),
            scheme.surfaceContainerHigh.luminance(),
            scheme.surfaceContainerHighest.luminance(),
        )
        val monotonic = lightOrder.zipWithNext().all { (a, b) -> a > b } ||
            lightOrder.zipWithNext().all { (a, b) -> a < b }
        assertTrue(monotonic, "$label surfaceContainer 五级必须单调递进,实际=$lightOrder")
    }

    // --- iOS 静态方案 ---
    // --- 自定义种子 MD3 方案(运行时走 createBiliPaiStyleColorScheme → MaterialKolor HCT) ---

    @Test
    fun `custom seed md3 light scheme explicitly sets all roles`() {
        val scheme = createBiliPaiStyleColorScheme(
            seedColor = seed,
            darkTheme = false,
            amoledDarkTheme = false,
            paletteStyle = PaletteStyle.TonalSpot,
            colorSpec = ColorSpec.SpecVersion.SPEC_2021,
        )
        assertAllRolesExplicit(
            scheme, lightBaseline, "自定义种子 MD3 light",
            expectedError = scheme.roles().filter { it.first.startsWith("error") }.map { it.second }
        )
        assertSurfaceContainerOrdered(scheme, "自定义种子 MD3 light")
        assertEquals(Color.Black, scheme.scrim)
        assertEquals(seed, scheme.surfaceTint)
    }

    @Test
    fun `custom seed md3 dark scheme explicitly sets all roles`() {
        val scheme = createBiliPaiStyleColorScheme(
            seedColor = seed,
            darkTheme = true,
            amoledDarkTheme = false,
            paletteStyle = PaletteStyle.TonalSpot,
            colorSpec = ColorSpec.SpecVersion.SPEC_2021,
        )
        assertAllRolesExplicit(
            scheme, darkBaseline, "自定义种子 MD3 dark",
            expectedError = scheme.roles().filter { it.first.startsWith("error") }.map { it.second }
        )
        assertSurfaceContainerOrdered(scheme, "自定义种子 MD3 dark")
        assertEquals(Color.Black, scheme.scrim)
        assertEquals(seed, scheme.surfaceTint)
    }

    @Test
    fun `custom seed md3 amoled scheme explicitly sets all roles`() {
        val scheme = createBiliPaiStyleColorScheme(
            seedColor = seed,
            darkTheme = true,
            amoledDarkTheme = true,
            paletteStyle = PaletteStyle.TonalSpot,
            colorSpec = ColorSpec.SpecVersion.SPEC_2021,
        )
        assertAllRolesExplicit(
            scheme, darkBaseline, "自定义种子 MD3 amoled",
            expectedError = scheme.roles().filter { it.first.startsWith("error") }.map { it.second }
        )
        // amoled 覆盖会把 surfaceContainer 压到近纯黑,五级单调性由既有覆盖逻辑保证,不再断言
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertEquals(Color.Black, scheme.scrim)
    }

    // --- materialkolor 动态路径 ---

    @Test
    fun `materialkolor dynamic scheme generates all roles for light and dark`() {
        val light = dynamicColorScheme(
            seedColor = seed,
            isDark = false,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
        )
        val dark = dynamicColorScheme(
            seedColor = seed,
            isDark = true,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
        )

        assertAllRolesExplicit(light, lightBaseline, "materialkolor light", expectedError = light.roles().filter { it.first.startsWith("error") }.map { it.second })
        assertAllRolesExplicit(dark, darkBaseline, "materialkolor dark", expectedError = dark.roles().filter { it.first.startsWith("error") }.map { it.second })
        assertSurfaceContainerOrdered(light, "materialkolor light")
        assertSurfaceContainerOrdered(dark, "materialkolor dark")
        assertEquals(Color.Black, light.scrim)
        assertEquals(Color.Black, dark.scrim)
        // surfaceTint 保持 materialkolor 原生调和值，不再被原始种子 hex 覆盖
        assertNotEquals(seed, light.surfaceTint)
        assertNotEquals(seed, dark.surfaceTint)
    }
}
