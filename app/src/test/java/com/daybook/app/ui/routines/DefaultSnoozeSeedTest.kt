package com.daybook.app.ui.routines

import org.junit.Assert.assertEquals
import org.junit.Test

/** rec 3 (N2) — the pure "which snooze does the form start at" rule. */
class DefaultSnoozeSeedTest {

    @Test
    fun `a new form starts at the app-wide default`() {
        assertEquals(20, seedSnooze(isEdit = false, savedValue = null, default = 20))
        assertEquals(15, seedSnooze(isEdit = false, savedValue = 99, default = 15))
    }

    @Test
    fun `an edit form keeps the item's own saved value`() {
        assertEquals(45, seedSnooze(isEdit = true, savedValue = 45, default = 10))
    }

    @Test
    fun `an edit with a missing saved value falls back to the default`() {
        assertEquals(10, seedSnooze(isEdit = true, savedValue = null, default = 10))
    }
}
