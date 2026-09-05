package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * v0.5.3 Phase 2 (S17): [exportDateFor] is what the exporter buckets a stored occurrence's
 * `DayEntry.date` off. The load-bearing guarantee: a persisted `local_date` wins over a
 * recompute, so history does not re-bucket when the device zone changes ("the travel case").
 */
class ExportLocalDateTest {

    // 2026-01-15 09:30 UTC. In Pacific/Kiritimati (UTC+14) this instant is already 2026-01-15
    // late evening; in Pacific/Honolulu (UTC-10) it is still 2026-01-14 — so the zone genuinely
    // decides the day for this timestamp.
    private val instant = 1_768_469_400_000L

    @Test fun `a non-null local_date wins even when the zone would map the timestamp to another day`() {
        assertEquals(
            "2026-01-15",
            exportDateFor("2026-01-15", instant, ZoneId.of("Pacific/Honolulu"))
        )
        assertEquals(
            "2026-01-15",
            exportDateFor("2026-01-15", instant, ZoneId.of("Pacific/Kiritimati"))
        )
    }

    @Test fun `a null local_date falls back to the recompute in the given zone`() {
        assertEquals(
            "2026-01-14",
            exportDateFor(null, instant, ZoneId.of("Pacific/Honolulu"))
        )
        assertEquals(
            "2026-01-15",
            exportDateFor(null, instant, ZoneId.of("Pacific/Kiritimati"))
        )
    }

    @Test fun `a blank local_date is treated as absent`() {
        assertEquals(
            "2026-01-15",
            exportDateFor("", instant, ZoneId.of("Pacific/Kiritimati"))
        )
    }

    @Test fun `the recompute is a padded ISO yyyy-MM-dd string`() {
        // 2026-03-04 12:00 UTC -> single-digit month and day must be zero-padded.
        val march4 = java.time.Instant.parse("2026-03-04T12:00:00Z").toEpochMilli()
        assertEquals("2026-03-04", exportDateFor(null, march4, ZoneId.of("UTC")))
    }
}
