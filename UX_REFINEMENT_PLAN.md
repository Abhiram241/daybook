# UX_REFINEMENT_PLAN.md

**Status: APPROVED / LOCKED.** Every decision is settled — there are no open questions.
A fresh implementer with no other context can execute this start to finish. **Plan only — no
`.kt` was changed to produce this.**

Baseline: branch `main` at commit `bde7c7f` (clean tree) — the build-21/22 keyboard+font
batch, the battery fix, and the **UX overhaul** (`UX_OVERHAUL_PLAN.md` /
`UX_OVERHAUL_PROGRESS.md`) are all already in. DB **v20** (`MIGRATION_19_20` =
`app_settings.theme_mode`). versionCode 23 / versionName "0.5.6" — **do not change**.
`compose-bom 2024.12.01`, minSdk 26, target 34. Single Gradle module `:app` (so `internal`
reaches every file in the app — this matters in §3.3).

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

## Standing decisions (carried from earlier rounds)

- **D1. Theme-style picker shape.** A "Dark style" picker **and** a "Light style" picker
  in Settings → Appearance, working *alongside* the existing Dark / Light / System toggle.
  Accent colour stays its own separate setting. Both style pickers are always visible
  (user can configure the mode they're not currently in).
- **D2. Onboarding is the only teaching surface this round.** No settings help-text, no
  FAQ, no tooltips. All feature explanation lives in the onboarding tour. (A one-line
  subtitle that says what a *control does* is a label, not help text — see LD12.)
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

---

## Decisions (locked)

The 15 questions that were open at plan time, each now decided. **Do not re-litigate, do
not ask about them** — the rest of this document is written to match.

### LD1 — Dark style set: **4 styles.**
`CHARCOAL` "Charcoal" (default, byte-identical to today) · `AMOLED` "True black" ·
`WARM` "Espresso" · `NAVY` "Midnight". Hexes in §3.5.
*Why:* four grounds cover the real axes users ask for (the current look, a true-black OLED
saver, a warm ground, a cool ground) and fit one 4-chip swatch row without scrolling; more
options past that are variations of these, not new choices.

### LD2 — Light style set: **4 styles.**
`PAPER` "Paper" (default, byte-identical to today) · `PURE` "Pure white" · `CREAM`
"Warm cream" · `SEPIA` "Sepia". Hexes in §3.5.
*Why:* mirrors LD1 one-for-one (default / neutral-extreme / warm / strongest warm), so the
two pickers read as the same control twice and the test matrix stays 4 × 4.

### LD3 — The optional 5th style per mode (`SLATE`, `MIST`): **dropped.**
Neither ships. The style tables in §3.5 have exactly 4 columns each.
*Why:* Slate (`#0E1113`) is within a couple of percent luminance of Charcoal and Mist
(`#F4F6F8`) of Paper — users cannot tell them apart on a phone, so they add a swatch, a
migration-safe string value and two more test rows for no perceivable choice.

### LD4 — `CardTint` pastels are **not** style-parameterized this round.
`CardTintsDark` / `CardTintsLight` keep today's values and are selected by mode only
(via `LocalIsDark`), for every style. §3.5 states this as a known limitation to hand back.
*Why:* the six pastels sit acceptably on all four grounds of their mode, and giving each
style its own 7-tint set is 56 new colour literals — a separate round's worth of tuning,
and pure scope creep against an additive-only round.

### LD5 — Migration shape: **one `MIGRATION_20_21`, three additive columns**, and
`corner_scale` is stored as SQLite **`REAL NOT NULL DEFAULT 1.0`** (Kotlin `Float`).
*Why:* one migration per round is the project's standing discipline (v16→v20 each added
`app_settings` columns the same way); a `REAL` stores the slider value directly, whereas an
integer step-index would hard-code the step count into the schema and need a second
migration the first time the range changes.

### LD6 — The style pickers do **not** appear in onboarding.
They live only in Settings → Appearance. Onboarding step 7 *mentions* them in copy
("a light or dark look with a background style") and points at Settings.
*Why:* onboarding must stay a tour, not a setup wizard; adding pickers there would also put
a "change your theme" decision in front of a user before they have seen the app — and D5
says the default look is the right first impression.

### LD7 — Onboarding deck: **7 `Teach` steps + `PermissionPrimer` + `Ready`**, copy shipped
verbatim from §2.2. Tone: second person, plain, concrete verbs, sentence case, ≤ 3
sentences per step, no exclamation marks and no marketing adjectives.
*Why:* the user's complaint was "features not explained properly", which is a coverage
problem (streaks and Journal were never named), not a length problem — 7 short steps fix
coverage while staying skimmable.

### LD8 — Illustrations: **add `STREAKS` and `PRIVACY`**; step 4 (Journalling) **reuses
`INTAKE`**. No new `JOURNAL` mock.
*Why:* streaks and privacy are the two new steps with nothing to show, so they earn a mock;
a Journal mock would be a near-duplicate of the intake chat-bubble mock for one step.

### LD9 — CTA-hiding signal: **reactive row count through the existing repository Flows**,
exposed as a **tri-state `StateFlow<Boolean?>`** (`null` = not read yet). The link renders
only when `hasExistingData == false && !reviewMode`; while `null`, `ReadyStep` reserves the
link's height and renders nothing there. No DAO change, no schema change.
*Why:* seeding the flow with `false` (the obvious `stateIn` default) would flash "Create
your first habit" at a returning user for one frame while the cloud bootstrap lands, which
is exactly what D4 forbids; the tri-state removes the flash without a layout jump.

### LD10 — Corner-scale range: **0.0 … 1.75, default 1.0, in 0.25 detents** (8 positions:
0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75).
*Why:* 1.75 keeps every scaled token below the fixed 28dp `navPill` and keeps `dialog` at
28dp — the top of M3's normal range — so the app still reads as Daybook at max; discrete
detents make "back to exactly 1.0" reachable by touch, and cap the Room writes of a drag at
8 instead of hundreds.

### LD11 — Token scope of the scale: the **8 numeric `AppShapes` tokens** scale
(`card`, `button`, `field`, `pill`, `tile`, `sheet`-top, `nav`-top, `dialog`) plus the M3
`extraLarge` (20dp base). **`segmented` stays `RoundedCornerShape(50)` and `navPill` stays
`RoundedCornerShape(28.dp)` — both fixed, at every scale.** `CircleShape` call sites are
untouched by construction.
*Why:* `segmented` and `navPill` are identity shapes — a squared segmented track stops
reading as a track, and a squared Detail nav stops reading as a floating pill; keeping both
pinned also means 0.0 still produces a usable app rather than an all-rectangles one.

### LD12 — Slider presentation: label **"Corners"**, subtitle *"How rounded cards, buttons
and fields look."*, end captions **"Square"** / **"Round"**, and a right-aligned live value
caption (`"1.0× · Default"`, `"Square"` at 0, else `"1.25×"`). Persist on **each detent
change** (the VM drops a write when the clamped value is unchanged).
*Why:* "Corners" is the noun the setting changes and matches the one-word neighbours
(Theme / Accent color / Font); a bare unlabelled slider has nothing for TalkBack to
announce, so the caption doubles as the `stateDescription`.

### LD13 — On-accent ink is **per-accent, computed**: new pure `onAccentInk(accent)` +
`LocalOnAccent` + a `DaybookColors.OnAccent` shim member, used everywhere content sits on
an accent fill (§1.3).
*Why:* the plan's earlier claim that all five light accents clear 4.5:1 against white is
**wrong** — measured, Mint `#0F9488` is 3.74:1, Amber `#B7791F` 3.64:1 and Coral `#E23D5B`
4.16:1, so the selected segmented label, chip label and primary-button label fail AA in
light mode today. Picking whichever ink (near-black `#0B0D0F` / white `#FFFFFF`) has the
higher ratio fixes all three and returns `#0B0D0F` for **every** accent in dark mode, i.e.
dark is byte-identical to today.

### LD14 — `TextFaint` becomes **decoration-only**; every `TextFaint` that carries
information moves to `TextMuted`. That means §1.2 rows 7–11 **and row 12 (the text-field
placeholder)** all ship; row 13 (illustration bars) and L3 (Week starts on) are no-ops, and
L2 gets the subtitle *"How much the Today greeting says."*
*Why:* `TextFaint` measures ~4.0:1 on the dark ground and ~3.1:1 on paper — under AA for
any normal-size text, so it can only be defensible for non-informational marks; one rule is
also easier for a future implementer to keep than a case-by-case list.

### LD15 — Naming / placement: rename `data/ThemeModePrefs.kt` → **`data/ThemePrefs.kt`**
(`object ThemePrefs`, same `daybook_prefs` file, same `theme_mode` key, 3 call sites). New
files are **`ui/theme/ThemeStyle.kt`**, **`ui/theme/Shapes.kt`** and
**`ui/components/StyleSwatchRow.kt`** (not appended to `Tokens.kt`). `StyleGround` and the
enums' `ground` property stay **`internal`** — no public accessor, no helper extension.
*Why:* the mirror now carries four keys, so "ThemeMode" in its name would be a lie, and the
rename is 3 compiler-caught edits; and because the app is a single `:app` module,
`internal` is already visible from `MainActivity`, so the "make `ground` public" worry in
the draft was unfounded.

---

## Verification gate (run at the end, all must pass)

```
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
  ./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

- `testDebugUnitTest` — the ~469 existing tests stay green + the new pure tests below pass.
- `assembleRelease` — R8 + lintVitalRelease clean, real-keystore signed (SHA-1
  `39e62d0f…`, see `RELEASE_SIGNING.md`).
- `app/schemas/com.daybook.app.data.local.AppDatabase/21.json` regenerated on disk (it is
  currently untracked for `20.json` too — the implementer must `git add` both when a commit
  eventually happens; **no commit this round**).
- Manual/device follow-ups listed per item.

New pure JUnit4 test files (no Mockito / Robolectric / coroutines-test — this project has
none), all under `app/src/test/java/com/daybook/app/...`:

- **`ScaledAppShapesTest`** — `scaledAppShapes(1f)` equals today's radii (card 14dp, button
  12dp, field 10dp, pill 10dp, tile 12dp, sheet-top 20dp, nav-top 18dp, dialog 16dp);
  `scaledAppShapes(0f)` → all 8 scalable tokens 0dp; `scaledAppShapes(1.75f)` → card 24.5dp,
  dialog 28dp; monotonic across 0 → 1 → 1.75; `segmented` == `RoundedCornerShape(50)` and
  `navPill` == `RoundedCornerShape(28.dp)` at **every** scale (LD11); `clampCornerScale`
  bounds (`-1f → 0f`, `9f → 1.75f`, `1f → 1f`); `scaledM3Shapes(1f).medium ==
  scaledAppShapes(1f).card`.
- **`DarkStyleTest` / `LightStyleTest`** — `fromKeyOrDefault(null|garbage)` → DEFAULT;
  `DEFAULT == CHARCOAL` / `PAPER`; `darkSchemeFor(CHARCOAL) == DaybookColorsDark` and
  `lightSchemeFor(PAPER) == DaybookColorsLight` (byte-identity guard, D8); `entries.size == 4`
  (LD1/LD2/LD3); every style's `textPrimary` ≥ 12:1 and `textMuted` ≥ 4.5:1 against its own
  `bg` **and** its own `surfaceElevated`, and `textFaint` ≥ 3:1 against its own `bg` (tiny
  WCAG relative-luminance helper lives in the test file).
- **`OnAccentInkTest`** (LD13) — for all 5 accents × dark: `onAccentInk(accent.dark) ==
  Color(0xFF0B0D0F)` (proves dark is unchanged); × light: the returned ink is ≥ 4.5:1
  against the accent; the helper picks the higher-contrast of the two candidate inks.
- **`HasExistingDataTest`** — `hasExistingData(habitCount, intakeCount)` predicate.
- Extend **`AccentColorThemeTest`** if it currently pins the dark scheme by literal.
- Update **`WizardStepTest`** — teach-step count 5 → 7 (LD7).

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
(`OnSolid` = near-black `#0B0D0F`) then sits on the bare dark track with no fill behind
it — invisible. This is exactly what both screenshots show.

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
        .border(1.dp, DaybookColors.Border, AppShapes.segmented)   // 1.2 row 3 — give it an edge
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
            // 1.2 row 2 — unselected label was TextMuted (~6:1, reads broken with thin Literata).
            // 1.3 (LD13) — selected label uses OnAccent, not OnSolid.
            val contentColor = if (selected) DaybookColors.OnAccent else DaybookColors.TextPrimary
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

Why the `matchParentSize` wrapper rather than `matchParentSize()` directly on the pill: the
pill also needs a `width(segWidth)` and an animated `offset`, so nesting keeps those working
while the wrapper supplies a finite height. The content `Row`'s `heightIn(min = 36.dp)`
per segment is what gives the track a resolved height in the first place; it still grows
past 36.dp at large font scale (that was the point of the build-21 change).

**Selected-text contrast:** with §1.3 in place the selected label is `OnAccent`, which is
≥ 4.5:1 on every one of the 5 accents in both modes. This holds under every theme style
(Item 3) because the pill fill is the accent, never a style ground colour — no per-style
special-casing is needed for the segmented control.

**Test:** layout, so no unit test. Device/gate check: on Appearance and Today & calendar,
every segmented control shows a filled accent pill under the selected option with a
readable label, in Dark + Light × all 4 styles per mode × all 5 accents ×
Literata/Nunito/Mono at the largest system font size.

## 1.2 App-wide legibility findings (ranked)

Contrast ratios below are **measured** WCAG ratios of the semantic token on its real
background; "dark" = the Charcoal palette. For reference: `TextMuted` `#9AA0A6` on `Bg`
`#0B0D0F` = **7.37:1** (fine) and on `SurfaceElevated` `#1E2124` = **6.13:1** (fine);
`TextFaint` `#6B7178` on `Bg` = **3.95:1** (fails AA for normal text). On paper,
`TextFaint` `#8A9099` on `Bg` `#FBFBF9` = **3.10:1**. The theme migrated to
`LocalDaybookColors` in the overhaul, so all fixes below are theme-aware automatically —
just pick a better *role*, never a hardcoded `Color`.

| # | Severity | File:line | What's wrong | Fix |
|---|----------|-----------|--------------|-----|
| 1 | **Critical** | `ui/components/SegmentedControl.kt:82-90` | Selected pill collapses to height 0 → no highlight, near-black selected label on bare track. All 3 screenshot controls. | §1.1 — `matchParentSize` wrapper + `heightIn(min=36)` per segment + `Row.fillMaxWidth()`. |
| 2 | High | `ui/components/SegmentedControl.kt:93` | Unselected label = `TextMuted` at `NavLabel` (12sp Medium) on `SurfaceElevated` = 6.13:1 — reads as "disabled / broken" with thin Literata. | Unselected → `TextPrimary`; style `DaybookText.ButtonLabel` (14sp SemiBold). |
| 3 | Med | `ui/components/SegmentedControl.kt:67-73` | Track has no border; `SurfaceElevated` (`#1E2124`) vs page `Bg` (`#0B0D0F`) is a weak edge — the control doesn't read as a control until tapped. | Add `.border(1.dp, DaybookColors.Border, AppShapes.segmented)`. |
| 4 | High | `ui/components/Components.kt` `GhostButton` (~L607-620) | Disabled: border stays `Hairline` (white-8%, invisible) + text `TextFaint`. A disabled Ghost button is nearly undetectable (`QuietTimeRow` in Settings uses this). | Disabled border → `DaybookColors.Outline`; disabled text keep `TextFaint` but add a `DaybookColors.SurfaceElevated.copy(alpha=.4f)` fill so the shape reads. |
| 5 | Med | `ui/components/Components.kt` `CircleIconButton` (~L150-160) | Disabled bg forced to `SurfaceElevated` — identical to the enabled **Ghost** style; only the icon tint (`TextFaint` vs `TextPrimary`) distinguishes them. Ambiguous. | Disabled: bg `SurfaceElevated.copy(alpha=.5f)` + 1dp `Outline` border so "off" is visibly different from "ghost". |
| 6 | Med | `ui/components/Components.kt:209` `DaybookChip` | Unselected chip label = `TextMuted` on `SurfaceElevated` = 6.13:1. Intake filter chips. | Unselected label → `TextPrimary`; selected label → `OnAccent` (§1.3). |
| 7 | Med | `ui/components/WeekStrip.kt:434-435` | Future / dimmed day numbers = `TextFaint` on `Bg` = 3.95:1 dark / 3.10:1 light — day digits (titleMedium ~16sp SemiBold) barely legible. | Future/dimmed → `TextMuted` (still clearly de-emphasised vs `TextPrimary`, but readable). |
| 8 | Med | `ui/components/SortSheet.kt:258,273` | Unchecked radio ring + unchecked facet tick drawn in `TextFaint` — the "not selected" control is almost invisible against the sheet. | Unchecked tick → `DaybookColors.TextMuted`; unchecked ring → `DaybookColors.Outline` (a non-text control needs ≥ 3:1, which `Outline` clears on both grounds). |
| 9 | Low | `ui/TimePickerComponents.kt:92` | "no times added" helper text in `TextFaint`. It's real information, not decoration. | → `TextMuted`. |
| 10 | Low | `ui/settings/SettingsScreen.kt:324` | About-footer "Daybook / Version 0.5.6 (23)" in `TextFaint`. | → `TextMuted`. |
| 11 | Low | `ui/settings/AboutSettingsScreen.kt:66` | Same footer treatment (`TextFaint`). | → `TextMuted`. |
| 12 | Low | `ui/components/Forms.kt:130` | Text-field placeholder = `TextFaint` (3.95:1 dark / 3.10:1 light). | → `TextMuted` (**ships** — LD14). Placeholder stays clearly below `TextPrimary` (7.37:1 vs 17.53:1), so "empty field" still reads as empty. |
| 13 | Low | `ui/onboarding/OnboardingIllustrations.kt:157,170` | Mock "bars" drawn in `TextFaint` — decorative, inside an illustration. | **No change** (decoration, not content — LD14). Listed for completeness. |
| 14 | High | the 16 `DaybookColors.OnSolid` call sites | White/near-black ink on an accent fill is a fixed per-mode constant, so in **light** mode Mint (3.74:1), Amber (3.64:1) and Coral (4.16:1) fail AA on primary buttons, selected chips, the selected segment, solid circle buttons, avatars and the selected week-strip day. | §1.3 — `DaybookColors.OnAccent`. |

### 1.3 On-accent ink (LD13)

Measured contrast of the ink candidates against each accent fill:

| Accent | dark value | on `#0B0D0F` | light value | on `#FFFFFF` | on `#0B0D0F` | **light ink** |
|---|---|---|---|---|---|---|
| MINT     | `#2DD4BF` | 10.46 | `#0F9488` | 3.74 ✗ | **5.20** | near-black |
| LAVENDER | `#A78BFA` | 7.15  | `#7C5CE0` | **4.70** | 4.14 ✗ | white |
| CORAL    | `#FB7185` | 7.23  | `#E23D5B` | 4.16 ✗ | **4.68** | near-black |
| SKY      | `#60A5FA` | 7.66  | `#2563EB` | **5.17** | 3.77 ✗ | white |
| AMBER    | `#FBBF24` | 11.66 | `#B7791F` | 3.64 ✗ | **5.35** | near-black |

In **dark** mode near-black wins for all five accents, so the resolved ink is `#0B0D0F`
for every accent — **dark mode is byte-identical to today.** In light mode the default
accent (LAVENDER) also keeps white, so the shipped light default is unchanged too; only
Mint / Coral / Amber flip to near-black, and each of those is a straight AA fix.

**Implementation — in `ui/theme/Accent.kt` (pure, no Compose runtime needed):**

```kotlin
private const val INK_DARK = 0xFF0B0D0FL      // == DarkSignals.onSolid
private const val INK_LIGHT = 0xFFFFFFFFL     // == LightSignals.onSolid

/** WCAG relative luminance of a Compose [Color]. Pure — see OnAccentInkTest. */
internal fun relativeLuminance(c: Color): Double { /* sRGB -> linear, 0.2126/0.7152/0.0722 */ }

private fun contrast(a: Color, b: Color): Double {
    val l1 = relativeLuminance(a); val l2 = relativeLuminance(b)
    val hi = maxOf(l1, l2); val lo = minOf(l1, l2)
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * The higher-contrast ink for content sitting ON a fill painted with [accent].
 * Pure — see OnAccentInkTest.
 */
fun onAccentInk(accent: Color): Color {
    val dark = Color(INK_DARK); val light = Color(INK_LIGHT)
    return if (contrast(accent, dark) >= contrast(accent, light)) dark else light
}

/** Resolved ink for the current accent; provided by [DaybookTheme]. */
val LocalOnAccent = staticCompositionLocalOf { onAccentInk(AccentColor.DEFAULT.dark) }
```

- `Tokens.kt` gains one shim member: `val OnAccent: Color @Composable get() =
  LocalOnAccent.current` (alongside the existing `OnSolid`, which stays for any future
  non-accent solid).
- `DaybookTheme` provides `LocalOnAccent provides onAccentInk(accentColor)` and uses the
  same value for the M3 `onPrimary` (§3.2).
- **Repoint these `DaybookColors.OnSolid` reads to `DaybookColors.OnAccent`** — every one
  of them sits on an accent fill (verified by grep; `Swatch`'s `checkColor` default is only
  reached by the accent picker, because `TintPicker` passes its own):
  `ui/components/SegmentedControl.kt:93` · `ui/components/Components.kt:140` (`CircleStyle.Solid`),
  `:209` + `:251` (`DaybookChip` selected), `:458` (`Swatch` default `checkColor`), `:551` +
  `:565` (`PrimaryButton`) · `ui/components/Avatar.kt:109,115` ·
  `ui/components/Forms.kt:187` · `ui/components/WeekStrip.kt:433` ·
  `ui/components/SortSheet.kt:319` (`checkedThumbColor`) ·
  `ui/settings/SettingsScreen.kt:410` (accent swatch check), `:507` (switch thumb) ·
  `ui/lock/AppLockSettingsScreen.kt:138` (switch thumb) ·
  `ui/journal/HabitJournalChatScreen.kt:158` (own-message bubble on accent).
- Nothing else changes: `DaybookColorScheme.onSolid` keeps its current per-mode value and
  still feeds the M3 scheme's `onError` etc.

### Unclear-label findings (content, not colour — D2 limits scope)

| # | File | Label | Problem | Fix |
|---|------|-------|---------|-----|
| L1 | `ui/settings/SettingsScreen.kt` "Streaks" group (~L629+) | **"Show streak flames"** — subtitle *"Hides the flame pill on Today…"* | Label says **Show**, subtitle says **Hides** — direct contradiction. A correctness bug, not "help text". | Subtitle → *"The streak flame on Today and the streak figure on Detail stats. Turn this off to hide them."* |
| L2 | `ui/settings/SettingsScreen.kt:582-608` "Greeting" | Segmented **Full / Simple / Off** with no indication of what each does. | Ambiguous without trying all three. | **Ships** (LD14): add subtitle *"How much the Today greeting says."* |
| L3 | `ui/settings/SettingsScreen.kt:557-566` "Week starts on" | Options **Sun / Mon / Sat** only. | "Sat" with no context reads as a typo; the full week isn't offered. | **No change** — product decision, out of scope. Noted only. |

Everything else in Settings (Accent colour, Font, Layout, 24-hour time, Hide resolved
reminders) has adequate self-describing subtitles — leave as-is per D2.

**Item 1 deliverables:** edit `SegmentedControl.kt` per §1.1; add `onAccentInk` /
`LocalOnAccent` / `DaybookColors.OnAccent` and repoint the 16 sites per §1.3; apply rows
2–12 and L1 + L2 from the tables; rows 13 and L3 are explicit no-ops.

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

## 2.2 Revised step deck (final copy — put verbatim in `OnboardingTeachSteps`)

7 `Teach` steps + `PermissionPrimer` + `Ready` (LD7). Add two `TeachIllustration` entries —
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

**4 — Journalling** · `TeachIllustration.INTAKE` (reused — LD8)
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

## 2.3 Hide the CTA for existing users (D4 / LD9)

**Signal.** `onboardingCompleted` is device-local (`app_settings`, not synced), so on a
reinstall / new device it is `false` and onboarding runs — but `CloudSyncRepository`'s
first-sign-in bootstrap restores the user's habit/intake rows. The reliable, already-
observable signal is **row count**, reactively (bootstrap may still be in flight when
`Ready` renders), exposed as a **tri-state** so the link can never flash (LD9):

- Add to `OnboardingViewModel`:
  ```kotlin
  // pure — HasExistingDataTest
  fun hasExistingData(habitCount: Int, intakeCount: Int): Boolean = habitCount + intakeCount > 0

  /** null = the two row-count Flows have not emitted yet. Never seeded false — see LD9. */
  val hasExistingData: StateFlow<Boolean?> =
      combine(
          habitRepository.observeAllHabits(),      // already exists
          foodMedRepository.observeAllTasks()      // already exists
      ) { h, i -> hasExistingData(h.size, i.size) }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
  ```
  `observeAllHabits()` / `observeAllTasks()` are existing repo Flows — **no DAO or schema
  change** (Journal habits are `HabitType.JOURNAL` rows in `habits`, so they're already
  counted). Inject `HabitRepository` + `FoodMedRepository` into `OnboardingViewModel`
  (constructor add; both are `@Singleton`, Hilt-provided).
- `OnboardingScreen`: collect `hasExistingData`; pass
  `showCreateHabit = !reviewMode && hasExistingData == false` into `ReadyStep`, and pick the
  Ready copy variant from the same signals (`null` → use the fresh-install headline/body but
  **no** link, and reserve the link's height with a `Spacer` so nothing jumps when it
  resolves).
- Fresh-install path: the Flows emit `(0, 0)` almost immediately → `false` → link shows,
  "You're set" copy.
- Re-run path (`onboarding_review` route → `configureReview()`): `reviewMode == true`
  already suppresses the link — unchanged; the "That's the tour" copy stays.

**STOP-and-ask flag for the implementer:** injecting two repositories into
`OnboardingViewModel` is within bounds (no schema, no DAO edit). If for any reason the
count must instead come from a *new* `@Query("SELECT COUNT(*)…")` DAO method, that is a
DAO change → **STOP and ask** before adding it. Prefer the repo-Flow route.

## 2.4 New illustrations (`OnboardingIllustrations.kt`) — LD8

Follow the file's existing pattern (static `MockFrame` / `Bar` primitives, theme-aware via
`DaybookColors` / `LocalAccent`, no assets). Add both entries to the `TeachIllustration`
enum and both branches to `OnboardingIllustration(kind)`:

- `STREAKS` — a small "card" row with a flame glyph + `7`, and a faint 7-dot week track,
  5 filled (accent) + 2 empty (`TextMuted`). ~120dp tall.
- `PRIVACY` — three stacked mini-rows: an accent swatch strip (5 dots), an "A" type
  sample, a lock glyph. Reuse `CardTints.Neutral` as the frame.

Step 4 (Journalling) maps to the existing `TeachIllustration.INTAKE` — do **not** add a
`JOURNAL` kind.

## 2.5 `WizardStep` / VM notes

- `OnboardingTeachSteps` grows from 5 to 7 entries — `buildWizardSteps`,
  `isLastWizardStep`, `StepDots`, `OnboardingTourSteps` all derive from the list size, so
  no logic change; the existing `WizardStepTest` count assertions update to 7.
- `configureReview()` / `reviewMode` path untouched apart from `showCreateHabit` already
  being `false` there.
- No new route, no `MainActivity` gate change beyond passing nothing new (the VM owns
  `hasExistingData`).

**Item 2 deliverables:** rewrite `OnboardingTeachSteps` with the §2.2 copy (7 steps); add
`STREAKS`/`PRIVACY` to `TeachIllustration` + their mocks; add the tri-state
`hasExistingData` to the VM + inject the two repos; branch `ReadyStep` copy + link on
`showCreateHabit`; update `WizardStepTest` + add `HasExistingDataTest`.

---

# ITEM 3 — Themed tint variants (Dark style + Light style pickers)

The user wants to choose a **background style** independent of the accent. New: a
"Dark style" picker and a "Light style" picker in Settings → Appearance, alongside the
Dark/Light/System toggle. **4 styles per mode** (LD1 / LD2 / LD3), Settings only (LD6).

## 3.1 Token architecture

Today (`ui/theme/Tokens.kt`): two constants `DaybookColorsDark` / `DaybookColorsLight` of
type `DaybookColorScheme` (13 roles), provided through `LocalDaybookColors`; `DaybookColors`
is a `@Composable`-getter shim over that local. `isDaybookDarkThemeActive` and
`CardTints.light` detect dark by **referential equality** `=== DaybookColorsDark`.

A "style" swaps the **ground + text roles** only. `success` / `warning` / `danger` /
`onSolid` are per-*mode* constants shared by every style in that mode. So:

**New file `ui/theme/ThemeStyle.kt`** (LD15):

```kotlin
// Per-mode signal colours — identical across all styles of that mode. Values are today's.
private val DarkSignals  = Signals(success = Color(0xFF4ADE80), warning = Color(0xFFFACC15),
                                   danger = Color(0xFFF87171), onSolid = Color(0xFF0B0D0F))
private val LightSignals = Signals(success = Color(0xFF15803D), warning = Color(0xFFB45309),
                                   danger = Color(0xFFDC2626), onSolid = Color(0xFFFFFFFF))

internal data class Signals(val success: Color, val warning: Color, val danger: Color, val onSolid: Color)

internal data class StyleGround(       // the 9 roles a style controls
    val bg: Color, val surface: Color, val surfaceElevated: Color,
    val outline: Color, val hairline: Color, val border: Color,
    val textPrimary: Color, val textMuted: Color, val textFaint: Color
)

enum class DarkStyle(val storageKey: String, val label: String, internal val ground: StyleGround) {
    CHARCOAL("CHARCOAL", "Charcoal",   GroundCharcoal),   // == today's DaybookColorsDark
    AMOLED  ("AMOLED",   "True black", GroundAmoled),
    WARM    ("WARM",     "Espresso",   GroundWarm),
    NAVY    ("NAVY",     "Midnight",   GroundNavy);

    companion object { val DEFAULT = CHARCOAL
        fun fromKeyOrDefault(k: String?) = entries.firstOrNull { it.storageKey == k } ?: DEFAULT }
}

enum class LightStyle(val storageKey: String, val label: String, internal val ground: StyleGround) {
    PAPER("PAPER", "Paper",       GroundPaper),           // == today's DaybookColorsLight
    PURE ("PURE",  "Pure white",  GroundPure),
    CREAM("CREAM", "Warm cream",  GroundCream),
    SEPIA("SEPIA", "Sepia",       GroundSepia);

    companion object { val DEFAULT = PAPER
        fun fromKeyOrDefault(k: String?) = entries.firstOrNull { it.storageKey == k } ?: DEFAULT }
}

fun darkSchemeFor(style: DarkStyle): DaybookColorScheme   = style.ground.toScheme(DarkSignals)
fun lightSchemeFor(style: LightStyle): DaybookColorScheme = style.ground.toScheme(LightSignals)

private fun StyleGround.toScheme(s: Signals) = DaybookColorScheme(
    bg, surface, surfaceElevated, outline, hairline, border,
    textPrimary, textMuted, textFaint, s.success, s.warning, s.danger, s.onSolid
)
```

`StyleGround` / `Signals` / the `ground` property are **`internal`** (LD15) — the app is a
single `:app` module, so `MainActivity` can read `style.ground.bg` directly in §3.3 with no
public accessor.

**`Tokens.kt` changes (keep the names, keep non-composable readers valid):**

```kotlin
// still the app-wide default; now DERIVED from the default style so there is exactly one
// source of truth. Guarded byte-identical by DarkStyleTest / LightStyleTest (D8).
val DaybookColorsDark  = darkSchemeFor(DarkStyle.CHARCOAL)
val DaybookColorsLight = lightSchemeFor(LightStyle.PAPER)
```

`GroundCharcoal` / `GroundPaper` literals are exactly today's field values (bg `#0B0D0F` …
/ bg `#FBFBF9` …). The test asserts `darkSchemeFor(CHARCOAL) == DaybookColorsDark` against
the *current* literal scheme copied into the test, so a later accidental drift fails the
build. Initialisation order: put the `Ground*` vals above the enums in `ThemeStyle.kt`, and
note that `Tokens.kt`'s two constants now depend on `ThemeStyle.kt` (both are top-level
`val`s in the same package — Kotlin resolves this fine; `CardTintsDark.Neutral` /
`CardTintsLight.Neutral` keep reading `DaybookColorsDark` / `DaybookColorsLight`).

**Dark detection must stop using referential equality** (a non-Charcoal dark style is a
different object):

```kotlin
val LocalIsDark = staticCompositionLocalOf { true }          // NEW, provided by DaybookTheme
val isDaybookDarkThemeActive: Boolean @Composable get() = LocalIsDark.current
// CardTints.light:  private val light: Boolean @Composable get() = !LocalIsDark.current
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
    cornerScale: Float = DEFAULT_CORNER_SCALE,     // NEW — Item 4
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    ...
    val dark = when (themeMode) { DARK -> true; LIGHT -> false; SYSTEM -> systemDark }
    val colors = remember(dark, darkStyle, lightStyle) {
        if (dark) darkSchemeFor(darkStyle) else lightSchemeFor(lightStyle)
    }
    val accentColor = accent.colorFor(dark)
    val onAccent = remember(accentColor) { onAccentInk(accentColor) }   // §1.3

    val scheme = remember(dark, accent, darkStyle, lightStyle) {
        (if (dark) DarkScheme else LightScheme).copy(
            primary = accentColor, onPrimary = onAccent,
            secondary = accentColor, tertiary = accentColor,
            // NEW — keep the M3 defaults (Switch, native dialogs, text-selection, etc.)
            // tracking the chosen STYLE, not just the Charcoal/Paper constants:
            background = colors.bg, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated, onSurfaceVariant = colors.textMuted,
            outline = colors.outline, outlineVariant = colors.outline, scrim = colors.bg
        )
    }
    val appShapes = remember(cornerScale) { scaledAppShapes(cornerScale) }   // Item 4
    val m3Shapes  = remember(cornerScale) { scaledM3Shapes(cornerScale) }    // Item 4

    CompositionLocalProvider(
        LocalDaybookColors provides colors,
        LocalIsDark provides dark,                 // NEW
        LocalAccent provides accentColor,
        LocalOnAccent provides onAccent,           // NEW — §1.3
        LocalDaybookShapes provides appShapes,     // NEW — Item 4
        LocalReduceMotion provides reduce
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = m3Shapes, content = content)
    }
}
```

`DarkScheme` / `LightScheme` top-level `darkColorScheme(...)` / `lightColorScheme(...)`
vals stay (they still read `DaybookColorsDark.*` = the Charcoal/Paper defaults, and keep
`surfaceTint = Color.Transparent`); the `.copy(...)` above now overrides every ground role
from the resolved `colors`, so a non-default style is fully applied to M3 too.

## 3.3 State plumbing

- **Entity** `data/model/DataModel.kt` `AppSettings`:
  ```kotlin
  @ColumnInfo(name = "dark_style",  defaultValue = "CHARCOAL") val darkStyle: String = DarkStyle.DEFAULT.storageKey,
  @ColumnInfo(name = "light_style", defaultValue = "PAPER")    val lightStyle: String = LightStyle.DEFAULT.storageKey,
  @ColumnInfo(name = "corner_scale", defaultValue = "1.0")     val cornerScale: Float = DEFAULT_CORNER_SCALE,   // Item 4
  ```
  Appended after `theme_mode`. Every `@ColumnInfo(defaultValue=…)` byte-matches the
  `MIGRATION_20_21` SQL. Device-local — **not** in `BackupModel`, `ContentHash`, sync,
  export (verify `data/backup/BackupModel.kt` and `data/sync/ContentHash.kt` don't
  enumerate `app_settings` — they don't today; nothing to change, just confirm).
- **DAO** `data/local/AppSettingsDao.kt` — three writers mirroring `updateThemeMode`:
  ```kotlin
  @Query("UPDATE app_settings SET dark_style = :v WHERE id = 1")   suspend fun updateDarkStyle(v: String)
  @Query("UPDATE app_settings SET light_style = :v WHERE id = 1")  suspend fun updateLightStyle(v: String)
  @Query("UPDATE app_settings SET corner_scale = :v WHERE id = 1") suspend fun updateCornerScale(v: Float)
  ```
  *(Additions to an existing DAO — sanctioned because they are the write path for the
  sanctioned `MIGRATION_20_21` columns, exactly like `updateThemeMode` was for
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
  The existing `setThemeMode` / `readThemeModeMirror` keep working — they just call the
  renamed object (LD15).
- **SP mirror** — rename `data/ThemeModePrefs.kt` → `data/ThemePrefs.kt`, `object
  ThemeModePrefs` → `object ThemePrefs` (LD15). Same `daybook_prefs` file, same existing
  `theme_mode` key and its `read`/`write`; add keys `dark_style` (default `"CHARCOAL"`),
  `light_style` (default `"PAPER"`), `corner_scale` (Float, default `1.0f`). All reads
  `runCatching`-guarded like the existing `read()`, and `readCornerScale` returns
  `clampCornerScale(stored)`. Three call sites to update: `MainActivity.kt:418`,
  `AppSettingsRepository.kt:71` and `:76`.
- **ViewModels** — `SettingsViewModel` and `OnboardingViewModel` each expose:
  ```kotlin
  val darkStyle: StateFlow<DarkStyle> = observeSettings().map { DarkStyle.fromKeyOrDefault(it.darkStyle) }
      .stateIn(scope, SharingStarted.Eagerly, DarkStyle.fromKeyOrDefault(repo.readDarkStyleMirror()))
  // lightStyle + cornerScale identical shape; cornerScale seeded from readCornerScaleMirror()
  fun setDarkStyle(s: DarkStyle)   = safeLaunch { repo.setDarkStyle(s.storageKey) }
  fun setLightStyle(s: LightStyle) = safeLaunch { repo.setLightStyle(s.storageKey) }
  fun setCornerScale(v: Float) {
      val c = clampCornerScale(v)
      if (c == cornerScale.value) return          // LD12 — drop no-op detent writes
      safeLaunch { repo.setCornerScale(c) }
  }
  ```
  `Eagerly` + SP-mirror seed = **zero flash** on cold start (same as `themeMode`).
- **`MainActivity`** — collect `darkStyle` / `lightStyle` / `cornerScale` from
  `onboardingViewModel` (next to the existing `themeMode` collect ~L279) and pass into
  `DaybookTheme(...)`.
- **`applyWindowTheme()`** (`MainActivity` ~L417) — the pre-inflate splash background must
  match the chosen *style*, not just dark/light. Do **not** add per-style
  `windowBackground` resources (combinatorial). Instead, after resolving `dark` from
  `ThemePrefs.read(this)`:
  ```kotlin
  val groundBg =
      if (dark) DarkStyle.fromKeyOrDefault(ThemePrefs.readDarkStyle(this)).ground.bg
      else      LightStyle.fromKeyOrDefault(ThemePrefs.readLightStyle(this)).ground.bg
  window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(groundBg.toArgb()))
  setTheme(if (dark) R.style.Theme_Daybook_Dark else R.style.Theme_Daybook_Light)  // keeps status-bar icon polarity
  ```
  `DarkStyle`/`LightStyle` live in `com.daybook.app.ui.theme` and `ground` is `internal`
  in the same module — importable from the Activity as-is (LD15).

## 3.4 Settings → Appearance UI (`ui/settings/SettingsScreen.kt` `AppearanceSettingsScreen`)

After the existing Theme `SegmentedControl` group and before "Accent color", add two
groups (D1 — both always shown, in both modes):

```
SectionHeader("Dark style", subtitle = "Background palette used in dark mode.")
SettingsGroup { StyleSwatchRow(DarkStyle.entries, current = darkStyle, onPick = vm::setDarkStyle) }

Spacer(Spacing.listGap)
SectionHeader("Light style", subtitle = "Background palette used in light mode.")
SettingsGroup { StyleSwatchRow(LightStyle.entries, current = lightStyle, onPick = vm::setLightStyle) }
```

`StyleSwatchRow` — new composable in **`ui/components/StyleSwatchRow.kt`** (LD15): a `Row`
of 4 preview chips (4 fit a 360dp-wide screen; still wrap it in
`Modifier.horizontalScroll(rememberScrollState())` so a large font scale can't clip it).
Each chip: a rounded-rect (`AppShapes.card`) ~72×56dp painted with the style's `bg`, with a
smaller inset rect in the style's `surface` and a 2–3px bar in `textPrimary`, an accent
ring + `Check` when `selected`, and the `label` beneath. This shows the actual palette
rather than a colour name. Reuse the `Swatch` grammar (≥44dp touch target, `Check` on
select, a `contentDescription` of the style label) where practical.

Live preview: because `darkStyle`/`lightStyle` feed `DaybookTheme` at the Activity root,
picking one recomposes the whole app instantly — the Appearance screen itself restyles
under the user's finger. No extra work.

## 3.5 Style palettes (hex — full role tables)

`success`/`warning`/`danger`/`onSolid` are the per-mode `Signals` above and are the same
for every style. Only the 9 `StyleGround` roles differ. Every value below is **measured**:
`textPrimary` ≥ 12:1 and `textMuted` ≥ 4.5:1 against both that style's `bg` and its
`surfaceElevated`, `textFaint` ≥ 3:1 against its `bg` (decoration floor — LD14). All 5
accents stay legible because content on an accent fill uses `OnAccent` (§1.3), which is
computed from the accent alone and therefore style-independent.

### Dark styles (LD1 / LD3 — exactly four)

| role | **Charcoal** (default, = today) | **True black** (AMOLED) | **Espresso** (WARM) | **Midnight** (NAVY) |
|------|------|------|------|------|
| bg              | `#0B0D0F` | `#000000` | `#14100D` | `#0A0E16` |
| surface         | `#16181B` | `#0C0D0F` | `#1E1813` | `#121826` |
| surfaceElevated | `#1E2124` | `#16181B` | `#271F18` | `#1A2233` |
| outline         | `#2A2D31` | `#26292E` | `#362B22` | `#283349` |
| hairline        | `#14FFFFFF` | `#14FFFFFF` | `#16FFF3E6` | `#14FFFFFF` |
| border          | `#14FFFFFF` | `#14FFFFFF` | `#16FFF3E6` | `#14FFFFFF` |
| textPrimary     | `#F2F3F5` | `#F2F3F5` | `#F4EFE9` | `#EEF1F6` |
| textMuted       | `#9AA0A6` | `#9AA0A6` | `#A79E92` | `#97A0B2` |
| textFaint       | `#6B7178` | `#6B7178` | `#766C60` | `#667085` |

Measured (textPrimary/bg · textMuted/bg · textMuted/surfaceElevated · textFaint/bg):
Charcoal 17.53 · 7.37 · 6.13 · 3.95 — True black 18.91 · 7.95 · 6.74 · 4.26 —
Espresso 16.55 · 7.16 · 6.14 · 3.68 — Midnight 17.06 · 7.35 · 6.05 · 3.88.

### Light styles (LD2 / LD3 — exactly four)

| role | **Paper** (default, = today) | **Pure white** (PURE) | **Warm cream** (CREAM) | **Sepia** (SEPIA) |
|------|------|------|------|------|
| bg              | `#FBFBF9` | `#FFFFFF` | `#FBF6EC` | `#F3E9D8` |
| surface         | `#FFFFFF` | `#FFFFFF` | `#FFFDF7` | `#FBF3E4` |
| surfaceElevated | `#F2F2EF` | `#F4F5F7` | `#F3EBDA` | `#EADFC8` |
| outline         | `#E2E2DE` | `#E4E6EA` | `#E6DCC6` | `#D9CBAD` |
| hairline        | `#14000000` | `#0F000000` | `#14000000` | `#1A000000` |
| border          | `#14000000` | `#0F000000` | `#14000000` | `#1A000000` |
| textPrimary     | `#1B1D20` | `#16181B` | `#23201A` | `#2E2718` |
| textMuted       | `#5B6068` | `#565B63` | `#6A6253` | `#665B45` |
| textFaint       | `#8A9099` | `#878D96` | `#8C8472` | `#8A7F63` |

Measured: Paper 16.30 · 6.11 · 5.64 · 3.10 — Pure white 17.79 · 6.84 · 6.27 · 3.34 —
Warm cream 15.08 · 5.59 · 5.08 · 3.45 — Sepia 12.30 · 5.55 · 5.05 · 3.30.
**Note:** Warm cream's `textFaint` and Sepia's `textMuted` / `textFaint` were darkened from
the first draft (`#9A9382`, `#6E634B`, `#9C9176`) — those measured 2.84 / 4.48 (on
`surfaceElevated`) / 2.60 and missed the floors above. Paper and Charcoal are untouched
(D8).

Further tuning is expected on-device (the current `DaybookColorsLight` already carries a
"Tuned on device" note) — but any change must keep the floors in `DarkStyleTest` /
`LightStyleTest` green.

`CardTint` sets (`CardTintsDark` / `CardTintsLight`, the pastel per-item card tints) are
**not** style-parameterized this round (LD4) — they keep their current values and are
selected by mode only, via `LocalIsDark`. The six pastels sit acceptably on all four
grounds of their mode. **Hand this back to the user as a known limitation:** on Espresso
and Midnight the pastels are tuned for Charcoal, so they read slightly cooler/warmer than
the ground; a future round could give each style its own tint set.

## 3.6 `MIGRATION_20_21` — the only schema change this round (LD5)

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
- `MigrationTest` (androidTest) — add a `migrate20To21` case following the existing
  pattern (create at 20, `runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21)`,
  plus a `preservesRows` variant asserting `dark_style = 'CHARCOAL'`, `light_style =
  'PAPER'`, `corner_scale = 1.0`). It only needs to `compileDebugAndroidTestKotlin` here
  (no device on this machine).

## 3.7 Item 3 tests

`DarkStyleTest` / `LightStyleTest` per the gate section: default resolution, garbage-key
fallback, `entries.size == 4`, **byte-identity of `CHARCOAL`/`PAPER` vs the current
constants**, and the per-style contrast floors (pure ratio helper in-test).

**Item 3 deliverables:** new `ui/theme/ThemeStyle.kt`; `Tokens.kt` (derive the two
constants + `LocalIsDark` + `OnAccent` shim member + repoint `isDaybookDarkThemeActive` /
`CardTints.light`); `Theme.kt` (`DaybookTheme` params + `.copy` ground roles + provide the
new locals); `ThemePrefs` rename + key additions; `AppSettings` + `AppSettingsDao` +
`AppSettingsRepository` + `SettingsViewModel` + `OnboardingViewModel`; `MainActivity`
(collect + pass + `applyWindowTheme` bg drawable); `AppearanceSettingsScreen` two groups +
`StyleSwatchRow`; `Migrations.kt` + `AppDatabase.kt` + `DatabaseModule.kt` + `21.json`;
the two style tests.

---

# ITEM 4 — Configurable corner roundness

A single global multiplier on the rectangular-ish shape tokens, driven by a slider on
Settings → Appearance. Default `1.0` = today's look (D5).

## 4.1 What `AppShapes` looks like today (`ui/theme/Tokens.kt` `object AppShapes`)

| token | value | scope | scales? |
|---|---|---|---|
| `card`     | `RoundedCornerShape(14.dp)` | SoftCard / FormGroup / SettingsGroup / progress cards | yes |
| `button`   | `RoundedCornerShape(12.dp)` | PrimaryButton / GhostButton | yes |
| `field`    | `RoundedCornerShape(10.dp)` | text fields | yes |
| `pill`     | `RoundedCornerShape(10.dp)` | chips, stat pills, mini badges (a rounded rect, **not** a stadium) | yes |
| `tile`     | `RoundedCornerShape(12.dp)` | icon tiles, menu-row icon squares, `Swatch` | yes |
| `sheet`    | `RoundedCornerShape(top 20.dp)` | ModalBottomSheet | yes (top only) |
| `nav`      | `RoundedCornerShape(top 18.dp)` | FloatingPillNav bar | yes (top only) |
| `dialog`   | `RoundedCornerShape(16.dp)` | AlertDialog / DaybookAlertDialog | yes |
| `segmented`| `RoundedCornerShape(50)` | SegmentedControl track/pill | **no — fixed (LD11)** |
| `navPill`  | `RoundedCornerShape(28.dp)` | Detail floating-nav footprint | **no — fixed (LD11)** |

M3 `Shapes` (`Theme.kt` top-level `DaybookShapes` val): `extraSmall=field`, `small=button`,
`medium=card`, `large=dialog`, `extraLarge=RoundedCornerShape(20.dp)` — the `extraLarge`
20dp base scales too.

`AppShapes` is referenced **76×**, all inside `@Composable`s (via `clip()` / `background()` /
`border()` / `shape =`), **except one file-scope reader**: `Theme.kt`'s `DaybookShapes`
val. Circular elements (avatars, `CircleIconButton`, dots, day circles, `StepDots`) use
`CircleShape` **directly**, not `AppShapes` — so they are inherently immune to the scale.

## 4.2 Range / mapping (LD10 / LD11 / LD12)

- **Range `0.0f … 1.75f`, default `1.0f`, in `0.25` detents** — 8 slider positions:
  `0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75`.
  - `0.0` → every scalable token `RoundedCornerShape(0.dp)` = perfectly square (the
    segmented track and Detail nav pill stay rounded, so the app is still usable).
  - `1.0` → today's values exactly.
  - `1.75` → `card` 24.5dp, `button` 21dp, `field`/`pill` 17.5dp, `tile` 21dp, `sheet`-top
    35dp, `nav`-top 31.5dp, `dialog` 28dp, M3 `extraLarge` 35dp — clearly "very rounded"
    while every token stays at or under the fixed 28dp `navPill`.
- **Per-token:** `scaled(baseDp) = (baseDp * scale).coerceAtLeast(0.dp)`. Only the numeric
  dp tokens scale. `segmented` stays `RoundedCornerShape(50)` and `navPill` stays
  `RoundedCornerShape(28.dp)` unconditionally (LD11).
- **`sheet` / `nav`** are top-corners-only — scale the two top values, keep bottom `0.dp`.
- **Slider presentation (LD12):** label **"Corners"**, subtitle *"How rounded cards,
  buttons and fields look."*, `"Square"` / `"Round"` captions at the ends, and a
  right-aligned live value caption — `"Square"` at `0.0`, `"1.0× · Default"` at `1.0`,
  otherwise `"1.25×"` etc. Mirror that caption into
  `Modifier.semantics { stateDescription = caption }` on the slider so TalkBack announces
  something meaningful.

## 4.3 Token architecture

**New file `ui/theme/Shapes.kt`** (LD15):

```kotlin
const val MIN_CORNER_SCALE = 0f
const val MAX_CORNER_SCALE = 1.75f
const val DEFAULT_CORNER_SCALE = 1f
const val CORNER_SCALE_STEP = 0.25f
/** Interior steps for the M3 Slider: 8 positions -> 6 interior. */
const val CORNER_SCALE_SLIDER_STEPS = 6

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
        segmented = RoundedCornerShape(50),          // fixed at every scale (LD11)
        navPill = RoundedCornerShape(28.dp)          // fixed at every scale (LD11)
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
  — is **deleted**; `DaybookTheme` passes `scaledM3Shapes(cornerScale)` (memoised) to
  `MaterialTheme` instead.
- Tests compare `scaledAppShapes(1f)` against hardcoded expected dps rather than a new
  `AppShapesBase` object — no extra public surface.

## 4.4 Delivery + live drag

- `DaybookTheme` gains `cornerScale: Float = DEFAULT_CORNER_SCALE` (shown in §3.2),
  provides `LocalDaybookShapes` and passes `scaledM3Shapes(cornerScale)` to `MaterialTheme`.
- `MainActivity` collects `cornerScale` from `onboardingViewModel` (seeded from the SP
  mirror, `Eagerly`).
- **Slider** in `AppearanceSettingsScreen` (its own `SettingsGroup` under the style
  pickers):
  ```kotlin
  val cornerScale by viewModel.cornerScale.collectAsStateWithLifecycle()
  Slider(
      value = cornerScale,
      onValueChange = viewModel::setCornerScale,      // detent -> persist -> live recomposition
      valueRange = MIN_CORNER_SCALE..MAX_CORNER_SCALE,
      steps = CORNER_SCALE_SLIDER_STEPS,              // 6 interior -> 8 positions, 0.25 apart
      modifier = Modifier.semantics { stateDescription = caption }
  )
  ```
  Drive the slider straight from the persisted `StateFlow` — no local `mutableFloatStateOf`
  mirror. With detents the whole drag is at most 8 writes, each a single-row `UPDATE` on
  the settings table (same cost profile as the accent picker's per-tap write), and
  `setCornerScale` drops a write whose clamped value already matches (§3.3). The
  Room→StateFlow→`DaybookTheme` path gives the live restyle.
- **SP mirror** (`ThemePrefs`) — `corner_scale` Float key, read by `readCornerScaleMirror()`;
  `applyWindowTheme()` does **not** need it (the window bg is a flat colour and nothing in
  the pre-inflate splash is rounded).
- Clamp: `clampCornerScale` is applied in `scaledAppShapes`, in `setCornerScale`, and in
  `ThemePrefs.readCornerScale`. A persisted out-of-range value (corrupt row) resolves to a
  clamped value, never crashes.

## 4.5 Item 4 tests

`ScaledAppShapesTest` — as enumerated in the verification gate:
- `scaledAppShapes(1f)` → `card` topStart == 14.dp (and the other 7 scalable tokens at
  their table values).
- `scaledAppShapes(0f)` → all 8 scalable tokens == `RoundedCornerShape(0.dp)`.
- `scaledAppShapes(1.75f)` → `card` topStart == 24.5.dp, `dialog` == 28.dp.
- Monotonic increase across `0f → 1f → 1.75f` for `card`.
- `segmented` == `RoundedCornerShape(50)` and `navPill` == `RoundedCornerShape(28.dp)` at
  `0f`, `1f` and `1.75f` (LD11).
- `clampCornerScale(-1f) == 0f`, `clampCornerScale(9f) == 1.75f`, `clampCornerScale(1f) == 1f`.
- `scaledM3Shapes(1f).medium == scaledAppShapes(1f).card`.

**Item 4 deliverables:** new `ui/theme/Shapes.kt`; `AppShapes` → shim + `LocalDaybookShapes`;
delete `Theme.kt`'s `DaybookShapes` val and wire `scaledM3Shapes` + `LocalDaybookShapes` in
`DaybookTheme`; `corner_scale` column (folded into `MIGRATION_20_21`) + entity/DAO/repo/VM/
mirror; `MainActivity` collect + pass; `AppearanceSettingsScreen` slider group;
`ScaledAppShapesTest`.

---

## Consolidated file-touch list

**New files**
- `ui/theme/ThemeStyle.kt` — `DarkStyle` / `LightStyle` (4 entries each) / `StyleGround` /
  `Signals` / `darkSchemeFor` / `lightSchemeFor`.
- `ui/theme/Shapes.kt` — `DaybookShapeScheme` / `scaledAppShapes` / `scaledM3Shapes` /
  `clampCornerScale` / the scale constants / `LocalDaybookShapes`.
- `ui/components/StyleSwatchRow.kt` — the style preview chips.
- `data/ThemePrefs.kt` — the renamed `data/ThemeModePrefs.kt` (git mv, then rename the
  object and add the 3 keys).
- Tests: `ScaledAppShapesTest`, `DarkStyleTest`, `LightStyleTest`, `OnAccentInkTest`,
  `HasExistingDataTest` (all `app/src/test/java/com/daybook/app/...`).

**Edited**
- `ui/components/SegmentedControl.kt` — §1.1 + `OnAccent`.
- `ui/components/Components.kt` — findings 4, 5, 6 + `OnAccent` (6 sites).
- `ui/components/WeekStrip.kt` — finding 7 + `OnAccent`.
- `ui/components/SortSheet.kt` — finding 8 + `OnAccent`.
- `ui/components/Avatar.kt` — `OnAccent` (2 sites).
- `ui/components/Forms.kt` — finding 12 + `OnAccent`.
- `ui/journal/HabitJournalChatScreen.kt` — `OnAccent`.
- `ui/lock/AppLockSettingsScreen.kt` — `OnAccent`.
- `ui/TimePickerComponents.kt` — finding 9.
- `ui/settings/SettingsScreen.kt` — findings 10, L1, L2, `OnAccent` (2 sites); Item 3 two
  style groups + Item 4 slider in `AppearanceSettingsScreen`; the
  `isDaybookDarkThemeActive` read still compiles unchanged.
- `ui/settings/AboutSettingsScreen.kt` — finding 11.
- `ui/onboarding/OnboardingViewModel.kt` — 7 teach steps, tri-state `hasExistingData`,
  inject the two repos, `darkStyle`/`lightStyle`/`cornerScale` flows.
- `ui/onboarding/OnboardingScreen.kt` — `ReadyStep` copy/link branch, new illustrations.
- `ui/onboarding/OnboardingIllustrations.kt` — `STREAKS` + `PRIVACY` mocks.
- `ui/MainActivity.kt` — collect + pass `darkStyle`/`lightStyle`/`cornerScale`;
  `applyWindowTheme()` style-aware window bg; `ThemePrefs` rename.
- `ui/theme/Tokens.kt` — derive `DaybookColorsDark/Light` from the default styles;
  `LocalIsDark`; `OnAccent` shim member; repoint `isDaybookDarkThemeActive` /
  `CardTints.light`; `AppShapes` shim.
- `ui/theme/Theme.kt` — `DaybookTheme` params (`darkStyle`, `lightStyle`, `cornerScale`),
  `.copy` ground roles + `onPrimary = onAccent`, `scaledM3Shapes`, provide `LocalIsDark` /
  `LocalOnAccent` / `LocalDaybookShapes`; delete the `DaybookShapes` val.
- `ui/theme/Accent.kt` — `relativeLuminance` / `onAccentInk` / `LocalOnAccent`.
- `data/AppSettingsRepository.kt` — `setDarkStyle/setLightStyle/setCornerScale` + mirror
  reads; `ThemePrefs` rename.
- `data/local/AppSettingsDao.kt` — `updateDarkStyle/updateLightStyle/updateCornerScale`.
- `data/model/DataModel.kt` — 3 `AppSettings` columns.
- `data/local/Migrations.kt` — `MIGRATION_20_21`.
- `data/local/AppDatabase.kt` — `version = 21`.
- `di/DatabaseModule.kt` — register `MIGRATION_20_21`.
- `ui/settings/SettingsViewModel.kt` — `darkStyle/lightStyle/cornerScale` flows + setters.
- `app/schemas/com.daybook.app.data.local.AppDatabase/21.json` — regenerated by the build.
- Existing tests: `WizardStepTest` (5 → 7 teach steps), `AccentColorThemeTest` (if it pins
  the literal scheme), `MigrationTest` androidTest (add `migrate20To21`).

**Must NOT change:** `data/backup/BackupModel.kt`, `data/sync/*` (`ContentHash`,
`PayloadCodec`, `SyncLogic`, `CloudSyncRepository`, `MonthPartitioner`),
`data/ExportImportRepository.kt`, `util/JsonUtils.kt`, versionCode/Name, any dependency /
SDK / Gradle version. If the implementer believes any of these must change → **STOP and
ask.**

---

## Sequencing for the implementer

1. **Item 1 SegmentedControl fix + §1.3 `OnAccent` + finding table** — smallest, unblocks
   the screenshots, no schema. Build + gate.
2. **Item 3 + Item 4 schema** — `MIGRATION_20_21` (3 cols), entity, DAO, repo, `21.json`,
   `DatabaseModule`, `ThemePrefs` rename. Build (schema green) before touching UI.
3. **Item 3 theme tokens** — `ThemeStyle.kt`, `Tokens.kt`, `Theme.kt`, `LocalIsDark`. Build.
4. **Item 4 shape tokens** — `Shapes.kt`, `AppShapes` shim, `Theme.kt` M3 shapes. Build.
5. **VMs + MainActivity** wiring for style + corner scale. Build.
6. **Appearance UI** — the two style groups + the corner slider.
7. **Item 2 onboarding** — copy, illustrations, `hasExistingData`, Ready branch.
8. **Remaining legibility rows (4, 5, 7–12) + L1 + L2.**
9. Full gate (the 5-target Gradle command) + write a short `UX_REFINEMENT_PROGRESS.md`
   (deviations, device follow-ups, the LD4 CardTint limitation). No commit, no APK push
   unless the user asks.

Device follow-ups to hand back: segmented pill visible in every theme/style/accent/font;
selected labels legible on all 5 accents in **light** mode (the LD13 fix); Dark/Light style
pickers restyle live and persist across cold start with no flash; the corner slider
reshapes cards live, detents feel right, and persists; onboarding reads well and the CTA is
absent (and never flashes) for a signed-in account with restored data; all 8 style palettes
eyeballed at each of the 5 accents.
