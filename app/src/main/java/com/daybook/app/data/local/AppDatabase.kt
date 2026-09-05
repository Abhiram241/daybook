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
    CustomPrompt::class
], version = 19, exportSchema = true)
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
}