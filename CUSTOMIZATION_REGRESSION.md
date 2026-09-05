# Customization / Personalization round — on-device regression watch-list

No phone was attached for this round. This is the manual test script for the next on-device pass.

Build: versionCode **13** (UNCHANGED), versionName **"0.5.5"** (UNCHANGED), Room DB **v16**
(`16.json` identityHash `bc72ee1c65ed5796db11ce3d4a7b2453`). Executes `CUSTOMIZATION_BUILD_PLAN.md`
phases P1–P10 — all 8 items of `CUSTOMIZATION_OPTIONS.md` §4.

---

> ## 🚨 THIS APK WILL NOT INSTALL OVER THE CURRENT BUILD
>
> The release APK has the **same `versionCode` (13)** as the build already on the phone. Android's
> package manager rejects an install whose `versionCode` is ≤ the installed one (a release APK is
> not debuggable, so `-d` downgrade does not apply).
>
> **Uninstall Daybook first, then install the new APK.**
>
> Because auth is Google-only and sync is cloud-backed, uninstall → reinstall → **Sign in with
> Google** re-pulls all definitions and resident months from Firestore — habits, intake reminders,
> journal questions and history all come back. **But every device-local `AppSettings` value resets
> to its default**, because none of them are synced or in the backup:
>
> - **new this round:** week-start, 24h clock, default calendar view, greeting style, time-of-day
>   word, hero-line phrasing, hide-resolved default, reduce motion, quiet hours (enable + start +
>   end), streak mode, rest days, show-streak-flames, default landing tab, visible tabs, remembered
>   list sort / show-archived / default snooze;
> - **pre-existing device-local:** accent colour, font, profile photo path, habit check-in time.
>
> The first launch after reinstall also runs the sign-in gate + a full sync bootstrap.
>
> An in-place update would be a **separate decision to bump `versionCode`**, explicitly out of
> scope here.

---

## 0. Migration (DB v15 → v16) — P1

- [ ] **Fresh install** at v16: app opens, no crash; Today / Habits / Intake / Settings render.
- [ ] **Upgrade from a v0.5.5 (build 13, DB v15) install** with existing habits + intake + history:
  - no data loss, no crash on first launch;
  - Individual + Batch reminders still armed after the upgrade;
  - every new `app_settings` column reads its default (MONDAY / 12h / week view / WARM greeting /
    time-word ON / "n left today" / streaks shown / STRICT / no rest days / all 3 tabs / …) — i.e.
    **no visible behaviour change until the user opens the new Settings screens**;
  - `habits.prompt_message`, `habits.motivation`, `food_med_tasks.motivation` read `NULL` on every
    existing row.
- [ ] androidTest `MigrationTest`: `migrate15To16_addsAllCustomizationColumns`, `migrateAll_3To16`,
  `fullOpenAtLatestVersion` — run on a device/emulator when available (compile-only verified here).
- [ ] `15.json` is byte-unchanged; `16.json` is `"version": 16`, identityHash
  `bc72ee1c65ed5796db11ce3d4a7b2453`; the diff vs `15.json` is **only** the 23 additive columns
  (20 on `app_settings` all `NOT NULL DEFAULT`, 3 nullable no-default on `habits` / `food_med_tasks`).

## rec 1 — Week start + 24h clock + default calendar view (P2)

- [ ] Settings → **Today & calendar** → *Calendar*: "Week starts on" = Sun / Mon / Sat.
- [ ] Change week-start → the **week strip** and the **month grid** re-lay-out (weekday initials
  rotate, lead-in blanks shift); the selected column stays put; today marker correct.
- [ ] **`StreakCalculator` is unaffected** by week-start (it buckets by calendar day) — the flame
  counts on Today + Detail do **not** change when you flip week-start. *(Confirmed in code; SD-5.)*
- [ ] Toggle **24-hour time** ON → every "Next: …" line, reminder-card time, the check-in time row,
  the quiet-hours rows, and the M3 **TimePicker** dial switch to 24h; storage is still `"HH:mm"`.
- [ ] Default calendar view = **Month** → Today opens with the month grid expanded; collapsing it
  in-session sticks (and survives a rotation); it re-expands on the next cold start.
- [ ] Pure tests: `WeekStartTest`, `DateTimeUtilsTest` (week-start + `formatTime` clock flag).

## rec 2 — Greeting tone + time-of-day word + hero phrasing (P6)

- [ ] Settings → Today & calendar → *Greeting*:
  - **Warm** (default) → the rotating pool ("Welcome back", "Let's go", "$tod, {name}", …).
  - **Plain** → one fixed line: "Hi, {name}" (or "Hi there" with no name), no rotation.
  - **Minimal** → **no greeting line** at all; only the date sub-row shows.
- [ ] **Show time-of-day word** OFF → "Good morning / afternoon / evening / night" never appears
  (the one template that used it falls back to "Hi there" / "Hi, {name}"). Row disabled under Minimal.
- [ ] **Hero line**: "13 left today" / "13 to go" / "13 tasks" (singular "1 task") / **Hidden**
  (BigHeadline not rendered, header spacing collapses). "All done" when nothing is pending.
- [ ] The greeting is still **computed once per app-open / boundary tick**, not per scroll frame
  (no flicker while scrolling Today).
- [ ] Pure test: `GreetingRenderTest`.

## rec 3 — Remembered list sort / show-archived + default snooze (P7)

- [ ] Habits: set Sort = Name + Show archived ON → **kill and relaunch** the app → the Habits
  filter sheet still shows Name + archived ON. Same for Intake (`intake_sort` / `intake_show_archived`).
- [ ] Type facet on both lists is **NOT** persisted (resets each session — by design).
- [ ] Today "hide resolved" default: Settings → Today & calendar → *Reminders* → "Hide resolved
  reminders by default" ON → relaunch → Today starts with completed / skipped / logged rows hidden;
  toggling it in the filter sheet still works for the session and writes the new default.
- [ ] Settings → Notifications & alarms → *Snooze* → "Snooze interval" stepper writes
  `default_snooze_minutes`; the **batch check-in** re-arms at the new interval.
- [ ] A **NEW** habit / intake form opens at the default snooze; an **EDIT** form keeps the item's
  own saved value; a user edit in a new form is never clobbered by the seed.
- [ ] Pure tests: `SortComparatorTest` (enum round-trip), `DefaultSnoozeSeedTest`.

## rec 4 — Reduce motion + accessibility (P3)

- [ ] Settings → Appearance → *Accessibility* → "Reduce motion" ON:
  - Today reminder cards: no placement spring, instant fade in/out;
  - inline-reply expand: plain fade, no spring;
  - week ↔ month calendar swap: instant (no size morph);
  - segmented-control pill: jumps, no slide;
  - `SoftCard` press-scale: no bounce (**the `graphicsLayer` itself is untouched** — the v0.5.1 §9
    yellow-blob fix still holds);
  - `PastelProgressBar`: snaps to value;
  - screen-to-screen nav: plain cross-fade, no slide/scale;
  - AdvancedSection / UndoSnack / list items: plain fade.
- [ ] System **Settings → Accessibility → Remove animations** ON (with the app pref OFF) triggers
  the same behaviour (`ANIMATOR_DURATION_SCALE == 0` is OR-ed in).
- [ ] TalkBack: accent swatches announce their colour name; the flame pill reads "N day streak";
  week-strip cells read "<full date>, today / selected / upcoming"; reminder-card icon reads the
  reminder title; each "Your progress" card reads "<title> N percent complete[, N day streak]".
- [ ] DO-NOT-TOUCH: pager drag/fling still physical; `goToPage` still an instant `scrollToPage`;
  `beyondViewportPageCount` unchanged; the bottom-nav active-dot animation unchanged.
- [ ] Pure test: `ReduceMotionTest` (`effectiveReduceMotion`).

## rec 5 — Quiet hours (P4)

- [ ] Settings → Notifications & alarms → *Quiet hours*: enable + Start + End time rows (rows greyed
  and inert when the switch is off; use the app clock format).
- [ ] Window **22:00–07:00** (wraps midnight):
  - a reminder due at **02:00** fires at **07:00** — **not lost, not at 02:00**;
  - snooze pressed at **23:30** lands the re-nag at **07:00** (next day);
  - a reminder due at **21:59** is untouched;
  - a reminder due exactly at **07:00** is untouched (exclusive end).
- [ ] Non-wrap window **13:00–14:00**: a 13:30-due reminder fires at 14:00; 12:00 and 14:00 untouched.
- [ ] An already-**overdue** (catch-up) reminder inside the window → deferred to the window end
  (catch-up honoured, just delayed to a civil hour).
- [ ] The **batch check-in** alarm respects quiet hours the same way.
- [ ] **Disable** quiet hours → immediate firing restored; with it disabled the arm/snooze/batch
  paths are byte-for-byte the pre-round behaviour (identity no-op).
- [ ] Notification **channel IDs unchanged** (`habits_v2` / `food_med_v2`) — no new channel.
- [ ] Pure test: `QuietHoursTest` (12 cases incl. wrap-midnight, overdue, degenerate window).

## rec 6 — Streak mode / rest days / hide streaks (P5)

- [ ] Settings → Today & calendar → *Streak display*:
  - **Strict** (default): a day counts only when every occurrence reached its done state — this is
    byte-identical to the current app (regression-pinned by `StreakCalculatorTest`).
  - **Lenient**: a day where every occurrence was done **or deliberately SKIPPED** counts; a
    still-pending / missed past day still breaks the run. Verify the flame differs Strict vs Lenient
    on a day with one skipped item.
- [ ] **Rest days**: pick e.g. Sunday. A Sunday with no occurrences neither breaks nor extends the
  run (the backward walk steps over it); a Sunday that WAS fully completed still counts +1.
- [ ] **Show streak flames** OFF → the flame pill disappears from Today's "Your progress" cards AND
  the "Current streak" / "Best" figures disappear from Detail → Stats. The streak is still
  **computed** (cheap); only its rendering is gated.
- [ ] An **Ongoing (STREAK) habit's** day-count on Detail is **NOT** hidden by "show streak flames"
  — that is the entire point of the type.
- [ ] Pure tests: `StreakCalculatorTest` (Lenient / rest-day / empty-rest-days regression).

## rec 7 — Default landing tab + hide tabs (P8) — HIGHEST RISK

- [ ] Settings → **Navigation**: "Default tab" (radio over the visible tabs), "Show tab: Habits",
  "Show tab: Intake"; **Today** is a locked row ("Always shown").
- [ ] Set default tab = **Intake** → cold start opens on Intake.
- [ ] Turn **Show tab: Habits** OFF → the bottom nav has **2 items**; the pager has **2 pages**;
  swipe works; system **back returns to Today** (index 0 is always Today); rotate on each tab.
- [ ] Turn it back ON → 3 tabs / 3 pages restored, in canonical order (Today, Habits, Intake).
- [ ] A notification **deep-link** to a habit / intake / journal still opens the right screen even
  with that tab hidden (Detail / Respond / Journal are stack routes, not tabs).
- [ ] With only Today visible: nav bar shows the one item, swipe is a no-op, back exits — acceptable.
- [ ] Pure test: `NavConfigTest` (visible-routes / landing-index / toggle, Today forced first).

## rec 8 — Per-habit notification text + "why" note + sync (P9) — ISOLATED SYNC PHASE

- [ ] Habit form → Advanced → **"Reminder text"** (hidden for Ongoing) + **"Why this matters"**
  (all types). Intake form → Advanced → **"Why this matters"** (beside the existing prompt).
- [ ] Set a habit's "Reminder text" → its notification body shows that text; unset → the default
  "Time to complete this habit". The **batch check-in** notification keeps its combined copy.
- [ ] The "why" note shows on the item's **Detail** header as a quoted accent line (habit + intake).
- [ ] **Zero-churn check (the whole point):** a user who sets **no** per-habit / per-intake text,
  updates to this build, makes an **unrelated** habit edit → **no parent-doc re-push**
  (`definitionsHash` unchanged — check Firestore write count / logs).
  *(Guarded by `PerHabitTextHashTest`; `@EncodeDefault(NEVER)` on all 3 new wire fields; the
  pre-existing `IntakeReminderDef.promptMessage` is deliberately left as-is.)*
- [ ] Export → the JSON carries `promptMessage` / `motivation` **only** when set; a plain item's
  `HabitDef` / `IntakeReminderDef` has neither key.
- [ ] Export → uninstall → reinstall → import → the per-habit text round-trips.
- [ ] An **older** build importing a v0.5.5 file ignores the unknown `promptMessage` / `motivation`
  keys without crashing (`ignoreUnknownKeys`).

## Settings IA (P10)

- [ ] Hub row order: Account & sync · Appearance · **Today & calendar** · **Navigation** ·
  Notifications & alarms · Journal questions · App lock · Export & import.
- [ ] Every new control uses `DaybookColors` + `LocalAccent` only (dark-only), reuses
  `SettingsGroup` / `SegmentedControl` / `DayOfWeekSelector` / `TimePickerDialog` / `SnoozeStepper`.
- [ ] There is deliberately **no Settings row** for persisted list sort / show-archived — the filter
  sheet just remembers.

## 4-gate (all four green on the final `clean` build)

```
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew \
  clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

- `testDebugUnitTest` — see the hand-off report for the exact count; `PerHabitTextHashTest`,
  `QuietHoursTest`, `StreakCalculatorTest` (extended), `DateTimeUtilsTest` (extended),
  `WeekStartTest`, `ReduceMotionTest`, `GreetingRenderTest`, `NavConfigTest`,
  `DefaultSnoozeSeedTest`, `SortComparatorTest` (extended) all pass.
- `assembleDebug` — pass.
- `assembleRelease` — R8 + `lintVitalRelease` clean, signed `CN=Daybook` SHA1 `39e62d0f…`.
- `compileDebugAndroidTestKotlin` — pass (covers the `MigrationTest` 15→16 additions).

## Known / accepted

- All rec 1–7 preferences are **device-local**, not synced and not in the backup — a reinstall
  resets them (called out in the box at the top). Only rec 8 touches sync.
- Reorder of the bottom-nav tabs is **not** implemented this round (SD-2); the only reordering is
  "Today forced to the front". `nav_tabs` is stored as an ordered CSV so reorder can be added later
  with no migration.
- Quiet hours **defers** a due alarm to the window end; it never suppresses one (SD-3).
