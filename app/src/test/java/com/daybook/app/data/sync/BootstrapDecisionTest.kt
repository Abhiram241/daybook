package com.daybook.app.data.sync

import com.daybook.app.data.sync.SyncLogic.BootstrapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first-sign-in `when` block (FIREBASE_0.5_PLAN.md §4 / §10). */
class BootstrapDecisionTest {

    @Test fun bothEmpty_attachOnly() =
        assertEquals(BootstrapAction.ATTACH_ONLY, SyncLogic.decideBootstrap(localEmpty = true, remoteExists = false, hashesEqual = false, promptShown = false))

    @Test fun localHasData_cloudEmpty_pushLocal() =
        assertEquals(BootstrapAction.PUSH_LOCAL, SyncLogic.decideBootstrap(localEmpty = false, remoteExists = false, hashesEqual = false, promptShown = false))

    @Test fun localEmpty_cloudHasData_pullRemote() =
        assertEquals(BootstrapAction.PULL_REMOTE, SyncLogic.decideBootstrap(localEmpty = true, remoteExists = true, hashesEqual = false, promptShown = false))

    @Test fun bothPresent_sameContent_attachOnly() =
        assertEquals(BootstrapAction.ATTACH_ONLY, SyncLogic.decideBootstrap(localEmpty = false, remoteExists = true, hashesEqual = true, promptShown = false))

    @Test fun bothPresent_different_firstTime_conflict() =
        assertEquals(BootstrapAction.CONFLICT, SyncLogic.decideBootstrap(localEmpty = false, remoteExists = true, hashesEqual = false, promptShown = false))

    @Test fun bothPresent_different_alreadyPrompted_attachOnly_lastWriteWins() =
        assertEquals(BootstrapAction.ATTACH_ONLY, SyncLogic.decideBootstrap(localEmpty = false, remoteExists = true, hashesEqual = false, promptShown = true))

    // v0.5.3 Phase 1 (S2) — the cross-uid PUSH_LOCAL refusal (belt-and-braces for D3).

    @Test fun foreignUid_withResidentData_refusesPushLocal() =
        assertTrue(refusePushLocalForForeignUid(residentUid = "uidA", signInUid = "uidB", localEmpty = false))

    @Test fun sameUid_neverRefuses() =
        assertFalse(refusePushLocalForForeignUid(residentUid = "uidA", signInUid = "uidA", localEmpty = false))

    @Test fun noResidentUid_neverRefuses() =
        assertFalse(refusePushLocalForForeignUid(residentUid = null, signInUid = "uidB", localEmpty = false))

    @Test fun foreignUid_butLocalEmpty_neverRefuses() =
        assertFalse(refusePushLocalForForeignUid(residentUid = "uidA", signInUid = "uidB", localEmpty = true))

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 4 (S-1, Critical, D3 option 1) — a month present
    // remotely but absent from the local resident set (evicted, not missing) must resolve to
    // ATTACH_ONLY, never CONFLICT.

    @Test fun residentMonthHashes_excludesEvictedMonth() {
        val remote = mapOf("2024-01" to "hashA", "2024-02" to "hashB")
        val resident = setOf("2024-02")
        assertEquals(mapOf("2024-02" to "hashB"), residentMonthHashes(remote, resident))
    }

    @Test fun evictedMonthPresentRemotely_resolvesToAttachOnly_notConflict() {
        // Simulates bootstrap(): a device that has evicted "2024-01" (still on the server from an
        // earlier push) but still holds "2024-02" locally, matching the server's "2024-02" hash.
        val remoteMonthHashes = mapOf("2024-01" to "hashA", "2024-02" to "hashB")
        val localMonthHashes = mapOf("2024-02" to "hashB")
        val residentSet = localMonthHashes.keys

        val comparable = residentMonthHashes(remoteMonthHashes, residentSet)
        assertEquals(localMonthHashes, comparable)

        assertEquals(
            BootstrapAction.ATTACH_ONLY,
            SyncLogic.decideBootstrap(
                localEmpty = false,
                remoteExists = true,
                hashesEqual = comparable == localMonthHashes,
                promptShown = false
            )
        )
    }

    @Test fun unfilteredComparison_wouldHaveWronglyForcedConflict() {
        // The pre-fix bug, preserved as a regression guard: comparing the RAW (unfiltered) remote
        // hashes against local — an extra month the device has legitimately evicted reads as a
        // genuine mismatch and forces CONFLICT.
        val remoteMonthHashes = mapOf("2024-01" to "hashA", "2024-02" to "hashB")
        val localMonthHashes = mapOf("2024-02" to "hashB")

        assertEquals(
            BootstrapAction.CONFLICT,
            SyncLogic.decideBootstrap(
                localEmpty = false,
                remoteExists = true,
                hashesEqual = remoteMonthHashes == localMonthHashes,
                promptShown = false
            )
        )
    }

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-10) — conflictAlreadyResolved.

    @Test fun conflictAlreadyResolved_sameUidSameRevision_true() {
        assertTrue(conflictAlreadyResolved("uidA", 5L, currentUid = "uidA", currentRemoteRevision = 5L))
    }

    @Test fun conflictAlreadyResolved_sameUidDifferentRevision_false() {
        // The bug this fixes: a NEW remote change since the last resolution must re-open the
        // possibility of a fresh CONFLICT prompt, not stay permanently suppressed.
        assertFalse(conflictAlreadyResolved("uidA", 5L, currentUid = "uidA", currentRemoteRevision = 6L))
    }

    @Test fun conflictAlreadyResolved_differentUid_false() {
        assertFalse(conflictAlreadyResolved("uidA", 5L, currentUid = "uidB", currentRemoteRevision = 5L))
    }

    @Test fun conflictAlreadyResolved_neverResolved_false() {
        assertFalse(conflictAlreadyResolved(null, 0L, currentUid = "uidA", currentRemoteRevision = 0L))
    }
}
