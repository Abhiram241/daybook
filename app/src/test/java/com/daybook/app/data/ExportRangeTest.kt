package com.daybook.app.data

import com.daybook.app.data.backup.BackupMeta
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.DaybookBackup
import com.daybook.app.util.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v0.5.3 Phase 6 (D2): guards the pure range-scoping logic behind
 * [ExportImportRepository.exportRange] — the [daysInRange] filter and the `meta` range stamp.
 * The repo method itself needs Room, so the object-level composition is exercised here instead.
 */
class ExportRangeTest {

    private val jsonUtils = JsonUtils()

    private fun day(d: String) = DayEntry(date = d)

    private val days = listOf(
        day("2026-02-27"), day("2026-03-01"), day("2026-03-15"),
        day("2026-03-31"), day("2026-04-01"), day("2026-04-02")
    )

    @Test fun `inclusive bounds - a day exactly on start or end is kept`() {
        val out = daysInRange(days, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"))
        assertEquals(listOf("2026-03-01", "2026-03-15", "2026-03-31"), out.map { it.date })
    }

    @Test fun `out-of-range days on both sides are excluded`() {
        val out = daysInRange(days, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"))
        assertTrue(out.none { it.date == "2026-02-27" || it.date.startsWith("2026-04") })
    }

    @Test fun `an empty span (start after end) returns nothing`() {
        assertTrue(daysInRange(days, LocalDate.parse("2026-04-10"), LocalDate.parse("2026-03-01")).isEmpty())
    }

    @Test fun `a one-day range returns just that day`() {
        val out = daysInRange(days, LocalDate.parse("2026-03-15"), LocalDate.parse("2026-03-15"))
        assertEquals(listOf("2026-03-15"), out.map { it.date })
    }

    @Test fun `output is sorted ascending even when input is not`() {
        val shuffled = listOf(day("2026-03-31"), day("2026-03-01"), day("2026-03-15"))
        val out = daysInRange(shuffled, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"))
        assertEquals(listOf("2026-03-01", "2026-03-15", "2026-03-31"), out.map { it.date })
    }

    @Test fun `an unparseable date is dropped, never a crash`() {
        val out = daysInRange(
            days + day("garbage"), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")
        )
        assertTrue(out.none { it.date == "garbage" })
    }

    /** The `meta` stamp a range export applies vs a full export (which leaves both null). */
    @Test fun `range meta is set on a scoped copy and null on a plain backup`() {
        val full = DaybookBackup(
            meta = BackupMeta(exportedAt = "x", appVersionName = "1.0"),
            definitions = Definitions(),
            days = days
        )
        assertNull(full.meta.rangeStart)
        assertNull(full.meta.rangeEnd)

        val lo = LocalDate.parse("2026-03-01")
        val hi = LocalDate.parse("2026-03-31")
        val scoped = full.copy(
            meta = full.meta.copy(rangeStart = lo.toString(), rangeEnd = hi.toString()),
            days = daysInRange(full.days, lo, hi)
        )
        assertEquals("2026-03-01", scoped.meta.rangeStart)
        assertEquals("2026-03-31", scoped.meta.rangeEnd)
        assertEquals(3, scoped.days.size)

        // Survives a JSON round trip, and formatVersion is still 2.
        val decoded = jsonUtils.decode(jsonUtils.encode(scoped))
        assertEquals(2, decoded.meta.formatVersion)
        assertEquals("2026-03-01", decoded.meta.rangeStart)
        assertEquals("2026-03-31", decoded.meta.rangeEnd)
    }
}
