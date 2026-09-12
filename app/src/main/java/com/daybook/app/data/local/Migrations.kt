package com.daybook.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3: additive columns on `app_settings` for the configurable user name and the
 * user-selectable accent colour. No data loss.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN user_name TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN accent_color TEXT NOT NULL DEFAULT 'MINT'")
    }
}

/**
 * v3 -> v4: additive column tracking whether the POST_NOTIFICATIONS runtime prompt has been
 * shown once, so a permanently-denied permission stops being re-asked silently (REV-18).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN notif_permission_asked INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 -> v5: drop the legacy, never-read `color_tag` column from `app_settings` (the app-wide
 * accent is now the single typed `accent_color` — REV-34). SQLite has no portable DROP COLUMN
 * on the versions Room bundles, so this is the standard create-copy-drop-rename rebuild. The
 * new table statement mirrors Room's generated schema for v5 exactly.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_settings_new` (" +
                "`id` INTEGER NOT NULL, " +
                "`default_snooze_minutes` INTEGER NOT NULL, " +
                "`backup_reminder_enabled` INTEGER NOT NULL, " +
                "`last_backup_export_at` INTEGER, " +
                "`onboarding_completed` INTEGER NOT NULL, " +
                "`user_name` TEXT NOT NULL DEFAULT '', " +
                "`accent_color` TEXT NOT NULL DEFAULT 'MINT', " +
                "`notif_permission_asked` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `app_settings_new` (" +
                "`id`, `default_snooze_minutes`, `backup_reminder_enabled`, `last_backup_export_at`, " +
                "`onboarding_completed`, `user_name`, `accent_color`, `notif_permission_asked`) " +
                "SELECT `id`, `default_snooze_minutes`, `backup_reminder_enabled`, `last_backup_export_at`, " +
                "`onboarding_completed`, `user_name`, `accent_color`, `notif_permission_asked` FROM `app_settings`"
        )
        db.execSQL("DROP TABLE `app_settings`")
        db.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")
    }
}
// NOTE: MIGRATION_12_13 (v0.5.3 Phase 2) is appended at the END of this file.

/**
 * v5 -> v6: additive nullable column holding the absolute path of the profile photo copied into
 * the app's filesDir (L5). No data loss.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN profile_photo_path TEXT")
    }
}

/**
 * v6 -> v7: additive column for the app-wide typeface choice (see
 * [com.daybook.app.ui.theme.FontChoice]). Stored as a plain String key, default 'GROTESK'
 * (the pre-0.3 look). No data loss.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN font_choice TEXT NOT NULL DEFAULT 'GROTESK'")
    }
}

/**
 * v7 -> v8 (v0.5.2). Five additive statements, no table rebuild, no data rewritten:
 *  - habits.type                     — INDIVIDUAL (today's behaviour) vs BATCH; existing rows
 *                                      get INDIVIDUAL, so nothing changes for them.
 *  - food_med_occurrences.description— the JOURNAL long-form answer. Nullable.
 *  - food_med_tasks.custom_category  — the saved category name for a CUSTOM/JOURNAL task. Nullable.
 *  - app_settings.habit_checkin_time — app-wide BATCH check-in time, "HH:mm", device-local.
 *  - custom_categories               — new table; PRIMARY KEY(name) is what makes the category
 *                                      list self-deduplicating.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN type TEXT NOT NULL DEFAULT 'INDIVIDUAL'")
        db.execSQL("ALTER TABLE food_med_occurrences ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE food_med_tasks ADD COLUMN custom_category TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN habit_checkin_time TEXT NOT NULL DEFAULT '21:00'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_categories` (" +
                "`name` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`name`))"
        )
    }
}

/**
 * v8 -> v9 (v0.5.3). Two additive statements, no table rebuild, no data rewritten:
 *  - food_med_tasks.prompt_message — the per-reminder custom prompt. Nullable.
 *  - custom_prompts               — new table; PRIMARY KEY(name) self-deduplicates the
 *                                   reusable prompt list, exactly like custom_categories.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_med_tasks ADD COLUMN prompt_message TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_prompts` (" +
                "`name` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`name`))"
        )
    }
}

/**
 * v9 -> v10 (v0.5.4 — Crohn's food-diary red-flag tracking). Four additive nullable columns,
 * no table rebuild, no existing row rewritten:
 *  - food_med_occurrences.red_flag        — per-log trigger marker (RedFlag name, or NULL).
 *  - food_med_occurrences.suspected_food  — per-log suspected trigger food, free text.
 *  - food_med_tasks.default_red_flag      — pre-fill for the above on a FOOD reminder.
 *  - food_med_tasks.default_suspected_food
 * Room stores the enum as its name in a TEXT column; NULL is a valid "unflagged" value, so no
 * DEFAULT is needed and no data is lost.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_med_occurrences ADD COLUMN red_flag TEXT")
        db.execSQL("ALTER TABLE food_med_occurrences ADD COLUMN suspected_food TEXT")
        db.execSQL("ALTER TABLE food_med_tasks ADD COLUMN default_red_flag TEXT")
        db.execSQL("ALTER TABLE food_med_tasks ADD COLUMN default_suspected_food TEXT")
    }
}

/**
 * v10 -> v11 (v0.5.2 build 8 — "outside food" marker). Two additive nullable columns, no table
 * rebuild, no existing row rewritten. NULL is a valid "not outside food" value, so no DEFAULT.
 *  - food_med_occurrences.outside_food        — per-log marker (0/1/NULL).
 *  - food_med_tasks.default_outside_food      — pre-fill for the above on a FOOD reminder.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_med_occurrences ADD COLUMN outside_food INTEGER")
        db.execSQL("ALTER TABLE food_med_tasks ADD COLUMN default_outside_food INTEGER")
    }
}

/**
 * v11 -> v12 (v0.5.2 perf/cleanup):
 *
 *  1. **D6 — index `occurrence_id` on both events tables.** Pure additive `CREATE INDEX`. The
 *     detail-screen activity query joins on `e.occurrence_id` and `hasShownEvent(occurrenceId)`
 *     runs once per reminder item on every `syncAll` (cold start, boot, WindowRefreshWorker) —
 *     unindexed each was a full table scan. The index names / statements match exactly what Room
 *     writes into `12.json` so `validateMigration` passes.
 *
 *  2. **§6.7 — drop two dead `app_settings` columns.** `backup_reminder_enabled` (never written,
 *     never read since v0.5.1 §M) and `last_backup_export_at` (write-only; its consumer was removed
 *     in §M). Standard SQLite create-copy-drop-rename on the single-row table — cheap, low risk.
 *     `default_snooze_minutes` is KEPT (read at OccurrenceScheduler:311). The new table statement
 *     mirrors Room's generated v12 schema for `app_settings` exactly.
 *
 * The `notification_id` columns on `habits` / `food_med_tasks` are vestigial too, but dropping
 * them needs a populated-table recreate that could not be device-verified here — deferred, data
 * safety over tidiness (see the plan's D2.3).
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. D6 indices.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_habit_events_occurrence_id` " +
                "ON `habit_events` (`occurrence_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_food_med_events_occurrence_id` " +
                "ON `food_med_events` (`occurrence_id`)"
        )

        // 2. Drop backup_reminder_enabled + last_backup_export_at from app_settings.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_settings_new` (" +
                "`id` INTEGER NOT NULL, " +
                "`default_snooze_minutes` INTEGER NOT NULL, " +
                "`onboarding_completed` INTEGER NOT NULL, " +
                "`user_name` TEXT NOT NULL DEFAULT '', " +
                "`accent_color` TEXT NOT NULL DEFAULT 'MINT', " +
                "`notif_permission_asked` INTEGER NOT NULL DEFAULT 0, " +
                "`profile_photo_path` TEXT, " +
                "`font_choice` TEXT NOT NULL DEFAULT 'GROTESK', " +
                "`habit_checkin_time` TEXT NOT NULL DEFAULT '21:00', " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `app_settings_new` (" +
                "`id`, `default_snooze_minutes`, `onboarding_completed`, `user_name`, " +
                "`accent_color`, `notif_permission_asked`, `profile_photo_path`, `font_choice`, " +
                "`habit_checkin_time`) " +
                "SELECT `id`, `default_snooze_minutes`, `onboarding_completed`, `user_name`, " +
                "`accent_color`, `notif_permission_asked`, `profile_photo_path`, `font_choice`, " +
                "`habit_checkin_time` FROM `app_settings`"
        )
        db.execSQL("DROP TABLE `app_settings`")
        db.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")
    }
}

/**
 * v12 -> v13 (v0.5.3 Phase 2). Additive only — no table rebuild, no row rewritten in place beyond
 * the two backfills below.
 *
 *  A1  — leading indices on scheduled_for (both occurrence tables) + a (status, scheduled_for)
 *        composite for the observeNextPendingMillis* queries. `scheduled_for` was never a leading
 *        index column, so the Home day query, the 800-day streak windows, `deleteInRange` and
 *        batch check-in all fell to `SCAN TABLE ... USE TEMP B-TREE`.
 *  S17 — local_date TEXT on both occurrence tables, backfilled from scheduled_for in the device's
 *        CURRENT zone (SQLite `localtime`). Historical rows therefore adopt whatever zone the phone
 *        is in at migration time — the best that can be done retroactively. New rows write
 *        local_date at insert time (OccurrenceScheduler / mapDaysToOccurrences), so the column is
 *        never NULL going forward.
 *  A4  — item_id (habit_id / task_id) denormalised onto both event tables + a (item_id, timestamp)
 *        index, so the Detail "Activity" query is a straight indexed LIMIT with no join. Orphaned
 *        events (owning occurrence already evicted) get item_id = NULL — they were already
 *        invisible to every query.
 *
 * Cost at scale (audit A11): the four `CREATE INDEX` builds and the `item_id` backfill subquery run
 * on the Room-open path. At 100x (~220k mostly-orphaned event rows) the `item_id` UPDATE is the
 * expensive statement — potentially 1-3 s on a cold slow device, ANR-adjacent on the first launch
 * after update. Unavoidable for an index migration; mitigated in practice because the two
 * occurrence tables are eviction-capped (~2 months resident) for signed-in users.
 *
 * v0.5.3 Phase 7 (audit A11 — closing note, informational): the 1-3 s worst case is the
 * "100x resident-uncapped" shape, which only a **signed-out** user can reach (no eviction runs
 * for them). Daybook gates all history behind sign-in, so that user has essentially no
 * occurrence history to migrate — the case is a non-population. Signed-in users are
 * eviction-capped to ~2 months resident, so their row counts are small. The real cold-start
 * cost after an update is the sync bootstrap, not this migration — addressed separately by
 * audit S1 (single parent-doc read with the month-hash summary).
 *
 * Index names/statements below MUST match Room's generated 13.json exactly (convention:
 * index_<table>_<colA>[_<colB>]).
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // A1 — indices
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_occurrences_scheduled_for` ON `habit_occurrences` (`scheduled_for`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_occurrences_status_scheduled_for` ON `habit_occurrences` (`status`, `scheduled_for`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_med_occurrences_scheduled_for` ON `food_med_occurrences` (`scheduled_for`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_med_occurrences_status_scheduled_for` ON `food_med_occurrences` (`status`, `scheduled_for`)")

        // S17 — local_date column + backfill (device's current zone, retroactive best effort)
        db.execSQL("ALTER TABLE `habit_occurrences` ADD COLUMN `local_date` TEXT")
        db.execSQL("ALTER TABLE `food_med_occurrences` ADD COLUMN `local_date` TEXT")
        db.execSQL("UPDATE `habit_occurrences` SET `local_date` = date(`scheduled_for`/1000, 'unixepoch', 'localtime')")
        db.execSQL("UPDATE `food_med_occurrences` SET `local_date` = date(`scheduled_for`/1000, 'unixepoch', 'localtime')")

        // A4 — item_id column + backfill from the owning occurrence + (item_id, timestamp) index
        db.execSQL("ALTER TABLE `habit_events` ADD COLUMN `item_id` TEXT")
        db.execSQL("ALTER TABLE `food_med_events` ADD COLUMN `item_id` TEXT")
        db.execSQL("UPDATE `habit_events` SET `item_id` = (SELECT `habit_id` FROM `habit_occurrences` WHERE `habit_occurrences`.`id` = `habit_events`.`occurrence_id`)")
        db.execSQL("UPDATE `food_med_events` SET `item_id` = (SELECT `task_id` FROM `food_med_occurrences` WHERE `food_med_occurrences`.`id` = `food_med_events`.`occurrence_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_events_item_id_timestamp` ON `habit_events` (`item_id`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_med_events_item_id_timestamp` ON `food_med_events` (`item_id`, `timestamp`)")
    }
}

/**
 * v13 -> v14 (v0.5.4 — Journal v2). Additive schema + one destructive data wipe scoped to JOURNAL
 * occurrences only (D5):
 *
 *  D1  — journal_questions: the global, ordered, user-configurable journal question set. Rides in
 *        Definitions (sync + backup) exactly like custom_prompts. PRIMARY KEY(id); `position` is
 *        the ordering. Seeded with one default row so the set is never empty on an upgrade (D6).
 *  D3  — food_med_occurrences.qa_json: the per-entry [{"q":…,"a":…}] snapshot. Nullable, no schema
 *        default (mirrors `description` / `local_date`).
 *  D5  — every JOURNAL-task occurrence row (and its events) is deleted. The scheduler regenerates
 *        PENDING slots for the rolling window on the next syncAll(). Non-JOURNAL (FOOD/MED/CUSTOM)
 *        occurrence and event rows are untouched. On the next signed-in push the affected month
 *        docs are rewritten with the journal logs removed.
 *
 * The table carries only its PK — no CREATE INDEX here, and `"indices": []` in 14.json.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // D1 — question set
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_questions` (" +
                "`id` TEXT NOT NULL, `text` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        // D6 — seed one default so the set is never empty for an upgrader. A fresh install seeds
        // via JournalQuestionRepository.ensureSeeded() on first observe (Room creates the table
        // empty). The '' escaping is SQLite's — "What's on your mind?".
        db.execSQL(
            "INSERT INTO `journal_questions` (`id`,`text`,`position`) " +
                "VALUES ('seed-default-0','What''s on your mind?',0)"
        )

        // D3 — per-entry Q&A snapshot
        db.execSQL("ALTER TABLE `food_med_occurrences` ADD COLUMN `qa_json` TEXT")

        // D5 — discard existing journal entry data (JOURNAL-task occurrences only). FOOD/MED/CUSTOM
        // occurrence + event rows are NOT in either subquery.
        db.execSQL(
            "DELETE FROM `food_med_events` WHERE `occurrence_id` IN (" +
                "SELECT `id` FROM `food_med_occurrences` WHERE `task_id` IN (" +
                "SELECT `id` FROM `food_med_tasks` WHERE `type` = 'JOURNAL'))"
        )
        db.execSQL(
            "DELETE FROM `food_med_occurrences` WHERE `task_id` IN (" +
                "SELECT `id` FROM `food_med_tasks` WHERE `type` = 'JOURNAL')"
        )
    }
}

/**
 * v14 -> v15 (v0.5.5 — "Ongoing" habit type). Two additive statements on `habits`, no table
 * rebuild, no data rewritten:
 *  - habits.streak_started_at — epoch millis the running count started, or NULL when not started.
 *                               Nullable, no DEFAULT (mirrors `local_date` / `qa_json`).
 *  - habits.streak_longest    — longest run remembered across "Mark as broken". NOT NULL
 *                               DEFAULT 0; existing rows read 0, matching the Kotlin default.
 * Existing habits are unaffected: they stay INDIVIDUAL/BATCH and never read either column.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN streak_started_at INTEGER")
        db.execSQL("ALTER TABLE habits ADD COLUMN streak_longest INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v15 -> v16 (Customization / Personalization round). One additive migration.
 *
 *  - 20 new `app_settings` columns, every one `NOT NULL` with a `DEFAULT` that byte-matches the
 *    `@ColumnInfo(defaultValue = …)` Kotlin default. All device-local — NOT synced, NOT in
 *    `BackupModel` (follows the `habit_checkin_time` precedent). Existing rows read the defaults,
 *    so every customization feature is a no-op until the user changes it.
 *  - `habits.prompt_message` / `habits.motivation` / `food_med_tasks.motivation` — nullable, NO
 *    schema DEFAULT (mirrors `custom_category` / `qa_json`). Synced (rec 8) with
 *    `@EncodeDefault(NEVER)` on the wire model so a user who sets none sees a byte-identical
 *    `definitionsHash`.
 *
 * `default_landing_tab` is a route-id `TEXT` ('home'), not an integer index — an index into the
 * *visible* tab list is meaningless once a tab is hidden (see the plan's P8 amendment).
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // rec 1 — week start / clock format / default calendar view
        db.execSQL("ALTER TABLE app_settings ADD COLUMN week_start TEXT NOT NULL DEFAULT 'MONDAY'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN clock_24h INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN calendar_default_expanded INTEGER NOT NULL DEFAULT 0")
        // rec 2 — greeting tone / time-of-day word / hero phrasing
        db.execSQL("ALTER TABLE app_settings ADD COLUMN greeting_tone TEXT NOT NULL DEFAULT 'WARM'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN greeting_time_word INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN hero_style TEXT NOT NULL DEFAULT 'COUNT_LEFT'")
        // rec 3 — persisted list sort / show-archived / hide-resolved
        db.execSQL("ALTER TABLE app_settings ADD COLUMN habit_sort TEXT NOT NULL DEFAULT 'ADDED'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN intake_sort TEXT NOT NULL DEFAULT 'ADDED'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN habit_show_archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN intake_show_archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN home_hide_resolved INTEGER NOT NULL DEFAULT 0")
        // rec 4 — reduce motion
        db.execSQL("ALTER TABLE app_settings ADD COLUMN reduce_motion INTEGER NOT NULL DEFAULT 0")
        // rec 5 — quiet hours
        db.execSQL("ALTER TABLE app_settings ADD COLUMN quiet_hours_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN quiet_start TEXT NOT NULL DEFAULT '22:00'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN quiet_end TEXT NOT NULL DEFAULT '07:00'")
        // rec 6 — streak mode / rest days / hide streaks
        db.execSQL("ALTER TABLE app_settings ADD COLUMN streak_mode TEXT NOT NULL DEFAULT 'STRICT'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN show_streaks INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN streak_rest_days TEXT NOT NULL DEFAULT ''")
        // rec 7 — default landing tab (route id) + ordered CSV of visible tabs
        db.execSQL("ALTER TABLE app_settings ADD COLUMN default_landing_tab TEXT NOT NULL DEFAULT 'home'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN nav_tabs TEXT NOT NULL DEFAULT 'home,routines,foodmed'")
        // rec 8 — synced per-habit / per-intake text. Nullable, NO schema default (mirrors custom_category).
        db.execSQL("ALTER TABLE habits ADD COLUMN prompt_message TEXT")
        db.execSQL("ALTER TABLE habits ADD COLUMN motivation TEXT")
        db.execSQL("ALTER TABLE food_med_tasks ADD COLUMN motivation TEXT")
    }
}

/**
 * v16 -> v17 (Journal-as-habit round). Journal moves from an Intake (`TaskType.JOURNAL`) concept to
 * a 4th `HabitType.JOURNAL`, with its own per-habit question list. This is a "fresh start" for
 * Journal data — the user's explicit decision — so existing Intake-Journal reminders/entries and the
 * global `journal_questions` table are wiped outright, not migrated forward.
 *
 *  (a) Additive columns:
 *      - habits.journal_questions_json — per-habit ordered question list, JSON-ish CSV-free string
 *        (see `journalQuestionsToJson`). NOT NULL DEFAULT '' (mirrors `times_json`/`active_days_json`
 *        — "no questions configured yet" is indistinguishable from "" for a non-Journal habit).
 *      - habit_occurrences.qa_json — the ordered [{"q":…,"a":…}] snapshot for an answered Journal
 *        habit slot. Nullable, no schema default (mirrors `food_med_occurrences.qa_json`).
 *
 *  (b) Destructive delete, scoped to `food_med_tasks.type = 'JOURNAL'` ONLY — FOOD/MED/CUSTOM rows
 *      are untouched. Neither `food_med_events` nor `food_med_occurrences` declares a `@ForeignKey`
 *      / cascade (confirmed by reading `DataModel.kt` — both are plain `@Entity` with only
 *      performance `@Index`), so children are deleted BEFORE parents, in this exact order:
 *      events -> occurrences -> tasks. Reversing the order (or trusting a cascade that doesn't
 *      exist) would silently orphan rows with no task, invisible everywhere in the UI.
 *
 *  (c) Drop the retired global `journal_questions` table outright (decision: no migration-forward
 *      of its content — a per-habit list replaces it, seeded fresh per Journal habit by the form).
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // (a) additive columns
        db.execSQL("ALTER TABLE habits ADD COLUMN journal_questions_json TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE habit_occurrences ADD COLUMN qa_json TEXT")

        // (b) discard ALL existing Intake-Journal data (user's explicit "fresh start" decision).
        // Children first — events, then occurrences, then tasks (no FK/cascade to rely on).
        db.execSQL(
            "DELETE FROM food_med_events WHERE occurrence_id IN (" +
                "SELECT id FROM food_med_occurrences WHERE task_id IN (" +
                "SELECT id FROM food_med_tasks WHERE type = 'JOURNAL'))"
        )
        db.execSQL(
            "DELETE FROM food_med_occurrences WHERE task_id IN (" +
                "SELECT id FROM food_med_tasks WHERE type = 'JOURNAL')"
        )
        db.execSQL("DELETE FROM food_med_tasks WHERE type = 'JOURNAL'")

        // (c) drop the retired global question table.
        db.execSQL("DROP TABLE IF EXISTS journal_questions")
    }
}

/**
 * v17 -> v18: split the single `accent_color` into 3 independent, device-local accent axes —
 * App (unchanged column), Habits, Intake. Additive only.
 *
 * The follow-up UPDATE preserves upgrade behaviour byte-for-byte: an existing user's Habits/
 * Intake sections start at whatever single accent they'd already chosen, not a hardcoded
 * lavender — only a genuinely fresh install (which never runs this migration) gets the new
 * LAVENDER default for all 3 (see AppSettings() constructor defaults).
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN habits_accent_color TEXT NOT NULL DEFAULT 'LAVENDER'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN intake_accent_color TEXT NOT NULL DEFAULT 'LAVENDER'")
        db.execSQL("UPDATE app_settings SET habits_accent_color = accent_color, intake_accent_color = accent_color")
    }
}

/**
 * `check_for_updates_enabled` — gates whether [com.daybook.app.util.update.InAppUpdateChecker]
 * is even called from `MainActivity.onResume()`. Device-local, additive, default true (matches
 * every existing install's current de-facto behaviour — the check already ran unconditionally
 * before this column existed).
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN check_for_updates_enabled INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v19 -> v20 (UX overhaul item 4 — light / dark theme). ONE additive column:
 * `theme_mode` ("DARK" / "LIGHT" / "SYSTEM"). `NOT NULL DEFAULT 'DARK'` byte-matches
 * `@ColumnInfo(defaultValue = "DARK")` so every existing row reads DARK and nothing changes
 * until the user opts in. No table rebuild, no row rewrite. Device-local — NOT in `BackupModel`,
 * NOT in any export, NOT in `ContentHash` / the sync loop — same as every `app_settings` column
 * since v16. This is the entire permitted schema surface for the overhaul.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN theme_mode TEXT NOT NULL DEFAULT 'DARK'")
    }
}

/**
 * v20 -> v21 (UX refinement round). THREE additive columns on app_settings, no table
 * rebuild, no row rewrite. All device-local — NOT in BackupModel, NOT in any export, NOT
 * in ContentHash / the sync loop — same as every app_settings column since v16.
 *   dark_style   — DarkStyle.storageKey  ("CHARCOAL" default == today's dark palette)
 *   light_style  — LightStyle.storageKey ("PAPER"    default == today's light palette)
 *   corner_scale — global corner-radius multiplier, REAL, 1.0 == today's radii
 * Each DEFAULT byte-matches the @ColumnInfo(defaultValue=…) Kotlin default, so every
 * existing row reads the current look and nothing changes until the user opts in.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN dark_style TEXT NOT NULL DEFAULT 'CHARCOAL'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN light_style TEXT NOT NULL DEFAULT 'PAPER'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN corner_scale REAL NOT NULL DEFAULT 1.0")
    }
}

/**
 * A1 (R18, C2, C8): v21 -> v22 — Round A ("Beast Mode" / workout mode). 100% additive: six new
 * tables (no existing table rebuilt, no row rewritten, no data lost) plus five new device-local
 * `app_settings` columns. Every DEFAULT below byte-matches the corresponding
 * `@ColumnInfo(defaultValue = …)` in WorkoutModel.kt / DataModel.kt — a mismatch fails Room's
 * identity-hash check at open (HEALTH_AND_WORKOUT_PLAN.md §3.5).
 *
 * Every `workout_routine_exercises` target column and every `workout_sets` value column is
 * NULLABLE with NO `NOT NULL DEFAULT` (Ri3 / R18): a non-null default would destroy the
 * distinction between "no target"/"not tracked" and "a target of zero", corrupting blank-is-
 * not-zero volume/PR logic. `MigrationTest.migrate21To22` asserts none of them is NOT NULL.
 *
 * No foreign keys (§3.2): child rows get repository-level cleanup inside `withTransaction`
 * instead, so the import path can insert children before parents in either order.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercises` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`primary_muscle` TEXT NOT NULL, `equipment` TEXT NOT NULL, " +
                "`tracking_mode` TEXT NOT NULL, `is_archived` INTEGER NOT NULL DEFAULT 0, " +
                "`source` TEXT NOT NULL DEFAULT 'USER', " +
                "`created_at` INTEGER NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
                "`id` TEXT NOT NULL, `local_date` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                "`ended_at` INTEGER, `title` TEXT, `notes` TEXT, " +
                "`status` TEXT NOT NULL DEFAULT 'ACTIVE', `source` TEXT NOT NULL DEFAULT 'MANUAL', " +
                "`routine_id` TEXT, " + // NULLABLE, no FK — a session must never be orphaned by a deleted routine.
                "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_local_date` ON `workout_sessions`(`local_date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_started_at` ON `workout_sessions`(`started_at`)")

        // Routines are DEFINITIONS, like habits and custom exercises — no local_date, never
        // month-partitioned, never evicted (§4.4 item 6).
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_routines` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `notes` TEXT, " +
                "`order_index` INTEGER NOT NULL, " +
                "`is_archived` INTEGER NOT NULL DEFAULT 0, " +
                "`source` TEXT NOT NULL DEFAULT 'USER', " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_routines_order_index` ON `workout_routines`(`order_index`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_routine_exercises` (" +
                "`id` TEXT NOT NULL, `routine_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, " +
                "`order_index` INTEGER NOT NULL, " +
                "`target_sets` INTEGER, `target_reps` INTEGER, `target_weight_kg` REAL, " +
                "`target_duration_seconds` INTEGER, `target_distance_meters` REAL, " +
                "`rest_seconds` INTEGER, `notes` TEXT, " +
                "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_routine_exercises_routine_id_order_index` " +
                "ON `workout_routine_exercises`(`routine_id`,`order_index`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_routine_exercises_exercise_id` " +
                "ON `workout_routine_exercises`(`exercise_id`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_exercises` (" +
                "`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, " +
                "`order_index` INTEGER NOT NULL, `notes` TEXT, `superset_id` TEXT, `rest_seconds` INTEGER, " +
                "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_exercises_session_id_order_index` " +
                "ON `workout_exercises`(`session_id`,`order_index`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercises_exercise_id` ON `workout_exercises`(`exercise_id`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_sets` (" +
                "`id` TEXT NOT NULL, `workout_exercise_id` TEXT NOT NULL, " +
                "`session_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, " +
                "`set_number` INTEGER NOT NULL, " +
                "`reps` INTEGER, `weight_kg` REAL, `duration_seconds` INTEGER, `distance_meters` REAL, " +
                "`rpe` INTEGER, " +
                "`set_type` TEXT NOT NULL DEFAULT 'NORMAL', " +
                "`notes` TEXT, `completed_at` INTEGER, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sets_workout_exercise_id_set_number` " +
                "ON `workout_sets`(`workout_exercise_id`,`set_number`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_session_id` ON `workout_sets`(`session_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_exercise_id` ON `workout_sets`(`exercise_id`)")

        // Device-local settings, same treatment as every app_settings column since v16 — all four
        // surfaced on Beast Mode's OWN settings screen (§3.8.2), not Daybook's main settings_*
        // screens. `nav_tabs` is untouched: Workout is not a tab (§3.6).
        db.execSQL("ALTER TABLE app_settings ADD COLUMN weight_unit TEXT NOT NULL DEFAULT 'KG'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN workout_accent_color TEXT NOT NULL DEFAULT 'CORAL'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN rest_timer_default_seconds INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN workout_hint_state INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN workout_today_card_enabled INTEGER NOT NULL DEFAULT 1")
        // Feature addition (post-A6, same still-unshipped MIGRATION_21_22 — nothing has been
        // installed to a device yet) — the Add-Exercise picker's default active filter chip
        // (§ new "group Add Exercise by muscle group" feature). Stores a MuscleGroup enum name,
        // or NULL for "All" (no default group). Nullable, NO schema default (mirrors
        // `profile_photo_path`) — a target column, same Ri3-flavoured reasoning as the routine
        // target columns: "no preference set" must not collapse into a real MuscleGroup value.
        db.execSQL("ALTER TABLE app_settings ADD COLUMN default_exercise_group TEXT")
    }
}
