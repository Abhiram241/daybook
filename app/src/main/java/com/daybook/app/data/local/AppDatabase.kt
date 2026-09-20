package com.daybook.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.daybook.app.data.model.AppSettings
import com.daybook.app.data.model.CustomCategory
import com.daybook.app.data.model.CustomPrompt
import com.daybook.app.data.model.FoodMedEvent
import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitEvent
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.Exercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.model.WorkoutRoutine
import com.daybook.app.data.model.WorkoutRoutineExercise
import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.model.HealthSession
import com.daybook.app.data.model.HealthWeightReading
import com.daybook.app.data.model.DailyReportAiSummary
import com.daybook.app.data.model.AiExclusion
import com.daybook.app.util.enums.Converters

@Database(entities = [
    Habit::class,
    HabitOccurrence::class,
    HabitEvent::class,
    FoodMedTask::class,
    FoodMedOccurrence::class,
    FoodMedEvent::class,
    AppSettings::class,
    CustomCategory::class,
    CustomPrompt::class,
    // A1: Round A (workout mode). Six new tables, 100% additive (MIGRATION_21_22).
    Exercise::class,
    WorkoutSession::class,
    WorkoutExercise::class,
    WorkoutSet::class,
    WorkoutRoutine::class,
    WorkoutRoutineExercise::class,
    // B2: Round B (Health Connect). Two new tables, 100% additive (MIGRATION_23_24, §7.2).
    HealthDay::class,
    HealthSession::class,
    // HEALTH_VITALS_RICHNESS_PLAN.md V1: one new table, 100% additive (MIGRATION_24_25, §2).
    HealthWeightReading::class,
    // DAILY_REPORT_PLAN.md §3.6: one new table, 100% additive (MIGRATION_25_26).
    DailyReportAiSummary::class,
    // BEAST_HEALTH_REPORT_AUDIT.md M4: one new nullable column on that table, 100% additive
    // (MIGRATION_27_28).
    // AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.2: one new table, 100% additive
    // (MIGRATION_28_29). Device-only — never in DATA_TABLES/BackupModel/ContentHash (S2).
    AiExclusion::class,
    // Hydration habit: one new table + four app_settings columns, 100% additive (MIGRATION_30_31).
    com.daybook.app.data.model.HydrationDay::class,
    // BEAST_MODE_ROUND_2026-09-16_PLAN.md §S1: one new table + one app_settings column, 100%
    // additive (MIGRATION_31_32). WorkoutSessionPhoto is device-local only — see its own KDoc.
    com.daybook.app.data.model.WorkoutSessionPhoto::class
    // Build 50 (owner review of the Health tab screenshot): two additive nullable columns, 100%
    // additive (MIGRATION_32_33) — `health_days.activity_source_apps` (item 1 — "which app/device
    // wrote this") and `app_settings.rest_timer_sound_uri` (item 2 — customizable rest-timer tone).
    // See each column's own @ColumnInfo KDoc.
    // [owner feedback, 2026-09-20]: four additive `app_settings` columns, 100% additive
    // (MIGRATION_33_34) — `health_source_activity`/`health_source_weight`/`health_source_nutrition`
    // (per-metric-family Health Connect source preference, used instead of naively summing every
    // contributing app's records) and `activity_source_backfill_done` (one-time gate for the
    // `activity_source_apps` historical backfill). See each column's own @ColumnInfo KDoc.
    // Owner feedback round 2 (2026-09-20): six additive `app_settings` columns, 100% additive
    // (MIGRATION_34_35) — `health_source_activity` split into one column per data point
    // (`health_source_steps`/`_distance`/`_calories`/`_heart_rate`/`_spo2`/`_hydration`), seeded
    // from the old combined value. See each column's own @ColumnInfo KDoc.
    // Owner feedback round 3 (2026-09-20): eight additive `app_settings` *_ranked columns
    // (MIGRATION_35_36) — single-pick-per-metric source preference upgraded to a ranked ordered
    // list. Two additive `app_settings` columns (MIGRATION_36_37) — Hydration-as-Intake reminder
    // time/task id. Two additive `workout_session_photos` columns (MIGRATION_37_38) — Firebase
    // Storage backup (cloud_path/cloud_uploaded_at). See SOURCE_PRIORITY_AND_HYDRATION_PLAN.md.
], version = 38, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitOccurrenceDao(): HabitOccurrenceDao
    abstract fun habitEventDao(): HabitEventDao
    abstract fun foodMedTaskDao(): FoodMedTaskDao
    abstract fun foodMedOccurrenceDao(): FoodMedOccurrenceDao
    abstract fun foodMedEventDao(): FoodMedEventDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun customPromptDao(): CustomPromptDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao
    abstract fun healthDao(): HealthDao
    abstract fun dailyReportAiSummaryDao(): DailyReportAiSummaryDao
    abstract fun aiExclusionDao(): AiExclusionDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun workoutPhotoDao(): WorkoutPhotoDao
}