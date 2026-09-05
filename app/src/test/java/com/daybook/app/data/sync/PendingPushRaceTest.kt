package com.daybook.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 1 (S4 / S10): the two decisions that stop a pull (or a slow push) from clearing
 * `pendingPush` for an edit it never actually pushed.
 */
class PendingPushRaceTest {

    @Test fun shouldClearPending_onlyWhenDirtyUnchanged() {
        assertTrue(shouldClearPending(seenDirty = 3, nowDirty = 3))
        assertFalse(shouldClearPending(seenDirty = 3, nowDirty = 4))
    }

    @Test fun shouldRearmPending_whenReExportDiffersFromApplied() {
        assertTrue(shouldRearmPendingAfterImport(appliedHash = "aaa", reExportHash = "bbb"))
        assertFalse(shouldRearmPendingAfterImport(appliedHash = "aaa", reExportHash = "aaa"))
    }
}
