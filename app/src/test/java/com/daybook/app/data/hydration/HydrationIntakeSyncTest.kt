package com.daybook.app.data.hydration

import com.daybook.app.data.hydration.HydrationIntakeSync.Companion.hydrationTimesJson
import com.daybook.app.util.DateTimeUtils
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 56 regression — the Hydration reminder never appeared on Today (builds 53-55) because
 * `HydrationIntakeSync` wrote `times_json` as a JSON-array literal (`["21:00"]`) while the single
 * reader, [DateTimeUtils.jsonToTimes], expects the comma-separated bare-"HH:mm" shape every other
 * writer produces. The malformed value parsed to ZERO times, so `OccurrenceScheduler.slots()`
 * produced no slots and no occurrence row was ever created — silently, with no crash.
 *
 * These tests pin the round-trip: whatever `hydrationTimesJson` writes MUST parse back to exactly
 * the requested time through the same reader the scheduler uses.
 */
class HydrationIntakeSyncTest {

    @Test fun `written times_json round-trips through the reader the scheduler uses`() {
        val json = hydrationTimesJson("21:00")
        assertEquals(listOf(LocalTime.of(21, 0)), DateTimeUtils.jsonToTimes(json))
    }

    @Test fun `written times_json matches what every other writer produces`() {
        assertEquals(DateTimeUtils.timesToJson(listOf(LocalTime.of(7, 30))), hydrationTimesJson("07:30"))
    }

    @Test fun `the old JSON-array shape is exactly what broke it`() {
        // Guards against anyone "restoring" the JSON literal: it parses to nothing.
        assertEquals(emptyList<LocalTime>(), DateTimeUtils.jsonToTimes("[\"21:00\"]"))
    }

    @Test fun `an unparseable time falls back to 9pm rather than yielding no slots`() {
        // A blank/garbage value must never produce an empty times_json, which would silently
        // reproduce the original bug (no slots, no occurrences, nothing on Today).
        listOf("", "   ", "not-a-time", "9pm").forEach { bad ->
            assertEquals(
                "input=$bad", listOf(LocalTime.of(21, 0)), DateTimeUtils.jsonToTimes(hydrationTimesJson(bad))
            )
        }
    }
}
