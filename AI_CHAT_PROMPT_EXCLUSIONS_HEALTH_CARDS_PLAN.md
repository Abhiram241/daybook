# Plan: Chat custom instructions · Hide entries from AI · Health card alignment

**Date:** 14 Sep 2026 · **Base:** working tree at v0.7.1 (build 38), DB version 28
**Status:** plan only, no code changed. Every open choice has been decided below (you asked for no
questions). Each decision is marked **[Decided]** so it can be overridden before building.

Three features:
1. **Chat custom instructions**: a separate "custom instructions" box for Chat, next to the existing
   one for AI Summary.
2. **Hide from AI**: pick specific habits, reminders or individual entries (for example one journal
   entry) to keep out of the AI. There are two separate lists, one for **Summary** and one for
   **Chat**.
3. **Health card alignment**: a card with one value (Steps 62, SpO₂ 95%) should line up with a card
   with two values (Active/Total, Average/Range).

---

## 0. Things that need your sign-off (hard constraints from the round rules)

| # | What | Why it needs sign-off |
|---|---|---|
| S1 | **Room migration 28 → 29**: 1 new column on `app_settings` plus 1 new table `ai_exclusions` | Schema change. It only adds things: no existing row changes and nothing is dropped. |
| S2 | **No sync or backup change**: the new column and table are device-only, like every other AI setting | Nothing is added to `BackupModel`, `ContentHash` or `CloudSyncRepository.DATA_TABLES`. Firestore docs are untouched. |
| S3 | **Existing "Custom instructions" text is copied** into the new Chat field during migration | Chat keeps behaving exactly as today until you edit the Chat box. |

No git commit/push and no App Distribution upload. The deliverable is a signed release APK. The
versionCode stays at 38 unless bumped, so **uninstall the old app before sideloading**. Uninstalling
loses local data, so sign in first so the cloud has a copy.

---

## 1. Chat custom instructions

### 1.1 What the user gets
In Settings → Daily Report AI:
- **"Summary instructions"**: the existing box, renamed. It now applies **only** to the AI Summary.
- **"Chat instructions"**: a new box placed inside the "Chat context" section, above the date range.
  Same look, 2,000-character limit, saved when the field loses focus.

**[Decided]** The two boxes are fully independent: editing one never changes the other.
**[Decided]** Migration copies today's text into the Chat box, so nothing changes for the user on
upgrade.

### 1.2 Data
- `AppSettings` (`data/model/DataModel.kt:327` area): add
  `@ColumnInfo(name = "ai_chat_meta_prompt", defaultValue = "") val aiChatMetaPrompt: String = ""`.
- `AppSettingsDao`: `@Query("UPDATE app_settings SET ai_chat_meta_prompt = :v WHERE id = 1") suspend fun updateAiChatMetaPrompt(v: String)`.
- `AppSettingsRepository`: `suspend fun setAiChatMetaPrompt(v: String) { ensureRow(); … }`.
- `MIGRATION_28_29` (`data/local/Migrations.kt`, shared with §2):
  ```sql
  ALTER TABLE app_settings ADD COLUMN ai_chat_meta_prompt TEXT NOT NULL DEFAULT '';
  UPDATE app_settings SET ai_chat_meta_prompt = ai_meta_prompt;
  ```

### 1.3 Code changes
| File | Change |
|---|---|
| `ui/settings/DailyReportAiSettingsViewModel.kt` | Add `chatMetaPrompt` StateFlow (via `col { it.aiChatMetaPrompt }`) and `setChatMetaPrompt(v)`. |
| `ui/settings/DailyReportAiSettingsScreen.kt` | Rename the section header to "Summary instructions" with subtitle "Added to every AI Summary request." Change `supportingText` to "Sent with every AI Summary." In the "Chat context" section, add a second `DaybookTextField` with its own `chatPromptDraft` / `chatPromptWasFocused` state, the same save-on-focus-loss pattern (lines 55-56, 107-110), the placeholder "e.g. Answer like a friendly coach. Keep it under 5 sentences." and the supporting text "Sent at the start of every chat." |
| `ui/report/DailyReportViewModel.kt` `openChat()` (`:281-370`) | Use `settings.aiChatMetaPrompt` instead of `settings.aiMetaPrompt`. **Move it out of the data block**: pass `metaPrompt = ""` to `buildMultiDayReportPrompt` and put the instructions in the system message as a labelled paragraph **after** the built-in rules and **before** the data (see 1.4). |
| `ui/report/DailyReportViewModel.kt` `generate()` | Unchanged; it keeps `aiMetaPrompt`. The summary staleness fingerprint (`settingsFingerprint`) stays summary-only, so editing chat instructions never marks a cached summary out of date. |

### 1.4 System message order for chat
```
<built-in rules: casual assistant, don't dump data on "hi", say when data is missing>

The user's own instructions for this chat (follow them unless they conflict with the rules above):
<aiChatMetaPrompt>                       ← omitted entirely when blank

<clamp note if the range was shortened>

<the day-by-day data>
```
**[Decided]** "Unless they conflict with the rules above": the user can change tone and length but
can't make the model invent data.

### 1.5 Chat edge cases
- If the instructions change while a chat is open, they take effect on the **next** chat opened. The
  system message is built once in `openChat()`; this matches how the categories and range work today.

---

## 2. Hide from AI (separate lists for Summary and Chat)

### 2.1 What the user gets
In Settings → Daily Report AI, two new rows:
- **"Hidden from AI Summary"**, with subtitle "3 items hidden" or "Nothing hidden"
- **"Hidden from Chat"**, with the same subtitle style

Each row opens the same picker screen (`AiExclusionsScreen`) for that list:

```
┌ Hidden from Chat ─────────────────────────────┐
│ Search habits & reminders…                    │
│                                               │
│ HABITS                                        │
│ ▸ Morning journal        [Hide all] ◻         │
│ ▸ Gym                                ◻        │
│ INTAKE                                        │
│ ▸ Breakfast                          ◻        │
│ ▸ Medication (Mesalamine)            ☑ all    │
└───────────────────────────────────────────────┘
tap "Morning journal" ▸ expands to its entries:
│   ☑ Sat 12 Sep · 9:00 PM  "Felt anxious about…"│
│   ◻ Fri 11 Sep · 9:00 PM  "Good day, walked…"  │
│   ◻ Thu 10 Sep · 9:00 PM  "Skipped"            │
│   [Load older entries]                         │
```
- **Hide all** (a switch on the item row): the whole habit or reminder, **including future entries**,
  never reaches that AI feature.
- **An individual entry** (checkbox): only that one logged entry is hidden.
- If "Hide all" is on, the entry checkboxes are shown checked and disabled, with the caption
  "Everything from this item is hidden".
- A "Copy from Summary list" / "Copy from Chat list" link at the top saves re-picking.

**[Decided] Scope:** habits (all types, including Journal) **and** intake reminders
(food/med/custom). Journal entries are the main use, but a medication log is just as sensitive. Workout
and health have their own on/off category switches already, so no per-entry hiding for those.
**[Decided] Entries listed:** only entries that have something (Done / Logged / Skipped, or journal
answers), newest first, 30 per page, via the existing paged queries
`HabitOccurrenceDao.getTerminalPageForHabit` and `FoodMedOccurrenceDao.getTerminalPageForTask`.
**[Decided] Preview text:** the first answer of a journal entry, the logged text for intake, or the
status word for plain habits. Previews are cut to 40 characters.
**[Decided]** Archived items still appear, in a collapsed "Archived" group at the bottom.
**[Decided] Report screen hint:** in the AI panel, under the Generate/Chat buttons, a caption
"2 entries hidden from AI · Manage", linking to the matching list. It only appears when something
hidden actually falls in the day or range being sent.
**[Decided]** The Daily Report page itself still shows everything. Hiding only affects what is sent
to the AI.

### 2.2 Data (device-only)
New entity `data/model/AiExclusion.kt`:
```kotlin
@Entity(tableName = "ai_exclusions", primaryKeys = ["scope", "kind", "target_id"])
data class AiExclusion(
    @ColumnInfo(name = "scope") val scope: String,        // "SUMMARY" | "CHAT"
    @ColumnInfo(name = "kind") val kind: String,          // "HABIT" | "TASK" | "HABIT_ENTRY" | "TASK_ENTRY"
    @ColumnInfo(name = "target_id") val targetId: String, // habit/task id, or occurrence id "itemId:millis"
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```
The occurrence ID format `"$itemId:$millis"` is deterministic and is the same on every device and
across restores, so an entry exclusion survives a re-sync of that month.

`MIGRATION_28_29` (continued):
```sql
CREATE TABLE IF NOT EXISTS ai_exclusions (
  scope TEXT NOT NULL, kind TEXT NOT NULL, target_id TEXT NOT NULL, created_at INTEGER NOT NULL,
  PRIMARY KEY(scope, kind, target_id));
```
- `AppDatabase`: add the entity, bump `version = 29`, add `abstract fun aiExclusionDao()`.
- `DatabaseModule`: add `MIGRATION_28_29` to `addMigrations(...)`.
- Export `schemas/.../29.json`. Add a `MigrationTest` case for 28→29: the column is copied and the
  table exists.
- **Do NOT** add `ai_exclusions` to `CloudSyncRepository.DATA_TABLES`, `BackupModel` or `ContentHash`.
- **Sign-out:** add `database.aiExclusionDao().deleteAll()` inside `wipeAllLocalData()`'s transaction
  (`CloudSyncRepository.kt:279-288`). Exclusions point at one account's entries.
- **Deleting a habit or reminder:** when a habit/task is deleted (`RoutinesViewModel.deleteHabit`,
  `AddHabitViewModel.deleteHabit`, `FoodMedViewModel.deleteItem`, `AddFoodMedViewModel.deleteItem`),
  also run `aiExclusionDao().deleteForItem(id)`. That removes `kind in (HABIT, TASK)` rows with
  `target_id = id` and entry rows with `target_id LIKE id || ':%'`.

`data/local/AiExclusionDao.kt`:
```kotlin
@Query("SELECT * FROM ai_exclusions WHERE scope = :scope") fun observe(scope: String): Flow<List<AiExclusion>>
@Query("SELECT * FROM ai_exclusions WHERE scope = :scope") suspend fun get(scope: String): List<AiExclusion>
@Insert(onConflict = REPLACE) suspend fun upsert(row: AiExclusion)
@Query("DELETE FROM ai_exclusions WHERE scope = :scope AND kind = :kind AND target_id = :id") suspend fun remove(scope: String, kind: String, id: String)
@Query("DELETE FROM ai_exclusions WHERE target_id = :id OR target_id LIKE :id || ':%'") suspend fun deleteForItem(id: String)
@Query("INSERT OR IGNORE INTO ai_exclusions SELECT :to, kind, target_id, :now FROM ai_exclusions WHERE scope = :from") suspend fun copyScope(from: String, to: String, now: Long)
@Query("DELETE FROM ai_exclusions") suspend fun deleteAll()
```

### 2.3 Domain helper (pure, unit-tested)
`data/AiExclusions.kt`:
```kotlin
enum class AiScope { SUMMARY, CHAT }
data class AiExclusionSet(val habitIds: Set<String>, val taskIds: Set<String>, val entryIds: Set<String>) {
    fun hidesTodo(row: TodoEntryRow) = row.habitId in habitIds || row.id in entryIds
    fun hidesIntake(row: IntakeEntryRow) = row.taskId in taskIds || row.id in entryIds
    val isEmpty get() = habitIds.isEmpty() && taskIds.isEmpty() && entryIds.isEmpty()
}
fun List<AiExclusion>.toSet(): AiExclusionSet
fun DailyReportData.withoutExcluded(x: AiExclusionSet): Pair<DailyReportData, Int>  // filtered copy + hidden count
fun AiExclusionSet.fingerprint(): String  // sorted join, for summary staleness
```

### 2.4 Wiring changes
| File | Change |
|---|---|
| `data/DailyReportRepository.kt` | Add `habitId: String` to `TodoEntryRow` and `taskId: String` to `IntakeEntryRow` (filled from `occ.habitId` / `occ.taskId` in `buildTodoRows`/`buildIntakeRows`). Report screen rendering is unchanged. |
| `ui/report/DailyReportViewModel.kt` `generate()` | Read `aiExclusionDao().get("SUMMARY")`, apply `report.withoutExcluded(...)` **before** `buildDailyReportPrompt`. If everything in the enabled categories was hidden, still send the prompt; the sections just say "No … logged". |
| `ui/report/DailyReportViewModel.kt` `openChat()` | Read the `"CHAT"` list and apply `withoutExcluded` to **each day** returned by `buildChatContext` before `buildMultiDayReportPrompt`. |
| Summary staleness | `settingsFingerprint(metaPrompt, categoriesCsv)` becomes `settingsFingerprint(metaPrompt, categoriesCsv, exclusionFingerprint)`. Hiding or un-hiding something for a day then shows the existing "settings changed since this summary" hint. Older rows keep working because a mismatch already shows the hint. |
| `DailyReportUiState` | Add `hiddenFromSummaryCount: Int` and `hiddenFromChatCount: Int`, computed in `uiState` from the selected day's report plus both lists (chat count uses the selected day only, for the caption). |
| `ui/report/DailyReportScreen.kt` AI panel | Show the caption from §2.1 when the count > 0, with a "Manage" `TextLink` → `settings_ai_exclusions/{scope}`. |
| New `ui/settings/AiExclusionsViewModel.kt` | `SavedStateHandle` arg `scope`. Exposes: the item list (habits + tasks, active then archived) with `hiddenAll`; per-item expanded entry pages (lazy load, 30 at a time); `toggleItem`, `toggleEntry`, `copyFrom(otherScope)`, a search query. All writes go through `safeLaunch`, and a failure shows a snackbar message. |
| New `ui/settings/AiExclusionsScreen.kt` | `SettingsSubScreen` + `LazyColumn` with sections Habits / Intake / Archived, expandable item rows, checkbox entry rows (48dp touch targets), a search field (with `imePadding`), and an empty state "No habits or reminders yet". |
| `ui/settings/DailyReportAiSettingsScreen.kt` | New "Privacy" section at the bottom with two `SettingsRow`s showing live counts (from `observe(scope)` in `DailyReportAiSettingsViewModel`). |
| `ui/MainActivity.kt` | Register `composable("settings_ai_exclusions/{scope}")` next to `settings_daily_report_ai` (`:929`). |

### 2.5 Entry edge cases
- A **hidden entry is edited later**: it stays hidden, because its ID doesn't change.
- An **undo back to pending** keeps the exclusion row. Harmless: pending rows aren't sent as content
  anyway.
- A **backfill that creates the row later** gets the same deterministic ID, so an exclusion made
  before that still applies.
- A **month was evicted**: the picker only lists entries stored on the phone. It shows "Older entries
  are in the cloud — open that month on Today to load them" under "Load older" when nothing more
  comes back but the user is signed in.

---

## 3. Health card alignment (Beast Mode → Health tab, and the Daily Report Health section)

### 3.1 What's wrong today (from the screenshot and `HealthTabScreen.kt:310-504`)
1. **Label and number don't share a baseline.** `MetricRow` is a `Row` with no vertical alignment
   (`:500`). The 16sp label sits at the top and the 20sp bold number sits lower, so "Steps" floats
   above "62" and "SpO₂" above "95%".
2. **One-value cards look half-empty.** A card with one row (Steps without distance, Oxygen without a
   range, Weight, Hydration) puts its single row right under the title and leaves a gap below. Its
   neighbour's rows fill the card, so the values in a grid row don't line up.
3. **Card heights are forced to 148dp** (`HealthTileHeight`, `:70`). That leaves empty space on short
   cards and **clips** content when the phone's font size is large.
4. **The title is cut off**: "Steps & dist…" (`:316`).
5. **Range mode adds "avg" to every value** ("8,412 avg", "72 bpm avg"). The values get longer and wrap
   or ellipsize differently from Day mode.
6. **The Nutrition card nests three full-width rows in one `Row`** (`:441-445`), so they overlap.
7. **Heart-rate range has no unit** ("74–85") while the average says "79 bpm".
8. The **Daily Report Health section** (`DailyReportScreen.kt:262-291`) uses a different layout again:
   a body-size label with a small grey caption value, so the same numbers look different on two screens.

### 3.2 New layout rules [Decided]
- **One shared card:** `ui/workout/health/HealthMetricCard.kt` (moved out of `HealthTabScreen`),
  also used by the Daily Report Health section.
- **Header:** icon + title on one line, `maxLines = 2` allowed. Titles are shortened so none need
  ellipsis at default font size: "Activity", "Calories", "Heart rate", "Sleep", "Oxygen", "Weight",
  "Hydration", "Nutrition".
- **Values sit at the bottom of the card**: `Column(Modifier.fillMaxHeight(), verticalArrangement =
  Arrangement.SpaceBetween)`, with the header at the top and the metric block at the bottom. In every
  grid row, the last value line of both cards is at the same height, whether a card has 1 or 2 values.
- **One value → "hero" style:** the label (CardSubtitle, muted) on one line, then the value below it
  in `BeastText.BigNumber` (bigger), left-aligned. Unit is smaller and muted: `95` `%`, `62` `steps`.
- **Two or more values → rows:** label left, value right, in one `Row` with `verticalAlignment =
  Alignment.Bottom`, and **both** texts use `Modifier.alignByBaseline()`, so label and number share a
  baseline. Values use `BeastText.TileNumber`. Row spacing is 6dp.
- **The single vs multi decision is made on the values that are actually present**, not per card type.
  Steps with no distance, or SpO₂ with no min/max, automatically uses the hero style.
- **Equal heights per grid row, no fixed height:** replace the 2-column `LazyVerticalGrid` tile items
  with `cards.chunked(2)` rows. Each is a `Row(Modifier.height(IntrinsicSize.Min))` with two
  `Modifier.weight(1f).fillMaxHeight()` cards. The pair matches the taller card and grows with font
  scale, so nothing is clipped. The screen becomes a `LazyColumn`: week strip/range header item, one
  item per tile pair, then Nutrition, then sessions. An odd last card keeps half width with an empty
  `Spacer(weight 1f)` beside it.
- **Minimum tile height** is 132dp (`heightIn(min = 132.dp)`), so a pair of hero cards doesn't look
  squashed.
- **Range mode:** no "avg" on values. The card gets the subtitle "Daily avg" (or "Total" for Sleep,
  which already sums). Values then have the same length as in Day mode.
- **Units:** heart-rate range shows "74–85 bpm"; SpO₂ range "93–98%". Weight uses the user's weight
  unit (`parseWeightUnit(settings.weightUnit)`) instead of a hardcoded "kg" (`:409`).
- **Nutrition (full width):** Calories as hero on the left; Protein / Carbs / Fat as three equal
  `weight(1f)` columns on the right, each with the label on top and the value below, centred.
- **Tap target and accessibility:** the whole card stays clickable. Add
  `semantics(mergeDescendants = true)` with a content description such as "Oxygen, SpO₂ 95 percent".
- **Contrast:** labels use `tint.onFillMuted` (passes 5.9–7.0:1 in dark and 4.9–5.6:1 in light, per
  the audit). No `tint.accent` text in these cards.

### 3.3 Daily Report Health section
Replace the plain label/caption rows (`DailyReportScreen.kt:269-286`) with a 2-column grid of the same
`HealthMetricCard` in a compact variant (no icon circle, `CardTints.Neutral` so it matches the other
report sections). It uses the same hero-vs-rows rule and the same units. "Tracked sessions: N" stays
as a caption below.

### 3.4 Files
| File | Change |
|---|---|
| New `ui/workout/health/HealthMetricCard.kt` | `HealthMetricCard(title, icon?, tint, subtitle?, metrics: List<HealthMetric>, onClick?, compact = false)`, `data class HealthMetric(label, value, unit?)`, private `HeroMetric` / `MetricRowAligned`. |
| `ui/workout/health/HealthTabScreen.kt` | Grid → `LazyColumn` + chunked pairs. Build a `List<HealthMetric>` per card (drop nulls), delete `HealthTileHeight`, the old `MetricCard`/`MetricRow` and the "avg" suffixes, fix Nutrition, add units. |
| New `data/health/HealthCardMetrics.kt` (pure) | `metricsFor(kind, day|aggregate, mode, weightUnit): List<HealthMetric>`. The single place that decides labels, units and which values exist. Unit-tested. |
| `ui/report/DailyReportScreen.kt` | `HealthSectionBody` uses `metricsFor` + compact `HealthMetricCard`. |
| `ui/workout/health/HealthDetailSheet.kt` | No layout change. Reuse `metricsFor` labels so the wording matches the cards. |

---

## 4. Build order
1. **Migration and data**: entity, DAO, `MIGRATION_28_29`, `AppDatabase` v29, schema export,
   `MigrationTest` 28→29, add to the sign-out wipe.
2. **Chat instructions** (§1): settings column, ViewModel, screen, `openChat` system message.
3. **Exclusion domain** (§2.3) + unit tests.
4. **Report wiring** (§2.4): row IDs, filtering in `generate`/`openChat`, fingerprint, counts,
   caption.
5. **Exclusions picker screen** + settings rows + nav route + delete clean-up.
6. **Health cards** (§3): pure `metricsFor` + tests → `HealthMetricCard` → Health tab → Daily Report
   section.
7. `./gradlew test`, `./gradlew assembleRelease` (`JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9`), then
   send the signed APK.

## 5. Tests
**Unit (plain JUnit, no Robolectric — matches repo style)**
- `AiExclusionSetTest`: whole-habit hides every row; an entry hides one row; a task-level hide
  doesn't touch habits with the same ID prefix; the count is correct; `isEmpty`.
- `WithoutExcludedTest`: filtering keeps workout/health untouched and keeps order.
- `ChatSystemMessageTest`: blank chat instructions produce no "user's own instructions" paragraph;
  non-blank text appears after the rules and before the data; summary instructions never appear in
  chat.
- `SettingsFingerprintTest`: a changed exclusion set changes the summary fingerprint; chat
  instructions don't.
- `HealthCardMetricsTest`: Steps without distance gives 1 metric (hero); HR with min/max gives 2
  metrics with a "bpm" range; Range mode has no "avg" in values; weight in lb converts.
- `MigrationTest` 28→29: an existing `ai_meta_prompt` is copied to `ai_chat_meta_prompt`; the
  `ai_exclusions` table accepts inserts; other rows are unchanged.

**On the device (your checklist)**
1. Upgrade over the current build: Settings → Daily Report AI shows your old text in **both** boxes.
2. Change only the Chat box, open Chat and say "hi": the reply follows the new tone. Generate a
   summary: it uses the Summary text.
3. Hide one journal entry from Chat only. Open chat on that day and ask "what did I write in my
   journal?": it isn't mentioned. Generate a summary: it **is** included.
4. Hide a whole medication reminder from Summary. Regenerate: no mention, and the "2 entries hidden
   from AI" caption shows. Old summary cards show the "settings changed" hint.
5. Delete a hidden habit: its count disappears from Settings.
6. Sign out and in: both hidden lists are empty.
7. Health tab, Day mode: Steps 62 and Calories Active/Total have their bottom lines level. Label and
   number share a baseline. Titles aren't cut off.
8. Set the phone font size to the largest: no card clips, and pairs grow together.
9. Range mode: values have no "avg"; cards say "Daily avg".
10. Daily Report → Health section looks like the Health tab cards.

## 6. Risks and how they're handled
| Risk | Handling |
|---|---|
| A migration bug blocks the app from opening | Only additive SQL, `MigrationTest` required before building, and v29 schema JSON committed to `schemas/`. |
| Hidden entries leak through a path that isn't filtered | Filtering happens at the two only places prompts are built (`generate`, `openChat`), after the report is loaded, and unit tests cover both. |
| Switching the Health tab from grid to `LazyColumn` changes scroll feel | The same content padding and spacing are kept; one pair row is one lazy item, so scrolling stays cheap. |
| The existing "switch every category off sends everything" bug (audit H-AI1) | Not fixed in this plan, but the new "Hidden" lists don't depend on it. Recommend fixing in the same build: store `"NONE"` when every category is off and disable Generate/Chat. It's a 10-line change. |
