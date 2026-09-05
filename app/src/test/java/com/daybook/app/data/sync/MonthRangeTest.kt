package com.daybook.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 6 (D2): guards [MonthPartitioner.monthKeysInRange] — the "which cloud month docs
 * must a date-range export hydrate" helper. Pure JVM, same shape as [MonthPartitionerTest].
 */
class MonthRangeTest {

    @Test fun `a single month range is just that month`() {
        assertEquals(listOf("2026-03"), MonthPartitioner.monthKeysInRange("2026-03", "2026-03"))
    }

    @Test fun `an in-year span is inclusive of both ends`() {
        assertEquals(
            listOf("2026-01", "2026-02", "2026-03"),
            MonthPartitioner.monthKeysInRange("2026-01", "2026-03")
        )
    }

    @Test fun `a span crossing a year boundary rolls over correctly`() {
        assertEquals(
            listOf("2025-11", "2025-12", "2026-01"),
            MonthPartitioner.monthKeysInRange("2025-11", "2026-01")
        )
    }

    @Test fun `start after end yields empty`() {
        assertTrue(MonthPartitioner.monthKeysInRange("2026-05", "2026-03").isEmpty())
    }

    @Test fun `a malformed key yields empty, never a crash`() {
        assertTrue(MonthPartitioner.monthKeysInRange("nope", "2026-03").isEmpty())
        assertTrue(MonthPartitioner.monthKeysInRange("2026-03", "2026-13").isEmpty())
        assertTrue(MonthPartitioner.monthKeysInRange("2026-3", "2026-05").isEmpty())
    }

    @Test fun `whitespace around a key is tolerated`() {
        assertEquals(listOf("2026-03"), MonthPartitioner.monthKeysInRange(" 2026-03 ", "2026-03"))
    }

    @Test fun `a long multi-year span is bounded and ordered`() {
        val keys = MonthPartitioner.monthKeysInRange("2024-01", "2026-12")
        assertEquals(36, keys.size)
        assertEquals("2024-01", keys.first())
        assertEquals("2026-12", keys.last())
        assertEquals(keys.sorted(), keys)
    }
}
