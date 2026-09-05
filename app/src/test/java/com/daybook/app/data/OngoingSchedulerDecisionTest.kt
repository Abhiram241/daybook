package com.daybook.app.data

import com.daybook.app.data.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.5 — [isNoScheduleHabit] gates the STREAK early-return in
 * [OccurrenceScheduler.syncHabitInternal]. A STREAK habit must reach the cancel path (zero
 * occurrences, zero alarms, prior INDIVIDUAL/BATCH schedule torn down); INDIVIDUAL and BATCH
 * must fall through to real scheduling.
 */
class OngoingSchedulerDecisionTest {

    @Test fun streak_isNoSchedule() {
        assertTrue(isNoScheduleHabit(HabitType.STREAK))
    }

    @Test fun individual_schedules() {
        assertFalse(isNoScheduleHabit(HabitType.INDIVIDUAL))
    }

    @Test fun batch_schedules() {
        assertFalse(isNoScheduleHabit(HabitType.BATCH))
    }

    /** Journal-as-habit round (B4): JOURNAL schedules exactly like INDIVIDUAL — own per-time
     *  alarms, not a no-schedule type like STREAK. */
    @Test fun journal_schedules() {
        assertFalse(isNoScheduleHabit(HabitType.JOURNAL))
    }

    /** Only STREAK is a no-schedule type — guards against a future enum addition. */
    @Test fun onlyStreakIsNoSchedule() {
        assertEquals(listOf(HabitType.STREAK), HabitType.entries.filter { isNoScheduleHabit(it) })
    }
}
