package com.daybook.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 2 (S1): [readMonthHashes] decides whether a cold start reads the month-hash summary
 * straight off the parent doc's `monthHashes` field or has to fall back to the per-doc `months`
 * collection scan. The scan is the one-time repair path for an old-layout parent doc only.
 */
class ParentMonthHashesTest {

    private val field = mapOf("2026-01" to "h1", "2026-02" to "h2")
    private val scan = mapOf("2026-01" to "SCAN", "2025-12" to "SCAN")

    @Test fun `the parent field wins when present and non-empty`() {
        var scanRan = false
        val out = readMonthHashes(field) { scanRan = true; scan }
        assertEquals(field, out)
        assertFalse("scan must not run when the field is present", scanRan)
    }

    @Test fun `a null field falls back to the scan`() {
        var scanRan = false
        val out = readMonthHashes(null) { scanRan = true; scan }
        assertEquals(scan, out)
        assertTrue(scanRan)
    }

    @Test fun `an empty field falls back to the scan`() {
        var scanRan = false
        val out = readMonthHashes(emptyMap()) { scanRan = true; scan }
        assertEquals(scan, out)
        assertTrue(scanRan)
    }
}
