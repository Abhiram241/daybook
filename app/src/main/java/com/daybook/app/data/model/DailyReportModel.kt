package com.daybook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// DAILY_REPORT_PLAN.md §3.6 — one new table, 100% additive (MIGRATION_25_26). Kept in its own
// file, same reasoning WorkoutModel.kt / HealthModel.kt give: Room only cares about AppDatabase's
// entities list, not which file an @Entity lives in.

/**
 * The cached AI-generated summary for one local calendar day (decision 3 — generated once, shown
 * instantly on reopen, replaced in place by "Regenerate"; never a history of regenerations).
 *
 * The API key used to generate this is NEVER stored here — only `provider`/`model` travel with the
 * data (§3.2/§3.6), so a second device with the same account can see the summary without needing
 * the same key, but can't regenerate until it has its own key for that provider.
 */
@Serializable
@Entity(tableName = "daily_report_ai_summaries")
data class DailyReportAiSummary(
    @PrimaryKey @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "summary_text") val summaryText: String,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    /** BEAST_HEALTH_REPORT_AUDIT.md M4 — [com.daybook.app.data.settingsFingerprint] of the
     *  meta-prompt + report-categories CSV active when this row was generated (MIGRATION_27_28).
     *  Null for every row generated before this column existed — treated the same as "differs
     *  from current settings" (never claim staleness we can't actually rule out) by the comparison
     *  in `DailyReportScreen.kt`'s `AiSummaryPanel`. */
    @ColumnInfo(name = "settings_fingerprint") val settingsFingerprint: String? = null
)
