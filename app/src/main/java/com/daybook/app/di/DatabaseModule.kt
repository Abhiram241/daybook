package com.daybook.app.di

import android.content.Context
import androidx.room.Room
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.CustomCategoryRepository
import com.daybook.app.data.CustomPromptRepository
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.local.MIGRATION_2_3
import com.daybook.app.data.local.MIGRATION_3_4
import com.daybook.app.data.local.MIGRATION_4_5
import com.daybook.app.data.local.MIGRATION_5_6
import com.daybook.app.data.local.MIGRATION_6_7
import com.daybook.app.data.local.MIGRATION_7_8
import com.daybook.app.data.local.MIGRATION_8_9
import com.daybook.app.data.local.MIGRATION_9_10
import com.daybook.app.data.local.MIGRATION_10_11
import com.daybook.app.data.local.MIGRATION_11_12
import com.daybook.app.data.local.MIGRATION_12_13
import com.daybook.app.data.local.MIGRATION_13_14
import com.daybook.app.data.local.MIGRATION_14_15
import com.daybook.app.data.local.MIGRATION_15_16
import com.daybook.app.data.local.MIGRATION_16_17
import com.daybook.app.data.local.MIGRATION_17_18
import com.daybook.app.data.local.MIGRATION_18_19
import com.daybook.app.data.local.MIGRATION_19_20
import com.daybook.app.data.local.MIGRATION_20_21
import com.daybook.app.data.local.MIGRATION_21_22
import com.daybook.app.data.local.MIGRATION_22_23
import com.daybook.app.data.local.MIGRATION_23_24
import com.daybook.app.data.local.MIGRATION_24_25
import com.daybook.app.data.local.MIGRATION_25_26
import com.daybook.app.data.local.MIGRATION_26_27
import com.daybook.app.data.local.MIGRATION_27_28
import com.daybook.app.data.local.MIGRATION_28_29
import com.daybook.app.data.local.MIGRATION_29_30
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.HealthRepository
import com.daybook.app.data.health.HealthConnectReader
import com.daybook.app.data.health.HealthSyncStateStore
import com.daybook.app.data.workout.ExerciseCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "daybook_database"
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30)
            // No 1->2 path exists, so a v1 database would otherwise throw on open and every DB
            // touch (including the alarm receiver's) would fail silently behind runCatching.
            .fallbackToDestructiveMigrationFrom(1)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideHabitRepository(database: AppDatabase): HabitRepository =
        HabitRepository(database)

    @Provides
    @Singleton
    fun provideFoodMedRepository(database: AppDatabase): FoodMedRepository =
        FoodMedRepository(database)

    @Provides
    @Singleton
    fun provideAppSettingsRepository(
        @ApplicationContext context: Context,
        database: AppDatabase
    ): AppSettingsRepository =
        AppSettingsRepository(context, database)

    @Provides
    @Singleton
    fun provideCustomCategoryRepository(database: AppDatabase): CustomCategoryRepository =
        CustomCategoryRepository(database)

    @Provides
    @Singleton
    fun provideCustomPromptRepository(database: AppDatabase): CustomPromptRepository =
        CustomPromptRepository(database)

    // A3: Round A (workout mode).
    @Provides
    @Singleton
    fun provideExerciseCatalog(@ApplicationContext context: Context): ExerciseCatalog =
        ExerciseCatalog(context)

    // A8: Hevy CSV import.
    @Provides
    @Singleton
    fun provideHevyImporter(
        database: AppDatabase,
        exerciseCatalog: ExerciseCatalog,
        cloudSyncRepository: com.daybook.app.data.sync.CloudSyncRepository
    ): com.daybook.app.data.workout.HevyImporter =
        com.daybook.app.data.workout.HevyImporter(database, exerciseCatalog, cloudSyncRepository)

    @Provides
    @Singleton
    fun provideWorkoutRepository(
        database: AppDatabase,
        exerciseCatalog: ExerciseCatalog,
        appSettingsRepository: AppSettingsRepository,
        hevyImporter: com.daybook.app.data.workout.HevyImporter
    ): WorkoutRepository =
        WorkoutRepository(database, exerciseCatalog, appSettingsRepository, hevyImporter)

    // B3: Round B (Health Connect, §7.3).
    @Provides
    @Singleton
    fun provideHealthConnectReader(@ApplicationContext context: Context): HealthConnectReader =
        HealthConnectReader(context)

    @Provides
    @Singleton
    fun provideHealthSyncStateStore(@ApplicationContext context: Context): HealthSyncStateStore =
        HealthSyncStateStore(context)

    @Provides
    @Singleton
    fun provideHealthRepository(
        database: AppDatabase,
        reader: HealthConnectReader,
        stateStore: HealthSyncStateStore
    ): HealthRepository =
        HealthRepository(database, reader, stateStore)

    // DAILY_REPORT_PLAN.md §4.
    @Provides
    @Singleton
    fun provideDailyReportRepository(
        database: AppDatabase,
        workoutRepository: WorkoutRepository,
        appSettingsRepository: AppSettingsRepository
    ): com.daybook.app.data.DailyReportRepository =
        com.daybook.app.data.DailyReportRepository(database, workoutRepository, appSettingsRepository)
}
