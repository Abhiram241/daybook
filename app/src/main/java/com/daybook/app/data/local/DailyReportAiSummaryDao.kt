package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.DailyReportAiSummary
import kotlinx.coroutines.flow.Flow

/** DAILY_REPORT_PLAN.md §3.6 — one row per local date; regenerating REPLACEs it, never appends. */
@Dao
interface DailyReportAiSummaryDao {

    @Query("SELECT * FROM daily_report_ai_summaries WHERE local_date = :localDate")
    fun observe(localDate: String): Flow<DailyReportAiSummary?>

    @Query("SELECT * FROM daily_report_ai_summaries WHERE local_date = :localDate")
    suspend fun get(localDate: String): DailyReportAiSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailyReportAiSummary)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(summaries: List<DailyReportAiSummary>)

    /** Full export path, mirroring `HealthDao.getAllDays()`. */
    @Query("SELECT * FROM daily_report_ai_summaries")
    suspend fun getAll(): List<DailyReportAiSummary>

    @Query("DELETE FROM daily_report_ai_summaries WHERE local_date BETWEEN :startYmd AND :endYmd")
    suspend fun deleteInLocalDateRange(startYmd: String, endYmd: String)

    @Query("DELETE FROM daily_report_ai_summaries")
    suspend fun deleteAll()
}
