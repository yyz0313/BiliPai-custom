package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.theme.UiPreset
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AppUiStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarLiquidSegmentedControlStructureTest {

    @Test
    fun `liquid segmented labels keep bottom bar foreground opacity`() {
        val onSurface = Color(0xFFF1F1F1)

        assertEquals(
            onSurface,
            resolveLiquidSegmentedControlUnselectedTextColor(
                onSurface = onSurface,
                enabled = true
            )
        )
        assertEquals(
            onSurface.copy(alpha = 0.42f),
            resolveLiquidSegmentedControlUnselectedTextColor(
                onSurface = onSurface,
                enabled = false
            )
        )
    }

    @Test
    fun `segmented labels reuse bottom bar glass content colors while moving`() {
        val unselected = Color(0xFF666666)
        val selected = Color(0xFFFF6699)

        val colors = resolveLiquidGlassSelectionContentColors(
            unselectedColor = unselected,
            selectedColor = selected,
            themeWeight = 1f,
            glassEnabled = true,
            indicatorProgress = 0.8f,
            indicatorBackdropEnabled = true
        )

        assertEquals(unselected, colors.visibleColor)
        assertEquals(unselected, colors.exportColor)
    }

    @Test
    fun `two segment indicator reuses the full home dock slot`() {
        val width = resolveSegmentedControlIndicatorWidthDp(
            slotWidthDp = 180f,
            indicatorHeightDp = 30f,
            itemCount = 2
        )

        assertEquals(180f, width)
    }

    @Test
    fun `multi segment indicator does not add inner edge insets`() {
        val width = resolveSegmentedControlIndicatorWidthDp(
            slotWidthDp = 66f,
            indicatorHeightDp = 30f,
            itemCount = 4,
        )

        assertEquals(66f, width)
    }

    @Test
    fun `multi segment indicator reuses the full home dock slot`() {
        val width = resolveSegmentedControlIndicatorWidthDp(
            slotWidthDp = 180f,
            indicatorHeightDp = 30f,
            itemCount = 3,
        )

        assertEquals(180f, width)
    }

    @Test
    fun `segmented capture expands past full drag scale lens and panel offset`() {
        assertEquals(
            72f,
            resolveBottomBarCaptureSafeInsetDp(
                indicatorWidthDp = 224f,
                refractionHeightDp = 24f,
                refractionAmountDp = 24f,
                panelOffsetDp = 4f
            ),
            0.001f
        )
        assertEquals(
            24f,
            resolveBottomBarCaptureSafeInsetDp(
                indicatorWidthDp = 0f,
                refractionHeightDp = 24f,
                refractionAmountDp = 24f,
                panelOffsetDp = 0f
            ),
            0.001f
        )
    }

    @Test
    fun `segmented indicator reduces height for cramped slots to stay capsule shaped`() {
        assertEquals(
            37.5f,
            resolveSegmentedControlIndicatorHeightDp(
                slotWidthDp = 60f,
                indicatorHeightDp = 56f,
            )
        )
    }

    @Test
    fun `segmented indicator keeps full height for already wide home slots`() {
        assertEquals(
            56f,
            resolveSegmentedControlIndicatorHeightDp(
                slotWidthDp = 128f,
                indicatorHeightDp = 56f,
            )
        )
    }

    @Test
    fun `segmented indicator offset follows slot position without clamping dead zone`() {
        assertEquals(
            4f,
            resolveSegmentedControlIndicatorOffsetDp(
                position = 0f,
                slotWidthDp = 60f,
                contentPaddingDp = 4f,
            )
        )
        assertEquals(
            34f,
            resolveSegmentedControlIndicatorOffsetDp(
                position = 0.5f,
                slotWidthDp = 60f,
                contentPaddingDp = 4f,
            )
        )
        assertEquals(
            244f,
            resolveSegmentedControlIndicatorOffsetDp(
                position = 4f,
                slotWidthDp = 60f,
                contentPaddingDp = 4f,
            )
        )
    }

    @Test
    fun `segmented control only follows continuous drag when touch starts on indicator`() {
        assertTrue(
            shouldFollowSegmentedControlIndicatorDrag(
                pointerX = 132f,
                indicatorPosition = 2f,
                itemWidthPx = 64f
            )
        )
        assertFalse(
            shouldFollowSegmentedControlIndicatorDrag(
                pointerX = 80f,
                indicatorPosition = 2f,
                itemWidthPx = 64f
            )
        )
        assertFalse(
            shouldFollowSegmentedControlIndicatorDrag(
                pointerX = 196.1f,
                indicatorPosition = 2f,
                itemWidthPx = 64f
            )
        )
    }

    @Test
    fun `segmented control sweep release resolves label without requiring indicator follow`() {
        assertEquals(
            0,
            resolveSegmentedControlSweepSelectionIndex(
                pointerX = -12f,
                itemWidthPx = 64f,
                itemCount = 4
            )
        )
        assertEquals(
            1,
            resolveSegmentedControlSweepSelectionIndex(
                pointerX = 82f,
                itemWidthPx = 64f,
                itemCount = 4
            )
        )
        assertEquals(
            3,
            resolveSegmentedControlSweepSelectionIndex(
                pointerX = 260f,
                itemWidthPx = 64f,
                itemCount = 4
            )
        )
    }

    @Test
    fun `segmented indicator can follow external realtime page position`() {
        assertEquals(
            1.35f,
            resolveSegmentedControlIndicatorPosition(
                internalPosition = 1f,
                externalPosition = 1.35f,
                itemCount = 4
            )
        )
        assertEquals(
            0f,
            resolveSegmentedControlIndicatorPosition(
                internalPosition = 1f,
                externalPosition = -0.2f,
                itemCount = 4
            )
        )
        assertEquals(
            3f,
            resolveSegmentedControlIndicatorPosition(
                internalPosition = 1f,
                externalPosition = 4.2f,
                itemCount = 4
            )
        )
    }

    @Test
    fun `native underline matches label width and remains centered in its segment`() {
        assertEquals(
            NativeUnderlineGeometry(offsetDp = 30f, widthDp = 40f),
            resolveNativeUnderlineGeometry(
                indicatorPosition = 0f,
                segmentWidthDp = 100f,
                labelWidthsDp = listOf(40f, 80f),
            )
        )
        assertEquals(
            NativeUnderlineGeometry(offsetDp = 110f, widthDp = 80f),
            resolveNativeUnderlineGeometry(
                indicatorPosition = 1f,
                segmentWidthDp = 100f,
                labelWidthsDp = listOf(40f, 80f),
            )
        )
    }

    @Test
    fun `native underline fits segments narrower than its preferred minimum width`() {
        assertEquals(
            NativeUnderlineGeometry(offsetDp = 0f, widthDp = 19f),
            resolveNativeUnderlineGeometry(
                indicatorPosition = 0f,
                segmentWidthDp = 19f,
                labelWidthsDp = listOf(16f, 16f),
            )
        )
    }

    @Test
    fun `native underline stretches between labels during page motion`() {
        val midpoint = resolveNativeUnderlineGeometry(
                indicatorPosition = 0.5f,
                segmentWidthDp = 100f,
                labelWidthsDp = listOf(40f, 80f),
            )
        assertEquals(53.431f, midpoint.offsetDp, 0.001f)
        assertEquals(101.421f, midpoint.widthDp, 0.001f)
    }

    @Test
    fun `segmented indicator only samples hidden tab backdrop while sliding without external backdrop`() {
        assertFalse(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = true,
                motionProgress = 0f,
                hasExternalBackdrop = false
            )
        )
        assertTrue(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = true,
                motionProgress = 0.01f,
                hasExternalBackdrop = false
            )
        )
        assertTrue(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = true,
                motionProgress = 0f,
                hasExternalBackdrop = true
            )
        )
        assertFalse(
            shouldDrawSegmentedControlIndicatorBackdrop(
                liquidGlassEnabled = false,
                motionProgress = 1f,
                hasExternalBackdrop = true
            )
        )
    }

    @Test
    fun `export capture backdrop requires an external page layer`() {
        assertTrue(
            shouldDrawSegmentedControlExportCaptureBackdrop(
                liquidGlassEnabled = true,
                hasExternalBackdrop = true
            )
        )
        assertFalse(
            shouldDrawSegmentedControlExportCaptureBackdrop(
                liquidGlassEnabled = true,
                hasExternalBackdrop = false
            )
        )
        assertFalse(
            shouldDrawSegmentedControlExportCaptureBackdrop(
                liquidGlassEnabled = false,
                hasExternalBackdrop = true
            )
        )
    }

    @Test
    fun `global glass uses dock plus indicator on native chrome`() {
        assertEquals(
            SegmentedControlChromeStyle.LIQUID_PILL,
            resolveSegmentedControlChromeStyle(
                uiStyle = AppUiStyle.MATERIAL3,
                prefersNativeChrome = true,
                androidNativeLiquidGlassEnabled = true,
                preferInlineContentStyle = true
            )
        )
    }

    @Test
    fun `android native chrome segmented control uses liquid pill when global glass is enabled`() {
        assertEquals(
            SegmentedControlChromeStyle.LIQUID_PILL,
            resolveSegmentedControlChromeStyle(
                uiStyle = AppUiStyle.MIUIX,
                prefersNativeChrome = true,
                androidNativeLiquidGlassEnabled = true,
                preferInlineContentStyle = false
            )
        )
    }

    @Test
    fun `segmented liquid glass follows the master switch and effect gate`() {
        assertTrue(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = false,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = false,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 33,
            )
        )
        assertFalse(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = false,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 33,
            )
        )
        assertFalse(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = false,
                sdkInt = 33,
            )
        )
    }

    @Test
    fun `segmented liquid glass falls back before Android 13 so selected content stays visible`() {
        assertFalse(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 32,
            )
        )
        assertTrue(
            resolveSegmentedControlLiquidGlassEnabled(
                storedLiquidGlassEnabled = true,
                liquidGlassEffectsEnabled = true,
                supportsIndependentLiquidGlass = true,
                androidNativeLiquidGlassEnabled = true,
                sdkInt = 33,
            )
        )
    }

}
