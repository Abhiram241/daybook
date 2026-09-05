package com.daybook.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.5.3 item 1 — drives the pure [revertShouldRearm]. A reverted occurrence re-acquires an
 * alarm only when its slot is today or in the future; a past slot stays alarm-less.
 */
class RevertRearmTest {

    private val startOfToday: Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun pastSlot_doesNotRearm() {
        assertFalse(revertShouldRearm(startOfToday - 1, startOfToday))
        assertFalse(revertShouldRearm(startOfToday - 86_400_000L, startOfToday))
    }

    @Test fun todaySlot_rearms() {
        assertTrue(revertShouldRearm(startOfToday, startOfToday))
        assertTrue(revertShouldRearm(startOfToday + 3_600_000L, startOfToday))
    }

    @Test fun futureSlot_rearms() {
        assertTrue(revertShouldRearm(startOfToday + 5 * 86_400_000L, startOfToday))
    }
}
