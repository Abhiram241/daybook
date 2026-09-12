# Beast Mode UI Redesign — Plan (not implemented yet)

Status: **planning only**. No code changed. This is the technical plan for the next round; see the
chat reply for the plain-language summary and the questions that need your decision first.

## 1. Why Beast Mode looks flat today

Every Beast Mode screen (`ui/workout/*.kt`) is built entirely out of the same shared components
the rest of Daybook uses (`ui/components/Components.kt`), with only the accent colour swapped
(default **Coral** — `Accent.kt:21`, applied via the `CompositionLocalProvider` in
`MainActivity.kt:746-750`). There is no visual identity of its own:

- Every single card in every Beast Mode screen uses `CardTints.Neutral` (a flat grey fill) —
  `WorkoutHomeScreen.kt:106,219`, `WorkoutSessionScreen.kt:135,299`, `AddExerciseScreen.kt:310`,
  `RoutineEditScreen.kt:148`, `WorkoutHistoryScreen.kt:286`, `WorkoutDetailScreen.kt:53,78`. None of
  the app's 6 pastel tints are ever used here — nothing tells routines, exercises, or PRs apart by
  colour.
- The live workout screen's stats (Duration/Volume/Sets — `WorkoutSessionScreen.kt:136-140`) are
  three plain text columns in a card. No ring, gauge, bar, or any kind of "you're making progress"
  visualization — the one screen you look at *during* a workout has the least energy of any screen
  in the app.
- Numbers (duration, volume, reps, weight) are all rendered at `CardTitle` size (16sp) —
  `WorkoutSessionScreen.kt:281`, `WorkoutDetailScreen.kt:107` — the same weight as a routine name.
  Nothing shouts "here's the number that matters."
- A completed set gets a faint 16%-alpha green tint (`WorkoutSessionScreen.kt:361`) and a PR gets a
  small ★ glyph (`:369`) — the only "reward" moments in the whole mode, and both are easy to miss.
- The nav pill, buttons (`PrimaryButton`/`GhostButton`), fields, and sheet chrome are byte-identical
  to the rest of the app (soft rounded corners, 1dp hairline borders, gentle press-scale) —
  intentionally calm/soft everywhere else, which is right for a journal app but reads as timid for
  something branded "Beast Mode."
- The one deliberately bold element that exists — `FinishFab` (`WorkoutSessionScreen.kt:256-275`)
  — is a solid accent pill with a shadow. It's the only screen element in the whole mode that
  looks remotely "powerful," which is telling.

## 2. Reference mood board → design direction

You picked all three of your reference screenshots and asked for a mix, used for *styling/feedback
only* — every button keeps doing exactly what it does today. Here's what each contributes and
where it lands:

| Reference | What we take from it | Where it shows up |
|---|---|---|
| Apple Fitness (dark, bold rings) | Near-black ground with real depth (not flat grey), saturated ring/gauge for the hero stat, big tabular numerals, bold all-caps micro-labels | Session header hero ring, Routines landing header |
| Light stat-grid (walk/sleep/water/heart) | 2-column grid of tinted stat tiles instead of a single flat list row | New "This week" stat grid on Routines landing; set-table restyle |
| Blue hero + calorie donut | A colour-blocked hero card that the rest of the screen sits under; a big circular "remaining/done" stat as the focal point | Session screen stats card becomes the donut hero; Workout Detail summary card |

None of this touches the rest of the app (Today/Habits/Intake/Settings) — it's scoped entirely to
`ui/workout/*` and a small set of new, Beast-only tokens/components, per your call to keep this a
**new Beast-only visual language**, not a change to the shared component library everyone else
uses.

## 3. New Beast-only tokens & components

All net-new, living in `ui/workout/` (or a new `ui/workout/beast/` package) so nothing outside
Beast Mode can regress:

1. **`BeastPalette`** — a darker, richer ground specific to Beast Mode screens (deeper near-black
   than `DaybookColors.Bg`, plus a subtle top-down accent-tinted gradient/vignette instead of a
   flat fill) and a "hot" variant of the current accent (a brighter/more saturated version of
   whatever `AccentColor` the user picked, for glows/rings only — the base accent picker in
   Settings is untouched). Applies to dark theme; light theme keeps today's flat ground but
   inherits the new card/typography treatment (see open question 5).
2. **`RingStat`** — a circular progress gauge (à la Apple's activity rings), stroke-based, takes a
   0–1 progress + a colour + a center label/value. Used for:
   - Live session header: elapsed time as a filling ring against a rough "typical session length"
     baseline (or simply an ever-filling ring with no cap — see open question 3), replacing the
     plain `StatCol("Duration", ...)` at `WorkoutSessionScreen.kt:137`.
   - Workout Detail's summary card (`WorkoutDetailScreen.kt:53-59`) — same ring, static (no
     animation) since it's a past session.
3. **`StatGridTile`** — a 2-column tinted grid tile (icon + big number + label), built on top of
   the existing `CardTint` system (so it can pick up any of the 6 pastel tints, not just Neutral).
   Used for:
   - A new "This week" row on `WorkoutHomeScreen` (volume lifted, workouts done, current streak,
     PRs this week) sitting above "My routines."
   - The Volume/Sets pair on the session screen (next to the new `RingStat`), replacing
     `StatCol("Volume", ...)` / `StatCol("Sets", ...)`.
4. **`BeastBigNumber`** — a typography treatment (bold, tabular-figure numerals, ~28–34sp) for any
   number that matters: elapsed time, total volume, a set's weight×reps, a PR value. Everywhere
   else in Beast Mode keeps today's `DaybookText` roles unchanged.
5. **PR celebration** — replace the plain ★ swap with a short one-shot scale/glow pulse on the row
   the moment a set is marked a PR (using the existing `Motion.pressSpring()` idiom, no new
   animation system), plus a small gold-tinted `MiniBadge`-style "PR" chip instead of just a
   colour swap on the set number.
6. **`BeastPrimaryButton`** — a heavier CTA variant for "Start an empty workout" and `FinishFab`:
   thicker corner radius reduction (sharper, more "gym" than "journal" — see §4), a subtle
   accent-glow shadow (already used once for `FinishFab`, extended to every primary CTA in the
   mode), bolder label weight/tracking. Same click targets/behaviour as today's `PrimaryButton`.
7. **Muscle-group colour coding** — exercise thumbnails/tiles in the Library and set blocks pick a
   `CardTint` keyed off `MuscleGroup` (there are already curated per-muscle illustration assets —
   `AddExerciseScreen.kt:272-290` — so this is just a colour mapping alongside the existing image
   mapping) instead of always `CardTints.Neutral`. Falls back to Neutral for groups with no natural
   mapping (FULL_BODY/NECK/OTHER/CARDIO).

None of items 1–7 change any ViewModel, repository, DB schema, or navigation — pure Compose/UI
layer, confined to files under `ui/workout/`.

## 4. Screen-by-screen plan

### 4.1 Routines landing (`WorkoutHomeScreen.kt`)
- Header (`:83-94`) gets a bolder hero treatment: bigger title weight, the routines count rendered
  as a `BeastBigNumber`, `BeastPalette` ground instead of flat `Bg`.
- New "This week" `StatGridTile` row (volume / workouts / streak / PRs) inserted above "My
  routines" (after `:139`, before the empty-state/list branch at `:141`).
- "Start an empty workout" (`:131-138`) becomes a `BeastPrimaryButton`.
- "Workout in progress" resume card (`:105-129`) keeps its function, gets the tinted-card + bolder
  number treatment for the elapsed-time label.
- `RoutineCard` (`:218-235`) picks up a muscle-group tint (from whichever exercises the routine's
  made of, or a rotating tint like the app's Habit cards already do) instead of flat Neutral, plus
  set/exercise counts rendered as small `BeastBigNumber`s instead of a plain caption.
- Everything else (overflow menu, delete confirm, duplicate, discard-active-session dialog) is
  unchanged — same `BottomSheetMenu`/`ConfirmDeleteDialog`/`DaybookAlertDialog` wiring, only the
  card/button skin around them changes.

### 4.2 Live workout log (`WorkoutSessionScreen.kt`) — the main event
- Stats card (`:135-148`) is rebuilt as a hero: `RingStat` for elapsed time (left, biggest), a
  `StatGridTile` pair for Volume/Sets (right), all on the `BeastPalette` ground. Rest-timer text
  (`:141-147`) becomes a bold pill overlay on/under the ring instead of a plain caption line.
- `ExerciseBlockCard` (`:286-336`) keeps its exact layout/inputs (name, notes, rest, set table,
  +Add Set) but picks up the muscle-group tint from §3.7 instead of Neutral, and the exercise name
  header gets slightly heavier weight.
- `SetTable` (`:339-416`): the PR row gets the celebration treatment from §3.5 instead of a plain
  gold star + text colour swap; a completed set's row highlight becomes a tinted-not-just-alpha
  fill matching the block's muscle tint (still clearly "done" at a glance, more colour identity).
  Editable cells (`EditableSetCell`, `:426-470`) are functionally untouched — same focus-loss
  commit behaviour, same keyboard types.
- Bottom bar (`:188-198`): `FinishFab` becomes `BeastPrimaryButton`'s pill variant (keeps its exact
  position/behaviour — `viewModel::finish` unchanged); `Discard` stays a `GhostButton` (destructive
  actions shouldn't get "powerful" styling — deliberately kept low-key).
- Rest-timer sheet, block overflow sheet, discard-confirm dialog, exercise-history sheet: unchanged
  wiring, inherit the new sheet chrome from §3 only cosmetically.

### 4.3 Exercise library / picker (`AddExerciseScreen.kt`)
- `ExerciseRow` (`:300-344`) picks up the muscle-tint thumbnail background instead of flat Neutral
  — makes the list scannable by muscle group at a glance, which is the actual point of this screen.
- `MuscleGroupFilterBar` (`:366-397`) keeps its exact two-button behaviour (Cardio toggle +
  Muscle Groups sheet) but the selected chip picks up the corresponding muscle tint instead of the
  flat accent fill every `DaybookChip` uses elsewhere.
- Search field, empty state, PICK-mode checkbox + "Add (n)" sticky bar, BROWSE-mode overflow
  (Edit/Archive): unchanged.

### 4.4 Routine editor (`RoutineEditScreen.kt`)
- `RoutineExerciseRow` (`:147-161`) same muscle-tint treatment as the library row; target summary
  (sets/reps/weight) rendered slightly bolder.
- Name/Notes fields, +Add exercise, targets sheet, remove/overflow, sticky "Save routine" bar:
  unchanged.

### 4.5 History (`WorkoutHistoryScreen.kt`) & Workout Detail (`WorkoutDetailScreen.kt`)
- `SessionRow` (`WorkoutHistoryScreen.kt:277-314`): same muscle/instant-session icon logic, tinted
  card instead of Neutral; volume/sets/exercises line gets the bold-number treatment for the
  volume figure specifically (the number people actually scan for).
- Workout Detail's summary card (`WorkoutDetailScreen.kt:53-59`) becomes the static `RingStat` +
  `StatGridTile` hero described in §3.2, matching the live session screen so a finished workout
  "looks like" the session that produced it. Exercise/set list below unchanged.
- Date-section headers, exercise filter chip/sheet, rename/duplicate/delete overflow: unchanged.

### 4.6 Beast Mode settings (`WorkoutSettingsScreen.kt`)
- Left mostly as-is (it's a utility screen, not a "hype" screen) — `SettingsGroup`/`SettingsRow`
  stay the calm, standard app chrome. Only change: the accent swatch row (`:85-101`) gets a caption
  clarifying that the accent now also drives the new ring/glow treatment, so the picker still
  explains what it affects.

## 5. Explicit non-goals / constraints

- **No functional changes.** Every tap, long-press, sheet, dialog, and nav gesture keeps doing
  exactly what it does today — confirmed with you already. This round is styling/feedback only:
  card skins, typography weight, new stat visualizations, motion/press feedback.
- **No DB/schema/repository/ViewModel changes.** Everything above is Composable-layer only, inside
  `ui/workout/` plus new files for the Beast-only tokens/components.
- **No changes outside Beast Mode.** `ui/components/Components.kt`, `ui/theme/*` global tokens, and
  every non-workout screen stay byte-identical. New tokens/components are additive, in their own
  files.
- **Accessibility preserved.** Any new colour-on-colour pairing (tinted card + text, glow + label)
  gets checked against the same contrast approach already used for `LocalOnAccent`/`onAccentInk`
  (`Accent.kt:44-75`); TalkBack labels on existing icons/buttons are kept as-is.
- **[[appforfood-workout-feedback]] reminder:** the elapsed-time ticker must stay a *live* value,
  not frozen — the new `RingStat` on the live session screen has to keep consuming
  `viewModel.elapsedSeconds` the same way `StatCol` does today (`WorkoutSessionScreen.kt:95`), not
  a one-shot computed label like the frozen resume-card label at `WorkoutHomeScreen.kt:102-104`
  (that one is intentionally frozen and stays that way).
- Deliverable for the actual implementation round follows the usual process: a plan/implement
  agent split, review of anything touching Room/sync (none expected here), and a signed release
  APK — not part of this planning step.

## 6. Suggested rollout order (for the implementation round, not now)

1. New Beast-only tokens: `BeastPalette`, `BeastBigNumber` text style, muscle→tint mapping.
2. `RingStat` + `StatGridTile` components, unit-testable pure math (progress fraction, tint
   resolution) kept separate from the Composable the way `Motion`/`onAccentInk` already are.
3. Session screen (§4.2) — highest-impact screen, ships first so you can sign off on the "feel"
   before it's replicated elsewhere.
4. Routines landing (§4.1) and Workout Detail (§4.5) — reuse the components from step 2/3.
5. Library/Picker + Routine editor (§4.3/§4.4) — muscle-tint rollout.
6. History row tint + Settings caption tweak (§4.5/§4.6) — smallest, last.
