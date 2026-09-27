package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.model.HealthSession
import com.daybook.app.data.model.HealthWeightReading
import kotlinx.coroutines.flow.Flow

/**
 * B2 (§7.1/§7.3) — day + session queries: a single-day read for `Day` mode, a range read for
 * `Range` mode (§7.4), a range read for export, and range deletes for eviction/month-scoped
 * import (§7.5). No foreign keys, same reasoning as `WorkoutDao` — `health_days`/`health_sessions`
 * are upserted independently by `HealthRepository` and by `ExportImportRepository`'s restore path.
 */
@Dao
interface HealthDao {

    // ------------------------------------------------------------------------------ health_days
    @Query("SELECT * FROM health_days WHERE local_date = :localDate")
    fun observeDay(localDate: String): Flow<HealthDay?>

    @Query("SELECT * FROM health_days WHERE local_date = :localDate")
    suspend fun getDay(localDate: String): HealthDay?

    /** [owner feedback, 2026-09-20] — the auto-resolution candidate pool for
     *  `HealthSourcePriority.resolveAuto`: the most recent day that actually has a recorded set of
     *  contributing sources. */
    @Query("SELECT activity_source_apps FROM health_days WHERE activity_source_apps IS NOT NULL ORDER BY local_date DESC LIMIT 1")
    suspend fun mostRecentActivitySourceApps(): String?

    /** [owner feedback, 2026-09-20] — bounds `HealthRepository.backfillActivitySourceApps` to
     *  exactly the range the user's own history already covers. */
    @Query("SELECT MIN(local_date) FROM health_days")
    suspend fun earliestLocalDate(): String?

    @Query("SELECT * FROM health_days WHERE local_date BETWEEN :startYmd AND :endYmd ORDER BY local_date ASC")
    fun observeDaysInRange(startYmd: String, endYmd: String): Flow<List<HealthDay>>

    @Query("SELECT * FROM health_days WHERE local_date BETWEEN :startYmd AND :endYmd ORDER BY local_date ASC")
    suspend fun getDaysInRange(startYmd: String, endYmd: String): List<HealthDay>

    /** Full export path (§7.5) — every day, regardless of month.
     *
     *  Audit finding #22 fix — see WorkoutDao.getAllSessions' identical fix; `local_date` is this
     *  table's own natural, stable ordering key. */
    @Query("SELECT * FROM health_days ORDER BY local_date ASC")
    suspend fun getAllDays(): List<HealthDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: HealthDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDays(days: List<HealthDay>)

    /** Month eviction / month import (§7.5). */
    @Query("DELETE FROM health_days WHERE local_date BETWEEN :startYmd AND :endYmd")
    suspend fun deleteDaysInLocalDateRange(startYmd: String, endYmd: String)

    /** S-1 fix — month-merge keep-local-if-not-named: only the dates the incoming payload
     *  actually carries health data for are cleared, mirroring `staleSessionIds`. */
    @Query("DELETE FROM health_days WHERE local_date IN (:dates)")
    suspend fun deleteDaysByLocalDates(dates: List<String>)

    /** Full wipe — the Beast Mode backup restore path only. */
    @Query("DELETE FROM health_days")
    suspend fun deleteAllDays()

    // -------------------------------------------------------------------------- health_sessions
    @Query("SELECT * FROM health_sessions WHERE local_date = :localDate ORDER BY start_millis ASC")
    fun observeSessionsForDay(localDate: String): Flow<List<HealthSession>>

    @Query("SELECT * FROM health_sessions WHERE local_date BETWEEN :startYmd AND :endYmd ORDER BY start_millis ASC")
    fun observeSessionsInRange(startYmd: String, endYmd: String): Flow<List<HealthSession>>

    @Query("SELECT * FROM health_sessions WHERE local_date BETWEEN :startYmd AND :endYmd ORDER BY start_millis ASC")
    suspend fun getSessionsInRange(startYmd: String, endYmd: String): List<HealthSession>

    /** Full export path (§7.5) — every session, regardless of month.
     *
     *  Audit finding #22 fix — see WorkoutDao.getAllSessions' identical fix. */
    @Query("SELECT * FROM health_sessions ORDER BY id ASC")
    suspend fun getAllSessions(): List<HealthSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<HealthSession>)

    /** Month eviction / month import (§7.5). */
    @Query("DELETE FROM health_sessions WHERE local_date BETWEEN :startYmd AND :endYmd")
    suspend fun deleteSessionsInLocalDateRange(startYmd: String, endYmd: String)

    /** S-1 fix — see `deleteDaysByLocalDates`. */
    @Query("DELETE FROM health_sessions WHERE local_date IN (:dates)")
    suspend fun deleteSessionsByLocalDates(dates: List<String>)

    /** Full wipe — the Beast Mode backup restore path only. */
    @Query("DELETE FROM health_sessions")
    suspend fun deleteAllSessions()

    /** §6.3's changes-token full-resync fallback and "Import my past data" both re-read a whole
     *  window; existing ids in that window are needed so a re-read upserts cleanly. D-3 fix — also
     *  now the reconciliation source: any id in this set no longer present in a fresh window read
     *  is stale (deleted upstream, or re-issued under a new Health Connect id) and gets removed. */
    @Query("SELECT id FROM health_sessions WHERE start_millis BETWEEN :fromMillis AND :toMillis")
    suspend fun sessionIdsStartingBetween(fromMillis: Long, toMillis: Long): List<String>

    /** D-3 fix — the reconciliation delete: ids `sessionIdsStartingBetween` found that a fresh
     *  window read no longer produced. */
    @Query("DELETE FROM health_sessions WHERE id IN (:ids)")
    suspend fun deleteSessionsByIds(ids: List<String>)

    // ------------------------------------------------------------------ health_weight_readings
    // HEALTH_VITALS_RICHNESS_PLAN.md §4 — same shape as the health_sessions block above.
    @Query("SELECT * FROM health_weight_readings WHERE local_date = :localDate ORDER BY at_millis DESC")
    fun observeWeightReadingsForDay(localDate: String): Flow<List<HealthWeightReading>>

    @Query("SELECT * FROM health_weight_readings WHERE local_date BETWEEN :startYmd AND :endYmd ORDER BY at_millis ASC")
    fun observeWeightReadingsInRange(startYmd: String, endYmd: String): Flow<List<HealthWeightReading>>

    /** Full export path (§7.5) — every reading, regardless of month. */
    @Query("SELECT * FROM health_weight_readings")
    suspend fun getAllWeightReadings(): List<HealthWeightReading>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeightReadings(readings: List<HealthWeightReading>)

    /** Month eviction / month import (§5). */
    @Query("DELETE FROM health_weight_readings WHERE local_date BETWEEN :startYmd AND :endYmd")
    suspend fun deleteWeightReadingsBetween(startYmd: String, endYmd: String)

    /** S-1 fix — see `deleteDaysByLocalDates`. */
    @Query("DELETE FROM health_weight_readings WHERE local_date IN (:dates)")
    suspend fun deleteWeightReadingsByLocalDates(dates: List<String>)

    /** Full wipe — the Beast Mode backup restore path only. */
    @Query("DELETE FROM health_weight_readings")
    suspend fun deleteAllWeightReadings()

    /** D-3 fix — see `sessionIdsStartingBetween`, same reconciliation shape. */
    @Query("SELECT id FROM health_weight_readings WHERE at_millis BETWEEN :fromMillis AND :toMillis")
    suspend fun weightReadingIdsBetween(fromMillis: Long, toMillis: Long): List<String>

    /** D-3 fix — see `deleteSessionsByIds`. */
    @Query("DELETE FROM health_weight_readings WHERE id IN (:ids)")
    suspend fun deleteWeightReadingsByIds(ids: List<String>)
}
