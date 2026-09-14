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
     */
    var sleepDedupResyncPending: Boolean
        get() = !prefs.getBoolean(KEY_SLEEP_DEDUP_RESYNC_DONE_V1, false)
        set(pending) = prefs.edit().putBoolean(KEY_SLEEP_DEDUP_RESYNC_DONE_V1, !pending).apply()

    private companion object {
        const val KEY_CHANGES_TOKEN = "health_changes_token"
        const val KEY_LAST_PULL_AT = "health_last_pull_at"
        const val KEY_LAST_FULL_RESYNC_AT = "health_last_full_resync_at"
        const val KEY_BACKFILL_THROUGH = "health_backfill_through"
        const val KEY_BG_PERMISSION_DENIED = "health_bg_permission_denied"
        const val KEY_LAST_STATUS = "health_last_status_message"
        const val KEY_LAST_STATUS_FAILED = "health_last_status_failed"
        const val KEY_SLEEP_DEDUP_RESYNC_DONE_V1 = "health_sleep_dedup_resync_done_v1"
    }
}
