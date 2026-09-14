package com.daybook.app.data

import com.daybook.app.data.model.AiExclusion
import com.daybook.app.data.model.HabitType
import com.daybook.app.data.model.TaskType
import com.daybook.app.data.workout.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §5 — pure, no I/O. */
class WithoutExcludedTest {

    private fun todo(id: String, habitId: String) = TodoEntryRow(
        id = id, habitId = habitId, timeLabel = "7:00 AM", label = "H", statusLabel = "Done",
        scheduledFor = 0L, habitType = HabitType.INDIVIDUAL, done = true, qaPairs = emptyList()
    )

    private fun intake(id: String, taskId: String) = IntakeEntryRow(
        id = id, taskId = taskId, timeLabel = "8:00 AM", label = "I", statusLabel = "Logged",
        flagLabel = null, outsideFood = false, scheduledFor = 0L, taskType = TaskType.FOOD,
        responseText = "", description = null, suspectedFood = null, qaPairs = emptyList()
    )

    private fun day(todo: List<TodoEntryRow>, intake: List<IntakeEntryRow>) = DailyReportData(
        date = LocalDate.of(2026, 9, 14), workout = null, health = null,
        intake = intake, todo = todo, aiSummary = null, weightUnit = WeightUnit.KG
    )

    @Test fun `an empty exclusion set changes nothing and keeps order`() {
        val d = day(listOf(todo("h1:1", "h1"), todo("h1:2", "h1")), listOf(intake("t1:1", "t1")))
        val (filtered, hidden) = d.withoutExcluded(AiExclusionSet.EMPTY)
        assertEquals(0, hidden)
        assertEquals(d.todo, filtered.todo)
        assertEquals(d.intake, filtered.intake)
    }

    @Test fun `filtering drops only the excluded rows and reports the count`() {
        val d = day(
            listOf(todo("h1:1", "h1"), todo("h2:1", "h2")),
            listOf(intake("t1:1", "t1"), intake("t2:1", "t2"))
        )
        val set = listOf(
            AiExclusion("CHAT", "HABIT", "h1", 0L),
            AiExclusion("CHAT", "TASK_ENTRY", "t2:1", 0L)
        ).toSet()
        val (filtered, hidden) = d.withoutExcluded(set)
        assertEquals(2, hidden)
        assertEquals(listOf("h2:1"), filtered.todo.map { it.id })
        assertEquals(listOf("t1:1"), filtered.intake.map { it.id })
    }

    @Test fun `workout and health sections are never touched by exclusions`() {
        val d = day(listOf(todo("h1:1", "h1")), emptyList())
        val set = listOf(AiExclusion("CHAT", "HABIT", "h1", 0L)).toSet()
        val (filtered, _) = d.withoutExcluded(set)
        assertEquals(d.workout, filtered.workout)
        assertEquals(d.health, filtered.health)
    }
}
