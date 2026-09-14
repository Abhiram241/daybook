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
    com.daybook.app.data.model.HydrationDay::class
], version = 31, exportSchema = true)
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
}