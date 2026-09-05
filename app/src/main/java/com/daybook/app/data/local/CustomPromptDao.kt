package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.CustomPrompt
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPromptDao {
    @Query("SELECT name FROM custom_prompts ORDER BY created_at DESC")
    fun observeNames(): Flow<List<String>>

    @Query("SELECT name FROM custom_prompts ORDER BY created_at DESC")
    suspend fun getNames(): List<String>

    /** IGNORE, not REPLACE: re-using an existing prompt must not reset its created_at. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(prompt: CustomPrompt): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vararg prompts: CustomPrompt): LongArray

    @Query("DELETE FROM custom_prompts WHERE name = :name")
    suspend fun deleteByName(name: String)

    /** Full wipe — backup restore path only, inside its transaction. */
    @Query("DELETE FROM custom_prompts")
    suspend fun deleteAll()
}
