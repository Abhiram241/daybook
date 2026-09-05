# ACCENT_UPDATES_PLAN.md

Round: three-axis accent color + fresh-install defaults + Ongoing-habit-card layout fix +
Firebase App Distribution (in-app updates). Base: repo HEAD as of 2026-09-05, versionCode 13 /
versionName "0.5.5", DB **v17** (verified live — `AppDatabase.kt` L27 `version = 17`; a
"journal habit" round landed `MIGRATION_16_17` after the last memory snapshot, which only knew
about v16). APK on disk: `Daybook-v0.5.5-login-redesign-release.apk` (most recent shipped build).

No code was changed while writing this plan. All line numbers below were read live from the
current tree, not recalled from memory.

---

## §0 — Sub-decisions (planner recommendation on each; flag only true judgment calls to the user)

| # | Decision | Recommendation | Reasoning |
|---|---|---|---|
| SD-1 | Where do the 2 new accent axes live in storage? | New `AppSettings` columns `habits_accent_color` / `intake_accent_color`, **device-local** (not synced, not backed up) — same treatment as the existing `accent_color` column. | Confirmed by grep: `accent_color`/`font_choice` appear nowhere in `data/backup/*.kt` or `data/sync/*.kt`. They were never synced to begin with, so the 3-axis split doesn't change sync/backup scope at all — no `@EncodeDefault(NEVER)` dance needed (that machinery is only for `BackupModel`/`Definitions` fields). |
| SD-2 | Schema mechanism | Additive `MIGRATION_17_18`, DB v17→v18, new `18.json`. Two new `TEXT NOT NULL` columns with `DEFAULT 'LAVENDER'`, **plus** an `UPDATE app_settings SET habits_accent_color = accent_color, intake_accent_color = accent_color` in the same migration. | SQLite `ALTER TABLE...ADD COLUMN...DEFAULT` only accepts a constant, not `= accent_color`. Without the follow-up `UPDATE`, every **existing** installed user would suddenly have Habits/Intake accents silently different from the single accent they already picked (a visual regression on upgrade). The `UPDATE` makes upgrade byte-behavior-preserving: everything stays exactly the color it already was, split into 3 knobs the user can now diverge from scratch. Fresh installs skip migrations entirely and get the Kotlin-constructor default (SD-4) instead — the SQL `DEFAULT` literal only governs a hypothetical raw `CREATE TABLE` at v18, not `ensureRow()`'s `insert(AppSettings())` path (see SD-4). |
| SD-3 | How does "Habits card follows its coloring" reconcile with the existing pastel `CardTint` per-item identity system (`CardTints.ALL` = Lavender/Peach/Mint/Butter/SlateBlue/Rose, auto-assigned by list position, REV-approved rule "CardTint owns card identity, accent = interactive only")? | **LOCKED by user (2026-09-05): accent bits only.** Keep pastel fill identity, override only `CardTint.accent` with the section's chosen `AccentColor`. Add an optional `sectionAccent: Color? = null` param to `CardTints.resolve()` / `byIndex()` / `byId()`; when supplied, return `tint.copy(accent = sectionAccent)`. Card **backgrounds** (`fill`/`fillRaised`/`onFill`/`onFillMuted`/`onFillFaint`) are explicitly untouched. | `tint.accent` is already the single field every "pops" element on a card reads: `IconTile`'s icon (`Components.kt:186`), `MiniBadge`'s fill (`Components.kt:484`), the streak flame + day-count text and the `TimeTag`/next-occurrence text (`RoutinesScreen.kt:273,287,292,316`), the Intake equivalent in `MainActivity.kt` composable card (`:233`). Overriding just this one field makes every card in the section visibly "follow" its section's accent (icon glyph color, flame color, next-time text color, badge tint) while the pastel `fill`/`fillRaised` backgrounds keep giving each list item its own distinct identity color — the v0.5.3 design rule stays intact per the user's explicit confirmation. |
| SD-4 | Fresh-install defaults — single source of truth | Change `AccentColor.DEFAULT` (`Accent.kt:23`) from `MINT` to `LAVENDER`, and `FontChoice.DEFAULT` (`Type.kt:31`) from `GROTESK` to `LITERATA`. Change the `AppSettings` Kotlin constructor defaults (`DataModel.kt:206,211`) from literal `AccentColor.MINT` / `"GROTESK"` to `AccentColor.DEFAULT` / `FontChoice.DEFAULT.storageKey` (so there is exactly one literal to ever change again). New `habitsAccentColor`/`intakeAccentColor` columns also default to `AccentColor.DEFAULT`. | Verified: `AppSettingsRepository.ensureRow()` (`AppSettingsRepository.kt:22-25`) does `database.appSettingsDao().insert(AppSettings())` — a literal Kotlin object construction. This is what actually seeds a brand-new install's single settings row, **not** the `@ColumnInfo(defaultValue=...)` SQL literal (that only fires for a raw `CREATE TABLE`, which Room never does standalone — it always runs through `ensureRow()`'s explicit insert first). So the fix must touch the Kotlin default, not just the annotation. I'll update the `@ColumnInfo(defaultValue=...)` literals too (`accent_color` MINT→LAVENDER, `font_choice` GROTESK→LITERATA) purely for schema-doc consistency with the new `18.json` snapshot — this is safe with no behavior change for upgrading users because it only affects a hypothetical fresh `CREATE TABLE` at v18, and real upgrades run the explicit `MIGRATION_17_18` ALTERs instead, never touching the pre-existing `accent_color`/`font_choice` values of a real user's row. `reduce_motion` already defaults `false` (off) — matches the screenshot, no change needed. |
| SD-5 | Do all 3 new accent axes (App / Habits / Intake) default to the *same* lavender on fresh install, or does the reference screenshot only fix the *general* App accent? | **LOCKED by user: all 3 default to LAVENDER.** Each of the 3 pickers remains independently configurable in Settings post-install (Phase 2/4 already builds them as 3 fully separate stored columns + 3 separate `Swatch` rows — defaulting them identically at install time doesn't couple them afterward). | The screenshot shows exactly one accent picker with lavender checked; the user confirmed sameness at install time, with each axis free to diverge the moment the user touches any of the 3 pickers in Settings. |
| SD-6 | Does the Ongoing-habit-card fix touch `OngoingStreak.kt` / `HabitDao` / `DetailScreen`'s STREAK branch? | **No — pure Compose layout fix inside `RoutinesScreen.kt`'s private `HabitCard`.** | Root cause (found by reading the actual composable, not guessed — see Phase 5) is a Compose-layout-only defect: the "Start" affordance is built from the shared `TextLink` component, which bakes in `.heightIn(min = 44.dp)` + `.padding(horizontal = 8.dp, vertical = 6.dp)` (`TextLink.kt:44,48`) for its own 44dp tap-target contract. The "running" state right below it (`RoutinesScreen.kt:284-301`) is a bare `Row` with **no** such padding. Because both branches sit in the exact same `Column` slot and both start with a flame icon, the flame visibly jumps ~8dp+padding to the right and the row height jumps by the `TextLink` min-height the moment a streak starts (or when you visually compare a not-started vs. a running Ongoing card side by side in the list) — this is almost certainly "not aligned properly." No data model or callback needs to change to fix this. |
| SD-7 | Firebase in-app updates — which product? | **Firebase App Distribution + its Android SDK's `checkForNewRelease()`/`updateIfNewReleaseAvailable()`.** Not Play Core In-App Updates (Play-Store-only, inapplicable — this app has no Play listing), not Remote Config (no update-prompting capability), not a custom Firestore/Storage polling scheme (reinventing App Distribution badly). | App Distribution is free on every plan including Spark (confirmed live: `firebase_get_project` shows `Billing Enabled: No` on `daybook-v2-1f578`, i.e. Spark plan, and App Distribution has never required Blaze). It is Google's purpose-built product for exactly "sideloaded testing-phase app, testers get notified/pull new builds" and ships a small Android SDK specifically for "is a newer release available, prompt the user, let them tap to install" from inside the app itself — which is the literal ask. |
| SD-8 | Which build type gets the update-check code path? | Gate it to `release` only, unconditionally (not `!DEBUG` conditionally-behind-something-else, and no 3rd build type). | This mirrors the existing `MainActivity.kt` OTA-adjacent precedent already in the codebase: `onResume` currently has "OTA check gated `!DEBUG`" per memory of the v0.5 Firebase round (confirms a `BuildConfig.DEBUG` gate pattern already exists for exactly this purpose — verify exact current line in Phase 6). The user explicitly said "this is a side loaded testing phase app" and wants the release APK they hand-distribute to check for updates; `debug` builds are dev-only and would be confusing to gate through App Distribution at all (they're already unminified/debuggable builds sent for a different reason — crash debugging, per `LOGIN_REDESIGN_RISK_FIX_REGRESSION.md`). |
| SD-9 | Version bump this round? | **LOCKED by user: YES.** versionCode 13→14, versionName "0.5.5"→"0.5.6". | User's explicit call, breaking from the last two rounds' freeze — reasoning given: this round adds real in-app-update infrastructure, and App Distribution's whole mechanism is "detect a newer versionCode," so bumping gives it something real to compare against. |
| SD-10 | Should Home's two "Your progress" stat cards (explicitly labeled Habits / Intake) also pick up their section's accent, even though Home itself otherwise stays on the general App accent? | **LOCKED by user: YES.** | User's explicit call — these two cards carry Habits/Intake identity even though they live on the Today/Home screen, so they follow the same accent-bits-only treatment as SD-3 (icon + streak-flame color only; their pastel identity — `CardTints.Mint` for Habits, `CardTints.Peach` for Intake, per `HomeScreen.kt:183-184` — stays fixed, matching SD-3's "backgrounds untouched" rule). See Phase 3b. |

---

## Phase 1 — Schema: `MIGRATION_17_18` (DB v17 → v18)

**Files:**
- `app/src/main/java/com/daybook/app/data/local/AppDatabase.kt` — `version = 17` → `18` (L27), add `MIGRATION_17_18` to the `addMigrations(...)` list (find the call near the `Room.databaseBuilder` block, mirror how `MIGRATION_16_17` was registered).
- `app/src/main/java/com/daybook/app/data/local/Migrations.kt` — new object after `MIGRATION_16_17` (currently ends ~L432):
  ```kotlin
  val MIGRATION_17_18 = object : Migration(17, 18) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE app_settings ADD COLUMN habits_accent_color TEXT NOT NULL DEFAULT 'LAVENDER'")
          db.execSQL("ALTER TABLE app_settings ADD COLUMN intake_accent_color TEXT NOT NULL DEFAULT 'LAVENDER'")
          // Preserve upgrade behavior byte-for-byte: an existing user's Habits/Intake sections
          // start at whatever single accent they'd already chosen, not a hardcoded lavender.
          db.execSQL("UPDATE app_settings SET habits_accent_color = accent_color, intake_accent_color = accent_color")
      }
  }
  ```
- `app/src/main/java/com/daybook/app/data/model/DataModel.kt` — `AppSettings` data class (currently L195-248):
  - L206: `@ColumnInfo(name = "accent_color", defaultValue = "LAVENDER") val accentColor: AccentColor = AccentColor.DEFAULT,`
  - L211: `@ColumnInfo(name = "font_choice", defaultValue = "LITERATA") val fontChoice: String = FontChoice.DEFAULT.storageKey,`
  - Append 2 new columns at the end of the constructor (append-only convention, never reorder):
    ```kotlin
    @ColumnInfo(name = "habits_accent_color", defaultValue = "LAVENDER") val habitsAccentColor: AccentColor = AccentColor.DEFAULT,
    @ColumnInfo(name = "intake_accent_color", defaultValue = "LAVENDER") val intakeAccentColor: AccentColor = AccentColor.DEFAULT
    ```
  - `DataModel.kt` needs `import com.daybook.app.ui.theme.FontChoice` if not already present — check.
- `app/schemas/com.daybook.app.data.local.AppDatabase/18.json` — generated by the `kaptKotlin`/Room schema export on next build; do not hand-write.
- `app/src/main/java/com/daybook/app/ui/theme/Accent.kt` — L23: `val DEFAULT = MINT` → `val DEFAULT = LAVENDER`.
- `app/src/main/java/com/daybook/app/ui/theme/Type.kt` — L31: `val DEFAULT = GROTESK` → `val DEFAULT = LITERATA`.
- `app/src/androidTest/.../MigrationTest.kt` (find exact path — same file that got a "+6 cases" for `MIGRATION_12_13`, "+4 x" pattern each round) — add a 17→18 case asserting: both new columns exist, `NOT NULL`, and a pre-existing row's `accent_color='CORAL'` (say) ends up with `habits_accent_color='CORAL'` and `intake_accent_color='CORAL'` after migrating (proves the `UPDATE` ran). Compile-only is fine per this project's established test-infra reality (no Robolectric/real-DB harness — mirror the existing MigrationTest style exactly).
- New unit test mirroring `StreakDefHashTest`/`PerHabitTextHashTest` precedent: **not needed** here — these columns were never synced, so there's no `definitionsHash`/backup-format churn risk to prove. (Sanity-check this assumption is still true after Phase 1 lands — confirm `BackupModel.kt` / `ExportImportRepository.kt` still have zero references to `accentColor`/`fontChoice`/the 2 new fields before calling Phase 1 done.)

**Gate:** `testDebugUnitTest` + `compileDebugAndroidTestKotlin` (schema/migration compiles + new test passes). Do not run `assembleRelease` until Phase 7 also lands, per this round's usual "gate every phase, ship once" cadence — or gate every phase per this project's normal practice, implementer's call, but the final APK is one build at the end.

---

## Phase 2 — Section-accent plumbing (`LocalAccent` override per Habits/Intake subtree)

**Current architecture (verified):** `LocalAccent` (`Accent.kt:29`) is a single `staticCompositionLocalOf` set exactly once, at the theme root, by `DaybookTheme(accent: AccentColor, ...)` (`Theme.kt:61,90-91`), called once from `MainActivity.kt:257` with the app-wide `accent` collected from `onboardingViewModel.accentColor` (`OnboardingViewModel.kt:178-179`, which is really just `SettingsViewModel`'s same `settingsRepository.observeSettings().map { it.accentColor }` pattern — one settings source, not the old "two disconnected ViewModels" bug). Every component below the root that wants the accent — `CircleIconButton` (`Components.kt:131`), `TextLink`'s default `color` param (`TextLink.kt:39`), `Swatch`'s selection check color, `PrimaryButton`, the M3 `Switch`/`RadioButton` scheme colors (`Theme.kt:82-88`), the nav bar, week-strip pill, progress fills — all read `LocalAccent.current` (or the M3 scheme, which is keyed on the same single `accent`). This is why today there is exactly one accent everywhere.

**Approach:** Compose lets an inner `CompositionLocalProvider(LocalAccent provides X)` shadow the outer one for just its subtree, with zero changes to any of the ~15+ consuming components. So: keep `DaybookTheme`'s top-level accent as the **App** accent (drives Home/Settings/Lock/Onboarding/nav-chrome — everything not inside a Habits or Intake screen), and wrap the Habits-section and Intake-section route bodies each in their own `CompositionLocalProvider`.

**Files:**
- `app/src/main/java/com/daybook/app/ui/MainActivity.kt`:
  - Near L254-256 (alongside the existing `accent`/`fontChoice`/`reduceMotion` collection), add:
    ```kotlin
    val habitsAccent by onboardingViewModel.habitsAccentColor.collectAsState()
    val intakeAccent by onboardingViewModel.intakeAccentColor.collectAsState()
    ```
  - `"routines" ->` branch (L530): wrap the existing `RoutinesScreen(...)` call in `CompositionLocalProvider(LocalAccent provides habitsAccent.color) { RoutinesScreen(...) }`.
  - `else -> FoodMedScreen(...)` branch (L537, i.e. the `"foodmed"` page): wrap in `CompositionLocalProvider(LocalAccent provides intakeAccent.color) { FoodMedScreen(...) }`.
  - `composable("add_habit")` (L582) and `composable("edit_habit/{habitId}")` (L585): wrap body in the habits provider.
  - `composable("add_foodmed")` (L589) and `composable("edit_foodmed/{taskId}")` (L592): wrap body in the intake provider.
  - `composable("detail/{itemType}/{itemId}")` (L596): branch on the already-extracted `itemType` string — `if (itemType == "habit") habitsAccent.color else intakeAccent.color`, wrap `DetailScreen(...)` in that provider. (Verify the exact string constants used for `itemType` — `"habit"` vs `"food_med"` — by grepping `goDetail(` call sites; L534/L541 show `"habit"` and `"food_med"` literally, so branch on those two.)
  - `composable("journal/{arg0}/{slotMillis}")` (L608, the food/med journal — confirmed intake-only: its only caller wires `RespondScreen`'s `isHabit=false` intake path and `HomeScreen.onNavigateToJournal`) → intake provider.
  - `composable("habit_journal_chat/{arg0}/{slotMillis}")` (L614) and `composable("habit_journal_edit/{occurrenceId}")` (L619) → habits provider (these are the newer per-habit journal feature from the "journal-habit" round, confirmed by the route name and by `Daybook-v0.5.5-journal-habit-release.apk` on disk — verify by reading `HabitJournalChatScreen`/`HabitJournalEditScreen` briefly before wiring, in case they're reused for intake too).
  - `composable("respond/{occId}?isHabit={isHabit}", ...)` (L624): read the `isHabit` nav arg (already parsed as a String default `"false"` per L627) and branch the same way. Grep for every `navigate("respond/...")` call site first (at least one exists at `DetailScreen.onOpenRespond` L604 hardcoded `isHabit=false`) to confirm whether a habit-side call site with `isHabit=true` actually exists in the current tree — if it doesn't, the `isHabit=true` branch is dead code today but still worth wiring correctly for correctness/future-proofing.
  - `"home"` page (L519), all `settings*` routes (L547-580), `settings_app_lock`, `settings_account` — **no change**, stay under the plain App-accent `DaybookTheme` root.
  - Needs `import androidx.compose.runtime.CompositionLocalProvider` and `import com.daybook.app.ui.theme.LocalAccent` if not already imported in `MainActivity.kt` — check.
- `app/src/main/java/com/daybook/app/ui/onboarding/OnboardingViewModel.kt`: add `habitsAccentColor`/`intakeAccentColor` `StateFlow<AccentColor>` next to the existing `accentColor` (L178-179), same `.map { it.habitsAccentColor }` / `.map { it.intakeAccentColor }` shape, same `stateIn(..., AccentColor.DEFAULT)` initial.
- `app/src/main/java/com/daybook/app/ui/settings/SettingsViewModel.kt`: add `setHabitsAccentColor(key: String)` / `setIntakeAccentColor(key: String)` mirroring `setAccentColor` (L174-176), plus their own `StateFlow<AccentColor>` for the Appearance screen to read current selection (mirror the `col(...)` helper already used for the customization-round settings, L191).
- `app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt`: add `setHabitsAccentColor(key: String)` / `setIntakeAccentColor(key: String)` mirroring `setAccentColor` (L30).
- `app/src/main/java/com/daybook/app/data/local/AppSettingsDao.kt`: add `updateHabitsAccentColor(key: String)` / `updateIntakeAccentColor(key: String)` `@Query("UPDATE app_settings SET habits_accent_color = :key WHERE id = 1")`-style, mirroring `updateAccentColor`.

**Gate:** `testDebugUnitTest` + `assembleDebug`.

---

## Phase 3 — Cards follow section accent (`CardTint.accent` override)

**Files:**
- `app/src/main/java/com/daybook/app/ui/theme/Tokens.kt` (`CardTints` object, L55-91): change signatures to
  ```kotlin
  fun byIndex(i: Int, sectionAccent: Color? = null): CardTint =
      ALL[((i % ALL.size) + ALL.size) % ALL.size].let { t -> sectionAccent?.let { t.copy(accent = it) } ?: t }
  fun byId(id: String, sectionAccent: Color? = null): CardTint =
      ALL[abs(id.hashCode()) % ALL.size].let { t -> sectionAccent?.let { t.copy(accent = it) } ?: t }
  fun resolve(overrideName: String?, positionalIndex: Int, sectionAccent: Color? = null): CardTint =
      (OVERRIDE[overrideName] ?: byIndex(positionalIndex)).let { t -> sectionAccent?.let { t.copy(accent = it) } ?: t }
  ```
  (Default-`null` keeps every other caller — e.g. Home's progress cards, if any use `byIndex`/`byId` directly — byte-identical; see Q4 below on whether Home's 2 progress cards should also get this treatment.)
- `app/src/main/java/com/daybook/app/ui/routines/RoutinesScreen.kt` — the `CardTints.resolve(...)` call at L106: pass `sectionAccent = LocalAccent.current` (this now resolves to the Habits accent automatically once Phase 2's `CompositionLocalProvider` wraps this screen — no need to thread a separate parameter down from `RoutineItem`/ViewModel).
- `app/src/main/java/com/daybook/app/ui/foodmed/FoodMedScreen.kt` — its equivalent `CardTints.resolve(...)`/`byIndex(...)` call (grep exact line — `FoodMedScreen.kt` did not show a `CardTints` import in the earlier grep, meaning its cards might be built inline in `MainActivity.kt`'s food-med card composable around L200-233, OR FoodMedScreen has its own private card function not yet located — **verify exact call site before implementing**, this is the one spot in Phase 3 I have not pinned to a line number). Pass `sectionAccent = LocalAccent.current` there too.
- The Neutral tint (`CardTints.Neutral`, used for Settings/form-group containers, L64-71 and referenced at `RoutinesScreen.kt:99`) — **do not** pass a `sectionAccent` override; Settings cards should keep reading whatever `LocalAccent.current` is in scope (App accent, since Settings isn't wrapped by Phase 2) automatically, or explicitly stay `DaybookColors.TextMuted` as today — confirm intent matches "Neutral" naming (i.e. Settings' utilitarian cards shouldn't visibly change from this round at all).

**Gate:** `testDebugUnitTest` + `assembleDebug`. Visually this phase has no automated test (Compose UI tests aren't in this project's toolbox, confirmed) — call it out in the regression doc for device verification instead.

### Phase 3b — Home "Your progress" stat cards follow section accent (SD-10, locked)

**File:** `app/src/main/java/com/daybook/app/ui/home/HomeScreen.kt`.

Verified: the two cards are built with **fixed** pastel tints today, not auto-assigned —
`ProgressCard("Habits", Icons.RUN, habitRatio, habitStreak, showStreaks, CardTints.Mint, Modifier.weight(1f))`
and `ProgressCard("Intake", Icons.MEDICATION, foodRatio, foodMedStreak, showStreaks, CardTints.Peach, Modifier.weight(1f))`
(`HomeScreen.kt:183-184`). `HomeScreen` itself is **not** wrapped by either of Phase 2's
`CompositionLocalProvider`s (it stays under the App accent per the `"home"` branch at
`MainActivity.kt:519`), so `LocalAccent.current` inside `HomeScreen` resolves to the App
accent, not Habits/Intake — the two section accent values must be threaded in explicitly rather
than read off the ambient local.

Reuse, don't duplicate: `MainActivity.kt` already collects `habitsAccent`/`intakeAccent` as
`State<AccentColor>` for Phase 2's provider wrapping (added near L254-256). Forward those same
two values straight into the existing `HomeScreen(...)` call (`MainActivity.kt:519-529`) as two
new parameters, e.g. `habitsAccent = habitsAccent, intakeAccent = intakeAccent`, and add matching
params to `HomeScreen`'s signature (`HomeScreen.kt:54`).

Then at the call sites (`HomeScreen.kt:183-184`), apply the same accent-bits-only override as
Phase 3, keeping each card's fixed pastel identity:
```kotlin
ProgressCard("Habits", Icons.RUN, habitRatio, habitStreak, showStreaks, CardTints.Mint.copy(accent = habitsAccent.color), Modifier.weight(1f))
ProgressCard("Intake", Icons.MEDICATION, foodRatio, foodMedStreak, showStreaks, CardTints.Peach.copy(accent = intakeAccent.color), Modifier.weight(1f))
```
`CardTint` is already a plain `data class` (`Tokens.kt:44-53`), so `.copy(accent = ...)` needs no
new helper — this is simpler than Phase 3's `CardTints.resolve(...)` override plumbing since
these two calls don't go through `resolve()`/`byIndex()`/`byId()` at all today.

No other part of Home (greeting header, reminders list, week strip, nav) changes — only these
two specific cards' icon + streak-flame colors move off the App accent.

**Gate:** folds into Phase 3's gate (`testDebugUnitTest` + `assembleDebug`); no separate build gate needed.

---

## Phase 4 — Settings UI: 3 accent pickers

**File:** `app/src/main/java/com/daybook/app/ui/settings/SettingsScreen.kt`, `AppearanceSettingsScreen` (L377-464).

Replace the single "Accent color" `SectionHeader` + `SettingsGroup` block (L385-408) with three, reusing the exact same `Swatch` row markup for each:

```kotlin
SectionHeader("App accent", subtitle = "Tints nav, Today and general buttons.")
SettingsGroup { /* Swatch row bound to viewModel.accentColor / setAccentColor — unchanged */ }

Spacer(Modifier.height(Spacing.listGap))
SectionHeader("Habits accent", subtitle = "Tints the Habits section's buttons, icons and cards.")
SettingsGroup { /* Swatch row bound to viewModel.habitsAccentColor / setHabitsAccentColor */ }

Spacer(Modifier.height(Spacing.listGap))
SectionHeader("Intake accent", subtitle = "Tints the Intake section's buttons, icons and cards.")
SettingsGroup { /* Swatch row bound to viewModel.intakeAccentColor / setIntakeAccentColor */ }
```

Keep the existing "App accent" subtitle close to today's wording ("Tints buttons, toggles and highlights across the app.") but scope it to say "general"/"app-wide" now that it's one of three, so a returning user isn't confused about why nav/Today didn't change when they picked a Habits color. Font + Accessibility sections (L410-462) are unchanged — those stay app-wide singular settings (the user only asked for accent to split 3 ways, not font/reduce-motion).

**Gate:** `assembleDebug` (no new logic to unit-test here beyond what Phase 2/3 already cover).

---

## Phase 5 — Ongoing (STREAK) habit card layout fix

**File:** `app/src/main/java/com/daybook/app/ui/routines/RoutinesScreen.kt`, private `HabitCard` (L198-391).

**Confirmed root cause** (read, not guessed): the two mutually-exclusive states of an Ongoing habit render through visibly different layout primitives in the same `Column` slot:
- *Not started* (L258-276): a `TextLink(text = "Start", leadingIcon = DaybookIcons.Flame, ...)`. `TextLink` (`TextLink.kt:42-56`) is a 44dp-min-height pill with `padding(horizontal = 8.dp, vertical = 6.dp)` baked in — designed as a standalone tap target, not a piece of inline card text.
- *Running* (L282-301): a bare `Row` with a 16dp `Icon` + 4dp `Spacer` + `Text`, **no** padding, **no** min-height.

Both start with a flame glyph at the same nominal "left edge of the Column," but the `TextLink` version sits 8dp further right (its own padding) and the row is forced ≥44dp tall vs. the running row's natural ~20dp — so the card visibly reflows/jumps between the two states, and looks structurally different from the "everyone-else" `else` branch (L303-318, which also starts flush-left with no extra padding). This reads exactly as "not aligned properly."

**Fix:** stop reusing `TextLink` for this one spot; build a bespoke flush row matching the running-state row's *geometry* (position/height/padding) while explicitly **keeping** `TextLink`'s bolder `ButtonLabel` text style — **LOCKED by user**: the "Start" row keeps its call-to-action styling; only the height/padding/alignment mismatch between the two states gets fixed, not the type style. Keep the click behavior identical:

```kotlin
habit.isStreak && habit.streakStartedAt == null -> {
    Spacer(Modifier.height(6.dp)) // match the running branch's L283 spacer, not L259's 4.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(AppShapes.pill)
            .clickableImpl(remember { MutableInteractionSource() }) {
                onStartStreakAt(
                    java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                )
            }
    ) {
        Icon(DaybookIcons.Flame, contentDescription = null, tint = tint.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Start", style = DaybookText.ButtonLabel, color = tint.accent, maxLines = 1)
    }
}
```

This: (a) matches the running row's icon size (16dp — `TextLink`'s `IconSize.Sm` is also 16dp, so the glyph size itself was never the problem, only its position) and spacer (4dp, same); (b) **keeps** `DaybookText.ButtonLabel` for the "Start" text exactly as today, preserving the intentional call-to-action-vs-status-line distinction between the not-started and running states; (c) drops the 44dp forced min-height and 8dp horizontal padding so the card's vertical rhythm matches every other card in the list — this padding/min-height removal, not the text style, is the actual fix for "not aligned properly"; (d) keeps the exact same `onStartStreakAt` callback wiring, so no ViewModel/DAO/scheduler change is needed (confirms SD-6).

Also worth a once-over while in this function (same fix, no extra scope): the comment at L277-281 already documents a *previous* alignment fix (moving "Mark as broken" out to the outer trailing-icon `Row` in v0.5.5) — re-verify after this Phase 5 change that the outer Row's `CircleIconButton`s (L326-342, `Alignment.CenterVertically` on the outer `Row` at L229) still center correctly against the new shorter/flush "Start" row height; a card whose middle `Column` just got shorter could shift the trailing icon-button cluster's vertical center relative to the `IconTile` at L230 if `IconTile`'s fixed 44dp height now exceeds the Column's natural height in the "Start" state — check whether `IconTile(size=44.dp)` was already taller than the "Start" content pre-fix (it likely was, given `TextLink`'s 44dp min-height made the whole row ≥44dp already) and confirm the outer Row's implicit height (driven by the tallest child) doesn't visibly shrink in a way that makes the icon tile look newly oversized. This is a "check after building," not a separate code change.

**Gate:** `assembleDebug` + visual device check (flagged in the regression doc — Compose UI tests aren't available in this project).

---

## Phase 6 — Firebase App Distribution (in-app update checks)

**Current state, verified live via Firebase MCP tools against project `daybook-v2-1f578`:**
- `firebase_get_environment` / `firebase_get_project`: project is **Spark (free) plan** — `"Billing Enabled: No"`. App Distribution is free on Spark, no upgrade needed.
- `firebase_list_apps(platform: android)`: one Android app, `appId 1:1054765667595:android:4c9078aa9a2181d141fe0e`, package `com.daybook.app`, 2 registered SHA-1s (debug `c1c9e735...`, release `39e62d0f...`).
- `firebase.json` has **no** `appDistribution` block; `firebase_read_resources` lists no App-Distribution init guide (Firebase's MCP resource set covers Firestore/Auth/Hosting/Crashlytics/GenAI init flows, but **not** App Distribution — its setup is CLI/console-only, not MCP-automatable from here).
- `app/build.gradle.kts` currently has **no** `firebase-appdistribution` dependency and no `com.google.firebase.appdistribution` plugin anywhere in the tree (grepped `plugins {}` blocks in both `build.gradle.kts` and `app/build.gradle.kts`, and grepped the whole repo for "appdistribution"/"AppDistribution" — zero hits outside this new plan). **Correction to stale memory**: an earlier round's notes described a `firebase-appdistribution:16.0.0-beta14` dependency and a TODO comment "~L157" as already present — neither exists in the current tree. Whatever added that dependency was since reverted or never actually landed; this phase starts from a clean slate, not a half-wired one.

**What the user must do manually (cannot be done by an agent or the Firebase MCP tools):**
1. In the Firebase console for `daybook-v2-1f578` → Release & Monitor → App Distribution → click "Get started" once (enables the App Distribution API for the project; the MCP toolset has no `appdistribution_*` function to do this remotely).
2. Create a tester group (e.g. "testers") and add the tester email addresses (including the user's own, for self-testing) — console UI, Release & Monitor → App Distribution → Testers & Groups.
3. For **CLI-based uploads** (recommended over drag-and-drop console uploads so this can be scripted later): `firebase login:ci` once to get a CI token, or simpler for a solo user, just `firebase appdistribution:distribute <path-to-apk> --app 1:1054765667595:android:4c9078aa9a2181d141fe0e --groups "testers" --release-notes "..."` using the already-authenticated `abhiramys.learn@gmail.com` CLI session (no service-account JSON needed for interactive/manual use — that's only required for unattended CI pipelines, which this project doesn't have).
4. (Only if the user later wants a fully unattended/CI upload): generate a service-account key with the "Firebase App Distribution Admin" IAM role in Google Cloud Console → IAM & Admin → Service Accounts, for project `daybook-v2-1f578`. **Not needed for the manual `firebase appdistribution:distribute` CLI flow above** — flag this as optional/deferred, not a blocking setup step.

**Code changes:**
- `build.gradle.kts` (root, `plugins {}` block, alongside `com.google.firebase.crashlytics` at L22): add
  ```kotlin
  id("com.google.firebase.appdistribution") version "5.0.0" apply false
  ```
  (Verify the latest stable version compatible with AGP 8.3.2 / Gradle at implementation time — the crashlytics plugin here is pinned to "3.0.2" as the AGP-8.3.2-known-good version; mirror that same diligence for the appdistribution plugin's version pin, and leave a comment explaining the choice like the existing plugins do.)
- `app/build.gradle.kts`:
  - `plugins {}` block (near L13): `id("com.google.firebase.appdistribution")`.
  - `dependencies {}` block (near the other `firebase-*` lines, ~L138-142): `implementation("com.google.firebase:firebase-appdistribution")` (BoM-managed, no explicit version needed — the project already pins `firebase-bom:33.1.2` at L137).
  - Add a `firebaseAppDistribution { }` DSL block, scoped inside the `release` build type only (find the `buildTypes { release { ... } }` block — same block that already sets `isMinifyEnabled`/`isShrinkResources`/`signingConfig`):
    ```kotlin
    release {
        // ...existing R8/signing config...
        firebaseAppDistribution {
            releaseNotesFile = "release-notes.txt" // or artifactType/groups if scripting uploads via Gradle instead of the CLI
        }
    }
    ```
    (This Gradle-plugin block is only needed if the user wants `./gradlew assembleRelease appDistributionUploadRelease` as the upload mechanism instead of the plain `firebase appdistribution:distribute` CLI command from step 3 above — **both work**; recommend documenting the CLI path as primary since it needs no Gradle DSL at all and is what the console's "get started" screen defaults to showing, but wire the Gradle plugin too since the dependency is already required for the SDK half below and the DSL block is a few lines.)
- New file `app/src/main/java/com/daybook/app/util/update/InAppUpdateChecker.kt` (or similar) wrapping:
  ```kotlin
  FirebaseAppDistribution.getInstance().checkForNewRelease()
      .addOnSuccessListener { release ->
          if (release != null) {
              FirebaseAppDistribution.getInstance().updateApp(UpdateProgress...)
              // or `updateIfNewReleaseAvailable()` which combines both calls + shows App
              // Distribution's own confirmation dialog + progress UI — simpler, recommended
              // for this app's low-complexity needs (no custom UI wanted per the ask).
          }
      }
      .addOnFailureListener { /* log via the existing ViewModelExt Crashlytics-report pattern, swallow */ }
  ```
  Recommend `updateIfNewReleaseAvailable()` (single call, shows App Distribution's own signIn-if-needed + "update available" dialog + download progress + install prompt, no custom UI to build) over hand-rolling `checkForNewRelease()` + a custom prompt.
- Call site: `MainActivity.kt` `onResume()` (grep the exact existing lifecycle callback — memory indicates an existing `!DEBUG`-gated OTA-adjacent check already exists near here from the original v0.5 Firebase round; **read that exact code first** before adding a duplicate/conflicting call — if it already stubs out an App-Distribution call that was never finished, complete it in place rather than adding a second one) gated `if (!BuildConfig.DEBUG)`.
- Testers must first accept the App Distribution email invite once (from step 2 above) and install the **first** distributed build via that invite link (this initial install can't come from `updateIfNewReleaseAvailable()` — the SDK only checks for updates to an *already-installed-via-App-Distribution* build; a plain sideloaded APK the user hands someone directly, outside App Distribution, will never see itself as "outdated" by this mechanism, because the SDK's tester-auth model is tied to the App Distribution install path).

**Gate:** `assembleDebug` + `assembleRelease` (verify the plugin/DSL doesn't break the existing signing config) + `testDebugUnitTest`. This phase has no meaningful unit test surface (it's a thin SDK wrapper) — note that plainly in the regression doc rather than inventing a test around a 2-line SDK call.

---

## Phase 7 — Version bump + regression doc

- **LOCKED (SD-9): bump.** `app/build.gradle.kts` `versionCode 13 → 14`, `versionName "0.5.5" → "0.5.6"`. Since this is an in-place versionCode bump over the already-installed build-13 APKs (schema is purely additive), a normal sideloaded reinstall-over-existing-install should work without an uninstall step first (unlike the "customization" round, which froze the versionCode and therefore forced an uninstall+reinstall) — confirm this holds at final build time and note it plainly in the regression doc either way.
- Write `ACCENT_UPDATES_REGRESSION.md` mirroring the established per-round convention (`CUSTOMIZATION_REGRESSION.md`/`LOGIN_REDESIGN_RISK_FIX_REGRESSION.md` shape): a device watch-list covering: (1) 3 independent accent pickers persist correctly across app restart, (2) upgrading an existing install preserves its pre-round single accent as all 3 post-round values (Phase 1's `UPDATE`), (3) a genuinely fresh install lands on Lavender/Literata/reduce-motion-off across all 3 pickers, (4) Habits cards' icon/flame/badge/next-time colors visually match the Habits accent picker and Intake cards match the Intake picker while pastel card-identity colors are unchanged, (4b) Home's two "Your progress" cards' icon/flame colors match Habits/Intake accents while their fixed Mint/Peach backgrounds are unchanged, (5) the Ongoing "Start" row now sits flush with the running-state row (no jump), keeps its bolder call-to-action text style, and the card doesn't visibly reflow height on Start, (6) App Distribution: a tester who installed via the App Distribution invite link sees an update prompt after a new `firebase appdistribution:distribute` upload; a plain sideloaded (non-App-Distribution) install correctly shows no crash/prompt from the new code path; (7) the versionCode bump itself installs cleanly over the existing build-13 APK without requiring uninstall.
- Final signed release APK build + delivery, per every prior round's closing step.

---

## LOCKED DECISIONS (user, 2026-09-05) — no open questions remain

**D1 (Task 1 reconciliation, SD-3):** Accent bits only. Pastel card-identity backgrounds (the 6-color auto-assigned palette) stay exactly as they are today; only *accent-role* elements on a card — the icon glyph inside the icon tile, the streak flame, badges, and the "next reminder" text color — switch to that section's chosen accent color. Card backgrounds are explicitly NOT touched. Implemented in Phase 3.

**D2 (Task 2 defaults, SD-5):** Fresh install — App / Habits / Intake all default to the same lavender shown in the reference screenshot. Each of the 3 pickers remains independently configurable in Settings post-install (unchanged — this was always how Phase 2/4 stores and renders them: 3 separate columns, 3 separate `Swatch` rows). Implemented in Phase 1 (defaults) + Phase 4 (UI).

**D3 (versioning, SD-9):** Bump. versionCode 13→14, versionName "0.5.5"→"0.5.6" — breaks from the last two rounds' freeze because this round ships real in-app-update infrastructure whose mechanism depends on a real versionCode change. Implemented in Phase 7.

**D4 (scope, SD-10):** Yes — the two "Your progress" stat cards on Home (Habits / Intake) pick up their respective section accent (icon + streak-flame color only, per D1's accent-bits-only rule), even though Home itself otherwise stays on the general App accent. Implemented in Phase 3b.

**D5 (Phase 5, cosmetic):** The fixed "Start" row keeps its bolder `ButtonLabel` call-to-action text style — do not flatten it to match the running-state row's plainer style. Only the height/padding/alignment mismatch between the two states is fixed, per the corrected Phase 5 code above.

*(Everything else in the brief — schema mechanics, which routes get which accent, the App Distribution product choice, the build-type gating, the exact TextLink root-cause fix — was already decided by the planner's own investigation per this project's "planner investigates and recommends" convention.)*

---

## HOW TO USE + TEST IN-APP UPDATES (first draft — refine after Phase 6 ships)

**One-time setup (you, in the Firebase console + terminal):**
1. Firebase console → `daybook-v2-1f578` → Release & Monitor → App Distribution → "Get started" (enables the API — one click, one time).
2. Same page → Testers & Groups → create a group (e.g. `testers`) → add your own email (and anyone else testing) to it.
3. You'll need the Firebase CLI logged in as `abhiramys.learn@gmail.com` (already the case in this environment) — no extra service-account file needed for manual uploads.

**Every time you cut a new build you want testers to get automatically:**
```
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
  ./gradlew assembleRelease

firebase appdistribution:distribute \
  app/build/outputs/apk/release/app-release.apk \
  --app 1:1054765667595:android:4c9078aa9a2181d141fe0e \
  --groups "testers" \
  --release-notes "Whatever changed in this build"
```
That single `firebase appdistribution:distribute` command uploads the APK and immediately notifies everyone in the `testers` group by email with an install link.

**On the tester's phone, the very first time:** they must open the email invite from App Distribution and install through that link once (this registers their device with App Distribution). A copy of the APK sent any other way (chat, USB, etc.) will never self-update through this mechanism — it has to originate from an App Distribution install for the SDK to recognize itself as "distribution-managed."

**After that first App-Distribution install:** every time you `firebase appdistribution:distribute` a new build, the app itself (checking on app resume, release builds only) will notice a newer version exists and show App Distribution's own built-in "Update available" dialog with a Download/Install button — no separate email or manual reinstall needed, no Play Store involved at any point.

**To test it yourself end-to-end** once Phase 6 ships: install the App-Distribution-distributed build once via the invite-link method above, then bump the version and `firebase appdistribution:distribute` a second build, then just reopen the already-installed app — the update dialog should appear within a few seconds of the app resuming.

---

**Plan file:** `/home/abhiram/Downloads/app-for-food/ACCENT_UPDATES_PLAN.md`.
**Phase count:** 7 (Phase 1 schema, Phase 2 accent plumbing, Phase 3 card-tint override + Phase 3b Home progress cards, Phase 4 Settings UI, Phase 5 Ongoing-card fix, Phase 6 App Distribution, Phase 7 version+regression-doc).
**Status: FINALIZED 2026-09-05.** All 5 previously-open questions (Q1-Q5) were answered by the user and are now locked in as D1-D5 above and reflected throughout §0 and the phase bodies. No open questions remain — ready to hand to the implementer.
