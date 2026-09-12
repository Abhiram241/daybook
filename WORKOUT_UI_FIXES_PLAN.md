# Workout ("Beast Mode") UI fixes — plan

Status: **LOCKED — ready for implementation.** Nothing implemented yet. Written after reading the current
implementation (WorkoutSessionScreen.kt, AddExerciseScreen.kt, Navigation.kt/DaybookScaffold,
WorkoutRepository.kt, WorkoutLogic.kt, WorkoutHomeScreen.kt, ExerciseTaxonomy.kt, DaybookIcons.kt).

Scope = the 7 items from the user's message, in the order given. Each section: current state
(verified in code), proposed fix, and open questions (also collected as one numbered list at the
end for the chat reply).

---

## 1. Move "Finish" to bottom-right

**Current**: `WorkoutSessionScreen.kt:98` — `BackHeader(title = "Log Workout", onBack = ...) { PrimaryButton("Finish", ...) }`.
Finish renders as the trailing slot of the top header row, full-height pill button, same row as
the back arrow. No nav bar shows during an active session (`WorkoutRoutes.SESSION` is not in
`WorkoutRoutes.NAV`, so `DaybookScaffold(showNav = false)` — verified in `MainActivity.kt`), so the
bottom of the screen is currently empty scroll space.

**Proposed**: keep the back arrow alone in the top header (title = the workout's name, see #2).
Add a docked "Finish" button pinned to the bottom-right, floating above the content (same
elevation/shadow language as the app's own FAB — `IconButtonSize.Fab` token already exists), NOT
part of the scrolling list. Reuse `PrimaryButton` styling but as a compact pill, not full-width, so
it reads as a floating action rather than a header control.

**Open question**: floating pill (like a FAB, always visible while scrolling, doesn't push
content) vs. anchored to the bottom of the stats card (visible only until you scroll past it)?
Recommend the floating pill — matches how "Finish" behaves in comparable workout-tracker apps and
stays reachable during a long session.

## 2. Default instant-workout name (time-of-day) + its own logo — LOCKED

**Current**: `WorkoutSession.title` (`WorkoutModel.kt:48`) is nullable and already supported
end-to-end — `WorkoutDetailScreen.kt:48` falls back to the local date, and Beast Mode already has a
"Rename workout" feature (`WorkoutHistoryScreen.kt:209`). But `WorkoutRepository.startEmptySession()`
(`WorkoutRepository.kt:145`) never sets `title` — it's always null for a quick-start ("Start an
empty workout") session. Routine-started sessions (`startSessionFromRoutine`) already get a name
(the routine's own name), so this gap is specific to the no-routine "instant" path.
`WorkoutSessionScreen`'s header is also hardcoded to the literal string `"Log Workout"`
(`WorkoutSessionScreen.kt:98`) regardless of the session's actual title.

Icon: there is currently one shared workout icon, `res/drawable/ic_workout.xml`, used everywhere —
Beast Mode's own nav tab, routine cards, and the empty-state icon. There is no separate icon for a
no-routine "instant" session anywhere (history rows, the "Workout in progress" card on the Beast
Mode home screen, or the session header).

**Decided (user: "your recommendation")**:
- `startEmptySession()` sets `title` at creation time, by local device clock:
  - 05:00–11:59 → **"Morning Workout"**
  - 12:00–16:59 → **"Afternoon Workout"**
  - 17:00–20:59 → **"Evening Workout"**
  - 21:00–04:59 → **"Night Workout"**
  Stays user-renameable afterwards (existing "Rename workout" feature, untouched).
- `WorkoutSessionScreen`'s header shows the session's actual title instead of the hardcoded "Log
  Workout".
- New icon: `DaybookIcons.Bolt` — a lightning-bolt glyph (the standard Material "flash/bolt"
  silhouette, redrawn as a single filled path at the same 24dp viewport convention every other
  `DaybookIcons` entry uses, so it matches the app's existing flat-icon style exactly — not
  imported from the material-icons-extended library, which was deliberately dropped for APK-size
  reasons). Used specifically for no-routine/instant sessions: the "Workout in progress" card on
  the Beast Mode home screen, its row in History, and the session header icon (if any). Routine-
  started sessions keep the current `ic_workout` dumbbell icon, so the two are visually distinct
  at a glance.

## 3. Workout mode's bottom nav — polish to match the main app's page-switching feel — LOCKED (bigger than originally scoped)

**Confirmed no visual glitch.** User clarified: the ask is to bring the same polish they gave
feedback on for the main app's nav — animations, page-to-page movement, styling — into Beast
Mode's nav, not to fix a rendering bug.

**The actual gap, found by comparing the two mechanisms directly:**

The main app's 3 tabs (Today/Habits/Intake) live as pages of one `HorizontalPager`
(`MainActivity.kt`, the `"main"` composable). This is what all that prior nav-feedback (Round 10
"pager lag" fix, v0.5.1 backlog item 2) was polishing:
- physically **swipeable** between tabs (drag follows your finger, directional slide);
- tapping a nav item calls `goToPage(index)`, which **snaps instantly with no animation** when
  jumping more than one tab over (e.g. Today→Intake) and only **animates for an adjacent tab**
  (e.g. Today→Habits) — this is the specific fix for the lag/jank the user reported before;
- the 3 tab ViewModels co-instantiate under the one `"main"` back-stack entry, so switching is
  instant with no cold-compose stutter.

Beast Mode's 3 tabs (Routines/History/Exercises, `WorkoutRoutes.NAV`) are **not** pager pages at
all — `goBeastRoute` (`MainActivity.kt:666`) is a plain `navController.navigate(route) {
popUpTo(HOME, saveState=true); launchSingleTop; restoreState }`. That means: **no swipe gesture
between Beast Mode tabs whatsoever**, and every tap plays the NavHost's generic destination
transition (`Motion.navEnter` in / fade+scale out) — never instant, never a directional slide tied
to tab order. This is exactly the kind of thing the "moving between pages" feedback was about on
the main app, and Beast Mode never got the equivalent treatment.

**Proposed fix**: convert Beast Mode's 3 nav destinations into pager pages using the same mechanism
as `"main"` — one `HorizontalPager` hosting Routines/History/Exercises, swipeable, `goBeastRoute`
becomes a `goToBeastPage(index)` following the identical snap-if-far / animate-if-adjacent /
directional-slide-on-drag-only rule already proven out on the main tabs. Nav bar visuals
(ripple, spring, tint, long-press) are untouched — they're already the same shared `FloatingPillNav`.

**Confirmed in scope, and the user has flagged this as the priority item of the round.** It's
restructuring how 3 of Beast Mode's screens are hosted (stacked destinations → pager pages),
including back-button behavior (today, History/Exercises→back returns to Routines; the pager
version needs the same `BackHandler` pattern `"main"` uses) and state hoisting for the 3 screens'
ViewModels. Implement/verify this one first, before the smaller items.

## 4. Set data entry (reps / kg / time / distance) is actually broken — LOCKED (save-on-blur)

**Confirmed real bug.** `WorkoutSessionScreen.kt`'s `SetTable` (`~line 273`) renders every set's
weight/reps/duration/distance as a plain, read-only `Text` composable — there is no `TextField`,
no tap target, nothing editable at all for those columns. Only the rightmost "complete" checkmark
column is interactive. So today, adding a set via "+ Add Set" creates a blank row (all dashes) that
can only ever be marked complete — there's no way to type in what you actually did. This matches
exactly what you're describing and what the screenshot shows (all dashes).

The good news: the plumbing to save an edited set already exists —
`WorkoutSessionViewModel.updateSet(set: WorkoutSet)` (`WorkoutSessionViewModel.kt:164`) is fully
wired to the repository; it's just never called from the UI.

**Proposed**: replace the static `Text` in each editable column (`WEIGHT`, `REPS`, `DURATION`,
`DISTANCE` — whichever apply to that exercise's tracking mode) with a small numeric input field:
numeric keyboard, decimal allowed for weight/distance, integer-only for reps/duration, saves via
`updateSet` on a per-field debounce or on focus-loss (not on every keystroke, to avoid a DB write
per digit typed). `SET` and `PREVIOUS`/`✓` columns stay as they are (not editable data). The
existing `DaybookTextField` is styled for a full-width labeled field, not a compact table cell, so
this needs a new small "table-cell" input variant rather than reusing it directly.

**Decided**: save on focus-loss / moving to the next field (not per-keystroke).

## 5. Add Exercise screen — ratio/padding/margin pass

**Current**: `AddExerciseScreen.kt` — single `LazyColumn` with `Spacing.screenH` (20dp) horizontal
padding; search field, then the two filter buttons (`MuscleGroupFilterButtons`, each
`Modifier.weight(1f)` with `Spacing.chipGap` = 8dp between), then the exercise list. Structurally
plain/reasonable already; nothing obviously broken in the code, but no dedicated visual pass has
been done on this screen (its Kdoc calls it a first, functional-but-undecorated pass — "no
muscle/equipment filter sheets and no thumbnails" was flagged as deferred at the time). Given item
6 below restructures this screen's layout anyway (moving the filter buttons to the bottom), I'll
fold this into that same pass rather than doing two separate layout edits.

**Proposed**: once the filter buttons move to the bottom bar (#6), re-check row height/padding
consistency between the search field, the exercise list rows, and thumbnail sizing so nothing
looks cramped or oversized relative to the rest of the app's screens (Habits/Intake use the same
`SoftCard` row pattern as a reference).

## 6. Cardio + Muscle Groups buttons → bottom bar, opening a half-page menu with real muscle images — LOCKED

**Current**: both filter buttons sit inline under the search bar (`MuscleGroupFilterButtons`,
`AddExerciseScreen.kt:245`). "Cardio" is a plain on/off toggle chip (tapping it directly sets/clears
the Cardio filter — no menu). "Muscle Groups ▾" already opens a bottom sheet (`SortSheet`, which
*is* already a half-page `ModalBottomSheet`, not full-screen) listing the other 19 groups as plain
text rows with a radio dot — no icons.

**Decided**:
- Move both buttons off the scrolling list and onto a small bar docked to the bottom of the
  screen (visually similar weight/placement to the main bottom nav, but this is a 2-button filter
  bar, not a nav — it should not look like a 3rd nav bar).
- "Muscle Groups" keeps opening the existing half-page sheet.
- **Cardio stays a direct on/off toggle** — no menu, nothing to pick from (it's binary).

**Icons — superseded by real assets.** The user supplied an actual per-muscle image library at
`~/Downloads/Daybook-Exercise-Assets/repdb-free-USABLE/images/muscles/` (26 `.webp` muscle
illustrations) — this replaces the earlier "approximate DaybookIcons" fallback plan entirely for
the Muscle Groups sheet. The library is finer-grained than the app's 20-value `MuscleGroup` enum
(e.g. 3 deltoid heads vs. the app's single `SHOULDERS`), so it needs a many-to-one mapping, and 4
enum values have no anatomical asset at all (`CARDIO`, `FULL_BODY`, `NECK`, `OTHER` — expected,
none of those name a specific muscle). Mapping — **confirmed by user, use as-is**:

| MuscleGroup | Image used | | MuscleGroup | Image used |
|---|---|---|---|---|
| ABDOMINALS | rectus-abdominis.webp | | LATS | latissimus-dorsi.webp |
| ABDUCTORS | abductors.webp | | LOWER_BACK | erector-spinae.webp |
| ADDUCTORS | adductors.webp | | QUADRICEPS | quadriceps.webp |
| BICEPS | biceps-brachii.webp | | SHOULDERS | lateral-deltoid.webp |
| CALVES | gastrocnemius.webp | | TRAPS | trapezius.webp |
| CHEST | pectoralis-major.webp | | TRICEPS | triceps-brachii.webp |
| FOREARMS | forearm-flexors.webp | | UPPER_BACK | rhomboids.webp |
| GLUTES | gluteus-maximus.webp | | CARDIO / FULL_BODY / NECK / OTHER | no image — keep a plain icon fallback (e.g. existing `DirectionsRun`/`Category`) |
| HAMSTRINGS | hamstrings.webp | | | |

Unused-but-available images (kept in the library, just not the chosen representative for their
group): `anterior-deltoid`, `posterior-deltoid`, `brachialis`, `brachioradialis`,
`forearm-extensors`, `soleus`, `obliques`, `transverse-abdominis`, `hip-flexors`,
`serratus-anterior`.

Mechanics: bundle the needed `.webp` files as Android drawable resources (native format, no
conversion needed), shown as a small leading thumbnail per row in the Muscle Groups sheet (and
optionally on the bottom-bar "Muscle Groups" button itself once a group is selected). Scope check:
this round only touches the **Muscle Groups filter sheet** — I have not assumed these images should
also replace the generic icon fallback used for exercise thumbnails elsewhere (`ExerciseThumbnail.kt`
`fallbackIcon = DaybookIcons.Category`) or the per-exercise rows in Add Exercise / the workout
session cards; say the word if you want that too, it's a natural follow-on but a separate,
larger change (every exercise row, not just the filter sheet).

## 7. Enhance the search algorithm — LOCKED (all three)

**Current, exact behavior** (`matchesExerciseSearch`, `WorkoutLogic.kt:121`): matches on **exercise
name only** (not muscle group, not equipment). It already handles word-order ("bike air" finds
"Air Bike") and spacing ("situps" finds "Sit Ups") via token matching + a space-stripped substring
check. It is not typo-tolerant (e.g. "curll" won't find "Curl") and doesn't search anything beyond
the name field.

**Decided — all three**:
1. Also match against the exercise's muscle-group label and equipment label, so typing "chest" or
   "dumbbell" surfaces relevant exercises even when those words aren't in the name.
2. Basic typo tolerance for short queries (small edit-distance match) so a near-miss spelling
   ("curll") still finds "Curl".
3. Rank matches instead of the current stable catalog order — exact/prefix name match first, then
   token match, then muscle/equipment match, then fuzzy — so the closest matches float to the top
   rather than results just staying in alphabetical catalog order.

---

## Status: ALL 7 ITEMS LOCKED — ready for implementation

1. Finish button — floating pill, bottom-right, always visible.
2. Instant-workout naming (4 time-of-day names) + new `DaybookIcons.Bolt` icon.
3. **PRIORITY — implement first.** Beast Mode tab-switching rebuilt as a swipeable pager matching
   main's exact mechanism (snap-if-far / animate-if-adjacent / directional-slide-on-drag).
4. Set editing — save on blur, not per-keystroke.
5. (n/a — folded into #6)
6. Filter bar moved to the bottom; Cardio stays a direct toggle; Muscle Groups sheet gets real
   per-muscle images from the user-supplied asset library, mapping table confirmed as-is.
7. Search: all three enhancements (muscle/equipment text matching, typo tolerance, result
   ranking).

No open questions remain. Suggested implementation order: #3 first (priority + highest risk/
largest surface area), then #4 (bug fix), then #6/#7 (Add Exercise screen — touched together
since #6 restructures its layout), then #1/#2/#5 (smaller, independent).

## Standing hard constraints (apply to every implementation round on this repo)
- No git commit/push, no Firebase App Distribution push.
- All changes must be revertable; do not mess up the current UI outside what's specified here.
- Preserve existing user data (local Room + Firestore docs). None of the 7 items require a Room
  schema/migration change as scoped (item 2 only sets an existing nullable `title` column; no new
  columns or tables needed) — if implementation discovers otherwise, stop and get explicit
  sign-off before touching the schema.
- Deliverable: a signed release APK, sent in chat via SendUserFile.
