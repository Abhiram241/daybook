package com.daybook.app.data

import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepository @Inject constructor(
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

    /** Reactive settings stream — re-emits whenever the single settings row changes. */
    fun observeSettings(): Flow<AppSettings> =
        database.appSettingsDao().observeSettings().map { it ?: AppSettings() }
}
