package com.daybook.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX refinement round — Item 1 §1.3 (LD13). [onAccentInk] picks whichever of near-black /
 * white has the higher contrast against the given accent fill. Dark accents must all resolve to
 * the fixed near-black ink (byte-identical to today); light accents must each clear AA (4.5:1).
 */
class OnAccentInkTest {

    private val inkDark = Color(0xFF0B0D0F)
    private val inkLight = Color(0xFFFFFFFF)

    @Test fun `every dark accent resolves to near-black ink (unchanged from today)`() {
        AccentColor.entries.forEach { accent ->
            assertEquals(
                "dark accent $accent did not resolve to the fixed near-black ink",
                inkDark,
                onAccentInk(accent.dark)
            )
        }
    }

    @Test fun `every light accent's ink clears 4_5 to 1 against that accent`() {
        AccentColor.entries.forEach { accent ->
            val ink = onAccentInk(accent.light)
            assertTrue(
                "$accent light ink $ink is below AA (4.5:1) against ${accent.light}",
                contrast(ink, accent.light) >= 4.5
            )
        }
    }

    @Test fun `the helper picks the higher-contrast of the two candidate inks`() {
        AccentColor.entries.forEach { accent ->
            listOf(accent.dark, accent.light).forEach { fill ->
                val ink = onAccentInk(fill)
                val darkContrast = contrast(inkDark, fill)
                val lightContrast = contrast(inkLight, fill)
                val expected = if (darkContrast >= lightContrast) inkDark else inkLight
                assertEquals(expected, ink)
            }
        }
    }

    @Test fun `known light-mode flips from the plan's measured table (§1_3)`() {
        // Mint / Amber / Coral fail AA against fixed white in light mode and must flip to
        // near-black; Lavender / Sky keep white (already >= 4.5:1).
        assertEquals(inkDark, onAccentInk(AccentColor.MINT.light))
        assertEquals(inkDark, onAccentInk(AccentColor.AMBER.light))
        assertEquals(inkDark, onAccentInk(AccentColor.CORAL.light))
        assertEquals(inkLight, onAccentInk(AccentColor.LAVENDER.light))
        assertEquals(inkLight, onAccentInk(AccentColor.SKY.light))
    }

    private fun relLuminance(c: Color): Double {
        fun chan(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * chan(c.red) + 0.7152 * chan(c.green) + 0.0722 * chan(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = relLuminance(a)
        val l2 = relLuminance(b)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }
}
