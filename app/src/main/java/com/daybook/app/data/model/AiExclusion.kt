package com.daybook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.2 — a "hide this from the AI" marker. Two
 * independent lists (one per [scope]) so a journal entry can be hidden from Chat while still
 * feeding the AI Summary, or vice versa. This is a privacy preference, not user content — still
 * NOT in BackupModel/ContentHash (the plain JSON file backup/restore path is untouched by this).
 *
 * User request (Firestore sync for AI meta-prompts + privacy settings): this table now IS synced,
 * per-user, full-replace on the Firestore parent doc's `aiExclusions` field (S2's original "NOT
 * synced" call was revisited — a privacy choice made on one device should follow the account to
 * every other device signed into it) — see `CloudSyncRepository.buildAiSyncSettings`/
 * `applyRemoteAiSettings`.
 *
 * [targetId] is either a habit/task id ([kind] HABIT/TASK, hides every past+future entry for that
 * item) or a deterministic occurrence id "$itemId:$millis" ([kind] HABIT_ENTRY/TASK_ENTRY, hides
 * just that one logged entry). The same occurrence id is produced on every device and survives a
 * re-sync of that month (ExportImportRepository.occId), so an entry exclusion keeps applying.
 */
@Entity(tableName = "ai_exclusions", primaryKeys = ["scope", "kind", "target_id"])
data class AiExclusion(
    @ColumnInfo(name = "scope") val scope: String,        // "SUMMARY" | "CHAT"
    @ColumnInfo(name = "kind") val kind: String,          // "HABIT" | "TASK" | "HABIT_ENTRY" | "TASK_ENTRY"
    @ColumnInfo(name = "target_id") val targetId: String, // habit/task id, or occurrence id "itemId:millis"
    @ColumnInfo(name = "created_at") val createdAt: Long
)
