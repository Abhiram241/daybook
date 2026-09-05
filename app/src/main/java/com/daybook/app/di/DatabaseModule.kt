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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
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
    fun provideAppSettingsRepository(database: AppDatabase): AppSettingsRepository =
        AppSettingsRepository(database)

    @Provides
    @Singleton
    fun provideCustomCategoryRepository(database: AppDatabase): CustomCategoryRepository =
        CustomCategoryRepository(database)

    @Provides
    @Singleton
    fun provideCustomPromptRepository(database: AppDatabase): CustomPromptRepository =
        CustomPromptRepository(database)
}
