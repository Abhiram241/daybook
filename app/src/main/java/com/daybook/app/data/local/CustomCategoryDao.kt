package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.CustomCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT name FROM custom_categories ORDER BY created_at DESC")
    fun observeNames(): Flow<List<String>>

    @Query("SELECT name FROM custom_categories ORDER BY created_at DESC")
    suspend fun getNames(): List<String>

    /** IGNORE, not REPLACE: re-using an existing category must not reset its created_at. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CustomCategory): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vararg categories: CustomCategory): LongArray

    @Query("DELETE FROM custom_categories WHERE name = :name")
    suspend fun deleteByName(name: String)

    /** Full wipe — backup restore path only, inside its transaction. */
    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()
}
