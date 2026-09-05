# Calendar drift + "back to today" + journal-chat keyboard — regression notes

Baseline before this round: versionCode **16** / versionName "0.5.6" / DB v19
(APKs `Daybook-v0.5.6-build16-{release,debug}.apk`).
This round: versionCode **17 → 18 → 19 → 20** / versionName **"0.5.6"** (unchanged name — bug-fix
/ polish refinement of the same 0.5.6 release, no new user-facing scope). **DB unchanged — no
migration.** build 17 = items 1–3; build 18 = item 4 (sort-sheet restyle); build 19 = item 5
(filter opener → bottom FAB); build 20 = item 4 follow-up (sort-sheet made compact).

All three items were diagnosed in `SESSION_HANDOFF_2026-09-05.md` and applied here.

---

## 1. Bug A — Home calendar selected date drifts on its own

**Symptom (user video, month grid expanded):** tap a date, then with *no further input* the
selected date keeps moving by itself over ~10 s (e.g. Jul 30 → Jun 7 → Jun 24), and it also
briefly lands on a future date despite the existing future guard.

**Why build 16's fix didn't cover it.** Build 16 added `lastProgrammaticPage` to stop a
Sync 1 ↔ Sync 2 *race* (a second tap landing while the first programmatic scroll was still
animating). That's a real but different bug. This one is a *feedback loop* that needs no user
input at all:

- `WeekStrip.kt` hosts two `HorizontalPager`s in one composable — a compact **week** pager
  (`pagerState`) and the expanded **month** grid's pager (`monthPagerState`) — swapped by
  `AnimatedContent(targetState = expanded)`, which only ever composes ONE body.
- The month pager's sync effect was already gated: `LaunchedEffect(targetMonthPage, expanded) { if (expanded && …) }`.
- The **week pager's** two effects were **not** gated on `expanded`:
  - **Sync 1** (`LaunchedEffect(targetPage)`): whenever `selectedDate` changes, `animateScrollToPage`
    the week pager onto its week — *even while `expanded` is true and the week pager isn't composed*
    (no measured viewport).
  - **Sync 2** (`snapshotFlow { pagerState.settledPage }` collector): on every settle, compute a
    new date by weekday-offset and call `onSelect`.
  - Scrolling an unmounted pager produces erratic settle events → Sync 2 turns them into a wrong
    `onSelect` → `selectedDate` changes → Sync 1 re-fires → loop. Matches the observed drift.
  - Secondary: Sync 2's `onSelect` had **no** future-date check — only `DayCell`'s direct-tap
    path (`if (!isFuture) onClick()`) did — so the loop could walk the selection into the future.

**Fix (`ui/components/WeekStrip.kt`, presentation-only):**
1. Sync 1 → `LaunchedEffect(targetPage, expanded)`, body runs only when `!expanded`. `expanded`
   stays in the key set so collapsing back to the week view still catches the pager up to
   wherever `selectedDate` landed from month-grid taps.
2. Sync 2 → `LaunchedEffect(pagerState, expanded)` with `if (expanded) return@LaunchedEffect`
   before the `snapshotFlow` — settle events from the unmounted week pager are ignored entirely
   until the week view is back.
3. Sync 2's `onSelect` now guarded: `if (newDate != sel && !newDate.isAfter(today)) onSelect(newDate)`
   — same rule `DayCell` enforces on taps, now on the programmatic path too.

**Device-verify:**
- Expand the month grid, tap a date, then *don't touch the screen for ~15 s* — the selection
  must stay exactly where you tapped, no drift.
- Tap across month/week boundaries in the grid, collapse back to the week strip — the week strip
  must show the week containing the date you last tapped.
- Swipe the week strip forward toward the current week — it must never select a day after today.

## 2. "Back to today" link on the Home calendar

New: a centered `TextLink("Back to today")` in `WeekStrip`'s header, between the month/week
label row and the calendar body, wrapped in `AnimatedVisibility(visible = selectedDate != today)`
— hidden whenever today is selected, springs in otherwise. Tapping it calls `onSelect(today)`
(both params already existed on `WeekStrip`; no new plumbing). Works in both the collapsed week
strip and the expanded month grid since it lives above the `AnimatedContent` swap.

**Device-verify:** select any non-today date (week strip or month grid) → the link appears →
tapping it returns the selection to today and the link disappears. No layout jump in the header
when it shows/hides.

## 3. Bug B — journal-chat keyboard leaves the screen black

**Symptom:** opening the keyboard on `HabitJournalChatScreen` (the one-question-at-a-time chat
journal) hides the `BackHeader` and all chat bubbles — just the input field floating mid-screen
above the keyboard, everything else black.

**Root cause:** `MainActivity` in `AndroidManifest.xml` had **no** `android:windowSoftInputMode`,
so it took the platform/OEM default of `adjustPan`. The app runs edge-to-edge
(`WindowCompat.setDecorFitsSystemWindows(window, false)` in `MainActivity`) and its bottom input
bar (`StickySaveBar`) already applies `Modifier.imePadding()` + `navigationBarsPadding()`. Under
`adjustPan` the OS translates the *entire window* up to keep the focused field visible; combined
with the Compose-side `imePadding`, the fixed `BackHeader` and the chat `LazyColumn` get pushed
off the top of the screen, and the revealed area below reads as solid black. `adjustResize`
instead shrinks the content area so Compose's own IME-inset handling positions everything.

**Fix (`app/src/main/AndroidManifest.xml`):** added
`android:windowSoftInputMode="adjustResize"` to the `.ui.MainActivity` `<activity>` block (with
an explanatory comment). App-wide, but `adjustResize` is the correct/standard setting for a
Compose edge-to-edge app using `WindowInsets.ime`; every text-entry screen already assumes it
(forms via `StickySaveBar`, onboarding via `imePadding`).

**Device-verify (needs a real device — not verifiable in the 4-gate):**
- Open a JOURNAL-type habit → chat entry → tap the answer field. `BackHeader` stays pinned at
  the top, the last question/answer bubbles stay visible above the input, input bar sits directly
  on top of the keyboard.
- Re-check the other keyboard screens for no regression: Add/Edit Habit, Add/Edit Intake
  (sticky Save bar rides above the keyboard, form scrolls), Onboarding name field, the Today
  card's in-app reply field, Settings → "Your name".

---

## 4. Restyled the sort / filter bottom sheet (build 18)

`ui/components/SortSheet.kt` — the one `ModalBottomSheet` shared by Intake filter, Habits filter,
and the app-lock "Lock after" chooser. The old version looked unfinished: near-fullscreen with a
handful of rows floating in dead space, grey-on-grey controls, and the selected radio rendering in
the *default* accent regardless of the user's chosen accent.

**Accent fix.** `LocalAccent` is a `staticCompositionLocalOf` provided at the theme root
(`Theme.kt`). `ModalBottomSheet` hosts its content in a separate dialog window; the accent is now
read **once in the host composition** (`val accent = LocalAccent.current` before `ModalBottomSheet`)
and threaded down as a plain `Color` param to every row/control, so it can't fall back to
`AccentColor.DEFAULT` inside the sheet window. Every accent-bearing surface in the sheet
(section labels, selected sort row + radio, checked facet row + checkbox + count pill, archived
Switch, Reset button) now uses that value.

**Visual.**
- `rememberModalBottomSheetState(skipPartiallyExpanded = true)` → the sheet snaps to a single
  content-sized height instead of opening tall and sparse.
- Section labels ("SORT BY" / "TYPE") → accent-colored, `labelMedium` SemiBold, aligned to the row
  text inset (24dp).
- Rows → one shared `SheetRow` wrapper: 12dp side inset, 12dp corner radius, `vertical = 13.dp`
  (down from 14 but now visually tighter because the active state is a filled rounded block). The
  active row (selected sort / checked facet / archived-on) fills with `accent @ 14% alpha`; its
  label goes accent + SemiBold.
- Facet count → a small rounded pill (`accent @ 16%` when checked, hairline otherwise).
- Dividers → inset 24dp with vertical breathing room, not edge-to-edge and cramped.
- Reset → a proper full-width pill button with a `accent @ 55%` hairline border and accent label,
  instead of the old faint centered grey text.
- Unchecked `Switch` given explicit `SurfaceElevated` track / hairline border / muted thumb so it
  reads on the dark sheet.

No API/behavior change — all `SortSheet` params and call sites (`FoodMedScreen`, `RoutinesScreen`,
app-lock) are untouched. `SheetSectionLabel` gained a required `accent: Color` param (internal,
only used within this file).

**build 20 follow-up — made more compact** (user: "buttons are too big and needs to be
organised"). The M3 `RadioButton` pads itself to a 48dp interactive target, which forced every
sort row to ~54dp regardless of my padding. Replaced it with a hand-drawn `RadioDot` (20dp ring +
10dp fill, the whole `SheetRow` is the tap target), matching the 20dp facet checkbox so both
sections read as one system. Also tightened: row inner padding 13→10dp / outer 3→2dp, row corner
12→10dp, section label top 16→12 / bottom 8→4, dividers vertical 10→6, Reset button 14→11dp and
its lead-in spacer 16→10, active-row fill 14%→12% alpha. The archived-row `Switch` is kept (one
row, and it's a real toggle affordance) so that row stays ~48dp — the only one that does.

**Device-verify:** open the filter sheet on Intake and on Habits with a **non-default accent** set
in Settings → Appearance → the section labels, the selected "Added/Name/Next" radio + its row
highlight, any ticked Type checkbox + its count pill, the "Show archived only" switch, and the
Reset border/text should all be that accent. Sheet should be sized to its content (not near
full-screen). Reset clears and closes; picking a sort/type updates the list behind and the
highlight moves.

## 5. One-hand reach: filter/sort opener moved to the bottom (build 19)

The Habits and Intake screens opened the sort/filter sheet from a `CircleIconButton` in the
top-right corner of `ScreenHeader` — the hardest spot on the screen to hit with the thumb of the
hand holding the phone.

**Change (`RoutinesScreen.kt`, `FoodMedScreen.kt`):** that button is removed from the header
`actions` slot (which now holds only the profile `Avatar`) and re-placed as a floating button in
the bottom thumb zone — `Alignment.BottomStart`, mirroring the existing Add FAB at
`BottomEnd`, vertically centred against it. It's a `CircleStyle.Tonal` (soft accent fill)
`IconButtonSize.Lg` (44dp) button, deliberately quieter than the 56dp `Solid` accent Add FAB so
the primary action still dominates. The active-filter accent dot rides on it as before.

Bottom-**left** (not stacked above Add on the right) so it never covers the cards' right-edge
3-dot menus, and both bottom corners are the canonical one-hand reachable zone. Shown even when
the list is empty — needed to clear a filter that has filtered everything out.

`HabitFilterButton` / `IntakeFilterButton` gained a `modifier` param; the `SortSheet` + its
`open` state still live inside those composables, just positioned from the call site now.

The Home ("Today") "Reminders" filter was left as-is: it's a `SectionHeader` trailing action
inside the scrolling list, not a pinned top-corner target, so it isn't the same reach problem.

**Device-verify:** on Habits and Intake, the filter/sort button sits at bottom-left, level with
the Add button at bottom-right, both comfortably thumb-reachable; tapping it opens the sheet;
the accent dot appears on it when a filter/sort is active; it stays visible and usable when the
list is empty or filtered to nothing. Header top-right now shows only the avatar.

## Final 4-gate

Build env: `JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk`.
Run for build 17 (items 1–3), build 18 (item 4) and build 19 (item 5) — identical results each time:

- `testDebugUnitTest`: **446 tests, 0 failures, 0 errors.**
- `assembleDebug`: **BUILD SUCCESSFUL.**
- `assembleRelease`: **BUILD SUCCESSFUL** (R8 + `lintVitalRelease` clean, real release keystore).
- `compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL.**

No new tests: every change is Compose-UI / manifest config with no JVM-testable pure function
extracted — matches this project's standing "no Robolectric / no Compose-UI-test deps"
constraint. DB schema untouched, so `MigrationTest` is unchanged.

APKs at repo root:
- build 17 — `Daybook-v0.5.6-build17-{release (7,162,457 B), debug (23,450,922 B)}.apk`
- build 18 — `Daybook-v0.5.6-build18-{release (7,178,841 B), debug (23,450,922 B)}.apk`
- build 19 — `Daybook-v0.5.6-build19-{release (7,178,841 B), debug (23,450,922 B)}.apk`

Each build pushed to Firebase App Distribution via `./gradlew appDistributionUploadRelease`
(project `daybook-v2-1f578`, app `1:1054765667595:android:4c9078aa9a2181d141fe0e`, group
`testers`). Build 19 is current; 17 and 18 are superseded.
