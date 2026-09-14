package com.daybook.app.data

import android.content.Context
import com.daybook.app.ui.theme.DEFAULT_CORNER_SCALE
import com.daybook.app.ui.theme.clampCornerScale

/**
 * UX overhaul item 4 / UX refinement round — a `SharedPreferences` mirror of the theme columns
 * on `AppSettings` (`theme_mode`, and since the refinement round `dark_style` / `light_style` /
 * `corner_scale`), so `MainActivity` can read the theme **synchronously before `setContent`**
 * (and pick a matching Activity `windowBackground`) with zero theme-flash on cold start. The
 * Room row stays the source of truth; each `write*` is called right after the matching Room
 * write of that column.
 *
 * Renamed from `ThemeModePrefs` (UX refinement round, LD15) — the mirror now carries four keys,
 * so "ThemeMode" alone was no longer an accurate name.
 *
 * Uses the same `daybook_prefs` file `MainActivity` already uses for `alarm_permission_asked`.
 */
object ThemePrefs {
    private const val FILE = "daybook_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DARK_STYLE = "dark_style"
    private const val KEY_LIGHT_STYLE = "light_style"
    private const val KEY_CORNER_SCALE = "corner_scale"

    /** The stored key ("DARK" / "LIGHT" / "SYSTEM"), or "LIGHT" when nothing has been written yet. */
    fun read(context: Context): String =
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME_MODE, "LIGHT") ?: "LIGHT"
        }.getOrDefault("LIGHT")

    fun write(context: Context, value: String) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME_MODE, value).apply()
        }
    }

    /** The stored dark-style key, or "CHARCOAL" (the default) when nothing has been written yet. */
    fun readDarkStyle(context: Context): String =
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_DARK_STYLE, "CHARCOAL") ?: "CHARCOAL"
        }.getOrDefault("CHARCOAL")

    fun writeDarkStyle(context: Context, value: String) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_DARK_STYLE, value).apply()
        }
    }

    /** The stored light-style key, or "SEPIA" (the default) when nothing has been written yet. */
    fun readLightStyle(context: Context): String =
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LIGHT_STYLE, "SEPIA") ?: "SEPIA"
        }.getOrDefault("SEPIA")

    fun writeLightStyle(context: Context, value: String) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_LIGHT_STYLE, value).apply()
        }
    }

    /** The stored corner-scale, clamped, or [DEFAULT_CORNER_SCALE] when nothing has been written yet. */
    fun readCornerScale(context: Context): Float =
        clampCornerScale(
            runCatching {
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(KEY_CORNER_SCALE, DEFAULT_CORNER_SCALE)
            }.getOrDefault(DEFAULT_CORNER_SCALE)
        )

    fun writeCornerScale(context: Context, value: Float) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_CORNER_SCALE, value).apply()
        }
    }
}
