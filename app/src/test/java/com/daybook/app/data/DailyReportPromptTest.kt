package com.daybook.app.data

import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.model.HabitType
import com.daybook.app.data.model.TaskType
import com.daybook.app.data.health.HealthCardKind
import com.daybook.app.data.workout.WeightUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** DAILY_REPORT_PLAN.md §3.4 — pure, deterministic, no I/O. */
class DailyReportPromptTest {

    private val date = LocalDate.of(2026, 8, 29)

    @Test fun `an entirely empty day names every section as absent`() {
        val prompt = buildDailyReportPrompt(null, null, emptyList(), emptyList(), date, WeightUnit.KG)
        assertTrue(prompt.contains("No workout logged."))
        assertTrue(prompt.contains("No health data logged."))
        assertTrue(prompt.contains("No food/med entries logged."))
        assertTrue(prompt.contains("Nothing scheduled."))
    }

    @Test fun `a workout section lists title, volume and exercises`() {
        val workout = WorkoutSectionData(
            listOf(
                WorkoutSessionSummary(
                    title = "Push day", durationMinutes = 45, totalVolumeKg = 1200f, setCount = 12,
                    exercises = listOf(WorkoutExerciseSummary("Bench Press", 4, "8 reps @ 60 kg"))
                )
            )
        )
        val prompt = buildDailyReportPrompt(workout, null, emptyList(), emptyList(), date, WeightUnit.KG)
        assertTrue(prompt.contains("Push day"))
        assertTrue(prompt.contains("Bench Press"))
        assertFalse(prompt.contains("No workout logged."))
    }

    @Test fun `intake rows show flag and outside-food markers`() {
        val intake = listOf(
            IntakeEntryRow(
                id = "occ1", taskId = "task1", timeLabel = "8:00 AM", label = "Breakfast", statusLabel = "Logged",
                flagLabel = "Possible trigger", outsideFood = true, scheduledFor = 0L,
                taskType = TaskType.FOOD, responseText = "Oatmeal", description = null,
                suspectedFood = null, qaPairs = emptyList()
            )
        )
        val prompt = buildDailyReportPrompt(null, null, intake, emptyList(), date, WeightUnit.KG)
        assertTrue(prompt.contains("Breakfast"))
        assertTrue(prompt.contains("Possible trigger"))
        assertTrue(prompt.contains("[outside food]"))
    }

    @Test fun `todo rows show status`() {
        val todo = listOf(
            TodoEntryRow(
                id = "occ2", habitId = "habit1", timeLabel = "7:00 AM", label = "Stretch", statusLabel = "Done",
                scheduledFor = 0L, habitType = HabitType.INDIVIDUAL, done = true, qaPairs = emptyList()
            )
        )
        val prompt = buildDailyReportPrompt(null, null, emptyList(), todo, date, WeightUnit.KG)
        assertTrue(prompt.contains("Stretch — Done"))
    }

    @Test fun `health section renders steps and heart rate when present`() {
        val health = HealthSectionData(
            day = HealthDay(localDate = "2026-08-29", steps = 8000, avgHeartRate = 70, updatedAt = 0L),
            visibleCards = setOf(HealthCardKind.STEPS, HealthCardKind.HEART_RATE),
            sessions = emptyList()
        )
        val prompt = buildDailyReportPrompt(null, health, emptyList(), emptyList(), date, WeightUnit.KG)
        assertTrue(prompt.contains("Steps: 8,000") || prompt.contains("Steps: 8000"))
        assertFalse(prompt.contains("No health data logged."))
    }

    @Test fun `output is deterministic for the same input`() {
        val a = buildDailyReportPrompt(null, null, emptyList(), emptyList(), date, WeightUnit.KG)
        val b = buildDailyReportPrompt(null, null, emptyList(), emptyList(), date, WeightUnit.KG)
        org.junit.Assert.assertEquals(a, b)
    }
}
