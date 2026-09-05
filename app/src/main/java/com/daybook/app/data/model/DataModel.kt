package com.daybook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.daybook.app.ui.theme.AccentColor
import com.daybook.app.ui.theme.FontChoice
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description", defaultValue = "") val description: String = "",
    @ColumnInfo(name = "color_tag") val colorTag: ColorTag = ColorTag.AUTO,
    // v0.5.1 §J: aligned to the form default ("task"). This is a Kotlin-side default only — the
    // column carries no schema `defaultValue`, so nothing about Room's identity hash changes.
    @ColumnInfo(name = "icon_key") val iconKey: String = "task",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "times_json") val timesJson: String = "",
    @ColumnInfo(name = "active_days_json") val activeDaysJson: String = "",
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "snooze_interval_minutes") val snoozeIntervalMinutes: Int = 10,
    @ColumnInfo(name = "notification_id") val notificationId: Int = 0,
    // v0.5.2: INDIVIDUAL (today's behaviour — own reminder per time) vs BATCH (surfaced by the
    // single app-wide check-in notification). At the END so no positional call site breaks.
    @ColumnInfo(name = "type", defaultValue = "INDIVIDUAL") val type: HabitType = HabitType.INDIVIDUAL,
    // v0.5.5: "Ongoing" habit (enum value STREAK) — a passive running day-count. Epoch millis the
    // count started, or null when not started. Nullable, no schema `defaultValue` (mirrors
    // `custom_category` / `local_date` / `qa_json`). Appended so no positional call site breaks.
    @ColumnInfo(name = "streak_started_at") val streakStartedAt: Long? = null,
    // v0.5.5: longest run remembered across "Mark as broken". `defaultValue = "0"` matches
    // MIGRATION_14_15's `DEFAULT 0` (mirrors `notif_permission_asked` / `type`).
    @ColumnInfo(name = "streak_longest", defaultValue = "0") val streakLongest: Int = 0,
    // Customization round (rec 8): per-habit custom notification text shown instead of the default
    // "Time to complete this habit". Nullable, NO schema default (mirrors `custom_category`). Synced
    // via HabitDef.promptMessage with @EncodeDefault(NEVER).
    @ColumnInfo(name = "prompt_message") val promptMessage: String? = null,
    // Customization round (rec 8 / HA1): per-habit "why this matters" note. Nullable, no schema
    // default. Synced via HabitDef.motivation with @EncodeDefault(NEVER).
    @ColumnInfo(name = "motivation") val motivation: String? = null,
    // Journal-as-habit round (DB v17): per-habit ordered question list, JSON array of strings.
    // Empty-string default (not nullable) mirrors `timesJson`/`activeDaysJson` — "no questions
    // configured yet" for a non-Journal habit is indistinguishable from "". Appended so no
    // positional call site breaks. Synced via HabitDef.journalQuestions with @EncodeDefault(NEVER).
    @ColumnInfo(name = "journal_questions_json", defaultValue = "") val journalQuestionsJson: String = ""
)

@Serializable
@Entity(tableName = "habit_occurrences",
    // v0.5.3 Phase 2 (A1): `scheduled_for` is not a leading column of the unique index, so the
    // Home day query, the 800-day streak windows, `deleteInRange` and batch check-in all fell to a
    // full scan + temp B-tree. A leading `scheduled_for` index + a `(status, scheduled_for)`
    // composite for the observeNextPending* queries fix that. Unique `(habit_id, scheduled_for)` kept.
    indices = [
        Index(value = ["habit_id", "scheduled_for"], unique = true),
        Index(value = ["scheduled_for"]),
        Index(value = ["status", "scheduled_for"])
    ])
data class HabitOccurrence(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "scheduled_for") val scheduledFor: Long,
    @ColumnInfo(name = "status") val status: Occurrence.Status = Occurrence.Status.PENDING,
    @ColumnInfo(name = "snooze_count") val snoozeCount: Int = 0,
    @ColumnInfo(name = "responded_at") val respondedAt: Long? = null,
    @ColumnInfo(name = "notification_id") val notificationId: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    // v0.5.3 Phase 2 (S17): the local calendar date ("yyyy-MM-dd") this slot belongs to, written at
    // insert time so a later device-zone change cannot re-bucket history. Kotlin default null;
    // column added by MIGRATION_12_13 with no schema DEFAULT (mirrors the `custom_category` precedent).
    @ColumnInfo(name = "local_date") val localDate: String? = null,
    // Journal-as-habit round (DB v17): the ordered [{"q":…,"a":…}] snapshot for a JOURNAL habit's
    // answered day. Null for INDIVIDUAL/BATCH/STREAK and for pre-this-round rows. Nullable, no
    // schema default, mirrors [FoodMedOccurrence.qaJson] exactly. Appended so no positional call
    // site breaks.
    @ColumnInfo(name = "qa_json") val qaJson: String? = null
)

@Serializable
@Entity(
    tableName = "habit_events",
    // v0.5.2 D6: the detail-screen activity query and `hasShownEvent` (once per reminder per
    // syncAll) both filter on occurrence_id — unindexed it is a full table scan + sort every time.
    // v0.5.3 Phase 2 (A4): a denormalised `item_id` (the habit id) + `(item_id, timestamp)` index
    // makes the Detail "Activity" query a straight indexed LIMIT with no join to habit_occurrences.
    indices = [
        Index(value = ["occurrence_id"]),
        Index(value = ["item_id", "timestamp"])
    ]
)
data class HabitEvent(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "action") val action: Event.Action,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    // v0.5.3 Phase 2 (A4): the owning habit id, denormalised off the occurrence. Kotlin default
    // null; column added by MIGRATION_12_13 with no schema DEFAULT. Orphaned (evicted-occurrence)
    // rows keep null — they were already invisible to every query.
    @ColumnInfo(name = "item_id") val itemId: String? = null
)

@Serializable
@Entity(tableName = "food_med_tasks")
data class FoodMedTask(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "type") val type: TaskType = TaskType.FOOD,
    @ColumnInfo(name = "color_tag") val colorTag: ColorTag = ColorTag.AUTO,
    @ColumnInfo(name = "icon_key") val iconKey: String = "restaurant",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "times_json") val timesJson: String = "",
    @ColumnInfo(name = "active_days_json") val activeDaysJson: String = "",
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "snooze_interval_minutes") val snoozeIntervalMinutes: Int = 10,
    @ColumnInfo(name = "notification_id") val notificationId: Int = 0,
    /** v0.5.2: the saved category name for a CUSTOM/JOURNAL task. Nullable, no schema default. */
    @ColumnInfo(name = "custom_category") val customCategory: String? = null,
    /** v0.5.3: the per-reminder inline/notification prompt shown instead of the default
     *  "What did you have?". Nullable, no schema default (mirrors custom_category). */
    @ColumnInfo(name = "prompt_message") val promptMessage: String? = null,
    /** v0.5.4 (Crohn's food diary): default trigger marker pre-filled every time this FOOD
     *  reminder is logged. Null / NONE = unflagged. Only meaningful when [type] == FOOD. */
    @ColumnInfo(name = "default_red_flag") val defaultRedFlag: RedFlag? = null,
    /** v0.5.4: default "suspected trigger food" text pre-filled on the log screen for this
     *  FOOD reminder. Nullable, no schema default. */
    @ColumnInfo(name = "default_suspected_food") val defaultSuspectedFood: String? = null,
    /** v0.5.2 build 8 (outside food): default "eaten out / not home-prepared" marker pre-filled
     *  every time this FOOD reminder is logged. Null / false = not outside food. FOOD [type] only. */
    @ColumnInfo(name = "default_outside_food") val defaultOutsideFood: Boolean? = null,
    /** Customization round (SD-6): per-intake "why this matters" note, mirroring the habit side.
     *  Nullable, no schema default. Synced via IntakeReminderDef.motivation with @EncodeDefault(NEVER). */
    @ColumnInfo(name = "motivation") val motivation: String? = null
)

@Serializable
@Entity(tableName = "food_med_occurrences",
    // v0.5.3 Phase 2 (A1) — see [HabitOccurrence]. Unique `(task_id, scheduled_for)` kept.
    indices = [
        Index(value = ["task_id", "scheduled_for"], unique = true),
        Index(value = ["scheduled_for"]),
        Index(value = ["status", "scheduled_for"])
    ])
data class FoodMedOccurrence(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "scheduled_for") val scheduledFor: Long,
    @ColumnInfo(name = "status") val status: Occurrence.Status = Occurrence.Status.PENDING,
    @ColumnInfo(name = "snooze_count") val snoozeCount: Int = 0,
    @ColumnInfo(name = "response_text") val responseText: String = "",
    @ColumnInfo(name = "responded_at") val respondedAt: Long? = null,
    @ColumnInfo(name = "notification_id") val notificationId: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** v0.5.2: the JOURNAL long-form answer. Nullable, no schema default. */
    @ColumnInfo(name = "description") val description: String? = null,
    /** v0.5.4 (Crohn's food diary): per-log trigger marker for this FOOD entry. Null / NONE =
     *  not flagged. Set each time the reminder is answered; independent per day. */
    @ColumnInfo(name = "red_flag") val redFlag: RedFlag? = null,
    /** v0.5.4: free-text suspected trigger food recorded with this specific log entry. */
    @ColumnInfo(name = "suspected_food") val suspectedFood: String? = null,
    /** v0.5.2 build 8: per-log "outside food" marker for this FOOD entry. Null / false = not
     *  outside food. Set each time the reminder is answered; independent per day. */
    @ColumnInfo(name = "outside_food") val outsideFood: Boolean? = null,
    // v0.5.3 Phase 2 (S17): local calendar date ("yyyy-MM-dd") — see [HabitOccurrence.localDate].
    // Appended so no positional call site breaks.
    @ColumnInfo(name = "local_date") val localDate: String? = null,
    /** v0.5.4 Phase 2 (D3): the ordered [{"q":…,"a":…}] snapshot for a JOURNAL entry — the questions
     *  AS ASKED at entry time plus their answers. Null for FOOD/MED/CUSTOM and for pre-v0.5.4 rows.
     *  Appended after `local_date` so no positional call site breaks. */
    @ColumnInfo(name = "qa_json") val qaJson: String? = null
)

@Serializable
@Entity(
    tableName = "food_med_events",
    // v0.5.2 D6 + v0.5.3 Phase 2 (A4) — see HabitEvent.
    indices = [
        Index(value = ["occurrence_id"]),
        Index(value = ["item_id", "timestamp"])
    ]
)
data class FoodMedEvent(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "action") val action: Event.Action,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    // v0.5.3 Phase 2 (A4): the owning task id, denormalised off the occurrence — see [HabitEvent.itemId].
    @ColumnInfo(name = "item_id") val itemId: String? = null
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = 1,
    @ColumnInfo(name = "default_snooze_minutes") val defaultSnoozeMinutes: Int = 10,
    // v0.5.2 D2: `backup_reminder_enabled` (never written, never read since v0.5.1 §M) and
    // `last_backup_export_at` (write-only, its consumer removed in §M) are dropped by
    // MIGRATION_11_12 — the "next time the schema is bumped for another reason" this comment used
    // to wait for. `default_snooze_minutes` is KEPT: still read at OccurrenceScheduler:311.
    @ColumnInfo(name = "onboarding_completed") val onboardingCompleted: Boolean = false,
    @ColumnInfo(name = "user_name", defaultValue = "") val userName: String = "",
    // Single typed representation of the app accent (was a bare String alongside a dead
    // `color_tag` enum column — REV-34). Room converts via Converters; kotlinx serializes by name.
    @ColumnInfo(name = "accent_color", defaultValue = "LAVENDER") val accentColor: AccentColor = AccentColor.DEFAULT,
    @ColumnInfo(name = "notif_permission_asked", defaultValue = "0") val notifPermissionAsked: Boolean = false,
    /** Absolute path of the copied-in profile photo under filesDir, or null when none is set (L5). */
    @ColumnInfo(name = "profile_photo_path") val profilePhotoPath: String? = null,
    /** App-wide typeface key — see [com.daybook.app.ui.theme.FontChoice]. Plain String, no converter. */
    @ColumnInfo(name = "font_choice", defaultValue = "LITERATA") val fontChoice: String = FontChoice.DEFAULT.storageKey,
    /** v0.5.2: app-wide BATCH habit check-in time, "HH:mm". Device-local — NOT synced, NOT backed up. */
    @ColumnInfo(name = "habit_checkin_time", defaultValue = "21:00") val habitCheckinTime: String = "21:00",

    // ---------------------------------------------------------------------------------------------
    // Customization / Personalization round (v0.5.5, DB v16). Every column below is DEVICE-LOCAL:
    // NOT synced, NOT in BackupModel — following the `habit_checkin_time` precedent. Appended,
    // never reordered. Each @ColumnInfo(defaultValue=…) byte-matches MIGRATION_15_16's SQL DEFAULT.
    // ---------------------------------------------------------------------------------------------
    // rec 1 — week start ("MONDAY"/"SUNDAY"/"SATURDAY"), 24h clock, default calendar view expanded.
    @ColumnInfo(name = "week_start", defaultValue = "MONDAY") val weekStart: String = "MONDAY",
    @ColumnInfo(name = "clock_24h", defaultValue = "0") val clock24h: Boolean = false,
    @ColumnInfo(name = "calendar_default_expanded", defaultValue = "0") val calendarDefaultExpanded: Boolean = false,
    // rec 2 — greeting tone ("WARM"/"PLAIN"/"MINIMAL"), time-of-day word, hero phrasing
    // ("COUNT_LEFT"/"COUNT_TO_GO"/"COUNT_TASKS"/"HIDDEN").
    @ColumnInfo(name = "greeting_tone", defaultValue = "WARM") val greetingTone: String = "WARM",
    @ColumnInfo(name = "greeting_time_word", defaultValue = "1") val greetingTimeWord: Boolean = true,
    @ColumnInfo(name = "hero_style", defaultValue = "COUNT_LEFT") val heroStyle: String = "COUNT_LEFT",
    // rec 3 — remembered list sort ("ADDED"/"NAME"/"NEXT_REMINDER"), show-archived, hide-resolved.
    @ColumnInfo(name = "habit_sort", defaultValue = "ADDED") val habitSort: String = "ADDED",
    @ColumnInfo(name = "intake_sort", defaultValue = "ADDED") val intakeSort: String = "ADDED",
    @ColumnInfo(name = "habit_show_archived", defaultValue = "0") val habitShowArchived: Boolean = false,
    @ColumnInfo(name = "intake_show_archived", defaultValue = "0") val intakeShowArchived: Boolean = false,
    @ColumnInfo(name = "home_hide_resolved", defaultValue = "0") val homeHideResolved: Boolean = false,
    // rec 4 — reduce motion (OR-ed at read time with ANIMATOR_DURATION_SCALE == 0).
    @ColumnInfo(name = "reduce_motion", defaultValue = "0") val reduceMotion: Boolean = false,
    // rec 5 — quiet hours: enable + "HH:mm" start/end (may wrap midnight).
    @ColumnInfo(name = "quiet_hours_enabled", defaultValue = "0") val quietHoursEnabled: Boolean = false,
    @ColumnInfo(name = "quiet_start", defaultValue = "22:00") val quietStart: String = "22:00",
    @ColumnInfo(name = "quiet_end", defaultValue = "07:00") val quietEnd: String = "07:00",
    // rec 6 — streak mode ("STRICT"/"LENIENT"), show-streaks gate, rest days (CSV of DayOfWeek names).
    @ColumnInfo(name = "streak_mode", defaultValue = "STRICT") val streakMode: String = "STRICT",
    @ColumnInfo(name = "show_streaks", defaultValue = "1") val showStreaks: Boolean = true,
    @ColumnInfo(name = "streak_rest_days", defaultValue = "") val streakRestDays: String = "",
    // rec 7 — default landing tab (route id) + ordered CSV of visible bottom-nav route ids.
    @ColumnInfo(name = "default_landing_tab", defaultValue = "home") val defaultLandingTab: String = "home",
    @ColumnInfo(name = "nav_tabs", defaultValue = "home,routines,foodmed") val navTabs: String = "home,routines,foodmed",

    // ---------------------------------------------------------------------------------------------
    // Three-axis accent round (DB v18). Device-local, same treatment as `accent_color` above
    // (never synced, never in BackupModel) — these just split the single accent into 3 independent
    // knobs. Appended, never reordered.
    // ---------------------------------------------------------------------------------------------
    @ColumnInfo(name = "habits_accent_color", defaultValue = "LAVENDER") val habitsAccentColor: AccentColor = AccentColor.DEFAULT,
    @ColumnInfo(name = "intake_accent_color", defaultValue = "LAVENDER") val intakeAccentColor: AccentColor = AccentColor.DEFAULT,

    // DB v19 — gates whether InAppUpdateChecker is called from MainActivity.onResume() at all.
    // Device-local. Auto-flipped to false the first time a tester explicitly cancels the App
    // Distribution "Enable testing features" sign-in prompt; flippable back on in Settings.
    @ColumnInfo(name = "check_for_updates_enabled", defaultValue = "1") val checkForUpdatesEnabled: Boolean = true
)

/**
 * Optional per-card pastel tint override. AUTO = auto-assign by list position.
 * Legacy rows (SIGNAL_TEAL etc.) fall through to AUTO via ColorTag.fromNameOrAuto().
 */
@Serializable enum class ColorTag { AUTO, LAVENDER, PEACH, MINT, BUTTER, SLATE_BLUE, ROSE;
    companion object {
        fun fromNameOrAuto(name: String?): ColorTag =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: AUTO
    }
}
// JOURNAL appended, NEVER reordered: Room stores enums by name, but FoodMedForm renders
// TaskType.entries in declaration order and backup code compares by name.
// Journal-as-habit round: JOURNAL is retired as an Intake concept — kept ONLY so an old backup's
// `TaskType.valueOf("JOURNAL")` still decodes losslessly; never reachable from the UI after this
// round (filtered out of every `TaskType.entries` render; a freshly-imported legacy JOURNAL task
// is remapped to CUSTOM by ExportImportRepository — see importAllData).
@Serializable enum class TaskType { FOOD, MED, CUSTOM, JOURNAL }

/**
 * v0.5.4 — Crohn's food-diary trigger marker on a FOOD intake. Stored by name (Room) so NEVER
 * reorder. `null` column and [NONE] both mean "not flagged".
 */
@Serializable enum class RedFlag {
    NONE, MAYBE, RED;
    companion object {
        /** Lenient parse for backup/sync import — unknown or blank -> null (unflagged). */
        fun fromNameOrNull(name: String?): RedFlag? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }?.takeIf { it != NONE }
    }
}
@Serializable enum class DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

/**
 * v0.5.2: INDIVIDUAL = a reminder per configured time; BATCH = one shared daily check-in.
 * v0.5.5: STREAK (user-facing label "Ongoing") = a passive running day-count habit — zero
 * occurrences, zero alarms, zero notifications, nothing on Today. STREAK is APPENDED and must
 * NEVER be reordered: Room stores enums by name and `HabitType.entries` renders in declaration
 * order.
 * Journal-as-habit round: JOURNAL = a scheduled, per-slot habit exactly like INDIVIDUAL (own
 * times/active-days/snooze), but resolving a slot opens a chat-style Q&A flow (an ordered,
 * per-habit configurable question list, `Habit.journalQuestionsJson`) instead of Complete/Skip.
 * Appended, NEVER reordered.
 */
@Serializable enum class HabitType { INDIVIDUAL, BATCH, STREAK, JOURNAL }

/**
 * v0.5.2: a saved, reusable CUSTOM/JOURNAL category name. PRIMARY KEY(name) is what makes the
 * category list self-deduplicating. Exported as Definitions.customCategories; in DATA_TABLES.
 */
@Serializable
@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * v0.5.3: a saved, reusable intake prompt message. PRIMARY KEY(name) self-deduplicates,
 * exactly like [CustomCategory]. Exported as Definitions.customPrompts; feeds DATA_TABLES.
 */
@Serializable
@Entity(tableName = "custom_prompts")
data class CustomPrompt(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

object Occurrence {
    // SNOOZED was never written — snoozing deliberately leaves the row PENDING so the alarm
    // path keeps re-nagging (REV-31). LOGGED is the "answered a food/med prompt" terminal state.
    @Serializable
    enum class Status { PENDING, COMPLETED, SKIPPED, LOGGED }
}

object Event {
    // AUTO_SNOOZED and LOGGED were never written (the re-nag path logs nothing; food/med answers
    // write REPLIED). Dropped in REV-31 — re-add AUTO_SNOOZED only with a per-fire dedupe if the
    // timeline should record ignored re-nags.
    @Serializable
    enum class Action { SHOWN, USER_SNOOZED, COMPLETED, SKIPPED, REPLIED }
}