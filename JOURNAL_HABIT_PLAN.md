# Journal-as-Habit + Button Consistency + Ongoing-Habit UI — Implementation Plan

Repo: `/home/abhiram/Downloads/app-for-food`. Build env: `JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew <task>` (JDK 21 at `/home/abhiram/.jdks/jbr-21.0.11` also works).

**4-gate every phase**: `testDebugUnitTest`, `assembleDebug`, `assembleRelease` (R8 + `lintVitalRelease` clean, real keystore CN=Daybook SHA1 39e62d0f), `compileDebugAndroidTestKotlin`.

This plan covers three independent-but-co-shipped efforts:

- **Task A** — fix the cramped "text field + Add button" row pattern (and audit the app for the same pattern elsewhere).
- **Task B** — Journal moves from an Intake type to a 4th `HabitType`, with a real chat UI, per-item questions, and a separate plain edit-form.
- **Task C** — Ongoing ("Streak") habit card UI: on-card "Mark as broken", redesigned alignment, backdated "Start", and an explicit History/Stats button.

---

## §0 — Open sub-decisions (recommendations — one-pass approval)

### Version (CORRECTION — do not bump)
**versionCode stays 13, versionName stays `"0.5.5"`.** The user said "keep it in this build only, build number no change" — Task A + Task B + Task C all ship in the *current* 0.5.5 build, same pattern as the recent Customization round absorbed into one version. The **Room DB version still bumps normally** (v16 → v17) for the Task B schema changes — only the app-level versionCode/versionName freeze. Final APK: **`Daybook-v0.5.5-journal-habit-release.apk`** (no "0.5.6" anywhere — file name, changelog headers, or code comments introduced by this round). Note the same caveat the Customization round documented: a same-versionCode rebuild will not register as an "update" over a previously-installed build with the same versionCode on a real device (Play/PackageInstaller keys on versionCode) — reinstall (uninstall/install, or `adb install -r` from the same signing key) is required to pick it up on a device that already has this versionCode installed.

### Task B sub-decisions

**B1 — Chat mid-flow interruption / "back".**
Recommend: every answer is written into an in-progress `qa_json` draft **as soon as it is sent** (each "send" persists the full snapshot so far via a lightweight `HabitOccurrence.qa_json` update, status left `PENDING`), so backgrounding or killing the app mid-chat loses nothing — reopening the chat (same occurrence id) resumes at the first unanswered question, replaying already-sent bubbles from the persisted draft. This mirrors `JournalViewModel`'s existing "no validation, no draft loss" ethos but adds durability the old one-question-at-a-time stepper never needed (it also lost draft answers on process death — not a regression, an improvement).
Recommend **deferring** "tap your own last answer bubble to edit it" (an in-flow undo) — added complexity for a first cut; the existing edit-form (Task B's dedicated non-chat screen) already covers "I want to change a past answer" once the entry is saved. Flag this as the one deliberately-cut affordance.

**B2 — Completion behaviour.**
Recommend **auto-save-and-pop** after the last question's answer is sent: the chat writes the final `qa_json`/`response_text`, calls `OccurrenceScheduler.logHabitJournal(...)`, shows a brief inline confirmation bubble ("Saved ✓" or similar, ~600ms) then pops back — matching a real chat's feel (no separate "Save" tap) and matching `JournalViewModel.save()`'s existing all-or-nothing write. This is a UX change from the old stepper's explicit "Save" button, which is intentional (spec explicitly wants "like a friend asking you questions in a messaging app").

**B3 — `TaskType.JOURNAL` enum fate.**
Recommend **keep the Kotlin enum constant present-but-dead**, do NOT remove it. Reasoning: `TaskType` is `@Serializable`, stored by name in Room, and decoded leniently in `ExportImportRepository.importAllData` via `runCatching { TaskType.valueOf(d.type) }.getOrDefault(TaskType.FOOD)` (ExportImportRepository.kt:292) — an *old* backup file (pre-this-round) still carries `"type":"JOURNAL"` strings in its `IntakeReminderDef.type` field, and a genuinely ancient device restoring that file must not silently reinterpret every old journal reminder as FOOD. Keeping the enum value means `TaskType.valueOf("JOURNAL")` still succeeds; the app then needs exactly one guard (see Phase 2) so a JOURNAL-typed `FoodMedTask` freshly imported this way is treated as inert/legacy (no scheduling, no Type-picker entry, filtered out of the Add/Edit form's `TaskType.entries` render) rather than reintroducing the retired UI. Removing the constant entirely is "cleaner" but forces every historical-backup import through a lossy remap with no upside — the dead-constant cost is one line in the enum plus one filter, forever documented.

**B4 — Journal habit's active-days/snooze.**
Recommend **yes, exactly like `INDIVIDUAL`** — own `times_json`/`active_days_json`, its own snooze interval, its own per-time alarms. No new scheduling concept. This is also the simplest scheduler change (Phase 2): `JOURNAL` joins `INDIVIDUAL` on every branch of `syncHabitInternal` except the *resolve* action, which opens the chat/edit-form instead of Complete/Skip.

**B5 — Streak counting for Journal-type habits.**
Recommend **YES — count toward the normal streak calculation exactly like `INDIVIDUAL`** (answering a chat = a completed day for `calculateHabitStreaks`), **NOT** the Ongoing/`STREAK`-type's current+best-only treatment. Journal is a scheduled, resolvable, per-slot habit (unlike `STREAK`, which has zero occurrences) — it fits the existing streak fold over `HabitOccurrence.status`/`scheduled_for` with no special-casing needed. `Occurrence.Status.LOGGED` (already used by FoodMed-JOURNAL) is the natural resolved-state to reuse here too (see B-status below), and the existing `calculateHabitStreaks` / `computeStats` machinery in `DetailViewModel` already treats any non-PENDING resolved status as "did the thing" for streak purposes — confirm this holds for `LOGGED` before wiring (Phase 6 verifies against `HabitSchedStatus`/the stats fold, which reads `status` generically, not by an INDIVIDUAL-only allow-list).

**B6 — Which `Occurrence.Status` marks an answered Journal habit occurrence.**
`Occurrence.Status` (DataModel.kt:318-323) is `PENDING, COMPLETED, SKIPPED, LOGGED`. Recommend reusing **`LOGGED`** (not `COMPLETED`) for a fully-answered `HabitOccurrence`, mirroring `FoodMedOccurrence`'s JOURNAL rows, which already use `LOGGED`. This keeps "has free-text/qa payload" and "is a plain checkbox completion" visually and semantically distinct in Detail's timeline glyph logic (`TimelineRow`'s action/status → icon map) with zero new enum work. No schema change to `Occurrence.Status` needed.

**B7 — Backfill for a past Journal habit slot.**
Recommend: **allow it**, reusing the exact `canBackfill` gate already used by both `backfillHabit` and `backfillFoodMed` (OccurrenceScheduler.kt:690-701) — a missed past Journal slot opens the **chat** for that historical date (steps snapshot from the habit's *current* `journalQuestionsJson`, same "no per-entry historical question drift" concern the old FoodMed-JOURNAL backfill already accepted) and Save routes through a new `backfillHabitJournal(habitId, slotMillis, qaJson, responseText)` that mirrors `backfillFoodMed`'s LOGGED branch (OccurrenceScheduler.kt:599-670) — never arms/cancels anything, exactly like every other backfill path. Re-opening an *already-backfilled* (LOGGED) past slot must route to the **edit-form**, not the chat again (same "resolved → edit-form, not chat" rule that governs live occurrences — B8 below). Justification for allowing it at all: FoodMed-JOURNAL already supports backfill (`JournalViewModel`'s `isBackfill` branch, JournalViewModel.kt:71-76, 128-141, 194-198) and there is no reason a habit-side journal should regress that capability — a forgotten day's journal entry is exactly the kind of thing backfill exists for.

**B8 — Editing entry point.**
Confirmed and locked (matches user's exact spec): tapping a **past answered** (`LOGGED`) occurrence — from Detail → History (mirroring `DetailScreen.kt:275`'s `event.action == REPLIED && isJournal -> onOpenJournal(occId)`, now generalized to a habit-side `isHabitJournal` branch that routes to the new **edit-form** route, not the chat) and from a resolved card on Today (mirroring `HomeScreen.kt:398`'s `editEntry` lambda) — opens the new plain edit-form screen directly. Only a **PENDING** occurrence (nothing answered yet) opens the chat. This is a pure routing decision keyed on `occurrence.status == PENDING` vs not, computed once in the nav-target resolver (Phase 4/5), no new state needed.

### Task C sub-decisions

**C1 — "Start" / re-start UX for a backdated date.**
Recommend: tapping **"Start"** (or, symmetrically, the same control after "Mark as broken" clears the run) always opens the existing themed `DaybookDatePickerDialog` (`TimePickerComponents.kt:185-215`) pre-selected to **today**, rather than a two-step "Today / Pick a date" chooser. Confirming with today already selected is a single tap — functionally identical to today's silent "start now" — while a user who forgot needs only one extra interaction (open the wheel, tap the earlier date, confirm) instead of navigating a disambiguation menu first. This reuses the exact component the v0.5.3 Phase 6 export-range feature already ships (`SettingsScreen.kt:897,908`) with **zero new UI primitives**. Add one new param to `DaybookDatePickerDialog`: `maxDate: LocalDate? = null` — when set, dates after it are unselectable (Material3 `DatePickerState` supports this via a `SelectableDates` implementation passed to `rememberDatePickerState`) — passed as `LocalDate.now()` at this call site so a future start date is structurally impossible, not just validated after the fact. The two existing export-range call sites pass no `maxDate` (unchanged behaviour).

**C2 — Exact day-count math for a backdated start.**
Confirmed unchanged: `daysSince(startMillis, nowMillis)` (`OngoingStreak.kt:14-18`) already computes `(DAYS.between(startDate, today) + 1)`, i.e. **inclusive** — this is pure date-bucketing (`Instant → ZonedDateTime → LocalDate`), so the *time-of-day* portion of the chosen start instant is irrelevant to the count; only the calendar date matters. Started 3 days ago → today's count is `3 + 1 = 4` days, exactly as the user's own example states. No change to `daysSince` or `HabitRepository.startStreak(id, nowMillis)` (`HabitRepository.kt:51-52`, already accepts an arbitrary `nowMillis`) — the ONLY new code is the UI passing a **user-chosen** date's start-of-day epoch millis instead of always `System.currentTimeMillis()`, via a new `RoutinesViewModel.startStreak(id: String, atMillis: Long)` overload (or changing the existing one-arg version's default to be explicit at the call site — either is fine; keep the existing zero-arg call sites working via a default parameter so nothing else in the codebase needs to change).

**C3 — Keep "Mark as broken" in the 3-dot menu too, or move it out entirely?**
Recommend **move it out of the `BottomSheetMenu` entirely** once the on-card control exists (`RoutinesScreen.kt:301-304`'s `if (habit.isStreak && habit.streakStartedAt != null) add(SheetAction(...,"Mark as broken"...))` is deleted). Keeping the action in two places for the same card invites the two call sites drifting (different confirm-dialog copy, one forgetting a future edit) for zero user benefit — the on-card control is strictly more discoverable than a menu entry it would duplicate. The 3-dot menu keeps Edit / Archive / Delete for a Streak habit, same as every other type.

**C4 — Card layout.** See Phase 8 below for the concrete before/after row sketch.

---

## §1 — Scope summary + DO-NOT-TOUCH invariants

**Task A** fixes `GhostButton`'s missing internal content padding (root cause below) so every shrink-to-content usage — currently only the two FoodMedForm "+ Add" rows — renders at a sane width instead of hugging its label text. No behaviour change, purely visual.

**Task B** removes Journal as an Intake (`TaskType`) concept end-to-end (data wiped, UI removed) and re-introduces it as a 4th `HabitType.JOURNAL`, with its own reminder schedule (Individual-like), a per-item ordered question list (`Habit.journalQuestionsJson`), a real scrolling chat UI for answering, and a separate plain multi-field edit-form for revisiting an answered entry. The global `journal_questions` table/editor is deleted outright (decision 3) — no migration-forward of its content.

**Task C** is a pure UI/UX pass over the existing `STREAK` ("Ongoing") habit type from the v0.5.5 round: an on-card "Mark as broken" affordance, a redesigned/aligned card layout, a backdated "Start" date picker, and an explicit History/Stats icon button. No new Room columns, no scheduler change — `streak_started_at` is written with a user-chosen date instead of always "now".

### DO-NOT-TOUCH invariants (every phase)
- Existing FoodMed **Food/Med/Custom** paths (form, scheduler, notifications, Detail, Home, backup) are untouched **except** removing the "Journal" chip from the Type picker (`FoodMedForm.kt:146-155`) and the now-dead `TaskType.JOURNAL`-specific branches becoming unreachable-but-present (per B3).
- Existing **Individual/Batch/Streak** habit behaviour is untouched except the additive `JOURNAL` arm.
- Notification channel IDs (`CHANNEL_HABITS = "habits_v2"`, `CHANNEL_FOOD_MED = "food_med_v2"`) are unchanged — Habit-Journal notifications post on the existing `CHANNEL_HABITS`, no new channel (see Phase 3 risk register entry on why a new channel is NOT needed).
- Pager `goToPage` / `beyondViewportPageCount` (MainActivity.kt) — untouched.
- `SoftCard` `graphicsLayer` — untouched.
- Real keystore/signing (`RELEASE_SIGNING.md`, CN=Daybook SHA1 39e62d0f) — untouched.
- The launch/sign-in gate — untouched.
- `definitionsHash` / cloud `ContentHash` — every new optional field introduced by Task B (`Habit.journalQuestionsJson` / `HabitDef.journalQuestions`, `HabitOccurrence.qaJson` / `HabitLog.qaJson`) is additive, nullable-or-empty-default, and `@EncodeDefault(EncodeDefault.Mode.NEVER)`-guarded exactly like `HabitDef.streakStartedAt`/`.motivation` and `Definitions.journalQuestions` already are — a user with zero Journal-type habits must see **zero** `definitionsHash`/month-`ContentHash` churn from this round. See §3 Risk register.

---

## §2 — Phases

### Phase 0 — Task A: fix the "text field + Add button" pattern (independent, do first)

**Root cause** (confirmed by reading the components): `GhostButton` (`ui/components/Components.kt:540-583`) sizes its `Box` to wrap its content (no `.fillMaxWidth()` — an intentional v0.5.3 Phase 0 change, comment at line 556-558, so the button can sit beside a weighted sibling in a `Row`) but its inner `Row` (lines 568-582) carries **no horizontal padding at all** — only `.height(50.dp)` on the outer `Box`. Every *other* call site (`TimePickerComponents.kt:97`, `RespondScreen.kt:189,193,205`, `AccountScreen.kt:162`, `SettingsScreen.kt:502,777,857,863,1024,1030,1069`, `JournalScreen.kt:157`) passes `Modifier.fillMaxWidth()`, so the zero-padding Row is invisibly centered inside a full-width box and the bug never shows. The two call sites that DON'T pass `fillMaxWidth()` — `FoodMedForm.kt:250-260` (Category "Add") and `FoodMedForm.kt:307-310` (Prompt message "Add") — sit in a `Row` next to a `Modifier.weight(1f)` `DaybookTextField`, so the button shrink-wraps to exactly the "Add" label's glyph width with **zero** breathing room on either side, producing the cramped, mis-shaped look in the screenshot (a tall, narrow rectangle with the text touching its rounded-corner border, next to a 44dp-tall-ish, generously-padded `DaybookTextField`).

**Fix** (one shared sub-component, every call site benefits): add horizontal content padding inside `GhostButton`'s `Row`:
```kotlin
Row(
    modifier = Modifier.padding(horizontal = 20.dp),   // NEW — was no padding at all
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
) { ... }
```
This is additive and harmless for every `fillMaxWidth()` caller (their content was already centered in a wide box; adding inner padding just gives the *shrink-wrap* case — the two FoodMedForm rows — the same comfortable label margin every full-width Ghost button already reads as having). No caller needs to change. Pick the padding value by matching `PrimaryButton`'s effective visual margin at `height(50.dp)` (labelLarge text is ~16sp; 20dp horizontal reads consistent with the app's `Spacing` scale — confirm against `Spacing.kt`'s token set during implementation and use a named token, not a bare literal, if one already exists at that value).

**Audit for other instances of the same pattern**: grepped `GhostButton(` across the tree (15 call sites) — confirmed only `FoodMedForm.kt:250` and `FoodMedForm.kt:307` omit `fillMaxWidth()`/an explicit width. No other "text field + small Add button" list-builder row exists elsewhere in the app (Settings' `QuietTimeRow`/`DateFieldRow` wrap their `GhostButton` in `Modifier.weight(1f)` + `fillMaxWidth()`, a different, already-consistent pattern). **`JournalQuestionsSettingsScreen.kt`'s "Add a question" row uses `PrimaryButton` full-width below the field, not this pattern** — not affected, and moot after Phase 7 deletes that screen anyway. Task B's new per-habit Questions editor (Phase 4) must NOT reintroduce this bug — it will use the same "new item text field, full-width `PrimaryButton` below it" shape `JournalQuestionsSettingsScreen.kt` already uses, sidestepping the pattern entirely, OR if a compact side-by-side "field + Add" row is used instead, it must pass `Modifier.fillMaxWidth()` on the `GhostButton` exactly like every already-correct call site.

**"Save reminder" sticky-bar disabled state** — confirmed **not** an instance of this bug. `PrimaryButton`'s disabled state (`Components.kt:492-538`) swaps `background` to `DaybookColors.SurfaceElevated` and text to `DaybookColors.TextFaint` (lines 515, 533) — a deliberate, distinct "inert" look (muted surface + faint text) separate from its enabled accent-filled state. This is intentional validation-gated styling (`FoodMedFormScaffold`'s `enabled = state.label.isNotBlank() && state.times.isNotEmpty()`, `FoodMedForm.kt:396`), not a sizing/shape bug — confirm visually once Phase 0 lands that it still reads as "disabled, not broken" (adequate contrast: `TextFaint` on `SurfaceElevated`), but no code change is anticipated here.

**Files touched**: `ui/components/Components.kt` (GhostButton only).
**Tests**: a Compose UI test (or a Robolectric/Paparazzi screenshot if the project has one wired — check for an existing screenshot-test harness before adding a new one) asserting `GhostButton`'s measured width, when NOT given `fillMaxWidth()`, exceeds the label's raw text width by ~40dp (2× the new horizontal padding) — a "visual-consistency" regression guard cheaper than a golden image. At minimum, document a before/after: **before** — Add button width ≈ text width (0dp margin); **after** — Add button width ≈ text width + 40dp, matching the visual weight of the adjacent 50dp-tall text field.
**4-gate**: run all four; this phase touches one shared file used everywhere buttons render, so `assembleRelease` (R8 + lintVital) is the most important signal that nothing else broke.

---

### Phase 1 — Task B: schema (DB v16 → v17)

**`data/model/DataModel.kt` changes:**
1. `HabitType` enum (line 275): append `JOURNAL` — `enum class HabitType { INDIVIDUAL, BATCH, STREAK, JOURNAL }`. Appended, never reordered (matches the existing STREAK-append comment at lines 270-274).
2. `Habit` entity (lines 13-44): add `@ColumnInfo(name = "journal_questions_json") val journalQuestionsJson: String = ""` at the end (after `motivation`, line 43) — nullable-free empty-string default mirrors `timesJson`/`activeDaysJson`'s existing empty-string-default pattern (not nullable, since "no questions configured yet" for a non-Journal habit is indistinguishable from "" exactly like an Individual habit with no times set before its first save). Store as a JSON string array of question texts, ordered — same shape as `journalQuestionsFromTexts`/`getOrderedTexts` already produce for the global table, just now owned per-habit. Add a small `DateTimeUtils`-adjacent codec (`journalQuestionsToJson`/`jsonToJournalQuestions`, mirroring `timesToJson`/`jsonToTimes`) OR reuse the existing `JournalQa`-adjacent JSON tooling with a plain `List<String>` serializer — pick whichever existing util module already has a `Json { }` instance configured for this shape (check `util/DateTimeUtils.kt` for the times/days codec pattern first).
3. `HabitOccurrence` entity (lines 57-70): add `@ColumnInfo(name = "qa_json") val qaJson: String? = null` at the end (after `localDate`, line 69) — nullable, no schema default, mirrors `FoodMedOccurrence.qaJson` (line 162) exactly.
4. `TaskType` enum (line 252): per B3, **leave `JOURNAL` in place**, add a one-line comment updating its status from "the removed Intake Journal type" to "retired — kept only so an old backup's `TaskType.valueOf(\"JOURNAL\")` still decodes; never reachable from the UI after this round" (see Phase 2's guard).
5. Delete `JournalQuestion` entity (lines 310-316) and its `@Entity(tableName = "journal_questions")` — the whole class goes.
6. `FoodMedTask`/`FoodMedOccurrence` JOURNAL-specific columns (`customCategory`, `description`, `qaJson`) are **left in place** (they're shared with CUSTOM/FOOD/MED, or in `qaJson`'s case simply unused going forward for any *newly created* row, since no task can be TaskType.JOURNAL anymore after Phase 2's Type-picker removal) — no column drop needed on the FoodMed side; Task B does not touch `food_med_tasks`/`food_med_occurrences` schema at all, only deletes their JOURNAL-typed *rows* (migration, below).

**Delete from Kotlin** (per the plan's explicit "delete the `@Entity`/DAO from Kotlin so schema regeneration matches" instruction):
- `data/model/DataModel.kt`: the `JournalQuestion` class.
- `data/local/JournalQuestionDao.kt`: whole file.
- `data/JournalQuestionRepository.kt`: whole file.
- `data/local/AppDatabase.kt`: remove `JournalQuestion::class` from `entities = [...]` (line 28), remove `abstract fun journalQuestionDao(): JournalQuestionDao` (line 41), bump `version = 16` → `version = 17` (line 29).
- `di/DatabaseModule.kt`: remove whatever `@Provides`/binding wires `JournalQuestionRepository` (grep confirmed this file references it — read it in Phase 1 to find and delete the exact provider).

**New DAO methods** (`data/local/HabitOccurrenceDao.kt`), mirroring `FoodMedOccurrenceDao`'s journal pair exactly (FoodMedOccurrenceDao.kt:66-94):
```kotlin
@Query("UPDATE habit_occurrences SET response_text_UNUSED... ")  // NOTE: HabitOccurrence has no response_text column today — see below
```
**Correction while drafting**: `HabitOccurrence` (unlike `FoodMedOccurrence`) currently has **no** `response_text` column at all — only `status`/`snoozeCount`/`respondedAt`/`notificationId`/`createdAt`/`localDate`. Habit-Journal doesn't need one (the `qa_json` blob is the only payload; there is no separate "joined non-blank answers" text field ever read elsewhere for a *habit*, unlike FoodMed where `response_text` feeds `HomeItem.responseText`'s trailing-slot preview). Recommend: **do not add a `response_text` column to `HabitOccurrence`** — add only `qa_json`, and derive any "preview text" the Today card needs (if Task B's Home wiring wants one) on the fly from `JournalQa.decode(qaJson)` at render time (join non-blank answers), exactly the string `journalQaPayload` already computes in `JournalViewModel.kt:36-42` — just don't persist it a second time on the habit side. This is a deliberate, justified small divergence from the FoodMed mirror, called out here so the implementer doesn't "helpfully" add a redundant column.

Actual new DAO methods:
```kotlin
@Query("UPDATE habit_occurrences SET qa_json = :qaJson, status = 'LOGGED', responded_at = :timestamp WHERE id = :occurrenceId")
suspend fun logJournalResponse(occurrenceId: String, qaJson: String?, timestamp: Long)

/** Journal Mode edit-in-place — mirrors FoodMedOccurrenceDao.editJournalResponse. Does NOT touch
 *  responded_at/status/scheduled_for. */
@Query("UPDATE habit_occurrences SET qa_json = :qaJson WHERE id = :occurrenceId")
suspend fun editJournalResponse(occurrenceId: String, qaJson: String?)

/** Draft auto-save (B1) — persists the in-progress answer snapshot without resolving the slot. */
@Query("UPDATE habit_occurrences SET qa_json = :qaJson WHERE id = :occurrenceId AND status = 'PENDING'")
suspend fun saveJournalDraft(occurrenceId: String, qaJson: String)
```

**Migration `MIGRATION_16_17`** (`data/local/Migrations.kt`), modeled directly on the existing `D5` journal-wipe block (visible at the top of the file read for this plan — the block deleting `food_med_events`/`food_med_occurrences` for `type='JOURNAL'` tasks) and on `MIGRATION_14_15`'s additive-column style:

```kotlin
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // (a) additive columns
        db.execSQL("ALTER TABLE habits ADD COLUMN journal_questions_json TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE habit_occurrences ADD COLUMN qa_json TEXT")

        // (b) discard ALL existing Intake-Journal data (user's explicit "fresh start" decision).
        // Children first (FK-safety net even if cascade isn't configured — verify actual FK
        // declarations on food_med_events/food_med_occurrences before trusting cascade alone):
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
```
**Verify before finalizing**: check `FoodMedOccurrence`/`FoodMedEvent`'s actual `@Entity` declarations (and any `@ForeignKey` annotations) in `DataModel.kt` — as read for this plan, **neither entity declares a `@ForeignKey`** (they're plain `@Entity(tableName=...)` with only `@Index` for performance, no FK constraint, no `onDelete` cascade). This confirms the migration **must** delete children explicitly in the order shown (events → occurrences → tasks) — there is no cascade to rely on, and reversing the order would leave orphaned `food_med_events`/`food_med_occurrences` rows referencing a deleted `task_id` with no task, which is exactly the kind of orphan `HabitEvent.itemId`'s "orphaned rows keep null" comment (DataModel.kt:90-92) warns is otherwise silently invisible everywhere. The WHERE clause `type = 'JOURNAL'` is scoped to a **subquery on `food_med_tasks.type`**, never touching FOOD/MED/CUSTOM rows — double-check this against the real MIGRATION test (below) with a populated table containing all four types before/after.
Register `MIGRATION_16_17` in `AppDatabase`'s migration list (wherever `MIGRATION_15_16` etc. are wired — likely `di/DatabaseModule.kt` or a Room builder call).
Generate `app/schemas/.../17.json` via the existing schema-export Gradle mechanism (matches how `16.json` was produced for the last round — run the project's schema-export task or `./gradlew` targets that already regenerate it, do not hand-write it).

**Tests**:
- A new `MigrationTest` 16→17 case (compile-only is fine per the constraint — no device attached): populate v16 tables with a mix of FOOD/MED/CUSTOM/JOURNAL `food_med_tasks` (+ occurrences + events for each), run the migration, assert JOURNAL rows and their children are gone, non-JOURNAL rows are untouched, `journal_questions` table no longer exists (query `sqlite_master`), and the two new columns exist with the right defaults.
- A unit test for the WHERE-clause correctness at the SQL level (or exercised entirely through the `MigrationTest` above — no separate pure-Kotlin test needed since this is pure SQL, not app logic).

**Files touched**: `data/model/DataModel.kt`, `data/local/AppDatabase.kt`, `data/local/Migrations.kt`, `data/local/HabitOccurrenceDao.kt`, deletion of `data/local/JournalQuestionDao.kt` + `data/JournalQuestionRepository.kt`, `di/DatabaseModule.kt` (remove the dead provider), new `app/schemas/.../17.json`, new `MigrationTest` case.
**4-gate**: all four — this phase alone should compile and pass unit tests even before any UI lands, since it's pure schema/DAO.

---

### Phase 2 — Task B: scheduler + notification + alarm wiring

**`data/OccurrenceScheduler.kt`:**
- `isNoScheduleHabit` (line 46): unchanged — `JOURNAL` is NOT a no-schedule type (per B4), so it must NOT be added here.
- `syncHabitInternal` (lines 224-271): per B4, `JOURNAL` behaves exactly like `INDIVIDUAL` through the whole slot-generation body (lines 239-268) — only line 269 (`if (habit.type == HabitType.INDIVIDUAL) armNextHabitInternal(habitId)`) needs to become `if (habit.type == HabitType.INDIVIDUAL || habit.type == HabitType.JOURNAL) armNextHabitInternal(habitId)`. `effectiveTimesJson` (lines 95-96) is untouched — `JOURNAL` habits carry their own `timesJson` like `INDIVIDUAL`, not the BATCH substitution.
- New resolve action, modeled on `logJournal` (lines 442-464) but for the habit side and using the Phase 1 DAO methods:
```kotlin
suspend fun logHabitJournal(occurrenceId: String, qaJson: String?, isEdit: Boolean = false) = syncMutex.withLock {
    val occ = db.habitOccurrenceDao().getOccurrenceById(occurrenceId)
    occ?.let { notificationUtils.cancelNotification(it.notificationId) }
    if (occ == null) return@withLock
    val qa = qaJson?.take(MAX_JOURNAL_CHARS)?.takeIf { it.isNotBlank() }
    if (isFoodMedEdit(occ.status, isEdit)) {   // reuse — the predicate is generic over Occurrence.Status
        db.habitOccurrenceDao().editJournalResponse(occurrenceId, qa)
        return@withLock
    }
    db.habitOccurrenceDao().logJournalResponse(occurrenceId, qa, System.currentTimeMillis())
    db.habitEventDao().insert(HabitEvent(occurrenceId = occurrenceId, action = Event.Action.REPLIED, itemId = occ.habitId))
    // finish: mirrors finishFoodMed but on the habit side (cancel, cancel-alarm, arm-next-no-catchup)
    notificationUtils.cancelNotification(occ.notificationId)
    notificationUtils.cancelReminderAlarm(occ.id, occ.notificationId, isHabit = true)
    armNextHabitInternal(occ.habitId, allowCatchup = false)
    notificationUtils.cancelNotification(occ.notificationId)
}
```
Rename `isFoodMedEdit` mentally to "is-edit-in-place" — it's already generic over `Occurrence.Status` + a caller flag (OccurrenceScheduler.kt:716-717), no signature change needed; just call it from the new habit path too (consider renaming to `isEditInPlace` for clarity while touching this file, purely cosmetic, optional).
- New backfill action, modeled on `backfillFoodMed`'s LOGGED branch (lines 599-670) combined with `backfillHabit`'s shape (lines 561-594):
```kotlin
suspend fun backfillHabitJournal(habitId: String, slotMillis: Long, qaJson: String?) = syncMutex.withLock {
    val habit = db.habitDao().getHabitById(habitId) ?: return@withLock
    val date = DateTimeUtils.timestampToLocalDate(slotMillis)
    if (!canBackfill(date, LocalDate.now(), habit.createdAt, habit.activeDaysJson, habit.isArchived)) return@withLock
    if (!monthResident(date)) return@withLock
    val id = occId(habitId, slotMillis)
    val now = System.currentTimeMillis()
    val qa = qaJson?.take(MAX_JOURNAL_CHARS)?.takeIf { it.isNotBlank() }
    db.withTransaction {
        val existing = db.habitOccurrenceDao().getOccurrenceById(id)
        val isBackfillEdit = existing != null && existing.status != Occurrence.Status.PENDING
        if (existing == null) {
            db.habitOccurrenceDao().insertAll(HabitOccurrence(
                id = id, habitId = habitId, scheduledFor = slotMillis,
                status = Occurrence.Status.LOGGED, qaJson = qa, respondedAt = now,
                notificationId = notificationIds.next(), localDate = date.toString()
            ))
        } else if (isBackfillEdit) {
            db.habitOccurrenceDao().editJournalResponse(id, qa)
        } else {
            db.habitOccurrenceDao().logJournalResponse(id, qa, now)
        }
        if (!isBackfillEdit) {
            db.habitEventDao().insert(HabitEvent(occurrenceId = id, action = Event.Action.REPLIED, itemId = habitId))
        }
    }
}
```
- `isJournalOccurrence` (lines 390-392): add a habit-side counterpart, e.g. `isHabitJournalOccurrence(occurrenceId: String): Boolean = db.habitOccurrenceDao().getOccurrenceById(occurrenceId)?.let { db.habitDao().getHabitById(it.habitId)?.type == HabitType.JOURNAL } == true` — needed by MainActivity's deep-link resolver (Phase 6).
- `detailTargetFor` (lines 381-386): unchanged — habit-journal's Detail target is just `"habit" to habitId`, same as any other habit type.

**B3's guard** (`TaskType.JOURNAL` dead-but-present): anywhere `TaskType.entries` is rendered for user choice (`FoodMedForm.kt:146`) must filter it out: `TaskType.entries.filter { it != TaskType.JOURNAL }.forEach { ... }`. Anywhere a `FoodMedTask` is loaded for editing (`EditFoodMedScreen`/its ViewModel — locate and check) must treat a legacy `type == TaskType.JOURNAL` row defensively — since the migration deletes all such rows outright (Phase 1), this scenario can only occur via a **future** import of an **old pre-this-round backup file** that still names JOURNAL tasks; recommend `ExportImportRepository.importAllData`'s task-mapping (line 288-308) gets one extra line: a `TaskType.JOURNAL` decoded from an old file is remapped to `TaskType.CUSTOM` (not silently kept as JOURNAL, since post-this-round nothing can render/schedule/edit a JOURNAL-typed FoodMedTask correctly anymore) — document this remap plainly in a comment, since it's the one place old data quietly changes shape on import.

**`util/notification/NotificationUtils.kt`:**
- New method `showHabitJournalNotification(occurrence: HabitOccurrence, habitTitle: String, promptMessage: String?)`, modeled on `showFoodMedNotification`'s `isJournal` branch (lines 319-356) but posted on `CHANNEL_HABITS` (not `CHANNEL_FOOD_MED`) with the existing habit action-intent helpers (`isHabit = true`), body `"Tap to write today's entry"`, and **Skip + Snooze only** (no Complete, no RemoteInput) — mirroring the FoodMed-JOURNAL shape exactly but swapping which channel/small-icon (`R.drawable.ic_notif_habit`, matching `showHabitNotification`'s icon at line 295) and which `isHabit` flag is used.
- The existing `showHabitNotification` (lines 293-312) is the call site that must branch: wherever `showHabitNotification` is invoked (`AlarmReceiver.kt` — confirmed no JOURNAL-specific string in that file today, meaning the branch decision currently lives entirely in `NotificationUtils`/`OccurrenceScheduler` callers, not in `AlarmReceiver` itself) — find the exact call site in `AlarmReceiver.kt` that currently unconditionally calls `notificationUtils.showHabitNotification(...)` for a fired habit occurrence, and add: look up the habit's `type`; if `JOURNAL`, call `showHabitJournalNotification` instead.
- **Why no new notification channel is needed**: `CHANNEL_HABITS` is already `IMPORTANCE_HIGH` and habit-scoped; a Journal-type habit is still fundamentally "a habit reminder", just with a different action set on the notification and a different tap destination — same reasoning FoodMed-JOURNAL used to justify staying on `CHANNEL_FOOD_MED` rather than getting its own channel. Confirm this holds by checking `AlarmReceiver.kt`'s existing `NotificationActionReceiver.ACTION_SKIP`/`ACTION_SNOOZE`/`ACTION_COMPLETE` handling — Skip/Snooze already work generically over `isHabit`; there is no `ACTION_COMPLETE` tap path to disable since the notification for Journal habits simply never adds that action (mirrors how FoodMed-JOURNAL's notification never adds the Reply action, line 345).

**`util/alarm/AlarmReceiver.kt`**: locate the exact habit-notification-post call site (read the file fully in this phase — it was not grepped for "JOURNAL" successfully because the branch doesn't exist yet, confirming this is genuinely new code, not a rename) and route through the `HabitType.JOURNAL` check described above. Also confirm the **notification-tap → app-open deep link** path: `contentIntent` (`NotificationUtils.kt:378-399`) already carries `EXTRA_OPEN_OCCURRENCE_ID` + `EXTRA_OPEN_IS_HABIT` generically — no change needed there; MainActivity's `LaunchedEffect(pendingDeepLink)` (Phase 6) is where the `isHabit && isJournalType` branch gets added, exactly parallel to the existing `!isHabit && isJournalOccurrence` branch (MainActivity.kt:368-378).

**Files touched**: `data/OccurrenceScheduler.kt`, `util/notification/NotificationUtils.kt`, `util/alarm/AlarmReceiver.kt`, `data/ExportImportRepository.kt` (the one-line legacy-JOURNAL-task remap on import), `ui/foodmed/FoodMedForm.kt` (filter `TaskType.entries`).
**Tests**: a scheduler unit test asserting `syncHabitInternal` arms an alarm for `HabitType.JOURNAL` exactly like `INDIVIDUAL` (extend whatever existing test covers `OngoingSchedulerDecisionTest`'s sibling scenarios, or add a new `HabitJournalSchedulerTest`); a pure test for the legacy-TaskType-JOURNAL-import remap.
**4-gate**: all four.

---

### Phase 3 — Task B: `HabitForm.kt` + `AddHabitViewModel.kt` — Type chip, Questions editor

**`HabitFormState`** (`HabitForm.kt:33-52`): add `val journalQuestions = mutableStateListOf<String>()`.

**Type row** (`HabitForm.kt:118-141`): add a 4th `DaybookChip` for `HabitType.JOURNAL` ("Journal"), with helper copy for the `when` block: `"Journal habits ask your saved questions at each reminder time, in a chat."`. Order the chips `Individual, Batch, Ongoing, Journal` (append, matching the enum's append-only order from Phase 1) or reorder for UX if desired — confirm chip order doesn't need to match enum declaration order anywhere else (check `HabitType.entries` isn't rendered generically elsewhere the way `TaskType.entries` is in FoodMedForm; `HabitForm.kt` hand-lists each chip explicitly, so this is a free choice).

**Times section** (`HabitForm.kt:143-152`): currently gated `if (state.type == HabitType.INDIVIDUAL)`. Per B4, change to `if (state.type == HabitType.INDIVIDUAL || state.type == HabitType.JOURNAL)`.

**New "Questions" `FormGroup`** inside `AdvancedSection` (after the existing groups, `HabitForm.kt:154-232`), gated `if (state.type == HabitType.JOURNAL)`, porting the exact add/edit/delete/reorder UX from `JournalQuestionsSettingsScreen.kt` (read in full for this plan, lines 101-269) but operating on `state.journalQuestions` (an in-memory `SnapshotStateList<String>`, no separate ViewModel/repository — this list IS the form field, saved as JSON on `saveHabit()`) instead of a Room-backed global list:
- Render each question as a row: label text + Up/Down/Delete `CircleIconButton`s (mirror `JournalQuestionRow`, `JournalQuestionsSettingsScreen.kt:220-269`), tap-to-edit opens the same `DaybookAlertDialog` pattern (lines 176-197) but mutating the in-memory list instead of calling a repo.
- ≥1 rule (D6): reuse the pure `canDelete(count: Int): Boolean = count > 1` — either import it if left as an `internal` top-level fun after `JournalQuestionRepository.kt` is deleted (move it to a small shared pure-function file, e.g. `data/JournalQaShared.kt` or inline it directly in `HabitForm.kt` since it's a one-liner) or re-derive it locally; either way keep a unit test on it.
- Add-a-question row: **use the `JournalQuestionsSettingsScreen` shape** — a `DaybookTextField` (no label) + `Spacer` + full-width `PrimaryButton("Add question", enabled = draft.isNotBlank())` **below** it, NOT a side-by-side "field + small Add button" row — this sidesteps Task A's pattern entirely per Phase 0's note, and is a straight port of already-shipped, already-correct UI.
- **Active days / Snooze** (`HabitForm.kt:211-231`, currently gated `if (state.type != HabitType.STREAK)`): per B4, `JOURNAL` should show these (only `STREAK` hides them) — the existing `!= HabitType.STREAK` guard already includes `JOURNAL` correctly with **zero code change**, since `JOURNAL != STREAK` is true. Confirm this reads right when the chip is added — no actual edit needed here, just verify.
- **"Reminder text" `FormGroup`** (`HabitForm.kt:193-202`, gated `if (state.type != HabitType.STREAK)`): same — `JOURNAL` already falls through to showing it, matching B4 ("Journal habit's own custom notification prompt" — though the actual notification body for Journal is the fixed "Tap to write today's entry" per Phase 2, so either (a) hide this field for JOURNAL too since it's inert, or (b) keep it and let `promptMessage` feed a future customization hook. **Recommend hiding it for JOURNAL** (mirrors why it's hidden for STREAK — no meaningful place for custom text to surface) — change the guard to `if (state.type != HabitType.STREAK && state.type != HabitType.JOURNAL)`. Flag this as a minor added scope beyond the given brief but a direct consequence of Phase 2's fixed notification body; call it out to the user/reviewer as a one-line judgment call.

**`anyAdvancedFieldNonDefault`** (`HabitForm.kt:275-283`): add a `JOURNAL`-aware branch: a non-default Questions list (anything other than empty, or however "default" is defined for a brand-new Journal habit — likely just `state.journalQuestions.isNotEmpty()` after the form seeds one default question, see below) should force Advanced open, same treatment `STREAK` gets today.

**Default-seeding a new Journal habit's first question**: since the global `JournalQuestionRepository.DEFAULT_QUESTION` ("What's on your mind?") pattern is gone, `AddHabitViewModel` should seed `journalQuestions = mutableListOf("What's on your mind?")` when the Type chip is first switched to `JOURNAL` on a *new* habit (not on every recomposition — gate it so switching away and back doesn't re-seed over user edits). This preserves the D6 "≥1 question, always" UX without a global default row anywhere in the schema.

**`AddHabitViewModel.kt`**: add `var journalQuestions = mutableListOf<String>()`; in `saveHabit()` add `journalQuestionsJson = journalQuestionsToJson(journalQuestions)` (or whatever codec Phase 1 lands) to the `Habit(...)` construction (mirrors every other field's carry-through, `AddHabitViewModel.kt:105-125`); in `clearForm()` reset it to a single default question (mirrors `streakStartedAt = null` etc., lines 158-174). Per B4/keep-columns precedent (`keepStreakColumns`, lines 24-31): a Journal→Journal edit should carry the questions list forward from what's already loaded (EditHabitScreen hydrates `journalQuestions` from the loaded `Habit.journalQuestionsJson` before the user touches Advanced) — a switch INTO or OUT OF Journal does **not** need the same reset dance `STREAK` needs, since `journalQuestionsJson` on a non-Journal habit is simply inert/ignored data, not data that corrupts scheduling if stale (unlike `streak_started_at`, which actively changes Detail's Stats branch). Recommend: leave whatever was loaded in place on a type switch away from Journal (harmless dead data, matches how `promptMessage`/`motivation` already survive type switches) rather than force-clearing it — simpler, and avoids losing a user's carefully-written question list if they flip the chip back and forth while composing the form.

**Files touched**: `ui/routines/HabitForm.kt`, `ui/routines/AddHabitViewModel.kt`, (EditHabitScreen — locate and hydrate `journalQuestions` from the loaded habit), a small shared pure-function module for `canDelete`/`moveInList`/`normaliseQuestionPositions` if `JournalQuestionRepository.kt`'s pure helpers are worth keeping rather than reimplementing (recommend moving them, they're already unit-tested-shaped and habit-agnostic).
**Tests**: `HabitFormSaveEnabledTest` extended for `JOURNAL` (title-only requirement, like BATCH/STREAK, or does it need ≥1 time like INDIVIDUAL? — per B4 it schedules like INDIVIDUAL, so recommend **yes, require ≥1 time**, update `habitFormSaveEnabled`'s `type != HabitType.INDIVIDUAL` check to `type !in setOf(HabitType.INDIVIDUAL, HabitType.JOURNAL)`); `AnyAdvancedFieldNonDefault` test extended; a small `JournalQuestionsListEditTest` (pure) covering the ported add/edit/delete/reorder helpers operating on a plain `List<String>` instead of Room rows.
**4-gate**: all four.

---

### Phase 4 — Task B: the chat screen (new)

**New route**: `"habit_journal_chat/{occurrenceId}/{slotMillis}"` mirroring the existing `"journal/{arg0}/{slotMillis}"` two-shape contract (occurrenceId for a live PENDING row, slotMillis>0 for a backfill of a habit id). Register in `MainActivity.kt` alongside the existing `composable("journal/{arg0}/{slotMillis}")` block (MainActivity.kt:592-597).

**New file** `ui/journal/HabitJournalChatScreen.kt` + `HabitJournalChatViewModel.kt` (new package location TBD — could live under `ui/journal/` alongside the existing files since it's the same domain concept, just habit-backed; recommend keeping it there rather than a new `ui/habitjournal/` package, for discoverability).

**ViewModel** (`HabitJournalChatViewModel`), modeled on `JournalViewModel.kt` but with a genuinely different UI shape:
- `init` block mirrors `JournalViewModel.init` (lines 121-171) exactly for **resolving which habit/occurrence/slot** is being answered — same `isBackfill` branch, same `wasResolved` computation — but the **question source** is now `habit.journalQuestionsJson` (decoded once) instead of `journalQuestionRepository.observeQuestions()`, and an **already-resolved** entry's questions still come from ITS OWN `qa_json` snapshot (same "renaming a question later must not rewrite old entries" rule, `JournalViewModel.kt:150-160`) — this part of the logic ports almost verbatim, just swapping the question source.
- Per B8: if `wasResolved` is true (occurrence status is `LOGGED`), the ViewModel should **not** even present a chat — the nav layer (see below) should have already routed to the edit-form instead. Recommend the ViewModel asserts/guards this (if somehow reached with `wasResolved == true`, redirect via a one-shot `UiState.redirectToEditForm = true` signal) rather than silently rendering a chat over already-answered data — defense in depth, since the "which screen to open" decision is made by the caller (Detail/Home/notification-tap), but the chat screen shouldn't trust that blindly.
- `UiState` shape, genuinely new (not the old `steps`/`index` stepper): a chat needs a **transcript** — recommend:
```kotlin
data class ChatUiState(
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),   // every Q + every sent A, in order, oldest first
    val currentQuestionIndex: Int = 0,                 // which question is "live" (being answered)
    val draftAnswer: String = "",                      // the compose-box text not yet sent
    val allAnswered: Boolean = false,
    val busy: Boolean = false,
    val saved: Boolean = false,
    val missing: Boolean = false
)
sealed class ChatMessage {
    data class Question(val text: String) : ChatMessage()
    data class Answer(val text: String) : ChatMessage()
}
```
- On load, if resuming a draft (B1: `qa_json` had a partial snapshot written from an earlier session), `messages` is rebuilt by replaying `JournalQa.decode(existingQaJson)` as alternating Question/Answer bubbles up to the first blank answer, and `currentQuestionIndex` resumes there.
- `onDraftChange(text)` — updates `draftAnswer` only, no persistence (avoid a DB write per keystroke).
- `sendAnswer()` — pure logic: append a `Question(current)` + `Answer(draft)` pair to `messages`, advance `currentQuestionIndex`, clear `draftAnswer`, **persist the running `qa_json` via `saveJournalDraft`** (B1's auto-save), and if that was the last question, per B2 auto-save-and-pop: call `occurrenceScheduler.logHabitJournal(...)` (or `backfillHabitJournal(...)` for the backfill shape), set `allAnswered = true` then `saved = true` after a short confirmation delay.
- Extract a **pure** helper (unit-testable without Compose/ViewModel), e.g. `fun advanceChat(state: ChatUiState, questions: List<String>, sentAnswer: String): ChatUiState` — the actual bubble-append + index-advance + all-answered check, exactly the spirit of the existing `clampIndex`/`isLastStep`/`journalQaPayload` pure functions (`JournalViewModel.kt:36-61`) but reshaped for a transcript instead of a stepper. Name it something like `ChatFlowTest`-friendly.

**Screen** (`HabitJournalChatScreen`), the actual new UI work:
- `BackHeader` (title = habit title), matching every other screen's header pattern.
- A `LazyColumn` (reverse-friendly or auto-scroll-to-bottom on new message — recommend `LazyColumn` with `state.animateScrollToItem(lastIndex)` inside a `LaunchedEffect(messages.size)`, simplest correct approach; a `reverseLayout=true` list is more idiomatic for chat but inverts scroll-to-top-for-oldest semantics unnecessarily for a short, bounded (≤ dozen questions) transcript — recommend the simpler forward-order + auto-scroll approach given the bounded length).
- Each `ChatMessage.Question` renders as a left-aligned bubble (e.g. `SoftCard`/a rounded `Box` with `CardTints.Neutral`, matching the app's existing card language rather than inventing a new "chat bubble" shape system) with the question text.
- Each `ChatMessage.Answer` renders as a right-aligned bubble in the accent tint (`LocalAccent.current` fill, `OnSolid` text) — the one deliberately new visual: a right-aligned, accent-colored, rounded rectangle distinct from every existing left-aligned card in the app. Keep the shape token consistent (`AppShapes.card` or a new slightly-more-rounded "bubble" shape if the design calls for it — recommend reusing `AppShapes.card` for consistency rather than adding a new shape token for one screen).
- Below the transcript: the "live" question is **not** its own bubble yet — render it as a subtle header/label above the input (e.g. `DaybookText.SectionTitle`, muted) OR simply push it into the transcript immediately as a `Question` bubble with no matching `Answer` yet (simpler mental model: the whole conversation so far, including the just-asked question, is always in the transcript; the compose box below is just "type your reply to the last bubble"). **Recommend the latter** — it reads more like a real chat thread and avoids a UI mode-switch between "the live question area" and "the transcript".
- Bottom-docked compose bar: `StickySaveBar`-style container (reuse the existing sticky-bar shell for IME/nav-inset handling) holding a `DaybookTextField` (multi-line capable, no label, placeholder "Type your answer…") + a circular send button (`CircleIconButton`, `DaybookIcons`'s existing send/arrow glyph if one exists, else reuse `Icons.Filled.Send` from Material icons like the rest of the app mixes sources) — tapping Send (or a keyboard Send/Done action) calls `sendAnswer()`, disabled while `draftAnswer.isBlank()`.
- `LaunchedEffect(state.saved)` pops back (mirrors `JournalScreen.kt:55-60`), with a brief "Saved" toast/snackbar OR an inline final bubble ("✓ Entry saved") shown for ~600ms before popping, per B2.
- `BackHandler`: mid-chat back should just pop (per B1, nothing is lost — the draft is already persisted after each send) — no special "step back" interception needed here, unlike the old stepper's `index > 0` back semantics, since there's no "undo the last send" affordance (B1 deferred).

**Files touched (new)**: `ui/journal/HabitJournalChatScreen.kt`, `ui/journal/HabitJournalChatViewModel.kt`.
**Files touched (existing)**: `ui/MainActivity.kt` (new route + nav wiring).
**Tests**: pure tests for the chat-advance helper (`ChatFlowTest`), draft-resume-from-partial-qaJson test, and a Compose UI test asserting bubbles render in order and the send button is disabled on a blank draft.
**4-gate**: all four.

---

### Phase 5 — Task B: the plain edit-form screen (new)

**New route**: `"habit_journal_edit/{occurrenceId}"` — always a live, already-resolved occurrence id (no backfill shape needed here per se, though a *backfilled-then-reopened* row is just a resolved `HabitOccurrence` like any other, so the same route serves both).

**New file** `ui/journal/HabitJournalEditScreen.kt` + `HabitJournalEditViewModel.kt`.

**ViewModel**: loads the occurrence, decodes `qa_json` via `JournalQa.decode` into an ordered `List<Pair<question, answer>>`, exposes a `List<QAField>` (question label + a mutable answer string per field, index-stable). `save()` re-encodes via `JournalQa.encode` and calls `occurrenceScheduler.logHabitJournal(occurrenceId, qaJson, isEdit = true)` — this is the **edit-in-place** path (B8), never touches `responded_at`/status, appends no new event, exactly like `logJournal(isEdit=true)`/`editFoodResponse` already guarantee.

**Screen**: standard form shape — `BackHeader` + `LazyColumn` of `FormGroup`-per-question (or one `FormGroup` containing all Q/A pairs stacked, matching how `DetailScreen`'s `journalRowPairs` already stacks Q&A for display, `DetailScreen.kt:387-388` — this screen is that same stacked layout but with each answer in an **editable** `DaybookTextField` instead of static text) — each question rendered as a label (`DaybookText.CardTitle` or similar) with its saved answer in a `DaybookTextField(singleLine = false)` directly below, all visible/editable at once, plus a standard `StickySaveBar` + `PrimaryButton("Save")` at the bottom (no chat framing at all — a completely conventional edit form, per the spec's explicit "must NOT reopen the chat").

**Files touched (new)**: `ui/journal/HabitJournalEditScreen.kt`, `ui/journal/HabitJournalEditViewModel.kt`.
**Files touched (existing)**: `ui/MainActivity.kt` (new route).
**Tests**: a pure test for the load→decode→re-encode round trip preserving question order and blank-answer handling; a Compose UI test asserting N questions render N editable fields pre-filled with their saved answers.
**4-gate**: all four.

---

### Phase 6 — Task B: Detail / Home / MainActivity wiring

**`ui/MainActivity.kt`:**
- Deep-link resolver (`LaunchedEffect(pendingDeepLink)`, lines 368-379): extend the `isJournal` check to also cover the habit side: `val isHabitJournal = isHabit && withContext(Dispatchers.IO) { occurrenceScheduler.isHabitJournalOccurrence(occId) }`, then branch: if `isHabitJournal`, look up the occurrence's `status` — `PENDING` → navigate to the chat route; else → navigate to the edit-form route (per B8). If plain `isJournal` (FoodMed side) → unchanged existing behaviour.
- `goJournal`/`goJournalBackfill` (lines 434-437): add habit-side counterparts `goHabitJournalChat`/`goHabitJournalEdit`/`goHabitJournalBackfill`, wired to the new routes from Phase 4/5.
- `DetailScreen` composable call (lines 580-590): add an `onOpenHabitJournal: (occId) -> Unit` (or reuse the existing `onOpenJournal` param generically if `DetailViewModel` exposes enough info to disambiguate FoodMed-vs-Habit journal at the call site — recommend a single callback with the *destination already resolved* by `DetailViewModel`/`DetailScreen` internally, so `MainActivity` doesn't need to know which flavor of journal it's routing, mirroring how `onOpenRespond` already works generically).
- New route registrations for `habit_journal_chat/{occurrenceId}/{slotMillis}` and `habit_journal_edit/{occurrenceId}` (Phase 4/5).

**`ui/detail/DetailViewModel.kt` + `DetailScreen.kt`:**
- Add `_isHabitJournal: MutableStateFlow<Boolean>` alongside the existing `_isJournal`/`_isOngoing` (lines 111-117), set when loading a habit whose `type == HabitType.JOURNAL` (mirrors line 326's `_isJournal.value = task.type == TaskType.JOURNAL` but on the habit-load path).
- `HistoryTab`'s row-click resolver (`DetailScreen.kt:272-278`): extend the `when` to route a habit-journal `REPLIED` event to the new **edit-form** route (never the chat, per B8) — `isHabit && isHabitJournal && event.action == REPLIED -> ({ onOpenHabitJournalEdit(occId) })`. This sits ABOVE the generic `isHabit -> onToggleHabit(occId)` branch (which is for plain Individual/Batch habit rows, an undo-toggle, not applicable to a resolved Journal entry).
- `journalRowPairs` (lines 387-388) and `TimelineRow`'s `isJournal` stacked-Q&A rendering (lines 390-460ish) already operate generically on `qa_json` + a boolean flag — pass `isHabitJournal` into the same `isJournal` parameter at the habit-journal call site (the function doesn't care which table the blob came from, it's pure string decode + render). **No new rendering code needed here** — only wiring the existing `isJournal: Boolean` flag to also be true for a habit-journal row, and sourcing the blob from `HabitOccurrence.qaJson` instead of `FoodMedOccurrence.qaJson` wherever `TimelineEvent` is built for the habit side (find and extend whatever maps `HabitEvent`+`HabitOccurrence` → `TimelineEvent` in `DetailViewModel.kt`, likely near line 352's "carry the Q&A snapshot" comment on the FoodMed side — add the mirror for habits).
- `StatsTab` (lines 302-365): per B5, a Journal-type habit should render exactly like the default (non-Ongoing) branch — `isOngoing` stays `false` for it (it's a different `_isOngoing`/`_isHabitJournal` flag entirely, no overlap), so **no change needed** to `StatsTab` itself — Current streak / Best / Completion rate / This month all already compute generically from `HabitOccurrence.status`/`scheduled_for` via `getScheduledStatusesForHabit` (HabitOccurrenceDao.kt:162-163), which doesn't care about `qa_json` at all. Confirm `calculateHabitStreaks`/`computeStats`'s fold treats `Occurrence.Status.LOGGED` as "counts as done" identically to `COMPLETED` — **read `util/streak/StreakCalculator.kt` (or wherever `calculateHabitStreaks` lives) in this phase** to confirm before shipping; if it currently only recognizes `COMPLETED` for habits (since no habit occurrence has ever used `LOGGED` before this round), this is the one real logic change Phase 6 must make — add `LOGGED` to whatever "resolved-positively" status set the habit-side streak fold uses, gated so it doesn't change any existing Individual/Batch behaviour (they never produce `LOGGED` rows, so widening the accepted-status set is a no-op for them, purely additive for Journal).

**`ui/home/HomeViewModel.kt` + `HomeScreen.kt`:**
- `HomeItem` (lines 52-95): add `val isHabitJournal: Boolean = false` alongside the existing `isJournal` (which stays FoodMed-only — recommend **not** overloading the existing field across two different underlying tables, since `HomeScreen.kt`'s existing `isJournal` branches assume a FoodMed `taskId`/`occurrenceId` shape in several places; a distinct flag keeps the two code paths legible even though visually the Today card should look and behave almost identically).
- `homeItemVisible`'s bucket logic (lines 105-114): route `isHabitJournal` into `ReminderFilter.JOURNAL` alongside `isJournal`, so the existing "Journal" filter chip on Home continues to mean "either kind of journal" from the user's perspective (item's own note: keep the filter enum unchanged, just widen what maps into it).
- Card rendering (`HomeScreen.kt:391-533`ish): the existing `isJournal`/`onOpenJournal`/`editEntry` logic (lines 391-398, 504-505, 533, 603) needs an `isHabitJournal` parallel branch: a `PENDING` habit-journal row shows the same "Write entry"/chat-opening trailing action (`DaybookIcons.Comment` `CircleIconButton`, line 505's exact shape) wired to `onOpenHabitJournalChat`; a resolved (`LOGGED`) one routes tap-to-edit through `onOpenHabitJournalEdit` instead of the undo-to-pending path every other resolved *habit* row gets (mirrors line 398's `editEntry` ternary, now three-way: `isJournal → FoodMed edit-form`, `isHabitJournal → Habit edit-form`, `else → toggle/undo`).
- `HomeViewModel`'s item-building code (lines 525, 602 per the earlier grep) constructs `isJournal = t.type == TaskType.JOURNAL` from a `FoodMedTask` — add the mirrored `isHabitJournal = h.type == HabitType.JOURNAL` wherever `HomeItem`s are built from `Habit`/`HabitOccurrence` rows (a different code path in the same file — locate it, likely nearby, building habit-sourced `HomeItem`s the same way the two grepped lines build FoodMed-sourced ones).

**Files touched**: `ui/MainActivity.kt`, `ui/detail/DetailViewModel.kt`, `ui/detail/DetailScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `util/streak/StreakCalculator.kt` (or equivalent — confirm exact filename during implementation) if the `LOGGED`-for-habits gap is real.
**Tests**: extend `homeItemVisible`'s existing test suite for the new flag; a streak-calculator test asserting a `LOGGED` habit occurrence counts identically to `COMPLETED`; a `DetailViewModel` test (if any exist for `_isJournal`/`_isOngoing` today — mirror its shape) for `_isHabitJournal`.
**4-gate**: all four — this is the highest-risk phase for "leaves a broken Complete/Skip card somewhere" (see §3), so pay particular attention to every render path a `HabitType.JOURNAL` occurrence can reach and confirm none of them fall through to the plain Individual/Batch Complete/Skip UI by omission.

---

### Phase 7 — Task B: backup/hash + Settings cleanup

**`data/backup/BackupModel.kt`:**
- `HabitDef` (lines 72-105): add
```kotlin
/** Journal-as-habit round: the per-habit ordered question set, texts only. Optional-with-default;
 *  `@EncodeDefault(NEVER)` so an EMPTY list is ABSENT — a user with no Journal habits sees a
 *  byte-identical definitionsHash (mirrors Definitions.journalQuestions / streakLongest). */
@EncodeDefault(EncodeDefault.Mode.NEVER)
val journalQuestions: List<String> = emptyList()
```
- `HabitLog` (lines 148-156): add
```kotlin
/** Journal-as-habit round: the ordered [{"q":…,"a":…}] snapshot for a JOURNAL habit's answered
 *  day; null for INDIVIDUAL/BATCH/STREAK. Optional-with-default so explicitNulls=false emits it
 *  as ABSENT for every non-Journal habit log — contentHash stays byte-identical. */
val qaJson: String? = null
```
Note `qaJson` here does **not** need `@EncodeDefault(NEVER)` — its default is `null`, and with `explicitNulls = false` (the project's existing `Json` config, confirmed by `IntakeLog.qaJson`'s identical un-annotated `null` default, `BackupModel.kt:180`) a null field is already omitted from the canonical bytes without the annotation; `@EncodeDefault(NEVER)` is specifically for a *non-null default* (like `emptyList()`/`0`/`false`) that would otherwise still be *written* even under `explicitNulls=false`. Follow `IntakeLog.qaJson`'s exact precedent, not `HabitDef.streakLongest`'s.
- `Definitions.journalQuestions` (the now-removed *global* field, lines 62-68): **delete it** — the per-habit field replaces it entirely, and per decision 2 there is no migration-forward, so no "keep both, deprecate one" step is needed. Deleting a field from a `@Serializable` class with `ignoreUnknownKeys = true` on decode (confirm this is the import-side config) is always safe for *old* files (the key is just ignored) — verify the decode `Json{}` instance used for import has `ignoreUnknownKeys = true` set (the `StreakDefHashTest`'s "lenient" `Json` at line 66 confirms the pattern exists somewhere; find the actual production decoder in `ExportImportRepository.kt`/`JsonUtils` and confirm).

**`data/ExportImportRepository.kt`:**
- `exportBackup()` (lines 111-233): delete the `journalQuestions = database.journalQuestionDao().getOrderedTexts()` line (120) and the `journalQuestions = journalQuestions` field in `Definitions(...)` (229) — both gone with the table. Add `journalQuestions = jsonToJournalQuestions(h.journalQuestionsJson)` (or equivalent decode) to the `HabitDef(...)` construction (after line 204). Add `qaJson = occ.qaJson?.takeIf { it.isNotBlank() }` to the `HabitLog(...)` construction (after line 147, mirroring `IntakeLog`'s identical line 167).
- `importAllData()` (lines 249+): delete the `journalQuestions = journalQuestionsFromTexts(...)` line (318) and whatever writes it to `journalQuestionDao` further down (not shown in the excerpt read, but grep confirmed `JournalQuestionRepository`/`Dao` usage in this file — locate and delete the write). Add `journalQuestionsJson = joinJournalQuestions(d.journalQuestions)` to the `Habit(...)` construction (mirrors line 281-285's pattern). Add `HabitOccurrence(..., qaJson = log.qaJson?.takeIf{...})` wherever `HabitLog`s are turned back into `HabitOccurrence` rows on import (locate the reverse of the export-side bucketing, likely nearby the `tasks`/`habits` reconstruction block).
- `journalQuestionsFromTexts`/`JournalQa` pure helpers (lines 759-800): `journalQuestionsFromTexts` (which builds `JournalQuestion` Room entities) is now dead — delete it, OR repurpose its dedupe/trim logic into a plain `List<String>` normalizer used by the per-habit editor (Phase 3) and the import path both. `JournalQa` (the `qa_json` codec, lines 777-800) is **unchanged and reused as-is** by the habit side — it's already table-agnostic (`encode(pairs: List<Pair<String,String>>): String` / `decode(jsonStr): List<Pair<String,String>>`), exactly the "mirror the existing pattern" the task description calls for.

**Hash test**: add a new `HabitJournalHashTest.kt` (mirroring `StreakDefHashTest.kt`'s exact shape, read in full for this plan) with the same three assertions: (a) a `journalQuestions`/`qaJson` content difference changes `ofDefinitions`/`ofDay`-equivalent hash; (b) the new fields' defaults serialize as ABSENT bytes; (c) a pre-this-round `Definitions`/`DayEntry` JSON (missing these keys entirely) decodes to the same defaults and hashes identically to an explicit-default instance — i.e. an existing non-Journal user's `definitionsHash` and every month's `contentHash` are provably unchanged by this round.

**Settings cleanup** (`ui/settings/JournalQuestionsSettingsScreen.kt` + its route):
- Delete `ui/settings/JournalQuestionsSettingsScreen.kt` entirely (both the `JournalQuestionsViewModel` and the composable — the whole file, confirmed self-contained, no other file defines symbols in it).
- `ui/MainActivity.kt`: delete the `composable("settings_journal_questions") { ... }` block (line 540-542) and the `import ... JournalQuestionsSettingsScreen` (line 70).
- `ui/settings/SettingsScreen.kt`: delete the `onOpenJournalQuestions` parameter (line 83), the `journalQuestionsViewModel: JournalQuestionsViewModel = hiltViewModel()` param (line 87 — confirm this ViewModel injection is actually used only for a badge/count on the settings row, not for anything else, before deleting), and the settings row that calls `onClick = onOpenJournalQuestions` (line 261) — read the surrounding ~20 lines in this phase to remove the whole row cleanly (icon, label, subtitle) rather than leaving a dangling half-row.
- Confirm no other file references `JournalQuestionsViewModel`/`JournalQuestionsSettingsScreen`/`onOpenJournalQuestions` after deletion (`grep -r` clean).

**Files touched**: `data/backup/BackupModel.kt`, `data/ExportImportRepository.kt`, `ui/settings/JournalQuestionsSettingsScreen.kt` (deleted), `ui/MainActivity.kt`, `ui/settings/SettingsScreen.kt`, new `HabitJournalHashTest.kt`.
**4-gate**: all four — this phase is the one most likely to leave an orphaned import (deleted-file references) that only shows up at compile time, so `testDebugUnitTest` + `compileDebugAndroidTestKotlin` are the fast-fail signals; run them first within this phase before the two assemble tasks.

---

### Phase 8 — Task C: Ongoing-habit card redesign + on-card "Mark as broken" + History/Stats button

**Current layout** (`RoutinesScreen.kt`'s `HabitCard`, lines 198-338), for an Ongoing habit specifically:
```
[IconTile] Title [~badge]                                    [⋮]
           description (optional)
           🔥 Start                                                    ← not started
   -- or --
           🔥 4 days   Best 5                                          ← running
```
Problems the user flagged: cramped/inconsistent vs. the "Next: <time>" subtitle line other types get, no on-card way to mark broken (buried in the 3-dot menu), no explicit history/stats affordance.

**Redesigned layout** (text sketch):
```
Not started:
[IconTile] Title [~badge]                          [🕐 History]  [⋮]
           description (optional)
           🔥 Start

Running:
[IconTile] Title [~badge]                          [🕐 History]  [⋮]
           description (optional)
           🔥 4 days   ·   Best 5              ...........   [✕]  ← "Mark as broken", danger-tinted
```
Concretely:
- **Header row** (`HabitCard`'s existing top `Row`, lines 214-294): add a `CircleIconButton(icon = DaybookIcons.Clock, contentDescription = "History and stats", onClick = onOpen, size = IconButtonSize.Sm.dp)` **only when `habit.isStreak`** (line ~292, right before the existing `MoreVert` button) — reuses the identical `onOpen` callback the whole card already uses (per C4/item-4's confirmation that this is "the same destination", not a new one), giving Ongoing habits a second, explicit, discoverable route to Detail alongside the existing whole-card tap. Non-Streak cards are unchanged (no new icon).
- **Status row for a running Ongoing habit** (currently lines 253-273): wrap the existing `Row` in a `Modifier.fillMaxWidth()` and change its arrangement so the day-count block stays leading and a new trailing danger-styled `CircleIconButton(icon = MI.Filled.Close, contentDescription = "Mark as broken", onClick = { confirmBroken = true }, style = CircleStyle.Danger, size = IconButtonSize.Sm.dp)` sits at the far end — i.e. insert `Spacer(Modifier.weight(1f))` between the "Best N" text and the new icon button, so the row reads `[flame][N days] · [muted "Best N"] .......... [✕]` — this is the exact `SpaceBetween`-via-weighted-spacer idiom `DayOfWeekSelector`/other rows in this codebase already use, not a new layout primitive. Replace the "· " separator between the day-count and "Best" with a small `Spacer(Modifier.width(8.dp))` as today (unchanged) — only the trailing icon is new.
- **`confirmBroken` dialog** (lines 318-337): unchanged content/copy, just now triggered from the new inline icon button instead of (in addition to, per C3) the 3-dot menu entry.
- **3-dot menu** (`BottomSheetMenu`'s `actions`, lines 299-310): **delete** the `if (habit.isStreak && habit.streakStartedAt != null) add(SheetAction(...,"Mark as broken"...))` block (lines 302-304) per C3 — Edit / Archive / Delete remain, same as every other habit type's menu.
- **Not-started row** (`TextLink("Start", ...)`, lines 243-251): unchanged shape, but its `onClick` now opens the date-picker flow (Phase 9) instead of calling `onStartStreak` directly.

**`HabitCard`'s parameter list**: `onOpenDetailFromHistory: () -> Unit` isn't actually a new distinct callback — reuse the existing `onOpen` param for the new History icon button (see above), so **no new parameter is needed** on `HabitCard` for item 4. Item 1/2 need `onMarkBroken` (already exists, param unchanged) just called from a new place; item 3 (Phase 9) needs the `onStartStreak` callback's signature to grow from `() -> Unit` to `(chosenDate: LocalDate) -> Unit` (or keep it `() -> Unit` and let the card itself own the date-picker dialog state + call a new `onStartStreakAt: (Long) -> Unit`).

**Files touched**: `ui/routines/RoutinesScreen.kt` (HabitCard only).
**Tests**: a Compose UI test confirming (a) the History icon renders only for `isStreak` habits, (b) the Mark-as-broken icon renders only when `isStreak && streakStartedAt != null`, (c) the 3-dot menu for a running Ongoing habit no longer contains a "Mark as broken" item.
**4-gate**: all four.

---

### Phase 9 — Task C: backdated "Start" via date picker

**`ui/TimePickerComponents.kt`**: extend `DaybookDatePickerDialog` (lines 185-215ish) with an optional `maxDate: LocalDate? = null` param; when set, construct `rememberDatePickerState`'s `selectableDates` argument to reject any date after `maxDate` (Material3 `SelectableDates` interface — implement `isSelectableDate(utcTimeMillis: Long): Boolean` comparing against `maxDate`, and `isSelectableYear` similarly if the API requires both). The two existing call sites in `SettingsScreen.kt` (897, 908) are unaffected (they don't pass the new param, defaulting to `null` = no restriction, unchanged behaviour).

**`ui/routines/RoutinesScreen.kt`**: `HabitCard` gains local dialog state `var showStartPicker by remember { mutableStateOf(false) }`; the `TextLink("Start", onClick = { showStartPicker = true }, ...)` (was `onClick = onStartStreak`); render:
```kotlin
if (showStartPicker) {
    DaybookDatePickerDialog(
        initial = LocalDate.now(),
        maxDate = LocalDate.now(),
        onDismiss = { showStartPicker = false },
        onConfirm = { picked ->
            showStartPicker = false
            onStartStreakAt(picked.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    )
}
```

**`ui/routines/RoutinesViewModel.kt`**: change `fun startStreak(id: String)` to `fun startStreak(id: String, atMillis: Long = System.currentTimeMillis()) = viewModelScope.launch { habitRepository.startStreak(id, atMillis) }` — `HabitRepository.startStreak(id, nowMillis)` (`HabitRepository.kt:51-52`) already accepts this, **zero repository/DAO change needed** (confirms C2's claim). `RoutinesScreen.kt`'s existing `onStartStreak = { viewModel.startStreak(habit.id) }` (line 117) becomes `onStartStreakAt = { millis -> viewModel.startStreak(habit.id, millis) }`.

**Validation**: since `maxDate = LocalDate.now()` structurally prevents picking a future date, no additional runtime guard is needed in `startStreak`/`HabitRepository` — but recommend adding one anyway as defense-in-depth (`atMillis.coerceAtMost(System.currentTimeMillis())` inside `HabitRepository.startStreak` or `RoutinesViewModel.startStreak`), matching the codebase's general "pure guard function, unit-tested" style (e.g. a one-line `fun clampStreakStart(picked: Long, now: Long): Long = minOf(picked, now)`).

**Files touched**: `ui/TimePickerComponents.kt` (DaybookDatePickerDialog), `ui/routines/RoutinesScreen.kt` (HabitCard), `ui/routines/RoutinesViewModel.kt`.
**Tests**: a pure test for `clampStreakStart`; a Compose UI test opening the picker, selecting a past date, and asserting `onStartStreakAt` fires with that date's start-of-day millis; confirm `daysSince` produces the expected inclusive count for a 3-days-ago backdated start (extend `OngoingStreak`'s existing test file if one exists, or add one).
**4-gate**: all four.

---

### Final Phase — no version bump, regression doc, release APK

- **Do NOT touch `versionCode`/`versionName`** in `app/build.gradle` (stays `13` / `"0.5.5"`, per §0's correction) — update only the inline comment at those two lines (currently `"v0.5.5 — sign-in gate bottom bar, first-login name skip, Ongoing habit"`) to append this round's summary, e.g. `"...; journal-as-habit + button fix + ongoing-habit UI"`, so the comment stays an accurate changelog breadcrumb without implying a version change.
- Write `JOURNAL_HABIT_REGRESSION.md` at repo root — a device watch-list (no phone attached during this build) covering: (1) a Journal-type habit's notification fires, taps into the chat, Skip/Snooze work without RemoteInput; (2) the chat's auto-scroll and send-disabled-on-blank behave on a real keyboard/IME; (3) backgrounding mid-chat and reopening resumes at the right question; (4) tapping a resolved Journal entry from Detail History and from Today both open the plain edit-form, never the chat; (5) an Ongoing habit's new on-card History icon and Mark-as-broken icon are tappable at real touch-target size and don't accidentally trigger the whole-card tap underneath (nested-clickable hit-testing); (6) the backdated date picker's max-date restriction actually greys out future dates on a real device (Material3 `SelectableDates` behaviour can differ subtly from unit-test expectations); (7) a real device restoring an ancient (pre-this-round) backup file with `TaskType: "JOURNAL"` entries imports without crashing and those entries land as CUSTOM per Phase 2's remap; (8) push/pull a Journal-habit definition through the cloud sync path once (if a second device/account is available) to confirm `journalQuestionsJson`/`qaJson` round-trip; (9) confirm a non-Journal, non-Ongoing existing user's cloud `definitionsHash` does NOT show a spurious re-push after installing this build (the core Risk register concern, below) — check `MonthHashDiffTest`-style logs/behaviour on a real account if one is available, otherwise flag as unverified-on-device.
- Build `Daybook-v0.5.5-journal-habit-release.apk` at repo root — this plan's final deliverable is the plan itself; building/signing/delivering the APK is the parent/implementer's job once every phase's 4-gate is green, using the existing release signing config (`RELEASE_SIGNING.md`, `assembleRelease`, real keystore).

---

## §3 — Risk register

1. **`definitionsHash`/`ContentHash` churn for non-Journal, non-Ongoing users** — the single most important thing to get right. Every new field (`HabitDef.journalQuestions`, `HabitLog.qaJson`, `Habit.journalQuestionsJson` at the Room layer) must be optional-with-a-serialization-absent-default exactly like the existing `streakStartedAt`/`streakLongest`/`promptMessage`/`motivation` precedents. Concretely: (a) `HabitDef.journalQuestions` needs `@EncodeDefault(EncodeDefault.Mode.NEVER)` (its default, `emptyList()`, is non-null and would otherwise still serialize under the project's `encodeDefaults = true` canonical-hash config — exactly the reasoning `StreakDefHashTest`/`JournalV2HashTest` already document); (b) `HabitLog.qaJson`'s default (`null`) does NOT need the annotation (follow `IntakeLog.qaJson`'s un-annotated precedent, since `explicitNulls = false` already omits nulls) — **do not over-apply `@EncodeDefault(NEVER)` to a nullable field**, it's specifically for non-null defaults. Ship `HabitJournalHashTest.kt` (Phase 7) mirroring `StreakDefHashTest.kt`'s three-part shape before considering Phase 7 done, and treat a failing/missing version of assertion (b) — "defaults serialize as ABSENT" — as a blocking bug, not a nice-to-have, since it's the exact assertion that protects existing users' sync traffic.

2. **The migration's destructive delete hitting the wrong rows.** `MIGRATION_16_17`'s JOURNAL-purge is scoped by a `type = 'JOURNAL'` subquery on `food_med_tasks` — verify with the `MigrationTest` (Phase 1) using a *populated* table containing all four `TaskType` values plus multiple tasks per type, asserting row counts before/after precisely (not just "some rows survived") for FOOD/MED/CUSTOM (unchanged count) vs JOURNAL (zero remaining, including children). Also verify deletion **order** (events → occurrences → tasks) against the confirmed absence of `@ForeignKey`/cascade on these entities — reversing the order, or trusting a cascade that doesn't exist, silently orphans rows that then show up nowhere in the UI (invisible data leak, not a crash) exactly as `HabitEvent.itemId`'s existing "orphaned rows keep null" comment warns can happen elsewhere in this codebase.

3. **`TaskType.JOURNAL` dead-enum-value discipline (B3).** Every place `TaskType.entries` is iterated for user-facing choice (confirmed: `FoodMedForm.kt:146`) must filter `JOURNAL` out — miss one and the retired Type chip silently reappears in the UI as a selectable option leading nowhere useful (since the whole downstream JOURNAL-specific rendering/scheduling path is being removed by this round). Grep `TaskType.entries` and `TaskType.JOURNAL` across the full tree as a final check before considering Phase 2/7 done, not just the one call site already found.

4. **Notification channel/versioning** — confirmed NOT an issue (Phase 2 justifies reusing `CHANNEL_HABITS`), but flag for the implementer: if a future reviewer decides Habit-Journal *should* get its own channel after all (e.g. to let users independently mute Journal reminders vs. other habit reminders), that's a **channel ID change**, which Android treats as immutable-per-install — getting it wrong now versus later is a one-way door on a shipped channel ID. Recorded here so the "keep using CHANNEL_HABITS" call isn't silently revisited without noticing the cost.

5. **Detail/Home wiring completeness — the "broken Complete/Skip card" failure mode.** The biggest functional risk in Phase 6: any render path that builds a `HomeItem`/Detail row from a `HabitOccurrence` generically (not habit-type-aware) will, by default, offer Complete/Skip/undo controls that are semantically wrong for a Journal-type habit (there is no "just tick it off" — it always needs the chat/edit-form). Audit every `HomeItem` construction site and every Detail row-click resolver for an *implicit* "else branch" that a `HabitType.JOURNAL` occurrence could fall into unnoticed — the plan's Phase 6 lists the known branch points (`homeItemVisible`, the card's trailing-action `when`, `editEntry`, `HistoryTab`'s row-click `when`) but a final `grep -n "HabitType\." ui/` sweep before closing Phase 6 is cheap insurance against a missed branch.

6. **Streak-fold `LOGGED` recognition (B5/B6)** — if `calculateHabitStreaks`/`computeStats` currently hard-codes `COMPLETED` as the only "did it" status for habits (plausible, since no habit occurrence has ever been `LOGGED` before this round), Journal habits will silently show a permanent 0-day streak despite being answered every day — a correctness bug that produces no crash and no obviously-wrong UI (a 0 looks like a valid, if sad, streak), making it easy to ship unnoticed. Explicitly read the streak-fold source in Phase 6 (not just infer from the DAO projection's generic typing) and add the targeted unit test called out there.

7. **Chat draft-persistence race (B1).** `saveJournalDraft` writes `qa_json` on every send while the occurrence is still `PENDING`; a concurrent `syncAll()`/`skipStaleForHabit` sweep (the same `STALE_AFTER_MS` auto-skip logic that already exists for ordinary habits/food-med) could flip a mid-chat occurrence to `SKIPPED` out from under the user if they leave a chat open past the stale window. This is a pre-existing class of race for any long-lived open reminder screen (the old FoodMed-JOURNAL page had the same exposure), so it's not a new risk introduced by this round, but Phase 4's ViewModel should defensively re-check `occurrence.status` before the final `logHabitJournal` write and surface a "this reminder expired while you were answering" state rather than silently succeeding against a row that moved on — worth a one-line defensive check even though it mirrors an accepted pre-existing gap.

---

## §4 — File-touch index

**New files**: `ui/journal/HabitJournalChatScreen.kt`, `ui/journal/HabitJournalChatViewModel.kt`, `ui/journal/HabitJournalEditScreen.kt`, `ui/journal/HabitJournalEditViewModel.kt`, `app/schemas/.../17.json`, `src/test/.../MigrationTest` (16→17 case, likely appended to an existing `MigrationTest.kt`), `src/test/.../HabitJournalHashTest.kt`, `src/test/.../ChatFlowTest.kt` (or similarly named pure-logic test for Phase 4).

**Deleted files**: `ui/settings/JournalQuestionsSettingsScreen.kt`, `data/JournalQuestionRepository.kt`, `data/local/JournalQuestionDao.kt`.

**Modified — Task A**: `ui/components/Components.kt`.

**Modified — Task B**: `data/model/DataModel.kt`, `data/local/AppDatabase.kt`, `data/local/Migrations.kt`, `data/local/HabitOccurrenceDao.kt`, `di/DatabaseModule.kt`, `data/OccurrenceScheduler.kt`, `util/notification/NotificationUtils.kt`, `util/alarm/AlarmReceiver.kt`, `ui/foodmed/FoodMedForm.kt`, `data/ExportImportRepository.kt`, `ui/routines/HabitForm.kt`, `ui/routines/AddHabitViewModel.kt`, `ui/routines/EditHabitScreen.kt` (or wherever habit-edit hydration lives — locate exact file), `ui/MainActivity.kt`, `ui/detail/DetailViewModel.kt`, `ui/detail/DetailScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `util/streak/StreakCalculator.kt` (verify exact filename — the module computing `calculateHabitStreaks`), `data/backup/BackupModel.kt`, `ui/settings/SettingsScreen.kt`.

**Modified — Task C**: `ui/TimePickerComponents.kt`, `ui/routines/RoutinesScreen.kt`, `ui/routines/RoutinesViewModel.kt`.

**Modified — version/docs**: `app/build.gradle` (comment only, NOT the version fields), new `JOURNAL_HABIT_REGRESSION.md`.

---

## §5 — Verified line references (as read for this plan; re-confirm at implementation time if any file has moved on)

- `data/model/DataModel.kt`: `Habit` 13-44; `HabitOccurrence` 57-70; `HabitEvent` 84-93; `FoodMedTask` 97-126; `FoodMedOccurrence` 136-163; `ColorTag` 244-249; `TaskType` 252; `RedFlag` 258-265; `HabitType` 275; `CustomCategory` 283-286; `CustomPrompt` 294-297; `JournalQuestion` 310-316; `Occurrence.Status` 318-323; `Event.Action` 325-331.
- `ui/journal/JournalScreen.kt`: whole file (182 lines) — the stepper UI being replaced by the chat pattern for the habit side (FoodMed-side JournalScreen itself is untouched, only its habit-side analog is new).
- `ui/journal/JournalViewModel.kt`: whole file (206 lines) — `journalQaPayload` 36-42, `journalIsEdit` 48-49, `clampIndex`/`isLastStep` 56-61, init 121-171, `save()` 185-205.
- `ui/foodmed/FoodMedForm.kt`: `TaskType` chips 144-164; Category row 218-277 (Add button 250-260); Prompt row 278-322 (Add button 307-310); save-enabled 393-397.
- `ui/components/Components.kt`: `PrimaryButton` 491-538; `GhostButton` 540-583 (root cause: no horizontal padding on the inner Row, 568-582).
- `ui/theme/Tokens.kt`: `AppShapes` 134-147 (`button = RoundedCornerShape(12.dp)`, line 136).
- `data/OccurrenceScheduler.kt`: `isNoScheduleHabit` 46; `syncHabitInternal` 224-271; `armNextHabitInternal` 275-290; `logFoodMed` 402-437; `logJournal` 442-464; `isJournalOccurrence` 390-392; `backfillHabit` 561-594; `backfillFoodMed` 599-670; `canBackfill` 690-701; `isFoodMedEdit` 716-717.
- `data/local/HabitOccurrenceDao.kt`: whole file (169 lines) — no `qa_json`/journal methods exist yet (confirmed by full read).
- `data/local/FoodMedOccurrenceDao.kt`: `logJournalResponse`/`editJournalResponse` 66-94 (the DAO-level mirror template).
- `util/notification/NotificationUtils.kt`: `smallIconFor` 287-291; `showHabitNotification` 293-312; `showFoodMedNotification` (isJournal branch) 319-356; `contentIntent` 378-399; `CHANNEL_HABITS`/`CHANNEL_FOOD_MED` 54-55.
- `ui/MainActivity.kt`: deep-link resolver 368-379; `goJournal`/`goJournalBackfill` 434-437; `journal/{arg0}/{slotMillis}` route 592-597; `settings_journal_questions` route 540-542 (to delete).
- `ui/detail/DetailScreen.kt`: `HistoryTab` 200-300 (row-click `when` 272-278); `StatsTab` 302-365 (`isOngoing` gates 318, 349); `journalRowPairs` 387-388; `TimelineRow` 390+.
- `ui/detail/DetailViewModel.kt`: `_isJournal`/`isJournal` 111-112 (habit-side needs a parallel `_isHabitJournal`); `_isOngoing`/`isOngoing` 116-117; task-load `_isJournal.value = ...` 326.
- `ui/home/HomeViewModel.kt`: `HomeItem` 52-95 (`isJournal` 74); `homeItemVisible` 105-114; `ReminderFilter` 98; item-building `isJournal = t.type == TaskType.JOURNAL` at 525, 602.
- `ui/home/HomeScreen.kt`: `openJournal`/routing 183-208; `onOpenJournal`/`editEntry` 391-398; trailing action 504-505; inline-reply gate 533; undo-vs-edit 603.
- `ui/routines/HabitForm.kt`: `HabitFormState` 33-52; Type row 118-141; times gate 143-152; Advanced groups 154-232 (Reminder text 193-202 gated `!= STREAK`; Active days/Snooze 211-231 gated `!= STREAK`); save-enabled 260-265; `anyAdvancedFieldNonDefault` 275-283.
- `ui/routines/AddHabitViewModel.kt`: whole file (190 lines) — `keepStreakColumns` 24-31; `saveHabit` 90-137; `clearForm` 158-174.
- `ui/routines/RoutinesScreen.kt`: `HabitCard` 198-338 (header row 214-294; not-started row 243-251; running row 253-273; menu actions 296-311; confirmBroken dialog 318-337); filter facets 176-196.
- `ui/routines/RoutinesViewModel.kt`: `RoutineItem` 42-61; `startStreak`/`markStreakBroken` 254-258; `habits` combine (streakDays computation) 153-187.
- `data/HabitRepository.kt`: `startStreak(id, nowMillis=now)` 51-52; `markStreakBroken` 58-64 (no change needed for Task C — confirms C2's "zero repo change" claim).
- `util/streak/OngoingStreak.kt`: `daysSince` 14-18 (whole file, 18 lines) — confirms C2's math.
- `ui/TimePickerComponents.kt`: `DaybookDatePickerDialog` 185-215ish.
- `ui/settings/SettingsScreen.kt`: `DaybookDatePickerDialog` usages 897, 908; `onOpenJournalQuestions` 83, row at 261; various `GhostButton` call sites (all `fillMaxWidth()`-safe) 502, 777, 857, 863, 1024, 1030, 1069.
- `ui/settings/JournalQuestionsSettingsScreen.kt`: whole file (269 lines) — the add/edit/delete/reorder template to port into `HabitForm.kt`'s per-item Questions editor, then delete.
- `data/JournalQuestionRepository.kt`: whole file (93 lines) — `add`/`edit`/`delete`/`move`/`canDelete`/`moveInList`/`normaliseQuestionPositions` — pure helpers worth relocating rather than reimplementing.
- `data/local/JournalQuestionDao.kt`: whole file (43 lines) — to delete.
- `data/local/AppDatabase.kt`: `entities=[...]` 18-29 (`version = 16` → 17); DAO list 32-41.
- `data/local/Migrations.kt`: D5 journal-wipe block (top of file as read); `MIGRATION_14_15` (STREAK columns, additive-only template); `MIGRATION_15_16` (20-column additive template + nullable no-default columns template).
- `data/backup/BackupModel.kt`: whole file (189 lines) — `Definitions` 53-69 (`journalQuestions` 62-68, to delete); `HabitDef` 71-105 (`streakStartedAt`/`streakLongest`/`promptMessage`/`motivation` as the `@EncodeDefault(NEVER)` templates); `IntakeReminderDef` 107-135; `HabitLog` 147-156; `IntakeLog` 158-181 (`qaJson` 177-180, the un-annotated-nullable-default template).
- `data/ExportImportRepository.kt`: `exportBackup` 111-233 (habit/task/day construction); `importAllData` 249-319+; `journalQuestionsFromTexts` 766-770; `JournalQa` 777-800; `defsDelta` 754-757.
- `src/test/.../data/sync/StreakDefHashTest.kt`: whole file (84 lines) — the exact 3-part hash-safety test template to mirror for `HabitJournalHashTest`.
- `app/build.gradle`: `versionCode = 13` / `versionName = "0.5.5"` at lines 31-32 (comment-only update, per §0 correction).
