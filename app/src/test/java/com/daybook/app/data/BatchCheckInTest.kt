package com.daybook.app.data

import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.HabitType
import com.daybook.app.data.model.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the pure [unresolvedBatch] from v0.5.2 §2 — no Room. Pins the pre-completion suppression
 * rule (a resolved BATCH occurrence is excluded) and SD-e (empty input → empty output).
 */
class BatchCheckInTest {

    private val dayStart = 1_000_000L
    private val dayEnd = 2_000_000L
    private val inDay = 1_500_000L

    private fun habit(id: String, type: HabitType, archived: Boolean = false) =
        Habit(id = id, title = id, type = type, isArchived = archived)

    private fun occ(id: String, habitId: String, status: Occurrence.Status, at: Long = inDay) =
        HabitOccurrence(id = id, habitId = habitId, scheduledFor = at, status = status, notificationId = 1)

    @Test fun onlyBatchHabitsAreReturned() {
        val habits = listOf(habit("b", HabitType.BATCH), habit("i", HabitType.INDIVIDUAL))
        val occs = listOf(
            occ("o1", "b", Occurrence.Status.PENDING),
            occ("o2", "i", Occurrence.Status.PENDING)
        )
        assertEquals(listOf("o1"), unresolvedBatch(habits, occs, dayStart, dayEnd).map { it.id })
    }

    @Test fun resolvedBatchOccurrencesAreExcluded() {
        val habits = listOf(habit("b", HabitType.BATCH))
        val occs = listOf(
            occ("done", "b", Occurrence.Status.COMPLETED),
            occ("skip", "b", Occurrence.Status.SKIPPED),
            occ("log", "b", Occurrence.Status.LOGGED),
            occ("pend", "b", Occurrence.Status.PENDING)
        )
        assertEquals(listOf("pend"), unresolvedBatch(habits, occs, dayStart, dayEnd).map { it.id })
    }

    @Test fun archivedBatchHabitIsExcluded() {
        val habits = listOf(habit("b", HabitType.BATCH, archived = true))
        val occs = listOf(occ("o1", "b", Occurrence.Status.PENDING))
        assertTrue(unresolvedBatch(habits, occs, dayStart, dayEnd).isEmpty())
    }

    @Test fun occurrencesOutsideTheDayWindowAreExcluded() {
        val habits = listOf(habit("b", HabitType.BATCH))
        val occs = listOf(
            occ("yesterday", "b", Occurrence.Status.PENDING, dayStart - 1),
            occ("tomorrow", "b", Occurrence.Status.PENDING, dayEnd + 1),
            occ("today", "b", Occurrence.Status.PENDING, inDay)
        )
        assertEquals(listOf("today"), unresolvedBatch(habits, occs, dayStart, dayEnd).map { it.id })
    }

    @Test fun emptyInputYieldsEmptyOutput_sdE() {
        assertTrue(unresolvedBatch(emptyList(), emptyList(), dayStart, dayEnd).isEmpty())
    }
}
