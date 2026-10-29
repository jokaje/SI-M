package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class InputFieldPaletteTest {
    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val x = channel.toDouble()
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
    @Test fun `input on fixed white cards remains readable in both system themes`() {
        for (dark in listOf(false, true)) {
            val palette = inputFieldPalette(dark)
            for (ink in listOf(palette.text, palette.label, palette.placeholder, palette.icon,
                palette.disabledText, palette.disabledLabel, palette.errorText, palette.errorLabel)) {
                assertTrue("ink contrast $ink dark=$dark", ratio(ink, palette.container) >= 4.5)
            }
            assertTrue(ratio(palette.cursor, palette.container) >= 3.0)
            assertTrue(ratio(palette.outline, palette.container) >= 3.0)
        }
    }
}
