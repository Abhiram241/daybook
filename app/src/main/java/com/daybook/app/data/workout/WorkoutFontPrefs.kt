package com.daybook.app.data.workout

import android.content.Context
import com.daybook.app.ui.theme.FontChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User request — "I want different fonts for that mode" — a font choice independent of the main
 * app's Settings > Appearance > Font, picked from Beast Mode's own settings screen. Plain
 * `SharedPreferences`, not a Room column: this is a device-local UI preference with no need to
 * sync across devices or survive a JSON export/import, so it deliberately avoids a schema
 * migration (every other Beast Mode setting lives in `app_settings`/Room; this one doesn't need
 * to, and adding a column just for this would need a migration + explicit sign-off per the
 * project's standing rule on schema changes).
 */
@Singleton
class WorkoutFontPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _fontChoice = MutableStateFlow(readStored())

    /** `null` means "match the app's own font choice" — Beast Mode still gets the bold-weight
     *  bump ([com.daybook.app.ui.workout.beast.beastTypography]) either way. */
    val fontChoice: StateFlow<FontChoice?> = _fontChoice

    fun setFontChoice(choice: FontChoice?) {
        // User request — Beast Mode's default font is GROTESK (Space Grotesk), not "match app
        // font". An explicit "Match app font" pick has to stay distinguishable from a fresh
        // install that never touched this setting (both used to collapse to "key absent"), so
        // that choice is now stored as its own sentinel instead of just removing the key.
        prefs.edit().putString(KEY, choice?.storageKey ?: MATCH_APP_SENTINEL).apply()
        _fontChoice.value = choice
    }

    private fun readStored(): FontChoice? = runCatching {
        when (val stored = prefs.getString(KEY, null)) {
            null -> DEFAULT // never set on this device — fresh-install default
            MATCH_APP_SENTINEL -> null // user explicitly chose "Match app font"
            else -> FontChoice.entries.firstOrNull { it.storageKey == stored } ?: DEFAULT
        }
    }.getOrDefault(DEFAULT)

    companion object {
        private const val FILE = "beast_mode_prefs"
        private const val KEY = "workout_font_choice"
        private const val MATCH_APP_SENTINEL = "APP"
        private val DEFAULT = FontChoice.GROTESK
    }
}
