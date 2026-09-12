# HISTORY_TAB_REDESIGN_PLAN.md

**Status: PLAN ONLY. No code was written to produce this document.** The user asked for a plan
for Beast Mode's History tab redesign, explicitly "do not implement" — this file is that plan,
for review and revision before any implementation round is scheduled.

## 0. What exists today (read from the live code)

`ui/workout/WorkoutHistoryScreen.kt` + `WorkoutHistoryViewModel.kt`:

- A single flat `LazyColumn` of every session (`observeRecentSessions(200)`), newest first
  (ordering comes from the DAO query, not re-checked here).
- Each row (`SessionRow`): a generic workout icon in an `IconTile`, then title ("Workout in
  progress" for an active session, else the session's title-or-date) and a one-line subtitle
  (either "Tap to carry on" or the plain date), and a `⋮` overflow with only one action, Delete.
- No per-session numbers at all — not exercise count, not set count, not volume, not duration.
  The screen's own doc comment admits this is a known gap: the plan (§3.7.6) originally called
  for a "5 exercises · 18 sets · 4,200 kg"-style summary line per row, and it was deliberately
  left out during A6a to avoid an N+1 aggregate query per row on a long history.
- No grouping — 200 sessions render as one undifferentiated list, no month/week headers, no
  "today/yesterday" treatment.
- No filtering or search (by exercise, by date range, by routine).
- Empty state: an `EmptyState` block plus a separate "Import from Hevy" ghost button.
- The full per-session stat treatment (Duration / Volume / Sets, à la `WorkoutDetailScreen`'s
  `StatColumn` row) only exists one screen deeper, after tapping into `WorkoutDetailScreen`.

This matches the user's "bad UI" complaint directly: compared to every other list-shaped screen
in Daybook (Today's day view, Habits, Intake — all of which show a status/progress signal right
on the row), History's rows carry almost no information. It reads as a bare debug list, not a
finished screen.

## 1. What "good" looks like elsewhere in this app (reuse, don't reinvent)

- `WorkoutDetailScreen`'s `StatColumn` row (Duration / Volume / Sets, three centered columns in
  a `SoftCard`) is the exact stat trio the plan wanted on History rows too — it already exists,
  it's just one screen too deep.
- `RoutinesViewModel` and `FoodMedViewModel` both solved "don't N+1 a derived value across a
  list" with one `GROUP BY` aggregate query feeding a `Map<id, value>` the list combines against
  — `WorkoutHistoryViewModel` needs the same shape: one query returning
  `{sessionId -> (setCount, totalVolumeKg, exerciseCount)}` for the visible page, not a query per
  row.
- `ScreenHeader` + month/date grouping already exists conceptually in Today's week strip; History
  doesn't need the same widget, but the same instinct (don't show 200 undifferentiated rows)
  applies.
- `ExerciseThumbnail` (added this round) makes a small thumbnail-collage per session plausible
  now, where it wasn't before this round's artwork work landed.

## 2. Proposed redesign

### 2.1 Per-row content (replaces the current title+date+overflow row)

Each session row becomes a richer card:

```
[thumb][thumb][thumb+2]  Push Day                    ⋮
                          Tue, 9 Sep · 52 min
                          4,200 kg · 18 sets · 5 exercises
```

- A small strip of up to 3 exercise thumbnails (from the session's blocks, reusing
  `ExerciseThumbnail`) as a collage, with a "+N" tile if there are more than 3 — gives the row an
  instant visual signature instead of one generic workout icon for every row.
- Title stays as today (session title, or date if untitled); "Workout in progress" special-case
  for `ACTIVE` sessions stays.
- **New subtitle line 1:** relative-ish date + duration ("Tue, 9 Sep · 52 min").
- **New subtitle line 2:** the Duration/Volume/Sets-style summary, condensed to one line
  ("4,200 kg · 18 sets · 5 exercises") instead of three separate `StatColumn`s (that layout suits
  Detail's full-width header; a compact inline string suits a list row).
- Keep the `⋮` overflow (Delete only today — see §4 for whether more actions belong here).

### 2.2 Grouping

Replace the flat 200-row list with date-bucketed sections, newest first:

- **This week** / **Last week** / **[Month name]** for anything older, each a
  `SectionHeader`-style sticky-ish label (doesn't need to be pinned/sticky — a plain header
  between groups matches the rest of the app, nothing else in Daybook uses sticky headers).
- Buckets computed client-side from `localDate`, same device-local-date logic already used by
  the streak calculator elsewhere in the app (no new date math primitive needed).

### 2.3 A lightweight header stat strip (optional — see open question 3)

A small non-scrolling summary above the list — e.g. "12 workouts this month · 3 this week" — the
same instinct as Today's progress cards, giving the tab a "you're making progress" feel instead
of being a pure log. Kept optional because it's the one piece of this plan with a real
implementation cost (another aggregate query, another design decision about what number matters
most) rather than a re-arrangement of data the app already computes elsewhere.

### 2.4 Data layer changes this implies

- One new `WorkoutDao` aggregate query: per-session set count + summed volume (kg) + distinct
  exercise count, for the current page of session ids — mirrors the existing
  `RoutinesViewModel`/`FoodMed` GROUP BY pattern, so no new pattern is introduced to the codebase.
- The row's thumbnail strip needs each session's first ≤3 distinct `exerciseId`s with an image —
  either piggyback on the same aggregate query or a second small query; either is fine, just
  shouldn't be one query per row.
- No schema change. No migration. Everything needed already exists in `workout_sessions` /
  `workout_exercises` / `workout_sets`.

### 2.5 What does NOT change

- Tapping a row still opens `WorkoutDetailScreen` (finished) or resumes the live session
  (active) — unchanged.
- The overflow menu's one action (Delete) — unchanged unless open question 4 below adds to it.
- The empty state and "Import from Hevy" entry point — unchanged, still shown for zero sessions.
- No new navigation destinations.

## 3. Open questions for the user (please answer before this becomes an implementation round)

1. **The header stat strip (§2.3)** — do you want it, or does the grouped/richer list alone
   solve the "feels bad" problem? *Recommendation: skip it for the first pass — ship the richer
   rows + grouping first, add a stat strip later if the tab still feels flat once you've used it.*
2. **Volume unit** — Detail screen always shows kg regardless of the app's weight-unit setting
   (worth checking whether that's itself a pre-existing bug); the redesigned row's summary line
   should obviously respect whatever the app's real weight-unit setting is. *Recommendation: yes,
   respect the setting — flagging this because it means the redesign should fix the Detail screen
   too while it's in the area, not just avoid repeating the bug.*
3. **Search/filter** — do you want to be able to filter History by a specific exercise (e.g. "show
   me every day I did squats") in this pass, or is that a later addition? *Recommendation: leave
   it out of this pass — it's a genuinely separate feature (a new query + a new UI affordance),
   not a visual redesign, and the current complaint reads as "the rows look bad," not "I can't
   find things."*
4. **Overflow menu** — should the redesigned row's `⋮` gain more actions now that it's being
   touched (Edit, Duplicate-as-routine, etc.), or should it stay Delete-only for this pass?
   *Recommendation: stay Delete-only — adding actions is unrelated to the visual complaint and
   would grow this into a bigger feature round.*
5. **Thumbnail collage (§2.1)** — up to 3 exercise thumbnails per row is a specific visual choice.
   Fine as described, or would you rather the row stay to one single icon (just resolving to the
   *session's* dominant/first exercise's real thumbnail instead of the generic workout icon,
   simpler to build) rather than a multi-thumbnail collage? *Recommendation: the collage — it's
   what makes rows visually distinguishable from each other at a glance, which is most of what's
   currently missing; a single resolved thumbnail is a smaller but much less noticeable
   improvement.*

## 4. Not part of this plan

Nothing here proposes changing `WorkoutHomeScreen` (Routines landing), `WorkoutSessionScreen`
(live logging), or the Exercises tab — this document is scoped to `WorkoutHistoryScreen` only, as
asked.
