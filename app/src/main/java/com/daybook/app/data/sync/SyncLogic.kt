package com.daybook.app.data.sync

/**
 * Pure sync decision logic (FIREBASE_0.5_PLAN.md §4 + §10). No Firestore, Room or Android
 * types — driven entirely by these small value classes so `EchoGuardTest` /
 * `BootstrapDecisionTest` need no mocks.
 */

/** The plaintext fields of the remote `users/{uid}` doc that the guards inspect. */
data class RemoteDoc(
    val exists: Boolean,
    val contentHash: String?,
    val deviceId: String?,
    val revision: Long,
    /** `snapshot.metadata.hasPendingWrites` — our own not-yet-acked local write echoing back. */
    val hasPendingWrites: Boolean
)

/** The bits of local [SyncStateStore] the guards compare against. */
data class SyncSnapshot(
    val lastSyncedHash: String?,
    val deviceId: String,
    val lastKnownRevision: Long
)

object SyncLogic {

    /**
     * The three-layer echo guard. Returns true only for a genuine remote change that must be
     * imported; false for any flavour of our own write coming back.
     */
    fun shouldApply(remote: RemoteDoc, state: SyncSnapshot): Boolean {
        if (!remote.exists) return false
        // 1. our own not-yet-acknowledged local write, echoed by the offline cache.
        if (remote.hasPendingWrites) return false
        // 2. we already hold exactly this content (pushed or pulled it).
        if (remote.contentHash != null && remote.contentHash == state.lastSyncedHash) return false
        // 3. our own acknowledged write coming back.
        if (remote.deviceId == state.deviceId && remote.revision <= state.lastKnownRevision) return false
        return true
    }

    /**
     * v0.5.1 §N: the same three-layer guard, applied per **month doc** instead of to one global
     * blob. Delegates to [shouldApply] so there is exactly one guard implementation and
     * `EchoGuardTest` still covers the logic; only the "hash we already hold" input differs —
     * it is that month's entry from `SyncStateStore.monthHashes` rather than the global hash.
     */
    fun shouldApplyMonth(
        remote: RemoteDoc,
        knownHashForMonth: String?,
        deviceId: String,
        knownRevision: Long
    ): Boolean = shouldApply(remote, SyncSnapshot(knownHashForMonth, deviceId, knownRevision))

    enum class BootstrapAction {
        /** Just attach the snapshot listener; nothing to move. */
        ATTACH_ONLY,
        /** First device — push local up. */
        PUSH_LOCAL,
        /** Reinstall / second device — pull cloud down. */
        PULL_REMOTE,
        /** Both sides non-empty and different — show the D2 prompt. */
        CONFLICT
    }

    /**
     * First-sign-in bootstrap decision (the §4 `when` block). [promptShown] is
     * `syncState.conflictPromptShownForUid == uid` — after the first resolution, last-write-wins
     * takes over and we stop prompting.
     *
     * v0.5.1 §N: the signature is unchanged, but [hashesEqual] now means "the definitions hash
     * matches **and** every month hash matches" — see `CloudSyncRepository.bootstrap`.
     */
    fun decideBootstrap(
        localEmpty: Boolean,
        remoteExists: Boolean,
        hashesEqual: Boolean,
        promptShown: Boolean
    ): BootstrapAction = when {
        !remoteExists && localEmpty -> BootstrapAction.ATTACH_ONLY
        !remoteExists -> BootstrapAction.PUSH_LOCAL
        localEmpty -> BootstrapAction.PULL_REMOTE
        hashesEqual -> BootstrapAction.ATTACH_ONLY
        promptShown -> BootstrapAction.ATTACH_ONLY   // last-write-wins from here on
        else -> BootstrapAction.CONFLICT
    }
}

// ---------------------------------------------------------------------------------------------
// v0.5.3 Phase 1 pure decision helpers — extracted so the sync engine's new races are unit-
// testable without a Room / Firebase harness (MASTER_FIX_PLAN §1.10).
// ---------------------------------------------------------------------------------------------

/**
 * v0.5.3 Phase 1 (S2) belt-and-braces: refuse a first-sign-in PUSH_LOCAL when the resident Room
 * data belongs to a *different* account than the one now signing in. D3's sign-out wipe should
 * make this unreachable in practice (local is empty after a clean sign-out); this catches a crash
 * between sign-out and wipe.
 */
internal fun refusePushLocalForForeignUid(
    residentUid: String?,
    signInUid: String,
    localEmpty: Boolean
): Boolean = residentUid != null && residentUid != signInUid && !localEmpty

/**
 * v0.5.3 Phase 1 (S10): `doPush` may clear `pendingPush` only when no tracked user edit landed
 * between the export snapshot ([seenDirty]) and the commit ([nowDirty]).
 */
internal fun shouldClearPending(seenDirty: Int, nowDirty: Int): Boolean = seenDirty == nowDirty

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 4 (S-1, Critical, D3 option 1): `bootstrap()` must not
 * compare against a month the device has legitimately evicted (`evictStaleMonths` — see
 * `SyncStateStore.hydratedMonths`). Filtering `remoteMonthHashes` down to [residentSet] before the
 * equality check is what turns "remote has months local doesn't" from a permanent CONFLICT into a
 * correct ATTACH_ONLY. Mirrors `doPush`'s existing `knownResident` pattern. Pure —
 * see `ResidentMonthHashesTest`.
 */
internal fun residentMonthHashes(
    remoteMonthHashes: Map<String, String>,
    residentSet: Set<String>
): Map<String, String> = remoteMonthHashes.filterKeys { it in residentSet }

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-10): whether [decideBootstrap]'s `promptShown`
 * should be true — i.e. whether this uid's conflict counts as "already resolved" so the
 * last-write-wins ATTACH_ONLY branch applies instead of re-prompting. Previously this was just
 * `conflictPromptShownForUid == uid`, meaning ANY resolution, ever, permanently suppressed
 * re-prompting for that uid — even for a completely unrelated LATER divergence. Scoping to the
 * remote revision at resolution time means a genuinely NEW remote change (a different revision)
 * re-opens the possibility of a fresh CONFLICT prompt. Pure — see `BootstrapDecisionTest`.
 */
internal fun conflictAlreadyResolved(
    promptShownForUid: String?,
    promptShownRevision: Long,
    currentUid: String,
    currentRemoteRevision: Long
): Boolean = promptShownForUid == currentUid && promptShownRevision == currentRemoteRevision

/**
 * v0.5.3 Phase 1 (S4): after applying a remote parent/month, re-arm `pendingPush` when the local
 * re-export no longer matches what was just applied — an unpushed local edit is still outstanding.
 */
internal fun shouldRearmPendingAfterImport(appliedHash: String, reExportHash: String): Boolean =
    appliedHash != reExportHash

/**
 * v0.5.3 Phase 2 (S1): read the month-hash summary — prefer the parent doc's `monthHashes` map
 * field ([parentField]); fall back to the per-doc `months` collection scan ([scanFallback]) only
 * when the field is absent or empty (an old-layout parent doc, or the first push by a pre-0.5.3
 * client). Pure — [ParentMonthHashesTest].
 */
internal fun readMonthHashes(
    parentField: Map<String, String>?,
    scanFallback: () -> Map<String, String>
): Map<String, String> =
    parentField?.takeIf { it.isNotEmpty() } ?: scanFallback()

/**
 * v0.5.3 Phase 3 (finding 19 / S16): while a push or pull is suppressed — a dismissed D2 conflict
 * (`dismissConflict`), or an in-flight `deleteRemoteDoc` — the sync row must read **paused**, not
 * fall back to a stale `Idle`. Returns [SyncStatus.Paused] when [suppressed], else [fallback].
 * Pure — [SyncPausedTransitionTest].
 */
internal fun statusWhilePaused(suppressed: Boolean, fallback: SyncStatus): SyncStatus =
    if (suppressed) SyncStatus.Paused else fallback

/** v0.5.3 Phase 1 (S7): the three outcomes of [CloudSyncRepository.flushPendingPush]. */
enum class FlushOutcome { SUCCESS_NOOP, RETRY_NO_UID, PROCEED }

/**
 * v0.5.3 Phase 1 (S7): a `SyncFlushWorker` run with nothing pending is a real success; one with a
 * pending push but no resolved uid must `Result.retry()`, not report success and consume the
 * unique work.
 */
internal fun flushOutcome(pending: Boolean, uidKnown: Boolean): FlushOutcome = when {
    !pending -> FlushOutcome.SUCCESS_NOOP
    !uidKnown -> FlushOutcome.RETRY_NO_UID
    else -> FlushOutcome.PROCEED
}

/**
 * v0.5.3 Phase 1 (S8): split a run of month writes into commit-sized chunks, bounded by BOTH the
 * Firestore batch-write limit ([maxWrites]) and a byte ceiling ([maxBytes], ~8 MiB — headroom
 * under Firestore's 10 MiB hard commit cap). [months] is `(monthKey, gzippedBytes)` in write
 * order. The caller has already dropped the truly-oversized (>1 MiB) months, so a single entry
 * over [maxBytes] here still gets its own chunk rather than being lost.
 */
internal fun chunkByBytes(
    months: List<Pair<String, Int>>,
    maxBytes: Long,
    maxWrites: Int
): List<List<String>> {
    val out = ArrayList<List<String>>()
    var cur = ArrayList<String>()
    var curBytes = 0L
    for ((month, bytes) in months) {
        if (cur.isNotEmpty() && (cur.size >= maxWrites || curBytes + bytes > maxBytes)) {
            out += cur
            cur = ArrayList()
            curBytes = 0L
        }
        cur += month
        curBytes += bytes
    }
    if (cur.isNotEmpty()) out += cur
    return out
}
