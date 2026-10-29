package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeContrastTest {
    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val x = channel.toDouble()
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun assertInk(ink: Color, panel: Color, label: String) {
        val value = contrast(ink, panel)
        assertTrue("$label: $value < 4.5 on $panel", value >= 4.5)
    }

    @Test
    fun fixedSurfaceAndInputRemainLegibleForSystemDarkAndLight() {
        for (dark in listOf(false, true)) {
            val scheme = simAppColorScheme(dark)
            assertTrue("onSurface dark=$dark", contrast(scheme.onSurface, scheme.surface) >= 4.5)
            assertTrue("onBackground dark=$dark", contrast(scheme.onBackground, scheme.background) >= 4.5)
            assertTrue("onPrimary dark=$dark", contrast(scheme.onPrimary, scheme.primary) >= 4.5)
            assertTrue("onSurfaceVariant dark=$dark", contrast(scheme.onSurfaceVariant, scheme.surface) >= 4.5)
            val p = inputFieldPalette(dark)
            for (ink in listOf(p.text, p.label, p.placeholder, p.icon, p.disabledText, p.disabledLabel, p.errorText, p.errorLabel))
                assertInk(ink, p.container, "input dark=$dark")
        }
    }

    @Test
    fun semanticPaletteIsLegibleOnPanelsInBothModes() {
        for (dark in listOf(false, true)) {
            val p = simPaletteFor(dark)
            for (panel in listOf(p.card, p.cardAlt)) {
                assertInk(p.ink, panel, "ink dark=$dark")
                assertInk(p.body, panel, "body dark=$dark")
                assertInk(p.muted, panel, "muted dark=$dark")
                assertInk(p.accent, panel, "accent dark=$dark")
                assertInk(p.success, panel, "success dark=$dark")
                assertInk(p.warning, panel, "warning dark=$dark")
                assertInk(p.danger, panel, "danger dark=$dark")
            }
            assertInk(p.infoInk, p.infoBg, "infoInk dark=$dark")
            assertInk(p.ink, p.surface, "ink on screen dark=$dark")
            assertInk(p.body, p.surface, "body on screen dark=$dark")
            // label sitting on a filled accent button
            assertInk(p.onAccent, p.accent, "onAccent dark=$dark")
            // selection tint must keep the accent visible on it
            assertTrue("accent on accentSoft dark=$dark", contrast(p.accent, p.accentSoft) >= 3.0)
        }
    }

    @Test
    fun inputPaletteFollowsTheActiveMode() {
        for (dark in listOf(false, true)) {
            val palette = inputFieldPalette(dark)
            val expected = simPaletteFor(dark)
            assertTrue("container dark=$dark", palette.container == expected.card)
            assertTrue("text dark=$dark", palette.text == expected.ink)
            assertTrue("errorLabel dark=$dark", palette.errorLabel == expected.danger)
        }
    }

    @Test
    fun textOnTintedBannersStaysReadable() {
        for (dark in listOf(false, true)) {
            val p = simPaletteFor(dark)
            for (banner in listOf(p.infoBg, p.successSoft, p.accentSoft)) {
                assertInk(p.ink, banner, "ink on tint dark=$dark")
                assertInk(p.body, banner, "body on tint dark=$dark")
                assertInk(p.muted, banner, "muted on tint dark=$dark")
            }
            assertInk(p.infoInk, p.infoBg, "infoInk dark=$dark")
            assertInk(p.success, p.successSoft, "success dark=$dark")
        }
    }

    @Test
    fun darkAndLightPalettesDiffer() {
        val light = simPaletteFor(false)
        val dark = simPaletteFor(true)
        assertTrue("card", light.card != dark.card)
        assertTrue("ink", light.ink != dark.ink)
        assertTrue("dark surface is darker", luminance(dark.surface) < luminance(light.surface))
        assertTrue("ink sits on the dark surface", luminance(dark.ink) > luminance(dark.surface))
        assertTrue("dark scheme is dark", luminance(simAppColorScheme(true).background) < 0.1)
    }
}
