package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.WorkoutSessionPhoto
import kotlinx.coroutines.flow.Flow

/** Item 12 — up to 3 rows per session, ordered by `order_index`. See `WorkoutSessionPhoto`'s KDoc
 *  for why this table has no foreign key and never joins sync. */
@Dao
interface WorkoutPhotoDao {

    @Query("SELECT * FROM workout_session_photos WHERE session_id = :sessionId ORDER BY order_index ASC")
    fun observeForSession(sessionId: String): Flow<List<WorkoutSessionPhoto>>

    @Query("SELECT * FROM workout_session_photos WHERE session_id = :sessionId ORDER BY order_index ASC")
    suspend fun getForSession(sessionId: String): List<WorkoutSessionPhoto>

    /** Batched — `WorkoutHistoryViewModel` resolves a whole page of sessions' photos in one query,
     *  same "never a query per row" rule the rest of History follows (item 13's own constraint). */
    @Query("SELECT * FROM workout_session_photos WHERE session_id IN (:sessionIds) ORDER BY order_index ASC")
    suspend fun getForSessions(sessionIds: List<String>): List<WorkoutSessionPhoto>

    /** Full export path (manual "Export my data" — item 12 open question 2, owner said yes). */
    @Query("SELECT * FROM workout_session_photos")
    suspend fun getAll(): List<WorkoutSessionPhoto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: WorkoutSessionPhoto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<WorkoutSessionPhoto>)

    @Query("DELETE FROM workout_session_photos WHERE id = :id")
    suspend fun deleteById(id: String)

    /** The main cleanup hook — `WorkoutRepository.deleteSession` and every other session-destroying
     *  path (full wipe, month re-hydration, sync eviction, sign-out) must call this so no row is
     *  ever orphaned. Callers collect `filePathsForSessions` first (for the file delete, done
     *  OUTSIDE the transaction) then call this inside the same transaction as the session delete. */
    @Query("DELETE FROM workout_session_photos WHERE session_id IN (:sessionIds)")
    suspend fun deleteForSessions(sessionIds: List<String>)

    @Query("SELECT file_path FROM workout_session_photos WHERE session_id IN (:sessionIds)")
    suspend fun filePathsForSessions(sessionIds: List<String>): List<String>

    @Query("SELECT file_path FROM workout_session_photos")
    suspend fun allFilePaths(): List<String>

    @Query("SELECT COUNT(*) FROM workout_session_photos WHERE session_id = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("DELETE FROM workout_session_photos")
    suspend fun deleteAll()

    /** Firebase Storage backup round (2026-09-20) — see `WorkoutPhotoCloudSync`'s KDoc. */
    @Query("UPDATE workout_session_photos SET cloud_path = :cloudPath, cloud_uploaded_at = :uploadedAt WHERE id = :id")
    suspend fun markUploaded(id: String, cloudPath: String, uploadedAt: Long)

    @Query("SELECT * FROM workout_session_photos WHERE cloud_uploaded_at IS NULL")
    suspend fun getPendingUpload(): List<WorkoutSessionPhoto>
}
