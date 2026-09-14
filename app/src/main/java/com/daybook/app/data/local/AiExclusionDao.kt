package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.AiExclusion
import kotlinx.coroutines.flow.Flow

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.2. */
@Dao
interface AiExclusionDao {
    @Query("SELECT * FROM ai_exclusions WHERE scope = :scope")
    fun observe(scope: String): Flow<List<AiExclusion>>

    @Query("SELECT * FROM ai_exclusions WHERE scope = :scope")
    suspend fun get(scope: String): List<AiExclusion>

    /** User request (Firestore sync) — every row, both scopes, for the parent-doc push. */
    @Query("SELECT * FROM ai_exclusions")
    suspend fun getAll(): List<AiExclusion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AiExclusion)

    /** User request (Firestore sync) — bulk upsert for a remote-parent-doc apply. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AiExclusion>)

    @Query("DELETE FROM ai_exclusions WHERE scope = :scope AND kind = :kind AND target_id = :id")
    suspend fun remove(scope: String, kind: String, id: String)

    /** Habit/task deletion cleanup — removes both the whole-item row ([kind] HABIT/TASK) and every
     *  per-entry row for it (`target_id` prefixed `"$id:"`). */
    @Query("DELETE FROM ai_exclusions WHERE target_id = :id OR target_id LIKE :id || ':%'")
    suspend fun deleteForItem(id: String)

    @Query("INSERT OR IGNORE INTO ai_exclusions (scope, kind, target_id, created_at) " +
        "SELECT :to, kind, target_id, :now FROM ai_exclusions WHERE scope = :from")
    suspend fun copyScope(from: String, to: String, now: Long)

    @Query("DELETE FROM ai_exclusions")
    suspend fun deleteAll()
}
