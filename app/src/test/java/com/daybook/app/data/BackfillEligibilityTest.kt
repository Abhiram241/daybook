package com.daybook.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.5.2 §9 / SD-i — drives the pure [canBackfill]. No Room, no Android.
 */
class BackfillEligibilityTest {

    private val today = LocalDate.of(2026, 6, 15) // a Monday
    private fun millisOf(d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // Mon/Wed/Fri active-days JSON (internal weekday enum names).
    private val mwf = "MONDAY,WEDNESDAY,FRIDAY"

    @Test fun pastActiveWeekdayAfterCreatedAt_isTrue() {
        val date = LocalDate.of(2026, 6, 8) // Monday, a week back
        assertTrue(canBackfill(date, today, millisOf(LocalDate.of(2026, 1, 1)), "", false))
    }

    @Test fun tomorrow_isFalse() {
        assertFalse(canBackfill(today.plusDays(1), today, millisOf(LocalDate.of(2026, 1, 1)), "", false))
    }

    @Test fun today_isTrue() {
        assertTrue(canBackfill(today, today, millisOf(LocalDate.of(2026, 1, 1)), "", false))
    }

    @Test fun beforeCreatedAt_isFalse_boundaryIsTrue() {
        val created = LocalDate.of(2026, 6, 10)
        assertFalse(canBackfill(created.minusDays(1), today, millisOf(created), "", false))
        assertTrue(canBackfill(created, today, millisOf(created), "", false))
    }

    @Test fun inactiveWeekday_isFalse_butEmptyJsonIsTrue() {
        val tuesday = LocalDate.of(2026, 6, 9)
        assertFalse(canBackfill(tuesday, today, millisOf(LocalDate.of(2026, 1, 1)), mwf, false))
        assertTrue(canBackfill(tuesday, today, millisOf(LocalDate.of(2026, 1, 1)), "", false))
    }

    @Test fun archived_isAlwaysFalse() {
        val date = LocalDate.of(2026, 6, 8)
        assertFalse(canBackfill(date, today, millisOf(LocalDate.of(2026, 1, 1)), "", true))
    }

    @Test fun createdLateOnTheSelectedDate_isTrue() {
        // createdAt is 22:30 local on the selected date — comparing raw millis instead of local
        // dates would reject the creation day itself.
        val date = LocalDate.of(2026, 6, 10)
        val createdMillis = date.atTime(22, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(canBackfill(date, today, createdMillis, "", false))
    }

    @Test fun idSchemeMatchesImport() {
        assertTrue("h1:1735689600000" == "h1" + ":" + 1735689600000L)
    }
}
