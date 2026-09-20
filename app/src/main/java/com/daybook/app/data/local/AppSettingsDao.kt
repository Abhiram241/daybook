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

    // DB v20 (UX overhaul item 4) — app theme mode ("DARK"/"LIGHT"/"SYSTEM").
    @Query("UPDATE app_settings SET theme_mode = :v WHERE id = 1")
    suspend fun updateThemeMode(v: String)

    // DB v21 (UX refinement round) — dark/light background style + corner-radius scale.
    @Query("UPDATE app_settings SET dark_style = :v WHERE id = 1")
    suspend fun updateDarkStyle(v: String)

    @Query("UPDATE app_settings SET light_style = :v WHERE id = 1")
    suspend fun updateLightStyle(v: String)

    @Query("UPDATE app_settings SET corner_scale = :v WHERE id = 1")
    suspend fun updateCornerScale(v: Float)

    // ------------------------------------------------------------------------ Round A (DB v22)
    @Query("UPDATE app_settings SET weight_unit = :v WHERE id = 1")
    suspend fun updateWeightUnit(v: String)

    @Query("UPDATE app_settings SET workout_accent_color = :v WHERE id = 1")
    suspend fun updateWorkoutAccentColor(v: String)

    @Query("UPDATE app_settings SET rest_timer_default_seconds = :v WHERE id = 1")
    suspend fun updateRestTimerDefaultSeconds(v: Int)

    @Query("UPDATE app_settings SET workout_hint_state = :v WHERE id = 1")
    suspend fun updateWorkoutHintState(v: Int)

    @Query("UPDATE app_settings SET workout_today_card_enabled = :v WHERE id = 1")
    suspend fun updateWorkoutTodayCardEnabled(v: Boolean)

    @Query("UPDATE app_settings SET default_exercise_group = :v WHERE id = 1")
    suspend fun updateDefaultExerciseGroup(v: String?)

    // ------------------------------------------------------------------------ Round B (DB v24)
    @Query("UPDATE app_settings SET health_tab_last_mode = :v WHERE id = 1")
    suspend fun updateHealthTabLastMode(v: Int)

    // ------------------------------------------------------------ DAILY_REPORT_REDESIGN_PLAN.md (DB v27)
    @Query("UPDATE app_settings SET ai_meta_prompt = :v WHERE id = 1")
    suspend fun updateAiMetaPrompt(v: String)

    @Query("UPDATE app_settings SET ai_report_categories = :v WHERE id = 1")
    suspend fun updateAiReportCategories(v: String)

    @Query("UPDATE app_settings SET ai_chat_range_start = :start, ai_chat_range_end = :end WHERE id = 1")
    suspend fun updateAiChatRange(start: String, end: String)

    @Query("UPDATE app_settings SET ai_chat_categories = :v WHERE id = 1")
    suspend fun updateAiChatCategories(v: String)

    // ---------------------------------------------- AI_CHAT..._PLAN.md §1 (DB v29)
    @Query("UPDATE app_settings SET ai_chat_meta_prompt = :v WHERE id = 1")
    suspend fun updateAiChatMetaPrompt(v: String)

    // ---------------------------------------------- Health tab card visibility (DB v30)
    @Query("UPDATE app_settings SET health_hidden_cards = :v WHERE id = 1")
    suspend fun updateHealthHiddenCards(v: String)

    // Hydration habit (DB v31).
    @Query("UPDATE app_settings SET hydration_enabled = :enabled, hydration_enabled_since = :since WHERE id = 1")
    suspend fun updateHydrationEnabled(enabled: Boolean, since: String)

    @Query("UPDATE app_settings SET hydration_goal_ml = :ml WHERE id = 1")
    suspend fun updateHydrationGoalMl(ml: Int)

    @Query("UPDATE app_settings SET hydration_unit = :unit WHERE id = 1")
    suspend fun updateHydrationUnit(unit: String)

    // Item 2 (DB v32).
    @Query("UPDATE app_settings SET exercise_details_prompt_dismissed = :v WHERE id = 1")
    suspend fun updateExerciseDetailsPromptDismissed(v: Boolean)

    // Build 50 item 2 (DB v33) — rest timer alert tone.
    @Query("UPDATE app_settings SET rest_timer_sound_uri = :v WHERE id = 1")
    suspend fun updateRestTimerSoundUri(v: String?)

    // Owner feedback round 2 (2026-09-20) (DB v35) — one setting per data point, replacing the
    // single combined `health_source_activity` setter above (still declared for schema validity —
    // see `AppSettings.healthSourceActivity`'s KDoc — but no longer written by any caller).
    @Query("UPDATE app_settings SET health_source_steps = :v WHERE id = 1")
    suspend fun updateHealthSourceSteps(v: String?)
    @Query("UPDATE app_settings SET health_source_distance = :v WHERE id = 1")
    suspend fun updateHealthSourceDistance(v: String?)
    @Query("UPDATE app_settings SET health_source_calories = :v WHERE id = 1")
    suspend fun updateHealthSourceCalories(v: String?)
    @Query("UPDATE app_settings SET health_source_heart_rate = :v WHERE id = 1")
    suspend fun updateHealthSourceHeartRate(v: String?)
    @Query("UPDATE app_settings SET health_source_spo2 = :v WHERE id = 1")
    suspend fun updateHealthSourceSpo2(v: String?)
    @Query("UPDATE app_settings SET health_source_hydration = :v WHERE id = 1")
    suspend fun updateHealthSourceHydration(v: String?)
    @Query("UPDATE app_settings SET health_source_weight = :v WHERE id = 1")
    suspend fun updateHealthSourceWeight(v: String?)
    @Query("UPDATE app_settings SET health_source_nutrition = :v WHERE id = 1")
    suspend fun updateHealthSourceNutrition(v: String?)
    @Query("UPDATE app_settings SET activity_source_backfill_done = :v WHERE id = 1")
    suspend fun updateActivitySourceBackfillDone(v: Boolean)

    // Owner feedback round 3 (2026-09-20) (DB v36) — ranked ordered list per metric, replacing the
    // single-pick columns above (still declared for schema validity, no longer written).
    @Query("UPDATE app_settings SET health_source_steps_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceStepsRanked(v: String?)
    @Query("UPDATE app_settings SET health_source_distance_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceDistanceRanked(v: String?)
    @Query("UPDATE app_settings SET health_source_calories_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceCaloriesRanked(v: String?)
    @Query("UPDATE app_settings SET health_source_heart_rate_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceHeartRateRanked(v: String?)
    @Query("UPDATE app_settings SET health_source_spo2_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceSpo2Ranked(v: String?)
    @Query("UPDATE app_settings SET health_source_hydration_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceHydrationRanked(v: String?)
    @Query("UPDATE app_settings SET health_source_weight_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceWeightRanked(v: String?)
    @Query("UPDATE app_settings SET health_source_nutrition_ranked = :v WHERE id = 1")
    suspend fun updateHealthSourceNutritionRanked(v: String?)

    // Owner feedback round 3 (2026-09-20) (DB v37) — Hydration-as-Intake.
    @Query("UPDATE app_settings SET hydration_reminder_time = :v WHERE id = 1")
    suspend fun updateHydrationReminderTime(v: String)
    @Query("UPDATE app_settings SET hydration_task_id = :v WHERE id = 1")
    suspend fun updateHydrationTaskId(v: String?)
}
