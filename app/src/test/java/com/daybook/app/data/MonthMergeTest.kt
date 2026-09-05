package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 3 (S15): guards [mergeMonth], the per-log month merge that replaced the old
 * clear-and-replace in `ExportImportRepository.importMonth`.
 *
 * The rules under test:
 *  - every incoming row is upserted (REPLACE on the `itemId:millis` PK — the cloud is the source
 *    for everything it names);
 *  - a local **resolved** row the cloud does not name is KEPT — this device's offline edit the
 *    other device has not seen yet;
 *  - a local **PENDING** row the cloud does not name is DELETED — a stale window slot;
 *  - a local PENDING row the cloud DOES name is upserted, never deleted.
 *
 * Together these make "two devices, same month, different reminders" lossless. Future PENDING
 * slots are out of scope by construction: `importMonth` only feeds rows before `now` into this
 * helper, so a currently-armed alarm's slot can never land in `deletePending`.
 */
class MonthMergeTest {

    private fun pending(id: String) = OccKey(id, resolved = false)
    private fun resolved(id: String) = OccKey(id, resolved = true)

    @Test fun `incoming rows are all upserted`() {
        val r = mergeMonth(local = emptyList(), incoming = listOf(pending("a:1"), resolved("a:2")))
        assertEquals(listOf("a:1", "a:2"), r.upsert)
        assertTrue(r.deletePending.isEmpty())
        assertTrue(r.keepLocal.isEmpty())
    }

    @Test fun `a local resolved row absent from incoming is kept`() {
        val r = mergeMonth(local = listOf(resolved("a:1")), incoming = emptyList())
        assertEquals(listOf("a:1"), r.keepLocal)
        assertTrue(r.deletePending.isEmpty())
    }

    @Test fun `a local pending row absent from incoming is deleted`() {
        val r = mergeMonth(local = listOf(pending("a:1")), incoming = emptyList())
        assertEquals(listOf("a:1"), r.deletePending)
        assertTrue(r.keepLocal.isEmpty())
    }

    @Test fun `a local pending row present in incoming is upserted, not deleted`() {
        val r = mergeMonth(local = listOf(pending("a:1")), incoming = listOf(pending("a:1")))
        assertEquals(listOf("a:1"), r.upsert)
        assertTrue(r.deletePending.isEmpty())
        assertTrue(r.keepLocal.isEmpty())
    }

    @Test fun `a local resolved row present in incoming is driven by the cloud (upsert), not kept`() {
        val r = mergeMonth(local = listOf(resolved("a:1")), incoming = listOf(resolved("a:1")))
        assertEquals(listOf("a:1"), r.upsert)
        assertTrue(r.keepLocal.isEmpty())
    }

    /** The audit's scenario: device A logged breakfast, device B logged lunch, same month. */
    @Test fun `A logs breakfast, B logs lunch - A's breakfast survives the apply of B's month`() {
        // Local (device A) has A's breakfast resolved plus a stale morning window slot.
        val local = listOf(resolved("meal:0800"), pending("meal:1230"))
        // Incoming month doc from device B names only B's lunch.
        val incoming = listOf(resolved("meal:1230"))

        val r = mergeMonth(local, incoming)

        assertEquals("B's lunch is applied", listOf("meal:1230"), r.upsert)
        assertEquals("A's breakfast is not lost", listOf("meal:0800"), r.keepLocal)
        assertFalse("A's breakfast is never deleted", r.deletePending.contains("meal:0800"))
        assertTrue("the stale pending slot is cleared once the cloud claims it", r.deletePending.isEmpty())
    }
}
