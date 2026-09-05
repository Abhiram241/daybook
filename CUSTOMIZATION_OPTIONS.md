# CUSTOMIZATION_OPTIONS.md — Daybook

Analysis + backlog of every customization / personalization option that could reasonably be
added to Daybook. **No production code changes** — this is a catalogue.

App state referenced: versionCode 13 / versionName "0.5.5" / Room DB **v15**. Dark-only by
design. Google-only auth. Month-partitioned Firestore sync; `Definitions` (habits, intake
reminders, custom categories, custom prompts, journal questions) ride the parent doc whose
`definitionsHash` must not churn for users who don't use a feature.

---

## 1. Summary — how this supersedes `PERSONALIZATION_IDEAS.md`

`PERSONALIZATION_IDEAS.md` (round 11, 2026-08-30) is a flat wishlist. This doc **replaces**
it: every item below is carried forward with a grounded "plugs into X" line, corrected effort,
and current status. Delete `PERSONALIZATION_IDEAS.md` once this lands, or leave it as a
historical note.

### 1a. Status of every `PERSONALIZATION_IDEAS.md` item

| PERSONALIZATION_IDEAS item | Status now | Where it stands |
|---|---|---|
| Accent colour picker | **DONE** (v0.4/0.5) | `AppSettings.accent_color`, `AppearanceSettingsScreen`, `AccentColor` enum (5 options). |
| Profile photo | **DONE** (round 9) | `AppSettings.profile_photo_path`, `ProfilePhotoStore`, `ProfileHeader` + tab headers. |
| Configurable name + rotating greeting | **DONE** (round 4) | `AppSettings.user_name`; `HomeViewModel.GREETINGS_ANON` / `namedGreeting` (8 templates, `floorMod(epochDay,8)`). |
| App-wide font picker | **DONE** (v0.3) | `AppSettings.font_choice`, `FontChoice` enum (5), `daybookTypography()`. |
| Light theme + "follow system" | **CARRIED FWD** (biggest ask) | Conflicts with the dark-only decision — see §5. `Theme.kt` `DarkScheme` is the only touch point. |
| AMOLED pure-black variant | **CARRIED FWD** | Depends on a light/dark split existing. `DaybookColors.Bg` is already `#0B0D0F` (near-black). |
| Custom accent (wheel / hex) | **CARRIED FWD** | `accent_color` stores an enum *name* string; hex needs a parse-fallback path in `Converters` + `AccentColor`. |
| Material You dynamic colour | **CARRIED FWD** | `dynamicDarkColorScheme()` into `DaybookTheme`; opt-in flag. |
| Card style toggle (pastel/flat/outlined) | **CARRIED FWD** | `CardTints` + `SoftCard`. |
| Corner style (soft/sharp) | **CARRIED FWD** | `AppShapes` in `Tokens.kt` — all radii are hard constants. |
| Text size / density | **CARRIED FWD** | `daybookTypography()` builds identical sizes for every `FontChoice`; no scale multiplier. `Spacing.listGap` fixed. |
| Alternate launcher icons | **CARRIED FWD** | `activity-alias` variants in the manifest; no data-layer change. |
| Greeting tone (minimal/warm/quote) | **CARRIED FWD** | `HomeViewModel.renderGreeting` — pool is fixed, no choice. |
| Editable greeting templates | **CARRIED FWD** | Same; would need a new stored list (sync candidate). |
| Per-habit "why" note | **CARRIED FWD** | `Habit.description` exists but renders as the card subtitle — a distinct motivation field is separate. |
| Emoji / photo per habit | **CARRIED FWD** | `Habit.icon_key` is a curated-set key (`Icons.getCuratedIconSet()`, 21 entries). |
| Week-start day (Sun/Mon) | **CARRIED FWD** — *cheaper than stated* | `WeekStrip.kt:64` + `MonthGrid` hardcode Monday. **`StreakCalculator` does NOT use week-start** (it counts consecutive calendar days), so this is display-only. |
| Quiet hours / DND window | **CARRIED FWD** | Nothing in `OccurrenceScheduler` / `NotificationUtils` defers by time-of-day. |
| Rest days / streak freeze / vacation | **CARRIED FWD** | `StreakCalculator.fullyCompletedDates` is strict all-or-nothing; no grace/skip concept. |
| Per-reminder custom notification text | **DONE for intake** (v0.5.3) | `FoodMedTask.prompt_message`, `NotificationUtils.resolvePrompt()`. Habits still fixed ("Time to complete this habit"). |
| Per-reminder sound / vibration / lead-time | **PARTIAL** | Per-item `snooze_interval_minutes` exists (`Habit` / `FoodMedTask`). Sound/vibration/lead-time do not. |
| Default landing tab + reorder / hide tabs | **CARRIED FWD** | `MainActivity` `pageRoutes` / `navItems` are fixed lists; pager always starts at page 0. |
| Auto-archive expired reminders after N days | **CARRIED FWD** | No archive automation anywhere. |
| Habit goals ("4×/week") | **CARRIED FWD** | `active_days_json` = which weekdays only; no frequency target, adherence math is per-scheduled-slot. |
| Richer intake logging (portion / mood / note) | **PARTIAL** (FOOD only) | v0.5.4 added `red_flag`, `suspected_food`, `outside_food`; JOURNAL has `qa_json`. Mood scale / portion size still new. |
| Tags / categories for reminders + filter | **PARTIAL** | `custom_categories` table + type filter (`FoodMedViewModel._typeFilter`) exist. Free-form tags do not. |
| Reduce motion toggle | **CARRIED FWD** | `Motion` in `Tokens.kt` has no gate; springs/tweens are unconditional. |
| Independent text scaling / high-contrast | **CARRIED FWD** | No `fontScale` override, no high-contrast palette. |

### 1b. Catalogue size (new + carried-forward, excluding DONE)

| Area | Ideas |
|---|---|
| Appearance / Theme | 12 |
| Home & Today | 9 |
| Habits | 6 |
| Intake & Journal | 9 |
| Notifications & Reminders | 11 |
| Streaks | 5 |
| Calendar & Time | 6 |
| Data & Backup | 7 |
| Privacy & Lock | 5 |
| Navigation & Layout | 6 |
| Accessibility | 5 |
| **Total** | **81** |

### 1c. The cost of "one new preference" (referenced throughout as **[settings-col]**)

An additive, **non-synced** `AppSettings` column:
1. `Migrations.kt` — `MIGRATION_15_16` = `ALTER TABLE app_settings ADD COLUMN <name> <type> [DEFAULT …]`.
2. `AppDatabase.kt:29` — bump `version = 16`; `DatabaseModule.kt:44` — add `MIGRATION_15_16` to `.addMigrations(...)`.
3. `exportSchema = true` — commit `app/schemas/com.daybook.app.data.local.AppDatabase/16.json`.
4. `DataModel.kt` `AppSettings` — new field (append; `@ColumnInfo` default matching the SQL default).
5. `AppSettingsDao.kt` — `@Query("UPDATE app_settings SET <col> = :v WHERE id = 1")`; `AppSettingsRepository.kt` — setter (`ensureRow()` + DAO call). `observeSettings()` already fans out to every consumer.
6. UI — a row in the relevant Settings sub-screen + `SettingsViewModel` / `OnboardingViewModel` flow.

**Synced** preference is materially more expensive (**[synced-pref]**): it must go in
`BackupModel.kt` `Definitions` (not `AppSettings` — the backup model deliberately carries no
settings row), which feeds `ContentHash.ofDefinitions` → `SyncStateStore.definitionsHash`.
The new field **must** carry `@EncodeDefault(EncodeDefault.Mode.NEVER)` (like
`Definitions.journalQuestions`, `HabitDef.streakStartedAt`) so a user who never sets it sees a
byte-identical `definitionsHash` and no spurious parent-doc push. Also: `ExportImportRepository`
export + import mapping, and a hash-stability test.

---

## 2. Per-screenshot observations — what is fixed today

### Screenshot 1 — Settings ▸ Export & import (`DataSettingsScreen`)
| Fixed / hardcoded | Could become a preference |
|---|---|
| Range defaults to "1st of this month → today" (`SettingsScreen.kt:561`) | Remembered last range; "last 7/30 days" presets. |
| Export is **manual only** — no scheduled/auto backup | Auto-export cadence (weekly/monthly) to Downloads; last-export age line (was removed in v0.5.1 §M). |
| "Share backup…" always shares a **full** export (`shareLatestExport` → `exportAllData`) | Share the chosen range instead / choose scope. |
| No "what to include" toggles | Exclude archived items / exclude answers / definitions-only export. |
| Format is fixed v2 JSON | (Low value) CSV export for the future stats page. |
| Retention (`RETENTION_DAYS = 90`, activity events) and month lazy-fetch are invisible | Surface "keep N months offline" / "keep activity log N days". |

### Screenshot 2 — Settings ▸ Journal questions (`JournalQuestionsSettingsScreen`)
| Fixed | Could become a preference |
|---|---|
| One global ordered set — "Applies to all journal reminders" | Per-reminder question sets (`FoodMedTask` → question-set id). |
| Every question is free-text | Question **types**: mood/1–5 scale, yes/no, number, single-select. |
| No per-question "optional" flag | Mark a question skippable without a blank answer. |
| Seed text "What's on your mind?" is fixed (`MIGRATION_13_14`) | — (fine as a seed). |
| "at least one question" rule (`canDelete`) | — (keep). |

### Screenshot 3 — Settings ▸ Notifications & alarms (`NotificationSettingsScreen`)
| Fixed | Could become a preference |
|---|---|
| Only knob is "Habit check-in time" (`AppSettings.habit_checkin_time`, device-local) | Multiple check-in times / per-weekday check-in. |
| No quiet-hours control | Quiet-hours window (defer or drop reminders overnight). |
| Channels `habits_v2` / `food_med_v2` are `IMPORTANCE_HIGH`, sound/vibration are OS-channel-only (`NotificationUtils.createNotificationChannels`) | In-app "sound", "vibrate", "priority" per category → recreate channel with a versioned suffix. |
| `AppSettings.default_snooze_minutes` (=10) is stored but only read for the BATCH check-in snooze | Surface a global default snooze; feed it into the per-item form default. |
| Re-nag is unlimited every snooze interval until resolved | "Stop reminding after N nudges" cap. |
| "stored on this phone and isn't synced" is itself a fixed choice | Optionally sync the check-in time. |
| No reminder lead-time | "remind me 10 min before" per reminder. |

### Screenshot 4 — Settings ▸ Account & sync (`AccountScreen`)
| Fixed | Could become a preference |
|---|---|
| Sync cadence: 3 s debounce (`DEBOUNCE_MS`), `onAppStop` flush, 24 h `WindowRefreshWorker` | "Sync on Wi-Fi only", "Sync manually only", "Sync every N hours". |
| **Sign-out always wipes local Room** (`wipeLocalForSignOut`) | "Keep data on this device when I sign out" toggle (Danger-zone copy already implies local data can be kept). |
| "Use Google photo" is a one-shot button | Auto-follow Google photo on every sign-in; use Google display name as the app name. |
| No multi-account / account switch | (Low value.) |
| "1 h ago" relative phrasing fixed | — |

### Screenshot 5 — Settings ▸ Appearance (`AppearanceSettingsScreen`)
| Fixed | Could become a preference |
|---|---|
| **Dark only** — no light / system option | See §5 (design decision). |
| 5 accents (`AccentColor`: Mint, Lavender, Coral, Sky, Amber) | Custom hex / colour wheel; Material You dynamic. |
| 5 fonts (`FontChoice`) | — (probably enough). |
| Accent swatches unlabeled | Add names for accessibility. |
| No text-size / density / corner-radius / motion / card-style / contrast controls | All of §3 Appearance rows below. |

### Screenshot 6 — Intake tab (`FoodMedScreen`)
| Fixed | Could become a preference |
|---|---|
| Card density / layout (icon tile + 3 text lines) | Compact vs comfortable list density. |
| Icon + tint auto-assigned by list index (`CardTints.byIndex`); override only in Add form ▸ Advanced | Surface tint/icon on the card's overflow menu. |
| Subtitle format "Food · 1×/day" is fixed | — |
| "Next: Today 2:40 PM" uses 12 h clock (`DateTimeUtils.displayTimeFormatter = "h:mm a"`) | 24 h toggle (see §7). |
| Filter defaults: sort = `IntakeSort.ADDED`, archived hidden, all types (`FoodMedViewModel`) | Remembered filter/sort; user default sort. |
| No grouping | Group by type / by time-of-day. |
| FAB fixed bottom-right | — (see §10). |

### Screenshot 7 — Habits tab (`RoutinesScreen`)
| Fixed | Could become a preference |
|---|---|
| "B" badge for BATCH; "6 active" count phrasing | — |
| Same card density as Intake | Density (shared with Intake). |
| Sort default `HabitSort.ADDED`, no grouping (`RoutinesViewModel`) | Remembered/default sort; group by type. |
| Ongoing (STREAK) habits also listed here | Optional separate section / filter. |

### Screenshot 8 — Today tab (`HomeScreen` / `HomeViewModel`)
| Fixed | Could become a preference |
|---|---|
| Greeting: 16-template pool, `floorMod(epochDay,8)` rotation, time-of-day word by hour bucket | Greeting **tone** (minimal / warm / rotating quote); disable the time-of-day word; edit templates. |
| Hero line `"$pending left today"` / `"All done"` (`HomeScreen.kt:99`) | Phrasing choice ("13 to go", "13 tasks", hide count). |
| `pending` counts `canComplete || canReply || canSkip` | — |
| Reminder card order = `sortedBy { scheduledEpoch }` | Sort choice; group by habit/intake/journal. |
| Resolved rows hidden by a **session-only** filter (`_showResolved`, resets on process death) | Persisted "always hide resolved" default. |
| Card action buttons fixed | — |

### Screenshot 9 — Today scrolled (week strip + "Your progress")
| Fixed | Could become a preference |
|---|---|
| Week strip collapsed by default; Monday-start (`WeekStrip.kt:64`, `MonthGrid` leading blanks) | Default to month view; **week-start day** (Sun/Mon/Sat). |
| "Your progress" = exactly two cards, Habits then Intake, metric = **% completed today only** | Choose metric (%, "3/8", streak); reorder / hide the section; add a "journal" card. |
| Streak flame pill hidden when `streak == 0`; label "day"/"days" | Toggle the streak pill; relabel; streak-as-of-selected-date is already computed (`habitStreak` keyed to `_selectedDate`). |
| Section title "Your progress" fixed | — |
| `ProgressCardHeight = 144.dp` fixed | Density. |

---

## 3. Customization catalogue

Effort key: **S** = one `[settings-col]` + 1–2 read sites, mostly UI. **M** = new column(s) +
logic across several files, or a new sub-screen, or touches scheduler/streak math. **L** =
architectural (new palette system, `[synced-pref]` round, per-reminder sub-entity, light theme).

### 3.1 Appearance / Theme

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| A1 | Light theme + "Follow system" | The single most-requested control | `Theme.kt` — add `LightScheme` beside `DarkScheme`; `DaybookTheme(themeMode)` param; `[settings-col]` `theme_mode` (DARK/LIGHT/SYSTEM); `DaybookColors` needs light values for every token; audit `CardTints`, hardcoded `DaybookColors.*` in ~40 screens | **L** (both layers) | **Conflicts with the dark-only design decision** — needs the design plan revisited. Every `Color(0x…)` literal outside `DaybookColors` becomes a bug. |
| A2 | AMOLED pure-black | Battery on OLED; contrast preference | Third palette (`Bg = #000000`, deeper surfaces) once A1's mode enum exists | **S** on top of A1, **M** standalone | Only sensible after A1. |
| A3 | Custom accent (hex / wheel) | Ownership; brand match | `AccentColor` currently an enum whose name is stored in `accent_color`; add a `CUSTOM` case + `accent_hex` `[settings-col]`; `Converters` + `AccentColor.fromKey` parse fallback; `DaybookTheme` `scheme.copy(primary = …)` already central | **M** | Contrast: a user picks near-black accent on near-black bg — clamp luminance. |
| A4 | Material You dynamic colour (Android 12+) | Matches wallpaper; "feels native" | `DaybookTheme` — `dynamicDarkColorScheme(context)` when `[settings-col]` `dynamic_color` on; overrides accent | **M** | Only `primary`/`secondary`/`tertiary` are keyed off accent today — safe. Pastels (`CardTints`) stay fixed → visual mismatch to design. |
| A5 | Card style: pastel / flat / outlined | Some find the multi-pastel busy | `SoftCard` (`Components.kt`) + `CardTints.resolve`; `[settings-col]` `card_style`; flat = all `Neutral`, outlined = `Neutral` + `Border` | **M** (UI only, but every list card) | Retires the "auto-assigned pastel" identity the redesign chose. |
| A6 | Corner radius scale: soft / default / sharp | Aesthetic | `AppShapes` in `Tokens.kt` → make it a function of a scale enum; `DaybookShapes` (M3) derives from it; `[settings-col]` `corner_style` | **M** (UI-wide, low risk) | `AppShapes` is `object` with `val` constants — needs a `CompositionLocal` or theme param. |
| A7 | Text size / UI scale (S/M/L/XL) | Readability without OS-wide change | `daybookTypography(choice, scale)` — multiply every `fontSize`/`lineHeight`; `[settings-col]` `text_scale`; theme already `remember(fontChoice)` | **M** | Fixed-height rows (`ProgressCardHeight`, `NavContentHeight`, `IconTile`) clip at XL — need `heightIn` sweep. |
| A8 | List density: compact / comfortable | Fit more per screen | `Spacing.listGap` / `cardInner` / `ProgressCardHeight` → density-scaled; `[settings-col]` `density` | **M** | Overlaps A7; do together. |
| A9 | Alternate launcher icons | Personal touch | `activity-alias` entries in `AndroidManifest.xml` + `PackageManager.setComponentEnabledSetting`; no data layer | **M** (manifest + drawables) | Killing/re-enabling aliases can drop the icon from the launcher briefly. |
| A10 | "Reduce motion" | Accessibility; battery; some users dislike the springs | `Motion` in `Tokens.kt` — gate `pressSpring`/`softSpring`/pager slide/`WeekStrip` `AnimatedContent` behind `LocalReduceMotion`; `[settings-col]` `reduce_motion` | **M** (many call sites, each trivial) | Also honour `Settings.Global.ANIMATOR_DURATION_SCALE == 0` for free. |
| A11 | High-contrast palette | Low vision | New token set (brighter `TextMuted`/`Border`), gated like A10 | **M** | Best bundled with A1/A7 as an "Accessibility" pass. |
| A12 | Accent swatch labels / names | Screen-reader + clarity | `AppearanceSettingsScreen` `Swatch` — add `contentDescription = a.name` | **S** (pure UI) | None. |

### 3.2 Home & Today

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| H1 | Greeting tone: minimal / warm / quote | "Good morning, Abhiram" vs "Friday" vs an affirmation | `HomeViewModel.renderGreeting` / `greetingIndexFor`; `[settings-col]` `greeting_tone`; minimal = date only, quote = a bundled rotating list | **S–M** | Quote list is new content; keep it local (not synced). |
| H2 | Disable the time-of-day word | Some dislike "Good night" at 2 am | `todWordFor` — gate on `[settings-col]` `greeting_time_word` | **S** | None. |
| H3 | Editable greeting templates | Full ownership | New stored `List<String>`; **`[synced-pref]` candidate** (feels like a definition) or local-only | **M–L** | If synced: `@EncodeDefault(NEVER)` + hash test. Local-only is much cheaper and probably right. |
| H4 | Hero line phrasing | "13 left today" vs "13 to go" vs hidden | `HomeScreen.kt:99` `heroText`; `[settings-col]` `hero_style` | **S** | Pure string. |
| H5 | "Your progress" metric choice | Prefer "3/8 done" or streak over % | `HomeScreen.ProgressCard` (`habitRatio`/`foodRatio` already computed); `[settings-col]` `progress_metric` | **S–M** | Count needs `items.count{…}` (already there as `pending`). |
| H6 | Show / hide / reorder Today sections | Intake-only users don't want "Your progress" or the week strip | `HomeScreen` `LazyColumn` items are static; `[settings-col]` bitmask or ordered list `today_sections` | **M** | Week strip drives `selectedDate` — hiding it must pin to today. |
| H7 | Streak pill visibility on progress cards | Some find streaks stressful | `ProgressCard` `if (streak > 0)`; `[settings-col]` `show_streaks` (global) | **S** | Pairs with S1/S5. |
| H8 | Persisted "hide resolved reminders" default | The session filter (`_showResolved`) resets every launch | `HomeViewModel._showResolved` initial value from `[settings-col]` `home_hide_resolved` | **S** | None. |
| H9 | Reminder list grouping (habit / intake / journal / by time) | Long lists on Today | `HomeViewModel.visibleItems` (filter already exists via `homeItemVisible`); add group headers in `HomeScreen` | **M** (UI mostly) | None. |

### 3.3 Habits

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| HA1 | Per-habit "why / motivation" note | Shown on card/detail as a nudge | New nullable `Habit.motivation` col (`MIGRATION_15_16`); `HabitForm` field; `HabitDef` in `BackupModel` → **`[synced-pref]`**, `@EncodeDefault(NEVER)` | **M** | Hash churn — needs the `NEVER` guard + test (this exact class of bug bit journal-v2). |
| HA2 | Habit frequency goal ("4×/week", "every other day") | `active_days_json` only picks weekdays | New `Habit.target_per_week` / schedule-kind col; `StreakCalculator` + adherence math + `OccurrenceScheduler.slots()` all assume fixed weekday slots | **L** (data + streak + scheduler) | Big — redefines what "done today" means. Design revisit. |
| HA3 | Surface icon/tint on the card overflow menu | Today they're buried in Add ▸ Advanced | `RoutinesScreen` card menu → reuse `TintPicker` / `Icons.getCuratedIconSet()`; writes existing `color_tag` / `icon_key` | **S** (UI only, columns exist) | None. |
| HA4 | Default sort per list (persisted) | `HabitSort.ADDED` every launch | `RoutinesViewModel._sort` / `FoodMedViewModel._sort` initial from `[settings-col]` `habit_sort` / `intake_sort` | **S** | Trivial; do both lists. |
| HA5 | "New reminder" defaults (default icon, default active days, default snooze) | Every new habit starts icon=task, all 7 days, snooze 10 (`HabitForm.kt:39-44`) | `[settings-col]` `default_snooze_minutes` already exists (unused for this) + new `default_active_days`; `HabitForm` / `FoodMedForm` initial state | **S–M** | None. |
| HA6 | Custom icon set / emoji per habit | 21 curated icons is limiting | `Icons.getIcon` maps keys → `ImageVector`; emoji would need a text-glyph render path in `IconTile` + `icon_key` carrying `"emoji:🏃"` | **M** | `iconKey` flows through backup/sync unvalidated already (`Icons` logs unknown keys) — safe-ish. |

### 3.4 Intake & Journal

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| IJ1 | Per-reminder journal question sets | "Morning pages" vs "gratitude" want different prompts | `journal_questions` is a single global table + `Definitions.journalQuestions`; add a `question_set_id` on `FoodMedTask` + a sets table | **L** (`[synced-pref]`, new entity, editor rework) | Real scope. `qa_json` snapshot per entry already isolates history — good. |
| IJ2 | Mood / scale / number question types | Structured journaling; feeds a stats page | `JournalQuestion` gains a `type` col; `JournalScreen` stepper renders per type; `qa_json` stores typed answers | **M–L** | `Definitions.journalQuestions` is currently `List<String>` — wire format change, `@EncodeDefault` care. |
| IJ3 | Per-habit custom notification text | Intake has `prompt_message`; habits don't | New nullable `Habit.prompt_message`; `NotificationUtils.showHabitNotification` (fixed "Time to complete this habit"); `HabitDef` sync | **M** | Mirror the intake pattern exactly (v0.5.3). Hash guard. |
| IJ4 | Portion size / quantity field on FOOD logs | Diet tracking | `FoodMedOccurrence` new nullable col + `IntakeLog` in backup; inline-reply UI in `HomeItem` (already has red-flag/suspected-food scaffolding) | **M** | `[synced-pref]`-adjacent (per-day log, goes in month docs not parent) — lower hash risk than a `Definitions` change. |
| IJ5 | Free-form tags on reminders + filter by tag | Beyond FOOD/MED/CUSTOM/JOURNAL | New `reminder_tags` (many-to-many) or a `tags_json` col; `FoodMedViewModel` filter; `Definitions` sync | **M–L** | Filter UI (`SortSheet`) exists to extend. |
| IJ6 | Entry templates / pre-filled answers | Faster logging of routine entries | `FoodMedTask` default-answer col (FOOD already has `default_suspected_food` etc.) extended to CUSTOM/journal | **M** | Pattern precedent exists (v0.5.4 defaults). |
| IJ7 | Intake card grouping (by type / time) | Screenshot 6 — flat list | `FoodMedScreen` list; `FoodMedViewModel.tasks` already paired with next-millis | **M** (UI) | None. |
| IJ8 | "1×/day" vs actual times on the card | Subtitle is a summary; some want the times inline | `FoodMedScreen` card composable | **S** | Pure UI. |
| IJ9 | Per-question "optional" flag | Don't force a blank answer | `JournalQuestion.optional` col; `JournalScreen` stepper "skip"; `qa_json` | **S–M** | `Definitions.journalQuestions` format change again — batch with IJ2. |

### 3.5 Notifications & Reminders

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| N1 | Quiet hours / DND window | No 3 am reminders | `OccurrenceScheduler.armNext*Internal` / `slots()` — clamp `triggerAt` out of the window (defer to window end) or skip; `AlarmReceiver` refire chain; `[settings-col]` `quiet_start` / `quiet_end` | **M** (scheduler logic) | Interacts with re-nag: a snooze landing in quiet hours must also defer. Test `OccurrenceSchedulerDecisionTest`-style. |
| N2 | Global default snooze, surfaced | `AppSettings.default_snooze_minutes` exists, only used for BATCH (`snoozeBatchCheckIn`) | `NotificationSettingsScreen` row → existing column; feed it as the `HabitForm`/`FoodMedForm` default | **S** | Column + repo path already exist; just no UI. |
| N3 | Re-nag cap ("stop after N nudges") | Unlimited re-nag is aggressive | `OccurrenceScheduler.snooze*` / `AlarmReceiver` — check `snooze_count` (already tracked on occurrences) vs `[settings-col]` `max_renag` | **M** | Behaviour change to a deliberately-unlimited design (REV-31 chose unlimited) — confirm with owner. |
| N4 | Per-category sound / vibration / importance | Screenshot 3 — channel settings are OS-only | `NotificationUtils.createNotificationChannels` — recreate with a versioned suffix on change; `[settings-col]` per channel | **M** | Channels are immutable once created (the whole `_v2` suffix saga) — every change = new channel id + delete old. Fiddly. |
| N5 | Reminder lead-time ("10 min before") | Prep time | New `Habit`/`FoodMedTask` `lead_minutes` col; `OccurrenceScheduler.slots()` offsets `scheduledFor` for the alarm but not the logged slot | **M** | Splits "alarm time" from "slot time" — touches `local_date` bucketing, streak, backfill. Careful. |
| N6 | Multiple / per-weekday BATCH check-in times | One check-in at 21:00 for everyone | `AppSettings.habit_checkin_time` is a single "HH:mm"; needs a list col + `armBatchCheckInInternal` loop | **M** | Device-local today; keep it so. |
| N7 | Sync the check-in time (opt-in) | Multi-device users re-set it each install | Move/duplicate `habit_checkin_time` into `Definitions` behind a flag | **M** | `[synced-pref]` hash care. Screenshot 3 explicitly says "isn't synced" — copy change too. |
| N8 | Per-reminder active-time-range ("only remind 9–5") | Work-only reminders | New col; `OccurrenceScheduler.slots()` filter | **M** | Overlaps N1 but per-item. |
| N9 | Habit notification actions choice (hide Skip / Snooze) | Simplify the shade | `NotificationUtils.showHabitNotification` `addAction` calls; `[settings-col]` bitmask | **S** | None. |
| N10 | Rolling-window length (how far ahead reminders exist) | `WINDOW_DAYS = 7` in `OccurrenceScheduler` — power users want more lead visibility | Constant → `[settings-col]`; `slots()` + `WindowRefreshWorker` | **S–M** | More rows per item; occurrence tables are eviction-capped for signed-in users, unbounded for signed-out. |
| N11 | Stale auto-skip threshold | `STALE_AFTER_MS = 24h` auto-skips missed prompts | Constant → `[settings-col]`; `skipStaleForTask`/`Habit` | **S** | Longer = more backlog head-blocking risk (the REV-05 class). |

### 3.6 Streaks

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| S1 | Rest days (planned off-days that don't break a streak) | Classic ask; matches `active_days` intent but for streaks | `StreakCalculator.fullyCompletedDates` / `computeStreaks` — treat a non-scheduled weekday as "not a gap"; partly implicit today (a day with no occurrences is just "absent" and breaks the run) | **M** | Pure-function change + tests (`StreakCalculatorTest`). Also `OngoingStreak.daysSince`. |
| S2 | Streak freeze / grace day ("one slip allowed") | Reduces all-or-nothing anxiety | `computeStreaks` — allow one missing date in the backward walk per `[settings-col]` `grace_days`; also "freeze tokens" earned | **M** | Design decision — changes what the flame means. Owner call. |
| S3 | Vacation mode (pause streaks for a date range) | Travel | New stored range; `StreakCalculator` skips the range; `OccurrenceScheduler` optionally suppresses reminders too | **M** | `[synced-pref]`-ish (a definition-level setting). |
| S4 | Streak definition: "all done" vs "any activity" | `fullyCompletedDates` requires **every** occurrence that day done | `[settings-col]` `streak_mode`; `fullyCompletedDates` predicate (`doneFlags.all` vs `doneFlags.any`) | **S** | Pure function + test. |
| S5 | Hide streaks entirely | Some users find them counterproductive | `[settings-col]` `show_streaks`; `HomeScreen.ProgressCard`, Detail stats, `RoutinesScreen` | **S** | Pairs with H7. |

### 3.7 Calendar & Time

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| C1 | Week-start day (Sun / Mon / Sat) | Regional norm | `WeekStrip.kt:64` `todayWeekStart`, `MonthGrid` `leadingBlanks` + weekday-initial row; `[settings-col]` `week_start`. **`StreakCalculator` unaffected** (calendar-day based) | **S–M** (display only) | Cheaper than `PERSONALIZATION_IDEAS` implied — no streak-math risk. `DateTimeUtils` has no week-start helper today; add one. |
| C2 | 24 h vs 12 h clock | Reverted to 12 h in round 7; re-expose as a choice | `DateTimeUtils.displayTimeFormatter` (`"h:mm a"`, hardcoded, US locale) → pick "HH:mm"; `[settings-col]` `clock_24h`; `TimePickerDialog` `is24Hour` | **S** | `displayTimeFormatter` is `private val` — make it a function of the pref. Storage ("HH:mm") unaffected. |
| C3 | Date format (relative vs absolute; d/M order) | "Fri, 4 Sept" is fixed (`HomeScreen` header, `DateTimeUtils.formatDayLabel`) | `DateTimeUtils` formatters; `[settings-col]` `date_style` | **S–M** | Several call sites; low risk. |
| C4 | Default calendar view: week vs month | `HomeScreen` `calendarExpanded` starts `false` | Initial value from `[settings-col]` `calendar_default_expanded` | **S** | None. |
| C5 | Relative-day phrasing ("Yesterday" vs date) | `getRelativeDayString` / `formatDayLabel` hardcode Today/Yesterday/Tomorrow | Gate on `[settings-col]` | **S** | Minor. |
| C6 | First-day-of-week-aware "this week" in stats | Future stats page | `DateTimeUtils` week helper (C1) reused | **S** on top of C1 | Depends on C1. |

### 3.8 Data & Backup

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| D1 | Auto-export cadence (off / weekly / monthly) | Round 4 deferred it; v0.5.1 §M removed the half-built reminder | New `WorkManager` periodic worker (model `WindowRefreshWorker`); `StorageUtils.saveExport`; `[settings-col]` `auto_export` | **M** | Scoped-storage writes to Downloads already work. Don't resurrect the old "reminder notification" — do the actual export. |
| D2 | "Last backup: N days ago" line | Removed with §M; users want reassurance | Re-add a `last_export_at` `[settings-col]` (was dropped by `MIGRATION_11_12`); `DataSettingsScreen` | **S** | Column was deliberately removed — re-adding is fine, just additive. |
| D3 | Export scope toggles (skip archived / answers-only / definitions-only) | Smaller / shareable backups | `ExportImportRepository.exportRange` / `exportAllData`; new params | **M** | `DaybookBackup` shape stays; just filter what's written. |
| D4 | Retention window for the activity log | `RETENTION_DAYS = 90` hardcoded (`RetentionPolicy.kt`) | Constant → `[settings-col]`; `isPrunableActivity` / `retentionCutoffMillis` already parametrised | **S** | Terminal events never pruned regardless — safe. |
| D5 | Offline history depth ("keep N months on device") | Current+previous month only (`CloudSyncRepository` SD-3a); older is lazy-fetched | Eviction window in `evictStaleMonths` / `ensureMonthHydrated`; `[settings-col]` `resident_months` | **M** | More resident months = bigger DB, slower `MIGRATION_12_13`-class sweeps. Signed-out users don't evict at all. |
| D6 | Sync cadence: Wi-Fi-only / manual-only / interval | Data/battery control (Screenshot 4) | `CloudSyncRepository` push loop (`DEBOUNCE_MS`), `WindowRefreshWorker` constraints, `SyncFlushWorker`; `[settings-col]` | **M** | "Manual only" needs a clear "unsynced changes" indicator. |
| D7 | Keep local data on sign-out | Sign-out currently always wipes (`wipeLocalForSignOut`) | `AccountViewModel.signOut` → branch on `[settings-col]` `wipe_on_signout`; `CloudSyncRepository` | **M** | Security nuance — a shared device wants the wipe. Default stays "wipe". |

### 3.9 Privacy & Lock

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| P1 | PIN length (4 / 6) | 4 is fixed (`PinHasher.isValidPin`, decision 7) | `PinHasher` + `LockScreen` pad; `AppLockRepository` | **S–M** | Was deliberately 4 — owner call. |
| P2 | Hide content in the app switcher (`FLAG_SECURE`) as opt-in | Journals are private; decision 9 said "no" globally | `MainActivity` `window.addFlags(FLAG_SECURE)` gated on `AppLockRepository` pref | **S** | Reverses decision 9 — make it opt-in, not default. Also blocks screenshots (that's the point). |
| P3 | Attempt lockout / cooldown | Decision 8 = unlimited, no cooldown | `AppLockRepository.verifyPin` (comment explicitly says "do not add a counter") | **S** | Explicitly rejected — needs owner sign-off. |
| P4 | Biometric-only (no PIN fallback) option | Fewer steps | `AppLockRepository.enable` forces a PIN as fallback | **M** | Locks users out if biometrics change — the current design is deliberate. Low priority. |
| P5 | Per-tab lock (lock only Intake/Journal) | Share the app without exposing the diary | New gating layer in `MainActivity` pager; `[settings-col]` | **M** | Meaningful privacy feature for a journaling app. |

### 3.10 Navigation & Layout

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| NL1 | Default landing tab | Intake-only users shouldn't land on Today | `MainActivity.MainApp` — `rememberPagerState(initialPage = …)` from `[settings-col]` `default_tab`; `BackHandler` target | **S** | `BackHandler` currently hard-returns to page 0 — update to the default. |
| NL2 | Reorder bottom-nav tabs | Put the tab you use most first | `MainActivity` `pageRoutes` / `navItems` are `remember { listOf(...) }`; `HorizontalPager` `when(page)`; `[settings-col]` ordered list | **M** | Directional slide + `goToPage` snap logic keys off index — keep consistent. |
| NL3 | Hide a tab | Habits-only or Intake-only users | Same as NL2 + `pageCount` | **M** | Deep links / FAB context must handle a missing tab. |
| NL4 | FAB visibility / position | Some want it gone or left-handed | `DaybookScaffold` `fabPresent`; `RoutinesScreen` / `FoodMedScreen` FAB | **S** | Empty-state Add button already exists as a fallback. |
| NL5 | Nav bar labels: always / selected-only / never | Space / preference | `Navigation.kt` nav item rendering; `[settings-col]` | **S** | Pure UI. |
| NL6 | Swipe-between-tabs on/off | Accidental swipes during scroll | `MainActivity` `HorizontalPager` `userScrollEnabled`; `[settings-col]` | **S** | None. |

### 3.11 Accessibility (as personalization)

| # | What | Why | Plugs into | Effort | Risk / conflicts |
|---|---|---|---|---|---|
| X1 | Reduce motion | See A10 | `Motion` gate | **M** | — |
| X2 | Independent text scale | See A7 | `daybookTypography` | **M** | — |
| X3 | High-contrast palette | See A11 | token set | **M** | — |
| X4 | Larger tap targets | `IconButtonSize` enum is fixed (32/40/44/56) | `Tokens.kt` `IconButtonSize` → scaled | **S–M** | Layout reflow. |
| X5 | Content-description pass (accent swatches, streak flame, week cells) | Screen-reader gaps | Component-level `contentDescription` | **S** | Not a stored preference — just fix it. |

---

## 4. Top ~8 recommendations (value ÷ effort, sequenced)

| # | Recommendation | Why now | Migration / sync? |
|---|---|---|---|
| 1 | **Week-start day (C1)** + **24h/12h toggle (C2)** + **default calendar view (C4)** | Highest-demand, lowest-risk. `StreakCalculator` is untouched (calendar-day based), so C1 is display-only — much cheaper than `PERSONALIZATION_IDEAS` assumed. | One `[settings-col]` (`week_start`, `clock_24h`, `calendar_default_expanded`), **no sync**, DB v15→v16 (single additive migration for all of §4). |
| 2 | **Greeting tone + disable time-of-day word (H1/H2)** and **hero-line phrasing (H4)** | Pure `HomeViewModel` / `HomeScreen` string logic. Big "feels mine" payoff for a day's work. | `[settings-col]`, no sync, **pure-UI quick win**. |
| 3 | **Surface the global default snooze (N2)** + **persisted list sort (HA4)** + **persisted "hide resolved" (H8)** | The columns/state already exist (`default_snooze_minutes`, `_sort`, `_showResolved`) — this is wiring, not new data. | Mostly no new column (`default_snooze_minutes` exists); 2 tiny cols for sort/hide. **Pure-UI quick win.** |
| 4 | **Reduce motion (A10)** + accessibility content-description pass (X5) | Accessibility gap; also a common preference. Each call site is trivial; honour `ANIMATOR_DURATION_SCALE == 0` for free. | One `[settings-col]`, no sync. UI-wide but low risk. |
| 5 | **Quiet hours (N1)** | Top behavioural ask after light theme. Contained in `OccurrenceScheduler.armNext*` + `AlarmReceiver`. | Two `[settings-col]` (start/end), no sync, but **scheduler logic + tests** — needs a schema round only for the columns. |
| 6 | **Streak mode + hide streaks (S4/S5)** and **rest days (S1)** | Personalizes the thing users actually feel. S4/S5 are pure predicate + UI flags; S1 is a bounded `StreakCalculator` change with existing tests. | `[settings-col]`, no sync. Pure-function changes — well-tested surface. |
| 7 | **Default landing tab (NL1)** + **reorder/hide tabs (NL2/NL3)** | Directly answers "intake-only user lands on Today". NL1 alone is trivial; NL2/NL3 are a contained `MainActivity` refactor. | `[settings-col]` (ordered list / index), no sync. NL1 = quick win; NL2/NL3 = M. |
| 8 | **Per-habit custom notification text (IJ3)** + **per-habit "why" note (HA1)** | Mirrors the shipped intake `prompt_message` pattern exactly; low novelty risk. | **Needs the `[synced-pref]` round**: new `HabitDef` fields in `BackupModel`, `@EncodeDefault(EncodeDefault.Mode.NEVER)`, a `definitionsHash`-stability test. This is the one that has bitten the project twice — do it deliberately. |

**Pure-UI quick wins, no migration:** #2, parts of #3 (`default_snooze_minutes` reuse),
HA3 (icon/tint on card menu), IJ8, NL4/NL5/NL6, A12, X5.
**Need a schema round (additive v15→v16, no sync):** #1, #4, #5, #6, #7, most of §3.
**Need a schema + sync round (`definitionsHash` care):** #8, HA1, HA2, IJ1, IJ2, IJ5, N7, S3.

---

## 5. Explicitly out-of-scope / rejected (with the cost if reversed)

| Item | Why out | Cost if the decision is reversed |
|---|---|---|
| **Full light theme / "follow system" (A1)** | Standing design decision: Daybook is dark-only. `Theme.kt` ships only `DarkScheme`; `DaybookColors` has no light values. | **L.** A complete light palette for every token; audit of ~40 screens for raw `Color(0x…)` / `DaybookColors.*` literals used outside a theme lookup; `CardTints` light variants; a `theme_mode` `[settings-col]`; QA of every screen in both modes. This is the single largest item in the backlog — plan it as its own version. A2 (AMOLED) and A11 (high-contrast) only become cheap *after* this. |
| **Per-notification lockout / attempt cooldown (P3)** | App-lock decision 8: unlimited attempts, no cooldown. `AppLockRepository.verifyPin` comment forbids a counter. | **S** code-wise, but reverses a deliberate UX call — needs owner sign-off, not just engineering. |
| **`FLAG_SECURE` globally (P2 as default)** | Decision 9: no screenshot-block, no app-switcher hiding. | **S.** Offer it as an *opt-in* under App lock instead of a default — that's the recommended form (P2). |
| **"Every N hours" reminder repetition** | Round 1 decision: specific clock times only, no interval mode. `timesJson` model assumes explicit times. | **M.** New schedule-kind enum on `Habit`/`FoodMedTask`; `OccurrenceScheduler.slots()` interval branch; `local_date` bucketing and streak/backfill review. Related to HA2. |
| **Biometric-only app lock (P4)** | `enable()` always stores a PIN fallback (decision 6). | **M**, plus a real lockout-risk if biometrics change. Low value. |
| **Multi-account / account switching** | Google-only, one uid, sign-out wipes local. | **L.** Touches every `SyncStateStore` key, `wipeLocalForSignOut`, month residency. Not worth it for this app. |
| **CSV / spreadsheet export** | v2 JSON is the one format; a stats page doesn't exist yet. | **M.** A `DayEntry` fold to CSV in `ExportImportRepository`. Revisit when the stats page lands. |

---

## Appendix — grounding notes (things the code does / doesn't do)

- **Already customizable but not fully surfaced in Settings:**
  - `AppSettings.default_snooze_minutes` (=10) — a real column with a repo path, but the **only**
    reader is `OccurrenceScheduler.snoozeBatchCheckIn` / `snoozeBatchCheckIn`. Per-item snooze uses
    `Habit.snooze_interval_minutes` / `FoodMedTask.snooze_interval_minutes` instead. There is **no
    Settings row** for either. (N2 is nearly free.)
  - `HomeViewModel.habitStreak` / `foodMedStreak` are **already keyed to `_selectedDate`** via
    `flatMapLatest` — "streak as of the day on screen" is computed today; the progress card just
    shows current. Relabeling / date-aware display (H7/S5) needs no new maths.
  - Per-item **icon + colour tint** are fully supported (`icon_key`, `color_tag` / `ColorTag`
    override, `TintPicker`, `Icons.getCuratedIconSet()`) — but only reachable inside the Add/Edit
    form's collapsed **Advanced** section (`HabitForm.kt:151`). Not on the list card menu (HA3).
  - `ColorTag` enum already carries 6 named tints + `AUTO`; `CardTints.resolve(overrideName, index)`
    handles per-item override vs auto-assign. A "card style" toggle (A5) plugs into the same resolve.
  - App lock **timeout** is already a 4-option enum (`LockTimeout`) with a Settings screen — a good
    template for other enum-valued preferences.
  - Intake / Habits **sort + type filter + show-archived** already exist as VM state
    (`FoodMedViewModel` / `RoutinesViewModel`, `SortSheet.kt`) — just **session-scoped**, not
    persisted (HA4).

- **Looks customizable but is deeply hardcoded:**
  - **Week starts Monday** in three places (`WeekStrip.kt:64` `todayWeekStart`, `MonthGrid`
    `leadingBlanks = firstOfMonth.dayOfWeek.value - 1`, the weekday-initial row). No `DateTimeUtils`
    helper for week-start. Fortunately `StreakCalculator` never references it.
  - **12-hour clock** is a `private val displayTimeFormatter = DateTimeFormatter.ofPattern("h:mm a",
    Locale.US)` in `DateTimeUtils` — every `formatTime` / `formatWhen` / card "Next:" line / picker
    goes through it. Storage is always `"HH:mm"`. Round 7 deliberately reverted from 24h.
  - **`Motion`, `AppShapes`, `Spacing`, `IconSize`, `IconButtonSize`** in `Tokens.kt` are all
    `object`s of compile-time constants — no `CompositionLocal`, so radius / density / motion / tap
    size can't vary without turning them into theme-provided values.
  - **`daybookTypography(choice)`** builds *identical* sizes/weights/line-heights for every
    `FontChoice` (only the family swaps) — there is no scale hook for a text-size preference.
  - **`GREETINGS_ANON` / `namedGreeting`** are top-level `val` lists in `HomeViewModel.kt`; the
    rotation is `floorMod(epochDay, 8)`. No tone, no user edits, no "off".
  - **"Your progress"** is literally two `ProgressCard(...)` calls in `HomeScreen.kt:126-127` with a
    fixed `ProgressCardHeight = 144.dp`; the metric is `ratio(items, isHabit)` (% only).
  - **Bottom nav** — `pageRoutes = remember { listOf("home","routines","foodmed") }` and `navItems`
    are fixed; `rememberPagerState(pageCount = { 3 })`; the pager always starts at page 0 and
    `BackHandler` hard-returns there.
  - **`WINDOW_DAYS = 7`**, **`STALE_AFTER_MS = 24h`**, **`CATCHUP_WINDOW_MS = 60min`** in
    `OccurrenceScheduler`; **`RETENTION_DAYS = 90`** in `RetentionPolicy`; **`DEBOUNCE_MS = 3_000`**
    / **`RESYNC_DEBOUNCE_MS = 1_000`** in `CloudSyncRepository`; **24h** period in
    `WindowRefreshWorker` — all `const val`, none user-visible.
  - **Notification channels** `habits_v2` / `food_med_v2` are created once at
    `IMPORTANCE_HIGH`; a channel is immutable after creation (the reason for the `_v2` suffix), so
    any in-app "importance / sound" control has to mint a new channel id and delete the old one.
  - **Journal questions** are one global ordered table + `Definitions.journalQuestions: List<String>`
    — no per-reminder sets, no question types, no per-question flags.
