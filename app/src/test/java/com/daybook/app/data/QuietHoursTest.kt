package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * rec 5 (N1 / SD-3) — quiet hours "defer to window end, never drop". Uses a fixed [ZoneId] so the
 * assertions are DST-safe and machine-independent.
 */
class QuietHoursTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata") // UTC+5:30, no DST

    private fun at(date: LocalDate, h: Int, m: Int): Long =
        date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun defer(trigger: Long, enabled: Boolean, start: String, end: String) =
        deferIfInsideQuietHours(trigger, enabled, start, end, zone)

    private val day = LocalDate.of(2026, 6, 15)

    @Test
    fun `disabled is the identity function`() {
        val t = at(day, 2, 0)
        assertEquals(t, defer(t, enabled = false, "22:00", "07:00"))
    }

    // ---- non-wrapping window 13:00-14:00 ----------------------------------------------

    @Test
    fun `non-wrap inside defers to the window end same day`() {
        val t = at(day, 13, 30)
        assertEquals(at(day, 14, 0), defer(t, true, "13:00", "14:00"))
    }

    @Test
    fun `non-wrap before the window is unchanged`() {
        val t = at(day, 12, 0)
        assertEquals(t, defer(t, true, "13:00", "14:00"))
    }

    @Test
    fun `non-wrap exactly at the exclusive end is unchanged`() {
        val t = at(day, 14, 0)
        assertEquals(t, defer(t, true, "13:00", "14:00"))
    }

    // ---- wrapping window 22:00-07:00 -------------------------------------------------

    @Test
    fun `wrap late evening defers to 07 00 next day`() {
        val t = at(day, 23, 0)
        assertEquals(at(day.plusDays(1), 7, 0), defer(t, true, "22:00", "07:00"))
    }

    @Test
    fun `wrap early morning defers to 07 00 same day`() {
        val t = at(day, 2, 0)
        assertEquals(at(day, 7, 0), defer(t, true, "22:00", "07:00"))
    }

    @Test
    fun `wrap one minute before the window is unchanged`() {
        val t = at(day, 21, 59)
        assertEquals(t, defer(t, true, "22:00", "07:00"))
    }

    @Test
    fun `wrap exactly at the exclusive end is unchanged`() {
        val t = at(day, 7, 0)
        assertEquals(t, defer(t, true, "22:00", "07:00"))
    }

    @Test
    fun `wrap start boundary is inside and defers`() {
        val t = at(day, 22, 0)
        assertEquals(at(day.plusDays(1), 7, 0), defer(t, true, "22:00", "07:00"))
    }

    // ---- overdue catch-up trigger --------------------------------------------------

    @Test
    fun `an overdue trigger inside the window is still deferred to the window end`() {
        // "now + 5s" style: 02:00:05 is inside 22:00-07:00 → held to 07:00.
        val t = at(day, 2, 0) + 5_000L
        assertEquals(at(day, 7, 0), defer(t, true, "22:00", "07:00"))
    }

    // ---- degenerate window -------------------------------------------------------

    @Test
    fun `zero-width window (start == end) is the identity function`() {
        val t = at(day, 3, 0)
        assertEquals(t, defer(t, true, "07:00", "07:00"))
    }

    @Test
    fun `unparseable times fall back to 22 00 - 07 00`() {
        val t = at(day, 23, 30)
        assertEquals(at(day.plusDays(1), 7, 0), defer(t, true, "nonsense", "also-bad"))
    }
}
