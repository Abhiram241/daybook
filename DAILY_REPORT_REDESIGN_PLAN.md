# DAILY_REPORT_REDESIGN_PLAN.md

**Status: PLAN ONLY — not implemented.** Written for a second implementation agent. No source file
has been touched to produce this document. It assumes the Daily Report feature as it exists RIGHT
NOW in the working tree (post BUG_AUDIT.md fixes, all still uncommitted/untracked) — every file
reference below was re-read fresh, not assumed from `DAILY_REPORT_PLAN.md`'s original design.

Covers, in order: (1) the chat-screen divider bug, (2) a global meta-prompt setting, (3) the "Hi"
data-dump bug, (4) the back-button nav bug, (5) the AI Summary tab redesign, (5B) the Report tab's
three layout issues (headerless-looking Health card, Habits placeholder dash, exercise-row text
wrap), (6) report context-category toggles, (7) chat-specific custom date-range + category settings.

**All open decisions below are FINAL — the user has reviewed and decided every item.** Section 8's
schema change (DB v26 → v27, five new `app_settings` columns) is **approved**; the implementer may
proceed with it directly, no further sign-off needed.

---

## 1. Chat screen divider/underline bug

**File:** `app/src/main/java/com/daybook/app/ui/report/DailyReportScreen.kt`, `DailyReportChatScreen`
(currently lines 481–564) and `AssistantBubble` (currently lines 566–578).

**What I could confirm by reading the code, and what I could not:** `BackHeader` (used for the
"Chat about today" title, `app/src/main/java/com/daybook/app/ui/components/BackHeader.kt`) draws no
divider, underline, or progress bar of its own — it is a plain `Row` (back button + title). Same
for `ScreenHeader`, `StickySaveBar`, and `DaybookTextField`'s input box — none of them contain a
`HorizontalDivider`, `LinearProgressIndicator`, or `PastelProgressBar` call that could produce a
stray bar. So the "purple bar" is not a leftover progress/divider *widget* sitting statically under
the header the way the user's phrasing suggests.

**Most likely actual cause — a rendered Markdown rule, not a stray component.** `AssistantBubble`
(line 567–578) renders every assistant reply through
`com.mikepenz.markdown.m3.Markdown(content = text)` inside a `SoftCard` capped at
`widthIn(max = 280.dp)`, left-aligned (`Arrangement.Start`). If the AI's reply contains Markdown
heading syntax (`## Workout`, `## Health`, etc. — exactly what `buildDailyReportPrompt` prompts the
model to imitate, and exactly the shape of the unwanted full-report reply in bug #3 below) or an
explicit `---` rule, the `mikepenz` Markdown renderer draws a rule/heading-underline styled in the
Material3 theme's primary color (which in this app resolves to the purple accent). Because it
renders *inside* the 280dp-capped bubble, not full-width, it reads exactly as described: "starts
partway across, doesn't span edge-to-edge" — it's the width of the first assistant bubble, sitting
right under the header because it's the very first thing in the chat transcript.

This means **bug #1 and bug #3 are very likely the same root cause wearing two descriptions** — the
data-dump reply (structured Markdown with headings) is what produces the bar. Fixing #3 (the model
stops replying with a full Markdown report to a greeting) will very likely make the bar disappear
on its own for that specific screenshot. But the underlying rendering issue (a Markdown heading/HR
inside a narrow chat bubble reading as a stray offset bar) can still occur any time the AI's answer
legitimately contains a heading or list (e.g., user asks "break down my workout" and the AI
reasonably replies with structured Markdown) — so it should be fixed at the rendering layer too,
independent of the prompt fix.

**Fix (do both):**
1. In `AssistantBubble`, pass explicit typography to `Markdown(...)` (the `mikepenz` M3 artifact
   accepts a `typography` parameter, e.g. `markdownTypography(...)`) so headings render as bold
   text (matching `DaybookText.CardTitle`/`bodyLarge`) with **no rule/underline decoration**, and
   so a literal `---` renders as a full-width `HorizontalDivider(color = DaybookColors.Hairline)`
   sized to the bubble's own width (intentional, thin, on-brand) instead of the library's default
   (which is what currently reads as "misplaced"). This makes any future structured reply look
   intentional per the user's own framing of the fix ("either a full-width divider or remove").
2. Ship #3's prompt fix (below) so casual replies don't return structured Markdown in the first
   place — the two fixes are complementary, not either/or.

**Verification for the implementer:** reproduce by opening chat, sending "Hi" against a day that
has workout/health/intake data, and confirming the bar's exact position matches the first assistant
bubble's top edge before/after. If it does NOT match — i.e. the bar is visible even with an empty
transcript — take a fresh screenshot and re-examine `BackHeader`/`ScreenHeader` for a system-level
cause (e.g. a stray `Divider` added by a Material3 default component slot) rather than assuming
this Markdown theory; the codebase as read today shows no other candidate.

---

## 2. Global meta-prompt / system-instruction setting

**New setting:** one free-text field, persisted, injected into both report generation and chat.

**Storage — `AppSettings` (Room), NOT `AiKeyStore`.** Unlike the API keys, a meta-prompt is a
content-shaping preference like `greeting_tone` or `habit_checkin_time` — it belongs in
`app_settings` (synced/backed-up like other behavioural settings, not treated as a credential). New
column: `app_settings.ai_meta_prompt` (`TEXT NOT NULL DEFAULT ''`). See §8 for the migration.

**Files to touch:**
- `app/src/main/java/com/daybook/app/data/model/DataModel.kt` — add
  `@ColumnInfo(name = "ai_meta_prompt", defaultValue = "") val aiMetaPrompt: String = ""` to
  `AppSettings`, appended after the last DB v24 column, following the file's own "append, never
  reorder" convention already documented inline for every prior round.
- `app/src/main/java/com/daybook/app/data/local/AppSettingsDao.kt` — add
  `@Query("UPDATE app_settings SET ai_meta_prompt = :v WHERE id = 1") suspend fun updateAiMetaPrompt(v: String)`,
  matching the single-column-write discipline (REV-25/04) every other setter follows.
- `app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt` — add
  `suspend fun setAiMetaPrompt(v: String) { ensureRow(); database.appSettingsDao().updateAiMetaPrompt(v) }`.
- `app/src/main/java/com/daybook/app/data/local/Migrations.kt` — `MIGRATION_26_27` (§8).
- `app/src/main/java/com/daybook/app/data/local/AppDatabase.kt` — version bump to 27, add the new
  migration to the builder's migration list.

**Where it's surfaced in Settings:** a new **"Daily Report AI"** settings screen/section (this same
screen also hosts §6 and §7's toggles — one destination, not scattered across AI Providers and a
new screen; see the end-user question list for why one screen is recommended over two). New route
`settings_daily_report_ai`, wired the same way `settings_ai_providers` is wired in
`MainActivity.kt` (composable block near line 921) and reached from `SettingsScreen.kt` next to the
existing "AI Providers" row (`onOpenAiProviders`). New file:
`app/src/main/java/com/daybook/app/ui/settings/DailyReportAiSettingsScreen.kt` +
`DailyReportAiSettingsViewModel.kt`, structured like `AiProvidersSettingsScreen.kt`/
`AiProvidersViewModel.kt` (`SettingsSubScreen("Daily Report AI", onNavigateBack) { ... }`,
`FormGroup(title = "Custom instructions") { DaybookTextField(...) }`).

Field spec: multi-line `DaybookTextField` (`singleLine = false`, `minLines = 3`), placeholder text
like *"e.g. Keep replies short and casual. Don't lecture about missed habits."*, `supportingText`
explaining it's prepended to every report/chat request. No length cap beyond what's reasonable to
send to a provider (~2000 chars is plenty; enforce nothing harder than that so it never silently
truncates without telling the user).

**Where it's injected — `DailyReportPrompt.kt`:**
- `buildDailyReportPrompt(...)` gains a new parameter `metaPrompt: String` and, when non-blank,
  prepends it as its own paragraph before the existing "You are summarising…" preamble (e.g.
  `if (metaPrompt.isNotBlank()) { sb.append(metaPrompt.trim()).append("\n\n") }` as the very first
  thing appended, before line 29's `sb.append("You are summarising...")`).
- The chat system-message builder in `DailyReportViewModel.openChat()` (current lines 247–258)
  similarly prepends the meta-prompt ahead of the "Here is the user's full Daybook Daily Report…"
  framing line.
- Both call sites (`generate()` and `openChat()`) read `appSettingsRepository.observeSettings()`'s
  `aiMetaPrompt` the same way they already read `weightUnit`/`weekStart` — no new plumbing pattern
  needed, `DailyReportViewModel` already injects `AppSettingsRepository`.

---

## 3. "Hi" triggers a full data-dump instead of a normal reply

**Root cause, read directly from `DailyReportViewModel.openChat()` (lines 231–264) and
`DailyReportPrompt.kt`:** `openChat()` builds the *entire* daily report as plain-text sections via
`buildDailyReportPrompt(...)` and wraps it as:

```kotlin
val systemMessage = "Here is the user's full Daybook Daily Report for the day, for context. " +
    "Answer their questions using only these facts; say so plainly if something isn't in " +
    "the data.\n\n$context"
```

This system message says "answer their questions using only these facts" but never tells the model
what to do when the user ISN'T asking a data question — a plain "Hi" has no answerable question in
it, so the model's most literal reading of "use these facts to answer" is to volunteer the facts.
This is a pure prompt-engineering gap, not a code-logic bug — nothing needs to inspect message
content.

**Fix — rewrite the system message text** (same call site, `openChat()`, lines 255–257). Concrete
replacement:

```kotlin
val systemMessage = "You are a helpful, casual assistant inside Daybook, a personal tracking app. " +
    "Below is the user's full data for today, provided ONLY as background context you may draw on " +
    "IF it's relevant to what the user actually asks or says.\n\n" +
    "For a greeting or small talk (e.g. \"hi\", \"hello\", \"how are you\"), just reply naturally " +
    "and briefly — do not recite or summarise the data below unprompted. Only bring up specific " +
    "facts from the data when the user's message is actually asking about their day, a habit, a " +
    "workout, food/med intake, or health metrics. If asked about something not covered in the data, " +
    "say so plainly rather than guessing.\n\n" +
    "$context"
```

(This is illustrative wording, not final copy — the implementer should keep the "only when
relevant" + "greetings get a normal reply" + "say so plainly if missing" instructions, which are
the three load-bearing clauses; exact phrasing can be polished.) Insert the §2 meta-prompt ahead of
this whole block per §2's spec.

**Decided: fix via prompt/instruction engineering only, no special-case code.** Special-casing
short/greeting-like messages in code (e.g. `if (draft.trim().length < 10 || draft in
setOf("hi","hello",...)) sendPlainGreeting()`) was considered and rejected — it's fragile (misses
"hey!!", "sup", non-English greetings, "hi, how's my streak" which IS a real question), doesn't
generalize to other awkward inputs the same failure mode could produce, and every provider adapter
already receives the same message list — a robust system prompt fixes the behavior once, for every
message shape, across all seven providers, with no new branching logic to maintain. The implementer
should build the system-message fix above and NOT add any message-content detection logic.

**No other file needs to change for this bug** — `generate()`'s one-shot summary flow is untouched
(that flow SHOULD dump the full report; that's its whole purpose). Only `openChat()`'s system
message changes.

---

## 4. Back button from Chat skips the Daily Report screen — root cause found

**Root cause, read directly from `MainActivity.kt`:**

```kotlin
composable("main") {
    // System back from Habits/Intake returns to Today first (matches the old
    // popUpTo("home") behaviour) before the activity exits.
    BackHandler(enabled = settledPage != 0) { goToPage(0) }
    HorizontalPager(...) { page -> ... }
}
```

This `BackHandler` is registered once, at the `"main"` NavHost destination's top level, and is
`enabled` any time the visible pager page isn't Today (page 0) — including when the Daily Report
tab (`page != 0`, whatever `visibleRoutes` puts it at) is showing its **internal** chat overlay.
`DailyReportScreen`'s chat mode (`chatOpen` in `DailyReportViewModel`, `DailyReportScreen.kt` lines
97–100) is not a NavHost destination or a bottom sheet — it's a same-composable overlay, toggled by
a plain `if (chatOpen) { DailyReportChatScreen(...); return }` inside `DailyReportScreen`. Compose's
`BackHandler` dispatch is a stack: the most recently composed **enabled** `BackHandler` wins.
Because `DailyReportScreen`/`DailyReportChatScreen` registers **no `BackHandler` of its own**, the
outer, always-enabled-when-`page != 0` handler in `MainActivity` is the only one in the stack, so
system back (gesture or button) calls `goToPage(0)` directly — jumping straight to Today and
skipping the chat-to-report step entirely, exactly as the user described.

The on-screen back arrow in `BackHeader(title = "Chat about today", onBack = viewModel::closeChat)`
works correctly (`viewModel.closeChat()` sets `chatOpen = false`, returning to the Report/AI-Summary
panel) — it's only the *system* back button/gesture that misbehaves, because it never reaches
`closeChat()`.

**Confirmed precedent for the correct fix, already in this codebase:**
`app/src/main/java/com/daybook/app/ui/journal/JournalScreen.kt:63` —
`BackHandler(enabled = state.index > 0) { vm.back() }` — a nested, conditionally-enabled
`BackHandler` inside a screen that itself lives inside the outer pager/NavHost, for exactly this
"multi-step screen embedded in one tab" shape. `DailyReportChatScreen` needs the same pattern.

**Fix — file `app/src/main/java/com/daybook/app/ui/report/DailyReportScreen.kt`:**

```kotlin
@Composable
fun DailyReportScreen(...) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val chatOpen by viewModel.chatOpen.collectAsStateWithLifecycle()

    // Fix (nav bug) — mirrors JournalScreen.kt:63's nested BackHandler. Registered here (not
    // inside DailyReportChatScreen) so it's unambiguous that this is scoped to THIS screen's
    // chat state, and is composed/enabled only while chatOpen is true, taking priority over
    // MainActivity's outer `BackHandler(enabled = settledPage != 0) { goToPage(0) }` — Compose's
    // BackHandler dispatch is a stack, most-recently-composed-and-enabled wins.
    androidx.activity.compose.BackHandler(enabled = chatOpen) { viewModel.closeChat() }

    if (chatOpen) {
        DailyReportChatScreen(viewModel = viewModel, contentPadding = contentPadding)
        return
    }
    ...
```

That's the entire fix — one `BackHandler` line, no changes to `MainActivity.kt`,
`NavConfig.kt`, or `Navigation.kt` needed. The existing outer handler is correct as-is for its own
job (page-to-Today); it simply needs to not be the ONLY handler in the stack while chat is open.

**Verification:** after the fix, from Chat: system back → Report/AI Summary panel (chat closes,
still on the Daily Report tab); system back again → Today tab, matching the requested two-step
behavior exactly.

---

## 5. Daily Report screen (AI Summary tab) redesign

**Files:** `app/src/main/java/com/daybook/app/ui/report/DailyReportScreen.kt` — specifically
`AiSummaryPanel` (currently lines 384–472). No ViewModel changes needed for this section beyond
what §6/§7 already require.

### 5.1 What's actually wrong, read from the current layout
`AiSummaryPanel` is two stacked `SoftCard`s inside a `Column` that's already inset by
`Modifier.padding(horizontal = Spacing.screenH)` (applied by the caller at line 148) — that horizontal
inset is standard and correct (matches every other tab), so "unused width" is not an inset bug; it's
that the **first card** crams five unrelated things into one vertical stack with no grouping: a
"Provider" label, a `GhostButton` provider picker, a `PrimaryButton` (Generate/Regenerate), an error
slot, a `GhostButton` (Chat), a chat-ended notice, and a second error slot — seven elements, six of
them full-width blocks, in one card. That's the "bulky/tall provider card." The second card (the
summary text) has only a one-line caption + the Markdown body with no visual separation between
"metadata about the summary" and "the summary itself," which reads as under-styled.

### 5.2 Redesigned layout — component by component

**A. Compact "Provider" row — replaces the top of the current card.** Instead of a label + a
full-width `GhostButton`, use a `SettingsRow`-style single row (reuse
`app/src/main/java/com/daybook/app/ui/components/SettingsComponents.kt`'s `SettingsRow`, which
already gives "icon + title + subtitle + trailing" in one 56–60dp row): icon `DaybookIcons.Bolt`,
title = selected provider's label (or "Choose a provider" in muted color if none), `onClick` opens
the existing `BottomSheetMenu` provider picker unchanged. This turns ~90dp of vertical space
(label + spacer + full GhostButton) into one ~60dp row. Wrap it in a thin `SoftCard(tint =
CardTints.Neutral, elevation = 0.dp)` on its own — not bundled with the generate/chat buttons below.

**B. Action row — Generate/Regenerate + Chat side by side, not stacked.** Currently both are
full-width blocks stacked vertically (`PrimaryButton` then `GhostButton`). Redesign as one `Row`
below the provider card, `horizontalArrangement = Arrangement.spacedBy(Spacing.listGap)`:
- `PrimaryButton(text = Generate/Regenerate/Generating…, modifier = Modifier.weight(1f), ...)`
- `GhostButton(text = "Chat", leadingIcon = { Icon(DaybookIcons.Send, ...) }, modifier =
  Modifier.weight(1f), onClick = viewModel::openChat)` — shortened from "Chat with AI about today"
  to "Chat" now that it's a half-width button next to Generate (the fuller phrase moves to the
  Chat screen's own empty-state copy, which already exists: "Ask anything about today's report…").

This halves the vertical footprint of the two actions and reads as a natural "primary action /
secondary action" pair instead of two independent stacked cards-within-a-card.

**C. Error/notice slots stay, but consolidated.** Keep the fixed-height `Box(heightIn(min = ...))`
idiom (it's there specifically so a not-yet-erroring state doesn't jump layout when an error
appears — a deliberate, good pattern, keep it) but merge `generationError` and `chatError` into ONE
slot below the action row, since only one of Generate/Chat is ever mid-action at a time in this
panel: `state.generationError ?: viewModel.chatError.collectAsStateWithLifecycle().value`. The
`chatEndedNotice` line (H3's process-death notice) stays as its own small `Text` below that, since
it's not an error and has different, muted styling already.

**D. The summary card — real typographic/attribution treatment.** Current: a caption line then the
raw `Markdown(...)` with no breathing room. Redesign:
- Card tint: switch from `CardTints.Neutral` to a distinct tint (`CardTints.Lavender`, matching the
  app's purple accent identity used for AI-flavored surfaces elsewhere) so the summary reads as a
  distinct "generated content" surface, not just another neutral settings-style card.
- Attribution line: replace the bare `Text("Generated by ${summary.provider} / ${summary.model}",
  DaybookText.Caption)` with a small `Row`: a `DaybookIcons.Bolt` icon (12–14dp, muted tint) +
  the same caption text + (new) a relative timestamp via the already-imported date utilities, e.g.
  `formatRelativeTimestamp(summary.generatedAt)` if such a helper exists in `util/` (check
  `DateTimeUtils.kt`/`util/` for an existing "time ago" formatter before adding one — the app
  likely already has one for sync-status display; reuse it, don't reinvent).
- Add a `HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)` with
  `Spacer(Modifier.height(10.dp))` above and below it, between the attribution row and the Markdown
  body — the concrete fix for "not properly styled aligned and placed": right now there's a
  Spacer(8dp) and nothing else marking where metadata ends and content begins.
- Body typography: pass an explicit `typography` to the `Markdown(...)` call here too (same
  investment as §1's chat-bubble fix) so headings/emphasis in the AI's summary render with the
  app's own type scale (`DaybookText.CardTitle`/`bodyLarge`/`Caption`) instead of the library's
  unstyled M3 defaults, which is likely part of why it currently reads as generic/unstyled.

**E. Full-width usage.** The screen already uses the same `Spacing.screenH` horizontal inset as
every other tab (Home, Habits, Intake) — changing that inset would make Daily Report inconsistent
with the rest of the app, which is not what "adapted to width" should mean here. The actual width
problem is internal to the cards (per-element padding/line-length), addressed by A–D above:
collapsing the provider block and pairing the two action buttons both make each card's *content*
use its available width more purposefully instead of stacking narrow, isolated controls. No change
to `Spacing.screenH` or the screen's outer padding is recommended.

### 5.3 Components reused (no new primitives invented)
`SettingsRow`, `SoftCard`, `CardTints`, `PrimaryButton`, `GhostButton`, `BottomSheetMenu`,
`HorizontalDivider`, `DaybookText`, `Spacing` — all already imported/available in this file or one
`import` away. The only genuinely new thing is passing `typography` into the two existing
`Markdown(...)` calls (§1 and §5.2-D), which is a parameter on a dependency already in use, not a
new component.

---

## 5B. Report tab (the static, non-AI half) — three concrete issues found in code

The user's screenshots cover the "Report" tab too, not just "AI Summary." All three items below are
in `app/src/main/java/com/daybook/app/ui/report/DailyReportScreen.kt`, in `reportSections`/
`SectionCard`/`WorkoutSectionBody`/`TodoSectionBody` (currently lines 157–221 and 324–381).

### 5B.1 The "headerless" Health card at the top of screenshot A

**Read directly from the code: every section IS given a title.** `SectionCard` (lines 183–192)
always renders `Text(title, style = DaybookText.CardTitle, ...)` before its body — there is no code
path that renders `HealthSectionBody` without the `"Health"` title above it. `reportSections`
(lines 157–181) always wraps each section in `SectionCard(title = "Workout"/"Health"/"Intake"/
"Habits") { ... }`. So there is no missing-header bug in the strict sense — the "Health" title text
is always emitted.

**What screenshot A is most likely showing:** a day with **no workout session** (`report.workout ==
null`, so the `report.workout?.let { ... }` block at line 169 emits nothing) — on such a day, the
**first** visible card is `SectionCard(title = "Health")`, immediately after the
WeekStrip+SegmentedControl item. Screenshot B (a different day, one with a workout) shows the
expected `Workout` card first, then `Health` — consistent with this theory: it's the same code path,
just two different days, one with and one without a workout logged. This is very likely a scroll-
position/legibility read, not a missing element — `DaybookText.CardTitle` resolves to
`MaterialTheme.typography.titleMedium` in `DaybookColors.TextPrimary`, which is the same size/weight
used for every other section's visible title including "Workout" and "Intake" in the same
screenshot set, so there's no per-section style discrepancy in the code to explain a Health-specific
"missing" header.

**Recommended fix — tighten the visual grouping instead of chasing a phantom bug**, since the code
shows the title is always present: increase the gap between the WeekStrip/SegmentedControl block and
the first section card so a headerless-looking transition (however it happened for that
screenshot) can't recur — bump the item spacing immediately after the segmented control, e.g. wrap
the segmented-control item's bottom in `Spacer(Modifier.height(Spacing.listGap))` in addition to the
existing `Arrangement.spacedBy(Spacing.listGap)` between LazyColumn items (currently `12.dp`, `Tokens.kt:216`),
or bump that one gap to `Spacing.listGap * 1.5` — cheap, safe, and improves the exact transition the
screenshot flags regardless of whether the "missing header" read was a scroll artifact. **Flagged as
a decision below** — the implementer should ask for (or take) a fresh, deliberately-top-scrolled
screenshot on a no-workout day before spending more effort here; the code gives no other lead.

### 5B.2 Habits row "—" placeholder — confirmed real, not a screenshot artifact

**Root cause, `TodoSectionBody` (lines 324–381), the non-STREAK branch (lines 356–363):**

```kotlin
Icon(
    if (row.done) DaybookIcons.CheckBox else DaybookIcons.Remove,
    contentDescription = if (row.done) "Done" else row.statusLabel,
    tint = if (row.done) DaybookColors.Success else DaybookColors.TextMuted,
    modifier = Modifier.height(20.dp)
)
```

`DaybookIcons.Remove` (`app/src/main/java/com/daybook/app/ui/icons/DaybookIcons.kt:60`) is defined
as `v("Remove", "M19,13H5v-2h14v2z")` — Material's "remove" glyph, a single flat horizontal bar.
Rendered at 20dp in muted gray next to a filled checkbox glyph for "done" rows, it reads exactly as
the user described: a plain dash/em-dash placeholder, not a designed status indicator — it looks
unfinished because, visually, it *is* the wrong icon family paired against `CheckBox` (a checkbox
glyph) instead of the checkbox family's own "unchecked" counterpart.

**The fix already exists elsewhere in this codebase — `DaybookIcons.CheckBoxBlank`**
(`DaybookIcons.kt:178`), the checked/unchecked pair already used together in
`app/src/main/java/com/daybook/app/ui/components/SortSheet.kt:327`:
`if (checked) DaybookIcons.CheckBox else DaybookIcons.CheckBoxBlank`. Swap `DaybookIcons.Remove` for
`DaybookIcons.CheckBoxBlank` in `TodoSectionBody` — a one-line change, reusing an icon pair the app
already treats as its canonical checked/unchecked vocabulary (used for exactly this "is this thing
checked or not" semantic elsewhere), rather than the flat-bar "remove" glyph which has no such
paired meaning here.

**Decided: one unified "not done" look, no per-status color/icon distinction.** Every non-done
status (`Skipped`, `Missed`, `Pending`) renders the same `CheckBoxBlank` glyph, differentiated only
by `row.statusLabel`'s screen-reader/text meaning (accessibility `contentDescription` is unchanged —
still `row.statusLabel`). This matches the rest of the app: no color-coding by status was found
anywhere else for this same occurrence-status vocabulary (checked `HomeScreen.kt`,
`HomeViewModel.kt`, `DetailViewModel.kt`, `RespondViewModel.kt` — none render a status-specific
icon/color scheme for SKIPPED vs. MISSED vs. PENDING; `RoutinesScreen.kt`'s own card is oriented
around today's live streak state, not a per-occurrence history row, so it has no equivalent either).
The implementer should ship exactly the two-state (done / not-done) glyph swap above — no
three-way visual variant.

### 5B.3 Long exercise names force a 3-line wrap in the reps/weight column — confirmed layout bug

**Root cause, `WorkoutSectionBody` (lines 194–221), the exercise row (lines 209–219):**

```kotlin
session.exercises.forEach { ex ->
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(ex.name, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextPrimary)
        Text(
            ex.bestSetLabel ?: "${ex.setCount} sets",
            style = DaybookText.Caption,
            color = DaybookColors.TextMuted
        )
    }
}
```

Neither `Text` has a `Modifier.weight(...)`, `maxLines`, or `overflow` set. With
`Arrangement.SpaceBetween` and no width constraint on either child, a long, unbroken `ex.name` (e.g.
"Single Arm Tricep Extension (Dumbbell)") is measured at its full intrinsic width first, which can
leave the `Row` with too little remaining horizontal space for the second `Text` — and because that
second `Text` also has no `maxLines`/`overflow`, Compose wraps IT across multiple lines to fit the
leftover width instead of the (missing) alternative of truncating either side. That's exactly the
"reps/weight value wraps to 3 lines" bug — it only shows up on long names because short names (Bench
Press, Chest Fly, etc.) leave enough leftover width that the value text never needs to wrap.

**Fix — bound both sides explicitly, not a one-off truncation hack:**

```kotlin
Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        ex.name,
        style = MaterialTheme.typography.bodyMedium,
        color = DaybookColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
    )
    Spacer(Modifier.width(8.dp))
    Text(
        ex.bestSetLabel ?: "${ex.setCount} sets",
        style = DaybookText.Caption,
        color = DaybookColors.TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(max = 130.dp)
    )
}
```

This is the same two-sided pattern the file already uses correctly elsewhere in this exact
screen — `HealthSectionBody`'s label/value rows (lines 241–247) and `IntakeSectionBody`'s
label/time column (lines 274–277) both give the flexible side `Modifier.weight(1f)` — this exercise
row is the one place in the file that was left unweighted. The value side gets a capped
`widthIn(max = 130.dp)` (comfortably fits the longest realistic label, e.g. "10 reps @ 100.0 kg")
plus `maxLines = 1` + ellipsis as a last-resort safety net rather than ever wrapping vertically
again; the name side gets `weight(1f, fill = false)` + ellipsis so it never pushes the value off
column on an even longer name than what's in the screenshot. `TextOverflow`/`Alignment` imports are
already present in the file (used by other sections) or one-line additions.

---

## 6. Report-generation context-category toggles

**Goal:** a settings toggle per data category (Workout, Health, Intake, Habits/To-do) that gates
what `buildDailyReportPrompt` includes when generating the one-shot AI Summary (`generate()`), and
separately, what `openChat()`'s seeded context includes for chat's "today" slice. Default: all four
enabled (identical behavior to today, until the user turns one off).

### 6.1 Storage
New `AppSettings` column: `ai_report_categories` (`TEXT NOT NULL DEFAULT
'WORKOUT,HEALTH,INTAKE,TODO'`), a CSV of category ids — same idiom as `nav_tabs`/`streak_rest_days`
(a CSV column with a small parsing helper, not four separate boolean columns, so a fifth category
later is additive with no migration). New small enum, colocated with `DailyReportPrompt.kt` or a new
tiny file `app/src/main/java/com/daybook/app/data/DailyReportContext.kt`:

```kotlin
enum class ReportCategory { WORKOUT, HEALTH, INTAKE, TODO }

fun parseReportCategories(csv: String?): Set<ReportCategory> {
    val stored = csv.orEmpty().split(",").mapNotNull { s ->
        ReportCategory.entries.firstOrNull { it.name == s.trim() }
    }.toSet()
    return stored.ifEmpty { ReportCategory.entries.toSet() } // blank/corrupt -> all enabled
}
```
(Mirrors `NavConfig.visibleRoutesFrom`'s "blank/unknown falls back to everything" rule.)

### 6.2 Plumbing — this must actually gate prompt content, not just exist cosmetically
- `AppSettingsDao`/`AppSettingsRepository`: `updateAiReportCategories`/`setAiReportCategories`,
  same single-column-write pattern as §2.
- `buildDailyReportPrompt(...)` (`DailyReportPrompt.kt`) gains a `categories: Set<ReportCategory>`
  parameter. Each of the four `## Workout` / `## Health` / `## Intake` / `## Habits / to-do`
  sections is now wrapped: `if (ReportCategory.WORKOUT in categories) { ...append workout section...
  }` — a disabled category is omitted from the prompt text entirely (not sent as "No workout
  logged", which would be a lie about the data; it's simply absent from the AI's input, matching
  what the user asked for: "don't send this to the AI").
- `DailyReportViewModel.generate()` reads `appSettingsRepository.observeSettings()`'s
  `aiReportCategories` (parsed via `parseReportCategories`) and passes it into
  `buildDailyReportPrompt(...)` alongside the existing `workout`/`health`/`intake`/`todo`/`date`/
  `weightUnit` args.
- **Important:** this does NOT touch `DailyReportRepository.observeReport()` or the static Report
  panel — the static half (workout/health/intake/todo sections rendered on-screen) is unaffected;
  these toggles only gate what's fed to the AI, exactly as item 6 specifies ("gate what's included
  in the prompt-building code... not just cosmetic").

### 6.3 Settings UI
In the same `DailyReportAiSettingsScreen.kt` from §2, a new `SectionHeader("What the AI Summary can
see", subtitle = "Turn a category off to leave it out of the report the AI generates. The report " +
"page itself is never affected.")` followed by a `SettingsGroup` with four `SettingsToggleRow`s
(reusing the exact component `NavigationLayoutSections` already uses for its four tab toggles) —
"Workout", "Health", "Intake", "Habits". Default state: derive from `parseReportCategories(null)` =
all four checked. **Confirmed final: this toggle set is independent from §7.4's Chat category
toggles** — two separate `SettingsToggleRow` groups, two separate `AppSettings` columns, never
shared state.

---

## 7. Chat-specific settings: custom date-range picker + per-range categories

**Goal:** chat's seeded context currently is always "today only" (`openChat()`'s
`buildDailyReportPrompt` call uses `state.report`, which is `DailyReportRepository.observeReport(
state.selectedDate)` — a single day). **Decided:** the user wants a full custom start/end date-range
picker (not a 3-preset Today/3-day/7-day chooser) — pick any two dates, inclusive — with the
**default staying "today only"** until the user explicitly sets a range. Categories within that
range are a separate toggle set from §6's report-generation toggles (a user may want the AI Summary
to skip Health but still let Chat answer health questions, or vice versa) — confirmed, not just a
recommendation.

### 7.1 Storage — three more `AppSettings` columns
- `ai_chat_range_start` (`TEXT NOT NULL DEFAULT ''`) — ISO `yyyy-MM-dd`, or empty string meaning "no
  custom range set."
- `ai_chat_range_end` (`TEXT NOT NULL DEFAULT ''`) — same shape.
  When either is blank, chat falls back to its current default behavior: **just the day currently
  open in the Report tab** (`state.selectedDate`), exactly as today, so nothing changes for anyone
  who never touches this setting. Once both are set, chat uses that **fixed, absolute** range on
  every open/send, regardless of which day the Report tab happens to be showing — the range is a
  standing preference, not tied to whatever date is currently browsed. (Note for the implementer to
  mention nowhere else but here: a fixed range does not auto-advance with "today" — if the user sets
  "Sep 1–Sep 10" and comes back in three weeks, chat still only sees that same fixed window until
  they update it. This is the direct, correct consequence of "let the user pick any start/end
  date," not a bug.)
- `ai_chat_categories` (`TEXT NOT NULL DEFAULT 'WORKOUT,HEALTH,INTAKE,TODO'`) — same CSV/enum
  scheme as §6.2, parsed with the same `parseReportCategories` helper (shared, not duplicated) — a
  separate column and a separate settings section from `ai_report_categories` (§6), never shared.

### 7.2 Repository — the actual multi-day plumbing
`DailyReportRepository` currently only exposes `observeReport(date: LocalDate): Flow<DailyReportData>`
(single day). New method, additive, does not touch `observeReport`:

```kotlin
/** §7 — chat's configurable multi-day context. [startDate]..[endDate] inclusive. Passing the same
 *  date for both (the default/fallback case) returns exactly the one-day list the current
 *  single-day openChat() already builds from. */
suspend fun buildChatContext(startDate: LocalDate, endDate: LocalDate): List<DailyReportData> {
    val days = generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(endDate) }
        .toList()
    // Reuses observeReport(date).first() per day — one-shot suspend read, not a live multi-day
    // Flow: chat context is captured once at "open chat" / "send message" time, same as today's
    // single-day openChat() already does with state.report (a snapshot, not a live subscription).
    return days.map { d -> observeReport(d).first() }
}
```

(`kotlinx.coroutines.flow.first()` — already an available import path in this codebase's Flow
usage elsewhere.) This is a read-only, additive method; no existing call site changes.

`buildDailyReportPrompt` needs a small variant (or an overload) for multiple days:
`buildMultiDayReportPrompt(days: List<DailyReportData>, categories: Set<ReportCategory>,
weightUnit: WeightUnit): String` — loops the existing single-day section-building logic (extract the
per-day body of the current function into a private helper `appendDaySections(sb, day, categories,
weightUnit)` so both the single-day `buildDailyReportPrompt` and the new multi-day builder share one
implementation) with a `## <date>` header per day, oldest first, so multi-day answers can be
attributed correctly ("on Tuesday you logged X").

### 7.3 ViewModel — `DailyReportViewModel.openChat()`
Reads `ai_chat_range_start`/`ai_chat_range_end`/`ai_chat_categories` from
`appSettingsRepository.observeSettings()` (same pattern as §6.3). Resolution logic:

```kotlin
val startStr = settings.aiChatRangeStart
val endStr = settings.aiChatRangeEnd
val (rangeStart, rangeEnd) = if (startStr.isBlank() || endStr.isBlank()) {
    state.selectedDate to state.selectedDate   // default: today/whatever day is open, unchanged
} else {
    LocalDate.parse(startStr) to LocalDate.parse(endStr)
}
```

then calls `repository.buildChatContext(rangeStart, rangeEnd)` and builds the system message from
`buildMultiDayReportPrompt(...)` instead of the current single-day `buildDailyReportPrompt(report...)`
call. In the default (blank) case this produces byte-for-byte the same content the single-day path
already produces (a one-day list routed through the shared per-day helper), so existing behavior is
preserved exactly for anyone who never touches the new setting.

### 7.4 Settings UI — full custom date-range picker
In `DailyReportAiSettingsScreen.kt`, a further section: `SectionHeader("Chat context", subtitle =
"By default, chat only sees the day you're viewing. Pick a date range to let it see more history.")`.

**Reuses the exact export-date-range pattern already in this codebase** —
`app/src/main/java/com/daybook/app/ui/settings/SettingsScreen.kt`'s `DataSettingsScreen` (its
`startDate`/`endDate` state + two `DaybookDatePickerDialog` calls, `SettingsScreen.kt:963–994`) is
the direct precedent: a "From" row and a "To" row, each opening
`app/src/main/java/com/daybook/app/ui/TimePickerComponents.kt`'s `DaybookDatePickerDialog` (already
used elsewhere for exactly this "pick a single date, with an optional max-date clamp" job — no new
date-picker component needed). Concretely:

- Two `SettingsRow`s ("From" / "To"), each `subtitle` showing the picked date (`"Not set — using
  today"` when blank) formatted via the same `DateTimeFormatter.ofPattern("d MMM yyyy")` the export
  screen already uses, `onClick` opening that row's `DaybookDatePickerDialog`.
- "To"'s `maxDate = LocalDate.now()` (can't pick a future end date — mirrors the export screen's own
  "no future export range" guard, `SettingsScreen.kt:973–976`). "From"'s `maxDate` = the currently
  picked "To" date (or `LocalDate.now()` if "To" isn't set yet) — same "start can't be after end,
  auto-clamped" behavior the export screen's `onConfirm` callbacks already implement (`if
  (endDate.isBefore(picked)) endDate = picked` / `if (startDate.isAfter(picked)) startDate =
  picked`), reused verbatim here for the two persisted setter calls instead of local `remember`
  state.
- A `TextLink("Reset to today", onClick = { viewModel.setChatRange("", "") })` below the two rows,
  visible only when a range is currently set, so clearing back to the default is one tap rather than
  re-picking today's date twice.
- **Categories**: four `SettingsToggleRow`s, labeled identically to §6.3's ("Workout", "Health",
  "Intake", "Habits") but under this "Chat context" header, backed by `ai_chat_categories` — a
  SEPARATE, independently-stored set of toggles from §6's report toggles, confirmed final (not a
  shared checkbox state).

---

## 8. Room schema change — APPROVED, implementer may proceed

This plan requires a **Room migration**, current DB version 26 → **27**. Purely additive (five new
nullable-with-default columns on the existing `app_settings` table, no table rebuild needed since
none of the five are involved in a foreign key or NOT NULL-without-default change). **The user has
approved this migration — no further sign-off is needed before implementing it.**

| Column | Type | Default | Used by |
|---|---|---|---|
| `ai_meta_prompt` | TEXT | `''` | §2 |
| `ai_report_categories` | TEXT | `'WORKOUT,HEALTH,INTAKE,TODO'` | §6 |
| `ai_chat_range_start` | TEXT | `''` | §7 |
| `ai_chat_range_end` | TEXT | `''` | §7 |
| `ai_chat_categories` | TEXT | `'WORKOUT,HEALTH,INTAKE,TODO'` | §7 |

**`MIGRATION_26_27`** (`app/src/main/java/com/daybook/app/data/local/Migrations.kt`), modeled on the
simple additive migrations in this file (not the full-table-rebuild style `MIGRATION_25_26` needed
when SQLite `ALTER TABLE` can't express the change — this one can, since it's five plain `ADD
COLUMN`s with defaults):

```kotlin
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ai_meta_prompt TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ai_report_categories TEXT NOT NULL DEFAULT 'WORKOUT,HEALTH,INTAKE,TODO'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ai_chat_range_start TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ai_chat_range_end TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ai_chat_categories TEXT NOT NULL DEFAULT 'WORKOUT,HEALTH,INTAKE,TODO'")
    }
}
```

Plus: `AppDatabase.kt`'s `@Database(..., version = 26, ...)` → `version = 27`, the migration added
to wherever `Room.databaseBuilder(...).addMigrations(...)` lists them, and (per this project's own
`MigrationTest.kt` convention already present in `app/src/androidTest/`) a new
`MIGRATION_26_27`-covering test case following the existing pattern in that file.

**Sync/backup treatment:** following the file's own precedent (every `app_settings` column since v16
is "device-local: NOT synced, NOT in BackupModel" unless stated otherwise) — these five follow the
SAME device-local treatment as `nav_tabs`/`streak_mode`/etc., i.e. **not** added to
`CloudSyncRepository.DATA_TABLES` or `BackupModel`/`DayEntry`'s JSON export. A meta-prompt, category
toggle, or chat date-range preference is a per-device UI preference, not day-log data — consistent
with how every other Settings-screen toggle in this app is already scoped. Final — no sync/export
plumbing needed for these five columns.

---

## 9. File-by-file summary for the implementer

**New files:**
- `app/src/main/java/com/daybook/app/ui/settings/DailyReportAiSettingsScreen.kt`
- `app/src/main/java/com/daybook/app/ui/settings/DailyReportAiSettingsViewModel.kt`
- `app/src/main/java/com/daybook/app/data/DailyReportContext.kt` (or fold into
  `DailyReportPrompt.kt` — implementer's call) — `ReportCategory` enum + `parseReportCategories`.

**Modified files:**
- `app/src/main/java/com/daybook/app/data/model/DataModel.kt` — 5 new `AppSettings` columns (§8,
  approved).
- `app/src/main/java/com/daybook/app/data/local/AppSettingsDao.kt` — 5 new update queries.
- `app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt` — 5 new setters (including one
  combined `setChatRange(start: String, end: String)` for §7.4's "Reset to today" action, which
  writes both `ai_chat_range_start`/`ai_chat_range_end` together).
- `app/src/main/java/com/daybook/app/data/local/Migrations.kt` — `MIGRATION_26_27`.
- `app/src/main/java/com/daybook/app/data/local/AppDatabase.kt` — version 26 → 27, register migration.
- `app/src/androidTest/java/com/daybook/app/data/local/MigrationTest.kt` — new test case.
- `app/src/main/java/com/daybook/app/data/DailyReportPrompt.kt` — meta-prompt param, category
  gating, extract shared per-day section builder, new multi-day builder (§2, §6, §7).
- `app/src/main/java/com/daybook/app/data/DailyReportRepository.kt` — new `buildChatContext(...)`.
- `app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt` — nested `BackHandler`
  input threading is in the Screen not the ViewModel (§4); `generate()`/`openChat()` read the new
  settings and call the updated prompt builders (§2, §6, §7).
- `app/src/main/java/com/daybook/app/ui/report/DailyReportScreen.kt` — `BackHandler` (§4),
  `AssistantBubble`/chat Markdown typography (§1), `AiSummaryPanel` redesign (§5), extra top spacing
  before the first Report-tab section card (§5B.1), `TodoSectionBody`'s status icon swapped to
  `DaybookIcons.CheckBoxBlank` (§5B.2), `WorkoutSectionBody`'s exercise row given weighted/bounded
  columns so long names don't wrap the reps/weight value (§5B.3).
- `app/src/main/java/com/daybook/app/ui/settings/SettingsScreen.kt` — add a row/entry point to the
  new Daily Report AI settings screen (near the existing "AI Providers" row).
- `app/src/main/java/com/daybook/app/ui/MainActivity.kt` — register the new
  `composable("settings_daily_report_ai") { ... }` destination, same shape as
  `"settings_ai_providers"` (line ~921).

**Not touched:** `NavConfig.kt`, `Navigation.kt`, `CloudSyncRepository.kt`, `BackupModel.kt`,
`ExportImportRepository.kt` (per §8's device-local recommendation), `AiKeyStore.kt`, any of the
`data/ai/*Provider.kt` adapters (the provider interface itself needs no change — every fix here is
either prompt text, settings plumbing, or Compose layout).
