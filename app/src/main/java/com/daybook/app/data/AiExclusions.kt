package com.daybook.app.data

import com.daybook.app.data.model.AiExclusion

/**
 * AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.3 — pure domain layer for "Hide from AI".
 * Two independent [AiScope]s (Summary / Chat), each backed by its own set of [AiExclusion] rows.
 * No I/O here — [AiExclusionSet] is a plain in-memory filter, unit-testable without Room.
 */
enum class AiScope { SUMMARY, CHAT }

private const val KIND_HABIT = "HABIT"
private const val KIND_TASK = "TASK"
private const val KIND_HABIT_ENTRY = "HABIT_ENTRY"
private const val KIND_TASK_ENTRY = "TASK_ENTRY"

data class AiExclusionSet(
    val habitIds: Set<String>,
    val taskIds: Set<String>,
    val entryIds: Set<String>
) {
    fun hidesTodo(row: TodoEntryRow): Boolean = row.habitId in habitIds || row.id in entryIds
    fun hidesIntake(row: IntakeEntryRow): Boolean = row.taskId in taskIds || row.id in entryIds

    val isEmpty: Boolean get() = habitIds.isEmpty() && taskIds.isEmpty() && entryIds.isEmpty()

    companion object {
        val EMPTY = AiExclusionSet(emptySet(), emptySet(), emptySet())
    }
}

fun List<AiExclusion>.toSet(): AiExclusionSet {
    val habitIds = HashSet<String>()
    val taskIds = HashSet<String>()
    val entryIds = HashSet<String>()
    forEach { row ->
        when (row.kind) {
            KIND_HABIT -> habitIds += row.targetId
            KIND_TASK -> taskIds += row.targetId
            KIND_HABIT_ENTRY, KIND_TASK_ENTRY -> entryIds += row.targetId
        }
    }
    return AiExclusionSet(habitIds, taskIds, entryIds)
}

/** Filters a single day's report data against [exclusions], returning the filtered copy plus how
 *  many rows (todo + intake) were removed. Workout and health data are untouched — this plan's
 *  scope (§2.1 "[Decided] Scope") is habits and intake reminders only; those two already have
 *  their own on/off category switches. */
fun DailyReportData.withoutExcluded(exclusions: AiExclusionSet): Pair<DailyReportData, Int> {
    if (exclusions.isEmpty) return this to 0
    val keptTodo = todo.filterNot { exclusions.hidesTodo(it) }
    val keptIntake = intake.filterNot { exclusions.hidesIntake(it) }
    val hiddenCount = (todo.size - keptTodo.size) + (intake.size - keptIntake.size)
    return copy(todo = keptTodo, intake = keptIntake) to hiddenCount
}

/** Deterministic fingerprint of a scope's exclusion set, for staleness detection (settingsFingerprint). */
fun AiExclusionSet.fingerprint(): String =
    (habitIds.sorted() + taskIds.sorted() + entryIds.sorted()).joinToString(",")
