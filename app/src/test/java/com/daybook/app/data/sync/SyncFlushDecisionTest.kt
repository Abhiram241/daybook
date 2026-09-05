package com.daybook.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.3 Phase 1 (S7): [flushOutcome] — a `SyncFlushWorker` run with nothing pending is a real
 * success; one with a pending push but no resolved uid must retry, not consume the unique work.
 */
class SyncFlushDecisionTest {

    @Test fun nothingPending_isSuccessNoop() =
        assertEquals(FlushOutcome.SUCCESS_NOOP, flushOutcome(pending = false, uidKnown = false))

    @Test fun nothingPending_evenWithUid_isSuccessNoop() =
        assertEquals(FlushOutcome.SUCCESS_NOOP, flushOutcome(pending = false, uidKnown = true))

    @Test fun pendingButNoUid_retries() =
        assertEquals(FlushOutcome.RETRY_NO_UID, flushOutcome(pending = true, uidKnown = false))

    @Test fun pendingWithUid_proceeds() =
        assertEquals(FlushOutcome.PROCEED, flushOutcome(pending = true, uidKnown = true))
}
