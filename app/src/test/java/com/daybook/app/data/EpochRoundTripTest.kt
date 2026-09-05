package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.5.3 Phase 3 (S17) — documents the one residual sharp edge. The reconstructed occurrence PK is
 * `"$itemId:$millis"` where `millis` is `epochOfLocal(date, hhmm, <current zone>)`. Restore a backup
 * made in zone A onto a device in zone B and a boundary row's PK shifts by the zone offset.
 *
 * The **sync** path is immune: `importMonth`'s merge and the `deletePendingByLocalMonth*` clears
 * match / clear by the stored `local_date`, not the PK. The **file** import path still recomputes —
 * making it zone-stable needs a v2-wire `scheduledForMillis` (deferred to Phase 6).
 */
class EpochRoundTripTest {

    private val date = LocalDate.of(2026, 1, 15)
    private val kolkata = ZoneId.of("Asia/Kolkata")     // UTC+5:30, no DST
    private val chicago = ZoneId.of("America/Chicago")   // UTC-6 in January

    @Test fun `a boundary local time maps to different millis in different zones`() {
        val a = epochOfLocal(date, "00:30", kolkata)!!
        val b = epochOfLocal(date, "00:30", chicago)!!
        assertTrue("the two zones must not agree for 00:30", a != b)

        // The delta is exactly the offset difference: +5:30 vs -6:00 = 11.5 hours.
        val elevenAndAHalfHours = (11L * 60 + 30) * 60_000L
        assertEquals(elevenAndAHalfHours, b - a)
    }

    @Test fun `the same zone is deterministic`() {
        assertEquals(epochOfLocal(date, "09:00", kolkata), epochOfLocal(date, "09:00", kolkata))
    }

    @Test fun `an unparseable time yields null rather than throwing`() {
        assertNull(epochOfLocal(date, "9am", kolkata))
        assertNull(epochOfLocal(date, "", kolkata))
    }

    @Test fun `a valid time with surrounding whitespace still parses`() {
        assertEquals(epochOfLocal(date, "09:00", kolkata), epochOfLocal(date, "  09:00  ", kolkata))
    }
}
