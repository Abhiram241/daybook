# HEALTH_AND_WORKOUT_PLAN.md

**Status: DRAFT (revision 5, 11 Sep 2026) — awaiting user sign-off. PLAN ONLY. No `.kt`,
`.xml`, `.gradle.kts` or `.json` file was created or modified to produce this document.**
The only file written by any planning pass is this one.

> **What changed in revision 5 — Beast Mode gets an inside.**
> Revision 4 settled *how you get into* Beast Mode (hold "Today"). Revision 5 settles **what is
> in there**, from six further user directives. Revision 4's "Beast Mode has no bottom nav, just
> a `×` in its header" detail is **overridden**; everything else in revision 4 stands.
>
> - **D1 — Beast Mode has its own Settings screen (§3.8.2).** New route **`"workout_settings"`**,
>   new file **`ui/workout/WorkoutSettingsScreen.kt`** + `WorkoutSettingsViewModel.kt`, reachable
>   **only from inside Beast Mode** (the gear in its landing header). Weight unit, Beast Mode
>   accent, default rest timer and the Today-row toggle **all move out of Daybook's main
>   Settings** and live there. Daybook's main Settings keeps **exactly one** Workout row —
>   **`Open Workout`** — because that is an *entry point*, not a setting, and it is R21's
>   accessibility floor. §3.8 rewritten.
> - **D2 — the landing screen is now "My routines" (§3.7.4), not a list of past sessions.** A new,
>   deliberately minimal **Routine** concept is designed: two new tables
>   (`workout_routines`, `workout_routine_exercises`), one new nullable column
>   (`workout_sessions.routine_id`), a creation flow (`"routine_edit?routineId={routineId}"`,
>   §3.7.5), and a `startSessionFromRoutine` that pre-populates the live session. **§3.1.2 R2
>   ("templates stay deferred") is re-decided in place.** This is *not* the PRD's
>   Programs/periodization layer, which stays in Round C (§C.3.2, reworded).
> - **The ad-hoc button is renamed. Final copy: `Start an empty workout`.** §3.7.4 argues it.
> - **D3 — the landing header mirrors Today / Habits / Intake exactly** (§3.7.4): `ScreenHeader`
>   with the title and a count subtitle on the left and a trailing 40 dp `CircleIconButton` in the
>   same corner slot `Avatar` occupies on `RoutinesScreen.kt:65` / `FoodMedScreen.kt:63` — except
>   it opens **Beast Mode's** settings, not the app's.
> - **D4 — Beast Mode has its own bottom nav (§3.6.7).** The same `FloatingPillNav`, three
>   destinations of its own: **Routines** (`"workout"`, leftmost) · **History**
>   (`"workout_history"`) · **Exercises** (`"workout_library"`). Revision 4's
>   "no bottom nav in Beast Mode" and the header `×` are **withdrawn**; §3.6.0's
>   "`DaybookScaffold` — no change" row is amended.
> - **D5 — the exit gesture is the enter gesture, mirrored (§3.6.8).** Press-and-hold the
>   **leftmost** Beast Mode nav item (Routines) to leave Beast Mode and land on Today. Same
>   `combinedClickableImpl`, same ~500 ms platform threshold, same 120 ms dead zone, same ramp,
>   same haptic, same reduced-motion branch — **one implementation, two configurations**.
>   `FloatingPillNav`'s hard-coded `item.route == "home"` gate is generalised to a
>   `longPressRoute` parameter.
> - **D6 — routine *import* is deliberately not designed.** `workout_routines.source` exists as a
>   forward-compatible provenance hook mirroring `WorkoutSession.source`; see §3.2.1.
> - **Consistency passes:** §3.8.1's Beast accent now wraps the **whole mode including its nav
>   bar**, and the mechanism moves from "each route wraps its content" to one provider around the
>   `DaybookScaffold` call — because the nav is drawn *outside* the `NavHost` (§3.8.1, R25).
>   §3.5's SQL, §3.7's screen table, §4.1/§4.2/§4.4's sync wiring, §4.7's phase list, §8's risks
>   (R24–R26 new) and §9 (**Q32 rewritten in place; Q34–Q35 appended**) all follow.
>
> **Nothing else changed.** §2.1's architecture rejection, §2.4's Round 0 (untouched — it is
> being implemented separately), §3.9's Hevy history import, Round B, Round C and revision 4's
> long-press *enter* mechanics are all intact.

> **What changed in revision 4 — one decision, threaded through everything it touches.**
> The user reviewed the Workout mockups and made a **firm navigation decision that overrides
> revision 3's §3.6**:
>
> > **Workout mode ("Beast Mode") is NOT a fourth bottom-nav tab. It is reached by
> > long-pressing the existing "Today" item in the bottom nav. A normal tap on Today still
> > opens Today. A long-press enters Beast Mode as a distinct, full-screen mode with its own
> > chrome and no bottom nav, with a clear way back out.**
>
> Everything that assumed the four-tab design has been rewritten, not patched around:
> - **§3.6 replaced wholesale.** `NavConfig.ALL_ROUTES` **stays at three routes** and
>   `NavConfigTest` needs **no change**. Workout is now its own top-level destination graph
>   (`"workout"` + five stacked routes) parented to the long-press handler, not a pager page.
>   The gesture, the press-and-hold feedback, the haptic, the discoverability hint and the exit
>   path are all specified exactly (§3.6.1–§3.6.6).
> - **§3.5 migration simplified.** The
>   `UPDATE app_settings SET nav_tabs = nav_tabs || ',workout'` statement is **deleted** —
>   there is no tab to make visible. `MIGRATION_21_22` is now **100 % additive**, which removes
>   the round's only non-additive statement and the sign-off caveat attached to it. Two new
>   device-local columns replace it (`workout_hint_state`, `workout_today_card_enabled`).
> - **§3.8 Settings.** The proposed *"Show Workout tab"* toggle is **gone**;
>   `NavigationSettingsScreen.kt` is **not touched at all** this round. The Workout settings
>   group gains a permanent **"Open Workout"** row instead — a guaranteed, always-visible way
>   in that does not depend on remembering a gesture.
> - **§3.8 / Beast Mode visual identity re-stated.** The workout accent is no longer "a fourth
>   accent axis in a shared pill nav"; it is the accent for an entire full-screen mode, which
>   is why the recommended default changes to **Coral**. See §3.8.1.
> - **Accessibility raised, not buried.** A long-press-only entry point locks out TalkBack and
>   switch-access users, users with motor impairments, and anyone who forgets the gesture
>   exists. **§9 Q32 (new)** puts the visible-fallback question to the user directly, with a
>   recommendation. **Q33 (new)** asks about the one-time hint. **Q8 and Q11 are rewritten in
>   place** (the "should the tab appear automatically?" question no longer has a referent).
>   **§8 gains R21–R23**; **R6 (four nav labels clipping) is withdrawn** — there is no fourth
>   label.
> - **Round A phase list (§4.7): A5 shrinks and moves.** No `NavConfig` change, no
>   `NavigationSettingsScreen` change, no nav icon in the pill — but a new gesture, a new
>   coach-mark component and a new mode root.
>
> **Nothing else in this document changed.** Round B (Health Connect), Round 0 (the intake
> fix), the Hevy import, the schema, the sync integration and §2.1's architecture rejection are
> untouched apart from wording where they said "the Workout tab".

> **What changed in revision 3.** The user supplied a large external **PRD v2 — "Universal
> Training Tracker"** (a serious, well-argued document proposing a universal
> Activity/Routine/Program/Workout model with a Backend-API + PostgreSQL/Supabase +
> mutation-queue sync architecture), a **comparison table of Health-Connect-integrating apps**
> (Strava, MyFitnessPal, Google Health, Google Fit, Samsung Health), and a **real bug report**
> ("if I log an intake item by mistake, I must be able to delete its content or reset its
> status to not-logged"). Revision 3 responds to all three:
> - **§0 — stale facts corrected.** The UX-refinement round is now **committed** (`5f536d4`)
>   and `versionCode` is **24**, not 23. The working tree is clean. Revision 2's note about an
>   uncommitted round on top of the code is **obsolete and has been removed**, and so has the
>   sequencing question it would have raised.
> - **§1 — new constraint C9**: no error may be silently swallowed, anywhere. **C6 amended** to
>   match.
> - **§2.1 (new)** — *Why we are not adopting the PRD's backend architecture.* An explicit,
>   reasoned rejection of Postgres / Supabase / a hand-written Backend API / a mutation queue
>   with `operation_id` + `version` + conflict resolution, against the **verified** capabilities
>   of Daybook's shipped Room + Firestore sync layer.
> - **§2.2 (new)** — the phased roadmap, and the single biggest open decision in this document:
>   **concrete Round A now** vs **the PRD's universal Activity/Metric architecture from day
>   one**. Put to the user as **Q22**, not decided here.
> - **§2.3 (new)** — the PRD's engineering advice that is genuinely free and genuinely good,
>   listed as where-it-lands pointers.
> - **§2.4 (new)** — **ROUND 0**, a fast-track fix for the intake-reset bug, deliberately
>   separated from Rounds A and B. **The data layer for this already exists and is correct**
>   (§2.4.1) — the defect is a three-place UI gap.
> - **§3.9.11 (new)** — Hevy import **pre-parse validation, success and failure states**, with
>   exact user-facing copy for every branch, and an explicit statement of whether partial
>   failure can occur.
> - **§3.10 (new)** — *Performance discipline*, the PRD's non-architectural engineering rules
>   folded into Round A as implementable requirements.
> - **§6.1 — replaced wholesale** with a comprehensive pass over **all 42 real Health Connect
>   record types**, verified against Google's published data-type list, each with an explicit
>   MVP / later / never decision and a specific reason.
> - **§6.2 / §6.3 — every failure branch now has user-visible copy** (C9).
> - **ROUND C (new, after Round B)** — the PRD's larger vision mapped onto Daybook's
>   architecture as an explicitly **unscheduled backlog**, so it is neither absorbed silently
>   into Round A nor thrown away.
> - **§8** — risks R16–R20 added. **§9** — **Q22–Q31 appended**; Q1–Q21 untouched.
> - **§5.1 corrected**: `1.1.0-alpha08`'s release notes say it *"added an API to check feature
>   availability"*, so revision 2's claim that alpha08 lacks `HealthConnectFeatures` is
>   probably wrong. Flagged for a compile check at B0 rather than re-asserted.
>
> **Nothing from revision 2 was removed** except the two facts that were verifiably stale.

> **What changed in revision 2.** The user supplied five reference screenshots of **Hevy**
> (a published gym-tracking app) showing the Add-Exercise and live-session surfaces they
> want Workout mode to resemble, plus a real **Hevy CSV export**
> (`workout_data.csv`, 34 lines) they want to import their existing history from.
> Revision 2 therefore reworks **Round A only**:
> - §3.1 scope — the deferred list is re-decided item by item, not silently kept.
> - §3.2 schema — a fourth table (`workout_exercises`) so an exercise block inside a session
>   can carry its own note, rest timer and superset id; `is_warmup` replaced by `set_type`.
> - §3.3 catalog — a per-exercise **primary muscle** + **equipment** taxonomy (the old coarse
>   `category` is gone), plus a Hevy-name alias map.
> - §3.4 DAOs — per-set-number "previous" lookup and personal-record detection as **queries**,
>   not stored columns.
> - §3.7 UI — richer Add-Exercise and live-session screens.
> - **§3.9 (new)** — Hevy CSV import, end to end.
> - §4.2 / §4.4 / §4.7 — wire model, the six export/import call sites, and the phase list.
> - §9 — **Q10 rewritten in place** (the rest-timer answer flips from "no" to "yes, in-app
>   only"); **Q14–Q21 appended.** Q1–Q9 and Q11–Q13 are untouched and keep their numbers.
>
> **Round B (Health Connect) is unchanged by revision 2.**

Two new feature efforts — plus, as of revision 3, **one bug fix that is deliberately kept
separate from both**:

- **Feature 1 — Health sync**: pull the user's Mi Band 10 (and any other band/watch whose
  companion app writes to Health Connect) data into Daybook.
- **Feature 2 — Workout mode ("Beast Mode")**: a first-class gym-logging surface, reached by
  **long-pressing Today** in the bottom nav and entered as a **separate full-screen mode** —
  *(REV4: not a fourth tab alongside Today / Habits / Intake; see §3.6)*.
- **Round 0 *(REV3)* — reset a mistakenly-logged intake entry** (§2.4). Small, no schema change,
  no sign-off needed, recommended to ship on its own before either feature.
- **Round C *(REV3)* — the external PRD's universal-activity-tracker vision**, recorded as an
  explicitly **unscheduled backlog** after Round B, not folded into Round A and not discarded.

There is a **"Open questions for the user"** section at the very bottom (§9). Everything
before it is written as *"here is what I recommend and why"*, not as settled fact. Nothing
in this document is locked until the user answers §9.

---

## 0. Verified baseline (read from the live repo, not assumed)

The task brief carried some stale numbers. What is actually in the tree, at branch `main`,
commit **`66bca0c`**, **working tree clean** (the only untracked file is this plan):

| Thing | Brief said | Actually is |
|---|---|---|
| Room DB version | 7 | **21** (`MIGRATION_2_3` … `MIGRATION_20_21`, `app/schemas/…/3.json`–`21.json`) |
| Month-partitioned Firestore sync | "in-flight design, not yet implemented" | **Already shipped.** `formatVersion = 3`, `users/{uid}` parent + `users/{uid}/months/{YYYY-MM}`, gzipped blobs, `MonthPartitioner`, lazy hydration, eviction, pinning, D2 conflict flow — all live in `data/sync/CloudSyncRepository.kt` |
| versionName / versionCode | "~0.5 / 0.5.1" | **0.5.6 / 24** *(REV3 — was 23 at revision 2; `66bca0c` bumped it)* |
| Tabs | Today / Habits / Intake in a `HorizontalPager` | Correct — and the tab set is **user-configurable** (`app_settings.nav_tabs` CSV + `ui/NavConfig.kt`). *(REV4: and it **stays** three routes — Workout does not join it, §3.6.)* |

> **REVISION 3 — a stale fact from revision 2, corrected.** Revision 2 recorded that "the
> working tree has an uncommitted, not-yet-approved UX_REFINEMENT round sitting on top of the
> current code". **That is no longer true.** `git log` now shows
> `5f536d4 UX refinement: theme styles, corner roundness, onboarding, legibility (v0.5.6)`
> followed by `66bca0c Bump versionCode to 24 for Firebase App Distribution push`, and
> `git status --porcelain` reports **nothing but this untracked plan file**. So there is no
> "build on top of uncommitted work or commit it first?" question to ask — **every round in
> this document, including Round 0 (§2.4), starts from a clean, committed `main` at build 24.**
> The version plan in §2 is adjusted accordingly.

Other load-bearing facts confirmed by reading:

- **Single Gradle module `:app`.** `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`,
  AGP **8.3.2**, Gradle wrapper **8.6**, Kotlin **2.0.21** (K2) + `kapt`, JDK 17 target.
- Every third-party pin in `app/build.gradle.kts` carries an explicit comment of the form
  *"X is the last release that builds against compileSdk 34"* — `firebase-bom:33.1.2`,
  `androidx.credentials:1.3.0`, `androidx.biometric:1.1.0`,
  `androidx.security:security-crypto:1.1.0-alpha06`. **compileSdk 34 is a deliberate,
  documented freeze, not an accident.** §5.1 is entirely about this.
- Hilt DI: `di/DatabaseModule.kt` (Room + every repository) and `di/FirebaseModule.kt`.
  `DaybookApplication` is `Configuration.Provider` with a `HiltWorkerFactory`; WorkManager's
  own startup initializer is stripped in the manifest.
- WorkManager jobs today: `WindowRefreshWorker` (daily; also drives
  `CloudSyncRepository.runMaintenance()`, and runs signed-out) and `SyncFlushWorker`
  (one-shot on `ON_STOP` when a push is pending).
- Sync bookkeeping deliberately lives in **SharedPreferences** (`SyncStateStore`, file
  `daybook_prefs`), *never* Room — because a Room write would re-trigger the
  `InvalidationTracker` observer that is the local-change signal. Any new sync-adjacent
  cursor/token must follow the same rule.
- `CloudSyncRepository.DATA_TABLES` is the tracked-table array, guarded by a tripwire unit
  test `data/sync/DataTablesSyncTest.kt` that asserts it matches `AppDatabase`'s entity list.
- Backup/sync wire model is `data/backup/BackupModel.kt` (`DaybookBackup` v2 =
  `meta` + `definitions` + `days`). `ContentHash` hashes **`definitions` + `days` only** —
  never `meta`. `ExportImportRepository` is the single translator between Room and that model.
- Design system: `ui/theme/Tokens.kt` (`DaybookColors`, `Spacing`, `DaybookText`, `Motion`,
  `CardTints`), `ui/theme/Shapes.kt` (`AppShapes` via `LocalDaybookShapes`, corner-scale
  aware), `ui/components/Components.kt` (`SoftCard`, `SectionHeader`, `PrimaryButton`,
  `GhostButton`, `CircleIconButton`, `EmptyState`), `ui/components/Navigation.kt`
  (`DaybookScaffold` + `FloatingPillNav`), `ScreenHeader`, `SegmentedControl`, `Sheets`,
  `SortSheet`, `ConfirmDeleteDialog`, `UndoSnack`, `StickySaveBar`.
- 87 unit tests + 3 instrumented tests already exist. The project's habit is
  **pure-function extraction + a unit test per decision**. New work is expected to match.

**REVISION 5 — five further facts, read from the live code, that §3.6.7, §3.7.4 and §3.8.2
depend on:**

- **The Habits tab's route id is literally `"routines"`.** `NavConfig.kt:14` —
  `val ALL_ROUTES = listOf("home", "routines", "foodmed")`, and `MainActivity.kt:539` maps
  `"routines" to NavItemSpec("routines", habitsIcon, "Habits")`. **So the word "routines" is
  already taken, by Habits, at the route level.** Beast Mode's new Routine concept (§3.2.1) must
  therefore **never** use `"routines"` as a route id, and its screen file must not be called
  `RoutinesScreen.kt` (`ui/routines/RoutinesScreen.kt` already exists and is the **Habits**
  screen). §3.6.6 uses `"workout"` for the landing and `WorkoutHomeScreen.kt` for the file.
  **R24** records this as a live trap.
- **`ScreenHeader` is the tab-screen header, and its trailing slot is a `RowScope`.**
  `ui/components/ScreenHeader.kt:29` — `ScreenHeader(title, subtitle, modifier, actions)`, a
  `Column` of a `Row(BigHeadline(weight 1f), actions())` over an optional muted subtitle. Both
  tab screens call it identically: `RoutinesScreen.kt:65` passes
  `title = "Habits", subtitle = "${habits.size} active"` and an `actions = { Avatar(…, size =
  40.dp, onClick = onNavigateToSettings) }`; `FoodMedScreen.kt:63` does the same for Intake.
  **That is the exact pattern §3.7.4 mirrors** — same composable, same 40 dp trailing control,
  same corner.
- **`CircleIconButton` already takes a size and is the app's icon-button.**
  `Components.kt:118` — `CircleIconButton(icon, contentDescription, onClick, modifier, style =
  CircleStyle.Ghost, size = 44.dp, enabled = true)`. Passing `size = 40.dp` puts it in exactly
  the slot `Avatar(size = 40.dp)` occupies on the other two tabs.
- **`Icons.Filled.Settings` is available without a new dependency.** The app aliases
  `androidx.compose.material.icons.Icons` as `MI` and already uses `MI.Filled.Person`,
  `MI.Filled.DateRange`, `MI.Filled.MoreVert`, `MI.Filled.Edit` (`SettingsScreen.kt:233`,
  `:247`, `FoodMedScreen.kt:258`). `Settings` is in the same **material-icons-core** set, so the
  gear in §3.7.4's header costs **no new vector, no `NavIconInflateTest` case and no
  `material-icons-extended` dependency** (which §0 records as deliberately removed).
- **`DaybookIcons` are `ImageVector`s built in code, and `NavItemSpec.icon` is an `ImageVector`.**
  `Navigation.kt:37` — `data class NavItemSpec(val route: String, val icon: ImageVector, val
  label: String)`. The three existing nav icons happen to be inflated vectors
  (`ImageVector.vectorResource(R.drawable.ic_nav_home)`, `MainActivity.kt:532–534`), but nothing
  requires that. **So Beast Mode's second and third nav icons can be `DaybookIcons.Clock`
  (`DaybookIcons.kt:98`) and `DaybookIcons.Category` (`:166`) with no new XML and no inflate
  risk at all** (§3.6.7). Only `ic_workout.xml`, which revision 4 already requires, is new.

**REVISION 4 — four further facts, read from the live code, that §3.6 depends on:**

- **`DaybookScaffold` hides the bottom nav for every stacked route, with no new
  plumbing.** `MainActivity.kt:608` calls it with `showNav = onMain`, where
  `onMain = backStackRoute == null || backStackRoute == "main"` (`:~525`). Every non-`main`
  `composable(...)` in the `NavHost` — `settings`, `detail/…`, `add_habit`, all of them —
  already renders full-screen with no pill nav. **Every stacked workout route therefore gets the
  full-screen, no-bottom-nav behaviour for free.** *(REV5 amendment: this is still true and still
  what the live session, the pickers and the forms want — but Beast Mode's **three nav
  destinations** now want the opposite, so `showNav` gains one disjunct. §3.6.7 specifies the
  exact three-line change; it is the only edit to `DaybookScaffold`'s call site in the round.)*
- **`FloatingPillNav` currently has no long-press path.** `Navigation.kt:108` attaches
  `.clickableImpl(remember { MutableInteractionSource() }) { onSelect(item.route) }` to each
  item's `Column`. `clickableImpl` is `Components.kt:106` — a two-line internal helper wrapping
  `Modifier.clickable(interactionSource = …, indication = null, onClick = …)`. It has **no
  `onLongClick` sibling**; §3.6.1 adds one next to it rather than calling `combinedClickable`
  ad hoc at the nav site.
- **There is no coach-mark, tooltip, spotlight or first-run-tip component anywhere in the app.**
  Verified: `grep -rni "coachmark\|tooltip\|spotlight\|first_run"` over
  `app/src/main/java/com/daybook/app` returns **zero hits**. `app_settings.onboarding_completed`
  is the only "have they seen this yet" flag that exists, and it is about the onboarding wizard,
  not about individual features. §3.6.3 therefore designs a **minimal** one out of
  `UndoSnack`'s existing visual recipe rather than reusing something that isn't there.
- **The accent palette is exactly five values** (`ui/theme/Accent.kt:18`): `MINT`, `LAVENDER`
  (the default), `CORAL`, `SKY`, `AMBER`, each with a `dark` and a `light` value and a
  `colorFor(dark)` resolver. There is no sixth colour to invent for Beast Mode (C5), and
  `LocalAccent` is a `staticCompositionLocalOf` provided once by `DaybookTheme`
  (`Theme.kt:153`) — so re-providing it for a subtree is a supported, one-line move (§3.8.1).

**REVISION 3 — three further facts, read from the live code, that the rest of this document
now depends on:**

- **There is no `Toast` and no `SnackbarHost` anywhere in the app.** Verified:
  `grep -rn "Toast.makeText\|SnackbarHost\|rememberSnackbar" app/src/main/java/com/daybook/app`
  returns **zero hits**. Daybook has exactly **two** confirmation/error patterns, and every
  requirement in this document that says "tell the user" must use one of them and **must not
  introduce a third**:
  1. **`ui/components/UndoSnack.kt`** — `BoxScope.UndoSnack(token: Int, text: String = "Undone")`.
     A pill pinned `Alignment.BottomCenter`, `padding(bottom = 96.dp)`, `AppShapes.pill`,
     `DaybookColors.SurfaceElevated` + `DaybookColors.Hairline` border,
     `DaybookText.CardSubtitle` / `DaybookColors.TextPrimary`, visible ~2.6 s, honours
     `LocalReduceMotion`. **No action button** — it confirms, it does not offer. It is shown by
     incrementing an `Int` token. Used in exactly one place today
     (`ui/home/HomeScreen.kt:119` + `:316`).
  2. **The fixed-height inline result slot** in `ui/settings/SettingsScreen.kt` (~line 1044) —
     `Box(Modifier.fillMaxWidth().heightIn(min = 36.dp))` holding a `DaybookText.Caption`
     `Text` coloured `DaybookColors.Success` when
     `msg.startsWith("Exported ") || msg.startsWith("Import successful")` and
     `DaybookColors.Danger` otherwise. **Fixed height so feedback never shifts the layout.**
     This is the pattern for anything the user must *read and act on*.
  Plus `ui/components/DaybookAlertDialog.kt` for confirm-before-acting, and
  `ConfirmDeleteDialog` for destructive confirmation.
- **Friendly error mapping already exists and must be reused, not re-invented:**
  `ExportImportRepository.friendlyImportError(e, fallback)` maps `SQLiteException` →
  *"Couldn't save the imported data — try again, or restart the app if it keeps happening."*,
  `IOException` → *"Couldn't read the file. Try picking it again."*, `SerializationException` →
  *"That file doesn't look like a Daybook backup."*, and **logs the raw throwable via `Log.e`
  rather than discarding it**. The old-format rejection constant is
  `ExportImportRepository.UNSUPPORTED` = *"This backup was made by an older version of Daybook
  and can't be restored."* — this is the "rejected with a readable reason in a screen state,
  not a toast" precedent C9 and §3.9.11 build on.
- **An occurrence-revert path already exists and is correct** — see §2.4.1. This materially
  shrinks the fix the user asked for.

---

## 1. Standing constraints for both rounds (non-negotiable)

- **C1. No `git commit`, no `git push`, no tag, no Firebase App Distribution upload.**
  Someone else does that.
- **C2. Existing local Room data must survive.** Every migration is additive
  (`CREATE TABLE` / `ALTER TABLE … ADD COLUMN`). No table rebuild, no row deletion, no
  `fallbackToDestructiveMigration` beyond the two already registered
  (`fallbackToDestructiveMigrationFrom(1)`, `…OnDowngrade()`).
- **C3. Existing Firestore cloud data must survive.** `formatVersion` stays **3**. The
  parent-doc and month-doc field names (`definitions`, `definitionsHash`, `monthHashes`,
  `payload`, `contentHash`, `revision`, `deviceId`, `formatVersion`, `appVersion`) are
  untouched. All new payload content is *inside* the existing gzipped blobs as
  **optional, default-absent** fields (§4.3) — a device on an older build still decodes
  every month doc via `ignoreUnknownKeys = true`.
- **C4. Any Room migration and any change to `BackupModel.kt` / `ContentHash` input needs
  explicit, separate user sign-off before a line is written.** Both rounds need both. See
  §9 Q11.
- **C5. No visual regression.** No new colour literal outside `ui/theme/`, no new shape
  outside `AppShapes`, no new type ramp outside `DaybookText`. Every new screen is built
  from the existing components listed in §0. New surfaces must honour `LocalReduceMotion`,
  `LocalDaybookShapes` (corner scale), `LocalIsDark` and the accent locals.
- **C6. Offline-first stays true.** Nothing new may block app launch, and every Health
  Connect / Firestore call stays failure-inert (`runCatching` + a logged/Crashlytics-recorded
  failure, never a crash and never a blocking spinner on the launch path) — **and never a
  silently-dropped failure: every user-triggered action that can fail must tell the user it
  failed, in plain language, even if the underlying cause is technical.** *(REV3 — the clause
  after the dash is new. "Failure-inert" was only ever meant to mean "does not take the app
  down"; it was never meant to mean "does not tell anyone". See C9.)*
- **C7. Battery discipline.** This project has already had a battery/heat regression round
  (`BATTERY_BUG_AUDIT.md` / `BATTERY_FIX_PLAN.md`). New flows use
  `collectAsStateWithLifecycle`, `flowOn(Default)` + `WhileSubscribed(5s)`, and no new
  always-on ticker, foreground service or exact alarm.
- **C8. One round, one migration.** Each round bumps `AppDatabase.version` exactly once and
  exports exactly one new `app/schemas/…/NN.json`, and adds exactly one
  `MIGRATION_x_y` registered in `DatabaseModule` and covered by a new case in
  `androidTest/…/MigrationTest.kt`.
- **C9 *(REV3, new)*. No error is silently swallowed, anywhere in this plan.** A `runCatching`
  whose failure branch only logs is **not acceptable** for anything the user started. Concretely:
  1. **Every user-triggered action that can fail must surface its failure in the UI**, using one
     of the two patterns §0 records — `UndoSnack` for a transient "that didn't work" on an
     action the user can simply retry, and the **fixed-height inline result slot** (or an
     equivalent inline status row / `DaybookAlertDialog`) for anything the user must read,
     understand or act on. **No new third pattern, no `Toast`, no `SnackbarHost`** (C5).
  2. **Plain language, never a raw exception message.** Route through
     `ExportImportRepository.friendlyImportError`'s idiom: a mapped, human sentence for the user;
     the raw throwable to `Log.e` / Crashlytics, **never discarded**.
  3. **A failure must never be indistinguishable from a success.** The specific trap this
     constraint exists to prevent is the one already fixed once in this codebase
     (`LOGIN_REDESIGN_RISK_FIX_PLAN.md` Phase 9, C-4: `logFoodMed` used to `return` silently on a
     missing row while the caller reported "saved"). The `LogResult.Rejected(reason)` shape that
     fix introduced is the pattern to copy for any new suspend action that can be refused.
  4. **Background, non-user-triggered work is the one exception, and it is bounded.** A daily
     `WindowRefreshWorker` pass or an automatic sync pull may fail quietly *in the moment* —
     interrupting someone to say a background poll failed is worse than useless — **but its
     outcome must still be readable somewhere the user can go and look**: a status row in
     Settings (Round B: §6.2's Settings → Health status line, which must show the last failure,
     not just the last success). "Quiet" is allowed; "unknowable" is not.
  5. This constraint applies **retroactively to every section of this document**, including
     §3, §4 and §6 as written in revisions 1 and 2. Where those sections already said
     "logged" and nothing more, revision 3 has amended them in place.

---

## 2. Recommended sequencing *(REV3: three rounds now, plus a backlog)*

**Recommendation: ship the intake-reset fix as Round 0 immediately and on its own (§2.4), then
Feature 2 (Workout) as Round A, then Feature 1 (Health) as Round B.** Round C (the external PRD's
universal-tracker vision) is backlog and is **not scheduled**; §2.2 explains why and §9 Q22 asks
the user to choose between it and Round A as planned.

*(Revision 2's original recommendation — Workout before Health — is unchanged and its reasoning
below still stands. Round 0 is not a competing priority: it is four files, no schema change, and
it does not consume the sequencing argument below.)*

Why that order:

- Workout mode needs **zero new dependencies, zero new runtime permissions, zero
  external-device testing, and zero toolchain risk**. It is entirely in-repo work using
  patterns the codebase already has three examples of (Habit / FoodMedTask / CustomCategory
  → Room → `ExportImportRepository` → month blob → UI tab).
- Health sync is gated on a real toolchain decision (§5.1), a new Android permission model
  with an OS-owned consent UI, and can only be meaningfully verified with the actual Mi Band
  10 + Mi Fitness on a physical phone. Bundling it with Workout means one bad variable
  contaminates both.
- Doing them as one round would put **five new tables, two new wire-model fields, a new
  full-screen mode with a new entry gesture *(REV4)*, a new dependency and a new permission
  flow behind one `MIGRATION_21_22`** — which violates C8's spirit and makes the round
  un-revertable in pieces.

Version plan:

| Round | Contents | DB | New schema JSON | versionCode / versionName |
|---|---|---|---|---|
| **0** *(REV3)* | Intake reset fix (§2.4) | 21 — **no migration** | none | **25 / "0.5.7"** |
| **A** | Workout mode | 21 → **22** | `22.json` | **26 / "0.6"** |
| **B** | Health Connect | 22 → **23** | `23.json` | **27 / "0.6.1"** |

*(REV3: these are now concrete, not "user's call". The baseline is **build 24 / 0.5.6**, the
committed state at `66bca0c` (§0). Round 0 is a bug fix with no schema change, so it takes a
patch version; Round A is a new feature surface, so it takes the minor bump the repo's own
convention implies. If the user wants different names they can say so — but a plan that leaves
this blank forces the implementer to invent it, which C9's sibling rule §9-promotion forbids.
If Round 0 is skipped, Round A becomes 25 / "0.6" and Round B 26 / "0.6.1".)*

If the user prefers Health first, swap the migration numbers (`MIGRATION_21_22` becomes the
health one, `MIGRATION_22_23` the workout one) — nothing else in this document changes.
Round 0 is independent of both and needs no renumbering in either case.

### 2.1 Why we are **not** adopting the PRD's backend architecture *(REVISION 3)*

**This subsection exists so that nobody proposes PostgreSQL for this app again without first
reading why it was rejected.** It is not a footnote. The external PRD (§4, §45–47 of that
document) proposes:

```
Android App → Room → Sync queue → WorkManager → Backend API → PostgreSQL   (Supabase suggested)
```

with every mutation carrying an `operation_id`, `entity_id`, `timestamp`, `version` and
`operation_type`, batched to a server that resolves conflicts deterministically.

**Decision: rejected in full. Daybook keeps Room + Firestore exactly as it is today.**
Not "deferred", not "later" — **rejected**, because adopting it would mean building a second
sync system next to a working one.

#### 2.1.1 What Daybook already has (verified by reading the live code, not assumed)

Read at `data/sync/CloudSyncRepository.kt`, `data/sync/SyncStateStore.kt`,
`data/sync/MonthPartitioner.kt`, `data/sync/ContentHash.kt`, `data/sync/PayloadCodec.kt`,
`data/sync/SyncLogic.kt`:

| PRD requirement | Daybook's existing mechanism | Where |
|---|---|---|
| "Every workout action writes locally first" (PRD §45) | **Room is the source of truth, unconditionally.** Every write is a Room write; nothing in the app writes to the network on a user action. | all repositories |
| "Sync queue records the mutation" (PRD §45) | **`InvalidationTracker.Observer(DATA_TABLES)`** — Room *itself* is the change queue. Any write to a tracked table fires the observer; no hand-maintained queue table can drift out of step with the data, because there is no second copy of the truth. | `CloudSyncRepository.attachTracker()` (~line 322) |
| "Batch sync, not one request per set" (PRD §5, §46) | **A debounced push** (`changes.debounce(DEBOUNCE_MS)`) plus `SyncFlushWorker`, a one-shot `ON_STOP` job when a push is pending. Logging 20 sets produces **one** eventual push, not 20. | `CloudSyncRepository` (~line 216), `SyncStateStore.KEY_PENDING_PUSH` |
| "WorkManager for deferrable background work" (PRD §5) | Already exactly this: `WindowRefreshWorker` (daily) + `SyncFlushWorker` (one-shot). **No polling service, no repeating alarm, no wake lock.** | `util/work/` |
| "Conflict handling — never blindly overwrite" (PRD §46) | The **D2 conflict flow**: `ConflictInfo` with concrete both-sides row counts, a user-facing prompt, `conflictPaused` halting sync for the session rather than guessing, and `conflictAlreadyResolved` so the prompt is not re-shown for a decision already made. | `CloudSyncRepository` lines ~57–152, ~388–423 |
| "`version` per mutation" (PRD §46) | **`revision` on the parent doc** plus **`contentHash` per month** and `definitionsHash` for definitions — content-addressed rather than counter-addressed, which is strictly better here: it cannot drift, and an unchanged month is provably unchanged. | `ContentHash.kt`, `F_MONTH_HASHES` |
| "Server-side access control" | **Firestore security rules.** `users/{uid}` owner-match. This is the part people miss: **the access control a hand-written API would exist to provide is already there, declaratively, with no server to run, patch, pay for or keep up.** | `firestore.rules` |
| "Don't load everything into memory" (PRD §43, §51) | **Month partitioning with lazy hydration, eviction and pinning** — the device holds recent months and fetches older ones on demand. | `MonthPartitioner.kt`, `hydrateRange` (~line 769) |

#### 2.1.2 The specific reasons to reject it

1. **There is no server, and adding one is a category change, not a feature.** Daybook is a
   sideloaded, single-developer, offline-first journal app whose only remote component is
   Firestore used as per-user storage. A Backend API means an operational surface that must be
   deployed, monitored, secured, migrated, backed up and paid for **forever**, and which becomes
   a single point of failure for data that currently cannot be lost by any outage because it
   lives in Room.
2. **A hand-written mutation queue would be strictly worse than the `InvalidationTracker`.**
   A queue table is a **second copy of "what changed"** that can disagree with the first. Every
   bug class it introduces — a mutation enqueued but not applied, applied but not enqueued,
   enqueued twice, enqueued for a row since deleted — is a bug class Daybook structurally cannot
   have today, because the data *is* the queue. This project has already been bitten once by a
   second copy of truth drifting (`DATA_TABLES` vs the schema — the "journal_questions incident",
   §4.1); the lesson points the other way from the PRD.
3. **`operation_id` + per-mutation `version` solves a problem Daybook does not have.**
   Per-operation versioning exists to merge concurrent edits from many writers. Daybook has
   **one human**, on **one or two devices**, usually **not simultaneously**. The month-level
   content hash plus the D2 prompt is the correctly-sized answer: it detects divergence
   reliably and, in the genuinely ambiguous case, **asks** rather than silently picking — which
   is what the PRD's own "never blindly overwrite" actually demands.
4. **It would break C3 outright.** `formatVersion` is 3 and there is live user data in the
   existing shape. Migrating to Postgres means a data migration of the user's real history
   across two storage engines, with no rollback, for zero user-visible benefit.
5. **It contradicts the PRD's own stated priorities.** The PRD ranks **battery #2** and
   **offline reliability #3**, and warns against "network request on button tap" (§54) and
   "every-30-seconds API calls" (§5). Daybook's debounced, batched, `ON_STOP`-flushed model is
   already the shape the PRD is arguing *for*. The architecture diagram is the weakest part of
   an otherwise strong document — it is the reflex of a team that assumes a backend, applied to
   an app that correctly does not have one.

#### 2.1.3 What this means for anyone implementing Round A, B or C

- **Do not add** a `sync_queue` / `mutations` / `pending_operations` table.
- **Do not add** `operation_id`, `entity_version`, `last_modified_by` or `dirty` columns to any
  entity in this plan.
- **Do not add** Supabase, Postgres, Ktor, Retrofit, or any HTTP client.
- **Do** add new tables to `CloudSyncRepository.DATA_TABLES` (§4.1) and to the six
  `ExportImportRepository` call sites (§4.4). **That is the whole of "wiring up sync" in this
  codebase**, and it is why Round A's sync work is one phase (A4) rather than a project.
- The **one** PRD sync idea worth keeping is already in §4.6: watch the payload size. Firestore's
  1 MiB per-document cap is the real ceiling, and §4.6 shows the headroom is comfortable.

### 2.2 The PRD's product vision, and the scope decision it forces *(REVISION 3)*

The architecture is wrong for this app. **The product thinking is not.** PRD §§1–3, 10–43 and
54–70 are a coherent, well-argued vision, and three things in it are simply correct:

- the four-layer model **Activity → Routine/Program → Workout → Progress** is the right way to
  think about training data;
- **"the product should not say *we don't have that exercise*"** — the user creates it;
- **"less UI, more logging" during a live set** (§64), and the P0/P1/P2 discipline that says
  don't build the AI coach before the logger is fast (§3, §67).

Some of that is **already what Round A does**: Hevy-parity gym logging, custom exercises,
previous/best lookups, PRs, rest timers, notes, import/export, offline-first — PRD §68's P0 list
overlaps Round A's §3.1.1 scope substantially.

But a large part of it is **a genuinely bigger system**: Routines, Programs, periodization,
deloads, substitution, a generic metrics engine, multi-sport custom activities, training load,
a fatigue dashboard. That is captured in **ROUND C** (after Round B), explicitly **not started
and not scheduled**.

#### 2.2.1 The decision the user must make — and it is a real fork, not a formality

**Round A currently plans concrete tables**: `exercises`, `workout_sessions`,
`workout_exercises`, `workout_sets`, with named columns `reps`, `weight_kg`,
`duration_seconds`, `distance_meters`, `rpe`. The PRD (§10) says explicitly **not** to do that,
and to build `activity / activity_metric / workout / workout_block / workout_item /
metric_value` instead — a generic engine where "reps" is a row, not a column.

| | **Option 1 — concrete Round A now (RECOMMENDED)** | **Option 2 — generic Activity/Metric engine from day one** |
|---|---|---|
| Schema | 4 tables, typed columns | ~6 tables, values in an EAV-shaped `metric_value` table |
| Queries in §3.4 | `ORDER BY weight_kg DESC` — one indexed read | a pivot/join per metric per set; "best set" stops being a single index scan |
| The live set table (§3.7.1) | `columnsFor(trackingMode)` — 4 cases, unit-testable, ~30 lines | columns come from the activity's metric definitions; every cell is a dynamic typed editor |
| Hevy CSV import (§3.9) | direct column mapping | must first synthesise metric definitions, then write values as rows |
| Risk of shipping nothing | low | **material** |
| Yoga / running / football / custom drills | not supported in Round A | supported by construction |
| Custom user metrics ("successful shots") | not supported in Round A | supported by construction |

**Recommendation: Option 1 — ship concrete Round A.** Three reasons:

1. **The user asked for a gym logger.** Every artefact they supplied — five Hevy screenshots, a
   Hevy CSV export — is barbell-and-dumbbell gym logging. The PRD's universality is a vision
   they found appealing; it is not, today, a described need.
2. **A generic metrics engine is meaningfully harder to implement correctly**, and this plan is
   explicitly written to be executable by a less-capable implementing model (§10). An EAV schema
   moves type-safety from the compiler into runtime convention, turns every read into a pivot,
   and makes the wire model (§4.2) and the six `ExportImportRepository` call sites (§4.4)
   substantially harder — and §4.4 item 6 is already flagged as the single easiest place in this
   plan to silently lose cloud data. **Scope ballooning here does not produce a worse workout
   tracker; it produces no workout tracker.**
3. **This app is a habit tracker adding its first workout feature.** Round A is the P0 the PRD's
   own §67 would prescribe: *build the engine, benchmark it on a real device, then layer on top.*

#### 2.2.2 Does Option 1 foreclose Option 2 later? — **No, and here is the specific reason**

This was checked rather than assumed, because "we can always generalise later" is usually a lie.

- **`Exercise.trackingMode`** (`"WEIGHT_REPS" / "REPS_ONLY" / "DURATION" / "DISTANCE_DURATION"`)
  is **already a coarse metric-set descriptor**. Each value names exactly which of
  `WorkoutSet`'s nullable columns are meaningful. The generic model's "this activity tracks
  Weight + Reps + RPE" is the same statement with finer resolution. Migrating means: for each
  `trackingMode`, emit the corresponding fixed set of `activity_metric` rows — a **pure,
  total, unit-testable function** over four cases, not a data-archaeology exercise.
- **`WorkoutSet`'s value columns are already all nullable**, and §3.9.3 makes "blank is not
  zero" a hard rule. So every existing row already says truthfully *which* metrics it carries.
  Converting one row to N `metric_value` rows is `listOfNotNull`-shaped.
- **`WorkoutExercise` already exists as a distinct block entity** (§3.2), which is precisely the
  PRD's `workout_block`. Revision 2 added it for per-block notes and supersets; it happens to be
  the exact join point a generic model needs.
- **`Exercise.source`** already distinguishes provenance, and **`WorkoutSession.source`**
  already anticipates non-manual origins.

**Three things must hold for this to stay true, and they are hereby requirements on Round A:**

- **Ri1.** `trackingMode` values are **never** reused for two different metric sets, and the
  string values are permanently stable — the same rule §3.3 applies to `builtin:` ids.
- **Ri2.** No code outside `columnsFor(trackingMode)` (§3.7.1) and the mapping functions in
  §3.4 may branch on `trackingMode`. One choke point means one function to replace.
- **Ri3.** No value column may ever be given a **non-null default** (e.g. `weight_kg REAL NOT
  NULL DEFAULT 0`). That is the one change that would genuinely foreclose Option 2, because it
  destroys the distinction between "0 kg" and "this metric does not apply" — and it would also
  break §3.9.3 and PR detection. §3.2 and §3.5 already specify nullable; **Ri3 makes it a rule
  rather than an accident.**

**§9 Q22 puts this fork to the user.** It is the biggest question in this document.

### 2.3 PRD engineering advice adopted into Round A *(REVISION 3)*

The PRD's non-architectural engineering guidance costs almost nothing and materially improves
quality, so it is folded into Round A **as requirements, not backlog**. It is collected in
**§3.10 (Performance discipline)**, and cross-referenced from the sections it touches:

| PRD source | Lands in | Status |
|---|---|---|
| §53 "complete set → write immediately, transactionally" | §3.10 P1, §3.7.1 | Round A's §3.7.1 **already implied** per-set writes; §3.10 P1 makes it explicit and non-optional |
| §55 "don't recompose the whole workout screen every timer tick" | §3.10 P2 | new requirement |
| §56 "never recalculate years of stats after one set" | §3.10 P3 | new requirement |
| §7 cold/warm start and interaction budgets | §3.10 P4 | new requirement |
| §54 lazy lists, stable keys, immutable UI models, `derivedStateOf` | §3.10 P5 | partly already the house style; now written down |
| §8 Baseline Profiles | §3.10 P6 | **explicitly deferred with a reason** — see §3.10 |
| §30 rest-timer battery rule | §3.1.2 R1 / §3.7.2 | **already done in revision 2**, and stricter than the PRD's version |
| §51 media caching / don't ship hundreds of GIFs | §3.1.2 | **already decided in revision 2** — Daybook ships no exercise art at all |
| §45 offline-first | §2.1 | **already true** |

### 2.4 ROUND 0 — fast-track fix: undo a mistaken intake log *(REVISION 3, new)*

> **This is a bug fix, not a feature round. It is deliberately placed here — before Round A —
> because it is small, low-risk, needs no migration, needs no sign-off under C4, and the user
> is hitting it today.** It should be built and shipped on its own, ahead of and independently
> of Rounds A and B.

**The user's report:** *"If I log an intake item by mistake, I must be able to delete its
content or reset its status to not-logged."*

#### 2.4.1 The data layer for this **already exists and is already correct**

The brief for this revision assumed a new `OccurrenceScheduler` method was needed. **It is
not.** Read from the live code:

```kotlin
// data/OccurrenceScheduler.kt:707
suspend fun revertFoodMed(occurrenceId: String) = syncMutex.withLock {
    val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId) ?: return@withLock
    notificationUtils.cancelNotification(occ.notificationId)
    db.foodMedOccurrenceDao().revertToPending(occurrenceId)
    if (revertShouldRearm(occ.scheduledFor, startOfTodayMillis())) syncTaskInternal(occ.taskId)
}
```

backed by:

```sql
-- data/local/FoodMedOccurrenceDao.kt:104  (@Query on revertToPending)
UPDATE food_med_occurrences SET status = 'PENDING', responded_at = NULL, snooze_count = 0,
  response_text = '', description = NULL, qa_json = NULL, red_flag = NULL,
  suspected_food = NULL, outside_food = NULL WHERE id = :occurrenceId
```

Measured against what the fix was supposed to do, field by field:

| Field | Required behaviour | `revertToPending` does | Verdict |
|---|---|---|---|
| `status` | → `PENDING` | → `'PENDING'` | ✅ |
| `responseText` | cleared | → `''` | ✅ |
| `respondedAt` | cleared | → `NULL` | ✅ |
| `redFlag` | cleared | → `NULL` | ✅ |
| `suspectedFood` | cleared | → `NULL` | ✅ |
| `outsideFood` | cleared | → `NULL` | ✅ |
| `description` | (not in the brief; must clear or a journal entry's body survives the reset) | → `NULL` | ✅ |
| `qaJson` | (same) | → `NULL` | ✅ |
| `snoozeCount` | **decision needed** | → `0` | ✅ — and `0` is right. `snooze_count` describes *this attempt at this slot*. A slot returned to PENDING is going to be offered again from scratch; carrying three stale snoozes into it would misreport the reminder's history and, via `snoozeFoodMed`'s escalation, could affect future scheduling. It is not user-visible data being destroyed — the `USER_SNOOZED` **event rows are append-only and are left untouched**, so the real history survives in `food_med_events`. |
| `notificationId` | **decision needed** | untouched | ✅ — and untouched is right. `notification_id` is a stable per-occurrence identity used to post and cancel that slot's notification; it is allocated once by `NotificationIdSequence` and must stay put or a later `cancelNotification` targets the wrong id. It is not state, it is an address. **Do not touch it.** |
| the posted notification | must be dismissed | `cancelNotification(occ.notificationId)` **before** the DB write | ✅ — and it follows the L3 "cancel first" ordering the rest of the file uses |
| re-arming the alarm | only for today/future slots | `revertShouldRearm(scheduledFor, startOfToday)` — pure, already unit-tested | ✅ |

**So the required new method already exists, with the right name, the right signature
(`suspend fun revertFoodMed(occurrenceId: String)`), in the right layer, and it is idempotent.
Round 0 must not add a second one.** *(The habit-side twin `revertHabit(occurrenceId: String)`
at line 700 is equally complete and is out of scope here — habits already expose it in the UI.)*

#### 2.4.2 The actual defect: a three-place UI gap

`revertFoodMed` is reachable from exactly two call sites, and **neither can be reached by a
user looking at a mistakenly-logged intake entry**:

**Gap 1 — the Today card's overflow sheet offers "Edit" *instead of* "Undo".**
`ui/home/HomeScreen.kt:544` computes
`isLoggedText = !item.isHabit && item.statusLabel == LOGGED_LABEL && item.occurrenceId != null`,
and at `:775` the sheet builds:

```kotlin
if (isLoggedText || isEditableHabitJournal) {
    add(SheetAction(MI.Filled.Edit, "Edit", onClick = editEntry))
} else if (item.statusLabel != null && item.statusLabel != MISSED_LABEL && item.occurrenceId != null) {
    add(SheetAction(DaybookIcons.Unarchive, "Undo", onClick = onUndo))
}
```

It is an **`else if`**. A logged intake row takes the first branch and the "Undo" action is
never added. The in-file comment is explicit that this was deliberate — *"Journal Mode: edit the
entry instead of undoing it … there is no 'undo' for it on the Today card anymore"* — so this is
a **deliberate past decision with an unintended consequence**, not an oversight to be silently
reverted. Line `:631` (`val undoable = !editable && …`) follows the same rule for the status tap.
**Consequence: a *skipped* intake entry can be undone; a *logged* one cannot.** Which is exactly
backwards relative to which mistake people actually make.

**Gap 2 — the Respond screen's intake form has no Undo at all.**
`ui/respond/RespondViewModel.kt:207` has `fun undo()`, which calls `revertFoodMed` for the
intake case. But `ui/respond/RespondScreen.kt`'s `StickySaveBar` only renders an "Undo" button
in the **`Kind.HABIT` + `state.readOnly`** branch (`:196`). The `Kind.INTAKE` branch renders
`"Save"`/`"Log"` and, when `!state.isEdit`, `"Skip"` — **never Undo**. So
`RespondViewModel.undo()` is **unreachable dead code on the intake path**.

**Gap 3 — the item's history screen routes straight into Gap 2.**
`ui/detail/DetailScreen.kt:292` maps an intake `REPLIED` timeline row to
`onOpenRespond(occId)` — i.e. into the editable Respond form of Gap 2. There is no per-row
undo affordance in the history list.

**Net effect, and it is precisely the user's complaint:** every route to a mistakenly-logged
intake entry terminates in an editor that can only *re-save*. The text can be changed; the
status can never go back to not-logged.

#### 2.4.3 The fix — exact changes, exact copy

**Principle: reuse the patterns that already exist** (§0), add no new component, add no new
string style, and **keep "Edit" working** — editing a log is a legitimate, separate action that
the Journal Mode round added on purpose. The fix is **"Undo" alongside "Edit"**, never instead
of it.

**Fix 1 (primary) — `ui/respond/RespondScreen.kt`, the `Kind.INTAKE` branch of `StickySaveBar`.**
This single change closes Gaps 2 and 3 at once, because Gap 3 routes here. When
`state.isEdit == true` (the occurrence was already resolved when the screen opened —
`RespondViewModel` line ~145 sets this), render a third, lowest-priority action below
`"Save"`:

```kotlin
RespondViewModel.Kind.INTAKE -> {
    PrimaryButton(
        text = if (state.isEdit) "Save" else "Log",
        onClick = { vm.log() },
        enabled = state.reply.isNotBlank() && !state.busy
    )
    if (!state.isEdit) {
        Spacer(Modifier.height(8.dp))
        GhostButton(text = "Skip", onClick = { vm.skip() }, modifier = Modifier.fillMaxWidth())
    } else {
        // ROUND 0: reset a mistaken log back to an unanswered slot. `vm.undo()` already
        // existed and already called revertFoodMed; it was simply unreachable from here.
        Spacer(Modifier.height(8.dp))
        GhostButton(
            text = "Reset to not logged",
            onClick = { confirmReset = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

- **Exact button label: `Reset to not logged`.** Not "Undo" — on this screen the user is looking
  at their own text in an editable field, and "Undo" reads as "undo my typing". "Delete" is
  wrong too: the reminder slot is not being deleted, only emptied.
- **It is confirmed before it runs**, because it destroys text the user may have typed. Reuse
  the existing `ui/components/DaybookAlertDialog.kt` — the same component the JSON import's
  confirm-first flow uses:
  - title: **`Reset this entry?`**
  - body: **`This clears what you logged and puts the reminder back to not logged. Your typed answer is deleted and can't be brought back.`**
  - confirm label: **`Reset`**, `destructive = true`
  - dismiss label: **`Cancel`**
- **On success** the screen already pops (`resolve { … }` sets `done = true`, which the screen
  observes). **The confirmation the user sees is on the screen they land back on** — see Fix 3.
- **On failure (C9).** `RespondViewModel.undo()` currently discards its result:
  `resolve` wraps the action in `runCatching { action() }` and then unconditionally sets
  `done = true` — **a swallowed error, and exactly what C9 forbids.** `undo()` must stop using
  `resolve` and handle its own outcome, mirroring how `log()` already handles `LogResult`:

  ```kotlin
  fun undo() {
      if (_state.value.busy) return
      _state.update { it.copy(busy = true, rejectedMessage = null) }
      safeLaunch {
          val ok = runCatching {
              if (isHabit) occurrenceScheduler.revertHabit(occId)
              else occurrenceScheduler.revertFoodMed(occId)
          }.isSuccess
          _state.update {
              if (ok) it.copy(busy = false, done = true)
              else it.copy(busy = false, rejectedMessage = "Couldn't reset this entry. Try again.")
          }
      }
  }
  ```

  `rejectedMessage` **already renders** in the `StickySaveBar` (`RespondScreen.kt:189`,
  `DaybookText.Metadata` / `DaybookColors.Warning`) — no new UI is needed for the failure path,
  which is the whole point of reusing the existing pattern. **Exact failure copy:
  `Couldn't reset this entry. Try again.`**
  *(Note: this also fixes the habit-side "Undo" button, which has the same swallowed-error
  behaviour today. That is a strict improvement and is in scope for Round 0.)*

**Fix 2 — `ui/home/HomeScreen.kt`, the overflow sheet at `:775`.** Turn the `else if` into two
independent `if`s so a logged intake row offers **both**:

```kotlin
if (isLoggedText || isEditableHabitJournal) {
    add(SheetAction(MI.Filled.Edit, "Edit", onClick = editEntry))
}
// ROUND 0: previously an `else if`, so a LOGGED intake row got "Edit" and nothing else —
// there was no way back to not-logged from any screen. Edit and Undo are different actions.
if (item.statusLabel != null && item.statusLabel != MISSED_LABEL && item.occurrenceId != null) {
    add(SheetAction(DaybookIcons.Unarchive, "Undo", onClick = onUndo))
}
```

- **Label stays `Undo`** here, matching the existing sheet action for skipped/completed rows.
  Two labels for one action would be worse than one imperfect label; the Respond screen's
  longer phrasing is justified only because that screen has an editable field in view.
- **Icon stays `DaybookIcons.Unarchive`**, the existing one.
- **No confirmation dialog on this path.** The Today card's sheet-action for habits has never
  had one, the row's content is visible right there, and the action is immediately followed by
  an `UndoSnack`. Adding a dialog here and not for habits would be an inconsistency.
- **Leave `:631`'s status-tap behaviour (`editable` wins) unchanged.** Tapping "Logged" opens
  the editor, as today. Only the overflow sheet gains the extra action. Changing the tap target
  would alter muscle memory for a working flow.

**Fix 3 — the confirmation the user sees.** `HomeScreen` already holds
`var undoToken by remember { mutableStateOf(0) }` (`:119`) and renders `UndoSnack(token = undoToken)`
(`:316`), incremented by `onUndo = { viewModel.revertItem(item); undoToken++ }` (`:266`).
So **Fix 2 needs no new confirmation work at all** — it rides the existing snack.

Two required adjustments:

- **Exact snack copy.** `UndoSnack`'s default text is `"Undone"`, which is right for the Today
  card. **Keep `"Undone"`.** Do not add a second variant.
- **C9 on this path.** `HomeViewModel.revertItem` (`:762`) currently launches and drops the
  outcome. It must surface failure the same way: keep the optimistic `undoToken++` (the Room
  flow will correct the row if it didn't happen), and on a caught throwable set an error message
  that `HomeScreen` shows via the same `UndoSnack`, with **exact copy
  `Couldn't undo that. Try again.`** Concretely, replace the bare `Int` token with a small state
  the screen already knows how to render:

  ```kotlin
  // ui/home/HomeViewModel.kt — ROUND 0 (C9): revert must not fail silently.
  private val _undoFeedback = MutableStateFlow<String?>(null)
  val undoFeedback: StateFlow<String?> = _undoFeedback.asStateFlow()

  fun revertItem(item: HomeItem) {
      val occ = item.occurrenceId ?: return
      safeLaunch {
          val ok = runCatching {
              if (item.isHabit) occurrenceScheduler.revertHabit(occ)
              else occurrenceScheduler.revertFoodMed(occ)
          }.isSuccess
          _undoFeedback.value = if (ok) "Undone" else "Couldn't undo that. Try again."
      }
  }
  ```

  and in `HomeScreen`, pass that string into the existing component:
  `UndoSnack(token = undoToken, text = undoFeedback ?: "Undone")`. **No new component, no new
  colour, no new shape** (C5).

**Fix 4 — `ui/detail/DetailScreen.kt`: no change.** Gap 3 is closed transitively by Fix 1,
because `:292` already routes an intake `REPLIED` row to `onOpenRespond`. **Adding a second,
per-row undo affordance in the history list is explicitly rejected**: it would need a new
long-press or swipe vocabulary that exists nowhere else in that screen (C5), and the two-tap
route through the Respond form is already short. Stated here so a later reader knows it was
considered and decided, not forgotten.

#### 2.4.4 What the user sees afterwards — **"Missed", and that is correct**

This must be written down plainly so it is not later filed as a new bug.

`ui/home/HomeViewModel.kt:722` classifies a `PENDING` occurrence:

```kotlin
Occurrence.Status.PENDING -> if (isPastDay && !backfillable) MISSED_LABEL else null
```

So after a reset:

- **Today's entry** → `statusLabel == null` → it returns to the normal unanswered state with its
  Reply / Skip affordances. Exactly what the user wants.
- **A past day's entry, still backfillable** → `statusLabel == null` → it appears under
  **"To do"** for that day and can be logged again. Also what the user wants.
- **A past day's entry that is no longer backfillable** → **"Missed"**.

**"Missed" is the correct and intended outcome, not a defect.** The entry genuinely was not
logged: the user has said the log was a mistake, and an unlogged past slot *is* a missed one.
Daybook has exactly two truthful states for a past unanswered slot and "Missed" is the one that
does not invent a third.

Two corollaries worth stating:

- **`export`/sync agree with the UI.** `ExportImportRepository` (~line 184) maps
  `Occurrence.Status.PENDING -> BackupStatus.MISSED`, so a reset entry exports as
  `"status": "missed"` with `answer`, `resolvedAt`, `description`, `qaJson`, `redFlag`,
  `suspectedFood` and `outsideFood` all absent. The UI label and the backup vocabulary already
  say the same word. Nothing to reconcile.
- **A reset does affect streaks**, because `streaksFromScheduledStatuses` counts `LOGGED` days
  (`ui/detail/DetailViewModel.kt:400`). Resetting a log can therefore shorten a streak. **That
  is also correct** — the streak was earned by a log the user has just said was a mistake — but
  it is worth saying out loud, because an unexpected streak drop is the kind of thing that gets
  reported as a second bug. **§9 Q23** puts it to the user.

#### 2.4.5 Migration and sync: **none, and none**

Both checked against the live code rather than asserted.

- **No Room migration.** `revertToPending` is an `UPDATE` over columns that have existed since
  v0.5.3 (`status`, `responded_at`, `snooze_count`, `response_text`) and v0.5.4 (`red_flag`,
  `suspected_food`, `outside_food`, `qa_json`). **No `CREATE TABLE`, no `ADD COLUMN`,
  `AppDatabase.version` stays 21, no new `app/schemas/…` JSON.** C2 and C8 are untouched, and
  **C4's sign-off does not apply to Round 0** — there is no migration and no `BackupModel`
  change to sign off on.
- **No special sync handling.** Verified in three steps:
  1. `"food_med_occurrences"` is **already** in `CloudSyncRepository.DATA_TABLES` (line ~1392),
     so the `InvalidationTracker.Observer` fires on the `UPDATE` and the debounced push is
     marked pending — automatically, with no new code.
  2. The occurrence serialises through the existing `IntakeLog` in `DayEntry`
     (`data/backup/BackupModel.kt:168`); a reverted row simply emits
     `status = BackupStatus.MISSED` with the optional fields absent. **No new wire field, so
     `ContentHash` input is unchanged and C3 holds** — this is an ordinary content edit, exactly
     like editing a log's text, which the app already does.
  3. **One real consideration, and it is already handled.** Reverting a *past* occurrence
     changes a past month's `contentHash`, so that month must be resident or the push would
     write a partial month — the same hazard §3.9.7 describes for the Hevy import. But a user
     can only reach a past occurrence through a screen that already required it to be loaded:
     `OccurrenceScheduler` guards past-day writes with `monthResident(date)` (line ~719) via
     `cloudSyncProvider.get().isMonthResident(...)`, and `HomeScreen` renders
     **`"Loading this month…"`** (`:616`) for a not-yet-hydrated past day. **Round 0 must not
     weaken either guard.** Explicit requirement: the new "Undo"/"Reset" affordances follow the
     same rule as the existing ones — **not offered while `backfillBlocked` is true.** Fix 2's
     `when` branch already sits below `backfillBlocked ->` in `HomeScreen`, so this is satisfied
     by construction; **Fix 1's Respond screen must be checked on-device for a past day in an
     unhydrated month** (expected: the row cannot be opened at all, so the case does not arise —
     confirm, don't assume).

#### 2.4.6 Round 0 phase list

| Phase | Work | Gate |
|---|---|---|
| Z1 | `RespondViewModel.undo()` rewritten to surface its outcome (C9); `RespondScreen` `Kind.INTAKE` gains the **`Reset to not logged`** `GhostButton` + the `DaybookAlertDialog` confirm | `assembleDebug` green |
| Z2 | `HomeScreen` sheet `else if` → two `if`s; `HomeViewModel.revertItem` surfaces its outcome; `UndoSnack(text = …)` wired | manual: log an intake item, reset it from both Today and the history screen |
| Z3 | Unit tests: a `RespondViewModel` test that a thrown revert leaves `done == false` and sets `rejectedMessage`; a `HomeViewModel` test that a failed revert emits the failure string. **A pure-function test is not possible for the sheet's action list** — instead extract the decision as `internal fun sheetActionsFor(isLoggedText: Boolean, isEditableHabitJournal: Boolean, statusLabel: String?, hasOccurrence: Boolean): List<String>` returning action labels, and unit-test all six cases. That extraction is the repo's own house style (§10) and is **required**, not optional. | `./gradlew test` green |
| Z4 | Full `./gradlew test` + `assembleRelease`; signed APK `Daybook-v0.5.7-intake-reset-release.apk`; `versionCode` 25 per `HOW_TO_PUSH_UPDATES.md` | all tests green |

**Estimated blast radius: three files** (`RespondScreen.kt`, `RespondViewModel.kt`,
`HomeScreen.kt`) **plus `HomeViewModel.kt`, no schema, no sync, no new component, no new
string style.** This is why it is recommended as an immediate, standalone round. **§9 Q24.**

---

# ROUND A — Workout mode

## 3. Feature 2: Workout mode (gym logging)

### 3.1 Scope

#### 3.1.0 What the Hevy screenshots actually show (read from the images, revision 2)

Recorded here because the rest of §3 is a response to it, and because a later reader will
otherwise not know which details were deliberate.

**Add Exercise (screens 1–3).** A full-screen page, not a sheet: header `Cancel` /
*Add Exercise* / `Create` (so "create a new exercise" is a header action on the picker, not a
row buried at the bottom of the list). Below it a `Search exercise` field, then **two equal
half-width filter buttons side by side — `All Equipment` and `All Muscles`**. Each opens its
own **bottom sheet with a drag handle and a title** (*Muscle Group*), a scrolling list where
the currently-selected option carries a trailing check, and a leading round icon per row. The
muscle sheet's first row is `All Muscles` (grid icon, checked) followed by an
**alphabetical** list: Abdominals, Abductors, Adductors, Biceps, Calves, Cardio, Chest, …
Full Body, Glutes, Hamstrings, Lats, Lower Back, Neck, Quadriceps, Shoulders, Traps, … Each
carries a **greyscale anatomical body diagram with the target muscle highlighted in blue**.
Back on the main page: a `Recent Exercises` section header, then rows of
**round exercise illustration + exercise name + muscle-group subtitle**
("Seated Cable Row - V Grip (Cable)" / *Upper Back*; "Squat (Barbell)" / *Quadriceps*;
"Triceps Pushdown" / *Triceps*), with a **circular trend-arrow button on the trailing edge**
of every row — a per-exercise history/progress entry point that is separate from tapping the
row to add it.

The subtitle is a **primary muscle**, not a body region: *Upper Back*, *Triceps*,
*Quadriceps*, *Biceps*, *Abdominals*, *Chest*. And the equipment is carried **in the name's
trailing parenthetical** — `(Cable)`, `(Barbell)`, `(Dumbbell)` — which is also exactly how
the CSV export names them (§3.9). Both facts drive §3.3.

**Live session (screens 4–5).** Header: a **collapse chevron** + the title *Log Workout*,
an **alarm-clock icon**, and a blue **`Finish`** button. When the page is scrolled the title
**swaps from "Log Workout" to the running elapsed time** ("15min 58s") — the duration follows
you down the page. A thin progress bar sits under the header. Then a stats row with three
labelled figures — **`Duration` 19min 46s** (tinted, because it is live), **`Volume` 1,680 kg**,
**`Sets` 7** — and, on its trailing edge, **two small front/back body silhouettes shading the
muscles this session has worked**.

Each exercise is a card: round thumbnail + **exercise name in the accent colour** (tappable)
+ an overflow `⋮`; an `Add notes here…` placeholder **per exercise** (not per set, and not
per session); a tappable **`Rest Timer: OFF`** row with a stopwatch icon; then a set table.

The set table's columns **change with the exercise**: Cable Crunch shows
`SET · PREVIOUS · KG · REPS · ✓`, but Bicycle Crunch — bodyweight — shows
`SET · PREVIOUS · REPS · ✓` with **no KG column at all**. `PREVIOUS` is per **set number**:
set 1 and set 2 of one exercise both read "30kg x 15" because that is what those two sets
were last time; a set with no history shows `–`. A completed row turns **solid green with a
filled green check**; an incomplete row keeps a grey check. A set that beat a personal record
**replaces its set number with a gold medal icon**. `+ Add Set` sits under each exercise's
table. At the very bottom of the page: a full-width accent **`+ Add Exercise`**, then
**`Settings`** and a red **`Discard Workout`** side by side.

#### 3.1.1 Scope

**In scope (MVP):**

- *(REV4, amended REV5)* **Entering and leaving the mode**: a **long-press on the Today nav
  item** opens Workout / Beast Mode as a **full-screen mode with its own three-item bottom nav**
  *(REV5 §3.6.7 — revision 4's "no bottom nav" is withdrawn)*; a **long-press on Beast Mode's
  own leftmost nav item** leaves it again, mirroring the way in exactly (§3.6.8), and the normal
  back gesture also leaves from the landing screen; a one-time coach-mark and an always-present
  Settings → `Open Workout` row make it findable; a Beast Mode row on Today is recommended and
  pending §9 Q32. **All of §3.6.**
- *(REV5)* **Beast Mode has its own Settings screen** (§3.8.2), reachable only from inside it,
  holding every Workout-specific preference. Daybook's main Settings keeps one row: `Open Workout`.
- *(REV5)* **Routines** — a named, reusable, ordered list of exercises with optional per-exercise
  target sets / reps / weight / time / distance / rest. Create, edit, duplicate, delete; start a
  session from one and the session comes pre-populated. **Deliberately minimal** — this is a
  *template*, not the PRD's Programs / periodization / weekly-cycle layer, which stays in
  Round C (§C.3.2). §3.2.1, §3.7.4, §3.7.5.
- Log a workout **session** (date, optional title, optional note, start/end).
- Add **exercises** to a session, from a **built-in catalog** and from **user-created custom
  exercises**, through a **searchable Add-Exercise screen with muscle-group and equipment
  filters** and a **Recent** section (§3.7).
- Log **sets** per exercise: reps + weight for strength; duration and/or distance for cardio
  or timed work; optional RPE; a **set type** (normal / warm-up / drop set / failure);
  optional per-set note.
- **Column set follows the exercise's tracking mode** — a bodyweight exercise never renders an
  empty KG column, a plank renders TIME, a cardio row renders DISTANCE + TIME.
- A **`PREVIOUS` column, matched per set number**, from the last session that contained the
  same exercise (§3.4).
- **Personal-record marking** on a set that beats the user's previous best for that exercise
  (§3.4) — computed live, never stored.
- A **live session header**: elapsed duration, total volume in kg, set count (§3.7).
- A **rest timer between sets — in-app only** (§3.1.2 R1). Counts down while the session
  screen is open; no notification, no foreground service, no exact alarm.
- **Per-exercise notes** inside a session, and per-exercise ordering.
- **Per-exercise history**: a sheet listing every past set of that exercise, newest first,
  with PRs marked (§3.1.2 R3) — a list, not a chart.
- **Workout history**: past sessions listed newest-first, grouped by date, tappable into a
  session detail that can be edited or deleted.
- A **resume banner** when a session was started and never finished.
- **Importing workout history from a Hevy CSV export** (§3.9).
- Everything syncs to the user's account like the rest of their data.

#### 3.1.2 The deferred list, re-decided item by item (revision 2)

Revision 1 deferred six things. The screenshots show the user using four of them daily, so
each is re-decided here explicitly. Nothing on this list is silently kept or silently dropped.

**R1 — Rest timer → NOW IN SCOPE, in-app only.** Revision 1 deferred it on the grounds that
"a countdown needs notifications or a foreground service" (§9 Q10). **That premise was only
half right.** A countdown that must fire while the phone is locked or the user is in another
app needs a notification channel, a `FOREGROUND_SERVICE` or an exact alarm — all three are
what C7 forbids and what caused the prior battery round. A countdown that only has to be
**visible on a screen the user is looking at** needs none of them: a `LaunchedEffect` keyed on
the running timer, ticking a `StateFlow` once a second, cancelled automatically the moment the
composable leaves composition. That is an ordinary Compose animation-grade cost, and it stops
dead when the screen is backgrounded.

So: **the rest timer counts down only while the live-session screen is composed and
foregrounded.** Leaving the screen or backgrounding the app does not kill the *set* — the
timer's target end-time is a timestamp, so returning to the screen shows the correct remaining
time (or "rest over") — it kills the *ticking*. **No sound, no vibration, no notification, no
wake lock, no `WorkManager` job, no `AlarmManager`.** A timer that alerts the user while
Daybook is closed remains **deferred** and gets its own round with its own battery review;
§9 Q10 is rewritten to put exactly that choice to the user.

The alarm-clock icon in the session header (screens 4–5) maps to a **rest-timer defaults
sheet**, not a system alarm.

**R2 — Templates / saved routines → *(REV5)* RE-DECIDED: NOW IN SCOPE, in a deliberately
minimal form.**

Revisions 2–4 read: *"Nothing in the five screenshots is a template surface; every one of them
is the ad-hoc logging path. Templates need their own tables (a routine, its exercises, its
target sets), their own editor, their own 'start from template' flow and their own sync fields.
That is a round, not a phase."* **The observation was right; the conclusion is overridden by the
user, who has asked for saved routines to be the first thing you see in Beast Mode (§3.7.4).**

The cost estimate above was also honest, and it is what keeps this **small**:

- **Two tables, not six** — `workout_routines` + `workout_routine_exercises` (§3.2.1), both
  folded into the **same** `MIGRATION_21_22` (C8 — one round, one migration; nothing has been
  implemented yet, so there is nothing to migrate *on top of*).
- **One editor, not a builder** — `RoutineEditScreen` (§3.7.5): a name, an optional note, an
  ordered list of exercises, and one target sheet per exercise. It reuses the exercise picker,
  the target sheet reuses `columnsFor(trackingMode)`, and it saves through `StickySaveBar`.
- **"Start from routine" is one repository function** — `startSessionFromRoutine` (§3.4),
  one `withTransaction` that inserts the blocks and the pre-filled sets. The live-session screen
  (§3.7.1) **does not change at all**: a routine-started session is an ordinary session whose
  rows happen to exist already.
- **Sync is the definitions path, which already exists** — routines are *definitions*, like
  habits and custom exercises, not day-bucketed data (§4.2, §4.4).

**What is still deferred, and is not being smuggled in:** Programs, weekly cycles,
periodization, deloads, progression engines, substitution, "suggest my next weight". All of that
stays in **Round C §C.3.3–C.3.6**, unchanged. A routine in Round A is a *list you start from*,
with no opinion about when you should do it or what you should lift. **§9 Q34.**

**And importing routines from another app is NOT in this round** — see §3.2.1 / D6.

**R3 — Progression charts and PR tracking → SPLIT.**
- **PR marking on a set: NOW IN SCOPE.** The gold medal in screen 5 is the single most
  motivating thing on the page, and it is a *query over data Round A already stores* plus one
  pure comparison function — no new table, no new column, no new visual vocabulary (an icon
  + the existing accent). §3.4 specifies it.
- **The `PREVIOUS` column: NOW IN SCOPE, and upgraded.** Revision 1 promised "your last set on
  this exercise" as a one-line hint. The screenshots show something stricter — *previous set
  **N** of this exercise* — which is more useful and barely harder. §3.4's DAO changes
  accordingly.
- **A per-exercise history list: NOW IN SCOPE.** The trend button on every Add-Exercise row
  (screen 3) has to go somewhere, and a reverse-chronological list of past sets is a
  `LazyColumn` of `SoftCard`s over data we already have.
- **Charts / graphs: STAY DEFERRED.** A line chart is genuinely new visual vocabulary — axes,
  gridlines, point markers, a colour ramp, an empty state, a time-range selector — and C5
  forbids inventing visual vocabulary outside the design system. It is also the one part of
  this that is worth doing properly rather than quickly. The trend button opens the **list**
  in Round A; the chart lands on top of it later without a schema change. §9 Q16.

**R4 — Supersets → UI STAYS DEFERRED, DATA IS PRESERVED.** No screenshot shows a superset, and
first-class superset UI means a grouping affordance, a coloured rail down the grouped cards,
reorder-within-group and a rest-timer rule that spans the group. Not MVP. **But** the Hevy CSV
carries a `superset_id` column (§3.9), and §3.2 adds a nullable `superset_id` to
`workout_exercises` so an import is **lossless**. Dropping it would mean the user's superset
structure is destroyed by the import and is unrecoverable without re-exporting from an app
they may have already uninstalled. One nullable TEXT column now costs nothing and saves a
second migration later — the identical argument §3.2 already makes for
`workout_sessions.source`. §9 Q19.

**R5 — Plate calculator, 1RM estimator, body measurements → STAY DEFERRED.** None appears in
any screenshot. A plate calculator needs a per-user plate inventory; 1RM is a formula nobody
asked for; body measurements are a whole second logging domain with their own table, their own
units and their own sync fields. Unchanged.

**R6 — Automatic link to Health-Connect workout sessions → STAYS DEFERRED.** Unchanged, and
§6.4 already argues it at length. Note that §3.9's Hevy import is **not** a counter-example —
see §3.9.6 for why a one-shot file transfer and a live derived-data feed are different things.

**Also deliberately not copied from Hevy, and why:**

- **Anatomical body diagrams and per-exercise illustrations** (the round photos in screen 3,
  the muscle-shaded silhouettes in the stats row of screen 4). These are licensed illustration
  assets — roughly 70 exercise renders plus ~20 muscle maps — that Daybook does not have and
  cannot draw. Shipping them would also add megabytes to a 7.2 MB APK and invent a visual
  language that exists nowhere else in the app (C5). **Daybook uses the existing icon +
  `CardTints` tinted-circle vocabulary instead**, exactly as the Habits and Intake rows already
  do. This is the largest visible divergence from the screenshots and §9 Q14 puts it to the
  user rather than deciding it for them.
- **The muscle-shaded session summary silhouettes.** Same asset problem. The stats row keeps
  Duration / Volume / Sets.
- **A "collapse the session and keep it running" chevron.** Hevy's chevron minimises the live
  workout into a persistent bar. Daybook's equivalent already exists and is cheaper: the
  session is a normal stacked route, and the **resume banner** on the Workout screen is what
  brings the user back. No new persistent-bar component. *(REV4: and because Beast Mode is now
  a separate full-screen mode you can leave entirely, the resume path is stated twice over —
  the banner inside Beast Mode **and** the Today row of §3.6.4, so an interrupted session is
  visible from the normal chrome, not only from inside the mode you just left. §3.6.5 works
  this through.)*

### 3.2 New Room entities (`data/model/WorkoutModel.kt` — new file)

Kept in a **new file**, not appended to the already-362-line `DataModel.kt`. Room does not
care which file an `@Entity` lives in; `AppDatabase`'s `entities = [...]` array is the only
registration point.

> **Revision 2 changed this section in three ways**, all of them free because **no code has
> been written yet** — these are plan edits, not migrations on top of a shipped schema:
> 1. **A fourth table, `workout_exercises`.** Revision 1 had sets pointing straight at a
>    session. The screenshots (an `Add notes here…` and a `Rest Timer` **per exercise block**,
>    not per set and not per session) and the CSV (`exercise_notes` and `superset_id` are
>    **exercise-level** columns, repeated identically on every set row) both say the exercise
>    block is a real entity. Modelling it as one is the difference between a clean join and
>    smearing the same note across every set row and hoping they stay in agreement.
> 2. **`category` → `primaryMuscle` + `equipment`** on `Exercise` (§3.3).
> 3. **`isWarmup: Boolean` → `setType: String`** on `WorkoutSet`, so Hevy's four set types
>    survive an import without a second boolean per type.

```
@Serializable @Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey id: String            // UUID for custom rows
    name: String
    primaryMuscle: String             // REV2: was `category`. One of MuscleGroup's ~20 values (§3.3)
    equipment: String                 // REV2: new. One of Equipment's ~9 values (§3.3)
    trackingMode: String              // "WEIGHT_REPS"/"REPS_ONLY"/"DURATION"/"DISTANCE_DURATION"
    isArchived: Boolean = false       // hidden from the picker, never deleted (history keeps referencing it)
    source: String = "USER"           // REV2: "USER" | "IMPORTED_HEVY" (§3.9.4). Lets the picker badge
                                      // an auto-created import row, and lets a future "tidy up my
                                      // imported exercises" tool find them. One TEXT column.
    createdAt: Long
    notes: String? = null
)
```

> **This table holds ONLY user-created exercises.** The built-in catalog is compiled-in
> Kotlin (§3.3), not seeded rows. See §3.3 for why.

```
@Serializable @Entity(tableName = "workout_sessions",
    indices = [Index("local_date"), Index("started_at")])
data class WorkoutSession(
    @PrimaryKey id: String            // UUID
    localDate: String                 // "yyyy-MM-dd" — the SAME timezone-stable convention as
                                      // HabitOccurrence.local_date / FoodMedOccurrence.local_date.
                                      // This is what MonthPartitioner buckets on. Load-bearing.
    startedAt: Long                   // epoch millis
    endedAt: Long? = null             // null == still in progress
    title: String? = null
    notes: String? = null
    status: String = "ACTIVE"         // "ACTIVE" / "COMPLETED"
    source: String = "MANUAL"         // "MANUAL" | "IMPORTED_HEVY" (REV2, §3.9.4). Still reserved
                                      // for "HEALTH_CONNECT" if §6.4 is ever un-deferred. One TEXT
                                      // column now costs nothing and saves a second migration later.
    routineId: String? = null         // REV5 (§3.2.1): the routine this session was started from,
                                      // or null for an empty/ad-hoc session and for every imported
                                      // one. NULLABLE, and deliberately NOT a foreign key: deleting
                                      // a routine must never delete or orphan the workouts you did
                                      // with it. Nothing in Round A's UI branches on it beyond
                                      // "Start again" (§3.7.4); it exists so a later Programs layer
                                      // (§C.3.3) can attribute sessions without a second migration —
                                      // the identical argument §3.2 already makes for `source` and
                                      // `superset_id`.
    createdAt: Long
)
```

```
// REV2: NEW TABLE. One row per exercise block inside a session.
@Serializable @Entity(tableName = "workout_exercises",
    indices = [Index(value = ["session_id", "order_index"]), Index("exercise_id")])
data class WorkoutExercise(
    @PrimaryKey id: String            // UUID
    sessionId: String
    exerciseId: String                // "builtin:bench_press" or a custom Exercise.id UUID
    orderIndex: Int                   // 0-based position of this block within the session
    notes: String? = null             // the "Add notes here…" line (screens 4–5). Per BLOCK.
    supersetId: String? = null        // REV2 / §3.1.2 R4: preserved losslessly from a Hevy import
                                      // and settable by a future superset UI. Nothing in Round A's
                                      // UI reads it; nothing in Round A's UI writes it either.
    restSeconds: Int? = null          // null == "Rest Timer: OFF" for this block (§3.1.2 R1).
                                      // Per block, per session — which is what makes "carry my
                                      // rest setting over from last time" a query, not a pref store.
    createdAt: Long
)
```

```
@Serializable @Entity(tableName = "workout_sets",
    indices = [Index(value = ["workout_exercise_id", "set_number"]),
               Index("session_id"), Index("exercise_id")])
data class WorkoutSet(
    @PrimaryKey id: String            // UUID (NOT autoincrement — autoincrement rowids do not
                                      // survive the export/import round trip; the existing
                                      // habit_events/food_med_events tables are the cautionary
                                      // example and are deliberately NOT in the wire model)
    workoutExerciseId: String         // REV2: the owning block
    sessionId: String                 // REV2: DENORMALISED ON PURPOSE. Immutable for the row's
                                      // lifetime, and it is what makes month eviction and the
                                      // chunked range export one indexed query instead of a join
                                      // (§4.4 items 1 and 6 — the two places a mistake loses data).
    exerciseId: String                // REV2: DENORMALISED ON PURPOSE. Also immutable, and it is
                                      // what makes the PREVIOUS and personal-record lookups (§3.4)
                                      // index-only on the hottest screen in the feature.
    setNumber: Int                    // 1-based within its block; also the PREVIOUS join key (§3.4)
    reps: Int? = null
    weightKg: Float? = null           // ALWAYS stored in kg; lb is a display conversion only
    durationSeconds: Int? = null
    distanceMeters: Float? = null
    rpe: Int? = null                  // 1..10
    setType: String = "NORMAL"        // REV2: replaces `isWarmup: Boolean`.
                                      // "NORMAL" | "WARMUP" | "DROPSET" | "FAILURE" — exactly Hevy's
                                      // four CSV values (§3.9.3). A boolean could only carry one of
                                      // them, so an import would have had to throw the rest away or
                                      // vandalise the user's own `notes` text with machine metadata.
                                      // Round A's UI offers NORMAL and WARMUP; the other two render
                                      // as a badge and round-trip untouched.
    notes: String? = null
    completedAt: Long? = null         // non-null == the green check is ticked
)
```

**No foreign keys with `onDelete = CASCADE`.** The rest of this schema uses plain indexed
id columns with repository-level cleanup (`habit_occurrences` → `habit_events` is exactly
this pattern), and a real FK would fight the import path, which inserts children before
parents in some orderings. `WorkoutRepository.deleteSession` deletes sets, then blocks, then
the session inside one `database.withTransaction { }`.

**No stored `isPersonalRecord` flag and no stored `previousSet` column.** Both are tempting
and both are wrong: they are *derived from other rows*, so editing or deleting a historical
session silently invalidates every cached value downstream of it, and both would then have to
ride the wire model and be re-derived on every import anyway. They are queries. §3.4.

#### 3.2.1 Routines — two more tables *(REVISION 5, new — D2 / §3.1.2 R2)*

**What a Routine is, stated narrowly so it cannot grow:** *a named, reusable, ordered list of
exercises, each with optional targets.* It is a **template you start a session from**. It has no
schedule, no week, no cycle, no progression rule and no opinion about what you should lift —
all of which stay in Round C (§C.3.3–C.3.6). A routine is to a session what `food_med_tasks` is
to `food_med_occurrences`: the definition, not the event.

Both tables live in the same **`data/model/WorkoutModel.kt`** and land in the same
**`MIGRATION_21_22`** (§3.5). C8 holds — **one round, one migration** — and this is legitimate
rather than a dodge because **no line of Round A has been written yet**: these are plan edits to
a migration that does not exist, not a second migration on top of a shipped schema.

```
// REV5: NEW TABLE. The template itself.
@Serializable @Entity(tableName = "workout_routines",
    indices = [Index("order_index")])
data class WorkoutRoutine(
    @PrimaryKey id: String            // UUID
    name: String                      // "Push day". Normalised + deduped exactly like
                                      // CustomCategoryRepository does category names (§3.4).
    notes: String? = null             // optional, shown under the name on the routine card
    orderIndex: Int                   // 0-based position in "My routines". User-reorderable
                                      // later; set to (max + 1) on create for now.
    isArchived: Boolean = false       // hidden from the list, never hard-deleted by archiving —
                                      // same treatment Exercise gets, and for the same reason:
                                      // WorkoutSession.routineId keeps pointing at it.
    source: String = "USER"           // REV5 / D6 — PROVENANCE HOOK. See the box below.
    createdAt: Long
    updatedAt: Long                   // bumped on every edit; drives "Edited 3 days ago" if it is
                                      // ever wanted, and is the tie-break for a future importer.
)
```

```
// REV5: NEW TABLE. One row per exercise slot inside a routine, with its optional targets.
@Serializable @Entity(tableName = "workout_routine_exercises",
    indices = [Index(value = ["routine_id", "order_index"]), Index("exercise_id")])
data class WorkoutRoutineExercise(
    @PrimaryKey id: String            // UUID
    routineId: String
    exerciseId: String                // "builtin:bench_press" or a custom Exercise.id UUID
    orderIndex: Int                   // 0-based position within the routine
    targetSets: Int? = null           // null == "no target" -> start-from-routine creates NO set
                                      // rows for this exercise and the user taps "+ Add Set"
    targetReps: Int? = null
    targetWeightKg: Float? = null     // ALWAYS kg, like WorkoutSet.weightKg. lb is display only.
    targetDurationSeconds: Int? = null
    targetDistanceMeters: Float? = null
    restSeconds: Int? = null          // null == no routine-level rest opinion; the block then
                                      // falls back to lastRestSecondsForExercise, then to
                                      // app_settings.rest_timer_default_seconds (§3.4)
    notes: String? = null             // copied into WorkoutExercise.notes when a session starts —
                                      // "seat 6, neutral grip" written once, seen every time
    createdAt: Long
)
```

**Every target column is nullable, and Ri3 applies to all five of them.** `target_reps INTEGER
NOT NULL DEFAULT 0` would destroy the distinction between *"3 sets of nothing in particular"*
and *"3 sets of 0 reps"*, exactly as it would on `WorkoutSet` — and §3.7.5's editor depends on
blank meaning blank. **R18 covers these columns too.**

**No foreign keys, same as §3.2.** `WorkoutRepository.deleteRoutine` deletes the
`workout_routine_exercises` rows, then the `workout_routines` row, in one
`database.withTransaction { }`. **Deleting a routine never touches `workout_sessions`** — a
session started from a deleted routine keeps its now-dangling `routine_id`, and §3.7.4's
"Start again" affordance simply does not render when the id resolves to nothing. Losing a
template must never lose the workouts you did with it.

**No `exercise_count` / `set_count` column.** Both are counts over other rows — the §3.2
argument against `isPersonalRecord`, applied again. The routine card's *"5 exercises · 15 sets"*
subtitle comes from a `GROUP BY` query (§3.4 `observeRoutineSummaries`).

> **D6 — `workout_routines.source`, and what it is deliberately NOT.**
> The column exists so that a routine which was **hand-built** can be told apart from one that was
> **imported** — without a further migration. Its value set today is exactly `"USER"`, mirroring
> `Exercise.source` and `WorkoutSession.source` **character for character**: `TEXT NOT NULL
> DEFAULT 'USER'`, an app-specific marker rather than a generic `"IMPORTED"` (§3.9.6 argues why a
> generic marker is useless the moment there are two importers), and **bookkeeping, not a
> permission** — an imported routine would be fully editable like any other.
>
> **Importing routines — from Hevy or anywhere else — is explicitly out of scope for this round
> and is deliberately deferred pending a separate discussion with the user; nothing about it is
> designed here, and §3.9's importer must not grow a routine path.** §3.9 imports workout
> **history** (what you did) and says so throughout; a Hevy *routine* export is a different file
> with a different shape, and pretending otherwise inside `HevyImporter` is how a small importer
> becomes an unreviewable one. The schema hook above is the whole of revision 5's forward
> compatibility, and it costs one TEXT column. **R17 applies: do not start this mid-round.**

### 3.3 Built-in exercise catalog (`data/workout/ExerciseCatalog.kt` — new file, code not rows)

A compiled-in `object ExerciseCatalog` holding ~70 common gym exercises as
`BuiltinExercise(id, name, primaryMuscle, equipment, trackingMode)`, ids prefixed
`"builtin:"` and **permanently stable** (never renamed — a renamed id orphans historical sets).

#### 3.3.1 Taxonomy (REVISION 2 — replaces the old coarse `category`)

Revision 1 had a single 9-value `category` field (`CHEST`/`BACK`/`LEGS`/…). The screenshots
show that will not do the job, for two independent reasons:

1. **The picker filters on two axes, not one.** `All Equipment` and `All Muscles` are separate
   buttons opening separate sheets (screens 1–3). A user looking for "something for biceps with
   a cable" filters both. One field cannot answer two questions.
2. **The muscle axis is finer than a body region.** The rows read *Upper Back*, *Triceps*,
   *Quadriceps*, *Biceps* — not *BACK*, *ARMS*, *LEGS*. Collapsing "Triceps" and "Biceps" into
   "ARMS" makes the filter useless for exactly the case it exists for.

So the catalog carries **two** enum-like string fields, both defined as Kotlin `enum class`es
in `data/workout/ExerciseTaxonomy.kt` and **persisted as their `name` strings** (the same
treatment every other string enum in this schema gets, so an unknown future value read back
from a newer device degrades to `OTHER` instead of throwing):

**`MuscleGroup` (~20)** — the screenshots' alphabetical list, plus the two the screenshots cut
off and an `OTHER` terminator:
`ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CARDIO, CHEST, FOREARMS, FULL_BODY, GLUTES,
HAMSTRINGS, LATS, LOWER_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS, UPPER_BACK, OTHER`.

**`Equipment` (~9)** — matching how both the Hevy UI and the Hevy CSV name things:
`NONE, BARBELL, DUMBBELL, KETTLEBELL, MACHINE, CABLE, PLATE, RESISTANCE_BAND, OTHER`.
`NONE` is the bodyweight case (plank, push-up, pull-up) and is the reason
`BODYWEIGHT` is **not** a separate value — a pull-up is "no equipment", and making it its own
equipment type would hide every bodyweight exercise from the `NONE` filter.

Both are displayed through a pure `MuscleGroupLabels` / `EquipmentLabels` map
(`UPPER_BACK` → "Upper Back"), unit-tested for totality, so adding a value cannot ship a
screen reading `UPPER_BACK` at the user.

**The `CARDIO` muscle group is deliberately kept** even though it is not a muscle — it is in
Hevy's own list (screen 2), and it is where treadmill/cycling/rowing go. Fighting that would
mean inventing a third axis.

#### 3.3.2 Who carries this metadata

| Row kind | `primaryMuscle` | `equipment` | Set by |
|---|---|---|---|
| Built-in (`builtin:…`) | required | required | the compiled-in catalog |
| User-created custom | required | required | the Create-exercise form (§3.7) — both are pickers, both default sensibly, neither is skippable, because an exercise missing from both filters is an exercise the user cannot find again |
| Auto-created by a Hevy import | best-effort | best-effort | §3.9.4 — `equipment` parsed from the name's trailing parenthetical, `primaryMuscle` from the alias table or `OTHER` |

**Yes, historical/custom rows need it too.** An exercise the filters cannot see is an exercise
that effectively does not exist in the picker, and the picker is the only way to add one to a
session.

#### 3.3.3 Proposed coverage (final list is §9 Q9)

Same ~70 exercises as revision 1, re-tabulated on the new muscle axis. The **Equipment** column
is what revision 2 adds.

| Primary muscle | ~count | Examples (equipment in brackets) |
|---|---|---|
| CHEST | 9 | bench press [BARBELL/DUMBBELL], incline bench [BARBELL/DUMBBELL], push-up [NONE], dip [NONE], cable fly [CABLE], chest press [MACHINE] |
| LATS / UPPER_BACK | 11 | deadlift [BARBELL], bent-over row [BARBELL/DUMBBELL], lat pulldown [CABLE], pull-up / chin-up [NONE], seated cable row [CABLE], face pull [CABLE] |
| TRAPS | 2 | shrug [BARBELL/DUMBBELL], upright row [BARBELL] |
| LOWER_BACK | 2 | back extension [NONE], good morning [BARBELL] |
| QUADRICEPS | 7 | back/front squat [BARBELL], leg press [MACHINE], lunge [DUMBBELL], Bulgarian split squat [DUMBBELL], leg extension [MACHINE] |
| HAMSTRINGS | 3 | Romanian deadlift [BARBELL], leg curl [MACHINE], Nordic curl [NONE] |
| GLUTES | 2 | hip thrust [BARBELL], glute bridge [NONE] |
| CALVES | 2 | standing / seated calf raise [MACHINE] |
| SHOULDERS | 7 | overhead press [BARBELL], shoulder press [DUMBBELL], lateral / front / rear-delt raise [DUMBBELL], Arnold press [DUMBBELL] |
| BICEPS | 5 | barbell / dumbbell / hammer / preacher curl, cable curl [CABLE] |
| TRICEPS | 4 | triceps pushdown [CABLE], skullcrusher [BARBELL], overhead extension [DUMBBELL], close-grip bench [BARBELL] |
| FOREARMS | 2 | wrist curl [DUMBBELL], farmer's carry [DUMBBELL] |
| ABDOMINALS | 8 | plank [NONE, DURATION], hanging leg raise [NONE], crunch [NONE], cable crunch [CABLE], Russian twist [PLATE], ab wheel [OTHER], dead bug [NONE], side plank [NONE, DURATION] |
| CARDIO | 8 | treadmill run [MACHINE], outdoor run [NONE], cycling [MACHINE], rowing machine [MACHINE], elliptical [MACHINE], stair climber [MACHINE], jump rope [OTHER], walking [NONE] — all DISTANCE_DURATION or DURATION |
| FULL_BODY | 6 | clean, snatch, clean & jerk, thruster [BARBELL], burpee [NONE], kettlebell swing [KETTLEBELL] |

#### 3.3.4 The Hevy alias map (REVISION 2, new — needed by §3.9)

The same object also holds `ExerciseCatalog.HEVY_ALIASES: Map<String, String>` — normalised
Hevy export name → `builtin:` id — for the names that will not survive normalisation alone
(`"Seated Cable Row - V Grip (Cable)"` → `builtin:seated_cable_row`,
`"Reverse Grip Lat Pulldown (Cable)"` → `builtin:lat_pulldown`,
`"Single Arm Tricep Extension (Dumbbell)"` → `builtin:triceps_overhead_extension`,
`"Bicycle Crunch"` → `builtin:crunch`, and so on). Target ~40 entries, seeded from the real
sample file and Hevy's most common movements. It is a **plain compiled-in map, unit-tested for
"every value is a real catalog id"** — which is the whole reason it is safe to hand-write.
§3.9.4 explains why an alias table beats fuzzy string distance.

**Why code, not seeded rows:**

1. A seeded table means every future catalog improvement (a typo, a better category, a new
   exercise) is a **Room migration** — and this project's discipline is one migration per
   round.
2. Seeded rows would ride the Firestore `definitions` blob on every device, inflating the
   parent doc and changing `definitionsHash` for a user who never opened Workout mode.
3. Users on two devices would get two copies if the seed ever re-ran.

`WorkoutRepository.observeExerciseCatalog()` returns `builtins + customRows`, merged and
sorted, so the picker sees one flat list. An `exerciseId` that resolves to neither (an old
custom exercise deleted on another device before this device synced) renders as
*"Unknown exercise"* rather than crashing — the same defensive read the icon resolver
already does (`ui/icons/IconResolverTest.kt`).

### 3.4 DAOs and repository

New files:

- `data/local/ExerciseDao.kt` — `observeCustom(): Flow<List<Exercise>>`, `getById`,
  `insert`/`update`/`archive`, `allIds()`, `deleteByIds(ids)`, `deleteAll()`.
  (`allIds` / `deleteByIds` / `deleteAll` exist on every other definition DAO for the sync
  diff path — see `HabitDao`.)
- `data/local/WorkoutDao.kt` — sessions + blocks + sets in one DAO:
  - `observeSessionsBetween(startMillis, endMillis): Flow<List<WorkoutSession>>`
  - `observeRecentSessions(limit: Int): Flow<List<WorkoutSession>>`
  - `observeActiveSession(): Flow<WorkoutSession?>`
  - `observeExercisesForSession(sessionId): Flow<List<WorkoutExercise>>` *(REV2)*
  - `observeSetsForSession(sessionId): Flow<List<WorkoutSet>>`
  - `getSessionsInLocalDateRange(startYmd, endYmd)` — for export
  - `getExercisesForSessions(ids)` / `getSetsForSessions(ids)` — **both must be chunked at 900
    bound vars**, the `SQLITE_MAX_VARS` constant already in `ExportImportRepository` (there is
    an existing instrumented test `ChunkedDeleteTest.kt` guarding exactly this class of bug)
  - `deleteSessionsInLocalDateRange(startYmd, endYmd)` + `deleteExercisesForSessions(ids)` +
    `deleteSetsForSessions(ids)` — for month eviction and month import
  - `sessionsStartingBetween(fromMillis, toMillis): List<WorkoutSession>` *(REV2)* — the
    duplicate probe for §3.9.5

  **REVISION 2 — the two derived-value queries.** These replace revision 1's single
  `lastSetForExercise(exerciseId)`. Both are `@Query`s, neither stores anything (§3.2):

  - **`previousSetsForExercise(exerciseId, excludeSessionId): List<WorkoutSet>`** — every set
    of the **most recent prior session** that contained this exercise, in one query:

    ```sql
    SELECT s.* FROM workout_sets s
    JOIN workout_sessions ws ON ws.id = s.session_id
    WHERE s.exercise_id = :exerciseId
      AND s.session_id != :excludeSessionId
      AND s.completed_at IS NOT NULL
      AND ws.status = 'COMPLETED'
    ORDER BY ws.started_at DESC
    LIMIT 50
    ```

    The ViewModel keeps only the rows belonging to the newest `session_id` in the result and
    indexes them **by `set_number`** — which is what makes the `PREVIOUS` column per-set
    rather than per-exercise (§3.1.0). Set 3 with no set 3 last time renders `–`. This is a
    **pure function over the query result** (`previousBySetNumber(rows): Map<Int, WorkoutSet>`)
    and gets its own unit test; the SQL is deliberately dumb so the interesting part is
    testable without Room.

  - **`bestSetForExercise(exerciseId, excludeSessionId): WorkoutSet?`** — the pre-session
    personal best, read **once when the exercise block is opened**, not per keystroke:

    ```sql
    SELECT s.* FROM workout_sets s
    WHERE s.exercise_id = :exerciseId
      AND s.session_id != :excludeSessionId
      AND s.completed_at IS NOT NULL
      AND s.set_type = 'NORMAL'            -- a warm-up is never a PR
    ORDER BY COALESCE(s.weight_kg, 0) DESC,
             COALESCE(s.reps, 0) DESC,
             COALESCE(s.duration_seconds, 0) DESC,
             COALESCE(s.distance_meters, 0) DESC
    LIMIT 1
    ```

    and a **pure** `isPersonalRecord(candidate, best, trackingMode): Boolean` that decides by
    tracking mode — heavier weight (WEIGHT_REPS; equal weight with strictly more reps also
    counts), more reps (REPS_ONLY), longer (DURATION), further (DISTANCE_DURATION) — with a
    unit test per mode and a "no history at all is **not** a PR" case, because marking every
    first-ever set with a medal makes the medal meaningless.

    **Why a query and not a cached `is_pr` column:** a PR is a statement about *every other
    row*, so deleting or editing one historical session would silently leave stale medals
    scattered through the history with nothing to recompute them; and a stored flag would have
    to ride the wire model, where an older device (§4.3) could push back a version with the
    flag wrong. The query costs one indexed row read per exercise block opened.

  - `lastRestSecondsForExercise(exerciseId): Int?` *(REV2)* — the most recent block's
    `rest_seconds`, so the rest timer the user set last time is pre-filled this time (§3.1.2
    R1). One more indexed read; no preference table, no new `app_settings` column per exercise.

- **`data/local/RoutineDao.kt` *(REV5, new — §3.2.1)*.** Kept as its own DAO rather than a fifth
  concern inside `WorkoutDao`, because routines are **definitions** and follow the definition
  DAOs' shape (`HabitDao`, `ExerciseDao`), including the three sync-diff functions:

  - `observeRoutineSummaries(): Flow<List<RoutineSummary>>` — everything the landing screen
    (§3.7.4) needs, in **one** query, so the list is not N+1 reads:

    ```sql
    SELECT r.id            AS id,
           r.name          AS name,
           r.notes         AS notes,
           COUNT(re.id)                 AS exerciseCount,
           COALESCE(SUM(re.target_sets), 0) AS targetSetCount
    FROM workout_routines r
    LEFT JOIN workout_routine_exercises re ON re.routine_id = r.id
    WHERE r.is_archived = 0
    GROUP BY r.id
    ORDER BY r.order_index ASC
    ```

    returning `data class RoutineSummary(val id: String, val name: String, val notes: String?,
    val exerciseCount: Int, val targetSetCount: Int)` — a plain Room POJO, **not** an entity, and
    the `LEFT JOIN` is what makes a routine with no exercises still appear (it renders
    *"No exercises yet"*, which is a state the editor can genuinely leave behind).
  - `observeRoutine(routineId): Flow<WorkoutRoutine?>`
  - `observeRoutineExercises(routineId): Flow<List<WorkoutRoutineExercise>>` — `ORDER BY order_index`
  - `getRoutine(routineId): WorkoutRoutine?` / `getRoutineExercises(routineId): List<WorkoutRoutineExercise>`
    — the non-Flow reads `startSessionFromRoutine` uses inside its transaction
  - `maxOrderIndex(): Int?` — for "append the new routine at the end"
  - `upsertRoutine(routine)` / `upsertRoutineExercises(rows)` / `deleteRoutineExercises(routineId)` /
    `deleteRoutine(routineId)` / `archiveRoutine(routineId, archived)`
  - `allIds(): List<String>` / `deleteByIds(ids)` / `deleteAll()` — the definition-sync diff path
    (§4.4 item 5). **`deleteByIds` must be chunked at the existing `SQLITE_MAX_VARS = 900`**
    (R10), like every other bulk id call in this plan.

- `data/WorkoutRepository.kt` — `@Singleton @Inject`, takes `AppDatabase`. Mirrors
  `HabitRepository`'s shape exactly. Owns:
  - `startSession()` *(REV5: renamed **`startEmptySession(): String`**, returning the new
    session id — the old name is now ambiguous next to `startSessionFromRoutine`)*,
    `finishSession(id)`, `discardSession(id)`
  - `addExerciseToSession(...)`, `removeExerciseFromSession(...)`, `reorderExercises(...)`,
    `setExerciseNotes(...)`, `setExerciseRest(...)` *(REV2)*
  - `addSet(...)`, `updateSet(...)`, `deleteSet(id)`, `toggleSetComplete(id)`
  - `createCustomExercise(name, primaryMuscle, equipment, trackingMode)` with the same
    normalise-and-dedupe treatment `CustomCategoryRepository` gives category names (there are
    already two unit tests for that idiom: `CustomCategoryNormaliseTest`,
    `CustomPromptNormaliseTest`)
  - `deleteSession(id)` inside `withTransaction` — sets, then blocks, then the session
  - `importHevyCsv(...)` *(REV2, §3.9)*
  - **Routines *(REV5, §3.2.1)*** — exact signatures, no implementer's call:

    ```kotlin
    // The editor's in-memory row. Immutable, primitives only (P5), no entity, no Flow.
    data class RoutineExerciseDraft(
        val id: String,                       // stable UUID, minted on add, kept across reorders
        val exerciseId: String,
        val targetSets: Int? = null,
        val targetReps: Int? = null,
        val targetWeightKg: Float? = null,
        val targetDurationSeconds: Int? = null,
        val targetDistanceMeters: Float? = null,
        val restSeconds: Int? = null,
        val notes: String? = null
    )

    fun observeRoutineSummaries(): Flow<List<RoutineSummary>>
    fun observeRoutineExercises(routineId: String): Flow<List<WorkoutRoutineExercise>>
    suspend fun createRoutine(name: String, notes: String?, exercises: List<RoutineExerciseDraft>): String
    suspend fun updateRoutine(routineId: String, name: String, notes: String?, exercises: List<RoutineExerciseDraft>)
    suspend fun duplicateRoutine(routineId: String): String
    suspend fun deleteRoutine(routineId: String)
    suspend fun startSessionFromRoutine(routineId: String): String
    ```

    - `createRoutine` / `updateRoutine` run in **one `withTransaction`**. `updateRoutine` is
      **delete-all-then-reinsert** for the child rows (`deleteRoutineExercises(routineId)` then
      `upsertRoutineExercises(rows)`), not a diff: a routine is a handful of rows, `orderIndex`
      changes on every reorder, and a diff here would be more code with more ways to leave the
      ordering inconsistent. It bumps `updatedAt`.
    - The **name is normalised and deduped** with the same idiom `CustomCategoryRepository` uses
      for category names — trim, collapse inner whitespace, case-insensitive compare against
      existing non-archived routines, and on a clash append `" 2"`, `" 3"`. Extracted as a pure
      `fun uniqueRoutineName(desired: String, existing: List<String>): String` with its own
      **`RoutineNameNormaliseTest`**, exactly as `CustomCategoryNormaliseTest` /
      `CustomPromptNormaliseTest` already do.
    - `duplicateRoutine` is that function doing its job: copy the routine and its rows with new
      UUIDs, name = `uniqueRoutineName(original.name, …)` → *"Push day 2"*, `source = "USER"`
      **even if the original was imported** (a copy you made is yours).
    - **`startSessionFromRoutine(routineId)` — the whole of "pre-populate", specified exactly.**
      One `withTransaction`:
      1. Insert a `WorkoutSession` with `status = "ACTIVE"`, `source = "MANUAL"`,
         **`routineId = routineId`**, `title = routine.name`, `startedAt = now`,
         `localDate = today`.
      2. For each `WorkoutRoutineExercise` in `orderIndex` order, insert a `WorkoutExercise` with
         the same `orderIndex`, `notes = routineExercise.notes`, and
         `restSeconds = routineExercise.restSeconds` — **the routine's rest value wins over
         `lastRestSecondsForExercise` and over `app_settings.rest_timer_default_seconds`**. The
         precedence, stated once so it is not re-derived three times:
         **routine target → last time's block value → the app default → OFF.**
      3. For each such block, insert **`targetSets ?: 0`** `WorkoutSet` rows,
         `setNumber = 1..n`, with `reps = targetReps`, `weightKg = targetWeightKg`,
         `durationSeconds = targetDurationSeconds`, `distanceMeters = targetDistanceMeters`,
         `setType = "NORMAL"`, and **`completedAt = null`**.
      4. Return the session id; the caller navigates to `workout_session/{id}`.

      **Why the target values are written into the set rows rather than shown as ghost
      placeholders**, decided rather than left open: a pre-filled row that the user ticks means
      *"I did exactly what I planned"*, which is the common case and costs one tap. The
      alternative — `NULL` values with the target as placeholder text — makes the green check
      either dangerous (tick it and store a set with no numbers, which contributes 0 volume and
      is invisible to PR detection) or conditional (disable the check until the row is filled),
      and neither is worth it. **`completedAt` is what separates plan from performance**, and it
      already is: §3.4's `previousSetsForExercise` and `bestSetForExercise` both filter
      `completed_at IS NOT NULL`, and `sessionStats` counts completed sets only — so an
      abandoned routine-started session **cannot** inflate Volume, invent a PR, or pollute the
      `PREVIOUS` column. **No `is_target` / `is_planned` flag is added**: it would be a stored
      derived state, which is the same mistake §3.2 rejects for `isPersonalRecord`.
      **`targetSets = null` creates no set rows at all** — no target means nothing to pre-fill,
      and the block behaves exactly like an ad-hoc one.
      A **`StartFromRoutineTest`** covers: 3 exercises with targets → 3 blocks and the right set
      rows with the right values and `completedAt == null`; an exercise with `targetSets = null`
      → a block with zero sets; rest-precedence over a `lastRestSecondsForExercise` value; and
      that `sessionStats` on the fresh session reports **0 volume, 0 sets**.

Also **pure, in `data/workout/`, so they are unit-testable without Room** *(REV2)*:
`sessionStats(sets): SessionStats` returning `(totalVolumeKg, setCount)` for the live header's
Volume / Sets figures — volume is `Σ (weightKg × reps)` over **completed** sets only, warm-ups
included (Hevy counts them; the screenshots' 1,680 kg is a plain sum), and a bodyweight or
duration set contributes 0 rather than being skipped, so the set count and the volume never
disagree about which rows they looked at.

Wired in `di/DatabaseModule.kt` as `@Provides @Singleton fun provideWorkoutRepository(db)`.

### 3.5 Migration — `MIGRATION_21_22` (NEEDS USER SIGN-OFF, C4)

Appended to `data/local/Migrations.kt`, registered in `DatabaseModule.addMigrations(…)`,
`AppDatabase.version` 21 → 22, entity list gains `Exercise::class`,
`WorkoutSession::class`, `WorkoutExercise::class` *(REV2)*, `WorkoutSet::class`,
**`WorkoutRoutine::class`, `WorkoutRoutineExercise::class` *(REV5)***, and **five** new
`abstract fun`s (`exerciseDao`, `workoutDao`, `routineDao` *(REV5)*, plus the existing two the
round already touches).

**This is still exactly one migration (C8).** Revision 2 added a fourth table; **revision 5 adds
a fifth and sixth (`workout_routines`, `workout_routine_exercises`) and one nullable column
(`workout_sessions.routine_id`)** — and they all land in `MIGRATION_21_22` alongside the rest of
Round A. This is legitimate rather than a C8 dodge for one specific reason: **no line of Round A
has been implemented, so `MIGRATION_21_22` does not exist yet.** There is nothing to migrate on
top of; these are edits to a migration that has never run. The Hevy import (§3.9) still needs
**no migration of its own** (§3.9.8), and `AppDatabase.version` stays at **22 for the whole of
Round A**.

```sql
CREATE TABLE IF NOT EXISTS `exercises` (
  `id` TEXT NOT NULL, `name` TEXT NOT NULL,
  `primary_muscle` TEXT NOT NULL, `equipment` TEXT NOT NULL,   -- REV2: was a single `category`
  `tracking_mode` TEXT NOT NULL, `is_archived` INTEGER NOT NULL DEFAULT 0,
  `source` TEXT NOT NULL DEFAULT 'USER',                       -- REV2
  `created_at` INTEGER NOT NULL, `notes` TEXT, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `workout_sessions` (
  `id` TEXT NOT NULL, `local_date` TEXT NOT NULL, `started_at` INTEGER NOT NULL,
  `ended_at` INTEGER, `title` TEXT, `notes` TEXT,
  `status` TEXT NOT NULL DEFAULT 'ACTIVE', `source` TEXT NOT NULL DEFAULT 'MANUAL',
  `routine_id` TEXT,                                           -- REV5 (§3.2.1). NULLABLE, no FK.
  `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_sessions_local_date` ON `workout_sessions`(`local_date`);
CREATE INDEX IF NOT EXISTS `index_workout_sessions_started_at` ON `workout_sessions`(`started_at`);

-- REV5: NEW TABLES (§3.2.1). Routines are DEFINITIONS, like habits and custom exercises —
-- they carry no local_date, they are never month-partitioned and they are never evicted (§4.4).
CREATE TABLE IF NOT EXISTS `workout_routines` (
  `id` TEXT NOT NULL, `name` TEXT NOT NULL, `notes` TEXT,
  `order_index` INTEGER NOT NULL,
  `is_archived` INTEGER NOT NULL DEFAULT 0,
  `source` TEXT NOT NULL DEFAULT 'USER',                       -- REV5 / D6: provenance hook
  `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_routines_order_index` ON `workout_routines`(`order_index`);

CREATE TABLE IF NOT EXISTS `workout_routine_exercises` (
  `id` TEXT NOT NULL, `routine_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL,
  `order_index` INTEGER NOT NULL,
  -- Ri3: every target is NULLABLE. A NOT NULL DEFAULT 0 here destroys the difference between
  -- "no target" and "a target of zero", exactly as it would on workout_sets. R18 covers these.
  `target_sets` INTEGER, `target_reps` INTEGER, `target_weight_kg` REAL,
  `target_duration_seconds` INTEGER, `target_distance_meters` REAL,
  `rest_seconds` INTEGER, `notes` TEXT,
  `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_routine_exercises_routine_id_order_index` ON `workout_routine_exercises`(`routine_id`,`order_index`);
CREATE INDEX IF NOT EXISTS `index_workout_routine_exercises_exercise_id` ON `workout_routine_exercises`(`exercise_id`);

-- REV2: new table (§3.2)
CREATE TABLE IF NOT EXISTS `workout_exercises` (
  `id` TEXT NOT NULL, `session_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL,
  `order_index` INTEGER NOT NULL, `notes` TEXT, `superset_id` TEXT, `rest_seconds` INTEGER,
  `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_exercises_session_id_order_index` ON `workout_exercises`(`session_id`,`order_index`);
CREATE INDEX IF NOT EXISTS `index_workout_exercises_exercise_id` ON `workout_exercises`(`exercise_id`);

CREATE TABLE IF NOT EXISTS `workout_sets` (
  `id` TEXT NOT NULL, `workout_exercise_id` TEXT NOT NULL,      -- REV2
  `session_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL,
  `set_number` INTEGER NOT NULL,
  `reps` INTEGER, `weight_kg` REAL, `duration_seconds` INTEGER, `distance_meters` REAL,
  `rpe` INTEGER,
  `set_type` TEXT NOT NULL DEFAULT 'NORMAL',                    -- REV2: was `is_warmup` INTEGER
  `notes` TEXT, `completed_at` INTEGER, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_sets_workout_exercise_id_set_number` ON `workout_sets`(`workout_exercise_id`,`set_number`);
CREATE INDEX IF NOT EXISTS `index_workout_sets_session_id` ON `workout_sets`(`session_id`);
CREATE INDEX IF NOT EXISTS `index_workout_sets_exercise_id` ON `workout_sets`(`exercise_id`);

-- device-local settings, same treatment as every app_settings column since v16.
-- REV5: all four below are surfaced on BEAST MODE's OWN settings screen (§3.8.2,
-- route "workout_settings"), NOT on Daybook's main settings_* screens. The storage is unchanged;
-- only where the switches live changed.
ALTER TABLE app_settings ADD COLUMN weight_unit TEXT NOT NULL DEFAULT 'KG';
-- REV4: this is the BEAST MODE accent — the accent for the whole Workout mode subtree, not a
-- tab tint. Default flips LAVENDER -> CORAL now that it no longer has to sit in the pill nav
-- beside the other three. §3.8.1. CORAL is an existing AccentColor (Accent.kt:21), not a new
-- colour literal (C5).
ALTER TABLE app_settings ADD COLUMN workout_accent_color TEXT NOT NULL DEFAULT 'CORAL';
-- REV2 (§3.1.2 R1): the default pre-filled into a new exercise block's rest timer.
-- 0 == OFF, which is what the screenshots show as the shipped default.
ALTER TABLE app_settings ADD COLUMN rest_timer_default_seconds INTEGER NOT NULL DEFAULT 0;
-- REV4 (§3.6.3): the long-press hint's lifecycle. Tri-state, one column, device-local:
--   0 = never shown          -> show the coach-mark AND the dot on the Today icon
--   1 = coach-mark dismissed -> hide the coach-mark, KEEP the dot (gesture still unused)
--   2 = gesture used at least once -> show neither, ever again
ALTER TABLE app_settings ADD COLUMN workout_hint_state INTEGER NOT NULL DEFAULT 0;
-- REV4 (§3.6.4): the visible fallback entry point on Today. Default ON.
-- CONTINGENT ON §9 Q32 — if the user answers "long-press only", this column and the row it
-- backs are both dropped from the round rather than shipped unused.
-- REV5: its switch now lives on Beast Mode's own settings screen (§3.8.2), not main Settings.
ALTER TABLE app_settings ADD COLUMN workout_today_card_enabled INTEGER NOT NULL DEFAULT 1;
```

> **REVISION 5 — still 100 % additive, and still one migration.** Everything added above is
> `CREATE TABLE` / `CREATE INDEX` / a **nullable** `ADD COLUMN`. **No `UPDATE`, no `DROP`, no
> table rebuild, no backfill** — a user upgrading from build 24 gets six empty tables, one null
> column and four new settings columns at their declared defaults, and nothing they already had
> is read or rewritten. C2 holds, and revision 4's improvement (deleting the `nav_tabs` `UPDATE`)
> is not undone.

Every `DEFAULT` above must **byte-match** the corresponding `@ColumnInfo(defaultValue = …)`
in the entity, or Room's identity-hash check fails at open. That rule has bitten this repo
before — the existing migrations all carry a comment saying so.

> **REVISION 4 — the `nav_tabs` `UPDATE` is deleted, and this is a real improvement.**
> Revision 3 ended this block with
> `UPDATE app_settings SET nav_tabs = nav_tabs || ',workout' WHERE …`, to make a fourth tab
> visible on existing installs. **Workout is not a tab any more (§3.6), so there is nothing to
> make visible.** That statement was the **only non-additive statement in the whole round**;
> with it gone, `MIGRATION_21_22` is now **100 % `CREATE TABLE` / `ALTER TABLE … ADD COLUMN`**,
> which is exactly what C2 asks for and removes the one thing in this round that could alter a
> row a user had already configured. **`nav_tabs` is not read, written or mentioned by Round A.**

**Tests to add:** `androidTest/…/MigrationTest.kt` gains a `migrate21To22` case (open a
21.json DB, run the migration, assert the **six** new tables *(REV5: was four —
`workout_routines` and `workout_routine_exercises` are the additions)* + **five** new
`app_settings` columns + **`workout_sessions.routine_id`** exist *(REV4: was three columns plus a
`nav_tabs` assertion; the `nav_tabs` assertion must **not** be written — asserting the absence of
an update is meaningless, and `nav_tabs` must simply be left alone)*). **R18's guard extends to
the five `workout_routine_exercises` target columns**: assert none of them is `NOT NULL`.
`app/schemas/…/22.json` regenerates on the next build — commit it.

### 3.6 Navigation — **Beast Mode is a mode, not a tab** *(SECTION REPLACED IN REVISION 4, EXTENDED IN REVISION 5)*

> **The decision this section implements, in the user's terms.** *Workout mode ("Beast Mode")
> is not a fourth item in the bottom nav. You get into it by **pressing and holding the "Today"
> item** in the bottom bar. Tapping Today still opens Today, exactly as it does now. Holding it
> takes you into a full-screen workout mode with its own look, and there is a clear way back out.*
>
> **Revision 3's §3.6 is withdrawn in full**, not amended: the four-route `ALL_ROUTES`, the
> `NavItemSpec` for Workout, the pager arm, the `NavigationSettingsScreen` toggle and the
> `ic_nav_workout` pill icon were all consequences of the tab design and none of them survives.
>
> **REVISION 5 — one detail of revision 4 is overridden, on the user's instruction.**
> Revision 4 specified Beast Mode as having **no bottom nav of its own**, with a `×` in its
> header as the way out. **That is replaced:** Beast Mode gets its **own** bottom nav — the same
> `FloatingPillNav` component, its own three destinations (**§3.6.7**) — and the way out is a
> **press-and-hold on its leftmost nav item**, the exact mirror of the press-and-hold on Today
> that got you in (**§3.6.8**). The `×` is withdrawn (§3.7.4 explains what takes the header's
> trailing slot instead, and §9 **Q35** asks whether it should come back).
> **Everything else in §3.6.0–§3.6.4 stands unchanged**, including — importantly — the fact that
> Daybook's *own* nav is still exactly three items and `NavConfig` is still not touched.

#### 3.6.0 What does **not** change — read this first, it is most of the section

| File | Revision 3 said | **Revision 4** |
|---|---|---|
| `ui/NavConfig.kt` | `ALL_ROUTES` gains `"workout"` | **No change.** Stays `listOf("home", "routines", "foodmed")`. |
| `ui/NavConfigTest.kt` | update the three-route expectations | **No change.** It keeps asserting three routes, and it is now a *tripwire*: if a later round quietly re-adds `"workout"` to `ALL_ROUTES`, this test goes red and the implementer is forced back here. |
| `app_settings.nav_tabs` | appended to by `MIGRATION_21_22` | **Not read, not written, not mentioned** by Round A (§3.5). |
| `ui/settings/NavigationSettingsScreen.kt` | gains a "Show Workout tab" toggle + a `labelFor` entry | **Not touched at all this round.** Workout is not a tab-visibility setting, so it has no business on that screen. |
| `HorizontalPager` / `beyondViewportPageCount` / `pagerState` | a fourth page | **No change.** Three pages, same swipe behaviour, same cold-composition cost. |
| The pager's `when (visibleRoutes.getOrElse(page))` | must gain an explicit `"foodmed" ->` arm so a `"workout"` page id does not fall through to Intake | **No change — and revision 3's warning is withdrawn.** There is no `"workout"` page id any more, so `else -> FoodMedScreen(...)` stays exactly as it is. |
| `BackHandler(enabled = settledPage != 0) { goToPage(0) }` inside `composable("main")` | "already generalises" | **No change, and it is not involved.** `NavHost` composes only the current destination, so while Beast Mode is on top this `BackHandler` is not registered at all. §3.6.5 explains what handles back instead: nothing new — the default pop. |
| ~~`DaybookScaffold`'s `showNav` logic — **No change**~~ | — | **AMENDED IN REVISION 5.** Revision 4 said `showNav = onMain` gives Beast Mode a full-screen, no-bottom-nav look for free. **Beast Mode now has a bottom nav of its own (§3.6.7)**, so `showNav` gains one disjunct — `onMain || backStackRoute in WorkoutRoutes.NAV` — and the `navItems` / `onSelectRoute` / `currentRoute` arguments switch to Beast Mode's set on those three routes. **Everything else about `DaybookScaffold` is still untouched**, and the *other* workout routes (the live session, the pickers, the forms, Beast Mode's own settings) still get full-screen-no-nav for free, exactly as revision 4 described. §3.6.7 gives the exact diff; it is the only change to the call site. |
| `res/drawable/ic_nav_workout.xml` | a fourth pill-nav icon | **Renamed `ic_workout.xml`** and repurposed: it is no longer a nav icon, but Beast Mode's header, the Today row (§3.6.4) and the Settings row still need a dumbbell glyph. Same 24 dp viewport, same stroke weight, same `?attr` tinting as the `ic_nav_*` set. **It must still be added to `androidTest/…/NavIconInflateTest.kt`** — that test exists to catch a vector that inflates on the authoring machine but not on API 26, which has nothing to do with where the vector is used. |

**Withdrawn risk.** Revision 3's "four nav labels may clip at 360 dp" layout risk (§8 R6) is
**gone**: there is no fourth label. The pill nav ships this round with the same three items,
the same widths and the same text it has today. See §8.

#### 3.6.1 The gesture — exact Compose mechanics

**Three small, additive changes. No new file.**

**(a) `ui/components/Components.kt` — a long-press sibling for `clickableImpl`.** The existing
helper (`:106`) is:

```kotlin
internal fun Modifier.clickableImpl(
    interaction: MutableInteractionSource,
    onClick: () -> Unit
): Modifier = this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
```

Add directly beneath it, in the same style:

```kotlin
// REV4 (§3.6.1) — the long-press-capable twin of clickableImpl. Same "no indication" rule: the
// app draws its own press feedback (scale/tint), it never uses the Material ripple.
// `onLongClickLabel` is NOT cosmetic — TalkBack reads it as "double tap and hold to <label>",
// and it is what puts the gesture in switch-access's actions menu. Never pass null here.
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.combinedClickableImpl(
    interaction: MutableInteractionSource,
    onLongClickLabel: String,
    onLongClick: () -> Unit,
    onClick: () -> Unit
): Modifier = this.combinedClickable(
    interactionSource = interaction,
    indication = null,
    onLongClickLabel = onLongClickLabel,
    onLongClick = onLongClick,
    onClick = onClick
)
```

`Modifier.combinedClickable` is `@ExperimentalFoundationApi` in the foundation version this
project pins; `MainActivity.MainApp()` is **already** annotated `@OptIn(ExperimentalFoundationApi::class)`
(for `HorizontalPager`), so this is a known, already-accepted opt-in in this codebase, not a new
kind of risk.

**(b) `ui/components/Navigation.kt` — `FloatingPillNav` gains two optional parameters**, both
defaulted so the signature stays source-compatible:

```kotlin
fun FloatingPillNav(
    items: List<NavItemSpec>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongSelect: ((String) -> Unit)? = null,   // REV4 §3.6.1
    longPressRoute: String? = null,             // REV5 §3.6.8 — WHICH item accepts the hold
    longPressLabel: String? = null,             // REV5 §3.6.8 — TalkBack's "double tap and hold to …"
    hintDotRoutes: Set<String> = emptySet()     // REV4 §3.6.3
)
```

> **REVISION 5 — why `longPressRoute` / `longPressLabel` exist.** Revision 4 hard-coded the gate
> as `item.route == "home"` and the label as `"Start a workout"`, because there was only ever one
> long-pressable nav item. **§3.6.8 adds a second, symmetric one** — hold Beast Mode's leftmost
> item to leave Beast Mode — and hard-coding two route ids inside a generic nav component is how
> a generic component stops being one. **Two parameters, one implementation, two configurations.**
> Daybook's nav passes `longPressRoute = "home"`, `longPressLabel = "Start a workout"`;
> Beast Mode's nav passes `longPressRoute = WorkoutRoutes.HOME`,
> `longPressLabel = "Leave Beast Mode"`. **Nothing else about the gesture differs** — not the
> threshold, not the dead zone, not the ramp, not the haptic, not the reduced-motion branch.

and `DaybookScaffold` / `DaybookScaffoldNav` thread the same four through (plus the coach-mark
slot of §3.6.3). There is exactly **one** `DaybookScaffold` call site in the app
(`MainActivity.kt:608`) — verified — so this is a few-line change, not a migration.

Inside the `items.forEach { item }` loop, line `:108` changes from `clickableImpl` to:

```kotlin
// REV5: was `item.route == "home"`. See the box above.
val longPressable = onLongSelect != null && longPressRoute != null && item.route == longPressRoute
…
.then(
    if (longPressable) Modifier.combinedClickableImpl(
        interaction = interaction,
        onLongClickLabel = longPressLabel ?: "",   // never null in practice; §3.6.1(a) forbids null
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongSelect!!(item.route)
        },
        onClick = { onSelect(item.route) }
    ) else Modifier.clickableImpl(interaction) { onSelect(item.route) }
)
```

Four things about this that are deliberate and must not be "tidied":

1. **The gate is a *route* check, not an index check** — *(REV5: and now a parameterised route
   check)*. Today is guaranteed present and first by `NavConfig.visibleRoutesFrom` (its KDoc
   states the "index 0 == Today" invariant), but keying on the route id means the gesture cannot
   migrate to the wrong item if reordering is ever implemented. The same holds for Beast Mode's
   own nav, whose leftmost item is `WorkoutRoutes.HOME` by construction (§3.6.7).
2. **The `interaction: MutableInteractionSource` is hoisted out of the modifier chain** into
   `remember { MutableInteractionSource() }` per item, because §3.6.2's press feedback reads
   `interaction.collectIsPressedAsState()`. It already is hoisted today (`:108`).
3. **The long-press works from any tab**, not only when Today is the selected page. Holding
   Today from Habits or Intake enters Beast Mode directly; it does **not** first switch to
   Today. (Tapping still switches, as now.)
4. **Long-pressing Today while Today is already selected is a normal long-press**, not a no-op —
   this is in fact where most users will do it.

**(c) `ui/MainActivity.kt` — one new callback**, `remember(navController)`-wrapped exactly like
the twelve that already exist:

```kotlin
val goWorkout: () -> Unit = remember(navController) {
    { navController.navigate("workout") { launchSingleTop = true } }
}
```

passed as `onLongSelectRoute = { goWorkout() }` on the `DaybookScaffold` call.

**The long-press duration is the platform's, and is not ours to pick: ~500 ms.**
`Modifier.combinedClickable` uses Compose's `viewConfiguration.longPressTimeoutMillis`, which is
`ViewConfiguration.getLongPressTimeout()` — **500 ms** on stock Android, unchanged since API 1.
**Do not hand-roll a timer and do not pass a custom duration.** The reason is not tidiness: a
user who has set Accessibility → *Touch and hold delay* to Medium (1 s) or Long (1.5 s) — which
people with tremor routinely do — gets their own value automatically from
`combinedClickable`, and gets nothing at all from a hard-coded `delay(500)`. The one place the
number is needed explicitly is §3.6.2's feedback ramp, which reads it from
`LocalViewConfiguration.current.longPressTimeoutMillis` rather than typing `500`.

#### 3.6.2 Press-and-hold feedback — what happens during the hold

**A long-press with no feedback feels broken**: the user holds, nothing moves, they let go
early, and they conclude the gesture does not exist. So the hold is visibly answered, and the
answer completes at exactly the moment the gesture fires.

**The decision — one ramp, one haptic, and nothing else:**

- **During the hold — the Today icon grows and takes the accent.** Read
  `val pressed by interaction.collectIsPressedAsState()`. After a **120 ms** dead zone (so an
  ordinary tap, which is over in ~60–100 ms, never animates), the icon's scale ramps
  **1.00 → 1.18** and its tint lerps from its current value (`DaybookColors.TextMuted` or the
  accent, per selection) to **`LocalAccent.current`**, using
  `animateFloatAsState(target, tween(durationMillis = rampMillis, easing = LinearEasing))` where
  `rampMillis = LocalViewConfiguration.current.longPressTimeoutMillis.toInt() - 120` (= **380 ms**
  at the platform default). **Linear easing, deliberately** — the ramp is a progress indicator,
  not a flourish, and it must reach its end state at the instant the gesture triggers so the
  growth reads as "filling up to something". Releasing early springs back to 1.00 via the
  existing `Motion.pressSpring()`, which is the same spring `CircleIconButton` already uses for
  its press scale (`Components.kt:129`) — no new motion token.
- **At the trigger — one haptic tick.** `LocalHapticFeedback.current.performHapticFeedback(
  HapticFeedbackType.LongPress)`, fired as the **first** statement inside `onLongClick`, before
  the `navigate` call, so the tick lands while the finger is still down. This is the standard
  Android long-press confirmation; it needs **no permission** and no `VIBRATE` in the manifest
  (`HapticFeedbackType` routes through the view's `performHapticFeedback`, which is
  permission-free), so **C7 and §3.10 P7's "nothing new in the manifest" both hold**.
- **Reduce-motion (`LocalReduceMotion.current == true`): the ramp is dropped, the signal is
  not.** The scale animation does not run at all (no growth, no spring-back). Instead the tint
  **snaps** to `LocalAccent.current` once the 120 ms dead zone passes and snaps back on release.
  The haptic still fires — **a haptic is not motion**, and removing the only remaining feedback
  channel from the users most likely to need it would be exactly backwards. This matches the
  discipline every other surface in the app follows (`UndoSnack`, `SegmentedControl`,
  `WeekStrip` all branch on `LocalReduceMotion`, they do not become inert).
- **Nothing else.** No radial/arc fill (a new drawn shape is new visual vocabulary, C5), no
  scrim, no bottom-sheet peek, no sound. The icon growing under the thumb plus a tick is the
  whole vocabulary.

**Testable part.** Extract the dead zone and ramp arithmetic as a pure function in
`ui/components/Navigation.kt`:
`internal fun longPressRampMillis(longPressTimeoutMillis: Long, deadZoneMillis: Int = 120): Int`
returning `(longPressTimeoutMillis - deadZoneMillis).coerceAtLeast(1).toInt()`, with a
`NavLongPressRampTest` covering the 500 ms default, a 1500 ms accessibility setting, and a
pathological sub-dead-zone value. Small, but it is the repo's house style (§10) and it stops the
`- 120` from being silently negative on a weird OEM.

#### 3.6.3 Discoverability — a hidden gesture that nobody is told about does not exist

This is the honest weakness of the decision, and it is answered in **two** places, both of which
retire themselves.

**(a) A one-time coach-mark, the first time the user opens Daybook after this update.**

There is **no coach-mark component in the app** (§0, verified). Rather than pull in a library or
invent a new visual language, build a minimal one, `ui/components/CoachMark.kt`, out of
`UndoSnack.kt`'s existing recipe — **the same surface, border, type ramp and reduce-motion
branch**, differing only in that it persists until dismissed and carries an action:

```kotlin
@Composable
fun BoxScope.NavCoachMark(
    text: String,
    actionLabel: String,          // "Got it"
    bottomClearance: Dp,          // DaybookScaffold's navClearance, so it floats just above the pill
    onDismiss: () -> Unit
)
```

- **Surface:** `AppShapes.card` (not `pill` — this is two lines of text plus an action),
  `DaybookColors.SurfaceElevated`, 1 dp `DaybookColors.Hairline` border, the same elevation
  `UndoSnack` uses. Text `DaybookText.CardSubtitle` / `DaybookColors.TextPrimary`, `maxLines = 2`.
- **Placement:** `Modifier.align(Alignment.BottomStart)`, `padding(start = Spacing.screenH,
  bottom = bottomClearance + 12.dp)`, `widthIn(max = 280.dp)`. Left-aligned over the **Today**
  slot — Today is always the leftmost item (`NavConfig`'s invariant), so left-alignment points at
  it without drawing anything. **No pointer triangle**: a drawn tail would be a new shape outside
  `AppShapes` (C5), and proximity plus the dot in (b) is unambiguous with three items.
- **Exact copy — this is final, not a placeholder:**
  - body: **`Hold "Today" to start a workout.`**
  - action: **`Got it`** (a `GhostButton`, right-aligned under the text)
- **Reduce-motion:** appears and disappears with no fade/slide when `LocalReduceMotion` is true;
  otherwise the same fade `UndoSnack` uses.
- **Accessibility:** the whole card is one focusable node with
  `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` so TalkBack announces it when it
  appears, and `Got it` is a real button, not a tap-anywhere dismissal.

**When it shows, exactly:** on the `main` destination only, when `workout_hint_state == 0`,
after a **600 ms** delay from first composition (so it does not collide with the launch
transition or an onboarding hand-off). It is dismissed — and `workout_hint_state` advances — by
**any** of: tapping `Got it` (→ 1), performing the long-press (→ 2), or navigating away from
`main` (→ 1). It therefore appears **once, ever**, and cannot reappear after a process death
because the flag is in Room, not in `remember`.

**(b) A persistent dot on the Today icon, until the gesture is used once.**

A 4 dp filled circle in `LocalAccent.current`, drawn at the Today icon's top-end corner
(`Modifier.offset(x = 6.dp, y = (-2).dp)` on a `Box` wrapping the `Icon`), rendered **only while
`workout_hint_state < 2`**. It is passed in as `hintDotRoutes = setOf("home")` (§3.6.1 b) rather
than hard-coded in `FloatingPillNav`, so the nav component stays generic.

- **It survives "Got it"** (state 1) and dies only on the first successful long-press (state 2).
  That split is the point: dismissing a tip is not the same as having learned the gesture, and
  the dot is the cheap reminder that survives in between.
- **It is not a badge and must never become one.** No count, no red, no "new" pill — a red
  unread badge on a nav item that can never be cleared by using the app normally is the thing
  users actually hate. Accent-coloured, 4 dp, and permanently gone after one use.
- `contentDescription` on the Today icon stays `null` (the existing comment at
  `Navigation.kt:116` explains why — the label `Text` below already carries the name and a
  description double-reads). The gesture reaches TalkBack through `onLongClickLabel` (§3.6.1),
  which is the correct channel; the dot is decorative.

**(c) And the fallback entry points of §3.6.4**, which are the real answer for anyone who never
sees, or cannot perform, the gesture.

#### 3.6.4 Visible entry points *(RECOMMENDED — §9 Q32 is the user's call)*

A long-press is a **shortcut**, and a shortcut should not be the only door. Two visible entry
points are planned:

1. **Settings → Workout → `Open Workout`** *(unconditional — ships regardless of Q32)*. A plain
   navigation row with the `ic_workout` glyph, calling the same `goWorkout()`. One row, zero new
   concepts, and it means the feature can **always** be reached by a user who has forgotten the
   gesture, cannot perform it, or is driving TalkBack. **This row is not optional and is not
   behind any toggle** — it is the guaranteed floor.
   *(REV5: it is now the **only** Workout row in Daybook's main Settings, because every actual
   Workout **setting** moved into Beast Mode's own settings screen (§3.8, §3.8.2). That makes
   this row strictly more load-bearing, not less: it is both the accessibility floor **and** the
   only pointer telling someone hunting for "weight unit" in Settings where it went. §3.8 gives
   it a subtitle that says so.)*
2. **A Beast Mode row on the Today screen** *(recommended; contingent on §9 Q32)*. A single
   `SoftCard` row in Today's `LazyColumn` — leading `CardTints` tinted circle + `ic_workout`,
   title, subtitle, tap → `goWorkout()`:
   - **No active session:** *(REV5 — relabelled)* title **`Beast Mode`**, subtitle
     **`Routines, workouts and history`**. Revision 4's `Start workout` / `Log sets, reps and
     weights` is **withdrawn**, because it is now wrong: tapping the row lands on Beast Mode's
     **routines landing page** (§3.7.4), it does not start a workout. A row that says "Start
     workout" and then doesn't is a small lie the user pays for every time.
   - **A session is ACTIVE:** title **`Workout in progress`**, subtitle **`Tap to carry on`**,
     tinted with the Beast Mode accent. This makes it double as the resume affordance outside
     Beast Mode (§3.6.5), from the **same** `observeActiveSession()` Flow the in-mode resume
     banner already uses (§3.1.1) — one query, two surfaces, no second source of truth.
   - **Placement:** appended **after** the grouped reminder items and **before** Round B's Health
     card and the bottom clearance spacer. Same reasoning §7.4 gives for the Health card — Today
     is *what you still have to do*, so nothing new may push a pending reminder down the screen —
     but it sits **above** Health because starting a workout is an action you can still take
     today, whereas health data is a read-out of what already happened.
     **`GroupHomeItemsTest`'s grouping is not modified** (C5/R7): the row is appended after the
     grouped items, not inserted into them.
   - **Hideable** via `app_settings.workout_today_card_enabled` (§3.5), surfaced as
     **`Show Beast Mode on Today`** *(REV5: relabelled to match the row it controls)* **on Beast
     Mode's own settings screen** *(REV5: moved out of main Settings — §3.8.2)*. Turning it off
     can never strand the feature, for two independent reasons: entry point 1 is unconditional,
     and the long-press still works. And the switch is not unreachable-once-off either — you
     turn it back on from inside Beast Mode, which you can always get into.

#### 3.6.5 Getting out — the exit path, stated exhaustively *(REWRITTEN IN REVISION 5)*

> **What changed.** Revision 4's `×` in the landing header is **withdrawn** — D3 gives that
> corner to Beast Mode's settings gear (§3.7.4), and D5 gives the exit to a press-and-hold on the
> leftmost nav item (§3.6.8). System back still leaves the mode from the landing screen, exactly
> as before, so there is **no route into Beast Mode that cannot be reversed without a gesture**.
> §9 **Q35** asks whether the `×` should come back anyway.

| From | Gesture | Result |
|---|---|---|
| Beast Mode landing (`"workout"`) | **Press-and-hold the leftmost nav item** *(REV5, §3.6.8)* | Leaves Beast Mode and lands on **Today**. The mirror of the way in. |
| Beast Mode landing | System back button / back gesture | Leaves Beast Mode via `NavHost`'s default pop → back on `"main"`, Daybook's own nav returns, **and the pager is on whatever page the user left from**, because `pagerState` is `remember`ed in `MainApp`'s scope, not inside `composable("main")`. **Do not add a `BackHandler` to the workout landing** — there is nothing to intercept, and a redundant handler is how "back does nothing" bugs start. |
| Beast Mode landing | *(withdrawn)* ~~the header's leading `×`~~ | **Gone in REV5.** The header's trailing slot is the settings gear (§3.7.4); there is no leading control. |
| `"workout_history"` / `"workout_library"` | Tap the leftmost nav item | Back to the landing. No-op if already there (`launchSingleTop`). |
| `"workout_history"` / `"workout_library"` | System back | Back to the **landing**, not out of the mode — because §3.6.7's `popUpTo("workout")` keeps the landing under them. **This is the point of the flat three-destination graph**: you cannot fall out of Beast Mode by backing out of a tab inside it. |
| `"workout_history"` / `"workout_library"` | **Press-and-hold the leftmost nav item** | Leaves Beast Mode from anywhere in the nav, landing on Today. It does **not** first navigate to the landing. (This mirrors §3.6.1's rule 3 exactly: holding Today from Habits or Intake enters Beast Mode directly.) |
| `"workout_settings"` | Back, or the header's back arrow | Pops to the screen it was opened from — the landing. **It is not a nav destination** (§3.6.7), so it renders full-screen with no pill nav, like every other stacked route. |
| `"workout_settings"` | The **`Leave Beast Mode`** row at the bottom of the screen *(REV5, §3.8.2)* | Leaves Beast Mode and lands on Today — the same `exitBeastMode()` the hold calls. **This row is the accessibility floor for the exit**, mirroring exactly what `Open Workout` is for the entrance (R21 / R26). |
| Live session (`workout_session/{id}`) | Back, or the header chevron | Pops to the Beast Mode landing. **No confirmation dialog.** |
| Live session, then the landing | Back twice | Leaves the mode entirely with the session still `ACTIVE`. **Allowed, and no prompt.** |
| Any deeper route (`add_exercise`, `new_exercise`, `edit_exercise`, `workout_detail`, `routine_edit`) | Back | Pops one level, as every other stacked route in the app already does. |

**One deliberate asymmetry, stated so it is not filed as a bug.** The **hold** lands you on
**Today**; **system back** lands you on **the page you came from**. That is intentional: the hold
is the mirror of "hold Today to enter", so it returns you to Today, while back means *"undo the
navigation I just did"*, which returns you where you were. Both leave Beast Mode; they differ
only in which of the three tabs you arrive on.

**Why there is no "you have unsaved sets" prompt, and why this is not hand-waving.**
§3.10 **P1** already requires that *every completed set is written to Room immediately, in its
own transaction* — the live session is explicitly forbidden from holding an in-memory
`List<WorkoutSet>` (P1's corollary). **So "unsaved sets" is not a state this design can be in.**
A confirm-on-exit dialog would be a dialog about a risk that has been designed out, and its only
lasting effect would be to train the user to dismiss dialogs. Discarding a session stays what
§3.7.1 already made it: an explicit **`Discard Workout`** button behind the existing
`ConfirmDeleteDialog`.

**What an interrupted session looks like, reconciled with the existing resume design.**
Revision 2 answered "the user wandered off mid-session" with the **resume banner** on the Workout
surface (§3.1.1), and that banner is unchanged. What is new in revision 4 is that the user can
now leave the *whole mode*, so the banner alone would be invisible from the normal chrome. Hence
§3.6.4's Today row flipping to **`Workout in progress` / `Tap to carry on`** — the same
`observeActiveSession()` Flow, rendered where the user actually is. **If §9 Q32 is answered
"long-press only", this reconciliation is lost** and an interrupted session becomes visible only
after re-entering Beast Mode; that is a concrete cost of the long-press-only answer and Q32 says
so.

**Re-entering while a session is ACTIVE — one predictable destination.** A long-press (or either
visible entry point) always lands on the **Beast Mode root**, never jumping straight into the
live session. The root shows the resume banner, so carrying on is one tap. Deep-jumping into a
session the user may have forgotten about, from a gesture, is disorienting; one destination is
worth one extra tap.

#### 3.6.6 The route graph

All six are plain `composable(...)` destinations in the **existing** `NavHost`, siblings of
`"main"` — **not** children of the pager and not a nested `navigation(...)` graph (a nested graph
would buy scoped ViewModels this round does not need, at the cost of a second start-destination
concept). Because none of them is `"main"`, `showNav = onMain` renders every one of them
full-screen with no bottom nav, automatically (§0).

| Route | Screen | Reached from |
|---|---|---|
| `"workout"` **(REV4, new — the mode root)** | `WorkoutScreen` (§3.7) | `goWorkout()`: the Today long-press, Settings → Open Workout, the Today row |
| `"workout_session/{sessionId}"` | `WorkoutSessionScreen` (§3.7.1) | the root's FAB / resume banner |
| `"workout_detail/{sessionId}"` | `WorkoutDetailScreen` | a past-session card on the root |
| `"add_exercise?session={sessionId}"` | `AddExerciseScreen` (§3.7.3) — the session arg stays **optional** *(REV2)* so it is also a plain catalog browser | the live session's `+ Add Exercise`; the root's overflow |
| `"new_exercise"` | `ExerciseFormScreen` | Add-Exercise's header `New` action |
| `"edit_exercise/{exerciseId}"` | `ExerciseFormScreen` | an exercise row's overflow |

The five stacked routes are **unchanged from revision 3** — only their parentage changed. The
callbacks are the same set, `remember(navController)`-wrapped like every existing one, with
`goStartWorkout` replaced by `goWorkout`:
`goWorkout`, `goWorkoutSession(id)`, `goWorkoutDetail(id)`, `goAddExercise(sessionId?)`,
`goNewExercise`, `goEditExercise(id)`.

### 3.7 UI (new package `ui/workout/`)

All screens built from `DaybookScaffold`'s `contentPadding`, `ScreenHeader`, `SoftCard`,
`SectionHeader`, `PrimaryButton`, `GhostButton`, `CircleIconButton`, `EmptyState`,
`SegmentedControl`, `SortSheet`, `ConfirmDeleteDialog`, `UndoSnack`, `StickySaveBar`.
Colours from `DaybookColors` / `CardTints` / `LocalAccent` only. Shapes from `AppShapes`.
Motion gated on `LocalReduceMotion`. **No new visual vocabulary.**

| File | Route | What it is |
|---|---|---|
| `WorkoutScreen.kt` | **stacked route `"workout"` — the Beast Mode root** *(REV4: was a pager page)* | **Header *(REV4, changed)*: a `BackHeader`-shaped header whose leading control is a `CircleIconButton` **`×`** (`contentDescription = "Close workout mode"`) — the way out (§3.6.5) — with the title `Workout` and the session count as its subtitle. **No `Avatar` action** (that is the tab-screen convention; this is not a tab) and **no bottom nav** (it is a stacked route, §3.6.6). The whole subtree is wrapped in the Beast Mode accent (§3.8.1). A resume banner `SoftCard` when a session is ACTIVE. A `LazyColumn` of past sessions, one `SoftCard` per session: date, title, "5 exercises · 18 sets · 4,200 kg", tap → detail, long-press/overflow → edit/delete with `UndoSnack`. `EmptyState` when there is nothing — **with an "Import from Hevy" `GhostButton` in it** *(REV2, §3.9.1)*. FAB ("Start workout") in the same `Box`-aligned position `RoutinesScreen` uses — **and with no pill nav underneath it, the FAB's bottom clearance is the plain navigation-bar inset, not `navClearance`**; `DaybookScaffold` already computes this correctly when `showNav` is false, so consume `contentPadding` and do not hand-compute. |
| `WorkoutViewModel.kt` | — | `@HiltViewModel`. Flows `flowOn(Default)` + `stateIn(WhileSubscribed(5_000))`, matching every other VM here. |
| `WorkoutSessionScreen.kt` | `workout_session/{sessionId}` | **The live log — the richest screen in Round A, rewritten in REV2. See §3.7.1.** |
| `WorkoutSessionViewModel.kt` | — | Owns the elapsed-time ticker and the rest-timer ticker (§3.7.2), the per-block `PREVIOUS` map and pre-session bests (§3.4), and `sessionStats`. |
| `AddExerciseScreen.kt` *(REV2: a full screen, was `ExercisePickerSheet`)* | `add_exercise?session={id}` | **See §3.7.3.** |
| `MuscleGroupSheet.kt` / `EquipmentSheet.kt` *(REV2)* | (sheets) | The two filter sheets. `ui/components/Sheets.kt` drag handle + title + a `LazyColumn` of rows, each `CircleIconButton`-style leading icon + label, trailing check on the selected one. First row is the "All …" clear-filter option. **REV3 — decided, was "implementer's call": ONE file, `ui/workout/FilterSheet.kt`, holding one composable** `@Composable fun FilterSheet(title: String, options: List<FilterOption>, selectedId: String?, allLabel: String, onSelect: (String?) -> Unit, onDismiss: () -> Unit)` with `data class FilterOption(val id: String, val label: String, val iconKey: String)`. The muscle and equipment sheets are the same list with different data, so two files would be two places to fix one bug. Call sites pass `title = "Muscle group"` / `allLabel = "All muscles"` and `title = "Equipment"` / `allLabel = "All equipment"` — **exact strings, sentence case, matching the app's existing sheet titles.** |
| `ExerciseFormScreen.kt` | `new_exercise`, `edit_exercise/{id}` | Name, **primary muscle** picker, **equipment** picker, tracking mode. *(REV2: two pickers where revision 1 had one `category`.)* Mirrors `AddHabitScreen`/`HabitForm`'s structure and `StickySaveBar`. Reached from the Add-Exercise screen's header action, matching Hevy's `Create`. |
| `ExerciseHistorySheet.kt` *(REV2)* | (sheet) | What the trend button opens (§3.1.2 R3): reverse-chronological list of every past session containing this exercise, its sets, and a medal on the best one. A list, **not a chart**. |
| `WorkoutDetailScreen.kt` | `workout_detail/{sessionId}` | Read view of a finished session with an "Edit" affordance that reopens `workout_session/{id}`. Shows the Duration / Volume / Sets summary and per-block notes. |

#### 3.7.1 `WorkoutSessionScreen` — the live log *(REVISION 2)*

Built entirely from existing components; the list below is a mapping from each thing in
screens 4–5 to the Daybook component that renders it, so nothing gets invented twice.

| Hevy element (screens 4–5) | Daybook rendering |
|---|---|
| Header: chevron, "Log Workout" → elapsed time on scroll, alarm icon, blue **Finish** | Existing `BackHeader` with a **title that swaps to the elapsed time once the list is scrolled past the stats row** (a `derivedStateOf` on the `LazyListState` — no new component). `CircleIconButton` for the rest-timer-defaults sheet in place of the alarm icon. `PrimaryButton` "Finish" as the header action. |
| Thin progress bar under the header | **Dropped.** It encodes nothing Daybook tracks (there is no target to progress toward without templates, R2). |
| Stats row: `Duration` / `Volume` / `Sets` | One `SoftCard` with three label-over-value columns, `DaybookText.Caption` label + `DaybookText` value, Duration in `LocalAccent.current` because it is live. Figures from `sessionStats` (§3.4). |
| Two muscle-shaded body silhouettes | **Dropped** — licensed art (§3.1.2). |
| Per-exercise card: thumbnail, accent name, `⋮` | `SoftCard` + a `CardTints` tinted circle with the exercise's icon + the name in `LocalAccent.current` + the existing overflow affordance (remove exercise, reorder, open history). |
| `Add notes here…` | A borderless `Forms.kt` text field bound to `WorkoutExercise.notes`. |
| `Rest Timer: OFF` row | A tappable row opening a duration sheet (Off / 30s / 60s / 90s / 2m / 3m / 5m / custom), writing `WorkoutExercise.restSeconds`, pre-filled from `lastRestSecondsForExercise` then `app_settings.rest_timer_default_seconds` (§3.4). |
| The set table, **columns varying by exercise** | A header `Row` + one `Row` per set, built from `trackingMode`: `WEIGHT_REPS` → SET · PREVIOUS · KG* · REPS · ✓; `REPS_ONLY` → SET · PREVIOUS · REPS · ✓; `DURATION` → SET · PREVIOUS · TIME · ✓; `DISTANCE_DURATION` → SET · PREVIOUS · DISTANCE · TIME · ✓. **A pure `columnsFor(trackingMode)` function with a unit test per mode** — this is exactly the kind of decision this repo extracts and tests. *(\* "KG" reads "LB" when `app_settings.weight_unit = LB`; storage stays kg, §3.8.)* |
| `PREVIOUS` = "30kg x 15", `–` when absent | From `previousBySetNumber` (§3.4), formatted by the shared `util/` formatter §7.1 already asks for. `DaybookColors.TextMuted`. |
| Completed row solid green + filled check | **Not a new green.** `DaybookColors.Success` at the existing container alpha, via `CardTints` — the same treatment a completed habit row already gets. Honours `LocalIsDark`. |
| Gold medal replacing the set number on a PR | A small existing-vocabulary icon tinted `DaybookColors.Warning` (the app's amber), **not** a new gold literal (C5). `contentDescription = "Personal record"`. |
| `+ Add Set` per exercise | `GhostButton`. Pre-fills from that block's previous set — the standard gym-app affordance. |
| `+ Add Exercise` / `Settings` / `Discard Workout` | `PrimaryButton` / `GhostButton` / `GhostButton` in `DaybookColors.Danger` with the existing `ConfirmDeleteDialog` in front of it. |

Field-input detail worth stating so it does not get invented twice: weight and reps use
plain numeric `TextField`s with the app's existing `Forms.kt` styling, **not** a new picker
component. `TimePickerComponents.kt` is for times only. The whole page is `imePadding()`-aware
— **the build-22 IME-overlap fix is the precedent and must not be regressed**, and a set table
with a focused field two-thirds down the page is the single most likely place to regress it.

#### 3.7.2 The two tickers, and why they are C7-safe *(REVISION 2)*

Two things count on this screen: the **session elapsed time** and the **rest timer**. Both use
the identical shape, and it is the shape C7 permits:

- The source of truth is a **timestamp**, never a counter: `startedAt` for the session,
  `restEndsAt` for the rest timer. Elapsed/remaining is always recomputed from
  `System.currentTimeMillis()`, so nothing drifts and nothing has to survive anything.
- Ticking is a single `LaunchedEffect(isRunning) { while (true) { emit(); delay(1000) } }` in
  the ViewModel, collected with `collectAsStateWithLifecycle`. It stops when the screen leaves
  composition **and** when the app is backgrounded, both for free.
- **No `WorkManager`, no `AlarmManager`, no `setExactAndAllowWhileIdle`, no foreground service,
  no `WakeLock`, no notification channel, no `POST_NOTIFICATIONS` usage, no sound, no
  vibration.** Nothing new appears in the manifest for either timer.
- Coming back to the screen mid-rest shows the correct remaining time (or "Rest over"),
  because the end-time is stored, not the countdown.

A build that needs any of the forbidden list has misread §3.1.2 R1 and must stop and re-ask.

#### 3.7.3 `AddExerciseScreen` *(REVISION 2 — replaces `ExercisePickerSheet`)*

A **full stacked route**, not a sheet — the screenshots show a full page, and it needs room for
a search field, two filter buttons, section headers and a long list. It is also reachable
outside a session (from the Beast Mode root's overflow) to manage the catalog, which a sheet
bolted to the session screen could not do. *(REV4: "or from Settings" is withdrawn — Settings
gets one `Open Workout` row (§3.8) and does not deep-link into individual workout screens.)*

- `BackHeader` with a **"New" action** in the trailing slot (Hevy's `Create`) → `new_exercise`.
- A search `TextField` (`Forms.kt`), matching on the normalised name (§3.9.4's normaliser —
  one implementation, used by both search and import).
- **Two half-width `GhostButton`s in a `Row`**: *All equipment* / *All muscles*, each showing
  the active filter's label when one is set, each opening its sheet. The pair replaces revision
  1's single `SegmentedControl` of categories — a `SegmentedControl` cannot hold 20 muscle
  groups.
- `SectionHeader("Recent")` over the exercises used in the last ~10 sessions (a
  `DISTINCT exercise_id ORDER BY started_at DESC LIMIT 12` query), then
  `SectionHeader("All exercises")` over the merged `builtins + customs`, alphabetical.
- Each row: tinted-circle icon (§3.1.2 — **no illustration**), name, **primary-muscle
  subtitle**, and a trailing `CircleIconButton` trend affordance opening
  `ExerciseHistorySheet`. Tapping the row itself adds the exercise to the session.
- An imported exercise (`Exercise.source = "IMPORTED_HEVY"`) carries a small "Imported" label
  in the subtitle line, so the user can see which rows the matcher had to invent (§3.9.4).
- `EmptyState` when a filter combination matches nothing, offering "Create \"<search text>\"".

### 3.8 Settings additions *(REVISED IN REVISION 4)*

> **What revision 4 removed.** Revision 3 also put a **"Show Workout tab"** toggle on
> `ui/settings/NavigationSettingsScreen.kt`. **That row is gone and that file is not touched
> this round** — Workout is not a tab, so its visibility is not a nav setting. `labelFor` gains
> nothing; the landing-tab picker gains nothing; `NavConfigTest` gains nothing.

`ui/settings/` gains **one new group with four or five rows** *(REV4: was three rows)*.
**Placement (decided in REV3, unchanged):** one new `FormGroup` headed by
`SectionHeader("Workout", subtitle = "Open Beast Mode, and its units, colour and rest timer.")`
*(REV4: subtitle reworded — it no longer says "the Workout tab")*, placed in `SettingsScreen.kt`
**immediately after the existing Appearance group and before "Backup & data"**, built from
`SettingsComponents.kt`'s existing row composables. A new group rather than strays inside
Appearance, because "weight unit" and "default rest timer" are not appearance settings and
burying them there makes them unfindable.

- **`Open Workout`** *(REV4, new — and this row is not optional)*. A plain navigation row,
  `ic_workout` glyph, calling the same `goWorkout()` the long-press calls (§3.6.1 c). It is the
  **guaranteed, always-visible, always-reachable** way into Beast Mode for anyone who does not
  know the gesture, cannot perform it, or is driving TalkBack — see §3.6.4 and §9 **Q32**.
  It is behind **no** toggle, ever.
- **Weight unit** — `SegmentedControl` kg / lb. Backed by `app_settings.weight_unit`.
  **Storage is always kg**; lb is a render-time conversion (`kg * 2.2046226f`, rounded to
  0.5 lb for display). A pure `WeightFormatTest` guards the round trip.
- **Beast Mode accent** *(REV4: renamed from "Workout accent")* — see §3.8.1. Backed by
  `app_settings.workout_accent_color`, **default `CORAL`** *(REV4: was `LAVENDER`)*. §9 Q11.
- **Default rest timer** *(REV2)* — Off / 30s / 60s / 90s / 2m / 3m / 5m. Backed by
  `app_settings.rest_timer_default_seconds`. Only the *starting* value for a new exercise
  block; a block that has been used before wins (§3.4 `lastRestSecondsForExercise`). The same
  control is what the session header's rest-timer icon opens (§3.7.1).
- **`Show workout on Today`** *(REV4, new — **contingent on §9 Q32**)*. A switch backed by
  `app_settings.workout_today_card_enabled`, controlling the Today row of §3.6.4. Safe to turn
  off precisely because `Open Workout` above is unconditional. **If Q32 is answered
  "long-press only", this row and its column are both dropped from the round** rather than
  shipped inert.

All of them are **device-local**: not in `BackupModel`, not in `ContentHash`, not synced — the
standing treatment for every `app_settings` column since v16. (`workout_hint_state`, §3.5, has
no Settings row at all: a "reset the workout tip" control is clutter for a one-time hint.)

#### 3.8.1 Beast Mode's visual identity, restated for a full-screen mode *(REVISION 4)*

Revision 3 described the workout accent as *"a fourth accent axis alongside the existing App /
Habits / Intake"*, on a **consistency** argument — i.e. it existed because the other tabs had
one. **That framing assumed tab-bar coexistence and is now wrong in a way that matters.**

- **What changed.** Habits' and Intake's accents tint surfaces that sit *inside shared chrome* —
  the same screen frame, the same pill nav, often two of them visible in one glance on Today. A
  fourth accent in that set had to **harmonise** with the other three or the nav pill and the
  Today cards would look noisy. **Beast Mode has no shared chrome at all**: no pill nav, its own
  header, its own full-screen surface, entered deliberately and left deliberately. Nothing of the
  normal app is on screen beside it.
- **So the accent gets to be louder, and that is the point of the mode.** The recommended
  default moves from `LAVENDER` (the app default — i.e. "no identity of its own") to **`CORAL`**,
  the highest-intensity value in the existing five-colour palette
  (`Accent.kt:21` — `#FB7185` dark / `#E23D5B` light). §9 Q11 confirms this with the user.
- **The constraint is unchanged and absolute (C5): no new colour literal.** Beast Mode picks one
  of the five existing `AccentColor` values. There is no "gym red", no gradient, no new
  `DaybookColors` entry, no second type ramp, no new shape. **The mode is visually distinct
  because the accent is louder and the chrome is different, not because it has its own design
  system.**
- **Mechanism — one line, no new plumbing.** `LocalAccent` is a `staticCompositionLocalOf`
  provided once by `DaybookTheme` (`Theme.kt:153`), so each workout route wraps its content in
  `CompositionLocalProvider(LocalAccent provides beastAccent.colorFor(LocalIsDark.current),
  LocalOnAccent provides onAccentInk(beastAccent.colorFor(LocalIsDark.current))) { … }`.
  **`LocalOnAccent` must be re-provided alongside it** — the UX-refinement round made on-accent
  ink per-accent and computed (`Accent.kt:78` and the LD13 comment above it, which records that
  Coral measures 4.16:1 against white); providing the accent without its ink is how the Finish
  button ends up with unreadable text in light mode. Every component below then picks the Beast
  accent up **for free**, because they all already read `LocalAccent.current` (§3.7.1's live
  Duration figure, the exercise names, `+ Add Exercise`, the PR row).
- **`LocalIsDark`, `LocalDaybookShapes` (corner scale) and `LocalReduceMotion` are *not*
  overridden.** Beast Mode is a louder accent inside the user's chosen theme, not a theme of its
  own — a mode that ignored the user's dark-mode or corner-roundness choice would be a visual
  regression under C5, not an identity.

`ui/settings/SettingsScreen.kt`'s **"Backup & data"** sub-screen also gains the Hevy import
row — §3.9.1.

### 3.9 Hevy CSV import *(REVISION 2 — new section)*

The user has workout history in **Hevy** and wants it in Daybook. Hevy exports a CSV, one row
per set. `/home/abhiram/Downloads/workout_data.csv` (34 lines: a header + 33 sets across
**4 sessions**) is the real sample this section was written against.

#### 3.9.0 The file, as actually read

```
"title","start_time","end_time","description","exercise_title","superset_id",
"exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
```

Facts confirmed by reading the sample, not assumed:

- **One row per set.** A session is the set of rows sharing `title` + `start_time` +
  `end_time`; an exercise block is a run of rows within it sharing `exercise_title`.
- **`start_time` / `end_time` are `"8 Sep 2026, 07:29"`** — a **local** wall-clock time, an
  **English** month abbreviation, and **no timezone at all**. Both facts matter (§3.9.3).
- **`set_index` is 0-based** per exercise block. Daybook's `set_number` is 1-based, so `+1`.
- **Quoting is inconsistent and both forms appear on the same line**: text fields are quoted
  (`"Morning workout ☀️"`, `""` for empty), numeric fields are bare (`25`, `10`), and an empty
  numeric field is **nothing at all** between two commas (`,,`). A parser that assumes every
  field is quoted, or that an empty field is `""`, fails on this exact file.
- **Non-ASCII is real**: the title contains `☀️` (U+2600 U+FE0F). Encoding is not hypothetical.
- **Blank ≠ zero.** `weight_kg` is empty for Bicycle Crunch (bodyweight) and Plank; `reps` is
  empty for Plank (`duration_seconds` = 65) and for Cycling (`distance_km` = 2.4,
  `duration_seconds` = 300). Writing `0` where the file says nothing invents data and corrupts
  both the volume total and PR detection.
- **`superset_id` is empty throughout this sample**, but it is a real Hevy column and a real
  Hevy feature (§3.1.2 R4).
- **`set_type` is `"normal"` throughout this sample.** Hevy's real export vocabulary also
  includes `warmup`, `dropset` and `failure` (§3.9.3).
- **`rpe` and `description` and `exercise_notes` are empty throughout this sample** but are
  populated in real exports; all three map to columns Daybook already has.
- All four sessions share the title `"Morning workout ☀️"` — **the title is not unique and is
  useless as an identity key.** §3.9.5 depends on this.

#### 3.9.1 Entry point

**Primary: Settings → "Backup & data" → a new `SectionHeader("Import from another app")` with
one row, "Import from Hevy (CSV)".** This mirrors the existing JSON import exactly rather than
inventing a second import UX:

- the same confirm-first-then-pick order the JSON import already uses
  (`SettingsScreen.kt` ~line 964: a `DaybookAlertDialog` whose confirm label is "Choose file",
  which then launches the picker);
- the same `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`
  system picker, with MIME types
  `arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")` — `*/*` is
  necessary because Android file providers label CSVs inconsistently, and the existing JSON
  import already includes it for the same reason;
- the same `_importResult` / `_isImporting` `StateFlow` pair and the same fixed-height result
  slot, so the summary text lands where every other import/export message already lands;
- the same `MAX_IMPORT_BYTES = 10 MB` pre-read size guard (a multi-year Hevy export is a few
  hundred KB; the sample is 5 KB).

**One deliberate difference in the dialog copy.** The JSON import's dialog is `destructive =
true` and says *"Importing replaces all current data on this phone."* **The Hevy import
replaces nothing** — it is a merge, and a merge that skips duplicates (§3.9.5). Its dialog is
`destructive = false` and says so: *"This adds your Hevy workouts to Daybook. Nothing already
in Daybook is changed or removed, and workouts you've already imported are skipped."*
Reusing the destructive copy here would frighten the user out of a safe action.

**Secondary: the Beast Mode root's `EmptyState`** *(REV4: was "the Workout tab's")* gets an
"Import from Hevy" `GhostButton` (§3.7). An empty Workout screen is the exact moment this
feature is wanted, and the button is one navigation call to the Settings row above. §9 Q17.
*(REV4 note: that navigation now **leaves** Beast Mode for Settings. It should therefore
`navigate("settings_data")` directly rather than popping first — the user lands on Backup & data
with the Hevy row in view, and `Back` from there returns them to the workout screen they came
from, which is the correct place to be once the import has run.)*

#### 3.9.2 Parsing: hand-rolled, no new dependency

`app/build.gradle.kts` has **no CSV library** — checked, not assumed. Adding one (opencsv,
commons-csv, kotlin-csv) would mean:

- breaking this file's own discipline, where **every** third-party pin carries a written
  justification comment (`firebase-bom:33.1.2`, `credentials:1.3.0`, `biometric:1.1.0`,
  `security-crypto:1.1.0-alpha06` each say in-line why that exact version); "we needed to split
  strings on commas" is not that kind of justification;
- new R8 / ProGuard surface on a release build that is already at ~7.2 MB and already has a
  shrinking story to keep working (§5.1 flags Guava for the same reason);
- a compileSdk-34 compatibility question for a benefit measured in about eighty lines.

**Recommendation: a small hand-rolled RFC4180 reader, `data/workout/CsvReader.kt`.** It must
handle, because this exact file requires them: quoted and unquoted fields **on the same line**;
commas inside quotes; `""` as an escaped quote; a completely empty field between two commas;
`CRLF` and `LF`; a **leading UTF-8 BOM** (Hevy's export has been seen to carry one, and an
un-stripped BOM silently breaks the *first header name* only, which is a horrible bug to find);
and a trailing newline. It returns `List<Map<String, String>>` keyed by header name, so a
column Hevy adds or reorders later does not shift every field by one.

Encoding is already solved upstream: `StorageUtils.readText` decodes with an explicit
`Charsets.UTF_8` (`StorageUtils.kt:80`), so `☀️` arrives intact. The reader must not re-decode.

This is precisely the kind of pure, boring, high-consequence function this repo unit-tests, and
it gets a `CsvReaderTest` with a case per bullet above plus the real sample's first three lines
verbatim.

#### 3.9.3 Column mapping

| Hevy column | Daybook | Notes |
|---|---|---|
| `title` | `WorkoutSession.title` | Kept verbatim, emoji and all. **Never used as an identity key** (§3.9.0). |
| `start_time` | `WorkoutSession.startedAt`, and `localDate` | See the date note below. |
| `end_time` | `WorkoutSession.endedAt` | |
| `description` | `WorkoutSession.notes` | |
| `exercise_title` | → an `exerciseId` via §3.9.4 | The hard part. |
| `superset_id` | `WorkoutExercise.supersetId` | Preserved losslessly, unread by Round A's UI (§3.1.2 R4). |
| `exercise_notes` | `WorkoutExercise.notes` | This is **why** `workout_exercises` exists (§3.2). |
| `set_index` | `WorkoutSet.setNumber` | **`+ 1`** — Hevy is 0-based, Daybook is 1-based. |
| `set_type` | `WorkoutSet.setType` | `normal`→`NORMAL`, `warmup`→`WARMUP`, `dropset`→`DROPSET`, `failure`→`FAILURE`; **anything unrecognised → `NORMAL`**, never a crash. |
| `weight_kg` | `WorkoutSet.weightKg` | Already kg — Daybook stores kg (§3.2). Blank → `null`, **not** `0`. |
| `reps` | `WorkoutSet.reps` | Blank → `null`. |
| `distance_km` | `WorkoutSet.distanceMeters` | **× 1000.** Blank → `null`. |
| `duration_seconds` | `WorkoutSet.durationSeconds` | Blank → `null`. |
| `rpe` | `WorkoutSet.rpe` | Blank → `null`. |
| — | `WorkoutSet.completedAt` | Set to the session's `endedAt`. Every row in an export is a set that **happened**, so leaving `completedAt` null would make the whole import invisible to the PREVIOUS and PR queries (§3.4), which filter on `completed_at IS NOT NULL`. |
| — | `WorkoutSession.status` | `"COMPLETED"` — an exported session is finished by definition. |
| — | `WorkoutSession.source` | `"IMPORTED_HEVY"` (§3.9.6). |

**Why `set_type` gets a column and not a squeeze into `notes`.** `notes` is the user's own
prose and is shown to them as such. Writing `"dropset"` into it fabricates text the user never
typed, cannot be reliably parsed back out, and would collide with a real `exercise_notes`
value. §3.2 made `setType` a first-class column for exactly this reason, and it costs one TEXT
column with a default that `@EncodeDefault(NEVER)` keeps hash-neutral (§4.2).

**Dates — the sharpest small detail in the whole import.** `"8 Sep 2026, 07:29"` must be parsed
with a `DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)` — **`Locale.ENGLISH`
explicitly**, because Hevy writes English month abbreviations regardless of device locale and a
device set to, say, French would fail to parse every row on a phone where the feature was
never tested. It carries **no zone**, so it is interpreted in `ZoneId.systemDefault()` — the
same convention `ExportImportRepository.epochOf` already uses, and the reason `localDate` comes
out timezone-stable and buckets correctly in `MonthPartitioner`. The parser should **also try
ISO-8601 first**, since Hevy's format has varied across app versions, and fall back to the
`d MMM yyyy` pattern. A row whose date parses as neither is **skipped and counted**, never
guessed. `HevyDateParseTest` covers: the sample's exact format, ISO-8601, a French default
locale, and an unparseable string.

#### 3.9.4 Matching Hevy's exercise names to Daybook's catalog

Hevy's names do not match Daybook's: `"Squat (Barbell)"`, `"Seated Cable Row - V Grip (Cable)"`,
`"Bicep Curl (Dumbbell)"`, `"Single Arm Tricep Extension (Dumbbell)"`. **This is a heuristic. It
will not be perfect, and the design's job is to make its failures harmless and visible rather
than to pretend they won't happen.**

A pure `HevyExerciseMatcher` (no Room, fully unit-tested) runs four steps in order:

1. **Normalise.** Lowercase; strip the **trailing parenthetical** and keep it as an *equipment
   hint* (`"Squat (Barbell)"` → name `squat`, hint `BARBELL`); replace `-`, `–`, `/` and
   punctuation with spaces; collapse whitespace; drop a leading `the`. The **same normaliser
   powers the Add-Exercise search field** (§3.7.3) — one implementation, one test.
2. **Exact normalised match** against `builtins + existing customs`. Ties broken by preferring
   a candidate whose `equipment` equals the hint.
3. **Alias table** — `ExerciseCatalog.HEVY_ALIASES` (§3.3.4), a hand-written map covering the
   ~40 common Hevy names that step 2 cannot reach (`seated cable row v grip` →
   `builtin:seated_cable_row`).
4. **Fall back to creating a custom `Exercise`**, once per distinct name per import:
   `name` = Hevy's name **verbatim** (so the user recognises it), `equipment` = the hint or
   `OTHER`, `primaryMuscle` = `OTHER`, `source = "IMPORTED_HEVY"`, and `trackingMode`
   **inferred from which columns are populated across all that name's rows in the file**
   (weight+reps → `WEIGHT_REPS`; reps only → `REPS_ONLY`; duration only → `DURATION`;
   distance present → `DISTANCE_DURATION`). Inferring from the data is better than defaulting,
   because it is what makes the imported Plank render a TIME column and the imported Cycling
   render DISTANCE + TIME (§3.7.1).

**No fuzzy / edit-distance matching.** Deliberate. A Levenshtein threshold loose enough to
catch `"Bicep Curl (Dumbbell)"` → `"Dumbbell Curl"` is also loose enough to catch
`"Incline Bench Press"` → `"Decline Bench Press"` or `"Front Squat"` → `"Back Squat"`. A wrong
match is **worse than no match**: it silently merges two different movements' histories, which
corrupts the PREVIOUS column and invents or destroys personal records, and the user has no way
to see that it happened. An extra custom exercise is visible, harmless, renameable and
mergeable by hand. §9 Q18.

**Recovery is a normal edit, not a special tool.** An auto-created exercise is an ordinary
custom `Exercise` row: the user can rename it, set its muscle and equipment, or archive it from
`ExerciseFormScreen`. The "Imported" label in the picker (§3.7.3) is how they find them.

#### 3.9.5 Duplicate safety

Importing the same file twice, or two overlapping exports, **must not** double the user's
history. Nor may it collide with sessions they logged by hand in Daybook.

**The dedupe key is `(startedAt, endedAt)` as exact epoch millis.** Rationale, from the sample:

- `title` is useless — all four sessions are `"Morning workout ☀️"` (§3.9.0).
- Set composition is useless as a *key* — the user may legitimately have edited an imported
  session afterwards (§3.9.6 says they can), and a composition hash would then fail to
  recognise it and re-import a second copy.
- Start **and** end together are effectively unique for a real human: two workouts cannot begin
  and end at the same minute. Start alone is slightly weaker but nearly as good; requiring both
  is the conservative choice.
- **No fuzzy time window.** A ±60s tolerance would let a genuinely different session be eaten,
  and a *missed* duplicate is recoverable (the user deletes it) while a *wrongly skipped*
  session is silently absent forever.

The rule applies **against every existing `workout_sessions` row regardless of `source`**, so
a hand-logged session and its Hevy twin do not both survive. It also applies **within the file**
— group rows into sessions first, then dedupe the groups — so a doubled export is handled.

**On a detected duplicate: skip the whole session, count it, and say so.** Never merge, never
partially insert, never overwrite. Merging would mean reconciling set-level edits the user made
in Daybook against an older snapshot from Hevy, which is a conflict-resolution problem nobody
asked for. A `HevyDedupeTest` covers: re-importing the identical file yields 0 new sessions; a
file with one new session and three old ones yields 1; a hand-logged session at the same
timestamps blocks its Hevy twin.

The whole import runs inside **one `database.withTransaction { }`** — either all of it lands or
none of it does, matching `importAllData`'s existing posture.

#### 3.9.6 `source`, and whether imported rows are second-class

**`WorkoutSession.source = "IMPORTED_HEVY"`, not a generic `"IMPORTED"`.** If a second importer
ever lands (Strong, FitNotes, Jefit all export CSV), a generic marker cannot tell them apart,
which makes "undo my Hevy import" or "re-import Hevy now that matching improved" impossible to
implement without a schema change. The string costs nothing.

**Imported sessions are fully editable, first-class rows.** Once they land they are
indistinguishable from hand-logged sessions in every way that matters: they are editable,
deletable, counted in Volume and Sets, and they **participate in PREVIOUS and personal-record
detection** (which is most of the point — importing history you can't set a PR against is
half a feature). The `source` string is bookkeeping, not a permission.

**Why this is not inconsistent with §6.4's read-only Health-Connect sessions.** The two look
similar and are structurally opposite:

- A Health Connect session is a **live view of an authoritative store that Daybook does not
  own**. It is re-read on every pull. A local edit would be overwritten on the next sync; a
  local delete would resurrect. Read-only is the only honest state for it.
- A Hevy CSV is a **one-shot transfer of ownership**. Nothing re-reads the file. There is no
  authority left to disagree with. From the moment the rows land, the user is the only source
  of truth for them, so treating them as anything less than fully theirs would be arbitrary.

The dividing line is *"does something else keep updating this?"*, not *"did the user type it?"*.

#### 3.9.7 Sync interaction — the sharp edge

**This is the part of §3.9 most likely to lose cloud data if implemented naively, and it is not
obvious.**

An import writes sessions into **past months**. On a signed-in account, past months are
**partitioned, hash-tracked and frequently evicted from the local DB** (§4.4 item 6): the
device may hold no local rows at all for `2026-07`, while the cloud holds a full month doc of
habits and intake. If the import writes a July workout into that empty local month and the
normal push path then runs, `MonthPartitioner` sees a changed `2026-07` and pushes what the
device has — **a month doc containing the imported workout and nothing else**, overwriting the
cloud's habits and intake for July.

**Mitigation: hydrate before importing, using the path that already exists.** The date-range
export solves the identical problem today — `SettingsViewModel.exportRange` calls
`cloudSync.beginRangeExport()`, hydrates the missing months with a `(done, total)` progress
indicator, and **aborts the whole operation with a plain-language message if any month cannot
be reached**. The Hevy import must do exactly the same thing, in this order:

1. Parse and group the file **first** (a parse failure must cost no network and no writes).
2. Compute the set of `"yyyy-MM"` months the file touches.
3. `beginRangeExport()` → hydrate those months → on any failure, **abort with nothing written**
   and a message in the existing voice (*"Couldn't fetch <month> from your account. Check your
   connection and try again — nothing was imported."*).
4. Import inside one transaction.
5. `endRangeExport()` in a `finally`, exactly as `exportRange` does.

**Signed-out users skip steps 3 and 5 entirely** — there is nothing to hydrate — and the import
is purely local. This must be a branch, not an unconditional call.

A new test asserts that importing into a month with existing habit data leaves that habit data
intact, alongside the existing `MonthMergeTest` / `RangeImportNonDestructiveTest` guards.

#### 3.9.8 Migration: **none of its own — it reuses `MIGRATION_21_22`**

Stated explicitly so it cannot be left ambiguous. The importer writes into `exercises`,
`workout_sessions`, `workout_exercises` and `workout_sets` — **the same four tables Round A
already creates in `MIGRATION_21_22` (§3.5)**, using the same columns, including
`workout_exercises.superset_id`, `workout_sets.set_type` and `exercises.source`, all of which
§3.5 already provisions. There is **no `MIGRATION_22_23` for the import**, no extra table, no
extra column, and `AppDatabase.version` stays at 22 for the whole of Round A. C8 holds.

Nor does the import change the wire model beyond what §4.2 already adds — an imported session
serialises through the identical `WorkoutLog` as a hand-logged one.

#### 3.9.9 The summary the user sees

Same voice and same place as the existing import messages (`"Import successful: 4 habits,
2 intake, 31 days of history"`):

> **`Imported 4 workouts, 33 sets and 9 new exercises. Skipped 2 already in Daybook.`**

Rules: always report **workouts, sets, new exercises created, duplicates skipped** — the
"new exercises" figure is what tells the user the matcher had to invent rows and is worth
looking at (§3.9.4). Drop a clause when its count is zero rather than printing "skipped 0", so
the common clean case reads cleanly. Rows skipped for an unparseable date get their own clause
(*"…and ignored 3 rows it couldn't read."*). Failures use the existing
`"Import failed: <reason>"` shape and `DaybookColors.Danger`, and the success branch must be
recognised by `SettingsScreen`'s existing `ok` check (which today tests
`msg.startsWith("Import successful")` / `"Exported "` — **extend that check, or the success
message renders in red**; a small trap worth naming).

#### 3.9.10a Pre-parse validation, and every visible outcome *(REVISION 3, new — C9)*

§3.9.9 gave the happy-path summary string. This subsection specifies **what the user sees for
every other branch**, and what is checked **before** anything is attempted. It is written to be
implementable without a single further decision.

##### A. Can a partial failure even happen? — **No. The design is all-or-nothing per file.**

Answered first, because designing UI for an impossible state is worse than not designing it.

§3.9.5 already states: *"The whole import runs inside one `database.withTransaction { }` —
either all of it lands or none of it does."* §3.9.7 adds that hydration happens **before** the
transaction and **aborts with nothing written** if any month cannot be reached. So the only
possible outcomes are:

1. **Nothing was written** (validation failed, parse failed, hydration failed, or the
   transaction threw and rolled back), or
2. **Everything the importer decided to import was written.**

**There is therefore no "some sessions imported, some didn't" state, and no per-row failure
report screen is designed.** What *does* exist, and must not be confused with partial failure,
are two kinds of **deliberate, successful skipping** — both of which land inside a successful
import and are reported as counts in the success summary:

- **Duplicate sessions** (§3.9.5) — skipped on purpose, counted.
- **Rows with an unparseable date** (§3.9.3) — skipped on purpose, counted.

**One hard requirement follows from this, and it is the single most important rule in this
subsection:** if **every** session in the file is skipped, or the file parses to **zero**
sessions, that is **not** a silent success. See outcome **S3** below. A "success" message
reporting nothing imported, with no explanation, is precisely the silent failure C9 forbids.

##### B. Pre-parse validation — five gates, in this order, before any parsing or any write

Each gate costs nothing and each has one exact message. **Order matters**: the cheapest and most
specific check runs first so the user gets the most actionable message, not the most generic one.

| # | Gate | Check | If it fails → message ID |
|---|---|---|---|
| V1 | **Size** | file bytes > `MAX_IMPORT_BYTES` (10 MB, §3.9.1) | **F1** |
| V2 | **Non-empty** | after `StorageUtils.readText`, the trimmed text is empty, **or** the file has fewer than 2 non-blank lines (a header with no data rows) | **F2** |
| V3 | **Parses as CSV** | `CsvReader` throws, or the header row yields zero columns | **F3** |
| V4 | **Is a Hevy export** | the **required header set** is present (see below) | **F4** |
| V5 | **Has at least one usable row** | ≥ 1 data row survives parsing with a parseable `start_time` | **F5** |

**The required header set — exactly these ten**, compared **case-insensitively, after trimming
whitespace and stripping a leading UTF-8 BOM** (§3.9.2):

```
title, start_time, end_time, exercise_title, set_index, set_type, weight_kg, reps,
distance_km, duration_seconds
```

**Deliberately *not* required, though all are mapped when present:** `description`,
`superset_id`, `exercise_notes`, `rpe`. Reason: these four are the columns most likely to be
absent from an older or newer Hevy export, they are all optional in Daybook's schema, and
**rejecting a perfectly importable file over a missing `rpe` column would be a self-inflicted
failure.** Extra/unknown columns are **ignored, never an error** — `CsvReader` keys by header
name (§3.9.2) precisely so a column Hevy adds later cannot break anything.

This is a pure function and gets a unit test:
**`data/workout/HevyCsvValidator.kt`** →
`fun validate(headers: List<String>): ValidationResult`, where
`sealed interface ValidationResult { object Ok : ValidationResult; data class MissingColumns(val names: List<String>) : ValidationResult }`.
Test cases: the real sample's header (→ `Ok`); a header missing `reps` (→ `MissingColumns(["reps"])`);
a header with a BOM on `title`; an upper-case header; a Daybook JSON backup's first line (→ not CSV,
caught at V3); a header with extra unknown columns (→ `Ok`).

##### C. Where the message appears — **a screen state, never a toast**

**All import outcomes render in the existing fixed-height inline result slot** in
`ui/settings/SettingsScreen.kt` (~line 1044) — the same `Box(Modifier.fillMaxWidth().heightIn(min
= 36.dp))` + `DaybookText.Caption` `Text` that the JSON import already uses (§0). The user needs
to **read and act on** these messages, and `UndoSnack` disappears after 2.6 s, so the snack is
the wrong pattern here. This reuses the app's existing "rejected with a readable reason in a
screen state" precedent — the same slot that shows *"This backup was made by an older version of
Daybook and can't be restored."* today.

**One required code change, flagged in §3.9.9 and restated because it is a silent trap:**
the slot colours green only when
`msg.startsWith("Exported ") || msg.startsWith("Import successful")`. Every string below that is
a success **must** be made to satisfy that check. **Decision (REV3): extend the check rather
than contort the copy** —

```kotlin
val ok = msg.startsWith("Exported ") || msg.startsWith("Import successful") ||
         msg.startsWith("Imported ")      // ROUND A (A8): the Hevy import's success prefix.
```

**Every failure string below deliberately begins with `Couldn't `** so it can never
accidentally match the success prefixes.

##### D. The exact strings — every branch, final copy

**Failure (nothing was imported). Rendered in `DaybookColors.Danger`.**

| ID | When | **Exact text** |
|---|---|---|
| **F1** | file over 10 MB | `Couldn't import: that file is too large (the limit is 10 MB).` |
| **F2** | empty file / header only | `Couldn't import: that file is empty.` |
| **F3** | not parseable as CSV | `Couldn't import: that file isn't a CSV. Export your data from Hevy as CSV and try again.` |
| **F4** | required columns missing | `Couldn't import: that doesn't look like a Hevy export. It's missing: <names>.` — `<names>` is the missing column names joined with `", "`, **verbatim as Hevy spells them** (`start_time`, not "Start time"), because that is what the user will see in their file. |
| **F5** | no readable rows | `Couldn't import: none of the rows in that file could be read.` |
| **F6** | a touched month couldn't be fetched (§3.9.7) | `Couldn't fetch <month> from your account. Check your connection and try again — nothing was imported.` — `<month>` formatted as **`September 2026`** (`MMMM yyyy`, `Locale.getDefault()`; it is being read aloud to the user, not parsed). **This string is already specified in §3.9.7 and must match it exactly.** |
| **F7** | the file couldn't be read at all (`IOException`) | `Couldn't read the file. Try picking it again.` — **reuse `friendlyImportError`'s existing wording verbatim** (§0); do not write a second sentence for the same condition. |
| **F8** | the transaction threw (`SQLiteException`) | `Couldn't save the imported workouts — try again, or restart the app if it keeps happening. Nothing was imported.` — the first clause is `friendlyImportError`'s existing `SQLiteException` wording with "data" → "workouts"; the second sentence is added because, unlike a JSON restore, this import is a **merge** and the user's first question will be *"did it half-happen?"*. **Answer it in the message.** |
| **F9** | anything else | `Couldn't import: something went wrong and nothing was changed.` |

In **every** F-case the raw throwable goes to `Log.e(TAG, …)` and Crashlytics — C9 clause 2.
**No F-case may be reached without one of these strings being set on `_importResult`.**

**Success. Rendered in `DaybookColors.Success`.**

The summary is built by a **pure** `fun summarise(result: HevyImportResult): String` in
`data/workout/HevyImporter.kt` (tested by `HevyImportSummaryTest`), from
`data class HevyImportResult(val sessions: Int, val sets: Int, val newExercises: Int, val skippedDuplicates: Int, val skippedRows: Int)`.

| ID | When | **Exact text** |
|---|---|---|
| **S1** | `sessions > 0`, nothing skipped | `Imported 4 workouts and 33 sets.` |
| **S1a** | `sessions > 0`, `newExercises > 0` | `Imported 4 workouts, 33 sets and 9 new exercises.` |
| **S2** | `sessions > 0`, duplicates skipped | append ` Skipped 2 already in Daybook.` |
| **S2a** | `sessions > 0`, rows skipped | append ` Ignored 3 rows it couldn't read.` |
| **S3** | **`sessions == 0` and `skippedDuplicates > 0`** | **`Nothing new to import — all 4 workouts in that file are already in Daybook.`** Rendered in **`DaybookColors.TextMuted`, not Success and not Danger** — it is neither. **This is the most important string in this table**: re-importing the same file is the single most likely thing the user will do, and a bare "Imported 0 workouts" would read as a failure. |
| **S4** | **`sessions == 0` and `skippedDuplicates == 0`** (nothing matched, nothing skipped) | **`Couldn't import: no workouts were found in that file.`** Danger, because something genuinely is wrong — V5 passed but grouping produced nothing. |

Composition rules, stated exactly so the implementer does not have to infer them:

- Always singular/plural correctly: `1 workout` / `2 workouts`, `1 set` / `2 sets`,
  `1 new exercise` / `2 new exercises`, `1 row` / `2 rows`.
- **Drop any clause whose count is 0** — never print "Skipped 0". (This rule is inherited
  unchanged from §3.9.9.)
- The counts clause uses an Oxford-free list: two items joined with ` and `, three joined with
  `, ` then ` and ` before the last.
- Skipped clauses are **separate sentences appended after a single space**, in the order
  duplicates-then-rows.

**Worked example matching §3.9.9's original:** `sessions=4, sets=33, newExercises=9,
skippedDuplicates=2, skippedRows=0` →
`Imported 4 workouts, 33 sets and 9 new exercises. Skipped 2 already in Daybook.`

##### E. The in-progress state

Reuse the existing `_isImporting` `StateFlow` and the existing button-label swap: the row's label
reads **`Importing…`** while it runs and the row is `enabled = false` (exactly what
`SettingsScreen.kt:1066` already does for `Import JSON`). During the §3.9.7 hydration step the
label reads **`Fetching your history…`**, because that step needs the network and can be slow
enough that an unexplained wait looks like a hang. Both are label swaps on the existing
`GhostButton` — **no spinner, no dialog, no new component** (C5/C6).

##### F. The Workout screen's empty-state entry point *(REV4: was "the Workout tab's")*

§3.9.1's secondary entry point navigates to the Settings row, so it shares every string above.
The `EmptyState` copy itself — previously unspecified, **now decided (R6)**:

- title: **`No workouts yet`**
- body: **`Start a workout to log your first session, or bring your history over from Hevy.`**
- primary action: **`Start workout`** · secondary `GhostButton`: **`Import from Hevy`**

#### 3.9.10 New files for §3.9

| File | Role |
|---|---|
| `data/workout/CsvReader.kt` | RFC4180-ish reader (§3.9.2). Pure. |
| `data/workout/HevyCsvValidator.kt` *(REV3)* | `validate(headers): ValidationResult` — the V4 header gate (§3.9.10a B). Pure. |
| `data/workout/HevyCsvParser.kt` | rows → `List<ParsedSession>`; date parsing, column mapping, grouping (§3.9.3). Pure. |
| `data/workout/HevyExerciseMatcher.kt` | name → `exerciseId` or "create this custom row" (§3.9.4). Pure. |
| `data/workout/HevyImporter.kt` | the only impure piece: dedupe probe, hydration guard, one transaction, the summary string (§3.9.5–§3.9.9). Called from `WorkoutRepository`. |
| tests | `CsvReaderTest`, `HevyCsvValidatorTest` *(REV3)*, `HevyDateParseTest`, `HevyCsvParserTest` (against the real sample), `HevyExerciseMatcherTest`, `HevyDedupeTest`, `HevyImportSummaryTest` (**must cover S1, S1a, S2, S2a, S3, S4 by exact string**) |

**Five of the six** files are pure and Room-free *(REV3: was four of five)*, which is the point:
the risky part is squeezed down to `HevyImporter`.

### 3.10 Performance discipline *(REVISION 3, new — sourced from the external PRD)*

The PRD's non-architectural engineering advice is good, cheap, and mostly aligned with what this
repo already does. It is adopted here **as Round A requirements**, phrased so each one is
checkable. These are **not backlog** and **not suggestions**.

**P1 — A completed set is written to Room immediately, in its own transaction.**
*(PRD §53.)* The live session must **never** accumulate sets in memory and persist them at
"Finish". Tapping the green check calls `WorkoutRepository.toggleSetComplete(id)` → a Room write
→ the Flow re-emits → the row turns green. A crash, a process death or a battery pull after set
18 must leave sets 1–18 in the database.
- **Already implied** by §3.2/§3.4's design (every set is a row with `completed_at`), and by
  §3.7.1's "Completed row solid green" being driven from the DB. **§3.10 makes it explicit and
  forbids the alternative.**
- This also supplies PRD §52 ("a workout must survive rotation, backgrounding, process death")
  for free: the resume banner (§3.1.1) reads `observeActiveSession()` from Room, so there is no
  in-memory session state to lose.
- **Corollary:** `WorkoutSessionViewModel` holds **no** `List<WorkoutSet>` of its own. Its state
  is derived from the DAO Flows plus the two tickers. A second in-memory copy is the bug.

**P2 — Timer state is isolated; the session screen does not recompose on a tick.**
*(PRD §55.)* Both tickers (§3.7.2) emit once a second. If the elapsed time is read at the top of
the screen's composable, **every exercise card and every set row recomposes 60 times a minute**
while the user is trying to type into a text field.
- **Requirement:** the ticking value is read **only inside the smallest composable that displays
  it** — the header's duration `Text` and the rest-timer row's `Text` — via a deferred read
  (`Text(text = { elapsed })`-style lambda, or a `derivedStateOf` confined to that composable),
  never hoisted into the parent's composition scope.
- The same rule covers §3.7.1's "title swaps to elapsed time on scroll": the `derivedStateOf` on
  `LazyListState` already specified there must be read inside the header, not at screen level.
- **Checkable:** in a debug build, enable Compose recomposition counts and confirm that one
  second of rest-timer countdown recomposes the timer `Text` and **nothing else**.

**P3 — No analytics are recomputed on set completion.**
*(PRD §56.)* Only three derived values may be computed during a live session, and each has a
stated cost:
- `sessionStats(sets)` (§3.4) — a fold over **this session's** sets only. Bounded by one
  workout, recomputed on change. Fine.
- `previousSetsForExercise` (§3.4) — **`LIMIT 50`, read once when the exercise block is opened**,
  cached in the ViewModel for the life of that block. **Not re-read per set, not per keystroke.**
- `bestSetForExercise` (§3.4) — **`LIMIT 1`, read once when the exercise block is opened.**
  Already stated in §3.4; restated here because it is the rule most likely to be casually broken
  by moving the call into the set row.
- **Forbidden:** any query over the user's whole workout history on the live-session screen.
  The per-exercise history list (§3.1.2 R3) is a **separate sheet**, opened deliberately, and is
  where a full-history read belongs.

**P4 — Interaction budgets.** *(PRD §7, §66.)* Targets, not guarantees; a miss is a bug to
investigate, not a release blocker:
- **Tap "complete set" → visible green: no network, no main-thread disk read.** This one *is*
  absolute: §2.1 already guarantees no user action touches the network.
- Add-Exercise search results: **under ~100 ms** for a typed character. Achievable because the
  catalog is a compiled-in in-memory list (§3.3) plus a small custom table — the search is a
  filter over a `List`, not a query.
- Opening the Workout screen must not read the whole history: `observeRecentSessions(limit)` is
  already paged-by-limit (§3.4).
- **App cold start must not regress at all**, and *(REV4)* the mode design makes this strictly
  easier than the tab design did: Beast Mode is a **stacked route that is not composed until it
  is entered**, whereas a fourth pager page would have been kept warm by
  `beyondViewportPageCount = 1` whenever the user sat on Intake. Nothing in §3 may run at
  application start; `WorkoutViewModel`'s flows are `WhileSubscribed(5_000)` (§3.7), so they do
  not start until the mode is opened.
- **The long-press itself costs nothing at rest** *(REV4)*. `combinedClickable` adds a gesture
  detector to one nav item; the press-feedback ramp (§3.6.2) is an `animateFloatAsState` that
  only runs while a finger is down, and it is read **inside the Today icon's own composable**,
  never hoisted into `FloatingPillNav`'s scope — otherwise holding Today would recompose the
  whole nav bar. This is P2's rule applied to the nav.

**P5 — List and state discipline.** *(PRD §54.)* Mostly already this repo's style; written down
so it is not lost:
- `LazyColumn` with **stable `key = { it.id }`** for the session list, the set rows, the
  exercise picker and the history sheet. (`DetailScreen.kt:282` is the existing example.)
- UI models are immutable `data class`es of primitives — no `AppDatabase` entity, no Flow, no
  lambda captured into a list item's state.
- All flows `flowOn(Default)` + `stateIn(WhileSubscribed(5_000))` (already required by §3.7/C7).
- Any fold over history off the main thread with `withContext(Dispatchers.Default)` — the
  precedent is `RespondViewModel`'s streak calculation and `DetailViewModel`'s stats fold.

**P6 — Baseline Profiles: explicitly DEFERRED, with the reason recorded.** *(PRD §8 calls them
"mandatory".)* **Not in Round A.** Generating one needs the `androidx.benchmark` macrobenchmark
plugin, **a second Gradle module**, and an AGP/Gradle version comfortably newer than the frozen
8.3.2 / 8.6 (§5.1). Adding a module to a deliberately single-module project (§0) to chase a
startup improvement, in the same round that adds a feature, is exactly the scope creep the PRD's
own §67 warns against. **It belongs with the toolchain round (§5.1 Option B)**, where the build
is being touched anyway. Recorded so that "the PRD said mandatory" is not later mistaken for an
oversight. **§9 Q25.**

**P7 — No new background work, restated.** *(PRD §5, §6.)* Round A adds **no** worker, **no**
alarm, **no** service, **no** notification channel and **no** permission. The only new
`WorkManager` interaction is the one that already exists (`SyncFlushWorker` firing because
`DATA_TABLES` gained four tables). §3.7.2's forbidden-API list is the enforcement point.

**P8 — GPS: not now, and not by accident.** *(PRD §49.)* Round A tracks distance as a **typed
number the user enters** (§3.2 `distanceMeters`), never a measured route. **No location
permission, no `FusedLocationProvider`, no `ACCESS_FINE_LOCATION` in the manifest.** Stated
because the PRD discusses GPS at length and an implementer reading it might assume the cardio
tracking mode implies location. **It does not.**

---

## 4. Round A sync integration (the sharp edges)

This is where a careless implementation breaks existing cloud data. Read all of §4 before
writing any of it.

### 4.1 `CloudSyncRepository.DATA_TABLES`

```
val DATA_TABLES = arrayOf(
    "habits", "habit_occurrences", "habit_events",
    "food_med_tasks", "food_med_occurrences", "food_med_events",
    "custom_categories", "custom_prompts",
    "exercises", "workout_sessions", "workout_exercises", "workout_sets"      // NEW (REV2: 4)
)
```

`data/sync/DataTablesSyncTest.kt` guards this array against the "journal_questions incident"
(a previous round changed the schema and left a stale entry here, and `attachTracker()` throws
the instant it registers an `InvalidationTracker.Observer` for a table that no longer exists).

**Correction (REV2), because getting this wrong would cost a day of confusion:** that test is
**one-directional**. Read at `app/src/test/java/com/daybook/app/data/sync/DataTablesSyncTest.kt`,
it asserts *"every `DATA_TABLES` entry exists in the latest committed schema JSON"* — it does
**not** assert the reverse. So **adding four tables will not turn it red**, and revision 1's
claim that "it will go red until updated, which is the point" is wrong. There is no automated
safety net here: forgetting to add the four names to `DATA_TABLES` produces a **silent** bug
where workout edits never mark a cloud push pending, and the user's gym log simply never
reaches their account. Phase A4 must add them deliberately, and the round's manual pass must
include *"log a set, background the app, confirm a push happened"*.

### 4.2 Wire model additions (`data/backup/BackupModel.kt`) — NEEDS SIGN-OFF (C4)

Two additions, both **optional and default-absent**:

```kotlin
// in Definitions — the user's CUSTOM exercises only, never the built-in catalog
@EncodeDefault(EncodeDefault.Mode.NEVER)
val customExercises: List<ExerciseDef> = emptyList()

@Serializable
data class ExerciseDef(
    val id: String, val name: String,
    val primaryMuscle: String, val equipment: String,     // REV2: replaces `category`
    val trackingMode: String, val createdAt: String,      // ISO-8601 UTC
    val archived: Boolean = false,
    val source: String = "USER",                          // REV2 (§3.9.6)
    @EncodeDefault(EncodeDefault.Mode.NEVER) val notes: String? = null
)
```

```kotlin
// in DayEntry — that local date's workout sessions
@EncodeDefault(EncodeDefault.Mode.NEVER)
val workouts: List<WorkoutLog> = emptyList()

@Serializable
data class WorkoutLog(
    val id: String,
    val startedAt: String,                 // ISO-8601 UTC
    val endedAt: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val status: String = "COMPLETED",
    val source: String = "MANUAL",         // "MANUAL" | "IMPORTED_HEVY" (REV2, §3.9.6)
    // REV2: sets now nest inside their exercise block rather than sitting flat on the session,
    // mirroring the `workout_exercises` table (§3.2). Nesting is also what lets a block's note,
    // rest timer and superset id round-trip without being repeated on every set.
    val exercises: List<WorkoutExerciseLog> = emptyList()
)

@Serializable
data class WorkoutExerciseLog(                            // REV2: new
    val id: String, val exerciseId: String,
    val orderIndex: Int,
    val notes: String? = null,
    val supersetId: String? = null,        // preserved losslessly (§3.1.2 R4)
    val restSeconds: Int? = null,
    val sets: List<WorkoutSetLog> = emptyList()
)

@Serializable
data class WorkoutSetLog(
    val id: String,
    val setNumber: Int,
    val reps: Int? = null, val weightKg: Float? = null,
    val durationSeconds: Int? = null, val distanceMeters: Float? = null,
    val rpe: Int? = null,
    val setType: String = "NORMAL",        // REV2: replaces `isWarmup: Boolean` (§3.2, §3.9.3)
    val notes: String? = null, val completedAt: String? = null
)
```

> **REV2 note on `setType` and `source` in the wire model.** Both are **non-null fields with a
> non-null default**, which is the one shape `explicitNulls = false` does *not* rescue — a
> defaulted `"NORMAL"` would be emitted into the canonical bytes. They are safe here only
> because they live inside `workouts`/`exercises`, which are themselves
> `@EncodeDefault(NEVER)` empty lists for a user with no gym data, so nothing is emitted at
> all. **Do not promote either field up to `DayEntry` or `HabitDef` level** without adding its
> own `@EncodeDefault(NEVER)` and its own byte-identity test.
>
> `WorkoutSet.sessionId` and `WorkoutSet.exerciseId` (§3.2) are **not** in the wire model —
> both are re-derivable on import from the enclosing `WorkoutLog` / `WorkoutExerciseLog`, and
> putting a denormalised value on the wire is how the two copies eventually disagree.

**The hash-neutrality guarantee, and why it is what protects existing cloud data.**
`ContentHash` and `MonthPartitioner` both serialise with `encodeDefaults = true` **and**
`explicitNulls = false`. That combination means:

- a `null` field is **omitted** from the canonical bytes automatically;
- a **non-null default** (an empty list, `false`, `0`) would otherwise be **emitted**, which
  is exactly what `@EncodeDefault(EncodeDefault.Mode.NEVER)` suppresses.

So `customExercises = emptyList()` and `workouts = emptyList()` produce
**byte-identical** `definitionsHash` and per-month `contentHash` values to today. A user who
never opens Workout mode sees **zero re-push**, zero cloud writes, and zero risk of the
D2 conflict dialog. This is the same technique already used for `HabitDef.streakLongest`,
`HabitDef.journalQuestions`, `HabitDef.promptMessage`, `IntakeLog.qaJson` and
`IntakeLog.outsideFood`. Copy those, including their comments.

**Mandatory new tests** (mirroring `StreakDefHashTest` / `JournalV2HashTest` /
`HabitJournalHashTest`, which exist for precisely this):

- `WorkoutDefHashTest` — `ContentHash.ofDefinitions(defs)` is byte-identical with and
  without the `customExercises` field present-but-empty.
- `WorkoutDayHashTest` — `ContentHash.ofDays(days)` is byte-identical with and without
  `workouts` present-but-empty.
- Extend `BackupModelTest` with a round-trip over a populated workout day.

### 4.3 Mixed-version hazard (must be stated to the user)

A device still running build 23 decodes a month blob containing `workouts` via
`ignoreUnknownKeys = true` — it will **not crash**, but it also will not know about the
field. If that old device then edits anything in that month, it re-exports the month
**without** `workouts` and pushes it — silently dropping the workout data from the cloud for
that month, and then from the new device on the next pull.

Mitigation: **update every device the user signs in on**, in the same sitting. This app is
sideloaded via Firebase App Distribution, and (per `HOW_TO_PUSH_UPDATES.md`) `versionCode`
is bumped so the in-app update check surfaces the new build. Not a code problem; a rollout
instruction. Called out in §9 Q11.

### 4.4 `ExportImportRepository` — six call sites

Every one of these must be extended, or data will silently disappear:

1. **`exportBackup()`** — read custom exercises into `Definitions.customExercises`; read
   sessions, **their exercise blocks** *(REV2)* and their sets, group by `local_date`, and
   attach as `DayEntry.workouts`. **Both** the block fetch and the set fetch must go through
   the chunked `getExercisesForSessions` / `getSetsForSessions` (900-var cap).
2. **`exportRange(start, end)`** — no change needed; it clips `full.days`, so `workouts`
   rides along automatically. Confirm with a test extension to `ExportRangeTest`.
3. **`importAllData(json)`** — full-replace path: wipe `exercises` / `workout_sessions` /
   `workout_exercises` / `workout_sets` inside the existing `withTransaction`, then insert from
   the file, re-deriving each set's `sessionId` / `exerciseId` from its enclosing block (§4.2).
4. **`importRange(backup)`** / **`importMonth(monthKey, days)`** — the **non-destructive
   merge** paths. Follow what these already do for occurrences: delete only the rows whose
   `local_date` falls inside the incoming range, then insert the incoming ones. Do **not**
   wipe globally. `RangeImportNonDestructiveTest` and `MonthMergeTest` are the existing
   guards; extend both.
5. **`applyRemoteDefinitions(defs)`** — upsert incoming custom exercises; delete local
   exercise rows whose ids are absent from the remote set, via
   `allIds()` / `deleteByIds(chunked)` — the exact `DefinitionsUpsertTest` pattern.
6. **`evictMonth(monthKey)`** — **THE EASIEST ONE TO GET WRONG.** Eviction drops an old
   month's rows locally once its hash matches the cloud. If it deletes occurrences but
   leaves that month's `workout_sessions` behind, the next `exportBackup()` produces a month
   bucket the cloud does not have → `MonthPartitioner.changedMonths` flags it → an endless
   re-push loop, or worse, a month doc that overwrites cloud history with a partial view.
   `evictMonth` must delete that month's `workout_sets`, **then its `workout_exercises`**
   *(REV2 — a third table to forget)*, **then** its `workout_sessions`, all by session id,
   chunked, in the same transaction. Leaving orphaned blocks behind is the quieter version of
   the same bug: they carry no `local_date` of their own, so nothing else would ever collect
   them.

**And a seventh, added by REV2:** `data/workout/HevyImporter.kt` (§3.9) writes into three of
these tables from outside `ExportImportRepository`. It must obey §3.9.7's hydrate-or-abort
rule before it writes a single row into a month the device may not hold. This is the same
class of mistake as item 6, approached from the other direction.

### 4.5 What does NOT change

`formatVersion` stays 3. No Firestore field names change. No `firestore.rules` change (the
parent-doc owner match already permits arbitrary fields; months are a subcollection under the
same match). No `firestore.indexes.json` change. No change to the push debounce, the listener
model, the echo guard, `SyncStateStore`, or the D2 conflict flow.

### 4.6 Blob-size sanity

`CloudSyncRepository`'s own §A7 note records ≈120 KB raw / ~15 KB gzipped per month doc
today, against Firestore's 1 MiB hard cap. A heavy lifter logging 5 sessions/week × 25 sets
adds roughly 500 set records/month ≈ 60 KB raw / ~6 KB gzipped. Comfortable. The existing
`SOFT`/`HARD` warn thresholds and the `oversizedMonths` skip path need no tuning.

## 4.7 Round A phase list (for the implementing agent)

**Revision 2 reshapes this table.** A1–A3 grow (a fourth entity, the two-axis taxonomy, the
derived-value queries), A6 splits into A6a/A6b because the live-session screen is now the
largest single piece of the round, and **A8 is new — the whole of §3.9.** Nothing was removed.

**Revision 4 rewrites A5 and touches A1, A7 and A9.** A5 loses every tab-related item
(`NavConfig`, `NavConfigTest`, the pill-nav icon, the `NavigationSettingsScreen` toggle, the
pager arm) and gains the gesture, the press feedback, the coach-mark and the mode root. A1 gains
two `app_settings` columns and loses the `nav_tabs` update. A7 gains the `Open Workout` row.
A9's migration assertions change. Everything else is untouched.

| Phase | Work | Gate |
|---|---|---|
| A0 | Re-read §1 constraints **(including the new C9)**. Confirm §9 answers are in hand — **including Q10 and Q14–Q21 (revision 2), Q22, Q25, Q30, Q31 (revision 3), and Q8 (rewritten), Q11 (rewritten), Q32 and Q33 (revision 4)**. **Q32 gates phase A7 and part of A1** — the `workout_today_card_enabled` column and the Today row ship together or not at all; do not write either before Q32 is answered. **Q22 is a hard gate: if the answer is "build the universal tracker", STOP — §3's schema is wrong for that answer and the round must be re-planned, not adapted mid-flight.** Re-read §2.2.2's **Ri1–Ri3** and treat them as binding on every later phase. | — |
| A1 | `data/model/WorkoutModel.kt`, **4 entities** *(REV2)*; `AppDatabase` v22 + 4 DAO accessors; `ExerciseDao`, `WorkoutDao`; `MIGRATION_21_22`; register in `DatabaseModule`; `AppSettings` **5** new columns *(REV2: + `rest_timer_default_seconds`; **REV4: + `workout_hint_state`, `workout_today_card_enabled`, and the `nav_tabs` UPDATE is deleted — the migration is now 100 % additive**)* + `AppSettingsDao` setters + `AppSettingsRepository` mirrors | `assembleDebug` green, `22.json` generated |
| A2 | `data/workout/ExerciseTaxonomy.kt` (`MuscleGroup`, `Equipment`, label maps) *(REV2)* + `data/workout/ExerciseCatalog.kt` (~70 builtins **with muscle + equipment**) + `HEVY_ALIASES` *(REV2)* + `ExerciseCatalogTest` (ids unique, ids stable, every muscle group reachable, **every alias value is a real catalog id**, label maps total) | unit tests green |
| A3 | `data/WorkoutRepository.kt` + DI provider + `ExerciseNormaliseTest`; **the derived-value layer (REV2)**: `previousSetsForExercise`, `bestSetForExercise`, `lastRestSecondsForExercise`, and the pure `previousBySetNumber` / `isPersonalRecord` / `sessionStats` / `columnsFor` functions **with a unit test each** | unit tests green |
| A4 | **Sync**: `BackupModel` additions (incl. `WorkoutExerciseLog`, `setType`), `DATA_TABLES` **(4 names — and note §4.1's correction: no test will catch this for you)**, all six `ExportImportRepository` call sites, `WorkoutDefHashTest`, `WorkoutDayHashTest`, extend `MonthMergeTest` / `RangeImportNonDestructiveTest` / `DefinitionsUpsertTest` / `BackupModelTest` | unit tests green — **do not proceed to UI until this is green** |
| A5 *(REV4 — REWRITTEN; the old contents are withdrawn)* | **The entry point and the mode shell.** `Components.combinedClickableImpl` (§3.6.1 a); `FloatingPillNav` + `DaybookScaffold` + `DaybookScaffoldNav` gain `onLongSelect` / `hintDotRoutes` / the coach-mark slot; the Today-icon press ramp + haptic + `LocalReduceMotion` branch (§3.6.2) with `longPressRampMillis` extracted and `NavLongPressRampTest` written; `ui/components/CoachMark.kt` (§3.6.3) + the `workout_hint_state` 0→1→2 wiring; the dot on the Today icon; `ic_workout.xml` + `NavIconInflateTest`; `MainActivity`'s `goWorkout` + the **six** workout `composable(...)` destinations (§3.6.6); the Beast Mode accent provider (§3.8.1). **Explicitly NOT in this phase, and not anywhere in this round: `NavConfig`, `NavConfigTest`, `nav_tabs`, `NavigationSettingsScreen`, the pager `when`, `beyondViewportPageCount`, any `BackHandler`.** | App launches with **three** tabs, unchanged. Tap Today → Today. Hold Today → the icon visibly ramps, a tick fires, Beast Mode opens full-screen with no bottom nav. `×` and system back both return to the tab you came from. Coach-mark appears once and never again after "Got it". With TalkBack on, focusing Today announces the long-press action and it can be triggered from the actions menu. With *Touch and hold delay* set to Long in Android accessibility settings, the ramp still finishes exactly when the gesture fires. |
| A6a | `ui/workout/` part 1: `WorkoutScreen` + `WorkoutViewModel`, `WorkoutDetailScreen`, `AddExerciseScreen` + the two filter sheets *(REV2)*, `ExerciseFormScreen` (two pickers), `ExerciseHistorySheet` *(REV2)* | manual pass: can browse, filter, search and create exercises |
| A6b | `ui/workout/` part 2 — **the live session (§3.7.1)**: `WorkoutSessionScreen` + VM, per-block cards, notes, mode-driven set table, PREVIOUS column, PR medal, the two tickers (§3.7.2), rest-timer sheet, Finish / Discard. **Re-verify the build-22 IME fix with a focused field low on the page.** | manual pass |
| A7 | Settings: **`Open Workout` *(REV4 — unconditional)***, weight unit, **Beast Mode accent** *(REV4: default `CORAL`)*, **default rest timer** *(REV2)*, and — **if and only if §9 Q32 says yes** — `Show workout on Today` + the Today row itself (§3.6.4); `WeightFormatTest` | unit tests green; **manual: Settings → Workout → Open Workout reaches Beast Mode with the gesture never used** |
| A8 | **Hevy CSV import (§3.9) — REV2, new.** `CsvReader`, **`HevyCsvValidator` *(REV3)***, `HevyCsvParser`, `HevyExerciseMatcher`, `HevyImporter`; the Settings → Backup & data row + dialog + picker; the Workout `EmptyState` button; the hydrate-or-abort guard (§3.9.7); **all of §3.9.10a — the five validation gates, every F-string, every S-string, the `Importing…`/`Fetching your history…` labels, and the `ok`-prefix extension**; `CsvReaderTest`, **`HevyCsvValidatorTest`**, `HevyDateParseTest`, `HevyCsvParserTest` (against the real sample file), `HevyExerciseMatcherTest`, `HevyDedupeTest`, `HevyImportSummaryTest` **(exact-string assertions for S1, S1a, S2, S2a, S3, S4)** | unit tests green; **manual: import the real `workout_data.csv`; import it again and confirm the S3 message appears, not "0 workouts"; then try importing a Daybook JSON backup and confirm the F3/F4 message appears** |
| A9 | `MigrationTest.migrate21To22` (**REV4: four tables + *five* `app_settings` columns, and **no** `nav_tabs` assertion**); full `./gradlew test` + `assembleRelease`; signed APK named per the repo's convention (`Daybook-v0.6-workout-release.apk`) | **all 100+ tests green** |

---

# ROUND B — Health sync (Mi Band 10 and any Health-Connect-compatible device)

## 5. Platform choice: Health Connect, not Google Fit

### 5.0 The recommendation and its justification

**Target Health Connect (`androidx.health.connect:connect-client`) exclusively. Do not
implement any Google Fit path, not even as a fallback.**

Evidence, verified during planning (September 2026):

1. **The Google Fit APIs are dead or dying.** Developer sign-ups for the Fit Android APIs
   and the `fitness.googleapis.com` REST API closed **1 May 2024**, and Google's own
   published position is that the Fit APIs are "only supported until the end of 2026". A
   brand-new integration written against them today could not even be registered, let alone
   be supported past this calendar year.
2. **The consumer Google Fit app is gone** as the hub it used to be; Google's own migration
   guide (`developer.android.com/health-and-fitness/health-connect/migration/fit`) points
   Android apps at Health Connect.
3. **Health Connect is the right architecture for this app anyway.** It is an **on-device**,
   OS-mediated data store. No OAuth, no Google Cloud project, no scope verification, no
   network round trip, no server. That matches Daybook's offline-first posture (constraint
   C6) in a way the Fit REST API never could.
4. **Mi Fitness writes to it.** Xiaomi's Mi Fitness companion app (which is how a Mi Band 10
   reaches a phone at all — the band has no open API and no direct app access) has a
   Health Connect integration that can share **Steps, Sleep, Heart rate and Workouts**.
   Enabling it is a user action inside Mi Fitness → Health Connect, not something Daybook can
   do for them. Same story for Samsung Health, Fitbit, Garmin Connect, Amazfit/Zepp,
   Google's own Fitbit-branded apps — which is exactly what makes "any other manufacturer's
   band" work for free. **Health Connect is the generic answer the user asked for.**

Consequence to be honest about: **Daybook never talks to the band.** It reads whatever
Mi Fitness has already deposited in Health Connect. If Mi Fitness has not synced (band out of
range, app killed by battery optimisation), Daybook sees nothing new. That is inherent to
every Android app in this category and cannot be engineered around.

### 5.1 The toolchain problem (the single biggest decision in Round B)

Verified directly from the published artifacts' `aar-metadata.properties`:

| `connect-client` version | requires `compileSdk` | requires AGP |
|---|---|---|
| `1.1.0-alpha08` | **34** | any |
| `1.1.0-alpha09` … `1.1.0-alpha12` | 35 | any |
| `1.1.0-beta01` | 35 | 8.6.0 |
| `1.1.0-beta02` … `1.1.0-rc03` | 36 | 8.9.1 |
| **`1.1.0` (stable, Oct 2025)** | **36** | **8.9.1** |
| `1.2.0-alpha06` (latest) | 36 | 8.9.1 |

Daybook is on **compileSdk 34, AGP 8.3.2, Gradle 8.6**.

So there are exactly two ways in:

**Option A (RECOMMENDED) — pin `androidx.health.connect:connect-client:1.1.0-alpha08`,
change nothing about the toolchain.**

- Zero risk to the existing build. `compileSdk` stays 34, AGP stays 8.3.2, Gradle stays 8.6,
  and every "last version that builds against compileSdk 34" pin
  (`firebase-bom:33.1.2`, `credentials:1.3.0`, `biometric:1.1.0`,
  `security-crypto:1.1.0-alpha06`) stays untouched.
- Its transitive dependencies are modest and already-satisfied-or-lower:
  `androidx.activity:1.2.0`, `androidx.annotation:1.8.1`, `androidx.core:core-ktx:1.12.0`,
  `com.google.guava:guava:31.1-android` (+ `kotlinx-coroutines-guava:1.7.3`, already on
  coroutines 1.7.3). **Guava is the one to watch** — it is a large artifact and R8 shrinking
  is already on for release; expect to add ProGuard keeps if anything strips wrong, and
  re-check the release APK size (currently ~7.2 MB).
- **Verified to contain everything the MVP needs**: `HealthConnectClient`,
  `PermissionController`, `HealthPermission`, `StepsRecord`, `HeartRateRecord`,
  `RestingHeartRateRecord`, `SleepSessionRecord`, `ExerciseSessionRecord`,
  `TotalCaloriesBurnedRecord`, `ActiveCaloriesBurnedRecord`, `DistanceRecord`,
  `AggregateRequest`, `ReadRecordsRequest`, `ChangesTokenRequest` and the `changes/` package.
- **What it lacks** — *(REV3: partially corrected. Google's published release notes for
  `1.1.0-alpha08` (4 Sep 2024) say it **"Added an API to check feature availability"**, so
  revision 2's claim that alpha08 lacks the `HealthConnectFeatures` probe is **probably wrong**.
  Do not rely on either statement: **Phase B0's first task is a compile check** — write a
  throwaway reference to `HealthConnectFeatures` and to each of the two permission constants and
  see what resolves. The plan must not carry a guess about an API surface that a one-minute build
  can settle.)* — the *constants*
  `PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND` (added in alpha09) and
  `PERMISSION_READ_HEALTH_DATA_HISTORY` (added in alpha10). **Workaround: the permission strings are
  OS-level and stable** — declare `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND`
  and `android.permission.health.READ_HEALTH_DATA_HISTORY` as literals in the manifest and
  pass the same literals to the permission-request contract. **This must be verified on a
  real device early in Phase B1** — if the older client's request contract rejects an unknown
  permission string, fall back to foreground-only reads (§6.3) and a 30-day window, which is
  still a complete, useful feature.
- Honest downside: it is a **two-year-old alpha**. The client library is a thin wrapper over
  an AIDL/proto interface that the Health Connect provider keeps backward-compatible (it
  ships as a Mainline module precisely so old clients keep working), so the risk is low but
  not zero.

**Option B — do a toolchain round first, then use `1.1.0` stable.**

Gradle 8.6 → 8.11.1+, AGP 8.3.2 → 8.9.1+, compileSdk 34 → 36, and then re-validate
**every** frozen pin above, plus `compose-bom`, plus the `google-services` / Crashlytics /
App Distribution Gradle plugins, plus kapt-under-K2. That is a genuine, multi-day,
whole-build round with **no user-visible benefit of its own** and a real chance of breaking
sign-in, biometrics, or the release build.

**Recommendation: Option A now; schedule Option B as its own separate round later**, at
which point moving from alpha08 to 1.1.0 stable is a one-line version bump. This keeps the
risk of the toolchain upgrade off the critical path of a feature the user actually wants.
§9 Q1 puts this to the user in plain language.

### 5.2 Distribution note (not a blocker today, a blocker later)

Apps distributed **on Google Play** that read Health Connect data must complete a
**Health Connect data-type declaration form** in Play Console and publish a privacy policy.
Daybook is sideloaded via **Firebase App Distribution** and has no Play listing, so the form
does not apply today. **It would become a hard gate the day this app is listed on Play.**

Separately, and regardless of distribution: Health Connect **requires** the app to declare a
privacy-policy rationale activity in the manifest (§6.2). That is not optional.

## 6. Feature 1: Health sync design

### 6.1 Data types — the comprehensive pass *(REVISION 3 — replaces revision 2's short table)*

The user asked for **"as much data as possible, done right"**, and supplied a comparison table
showing how much is out there (Strava → activities; MyFitnessPal → food, calories, macros,
hydration; Samsung Health → steps, HR, sleep, workouts, SpO₂; Google Health/Fit → the lot).
Revision 2's six-row table was written against "what a Mi Band 10 emits" and is too narrow for
that ask. **Every real Health Connect record type is enumerated below with an explicit
MVP / LATER / NEVER decision and a specific reason.**

**Verification.** The list is taken from Google's published Health Connect data-type reference
(`developer.android.com/health-and-fitness/guides/health-connect/plan/data-types`), fetched
during this revision, **not from memory. 42 record types across 7 categories.** Revision 3's
brief listed 36 and **missed six**: `ActivityIntensityRecord`, `CyclingPedalingCadenceRecord`,
`PlannedExerciseSessionRecord`, `StepsCadenceRecord`, `BodyWaterMassRecord`,
`MindfulnessSessionRecord`. All six are included below.

#### 6.1.0 A hard constraint that decides several rows for us

Under §5.1 **Option A** the pin is `connect-client:1.1.0-alpha08` (Sep 2024). Release notes give
first-available versions, so these types **do not exist in the pinned client** and are
**LATER-by-construction** regardless of their merit:

| Record | First available | Consequence |
|---|---|---|
| `SkinTemperatureRecord` | `1.1.0-alpha10` | not compilable under the pin |
| `PlannedExerciseSessionRecord` | `1.1.0-alpha10` | not compilable under the pin |
| `MindfulnessSessionRecord` | `1.1.0-beta02` | not compilable under the pin |
| `ActivityIntensityRecord` | `1.2.0-alpha01` | not compilable under the pin |

If the user later chooses §5.1 Option B (the toolchain round), these four become available as a
one-line version bump. **Marked `LATER (pin)` below.** Every other type in the table predates
alpha08 and is available today — **to be confirmed by the B0 compile check**, not assumed.

#### 6.1.1 Activity (15 types)

| Record | Decision | Reason |
|---|---|---|
| `StepsRecord` | **MVP** | The single most-wanted number, written by every band and phone. Read as `AggregateRequest(StepsRecord.COUNT_TOTAL)` daily — **never raw records**, which are thousands of tiny rows. |
| `DistanceRecord` | **MVP** | Pairs with steps in one summary row; one more aggregate on a request Daybook is already making. Daily `DISTANCE_TOTAL`. |
| `ActiveCaloriesBurnedRecord` | **MVP** | The "calories burned" figure users expect; aggregate daily total. |
| `TotalCaloriesBurnedRecord` | **MVP** | Read alongside active because **different apps write different ones** — Mi Fitness may write one and not the other, and showing a blank because we picked the wrong field would look like a bug. `HealthDay` stores both nullable and the UI prefers active. |
| `ExerciseSessionRecord` | **MVP** | The band's recorded workouts. Directly answers "what did my band see", and §6.4's whole separate-from-Round-A argument presumes it. |
| `FloorsClimbedRecord` | **LATER** | Real and cheap, but Mi Band 10 does not report floors reliably and it would render as a permanently empty stat. Add when there is a Health screen with room for a secondary-stats row. |
| `ElevationGainedRecord` | **LATER** | Only meaningful for outdoor sessions, and Daybook shows no route or elevation profile that would give the number context. |
| `Vo2MaxRecord` | **LATER** | Genuinely interesting and Xiaomi/Samsung do write it — but it is a single sporadic value that needs a trend line to mean anything, and charts are deferred (§3.1.2 R3). Revisit with charts. |
| `SpeedRecord` | **NEVER (as a day metric)** | A per-sample series inside a session. Daybook has no session-detail chart, so it would be thousands of rows read to display nothing. Reconsider only if a per-session detail view with graphs is ever built. |
| `PowerRecord` | **NEVER** | Cycling-power-meter data. Daybook has no cyclist-facing surface and the user has not asked for one. |
| `StepsCadenceRecord` | **NEVER** | Per-sample cadence series; same objection as `SpeedRecord`, with a narrower audience. |
| `CyclingPedalingCadenceRecord` | **NEVER** | As above, and requires a cadence sensor almost nobody has. |
| `WheelchairPushesRecord` | **NEVER** | Only meaningful as a **replacement** for steps for wheelchair users, which means a whole alternative activity model in the UI. Excluding it is a real accessibility gap and is named as one here rather than passed over in silence. Revisit if the user ever wants it — it is a small read, but the UI work is not small. |
| `ActivityIntensityRecord` | **LATER (pin)** | Needs `1.2.0-alpha01`. Also brand-new, so few apps write it yet. |
| `PlannedExerciseSessionRecord` | **LATER (pin)** | Needs `1.1.0-alpha10`. It is *planned* training, which is **Round C territory** (Programs/Routines), not Round B's "what happened" reporting. |

#### 6.1.2 Vitals (9 types)

| Record | Decision | Reason |
|---|---|---|
| `HeartRateRecord` | **MVP** | Daily avg/min/max via `AggregateRequest` (`BPM_AVG`/`BPM_MIN`/`BPM_MAX`). **Never the raw per-sample series** — a day of continuous HR is thousands of records and Daybook has no chart to justify reading them. |
| `RestingHeartRateRecord` | **MVP** | One value per day, the most meaningful single cardio number, and a well-established health signal. Direct read, no aggregate needed. |
| `OxygenSaturationRecord` | **MVP** *(REV3: promoted from "later")* | The user's own comparison table lists **SpO₂** as a Samsung Health headline metric, and Mi Band 10 measures it. One nullable `Float` column, one more permission. The user explicitly asked for maximum coverage; this is the clearest case of a metric that was deferred only for permission-count reasons. |
| `HeartRateVariabilityRmssdRecord` | **LATER** | Bands do write it, but HRV is **only** interpretable as a deviation from your own baseline — a single number is noise. It is genuinely useful in a fatigue dashboard, which is **Round C** (§C.6). Storing it now with nowhere to show it would be storage without a feature. |
| `RespiratoryRateRecord` | **LATER** | Sparsely written, usually only during sleep, and Daybook's sleep card has no room for it in Round B. |
| `BodyTemperatureRecord` | **LATER** | Manually-entered clinical data in practice; no band the user owns writes it automatically. |
| `BasalBodyTemperatureRecord` | **SEE §6.1.5** | Categorised as Cycle Tracking in practice. Deferred to the §9 Q26 decision. |
| `BloodPressureRecord` | **LATER** | Requires a BP cuff. If the user owns one this becomes a quick win — it is a simple two-number record — but nothing in their stated setup writes it. **§9 Q27** asks. |
| `BloodGlucoseRecord` | **LATER, with a specific note** | Normally out of scope — but Daybook is **already a Crohn's-oriented food diary** (`red_flag`, `suspected_food`, `outside_food` in §0's schema), and glucose alongside food logs is a coherent idea for someone tracking gut symptoms. **Not MVP** (it needs a CGM or manual entry, and presenting medical data next to a symptom diary raises a care question this plan will not answer unilaterally). **§9 Q27** asks. |
| `SkinTemperatureRecord` | **LATER (pin)** | Needs `1.1.0-alpha10`. |

#### 6.1.3 Sleep (1 type)

| Record | Decision | Reason |
|---|---|---|
| `SleepSessionRecord` | **MVP** | Already in revision 2 and unchanged: session read, sum the `stages` for deep/light/REM/awake. §7.1's `HealthDay` already has all five columns. |

#### 6.1.4 Body measurement (7 types)

| Record | Decision | Reason |
|---|---|---|
| `WeightRecord` | **MVP** *(REV3: promoted from "later")* | The most-requested body metric, written by every smart scale and by Mi Fitness manual entry, and **the only one of this group that a plain band user actually populates**. One nullable `Float`. It also removes the need for §3.1.2 R5's deferred hand-logged body-measurement feature to exist before the user can see their weight at all. |
| `BodyFatRecord` | **LATER** | Smart-scale bioimpedance only, and the numbers are notoriously unreliable. Cheap to add later next to weight. |
| `BasalMetabolicRateRecord` | **LATER** | A derived estimate, not a measurement; useful only alongside nutrition targets, which is `NutritionRecord`'s question (§6.1.6). |
| `LeanBodyMassRecord` | **LATER** | Smart-scale-derived; same objection as body fat, smaller audience. |
| `BoneMassRecord` | **LATER** | Smart-scale-derived; smallest audience of the group. |
| `BodyWaterMassRecord` | **LATER** | As above. Noted explicitly because the revision brief's list omitted it. |
| `HeightRecord` | **LATER** | Changes essentially never. Its only use is computing BMI, which Daybook does not show. If BMI is ever wanted this is a one-line addition. |

#### 6.1.5 Cycle tracking (7 types) — **NOT DECIDED HERE**

`MenstruationFlowRecord`, `MenstruationPeriodRecord`, `OvulationTestRecord`,
`CervicalMucusRecord`, `SexualActivityRecord`, `IntermenstrualBleedingRecord`,
`BasalBodyTemperatureRecord`.

**Decision: deliberately deferred to the user. Not silently included, not silently excluded.**

This is the most sensitive category Health Connect carries. Requesting these permissions puts
them on the OS consent screen where the user sees them listed, and `SexualActivityRecord` in
particular is data most people would not expect a habit-tracking app to ask for. There is also a
real argument *for*: this data next to a symptom-and-food diary is genuinely useful to someone
tracking a chronic condition, and excluding it by default is its own kind of decision.

**The plan's position: default to NOT requesting any of them**, because an unrequested permission
is invisible and a wrongly-requested one is alarming. **§9 Q26 asks the user directly**, and
offers the middle option: ship it **off**, behind an explicit opt-in row in Settings → Health, so
the permission is only ever requested by someone who went looking for it.

#### 6.1.6 Nutrition (2 types) — and the Intake question

| Record | Decision | Reason |
|---|---|---|
| `HydrationRecord` | **MVP** | Simple (a volume with a time), written by MyFitnessPal and Samsung Health per the user's own table, and it maps to a single daily-total number. No conflict with anything Daybook already owns. |
| `NutritionRecord` | **MVP, read-only, displayed separately** — see below | MyFitnessPal writes it and the user's table rates that integration ⭐⭐⭐⭐⭐. It is also the one record type that **collides with a feature Daybook already has.** |

**The `NutritionRecord` decision, made explicitly rather than by citing precedent.**

Daybook already has a hand-logged **Intake** feature: `food_med_tasks` / `food_med_occurrences`,
with free-text answers, a Crohn's trigger flag, a suspected-trigger-food field and an
outside-food marker (§0). `NutritionRecord` carries something structurally different: calories
and macros per meal, written by another app.

**Decision: imported nutrition is shown as a read-only summary, in its own card, on the Health
surface — never merged into, never written into, and never displayed inside the Intake tab.**

Concretely:
- A new nullable group of columns on `HealthDay` (§7.1): `nutritionCalories: Float?`,
  `nutritionProteinGrams: Float?`, `nutritionCarbsGrams: Float?`, `nutritionFatGrams: Float?`,
  `hydrationMl: Float?` — **daily aggregates only, not per-meal rows.** Per-meal storage would
  mean a second occurrence-shaped table that competes with `food_med_occurrences` for the same
  conceptual space, which is precisely the confusion to avoid.
- Rendered on `HealthScreen` (§7.4) as one `SoftCard` titled **`Nutrition`** with a subtitle
  naming the writing app: **`From MyFitnessPal`** — resolved at runtime from
  `metadata.dataOrigin.packageName` through a small pure `SourceAppLabels` map with an
  **exact fallback string `From another app`** for an unrecognised package. **No icon scraping,
  no `PackageManager` label lookup** (it fails for an uninstalled writer and costs a
  synchronous binder call).
- **Nothing about the Intake tab changes.** No badge, no merged row, no "you also ate" line.

**Why separate, argued rather than cited.** §6.4 keeps band-recorded workouts out of the Workout
tab, and the same reasoning applies here *for the same underlying reason, not by analogy*: the
dividing line established in §3.9.6 is **"does something else keep updating this?"** A
`NutritionRecord` is **re-read on every pull and owned by MyFitnessPal** — a local edit would be
overwritten, a local delete would resurrect. Daybook's Intake entries are the user's own prose
and are theirs to edit forever. Putting two things with opposite ownership rules in one list
means one of them behaves surprisingly. And there is a concrete failure the separation prevents:
a merged view would make the same lunch appear twice — once as the user's typed note, once as
MyFitnessPal's 620 kcal — with no honest way to dedupe them. **§9 Q28** confirms with the user.

#### 6.1.7 Wellness (1 type)

| Record | Decision | Reason |
|---|---|---|
| `MindfulnessSessionRecord` | **LATER (pin)** | Needs `1.1.0-beta02`. Conceptually it is a fine fit for a habit tracker — but it is new, few apps write it, and it is unreachable under the Option A pin. |

#### 6.1.8 Summary of the MVP permission set

**12 record types, 12 read permissions** (up from revision 2's 8):

`StepsRecord`, `DistanceRecord`, `ActiveCaloriesBurnedRecord`, `TotalCaloriesBurnedRecord`,
`ExerciseSessionRecord`, `SleepSessionRecord`, `HeartRateRecord`, `RestingHeartRateRecord`,
`OxygenSaturationRecord`, `WeightRecord`, `HydrationRecord`, `NutritionRecord`.

**The honest cost, stated plainly:** revision 2 argued that "more permissions requested up front
means a lower grant rate", and that is still true — the OS consent sheet now shows twelve lines
instead of eight. **§9 Q29** offers the user the mitigation: request the six **core** types
(steps, distance, calories ×2, exercise, sleep) on the first "Connect" tap, and the six
**optional** ones (HR ×2, SpO₂, weight, hydration, nutrition) from a second row in Settings →
Health labelled **`Add more health data`**. *Recommendation: ask for all twelve at once.*
Health Connect's consent sheet lets the user tick individual types anyway, so splitting it mostly
buys a second trip through a system dialog.

#### 6.1.9 Never, at any point, for any version

- **Raw per-sample series of any kind** — heart rate, speed, cadence, power. Aggregates only.
  Reading a day of per-second HR to render one number is the exact "observe huge tables
  indiscriminately" mistake the PRD's §54 warns about, and it would be a battery regression (C7).
- **GPS routes / `ExerciseRoute`.** Daybook draws no maps, has no map dependency, and adding one
  would be megabytes and a new visual language (C5).
- **Writing anything back to Health Connect.** §6.3 and §9 Q6. Unchanged.
- **Xiaomi "Stress", "Body Energy", "PAI" and similar.** These are **vendor-invented metrics with
  no Health Connect record type at all** — they exist only inside Mi Fitness and are not
  published to any app. They will never appear in Daybook, no matter which permissions are
  granted. Worth telling the user plainly so their absence is not read as a bug.

### 6.2 Permissions, manifest and availability

`AndroidManifest.xml` additions:

```xml
<!-- REV3 §6.1.8 — the 12-type MVP set. The first eight are revision 2's; the last four are
     revision 3's additions (SpO2, weight, hydration, nutrition). -->
<uses-permission android:name="android.permission.health.READ_STEPS"/>
<uses-permission android:name="android.permission.health.READ_SLEEP"/>
<uses-permission android:name="android.permission.health.READ_EXERCISE"/>
<uses-permission android:name="android.permission.health.READ_HEART_RATE"/>
<uses-permission android:name="android.permission.health.READ_RESTING_HEART_RATE"/>
<uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED"/>
<uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED"/>
<uses-permission android:name="android.permission.health.READ_DISTANCE"/>
<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION"/>
<uses-permission android:name="android.permission.health.READ_WEIGHT"/>
<uses-permission android:name="android.permission.health.READ_HYDRATION"/>
<uses-permission android:name="android.permission.health.READ_NUTRITION"/>
<!-- optional, requested separately, degrade gracefully if refused -->
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"/>
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY"/>

<!-- required so getSdkStatus() can see the provider on Android 13 and below -->
<queries><package android:name="com.google.android.apps.healthdata" /></queries>
```

and, inside `<application>`, the **required** privacy-policy rationale activity, in both its
forms:

```xml
<activity android:name=".ui.health.HealthPermissionsRationaleActivity"
          android:exported="true" android:theme="@style/Theme.Daybook">
  <intent-filter>
    <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
  </intent-filter>
</activity>
<activity-alias android:name="ViewPermissionUsageActivity"
          android:exported="true"
          android:targetActivity=".ui.health.HealthPermissionsRationaleActivity"
          android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
  <intent-filter>
    <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
    <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
  </intent-filter>
</activity-alias>
```

`HealthPermissionsRationaleActivity` is a tiny `ComponentActivity` (it does **not** need to
be the `FragmentActivity` that `MainActivity` is, since it hosts no `BiometricPrompt`)
rendering one Compose screen inside `DaybookTheme`: what Daybook reads, why, that it never
writes, and that the data stays on the device except for the user's own encrypted account
backup. This doubles as the app's health privacy statement.

**Availability and degradation ladder** (`data/health/HealthConnectAvailability.kt`, a pure
mapping + a thin `getSdkStatus` caller, unit-tested):

*(REV3 — C9: every row now carries **exact final copy**, and three failure branches that
revision 2 left implicit have been added. The rule for this whole table: **the Today card may
hide itself silently, but Settings → Health must always be able to tell the user why.**
Hiding a card is not an error; a user asking "why is there nothing here?" and finding no answer
is.)*

| State | **Exact behaviour and copy** |
|---|---|
| Android 8.0–8.1 (API 26–27) | Health Connect requires Android 9+. Treat as permanently unavailable — falls into `SDK_UNAVAILABLE` below. Never branch on `Build.VERSION` for this; `getSdkStatus()` reports it. |
| `SDK_UNAVAILABLE` | Today card never renders. Settings → Health shows one muted line: **`Health Connect isn't available on this phone.`** No Play link — there is nothing to install. No "Connect" button. |
| `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` | Settings → Health shows: **`Health Connect needs updating before Daybook can read your health data.`** with a `GhostButton` **`Update Health Connect`** deep-linking to `market://details?id=com.google.android.apps.healthdata`. **If no app can handle that intent** (`ActivityNotFoundException` — a phone with no Play Store), show **`Couldn't open the Play Store on this phone.`** in `DaybookColors.Danger` **instead of crashing or doing nothing.** *(REV3: revision 2 specified the deep link but not this failure.)* |
| `SDK_AVAILABLE`, permissions not granted | Today card hidden. Settings → Health shows **`Connect`**, launching the OS permission sheet. Sub-line: **`Daybook only reads. It never writes anything to Health Connect.`** |
| **Permission sheet returned with nothing granted** *(REV3, new)* | The user declined. Show, once, in the Settings result slot: **`No health data was shared. You can tap Connect again any time, or choose which data to share in the Health Connect app.`** **Do not re-prompt automatically and do not nag** (C7/§6.3). This branch was previously unwritten, which would have left a declined grant looking identical to a broken one. |
| **Permission sheet threw / no handler** *(REV3, new)* | `ActivityNotFoundException` or any throwable from the request contract. Show **`Couldn't open the Health Connect permission screen. Make sure Health Connect is installed and up to date.`** in `DaybookColors.Danger`, and `Log.e` the throwable. This is also the branch that catches §5.1's named risk R4 (the alpha08 client rejecting an unknown permission string) — **so it must not be a silent catch**, or the one on-device symptom of that risk disappears. |
| **Partially granted** *(REV3, new)* | Some of the 12 types granted, some not — the normal outcome, because the OS sheet has per-type checkboxes. **Not an error.** Read and show what was granted; show nothing for what wasn't (no empty stat with a dash). Settings → Health lists the ungranted types under **`Not shared:`** followed by their display names, with a **`Change what's shared`** row opening the Health Connect settings screen. |
| Granted, but zero records | First-class help state, not a blank card: **`No data yet. In Mi Fitness, open Profile → Settings → Health Connect and turn on Steps, Sleep, Heart rate and Workouts.`** By far the most likely real-world failure. |
| **A read threw** *(REV3, new — C9)* | Any `HealthConnectClient` call failing (provider killed, `RemoteException`, a record type the provider does not know). Keep the last-good data on screen — **do not blank the card** — and set the Settings → Health status line to **`Couldn't refresh your health data. Last updated <relative time>.`** where `<relative time>` is the app's existing relative formatter ("2 hours ago"). This is C9 clause 4 in practice: quiet in the moment, **knowable on demand**. |
| Granted, data present | Normal. Settings → Health status line reads **`Last updated <relative time>.`** |

Android 14+ (API 34) has Health Connect **in the framework**; Android 9–13 needs the Play
Store app. `HealthConnectClient.getSdkStatus()` abstracts both — never branch on
`Build.VERSION` directly.

`minSdk` stays **26**. It does not need to rise; the feature simply reports unavailable on
26–27.

### 6.3 Sync model

**Direction: read-only. Daybook never writes to Health Connect.** Writing steps or heart
rate back would be nonsense (Daybook does not measure them) and would risk creating feedback
loops with Mi Fitness. The one arguable write — publishing a Daybook gym session as an
`ExerciseSessionRecord` — is **deferred** (§6.4), and would need its own `WRITE_EXERCISE`
permission and its own sign-off.

**Cadence (recommended):**

1. **On app resume** — `MainActivity.onResume` triggers a pull, **throttled to at most once
   every 15 minutes** via a timestamp in the health state store. Cheap, and it is what makes
   the feature feel live.
2. **Once a day in the background** — a new `HealthPullWorker` (`@HiltWorker`,
   `CoroutineWorker`, mirroring `WindowRefreshWorker`'s shape exactly). Do **not** invent a
   new periodic cadence: enqueue it as a `PeriodicWorkRequest` with the same constraints
   discipline (no network requirement — Health Connect is local), or fold the call into the
   existing daily `WindowRefreshWorker` if the user prefers one fewer job. **Recommended:
   fold into `WindowRefreshWorker`** — one fewer scheduled job is one fewer battery
   line-item, and this project has a battery-regression history (C7).
3. **Delta reads via the changes API.** After the first full pull, store a **changes token**
   and use `getChanges(token)` so subsequent pulls process only what actually changed. If
   the token has expired (`changesTokenExpired`), fall back to a full re-read of the last
   30 days and mint a new token. This is what keeps the read cheap enough to run daily.
   **REV3 (C9) — the expiry fallback is silent, and that is correct, but it must not be
   invisible.** A token expiring is **normal** (it happens after ~30 days of not syncing, or
   when the provider is updated) and interrupting the user about it would be noise. So: the
   fallback runs automatically, **and** `HealthSyncStateStore` records
   `health_last_full_resync_at`, which Settings → Health surfaces as a plain line
   **`Rebuilt your health history <relative time>.`** — shown only when it happened within the
   last 24 hours. The distinction C9 draws: *quiet* is fine, *unknowable* is not.
   **If the full re-read itself then fails**, that is a real failure and takes the "A read
   threw" row of §6.2's ladder — **`Couldn't refresh your health data. Last updated <relative
   time>.`** The token must **not** be cleared in that case, or every subsequent pull
   re-attempts a full 30-day read forever (a silent battery regression, C7).
4. **No `registerForDataNotifications` push subscription.** It exists in principle but is
   provider-dependent and would mean waking the app on someone else's write cadence —
   directly against C7. Poll-on-resume + daily is the right shape for a journal app.
5. **A manual "Refresh now" row** in Settings → Health, for when the user just synced their
   band and wants to see it immediately.
   **REV3 (C9) — "Refresh now" is user-triggered, so it must report both outcomes.** Row label
   swaps to **`Refreshing…`** and disables while it runs. On success, the status line updates to
   **`Last updated just now.`** On failure, **`Couldn't refresh your health data. Check that
   Health Connect is still installed, then try again.`** in `DaybookColors.Danger`. **On success
   with nothing new — which is the common case — say so rather than looking inert:
   `Up to date — nothing new from your band.`** A button that visibly does nothing is
   indistinguishable from a broken one.

**Background-read permission:** the daily worker reads while the app is backgrounded, which
on newer Health Connect providers requires
`android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND`. Request it as an **optional
extra**. If refused or unsupported: drop to on-resume-only reads and say so in Settings —
**exact copy: `Daybook will update your health data when you open the app.`** — recording
`health_bg_permission_denied` so the request is **not** repeated. Never nag.
**REV3 (C9):** if the *request itself* throws (§5.1 R4: the alpha08 client rejecting an unknown
permission string), that is **not** the same as a refusal and must not be recorded as one. Show
**`Couldn't ask for background access on this phone. Daybook will update when you open the
app.`**, `Log.e` the throwable, and treat it as unsupported-not-denied — because a refusal is the
user's decision and a throw is our bug, and conflating them would hide R4 exactly where it needs
to be visible.

**REV3 (C9) — "Import my past data" (§6.3 history window) is user-triggered and needs the same
treatment.** Row label **`Import my past data`** → **`Importing…`** while running. Success:
**`Imported your health history back to <date>.`** (`<date>` as `d MMM yyyy`). Permission
refused: **`Daybook can only see the last 30 days without access to your history.`** Failed:
**`Couldn't import your past data. Try again.`** Nothing found: **`No older health data was
available.`**

**History window:** without `READ_HEALTH_DATA_HISTORY`, Health Connect only serves the
**30 days preceding the permission grant**; reading a single older record **errors**. So:

- default first pull = **last 30 days**;
- Settings → Health gets an **"Import my past data"** button that requests the history
  permission and, if granted, backfills further (recommend a 365-day cap — a longer window
  means more month docs hydrated and pushed, and older data the band has often does not have
  anyway);
- important caveat to surface in the UI: **uninstalling Daybook revokes the history
  permission, and reinstalling resets the 30-day window from the new grant date.** The
  data already pulled into Daybook's own Room DB survives via the account sync — which is a
  real argument in favour of §9 Q5 answering "yes, sync it".

**State storage:** a new `data/health/HealthSyncStateStore.kt`, `@Singleton`, backed by the
**same `daybook_prefs` SharedPreferences file** `SyncStateStore` uses. Keys:
`health_changes_token`, `health_last_pull_at`, `health_backfill_through` (earliest date
pulled), `health_bg_permission_denied`. **It must not be a Room table** — a Room write here
would re-trigger the `InvalidationTracker` observer and mark a cloud push pending on every
poll, which is precisely the feedback loop `SyncStateStore`'s KDoc warns about.

### 6.4 Relationship to Round A's Workout mode — DEFERRED, deliberately

**Decision: a workout recorded by the band (an `ExerciseSessionRecord`) does NOT appear in
Workout mode. It appears in the Health surface only.** Workout mode shows only
sessions the user logged by hand. *(REV4: wording only — "the Workout tab" throughout this
subsection now means "Workout / Beast Mode", the full-screen mode of §3.6. The decision and
every reason for it are unchanged, and if anything the separation is now cleaner: band data
lives on Today and the Health screen, hand-logged sets live behind the long-press, and the two
surfaces no longer sit next to each other in one nav bar inviting the comparison.)*

Reasoning, in the terms that matter:

- **Double-counting is the default failure.** Log a gym session in Daybook *and* wear the
  band, and you now have two records of one workout. Merging them needs overlap-window
  dedupe heuristics that are wrong often enough to be annoying.
- **An imported session is not editable the same way.** It has no sets, reps or weights —
  the band does not know them. Putting it in Workout mode means inventing a read-only
  session state, an "imported" badge, a different detail screen, and a rule for what happens
  when the user edits one.
- **Ownership and sync get murky.** A Health-Connect-derived session is derived data that can
  be re-derived; a hand-logged one cannot. Storing them in the same table and syncing them
  identically means a deleted band workout can resurrect from the cloud.

Keeping them separate is honest: *"this is what you logged"* vs *"this is what your band
saw"*. The `workout_sessions.source` column (§3.2) exists so that if the user later wants an
"import this band workout as a workout entry" **button** — an explicit, one-tap, user-driven
action, not automatic merging — it is a UI change and not a migration.

## 7. Round B technical breakdown

### 7.1 New Room entities (`data/model/HealthModel.kt` — new file)

```
@Serializable @Entity(tableName = "health_days")
data class HealthDay(
    @PrimaryKey localDate: String        // "yyyy-MM-dd" — one row per local calendar day
    steps: Int? = null
    distanceMeters: Float? = null
    activeCalories: Float? = null
    totalCalories: Float? = null
    restingHeartRate: Int? = null
    avgHeartRate: Int? = null
    minHeartRate: Int? = null
    maxHeartRate: Int? = null
    sleepMinutes: Int? = null
    sleepDeepMinutes: Int? = null
    sleepLightMinutes: Int? = null
    sleepRemMinutes: Int? = null
    sleepAwakeMinutes: Int? = null
    sleepStartMillis: Long? = null
    sleepEndMillis: Long? = null
    // ---- REVISION 3: the four record types promoted to MVP in §6.1 ----
    spo2Percent: Float? = null           // OxygenSaturationRecord, daily average (§6.1.2)
    weightKg: Float? = null              // WeightRecord, the day's last reading (§6.1.4).
                                         // ALWAYS kg — the same storage rule as WorkoutSet.weightKg
                                         // (§3.2), so one display conversion serves both features.
    hydrationMl: Float? = null           // HydrationRecord, daily total (§6.1.6)
    nutritionCalories: Float? = null     // NutritionRecord, daily totals (§6.1.6). Aggregates ONLY:
    nutritionProteinGrams: Float? = null //  per-meal rows are deliberately NOT stored, because they
    nutritionCarbsGrams: Float? = null   //  would compete with food_med_occurrences for the same
    nutritionFatGrams: Float? = null     //  conceptual space. See §6.1.6's decision.
    nutritionSourceApp: String? = null   // metadata.dataOrigin.packageName of whoever wrote the
                                         // nutrition rows, for the "From MyFitnessPal" subtitle.
                                         // Null when the day has no nutrition data.
    updatedAt: Long
)
```

Every metric is **nullable**, and nullable means "no data", which is distinct from zero.
A day with 0 steps recorded is not the same as a day the band was on the charger. *(REV3: this is
the same rule as **Ri3** in §2.2.2, for the same reason, and it applies to all nine new columns
above — none of them may ever acquire a `NOT NULL DEFAULT 0`.)*

*(REV3 — C4/C8 note: the nine new columns land in the **same** `MIGRATION_22_23` as the rest of
Round B (§7.2). One round, one migration, unchanged. They are specified here rather than deferred
to a later round because adding them afterwards would cost a second migration for no benefit.)*

```
@Serializable @Entity(tableName = "health_sessions",
    indices = [Index("local_date"), Index("start_millis")])
data class HealthSession(
    @PrimaryKey id: String               // Health Connect Record.metadata.id — stable, so a
                                         // re-read upserts rather than duplicating
    localDate: String
    exerciseType: Int                    // ExerciseSessionRecord.EXERCISE_TYPE_* constant
    title: String? = null
    startMillis: Long
    endMillis: Long
    durationMinutes: Int
    activeCalories: Float? = null
    distanceMeters: Float? = null
    avgHeartRate: Int? = null
    sourceApp: String? = null            // metadata.dataOrigin.packageName, e.g. "com.mi.health"
    updatedAt: Long
)
```

`exerciseType` is stored as the raw int and mapped to a display label by a pure
`ExerciseTypeLabels` map (unit-tested), so an unknown/future type renders as "Workout"
instead of crashing.

`ui/health/` also needs a distance/duration formatter shared with Round A's set rows — put
it in `util/` so both use one implementation.

### 7.2 `MIGRATION_22_23` (NEEDS USER SIGN-OFF, C4)

Additive: `CREATE TABLE health_days`, `CREATE TABLE health_sessions` + its two indices,
and (if §9 Q4 says "a card on Today") one device-local
`ALTER TABLE app_settings ADD COLUMN health_card_enabled INTEGER NOT NULL DEFAULT 1`.
`AppDatabase.version` 22 → 23, two entities + two DAO accessors, registered in
`DatabaseModule`, `23.json` exported, `MigrationTest.migrate22To23` added.

### 7.3 New files

| File | Role |
|---|---|
| `data/health/HealthConnectAvailability.kt` | `getSdkStatus` wrapper + pure state→UI mapping |
| `data/health/HealthPermissions.kt` | the permission string set, the optional extras, and a pure `missingPermissions(granted, required)` helper |
| `data/health/HealthConnectReader.kt` | all `HealthConnectClient` calls: aggregates, session reads, changes token. **The only file that imports `androidx.health.connect.*`.** |
| `data/health/HealthSyncStateStore.kt` | SharedPreferences token/cursor store (§6.3) |
| `data/HealthRepository.kt` | orchestration: pull → map → upsert into Room; `@Singleton`, Hilt-provided in `DatabaseModule` |
| `data/local/HealthDao.kt` | day + session queries, range reads for export, range deletes for eviction |
| `util/work/HealthPullWorker.kt` | *(only if §9 Q2 says a separate job; otherwise this is a call inside `WindowRefreshWorker`)* |
| `ui/health/HealthPermissionsRationaleActivity.kt` | the required manifest activity + privacy copy |
| `ui/health/HealthScreen.kt` + `HealthViewModel.kt` | the full health view (stacked route `"health"`) |
| `ui/health/HealthSummaryCard.kt` | the compact Today card |
| `ui/settings/HealthSettingsScreen.kt` | connect / disconnect / refresh now / import past data / which types / status |

**Isolation rule:** every Health Connect type stays behind `HealthConnectReader` and
`HealthConnectAvailability`. Nothing in `ui/`, `HealthRepository` or the Room layer imports
`androidx.health.connect.*`. If Option A's alpha pin later moves to 1.1.0 stable (§5.1), that
is then a two-file change.

### 7.4 UI placement

**Recommendation: a summary card on Today + a stacked "Health" screen behind it. NOT a
new bottom-nav tab.** *(REV4: was "not a **fifth** tab" — after §3.6 the nav still has three
items when Round B starts, so a Health tab would be the fourth. The recommendation is
unchanged; see the bullet below for why the newly-free slot changes nothing.)*

- Today already groups its content; the health card is one more `SoftCard`. **REV3 — placement
  decided, was "implementer places it":** it goes **below the last reminder group and above the
  bottom clearance spacer**, i.e. the final item in Today's `LazyColumn`. Reason: Today's purpose
  is *what you still have to do*; health data is *what already happened*, so it must never push a
  pending reminder down the screen. `GroupHomeItemsTest`'s existing grouping is **not** modified —
  the card is appended after the grouped items, not inserted into them, so that test's
  expectations stay valid (C5/R7).
  **REV4 — one ordering clarification now that Round A may also append to Today:** if §9 Q32 is
  answered yes, Round A puts a `Start workout` row in this same appended region (§3.6.4). The
  order is **grouped reminders → Workout row → Health card → clearance spacer**: the workout row
  is an action still available today, the health card is a read-out of what already happened. If
  Q32 is answered no, nothing changes here at all.
- **Exact card content:** title row **`Health`**, then one compact stats row of at most **three**
  figures in this priority order, skipping any that are null: **steps**, **sleep** (as `7h 12m`),
  **workouts** (as `2 workouts` / `1 workout`). Three is the limit because the row must not wrap
  at 360 dp (R6's clipping precedent). Everything else lives on the Health screen.
- **Exact empty/permission copy on the card: the card renders nothing at all** when Health
  Connect is unavailable or ungranted — it does not render an empty shell with a "Connect"
  prompt, because Today is not a settings screen. Discovery happens in Settings → Health, and
  §6.2's ladder guarantees that screen always explains itself.
- Tapping it opens `"health"`, a stacked destination with the existing `BackHeader`: a
  `WeekStrip`-driven day picker (the component already exists and is already used on Today),
  the full day's metrics, and the band's workout sessions for that day.
- **A fourth bottom tab is one too many** for the pill nav at 360dp, and health data is
  something you glance at, not a place you live. *(REV4 — this reason needed restating, because
  the old one ("five tabs is too many; Round A already spends the fourth slot on Workout") no
  longer holds: Round A **does not** take a tab slot any more (§3.6), so the nav will still have
  three items when Round B starts. The conclusion is unchanged anyway — the argument for the
  Today card was never "there is no room", it was "health data is a glance, not a destination",
  and a tab implies a destination. **The free fourth slot must not be read as an invitation.**
  Note also that Round A has now established a second pattern for a surface that is not a tab:
  if Health ever outgrows the card, the precedent is §3.6's stacked mode, not a nav item.)*
- The card is hideable via `app_settings.health_card_enabled` so a user who does not wear a
  band never sees it.

### 7.5 Sync integration for health data

Same mechanics as §4, so only the deltas are listed:

- `DATA_TABLES` gains `"health_days"`, `"health_sessions"`; `DataTablesSyncTest` updated.
- `BackupModel.DayEntry` gains **one** optional field:
  ```kotlin
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val health: HealthDayLog? = null        // null == this day has no health data
  ```
  with `HealthDayLog` carrying the day metrics plus `sessions: List<HealthSessionLog>`.
  Being **nullable**, `explicitNulls = false` omits it automatically — the hash-neutrality
  guarantee of §4.2 holds identically. New `HealthDayHashTest` proves it.
- `ExportImportRepository`: the same six call sites as §4.4. **`evictMonth` again is the
  trap** — it must delete that month's `health_days` and `health_sessions` rows, or evicted
  months re-push forever.
- `Definitions` is **not** touched — health data has no definitions.

**Why sync health data at all** (the case for §9 Q5 = yes): the Mi Band pairs with one
phone, so a second device would otherwise show an empty Health section; Health Connect's own
history is bounded and resets on reinstall; and the user's health context sitting next to
their journal is the whole point of putting it in Daybook rather than opening Mi Fitness.

**Why one might say no:** it is derived data, it is the larger of the two payload additions,
and it is the one that carries the §4.3 mixed-version hazard for data the user cannot easily
re-enter. Saying no means health data is device-local (like every `app_settings` column since
v16), the Firestore payload never changes, and Round B needs no payload sign-off at all.
That is a legitimate, safer answer.

### 7.6 Round B phase list

| Phase | Work | Gate |
|---|---|---|
| B0 | Add the pinned dependency (§5.1 Option A) + **the 12 manifest permissions of §6.1.8**, `<queries>`, rationale activity. **Build and install on the real phone before writing any feature code** — confirm the Guava/R8 interaction and the APK size. **REV3 — first task of this phase is the API-surface compile check (§5.1):** reference `HealthConnectFeatures`, `PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND`, `PERMISSION_READ_HEALTH_DATA_HISTORY`, and **every one of the 12 MVP record classes**, and record which resolve. The plan's §6.1 availability claims are evidence-based but not compiler-verified; this settles them in one build. | `assembleRelease` green, app launches, **a written list of which symbols resolved** |
| B1 | `HealthConnectAvailability`, `HealthPermissions`, rationale activity + privacy screen, Settings → Health with the Connect button. **Verify on-device that the OS consent sheet appears and that the two optional permission strings are accepted (§5.1).** | permission grant works end-to-end |
| B2 | `HealthModel.kt`, `HealthDao`, `MIGRATION_22_23`, `AppDatabase` v23, DI, `23.json`, `MigrationTest.migrate22To23` | tests green |
| B3 | `HealthConnectReader` (aggregates + sessions + changes token), `HealthSyncStateStore`, `HealthRepository`; pure unit tests for the record→entity mappers and the changes-token fallback decision | tests green |
| B4 | Cadence: on-resume throttled pull + the daily call (folded into `WindowRefreshWorker` per §6.3) + "Refresh now" + "Import my past data" | manual pass with the actual band |
| B5 | Sync: `DayEntry.health`, `DATA_TABLES`, six `ExportImportRepository` call sites, `HealthDayHashTest`, eviction | tests green — **before any UI** |
| B6 | `HealthSummaryCard` on Today (§7.4's exact placement and three-stat rule), `HealthScreen` **including the separate `Nutrition` / "From …" card of §6.1.6**, `HealthSettingsScreen`, **every row of §6.2's ladder including the four REV3 failure branches, with its exact copy**, and §6.3's Refresh-now / Import-past-data outcome strings | manual pass, including **with Health Connect uninstalled, with permissions fully denied, and with permissions PARTIALLY granted** — the last is the most likely real-world state and the easiest to leave untested |
| B7 | Full `./gradlew test` + `assembleRelease`; signed APK | all tests green |

---

# ROUND C — Universal Activity Tracker *(BACKLOG — NOT STARTED, NOT SCHEDULED)*

> **Status: this is a backlog map, not a plan. Nothing here is scheduled, estimated, or
> approved. No part of Round C may be built as part of Round 0, A or B.** It exists because the
> external PRD is a legitimate product vision that deserves recording rather than discarding,
> and because §2.2's fork (Q22) cannot be put to the user honestly without showing what the
> other road looks like.
>
> **Sequencing: strictly after Round A and Round B.** Round A must be shipped, used for real
> workouts, and found wanting before any of this is worth building.
>
> Sections are lettered (C.1, C.2 …) deliberately, so §8/§9/§10 keep their existing numbers (R7).

## C.1 What Round C is

The PRD's central claim, and it is a good one: **strength training, yoga, running, sports drills
and arbitrary custom activities are the same underlying model at different settings** —

```
ACTIVITY → ROUTINE / PROGRAM → WORKOUT → PERFORMANCE / PROGRESS
```

Round A implements the third and fourth boxes for one activity type (gym). Round C generalises
the first two and widens the third.

## C.2 The schema shift, and what it costs

Round A's concrete tables become a generic core. The PRD's §10 shape, mapped onto Daybook's
actual naming conventions:

| PRD entity | Daybook table | Relationship to Round A |
|---|---|---|
| `activity` | `activities` | **`exercises` renamed and widened** — gains `category` (Strength/Cardio/Yoga/Mobility/Sport/Combat/Skill/Recovery/Custom), `aliases`, `instructions`, `tags` |
| `activity_metric` | `activity_metrics` | **NEW.** One row per metric an activity tracks. Replaces `trackingMode` per §2.2.2's migration function |
| `workout` | `workout_sessions` | **unchanged** |
| `workout_block` | `workout_exercises` | **unchanged** — already the right shape (§2.2.2) |
| `workout_item` | `workout_sets` **minus its value columns** | the row survives; `reps`/`weight_kg`/`duration_seconds`/`distance_meters`/`rpe` move out |
| `metric_value` | `metric_values` | **NEW.** One row per (item, metric) pair |

**The honest cost, so this is never proposed as a small change:**

- **Two Room migrations, at least**, and the second one is **not additive** — it moves data out
  of `workout_sets`' columns into `metric_values` rows. C2 says every migration is additive, so
  **Round C requires an explicit, separate amendment to C2** or a strategy that leaves the old
  columns in place, unused, forever. Neither is free.
- **The wire model (§4.2) changes shape**, and the hash-neutrality guarantee has to be re-proved
  for the new shape against every existing user's cloud data (C3). This is the single riskiest
  part of Round C and it is riskier than anything in Rounds 0/A/B.
- **Every query in §3.4 is rewritten.** `bestSetForExercise`'s `ORDER BY weight_kg DESC` becomes
  a join-and-pivot over `metric_values`, and the "which metric is this activity's PR metric"
  question becomes data rather than code.
- **The live session UI becomes dynamic.** §3.7.1's `columnsFor(trackingMode)` — four cases,
  unit-testable — becomes "render an editor per metric definition, typed at runtime".

## C.3 What Round C would add, in PRD priority order

**C.3.1 Custom activities and custom metrics** *(PRD §11–14, §17)* — the five-screen
"+ Create Activity" builder: name → category → what to track (including "create custom metric")
→ record rules → progression rules. This is the feature that makes the PRD's claim true, and it
is **the reason to do Round C at all**. Without it, Round C is a schema change with no user-visible
payoff.

**C.3.2 Routines** *(PRD §18–19)* — reusable ordered collections of activities with per-activity
target sets/reps/rest. This is revision 2's deferred **R2**, unchanged, now correctly placed:
it belongs with Programs, not bolted onto Round A. Needs its own tables (`routines`,
`routine_items`), its own editor with drag-reorder, and a "start from routine" path that
pre-populates a session.

**C.3.3 Programs, periodization, deloads** *(PRD §20, §33–34)* — routines arranged over weeks.
Includes the PRD's good instinct at §34: **never silently manipulate someone's program — always
show the reason.**

**C.3.4 Progression engines** *(PRD §31–32)* — double progression, RPE/RIR, percentage-based,
and the generalisation that progression is not always "more weight" (plank → longer, running →
faster, basketball → more accurate). Depends entirely on C.3.1's metric definitions.

**C.3.5 Substitution** *(PRD §35)* — planned activity vs actual activity vs reason, preserving
historical truth. A good idea; cheap **only after** Routines exist.

**C.3.6 Generalised PRs and analytics** *(PRD §36–37)* — Round A's `isPersonalRecord` becomes
per-metric with a per-activity rule (highest / lowest / fastest / longest / best percentage).

**C.3.7 Training load and the fatigue dashboard** *(PRD §38–39)* — **and the PRD's best single
piece of UX advice, which should be adopted verbatim if this is ever built:** never show a
mysterious "Fitness score: 73"; say *"Training load increased 24% from your previous 4-week
average."* And never say "you are overtrained" — that is an unjustified medical claim.
**Note the dependency: a fatigue dashboard is the one feature that would justify Round B
collecting `HeartRateVariabilityRmssdRecord` (§6.1.2).**

**C.3.8 Richer streaks and rest-day intelligence** *(PRD §24–25, §59)* — multiple streak models
(training-day, weekly-goal, per-activity, distance-goal), and the rule that **a planned rest day
must not break a consistency streak**. **Daybook already does the hard part correctly**: its
streaks are derived from stored occurrence rows, never from a cached `current_streak` integer —
which is exactly what PRD §59 prescribes. So this is a UI and rules widening, not a rewrite.

**C.3.9 Charts** — revision 2's deferred item from §3.1.2 R3 / Q16. Naturally lands here, since
a generic metrics engine needs generic charting anyway.

## C.4 What stays rejected even in Round C

- **Everything in §2.1** — no backend, no Postgres, no mutation queue. Round C changes the
  *schema*, not the *sync architecture*. All of it still rides Room + Firestore.
- **Social features** (PRD §61) — the PRD excludes them and so does this plan. No feed, no
  followers, no leaderboards, ever.
- **AI coaching / AI-generated programs** (PRD §3 P2, §67 Phase 8) — the PRD itself says last,
  and this plan says not at all without a separate conversation.
- **Wearable/Wear OS app** (PRD §50) — a second app module and a second distribution channel.
- **A bundled media library of exercise GIFs** (PRD §15, §51) — already rejected in §3.1.2 on
  licensing and APK-size grounds, and Round C does not change that argument.
- **GPS route tracking** (PRD §49) — see §3.10 P8. Would need a maps dependency and a location
  permission; a separate decision, not a Round C freebie.

## C.5 The one Round C idea worth stealing early

**PRD §29's four-level notes system** — activity note (always visible, e.g. *"Seat 6. Neutral
grip."*), routine note, session note, set note. Round A already has three of the four
(`Exercise.notes`, `WorkoutSession.notes`, `WorkoutSet.notes`, `WorkoutExercise.notes`).

The PRD identifies the genuinely missing piece: a **pinned, always-visible activity note** that
shows every time you do that exercise. `Exercise.notes` (§3.2) is the right column and **already
exists in Round A's schema** — it is simply not rendered anywhere. **Displaying it on the live
session's exercise card is a ~10-line change with no schema cost**, and it is the single
highest-value-per-line idea in the whole PRD.

**Recommendation: pull this one item forward into Round A**, as an addition to §3.7.1's
exercise-card row: if `Exercise.notes` is non-blank, render it under the exercise name in
`DaybookText.Metadata` / `DaybookColors.TextMuted`, above the per-block `Add notes here…` field.
The two are different things and must be visually distinct: the pinned note is *about the
exercise, forever*; the block note is *about today*. **§9 Q30.**

---

## 8. Risk register

| # | Risk | Mitigation |
|---|---|---|
| R1 | A new synced field changes `contentHash` for users with no workout/health data → mass re-push, possible D2 conflict prompt | `@EncodeDefault(NEVER)` / nullable-with-`explicitNulls=false`, plus a dedicated byte-identity unit test per field (§4.2). Non-negotiable. |
| R2 | `evictMonth` forgets the new tables → infinite month re-push or partial cloud overwrite | §4.4 item 6 / §7.5. Add an explicit test that evicting a month with workouts leaves `changedMonths` empty. |
| R3 | An old build on a second device silently strips the new fields | Update every signed-in device in the same sitting (§4.3, §9 Q11). |
| R4 | `connect-client` alpha08 rejects the two optional permission strings | Detect at Phase B1 on-device; fall back to foreground-only + 30-day window. Feature still ships. |
| R5 | Guava (via connect-client) breaks R8 / inflates the APK | Phase B0 builds release **first**, before any feature code. Add ProGuard keeps if needed. |
| ~~R6~~ | ~~Four nav labels clip at 360dp~~ — **WITHDRAWN IN REVISION 4** | There is no fourth nav label. §3.6 keeps the pill nav at three items with the same widths and the same text it ships today, so this risk has no mechanism. *(Kept struck through rather than deleted so a reader of revision 3 can see it was answered, not forgotten.)* |
| R7 | Mi Fitness simply is not writing to Health Connect | First-class empty state with exact instructions (§6.2), not a blank card. |
| R8 | The daily health read becomes a battery line-item | Fold into the existing `WindowRefreshWorker`; use the changes token so the steady-state read is near-empty (C7). |
| R9 | Room identity-hash mismatch from a `DEFAULT` that does not byte-match `@ColumnInfo` | Stated in §3.5. The migration test catches it. |
| R10 | `workout_sets` bulk reads blow SQLite's 999-variable ceiling | Chunk at the existing `SQLITE_MAX_VARS = 900`; `ChunkedDeleteTest` is the precedent. **REV2: `workout_exercises` needs the same treatment — it is a second table read by the same session-id lists.** |
| R11 *(REV2)* | **A Hevy import writes into an evicted past month and the next push overwrites that month's cloud habits/intake with workouts only.** The worst outcome in revision 2, and it is silent. | §3.9.7 — hydrate every touched month via the existing `beginRangeExport()` path **before** writing anything, abort with nothing written if any month can't be reached, and skip the whole step when signed out. Plus a test that importing into a month with habit data leaves it intact. |
| R12 *(REV2)* | `DATA_TABLES` is not updated for the four new tables, so workout edits never mark a push pending and the gym log silently never reaches the account. | §4.1 — **`DataTablesSyncTest` is one-directional and will NOT catch this.** Deliberate step in A4 + a manual "log a set, background the app, confirm a push" check. |
| R13 *(REV2)* | The Hevy name matcher silently merges two different exercises, corrupting PREVIOUS and inventing/destroying personal records with nothing visible to the user. | §3.9.4 — normalise + alias table only, **no fuzzy/edit-distance matching**; unmatched names become visible, renameable custom rows badged "Imported". |
| R14 *(REV2)* | The rest timer creeps back toward a notification / foreground service / exact alarm and re-opens the battery regression. | §3.1.2 R1 and §3.7.2 are explicit: timestamp-derived, `LaunchedEffect`-ticked, dies with the composition. **The forbidden-API list in §3.7.2 is a hard gate — a build that needs any of it must stop and re-ask, not improvise.** |
| R15 *(REV2)* | A CSV date parsed with the device's default locale fails on every row for a non-English phone, on a code path nobody tested. | §3.9.3 — `Locale.ENGLISH` explicitly, ISO-8601 attempted first, unparseable rows skipped and counted; `HevyDateParseTest` includes a French-default-locale case. |
| R16 *(REV3)* | **A future round re-proposes the PRD's Postgres/Supabase/backend-API architecture**, because the PRD is persuasive and §2.1's reasoning has been forgotten. The cost is not a wasted discussion — it is a second sync system half-built alongside a working one. | **§2.1 is written as a standing rejection with named reasons, not a passing remark**, and §2.1.3 lists the specific artefacts (`sync_queue` tables, `operation_id` columns, HTTP clients) that must never appear. Any proposal to revisit it must cite §2.1.2 point by point. |
| R17 *(REV3)* | **Round C's vision leaks into Round A's scope** — a generic metrics engine, Routines or Programs get "just started" mid-round, Round A never ships, and the user gets nothing. The PRD's own §67/§70 warns about exactly this ("don't let one giant generation pass build the whole app"). | Round C is a **separate, lettered, explicitly unscheduled** section with "NOT STARTED, NOT SCHEDULED" in its heading. §2.2 states the fork and §9 Q22 forces the user to choose *before* A1 begins. §2.2.2's **Ri1–Ri3** keep the door open so the choice is deferrable without being foreclosed. |
| R18 *(REV3)* | **Ri3 is violated** — a `NOT NULL DEFAULT 0` is added to a value column (`weight_kg`, `reps`, `duration_seconds`, `distance_meters`, or any of §7.1's nine new health columns) because "a nullable float is annoying in Kotlin". This **silently destroys the difference between zero and no-data**, which breaks §3.9.3's blank-is-not-zero rule, corrupts PR detection and volume totals, and permanently forecloses §2.2's Option 2. | §2.2.2 **Ri3** states it as a rule; §3.2, §3.5 and §7.1 all specify nullable. **Add a migration test that asserts no value column is `NOT NULL`** — this is the one Ri that is cheap to guard automatically, and the one most likely to be broken by an implementer optimising for convenience. |
| R19 *(REV3)* | **A failure is caught, logged and never shown** — the single most likely way this plan degrades in practice, because `runCatching { … }.getOrNull()` is the path of least resistance and the app currently has **no** toast/snackbar habit to fall into. | **C9**, plus the fact that §0 now records exactly two approved patterns with exact component names and line references, and §2.4.3 / §3.9.10a / §6.2 / §6.3 give **exact final copy for every branch** so there is nothing left to invent. **Round-review checklist item: grep the diff for `runCatching` and check every failure branch reaches the UI.** |
| R20 *(REV3)* | **The 12-permission Health Connect consent sheet (§6.1.8) suppresses the grant rate**, and the user ends up with less data than revision 2's 8-permission version would have delivered — the opposite of what "as much as possible" was asking for. | Real, and not fully mitigable. §9 **Q29** puts the split-request alternative to the user explicitly. §6.2's **"Partially granted"** ladder row makes a partial grant a normal, well-rendered state rather than a broken one, so a cautious user who ticks six of twelve still gets a working app. |
| R21 *(REV4)* | **The feature is invisible.** A long-press is not discoverable, the one-time coach-mark is seen once and forgotten, and a user who never performs the gesture concludes Daybook has no workout feature at all — after a whole round was spent building one. This also covers the accessibility case: TalkBack, switch access and motor impairment all make the gesture harder or impossible, and "I forgot" affects everyone eventually. | **This is the single biggest risk revision 4 introduces, and it is not fully mitigable by design alone — §9 Q32 puts it to the user.** Planned mitigations: the coach-mark + icon dot (§3.6.3), the **unconditional** Settings → Workout → `Open Workout` row (§3.8, behind no toggle), the recommended Today row (§3.6.4), and `onLongClickLabel = "Start a workout"` so TalkBack and switch access expose the gesture as a named action rather than a hidden one (§3.6.1). **If Q32 lands on "long-press only", the Settings row still ships** — a feature with literally no visible entry point anywhere is not an option this plan will write. |
| R22 *(REV4)* | **The long-press fires when the user meant to tap**, or the ramp animation flickers on every ordinary tap of Today — turning the most-used nav item into a surprise. | `combinedClickable` uses the platform's own tap/long-press discrimination (§3.6.1), which is the same one every Android app uses, and the nav item is **not** inside a scrollable, so there is no scroll-vs-hold ambiguity to resolve. The ramp's **120 ms dead zone** (§3.6.2) is specifically there so a tap (~60–100 ms) never animates. **Manual check at A5: tap Today twenty times in a row from each of the three tabs and confirm nothing ever flashes or navigates wrongly.** |
| R23 *(REV4)* | **A later round quietly re-adds `"workout"` to `NavConfig.ALL_ROUTES`** — because a tab is the obvious thing to do with a screen, and this decision lives in a document nobody re-reads. | `ui/NavConfigTest.kt` is **left asserting three routes on purpose** (§3.6.0) and becomes the tripwire: re-adding the route turns it red. The failure message should be made to say why — *"Workout is a mode, not a tab — see HEALTH_AND_WORKOUT_PLAN §3.6"* — in the assertion. |

---

## 9. Open questions for the user

These are the decisions a build agent cannot make. Each has a recommendation. Nothing is
implemented until these are answered.

> **Revision 2 numbering.** Q1–Q9 and Q11–Q13 are **untouched** and keep their numbers.
> **Q10 (rest timer) is rewritten in place** — the screenshots showed the user relies on it,
> so the answer flips from "no, defer it" to "yes, but only while the screen is open", and
> rewriting it was clearer than leaving a stale "no" above a contradicting new question.
> **Q14–Q21 are appended.** Nothing was renumbered.
>
> **Revision 4 numbering.** **Q8 and Q11 are rewritten in place** — Q8 asked whether the Workout
> *tab* should appear automatically, and there is no tab any more, so leaving it would leave a
> question with no referent; Q11's accent half said "the Workout tab". **Q4 and Q17 have a
> clause each corrected** for the same reason. **Q32–Q33 are appended.** Nothing was renumbered
> and nothing else was touched.

**Q1 — The health library version.** The officially-supported version of Android's health
library needs Daybook's build tools upgraded, which would touch every other library in the
app (sign-in, fingerprint unlock, cloud sync) with no benefit you'd see.
*Recommendation: use the last version that works with today's setup, and do the build-tool
upgrade later as its own separate job.*

**Q2 — How often should band data refresh?**
*Recommendation: automatically — whenever you open Daybook (at most once every 15 minutes)
and once a day in the background, plus a "Refresh now" button. No new background job; it
rides the daily job Daybook already runs.*

**Q3 — How far back should Daybook pull on first connect?** Android only lets apps see the
last 30 days unless you grant an extra "history" permission.
*Recommendation: last 30 days automatically, with an "Import my past data" button that asks
for the extra permission and pulls up to a year.*

**Q4 — Where should band data appear?**
*Recommendation: a small summary card on Today (steps, sleep, workouts), tapping it opens a
full Health page. Not a new bottom tab — the bottom bar stays at three (Today / Habits /
Intake), and band data is something you glance at rather than a place you live.*
*(Reworded in revision 4: this used to say "with Workout added that would be five". Workout no
longer takes a tab slot, but the recommendation is unchanged — the reason was never lack of
room.)*

**Q5 — Should band data be saved to your account and shared across your devices?**
*Recommendation: yes. Your band only pairs with one phone, Android's own health store forgets
old data, and it means your step/sleep history survives a phone change. This does change the
shape of what Daybook stores in the cloud, so it needs the sign-off in Q11.*
*(Saying no is safe and cheaper: band data would just live on the one phone.)*

**Q6 — Should Daybook ever send data back to your band's health store?**
*Recommendation: no. Daybook only reads. It doesn't measure steps or heart rate, so writing
anything back would be inventing data.*

**Q7 — Should a run your band recorded show up in Workout mode as a gym entry?**
*Recommendation: no, keep them separate — "what you logged" vs "what your band saw".
Merging them means the same workout can appear twice. We'll leave the door open to add an
"add this to my workouts" button later.*

**Q8 — How you get into Workout mode. *(Rewritten in revision 4 — you've already decided this;
this is here to confirm it reads back the way you meant it.)***
You said Workout should **not** be a fourth button in the bottom bar. Instead, you **press and
hold "Today"** in the bottom bar to go into Workout mode ("Beast Mode").
*This is what that now means, exactly:*
- *A normal **tap** on Today still opens Today. Nothing about the bottom bar changes — same
  three buttons, same look.*
- *A **press-and-hold** on Today (about half a second, the standard Android hold) takes you into
  Workout mode. While you're holding, the Today icon **grows and lights up in colour**, finishing
  right as it triggers, and you get a small **buzz** — so it never feels like nothing's
  happening.*
- *Workout mode is **full screen with no bottom bar**. It's a different mode, not another tab.*
- *You get out with the **×** at the top left, or with the normal back gesture — either way you
  land back on the tab you came from.*
- *The first time you open Daybook after the update, a one-time tip appears above the bottom bar
  saying **"Hold 'Today' to start a workout."** with a **Got it** button, plus a small coloured
  dot on the Today icon that disappears for good once you've used the hold once. (Q33.)*
*Recommendation: yes, exactly as above — **and please also read Q32**, which is the one real
worry with a hold-only shortcut.*

**Q9 — The built-in exercise list.** Daybook will ship with roughly 70 common gym exercises
across Chest, Back, Legs, Shoulders, Arms, Core, Cardio and Full-body, and you can add your
own.
*Recommendation: go with that. Tell me if there's anything you specifically want in there
(a machine at your gym, a particular movement) and I'll make sure it's on the list.*

**Q10 — Rest timer between sets. *(Rewritten in revision 2 — the answer has changed.)***
The Hevy screenshots show a "Rest Timer" line on every exercise, so this is clearly something
you use, and revision 1's "no" was too blunt.
*Recommendation: **yes, with one limit.** Daybook will count your rest down on the workout
screen, per exercise, with the usual presets (30s, 60s, 90s, 2min, 3min, 5min, or off). It
will **not** buzz, ring or send you a notification when the rest is up, and it will not count
down while you're in another app or the phone is locked — come back to the screen and it shows
the right time remaining, because it's working from a finish time, not a running counter.*
*Why the limit: a timer that has to reach you while Daybook is closed needs a permanent
background notification or an alarm, and that is exactly the kind of thing that caused the
battery and heat problem last time. If you want the buzz-when-it's-up version, say so and it
becomes its own small update with its own battery check — not something smuggled into this
round.*

**Q11 — Small stuff, bundled. *(Second half rewritten in revision 4.)*** Weight in **kg** by
default with a kg/lb switch in Settings? And a colour of its own for Workout mode, the way
Habits and Intake each have one?
*Recommendation: yes to both — and for the colour, **Coral** (the app's red-pink) rather than
the default lavender. Now that Workout mode is its own full-screen place with no bottom bar
showing, its colour doesn't have to sit politely next to the other three any more, so it can
actually be the loud one. It's one of the five colours Daybook already has — no new colours are
being invented — and you can change it in Settings whenever you like.*

**Q12 — The two sign-offs this needs.**
1. **Database change.** Both rounds add new storage to Daybook's database. Nothing existing
   is deleted or rewritten — it's purely new tables — but the standing rule is that you say
   yes explicitly first.
2. **Cloud format change.** Workout logs (and band data, if Q5 is yes) get added to what
   Daybook backs up to your account. Existing cloud data is untouched and nothing is
   rewritten. **One caveat worth knowing:** if you're signed in on more than one phone,
   update them all in the same sitting — a phone still on the old version would quietly drop
   the new data when it next saves.
*Recommendation: yes to both, with the "update all your devices" caveat noted.*

**Q13 — Order of work.**
*Recommendation: Workout mode first (no new permissions, no new libraries, can be built and
tested entirely without your band), then band sync.*

---

### Added in revision 2 (from the Hevy screenshots and the CSV export)

**Q14 — The exercise pictures.** Hevy shows a little drawing of each exercise, and a body
diagram with the muscle coloured in, in its filter list and on its summary. Daybook can't
copy those — they're drawn artwork that comes with the app, and roughly ninety of them would
also make Daybook noticeably bigger to download.
*Recommendation: use Daybook's existing coloured-circle icons instead, the same style as your
Habits and Intake rows. Everything else on those screens — the search, the two filters, the
muscle name under each exercise, the Recent list — works exactly as in the screenshots. If
having the pictures really matters, that's a separate conversation about licensing artwork,
not something to solve inside this round.*

**Q15 — How exercises get filed.** The screenshots file each exercise under a specific muscle
("Upper Back", "Triceps", "Quadriceps") **and** separately by equipment ("Barbell", "Cable",
"Dumbbell") — two filters, not one.
*Recommendation: match that. About 20 muscle groups and about 9 equipment types, and when you
create your own exercise you pick one of each. It's two taps more when creating an exercise,
and it's the only way the two filter buttons actually do anything.*

**Q16 — The little trend button next to each exercise.** In Hevy this opens that exercise's
progress.
*Recommendation: in this round it opens a **list** — every time you've done that exercise,
newest first, with your best set marked. The **graph** comes later. A proper chart means
building axes, gridlines and a whole visual style Daybook doesn't have yet, and it's worth
doing properly rather than rushing it into this round. The list uses data we're already
storing, so the graph can be added on top later without changing anything underneath.*

**Q17 — Where the "Import from Hevy" button lives.**
*Recommendation: two places, both leading to the same thing — in **Settings → Backup & data**,
right next to the backup import you already have, and as a button on the **empty Workout
screen**, since a brand-new empty workout list is exactly when you'll want it. It opens your
phone's normal file picker, the same as importing a Daybook backup does.*
*(Reworded in revision 4: "the empty Workout tab" → "the empty Workout screen". Same button,
same place inside Workout mode.)*

**Q18 — What happens to a Hevy exercise Daybook doesn't recognise.** Hevy names things like
"Seated Cable Row - V Grip (Cable)". Daybook will match most of them to its own list, but not
all.
*Recommendation: anything it can't match gets **added as your own custom exercise, keeping
Hevy's exact name**, marked "Imported" so you can spot them. You can rename them or file them
properly afterwards. Deliberately **not** doing clever almost-matching: something that's
willing to guess "Bicep Curl (Dumbbell)" is "Dumbbell Curl" is also willing to guess "Incline
Bench Press" is "Decline Bench Press", and a wrong guess quietly mixes two exercises' histories
together and fakes or wipes out your personal records. An extra exercise in the list is
obvious and fixable; a bad merge isn't.*

**Q19 — Supersets from Hevy.** Hevy's export records which exercises you did as a superset.
Daybook won't show supersets in this round.
*Recommendation: **store the grouping anyway, invisibly.** It costs nothing now, and it means
if supersets get added later your imported history already has them. The alternative is
throwing the information away permanently — you'd have to still have Hevy installed to get it
back.*

**Q20 — Importing the same file twice.**
*Recommendation: Daybook matches on a workout's exact start and end time. Anything already
there — whether you imported it before or logged it by hand — is **skipped, not merged**, and
the summary tells you how many ("Imported 4 workouts, 33 sets and 9 new exercises. Skipped 2
already in Daybook."). No "close enough" time matching: a workout wrongly treated as a
duplicate would just silently never appear, which is worse than an occasional double you can
delete.*

**Q21 — One thing to know about importing old workouts.** If you're signed in, Daybook doesn't
keep every past month on the phone — old months live in your account and get fetched when
needed. So importing workouts from months ago needs an internet connection for a moment first.
*Recommendation: Daybook fetches those months before importing (with the same progress bar the
date-range export already shows), and if it can't reach one it **stops and imports nothing**
rather than importing half. Signed out, none of this applies and it just imports straight away.
No action needed from you — this is here so the behaviour isn't a surprise.*

**Note on sign-offs:** the Hevy import needs **no extra database or cloud sign-off beyond
Q12**. It writes into the same new tables Workout mode already creates, using the same cloud
format. It is not a second database change.

---

### Added in revision 3 (from the pasted PRD, the app comparison table, and the intake bug report)

> **Numbering.** Q1–Q21 are **untouched** and keep their numbers and their answers.
> **Q22–Q31 are appended.** Nothing was renumbered or rewritten.
>
> **One question from the previous revision has been withdrawn rather than asked:** whether to
> build the intake fix on top of the uncommitted UX-refinement work, or commit that first.
> **It no longer applies** — that round is committed (`5f536d4`) and the working tree is clean
> (§0). There is nothing to sequence around.

**Q22 — THE BIG ONE: how ambitious should the workout feature be?** *(This is the most
consequential question in this document. Everything else is detail.)*

The document you sent describes a tracker that can log **anything** — gym, running, yoga,
football drills, boxing rounds, physio exercises — with you inventing your own activities and
your own things-to-measure ("successful shots", "hold time"), plus training programs that run
over weeks and adjust themselves. It is a genuinely good vision and it is well argued.

It is also a **much bigger** app than what's planned right now, and it would mean redesigning
the workout feature from the ground up before a single line is written — because "anything you
can measure" and "reps, weight, distance, time" are different foundations, not the same one with
more added.

*Recommendation: **build the gym version first (what's already planned), and treat the
everything-tracker as a possible later project.*** Three reasons:
1. Everything you've actually shown me is gym logging — five Hevy screenshots and a Hevy export.
2. The flexible version is considerably harder to build correctly, and this plan is written to be
   handed to a less capable assistant to implement. The realistic risk isn't a slightly worse
   workout tracker — it's **not getting one at all** because the scope kept growing.
3. **It doesn't close the door.** I've checked this specifically rather than just claiming it:
   the way the planned tables store things ("this exercise tracks weight and reps", with every
   value allowed to be empty rather than zero) is already a simplified version of the flexible
   design, and converting later is a mechanical job rather than a rescue operation — **provided
   three specific rules are followed from day one**, which are now written into the plan (§2.2.2).
*If you'd rather commit to the full vision now, say so and I'll re-plan the workout round from
scratch around it — but it should be a decision, not a drift.*

**Q23 — The intake fix: after you reset a past entry, it will say "Missed". Is that right?**
You asked to be able to reset a mistakenly-logged intake item back to not-logged.
Good news: **most of this already exists inside the app** — the reset itself is written, tested
and working; it just isn't reachable from any button for a *logged* item (skipped items can
already be undone). So this is a small wiring fix, not new machinery.

One consequence to confirm: if you reset an entry from **today**, it goes back to normal and you
can log it again. If you reset one from **a past day that's too old to log**, it will show as
**"Missed"** — because it genuinely wasn't logged. It will also **shorten a streak** that entry
was propping up.
*Recommendation: yes, that's correct behaviour and we should keep it. Anything else would mean
inventing a third status ("was logged, then un-logged") that would then have to be explained
everywhere it appears, including in your backups.*

**Q24 — Should the intake fix ship on its own, right now, before anything else?**
It touches four files, changes no data storage, needs no sign-off, and can't affect your existing
data or your cloud backup.
*Recommendation: **yes — ship it immediately as its own small update (version 0.5.7), ahead of
the workout and health rounds.*** You're hitting this bug today; there's no reason to make it
wait behind a multi-week feature.

**Q25 — A performance tool the document calls "mandatory", which I'm recommending we skip for
now.** It's a startup-speed optimisation ("Baseline Profiles"). It genuinely helps, but adding it
requires upgrading Daybook's build tooling — the same upgrade Q1 is already recommending we defer.
*Recommendation: skip it for now and do it as part of the build-tool upgrade later, when the build
is being touched anyway. Adding it during a feature round means two risky things at once.*

**Q26 — Menstrual and reproductive health data: should Daybook touch it at all?**
Android's health store can share period tracking, ovulation tests, and related data. Daybook
**can** read it, and because Daybook is already a symptom-and-food diary there's a real argument
that it would be useful alongside everything else.

But asking for it puts those categories on the permission screen you'll see when connecting — and
one of them is sexual activity, which most people would find surprising coming from a habit app.
*Recommendation: **leave it out by default**, with a middle option available: I can build it
switched off, behind a specific "also connect cycle tracking" row in Settings, so it's only ever
requested if you deliberately go and turn it on. Three answers are fine here: **no** (simplest),
**opt-in row** (recommended if you want it at all), or **yes, include it like everything else**.*

**Q27 — Blood pressure and blood glucose: do you have anything that records them?**
Both are readable. Blood pressure needs a cuff that syncs to your phone; glucose needs a
continuous monitor or manual entry. Neither comes from a Mi Band.
*Recommendation: leave both out unless you own the hardware — an empty stat is worse than no stat.
Worth asking specifically because glucose alongside a food diary is genuinely useful for gut
symptoms, so if you do have a monitor, tell me and I'll include it.*

**Q28 — Food and calorie data from other apps (MyFitnessPal, Samsung Health).**
Daybook already has your own Intake reminders, where you type what you ate. Other apps can share
calories, protein/carbs/fat and water intake.
*Recommendation: **read it, but keep it in a separate card on the Health page labelled "From
MyFitnessPal" — never mixed into your own Intake entries.*** Your Intake notes are yours to edit
forever; the imported numbers belong to the other app and get overwritten every time it syncs. If
they shared a list, the same lunch would show up twice with no sensible way to merge them.

**Q29 — Connecting to your band will now ask for 12 kinds of health data, not 8.**
You asked for as much as possible, so I've added oxygen (SpO₂), weight, water and nutrition to
steps, sleep, heart rate, workouts, calories and distance.
*Recommendation: **ask for all 12 at once.*** The Android permission screen lets you tick them
individually anyway, so splitting it into "essentials now, extras later" mostly just means going
through a system dialog twice. Say the word if you'd rather it asked for six first.

**Q30 — One small idea from your document that I think is worth pulling forward.**
It suggests a **pinned note per exercise** — something like *"Seat position 6, neutral grip"* that
shows up every single time you do that exercise, as opposed to a note about today's session.
*Recommendation: **yes, include it in the workout round.** The storage for it is already in the
plan, so it's roughly a ten-line addition and it's the single most useful small idea in the whole
document. It'll appear in grey under the exercise name, clearly separate from the "notes for
today" box.*

**Q31 — The version numbers.** Daybook is currently build 24 / version 0.5.6.
*Recommendation: intake fix = **25 / 0.5.7**, workout = **26 / 0.6**, band sync = **27 / 0.6.1**.
Just confirm, or tell me different names.*

---

### Added in revision 4 (from your decision about how Workout mode is reached)

> Everything in §3.6 follows from your instruction, so there is nothing to ask about the
> decision itself. These two questions are the consequences of it that I don't think I should
> decide on your behalf.

**Q32 — THE ONE TO ACTUALLY THINK ABOUT: should there also be a visible button, or is
press-and-hold the only way in?**

Press-and-hold is a great shortcut. It is also, by its nature, **invisible** — there is nothing
on screen that says it exists. That matters more than it sounds:

- **You'll know it.** But if you ever hand the phone to someone, or come back to Daybook after a
  few months away, the workout feature is simply gone as far as the screen is concerned.
- **It's the classic accessibility problem.** Anyone using Android's screen reader (TalkBack),
  switch access, or who has a tremor or limited hand strength, finds press-and-hold anywhere from
  awkward to impossible. I'm building the gesture so the screen reader *does* announce it, and so
  Android's "touch and hold delay" setting is respected — but that's mitigation, not a fix.

*So regardless of your answer, I'm putting one small **"Open Workout"** row in Settings that's
always there — that's the floor, and it costs one line.*

**The actual question is whether you also want a visible way in on the Today screen.**

*Recommendation: **yes.** A single row at the bottom of Today — a dumbbell, **"Start workout"**,
tap to go in. Two reasons beyond discoverability:*
1. *When a workout is **half-finished**, that row changes to **"Workout in progress · Tap to
   carry on"**. Without it, a workout you walked away from is invisible until you remember to
   hold Today again.*
2. *It costs one row and can be switched off in Settings if it annoys you — and switching it off
   is safe, because the Settings row above never goes away.*

*The press-and-hold stays either way; it's the fast way in once you know it. **Your options:**
**(a)** hold + Settings row + Today row (recommended), **(b)** hold + Settings row only, or
**(c)** hold only — which I'd push back on, because it means the only way into a whole feature is
a gesture nothing on screen mentions.*

**Q33 — The one-time tip and the little dot: OK, or too much?**
The first time you open Daybook after the update, a small card appears just above the bottom bar:
**"Hold 'Today' to start a workout."** with a **Got it** button. It appears **once, ever**.
Separately, a **small coloured dot** sits on the Today icon until the first time you actually use
the hold — then it's gone permanently.
*Recommendation: keep both. The tip is how you'd ever find out the gesture exists; the dot is the
reminder that survives after you've dismissed the tip but haven't used it yet. Neither can come
back: no counts, no red "unread" badge, nothing you have to keep clearing. If you'd rather have
no dot at all, say so and I'll ship just the tip.*

---

## 10. Notes for the implementing agent

- **Read `§1 constraints` and `§9` answers before touching anything.** If an answer is
  missing, stop and ask — do not guess. **Note that revision 2 rewrote Q10 and added Q14–Q21,
  revision 3 added Q22–Q31, and revision 4 rewrote Q8 and Q11 and added Q32–Q33; an answer set
  collected against revision 1, 2 or 3 is incomplete.**
- **REVISION 4 — read §3.6 before writing a single line of navigation code.** Workout mode is
  **not a tab**. If you are about to touch `NavConfig.ALL_ROUTES`, `NavConfigTest`,
  `app_settings.nav_tabs`, `NavigationSettingsScreen.kt`, the `HorizontalPager`'s `when`, or add
  a fourth `NavItemSpec`, **you are implementing revision 3's withdrawn design** — stop and
  re-read §3.6.0, which lists every one of those as "no change". Workout is reached by a
  **long-press on the Today nav item** (§3.6.1) and lives as a stacked top-level destination
  (§3.6.6). **Q32's answer decides whether the Today row and its `app_settings` column exist at
  all — it gates parts of A1 and A7.**
- **REVISION 3 — read these four things before anything else:**
  1. **§2.1** — the backend architecture rejection. If you are about to add a sync queue, an
     `operation_id`, a Postgres schema or an HTTP client, you have misread this plan.
  2. **§2.2 + Q22** — whether Round A is the concrete schema in §3 or a universal
     Activity/Metric engine. **These produce different code from phase A1 onward.** If Q22 is
     unanswered, stop.
  3. **C9** — no error is silently swallowed. There are exactly **two** approved UI patterns
     (§0) and every failure string in this document is already written out. **You should not
     have to invent a single user-facing sentence.** If you find yourself writing one, that is a
     gap in the plan: stop and ask rather than improvising.
  4. **§2.4 (Round 0)** — a standalone bug fix. **Its data layer already exists**
     (`OccurrenceScheduler.revertFoodMed`); do not write a second one. It is deliberately not
     part of Round A and should not be bundled into it.
- **REV3 — three "already exists, don't rebuild it" traps**, each of which would otherwise cost
  a wasted implementation: `revertFoodMed` / `revertToPending` (§2.4.1); `RespondViewModel.undo()`
  (exists, is unreachable from the intake path — wire it, don't duplicate it); and
  `Exercise.notes` (exists in Round A's schema, simply unrendered — §C.5 / Q30).
- **Phase A4 / B5 (sync) must be green before any UI work in that round.** The UI is the
  easy part; the sync integration is where data is lost.
- **Revision 2's three named traps**, each of which is silent if missed:
  `DATA_TABLES` has no test guarding the *new*-table direction (§4.1 / R12); the Hevy import
  must hydrate evicted months before writing (§3.9.7 / R11); and the rest timer's
  forbidden-API list (§3.7.2 / R14) is a hard stop, not a preference.
- **The Hevy sample export lives at `/home/abhiram/Downloads/workout_data.csv`.** Use the real
  file in `HevyCsvParserTest`, not a hand-written approximation — its mixed quoting, empty
  numeric fields and emoji title are the cases that break naive parsers (§3.9.0).
- Every new pure decision function gets a unit test. That is this repo's culture and the
  reason it has 87 of them.
- Match the existing comment style: every non-obvious line carries a `// <round> (<id>):`
  comment explaining *why*, not *what*. Future rounds read these.
- Follow `HOW_TO_PUSH_UPDATES.md` for the `versionCode` bump so testers' installed apps
  detect the new build. **Do not commit or push** (C1) — hand back a signed release APK named
  in the repo's existing convention.
- Write a `HEALTH_AND_WORKOUT_PROGRESS.md` checkpoint every 2–3 phases, in the style of
  `UX_REFINEMENT_PROGRESS.md`, so the round is resumable.
