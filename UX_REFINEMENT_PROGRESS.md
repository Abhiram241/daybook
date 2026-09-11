# UX_REFINEMENT_PROGRESS.md

`UX_REFINEMENT_PLAN.md` implementation — COMPLETE. Final verification gate is green.

## Gate result

```
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
  ./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```
BUILD SUCCESSFUL (124 actionable tasks). `testDebugUnitTest`: 498 tests, 0 failures, 0 errors
(469 pre-existing + ~29 new). `assembleRelease` (R8 + lintVitalRelease) clean.
`compileDebugAndroidTestKotlin` clean (includes the new `migrate20To21*` MigrationTest cases).

Release APK: `app/build/outputs/apk/release/app-release.apk`, 7,429,511 bytes,
mtime 2026-09-11 08:00:22. `apksigner verify --print-certs`: Signer #1 SHA-1
`39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c` — matches `RELEASE_SIGNING.md`.

No git commit / push / Firebase push performed (`git log` still at `bde7c7f`).
versionCode 23 / versionName "0.5.6" unchanged.

## Item-by-item

**Item 1 — Legibility hunt: DONE.**
- §1.1 `SegmentedControl.kt` height-0 bug fixed (`matchParentSize` wrapper + border +
  `heightIn(min=36)` per segment + `ButtonLabel` + unselected=TextPrimary/selected=OnAccent).
- §1.3 `onAccentInk`/`LocalOnAccent`/`DaybookColors.OnAccent` added (`Accent.kt`, `Tokens.kt`);
  all 16 `OnSolid`→`OnAccent` call sites repointed (`Components.kt` CircleStyle.Solid/
  DaybookChip x2/Swatch/PrimaryButton x2, `Avatar.kt` x2, `Forms.kt`, `WeekStrip.kt`,
  `SortSheet.kt`, `SettingsScreen.kt` x2, `AppLockSettingsScreen.kt`,
  `HabitJournalChatScreen.kt`, `SegmentedControl.kt`).
- Findings 2,3 (part of §1.1), 4 (GhostButton disabled), 5 (CircleIconButton disabled), 6
  (DaybookChip), 7 (WeekStrip), 8 (SortSheet radio/facet), 9 (TimePickerComponents), 10
  (SettingsScreen footer), 11 (AboutSettingsScreen footer), 12 (Forms placeholder, LD14) — all
  applied. Row 13 and L3 are explicit no-ops per the plan.
- L1 (Streaks subtitle contradiction) and L2 (Greeting subtitle, LD14) applied in
  `SettingsScreen.kt`.

**Item 2 — Onboarding: DONE.**
- `OnboardingViewModel.kt`: 7 `Teach` steps with verbatim §2.2 copy; `TeachIllustration` gains
  `STREAKS`/`PRIVACY`; tri-state `hasExistingData: StateFlow<Boolean?>` + pure
  `hasExistingData(habitCount, intakeCount)` predicate; `HabitRepository` +
  `FoodMedRepository` injected (repo Flows only — no DAO/schema change); `darkStyle`/
  `lightStyle`/`cornerScale` StateFlows added (Eagerly, mirror-seeded).
- `OnboardingScreen.kt`: NameAsk helper line, PermissionPrimer body copy, `ReadyStep` 3-way
  branch (reviewMode / hasExistingData==true / false-or-null) with a fixed `heightIn(min=44dp)`
  Box reserving the link's height so nothing shifts on resolve (LD9).
- `OnboardingIllustrations.kt`: `StreaksMock()` + `PrivacyMock()` added and wired into the
  `when`; Journalling step reuses `TeachIllustration.INTAKE` (LD8, no new mock).
- `WizardStepTest` updated (5→7, PermissionPrimer/Ready indices 7/8, illustration-reuse test
  replacing the old strict-distinct assertion). New `HasExistingDataTest`.

**Item 3 — Dark/Light style pickers: DONE.**
- New `ui/theme/ThemeStyle.kt` — `DarkStyle` (CHARCOAL/AMOLED/WARM/NAVY) and `LightStyle`
  (PAPER/PURE/CREAM/SEPIA), 4 entries each, exact hexes from §3.5; `StyleGround`/`Signals`
  internal; `darkSchemeFor`/`lightSchemeFor`.
- `Tokens.kt`: `DaybookColorsDark`/`DaybookColorsLight` now derived from
  `darkSchemeFor(CHARCOAL)`/`lightSchemeFor(PAPER)` (byte-identical, guarded by tests);
  `LocalIsDark` replaces the `=== DaybookColorsDark` referential check; `CardTints.light`
  repointed; `DaybookColors.OnAccent` shim added; `AppShapes` converted to a
  `@Composable`-getter shim over `LocalDaybookShapes` (see Item 4).
- `Theme.kt`: `DaybookTheme` takes `darkStyle`/`lightStyle`/`cornerScale`; M3 `colorScheme`
  copies every resolved ground role + `onAccent`; provides `LocalIsDark`/`LocalOnAccent`/
  `LocalDaybookShapes`; old file-scope `DaybookShapes` val deleted.
- Schema: `MIGRATION_20_21` (3 additive columns, exactly as specified), `AppDatabase.version`
  20→21, `DatabaseModule` registers the migration, `AppSettings` entity + `AppSettingsDao` (3
  UPDATE queries) + `AppSettingsRepository` (setters + SP-mirror readers) all updated.
  `21.json` confirmed regenerated and correct (verified via the schema JSON directly).
- `data/ThemeModePrefs.kt` → `data/ThemePrefs.kt` (git mv, not staged/committed), object
  renamed, 3 new keys added with `clampCornerScale` applied on read. All 3 call sites updated.
- `MainActivity.kt`: collects and passes darkStyle/lightStyle/cornerScale into `DaybookTheme`;
  `applyWindowTheme()` resolves the style ground `bg` for the pre-inflate window background.
- `SettingsViewModel.kt`: darkStyle/lightStyle/cornerScale flows + setters (`setCornerScale`
  drops no-op writes per LD12).
- New `ui/components/StyleSwatchRow.kt` (two thin overloads over a private shared core —
  `DarkStyle`/`LightStyle.ground` stays internal per LD15, read directly since single module).
- `AppearanceSettingsScreen` (`SettingsScreen.kt`): two new groups ("Dark style" / "Light
  style") between the Theme toggle and Accent color, using `StyleSwatchRow`.
- New `DarkStyleTest` / `LightStyleTest` (default fallback, entries.size==4, D8 byte-identity
  against a hardcoded historical literal, per-style contrast floors from §3.5). New
  `OnAccentInkTest`. `AccentColorThemeTest` checked — doesn't pin the dark scheme by literal,
  no change needed, still passes.
- `MigrationTest.kt` (androidTest): added `migrate20To21_addsDarkLightStyleAndCornerScaleColumns`,
  `migrate20To21_preservesRowsAndDefaultsToTodaysLook`, `migrateAll_3To21`; updated
  `fullOpenAtLatestVersion` to register `MIGRATION_20_21`.

**Item 4 — Corner roundness slider: DONE.**
- New `ui/theme/Shapes.kt` — scale constants (0.0–1.75, default 1.0, 0.25 step, 6 interior
  slider steps), `DaybookShapeScheme`, `scaledAppShapes`, `scaledM3Shapes`,
  `clampCornerScale`, `LocalDaybookShapes`.
- `AppShapes` object converted to a `@Composable`-getter shim; two file-scope non-composable
  readers that broke under the shim were fixed by moving them inside their composable body
  (`Components.kt` `SoftCard`'s local `CardShape`, `Navigation.kt` `FloatingPillNav`'s local
  `NavShape`) — no other file-scope readers existed (verified by grep).
- `Theme.kt` wires `scaledAppShapes`/`scaledM3Shapes` off `cornerScale` and provides
  `LocalDaybookShapes`.
- Corner slider group ("Corners", Square/Round captions, live value caption mirrored into
  `stateDescription`) added to `AppearanceSettingsScreen`, under the two style groups.
- New `ScaledAppShapesTest` (scale 0/1/1.75 exact values, monotonicity, segmented/navPill
  fixed at every scale, clamp bounds, M3 `medium == card` equivalence).

## Migration diff (`data/local/Migrations.kt`, appended after `MIGRATION_19_20`)

```kotlin
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN dark_style TEXT NOT NULL DEFAULT 'CHARCOAL'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN light_style TEXT NOT NULL DEFAULT 'PAPER'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN corner_scale REAL NOT NULL DEFAULT 1.0")
    }
}
```

Exactly the 3 columns specified — nothing else touched a Room entity/DAO/migration/sync
payload/`BackupModel`/Firestore shape. Confirmed by grep: `BackupModel.kt` and
`data/sync/ContentHash.kt` reference neither `app_settings` nor any of the new field names.

## STOP-and-ask items

None. No point in the plan required a schema/DAO/sync change beyond what was explicitly
sanctioned (`MIGRATION_20_21` + its 3 DAO UPDATE queries, which mirror the existing
`updateThemeMode` pattern exactly as the plan pre-authorized).

## Confirmations

- No `git commit`, `git push`, branch/tag, or Firebase push performed at any point.
- `versionCode` (23) and `versionName` ("0.5.6") unchanged; no dependency/SDK/Gradle/plugin
  version changed.
- Defaults reproduce today's exact look: `DarkStyleTest`/`LightStyleTest` assert
  `darkSchemeFor(CHARCOAL)`/`lightSchemeFor(PAPER)` equal a hardcoded historical literal (not
  the derived constant itself, to avoid a circular/tautological check), and `AppShapes` at
  `cornerScale = 1.0` reproduces every original dp value (asserted in `ScaledAppShapesTest`).
- Known limitation handed back per the plan (LD4): `CardTint` pastels are not
  style-parameterized this round — they stay mode-only (`LocalIsDark`), so on Espresso and
  Midnight the pastel card tints are tuned for Charcoal and read slightly off-temperature.
