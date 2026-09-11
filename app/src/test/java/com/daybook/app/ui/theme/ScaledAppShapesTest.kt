package com.daybook.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX refinement round — Item 4. [scaledAppShapes] / [scaledM3Shapes] / [clampCornerScale] are
 * pure, so this is plain JUnit4 (no Robolectric / Compose runtime needed to construct
 * [RoundedCornerShape] values or compare them for equality — they're plain data classes).
 */
class ScaledAppShapesTest {

    @Test fun `scale 1 reproduces today's exact radii`() {
        val s = scaledAppShapes(1f)
        assertEquals(RoundedCornerShape(14.dp), s.card)
        assertEquals(RoundedCornerShape(12.dp), s.button)
        assertEquals(RoundedCornerShape(10.dp), s.field)
        assertEquals(RoundedCornerShape(10.dp), s.pill)
        assertEquals(RoundedCornerShape(12.dp), s.tile)
        assertEquals(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp), s.sheet)
        assertEquals(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp), s.nav)
        assertEquals(RoundedCornerShape(16.dp), s.dialog)
    }

    @Test fun `scale 0 is perfectly square for every scalable token`() {
        val s = scaledAppShapes(0f)
        assertEquals(RoundedCornerShape(0.dp), s.card)
        assertEquals(RoundedCornerShape(0.dp), s.button)
        assertEquals(RoundedCornerShape(0.dp), s.field)
        assertEquals(RoundedCornerShape(0.dp), s.pill)
        assertEquals(RoundedCornerShape(0.dp), s.tile)
        assertEquals(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp), s.sheet)
        assertEquals(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp), s.nav)
        assertEquals(RoundedCornerShape(0.dp), s.dialog)
    }

    @Test fun `scale 1_75 is the max end of the range`() {
        val s = scaledAppShapes(1.75f)
        assertEquals(RoundedCornerShape(24.5.dp), s.card)
        assertEquals(RoundedCornerShape(28.dp), s.dialog)
    }

    @Test fun `card radius is monotonic across 0 to 1 to 1_75`() {
        val r0 = scaledAppShapes(0f).card
        val r1 = scaledAppShapes(1f).card
        val r175 = scaledAppShapes(1.75f).card
        // RoundedCornerShape doesn't expose the raw Dp publicly for comparison, so compare via
        // the same construction the production code uses.
        assertEquals(RoundedCornerShape(0.dp), r0)
        assertEquals(RoundedCornerShape(14.dp), r1)
        assertEquals(RoundedCornerShape(24.5.dp), r175)
    }

    @Test fun `segmented and navPill are fixed at every scale (LD11)`() {
        listOf(0f, 1f, 1.75f).forEach { scale ->
            val s = scaledAppShapes(scale)
            assertEquals(RoundedCornerShape(50), s.segmented)
            assertEquals(RoundedCornerShape(28.dp), s.navPill)
        }
    }

    @Test fun `clampCornerScale bounds the range`() {
        assertEquals(0f, clampCornerScale(-1f))
        assertEquals(1.75f, clampCornerScale(9f))
        assertEquals(1f, clampCornerScale(1f))
    }

    @Test fun `scaledM3Shapes medium matches scaledAppShapes card`() {
        assertEquals(scaledAppShapes(1f).card, scaledM3Shapes(1f).medium)
    }

    @Test fun `default constant is 1_0`() {
        assertTrue(DEFAULT_CORNER_SCALE == 1f)
    }
}
