package com.daybook.app.di

import android.content.Context
import androidx.room.Room
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.CustomCategoryRepository
import com.daybook.app.data.CustomPromptRepository
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.local.ALL_MIGRATIONS
import com.daybook.app.data.local.AppDatabase
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
            // DI-2 fix (AUDIT_2026-09-16.md) — was a hand-maintained list duplicating
            // `MigrationTest.fullOpenAtLatestVersion()`'s own hand-maintained list; the two had
            // already drifted two versions apart with nothing to catch it. Both now read
            // `ALL_MIGRATIONS` (Migrations.kt) — add a new migration there once.
            .addMigrations(*ALL_MIGRATIONS)
            // No 1->2 path exists, so a v1 database would otherwise throw on open and every DB
            // touch (including the alarm receiver's) would fail silently behind runCatching.
            .fallbackToDestructiveMigrationFrom(1)
            // DI-1 fix (AUDIT_2026-09-16.md) — deliberately NOT
            // .fallbackToDestructiveMigrationOnDowngrade(). That line silently dropped every table
            // whenever the on-disk schema version was higher than the running build's — exactly
            // what happens side-loading an older APK over a newer one, which this repo's own
            // workflow does routinely (see old-apks-DO-NOT-INSTALL/README.txt). A downgrade must
            // throw on open (loud, recoverable by reinstalling the newer build), never wipe data.
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
        hevyImporter: com.daybook.app.data.workout.HevyImporter,
        workoutPhotoStore: com.daybook.app.data.WorkoutPhotoStore,
        workoutPhotoCloudSync: com.daybook.app.data.WorkoutPhotoCloudSync,
        @ApplicationContext context: Context
    ): WorkoutRepository =
        WorkoutRepository(database, exerciseCatalog, appSettingsRepository, hevyImporter, workoutPhotoStore, workoutPhotoCloudSync, context)

    // B3: Round B (Health Connect, §7.3).
    @Provides
    @Singleton
    fun provideHealthConnectReader(@ApplicationContext context: Context): HealthConnectReader =
        HealthConnectReader(context)

    @Provides
    @Singleton
    fun provideHealthConnectHydrationWriter(@ApplicationContext context: Context): com.daybook.app.data.health.HealthConnectHydrationWriter =
        com.daybook.app.data.health.HealthConnectHydrationWriter(context)

    @Provides
    @Singleton
    fun provideHealthSyncStateStore(@ApplicationContext context: Context): HealthSyncStateStore =
        HealthSyncStateStore(context)

    @Provides
    @Singleton
    fun provideHealthRepository(
        database: AppDatabase,
        reader: HealthConnectReader,
        stateStore: HealthSyncStateStore,
        appSettingsRepository: AppSettingsRepository,
        // Audit finding #1 fix — see HealthRepository.pullWindow's residency guard.
        cloudSyncProvider: javax.inject.Provider<com.daybook.app.data.sync.CloudSyncRepository>
    ): HealthRepository =
        HealthRepository(database, reader, stateStore, appSettingsRepository, cloudSyncProvider)

    // DAILY_REPORT_PLAN.md §4.
    @Provides
    @Singleton
    fun provideDailyReportRepository(
        database: AppDatabase,
        workoutRepository: WorkoutRepository,
        appSettingsRepository: AppSettingsRepository,
        // Audit finding #1 fix — see DailyReportRepository.saveAiSummary's residency guard.
        cloudSyncProvider: javax.inject.Provider<com.daybook.app.data.sync.CloudSyncRepository>
    ): com.daybook.app.data.DailyReportRepository =
        com.daybook.app.data.DailyReportRepository(database, workoutRepository, appSettingsRepository, cloudSyncProvider)
}
