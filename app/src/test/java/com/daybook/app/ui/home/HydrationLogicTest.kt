package com.daybook.app.ui.home

import com.daybook.app.data.hydration.HydrationUnits
import com.daybook.app.data.model.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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

    @Test fun `habit is active from its enable date up to today only`() {
        val today = LocalDate.of(2026, 9, 14)
        assertEquals(false, hydrationActiveOn(today, today, enabled = false, enabledSince = "2026-09-01"))
        assertEquals(true, hydrationActiveOn(today, today, enabled = true, enabledSince = "2026-09-01"))
        assertEquals(false, hydrationActiveOn(LocalDate.of(2026, 8, 31), today, true, "2026-09-01"))
        assertEquals(false, hydrationActiveOn(today.plusDays(1), today, true, "2026-09-01"))
        assertEquals(true, hydrationActiveOn(today, today, true, ""))
    }

    @Test fun `status label is Done at goal, Missed on a past day below goal`() {
        val today = LocalDate.of(2026, 9, 14)
        assertEquals("Done", hydrationHomeItem(HydrationUi(today, 2000, 2000, "L"), today).statusLabel)
        assertNull(hydrationHomeItem(HydrationUi(today, 500, 2000, "L"), today).statusLabel)
        assertEquals(MISSED_LABEL, hydrationHomeItem(HydrationUi(today.minusDays(1), 500, 2000, "L"), today).statusLabel)
    }

    @Test fun `streak occurrences complete only on goal days`() {
        val occs = hydrationStreakOccurrences(
            LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 14),
            mapOf("2026-09-12" to 2100, "2026-09-13" to 900), goalMl = 2000
        )
        assertEquals(listOf(Occurrence.Status.COMPLETED, Occurrence.Status.PENDING, Occurrence.Status.PENDING), occs.map { it.status })
        assertEquals(listOf("2026-09-12", "2026-09-13", "2026-09-14"), occs.map { it.localDate })
    }
}
