package com.daybook.app.data.health

import com.daybook.app.data.model.HealthDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSyncLogicTest {

    private fun day(
        localDate: String = "2026-09-01",
        steps: Int? = null,
        avgHeartRate: Int? = null,
        weightKg: Float? = null,
        updatedAt: Long = 1L
    ) = HealthDay(localDate = localDate, steps = steps, avgHeartRate = avgHeartRate, weightKg = weightKg, updatedAt = updatedAt)

    // ------------------------------------------------------------------ isEmptyHealthDay

    @Test fun isEmptyHealthDay_trueWhenEveryMetricNull() {
        assertTrue(isEmptyHealthDay(day()))
    }

    @Test fun isEmptyHealthDay_falseWhenOneMetricPresent() {
        assertFalse(isEmptyHealthDay(day(steps = 0)))
    }

    // ------------------------------------------------------------------ mergeHealthDay (D-2)

    @Test fun mergeHealthDay_noExisting_returnsIncomingUnchanged() {
        val incoming = day(steps = 5000)
        assertEquals(incoming, mergeHealthDay(null, incoming))
    }

    @Test fun mergeHealthDay_incomingNullField_keepsExistingValue() {
        val existing = day(steps = 5000, avgHeartRate = 70, updatedAt = 1L)
        val incoming = day(steps = null, avgHeartRate = null, updatedAt = 2L)
        val merged = mergeHealthDay(existing, incoming)
        assertEquals(5000, merged.steps)
        assertEquals(70, merged.avgHeartRate)
        // The read still counts as "now" even though every field was preserved.
        assertEquals(2L, merged.updatedAt)
    }

    @Test fun mergeHealthDay_incomingRealValue_alwaysWins_evenALegitimateZero() {
        val existing = day(steps = 5000)
        val incoming = day(steps = 0)
        assertEquals(0, mergeHealthDay(existing, incoming).steps)
    }

    @Test fun mergeHealthDay_partialOverlap_mergesFieldByField() {
        val existing = day(steps = 5000, avgHeartRate = 70, weightKg = 80f)
        // Heart rate permission revoked: this read only comes back with steps.
        val incoming = day(steps = 6000, avgHeartRate = null, weightKg = null)
        val merged = mergeHealthDay(existing, incoming)
        assertEquals(6000, merged.steps)
        assertEquals(70, merged.avgHeartRate)
        assertEquals(80f, merged.weightKg)
    }

    // ------------------------------------------------------------------ sameHealthDayPayload (B-3)

    @Test fun sameHealthDayPayload_trueWhenOnlyUpdatedAtDiffers() {
        val a = day(steps = 5000, updatedAt = 1L)
        val b = day(steps = 5000, updatedAt = 2L)
        assertTrue(sameHealthDayPayload(a, b))
    }

    @Test fun sameHealthDayPayload_falseWhenAMetricDiffers() {
        val a = day(steps = 5000, updatedAt = 1L)
        val b = day(steps = 5001, updatedAt = 1L)
        assertFalse(sameHealthDayPayload(a, b))
    }

    // ------------------------------------------------------------------ automaticWindowFloor (B-1)

    private val today = LocalDate.of(2026, 9, 20)

    @Test fun automaticWindowFloor_noBackfill_defaultsTo30DayWindow() {
        assertEquals(today.minusDays(29), automaticWindowFloor(today, backfillThrough = null, hasHistoryAccess = true))
    }

    @Test fun automaticWindowFloor_deepBackfill_isCappedAtCapDays() {
        // A year-deep import happened; an automatic pull must never re-read that whole year.
        val yearAgo = today.minusDays(364)
        val floor = automaticWindowFloor(today, backfillThrough = yearAgo, hasHistoryAccess = true, capDays = 90L)
        assertEquals(today.minusDays(89), floor)
    }

    @Test fun automaticWindowFloor_shallowBackfill_withinCap_usesBackfillDepth() {
        // A backfill shallower than the cap is still honoured in full (item 5's original intent).
        val shallow = today.minusDays(45)
        val floor = automaticWindowFloor(today, backfillThrough = shallow, hasHistoryAccess = true, capDays = 90L)
        assertEquals(shallow, floor)
    }

    @Test fun automaticWindowFloor_noHistoryAccess_ignoresBackfillEntirely() {
        // Without READ_HEALTH_DATA_HISTORY, Health Connect silently caps reads at ~30 days anyway —
        // asking for the deeper backfill window would just burn calls on empty days.
        val yearAgo = today.minusDays(364)
        val floor = automaticWindowFloor(today, backfillThrough = yearAgo, hasHistoryAccess = false, capDays = 90L)
        assertEquals(today.minusDays(29), floor)
    }
}
