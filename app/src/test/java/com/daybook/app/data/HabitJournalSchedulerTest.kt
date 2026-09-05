package com.daybook.app.data

import com.daybook.app.data.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journal-as-habit round (B4) — [armsOwnAlarm]: JOURNAL arms its own "next" alarm exactly like
 * INDIVIDUAL (`OccurrenceScheduler.syncHabitInternal`'s post-sync arm call); BATCH is surfaced only
 * by the single app-wide check-in alarm and STREAK arms nothing at all.
 */
class HabitJournalSchedulerTest {

    @Test fun individual_armsOwnAlarm() {
        assertTrue(armsOwnAlarm(HabitType.INDIVIDUAL))
    }

    @Test fun journal_armsOwnAlarmExactlyLikeIndividual() {
        assertTrue(armsOwnAlarm(HabitType.JOURNAL))
    }

    @Test fun batch_doesNotArmOwnAlarm() {
        assertFalse(armsOwnAlarm(HabitType.BATCH))
    }

    @Test fun streak_doesNotArmOwnAlarm() {
        assertFalse(armsOwnAlarm(HabitType.STREAK))
    }

    /** Exactly INDIVIDUAL and JOURNAL arm their own alarm — guards against a future enum addition. */
    @Test fun onlyIndividualAndJournalArmOwnAlarm() {
        assertEquals(
            setOf(HabitType.INDIVIDUAL, HabitType.JOURNAL),
            HabitType.entries.filter { armsOwnAlarm(it) }.toSet()
        )
    }
}
