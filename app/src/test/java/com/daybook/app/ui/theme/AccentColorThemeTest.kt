package com.daybook.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX overhaul item 4 — the accent split into per-theme [AccentColor.dark] / [AccentColor.light].
 * Guards "dark values unchanged": every `.dark` must still equal the pre-overhaul single constant.
 */
class AccentColorThemeTest {

    /** The exact colours [AccentColor] carried before the overhaul (was `val color`). */
    private val historicalDark = mapOf(
        AccentColor.MINT to Color(0xFF2DD4BF),
        AccentColor.LAVENDER to Color(0xFFA78BFA),
        AccentColor.CORAL to Color(0xFFFB7185),
        AccentColor.SKY to Color(0xFF60A5FA),
        AccentColor.AMBER to Color(0xFFFBBF24)
    )

    @Test
    fun `dark values are byte-identical to the pre-overhaul constants`() {
        AccentColor.entries.forEach { accent ->
            assertEquals("dark accent changed for $accent", historicalDark[accent], accent.dark)
            assertEquals(accent.dark, accent.colorFor(dark = true))
        }
    }

    @Test
    fun `light values differ from dark`() {
        AccentColor.entries.forEach { accent ->
            assertNotEquals("light accent equals dark for $accent", accent.dark, accent.light)
            assertEquals(accent.light, accent.colorFor(dark = false))
        }
    }

    @Test
    fun `default is LAVENDER and keys are the enum names`() {
        assertTrue(AccentColor.DEFAULT == AccentColor.LAVENDER)
        AccentColor.entries.forEach { assertEquals(it.name, it.storageKey) }
        assertEquals(AccentColor.CORAL, AccentColor.fromKey("CORAL"))
        assertEquals(AccentColor.DEFAULT, AccentColor.fromKey("bogus"))
    }

    @Test
    fun `light accents keep reasonable contrast against the light surface`() {
        // The light card ground is DaybookColorsLight.surface (#FFFFFF). A saturated light accent
        // should be clearly darker than that — cheap luminance sanity check, not a full WCAG ratio.
        val surfaceLum = relLuminance(DaybookColorsLight.surface)
        AccentColor.entries.forEach { accent ->
            assertTrue(
                "light accent $accent is not darker than the light surface",
                relLuminance(accent.light) < surfaceLum - 0.15
            )
        }
    }

    private fun relLuminance(c: Color): Double {
        fun chan(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * chan(c.red) + 0.7152 * chan(c.green) + 0.0722 * chan(c.blue)
    }
}
