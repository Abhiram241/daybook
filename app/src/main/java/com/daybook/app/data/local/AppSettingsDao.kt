package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: AppSettings): Long

    // Per-field updates: every mutation carries only the column it changes, so concurrent
    // writers on the single id=1 row can't clobber each other's fields (REV-25) and a write
    // from a still-loading screen can't reset the row to defaults (REV-04).
    @Query("UPDATE app_settings SET user_name = :name WHERE id = 1")
    suspend fun updateUserName(name: String)

    @Query("UPDATE app_settings SET accent_color = :key WHERE id = 1")
    suspend fun updateAccentColor(key: String)

    @Query("UPDATE app_settings SET onboarding_completed = :done WHERE id = 1")
    suspend fun updateOnboardingCompleted(done: Boolean)

    @Query("UPDATE app_settings SET check_for_updates_enabled = :enabled WHERE id = 1")
    suspend fun updateCheckForUpdatesEnabled(enabled: Boolean)

    // v0.5.1 §M: updateBackupReminderEnabled removed. v0.5.2 D2: updateLastBackupExportAt removed
    // and both columns dropped by MIGRATION_11_12.

    @Query("UPDATE app_settings SET notif_permission_asked = :asked WHERE id = 1")
    suspend fun updateNotifPermissionAsked(asked: Boolean)

    @Query("UPDATE app_settings SET profile_photo_path = :path WHERE id = 1")
    suspend fun updateProfilePhotoPath(path: String?)

    @Query("UPDATE app_settings SET font_choice = :key WHERE id = 1")
    suspend fun updateFontChoice(key: String)

    @Query("UPDATE app_settings SET habit_checkin_time = :hhmm WHERE id = 1")
    suspend fun updateHabitCheckinTime(hhmm: String)

    // ------------------------------------------------------------------ Customization round (DB v16)
    // Every setter is its own single-column UPDATE, per the per-column write discipline (REV-25/04).
    @Query("UPDATE app_settings SET week_start = :v WHERE id = 1")
    suspend fun updateWeekStart(v: String)

    @Query("UPDATE app_settings SET clock_24h = :v WHERE id = 1")
    suspend fun updateClock24h(v: Boolean)

    @Query("UPDATE app_settings SET calendar_default_expanded = :v WHERE id = 1")
    suspend fun updateCalendarDefaultExpanded(v: Boolean)

    @Query("UPDATE app_settings SET greeting_tone = :v WHERE id = 1")
    suspend fun updateGreetingTone(v: String)

    @Query("UPDATE app_settings SET greeting_time_word = :v WHERE id = 1")
    suspend fun updateGreetingTimeWord(v: Boolean)

    @Query("UPDATE app_settings SET hero_style = :v WHERE id = 1")
    suspend fun updateHeroStyle(v: String)

    @Query("UPDATE app_settings SET habit_sort = :v WHERE id = 1")
    suspend fun updateHabitSort(v: String)

    @Query("UPDATE app_settings SET intake_sort = :v WHERE id = 1")
    suspend fun updateIntakeSort(v: String)

    @Query("UPDATE app_settings SET habit_show_archived = :v WHERE id = 1")
    suspend fun updateHabitShowArchived(v: Boolean)

    @Query("UPDATE app_settings SET intake_show_archived = :v WHERE id = 1")
    suspend fun updateIntakeShowArchived(v: Boolean)

    @Query("UPDATE app_settings SET home_hide_resolved = :v WHERE id = 1")
    suspend fun updateHomeHideResolved(v: Boolean)

    @Query("UPDATE app_settings SET reduce_motion = :v WHERE id = 1")
    suspend fun updateReduceMotion(v: Boolean)

    @Query("UPDATE app_settings SET quiet_hours_enabled = :v WHERE id = 1")
    suspend fun updateQuietHoursEnabled(v: Boolean)

    @Query("UPDATE app_settings SET quiet_start = :v WHERE id = 1")
    suspend fun updateQuietStart(v: String)

    @Query("UPDATE app_settings SET quiet_end = :v WHERE id = 1")
    suspend fun updateQuietEnd(v: String)

    @Query("UPDATE app_settings SET streak_mode = :v WHERE id = 1")
    suspend fun updateStreakMode(v: String)

    @Query("UPDATE app_settings SET show_streaks = :v WHERE id = 1")
    suspend fun updateShowStreaks(v: Boolean)

    @Query("UPDATE app_settings SET streak_rest_days = :v WHERE id = 1")
    suspend fun updateStreakRestDays(v: String)

    @Query("UPDATE app_settings SET default_landing_tab = :v WHERE id = 1")
    suspend fun updateDefaultLandingTab(v: String)

    @Query("UPDATE app_settings SET nav_tabs = :v WHERE id = 1")
    suspend fun updateNavTabs(v: String)

    @Query("UPDATE app_settings SET default_snooze_minutes = :v WHERE id = 1")
    suspend fun updateDefaultSnoozeMinutes(v: Int)
}