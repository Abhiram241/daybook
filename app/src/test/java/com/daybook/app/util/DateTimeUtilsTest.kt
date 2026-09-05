package com.daybook.app.util

import com.daybook.app.data.model.DayOfWeek
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

class DateTimeUtilsTest {

    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")) // UTC+5:30
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `timestampToLocalDate uses the device zone, not UTC`() {
        // 2024-03-10 01:00 IST == 2024-03-09 19:30 UTC. Must report the 10th locally.
        val millis = LocalDate.of(2024, 3, 10).atTime(1, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2024, 3, 10), DateTimeUtils.timestampToLocalDate(millis))
    }

    @Test
    fun `startOfDay and endOfDay bracket the whole local day`() {
        val date = LocalDate.of(2024, 6, 1)
        val start = DateTimeUtils.startOfDay(date)
        val end = DateTimeUtils.endOfDay(date)

        assertTrue(start < end)
        assertEquals(date, Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate())
        assertEquals(date, Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate())
        // end is the last millisecond before the next day starts
        assertEquals(start + 24L * 60 * 60 * 1000 - 1, end)
    }

    @Test
    fun `times json round-trips`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(13, 30), LocalTime.of(21, 15))
        val json = DateTimeUtils.timesToJson(times)
        assertEquals("08:00,13:30,21:15", json)
        assertEquals(times, DateTimeUtils.jsonToTimes(json))
    }

    @Test
    fun `jsonToTimes tolerates blanks and whitespace`() {
        assertEquals(emptyList<LocalTime>(), DateTimeUtils.jsonToTimes(""))
        assertEquals(listOf(LocalTime.of(9, 0)), DateTimeUtils.jsonToTimes(" 09:00 , "))
    }

    @Test
    fun `jsonToTimes drops unparseable entries instead of throwing`() {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-13, Low) — was a bare .map { LocalTime.parse
        // }, so one malformed entry took down the whole times_json. Mirrors jsonToDays' existing
        // per-element-drop behavior.
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(21, 0)), DateTimeUtils.jsonToTimes("08:00,not-a-time,21:00"))
        assertEquals(emptyList<LocalTime>(), DateTimeUtils.jsonToTimes("garbage,more-garbage"))
    }

    @Test
    fun `days json round-trips and drops unknown names`() {
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertEquals(days, DateTimeUtils.jsonToDays(DateTimeUtils.daysToJson(days)))
        assertEquals(listOf(DayOfWeek.MONDAY), DateTimeUtils.jsonToDays("MONDAY,FUNDAY"))
    }

    @Test
    fun `stringToTime falls back to MIN on a bad value`() {
        assertEquals(LocalTime.of(7, 5), DateTimeUtils.stringToTime("07:05"))
        assertEquals(LocalTime.MIN, DateTimeUtils.stringToTime("not-a-time"))
    }

    @Test
    fun `isDateActive is true when no days are configured`() {
        assertTrue(DateTimeUtils.isDateActive(LocalDate.of(2024, 6, 1), ""))
    }

    // ---- rec 1: week-start + 24h clock -------------------------------------------------

    @Test
    fun `firstDayOfWeek maps the setting string, unknown falls back to Monday`() {
        assertEquals(java.time.DayOfWeek.SUNDAY, DateTimeUtils.firstDayOfWeek("SUNDAY"))
        assertEquals(java.time.DayOfWeek.SATURDAY, DateTimeUtils.firstDayOfWeek("SATURDAY"))
        assertEquals(java.time.DayOfWeek.MONDAY, DateTimeUtils.firstDayOfWeek("MONDAY"))
        assertEquals(java.time.DayOfWeek.MONDAY, DateTimeUtils.firstDayOfWeek("garbage"))
    }

    @Test
    fun `startOfWeek for a Friday under each week-start`() {
        val fri = LocalDate.of(2026, 9, 4) // a Friday
        assertEquals(LocalDate.of(2026, 8, 31), DateTimeUtils.startOfWeek(fri, "MONDAY"))
        assertEquals(LocalDate.of(2026, 8, 30), DateTimeUtils.startOfWeek(fri, "SUNDAY"))
        assertEquals(LocalDate.of(2026, 8, 29), DateTimeUtils.startOfWeek(fri, "SATURDAY"))
    }

    @Test
    fun `indexInWeek is 0 on the first weekday and 6 on the last`() {
        val sun = LocalDate.of(2026, 8, 30)
        assertEquals(0, DateTimeUtils.indexInWeek(sun, "SUNDAY"))
        assertEquals(6, DateTimeUtils.indexInWeek(sun, "MONDAY"))
        // Friday within a Saturday-start week is offset 6.
        assertEquals(6, DateTimeUtils.indexInWeek(LocalDate.of(2026, 9, 4), "SATURDAY"))
    }

    @Test
    fun `startOfWeek is idempotent and always lands on the configured first weekday`() {
        for (ws in listOf("MONDAY", "SUNDAY", "SATURDAY")) {
            var d = LocalDate.of(2026, 1, 1)
            repeat(20) {
                val s = DateTimeUtils.startOfWeek(d, ws)
                assertEquals(DateTimeUtils.firstDayOfWeek(ws), s.dayOfWeek)
                assertEquals(s, DateTimeUtils.startOfWeek(s, ws))
                d = d.plusDays(3)
            }
        }
    }

    @Test
    fun `formatTime honours the clock24h flag`() {
        val t = LocalTime.of(18, 5)
        assertEquals("18:05", DateTimeUtils.formatTime(t, true))
        assertEquals("6:05 PM", DateTimeUtils.formatTime(t, false))
        // the no-arg shim stays 12h
        assertEquals("6:05 PM", DateTimeUtils.formatTime(t))
    }
}
