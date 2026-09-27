package com.daybook.app.data.health

import android.content.Context
import android.content.SharedPreferences

/**
 * B3 (§6.3, §7.3) — Health Connect sync bookkeeping. Deliberately **not** in Room, same reasoning
 * as [com.daybook.app.data.sync.SyncStateStore]: a Room write here would re-trigger the
 * `InvalidationTracker` observer and mark a cloud push pending on every poll, the exact feedback
 * loop that store's KDoc warns about. Reuses the same `daybook_prefs` SharedPreferences file.
 *
 * Provided by `DatabaseModule.provideHealthSyncStateStore`, mirroring [HealthConnectReader].
 */
class HealthSyncStateStore(ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("daybook_prefs", Context.MODE_PRIVATE)

    /** Health Connect's opaque changes-token, or null before the first successful full pull. */
    var changesToken: String?
        get() = prefs.getString(KEY_CHANGES_TOKEN, null)
        set(v) = prefs.edit().putString(KEY_CHANGES_TOKEN, v).apply()

    /**
     * HC-2 fix (AUDIT_2026-09-16.md) — the sorted, comma-joined set of permission strings
     * [changesToken] was minted with. `reader.changes(token)` only ever reports changes within the
     * scope the token was minted for, so if the user goes back into Health Connect and shares an
     * additional data type afterwards, the existing token silently keeps asking about the OLD,
     * narrower scope forever — the newly-shared type produces no data until the token happens to
     * expire on its own (typically ~30 days). `HealthRepository.pull()` compares this against the
     * live granted set on every pull and, if they differ, treats the token exactly like an expired
     * one: a full 30-day re-read plus a freshly-minted, correctly-scoped token. Null for a token
     * minted before this fix shipped — that is treated as "no info, don't force a resync just for
     * this", since `sleepDedupResyncPending`'s one-shot flag already forces one full resync per
     * update, which populates this field for the first time.
     */
    var grantedScope: String?
        get() = prefs.getString(KEY_GRANTED_SCOPE, null)
        set(v) = prefs.edit().putString(KEY_GRANTED_SCOPE, v).apply()

    /** Epoch millis of the last successful pull of any kind (on-resume, worker, or manual). */
    var lastPullAt: Long
        get() = prefs.getLong(KEY_LAST_PULL_AT, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_PULL_AT, v).apply()

    /** Epoch millis of the last time a changes-token expiry forced a full 30-day re-read (§6.3),
     *  surfaced as "Rebuilt your health history <relative time>." when within the last 24h. */
    var lastFullResyncAt: Long
        get() = prefs.getLong(KEY_LAST_FULL_RESYNC_AT, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_FULL_RESYNC_AT, v).apply()

    /** Epoch millis the user's history has been backfilled through via "Import my past data",
     *  or 0 if never run — the automatic window is always the last 30 days regardless. */
    var backfillThroughMillis: Long
        get() = prefs.getLong(KEY_BACKFILL_THROUGH, 0L)
        set(v) = prefs.edit().putLong(KEY_BACKFILL_THROUGH, v).apply()

    /** True once the optional `READ_HEALTH_DATA_IN_BACKGROUND` request has been refused or found
     *  unsupported, so it is asked for at most once (§6.3 — "never nag"). */
    var backgroundPermissionDenied: Boolean
        get() = prefs.getBoolean(KEY_BG_PERMISSION_DENIED, false)
        set(v) = prefs.edit().putBoolean(KEY_BG_PERMISSION_DENIED, v).apply()

    /** Last-refresh outcome, for Beast Mode Settings' status line (C9.4 — a background failure
     *  must be readable somewhere the user can check). Null before the first ever attempt. */
    var lastStatusMessage: String?
        get() = prefs.getString(KEY_LAST_STATUS, null)
        set(v) = prefs.edit().putString(KEY_LAST_STATUS, v).apply()

    /** True if [lastStatusMessage] represents a failure rather than a success, so the UI can
     *  colour it [com.daybook.app.ui.theme.DaybookColors.Danger] vs the normal muted text. */
    var lastStatusIsFailure: Boolean
        get() = prefs.getBoolean(KEY_LAST_STATUS_FAILED, false)
        set(v) = prefs.edit().putBoolean(KEY_LAST_STATUS_FAILED, v).apply()

    /**
     * User bug report — the sleep-session dedup fix (`clusterSleepSessions`) only ever runs when a
     * day is actually RE-aggregated. `HealthRepository.pull()`'s normal incremental path only
     * re-reads a day Health Connect reports as changed since the last pull — a day that was
     * already synced (with the old, inflated sleep total) before this fix shipped has nothing
     * "changed" about it, so it would sit in Room with the stale number forever. This one-shot
     * flag forces exactly one full 30-day re-aggregate on the next pull after updating, the same
     * window `pull()` already re-reads on a first-ever pull or an expired token — just triggered by
     * an app update instead. Bump the key suffix (`_v1` -> `_v2`, …) if a future fix needs the same
     * one-time "resync everyone once" treatment again.
     *
     * HC-1 fix (AUDIT_2026-09-16.md, 2026-09-19 build) — this key already fired once for the
     * original overlap-only dedup fix, so a user who updated then would read `false` here forever
     * and never get the *continuation* fix's recompute. Bumped `_v1` -> `_v2` per the KDoc's own
     * instruction: a build with the old key would see `_v2` as never-consumed and correctly force
     * one more 30-day re-aggregate, picking up nights previously truncated by the continuation bug.
     */
    var sleepDedupResyncPending: Boolean
        get() = !prefs.getBoolean(KEY_SLEEP_DEDUP_RESYNC_DONE_V2, false)
        set(pending) = prefs.edit().putBoolean(KEY_SLEEP_DEDUP_RESYNC_DONE_V2, !pending).apply()

    /**
     * Audit finding #8 fix — called alongside wiping the `health_days`/`health_sessions`/
     * `health_weight_readings` Room tables on sign-out, so this bookkeeping (which governs whether
     * the next pull takes the cheap incremental-delta path or a full re-read) doesn't claim
     * "already synced through X" against tables that were just emptied — left as-is, the next
     * account's first pull would take the delta branch against a now-empty Room and silently never
     * properly re-populate its local health history from Health Connect (a device-level store,
     * unaffected by which Daybook account is signed in). Deliberately leaves
     * [backgroundPermissionDenied] (an OS permission decision, not account data) and
     * [sleepDedupResyncPending] / [lastStatusMessage] (one-shot app-version migration / cosmetic
     * status bookkeeping) untouched.
     */
    fun resetForAccountWipe() {
        prefs.edit()
            .remove(KEY_CHANGES_TOKEN)
            .remove(KEY_GRANTED_SCOPE)
            .putLong(KEY_LAST_PULL_AT, 0L)
            .putLong(KEY_LAST_FULL_RESYNC_AT, 0L)
            .putLong(KEY_BACKFILL_THROUGH, 0L)
            .apply()
    }

    private companion object {
        const val KEY_CHANGES_TOKEN = "health_changes_token"
        const val KEY_GRANTED_SCOPE = "health_granted_scope"
        const val KEY_LAST_PULL_AT = "health_last_pull_at"
        const val KEY_LAST_FULL_RESYNC_AT = "health_last_full_resync_at"
        const val KEY_BACKFILL_THROUGH = "health_backfill_through"
        const val KEY_BG_PERMISSION_DENIED = "health_bg_permission_denied"
        const val KEY_LAST_STATUS = "health_last_status_message"
        const val KEY_LAST_STATUS_FAILED = "health_last_status_failed"
        const val KEY_SLEEP_DEDUP_RESYNC_DONE_V2 = "health_sleep_dedup_resync_done_v2"
    }
}
