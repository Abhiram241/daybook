package com.daybook.app.ui.foodmed

import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.Habit
import com.daybook.app.ui.routines.HabitSort
import com.daybook.app.ui.routines.sortHabits
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.1 §A/§B — the list comparators used to live inline inside a `combine` block and were
 * untestable. They are now pure top-level functions ([sortIntake], [sortHabits]); this pins their
 * contract: ascending `createdAt`, case-insensitive name, nulls-last next-reminder, stable order.
 */
class SortComparatorTest {

    private fun task(label: String, createdAt: Long) =
        FoodMedTask(id = "t-$label-$createdAt", label = label, createdAt = createdAt)

    private fun habit(title: String, createdAt: Long) =
        Habit(id = "h-$title-$createdAt", title = title, createdAt = createdAt)

    // ---- Intake ----------------------------------------------------------------

    @Test fun intake_added_ordersByCreatedAtAscending() {
        val a = task("A", 300L) to null
        val b = task("B", 100L) to null
        val c = task("C", 200L) to null
        val out = sortIntake(listOf(a, b, c), IntakeSort.ADDED).map { it.first.label }
        assertEquals(listOf("B", "C", "A"), out)
    }

    @Test fun intake_name_isCaseInsensitive() {
        val banana = task("Banana", 1L) to null
        val apple = task("apple", 2L) to null
        val out = sortIntake(listOf(banana, apple), IntakeSort.NAME).map { it.first.label }
        assertEquals(listOf("apple", "Banana"), out)
    }

    @Test fun intake_nextReminder_putsNullsLast() {
        val noPending = task("archived", 1L) to null
        val soon = task("soon", 2L) to 1_000L
        val later = task("later", 3L) to 5_000L
        val out = sortIntake(listOf(noPending, later, soon), IntakeSort.NEXT_REMINDER).map { it.first.label }
        assertEquals(listOf("soon", "later", "archived"), out)
    }

    @Test fun intake_isStableForEqualKeys() {
        val first = task("first", 42L) to null
        val second = task("second", 42L) to null
        val third = task("third", 42L) to null
        val out = sortIntake(listOf(first, second, third), IntakeSort.ADDED).map { it.first.label }
        assertEquals(listOf("first", "second", "third"), out)
    }

    // ---- Habits --------------------------------------------------------------

    @Test fun habits_added_ordersByCreatedAtAscending() {
        val a = habit("A", 300L) to null
        val b = habit("B", 100L) to null
        val c = habit("C", 200L) to null
        val out = sortHabits(listOf(a, b, c), HabitSort.ADDED).map { it.first.title }
        assertEquals(listOf("B", "C", "A"), out)
    }

    @Test fun habits_name_isCaseInsensitive() {
        val banana = habit("Banana", 1L) to null
        val apple = habit("apple", 2L) to null
        val out = sortHabits(listOf(banana, apple), HabitSort.NAME).map { it.first.title }
        assertEquals(listOf("apple", "Banana"), out)
    }

    @Test fun habits_nextReminder_putsNullsLast() {
        val archived = habit("archived", 1L) to null
        val soon = habit("soon", 2L) to 1_000L
        val later = habit("later", 3L) to 5_000L
        val out = sortHabits(listOf(archived, later, soon), HabitSort.NEXT_REMINDER).map { it.first.title }
        assertEquals(listOf("soon", "later", "archived"), out)
    }

    @Test fun habits_isStableForEqualKeys() {
        val first = habit("first", 42L) to null
        val second = habit("second", 42L) to null
        val third = habit("third", 42L) to null
        val out = sortHabits(listOf(first, second, third), HabitSort.ADDED).map { it.first.title }
        assertEquals(listOf("first", "second", "third"), out)
    }

    // ---- rec 3: the persisted-sort string round-trips through the enum ---------------

    @Test fun habitSort_enumRoundTripsAndFallsBackOnGarbage() {
        for (s in HabitSort.entries) {
            assertEquals(s, runCatching { HabitSort.valueOf(s.name) }.getOrDefault(HabitSort.ADDED))
        }
        assertEquals(HabitSort.ADDED, runCatching { HabitSort.valueOf("nonsense") }.getOrDefault(HabitSort.ADDED))
    }

    @Test fun intakeSort_enumRoundTripsAndFallsBackOnGarbage() {
        for (s in IntakeSort.entries) {
            assertEquals(s, runCatching { IntakeSort.valueOf(s.name) }.getOrDefault(IntakeSort.ADDED))
        }
        assertEquals(IntakeSort.ADDED, runCatching { IntakeSort.valueOf("") }.getOrDefault(IntakeSort.ADDED))
    }
}
