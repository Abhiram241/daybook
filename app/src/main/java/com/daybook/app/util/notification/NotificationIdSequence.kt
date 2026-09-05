package com.daybook.app.util.notification

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide monotonic source of notification IDs, which double as the base for a reminder's
 * PendingIntent request codes.
 *
 * Replaces `String.hashCode()` (REV-28): a 32-bit non-cryptographic hash collision between two
 * occurrences produced the *same* `(requestCode, action, component)` and therefore the same
 * PendingIntent, so arming one silently cancelled the other's alarm — a reminder that never
 * fires, with no trace. A never-reused counter cannot collide.
 *
 * Backed by SharedPreferences so IDs stay unique across process death. `next()` returns the
 * value and advances; callers derive request codes as `id * 4 + {0..3}` (fire / refire / open /
 * action), and [MAX] keeps that multiplication inside `Int` range.
 */
@Singleton
class NotificationIdSequence @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("notification_id_seq", Context.MODE_PRIVATE)

    @Synchronized
    fun next(): Int {
        val current = prefs.getInt(KEY, START).let { if (it < START || it >= MAX) START else it }
        prefs.edit().putInt(KEY, current + 1).apply()
        return current
    }

    private companion object {
        const val KEY = "next_id"
        const val START = 1_000          // keep low ids clear of any legacy hashCode-based intents
        const val MAX = 500_000_000      // * 4 for request codes stays well inside Int.MAX_VALUE
    }
}
