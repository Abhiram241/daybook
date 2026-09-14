package com.daybook.app.data

import com.daybook.app.data.model.AiExclusion
import com.daybook.app.data.model.HabitType
import com.daybook.app.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §5 — pure, no I/O. */
class AiExclusionSetTest {

    private fun todo(id: String, habitId: String) = TodoEntryRow(
        id = id, habitId = habitId, timeLabel = "7:00 AM", label = "H", statusLabel = "Done",
        scheduledFor = 0L, habitType = HabitType.INDIVIDUAL, done = true, qaPairs = emptyList()
    )

    private fun intake(id: String, taskId: String) = IntakeEntryRow(
        id = id, taskId = taskId, timeLabel = "8:00 AM", label = "I", statusLabel = "Logged",
        flagLabel = null, outsideFood = false, scheduledFor = 0L, taskType = TaskType.FOOD,
        responseText = "", description = null, suspectedFood = null, qaPairs = emptyList()
    )

    @Test fun `a whole-habit exclusion hides every row for that habit`() {
        val set = listOf(AiExclusion("CHAT", "HABIT", "h1", 0L)).toSet()
        assertTrue(set.hidesTodo(todo("h1:100", "h1")))
        assertTrue(set.hidesTodo(todo("h1:200", "h1")))
        assertFalse(set.hidesTodo(todo("h2:100", "h2")))
    }

    @Test fun `an entry exclusion hides only that one entry`() {
        val set = listOf(AiExclusion("CHAT", "HABIT_ENTRY", "h1:100", 0L)).toSet()
        assertTrue(set.hidesTodo(todo("h1:100", "h1")))
        assertFalse(set.hidesTodo(todo("h1:200", "h1")))
    }

    @Test fun `a task-level hide does not touch habits with the same id prefix`() {
        val set = listOf(AiExclusion("SUMMARY", "TASK", "x1", 0L)).toSet()
        assertTrue(set.hidesIntake(intake("x1:100", "x1")))
        assertFalse(set.hidesTodo(todo("x1:100", "x1")))
    }

    @Test fun `the count and isEmpty are correct`() {
        assertTrue(AiExclusionSet.EMPTY.isEmpty)
        val set = listOf(
            AiExclusion("CHAT", "HABIT", "h1", 0L),
            AiExclusion("CHAT", "TASK", "t1", 0L),
            AiExclusion("CHAT", "TASK_ENTRY", "t2:50", 0L)
        ).toSet()
        assertFalse(set.isEmpty)
        assertEquals(1, set.habitIds.size)
        assertEquals(1, set.taskIds.size)
        assertEquals(1, set.entryIds.size)
    }
}
