package com.daybook.app.data

/**
 * Journal-as-habit round: pure list-editing helpers for a per-habit ordered question list
 * (`HabitFormState.journalQuestions`, a plain in-memory `List<String>` — no Room table anymore).
 *
 * Relocated verbatim (behaviour-identical) from the now-deleted global `JournalQuestionRepository`
 * when the global `journal_questions` table was retired in favour of `Habit.journalQuestionsJson`.
 * These were already pure and already unit-tested-shaped; only their home changed.
 */

/** Pure: trim; blank -> null. Case-sensitive, case-preserving — same rule as `normalisePrompt`. */
fun normaliseQuestionText(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

/** Pure: the D6 >=1-question rule — a question may be deleted only when more than one remains. */
fun canDelete(count: Int): Boolean = count > 1

/**
 * Pure list-move maths: returns [list] with the element at [from] moved to index [to]; the SAME
 * instance is returned unchanged when either index is out of range or `from == to`.
 */
fun <T> moveInList(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices || to !in list.indices || from == to) return list
    val out = list.toMutableList()
    out.add(to, out.removeAt(from))
    return out
}
