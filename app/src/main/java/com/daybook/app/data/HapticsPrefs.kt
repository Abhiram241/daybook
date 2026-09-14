package com.daybook.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide "Vibration / Haptics" toggle (Settings > Appearance > Feel). Plain `SharedPreferences`,
 * not a Room column — same reasoning as [com.daybook.app.data.workout.WorkoutFontPrefs]: this is a
 * device-local UI preference with no need to sync across devices or survive a JSON export/import,
 * so it deliberately avoids a schema migration (every `app_settings` column needs one + explicit
 * sign-off per the project's standing rule on schema changes; a SharedPreferences flag doesn't).
 *
 * Read by [com.daybook.app.ui.theme.DaybookTheme] (via `LocalHapticsEnabled`) and written from
 * Settings > Appearance > Feel.
 */
@Singleton
class HapticsPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
        _hapticsEnabled.value = enabled
    }

    companion object {
        private const val FILE = "haptics_prefs"
        private const val KEY = "haptics_enabled"
    }
}
