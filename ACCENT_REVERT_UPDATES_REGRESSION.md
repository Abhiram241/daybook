# Accent revert + update toggle + calendar fixes — regression notes

Baseline before this round: versionCode 15 / "0.5.6" (a dummy test-only bump, no code changes) /
DB v18. This round: versionCode **16** / versionName **"0.5.6"** (unchanged name — this refines
the same 0.5.6 release, it doesn't add new user-facing scope) / DB **v19**
(`MIGRATION_18_19`, additive only: `app_settings.check_for_updates_enabled INTEGER NOT NULL
DEFAULT 1`).

## 1. Reverted: 3-axis accent → single global accent

The previous round's App/Habits/Intake independent-accent split is undone, per explicit user
request ("make it like before"). Settings → Appearance is back to ONE "Accent color" swatch row;
Habits, Intake, Home, and general chrome all read the single ambient `LocalAccent` again exactly
as before that round.

**What was NOT reverted / touched:**
- Fresh-install defaults (lavender accent + Literata font + reduce-motion off) — untouched, still
  correct, now simply applies to the one restored global accent.
- The Ongoing/STREAK habit-card layout fix from the accent-updates round — untouched.
- Firebase App Distribution wiring — untouched (and extended by item 2 below).

**Schema:** `habits_accent_color` / `intake_accent_color` columns from `MIGRATION_17_18` are left
in place as inert/dead columns — no new migration was written to drop them (SQLite column drops
are needlessly risky here, and nothing reads or writes them anymore). Confirmed they were never
referenced by sync/backup code (`BackupModel`, `ExportImportRepository`) — they were always
device-local-only, so this leaves no orphaned sync behavior.

**Files touched:** `MainActivity.kt` (removed the per-route `CompositionLocalProvider(LocalAccent
provides ...)` wraps and the `habitsAccent`/`intakeAccent` state collection), `HomeScreen.kt`
(removed the two accent params, `ProgressCard` calls back to plain `CardTints.Mint`/`.Peach`),
`RoutinesScreen.kt` / `FoodMedScreen.kt` (dropped the `sectionAccent` argument to
`CardTints.resolve`), `Tokens.kt` (`CardTints.byIndex`/`byId`/`resolve` lost the now-unused
`sectionAccent` parameter entirely — no other caller used it), `SettingsScreen.kt` /
`SettingsViewModel.kt` / `OnboardingViewModel.kt` / `AppSettingsRepository.kt` /
`AppSettingsDao.kt` (removed the Habits/Intake accent setters and the two extra Settings swatch
rows).

**Device-verify:** every screen's interactive accent (nav, chips, buttons, switches, cards' icon/
flame/badge/next-text) should be the ONE color chosen in Settings → Appearance → Accent color,
with no separate Habits/Intake pickers visible.

## 2. "Check for updates" Settings toggle (auto-off on decline)

New row: Settings → Notifications & alarms → "Updates" section → "Check for updates" toggle
(default ON). `MainActivity.onResume()` now only calls `InAppUpdateChecker.checkForUpdate()` when
this setting is true. `InAppUpdateChecker` takes a new `onAuthenticationCanceled` callback, fired
only on `FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED` (confirmed against the
real 16.0.0-beta14/api-16.0.0-beta11 SDK sources — this is the actual enum value, not a guess) —
i.e. only when a tester explicitly cancels the "Enable testing features" sign-in prompt. Every
other failure (`NETWORK_FAILURE`, `UPDATE_NOT_AVAILABLE`, `HOST_ACTIVITY_INTERRUPTED`, etc.) does
NOT touch the toggle, since those are transient/unrelated to tester intent.

**Device-verify:** decline the "Enable testing features" prompt once → the toggle should flip off
by itself (visible next time you open Settings) and the prompt should not reappear on future app
opens until you flip it back on manually.

## 3. Calendar bug fixes (Home screen week-strip / month grid)

Both bugs live in `ui/components/WeekStrip.kt`, shared by the collapsed week strip and the
expanded month grid (both render through the same `DayCell`).

**Bug A — selection "shifts" after picking a date.** Root cause: two effects keep the (visible or
hidden) week pager in sync with `selectedDate` — "Sync 1" scrolls the pager to match an externally
changed `selectedDate` (e.g. a month-grid tap in a different week), and "Sync 2" carries the
selection forward by weekday-offset whenever the pager settles on a new page (intended for a
genuine user swipe). These two could race: if `selectedDate` changed AGAIN while Sync 1's
programmatic scroll was still animating, Sync 2 would fire once that stale scroll settled and
recombine it with the NEWEST `selectedDate` — silently overwriting the user's latest pick with a
third, unintended date. Fixed by tracking the page Sync 1 itself just targeted and having Sync 2
ignore exactly the one settle event that scroll produces, so only a genuine user swipe still
carries the weekday-offset forward.

*Device-verify:* tap around the month grid across week/month boundaries, including a quick second
tap before the first settles — the finally-selected date should always be the one you actually
tapped, never a different neighboring date appearing a moment later.

**Bug B — no guard against selecting a future date.** `DayCell` already computed `isFuture` (used
for dimming) but never used it to block the tap. Fixed: the click handler now no-ops when
`isFuture` is true, in both the week strip and month grid (same shared `DayCell`). Also added the
same guard to the date-range export pickers in Settings → Data (`DaybookDatePickerDialog`'s
existing `maxDate` parameter, previously passed as `null`/unrestricted there) — exporting a future
date range made no sense either, and the parameter already existed for exactly this purpose (it
was already used by the Ongoing-habit "Start" date picker).

*Device-verify:* future days in both the week strip and month grid should be untappable (dimmed,
no selection change); the export date-range pickers should not let you pick a date past today.

## Final 4-gate (this round)

`testDebugUnitTest`: 446 tests, 0 failures. `assembleDebug`: BUILD SUCCESSFUL. `assembleRelease`:
BUILD SUCCESSFUL (R8 + lintVitalRelease clean, real release keystore, SHA-1
`39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c` — matches `RELEASE_SIGNING.md`).
`compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL. Added 3 new `MigrationTest` cases
(`migrate18To19_addsCheckForUpdatesEnabledColumn`, `migrate18To19_defaultsToEnabled`,
`migrateAll_3To19`) plus the updated `fullOpenAtLatestVersion`. No new JVM unit tests were needed
for the WeekStrip fixes (pure Compose UI logic, no JVM-testable pure functions extracted — matches
this project's established "no Robolethin/Compose-UI-test deps" constraint).

APKs delivered: `Daybook-v0.5.6-build16-release.apk` (7,162,421 B) and
`Daybook-v0.5.6-build16-debug.apk` (23,450,898 B), both at repo root.

Baseline before this round: versionCode 15 / "0.5.6" (dummy test bump) / DB v18 / APK
`Daybook-v0.5.6-login-redesign-release.apk` (last real-feature release).
