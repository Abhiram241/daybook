package com.daybook.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * v0.5.3 Phase 3 (finding 19 / S16): [statusWhilePaused] is the decision behind the new
 * [SyncStatus.Paused] variant. While a push or pull is suppressed — a dismissed D2 conflict
 * (`dismissConflict`) or an in-flight `deleteRemoteDoc` — the account screen's sync row must read
 * "paused", not fall back to a stale `Idle` that means "up to date".
 */
class SyncPausedTransitionTest {

    @Test fun `suppressed maps to Paused, discarding the fallback`() {
        assertSame(SyncStatus.Paused, statusWhilePaused(suppressed = true, fallback = SyncStatus.Idle(123L)))
        assertSame(SyncStatus.Paused, statusWhilePaused(suppressed = true, fallback = SyncStatus.Syncing))
    }

    @Test fun `not suppressed passes the fallback through untouched`() {
        val idle = SyncStatus.Idle(456L)
        assertSame(idle, statusWhilePaused(suppressed = false, fallback = idle))
        assertSame(SyncStatus.Offline, statusWhilePaused(suppressed = false, fallback = SyncStatus.Offline))
    }

    @Test fun `Paused is its own variant, distinct from Idle`() {
        assertEquals(SyncStatus.Paused, SyncStatus.Paused)
        assertProperlyDistinct(SyncStatus.Paused, SyncStatus.Idle(0L))
    }

    private fun assertProperlyDistinct(a: SyncStatus, b: SyncStatus) {
        if (a == b) throw AssertionError("$a and $b must not be equal")
    }
}
