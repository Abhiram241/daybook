package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.daybook.app.data.model.Habit
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE is_archived = 0 ORDER BY created_at DESC")
    fun getActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits")
    fun observeAllHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabitById(habitId: String): Habit?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(habit: Habit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg habits: Habit): LongArray

    @Update
    suspend fun update(habit: Habit)

    @Delete
    suspend fun delete(habit: Habit)

    @Query("UPDATE habits SET is_archived = 1 WHERE id = :habitId") 
    suspend fun archiveHabit(habitId: String)

    @Query("UPDATE habits SET is_archived = 0 WHERE id = :habitId")
    suspend fun unarchiveHabit(habitId: String)

    /** Full wipe — used only by the backup restore path (L4), inside its transaction. */
    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    /** v0.5.3 Phase 1 (S6): every habit id, to diff against a remote definitions set. */
    @Query("SELECT id FROM habits")
    suspend fun allIds(): List<String>

    /** v0.5.3 Phase 1 (S6): delete only the definition rows absent from the remote set. */
    @Query("DELETE FROM habits WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** v0.5.5: begin the running day-count for an "Ongoing" (STREAK) habit. Single-column write. */
    @Query("UPDATE habits SET streak_started_at = :nowMillis WHERE id = :habitId")
    suspend fun startStreak(habitId: String, nowMillis: Long)

    /** v0.5.5: "Mark as broken" — clear the current run, keep the recorded longest. */
    @Query("UPDATE habits SET streak_started_at = NULL, streak_longest = :longest WHERE id = :habitId")
    suspend fun clearStreak(habitId: String, longest: Int)

    /** Customization round (rec 8): per-habit custom notification text. Single-column write. */
    @Query("UPDATE habits SET prompt_message = :v WHERE id = :habitId")
    suspend fun updatePromptMessage(habitId: String, v: String?)

    /** Customization round (rec 8 / HA1): per-habit "why this matters" note. Single-column write. */
    @Query("UPDATE habits SET motivation = :v WHERE id = :habitId")
    suspend fun updateMotivation(habitId: String, v: String?)
}
