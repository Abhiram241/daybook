package com.daybook.app.data.workout

import android.content.Context
import com.daybook.app.ui.theme.DarkStyle
import com.daybook.app.ui.theme.LightStyle
import com.daybook.app.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Beast Mode's own theme (dark/light/system + dark style + light style), independent of Settings >
 * Appearance. Every field is nullable: `null` means "same as the app", which is also the
 * fresh-install state. Plain `SharedPreferences` in the same `beast_mode_prefs` file as
 * [WorkoutFontPrefs], for the same reason — a device-local UI preference, no schema migration.
 */
@Singleton
class WorkoutThemePrefs @Inject constructor(@ApplicationContext context: Context) {
    data class BeastTheme(
        val themeMode: ThemeMode? = null,
        val darkStyle: DarkStyle? = null,
        val lightStyle: LightStyle? = null
    ) {
        val followsApp: Boolean get() = themeMode == null && darkStyle == null && lightStyle == null
    }

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(readStored())
    val theme: StateFlow<BeastTheme> = _theme

    /** `null` = same as app. Picking "same as app" for the mode also resets both styles, so the
     *  whole look matches the app again. */
    fun setThemeMode(mode: ThemeMode?) {
        update(if (mode == null) BeastTheme() else _theme.value.copy(themeMode = mode))
    }

    fun setDarkStyle(style: DarkStyle) = update(_theme.value.copy(darkStyle = style))

    fun setLightStyle(style: LightStyle) = update(_theme.value.copy(lightStyle = style))

    private fun update(t: BeastTheme) {
        runCatching {
            prefs.edit()
                .putString(KEY_MODE, t.themeMode?.storageKey)
                .putString(KEY_DARK_STYLE, t.darkStyle?.storageKey)
                .putString(KEY_LIGHT_STYLE, t.lightStyle?.storageKey)
                .apply()
        }
        _theme.value = t
    }

    private fun readStored(): BeastTheme = runCatching {
        BeastTheme(
            themeMode = prefs.getString(KEY_MODE, null)
                ?.let { k -> ThemeMode.entries.firstOrNull { it.storageKey == k } },
            darkStyle = prefs.getString(KEY_DARK_STYLE, null)
                ?.let { k -> DarkStyle.entries.firstOrNull { it.storageKey == k } },
            lightStyle = prefs.getString(KEY_LIGHT_STYLE, null)
                ?.let { k -> LightStyle.entries.firstOrNull { it.storageKey == k } }
        )
    }.getOrDefault(BeastTheme())

    companion object {
        private const val FILE = "beast_mode_prefs"
        private const val KEY_MODE = "workout_theme_mode"
        private const val KEY_DARK_STYLE = "workout_dark_style"
        private const val KEY_LIGHT_STYLE = "workout_light_style"
    }
}
