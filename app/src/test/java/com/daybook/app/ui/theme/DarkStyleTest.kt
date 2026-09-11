package com.daybook.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX refinement round — Item 3 (LD1 / LD3 / D8). [DarkStyle] resolution, default fallback, and
 * the per-style WCAG contrast floors from UX_REFINEMENT_PLAN.md §3.5. Pure — no Compose runtime
 * needed beyond the [Color] value class itself.
 */
class DarkStyleTest {

    /**
     * The exact pre-refinement `DaybookColorsDark` literal, copied here (not read from
     * [DaybookColorsDark] itself, which is now DERIVED from [darkSchemeFor] — comparing against
     * it would be circular and could never fail). A later accidental drift in `GroundCharcoal` /
     * `DarkSignals` fails this test (D8).
     */
    private val historicalDark = DaybookColorScheme(
        bg = Color(0xFF0B0D0F),
        surface = Color(0xFF16181B),
        surfaceElevated = Color(0xFF1E2124),
        outline = Color(0xFF2A2D31),
        hairline = Color(0x14FFFFFF),
        border = Color(0x14FFFFFF),
        textPrimary = Color(0xFFF2F3F5),
        textMuted = Color(0xFF9AA0A6),
        textFaint = Color(0xFF6B7178),
        success = Color(0xFF4ADE80),
        warning = Color(0xFFFACC15),
        danger = Color(0xFFF87171),
        onSolid = Color(0xFF0B0D0F)
    )

    @Test fun `fromKeyOrDefault falls back to CHARCOAL for null or garbage`() {
        assertEquals(DarkStyle.CHARCOAL, DarkStyle.fromKeyOrDefault(null))
        assertEquals(DarkStyle.CHARCOAL, DarkStyle.fromKeyOrDefault("nonsense"))
        assertEquals(DarkStyle.CHARCOAL, DarkStyle.fromKeyOrDefault(""))
    }

    @Test fun `DEFAULT is CHARCOAL`() {
        assertEquals(DarkStyle.CHARCOAL, DarkStyle.DEFAULT)
    }

    @Test fun `fromKeyOrDefault resolves every storage key`() {
        DarkStyle.entries.forEach { style ->
            assertEquals(style, DarkStyle.fromKeyOrDefault(style.storageKey))
        }
    }

    @Test fun `exactly four dark styles ship (LD1 slash LD3)`() {
        assertEquals(4, DarkStyle.entries.size)
    }

    @Test fun `CHARCOAL is byte-identical to the pre-refinement DaybookColorsDark constant (D8)`() {
        assertEquals(historicalDark, darkSchemeFor(DarkStyle.CHARCOAL))
        // ...and DaybookColorsDark itself is still derived from it (Tokens.kt).
        assertEquals(historicalDark, DaybookColorsDark)
    }

    @Test fun `every dark style clears the measured contrast floors from the plan (§3_5)`() {
        DarkStyle.entries.forEach { style ->
            val scheme = darkSchemeFor(style)
            assertTrue(
                "$style textPrimary/bg below 12:1",
                contrast(scheme.textPrimary, scheme.bg) >= 12.0
            )
            assertTrue(
                "$style textMuted/bg below 4.5:1",
                contrast(scheme.textMuted, scheme.bg) >= 4.5
            )
            assertTrue(
                "$style textMuted/surfaceElevated below 4.5:1",
                contrast(scheme.textMuted, scheme.surfaceElevated) >= 4.5
            )
            assertTrue(
                "$style textFaint/bg below 3:1 (decoration floor, LD14)",
                contrast(scheme.textFaint, scheme.bg) >= 3.0
            )
        }
    }

    // -- tiny WCAG relative-luminance / contrast helpers, local to this test file --

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
