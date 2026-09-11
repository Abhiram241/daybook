package com.daybook.app.data

import android.content.Context

/**
 * UX overhaul item 4 — a `SharedPreferences` mirror of `AppSettings.theme_mode`, so
 * `MainActivity` can read the theme **synchronously before `setContent`** (and pick a matching
 * Activity `windowBackground`) with zero theme-flash on cold start. The Room row stays the
 * source of truth; [write] is called right after every Room write of the column.
 *
 * Uses the same `daybook_prefs` file `MainActivity` already uses for `alarm_permission_asked`.
 */
object ThemeModePrefs {
    private const val FILE = "daybook_prefs"
    private const val KEY = "theme_mode"

    /** The stored key ("DARK" / "LIGHT" / "SYSTEM"), or "DARK" when nothing has been written yet. */
    fun read(context: Context): String =
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "DARK") ?: "DARK"
        }.getOrDefault("DARK")

    fun write(context: Context, value: String) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY, value).apply()
        }
    }
}
