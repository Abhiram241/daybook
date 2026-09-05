package com.daybook.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.3 Phase 3 (A4): [pageBounds] turns a zero-based page index + page size into the
 * `(offset, limit)` pair the paged terminal-timeline DAO query takes. Trivial guard — the value is
 * that a negative page or size can never produce a negative bound handed to SQLite.
 */
class TerminalPagingTest {

    @Test fun `page zero starts at offset zero`() {
        assertEquals(0 to 100, pageBounds(page = 0, size = 100))
    }

    @Test fun `page N offsets by N times the size`() {
        assertEquals(100 to 100, pageBounds(page = 1, size = 100))
        assertEquals(300 to 100, pageBounds(page = 3, size = 100))
    }

    @Test fun `a negative page is clamped to zero`() {
        assertEquals(0 to 50, pageBounds(page = -5, size = 50))
    }

    @Test fun `a negative size is clamped to zero`() {
        assertEquals(0 to 0, pageBounds(page = 2, size = -1))
    }
}
