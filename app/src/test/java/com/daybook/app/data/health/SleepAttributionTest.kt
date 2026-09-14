package com.daybook.app.data.health

import com.daybook.app.data.model.HealthDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** User request — a night crossing midnight shows on both dates; totals follow SleepCountDay. */
class SleepAttributionTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    /** A row for the wake-up [date] whose sleep started at [bed]. */
    private fun night(date: String, bed: Long, wake: Long, minutes: Int) =
        HealthDay(localDate = date, sleepMinutes = minutes, sleepStartMillis = bed, sleepEndMillis = wake, updatedAt = 0L)

    private val sep14 = night("2026-09-14", millis(2026, 9, 13, 23), millis(2026, 9, 14, 7), 480)
    private val sep15 = night("2026-09-15", millis(2026, 9, 14, 23, 30), millis(2026, 9, 15, 6, 30), 420)

    @Test fun `night 13-14 is listed on the 13th with its span label`() {
        val entries = sleepEntriesForDay(LocalDate.of(2026, 9, 13), day = null, nextDay = sep14, zoneId = zone)
        assertEquals(1, entries.size)
        assertEquals(true, entries[0].label.startsWith("13–14 "))
    }

    @Test fun `the 14th lists 13-14 first then 14-15`() {
        val entries = sleepEntriesForDay(LocalDate.of(2026, 9, 14), day = sep14, nextDay = sep15, zoneId = zone)
        assertEquals(2, entries.size)
        assertEquals(sep14, entries[0].row)
        assertEquals(sep15, entries[1].row)
        assertEquals(true, entries[0].label.startsWith("13–14"))
        assertEquals(true, entries[1].label.startsWith("14–15"))
    }

    @Test fun `a sleep that started after midnight is only on its own date`() {
        val early = night("2026-09-15", millis(2026, 9, 15, 1), millis(2026, 9, 15, 8), 420)
        assertEquals(0, sleepEntriesForDay(LocalDate.of(2026, 9, 14), null, early, zone).size)
        val onDay = sleepEntriesForDay(LocalDate.of(2026, 9, 15), early, null, zone)
        assertEquals(1, onDay.size)
        assertEquals(false, onDay[0].label.contains("–"))
    }

    @Test fun `range totals follow the counting setting`() {
        val rows = listOf(sep14, sep15)
        val d13 = LocalDate.of(2026, 9, 13)
        val d14 = LocalDate.of(2026, 9, 14)
        // Only the 14th in range.
        assertEquals(listOf(sep14), sleepRowsCountedInRange(rows, d14, d14, SleepCountDay.WAKE, zone))
        assertEquals(listOf(sep15), sleepRowsCountedInRange(rows, d14, d14, SleepCountDay.BED, zone))
        assertEquals(listOf(sep14, sep15), sleepRowsCountedInRange(rows, d14, d14, SleepCountDay.BOTH, zone))
        // 13th..14th: wake-up counts only sep14; both-days counts sep14 twice + sep15 once.
        assertEquals(listOf(sep14), sleepRowsCountedInRange(rows, d13, d14, SleepCountDay.WAKE, zone))
        assertEquals(3, sleepRowsCountedInRange(rows, d13, d14, SleepCountDay.BOTH, zone).size)
    }
}
