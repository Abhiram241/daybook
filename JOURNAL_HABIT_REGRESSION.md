# Journal-as-Habit + Button Fix + Ongoing-habit UI round — on-device regression watch-list

No phone was attached for this round. This is the manual test script for the next on-device pass.

Build: versionCode **13** (UNCHANGED), versionName **"0.5.5"** (UNCHANGED), Room DB **v17**
(`17.json` identityHash `ab87929e759dfe8996bb20df8447a99a`). Executes `JOURNAL_HABIT_PLAN.md`
Phases 0–9 — Task A (button fix), Task B (Journal as a 4th `HabitType`), Task C (Ongoing-habit
card redesign). Release APK: `Daybook-v0.5.5-journal-habit-release.apk`, signed with the real
release keystore (CN=Daybook, SHA1 `39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c`).

---

> ## 🚨 THIS APK WILL NOT INSTALL OVER THE CURRENT BUILD
>
> Same `versionCode` (13) as whatever is already on the phone from the Customization round
> (`Daybook-v0.5.5-customization-release.apk`, DB v16). Android rejects an install whose
> `versionCode` is ≤ the installed one.
>
> **Uninstall Daybook first, then install this APK.** Sign-in with Google re-pulls definitions and
> resident months from Firestore; every device-local `AppSettings` value (accent, font, quiet
> hours, streak mode, etc. — see `CUSTOMIZATION_REGRESSION.md`'s list) resets to default and must
> be re-set by hand after reinstall. This is unchanged from the prior round's caveat.

---

## 0. Migration (DB v16 → v17)

- [ ] **Fresh install** at v17: app opens, no crash; Today / Habits / Intake / Settings render.
- [ ] **Upgrade from a v0.5.5 (build 13, DB v16) install** with an existing FoodMed **Journal**
      reminder that has history:
  - after the upgrade, that reminder and ALL of its history are **gone** (the user's explicit
    "fresh start, don't migrate" decision) — confirm no crash, no orphaned row anywhere in Home /
    Detail / Settings;
  - every FOOD/MED/CUSTOM reminder and its history is **untouched** (same count, same answers);
  - Settings → the old **Journal questions** row is gone entirely (the screen and its nav entry
    were deleted).
- [ ] androidTest `MigrationTest`: `migrate16To17_addsJournalColumns`,
      `migrate16To17_newHabitColumnDefaultsToEmptyString`,
      `migrate16To17_purgesOnlyJournalTaskAndItsChildren_childrenBeforeParent`,
      `migrate16To17_dropsJournalQuestionsTable` — run on a device/emulator when available
      (compile-only verified here via `compileDebugAndroidTestKotlin`).
- [ ] `16.json` is byte-unchanged; `17.json` is `"version": 17`, identityHash
      `ab87929e759dfe8996bb20df8447a99a`; the diff vs `16.json` is the two additive columns
      (`habits.journal_questions_json` NOT NULL DEFAULT '', `habit_occurrences.qa_json` nullable)
      plus the dropped `journal_questions` table.

## 1. Task A — the cramped "field + Add button" rows (`FoodMedForm.kt`)

- [ ] Add/Edit Intake form → **Category** section → "New category" field + "Add" button: the
      button now has visible left/right margin around its label (was hugging the text with ~0dp
      margin, touching its own rounded border).
- [ ] Same form → **Advanced → Prompt message** → "New prompt" field + "Add" button: same fix.
- [ ] Every OTHER `GhostButton` in the app (Settings rows, Account, the old journal stepper's
      Back button, TimePicker's cancel) still looks identical — they already passed
      `fillMaxWidth()`, so the new inner padding is invisible on them.
- [ ] The Habit form's new **Questions** editor (below) does NOT reproduce this bug — its "Add
      question" control is a full-width `PrimaryButton` below the field, not a compact
      side-by-side row.

## 2. Task B — Journal as a 4th Habit type

### 2a. Creating a Journal habit
- [ ] Habits tab → **+** → Type row now shows **Individual / Batch / Ongoing / Journal** (4 chips).
- [ ] Tapping **Journal** on a brand-new (empty) form seeds one default question, "What's on your
      mind?" — reopening/switching away and back does NOT re-seed over edits already made.
- [ ] Journal habit shows the **Reminder times** picker (like Individual) — at least one time is
      required to save (Save stays disabled with 0 times, exactly like Individual).
- [ ] **Active days / Snooze** show (unlike Ongoing, which hides them).
- [ ] **Reminder text** (custom notification prompt) is HIDDEN for Journal (like Ongoing) — its
      notification body is the fixed "Tap to write today's entry".
- [ ] Advanced → **Questions**: add / edit (tap a row) / delete (only when >1 remains) / reorder
      (up/down arrows) all work on the in-memory list; Advanced auto-expands on Edit if the
      question list differs from the single seeded default.

### 2b. The chat flow (Phase 4)
- [ ] At the reminder time, the notification fires on the **existing habits notification channel**
      (no new channel), with **Skip + Snooze only** (no Complete action, no reply-inline).
- [ ] Tapping the notification (or the Home card's "write entry" icon on a pending slot) opens a
      **chat-style screen**: one question bubble (left, neutral), a compose box below.
- [ ] Typing an answer and tapping Send: the answer appears as a right-aligned, accent-coloured
      bubble; the NEXT question appears as a new left bubble immediately after. Send is disabled
      while the compose box is blank.
- [ ] Answering the LAST question auto-saves and pops back (no separate "Save" button tap) —
      confirm a brief "✓ Entry saved" line shows before the screen pops.
- [ ] **Backgrounding mid-chat and reopening** the same reminder resumes exactly where you left
      off — already-sent bubbles replay from the persisted draft, the next unanswered question is
      live. (Each send persists a draft snapshot to `qa_json` while `status` is still `PENDING`.)
- [ ] Skip / Snooze from the notification (or the Home card's ⋮ sheet) work exactly like an
      Individual habit's.

### 2c. The plain edit-form (Phase 5, B8)
- [ ] Tapping an **already-answered** Journal entry — from Detail → History, or from Today's
      resolved ("Logged") card — opens a **plain form**, NOT the chat: every question with its
      saved answer in an editable field, all visible at once, a normal "Save" button at the
      bottom.
- [ ] Editing an answer and saving updates the entry **in place**: `responded_at` and the History
      timestamp do NOT change, no duplicate History row is created.
- [ ] A **PENDING** (not yet answered) slot always opens the chat, never this form.

### 2d. Backfill (B7)
- [ ] Scroll the Habits/Today week strip to a **past day** with a missed Journal-habit slot
      (before today, on/after the habit's creation date, matching its active days): the Today card
      shows the "write entry" affordance; tapping it opens the **chat**, seeded from the habit's
      CURRENT question list.
- [ ] Re-opening an **already-backfilled** past slot opens the **edit-form**, not the chat again.

### 2e. Detail / Home rendering (Phase 6 — the highest-risk area)
- [ ] A Journal habit's Detail → **History** tab shows each answered day as stacked
      (question, answer) pairs — same visual treatment as a FoodMed Journal entry's History rows.
- [ ] Detail → **Stats** tab: Current streak / Best / Completion rate / This month all populate
      correctly for a Journal habit answered on consecutive days (do NOT show a stuck 0 — this was
      the single highest-risk regression in this round: a habit-side `LOGGED` occurrence must count
      the same as `COMPLETED`).
- [ ] Home's **Reminders filter** → "Journal" bucket includes BOTH FoodMed Journal reminders and
      Journal-type habits.
- [ ] **No Journal-type habit occurrence ever shows a plain Complete/Skip pair** — pending shows
      only the chat-opening "write entry" icon; resolved shows only the edit-affordance (tap to
      edit / a sheet "Edit" row), never an "Undo" toggle that would silently flip it to
      COMPLETED/SKIPPED.

### 2f. Sync / backup (Phase 7)
- [ ] Export a backup with a Journal habit that has answered days: the `.json` file contains
      `journalQuestions` on that habit's definition and `qaJson` on its answered `HabitLog` days;
      every OTHER habit's definition/day JSON is **byte-identical** to before this round (no stray
      empty-array/`null` keys appearing).
- [ ] **The core sync-safety check**: an existing user with **zero** Journal-type habits should see
      **NO** `definitionsHash` / month-`ContentHash` churn from installing this build — i.e. no
      spurious full re-push of their existing data. (`HabitJournalHashTest` pins this in code; a
      real-device confirmation — watching sync logs on an existing account before/after this
      install — is the belt-and-braces check, unverified on-device here.)
- [ ] Push/pull a Journal habit definition + an answered day through the cloud sync path once (a
      second device or account) to confirm `journalQuestionsJson`/`qaJson` round-trip correctly.
- [ ] Restore an **ancient (pre-this-round) backup file** containing `"type":"JOURNAL"` intake
      reminders: import completes without crashing, and those entries land as **CUSTOM** reminders
      (not JOURNAL — the retired type is remapped defensively on import per B3).

## 3. Task C — Ongoing ("Streak") habit card

- [ ] A **not-started** Ongoing habit's card: header row now shows a small clock ("History")
      icon next to the ⋮ menu button (new); tapping either opens the same Detail screen.
- [ ] Tapping **"Start"** now opens the themed date picker (pre-selected to **today**) instead of
      silently starting the count — confirming with today selected behaves exactly like the old
      one-tap "start now".
- [ ] Picking a **past date** in that picker (e.g. 3 days ago) and confirming: the card's day-count
      immediately reads **4** (inclusive: 3 days ago → today is 4 calendar days), not 1.
- [ ] The picker's calendar **greys out every date after today** — cannot select a future start
      (confirm this holds visually on a real device; Material3's `SelectableDates` behaviour is
      unit-tested but worth eyeballing on-device per the plan's own risk note).
- [ ] A **running** Ongoing habit's card: the day-count row now ends with a small danger-tinted
      "✕" (Mark as broken) icon, right-aligned — tapping it shows the same confirm dialog as
      before ("This clears the current run of N days. Your longest streak is kept.").
- [ ] The card's **⋮ menu** for a running Ongoing habit no longer contains "Mark as broken" — only
      Edit / Archive / Delete (same as every other habit type).
- [ ] Tapping the new on-card History icon or the Mark-as-broken icon does **not** also trigger the
      whole-card tap underneath (no double-navigation / no accidental Detail-then-dialog stacking)
      — confirm real touch-target behaviour on a device (nested-clickable hit-testing can differ
      subtly from a Compose UI test's synthetic taps).

## 4. Regression — everything NOT touched

- [ ] Individual / Batch habit scheduling, notifications, Detail, Home, backup — byte-identical to
      the Customization-round build (no code path for these types changed shape, only additive
      `HabitType.JOURNAL`/`isHabitJournal` branches were inserted alongside them).
- [ ] FoodMed Food/Med/Custom reminders — byte-identical, except the Type picker no longer offers
      "Journal" (intentional) and the two "Add" button rows are wider (intentional, Task A).
- [ ] The pre-existing FoodMed **Journal** stepper screen (`JournalScreen.kt`) is unchanged code —
      confirm it is genuinely unreachable now (no live UI path creates a new `TaskType.JOURNAL`
      task) other than a legacy already-PENDING deep link/notification from before this round's
      first launch, which should just work as before until it resolves.
