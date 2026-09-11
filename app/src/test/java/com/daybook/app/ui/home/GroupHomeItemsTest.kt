package com.daybook.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * UX overhaul item 7 — [groupHomeItems] section labels, membership, counts and order across
 * today / past / future and with `byType` on & off.
 */
class GroupHomeItemsTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val nowMs = 1_000_000_000_000L

    private fun item(
        id: String,
        epoch: Long,
        statusLabel: String? = null,
        isHabit: Boolean = true,
        isJournal: Boolean = false,
        isHabitJournal: Boolean = false
    ) = HomeItem(
        id = id,
        title = id,
        subtitle = null,
        iconKey = "task",
        colorTag = "AUTO",
        scheduledTime = "09:00",
        scheduledEpoch = epoch,
        isHabit = isHabit,
        detailId = id,
        occurrenceId = id,
        canComplete = statusLabel == null,
        canSkip = statusLabel == null,
        canSnooze = statusLabel == null,
        canReply = false,
        responseText = null,
        statusLabel = statusLabel,
        isPast = false,
        isFuture = false,
        isJournal = isJournal,
        isHabitJournal = isHabitJournal
    )

    @Test
    fun `today splits unresolved items into Overdue, Now, Later and resolved into Done`() {
        val items = listOf(
            item("overdue", nowMs - 3_600_000L),
            item("now", nowMs + 10 * 60_000L),
            item("later", nowMs + 5 * 3_600_000L),
            item("done", nowMs + 60_000L, statusLabel = "Done"),
            item("skipped", nowMs + 60_000L, statusLabel = "Skipped")
        )
        val sections = groupHomeItems(items, today, today, nowMs, byType = false)
        assertEquals(listOf("Overdue", "Now", "Later", "Done"), sections.map { it.label })
        assertEquals(listOf("overdue"), sections[0].items.map { it.id })
        assertEquals(listOf("now"), sections[1].items.map { it.id })
        assertEquals(listOf("later"), sections[2].items.map { it.id })
        assertEquals(listOf("done", "skipped"), sections[3].items.map { it.id })
        assertEquals(2, sections[3].count)
    }

    @Test
    fun `empty sections are dropped`() {
        val items = listOf(item("later", nowMs + 5 * 3_600_000L))
        val sections = groupHomeItems(items, today, today, nowMs, byType = false)
        assertEquals(listOf("Later"), sections.map { it.label })
    }

    @Test
    fun `the Now window is 90 minutes ahead`() {
        val items = listOf(
            item("edge-in", nowMs + 90 * 60_000L),
            item("edge-out", nowMs + 90 * 60_000L + 1)
        )
        val sections = groupHomeItems(items, today, today, nowMs, byType = false)
        assertEquals(listOf("edge-in"), sections.first { it.label == "Now" }.items.map { it.id })
        assertEquals(listOf("edge-out"), sections.first { it.label == "Later" }.items.map { it.id })
    }

    @Test
    fun `a past day groups by status with To do first`() {
        val past = today.minusDays(2)
        val items = listOf(
            item("todo", 1L, statusLabel = null),
            item("missed", 2L, statusLabel = "Missed"),
            item("logged", 3L, statusLabel = "Logged"),
            item("skipped", 4L, statusLabel = "Skipped"),
            item("done", 5L, statusLabel = "Done")
        )
        val sections = groupHomeItems(items, past, today, nowMs, byType = false)
        assertEquals(listOf("To do", "Missed", "Logged", "Skipped", "Done"), sections.map { it.label })
    }

    @Test
    fun `a future day is a single Upcoming section`() {
        val future = today.plusDays(3)
        val items = listOf(item("a", 10L), item("b", 5L))
        val sections = groupHomeItems(items, future, today, nowMs, byType = false)
        assertEquals(listOf("Upcoming"), sections.map { it.label })
        // sorted by epoch within the section
        assertEquals(listOf("b", "a"), sections[0].items.map { it.id })
    }

    @Test
    fun `byType groups into Habits, Intake, Journal regardless of selected day`() {
        val items = listOf(
            item("h1", 1L, isHabit = true),
            item("i1", 2L, isHabit = false),
            item("j1", 3L, isHabit = false, isJournal = true),
            item("hj1", 4L, isHabit = true, isHabitJournal = true)
        )
        val sections = groupHomeItems(items, today, today, nowMs, byType = true)
        assertEquals(listOf("Habits", "Intake", "Journal"), sections.map { it.label })
        assertEquals(listOf("h1"), sections[0].items.map { it.id })
        assertEquals(listOf("i1"), sections[1].items.map { it.id })
        assertEquals(listOf("j1", "hj1"), sections[2].items.map { it.id })
    }

    @Test
    fun `total item count is preserved across grouping`() {
        val items = (1..12).map { item("x$it", nowMs + it * 60_000L, statusLabel = if (it % 3 == 0) "Done" else null) }
        listOf(true, false).forEach { byType ->
            val total = groupHomeItems(items, today, today, nowMs, byType).sumOf { it.items.size }
            assertEquals(items.size, total)
        }
    }

    @Test
    fun `items stay in scheduledEpoch order within a section`() {
        val items = listOf(
            item("c", nowMs + 300_000L),
            item("a", nowMs + 100_000L),
            item("b", nowMs + 200_000L)
        )
        val sections = groupHomeItems(items, today, today, nowMs, byType = false)
        val later = sections.first { it.label == "Later" || it.label == "Now" }
        assertTrue(later.items.map { it.scheduledEpoch }.zipWithNext().all { it.first <= it.second })
    }
}
