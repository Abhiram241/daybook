# Daybook — feature inventory

What the app does, area by area. Written against **versionCode 13 / versionName 0.5.5 / Room DB v17**
(the journal-habit round — versionCode/versionName are unchanged from the prior customization round;
only the DB schema moved). Package `com.daybook.app`. Android-only, offline-first, dark-only.

Each feature has a one-to-three-sentence description and, in parentheses, the main files/screens
that implement it. "Where the setting lives" is called out wherever a feature is configurable.

---

## 1. Onboarding & account

- **Google sign-in gate.** The app is unusable until you sign in with Google (the only auth
  method). The gate has no back button and no skip; system back does nothing on it. Sign-in
  happens *before* onboarding so the name can be pre-filled from the Google display name.
  (`ui/account/SignInGate.kt`, `ui/account/SignInContent.kt`, `ui/MainActivity.kt` launch gate,
  `data/auth/AuthRepository.kt`)
- **Bottom-anchored "Continue with Google" button.** The headline and blurb scroll at the top;
  the button is pinned above the navigation-bar inset so it stays in thumb reach on a small phone.
  (`ui/account/SignInContent.kt`, `ui/components/StickySaveBar.kt`)
- **Name asked once, only when unknown.** On a genuine first login with a derivable name (Google
  `displayName` non-blank), the name is saved silently and onboarding never appears. Only a first
  login with a blank display name shows the "What should we call you?" screen. Changing the name
  later is done in Settings → Account.
  (`ui/onboarding/OnboardingScreen.kt`, `ui/onboarding/deriveOnboardingName` /
  `DeriveOnboardingNameTest`, `ui/MainActivity.kt`)
- **Onboarding screen.** A one-screen explainer: a pastel tint sample strip, a welcome hero, the
  name field, and three feature cards (smart reminders, one-tap logging, streaks). "Get started"
  is disabled until a name is entered. (`ui/onboarding/OnboardingScreen.kt`)
- **Conflict / restore dialog.** On first sign-in, if the cloud already holds data that doesn't
  match this device, a dialog offers "restore from cloud" (replace this device) vs "keep this
  device" (replace the cloud), or dismiss (sync stays paused, re-prompts next launch). Shows
  concrete counts (local vs remote habits and days).
  (`data/sync/CloudSyncRepository.kt` `raiseConflict` / `resolveConflict`, `ui/account/AccountScreen.kt`)
- **Four-stage launch gate.** Every launch is routed through, outermost first: (1) app lock,
  (2) auth (neutral splash while Loading, blocking sign-in if not signed in), (3) onboarding
  (neutral splash while unsettled, onboarding screen if incomplete), (4) the app. The rule "never
  route from an unsettled snapshot" prevents an onboarding-screen flash on every launch.
  (`ui/MainActivity.kt` `when {…}` block)

## 2. Today screen

- **Rotating greeting + tone.** A greeting line that rotates daily through a pool ("Welcome back",
  "Let's go", "Good morning, {name}", …). Tone is configurable: **Warm** (the rotating pool),
  **Plain** (one fixed "Hi, {name}"), **Minimal** (no greeting line at all). A separate toggle
  controls whether the time-of-day word ("Good morning / afternoon / evening / night") is used.
  Computed once per app-open / boundary tick, not per scroll frame.
  (`ui/home/HomeViewModel.kt` `renderGreeting` / `GreetingRenderTest`; setting: Settings →
  Today & calendar → Greeting)
- **"N left today" hero line.** A big count of unresolved reminders for the viewed day. Phrasing
  is configurable: "13 left today" / "13 to go" / "13 tasks" (singular "1 task") / **Hidden**.
  Shows "All done" when nothing is pending.
  (`ui/home/HomeScreen.kt` `heroText`; setting: Settings → Today & calendar → Greeting → Hero line)
- **Week strip ↔ inline month calendar.** A horizontal seven-day strip that expands in place into
  a month grid and collapses back, with a size-morph animation. Tapping a day selects it; the
  selected column stays put when you change week-start. Default view (week or month on cold start)
  is configurable.
  (`ui/components/WeekStrip.kt`, `ui/home/HomeScreen.kt`; setting: Settings → Today & calendar →
  Calendar → Default calendar view)
- **"Your progress" cards.** Two cards, **Habits** (Mint) and **Intake** (Peach), each showing a
  percent-complete ring/bar for the viewed day and a streak flame pill when the streak is > 0.
  The two cards use their own tint accent, not the app accent, so they read as distinct.
  (`ui/home/HomeScreen.kt` `ProgressCard` / `PastelProgressBar`)
- **Streak flame pill.** A small pill showing the current streak count, measured *as of the viewed
  day* (not a single global number). Hidden entirely when "Show streak flames" is off.
  (`ui/home/HomeScreen.kt`, `util/streak/StreakCalculator.kt`; setting: Settings → Today &
  calendar → Streak display → Show streak flames)
- **Reminders list.** The viewed day's reminder items from both domains, each a pastel card with
  icon, title, scheduled time and inline actions. Resolved items can be hidden.
  (`ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`)
- **Inline resolve / skip / reply / journal actions.** Habit cards: Complete / Skip — except a
  Journal habit's card, which opens the chat instead (§5b). Intake cards: an inline reply field
  (plus red-flag / suspected-food / outside-food inputs for FOOD), Skip. (The old Intake-Journal
  card, which opened the legacy stepper, no longer occurs on live data — Journal is retired as an
  Intake type, §5a.) A resolved entry can be tapped to re-edit in place — a resolved Journal habit
  entry opens `HabitJournalEditScreen` (§5b) rather than the chat.
  (`ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt` `skipItem` / `replyToItem` /
  `isHabitJournal`, `data/OccurrenceScheduler.kt`)
- **Past-day backfill.** Selecting a past day turns the cards into "log this retroactively" — you
  can record done / skipped / a note for a slot that has no alarm. Blocked when the containing
  cloud month isn't resident in Room yet (a "monthReady" guard).
  (`ui/home/HomeScreen.kt`, `data/OccurrenceScheduler.kt` `backfillHabit` / `backfillFoodMed` /
  `canBackfill` / `BackfillEligibilityTest`)
- **Reminders filter sheet.** Facet filter by Habits / Intake / Journal, plus a "show resolved"
  toggle. The default for "hide resolved" is a setting; the filter sheet's session state also
  writes that default back. (`ui/home/HomeScreen.kt`; setting: Settings → Today & calendar →
  Reminders → Hide resolved reminders by default)

## 3. Habits (Routines)

- **Four habit types.**
  - **Individual** — a reminder per configured time; the classic behaviour. Each configured
    time+weekday produces its own occurrence and its own notification.
  - **Batch** — no per-time reminders; surfaced by a single app-wide daily check-in notification
    at a configurable time. Still keeps per-habit occurrence rows so its streak and Today card
    work exactly like an Individual's.
  - **Ongoing** (enum value `STREAK`, label "Ongoing") — a passive running day-count. Zero
    occurrences, zero alarms, zero notifications, nothing on Today. "Start" begins the count;
    "Mark as broken" records the longest run and resets. The Today "Show streak flames" toggle
    does **not** hide an Ongoing habit's counter — that is the whole point of the type.
  - **Journal** (enum value `JOURNAL`, journal-habit round) — schedules and arms alarms exactly
    like Individual (its own per-time occurrences, own notifications, own streak), but each fire
    opens a chat-style Q&A instead of a Complete/Skip card. The question set is configured
    per-habit (add / edit / delete / reorder) in the habit form itself — there is no longer a
    global question list. An answered slot resolves to `Occurrence.Status.LOGGED` (not
    `COMPLETED`) and still counts toward the habit's streak.
  (`data/model/DataModel.kt` `HabitType`, `data/OccurrenceScheduler.kt` `isNoScheduleHabit` /
  `armsOwnAlarm` / `OngoingSchedulerDecisionTest`, `util/streak/OngoingStreak.kt` `daysSince`,
  `ui/routines/RoutinesScreen.kt`)
- **Create / edit / archive / delete.** Add and Edit forms are structurally identical (same field
  order, same grouping). Archive is a soft-delete that keeps history; Delete asks for confirmation
  and removes the habit. Archived habits are shown via a filter toggle.
  (`ui/routines/AddHabitScreen.kt`, `ui/routines/EditHabitScreen.kt`, `ui/routines/HabitForm.kt`,
  `ui/routines/RoutinesViewModel.kt`, `ui/components/ConfirmDeleteDialog.kt`)
- **Active days.** Seven weekday toggle chips; empty = every day. (`ui/routines/HabitForm.kt`,
  `util/DateTimeUtils.kt`)
- **Per-time reminders.** Add / remove clock times; the "next reminder" preview updates live.
  (`ui/routines/HabitForm.kt`, `ui/TimePickerComponents.kt`)
- **Snooze interval.** Per-habit, minimum 10 minutes (Doze rate-limits exact alarms below that).
  New forms open at the app default snooze; editing keeps the item's own value.
  (`ui/routines/HabitForm.kt`, `ui/routines/DefaultSnoozeSeedTest.kt`)
- **Icon + card tint.** A curated icon set (~21 glyphs) and an optional pastel tint override
  (otherwise auto-assigned by list position). (`ui/icons/Icons.kt`, `ui/theme/Tokens.kt`
  `CardTints`, `ui/routines/HabitForm.kt`)
- **Advanced section.** Collapsed by default; auto-expands on Edit when any advanced field is
  non-default. Holds: per-habit reminder text (hidden for Ongoing and Journal), "why this matters"
  note. (`ui/routines/HabitForm.kt`, `ui/components/Components.kt` AdvancedSection)
- **Per-habit journal questions.** Only shown when Type = Journal: an add / edit / delete / reorder
  list editor (same "at least one question" rule the old global screen had), seeded with one
  default question ("What's on your mind?") the first time a habit is switched to Journal. Backed
  by an in-memory edit list, no separate Room table — the ordered list is saved as
  `Habit.journalQuestionsJson` when the form saves. (`ui/routines/HabitForm.kt`
  `JournalQuestionsFormGroup`, `data/JournalQuestionListEdits.kt`)
- **Habit list filter sheet.** Facet by type (Individual / Batch / Ongoing) with counts, plus
  show-archived. The type facet is *not* persisted (resets each session); sort and show-archived
  are. **Note:** the type facet list is still hardcoded to Individual/Batch/Ongoing — there is no
  "Journal" chip yet, so Journal habits can only be excluded from view via the other three facets
  combined, never selected on their own. (`ui/routines/RoutinesScreen.kt`,
  `ui/components/SortSheet.kt`)
- **Habit detail — History + Stats tabs.** A `SegmentedControl` switch. History is a paged
  timeline of events (shown / snoozed / completed / skipped / replied). Stats shows completion
  rate, this-month figure, current streak and best (streak figures hidden when "Show streak
  flames" is off; hidden entirely for an Ongoing habit, which shows its day count instead). For a
  Journal habit, an answered (`LOGGED`) history row opens `HabitJournalEditScreen` instead of
  toggling Complete/Skip, and completion-rate / this-month / streak figures are computed against
  `LOGGED` rather than `COMPLETED` (a bug where `StreakCalculator` and `DetailViewModel.computeStats`
  only recognised `COMPLETED` was fixed as part of adding this type). The detail header carries the
  "why this matters" note as a quoted accent line.
  (`ui/detail/DetailScreen.kt`, `ui/detail/DetailViewModel.kt` `isHabitJournal`,
  `ui/detail/DetailPaging.kt`, `util/streak/StreakCalculator.kt`)
- **Ongoing habit card.** Not started: a "Start" link that opens a themed date picker capped at
  today (`DaybookDatePickerDialog(maxDate = today)`) so a forgotten start day can be backdated,
  with `clampStreakStart` as a defense-in-depth guard against a future date sneaking through.
  Running: the day count plus a muted "Best N" figure, a direct danger-styled "Mark as broken"
  icon inline on the card (moved off the ⋮ overflow sheet, which no longer offers it), and a
  History/Stats icon that opens the same Detail screen as tapping the card — an explicit,
  discoverable entry point now that the row itself carries interactive controls of its own.
  (`ui/routines/RoutinesScreen.kt` `HabitCard`, `ui/routines/RoutinesViewModel.kt`
  `clampStreakStart` / `startStreak` / `markStreakBroken`)

## 4. Intake (Food / Med)

- **Types: Food, Med, Custom.** Custom uses a named, reusable category (self-deduplicating by
  name). **Journal is retired as an Intake type as of the journal-habit round** — the Type picker
  no longer offers it (`TaskType.entries.filter { it != TaskType.JOURNAL }`), and journal-as-habit
  (§5) replaces it going forward; see §5 for what happens to old Journal-type intake data.
  (`data/model/DataModel.kt` `TaskType` / `CustomCategory`, `ui/foodmed/FoodMedForm.kt`,
  `data/CustomCategoryRepository.kt`)
- **Per-reminder times, active days, snooze.** Same schedule model as habits.
  (`ui/foodmed/FoodMedForm.kt`)
- **Red-flag food diary (Crohn's).** Each FOOD log carries an optional trigger marker —
  none / maybe / red — plus a free-text "suspected trigger food". A FOOD reminder can also carry
  defaults for both, pre-filled every time it's logged. NONE is stored as NULL so an unflagged
  log is indistinguishable from a pre-feature row.
  (`data/model/DataModel.kt` `RedFlag`, `ui/foodmed/FoodMedForm.kt`, `ui/respond/RespondScreen.kt`,
  `data/OccurrenceScheduler.kt` `logFoodMed`)
- **"Outside food" marker.** A per-log "eaten out / not home-prepared" flag, with a per-reminder
  default. FOOD type only. (`data/model/DataModel.kt` `defaultOutsideFood` / `outsideFood`)
- **Per-reminder custom notification / prompt text.** Replaces the default "What did you have?" in
  both the inline reply label and the notification. Reusable prompt messages are saved and
  self-deduplicate by name. (`data/model/DataModel.kt` `promptMessage` / `CustomPrompt`,
  `util/notification/NotificationUtils.kt` `resolvePrompt`, `data/CustomPromptRepository.kt`)
- **Per-reminder "why / motivation" note.** Shown on the item's Detail header as a quoted accent
  line, mirroring the habit side. (`data/model/DataModel.kt` `FoodMedTask.motivation`,
  `ui/detail/DetailScreen.kt`)
- **Intake list + detail.** Same list/filter/detail structure as habits (History + Stats tabs,
  streak = consecutive fully-logged days). (`ui/foodmed/FoodMedScreen.kt`,
  `ui/foodmed/FoodMedViewModel.kt`, `ui/detail/DetailScreen.kt`)

## 5. Journal mode

**As of the journal-habit round, Journal moved from an Intake (`TaskType.JOURNAL`) concept to a
4th `HabitType.JOURNAL`.** The two are separate, similarly-shaped features that must not be
conflated: §5a is legacy and effectively dead; §5b is the new, live feature.

### 5a. Legacy Intake-Journal (retired, kept only for safety)

- **The Intake Journal type is gone from live use, not just "unchanged".** The migration to DB v17
  purges every existing `food_med_tasks` row of type `JOURNAL` (and its occurrences/events) and
  drops the old global `journal_questions` table outright — a deliberate "fresh start", not a
  forward migration. The Intake Type picker no longer offers Journal as a choice, so no *new*
  Journal-type intake reminder can be created. There is no more Settings → Journal questions
  screen. (`data/local/Migrations.kt` `MIGRATION_16_17`, `ui/foodmed/FoodMedForm.kt` Type picker)
- **`JournalScreen.kt` / `JournalViewModel.kt` (the original conversational one-question-at-a-time
  stepper — Back / Next, progress bar, Save on the last step) are untouched code-wise** but are
  now unreachable from any live flow: kept only so a genuinely-ancient still-pending alarm or
  notification from before the update can't crash, falling back to a single fixed question
  ("What's on your mind?") since the global question set it used to read no longer exists.
  (`ui/journal/JournalViewModel.kt` class doc, `data/ExportImportRepository.kt` — a legacy
  `TaskType.JOURNAL` value in an imported backup decodes losslessly but is remapped to `CUSTOM`
  rather than reintroducing a dead type)

### 5b. Habit-Journal (new, live feature)

- **Per-habit, ordered question set.** No more global list — add / edit / delete / reorder
  questions in the habit's own form (Type = Journal), with the same "at least one question" rule
  the old global screen had. Stored as `Habit.journalQuestionsJson`.
  (`ui/routines/HabitForm.kt` `JournalQuestionsFormGroup`, `data/JournalQuestionListEdits.kt`)
- **Chat-style entry, not a stepper.** Tapping a Journal habit's reminder opens
  `HabitJournalChatScreen` — a bubble-per-question/answer transcript (one question live at a time,
  the running conversation stays visible above it), auto-saving as each answer is sent and
  auto-popping the screen once the last question is answered. This is a different UI from the
  legacy stepper in §5a — do not confuse the two.
  (`ui/journal/HabitJournalChatScreen.kt`, `ui/journal/HabitJournalChatViewModel.kt`
  `advanceChat` / `ChatFlowTest`)
- **Each entry snapshots its questions.** The ordered `[{"q":…,"a":…}]` snapshot is stored on the
  habit occurrence (`qa_json`), so later edits to a habit's question list never rewrite past
  entries — same trick the old Intake-Journal used.
  (`data/model/DataModel.kt` `HabitOccurrence.qaJson`)
- **Editing an answered entry opens a plain form, not the chat.** `HabitJournalEditScreen` shows
  all questions/answers as ordinary text fields; re-saving updates in place (no new event, keeps
  the original timestamp, matches `isFoodMedEdit`'s pattern on the Intake side).
  (`ui/journal/HabitJournalEditScreen.kt`, `ui/journal/HabitJournalEditViewModel.kt`)
- **Schedules and arms alarms like an Individual habit** — its own per-time occurrences, own
  notifications, own re-nag; `armsOwnAlarm` includes `JOURNAL` alongside `INDIVIDUAL`. An answered
  slot resolves to `Occurrence.Status.LOGGED` and counts toward the habit's normal streak.
  (`data/OccurrenceScheduler.kt` `armsOwnAlarm`, `util/streak/StreakCalculator.kt`)
- **Routing distinguishes the two Journal features.** `MainActivity.kt` routes
  `habit_journal_chat/…` and `habit_journal_edit/…` for this feature, using
  `OccurrenceScheduler.isHabitJournalOccurrence` — separate from the pre-existing
  `isJournalOccurrence` check that still (nominally) covers §5a. `HomeScreen.kt` / `HomeViewModel.kt`
  and `DetailScreen.kt` / `DetailViewModel.kt` carry an `isHabitJournal` flag so a Journal habit's
  occurrence renders/routes correctly instead of falling through to a generic Complete/Skip card.
  (`ui/MainActivity.kt`, `data/OccurrenceScheduler.kt` `isHabitJournalOccurrence`)

## 6. Reminders / notifications / alarms

- **Exact alarms.** Each item keeps exactly one "next" `AlarmManager.setExactAndAllowWhileIdle`
  alarm armed; falls back to an inexact alarm on `SecurityException` (permission not granted).
  The next occurrence is armed only when the current one is resolved, or on a full sweep.
  (`data/OccurrenceScheduler.kt`, `util/notification/NotificationUtils.kt` `scheduleReminderAlarm`)
- **Per-category small icons.** Status-bar glyph by task type (med / food / neutral fallback).
  (`util/notification/NotificationUtils.kt` `smallIconFor`, `res/drawable/ic_notif_*`)
- **Notification actions.**
  - Habit: **Skip / Snooze / Complete**.
  - Intake: **Skip / Snooze / Reply** (Reply uses `RemoteInput` — an inline text field in the
    notification, WhatsApp-style).
  - Intake-Journal (legacy, retired — see §5a): **Skip / Snooze**, body tap = Open.
  - Habit-Journal (§5b): **Skip / Snooze only, no Complete** — posted on the same `habits_v2`
    channel as a plain habit, since a chat-answered entry has no "just tick it off"; body tap
    opens the chat. (`util/notification/NotificationUtils.kt` `showHabitJournalNotification`)
  - Batch check-in: **Snooze / Done**, body tap = open app.
  Button order is fixed so muscle memory works.
  (`util/alarm/NotificationActionReceiver.kt`, `util/notification/NotificationUtils.kt`)
- **Unlimited re-nag until resolved.** When a reminder fires, it also schedules a "re-nag" alarm
  one snooze-interval later for the same occurrence; this repeats forever until you Complete /
  Skip / Reply. "No response" = the re-nag firing again while still PENDING — the snooze interval
  *is* the timeout. (`util/alarm/AlarmReceiver.kt` `fireHabit` / `fireFoodMed`)
- **Notification auto-dismiss on answer.** Answering from the shade or in-app cancels the shade
  notification unconditionally — even when the scheduler no-ops because the occurrence was already
  resolved. A reply posts a short "Logged: …" ack on the same id with a 3 s timeout as a backstop
  for stuck "sending" UI (notably on Motorola).
  (`util/alarm/NotificationActionReceiver.kt` `finally` block,
  `util/notification/NotificationUtils.kt` `postReplyAck`)
- **Batch check-in combined notification.** One notification covering every unresolved Batch habit
  for today, listing up to four titles. Posts nothing when nothing is unresolved. Re-arms for
  tomorrow every time it fires. (`util/notification/NotificationUtils.kt` `showBatchHabitNotification`,
  `data/OccurrenceScheduler.kt` `armBatchCheckIn` / `unresolvedBatch` / `BatchCheckInTest`;
  setting: Settings → Notifications & alarms → Check-in time)
- **Quiet hours.** A due alarm that would fire inside the user's quiet window is *deferred to the
  window's end* (wraps past midnight), never dropped. Applies to arm, snooze and the batch
  check-in alike. Identity no-op when disabled.
  (`data/QuietHours.kt` `deferIfInsideQuietHours` / `QuietHoursTest`, `data/OccurrenceScheduler.kt`
  `quietDefer`; setting: Settings → Notifications & alarms → Quiet hours)
- **Boot / time-change re-arm.** After reboot, app update, or a timezone / clock change, every
  alarm is re-armed via a full sweep. A timezone change does *not* re-bucket history (each
  occurrence stores its local calendar date at creation).
  (`util/alarm/BootCompletedReceiver.kt`, `AndroidManifest.xml`)
- **Rolling window top-up.** A daily `WorkManager` worker keeps the reminder window generated even
  if the app is never opened. (`util/work/WindowRefreshWorker.kt`, `DaybookApplication.kt`)
- **Permission flows.** `POST_NOTIFICATIONS` runtime prompt (asked once; "permanently denied"
  routes to app notification settings). Exact-alarm permission asked once, sequenced *after* the
  notification decision so two system dialogs never stack. Both re-grantable from Settings.
  (`ui/MainActivity.kt` `LaunchedEffect` blocks, `ui/settings/SettingsScreen.kt` `PermissionRow`)
- **Diagnostics.** "Send test notification" (isolates `notify()` from the whole alarm pipeline)
  and "Re-arm all reminders". A "why a notification wouldn't appear" check surfaces app-level and
  per-channel blocks. (`ui/settings/SettingsScreen.kt`,
  `util/notification/NotificationUtils.kt` `postTestNotification` / `notificationBlockReason`)
- **Pre-time resolution suppresses the notification.** Resolving an occurrence before its time
  cancels its armed alarm; the fire path also no-ops if the row is no longer PENDING.
  (`data/OccurrenceScheduler.kt` resolve actions, `util/alarm/AlarmReceiver.kt` status check)
- **Channels.** Two: `habits_v2`, `food_med_v2`, both `IMPORTANCE_HIGH`. Channel IDs are
  versioned because a channel is immutable once created — legacy `habits` / `food_med` are deleted
  on startup. (`util/notification/NotificationUtils.kt` `createNotificationChannels`)

## 7. Streaks

- **Strict vs Lenient mode.** Strict (default, byte-identical to older behaviour): a day counts
  only when every occurrence that day reached its done state. Lenient: a day also counts when
  every occurrence was done *or deliberately skipped*; a still-pending / missed past day still
  breaks the run. (`util/streak/StreakCalculator.kt` `daySatisfies` / `StreakCalculatorTest`;
  setting: Settings → Today & calendar → Streak display → Streak counting)
- **Rest days.** A CSV of weekdays that neither break nor extend a run when empty; a rest day that
  *was* fully completed still counts +1. (`util/streak/StreakCalculator.kt` `parseRestDays` /
  `computeStreaks`; setting: Settings → Today & calendar → Streak display → Rest days)
- **Hide-streaks toggle.** Gates *rendering only* — the flame pill on Today and the current/best
  figures on Detail → Stats. The streak is still computed. Does not affect an Ongoing habit's
  counter. (`ui/home/HomeScreen.kt`, `ui/detail/DetailViewModel.kt`; setting: Settings → Today &
  calendar → Streak display → Show streak flames)
- **Local-date bucketing.** Streaks group by calendar day in the device timezone; week-start does
  *not* affect streak counts. (`util/streak/StreakCalculator.kt`)
- **"Streak as of the viewed day".** The Today flame reflects the day on screen, not a single
  global number. (`util/streak/StreakCalculator.kt` `asOf` parameter)
- **Ongoing-habit day counter.** An inclusive running day count from the start date (started
  today → 1). "Mark as broken" stores `max(longest, current run)`.
  (`util/streak/OngoingStreak.kt` `daysSince` / `OngoingStreakTest`, `data/HabitRepository.kt`
  `startStreak` / `markStreakBroken`)

## 8. Personalisation / Settings

All of these except accent / font / profile photo / check-in time are **device-local** (not
synced, not in the backup). Settings hub row order: Account & sync · Appearance · Today & calendar
· Navigation · Notifications & alarms · App lock · Export & import. **The "Journal questions" row
is gone** (journal-habit round) — question sets are now configured per-habit in the habit form
(§5b), not globally in Settings.
(`ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `data/model/DataModel.kt`
`AppSettings`, `data/AppSettingsRepository.kt`)

- **Accent colour** — 5 options; tints buttons, toggles, highlights and every default M3 control.
  (Settings → Appearance → Accent color; `ui/theme/Accent.kt`, `ui/theme/Theme.kt`)
- **App font** — 5 typefaces, applied app-wide. (Settings → Appearance → Font; `ui/theme/Type.kt`,
  `res/font/`)
- **Week-start day** — Monday / Sunday / Saturday; re-lays-out the week strip and month grid.
  (Settings → Today & calendar → Calendar; `util/WeekStartTest`)
- **12- / 24-hour clock** — flips every time display and the M3 time-picker dial; storage stays
  `"HH:mm"`. (Settings → Today & calendar → Calendar → 24-hour time; `util/DateTimeUtils.kt`)
- **Default calendar view** — week or month on cold start (in-session collapse survives rotation).
  (Settings → Today & calendar → Calendar)
- **Greeting tone / time-of-day word / hero phrasing** — see §2. (Settings → Today & calendar →
  Greeting)
- **Reduce motion** — drops springs / slides / size-morphs for plain fades; also honoured when
  the OS animator duration scale is 0. (Settings → Appearance → Accessibility; `ui/theme/Theme.kt`
  `effectiveReduceMotion` / `ReduceMotionTest`)
- **Default landing tab** — which tab a cold start opens on. (Settings → Navigation;
  `ui/NavConfig.kt` `landingIndex` / `NavConfigTest`)
- **Hide bottom-nav tabs** — Habits and/or Intake can be hidden (Today is always shown and always
  first). The pager page count follows. Reorder is not implemented (stored as an ordered CSV so it
  can be added later). (Settings → Navigation; `ui/NavConfig.kt` `visibleRoutesFrom` / `toggleRoute`)
- **Persisted list state** — Habits/Intake sort (Added / Name / Next reminder) and show-archived,
  plus Today "hide resolved" default, all survive a relaunch. There is deliberately no Settings
  row for these — the filter sheet just remembers.
  (`ui/components/SortSheet.kt`, `ui/foodmed/SortComparatorTest`)
- **Default snooze** — seeds new forms; the batch check-in re-arms at the new interval.
  (Settings → Notifications & alarms → Snooze)
- **Profile photo** — the Google account photo or a picked image, copied into `filesDir`, EXIF
  orientation corrected. Shown on tab headers and the account screen. (Settings → Account;
  `data/ProfilePhotoStore.kt`, `data/auth/GoogleAvatarFetcher.kt`, `ui/components/Avatar.kt`)
- **Habit check-in time** — the single app-wide Batch check-in time. (Settings → Notifications &
  alarms → Check-in time)

## 9. Data

- **Date-range JSON export.** Pick a start and end date, get a JSON of just that range. For a
  signed-in user whose older months are evicted from Room, the export first hydrates every
  overlapping cloud month from Firestore (with a progress indicator), then writes a complete file;
  an offline failure aborts with a clear message rather than a truncated file. Uses the v2
  `BackupModel` shape; `meta` records the range. Shared via the Storage Access Framework.
  (Settings → Export & import; `data/ExportImportRepository.kt` `exportRange` / `exportRangeJson`,
  `data/sync/CloudSyncRepository.kt` `hydrateRange` / `HydrateResult`,
  `ui/settings/SettingsScreen.kt`)
- **Import.** Per-log merge, lenient. A range-scoped file imports *without* deleting cloud months
  outside the range. Event ids are stripped on import so device-local rowids never collide.
  v1-format files are rejected (no upgrade path). (`data/ExportImportRepository.kt` `importAllData`
  / `importRange` / `importMonth` / `mergeMonth`, `RangeImportNonDestructiveTest`,
  `ImportMonthNotifPreserveTest`)
- **Share backup.** The export is handed to the system share sheet via a `FileProvider`.
  (`ui/settings/SettingsScreen.kt`, `AndroidManifest.xml` provider, `res/xml/file_paths.xml`)
- **Cloud sync.** Month-partitioned Firestore. **Room is the source of truth**; Firestore holds a
  gzipped, derived mirror: `users/{uid}` (definitions blob + `definitionsHash` + `monthHashes`
  summary + revision) and `users/{uid}/months/{YYYY-MM}` (that month's day-logs blob + hash).
  Debounced push (3 s, flushed on app stop), scoped snapshot listeners on resident months only,
  first-sign-in bootstrap (attach / push-local / pull-remote / conflict), per-log merge on
  incoming months, only current+previous month hydrated (older fetched on demand and evicted once
  the hash matches), 90-day retention sweep of no-content activity events (runs signed-out too).
  (`data/sync/CloudSyncRepository.kt`, `data/sync/SyncLogic.kt`, `data/sync/MonthPartitioner.kt`,
  `data/sync/ContentHash.kt`, `data/sync/PayloadCodec.kt`, `data/RetentionPolicy.kt`,
  `firestore.rules`)
- **Sign-out wipes local data.** A genuine sign-out cancels every alarm/notification, then wipes
  all data tables in one transaction, then resets the sync bookkeeping. Signing back into the same
  account re-pulls everything from the cloud. (`data/sync/CloudSyncRepository.kt`
  `wipeLocalForSignOut`)

## 10. Security & privacy

- **App lock.** Optional 4-digit PIN (PBKDF2-hashed with a per-install salt in
  `EncryptedSharedPreferences`) plus `BIOMETRIC_STRONG`. Locks on cold start and after a
  configurable background timeout (Immediately / 1 / 5 / 15 min). Unlimited attempts, no cooldown.
  Change or disable from Settings; a successful biometric prompt is sufficient to change lock
  settings. The lock sits *outside* everything, including the sign-in form (a locked device must
  not expose the account email). (`data/lock/AppLockRepository.kt`, `data/lock/PinHasher.kt` /
  `PinHasherTest`, `data/lock/BiometricGate.kt`, `ui/lock/LockScreen.kt`,
  `ui/lock/AppLockSettingsScreen.kt`; setting: Settings → App lock)
- **Delete-account danger zone.** A Danger-styled section in Settings → Account. Deletes the
  Firestore parent doc *and* every month sub-collection (a parent delete does not cascade), while
  the token is still valid, with an optional "also erase this device" checkbox and a re-auth retry
  path. (`ui/account/AccountScreen.kt`, `ui/account/AccountViewModel.kt`,
  `data/sync/CloudSyncRepository.kt` `deleteRemoteDoc`)
- **No analytics, offline-first.** No tracking SDKs. Every Firebase call is failure-inert and none
  is on the launch path; the app is fully usable with no network once signed in.
  (`data/sync/CloudSyncRepository.kt` class doc, `di/FirebaseModule.kt`)

## 11. Navigation

- **Three tabs — Today / Habits / Intake — as a swipeable pager.** One `HorizontalPager`; swipe
  between tabs, or tap the bottom nav (a tab tap always snaps, never travels through the middle
  page). Today is index 0 and system back from another tab returns to Today first.
  (`ui/MainActivity.kt` `MainApp`, `ui/components/Navigation.kt`)
- **Detail / Add / Edit / Journal / Habit-Journal chat / Habit-Journal edit / Respond pages** are
  stack routes over the pager (`journal/…`, `habit_journal_chat/…`, `habit_journal_edit/…`,
  `respond/…`), so a notification deep link reaches the right screen even if its tab is hidden.
  (`ui/MainActivity.kt` `NavHost`)
- **Settings hub with sub-screens** — Account, Appearance, Today & calendar, Navigation,
  Notifications & alarms, App lock, Export & import. (No more "Journal questions" sub-screen —
  that screen and its route are deleted; see §5b.)
  (`ui/settings/SettingsScreen.kt`, `ui/settings/NavigationSettingsScreen.kt`)
- **Notification deep link** — a tapped reminder opens its Detail screen, or the appropriate
  journal UI for a journal occurrence: the legacy stepper for the retired Intake-Journal type
  (§5a, effectively unreachable now) or the chat for a Journal habit (§5b).
  (`ui/MainActivity.kt` `pendingDeepLink` handler / `isHabitJournalOccurrence` check,
  `util/notification/NotificationUtils.kt` `contentIntent`)

## 12. Under the hood

- **Tech stack.** Kotlin 2.0.21 (K2), Jetpack Compose (BOM 2024.12.01) + Material 3, MVVM +
  Repository, Hilt (kapt) for DI, Room 2.6.1 (SQLite) for local storage, `AlarmManager` for exact
  reminders + `WorkManager` for periodic top-up, `kotlinx.serialization` for JSON, Firebase Auth
  (Google-only, Credential Manager) + Firestore for sync, Coil for images, `androidx.biometric` +
  `security-crypto` for the app lock. AGP 8.3.2, Gradle 8.6, minSdk 26, compile/targetSdk 34.
- **Data model.** `Habit` / `HabitOccurrence` / `HabitEvent`, `FoodMedTask` / `FoodMedOccurrence`
  / `FoodMedEvent`, `AppSettings` (single row), `CustomCategory`, `CustomPrompt`. **`JournalQuestion`
  is gone** (journal-habit round) — its table is dropped and a Journal habit's questions live
  instead as an ordinary string column on `Habit` (`journalQuestionsJson`). Occurrences are
  concrete scheduled instances; events are an append-only log with an identical shape on both
  sides. (`data/model/DataModel.kt`)
- **DB version history.** Currently v17. Exported schemas live in
  `app/schemas/com.daybook.app.data.local.AppDatabase/` (`3.json` … `17.json`); migrations in
  `data/local/Migrations.kt` (`MIGRATION_2_3` … `MIGRATION_16_17`), registered in
  `di/DatabaseModule.kt` with `fallbackToDestructiveMigrationFrom(1)`. `MIGRATION_16_17` (the
  journal-habit round) adds `habits.journal_questions_json` and `habit_occurrences.qa_json`,
  purges every `food_med_tasks` row of type `JOURNAL` (with its occurrences/events), and drops the
  retired `journal_questions` table. Instrumented coverage in
  `app/src/androidTest/.../MigrationTest.kt`.
- **What "offline-first" means here.** The live store is on-device Room, which Android persists
  automatically — so not backing up is safe by default. JSON export and Firestore are *derived*
  mirrors, never the source of truth. Every network call is failure-inert; none blocks a launch
  or a screen render. The one deliberate exception is the sign-in gate.
