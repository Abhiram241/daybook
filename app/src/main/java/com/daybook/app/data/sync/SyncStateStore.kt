package com.daybook.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync bookkeeping — deliberately **not** in Room (FIREBASE_0.5_PLAN.md §4).
 *
 * The local-change signal is a Room `InvalidationTracker` observer; writing sync state into a
 * Room table would re-trigger it in a feedback loop. Keeping it out of Room also means the DB
 * stays at **v7** with no migration. Reuses the existing `daybook_prefs` file (MainActivity
 * already uses it for the exact-alarm flag).
 */
@Singleton
class SyncStateStore @Inject constructor(@ApplicationContext ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("daybook_prefs", Context.MODE_PRIVATE)

    /** Random UUID, generated once, stable for the install. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    /**
     * v0.5.1 §N: hash of the **definitions** blob on the parent doc we last pushed OR pulled.
     *
     * Renamed from `lastSyncedHash` (which meant definitions+days for the single-blob layout) with
     * a new pref key, deliberately: the semantics changed, and a stale v0.5 value read under the
     * old name would be silently misinterpreted as "the definitions already match" and suppress
     * the first parent push. `clearForSignOut` removes the old key too.
     */
    var definitionsHash: String?
        get() = prefs.getString(KEY_DEFS_HASH, null)
        set(v) = prefs.edit().putString(KEY_DEFS_HASH, v).apply()

    /**
     * v0.5.1 §N: `{"2026-08":"ab12…","2026-07":"cd34…"}` — the content hash of each month doc as
     * we last pushed or pulled it. One JSON string in one pref key; still SharedPreferences, still
     * no Room, so the DB stays at v7.
     */
    var monthHashes: Map<String, String>
        get() = runCatching {
            monthJson.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                prefs.getString(KEY_MONTH_HASHES, "{}") ?: "{}"
            )
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit().putString(
            KEY_MONTH_HASHES,
            monthJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), v)
        ).apply()

    /**
     * v0.5.1 §N: months whose occurrence rows are actually resident in Room right now.
     *
     * **Load-bearing.** A month in [monthHashes] but *not* here was evicted, and must never be
     * treated as "emptied locally" — that would push a `delete()` of the user's cloud history.
     * See `MonthPartitioner.changedMonths`' caller contract.
     */
    var hydratedMonths: Set<String>
        get() = prefs.getString(KEY_HYDRATED_MONTHS, "")
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        set(v) = prefs.edit().putString(KEY_HYDRATED_MONTHS, v.joinToString(",")).apply()

    var lastKnownRevision: Long
        get() = prefs.getLong(KEY_LAST_REV, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_REV, v).apply()

    var lastSyncedAtMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_SYNC_AT, v).apply()

    var conflictPromptShownForUid: String?
        get() = prefs.getString(KEY_CONFLICT_UID, null)
        set(v) = prefs.edit().putString(KEY_CONFLICT_UID, v).apply()

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-10): the remote `F_REV` at the moment
     * [conflictPromptShownForUid] was last set (i.e. right after [CloudSyncRepository.resolveConflict]
     * finishes). Without this, [conflictPromptShownForUid] alone meant "this uid's conflict has
     * EVER been resolved, once, ever" — after the first resolution, `decideBootstrap` took the
     * last-write-wins ATTACH_ONLY branch forever, even for a completely unrelated LATER divergence
     * from a different device. Scoping to the revision means a future bootstrap only skips
     * re-prompting when the remote genuinely hasn't changed since that resolution.
     */
    var conflictPromptShownRevision: Long
        get() = prefs.getLong(KEY_CONFLICT_REVISION, 0L)
        set(v) = prefs.edit().putLong(KEY_CONFLICT_REVISION, v).apply()

    /**
     * v0.5.3 Phase 1 (S2): the uid whose data is currently resident in Room. Set on every
     * successful bootstrap / attach / conflict resolution; read by the cross-uid PUSH_LOCAL
     * refusal in [CloudSyncRepository.bootstrap]. Deliberately survived by [clearForSignOut]
     * (the belt-and-braces guard needs it); only [reset] — the D3 full wipe — removes it.
     */
    var lastSyncedUid: String?
        get() = prefs.getString(KEY_LAST_SYNCED_UID, null)
        set(v) = prefs.edit().putString(KEY_LAST_SYNCED_UID, v).apply()

    /** Set before a push `set()`, cleared in its success callback — a kill before the SDK even
     *  enqueues the write is recoverable by [com.daybook.app.util.work.SyncFlushWorker]. */
    var pendingPush: Boolean
        get() = prefs.getBoolean(KEY_PENDING_PUSH, false)
        set(v) = prefs.edit().putBoolean(KEY_PENDING_PUSH, v).apply()

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-3): the epoch-millis a batch-habit check-in
     * snooze runs until, or 0 when none is pending. Deliberately in this existing
     * SharedPreferences store rather than a new Room column — this round makes no schema changes.
     * `OccurrenceScheduler.armBatchCheckInInternal` arms to `max(nextCheckin, this)` so a later
     * `syncAll()` sweep can no longer silently discard an in-flight snooze (the bug this fixes: the
     * snooze only ever lived in the one `AlarmManager` PendingIntent it scheduled, and any
     * `armBatchCheckIn()` call before it fired — e.g. a routine sweep — replaced that alarm with
     * the next scheduled check-in time, silently un-snoozing it).
     */
    var batchSnoozeUntil: Long
        get() = prefs.getLong(KEY_BATCH_SNOOZE_UNTIL, 0L)
        set(v) = prefs.edit().putLong(KEY_BATCH_SNOOZE_UNTIL, v).apply()

    /** Wipe everything sync-related on sign-out so the next account starts clean. */
    fun clearForSignOut() {
        prefs.edit()
            .remove(KEY_LAST_HASH)          // v0.5's combined hash — dead, removed for good measure
            .remove(KEY_DEFS_HASH)
            .remove(KEY_MONTH_HASHES)       // a new account must not inherit these (§N)
            .remove(KEY_HYDRATED_MONTHS)
            .remove(KEY_LAST_REV)
            .remove(KEY_LAST_SYNC_AT)
            .remove(KEY_PENDING_PUSH)
            // deviceId and conflictPromptShownForUid are install-scoped — keep them.
            // lastSyncedUid is deliberately kept — the S2 cross-uid guard reads it on next sign-in.
            .apply()
    }

    /**
     * v0.5.3 Phase 1 (D3): the full sign-out wipe. [clearForSignOut] plus [lastSyncedUid], so the
     * next account bootstraps from a genuinely clean slate (Room is wiped in the same flow).
     */
    fun reset() {
        clearForSignOut()
        prefs.edit().remove(KEY_LAST_SYNCED_UID).apply()
    }

    private companion object {
        val monthJson = Json { encodeDefaults = true }

        const val KEY_DEVICE_ID = "sync_device_id"
        /** v0.5's definitions+days hash for the single-blob layout. Dead; only ever removed now. */
        const val KEY_LAST_HASH = "sync_last_hash"
        const val KEY_DEFS_HASH = "sync_definitions_hash"
        const val KEY_MONTH_HASHES = "sync_month_hashes"
        const val KEY_HYDRATED_MONTHS = "sync_hydrated_months"
        const val KEY_LAST_REV = "sync_last_revision"
        const val KEY_LAST_SYNC_AT = "sync_last_at"
        const val KEY_CONFLICT_UID = "sync_conflict_prompt_uid"
        /** LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-10). */
        const val KEY_CONFLICT_REVISION = "sync_conflict_prompt_revision"
        const val KEY_PENDING_PUSH = "sync_pending_push"
        /** v0.5.3 Phase 1 (S2): which uid the resident Room data belongs to. */
        const val KEY_LAST_SYNCED_UID = "sync_last_synced_uid"
        /** LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-3). */
        const val KEY_BATCH_SNOOZE_UNTIL = "batch_snooze_until"
    }
}
