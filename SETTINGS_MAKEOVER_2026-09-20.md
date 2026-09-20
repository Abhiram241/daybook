# Beast Mode Settings makeover — 2026-09-20

Owner complaint: "beast mode settings are too unorganized... must be categorized under submenus in necessary places and use better texts for explaining settings better."

Source read in full: `app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsScreen.kt`, `WorkoutSettingsViewModel.kt`. Pattern mirrored from `app/src/main/java/com/daybook/app/ui/settings/SettingsScreen.kt` (`SettingsScreen` hub → `SettingsSubScreen` shells like `AppearanceSettingsScreen`, `TodayCalendarSettingsScreen`, `NotificationSettingsScreen`, `DataSettingsScreen`).

This is a copy-and-structure change only. No colors/spacing/component styles change. Every row keeps its existing icon, action, and behavior — only grouping, navigation depth, and text move.

## Assumptions (owner said don't ask questions — these are the calls made)

1. **Hydration is already out of Beast Mode.** `HydrationSettingsSection` lives only in the main app's `NotificationSettingsScreen` (`SettingsScreen.kt` line ~967), never in `WorkoutSettingsScreen.kt`. There is no hydration toggle/reminder-time row to remove from Beast Mode — nothing here needs deleting. The one hydration-adjacent item that *does* live in Beast Mode is the "Hydration" row inside "Data source priority" — that is a Health Connect **source-app** picker (which app's water-log entries Daybook trusts), unrelated to the reminder/logging feature moving to Intake. It stays in Data source priority under this reorg.
2. **Sub-screens, not accordions.** Every group below becomes its own Beast Mode sub-screen (one level deep from the hub, same depth Beast Mode Settings already is from Routines/History/Health — so total nav depth from those tabs becomes gear → hub → sub-screen, never deeper). "Today screen" (a single switch) and "Leave Beast Mode" (a single action) are folded into an adjacent sub-screen / kept as a standalone hub action rather than getting their own one-row submenu, since a submenu with one row is worse than the current flat list, not better.
3. **"Today screen" toggle folds into "Workout defaults".** It's a Beast-Mode-default-behavior setting (does a card show up), same family as weight unit / rest timer / default exercise group.
4. **Hub row subtitles are static "what's inside" summaries**, not live dynamic previews (mirrors `TodayCalendarSettingsScreen`'s hub entry "Week start, clock, greeting, streaks", not `AppearanceSettingsScreen`'s dynamic accent/font subtitle) — this needs no new state plumbing in the ViewModel, just literal strings, so it's a pure text/structure change as scoped.
5. **"Data source priority" becomes a 2-level construct but stays 1 level of sub-screen from the hub**: hub → "Data source priority" sub-screen (list of 8 metric rows) → tapping a metric opens its ranked-list UI (owned by the parallel workstream upgrading Auto/single-pick to a reorderable ranked list). That per-metric ranked-list screen is NOT a second sub-screen level in the sense this doc's "max one level" rule forbids — it's the existing per-metric picker (already a `SortSheet`/screen-like construct today) just upgraded in place; the metric row stays where it is, only its destination's content changes (parallel effort's job).
6. **Leave Beast Mode stays a standalone hub-level action row**, outside every submenu and every `SettingsGroup` that contains actual settings — exactly like "Sign out" sits alone at the bottom of the main `SettingsScreen` hub, below its own last `SettingsGroup`.
7. **New sub-screens reuse this file's own existing shell** (`BackHeader` + `LazyColumn` + `SectionHeader` + `SettingsGroup` + `SettingsRowDivider`, exactly as coded today in `WorkoutSettingsScreen.kt`) rather than borrowing `SettingsSubScreen` from the main-app settings package — same visual result, zero cross-package coupling. Route naming: children of `"workout_settings"` (e.g. `"workout_settings/appearance"`, `"workout_settings/workout_defaults"`, `"workout_settings/health_sync"`, `"workout_settings/data_sources"`, `"workout_settings/import_backup"`), stacked, same Beast-accent wrapping as the parent.

## Before: current flat structure (in on-screen order)

1. SectionHeader "Theme" — theme mode segmented control (Match app/Dark/Light/System)
2. SectionHeader "Dark style" — dark palette swatches
3. SectionHeader "Light style" — light palette swatches
4. SectionHeader "Accent & font" — Beast Mode accent swatches row + "Beast Mode font" row
5. SectionHeader "Workout" — Weight unit, Default rest timer, Rest timer sound, Default exercise group, [conditional] Fix exercise details, [conditional] Remind me about missing details
6. SectionHeader "Today screen" — Show Beast Mode on Today switch
7. SectionHeader "Health Connect" — Connect/Connected row, Status, Refresh now, Import my past data, Which data is shared, Count sleep hours on
8. SectionHeader "Data source priority" — Steps, Distance, Calories, Heart rate, SpO2, Hydration, Weight, Nutrition (8 rows, each a single-app picker)
9. SectionHeader "Import & backup" — Import from Hevy, Import Beast Mode backup
10. Leave Beast Mode (no header, own group)

~35+ rows/controls on one continuous scroll, no way to jump to a category, several rows with no subtitle at all.

## After: hub + 5 sub-screens

Beast Mode Settings hub (route `workout_settings`) becomes ONE `SettingsGroup` of 5 navigation rows, plus the standalone "Leave Beast Mode" action row below it — mirroring the main `SettingsScreen` hub's own shape exactly:

```
SettingsGroup {
  "Appearance"            → workout_settings/appearance
  "Workout defaults"      → workout_settings/workout_defaults
  "Health Connect sync"   → workout_settings/health_sync
  "Data source priority"  → workout_settings/data_sources
  "Import & backup"       → workout_settings/import_backup
}
SettingsGroup {
  "Leave Beast Mode"      → onLeaveBeastMode() (unchanged action)
}
```

### Hub row text (exact)

| Row title | Icon (reuse existing) | Subtitle (new, exact text) |
|---|---|---|
| Appearance | `DaybookIcons.Palette` | "Theme, colors, and font — just for Beast Mode" |
| Workout defaults | `ImageVector.vectorResource(R.drawable.ic_workout)` | "Units, rest timer, default exercise filter, and the Today card" |
| Health Connect sync | `DaybookIcons.Heart` | "Connect, check status, and choose what syncs" |
| Data source priority | `DaybookIcons.Heart` (or `DaybookIcons.ImportExport` if two Heart icons in a row reads oddly — implementor's call, cosmetic only) | "Pick which app to trust when more than one shares the same kind of data" |
| Import & backup | `DaybookIcons.ImportExport` | "Bring in workouts from Hevy or restore a Beast Mode backup" |
| Leave Beast Mode | `Icons.AutoMirrored.Filled.ExitToApp` | *(none — matches today, matches main hub's "Sign out")* |

---

### Sub-screen 1 — "Appearance" (`workout_settings/appearance`)

Everything currently under Theme / Dark style / Light style / Accent & font, same order, same controls, headers unchanged:

- SectionHeader "Theme", subtitle "Beast Mode can look different from the rest of the app." *(unchanged — already clear)*
- Theme segmented control (Match app/Dark/Light/System) *(unchanged)*
- SectionHeader "Dark style", subtitle "Background palette used when Beast Mode is dark." *(unchanged)*
- Dark style swatches *(unchanged)*
- SectionHeader "Light style", subtitle "Background palette used when Beast Mode is light." *(unchanged)*
- Light style swatches *(unchanged)*
- SectionHeader "Accent & font" *(unchanged — add subtitle, was missing one: "The color and typeface Beast Mode uses instead of the app's own.")*
- "Beast Mode accent" row, subtitle "Re-tints the mode, its nav bar, and the session ring/glow." *(unchanged — already clear)*
- Accent swatches *(unchanged)*
- "Beast Mode font" row, subtitle = current font label or "Match app font" *(unchanged)*

### Sub-screen 2 — "Workout defaults" (`workout_settings/workout_defaults`)

- SectionHeader "Workout" *(unchanged title; add subtitle: "These are only starting points — you can always change them per workout.")*
- "Weight unit" row, trailing kg/lb segmented control *(unchanged; no subtitle needed, self-explanatory)*
- "Default rest timer" row, subtitle **rewrite** from "The starting value for a new exercise block" → **"How long the rest timer counts down by default. Change it any time from inside a workout."**
- "Rest timer sound" row, subtitle = resolved ringtone title or "Default" *(unchanged)*
- "Default exercise group" row, subtitle = current group or "All" *(unchanged; already a clear value preview)*
- [conditional] "Fix exercise details" row, subtitle = dynamic count *(unchanged)*
- [conditional] "Remind me about missing details" switch, subtitle **add** (currently none): **"Shows a reminder popup on Routines until every exercise has a muscle group and equipment set."**
- SectionHeader "Today screen" *(unchanged, now nested here instead of its own top-level section — add subtitle: "Controls what shows up on your Today screen.")*
- "Show Beast Mode on Today" switch, subtitle **add** (currently none): **"Adds a card to your Today screen showing today's planned or logged workout."**

### Sub-screen 3 — "Health Connect sync" (`workout_settings/health_sync`)

- SectionHeader "Health Connect" *(unchanged; add subtitle: "Health Connect is Android's shared health data store — Daybook reads from it, and it never leaves your phone.")*
- Connect/Connected row *(unchanged — subtitle logic already good: explains what's read/written and shared count)*
- "Status" row, subtitle = last sync status line *(unchanged)*
- "Refresh now" / "Refreshing…" row, subtitle **add** (currently none): **"Pulls the latest steps, heart rate, and other data from Health Connect right now, instead of waiting for the next automatic sync."**
- "Import my past data" / "Importing…" row, subtitle **rewrite** from "Backfill up to a year of history" → **"Brings in up to a year of your past Health Connect history, not just what's synced since you connected."**
- "Which data is shared" row, subtitle **add** (currently none): **"See which of the 12 health data types Daybook can read, and open Health Connect to change what's shared."**
- "Count sleep hours on" row, subtitle = selected day label *(unchanged; the row's own sheet title already carries the explanation — no change needed here, this is a value-preview row like "Default exercise group")*

### Sub-screen 4 — "Data source priority" (`workout_settings/data_sources`)

- SectionHeader — **unchanged text**, already clear: "Data source priority", subtitle "When more than one app writes the same kind of data to Health Connect, Daybook uses just one of them instead of adding them together."
- 8 rows, same order as today, same titles, same subtitle format (`"From <App>"` or `"Auto"`): Steps, Distance, Calories, Heart rate, SpO2, Hydration, Weight, Nutrition.
- **Room reserved for the parallel effort:** each row's destination upgrades from the current single-pick `SortSheet` (Auto / one app) to a reorderable ranked-list screen (Auto stays as the top implicit option, then a drag-orderable list of every discovered app for that metric). No row is added or removed here — this sub-screen's job is only to hold the 8 rows in one place instead of eating ~9 rows' worth of space (header + 8 rows) out of the main flat list. Row tap target, title, and current-value subtitle format are unchanged; only what opens changes, and that's the other workstream's scope.

### Sub-screen 5 — "Import & backup" (`workout_settings/import_backup`)

- SectionHeader "Import & backup" *(unchanged)*
- "Import from Hevy" row, subtitle "Bring your workout history over from a Hevy CSV export" *(unchanged — already clear)*
- "Import Beast Mode backup" / "Importing…" row, subtitle "Restore gym history, routines and health data from a Beast Mode JSON file" *(unchanged — already clear)*
- Confirm dialog copy for the backup import *(unchanged — already clear, explains what gets replaced and what doesn't)*

---

## Top-level section/submenu names (final)

1. Appearance
2. Workout defaults
3. Health Connect sync
4. Data source priority
5. Import & backup
6. Leave Beast Mode (standalone action, not a submenu)

---

Files most relevant if you want to verify or implement this:
- `app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsScreen.kt` (all rows/text/nav live here today)
- `app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsViewModel.kt` (no changes needed — same state/actions, only UI regrouped)
- `app/src/main/java/com/daybook/app/ui/settings/SettingsScreen.kt` (the hub→sub-screen pattern this design mirrors, plus confirms HydrationSettingsSection's actual current location)
- `app/src/main/java/com/daybook/app/ui/components/SettingsComponents.kt` (SettingsGroup/SettingsRow — reused as-is)
- `app/src/main/java/com/daybook/app/ui/components/Components.kt` (SectionHeader — reused as-is)
