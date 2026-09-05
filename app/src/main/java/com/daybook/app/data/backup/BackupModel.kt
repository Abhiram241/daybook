@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.daybook.app.data.backup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * Daybook backup format **v2** (L4) — a clean break from the v1 "export bundle", which was a raw
 * dump of every Room row (including device-local notification ids, autoincrement event rowids and
 * the settings row with the user's name).
 *
 * v2 is day-partitioned and self-describing:
 *  - `definitions` holds the habits and intake reminders exactly once, in domain terms
 *    ("HH:mm" times, 1..7 weekdays) rather than the internal comma-joined `*_json` columns;
 *  - `days` is the history, one entry per local calendar date, sorted oldest → newest, each fully
 *    self-contained. A per-day / per-week / per-month rollup is a fold over `days` — no Room
 *    access, no cross-referencing beyond the definition ids.
 *
 * `meta` deliberately carries **no** username, device id or internal counters: a backup is data,
 * not an identity, and restore never validates who made it.
 *
 * v1 files are rejected on import (see ExportImportRepository); there is no upgrade path.
 */
@Serializable
data class DaybookBackup(
    val meta: BackupMeta,
    val definitions: Definitions,
    /** Sorted ascending by `date`. */
    val days: List<DayEntry>
)

@Serializable
data class BackupMeta(
    /** 2 = this format. 1 = the legacy bundle, which this app refuses. */
    val formatVersion: Int = FORMAT_VERSION,
    /** ISO-8601 UTC, e.g. "2026-08-29T14:03:11Z". */
    val exportedAt: String,
    val appVersionName: String,
    /**
     * v0.5.3 Phase 6 (D2): the local-date span this file covers, "yyyy-MM-dd", or **null** on a
     * full export. Optional-with-default so every existing v2 file and every already-synced cloud
     * month doc still parse unchanged. Deliberately NOT part of [com.daybook.app.data.sync.ContentHash]
     * (it hashes `definitions + days` only), so a range field cannot destabilise the sync loop.
     * A restore branches on `rangeStart != null` to pick the non-destructive merge path.
     */
    val rangeStart: String? = null,
    val rangeEnd: String? = null
) {
    companion object { const val FORMAT_VERSION = 2 }
}

@Serializable
data class Definitions(
    val habits: List<HabitDef> = emptyList(),
    val intakeReminders: List<IntakeReminderDef> = emptyList(),
    /** v0.5.2: saved reusable CUSTOM/JOURNAL category names. Optional-with-default so old backups
     *  and already-synced cloud month docs deserialize unchanged. */
    val customCategories: List<String> = emptyList(),
    /** v0.5.3: saved reusable intake prompt messages. Optional-with-default (same reason). */
    val customPrompts: List<String> = emptyList(),
    // Journal-as-habit round: the GLOBAL journal-question set is retired — replaced entirely by
    // the per-habit HabitDef.journalQuestions below. No migration-forward of old content (user's
    // explicit "fresh start" decision); an old backup's `journalQuestions` key is simply ignored
    // on decode (ignoreUnknownKeys = true).
)

@Serializable
data class HabitDef(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorTag: String,
    /** "HH:mm", sorted ascending. */
    val times: List<String> = emptyList(),
    /** 1=Mon … 7=Sun, sorted. Empty means every day. */
    val activeDays: List<Int> = emptyList(),
    val snoozeMinutes: Int = 10,
    /** ISO-8601 UTC. */
    val createdAt: String,
    val archived: Boolean = false,
    /** v0.5.2: INDIVIDUAL / BATCH / STREAK. Optional-with-default. */
    val type: String = "INDIVIDUAL",
    /** v0.5.5: "Ongoing" (STREAK) habit — epoch millis the running count started, or null when not
     *  started. `@EncodeDefault(NEVER)` so a null (the default) is ABSENT in the canonical bytes. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val streakStartedAt: Long? = null,
    /** v0.5.5: longest run remembered across "Mark as broken". `@EncodeDefault(NEVER)` so 0 (the
     *  default) is ABSENT even though `ContentHash` / `JsonUtils` set `encodeDefaults = true` — a
     *  user with zero Ongoing habits sees ZERO change to `definitionsHash` (mirrors
     *  `Definitions.journalQuestions`). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val streakLongest: Int = 0,
    /** Customization round (rec 8 / IJ3): per-habit custom notification text. `@EncodeDefault(NEVER)`
     *  so a null (the default) is ABSENT in the canonical bytes — a user who sets none sees a
     *  byte-identical `definitionsHash` (mirrors `streakLongest` / `journalQuestions`). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val promptMessage: String? = null,
    /** Customization round (rec 8 / HA1): per-habit "why this matters" note. `@EncodeDefault(NEVER)`. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val motivation: String? = null,
    /** Journal-as-habit round: the per-habit ordered question set, texts only. Optional-with-default;
     *  `@EncodeDefault(NEVER)` so an EMPTY list is ABSENT — a user with no Journal habits sees a
     *  byte-identical definitionsHash (mirrors the old, now-removed `Definitions.journalQuestions`
     *  / `streakLongest`). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val journalQuestions: List<String> = emptyList()
)

@Serializable
data class IntakeReminderDef(
    val id: String,
    val name: String,
    /** FOOD / MED / CUSTOM / JOURNAL. */
    val type: String,
    val iconKey: String,
    val colorTag: String,
    val times: List<String> = emptyList(),
    val activeDays: List<Int> = emptyList(),
    val snoozeMinutes: Int = 10,
    val createdAt: String,
    val archived: Boolean = false,
    /** v0.5.2: the saved category name for a CUSTOM/JOURNAL reminder. Optional-with-default. */
    val customCategory: String? = null,
    /** v0.5.3: the per-reminder custom prompt message. Optional-with-default. */
    val promptMessage: String? = null,
    /** v0.5.4: FOOD reminder's default trigger flag ("MAYBE"/"RED"); null = none. Optional. */
    val defaultRedFlag: String? = null,
    /** v0.5.4: FOOD reminder's default suspected trigger food. Optional-with-default. */
    val defaultSuspectedFood: String? = null,
    /** v0.5.2 build 8: FOOD reminder's default "outside food" marker; null/false = no. Optional. */
    val defaultOutsideFood: Boolean? = null,
    /** Customization round (SD-6): per-intake "why this matters" note. `@EncodeDefault(NEVER)` so a
     *  null default is ABSENT in the canonical bytes. The pre-existing [promptMessage] field is
     *  deliberately left as-is (it has shipped since v0.5.3; "fixing" it now would churn). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val motivation: String? = null
)

@Serializable
data class DayEntry(
    /** Local calendar date, "yyyy-MM-dd". */
    val date: String,
    /** Sorted by `scheduledTime`. */
    val habitLogs: List<HabitLog> = emptyList(),
    /** Sorted by `scheduledTime`. */
    val intakeLogs: List<IntakeLog> = emptyList()
)

@Serializable
data class HabitLog(
    val habitId: String,
    /** "HH:mm" local. */
    val scheduledTime: String,
    /** done / skipped / missed. */
    val status: String,
    /** ISO-8601 UTC; null when missed. */
    val resolvedAt: String? = null,
    /** Journal-as-habit round: the ordered [{"q":…,"a":…}] snapshot for a JOURNAL habit's answered
     *  day; null for INDIVIDUAL/BATCH/STREAK. Optional-with-default — its default is null, and with
     *  `explicitNulls = false` a null field is already omitted from the canonical bytes without
     *  `@EncodeDefault`, following `IntakeLog.qaJson`'s exact precedent (NOT `HabitDef.streakLongest`'s
     *  — that annotation is only for a non-null default). */
    val qaJson: String? = null
)

@Serializable
data class IntakeLog(
    val reminderId: String,
    /** "HH:mm" local. */
    val scheduledTime: String,
    /** logged / skipped / missed. */
    val status: String,
    /** The free-text food/med answer; null when there wasn't one. */
    val answer: String? = null,
    /** ISO-8601 UTC; null when missed. */
    val resolvedAt: String? = null,
    /** v0.5.2: the JOURNAL long-form entry; null for FOOD/MED/CUSTOM. Optional-with-default. */
    val description: String? = null,
    /** v0.5.4: per-log Crohn's trigger flag ("MAYBE"/"RED"); null = unflagged. Optional. */
    val redFlag: String? = null,
    /** v0.5.4: per-log suspected trigger food, free text; null = none. Optional-with-default. */
    val suspectedFood: String? = null,
    /** v0.5.2 build 8: per-log "outside food" marker; null/false = no. Optional-with-default. */
    val outsideFood: Boolean? = null,
    /** v0.5.4 Journal v2 (D3): the ordered `[{"q":…,"a":…}]` snapshot; null for FOOD/MED/CUSTOM and
     *  pre-v0.5.4. Optional-with-default so `explicitNulls=false` emits it as ABSENT — a non-journal
     *  month's `contentHash` is byte-identical before/after this build. */
    val qaJson: String? = null
)

/** Status vocabulary, kept out of the wire model so both sides agree on the exact strings. */
object BackupStatus {
    const val DONE = "done"
    const val LOGGED = "logged"
    const val SKIPPED = "skipped"
    const val MISSED = "missed"
}
