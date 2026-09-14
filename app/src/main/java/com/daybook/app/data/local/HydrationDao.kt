package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.HydrationDay
import kotlinx.coroutines.flow.Flow

/** Hydration habit — one row per local date, same shape as `DailyReportAiSummaryDao`. */
@Dao
interface HydrationDao {

    @Query("SELECT * FROM hydration_days WHERE local_date = :localDate")
    fun observe(localDate: String): Flow<HydrationDay?>

    @Query("SELECT * FROM hydration_days WHERE local_date = :localDate")
    suspend fun get(localDate: String): HydrationDay?

    @Query("SELECT * FROM hydration_days WHERE local_date BETWEEN :startYmd AND :endYmd")
    fun observeRange(startYmd: String, endYmd: String): Flow<List<HydrationDay>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: HydrationDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<HydrationDay>)

    @Query("SELECT * FROM hydration_days")
    suspend fun getAll(): List<HydrationDay>

    @Query("DELETE FROM hydration_days WHERE local_date = :localDate")
    suspend fun delete(localDate: String)

    @Query("DELETE FROM hydration_days WHERE local_date BETWEEN :startYmd AND :endYmd")
    suspend fun deleteInLocalDateRange(startYmd: String, endYmd: String)

    @Query("DELETE FROM hydration_days")
    suspend fun deleteAll()
}
