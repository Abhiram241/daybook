package com.daybook.app.data.sync

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.daybook.app.BuildConfig
import com.daybook.app.data.ExportImportRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.retentionCutoffMillis
import com.daybook.app.data.auth.AuthRepository
import com.daybook.app.data.auth.AuthState
import com.daybook.app.data.auth.awaitCompat
import com.daybook.app.data.backup.DaybookBackup
import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.util.work.SyncFlushWorker
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What the account screen's sync row renders. */
sealed interface SyncStatus {
    data object Disabled : SyncStatus          // signed out
    data class Idle(val lastSyncedAtMillis: Long) : SyncStatus
    data object Syncing : SyncStatus
    data object Offline : SyncStatus
    data class Error(val message: String?) : SyncStatus
    /**
     * v0.5.3 Phase 3 (finding 19 / S16): sync is fully halted for the session — a dismissed D2
     * conflict, or an in-flight `deleteRemoteDoc`. Distinct from [Idle], which meant "up to date".
     */
    data object Paused : SyncStatus
}

/**
 * v0.5.3 Phase 6 (D2): outcome of [CloudSyncRepository.hydrateRange] — the pre-flight that pulls
 * every cloud month overlapping a date-range export into Room before the file is written.
 *
 *  - [Ok]        — every month in range is resident (or the account is signed out).
 *  - [NoAccount] — signed out: nothing to hydrate, all local data is already complete.
 *  - [Offline]   — [month] could not be reached; **nothing was written**, the caller must abort
 *                  the export and surface a "connect and retry" message rather than a truncated file.
 */
sealed interface HydrateResult {
    data object Ok : HydrateResult
    data object NoAccount : HydrateResult
    data class Offline(val month: String) : HydrateResult
}

/** Concrete both-sides-non-empty counts for the D2 conflict dialog. */
data class ConflictInfo(
    val localHabits: Int,
    val localDays: Int,
    val remoteHabits: Int,
    val remoteDays: Int
)

/**
 * Cloud sync engine — **month-partitioned** as of v0.5.1 §N.
 *
 * **Invariant: Room is the source of truth.** Firestore holds a derived, gzipped view of it:
 *
 * ```
 * users/{uid}                    definitions (Blob), definitionsHash, revision, updatedAt,
 *                                deviceId, formatVersion = 3, appVersion
 * users/{uid}/months/{YYYY-MM}   payload (Blob of that month's List<DayEntry>), contentHash,
 *                                revision, updatedAt, deviceId
 * ```
 *
 * Why this shape (decisions 1–4):
 *  - the parent doc is written **only when the definitions change**, so answering a reminder no
 *    longer rewrites the whole definitions blob;
 *  - history is one doc per **local** calendar month, so a write touches one month, not the
 *    user's entire past, and a reinstall does not download years of it to open the app;
 *  - only the current and previous month are hydrated into Room (SD-3a); anything older is
 *    fetched on demand by [ensureMonthHydrated] and evicted again once its hash matches the cloud.
 *
 * **v0.5's single-blob cloud data is discarded, by design** (decision 4). `formatVersion` 3 marks
 * the new layout and the first parent push is a `set()`, which drops the old `payload` field.
 * There is no migration path and none is wanted.
 *
 * Offline-first (R9) still holds for everything here: all Firebase calls are failure-inert and
 * none are on the launch path. (The *sign-in gate* added in §D is a deliberate, separate reversal.)
 */
@Singleton
class CloudSyncRepository @Inject constructor(
    private val auth: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val exportImport: ExportImportRepository,
    private val database: AppDatabase,
    private val scheduler: OccurrenceScheduler,
    private val syncState: SyncStateStore,
    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 5 (S-4): wipeLocalForSignOut needs these two to reset
    // the identity columns the 8-table Room wipe deliberately never touched. Neither depends back
    // on CloudSyncRepository (both take only AppDatabase/Context), so no Hilt cycle risk.
    private val settingsRepository: com.daybook.app.data.AppSettingsRepository,
    private val profilePhotoStore: com.daybook.app.data.ProfilePhotoStore,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushMutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _conflict = MutableStateFlow<ConflictInfo?>(null)
    val conflict: StateFlow<ConflictInfo?> = _conflict.asStateFlow()

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    /**
     * v0.5.3 Phase 3 (A10 partial): a debounced "re-arm alarms" signal. `applyRemoteMonth` /
     * `applyRemoteParent` used to call `scheduler.syncAll()` (O(active items) transactions) once
     * **per month doc** during a multi-month hydration. They now coalesce into a single sweep after
     * the listener's batch drains. Combined with the A2 fix this kills the
     * "20 items × 10 sweeps = 200 armed alarms" case.
     */
    private val resyncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private var trackerObserver: InvalidationTracker.Observer? = null
    private var parentReg: ListenerRegistration? = null
    private var monthsReg: ListenerRegistration? = null
    private var currentUid: String? = null
    /** True while a D2 prompt is unanswered — sync stays paused for the session. */
    private var conflictPaused = false
    private var started = false

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-6): the "fourth echo guard" (`hydrating`, an
    // AtomicInteger raised around every hydration/eviction Room write, checked by the tracker
    // observer below) is DELETED. It never actually suppressed anything: Room's
    // `InvalidationTracker` callback fires asynchronously relative to the write, so by the time
    // `onInvalidated` runs, `hydrating` has typically already been decremented back to 0 by the
    // `finally` block that raised it — an inherent race, not an occasional one. The guard's own old
    // KDoc already said as much in its "belt and braces" framing: the REAL correctness guarantee
    // was always the hash diff (the hydrated month's `contentHash` lands in
    // `SyncStateStore.monthHashes` as part of the same write, so even a "leaked" invalidation that
    // marks `pendingPush` produces a hash-equal diff in `doPush` and pushes nothing). Removing the
    // guard costs one wasted `exportBackup()` + hash per remote change — the plan's own assessment
    // of the only real cost — in exchange for deleting dead, misleadingly-documented complexity.
    // Every former `hydrating { ... }` call site below is simply unwrapped.

    /**
     * v0.5.3 Phase 1 (S10): bumped alongside `syncState.pendingPush = true` on every tracked
     * invalidation, so [doPush] / [applyRemoteMonth] / [applyRemoteParent] can tell whether a user
     * edit landed mid-operation and must NOT have its `pendingPush` cleared out from under it.
     */
    private val dirtyCounter = AtomicInteger(0)

    /** Months already fetched (or attempted) this session, so a week-strip scrub can't storm. */
    private val hydrationAttempted: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * v0.5.3 Phase 1 (S3): months the user is actively viewing (pinned by `HomeViewModel` on month
     * entry, unpinned on leave, and by [ensureMonthHydrated] on success). [evictStaleMonths] never
     * evicts a pinned month — eviction racing the user's navigation is exactly S3.
     */
    private val pinnedMonths: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * v0.5.3 Phase 6 (D2, S3-adjacent): months pinned for the duration of a date-range export, so
     * a concurrent [runMaintenance] eviction cannot drop a month between [hydrateRange] fetching it
     * and the file being written. Kept separate from [pinnedMonths] (the user's on-screen month) so
     * releasing an export's pins can never unpin the month Home is showing. Cleared by
     * [endRangeExport] in the caller's `finally`.
     */
    private val exportRangePins: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * v0.5.3 Phase 1 (S9): months whose gzipped payload is over the 1 MiB hard doc limit. They are
     * skipped by [doPush] (a `batch.commit()` including one fails permanently) and retried only
     * once a later re-export drops them back under the limit.
     */
    private val oversizedMonths: MutableSet<String> = mutableSetOf()

    /** Call once (from DaybookApplication). Idempotent. */
    fun start() {
        if (started) return
        started = true
        scope.launch { auth.state.collect { onAuthState(it) } }
        startDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun startDebounce() {
        scope.launch {
            changes.debounce(DEBOUNCE_MS).collect {
                runCatching { pushMutex.withLock { doPush(force = false) } }
            }
        }
        // v0.5.3 Phase 3 (A10 partial): one coalesced alarm re-arm after a hydration batch drains.
        scope.launch {
            resyncRequests.debounce(RESYNC_DEBOUNCE_MS).collect {
                runCatching { scheduler.syncAll() }
            }
        }
    }

    /** v0.5.3 Phase 3 (A10 partial): request a debounced `scheduler.syncAll()`. */
    private fun requestResync() { resyncRequests.tryEmit(Unit) }

    // ------------------------------------------------------------------ auth lifecycle

    private suspend fun onAuthState(st: AuthState) {
        val signedIn = st as? AuthState.SignedIn
        if (signedIn == null) {
            if (currentUid != null) {
                // v0.5.3 Phase 1 (S2/D3): a GENUINE sign-out (`AuthState.SignedOut`) from a known
                // uid wipes all local data so the next account starts clean. A transient
                // `AuthState.Loading` (never emitted after SignedIn in practice, but guard anyway)
                // must NOT wipe — it would erase a solo user's data on nothing.
                val genuineSignOut = st is AuthState.SignedOut
                teardown()
                if (genuineSignOut) wipeLocalForSignOut() else syncState.clearForSignOut()
            }
            _status.value = SyncStatus.Disabled
            return
        }
        if (signedIn.uid == currentUid) return
        currentUid = signedIn.uid
        conflictPaused = false
        _conflict.value = null
        hydrationAttempted.clear()
        attachTracker()
        runCatching { bootstrap(signedIn.uid) }
            .onFailure { Log.w(TAG, "bootstrap failed", it); _status.value = SyncStatus.Offline }
        attachSnapshotListeners(signedIn.uid)
    }

    /**
     * v0.5.3 Phase 1 (D3): the sign-out local wipe. Reachable ONLY from a genuine
     * `AuthState.SignedOut` transition where `currentUid` was non-null (see [onAuthState]) — never
     * from `AuthState.Loading`, never on first launch. Also covers token-revocation sign-out:
     * `AuthRepository`'s `IdTokenListener` calls `auth.signOut()`, which flows through here.
     *
     * Order matters: cancel every armed alarm/notification FIRST (that path needs the occurrence
     * rows to find the alarms), then wipe all 8 data tables in one transaction, then reset the
     * sync bookkeeping.
     */
    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-5): the shared transactional wipe body — the
     * single implementation both [wipeLocalForSignOut] (sign-out) and
     * `AccountViewModel.deleteAccount`'s "also erase local data" option now call. Previously
     * `AccountViewModel` had its own second, weaker, NON-transactional copy
     * (`AccountViewModel.wipeLocalData`) missing `customCategoryDao`/`customPromptDao` deletes,
     * `scheduler.cancelAllReminders()`, and `syncState.reset()` — deleted outright in this phase.
     */
    suspend fun wipeAllLocalData() {
        runCatching { scheduler.cancelAllReminders() }
        database.withTransaction {
            database.habitEventDao().deleteAll()
            database.foodMedEventDao().deleteAll()
            database.habitOccurrenceDao().deleteAll()
            database.foodMedOccurrenceDao().deleteAll()
            database.habitDao().deleteAll()
            database.foodMedTaskDao().deleteAll()
            database.customCategoryDao().deleteAll()
            database.customPromptDao().deleteAll()
        }
        syncState.reset()
        hydrationAttempted.clear()
        pinnedMonths.clear()
        exportRangePins.clear()   // v0.5.3 Phase 6 (D2)
        oversizedMonths.clear()
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 5 (S-4): the 8-table transaction above never
        // touched `app_settings` (deliberate — every device-scoped preference on it, like
        // accent/font/quiet-hours, must survive a sign-out). But three columns on that same
        // row are IDENTITY, not device preference, and must be reset here or a second account
        // signing in on a shared device inherits the first account's name/photo and skips
        // onboarding entirely.
        runCatching {
            settingsRepository.setUserName("")
            settingsRepository.setProfilePhotoPath(null)
            settingsRepository.setOnboardingCompleted(false)
            profilePhotoStore.clear()
        }.onFailure { Log.w(TAG, "wipeAllLocalData: identity reset failed", it) }
        Log.i(TAG, "wipeAllLocalData: all local data cleared, alarms cancelled, sync state reset")
    }

    private suspend fun wipeLocalForSignOut() = wipeAllLocalData()

    private fun teardown() {
        parentReg?.remove(); parentReg = null
        monthsReg?.remove(); monthsReg = null
        trackerObserver?.let { database.invalidationTracker.removeObserver(it) }
        trackerObserver = null
        currentUid = null
        conflictPaused = false
        _conflict.value = null
        hydrationAttempted.clear()
    }

    private fun attachTracker() {
        if (trackerObserver != null) return
        val obs = object : InvalidationTracker.Observer(DATA_TABLES) {
            override fun onInvalidated(tables: Set<String>) {
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-6): the `hydrating.get() > 0` guard
                // that used to sit here is gone (see the comment above the deleted `hydrating`
                // AtomicInteger) — it never reliably suppressed a hydration write's own
                // invalidation anyway. `doPush`'s hash diff is what actually stops a redundant
                // upload; marking `pendingPush` an extra time here is at most one wasted push
                // attempt that finds nothing to send.
                syncState.pendingPush = true
                dirtyCounter.incrementAndGet()   // v0.5.3 Phase 1 (S10)
                changes.tryEmit(Unit)
            }
        }
        trackerObserver = obs
        database.invalidationTracker.addObserver(obs)
    }

    // ------------------------------------------------------------------ bootstrap (first sign-in)

    private suspend fun bootstrap(uid: String) {
        _status.value = SyncStatus.Syncing
        // §1 D1: the object straight from Room — was serialise-to-pretty-String-and-parse-back.
        //
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 13 (C-5, Medium): a genuine export failure (e.g. a
        // SQLite error) used to fold into `localDefsHash = null`, which forces `hashesEqual = false`
        // below regardless of what the remote actually holds — the same code path a REAL hash
        // mismatch takes, landing in CONFLICT and forcing the user through a data-loss-risk dialog
        // over what was actually an internal error, not a genuine divergence. Now: an export
        // failure here aborts bootstrap entirely (no `decideBootstrap` call at all) and reports
        // `SyncStatus.Error` — the next trigger (any subsequent sync tick) just retries.
        val localBackupResult = runCatching { exportImport.exportBackup() }
        val localBackup = localBackupResult.getOrNull()
        if (localBackupResult.isFailure) {
            val t = localBackupResult.exceptionOrNull()
            Log.w(TAG, "bootstrap: exportBackup failed — aborting without a conflict decision", t)
            t?.let { com.daybook.app.util.recordUnhandledException(it) }
            _status.value = SyncStatus.Error(t?.message)
            return
        }
        val localEmpty = isLocalEmpty()

        val parent = fetchDoc(docRef(uid))
        val remoteExists = parent?.exists() == true && parent.get(F_DEFINITIONS) is Blob
        val remoteMonthHashes = fetchRemoteMonthHashes(uid, parent)   // v0.5.3 Phase 2 (S1)

        val localDefsHash = localBackup?.definitions?.let { ContentHash.ofDefinitions(it) }
        val localMonthHashes =
            MonthPartitioner.hashes(MonthPartitioner.partition(localBackup?.days.orEmpty()))

        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 4 (S-1, Critical, D3 option 1): compare only the
        // RESIDENT months, not every month ever pushed. Without this, any month `evictStaleMonths`
        // has dropped (normal, expected behavior — see SyncStateStore.hydratedMonths) makes every
        // subsequent cold start see "remote has months local doesn't" and permanently wedge into
        // CONFLICT. `residentSet` mirrors doPush's existing `knownResident` pattern (~line 468).
        val residentSet = syncState.hydratedMonths + localMonthHashes.keys + MonthPartitioner.recentMonths()
        val comparableRemoteMonthHashes = residentMonthHashes(remoteMonthHashes, residentSet)

        // §N: "hashes equal" is now definitions-equal AND every-RESIDENT-month-equal.
        val hashesEqual = remoteExists &&
            localDefsHash != null &&
            parent?.getString(F_DEFS_HASH) == localDefsHash &&
            comparableRemoteMonthHashes == localMonthHashes

        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-10): scoped to the remote revision at the
        // moment of the last resolution too — see conflictAlreadyResolved's KDoc.
        val promptShown = conflictAlreadyResolved(
            syncState.conflictPromptShownForUid, syncState.conflictPromptShownRevision,
            uid, parent?.getLong(F_REV) ?: 0L
        )

        // v0.5.3 Phase 1 (S2): belt-and-braces. D3's sign-out wipe makes `localEmpty` true on a
        // normal sign-out -> sign-in, so this only bites if that wipe somehow did not run (a crash
        // in the sign-out -> wipe window). Refuse to PUSH_LOCAL another account's resident data
        // into this one; force the conflict prompt instead.
        if (refusePushLocalForForeignUid(syncState.lastSyncedUid, uid, localEmpty)) {
            Log.w(TAG, "resident data belongs to ${syncState.lastSyncedUid}, signing into $uid — refusing PUSH_LOCAL")
            raiseConflict(uid, parent, localBackup, remoteMonthHashes)
            return
        }

        when (SyncLogic.decideBootstrap(localEmpty, remoteExists, hashesEqual, promptShown)) {
            SyncLogic.BootstrapAction.ATTACH_ONLY -> {
                if (remoteExists) {
                    syncState.definitionsHash = parent?.getString(F_DEFS_HASH)
                    syncState.monthHashes = remoteMonthHashes
                    syncState.hydratedMonths = localMonthHashes.keys + MonthPartitioner.recentMonths()
                    syncState.lastKnownRevision = parent?.getLong(F_REV) ?: 0L
                }
                markIdle()
            }
            SyncLogic.BootstrapAction.PUSH_LOCAL ->
                pushMutex.withLock { doPush(force = true, prefetched = localBackup) }
            SyncLogic.BootstrapAction.PULL_REMOTE ->
                pushMutex.withLock { pullRemote(uid, parent, remoteMonthHashes) }
            SyncLogic.BootstrapAction.CONFLICT ->
                raiseConflict(uid, parent, localBackup, remoteMonthHashes)
        }
        // v0.5.3 Phase 1 (S2): the resident data now belongs to this uid (CONFLICT leaves it
        // untouched — resolveConflict sets it once the user picks a side).
        if (_conflict.value == null) syncState.lastSyncedUid = uid
    }

    /**
     * Reinstall / second device. Pull the parent's definitions, then hydrate **only** the recent
     * months — the rest stays lazy. This is the case that most benefits from partitioning: opening
     * the app no longer waits on years of history.
     */
    private suspend fun pullRemote(
        uid: String,
        parent: DocumentSnapshot?,
        remoteMonthHashes: Map<String, String>
    ) {
        if (parent == null) { markIdle(); return }
        if (!applyRemoteParent(parent)) return
        syncState.monthHashes = remoteMonthHashes
        val recent = MonthPartitioner.recentMonths()
        for (month in recent.intersect(remoteMonthHashes.keys)) {
            val snap = fetchDoc(monthRef(uid, month)) ?: continue
            applyRemoteMonth(month, snap)
        }
        syncState.hydratedMonths = recent
        syncState.lastKnownRevision = parent.getLong(F_REV) ?: 0L
        requestResync()   // v0.5.3 Phase 3 (A10 partial): one coalesced sweep, not one per month
        markIdle()
    }

    // ------------------------------------------------------------------ push

    /** Public manual trigger ("Sync now"). */
    suspend fun syncNow() {
        val uid = currentUid ?: return
        pushMutex.withLock {
            // v0.5.3 Phase 1 (S12): pull BEFORE push on a manual "Sync now". A push-then-pull
            // reconnect ordering maximises the S15 last-writer-wins window; pulling first shrinks it.
            val parent = runCatching { docRef(uid).get(Source.SERVER).awaitCompat() }.getOrNull()
            if (parent != null && parent.exists() && shouldApplyParent(parent)) {
                applyRemoteParent(parent)
            }
            // Only months this device actually keeps resident. Pulling every month doc here would
            // undo eviction on every "Sync now" tap, re-hydrating years of history the app just
            // deliberately dropped — and the next push would evict it again.
            val resident = syncState.hydratedMonths + MonthPartitioner.recentMonths()
            val months = runCatching { monthsRef(uid).get(Source.SERVER).awaitCompat() }.getOrNull()
            months?.documents?.forEach { d ->
                if (d.id in resident && shouldApplyMonthDoc(d)) applyRemoteMonth(d.id, d)
            }
            doPush(force = false)
            markIdle()
        }
    }

    /**
     * One export -> partition by local month -> per-month hash diff -> batched write of **only**
     * the month docs that changed, plus the parent doc only if the definitions changed.
     */
    private suspend fun doPush(
        force: Boolean,
        prefetched: DaybookBackup? = null,
        // v0.5.3 Phase 6 (S5c): only an explicit user action ("replace the cloud with this device")
        // may let a diff-driven month DELETION through. The debounced/automatic path never can — an
        // evicted or range-import-trimmed month is indistinguishable from an emptied one, and the
        // conservative choice is to keep the cloud doc.
        allowMonthDeletions: Boolean = false
    ) {
        val uid = currentUid ?: return
        // v0.5.3 Phase 3 (finding 19 / S16): surface the pause, don't fall through silently to a
        // stale Idle. Covers both a dismissed D2 conflict and an in-flight deleteRemoteDoc.
        if (conflictPaused) { _status.value = SyncStatus.Paused; return }
        // v0.5.3 Phase 1 (S10): snapshot the dirty counter BEFORE the export. If a tracked user
        // edit lands between here and the commit, `dirtyCounter` moves and we must not clear
        // `pendingPush` — the debounced re-emit will push the newer edit.
        val seenDirty = dirtyCounter.get()
        // §1 D1: object build, no serialise/parse round trip. `prefetched` reuses bootstrap's
        // single export so a first sign-in does not export the whole history twice.
        val backup = prefetched ?: runCatching { exportImport.exportBackup() }.getOrNull() ?: return
        // Preserved from v0.5: never push an empty-definitions payload over real cloud data.
        if (isLocalEmpty()) return

        val defsHash = ContentHash.ofDefinitions(backup.definitions)
        val byMonth = MonthPartitioner.partition(backup.days)
        val curHashes = MonthPartitioner.hashes(byMonth)

        // Only months actually RESIDENT on this device may be considered for deletion. An evicted
        // month is absent from `curHashes` but is emphatically not "emptied locally" — treating it
        // as one would delete the user's cloud history. See SyncStateStore.hydratedMonths.
        val resident = syncState.hydratedMonths + curHashes.keys + MonthPartitioner.recentMonths()
        val knownResident = syncState.monthHashes.filterKeys { it in resident }
        val changedRaw = MonthPartitioner.changedMonths(curHashes, knownResident)
        // v0.5.3 Phase 6 (S5c): drop diff-driven month deletions unless this is a user-initiated
        // "replace the cloud" push. `pushDeletesAllowed` is the pure decision (RangeImportNonDestructiveTest).
        val changed = if (com.daybook.app.data.pushDeletesAllowed(
                changedRaw.associateWith { byMonth[it] }, allowMonthDeletions
            )
        ) {
            changedRaw
        } else {
            val dropped = changedRaw.filter { byMonth[it] == null }
            if (dropped.isNotEmpty()) {
                Log.w(TAG, "doPush: keeping cloud doc(s) for ${dropped} — diff-driven delete not user-initiated (S5c)")
            }
            changedRaw.filter { byMonth[it] != null }.toSet()
        }
        val defsChanged = force || defsHash != syncState.definitionsHash

        if (!defsChanged && changed.isEmpty()) {
            if (shouldClearPending(seenDirty, dirtyCounter.get())) syncState.pendingPush = false
            markIdle()
            return
        }

        _status.value = SyncStatus.Syncing
        val rev = syncState.lastKnownRevision + 1
        syncState.pendingPush = true

        try {
            // v0.5.3 Phase 1 (S8/S9): gzip each month up front. A month over the 1 MiB hard doc
            // limit is SKIPPED (a `batch.commit()` containing it fails permanently) and parked in
            // `oversizedMonths` until a later re-export shrinks it. Everything else is written,
            // chunked by BOTH the 450-write batch limit AND an ~8 MiB byte ceiling per commit.
            data class MonthWrite(val month: String, val gz: ByteArray?)
            val writes = ArrayList<MonthWrite>()
            var sawOversized = false
            for (month in changed) {
                val days = byMonth[month]
                if (days == null) {
                    writes += MonthWrite(month, null)   // a delete()
                    continue
                }
                val gz = PayloadCodec.gzipString(MonthPartitioner.encodeDays(days))
                if (gz.size > HARD_DOC_LIMIT) {
                    oversizedMonths += month
                    sawOversized = true
                    Log.w(TAG, "month $month is ${gz.size}B — over the 1 MiB cap, skipping")
                    continue
                }
                oversizedMonths -= month
                if (gz.size > SOFT_SIZE_WARN) {
                    Log.w(TAG, "month $month is ${gz.size}B — approaching the 1 MiB cap")
                }
                writes += MonthWrite(month, gz)
            }

            // v0.5.3 Phase 2 (S1): the month-hash map as it will stand AFTER this push — mirrors the
            // post-commit `newHashes` merge below, restricted to the months actually written here
            // (an oversized-skipped month keeps its old hash). Written onto the parent doc.
            val projectedHashes = syncState.monthHashes.toMutableMap().apply {
                for (w in writes) {
                    val h = if (w.gz == null) null else curHashes[w.month]
                    if (h != null) put(w.month, h) else remove(w.month)
                }
            }

            val writeByMonth = writes.associateBy { it.month }
            val groups = chunkByBytes(
                writes.map { it.month to (it.gz?.size ?: 0) },
                MAX_COMMIT_BYTES,
                BATCH_CHUNK
            )
            val batches = ArrayList<WriteBatch>()
            if (groups.isEmpty()) {
                if (defsChanged) {
                    firestore.batch()
                        .also { it.set(docRef(uid), parentData(backup.definitions, defsHash, rev, projectedHashes)) }
                        .let { batches += it }
                }
            } else {
                groups.forEachIndexed { index, group ->
                    val batch = firestore.batch()
                    if (index == 0) {
                        if (defsChanged) {
                            batch.set(docRef(uid), parentData(backup.definitions, defsHash, rev, projectedHashes))
                        } else {
                            // v0.5.3 Phase 2 (S1): a pure-history push still refreshes the parent's
                            // month-hash summary (one extra doc write) so the cold-start read stays a
                            // single document. `update` leaves `definitions` / `formatVersion` alone.
                            batch.update(
                                docRef(uid),
                                mapOf(
                                    F_MONTH_HASHES to projectedHashes,
                                    F_REV to rev,
                                    F_UPDATED to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    for (month in group) {
                        val w = writeByMonth.getValue(month)
                        if (w.gz == null) {
                            batch.delete(monthRef(uid, month))
                        } else {
                            batch.set(
                                monthRef(uid, month),
                                mapOf(
                                    F_PAYLOAD to Blob.fromBytes(w.gz),
                                    F_HASH to curHashes.getValue(month),
                                    F_REV to rev,
                                    F_DEVICE to syncState.deviceId,
                                    F_UPDATED to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    batches += batch
                }
            }
            // v0.5.3 Phase 7 (audit S11 — sub-batch interruption, informational): each
            // `batch.commit()` is atomic (Firestore guarantees all-or-nothing per WriteBatch, so
            // no month doc is ever half-written), but the loop is NOT atomic across batches. A
            // process kill between commits leaves the cloud with a mix of rev and rev-1 month
            // docs. This is self-healing: the local `syncState.monthHashes` / `lastKnownRevision`
            // are only advanced AFTER the loop completes, so on relaunch `bootstrap` recomputes
            // `localMonthHashes`, sees the un-pushed months still dirty, and re-pushes them. Worst
            // case is one CONFLICT prompt on relaunch if >450 months changed in the interrupted
            // run (a partial higher-rev set on the cloud). No data loss, no manual recovery.
            for (batch in batches) batch.commit().awaitCompat()

            if (defsChanged) syncState.definitionsHash = defsHash
            val pushedMonths = writes.mapTo(HashSet()) { it.month }
            val newHashes = syncState.monthHashes.toMutableMap()
            for (month in changed) {
                if (month !in pushedMonths) continue   // oversized-skip: leave its old hash intact
                val h = curHashes[month]
                if (h != null) newHashes[month] = h else newHashes.remove(month)
            }
            syncState.monthHashes = newHashes
            syncState.hydratedMonths = curHashes.keys + MonthPartitioner.recentMonths()
            syncState.lastKnownRevision = rev
            // v0.5.3 Phase 1 (S10): only clear `pendingPush` if no user edit landed during the
            // push. An oversized-skipped month does NOT keep the flag armed — it is retried when a
            // future edit shrinks it, not by a busy re-push loop.
            if (shouldClearPending(seenDirty, dirtyCounter.get())) syncState.pendingPush = false
            currentUid?.let { syncState.lastSyncedUid = it }
            Log.i(TAG, "pushed ${pushedMonths.size} month doc(s)${if (defsChanged) " + parent" else ""} rev=$rev")
            // v0.5.3 Phase 1 (S3): eviction is OFF the push path now — it runs in [runMaintenance]
            // (daily worker) and [onAppStop].
            if (sawOversized) {
                _status.value = SyncStatus.Error(
                    "A month is too large to sync (over 1 MB). Trim a long entry."
                )
            } else {
                markIdle()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "push failed", t)
            _status.value = if (isOffline(t)) SyncStatus.Offline else SyncStatus.Error(t.message)
        }
    }

    private fun parentData(
        defs: Definitions,
        defsHash: String,
        rev: Long,
        // v0.5.3 Phase 2 (S1): the full month-hash summary, written onto the parent doc in the same
        // batch as the month docs so a cold start reads ONE document instead of a full `months`
        // collection scan. Additive field — a pre-0.5.3 client simply ignores it.
        monthHashes: Map<String, String>
    ): Map<String, Any> = mapOf(
        F_DEFINITIONS to Blob.fromBytes(
            PayloadCodec.gzipString(MonthPartitioner.encodeDefinitionsJson(defs))
        ),
        F_DEFS_HASH to defsHash,
        F_REV to rev,
        F_DEVICE to syncState.deviceId,
        F_UPDATED to FieldValue.serverTimestamp(),
        F_FORMAT to FORMAT_VERSION,
        F_APPVER to BuildConfig.VERSION_NAME,
        F_MONTH_HASHES to monthHashes
    )

    /**
     * SD-3(a) eviction. Drops occurrence rows for months outside the hydrated window, and **only**
     * when that month's local hash already matches what the cloud holds — unpushed local data is
     * never evicted. A month whose hash does not match is left resident for the next push to
     * reconcile.
     */
    internal suspend fun evictStaleMonths(curHashes: Map<String, String>) {
        if (syncState.pendingPush) return
        val recent = MonthPartitioner.recentMonths()
        val known = syncState.monthHashes
        // v0.5.3 Phase 1 (S3): never evict a month the user is actively viewing (pinnedMonths), or
        // one an in-flight date-range export is depending on (exportRangePins — Phase 6 / D2).
        val evictable = curHashes.keys.filter {
            it !in recent && it !in pinnedMonths && it !in exportRangePins &&
                known[it] == curHashes[it]
        }
        if (evictable.isEmpty()) return
        for (month in evictable) {
            if (exportImport.evictMonth(month)) {
                Log.i(TAG, "evicted month $month (hash matches cloud)")
            }
        }
        syncState.hydratedMonths = syncState.hydratedMonths - evictable.toSet()
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-7): `monthHashes` fed S-1/Phase 4's bug by
        // growing unboundedly — every month ever pushed, never pruned on eviction. Prune it here
        // alongside `hydratedMonths`, the same pattern `onLocalDataReplaced` already uses. This is
        // NOT a second fix for S-1 (Phase 4's resident-set filter in `bootstrap()` already gets the
        // comparison right from a live remote read regardless) — it's `monthHashes` living up to
        // its actual job: a cache of hashes for RESIDENT months, not a permanent "shape of the
        // cloud" ledger.
        syncState.monthHashes = syncState.monthHashes - evictable.toSet()
        // Force a re-fetch if the user navigates back into one of these.
        hydrationAttempted.removeAll(evictable.toSet())
        reattachMonthsListener()   // v0.5.3 Phase 1 (S12): resident set shrank
    }

    /**
     * v0.5.3 Phase 1 (S3) + Phase 3 (A3 / A6): sync-side maintenance, called by the daily
     * `WindowRefreshWorker` and by [onAppStop]. Off the hot push path — nobody is watching a screen.
     *
     *  - **retention sweep** (A3/A6): delete `SHOWN` / `USER_SNOOZED` event rows older than
     *    `RETENTION_DAYS` (90). Runs even when signed OUT — that is the only thing bounding the event
     *    tables for a solo user, and `WindowRefreshWorker` runs signed-out too. Terminal
     *    `REPLIED` / `COMPLETED` / `SKIPPED` events and every occurrence row are never touched.
     *  - **month eviction** (S3): signed-in only — drops occurrence rows for months whose local
     *    hash already matches the cloud.
     *
     * v0.5.3 Phase 7 (audit A6 — residual, informational): running the retention sweep for
     * signed-out users bounds A6's unbounded-*events* case. Unbounded *occurrence* rows for a
     * user who has **never** signed in are still not swept here (eviction is signed-in only), but
     * that is a non-population under the sign-in gate — a signed-out user accrues at most the
     * rolling-window's worth of occurrences before the window worker prunes ahead of them.
     */
    suspend fun runMaintenance() {
        if (conflictPaused) return
        val cutoff = retentionCutoffMillis(System.currentTimeMillis())
        runCatching {
            database.withTransaction {
                database.habitEventDao().pruneActivityBefore(cutoff)
                database.foodMedEventDao().pruneActivityBefore(cutoff)
            }
        }.onFailure { Log.w(TAG, "retention sweep failed", it) }
        if (currentUid == null) return
        val backup = runCatching { exportImport.exportBackup() }.getOrNull() ?: return
        val curHashes = MonthPartitioner.hashes(MonthPartitioner.partition(backup.days))
        pushMutex.withLock { evictStaleMonths(curHashes) }
    }

    /** v0.5.3 Phase 1 (S3): pin/unpin the month the user is viewing so it is never evicted. */
    fun pinMonth(month: String) { pinnedMonths.add(month) }
    fun unpinMonth(month: String) { pinnedMonths.remove(month) }
    fun clearPins() { pinnedMonths.clear() }

    // ------------------------------------------------------------------ v0.5.3 Phase 6 (D2 / S5)

    /**
     * v0.5.3 Phase 6 (D2): pull every cloud month overlapping `[startMonth, endMonth]` into Room so
     * a date-range export writes a **complete** file, never a silently-truncated one.
     *
     * All-or-nothing: the first month that cannot be reached returns [HydrateResult.Offline] with
     * **nothing partially applied to the file** (each month's own hydrate is atomic, and the caller
     * writes no file on `Offline`). Reuses the Phase-1 [ensureMonthHydrated] path, so the S8b
     * "server-empty ≠ unreachable" distinction and the scoped listener re-attach both apply; a
     * month already resident (recent window, or previously hydrated) is skipped. Every touched
     * month is added to [exportRangePins] so a concurrent eviction cannot drop it before the file
     * is written — the caller MUST call [endRangeExport] in a `finally`.
     *
     * @param onProgress invoked after each month with `(done, total)` for the UI indicator.
     */
    suspend fun hydrateRange(
        startMonth: String,
        endMonth: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): HydrateResult {
        currentUid ?: return HydrateResult.NoAccount
        if (conflictPaused) return HydrateResult.Offline(startMonth)
        val months = MonthPartitioner.monthKeysInRange(startMonth, endMonth)
        if (months.isEmpty()) return HydrateResult.Ok
        val total = months.size
        var done = 0
        for (month in months) {
            if (!isMonthResident(month)) {
                runCatching { ensureMonthHydrated(month) }
                // ensureMonthHydrated is failure-inert; residency is the only reliable signal.
                if (!isMonthResident(month)) {
                    Log.w(TAG, "hydrateRange: $month unreachable — aborting, no file written")
                    return HydrateResult.Offline(month)
                }
            }
            exportRangePins.add(month)
            done++
            onProgress(done, total)
        }
        Log.i(TAG, "hydrateRange($startMonth..$endMonth): $total month(s) resident")
        return HydrateResult.Ok
    }

    /** v0.5.3 Phase 6 (D2): release the pins [hydrateRange] took. Call in the export's `finally`. */
    fun endRangeExport() {
        exportRangePins.clear()
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-8): a large date-range export can hydrate
        // (and pin) far more than 30 months. Without this, `hydratedMonths` would sit swollen until
        // the next daily `WindowRefreshWorker`/`onAppStop` maintenance tick happens to run
        // `evictStaleMonths` — proactively kick that off now instead of waiting, reusing the exact
        // same (already-safe: only evicts a hash-matching, non-pinned, non-recent month) logic
        // `runMaintenance` already runs periodically.
        scope.launch { runCatching { runMaintenance() } }
    }

    /**
     * v0.5.3 Phase 6 (S5): after a file import replaced local data, pin the sync bookkeeping to
     * **exactly** the months that file covered (plus the always-resident recent months). Without
     * this, a month left in [SyncStateStore.hydratedMonths] but absent from the imported file is
     * seen by the next `doPush`'s [MonthPartitioner.changedMonths] diff as "emptied locally" and
     * its cloud document is deleted — the audit's S5. Called by `SettingsViewModel` after both the
     * full and the range import paths.
     */
    fun onLocalDataReplaced(coveredMonths: Set<String>) {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 14 (S-13, Low): UNION with the existing
        // hydratedMonths, not a replace. Replacing narrowed the resident set to exactly the
        // imported file's months — a month that was resident BEFORE the import (its Room rows are
        // still physically present; a range-file import never touches months outside its own
        // range) but absent from `coveredMonths` would then read as "not resident," which is a
        // false signal to `doPush`'s `changedMonths` diff (its caller contract requires `known` to
        // be exactly the resident set — see MonthPartitioner.changedMonths' KDoc) that can cause a
        // spurious cloud-month deletion for data that's still right here. Simpler and strictly
        // safer than narrowing: this device's residency can only grow via an import, never shrink.
        val resident = syncState.hydratedMonths + coveredMonths + MonthPartitioner.recentMonths()
        syncState.hydratedMonths = resident
        syncState.monthHashes = syncState.monthHashes.filterKeys { it in resident }
        Log.i(TAG, "onLocalDataReplaced: hydratedMonths unioned to ${resident.sorted()}")
    }

    // ------------------------------------------------------------------ pull / listeners

    private fun attachSnapshotListeners(uid: String) {
        parentReg?.remove()
        monthsReg?.remove()

        parentReg = docRef(uid).addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "parent snapshot error", err); return@addSnapshotListener }
            if (snap == null || !snap.exists()) return@addSnapshotListener
            if (!shouldApplyParent(snap)) return@addSnapshotListener
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 0b — every other scope.launch in this file is
            // runCatching-wrapped; this one wasn't, and it goes live the moment auth flips to
            // SignedIn (i.e. "after login"), making it the single most structurally-plausible
            // candidate for the unreproduced post-login crash report.
            scope.launch {
                runCatching {
                    pushMutex.withLock {
                        if (applyRemoteParent(snap)) { requestResync(); markIdle() }
                    }
                }.onFailure { Log.e(TAG, "parent listener apply failed", it); com.daybook.app.util.recordUnhandledException(it) }
            }
        }

        monthsReg = scopedMonthsListener(uid)
    }

    /**
     * v0.5.3 Phase 1 (S12): the months listener scoped to just the RESIDENT month docs via
     * `whereIn(FieldPath.documentId(), …)` (Firestore caps `whereIn` at 30; the resident set is
     * ~3 in practice). An unscoped listener's first snapshot on a fresh install is a full download
     * of every month payload. Re-attached by [reattachMonthsListener] whenever the resident set
     * changes.
     */
    private fun scopedMonthsListener(uid: String): ListenerRegistration {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-8) — see MonthPartitioner.cappedMostRecentMonths's KDoc.
        val resident = MonthPartitioner.cappedMostRecentMonths(
            syncState.hydratedMonths + MonthPartitioner.recentMonths()
        )
        val query = if (resident.isEmpty()) monthsRef(uid)
            else monthsRef(uid).whereIn(FieldPath.documentId(), resident)
        return query.addSnapshotListener { qs, err ->
            if (err != null) { Log.w(TAG, "months snapshot error", err); return@addSnapshotListener }
            // documentChanges, not documents: otherwise every month re-processes on any change.
            qs?.documentChanges?.forEach { change ->
                if (change.type == DocumentChange.Type.REMOVED) {
                    // A month doc removed remotely. Deliberately NOT mirrored into a local delete:
                    // the one thing this engine must never do is destroy local history on an
                    // ambiguous signal. The next push reconciles it if the month is still resident.
                    Log.i(TAG, "month ${change.document.id} removed remotely — local rows kept")
                    return@forEach
                }
                val doc = change.document
                // Only months this device keeps resident (SD-3a). A remote change to an evicted
                // month is not ignored — it is simply deferred to `ensureMonthHydrated`, which
                // runs the moment the user navigates into that month. Applying it here instead
                // would silently undo eviction and defeat the point of partitioning.
                if (doc.id !in syncState.hydratedMonths + MonthPartitioner.recentMonths()) return@forEach
                if (!shouldApplyMonthDoc(doc)) return@forEach
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 0b — same rationale as attachSnapshotListeners'
                // parent listener above: this was the other unguarded scope.launch in the file.
                scope.launch {
                    runCatching {
                        pushMutex.withLock {
                            if (applyRemoteMonth(doc.id, doc)) {
                                requestResync()   // v0.5.3 Phase 3 (A10 partial)
                                markIdle()
                            }
                        }
                    }.onFailure { Log.e(TAG, "month listener apply failed", it); com.daybook.app.util.recordUnhandledException(it) }
                }
            }
        }
    }

    /**
     * v0.5.3 Phase 1 (S12): rebuild the scoped months listener after the resident set changes, so
     * a newly-hydrated month starts receiving live updates and an evicted one stops. No-op when
     * signed out.
     */
    private fun reattachMonthsListener() {
        val uid = currentUid ?: return
        monthsReg?.remove()
        monthsReg = scopedMonthsListener(uid)
    }

    private fun shouldApplyParent(snap: DocumentSnapshot): Boolean = SyncLogic.shouldApply(
        remoteDocOf(snap, snap.getString(F_DEFS_HASH)),
        SyncSnapshot(syncState.definitionsHash, syncState.deviceId, syncState.lastKnownRevision)
    )

    private fun shouldApplyMonthDoc(doc: DocumentSnapshot): Boolean = SyncLogic.shouldApplyMonth(
        remoteDocOf(doc, doc.getString(F_HASH)),
        syncState.monthHashes[doc.id],
        syncState.deviceId,
        syncState.lastKnownRevision
    )

    private fun remoteDocOf(snap: DocumentSnapshot, hash: String?) = RemoteDoc(
        exists = snap.exists(),
        contentHash = hash,
        deviceId = snap.getString(F_DEVICE),
        revision = snap.getLong(F_REV) ?: 0L,
        hasPendingWrites = snap.metadata.hasPendingWrites()
    )

    /**
     * Applies the parent doc's definitions **without touching history**.
     *
     * v0.5.3 Phase 1 (S6): a **targeted** upsert via [ExportImportRepository.applyRemoteDefinitions]
     * — no longer recombined with local days and routed through `importAllData` (which `deleteAll()`s
     * both event tables and every future PENDING occurrence and re-issues every `notification_id`).
     * Returns false when there was nothing usable to apply.
     */
    private suspend fun applyRemoteParent(snap: DocumentSnapshot): Boolean {
        if (conflictPaused) { _status.value = SyncStatus.Paused; return false }   // v0.5.3 Phase 3
        val blob = snap.get(F_DEFINITIONS) as? Blob ?: return false
        val defs = runCatching {
            MonthPartitioner.decodeDefinitionsJson(PayloadCodec.gunzipToString(blob.toBytes()))
        }.getOrNull() ?: return false
        if (defs.habits.isEmpty() && defs.intakeReminders.isEmpty()) {
            Log.w(TAG, "remote definitions are empty — refusing to apply")
            return false
        }
        _status.value = SyncStatus.Syncing
        val dirtyBefore = dirtyCounter.get()
        val result = exportImport.applyRemoteDefinitions(defs)
        if (!result.success) {
            Log.w(TAG, "remote definitions import rejected: ${result.message}")
            _status.value = SyncStatus.Error("Couldn't apply cloud data")
            return false
        }
        // Store the hash of a FRESH re-export, not the remote one: applyRemoteDefinitions is not a
        // perfect inverse of exportBackup, and this is what makes the one possible reconciling push
        // converge in a single round (the v0.5 D-note asymmetry, unchanged).
        val reexport = runCatching { exportImport.exportBackup() }.getOrNull()
        val appliedHash = ContentHash.ofDefinitions(defs)
        val reHash = reexport?.definitions?.let { ContentHash.ofDefinitions(it) }
        syncState.definitionsHash = reHash ?: snap.getString(F_DEFS_HASH)
        syncState.lastKnownRevision = maxOf(syncState.lastKnownRevision, snap.getLong(F_REV) ?: 0L)
        // v0.5.3 Phase 1 (S4): do NOT blanket-clear pendingPush. Only clear it when nothing else is
        // outstanding — a local/remote definitions divergence, or a user edit that landed during
        // the import, must still be pushed.
        if (reHash != null && shouldRearmPendingAfterImport(appliedHash, reHash)) {
            syncState.pendingPush = true
        } else if (shouldClearPending(dirtyBefore, dirtyCounter.get())) {
            syncState.pendingPush = false
        }
        currentUid?.let { syncState.lastSyncedUid = it }
        return true
    }

    /** Gunzip one month doc and merge it into Room via [ExportImportRepository.importMonth]. */
    private suspend fun applyRemoteMonth(month: String, doc: DocumentSnapshot): Boolean {
        if (conflictPaused) { _status.value = SyncStatus.Paused; return false }   // v0.5.3 Phase 3
        val blob = doc.get(F_PAYLOAD) as? Blob ?: return false
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 8 (C-3, High): gunzip failure AND decode failure
        // (now that decodeDays returns null instead of emptyList() — see its KDoc) both bail out
        // here, matching applyRemoteParent's existing null-guard. Neither touches Room or stores
        // the remote hash — a corrupt payload leaves this month exactly as it was, retryable.
        val gunzipped = runCatching { PayloadCodec.gunzipToString(blob.toBytes()) }.getOrNull() ?: return false
        val days = MonthPartitioner.decodeDays(gunzipped) ?: return false
        // C-3's second guard: a real empty month has a specific, well-defined hash
        // (ContentHash.ofDays(emptyList())). A doc that decodes to zero days but claims a
        // DIFFERENT hash is not "empty" — it's corrupt (e.g. a write truncated to a bare `[]`,
        // which is syntactically valid JSON but the wrong content). Refuse it the same way.
        if (days.isEmpty() && !MonthPartitioner.isGenuinelyEmptyMonth(doc.getString(F_HASH))) {
            Log.w(TAG, "month $month decoded to zero days but hash doesn't match an empty month — treating as corrupt, not applying")
            return false
        }
        _status.value = SyncStatus.Syncing
        val dirtyBefore = dirtyCounter.get()
        val result = exportImport.importMonth(month, days)
        if (!result.success) {
            Log.w(TAG, "month $month merge failed: ${result.message}")
            return false
        }
        // The REMOTE hash is stored, not a re-export hash: that is what stops a redelivered
        // snapshot from re-importing forever (guard 2).
        val appliedHash = doc.getString(F_HASH) ?: ContentHash.ofDays(days)
        syncState.monthHashes = syncState.monthHashes + (month to appliedHash)
        syncState.hydratedMonths = syncState.hydratedMonths + month
        syncState.lastKnownRevision = maxOf(syncState.lastKnownRevision, doc.getLong(F_REV) ?: 0L)
        hydrationAttempted += month
        // v0.5.3 Phase 1 (S4): re-export THIS month and compare. If the local content differs from
        // what was just applied, an unpushed local edit is still outstanding — keep pendingPush
        // armed so SyncFlushWorker is not turned into a no-op.
        val reHash = runCatching {
            val re = exportImport.exportBackup()
            MonthPartitioner.partition(re.days)[month]?.let { ContentHash.ofDays(it) }
                ?: ContentHash.ofDays(emptyList())
        }.getOrNull()
        if (reHash != null && shouldRearmPendingAfterImport(appliedHash, reHash)) {
            syncState.pendingPush = true
        } else if (shouldClearPending(dirtyBefore, dirtyCounter.get())) {
            syncState.pendingPush = false
        }
        currentUid?.let { syncState.lastSyncedUid = it }
        reattachMonthsListener()   // v0.5.3 Phase 1 (S12): this month is now resident
        Log.i(TAG, "hydrated month $month (${days.size} days)")
        return true
    }

    /**
     * Lazy fetch for a month outside the hydrated window (SD-3a). Called from `HomeViewModel`
     * whenever the selected date moves into a new month, which covers the week strip, the calendar
     * and anything else that ultimately moves `_selectedDate`.
     *
     * **Failure-inert and non-blocking by contract:** the day renders empty and fills in when the
     * fetch lands, through exactly the same reactive path a remote change already uses. Never
     * throws, never blocks rendering.
     */
    /**
     * v0.5.2 §9 / 5B.8: true when this month's day-logs are resident in Room, so a local edit
     * (a §9 backfill) is safe to push — otherwise `doPush` would write a one-day payload over the
     * cloud's full month document. A signed-out user is always resident: there is no cloud to lose.
     */
    fun isMonthResident(month: String): Boolean =
        currentUid == null ||
            month in syncState.hydratedMonths ||
            month in MonthPartitioner.recentMonths()

    suspend fun ensureMonthHydrated(month: String) {
        val uid = currentUid ?: return
        if (conflictPaused) { _status.value = SyncStatus.Paused; return }   // v0.5.3 Phase 3
        if (month in syncState.hydratedMonths) return
        if (!hydrationAttempted.add(month)) return
        runCatching {
            // v0.5.3 Phase 1 (S8b): SERVER-only. A null result means the server was UNREACHABLE,
            // not that the month is empty. Marking an unreachable month hydrated would let
            // isMonthResident() return true and a §9 backfill write a one-day payload over the
            // cloud's full month doc.
            //
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-9): a month stuck "loading" while offline
            // used to only retry on the next external trigger (a re-navigation, or HomeViewModel's
            // `monthReady`'s wall-clock-tick re-poll, which can be minutes away). A short, bounded
            // retry-with-backoff here means a transient blip (or connectivity that returns within a
            // couple of seconds) resolves within this same UI-triggered call instead of leaving the
            // screen showing "Loading…" until the next unrelated trigger fires.
            var snap = fetchDocServer(monthRef(uid, month))
            if (snap == null) {
                for (delayMs in RETRY_BACKOFF_MS) {
                    delay(delayMs)
                    snap = fetchDocServer(monthRef(uid, month))
                    if (snap != null) break
                }
            }
            when {
                snap == null -> {
                    // Still unreachable after the retries above — do NOT mark hydrated; free
                    // hydrationAttempted so a later navigation (or the monthReady re-trigger) retries.
                    hydrationAttempted.remove(month)
                    Log.i(TAG, "ensureMonthHydrated($month): server unreachable — left unhydrated")
                }
                !snap.exists() -> {
                    // Server CONFIRMS nothing for this month — safe to mark hydrated; a backfill
                    // into a genuinely empty month is correct.
                    syncState.hydratedMonths = syncState.hydratedMonths + month
                    pinnedMonths.add(month)
                    reattachMonthsListener()
                }
                else -> pushMutex.withLock {
                    if (applyRemoteMonth(month, snap)) {
                        pinnedMonths.add(month)
                        requestResync()   // v0.5.3 Phase 3 (A10 partial)
                        markIdle()
                    } else {
                        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 6 (S-3): `applyRemoteMonth` returned
                        // false (e.g. C-3's corrupt-payload guard, Phase 8) — the month was NOT
                        // actually hydrated. Previously this fell through with no cleanup, leaving
                        // `hydrationAttempted` permanently marking it "tried" and blocking every
                        // future retry. Free it so the next navigation into this month retries.
                        hydrationAttempted.remove(month)
                    }
                }
            }
        }.onFailure {
            Log.w(TAG, "ensureMonthHydrated($month) failed", it)
            // Allow a retry on the next navigation into this month.
            hydrationAttempted.remove(month)
        }
    }

    // ------------------------------------------------------------------ conflict (D2)

    private suspend fun raiseConflict(
        uid: String,
        parent: DocumentSnapshot?,
        local: DaybookBackup?,
        remoteMonthHashes: Map<String, String>
    ) {
        conflictPaused = true
        val remoteDefs = (parent?.get(F_DEFINITIONS) as? Blob)?.let { blob ->
            runCatching {
                MonthPartitioner.decodeDefinitionsJson(PayloadCodec.gunzipToString(blob.toBytes()))
            }.getOrNull()
        }
        // §N: the remote day count now has to be summed across the month docs. One collection read
        // per conflict prompt, which happens at most once per sign-in.
        val remoteDays = runCatching {
            monthsRef(uid).get(Source.SERVER).awaitCompat().documents.sumOf { d ->
                val blob = d.get(F_PAYLOAD) as? Blob ?: return@sumOf 0
                runCatching {
                    // Phase 8 (C-3): decodeDays is now nullable on decode failure — a corrupt
                    // remote month just doesn't count toward this display-only conflict-dialog
                    // total (a real merge is guarded separately in applyRemoteMonth).
                    MonthPartitioner.decodeDays(PayloadCodec.gunzipToString(blob.toBytes()))?.size ?: 0
                }.getOrDefault(0)
            }
        }.getOrDefault(remoteMonthHashes.size)

        _conflict.value = ConflictInfo(
            localHabits = local?.definitions?.habits?.size ?: 0,
            localDays = local?.days?.size ?: 0,
            remoteHabits = remoteDefs?.habits?.size ?: 0,
            remoteDays = remoteDays
        )
        markIdle()
    }

    /** D2 resolution. `restoreFromCloud` = replace this device; else replace the cloud. */
    suspend fun resolveConflict(restoreFromCloud: Boolean) {
        val uid = currentUid ?: return
        conflictPaused = false
        _conflict.value = null
        pushMutex.withLock {
            if (restoreFromCloud) {
                val parent = fetchDoc(docRef(uid))
                pullRemote(uid, parent, fetchRemoteMonthHashes(uid, parent))   // v0.5.3 Phase 2 (S1)
            } else {
                // v0.5.3 Phase 6 (S5c): the user explicitly chose "this device wins" — a legit
                // reason to trim cloud months this device no longer has.
                doPush(force = true, allowMonthDeletions = true)
            }
        }
        // v0.5.3 Phase 1 (S2): the resident data now belongs to this uid.
        syncState.lastSyncedUid = uid
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-10): record WHICH remote revision this
        // resolution settled on — `bootstrap()` only treats this uid's conflict as "already
        // resolved, skip re-prompting" while the remote still matches this exact revision. Both
        // branches above update `syncState.lastKnownRevision` to the post-resolution value
        // (`pullRemote` to the pulled doc's F_REV; `doPush` to its own newly-written revision), so
        // reading it here, after the pushMutex block, is the correct "as of now" value.
        syncState.conflictPromptShownForUid = uid
        syncState.conflictPromptShownRevision = syncState.lastKnownRevision
    }

    /** Dismissing the dialog: sync stays paused, re-prompts on next launch (promptShown unset). */
    fun dismissConflict() {
        _conflict.value = null
        conflictPaused = true
        // v0.5.3 Phase 3 (finding 19): the row was showing "Idle" while sync was entirely halted
        // for the session — say so.
        _status.value = SyncStatus.Paused
    }

    // ------------------------------------------------------------------ lifecycle hooks

    /** Flush on the moment the user is most likely to leave and the process most likely to die. */
    fun onAppStop() {
        if (currentUid == null) return
        if (syncState.pendingPush) {
            scope.launch { runCatching { pushMutex.withLock { doPush(force = false) } } }
            SyncFlushWorker.enqueue(context)
        }
        // v0.5.3 Phase 1 (S3): eviction is off the push path — run it here, where the user is
        // leaving the screen, not mid-navigation.
        scope.launch { runCatching { runMaintenance() } }
    }

    /**
     * v0.5.3 Phase 1 (S7): true once auth has resolved past [AuthState.Loading] **and** a uid is
     * known. `currentUid` is populated async by the auth collector, so a worker resumed after a
     * process death can otherwise win the race and report a bogus success.
     */
    suspend fun awaitReady(timeoutMs: Long = 8_000): Boolean =
        withTimeoutOrNull(timeoutMs) {
            auth.state.first { it !is AuthState.Loading }
            // Give the onAuthState collector a beat to assign currentUid.
            if (currentUid == null) auth.state.first { currentUid != null || it is AuthState.SignedOut }
            currentUid != null
        } ?: false

    /** Called by [SyncFlushWorker]. */
    suspend fun flushPendingPush(): Boolean {
        when (flushOutcome(syncState.pendingPush, currentUid != null)) {
            FlushOutcome.SUCCESS_NOOP -> return true
            FlushOutcome.RETRY_NO_UID -> {
                // v0.5.3 Phase 1 (S7): wait for the uid to resolve; if it still hasn't, RETRY —
                // never report success and consume the unique work.
                if (!awaitReady() || currentUid == null) return false
            }
            FlushOutcome.PROCEED -> {}
        }
        return runCatching { pushMutex.withLock { doPush(force = false) } }.isSuccess &&
            !syncState.pendingPush
    }

    /**
     * Delete the cloud data — for account deletion, while the token is still valid (D-del).
     *
     * A parent-doc `delete()` in Firestore does **not** delete its subcollections. Miss the months
     * enumeration here and a deleted account leaves orphaned history in Firestore forever.
     *
     * v0.5.3 Phase 3 (S16): the debounced push loop is paused for the whole delete. Between the
     * subcollection delete and the parent delete, a `doPush` fired by any concurrent Room write
     * would recreate the docs under a soon-to-be-deleted uid — orphaned, billed, unreachable data.
     * Reuses the `conflictPaused` gate (checked by `doPush` / `applyRemote*` / `ensureMonthHydrated`),
     * so it also covers the re-auth retry window in `AccountViewModel.deleteAccount`.
     */
    suspend fun deleteRemoteDoc(): Boolean {
        val uid = currentUid ?: return false
        conflictPaused = true
        _status.value = SyncStatus.Paused
        return try {
            runCatching {
                val months = monthsRef(uid).get(Source.SERVER).awaitCompat().documents
                months.map { it.reference }.chunked(BATCH_CHUNK).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it) }
                    batch.commit().awaitCompat()
                }
                docRef(uid).delete().awaitCompat()
            }.onFailure { Log.w(TAG, "deleteRemoteDoc failed", it); com.daybook.app.util.recordUnhandledException(it) }.isSuccess
        } finally {
            conflictPaused = false
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun docRef(uid: String): DocumentReference =
        firestore.collection("users").document(uid)

    private fun monthsRef(uid: String) = docRef(uid).collection(COLL_MONTHS)

    private fun monthRef(uid: String, month: String): DocumentReference =
        monthsRef(uid).document(month)

    /** SERVER first, falling back to the offline CACHE — the shape `bootstrap` has always used. */
    private suspend fun fetchDoc(ref: DocumentReference): DocumentSnapshot? =
        runCatching { ref.get(Source.SERVER).awaitCompat() }
            .recoverCatching { ref.get(Source.CACHE).awaitCompat() }
            .getOrNull()

    /**
     * v0.5.3 Phase 1 (S8b): SERVER-only. Returns null on ANY failure (offline, timeout,
     * permission). The caller MUST treat null as "unknown / unreachable" — never as "the document
     * does not exist". Only a non-null snapshot with `!exists()` means the server confirmed
     * nothing is there.
     */
    private suspend fun fetchDocServer(ref: DocumentReference): DocumentSnapshot? =
        runCatching { ref.get(Source.SERVER).awaitCompat() }.getOrNull()

    /**
     * v0.5.3 Phase 2 (S1): read the month-hash summary from the parent doc's [F_MONTH_HASHES] map
     * (already fetched by the caller). Falls back to the per-doc `months` collection scan only when
     * the field is absent — an old-layout parent doc, or the first push by a pre-0.5.3 client. The
     * summary is written back on the next push, so the scan is a one-time repair path. `FORMAT_VERSION`
     * stays 3: the field is additive, so no force-bootstrap is needed.
     */
    private suspend fun fetchRemoteMonthHashes(uid: String, parent: DocumentSnapshot?): Map<String, String> {
        val field = parentMonthHashesField(parent)
        // The "field wins, else scan" decision is [readMonthHashes] (pure, ParentMonthHashesTest);
        // it is not called directly only because its fallback cannot be a suspend lambda.
        if (!field.isNullOrEmpty()) return field
        return runCatching { monthsRef(uid).get(Source.SERVER).awaitCompat() }
            .recoverCatching { monthsRef(uid).get(Source.CACHE).awaitCompat() }
            .getOrNull()
            ?.documents
            ?.mapNotNull { d -> d.getString(F_HASH)?.let { d.id to it } }
            ?.toMap()
            .orEmpty()
    }

    /** v0.5.3 Phase 2 (S1): pull the [F_MONTH_HASHES] map field off a parent snapshot, String→String only. */
    private fun parentMonthHashesField(parent: DocumentSnapshot?): Map<String, String>? =
        (parent?.get(F_MONTH_HASHES) as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap()

    private suspend fun isLocalEmpty(): Boolean =
        database.habitDao().observeAllHabits().first().isEmpty() &&
            database.foodMedTaskDao().observeAllTasks().first().isEmpty()

    private fun markIdle() {
        syncState.lastSyncedAtMillis = System.currentTimeMillis()
        _status.value = SyncStatus.Idle(syncState.lastSyncedAtMillis)
    }

    private fun isOffline(t: Throwable): Boolean {
        val n = t.javaClass.simpleName
        return n == "FirebaseNetworkException" ||
            t.message?.contains("UNAVAILABLE", ignoreCase = true) == true ||
            t.message?.contains("offline", ignoreCase = true) == true
    }

    // internal (not private) so DataTablesSyncTest can reach DATA_TABLES from the test source set.
    internal companion object {
        const val TAG = "CloudSyncRepository"
        const val DEBOUNCE_MS = 3_000L
        /**
         * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-9): [ensureMonthHydrated]'s bounded
         * retry-with-backoff on a server-unreachable result — 2 extra attempts, ~2.3s total, since
         * this runs from a UI-triggered suspend call and must not hang indefinitely.
         */
        val RETRY_BACKOFF_MS = listOf(500L, 1_800L)
        /** v0.5.3 Phase 3 (A10 partial): coalesce per-month-doc alarm re-arms into one sweep. */
        const val RESYNC_DEBOUNCE_MS = 1_000L
        const val SOFT_SIZE_WARN = 700_000
        /** Firestore's hard limit is 500 writes per batch; 450 leaves room for the parent (R8). */
        const val BATCH_CHUNK = 450
        /**
         * v0.5.3 Phase 1 (S8): commit a sub-batch early once its accumulated gzipped payload bytes
         * approach this — headroom under Firestore's 10 MiB hard per-commit cap.
         */
        const val MAX_COMMIT_BYTES = 8L * 1024 * 1024
        /**
         * v0.5.3 Phase 1 (S9): a single month doc whose gzipped payload exceeds this is skipped
         * (a `batch.commit()` containing it fails permanently) and parked until it shrinks.
         */
        const val HARD_DOC_LIMIT = 1_000_000
        /** 3 = the month-partitioned layout. 2 was v0.5's single blob, which is discarded. */
        const val FORMAT_VERSION = 3L
        const val COLL_MONTHS = "months"

        // The data tables that feed the v2 backup format — NOT "app_settings" (accent/font/photo/
        // name aren't in the format, so a settings write must not cost a cloud round trip).
        // v0.5.2: "custom_categories" is added because it feeds Definitions.customCategories, and a
        // category added on its own must mark pendingPush (SD-b).
        // v0.5.3: "custom_prompts" is added for the same reason — it feeds Definitions.customPrompts.
        // The global "journal_questions" table (v0.5.4 Phase 2/D1) was retired in the journal-habit
        // round: MIGRATION_16_17 drops it outright in favour of the per-habit
        // `habits.journal_questions_json` column (Migrations.kt ~line 433), but this array was never
        // updated to match. Registering an InvalidationTracker.Observer for a table that no longer
        // exists in schema v17 throws IllegalArgumentException("There is no table with name
        // journal_questions") the instant attachTracker() runs from onAuthState — i.e. immediately
        // after every sign-in. THIS WAS THE ROOT CAUSE of the "crashes after login" report (confirmed
        // via `adb logcat -b crash` on-device 2026-09-05, present since at least that morning's build).
        // "habits" already covers journal-question edits now that they live in a habits column, so
        // dropping this entry loses no invalidation coverage.
        // TRIPWIRE: DataTablesSyncTest asserts every entry below still exists in AppDatabase's live
        // entity list. Keep it green when you edit this array — it's what would have caught the
        // journal_questions incident above before it shipped.
        val DATA_TABLES = arrayOf(
            "habits", "habit_occurrences", "habit_events",
            "food_med_tasks", "food_med_occurrences", "food_med_events",
            "custom_categories", "custom_prompts"
        )

        // Parent doc.
        const val F_DEFINITIONS = "definitions"
        const val F_DEFS_HASH = "definitionsHash"
        const val F_FORMAT = "formatVersion"
        const val F_APPVER = "appVersion"
        /** v0.5.3 Phase 2 (S1): parent-doc map field {monthKey -> contentHash}, the summary that
         *  replaces a full `months` collection scan on every cold start. Plain map field — no rules
         *  change (the parent-doc match already lets the owner write arbitrary fields). */
        const val F_MONTH_HASHES = "monthHashes"
        // Month doc.
        const val F_PAYLOAD = "payload"
        const val F_HASH = "contentHash"
        // Both.
        const val F_REV = "revision"
        const val F_DEVICE = "deviceId"
        const val F_UPDATED = "updatedAt"
    }
}
