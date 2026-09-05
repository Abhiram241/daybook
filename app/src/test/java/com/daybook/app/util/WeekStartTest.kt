package com.daybook.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * rec 1 (C1) — pure week-start maths. Pins the exact dates the build plan (P2) enumerates so a
 * regression in [DateTimeUtils.startOfWeek] / [DateTimeUtils.indexInWeek] is loud.
 */
class WeekStartTest {

    private val friday = LocalDate.of(2026, 9, 4) // 2026-09-04 is a Friday

    @Test
    fun `sunday start`() {
        assertEquals(LocalDate.of(2026, 8, 30), DateTimeUtils.startOfWeek(friday, "SUNDAY"))
    }

    @Test
    fun `monday start`() {
        assertEquals(LocalDate.of(2026, 8, 31), DateTimeUtils.startOfWeek(friday, "MONDAY"))
    }

    @Test
    fun `saturday start`() {
        assertEquals(LocalDate.of(2026, 8, 29), DateTimeUtils.startOfWeek(friday, "SATURDAY"))
    }

    @Test
    fun `week is seven contiguous days from the configured start`() {
        for (ws in listOf("MONDAY", "SUNDAY", "SATURDAY")) {
            val start = DateTimeUtils.startOfWeek(friday, ws)
            (0..6).forEach { off ->
                assertEquals(off, DateTimeUtils.indexInWeek(start.plusDays(off.toLong()), ws))
            }
        }
    }
}
