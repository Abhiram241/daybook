# UX_REFINEMENT_PLAN.md

Fresh analysis + implementation plan for the next Daybook round. **Plan only — no `.kt`
was changed to produce this.** A separate implementer executes this after the user answers
the questions at the bottom of the chat-review.

Baseline: working tree at commit `46a714d` + uncommitted battery-fix + build-21/22 + the
**UX overhaul** (`UX_OVERHAUL_PLAN.md` / `UX_OVERHAUL_PROGRESS.md`). DB **v20**
(`MIGRATION_19_20` = `app_settings.theme_mode`), uncommitted. versionCode 22 /
versionName "0.5.6" — **do not change**. `compose-bom 2024.12.01`, minSdk 26, target 34.

This round has **4 items**:

1. Legibility / contrast / unclear-label hunt (root cause: `SegmentedControl`).
2. Onboarding — richer teaching copy + hide the "Create your first habit" CTA for
   accounts that already have data.
3. Themed tint variants — a **Dark style** picker + a **Light style** picker (separate
   from accent), style-parameterized `DaybookColorScheme`.
4. Configurable corner roundness — a global **corner-scale** slider on Appearance.

Schema change this round: **one** additive `MIGRATION_20_21` on `app_settings` — **3
columns** (`dark_style`, `light_style`, `corner_scale`). `AppDatabase.version` 20 → 21, one
new `21.json`. Nothing else may touch a Room entity / DAO / migration / sync payload /
`BackupModel` / Firestore shape — if the implementer finds they must, **STOP and ask**.

---

## Decisions (locked)

These are already settled — do not re-litigate, do not ask about them:

- **D1. Theme-style picker shape.** A "Dark style" picker **and** a "Light style" picker
  in Settings → Appearance, working *alongside* the existing Dark / Light / System toggle.
  Accent colour stays its own separate setting. Both style pickers are always visible
  (user can configure the mode they're not currently in).
- **D2. Onboarding is the only teaching surface this round.** No settings help-text, no
  FAQ, no tooltips. All feature explanation lives in the onboarding tour.
- **D3. Hunt scope = legibility + contrast + unclear labels only.** Not spacing, not
  visual polish, not flow redesign.
- **D4. Existing users hide the CTA.** The "Create your first habit" link on the closing
  onboarding step must not render for an account that already has habit / intake data (or
  in `reviewMode`).
- **D5. Current look is the default for every new setting.** `dark_style` defaults to the
  current dark palette ("Charcoal"), `light_style` to the current light palette ("Paper"),
  `corner_scale` defaults to `1.0`. An existing user sees **zero change** until they opt in.
- **D6. `MIGRATION_20_21` is additive-only**, `app_settings` only, not in `BackupModel` /
  sync / `ContentHash` — same treatment as every `app_settings` column since v16.
- **D7. Revertable, corrective/additive only.** No `git commit` / push, no Firebase App
  Distribution push. No versionCode/Name/dependency/SDK/Gradle bumps.
- **D8. Dark "Charcoal" and light "Paper" styles must remain byte-identical to today's
  `DaybookColorsDark` / `DaybookColorsLight`.** Guard with a unit test.

Open questions (answers fold into this block later): the exact style sets + optional
5th style per mode; corner-scale range / label / which tokens are in scope; whether the
style pickers also appear in onboarding; onboarding copy tone. See chat-review.

---

## Verification gate (run at the end, all must pass)

```
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=~/android-sdk \
  ./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

- `testDebugUnitTest` — 469 existing tests stay green + the new pure tests below pass.
- `assembleRelease` — R8 + lintVitalRelease clean, real-keystore signed (SHA-1
  `39e62d0f…`, see `RELEASE_SIGNING.md`).
- `app/schemas/com.daybook.app.data.local.AppDatabase/21.json` regenerated on disk (it is
  currently untracked for 20.json too — the implementer must `git add` both when a commit
  eventually happens; **no commit this round**).
- Manual/device follow-ups listed per item.

New pure JUnit4 test files (no Mockito / Robolectric / coroutines-test — this project has
none):

- `ScaledAppShapesTest` — `scaledAppShapes(1f)` equals current radii; `scaledAppShapes(0f)`
  → all rectangular tokens 0dp; `2f` → doubled; monotonic; `segmented` (and `navPill` per
  Q7) invariant; `clampCornerScale` bounds.
- `DarkStyleTest` / `LightStyleTest` — `fromKeyOrDefault(null|garbage)` → DEFAULT;
  `DEFAULT == CHARCOAL` / `PAPER`; `darkSchemeFor(CHARCOAL) == DaybookColorsDark` and
  `lightSchemeFor(PAPER) == DaybookColorsLight` (byte-identity guard, D8); every style's
  `textPrimary` / `textMuted` clear a min WCAG ratio vs its own `bg` (tiny contrast helper
  lives in the test).
- `HasExistingDataTest` — `hasExistingData(habitCount, intakeCount)` predicate.
- Extend `AccentColorThemeTest` if it currently pins the dark scheme by literal.

---

# ITEM 1 — Legibility / contrast / unclear-label hunt

## 1.1 Root cause: `SegmentedControl` selected pill renders at height 0

**File:** `app/src/main/java/com/daybook/app/ui/components/SegmentedControl.kt`

The selected-segment fill is a separate `Box` (lines 82–89):

```kotlin
Box(
    Modifier
        .offset(x = pillX)
        .width(segWidth)
        .fillMaxHeight()          // <-- the bug
        .clip(AppShapes.segmented)
        .background(accent)
)
Row(Modifier.fillMaxSize()) { ... }   // segments
```

The outer `BoxWithConstraints` is `.fillMaxWidth().heightIn(min = 40.dp)…` — **`heightIn`
sets only the *minimum*.** The parent (`Column(Modifier.padding(cardInner))` inside a
`SettingsGroup`) imposes no fixed height, so the max-height constraint reaching
`BoxWithConstraints` is unbounded. `Box` children measure against the *incoming*
constraints, not the Box's resolved size, so `fillMaxHeight()` on the pill has nothing
finite to fill → it collapses to its content height, which is **0** (the pill has no
children). Result: **the accent pill is never visible**, and the selected label
(`DaybookColors.OnSolid` = near-black `#0B0D0F`) then sits on the bare dark track with no
fill behind it — invisible. This is exactly what both screenshots show.

**Regression origin:** the build-21 font-clipping batch (`KEYBOARD_FONT_FIXES_PLAN.md`
B1–B15) swept `height()` → `heightIn(min=)` across `SegmentedControl.kt` among others, to
let controls grow at large font scale. Same failure family as the **build-22 hotfix** for
`FloatingPillNav` (`heightIn(min=)` + `fillMax*` children). There it stretched; here (a
`Box`, not a weighted `Row`) it collapses.

### Fix (keeps the slide animation and the font-scale headroom)

```kotlin
BoxWithConstraints(
    modifier = modifier
        .fillMaxWidth()
        .heightIn(min = 40.dp)
        .clip(AppShapes.segmented)
        .background(DaybookColors.SurfaceElevated)
        .border(1.dp, DaybookColors.Border, AppShapes.segmented)   // 1.3 — give it an edge
        .padding(4.dp)
) {
    val segWidth = maxWidth / count
    val rm = LocalReduceMotion.current
    val pillX by animateDpAsState(
        targetValue = segWidth * selectedIndex,
        animationSpec = if (rm) snap() else Motion.placementSpring(),
        label = "segPill"
    )

    // The pill lives inside a matchParentSize wrapper: that wrapper is measured with the
    // track's RESOLVED size as fixed constraints, so fillMaxHeight() inside it works.
    Box(Modifier.matchParentSize()) {
        Box(
            Modifier
                .offset(x = pillX)
                .width(segWidth)
                .fillMaxHeight()
                .clip(AppShapes.segmented)
                .background(accent)
        )
    }

    Row(Modifier.fillMaxWidth()) {                    // was fillMaxSize()
        options.forEach { spec ->
            val selected = spec.key == selectedKey
            // 1.2 — unselected label was TextMuted (~6:1, reads broken with thin Literata).
            val contentColor = if (selected) DaybookColors.OnSolid else DaybookColors.TextPrimary
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)           // was fillMaxHeight(); gives the track a real height
                    .clip(AppShapes.segmented)
                    .clickableImpl(remember { MutableInteractionSource() }) { onSelect(spec.key) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (spec.icon != null) {
                    Icon(spec.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(IconSize.Sm))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    spec.label,
                    style = DaybookText.ButtonLabel,  // labelLarge SemiBold 14sp; was NavLabel 12sp Medium
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}
```

Why `matchParentSize` wrapper rather than `matchParentSize()` directly on the pill: the
pill also needs a `width(segWidth)` and an animated `offset` — nesting keeps those working
while the wrapper supplies a finite height. The content `Row`'s `heightIn(min = 36.dp)`
per segment is what gives the track a resolved height in the first place; it still grows
past 36.dp at large font scale (that was the point of the build-21 change).

**Selected-text contrast across all 5 accents + light theme:** `OnSolid` is `#0B0D0F`
(dark) / `#FFFFFF` (light). All 5 **dark** accent values (`#2DD4BF`, `#A78BFA`, `#FB7185`,
`#60A5FA`, `#FBBF24`) clear 4.5:1 against near-black. All 5 **light** accent values
(`#0F9488`, `#7C5CE0`, `#E23D5B`, `#2563EB`, `#B7791F`) clear 4.5:1 against white. This
also holds under every proposed theme-style (Item 3) because the pill fill is the accent,
not a style ground colour. No per-style special-casing needed for the segmented control.

**Test:** layout, so no unit test. Device/gate check: on Appearance and Today & calendar,
every segmented control shows a filled accent pill under the selected option with a
readable label, in Dark + Light + every style + all 5 accents + Literata/Nunito/Mono
fonts at the largest system font size.

## 1.2 App-wide legibility findings (ranked)

Contrast ratios below are the semantic-token values on their real background; "dark" = the
Charcoal palette. `TextMuted` = `#9AA0A6` on `#0B0D0F` ≈ 7.4:1 (fine); `TextFaint` =
`#6B7178` on `#0B0D0F` ≈ 3.4:1 (fails AA for normal text, borderline for large). The
theme migrated to `LocalDaybookColors` in the overhaul, so all fixes below are
theme-aware automatically — just pick a better *role*, never a hardcoded `Color`.

| # | Severity | File:line | What's wrong | Fix |
|---|----------|-----------|--------------|-----|
| 1 | **Critical** | `ui/components/SegmentedControl.kt:82-90` | Selected pill collapses to height 0 → no highlight, near-black selected label on bare track. All 3 screenshot controls. | §1.1 — `matchParentSize` wrapper + `heightIn(min=36)` per segment + `Row.fillMaxWidth()`. |
| 2 | High | `ui/components/SegmentedControl.kt:93` | Unselected label = `TextMuted` at `NavLabel` (12sp Medium) on `SurfaceElevated` ≈ 6:1 — reads as "disabled / broken" with thin Literata. | Unselected → `TextPrimary`; style `DaybookText.ButtonLabel` (14sp SemiBold). |
| 3 | Med | `ui/components/SegmentedControl.kt:67-73` | Track has no border; `SurfaceElevated` (`#1E2124`) vs page `Bg` (`#0B0D0F`) is a weak edge — the control doesn't read as a control until tapped. | Add `.border(1.dp, DaybookColors.Border, AppShapes.segmented)`. |
| 4 | High | `ui/components/Components.kt` `GhostButton` (~L607-620) | Disabled: border stays `Hairline` (white-8%, invisible) + text `TextFaint`. A disabled Ghost button is nearly undetectable (`QuietTimeRow` in Settings uses this). | Disabled border → `DaybookColors.Outline`; disabled text keep `TextFaint` but add a `DaybookColors.SurfaceElevated.copy(alpha=.4f)` fill so the shape reads. |
| 5 | Med | `ui/components/Components.kt` `CircleIconButton` (~L150-160) | Disabled bg forced to `SurfaceElevated` — identical to the enabled **Ghost** style; only the icon tint (`TextFaint` vs `TextPrimary`) distinguishes them. Ambiguous. | Disabled: bg `SurfaceElevated.copy(alpha=.5f)` + 1dp `Outline` border so "off" is visibly different from "ghost". |
| 6 | Med | `ui/components/Components.kt:208` `DaybookChip` | Unselected chip label = `TextMuted` on `SurfaceElevated` ≈ 6:1. Intake filter chips. | Unselected label → `TextPrimary`; keep selected `OnSolid` on accent. |
| 7 | Med | `ui/components/WeekStrip.kt:434-435` | Future / dimmed day numbers = `TextFaint` on `Bg` ≈ 3.4:1 — day digits (titleMedium ~16sp SemiBold) barely legible. | Future/dimmed → `TextMuted` (still clearly de-emphasised vs `TextPrimary`, but readable). |
| 8 | Med | `ui/components/SortSheet.kt:258,273` | Unchecked radio ring + unchecked facet tick drawn in `TextFaint` — the "not selected" control is almost invisible against the sheet. | Unchecked ring/tick → `DaybookColors.TextMuted` (or `Outline` for the ring, min 3:1). |
| 9 | Low | `ui/TimePickerComponents.kt:92` | "no times added" helper text in `TextFaint`. It's real information, not decoration. | → `TextMuted`. |
| 10 | Low | `ui/settings/SettingsScreen.kt:324` | About-footer "Daybook / Version 0.5.6 (22)" in `TextFaint`. | → `TextMuted`. |
| 11 | Low | `ui/settings/AboutSettingsScreen.kt:66` | Same footer treatment (`TextFaint`). | → `TextMuted`. |
| 12 | Low | `ui/components/Forms.kt:130` | Text-field placeholder = `TextFaint`. (WCAG exempts placeholders, but with Mono/Literata it's rough.) | Optional → `TextMuted`. Leave if the user prefers the quieter placeholder. |
| 13 | Low | `ui/onboarding/OnboardingIllustrations.kt:157,170` | Mock "bars" drawn in `TextFaint` — decorative, inside an illustration. | No change (decorative, not content). Listed for completeness. |

### Unclear-label findings (content, not colour — D2 limits scope)

| # | File | Label | Problem | Fix |
|---|------|-------|---------|-----|
| L1 | `ui/settings/SettingsScreen.kt` "Streaks" group (~L629+) | **"Show streak flames"** — subtitle *"Hides the flame pill on Today…"* | Label says **Show**, subtitle says **Hides** — direct contradiction. This is a correctness bug, not "help text". | Subtitle → *"The streak flame on Today and the streak figure on Detail stats. Turn this off to hide them."* |
| L2 | `ui/settings/SettingsScreen.kt:582-608` "Greeting" | Segmented **Full / Simple / Off** with no indication of what each does. | Ambiguous without trying all three. | **Question Q5** — either a one-line subtitle (arguably not "help text") or leave. Recommend a subtitle: *"How much the Today greeting says."* |
| L3 | `ui/settings/SettingsScreen.kt:557-566` "Week starts on" | Options **Sun / Mon / Sat** only. | "Sat" with no context reads as a typo; the full week isn't offered. | Out of scope (product decision) — noted only. |

Everything else in Settings (Accent colour, Font, Layout, 24-hour time, Hide resolved
reminders) has adequate self-describing subtitles — leave as-is per D2.

**Item 1 deliverables for the implementer:** edit `SegmentedControl.kt` per §1.1; apply
rows 2–11 (+ L1) from the tables; row 12 pending Q5-adjacent taste call; rows 13/L3 no-op.

---

# ITEM 2 — Onboarding

**Files:** `ui/onboarding/OnboardingViewModel.kt`, `ui/onboarding/OnboardingScreen.kt`,
`ui/onboarding/OnboardingIllustrations.kt`, and the gate in `ui/MainActivity.kt`
(`onboardingCompleted == false` branch ~L320, and `onboarding_review` route ~L680).

## 2.1 What ships today

`WizardStep` = `NameAsk` (only when no name derivable from Google) · 5 `Teach` steps ·
`PermissionPrimer` · `Ready`. Each `Teach` = one title + one paragraph + a code-drawn mock
(`TeachIllustration` = TODAY / MAKE_HABIT / INTAKE / SHADE / YOURS). `Ready` always shows a
"Create your first habit" `TextLink` when `!reviewMode`.

Problems the user reports: features "not explained properly" — streaks are never mentioned;
Journal is folded into one clause; notification *actions* aren't named concretely; app-lock
+ sync + theming are crammed into a single "Yours, and private" step. And the CTA shows for
returning users who already have restored data.

## 2.2 Revised step deck (copy is final proposed text — put verbatim in `OnboardingTeachSteps`)

7 `Teach` steps + `PermissionPrimer` + `Ready`. Add two `TeachIllustration` entries —
`STREAKS`, `PRIVACY` — each a cheap static mock in `OnboardingIllustrations.kt` (spec in
§2.4). Keep every step body ≤ ~3 sentences so it stays skimmable.

**NameAsk** (unchanged trigger; tweak body):
- Headline: `Welcome to Daybook`
- Sub: `Reminders that ask what you actually ate, took, and did — and keep the log for you.`
- Field label `Your name`, placeholder `e.g. Alex`, helper line under it:
  `Used in the Today greeting. Change it any time in Settings.`

**1 — Today is home base** · `TeachIllustration.TODAY`
> Today opens on a greeting and how many reminders are still open. The strip along the top
> switches days — tap any past day to log something you missed. Tap the month name for the
> full calendar. Two cards track today's habit and intake progress.

**2 — Habits, four ways** · `TeachIllustration.MAKE_HABIT`
> Add a habit from the Habits tab. **Individual** reminds you at each time you set.
> **Batch** rolls several small habits into one evening check-in. **Ongoing** just counts
> the days since you started, with no reminders. **Journal** asks you a short set of
> questions each time. You pick the days, the times, and how long a snooze lasts.

**3 — Intake: what you actually had** · `TeachIllustration.INTAKE`
> Intake reminders ask "what did you have?" and save your reply as the log — type it on the
> card or straight from the notification. Use it for meals, medication, water, anything you
> want a record of. Mark a food as a **red flag** and Daybook builds a diary of possible
> triggers.

**4 — Journalling** · `TeachIllustration.INTAKE` (reuse) *or* a small new `JOURNAL` mock
> A Journal habit turns a check-in into a few written prompts — "How did you sleep?",
> "What's on your mind?". Answers are saved per day, and you can read the whole thread back
> from the habit's detail screen. Edit the questions whenever you like.

**5 — Reminders that follow up** · `TeachIllustration.SHADE`
> Every reminder lands as a notification with buttons: **Complete** or **Skip** for habits;
> **Reply**, **Skip** or **Snooze** for intake. Anything you don't answer keeps nudging on
> the snooze interval until you do. **Quiet hours** hold reminders back and release them
> later — nothing is dropped.

**6 — Streaks** · `TeachIllustration.STREAKS` (new)
> Each habit counts consecutive days done. The flame on Today shows your best current run;
> a habit's detail screen shows its full history. A missed day resets it. Not your thing?
> Turn the flame off in Settings → Today & calendar.

**7 — Yours, private, and backed up** · `TeachIllustration.PRIVACY` (new)
> Pick an accent colour, a font, a light or dark look with a background style, and choose
> which tabs show — all in Settings → Appearance. Everything is stored on your device
> first. Signing in mirrors an encrypted copy to your account, so a new phone picks up
> where you left off. Add a PIN or fingerprint lock in Settings → Privacy & lock.

**PermissionPrimer** — keep the 3 rows + inline "Allow" buttons; expand each body:
- Notifications — `Without this, reminders are silent. They still appear in the app, but nothing pops up on your phone.`
- Exact alarms — `Lets a reminder fire at the exact minute you set. Without it, Android can delay it by up to about 15 minutes to save power.`
- Unrestricted battery — `Stops the system freezing Daybook in the background, which would silence reminders after a few days. One-time setting.`
- Intro line — `Allow these now or later — Daybook asks again if it needs to.` (unchanged)

**Ready** — 3 variants (see §2.3 for the predicate):
- Fresh install, no data: headline `You're set` · body `Make your first habit now, or look around first.` · `TextLink("Create your first habit")` shown.
- Returning user, data present: headline `Welcome back` · body `Your habits and reminders are already here — Today has the details.` · **no link**.
- `reviewMode`: headline `That's the tour` · body `Tap Done to head back to settings.` (unchanged).

## 2.3 Hide the CTA for existing users (D4)

**Signal.** `onboardingCompleted` is device-local (`app_settings`, not synced), so on a
reinstall / new device it is `false` and onboarding runs — but `CloudSyncRepository`'s
first-sign-in bootstrap restores the user's habit/intake rows. The reliable, already-observable
signal is **row count**, reactively (bootstrap may still be in flight when `Ready` renders):

- Add to `OnboardingViewModel`:
  ```kotlin
  // pure — HasExistingDataTest
  fun hasExistingData(habitCount: Int, intakeCount: Int): Boolean = habitCount + intakeCount > 0

  val hasExistingData: StateFlow<Boolean> =
      combine(
          habitRepository.observeAllHabits(),      // already exists
          foodMedRepository.observeAllTasks()      // already exists
      ) { h, i -> hasExistingData(h.size, i.size) }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
  ```
  `observeAllHabits()` / `observeAllTasks()` are existing repo Flows — **no DAO or schema
  change** (Journal habits are `HabitType.JOURNAL` rows in `habits`, so they're already
  counted). Inject `HabitRepository` + `FoodMedRepository` into `OnboardingViewModel`
  (constructor add; both are `@Singleton`, Hilt-provided).
- `OnboardingScreen`: collect `hasExistingData`; pass
  `showCreateHabit = !reviewMode && !hasExistingData` into `ReadyStep`, and pick the Ready
  copy variant from the same booleans.
- Fresh-install path: `hasExistingData` is `false` → link shows, "You're set" copy.
- Re-run path (`onboarding_review` route → `configureReview()`): `reviewMode == true`
  already suppresses the link — unchanged; the "That's the tour" copy stays.

**STOP-and-ask flag for the implementer:** injecting two repositories into
`OnboardingViewModel` is within bounds (no schema, no DAO edit). If for any reason the
count must instead come from a *new* `@Query("SELECT COUNT(*)…")` DAO method, that is a
DAO change → **STOP and ask** before adding it. Prefer the repo-Flow route.

## 2.4 New illustrations (`OnboardingIllustrations.kt`)

Follow the file's existing pattern (static `Canvas` / primitive `Box` mocks, theme-aware
via `DaybookColors` / `LocalAccent`, no assets):

- `STREAKS` — a small "card" row with a flame glyph + `7` and a faint 7-dot week track,
  5 filled (accent) + 2 empty (`TextMuted`). ~120dp tall.
- `PRIVACY` — three stacked mini-rows: an accent swatch strip (5 dots), an "A" type
  sample, a lock glyph. Reuse `CardTints.Neutral` as the frame.

If the user would rather not add mocks (Q6), step 4 reuses `INTAKE` and steps 6/7 reuse
`YOURS` — the copy still lands.

## 2.5 `WizardStep` / VM notes

- `OnboardingTeachSteps` grows from 5 to 7 entries — `buildWizardSteps`,
  `isLastWizardStep`, `StepDots`, `OnboardingTourSteps` all derive from the list size, so
  no logic change; the existing `WizardStepTest` count assertions update to 7.
- `configureReview()` / `reviewMode` path untouched apart from `showCreateHabit` already
  being `false` there.
- No new route, no `MainActivity` gate change beyond passing nothing new (the VM owns
  `hasExistingData`).

**Item 2 deliverables:** rewrite `OnboardingTeachSteps` with the §2.2 copy; add
`STREAKS`/`PRIVACY` to `TeachIllustration` + mocks (Q6); add `hasExistingData` to the VM +
inject the two repos; branch `ReadyStep` copy + link on `showCreateHabit`; update
`WizardStepTest` + add `HasExistingDataTest`.

---

# ITEM 3 — Themed tint variants (Dark style + Light style pickers)

The user wants to choose a **background style** independent of the accent. New: a
"Dark style" picker and a "Light style" picker in Settings → Appearance, alongside the
Dark/Light/System toggle.

## 3.1 Token architecture

Today (`ui/theme/Tokens.kt`): two constants `DaybookColorsDark` / `DaybookColorsLight` of
type `DaybookColorScheme` (13 roles), provided through `LocalDaybookColors`; `DaybookColors`
is a `@Composable`-getter shim over that local. `isDaybookDarkThemeActive` and
`CardTints.light` detect dark by **referential equality** `=== DaybookColorsDark`.

A "style" swaps the **ground + text roles** only. `success` / `warning` / `danger` /
`onSolid` are per-*mode* constants shared by every style in that mode. So:

**New file `ui/theme/ThemeStyle.kt`:**

```kotlin
// Per-mode signal colours — identical across all styles of that mode. Values are today's.
private val DarkSignals  = Signals(success = Color(0xFF4ADE80), warning = Color(0xFFFACC15),
                                   danger = Color(0xFFF87171), onSolid = Color(0xFF0B0D0F))
private val LightSignals = Signals(success = Color(0xFF15803D), warning = Color(0xFFB45309),
                                   danger = Color(0xFFDC2626), onSolid = Color(0xFFFFFFFF))

data class StyleGround(                 // the 9 roles a style controls
    val bg: Color, val surface: Color, val surfaceElevated: Color,
    val outline: Color, val hairline: Color, val border: Color,
    val textPrimary: Color, val textMuted: Color, val textFaint: Color
)

enum class DarkStyle(val storageKey: String, val label: String, internal val ground: StyleGround) {
    CHARCOAL("CHARCOAL", "Charcoal", GroundCharcoal),   // == today's DaybookColorsDark
    AMOLED  ("AMOLED",   "True black", GroundAmoled),
    WARM    ("WARM",     "Espresso",  GroundWarm),
    NAVY    ("NAVY",     "Midnight",  GroundNavy);
    // + SLATE — pending Q3
    companion object { val DEFAULT = CHARCOAL
        fun fromKeyOrDefault(k: String?) = entries.firstOrNull { it.storageKey == k } ?: DEFAULT }
}

enum class LightStyle(val storageKey: String, val label: String, internal val ground: StyleGround) {
    PAPER("PAPER", "Paper", GroundPaper),               // == today's DaybookColorsLight
    PURE ("PURE",  "Pure white", GroundPure),
    CREAM("CREAM", "Warm cream", GroundCream),
    SEPIA("SEPIA", "Sepia", GroundSepia);
    // + MIST — pending Q3
    companion object { val DEFAULT = PAPER
        fun fromKeyOrDefault(k: String?) = entries.firstOrNull { it.storageKey == k } ?: DEFAULT }
}

fun darkSchemeFor(style: DarkStyle): DaybookColorScheme  = style.ground.toScheme(DarkSignals)
fun lightSchemeFor(style: LightStyle): DaybookColorScheme = style.ground.toScheme(LightSignals)

private fun StyleGround.toScheme(s: Signals) = DaybookColorScheme(
    bg, surface, surfaceElevated, outline, hairline, border,
    textPrimary, textMuted, textFaint, s.success, s.warning, s.danger, s.onSolid
)
```

**`Tokens.kt` changes (keep the names, keep non-composable readers valid):**

```kotlin
// still the app-wide default; now DERIVED from the default style so there is exactly one
// source of truth. Guarded byte-identical by DarkStyleTest (D8).
val DaybookColorsDark  = darkSchemeFor(DarkStyle.CHARCOAL)
val DaybookColorsLight = lightSchemeFor(LightStyle.PAPER)
```

`GroundCharcoal` / `GroundPaper` literals are exactly today's field values
(bg `#0B0D0F` … / bg `#FBFBF9` …). The test asserts
`darkSchemeFor(CHARCOAL) == DaybookColorsDark` against the *current* literal scheme copied
into the test, so a later accidental drift fails the build.

**Dark detection must stop using referential equality** (a non-Charcoal dark style is a
different object):

```kotlin
val LocalIsDark = staticCompositionLocalOf { true }          // NEW, provided by DaybookTheme
val isDaybookDarkThemeActive: Boolean @Composable get() = LocalIsDark.current
// CardTints.light:  private val light @Composable get() = !LocalIsDark.current
```

`isDaybookDarkThemeActive` has exactly **one** call site
(`ui/settings/SettingsScreen.kt:402`, accent-swatch preview) — trivial. `CardTints.light`
is internal to `Tokens.kt`.

## 3.2 `DaybookTheme` resolution (`ui/theme/Theme.kt`)

```kotlin
@Composable
fun DaybookTheme(
    accent: AccentColor = AccentColor.DEFAULT,
    fontChoice: FontChoice = FontChoice.DEFAULT,
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    darkStyle: DarkStyle = DarkStyle.DEFAULT,      // NEW
    lightStyle: LightStyle = LightStyle.DEFAULT,   // NEW
    cornerScale: Float = 1f,                       // NEW — Item 4
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    ...
    val dark = when (themeMode) { DARK -> true; LIGHT -> false; SYSTEM -> systemDark }
    val colors = if (dark) darkSchemeFor(darkStyle) else lightSchemeFor(lightStyle)
    val accentColor = accent.colorFor(dark)

    val scheme = remember(dark, accent, darkStyle, lightStyle) {
        (if (dark) DarkScheme else LightScheme).copy(
            primary = accentColor, onPrimary = colors.onSolid,
            secondary = accentColor, tertiary = accentColor,
            // NEW — keep the M3 defaults (Switch, native dialogs, text-selection, etc.)
            // tracking the chosen STYLE, not just the Charcoal/Paper constants:
            background = colors.bg, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated, onSurfaceVariant = colors.textMuted,
            outline = colors.outline, outlineVariant = colors.outline, scrim = colors.bg
        )
    }
    val m3Shapes = remember(cornerScale) { scaledM3Shapes(cornerScale) }   // Item 4

    CompositionLocalProvider(
        LocalDaybookColors provides colors,
        LocalIsDark provides dark,                 // NEW
        LocalAccent provides accentColor,
        LocalDaybookShapes provides remember(cornerScale) { scaledAppShapes(cornerScale) },  // Item 4
        LocalReduceMotion provides reduce
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = m3Shapes, content = content)
    }
}
```

`DarkScheme` / `LightScheme` top-level `darkColorScheme(...)` / `lightColorScheme(...)`
vals stay (they still read `DaybookColorsDark.*` = the Charcoal/Paper defaults); the
`.copy(...)` above now overrides every ground role from the resolved `colors`, so a
non-default style is fully applied to M3 too.

## 3.3 State plumbing

- **Entity** `data/model/DataModel.kt` `AppSettings`:
  ```kotlin
  @ColumnInfo(name = "dark_style",  defaultValue = "CHARCOAL") val darkStyle: String = DarkStyle.DEFAULT.storageKey,
  @ColumnInfo(name = "light_style", defaultValue = "PAPER")    val lightStyle: String = LightStyle.DEFAULT.storageKey,
  @ColumnInfo(name = "corner_scale", defaultValue = "1.0")     val cornerScale: Float = 1f,   // Item 4
  ```
  Appended after `theme_mode`. Every `@ColumnInfo(defaultValue=…)` byte-matches the
  `MIGRATION_20_21` SQL. Device-local — **not** in `BackupModel`, `ContentHash`, sync,
  export (verify `data/backup/BackupModel.kt` and `data/sync/ContentHash.kt` don't
  enumerate `app_settings` — they don't today; nothing to change, just confirm).
- **DAO** `data/local/AppSettingsDao.kt` — three writers mirroring `updateThemeMode`:
  ```kotlin
  @Query("UPDATE app_settings SET dark_style = :v WHERE id = 1")  suspend fun updateDarkStyle(v: String)
  @Query("UPDATE app_settings SET light_style = :v WHERE id = 1") suspend fun updateLightStyle(v: String)
  @Query("UPDATE app_settings SET corner_scale = :v WHERE id = 1") suspend fun updateCornerScale(v: Float)
  ```
  *(These are additions to an existing DAO — sanctioned because they are the write path for
  the sanctioned `MIGRATION_20_21` columns, exactly like `updateThemeMode` was for
  `MIGRATION_19_20`. No `SELECT` shape changes.)*
- **Repository** `data/AppSettingsRepository.kt` — mirror the `setThemeMode` pattern
  (Room write **then** SharedPreferences mirror):
  ```kotlin
  suspend fun setDarkStyle(v: String)  { ensureRow(); dao().updateDarkStyle(v);  ThemePrefs.writeDarkStyle(context, v) }
  suspend fun setLightStyle(v: String) { ensureRow(); dao().updateLightStyle(v); ThemePrefs.writeLightStyle(context, v) }
  suspend fun setCornerScale(v: Float) { ensureRow(); dao().updateCornerScale(v); ThemePrefs.writeCornerScale(context, v) }
  fun readDarkStyleMirror(): String   = ThemePrefs.readDarkStyle(context)
  fun readLightStyleMirror(): String  = ThemePrefs.readLightStyle(context)
  fun readCornerScaleMirror(): Float  = ThemePrefs.readCornerScale(context)
  ```
- **SP mirror** — extend `data/ThemeModePrefs.kt` (rename to `ThemePrefs`, or add keys and
  leave the name) in the same `daybook_prefs` file: keys `dark_style` (def `"CHARCOAL"`),
  `light_style` (def `"PAPER"`), `corner_scale` (def `1.0f`). All reads `runCatching`-guarded
  like the existing `read()`.
- **ViewModels** — `SettingsViewModel` and `OnboardingViewModel` each expose:
  ```kotlin
  val darkStyle: StateFlow<DarkStyle> = observeSettings().map { DarkStyle.fromKeyOrDefault(it.darkStyle) }
      .stateIn(scope, SharingStarted.Eagerly, DarkStyle.fromKeyOrDefault(repo.readDarkStyleMirror()))
  // lightStyle + cornerScale identical shape; cornerScale seeded from readCornerScaleMirror()
  fun setDarkStyle(s: DarkStyle)  = safeLaunch { repo.setDarkStyle(s.storageKey) }
  fun setLightStyle(s: LightStyle) = safeLaunch { repo.setLightStyle(s.storageKey) }
  fun setCornerScale(v: Float)    = safeLaunch { repo.setCornerScale(clampCornerScale(v)) }
  ```
  `Eagerly` + SP-mirror seed = **zero flash** on cold start (same as `themeMode`).
- **`MainActivity`** — collect `darkStyle` / `lightStyle` / `cornerScale` from
  `onboardingViewModel` (next to the existing `themeMode` collect ~L279) and pass into
  `DaybookTheme(...)`.
- **`applyWindowTheme()`** (`MainActivity` ~L417) — the pre-inflate splash background must
  match the chosen *style*, not just dark/light. Do **not** add per-style
  `windowBackground` resources (combinatorial). Instead, after resolving `dark`:
  ```kotlin
  val groundBg =
      if (dark) DarkStyle.fromKeyOrDefault(ThemePrefs.readDarkStyle(this)).ground.bg
      else      LightStyle.fromKeyOrDefault(ThemePrefs.readLightStyle(this)).ground.bg
  window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(groundBg.toArgb()))
  setTheme(if (dark) R.style.Theme_Daybook_Dark else R.style.Theme_Daybook_Light)  // keeps status-bar icon polarity
  ```
  `DarkStyle`/`LightStyle` enums live in `com.daybook.app.ui.theme` — importable from the
  Activity. `ground` is `internal`; expose a tiny `val DarkStyle.bg get() = ground.bg`
  helper or make `ground` `public`.

## 3.4 Settings → Appearance UI (`ui/settings/SettingsScreen.kt` `AppearanceSettingsScreen`)

After the existing Theme `SegmentedControl` group and before "Accent color", add two
groups (D1 — both always shown):

```
SectionHeader("Dark style", subtitle = "Background palette used in dark mode.")
SettingsGroup { StyleSwatchRow(DarkStyle.entries, current = darkStyle, onPick = vm::setDarkStyle) }

Spacer(listGap)
SectionHeader("Light style", subtitle = "Background palette used in light mode.")
SettingsGroup { StyleSwatchRow(LightStyle.entries, current = lightStyle, onPick = vm::setLightStyle) }
```

`StyleSwatchRow` — a new small composable (put in `ui/components/` or inline): a
horizontally-scrollable `Row` of preview chips, one per style. Each chip: a rounded-rect
(`AppShapes.card`) ~72×56dp painted with the style's `bg`, with a smaller inset rect in the
style's `surface` and a 2-3px bar in `textPrimary`, an accent ring/`Check` when
`selected`, and the `label` beneath. This shows the actual palette rather than a colour
name. Reuse `Swatch` grammar (44dp target, `Check` on select) where practical.

Live preview: because `darkStyle`/`lightStyle` feed `DaybookTheme` at the Activity root,
picking one recomposes the whole app instantly — the Appearance screen itself restyles
under the user's finger. No extra work.

## 3.5 Style palettes (hex — full role tables)

`success`/`warning`/`danger`/`onSolid` are the per-mode `Signals` above and are the same
for every style. Only the 9 `StyleGround` roles differ. All tuned so `textPrimary` ≥ ~12:1
and `textMuted` ≥ ~4.6:1 on that style's own `bg`, and so all 5 accent values (used as
fills with `OnSolid`, or as glyph tints on `surface`+) stay ≥ 4.5:1. Final tuning is
expected on-device (the current `DaybookColorsLight` already carries a "Tuned on device"
note) — Q3 covers whether to keep the optional 5th style per mode.

### Dark styles

| role | **Charcoal** (default, = today) | **True black** (AMOLED) | **Espresso** (warm) | **Midnight** (navy) | *Slate (opt.)* |
|------|------|------|------|------|------|
| bg              | `#0B0D0F` | `#000000` | `#14100D` | `#0A0E16` | `#0E1113` |
| surface         | `#16181B` | `#0C0D0F` | `#1E1813` | `#121826` | `#181C1F` |
| surfaceElevated | `#1E2124` | `#16181B` | `#271F18` | `#1A2233` | `#21262A` |
| outline         | `#2A2D31` | `#26292E` | `#362B22` | `#283349` | `#2F363B` |
| hairline        | `#14FFFFFF` | `#14FFFFFF` | `#16FFF3E6` | `#14FFFFFF` | `#14FFFFFF` |
| border          | `#14FFFFFF` | `#14FFFFFF` | `#16FFF3E6` | `#14FFFFFF` | `#14FFFFFF` |
| textPrimary     | `#F2F3F5` | `#F2F3F5` | `#F4EFE9` | `#EEF1F6` | `#F1F3F5` |
| textMuted       | `#9AA0A6` | `#9AA0A6` | `#A79E92` | `#97A0B2` | `#9BA3AA` |
| textFaint       | `#6B7178` | `#6B7178` | `#766C60` | `#667085` | `#6B747C` |

### Light styles

| role | **Paper** (default, = today) | **Pure white** | **Warm cream** | **Sepia** | *Mist (opt.)* |
|------|------|------|------|------|------|
| bg              | `#FBFBF9` | `#FFFFFF` | `#FBF6EC` | `#F3E9D8` | `#F4F6F8` |
| surface         | `#FFFFFF` | `#FFFFFF` | `#FFFDF7` | `#FBF3E4` | `#FFFFFF` |
| surfaceElevated | `#F2F2EF` | `#F4F5F7` | `#F3EBDA` | `#EADFC8` | `#EBEEF2` |
| outline         | `#E2E2DE` | `#E4E6EA` | `#E6DCC6` | `#D9CBAD` | `#DDE1E7` |
| hairline        | `#14000000` | `#0F000000` | `#14000000` | `#1A000000` | `#12000000` |
| border          | `#14000000` | `#0F000000` | `#14000000` | `#1A000000` | `#12000000` |
| textPrimary     | `#1B1D20` | `#16181B` | `#23201A` | `#2E2718` | `#191C20` |
| textMuted       | `#5B6068` | `#565B63` | `#6A6253` | `#6E634B` | `#565D66` |
| textFaint       | `#8A9099` | `#878D96` | `#9A9382` | `#9C9176` | `#868E99` |

`CardTint` sets (`CardTintsDark` / `CardTintsLight`, the pastel per-item card tints) are
**not** style-parameterized this round — they keep their current values and still read via
`LocalIsDark`. (A future round could give warm/navy styles their own pastel sets; out of
scope now, and the current pastels sit acceptably on all four dark grounds.) Note this
limitation for the user.

## 3.6 `MIGRATION_20_21`  — the only schema change this round

**Route approval through Q1 + Q2 + Q4 before the implementer writes it.**

`data/local/Migrations.kt` (append after `MIGRATION_19_20`):

```kotlin
/**
 * v20 -> v21 (UX refinement round). THREE additive columns on app_settings, no table
 * rebuild, no row rewrite. All device-local — NOT in BackupModel, NOT in any export, NOT
 * in ContentHash / the sync loop — same as every app_settings column since v16.
 *   dark_style   — DarkStyle.storageKey  ("CHARCOAL" default == today's dark palette)
 *   light_style  — LightStyle.storageKey ("PAPER"    default == today's light palette)
 *   corner_scale — global corner-radius multiplier, REAL, 1.0 == today's radii
 * Each DEFAULT byte-matches the @ColumnInfo(defaultValue=…) Kotlin default, so every
 * existing row reads the current look and nothing changes until the user opts in.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN dark_style TEXT NOT NULL DEFAULT 'CHARCOAL'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN light_style TEXT NOT NULL DEFAULT 'PAPER'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN corner_scale REAL NOT NULL DEFAULT 1.0")
    }
}
```

- `data/local/AppDatabase.kt` — `version = 20` → `version = 21`. `exportSchema = true`
  already; a build regenerates
  `app/schemas/com.daybook.app.data.local.AppDatabase/21.json`.
- `di/DatabaseModule.kt` — `import …MIGRATION_20_21` and append it to the
  `.addMigrations(...)` list. `fallbackToDestructiveMigrationFrom(1)` and
  `…OnDowngrade()` stay.
- `AppSettings` entity — the 3 fields from §3.3 (Room maps `Float` ↔ `REAL`).
- **Do not** touch `BackupModel.kt`, `PayloadCodec.kt`, `ContentHash.kt`,
  `CloudSyncRepository.kt`, `ExportImportRepository.kt`, `MonthPartitioner.kt`,
  `JsonUtils.kt`. Confirm by grep that none reference `app_settings` columns (they don't).
- `MigrationTest` (androidTest) — add a `20 → 21` case if the file enumerates pairs;
  it only needs to `compileDebugAndroidTestKotlin` (no device).

## 3.7 Item 3 tests

`DarkStyleTest` / `LightStyleTest` per the gate section: default resolution, garbage-key
fallback, **byte-identity of `CHARCOAL`/`PAPER` vs the current constants**, and a
`textPrimary`/`textMuted` contrast floor per style (pure ratio helper in-test).

**Item 3 deliverables:** new `ui/theme/ThemeStyle.kt`; `Tokens.kt` (derive the two
constants + `LocalIsDark` + repoint `isDaybookDarkThemeActive` / `CardTints.light`);
`Theme.kt` (`DaybookTheme` params + `.copy` ground roles); `ThemePrefs` key additions;
`AppSettings` + `AppSettingsDao` + `AppSettingsRepository` + `SettingsViewModel` +
`OnboardingViewModel`; `MainActivity` (collect + pass + `applyWindowTheme` bg drawable);
`AppearanceSettingsScreen` two groups + `StyleSwatchRow`; `Migrations.kt` +
`AppDatabase.kt` + `DatabaseModule.kt` + `21.json`; the two style tests.

---

# ITEM 4 — Configurable corner roundness

A single global multiplier on the rectangular-ish shape tokens, driven by a slider on
Settings → Appearance. Default `1.0` = today's look (D5).

## 4.1 What `AppShapes` looks like today (`ui/theme/Tokens.kt` `object AppShapes`)

| token | value | scope |
|---|---|---|
| `card`     | `RoundedCornerShape(14.dp)` | SoftCard / FormGroup / SettingsGroup / progress cards |
| `button`   | `RoundedCornerShape(12.dp)` | PrimaryButton / GhostButton |
| `field`    | `RoundedCornerShape(10.dp)` | text fields |
| `pill`     | `RoundedCornerShape(10.dp)` | chips, stat pills, mini badges (a rounded rect, **not** a stadium) |
| `tile`     | `RoundedCornerShape(12.dp)` | icon tiles, menu-row icon squares, `Swatch` |
| `sheet`    | `RoundedCornerShape(top 20.dp)` | ModalBottomSheet |
| `nav`      | `RoundedCornerShape(top 18.dp)` | FloatingPillNav bar |
| `dialog`   | `RoundedCornerShape(16.dp)` | AlertDialog / DaybookAlertDialog |
| `segmented`| `RoundedCornerShape(50)` | SegmentedControl track/pill — **stadium, never scale** |
| `navPill`  | `RoundedCornerShape(28.dp)` | Detail floating-nav footprint (near-stadium) |

M3 `Shapes` (`Theme.kt` top-level `DaybookShapes` val): `extraSmall=field`, `small=button`,
`medium=card`, `large=dialog`, `extraLarge=RoundedCornerShape(20.dp)`.

`AppShapes` is referenced **76×**, all inside `@Composable`s (via `clip()` / `background()` /
`border()` / `shape =`), **except one file-scope reader**: `Theme.kt`'s `DaybookShapes`
val. Circular elements (avatars, `CircleIconButton`, dots, day circles, `StepDots`) use
`CircleShape` **directly**, not `AppShapes` — so they are inherently immune to the scale.

## 4.2 Recommended range / mapping

- **Range `0.0f … 2.0f`**, **default `1.0f`.** Continuous slider, live update.
  - `0.0` → every scalable token `RoundedCornerShape(0.dp)` = perfectly square.
  - `1.0` → today's values exactly.
  - `2.0` → `card` 28.dp, `button` 24.dp, `dialog` 32.dp, `sheet`-top 40.dp — "very
    rounded" without going comical. (Q7 asks whether to cap at 1.75.)
- **Per-token:** `scaled(baseDp) = (baseDp * scale).coerceAtLeast(0.dp)`. Only the
  numeric-dp tokens scale. `segmented` stays `RoundedCornerShape(50)` unconditionally.
  `navPill` — **recommend also keep fixed** at `RoundedCornerShape(28.dp)` so the Detail
  nav keeps reading as a floating pill; Q7 confirms.
- **`sheet` / `nav`** are top-corners-only — scale the two top values, keep bottom `0.dp`.
- Slider label: **"Corners"** (Q8 — alternative "Roundness"). Optionally show a live
  numeric like `1.0×` or map to words (Square · Default · Round). Recommend a bare slider
  with min/max end captions "Square" / "Round" and the current card preview visibly
  reshaping.

## 4.3 Token architecture

**New — `ui/theme/Shapes.kt` (or appended to `Tokens.kt`):**

```kotlin
const val MIN_CORNER_SCALE = 0f
const val MAX_CORNER_SCALE = 2f
const val DEFAULT_CORNER_SCALE = 1f
fun clampCornerScale(v: Float): Float = v.coerceIn(MIN_CORNER_SCALE, MAX_CORNER_SCALE)

@Immutable
data class DaybookShapeScheme(
    val card: RoundedCornerShape, val button: RoundedCornerShape, val field: RoundedCornerShape,
    val pill: RoundedCornerShape, val tile: RoundedCornerShape, val sheet: RoundedCornerShape,
    val nav: RoundedCornerShape, val dialog: RoundedCornerShape,
    val segmented: RoundedCornerShape, val navPill: RoundedCornerShape
)

// pure — ScaledAppShapesTest
fun scaledAppShapes(scale: Float): DaybookShapeScheme {
    val s = clampCornerScale(scale)
    fun r(dp: Float) = RoundedCornerShape((dp * s).coerceAtLeast(0f).dp)
    fun top(dp: Float) = RoundedCornerShape(topStart = (dp * s).coerceAtLeast(0f).dp,
                                            topEnd   = (dp * s).coerceAtLeast(0f).dp)
    return DaybookShapeScheme(
        card = r(14f), button = r(12f), field = r(10f), pill = r(10f), tile = r(12f),
        sheet = top(20f), nav = top(18f), dialog = r(16f),
        segmented = RoundedCornerShape(50),          // never scales
        navPill = RoundedCornerShape(28.dp)          // fixed (Q7)
    )
}

// pure — builds the M3 Shapes from the same scale
fun scaledM3Shapes(scale: Float): Shapes {
    val a = scaledAppShapes(scale)
    return Shapes(extraSmall = a.field, small = a.button, medium = a.card, large = a.dialog,
                  extraLarge = RoundedCornerShape((20f * clampCornerScale(scale)).coerceAtLeast(0f).dp))
}

val LocalDaybookShapes = staticCompositionLocalOf { scaledAppShapes(DEFAULT_CORNER_SCALE) }
```

**`object AppShapes` becomes a `@Composable`-getter shim** over `LocalDaybookShapes`
(identical trick to `DaybookColors`):

```kotlin
object AppShapes {
    val card: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.card
    val button: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.button
    // … field, pill, tile, sheet, nav, dialog, segmented, navPill
}
```

- All 76 call sites compile unchanged (they're inside composables).
- **The one file-scope reader** — `Theme.kt`'s `private val DaybookShapes = Shapes(...)`
  — is **deleted**; `DaybookTheme` now passes `scaledM3Shapes(cornerScale)` (memoised) to
  `MaterialTheme`.
- Keep the raw base values available for tests: either leave a
  `object AppShapesBase { val card = RoundedCornerShape(14.dp); … }` or have the test call
  `scaledAppShapes(1f)` and compare to hardcoded expected dps. Prefer the latter (no new
  public surface).

## 4.4 Delivery + live drag

- `DaybookTheme` gains `cornerScale: Float = 1f` (already shown in §3.2); provides
  `LocalDaybookShapes provides remember(cornerScale){ scaledAppShapes(cornerScale) }` and
  passes `scaledM3Shapes(cornerScale)` to `MaterialTheme`.
- `MainActivity` collects `cornerScale` from `onboardingViewModel` (seeded from the SP
  mirror, `Eagerly`).
- **Slider** in `AppearanceSettingsScreen` (its own `SettingsGroup` under the style
  pickers):
  ```kotlin
  val cornerScale by viewModel.cornerScale.collectAsStateWithLifecycle()
  var live by remember(cornerScale) { mutableFloatStateOf(cornerScale) }
  Slider(
      value = live,
      onValueChange = { live = it; viewModel.setCornerScale(it) },   // live persist -> live recomposition
      valueRange = MIN_CORNER_SCALE..MAX_CORNER_SCALE
  )
  ```
  `setCornerScale` writes Room on every change; that's a single-row `UPDATE` on the settings
  table — same cost profile as the accent picker's per-tap write. The Room→StateFlow→
  `DaybookTheme` path gives the live restyle. If profiling on device shows jank, throttle
  in the VM with `setCornerScale` rounding to 2 decimals and only writing on change (note
  for implementer — do **not** add a coroutines-`debounce` test dependency).
- **SP mirror** (`ThemePrefs`) — `corner_scale` Float key, read in
  `readCornerScaleMirror()`; `applyWindowTheme()` does **not** need it (window bg is a flat
  colour; shape flash is imperceptible and there's nothing rounded in the pre-inflate
  splash).
- Clamp: `clampCornerScale` is applied in `scaledAppShapes`, in `setCornerScale`, and in
  `fromKeyOrDefault`-style read. Persisted out-of-range values (corrupt row) resolve to a
  clamped value, never crash.

## 4.5 Item 4 tests

`ScaledAppShapesTest`:
- `scaledAppShapes(1f)` → `card` topStart == 14.dp (etc. for all 8 scalable tokens).
- `scaledAppShapes(0f)` → all 8 scalable tokens == `RoundedCornerShape(0.dp)`;
  `segmented` == `RoundedCornerShape(50)`; `navPill` == `RoundedCornerShape(28.dp)`.
- `scaledAppShapes(2f)` → `card` topStart == 28.dp; monotonic increase 0→1→2.
- `clampCornerScale(-1f) == 0f`, `clampCornerScale(9f) == 2f`, `clampCornerScale(1f) == 1f`.
- `scaledM3Shapes(1f).medium == scaledAppShapes(1f).card`.

**Item 4 deliverables:** new `ui/theme/Shapes.kt`; `AppShapes` → shim + `LocalDaybookShapes`;
delete `Theme.kt` `DaybookShapes` val, wire `scaledM3Shapes` + `LocalDaybookShapes` in
`DaybookTheme`; `corner_scale` column (folded into `MIGRATION_20_21`) + entity/DAO/repo/VM/
mirror; `MainActivity` collect + pass; `AppearanceSettingsScreen` slider group;
`ScaledAppShapesTest`.

---

## Consolidated file-touch list

**New files**
- `ui/theme/ThemeStyle.kt` — `DarkStyle` / `LightStyle` / `StyleGround` / `Signals` /
  `darkSchemeFor` / `lightSchemeFor`.
- `ui/theme/Shapes.kt` — `DaybookShapeScheme` / `scaledAppShapes` / `scaledM3Shapes` /
  `clampCornerScale` / `LocalDaybookShapes` (may instead be appended to `Tokens.kt`).
- `ui/onboarding/OnboardingIllustrations.kt` — add `STREAKS` + `PRIVACY` mocks (Q6).
- Tests: `ScaledAppShapesTest`, `DarkStyleTest`, `LightStyleTest`, `HasExistingDataTest`
  (all `app/src/test/java/com/daybook/app/...`).
- (maybe) `ui/components/StyleSwatchRow.kt`.

**Edited**
- `ui/components/SegmentedControl.kt` — §1.1.
- `ui/components/Components.kt` — findings 4, 5, 6.
- `ui/components/WeekStrip.kt` — finding 7.
- `ui/components/SortSheet.kt` — finding 8.
- `ui/TimePickerComponents.kt` — finding 9.
- `ui/settings/SettingsScreen.kt` — findings 2/3 (via SegmentedControl), 10, L1; Item 3
  two style groups + Item 4 slider in `AppearanceSettingsScreen`; repoint
  `isDaybookDarkThemeActive` call (still compiles).
- `ui/settings/AboutSettingsScreen.kt` — finding 11.
- `ui/components/Forms.kt` — finding 12 (optional).
- `ui/onboarding/OnboardingViewModel.kt` — 7 teach steps, `hasExistingData`, inject repos,
  `darkStyle`/`lightStyle`/`cornerScale` flows.
- `ui/onboarding/OnboardingScreen.kt` — `ReadyStep` copy/link branch, new illustrations.
- `ui/MainActivity.kt` — collect + pass `darkStyle`/`lightStyle`/`cornerScale`;
  `applyWindowTheme()` style-aware window bg.
- `ui/theme/Tokens.kt` — derive `DaybookColorsDark/Light`; `LocalIsDark`; repoint
  `isDaybookDarkThemeActive` / `CardTints.light`; `AppShapes` shim; `LocalDaybookShapes`.
- `ui/theme/Theme.kt` — `DaybookTheme` params (`darkStyle`, `lightStyle`, `cornerScale`),
  `.copy` ground roles, `scaledM3Shapes`, provide `LocalIsDark` + `LocalDaybookShapes`;
  delete `DaybookShapes` val.
- `data/ThemeModePrefs.kt` → `ThemePrefs` (or keep name) — 3 new keys.
- `data/AppSettingsRepository.kt` — `setDarkStyle/setLightStyle/setCornerScale` +
  mirror reads.
- `data/local/AppSettingsDao.kt` — `updateDarkStyle/updateLightStyle/updateCornerScale`.
- `data/model/DataModel.kt` — 3 `AppSettings` columns.
- `data/local/Migrations.kt` — `MIGRATION_20_21`.
- `data/local/AppDatabase.kt` — `version = 21`.
- `di/DatabaseModule.kt` — register `MIGRATION_20_21`.
- `ui/settings/SettingsViewModel.kt` — `darkStyle/lightStyle/cornerScale` flows + setters.
- `app/schemas/com.daybook.app.data.local.AppDatabase/21.json` — regenerated by build.
- Existing tests: `WizardStepTest` (5→7), `AccentColorThemeTest` (if it pins the literal
  scheme), `MigrationTest` androidTest (add 20→21 if it enumerates).

**Must NOT change:** `data/backup/BackupModel.kt`, `data/sync/*` (`ContentHash`,
`PayloadCodec`, `SyncLogic`, `CloudSyncRepository`, `MonthPartitioner`),
`data/ExportImportRepository.kt`, `util/JsonUtils.kt`, versionCode/Name, any dependency /
SDK / Gradle version. If the implementer believes any of these must change → **STOP and
ask.**

---

## Sequencing for the implementer

1. **Item 1 SegmentedControl fix + finding table** — smallest, unblocks the screenshots,
   no schema. Build + gate.
2. **Item 3 + Item 4 schema** — `MIGRATION_20_21` (3 cols), entity, DAO, repo, `21.json`,
   `DatabaseModule`. Build (schema green) before touching UI.
3. **Item 3 theme tokens** — `ThemeStyle.kt`, `Tokens.kt`, `Theme.kt`, `LocalIsDark`,
   `ThemePrefs`. Build.
4. **Item 4 shape tokens** — `Shapes.kt`, `AppShapes` shim, `Theme.kt` M3 shapes. Build.
5. **VMs + MainActivity** wiring for style + corner scale. Build.
6. **Appearance UI** — style groups + corner slider.
7. **Item 2 onboarding** — copy, illustrations, `hasExistingData`, Ready branch.
8. **Legibility rows 2–12 + L1.**
9. Full 5-command gate + write a short `UX_REFINEMENT_PROGRESS.md` (deviations, device
   follow-ups). No commit, no APK push unless the user asks.

Device follow-ups to hand back: segmented pill visible in every theme/accent/font;
Dark/Light style pickers restyle live and persist across cold start with no flash; corner
slider reshapes cards live and persists; onboarding reads well and the CTA is absent for a
signed-in account with restored data; all 4 style palettes eyeballed at each of the 5
accents.
