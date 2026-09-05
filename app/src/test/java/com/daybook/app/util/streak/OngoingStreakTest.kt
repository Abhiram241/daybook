package com.daybook.app.util.streak

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** v0.5.5 — inclusive day-count helper for "Ongoing" habits. */
class OngoingStreakTest {

    private val utc = ZoneId.of("UTC")

    private fun millis(date: LocalDate, time: LocalTime = LocalTime.NOON, zone: ZoneId = utc): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun startedToday_isOne() {
        val today = LocalDate.of(2026, 3, 10)
        assertEquals(1, daysSince(millis(today, LocalTime.of(1, 0)), millis(today, LocalTime.of(23, 0)), utc))
    }

    @Test
    fun startedYesterday_isTwo() {
        val start = LocalDate.of(2026, 3, 9)
        val now = LocalDate.of(2026, 3, 10)
        assertEquals(2, daysSince(millis(start), millis(now), utc))
    }

    @Test
    fun startedNineDaysAgo_isTen() {
        val start = LocalDate.of(2026, 3, 1)
        val now = LocalDate.of(2026, 3, 10)
        assertEquals(10, daysSince(millis(start), millis(now), utc))
    }

    @Test
    fun nowBeforeStart_isZero() {
        val start = LocalDate.of(2026, 3, 10)
        val now = LocalDate.of(2026, 3, 8)
        assertEquals(0, daysSince(millis(start), millis(now), utc))
    }

    @Test
    fun straddlingDstChange_isWholeDayCount() {
        // US "spring forward" is 2026-03-08 in America/New_York.
        val ny = ZoneId.of("America/New_York")
        val start = millis(LocalDate.of(2026, 3, 7), LocalTime.of(9, 0), ny)
        val now = millis(LocalDate.of(2026, 3, 10), LocalTime.of(9, 0), ny)
        // 7th, 8th, 9th, 10th inclusive = 4, regardless of the lost hour.
        assertEquals(4, daysSince(start, now, ny))
    }
}
