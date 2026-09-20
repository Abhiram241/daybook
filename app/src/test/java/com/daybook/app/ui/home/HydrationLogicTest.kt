package com.daybook.app.ui.home

import com.daybook.app.data.hydration.HydrationUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hydration-as-Intake round (2026-09-20) — the synthetic-HomeItem machinery this test file used
 * to cover (`hydrationActiveOn`/`hydrationHomeItem`/`HydrationUi`/`hydrationStreakOccurrences`)
 * was deliberately deleted from `HomeViewModel.kt` per SOURCE_PRIORITY_AND_HYDRATION_PLAN's §2.3
 * design decision: Hydration is now a real `FoodMedTask`/`FoodMedOccurrence` flowing through the
 * same `OccurrenceScheduler` pipeline as every other Intake item, not a synthetic Today-only
 * construct. Its scheduling/streak logic is exercised by that shared pipeline's own tests, not a
 * hydration-specific one. Only the still-live [HydrationUnits] parsing/formatting logic remains
 * covered here.
 */
class HydrationLogicTest {

    @Test fun `ml and L inputs convert to whole ml`() {
        assertEquals(1500, HydrationUnits.toMl("1.5", HydrationUnits.L))
        assertEquals(1500, HydrationUnits.toMl("1,5", HydrationUnits.L))
        assertEquals(250, HydrationUnits.toMl("250", HydrationUnits.ML))
        assertNull(HydrationUnits.toMl("", HydrationUnits.ML))
        assertNull(HydrationUnits.toMl("-1", HydrationUnits.L))
        assertNull(HydrationUnits.toMl("abc", HydrationUnits.L))
    }

    @Test fun `formats in the chosen unit`() {
        assertEquals("1.25 L", HydrationUnits.format(1250, HydrationUnits.L))
        assertEquals("2 L", HydrationUnits.format(2000, HydrationUnits.L))
        assertEquals("1250 ml", HydrationUnits.format(1250, HydrationUnits.ML))
    }
}
