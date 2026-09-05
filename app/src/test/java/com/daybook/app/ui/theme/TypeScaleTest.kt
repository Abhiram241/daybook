package com.daybook.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * v0.5.3 Phase 0 (§2.2 / backlog #1). Pure JVM — `daybookTypography` builds plain
 * [androidx.compose.ui.text.TextStyle] holders, no font file is loaded.
 *
 * Guards forever: the four slots the pre-0.5.3 scale left on Material defaults
 * (`displaySmall`, `headlineLarge`, `headlineSmall`, `titleSmall`) are now defined, on the
 * chosen [FontChoice]'s display family, at the spec size/weight.
 */
class TypeScaleTest {

    @Test
    fun `all four previously-missing slots are defined on the display family for every FontChoice`() {
        for (choice in FontChoice.entries) {
            val t = daybookTypography(choice)
            val displayRef = t.displayMedium.fontFamily
            assertNotNull("$choice displayMedium has no family", displayRef)

            for ((name, style) in listOf(
                "displaySmall" to t.displaySmall,
                "headlineLarge" to t.headlineLarge,
                "headlineSmall" to t.headlineSmall,
                "titleSmall" to t.titleSmall,
            )) {
                assertNotNull("$choice $name has no fontFamily", style.fontFamily)
                assertSame(
                    "$choice $name must use the display family, not a Material default",
                    displayRef, style.fontFamily
                )
            }
        }
    }

    @Test
    fun `bundled-face choices put a non-default family on the new slots`() {
        // GROTESK/LITERATA/NUNITO/MONO all carry a real bundled family on display; only SYSTEM
        // is FontFamily.Default.
        for (choice in listOf(FontChoice.GROTESK, FontChoice.LITERATA, FontChoice.NUNITO, FontChoice.MONO)) {
            val t = daybookTypography(choice)
            assertNotSame(
                "$choice headlineSmall should not fall back to FontFamily.Default",
                FontFamily.Default, t.headlineSmall.fontFamily
            )
        }
        assertSame(FontFamily.Default, daybookTypography(FontChoice.SYSTEM).headlineSmall.fontFamily)
    }

    @Test
    fun `the new slots match the Phase 0 spec size and weight`() {
        // Family swaps per choice but size/weight are identical across the scale.
        val t = daybookTypography(FontChoice.GROTESK)

        assertEquals(28.sp, t.displaySmall.fontSize)
        assertEquals(FontWeight.Bold, t.displaySmall.fontWeight)

        assertEquals(30.sp, t.headlineLarge.fontSize)
        assertEquals(FontWeight.Bold, t.headlineLarge.fontWeight)

        assertEquals(22.sp, t.headlineSmall.fontSize)
        assertEquals(FontWeight.Bold, t.headlineSmall.fontWeight)

        assertEquals(14.sp, t.titleSmall.fontSize)
        assertEquals(FontWeight.SemiBold, t.titleSmall.fontWeight)
    }
}
