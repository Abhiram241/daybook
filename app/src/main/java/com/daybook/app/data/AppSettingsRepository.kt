package com.daybook.app.data

import android.content.Context
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    suspend fun getSettings(): AppSettings {
        return database.appSettingsDao().getSettings() ?: AppSettings().also { save(it) }
    }

    suspend fun save(settings: AppSettings) {
        database.appSettingsDao().insert(settings)
    }

    private suspend fun ensureRow() {
        if (database.appSettingsDao().getSettings() == null) {
            database.appSettingsDao().insert(AppSettings())
        }
    }

    // Targeted single-column writes — see AppSettingsDao (REV-25 / REV-04).
    suspend fun setUserName(name: String) { ensureRow(); database.appSettingsDao().updateUserName(name) }
    suspend fun setAccentColor(key: String) { ensureRow(); database.appSettingsDao().updateAccentColor(key) }
    suspend fun setOnboardingCompleted(done: Boolean) { ensureRow(); database.appSettingsDao().updateOnboardingCompleted(done) }
    // v0.5.1 §M: setBackupReminderEnabled removed with the monthly-backup-reminder feature.
    // v0.5.2 D2: setLastBackupExportAt removed — the column was write-only; dropped by MIGRATION_11_12.
    suspend fun setNotifPermissionAsked(asked: Boolean) { ensureRow(); database.appSettingsDao().updateNotifPermissionAsked(asked) }
    suspend fun setProfilePhotoPath(path: String?) { ensureRow(); database.appSettingsDao().updateProfilePhotoPath(path) }
    suspend fun setFontChoice(key: String) { ensureRow(); database.appSettingsDao().updateFontChoice(key) }
    suspend fun setHabitCheckinTime(hhmm: String) { ensureRow(); database.appSettingsDao().updateHabitCheckinTime(hhmm) }
    suspend fun setCheckForUpdatesEnabled(enabled: Boolean) { ensureRow(); database.appSettingsDao().updateCheckForUpdatesEnabled(enabled) }

    // --------------------------------------------------------------- Customization round (DB v16)
    suspend fun setWeekStart(v: String) { ensureRow(); database.appSettingsDao().updateWeekStart(v) }
    suspend fun setClock24h(v: Boolean) { ensureRow(); database.appSettingsDao().updateClock24h(v) }
    suspend fun setCalendarDefaultExpanded(v: Boolean) { ensureRow(); database.appSettingsDao().updateCalendarDefaultExpanded(v) }
    suspend fun setGreetingTone(v: String) { ensureRow(); database.appSettingsDao().updateGreetingTone(v) }
    suspend fun setGreetingTimeWord(v: Boolean) { ensureRow(); database.appSettingsDao().updateGreetingTimeWord(v) }
    suspend fun setHeroStyle(v: String) { ensureRow(); database.appSettingsDao().updateHeroStyle(v) }
    suspend fun setHabitSort(v: String) { ensureRow(); database.appSettingsDao().updateHabitSort(v) }
    suspend fun setIntakeSort(v: String) { ensureRow(); database.appSettingsDao().updateIntakeSort(v) }
    suspend fun setHabitShowArchived(v: Boolean) { ensureRow(); database.appSettingsDao().updateHabitShowArchived(v) }
    suspend fun setIntakeShowArchived(v: Boolean) { ensureRow(); database.appSettingsDao().updateIntakeShowArchived(v) }
    suspend fun setHomeHideResolved(v: Boolean) { ensureRow(); database.appSettingsDao().updateHomeHideResolved(v) }
    suspend fun setReduceMotion(v: Boolean) { ensureRow(); database.appSettingsDao().updateReduceMotion(v) }
    suspend fun setQuietHoursEnabled(v: Boolean) { ensureRow(); database.appSettingsDao().updateQuietHoursEnabled(v) }
    suspend fun setQuietStart(v: String) { ensureRow(); database.appSettingsDao().updateQuietStart(v) }
    suspend fun setQuietEnd(v: String) { ensureRow(); database.appSettingsDao().updateQuietEnd(v) }
    suspend fun setStreakMode(v: String) { ensureRow(); database.appSettingsDao().updateStreakMode(v) }
    suspend fun setShowStreaks(v: Boolean) { ensureRow(); database.appSettingsDao().updateShowStreaks(v) }
    suspend fun setStreakRestDays(v: String) { ensureRow(); database.appSettingsDao().updateStreakRestDays(v) }
    suspend fun setDefaultLandingTab(v: String) { ensureRow(); database.appSettingsDao().updateDefaultLandingTab(v) }
    suspend fun setNavTabs(v: String) { ensureRow(); database.appSettingsDao().updateNavTabs(v) }
    suspend fun setDefaultSnoozeMinutes(v: Int) { ensureRow(); database.appSettingsDao().updateDefaultSnoozeMinutes(v) }

    // DB v20 (UX overhaul item 4). Writes Room, then mirrors to SharedPreferences so
    // MainActivity can read the theme synchronously before setContent (zero flash).
    suspend fun setThemeMode(v: String) {
        ensureRow()
        database.appSettingsDao().updateThemeMode(v)
        ThemePrefs.write(context, v)
    }

    /** Synchronous, non-suspending read of the theme-mode SharedPreferences mirror. Used as the
     *  StateFlow initial value so the very first composition is already the right theme. */
    fun readThemeModeMirror(): String = ThemePrefs.read(context)

    // DB v21 (UX refinement round). Same Room-then-mirror pattern as setThemeMode.
    suspend fun setDarkStyle(v: String) {
        ensureRow()
        database.appSettingsDao().updateDarkStyle(v)
        ThemePrefs.writeDarkStyle(context, v)
    }

    suspend fun setLightStyle(v: String) {
        ensureRow()
        database.appSettingsDao().updateLightStyle(v)
        ThemePrefs.writeLightStyle(context, v)
    }

    suspend fun setCornerScale(v: Float) {
        ensureRow()
        database.appSettingsDao().updateCornerScale(v)
        ThemePrefs.writeCornerScale(context, v)
    }

    fun readDarkStyleMirror(): String = ThemePrefs.readDarkStyle(context)
    fun readLightStyleMirror(): String = ThemePrefs.readLightStyle(context)
    fun readCornerScaleMirror(): Float = ThemePrefs.readCornerScale(context)

    // --------------------------------------------------------------------------- Round A (DB v22)
    suspend fun setWeightUnit(v: String) { ensureRow(); database.appSettingsDao().updateWeightUnit(v) }
    suspend fun setWorkoutAccentColor(v: String) { ensureRow(); database.appSettingsDao().updateWorkoutAccentColor(v) }
    suspend fun setRestTimerDefaultSeconds(v: Int) { ensureRow(); database.appSettingsDao().updateRestTimerDefaultSeconds(v) }
    suspend fun setWorkoutHintState(v: Int) { ensureRow(); database.appSettingsDao().updateWorkoutHintState(v) }
    suspend fun setWorkoutTodayCardEnabled(v: Boolean) { ensureRow(); database.appSettingsDao().updateWorkoutTodayCardEnabled(v) }
    suspend fun setDefaultExerciseGroup(v: String?) { ensureRow(); database.appSettingsDao().updateDefaultExerciseGroup(v) }

    // --------------------------------------------------------------------------- Round B (DB v24)
    suspend fun setHealthTabLastMode(v: Int) { ensureRow(); database.appSettingsDao().updateHealthTabLastMode(v) }

    // ------------------------------------------------------------ DAILY_REPORT_REDESIGN_PLAN.md (DB v27)
    suspend fun setAiMetaPrompt(v: String) { ensureRow(); database.appSettingsDao().updateAiMetaPrompt(v) }
    suspend fun setAiReportCategories(v: String) { ensureRow(); database.appSettingsDao().updateAiReportCategories(v) }
    suspend fun setAiChatCategories(v: String) { ensureRow(); database.appSettingsDao().updateAiChatCategories(v) }
    /** §7.4's "Reset to today" action writes both columns together, atomically. */
    suspend fun setChatRange(start: String, end: String) {
        ensureRow()
        database.appSettingsDao().updateAiChatRange(start, end)
    }

    // ------------------------------------------------- AI_CHAT..._PLAN.md §1 (DB v29)
    suspend fun setAiChatMetaPrompt(v: String) { ensureRow(); database.appSettingsDao().updateAiChatMetaPrompt(v) }

    suspend fun setHealthHiddenCards(v: String) { ensureRow(); database.appSettingsDao().updateHealthHiddenCards(v) }

    /** Reactive settings stream — re-emits whenever the single settings row changes. */
    fun observeSettings(): Flow<AppSettings> =
        database.appSettingsDao().observeSettings().map { it ?: AppSettings() }
}
