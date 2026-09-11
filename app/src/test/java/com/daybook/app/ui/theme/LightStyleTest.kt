package com.daybook.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX refinement round — Item 3 (LD2 / LD3 / D8). [LightStyle] resolution, default fallback, and
 * the per-style WCAG contrast floors from UX_REFINEMENT_PLAN.md §3.5. Pure — no Compose runtime
 * needed beyond the [Color] value class itself.
 */
class LightStyleTest {

    /**
     * The exact pre-refinement `DaybookColorsLight` literal, copied here (not read from
     * [DaybookColorsLight] itself, which is now DERIVED from [lightSchemeFor] — comparing
     * against it would be circular and could never fail). A later accidental drift in
     * `GroundPaper` / `LightSignals` fails this test (D8).
     */
    private val historicalLight = DaybookColorScheme(
        bg = Color(0xFFFBFBF9),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFF2F2EF),
        outline = Color(0xFFE2E2DE),
        hairline = Color(0x14000000),
        border = Color(0x14000000),
        textPrimary = Color(0xFF1B1D20),
        textMuted = Color(0xFF5B6068),
        textFaint = Color(0xFF8A9099),
        success = Color(0xFF15803D),
        warning = Color(0xFFB45309),
        danger = Color(0xFFDC2626),
        onSolid = Color(0xFFFFFFFF)
    )

    @Test fun `fromKeyOrDefault falls back to PAPER for null or garbage`() {
        assertEquals(LightStyle.PAPER, LightStyle.fromKeyOrDefault(null))
        assertEquals(LightStyle.PAPER, LightStyle.fromKeyOrDefault("nonsense"))
        assertEquals(LightStyle.PAPER, LightStyle.fromKeyOrDefault(""))
    }

    @Test fun `DEFAULT is PAPER`() {
        assertEquals(LightStyle.PAPER, LightStyle.DEFAULT)
    }

    @Test fun `fromKeyOrDefault resolves every storage key`() {
        LightStyle.entries.forEach { style ->
            assertEquals(style, LightStyle.fromKeyOrDefault(style.storageKey))
        }
    }

    @Test fun `exactly four light styles ship (LD2 slash LD3)`() {
        assertEquals(4, LightStyle.entries.size)
    }

    @Test fun `PAPER is byte-identical to the pre-refinement DaybookColorsLight constant (D8)`() {
        assertEquals(historicalLight, lightSchemeFor(LightStyle.PAPER))
        // ...and DaybookColorsLight itself is still derived from it (Tokens.kt).
        assertEquals(historicalLight, DaybookColorsLight)
    }

    @Test fun `every light style clears the measured contrast floors from the plan (§3_5)`() {
        LightStyle.entries.forEach { style ->
            val scheme = lightSchemeFor(style)
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
