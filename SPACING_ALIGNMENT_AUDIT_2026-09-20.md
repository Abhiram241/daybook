# Spacing & Alignment Audit — 2026-09-20

Read-only audit, triggered by 4 owner-marked screenshots plus a broader sweep of
`app/src/main/java/com/daybook/app/ui/`. Scope is strictly padding/spacing/alignment/sizing —
no color, theme, or typography changes. Every finding below cites the exact file/line and the
concrete `Modifier`/`Arrangement`/`Spacer` fix, using the existing `Spacing` scale
(`ui/theme/Tokens.kt`) rather than new raw `dp` values, unless noted.

Total: **9 distinct issues** — the 4 owner-marked examples + 5 more found in the broader hunt.

---

## Part 1 — The 4 marked examples

### 1. "New habit" form — Type selector background / caption overlap
**File:** `app/src/main/java/com/daybook/app/ui/routines/HabitForm.kt:127-166`

Root cause confirmed by reading `FormGroup` (`ui/components/Forms.kt:71-85`) and `SoftCard`
(`ui/components/Components.kt:102-113`): `FormGroup`'s content lambda runs inside `SoftCard`'s
plain `Column(...)` with **no `verticalArrangement`**, i.e. zero gap between children by default.
`HabitForm.kt`'s Type group is:

```kotlin
FormGroup("Type") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DaybookChip(...) // Individual / Batch / Ongoing / Journal
    }
    Text(...caption..., style = DaybookText.Caption, color = DaybookColors.TextMuted)  // <-- touches FlowRow, 0dp gap
}
```

The caption `Text` sits flush against the `FlowRow` with no spacer, so the selected chip's pill
and the caption visually merge — this is what the owner's blue circle is actually drawing
attention to (not a background-alignment bug in `DaybookChip` itself; that component renders
correctly — see `Components.kt:225-289`).

**Proof this is the anomaly, not the norm:** `FoodMedForm.kt:146-172` has the *identical* Type
FlowRow-then-caption pattern for TaskType, and it already inserts
`Spacer(Modifier.height(8.dp))` between the FlowRow and the caption (line 166). `HabitForm.kt` is
missing that line.

**Fix:** in `HabitForm.kt`, immediately after the `FlowRow` block (after line 150) and before the
`Text(...)` caption (line 151), add:
```kotlin
Spacer(Modifier.height(Spacing.sm))
```
(`Spacing.sm` = 8dp, matching `FoodMedForm.kt`'s existing convention exactly.)

---

### 2. Workout detail (view mode) — muscle pill too close to Set lines
**File:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutDetailScreen.kt:192-230` (card), `262-271` (`MusclePill`)

Structure confirmed:
```kotlin
Column(Modifier.weight(1f)) {
    Text(info?.name ?: "Exercise", ...)      // exercise name
    Spacer(Modifier.height(4.dp))            // name -> pill gap
    MusclePill(group = ..., tint = tint)     // "Chest" pill (own 4dp vertical padding, Components L267)
}
...
if (blockSets.isNotEmpty()) {
    Spacer(Modifier.height(Spacing.sm))      // pill -> "Set 1..." gap = 8dp total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { ...set lines... }
}
```
8dp (`Spacing.sm`) is the *smallest* named gap above `xs`, and it sits right below a pill that
already has 4dp of its own internal padding — visually the set lines read as crowding the pill
bottom edge, exactly where the owner circled both the "Bench Press (Dumbbell)" and "Chest Fly"
cards.

**Fix:** bump the pre-sets spacer from `Spacing.sm` to `Spacing.md` (12dp) at line 221:
```kotlin
Spacer(Modifier.height(Spacing.md))   // was Spacing.sm
```
This is consistent for both circled cards since they share the same code path (no per-card
special-casing exists).

---

### 3. Workout session (active) — exercise name wraps into notes field
**File:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutSessionScreen.kt:809-843`

The header Row **does** already have `Modifier.weight(1f)` on the name `Text` (line 826) — the
owner's hypothesis of a missing weight modifier does not hold; `Row` correctly reserves the fixed
136dp+ for the three 44dp `CircleIconButton`s and gives the rest to the name. The actual bug is
the gap immediately below:
```kotlin
Row(verticalAlignment = Alignment.CenterVertically, ...) { thumbnail; name (weight 1f, up to 3 lines); up/down/⋮ }
Spacer(Modifier.height(4.dp))     // <-- raw literal, way too tight for a 3-line wrapped title
DaybookTextField(... "Add notes here…" ...)
```
A raw `4.dp` (`Spacing.xs`) spacer was sized for a 1-line name; when the name wraps to 3 lines the
same 4dp reads as the title crowding/underlining into the notes field, which is what the owner's
blue markup shows.

**Fix (2-part, `WorkoutSessionScreen.kt` line 843):**
```kotlin
Spacer(Modifier.height(Spacing.sm))   // was raw 4.dp — token, and 2x the breathing room
```
Also change the outer header `Row`'s `verticalAlignment` from `Alignment.CenterVertically` to
`Alignment.Top` (line 810) so the thumbnail and the 3 icon buttons stay pinned to the first line
of the name instead of drifting to the vertical midpoint of a 3-line title — this is what makes a
long name look like it's colliding with the buttons rather than just wrapping cleanly beside them.

---

### 4. History screen — filter chips not equal width / inconsistent height
**File:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutHistoryScreen.kt:293-337`

Confirmed root cause — and it's a **regression**, not a pre-existing gap. The current code has an
inline comment dated the same day as this audit:
```kotlin
// Owner feedback (2026-09-20) — all three chips must sit on one row, not two. `DaybookChip`
// already sizes to its own content ... dropping the old `Modifier.weight(1f)` constraint and
// putting all three in one horizontally-scrollable Row ...
Row(
    Modifier.fillMaxWidth().padding(...).horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)
) {
    DaybookChip(label = "Muscle Group ▾" or selection, ...)                          // ChipSize.Medium (34dp)
    DaybookChip(label = "Exercise ▾" or selection, ...)                              // ChipSize.Medium (34dp)
    DaybookChip(label = "Date ▾"/"Muscle ▾", ..., size = ChipSize.Small)             // 26dp — height mismatch
}
```
`horizontalScroll` gives the `Row` infinite measured width, so `Modifier.weight(1f)` cannot be
reapplied as-is (that's exactly why it was removed — see the same-day comment on
`DaybookChip`'s internal `Text`, `Components.kt:269-282`, about weight resolving to 0 under
infinite constraints). Two independent bugs are both visible in the screenshot: (a) content-sized
chips ⇒ "Muscle Group ▾" (long label) is much wider than "Date ▾" (short label), and (b) the 3rd
chip's `ChipSize.Small` (26dp) vs the other two's default `ChipSize.Medium` (34dp) ⇒ visibly
shorter / misaligned baseline.

**Fix (`WorkoutHistoryScreen.kt` lines 310-337):**
1. Drop `.horizontalScroll(rememberScrollState())` — the row must have a **bounded** width for
   equal thirds to work, and the owner's later request (equal width, single row) doesn't need
   scrolling since only 3 chips exist.
2. Give each `DaybookChip` call `Modifier.weight(1f)`:
   ```kotlin
   Row(
       Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
       horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)
   ) {
       DaybookChip(..., modifier = Modifier.weight(1f))   // Muscle Group
       DaybookChip(..., modifier = Modifier.weight(1f))   // Exercise
       DaybookChip(..., modifier = Modifier.weight(1f))   // Date/Muscle — remove `size = ChipSize.Small`
   }
   ```
3. Remove `size = ChipSize.Small` from the 3rd chip so all three use the default `ChipSize.Medium`
   (34dp) — fixes the height/baseline mismatch.
4. In `DaybookChip` (`Components.kt:242-263`), add `horizontalArrangement = Arrangement.Center` to
   its inner `Row` (currently defaults to `Arrangement.Start`) so a chip stretched to a wide
   equal-width slot centers its label instead of hugging the left edge. This only changes visual
   output when a chip is wider than its content (i.e. only for these 3 weighted chips) — every
   other bare/content-sized `DaybookChip` call site in the app (muscle filter chips, category
   chips, etc.) is unaffected since Center vs Start is a no-op when width == content width.
5. Re-check `DaybookChip`'s long-label truncation: with `maxLines = 1` + `TextOverflow.Ellipsis`
   already on the inner `Text` (`Components.kt:283-289`), a selected long exercise name will
   correctly ellipsize inside its now-fixed 1/3-width slot instead of forcing the chip wide.

---

## Part 2 — Broader hunt (5 additional instances)

### 5. `FoodMedForm.kt` — same Type-selector-to-caption pattern (currently correct; cross-check note)
**File:** `app/src/main/java/com/daybook/app/ui/foodmed/FoodMedForm.kt:146-172`
Not a bug — flagged here only as the reference implementation for fixing #1. Already has
`Spacer(Modifier.height(8.dp))` between its Type `FlowRow` and its "Icon follows the type..."
caption. No action needed, but **use the same literal** (`Spacing.sm`, i.e. `8.dp`) in the fixed
`HabitForm.kt` for consistency — consider replacing this file's raw `8.dp` literal with
`Spacing.sm` too while touching nearby code, so both forms reference the same token instead of one
using a token and the other a raw literal.

### 6. `RoutineEditScreen.kt` — reorder icon buttons use an off-scale raw size
**File:** `app/src/main/java/com/daybook/app/ui/workout/RoutineEditScreen.kt:191-198`
```kotlin
CircleIconButton(icon = Icons.Filled.KeyboardArrowUp, ..., size = 28.dp)
CircleIconButton(icon = Icons.Filled.KeyboardArrowDown, ..., size = 28.dp)
```
`IconButtonSize` (`Tokens.kt:317-322`) only defines `Sm(32.dp) / Md(40.dp) / Lg(44.dp) / Fab(56.dp)`
— `28.dp` matches none of them (it predates the token's introduction) and sits oddly small next
to the sibling `⋮` "More" button on the same row, which uses the (default) larger size. This is
the same class of "raw value instead of token" issue the owner's brief calls out.
**Fix:** change both to `size = IconButtonSize.Sm.dp` (32dp) — closest existing token, and matches
`WorkoutSessionScreen.kt`'s own up/down buttons which were already bumped to a named size (see the
comment at `WorkoutSessionScreen.kt:830-832`).

### 7. `WorkoutDetailScreen.kt` — "Exercises" muscle-filter chip row has an inconsistent top gap
**File:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutDetailScreen.kt` — the `All /
Chest / Triceps / Forearms` filter row above the exercise cards (same screen as example 2). This
row is content-sized (correct — it's a natural scrolling filter list, not a 3-way equal split like
History's), so no width fix is needed there, but verify its vertical padding uses `Spacing.sm`/`Spacing.md`
consistently with `HistoryFilterBar`'s `vertical = Spacing.sm` (line 313) rather than a bespoke
value, so the two nearly-identical filter-bar patterns (Detail's muscle filter, History's 3-chip
bar) read as the same component family. Align on `Spacing.sm` if it currently differs.

### 8. `Components.kt` — `DaybookChip`'s selected-pill centering gap (root cause shared with #4)
**File:** `app/src/main/java/com/daybook/app/ui/components/Components.kt:242-263`
The chip's inner `Row` has no `horizontalArrangement` (defaults to `Start`) and only
`.padding(horizontal = 12.dp)`. This is invisible for every content-sized chip in the app today,
but becomes visible the instant any chip is stretched wider than its content (exactly what fixing
#4 introduces). Listed separately from #4 because this is a shared-component fix, not a
call-site-only fix — once made, it benefits any future equal-width chip row elsewhere in the app,
not just History.

### 9. `HabitForm.kt` / `FoodMedForm.kt` Type chip rows — not a true `SegmentedControl`
**Files:** `HabitForm.kt:127-150`, `FoodMedForm.kt:146-164`
Neither "Type" selector actually uses the shared `SegmentedControl` component
(`ui/components/SegmentedControl.kt` — the real equal-`weight(1f)`-segments-with-sliding-pill
component, already used correctly by `WorkoutSettingsScreen.kt`, `DailyReportScreen.kt`, and
`HealthTabScreen.kt`). Both Type selectors instead use a `FlowRow` of standalone `DaybookChip`s,
which is why example #1's chips are unevenly sized/wrapped rather than forming a clean segmented
track. This is a larger structural change (component swap, not just spacing), so it is **out of
scope for this padding/spacing-only pass** — flagging it here only so a future pass can decide
whether "Type" should become a real `SegmentedControl` instead of a chip `FlowRow`. No fix
proposed in this document; noted for awareness only.

---

## Build/verify checklist

- [ ] `./gradlew :app:compileDebugKotlin` — confirm no compile errors from the added `Spacer`/`Modifier.weight` calls.
- [ ] `./gradlew :app:lintDebug` — no new lint warnings (unused imports if `Spacing`/`Arrangement` imports were already present, no need to re-add).
- [ ] Launch **New habit** form (`ui/routines/AddHabitScreen.kt` route) — confirm the Type chip row and its caption now have visible breathing room, no overlap.
- [ ] Open an existing **Workout detail** (view mode) with multiple sets — confirm the muscle pill (e.g. "Chest") has a clearly larger gap to "Set 1: ..." than before, on at least 2 different exercise cards.
- [ ] Start/resume a **Workout session**, and if possible use/rename an exercise to a long name (e.g. "Single Arm Tricep Extension (Dumbbell)") to force 3-line wrap — confirm the name no longer visually crowds the notes field, and the thumbnail/icon buttons sit near the top of the name rather than centered through it.
- [ ] Open **History** — confirm all 3 filter chips ("Muscle Group ▾", "Exercise ▾", "Date ▾"/"Muscle ▾") are equal width, evenly spaced, and the same height/baseline; select a muscle group and a long exercise name to confirm ellipsis still works inside the now-fixed-width slot.
- [ ] Spot-check that unrelated `DaybookChip` usages elsewhere (Workout detail's muscle filter, FoodMed category chips, Routines time-tag chips) look unchanged — the `Arrangement.Center` addition should be a no-op for all of them.
- [ ] Toggle light/dark theme on all 4 touched screens — confirm no color/theme regressions (out of scope for this pass, but worth a quick look since `SoftCard`/`DaybookChip` are theme-aware).
