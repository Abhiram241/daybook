package com.daybook.app.data.sync

import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.HabitLog
import com.daybook.app.data.backup.IntakeLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.1 §N — the per-month hash diff that decides what a push actually writes.
 *
 * The no-op case is the important one: it is what keeps a settings write, or a re-export that
 * changed nothing, from costing a cloud round trip.
 */
class MonthHashDiffTest {

    private fun day(date: String, answer: String? = null) = DayEntry(
        date = date,
        habitLogs = listOf(HabitLog("h1", "09:00", "done")),
        intakeLogs = listOf(IntakeLog("t1", "13:00", "logged", answer = answer))
    )

    @Test
    fun `identical maps produce no changes`() {
        val m = mapOf("2026-08" to "aaa", "2026-07" to "bbb")
        assertTrue(MonthPartitioner.changedMonths(m, m).isEmpty())
    }

    @Test
    fun `one changed hash yields exactly that month`() {
        val current = mapOf("2026-08" to "CHANGED", "2026-07" to "bbb")
        val known = mapOf("2026-08" to "aaa", "2026-07" to "bbb")
        assertEquals(setOf("2026-08"), MonthPartitioner.changedMonths(current, known))
    }

    @Test
    fun `a month present locally but unknown to the cloud is a new month`() {
        val current = mapOf("2026-08" to "aaa", "2026-09" to "ccc")
        val known = mapOf("2026-08" to "aaa")
        assertEquals(setOf("2026-09"), MonthPartitioner.changedMonths(current, known))
    }

    @Test
    fun `a month known to the cloud but absent locally is a deletion`() {
        val current = mapOf("2026-08" to "aaa")
        val known = mapOf("2026-08" to "aaa", "2026-07" to "bbb")
        assertEquals(setOf("2026-07"), MonthPartitioner.changedMonths(current, known))
    }

    @Test
    fun `both a change and a deletion are reported together`() {
        val current = mapOf("2026-08" to "CHANGED")
        val known = mapOf("2026-08" to "aaa", "2026-06" to "ccc")
        assertEquals(setOf("2026-08", "2026-06"), MonthPartitioner.changedMonths(current, known))
    }

    @Test
    fun `an evicted month is invisible once the caller filters known by residency`() {
        // The caller contract: CloudSyncRepository pre-filters `known` to resident months, so an
        // evicted month cannot be mistaken for "emptied locally" and delete the user's history.
        val current = mapOf("2026-08" to "aaa")
        val known = mapOf("2026-08" to "aaa", "2026-02" to "old")
        val resident = setOf("2026-08")          // 2026-02 was evicted
        val filtered = known.filterKeys { it in resident }
        assertTrue(MonthPartitioner.changedMonths(current, filtered).isEmpty())
        // Without the filter it would have been reported as a deletion:
        assertEquals(setOf("2026-02"), MonthPartitioner.changedMonths(current, known))
    }

    @Test
    fun `hashes is stable across calls on equal input`() {
        val partitioned = MonthPartitioner.partition(listOf(day("2026-08-01"), day("2026-08-02")))
        assertEquals(MonthPartitioner.hashes(partitioned), MonthPartitioner.hashes(partitioned))
    }

    @Test
    fun `hashes differ when one day's logs differ`() {
        val a = MonthPartitioner.hashes(MonthPartitioner.partition(listOf(day("2026-08-01", "toast"))))
        val b = MonthPartitioner.hashes(MonthPartitioner.partition(listOf(day("2026-08-01", "eggs"))))
        assertNotEquals(a.getValue("2026-08"), b.getValue("2026-08"))
    }

    @Test
    fun `a change in one month leaves the other months' hashes untouched`() {
        val before = MonthPartitioner.hashes(
            MonthPartitioner.partition(listOf(day("2026-07-31"), day("2026-08-01", "toast")))
        )
        val after = MonthPartitioner.hashes(
            MonthPartitioner.partition(listOf(day("2026-07-31"), day("2026-08-01", "eggs")))
        )
        assertEquals(
            "July must not be rewritten because an August day changed",
            before.getValue("2026-07"),
            after.getValue("2026-07")
        )
        assertEquals(setOf("2026-08"), MonthPartitioner.changedMonths(after, before))
    }

    @Test
    fun `an empty month list hashes to an empty map`() {
        assertTrue(MonthPartitioner.hashes(emptyMap()).isEmpty())
    }
}
