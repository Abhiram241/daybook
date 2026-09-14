# DAILY_REPORT_PLAN.md

**Status: PLAN ONLY — not implemented.** No `.kt`/`.xml`/`.gradle.kts`/`.json` file has been
touched to produce this document. New, independent feature — normal mode (outside Beast Mode),
does not modify `HEALTH_AND_WORKOUT_PLAN.md` or `HEALTH_VITALS_RICHNESS_PLAN.md`, though it *reads*
data both of those introduced (workout sessions, health days).

Four product questions were asked up front rather than assumed; answers below are binding on
everything that follows:

1. **Entry point: a brand-new fourth bottom-nav tab, rightmost, next to Intake.** This is a
   deliberate, explicit change to a previously-locked invariant — the main plan's §0 states
   Daybook's nav "stays exactly three routes" and `NavConfig.ALL_ROUTES` is `["home", "routines",
   "foodmed"]`. That invariant was about Beast Mode never joining the tab bar; it says nothing
   about a fourth *main-app* tab, and the user has now explicitly asked for one. §2 below is the
   full nav-wiring cost of that decision, named plainly rather than smuggled in.
2. **"Todo of that day" = Daybook's existing Habits tab occurrences** (`habit_occurrences` —
   what was due, what was completed vs. missed). Not a new to-do concept.
3. **The AI summary is cached, not regenerated on every view.** Stored per day once generated
   (which provider/model, when), shown instantly on reopen, with a manual "Regenerate" action.
   Syncs like the rest of Daybook's data.
4. **OpenCode / OpenCode Zen's exact request shape is unconfirmed** ("idk" was the honest answer).
   §3.3 builds the provider layer so every adapter is a small, swappable implementation of one
   interface, defaults OpenCode/OpenCode Zen to the OpenAI-compatible chat-completions shape (the
   shape five of the seven providers already use), and flags this explicitly as needing a
   docs-check against OpenCode's actual API before that one adapter ships — not a blocker for the
   other six providers or for the static report, which needs no AI provider at all.

---

## 0. What "static" means, and why it's load-bearing

**The static report half of this page must work with zero network access, zero API key, and zero
AI provider configured — always.** The AI toggle is additive, never required. This mirrors the
main plan's C6 (offline-first, nothing blocks on network) and C9 (a feature must degrade
honestly, never silently) — a user who never touches Settings' new API-key rows still gets a
complete, correct daily report from data already on their device.

Concretely: the screen renders its static half from four Room queries (§1) the instant its Flow
combine emits, the same `collectAsStateWithLifecycle` + `WhileSubscribed(5s)` discipline the rest
of the app uses (C7). The AI half is a separate, independent state slice that starts empty/"not
generated" and only ever changes because the user tapped something.

---

## 1. The static report — what it synthesizes, and from where

One page, one selected day (a `WeekStrip` day picker — the same reused component §7.4 of the main
plan already established the precedent for, byte-identical to Today's and Beast Mode Health's).
Four independent data slices, each individually absent-tolerant (a day with no workout still
shows the other three sections; C9's "never silently drop, always say so" extends here as "a
missing section is announced, not omitted without explanation" — see §1.5):

### 1.1 Workout section
Read via a new `DailyReportRepository` (not `WorkoutRepository` directly — see §4 on why this
needs its own read-only cross-cutting repository) querying `workout_sessions` /
`workout_exercises` / `workout_sets` for the selected `localDate`. Shows: session title (or "Ad-hoc
workout"), duration (`endedAt - startedAt`), total volume (Σ weight×reps, the same pure function
`WorkoutRepository` already computes for the live session header — reused, not reimplemented),
set count, exercise list with per-exercise best set. A day with no session renders nothing in this
section (§1.5), not an empty card.

### 1.2 Health section
Reads `health_days` / `health_sessions` (and, once `HEALTH_VITALS_RICHNESS_PLAN.md` ships,
`health_weight_readings`) for the selected date — the exact same row `HealthTabScreen`'s `Day`
mode already reads via `HealthDao.observeDay`. Shows the same per-metric figures Beast Mode's
Health tab shows (steps, calories, heart rate range, sleep, SpO₂, weight, hydration, nutrition,
band sessions), reusing `visibleHealthCards`'s per-metric hide rule (R32) so an unreported metric
is silently absent from the report exactly as it is from the Health tab — no new hide-logic to
invent, one function reused.

### 1.3 Intake section
Reads `food_med_occurrences` for the selected `local_date` — logged food/med entries, red-flag
markers, outside-food markers. Shown as a compact list (time + item + flag badge), reusing the
existing `RedFlag`/outside-food label mappings already in the Intake tab rather than inventing new
copy.

### 1.4 Todo (Habits) section
Reads `habit_occurrences` for the selected date — what was due, what was completed, what was
missed/snoozed. Reuses `RoutinesScreen`'s existing status labels.

### 1.5 The "nothing happened" rule
Each of the four sections independently renders **only if that section has data for the day** —
same per-section hide instinct as R32's per-metric hide rule, one level up (a whole section
instead of one card). If **all four** are empty, the page shows one `EmptyState` — "Nothing logged
for this day" — instead of four stacked empty sections. This is a new, explicit rule for this
screen (not a copy-paste of R32, which governs individual Health cards, not whole page sections),
stated once here as the page's own R-equivalent: **R-DR1**.

---

## 2. New nav tab — the actual wiring cost of decision 1

Everything here is a change to main-app files the earlier plans deliberately left untouched.
Named in full so nothing is silently assumed:

- `NavConfig.ALL_ROUTES` gains a fourth id, e.g. `"report"`, appended last — `["home", "routines",
  "foodmed", "report"]`. Appending last (not inserting) is what makes it the rightmost tab for
  free, since `toggleRoute`'s re-insertion logic and every rendering loop already iterate
  `ALL_ROUTES` in order.
- `NavConfigTest` gains a case for the fourth id; `visibleRoutesFrom`'s "blank/all-unknown value
  falls back to all three" comment/behaviour becomes "falls back to all four."
- `NavigationSettingsScreen.kt` gains a fourth toggle row (Daily Report can be hidden from the bar
  like Habits/Intake already can — "home" stays permanently on, matching the existing rule).
- `MainActivity.kt`: a fourth `NavItemSpec("report", reportIcon, "Report")` in the nav-items map
  (`MainActivity.kt:576-578`'s three-entry map gains a fourth line), and a fourth branch in the
  `HorizontalPager`'s `when (visibleRoutes.getOrElse(page) { "home" })` (`MainActivity.kt:867+`)
  rendering the new `DailyReportScreen`.
- New icon: reuse an existing `DaybookIcons` glyph rather than inflate a new vector — `BarChart` (a
  "report" reads naturally as a small chart glyph) is already in the set and unused by any current
  nav item.
- **C5 still governs this screen** — unlike Beast Mode's carved-out exception in the health plans,
  this page lives in the *main* app, so it is built entirely from `DaybookColors`/`AppShapes`/
  `DaybookText`, no Beast palette, no new colour literal.

---

## 3. The AI half

### 3.1 The toggle
A `SegmentedControl` at the top of the page (the same sliding-filled-pill component used
everywhere else) with two options: **`Report`** (the static half, §1 — always the default) and
**`AI Summary`**. Switching to `AI Summary` never re-fetches or blocks the static half; it's a
second, independent panel.

### 3.2 Settings — multi-provider API keys
New section in main Settings (not Beast Mode Settings — this is a main-app feature), e.g. `AI
Providers`, one row per provider:

**Providers**: Google AI Studio (Gemini), OpenRouter, NVIDIA NIM, OpenCode, OpenCode Zen, OpenAI,
Anthropic (Claude). Seven rows, each independently: empty / a key saved (masked, e.g. `sk-••••1a2b`
with a reveal toggle) / a key saved and last verified working (a lightweight "test this key" call
per provider, main plan's C9 idiom — plain-language failure, e.g. "That key was rejected by
OpenRouter" rather than a raw HTTP status).

- **Storage: `EncryptedSharedPreferences`, its own file (`daybook_ai_keys`), not `daybook_prefs`.**
  Directly reuses the exact pattern `AppLockRepository` already established (`MasterKey` +
  `EncryptedSharedPreferences`, `data/lock/AppLockRepository.kt:1-40`) rather than inventing a new
  one — and deliberately its own file for the same reason `AppLockRepository`'s KDoc gives for its
  own file: `daybook_prefs` is touched by `SyncStateStore.clearForSignOut()`, and an API key has no
  business being wiped by that path (a signed-out-then-back-in user shouldn't lose their AI keys
  the way sync cursors correctly get wiped).
  Key: `Provider -> apiKey: String`.
- **Never synced to Firestore, never in the JSON export.** An API key is a credential, not app
  data — it does not belong in `BackupModel`/`DayEntry`, the split Daybook/Beast-Mode export
  (`HEALTH_AND_WORKOUT_PLAN.md` §7.5), or `CloudSyncRepository.DATA_TABLES`. Each device holds its
  own keys; moving to a new device means re-entering them, same as any other app's API-key
  settings row. Stated explicitly because it is the one place in this whole document family where
  "should this sync" has a different answer than everything else has had.
- Per-provider **model** field: a plain text field, not a hardcoded dropdown (provider catalogs
  change too often to hand-maintain, and this keeps the settings row honest about what it does and
  doesn't validate) with a sensible placeholder per provider (e.g. `gpt-4o-mini` for OpenAI,
  `gemini-2.0-flash` for Google AI Studio) — the placeholder is a hint, not a default silently
  substituted if left blank; a blank model field at generation time is a validation error (C9),
  not a silent fallback.

### 3.3 Provider abstraction (`data/ai/` — new package)
```kotlin
interface AiProvider {
    val id: String                       // "OPENAI", "GOOGLE_AI_STUDIO", "OPENROUTER",
                                          // "NVIDIA_NIM", "OPENCODE", "OPENCODE_ZEN", "ANTHROPIC"
    suspend fun complete(apiKey: String, model: String, prompt: String): AiResult
}
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Failure(val message: String) : AiResult()   // plain-language, C9 — the raw
                                                            // exception/HTTP body goes to Log.e,
                                                            // never straight to the UI
}
```
- **`OpenAiCompatibleProvider`**: one adapter class, parameterised by base URL, used for **OpenAI,
  OpenRouter, NVIDIA NIM, OpenCode, and OpenCode Zen** — all five publish (or are assumed, per
  decision 4, to publish) the same `POST /chat/completions` request/response shape. This is the
  entire reason a shared adapter is worth building instead of seven bespoke ones: four of the five
  are OpenAI-compatible today per public docs, and the fifth/sixth (OpenCode, OpenCode Zen) default
  to the same assumption pending confirmation.
- **`GoogleAiStudioProvider`**: Gemini's `generateContent` REST shape — genuinely different request
  JSON, its own adapter.
- **`AnthropicProvider`**: the Messages API shape (`/v1/messages`, `x-api-key` header instead of
  `Authorization: Bearer`) — its own adapter.
- **New dependency: OkHttp.** Verified: the app has zero direct HTTP client dependency today —
  Firebase's own SDKs handle Firestore's networking internally and are not something app code
  should reach into. Seven REST endpoints across three distinct request shapes is enough surface
  that hand-rolling `HttpURLConnection` would be genuinely worse (manual header/timeout/redirect
  handling, no connection pooling) than pulling in one small, well-known, actively-maintained
  library — the same "worth a new dependency" bar the main plan applied to
  `androidx.health.connect` in §5.1. `kotlinx.serialization` (already a dependency) handles the
  JSON on both sides.
- Every provider call is a **synchronous, user-triggered suspend call** — no background worker, no
  retry loop, no polling (C7). A failure is `AiResult.Failure` with a plain sentence, surfaced via
  the fixed-height inline result slot idiom (main plan §0) already established for Settings-style
  screens — not a `Toast`, not a `SnackbarHost` (C5/C9, unchanged even for this new screen).

### 3.4 Building the prompt
A pure function, `buildDailyReportPrompt(workout, health, intake, todo, date): String` —
deterministic, unit-testable, no I/O — assembles the same four sections §1 renders into plain text
(not JSON; every provider's `complete` takes one prompt string, keeping the provider interface
identical regardless of what's actually being summarized, which is what makes this screen's AI use
re-generalizable if another summarizable page ever wants the same provider layer). A day with a
section missing (§1.5) omits that section from the prompt text too, with a one-line note ("No
workout logged") — the model is told what's absent instead of guessing from silence.

### 3.5 Generation flow
1. User switches to `AI Summary`, sees a provider **and** model picker (a `BottomSheetMenu` listing
   only providers that currently have a saved key — main plan's per-metric-hide instinct again:
   don't offer a provider you can't call) plus a `Generate` button.
2. Tap `Generate` → button reads `Generating…`, disabled, the exact `isExporting`-style pattern
   `DataSettingsScreen` already uses for its own long-running action.
3. On success: text rendered, `HealthDayAiSummary` row upserted (§3.6) recording provider, model,
   generated-at timestamp, and the summary text; a `Regenerate` (with the same provider/model
   preselected, editable) replaces `Generate`.
4. On failure: the fixed-height result slot shows the mapped plain-language message (C9); nothing
   is saved; `Generate` remains available to retry.

### 3.6 Storage & sync
New table, additive migration (own DB version bump, own schema JSON, own `MigrationTest` case —
main plan's C8 discipline, unrelated to and independent of the health-plans' migration numbering):

```kotlin
@Entity(tableName = "daily_report_ai_summaries")
data class DailyReportAiSummary(
    @PrimaryKey val localDate: String,     // one summary per day, like HealthDay
    val provider: String,
    val model: String,
    val summaryText: String,
    val generatedAt: Long
)
```
- **Syncs like normal Daybook data** (decision 3) — added to `CloudSyncRepository.DATA_TABLES`,
  included in the *Daybook* JSON export (not the Beast Mode one — this table has nothing to do
  with workout/health provenance, it's a main-app artifact), a new optional `DayEntry` field
  following the same `explicitNulls = false` hash-neutrality rule as every prior optional field
  addition in this document family.
- **The API key itself never appears here** — only `provider`/`model`/text/timestamp travel with
  the data; a second device with the same account sees the *summary* without needing the same
  keys, but can't regenerate until it has its own key for that provider (consistent with §3.2's
  "keys are per-device, credentials don't sync" rule).
- Regenerating replaces the row for that date (one summary per day, not a history of regenerations
  — a user who wants a different take taps `Regenerate` and the old text is gone, same as editing
  any other Daybook entry in place).

---

## 4. Why a new `DailyReportRepository` instead of reusing the existing ones directly

`WorkoutRepository`/`HealthRepository`/`FoodMedRepository`/`RoutinesRepository` (or their DAOs)
each already expose what's needed per-section, but the report screen wants **one combined Flow**
across all four, `combine`d the same way `HealthTabViewModel` already combines several flows into
one `uiState` (`HealthTabViewModel.kt:139-167`'s pattern is the template). A thin
`DailyReportRepository` doing that combine — reading the other repositories'/DAOs' existing
queries, adding no new table of its own beyond §3.6's summary cache — keeps the four source
repositories exactly as they are (no new methods bolted onto `WorkoutRepository` for a screen that
isn't Beast Mode's) and keeps the combine logic in one tested place rather than duplicated in a
fat ViewModel.

---

## 5. Phase list

| Phase | Work | Gate |
|---|---|---|
| D0 | Confirm OpenCode / OpenCode Zen's actual request shape against their docs (decision 4) — resolve before `OpenAiCompatibleProvider` is pointed at either, not before the other five providers or the static report | written confirmation of the real shape, or explicit fallback |
| D1 | Nav wiring (§2): `NavConfig`, `NavConfigTest`, `NavigationSettingsScreen`, `MainActivity`'s map + pager branch, new icon | app launches with a working (empty) fourth tab |
| D2 | `DailyReportRepository` (§4) + `DailyReportScreen`/`ViewModel`, static report only (§1), no AI yet | manual pass: a day with all four sections, a day with none, a day with some |
| D3 | Settings: `AI Providers` section (§3.2), `EncryptedSharedPreferences` store, per-provider key/model fields, the "test this key" action | tests green for the store; manual pass for at least one real provider |
| D4 | `data/ai/` provider interface + `OpenAiCompatibleProvider` + `GoogleAiStudioProvider` + `AnthropicProvider`, OkHttp dependency added | unit tests with mocked HTTP responses per adapter |
| D5 | `buildDailyReportPrompt` (pure, unit-tested) + the `AI Summary` toggle UI + generation flow (§3.5) | manual pass: success, a rejected key, a network failure, a day with missing sections |
| D6 | `DailyReportAiSummary` table + migration + sync wiring (`DATA_TABLES`, `DayEntry` field, hash test) | tests green |
| D7 | Full `./gradlew test` + `assembleRelease`; signed APK | all tests green |
