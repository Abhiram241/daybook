# Accent-updates round — on-device regression watch-list

No phone was attached during implementation. This is the manual test script for the next
on-device pass. Executes `ACCENT_UPDATES_PLAN.md` Phases 1–7.

Build: versionCode **14** (bumped from 13), versionName **"0.5.6"** (bumped from "0.5.5"), Room DB
**v18** (`18.json`, 31 columns on `app_settings`). APKs at repo root:
`Daybook-v0.5.6-accent-updates-release.apk` (7,178,805 B, real-keystore-signed, SHA-1
`39e62d0f...` verified via `apksigner --print-certs`) and
`Daybook-v0.5.6-accent-updates-debug.apk` (23,450,898 B).

---

> ## This APK installs IN-PLACE over the existing build 13 install
>
> Unlike the "customization" round (which froze versionCode and forced an uninstall), this round
> bumped versionCode 13→14, and the schema change is purely additive (`MIGRATION_17_18`). A normal
> sideloaded **update install** (no uninstall needed) should work and should preserve every
> existing device-local setting, including the single accent colour the user already had — see
> item 2 below for exactly what that migration does to it.

---

## 1. Migration (DB v17 → v18) — Phase 1

- [ ] **Fresh install** at v18: app opens, no crash; onboarding shows lavender accent + Literata
  font already selected if you check Settings → Appearance before changing anything.
- [ ] **Upgrade from a v0.5.5 (build 13, DB v17) install** with an existing accent colour picked
  (e.g. Coral): after the upgrade, Settings → Appearance shows **all three** pickers (App/Habits/
  Intake) with **Coral** selected — not reset to lavender. No data loss, no crash.
- [ ] androidTest `MigrationTest`: `migrate17To18_addsHabitsAndIntakeAccentColumns`,
  `migrate17To18_copiesExistingAccentIntoBothNewColumns`, `migrateAll_3To18`,
  `fullOpenAtLatestVersion` — run on a device/emulator when available (compile-only verified here).
- [ ] `17.json` is byte-unchanged; `18.json` is `"version": 18`; the diff vs `17.json` is **only**
  the 2 additive `app_settings` columns (`habits_accent_color`, `intake_accent_color`, both
  `TEXT NOT NULL DEFAULT 'LAVENDER'`).

## 2. Fresh-install defaults — Phase 1 / SD-4 / D2

- [ ] Brand-new install (fresh data, not an upgrade): Settings → Appearance shows **App accent**,
  **Habits accent**, and **Intake accent** all on **Lavender** (2nd swatch), **Font** = **Literata**
  (checked), **Reduce motion** = **off**. Matches the reference screenshot exactly.
- [ ] This is a Kotlin-constructor default (`AppSettings()` inserted by `ensureRow()`), not just a
  SQL `DEFAULT` literal — confirm by checking a genuinely fresh install, not an upgraded one.

## 3. Three independent accent axes — Phases 2 & 4

- [ ] Settings → Appearance now shows **three** separate swatch rows: "App accent", "Habits
  accent", "Intake accent" — each independently tappable, each persists across app restart.
- [ ] Change **App accent** only: nav bar, Today screen buttons/switches, Settings screens, Lock
  screen, Sign-in screen all retint. Habits and Intake screens/cards are **unaffected**.
- [ ] Change **Habits accent** only: the Habits tab's own buttons (FAB, chips, "Start" on an
  Ongoing card), the Add/Edit Habit forms, the Detail screen for a habit, and the Habit Journal
  chat/edit screens all retint. Today's own chrome and the Intake tab are unaffected.
- [ ] Change **Intake accent** only: the Intake tab, Add/Edit Intake forms, a food/med Detail
  screen, the Journal (food/med) screen, and the Respond screen for an intake item (`isHabit=false`)
  all retint. Habits and Today's own chrome are unaffected.
- [ ] The shared `respond/{occId}?isHabit={isHabit}` route picks the right accent per the
  `isHabit` nav arg — verify both a habit-side and an intake-side Respond screen (if a habit-side
  call site exists in this build; it may currently be unreached code, still worth a visual check
  if reachable via a notification action).

## 4. Cards follow section accent, pastel identity unchanged — Phase 3 / D1

- [ ] Habits list: each card's **icon glyph**, **streak flame**, **"B"/"~" mini-badges**, and
  **"Next: …" text** match the current Habits accent colour. Each card's **background pastel**
  (Lavender/Peach/Mint/Butter/SlateBlue/Rose, auto-assigned by position or a chosen `colorTag`
  override) is **unchanged** — still varies per item, not recoloured to the accent.
- [ ] Intake list: same check — icon/badge/next-time text follow the Intake accent; card
  backgrounds keep their own pastel identity.
- [ ] Neutral-tint cards (Settings groups, empty-state cards) are **visually unchanged** by this
  round — they were never wired to a section accent.

## 4b. Home "Your progress" stat cards — Phase 3b / D4

- [ ] Today screen's two stat cards: "Habits" card's icon + streak-flame colour follow the
  **Habits** accent; "Intake" card's icon + streak-flame colour follow the **Intake** accent —
  even while Home's own chrome (greeting, nav, other buttons) stays on the **App** accent.
- [ ] Both cards' pastel backgrounds stay fixed (Habits = Mint, Intake = Peach) regardless of any
  accent choice — only the icon/flame recolour.
- [ ] Home's per-item reminders list (the mixed habit+intake feed lower on the page) is
  **unaffected** by this round — it was never in scope (only the two named stat cards were).

## 5. Ongoing (STREAK) habit-card alignment fix — Phase 5 / D5

- [ ] Create an Ongoing habit, do not start it: the "Start" row sits **flush** with where the
  "running" row sits once started — same left edge, same vertical position, no visible card-height
  jump between the two states.
- [ ] "Start" text keeps its **bolder** call-to-action style (still visually distinct from the
  plainer "N days" running-state text) — it was NOT flattened to match.
- [ ] Tap "Start": streak begins immediately at today, card switches to the running state with no
  layout jump beyond the expected content change.
- [ ] "Mark as broken" (only visible once running) and the History/Clock icon button both still
  vertically center correctly against the whole card, not just the inner flame row.
- [ ] 3-dot menu → "Choose start date" (only offered before a streak starts) still opens the
  backdating date picker and works identically to before.
- [ ] No functional change: `onStartStreakAt` callback wiring, `OngoingStreak.kt`, `HabitDao`, and
  the Detail screen's STREAK branch are byte-identical to before this round.

## 6. Firebase App Distribution (in-app updates) — Phase 6

**One-time setup required before this can be tested at all** (see the how-to below) — none of
this fires without it:

- [ ] Firebase console → App Distribution → "Get started" has been clicked once for
  `daybook-v2-1f578`.
- [ ] A tester group (e.g. `testers`) exists with at least your own email in it.
- [ ] A build has been uploaded via `firebase appdistribution:distribute` (or
  `./gradlew assembleRelease appDistributionUploadRelease`) at least once, and you installed it
  via the emailed invite link (NOT by copying the APK some other way).

Once that's done:

- [ ] Install this round's release build via a fresh App Distribution upload/invite (not a plain
  sideload) — confirms the tester-registration path.
- [ ] Cut a **newer** build (higher versionCode), distribute it, then simply reopen the
  already-installed app: within a few seconds of `onResume`, App Distribution's own "Update
  available" dialog should appear with a Download/Install button.
- [ ] A **plain sideloaded** install (this round's APK handed over directly, not via App
  Distribution) shows **no crash and no prompt** from this code path — `updateIfNewReleaseAvailable()`
  fails silently (logged at `Log.w`, tag `InAppUpdateChecker`) for a non-registered install.
- [ ] `debug` builds never call this at all (`BuildConfig.DEBUG` gate in `MainActivity.onResume`).
- [ ] R8/`lintVitalRelease` stayed clean with the new dependency — confirmed at build time; no
  additional proguard rules were needed (the App Distribution AAR ships its own consumer rules).

## 7. Version bump — Phase 7

- [ ] This APK (versionCode 14) installs as an **in-place update** over the existing versionCode-13
  install with no uninstall step, no data loss, and every device-local setting (accent × 3, font,
  reduce motion, profile photo, habit check-in time, etc.) preserved exactly as it was.
- [ ] Settings → About footer reads "Daybook · Version 0.5.6 (14)".

---

## HOW TO USE + TEST IN-APP UPDATES

**One-time setup (Firebase console + terminal), do this once:**

1. Firebase console → project `daybook-v2-1f578` → **Release & Monitor → App Distribution** →
   click **"Get started"** (enables the App Distribution API — one click, one time; the Firebase
   MCP tooling has no remote way to do this step, it's console-only).
2. Same page → **Testers & Groups** → create a group named `testers` (matches the `groups =
   "testers"` already wired into `app/build.gradle.kts`'s `firebaseAppDistribution { }` block) →
   add your own email (and anyone else testing) to it.
3. The Firebase CLI in this environment is already logged in as `abhiramys.learn@gmail.com` — no
   service-account JSON is needed for manual/interactive uploads. (A service-account key with the
   "Firebase App Distribution Admin" role is only needed later if you want a fully unattended CI
   upload — optional, not required for anything above.)

**Every time you cut a build you want testers to get automatically**, either:

```bash
# Option A — plain CLI, no Gradle DSL involved:
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
  ./gradlew assembleRelease

firebase appdistribution:distribute \
  app/build/outputs/apk/release/app-release.apk \
  --app 1:1054765667595:android:4c9078aa9a2181d141fe0e \
  --groups "testers" \
  --release-notes "Whatever changed in this build"
```

```bash
# Option B — one Gradle command (uses the firebaseAppDistribution{} block already wired into the
# `release` build type, same groups/release-notes baked in):
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
  ./gradlew assembleRelease appDistributionUploadRelease
```

Either command uploads the APK and immediately emails everyone in the `testers` group an install
link.

**On a tester's phone, the very first time ever:** they must open the App Distribution email
invite and install through that link once — this is what registers their device as
"distribution-managed." A copy of the APK sent any other way (chat, USB, a direct file share)
will never self-update through this mechanism, because the SDK only recognizes itself as
distribution-managed for installs that originated from an App Distribution link.

**After that first App-Distribution install, forever:** every time you distribute a new build,
the app itself checks on resume (release builds only — debug builds never check) and shows App
Distribution's own built-in "Update available" dialog with a Download/Install button. No email,
no manual reinstall, no Play Store involved anywhere in this flow.

**To test the whole loop yourself right now:**

1. Install `Daybook-v0.5.6-accent-updates-release.apk` via the invite-link method above (not a
   plain sideload) — this is your "tester device" baseline.
2. Bump `versionCode` in `app/build.gradle.kts` by 1, rebuild, and distribute that as a second
   build (either option above).
3. Reopen the already-installed app on the tester device (just bring it to the foreground — no
   need to force-kill it first). The "Update available" dialog should appear within a few seconds.
4. Tap through it — it downloads and prompts a normal APK install, exactly like installing any
   updated APK.
