package com.daybook.app.data.sync

import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.HabitLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** v0.5.1 §N. Pure JVM — [MonthPartitioner] touches no Firestore, Room or Android type. */
class MonthPartitionerTest {

    private fun day(date: String, logs: Int = 0) = DayEntry(
        date = date,
        habitLogs = (0 until logs).map { HabitLog("h1", "0%d:00".format(it), "done") }
    )

    @Test
    fun `monthKeyOf takes the yyyy-MM prefix of a valid ISO date`() {
        assertEquals("2026-08", MonthPartitioner.monthKeyOf("2026-08-30"))
        assertEquals("2026-01", MonthPartitioner.monthKeyOf("2026-01-01"))
        assertEquals("2025-12", MonthPartitioner.monthKeyOf("2025-12-31"))
    }

    @Test
    fun `monthKeyOf rejects anything that is not a real yyyy-MM-dd date`() {
        // "2026-1-5" has a take(7)-friendly shape but is not a date this app writes; bucketing it
        // as "2026-1-" would be worse than dropping it.
        assertNull(MonthPartitioner.monthKeyOf("2026-1-5"))
        assertNull(MonthPartitioner.monthKeyOf(""))
        assertNull(MonthPartitioner.monthKeyOf("not-a-date"))
        assertNull(MonthPartitioner.monthKeyOf("2026-13-01"))
    }

    @Test
    fun `an over-long day clamps into its own month, matching the importer`() {
        // java.time's default SMART resolver clamps 2026-02-30 to 2026-02-28, and
        // ExportImportRepository.parseDate uses the very same formatter. The partitioner must
        // agree with the importer about which month a day lands in, so this is deliberate: the
        // alternative (ResolverStyle.STRICT here only) would put a day in no month while the
        // importer happily wrote it into February.
        assertEquals("2026-02", MonthPartitioner.monthKeyOf("2026-02-30"))
    }

    @Test
    fun `partition buckets days that straddle month boundaries`() {
        val days = listOf(
            day("2026-07-31"),
            day("2026-08-01"),
            day("2026-08-31"),
            day("2026-09-01")
        )
        val out = MonthPartitioner.partition(days)

        assertEquals(setOf("2026-07", "2026-08", "2026-09"), out.keys)
        assertEquals(1, out.getValue("2026-07").size)
        assertEquals(2, out.getValue("2026-08").size)
        assertEquals(1, out.getValue("2026-09").size)
    }

    @Test
    fun `a boundary day lands in the month its date string names (decision 3)`() {
        // DayEntry.date is already a LOCAL yyyy-MM-dd, so this is a string property and needs no
        // timezone fixture — that is exactly the guarantee decision 3 buys.
        assertEquals(
            listOf("2026-08"),
            MonthPartitioner.partition(listOf(day("2026-08-01"))).keys.toList()
        )
        assertEquals(
            listOf("2026-07"),
            MonthPartitioner.partition(listOf(day("2026-07-31"))).keys.toList()
        )
    }

    @Test
    fun `partition preserves ascending order within a month`() {
        val out = MonthPartitioner.partition(
            listOf(day("2026-08-01"), day("2026-08-15"), day("2026-08-31"))
        )
        assertEquals(
            listOf("2026-08-01", "2026-08-15", "2026-08-31"),
            out.getValue("2026-08").map { it.date }
        )
    }

    @Test
    fun `partition of an empty list is empty`() {
        assertTrue(MonthPartitioner.partition(emptyList()).isEmpty())
    }

    /**
     * v0.5.3 Phase 2 (S17) regression guard: the exporter now keys `DayEntry.date` off the stored
     * `local_date`, but `MonthPartitioner` must still bucket PURELY off the `DayEntry.date` string —
     * no timezone maths leaked in. Two days one calendar day apart land in the month their date
     * string names, regardless of any instant they might once have derived from.
     */
    @Test
    fun `partition keys off the DayEntry_date string only, with no timezone maths`() {
        val out = MonthPartitioner.partition(listOf(day("2026-01-31"), day("2026-02-01")))
        assertEquals(setOf("2026-01", "2026-02"), out.keys)
        assertEquals("2026-01-31", out.getValue("2026-01").single().date)
        assertEquals("2026-02-01", out.getValue("2026-02").single().date)
    }

    @Test
    fun `partition drops nothing and duplicates nothing`() {
        val days = (1..28).map { day("2026-02-%02d".format(it)) } +
            (1..31).map { day("2026-03-%02d".format(it)) }
        val flattened = MonthPartitioner.partition(days).values.flatten()
        assertEquals(days.size, flattened.size)
        assertEquals(days.map { it.date }.toSet(), flattened.map { it.date }.toSet())
    }

    @Test
    fun `unparseable days are dropped rather than mis-bucketed`() {
        val out = MonthPartitioner.partition(listOf(day("2026-08-01"), day("garbage")))
        assertEquals(setOf("2026-08"), out.keys)
        assertEquals(1, out.getValue("2026-08").size)
    }

    @Test
    fun `recentMonths is this month plus the previous one`() {
        assertEquals(
            setOf("2026-08", "2026-07"),
            MonthPartitioner.recentMonths(YearMonth.of(2026, 8))
        )
        // January must roll back into the previous year.
        assertEquals(
            setOf("2026-01", "2025-12"),
            MonthPartitioner.recentMonths(YearMonth.of(2026, 1))
        )
    }

    @Test
    fun `epochRangeOf is a half-open month in local time`() {
        val (start, end) = MonthPartitioner.epochRangeOf("2026-08")!!
        assertTrue(end > start)
        // 31 days in August; the range must be exactly that many local days wide.
        assertEquals(31L, (end - start) / (24L * 60 * 60 * 1000))
        assertNull(MonthPartitioner.epochRangeOf("nope"))
        assertNull(MonthPartitioner.epochRangeOf("2026-13"))
    }

    @Test
    fun `days round-trip through the cloud blob encoding`() {
        val days = listOf(day("2026-08-01", logs = 2), day("2026-08-02", logs = 1))
        val decoded = MonthPartitioner.decodeDays(MonthPartitioner.encodeDays(days))
        assertEquals(days, decoded)
    }

    @Test
    fun `decodeDays returns null rather than throwing on garbage`() {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 8 (C-3, High): was emptyList() — indistinguishable
        // from a genuinely empty month and let a corrupt payload silently wipe local PENDING rows.
        // Now null, mirroring decodeDefinitionsJson, so the caller can tell "corrupt" from "empty".
        assertNull(MonthPartitioner.decodeDays("{not json"))
    }

    @Test
    fun `decodeDays returns empty list for a genuinely empty month`() {
        assertEquals(emptyList<DayEntry>(), MonthPartitioner.decodeDays(MonthPartitioner.encodeDays(emptyList())))
    }

    // ---- isGenuinelyEmptyMonth (Phase 8, C-3's second guard) ----

    @Test fun isGenuinelyEmptyMonth_noStoredHash_treatedAsEmpty() {
        assertTrue(MonthPartitioner.isGenuinelyEmptyMonth(null))
    }

    @Test fun isGenuinelyEmptyMonth_hashMatchesEmptyDays_treatedAsEmpty() {
        assertTrue(MonthPartitioner.isGenuinelyEmptyMonth(ContentHash.ofDays(emptyList())))
    }

    @Test fun isGenuinelyEmptyMonth_hashFromNonEmptyContent_treatedAsCorrupt() {
        val realHash = ContentHash.ofDays(listOf(day("2026-08-01", logs = 2)))
        assertFalse(MonthPartitioner.isGenuinelyEmptyMonth(realHash))
    }

    // ---- cappedMostRecentMonths (Phase 11, S-8) ----

    @Test fun cappedMostRecentMonths_keepsMostRecent_dropsOldest() {
        // 35 genuinely distinct months (2018-01 .. 2020-11), plus 3 always-recent ones.
        val old = (0 until 35).map { i -> "%04d-%02d".format(2018 + i / 12, (i % 12) + 1) }.toSet()
        val resident = old + setOf("2026-01", "2026-02", "2026-03")
        assertEquals(38, resident.size)

        val capped = MonthPartitioner.cappedMostRecentMonths(resident, cap = 30)
        assertEquals(30, capped.size)
        // The three genuinely-recent months must always survive the cap.
        assertTrue(capped.containsAll(listOf("2026-01", "2026-02", "2026-03")))
        // The single OLDEST month in the set is the one guaranteed to be dropped.
        assertTrue("2018-01" !in capped)
        assertTrue("2020-11" in capped) // the newest of the "old" batch must survive
    }

    @Test fun cappedMostRecentMonths_underCap_returnsEverythingSorted() {
        val resident = setOf("2024-03", "2024-01", "2024-02")
        assertEquals(listOf("2024-03", "2024-02", "2024-01"), MonthPartitioner.cappedMostRecentMonths(resident))
    }
}
