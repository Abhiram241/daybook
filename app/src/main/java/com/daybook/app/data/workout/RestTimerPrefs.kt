package com.daybook.app.data.workout

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 4 (BEAST_MODE_ROUND_2026-09-16_PLAN.md, open question 6 — "yes, and store it in the
 * lightweight settings file rather than the main database", taken per the plan's own recommendation
 * per the fallback rule) — the rest timer's on-screen countdown. Deliberately NOT Room: it is
 * temporary, phone-specific UI state, not something that should sync to another device (a rest
 * timer running on a tablet would make no sense), and a Room write here would trip the
 * `InvalidationTracker` and mark a cloud push pending for no reason — same reasoning
 * `HealthSyncStateStore`/`SyncStateStore` already document for their own prefs use.
 *
 * The actual BEEP is independent of this — it's an `AlarmManager` alarm the OS holds regardless
 * (see `NotificationUtils.scheduleRestAlarm`). This is only what lets the on-screen ring keep
 * counting down correctly if the app process was killed and relaunched mid-rest. Only one workout
 * session can ever be ACTIVE at a time, so this is a single slot, not keyed per-session — restoring
 * it is a no-op (silently ignored) if the persisted `sessionId` doesn't match the session now
 * open, or if `endsAtMillis` has already passed.
 */
@Singleton
class RestTimerPrefs @Inject constructor(@ApplicationContext ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("daybook_prefs", Context.MODE_PRIVATE)

    fun save(sessionId: String, endsAtMillis: Long, totalSeconds: Int) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putLong(KEY_ENDS_AT, endsAtMillis)
            .putInt(KEY_TOTAL_SECONDS, totalSeconds)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_ENDS_AT)
            .remove(KEY_TOTAL_SECONDS)
            .apply()
    }

    /** Null if nothing is stored, it belongs to a different session, or it has already expired. */
    fun restore(sessionId: String): Pair<Long, Int>? {
        val storedSessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        if (storedSessionId != sessionId) return null
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0L)
        if (endsAt <= System.currentTimeMillis()) return null
        val total = prefs.getInt(KEY_TOTAL_SECONDS, 0)
        return endsAt to total
    }

    private companion object {
        const val KEY_SESSION_ID = "rest_timer_session_id"
        const val KEY_ENDS_AT = "rest_timer_ends_at"
        const val KEY_TOTAL_SECONDS = "rest_timer_total_seconds"
    }
}
