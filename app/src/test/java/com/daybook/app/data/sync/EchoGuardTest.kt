package com.daybook.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three-layer echo guard (FIREBASE_0.5_PLAN.md §4 / §10). No Firestore types. */
class EchoGuardTest {

    private val state = SyncSnapshot(lastSyncedHash = "hashA", deviceId = "devA", lastKnownRevision = 5)

    private fun remote(
        hash: String? = "hashB",
        device: String? = "devB",
        rev: Long = 6,
        pending: Boolean = false,
        exists: Boolean = true
    ) = RemoteDoc(exists, hash, device, rev, pending)

    @Test fun ownPendingWrite_notApplied() =
        assertFalse(SyncLogic.shouldApply(remote(pending = true), state))

    @Test fun sameContentHash_notApplied() =
        assertFalse(SyncLogic.shouldApply(remote(hash = "hashA"), state))

    @Test fun ownAckedRevision_notApplied() =
        assertFalse(SyncLogic.shouldApply(remote(device = "devA", rev = 5), state))

    @Test fun ownOlderRevision_notApplied() =
        assertFalse(SyncLogic.shouldApply(remote(device = "devA", rev = 3), state))

    @Test fun genuineRemoteChange_applied() =
        assertTrue(SyncLogic.shouldApply(remote(), state))

    @Test fun olderRevisionFromAnotherDevice_stillApplied() =
        // last-write-wins: an older-numbered write from a different device is still "not ours".
        assertTrue(SyncLogic.shouldApply(remote(device = "devB", rev = 1), state))

    @Test fun nonExistentDoc_notApplied() =
        assertFalse(SyncLogic.shouldApply(remote(exists = false), state))
}
