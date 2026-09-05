# Login Redesign + Risk-Audit Fix round — on-device regression watch-list

No phone was attached for this round (same constraint as the Journal-as-Habit round before it).
This is the manual test script for the next on-device pass.

Build: versionCode **13** (UNCHANGED), versionName **"0.5.5"** (UNCHANGED), Room DB **v17**
(`17.json` identityHash `ab87929e759dfe8996bb20df8447a99a`, UNCHANGED — no migration this round).
Executes `LOGIN_REDESIGN_RISK_FIX_PLAN.md` Phases 0–17: Phase 0 (crash stopgap), Phase 1 (icon
branding), Phases 2–3 (sign-in redesign + onboarding wizard), Phases 4–16 (all 41 findings from
`RISK_AUDIT_SYNC_NOTIFICATIONS_ERRORS.md`). Release APK:
`Daybook-v0.5.5-login-redesign-release.apk`, signed with the real release keystore (CN=Daybook,
SHA1 `39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c`). Debug APK (new this round, see §7):
`Daybook-v0.5.5-login-redesign-debug.apk`.

---

> ## 🚨 THIS APK WILL NOT INSTALL OVER THE CURRENT BUILD — THIRD CONSECUTIVE ROUND ON versionCode 13
>
> Same `versionCode` (13) as whatever is already on the phone from the Journal-as-Habit round
> (`Daybook-v0.5.5-journal-habit-release.apk`, also DB v17). Android's `PackageInstaller` refuses
> an install whose `versionCode` is ≤ the one already installed — it will not silently fail, it
> will just refuse the "update" outright.
>
> **Uninstall Daybook first, then install this APK.** Sign-in with Google re-pulls definitions and
> resident months from Firestore; every device-local `AppSettings` value (accent, font, quiet
> hours, streak mode, nav tabs, etc.) resets to default and must be re-set by hand after reinstall.
> This is unchanged from every prior round's caveat — repeating it here loudly because staying on
> versionCode 13 for a third round in a row makes it easy to forget.
>
> This round's DEBUG apk (`Daybook-v0.5.5-login-redesign-debug.apk`) carries the SAME versionCode
> and a DIFFERENT signing key (the debug keystore, not the release one) from every release APK —
> Android will refuse to install it over an existing release install for that reason too. If you
> want the debug build on a device that currently has any release APK installed, uninstall first.

---

## 0. Crash mitigation (Phase 0) — the reason this whole round started

No proven root-cause fix exists for the original "crashes after login" report — a rigorous static
audit ruled out every plausible cause with a citation, and no crash trace was ever captured (no
device could reproduce it, no emulator could reach a real Google-signed-in state). **This round
ships a stopgap, not a cure.** The watch-list item that matters most:

- [ ] **Install fresh, sign in, use the app completely normally for a full day** — habits, intake
      logging, notifications, backgrounding/foregrounding, a device restart if convenient. If it
      crashes:
  1. Retrieve `crash_log.txt` immediately, before doing anything else that might overwrite it (it's
     capped at 256KB, most-recent-entry-first, so it won't be lost by more use — but grab it early
     anyway). See **§8 "How to send us the next crash"** below for the exact retrieval steps.
  2. Attach it to the kickoff of whatever round investigates this next. This is the FIRST real
     signal on this bug since it was reported — do not let it get lost.
- [ ] Confirm Settings → About shows a **"Copy crash log"** link ONLY when `crash_log.txt` actually
      exists (it should be absent on a fresh install with no crash yet). Tapping it (once a crash
      has occurred) copies the file's contents to the clipboard.
- [ ] Force a synthetic crash if you want to test the mechanism directly (e.g. a debug build with a
      deliberately thrown exception on a button tap) — confirm `crash_log.txt` appears in
      `/data/data/com.daybook.app/files/` afterward and the Settings link appears.

## 1. Icon branding (Phase 1)

- [ ] Settings → scroll to the bottom "Daybook" / "Version X (Y)" footer: a 48dp greyscale app-icon
      mark now sits above the text, re-themed correctly under whatever `AccentColor` is active (it
      should look identical across all 5 accents — it's rendered from the monochrome/greyscale
      layer, not accent-tinted).
- [ ] Notification icons are **unchanged** — the small status-bar icons for habit/food/med
      reminders still use their existing single-color vector drawables, not the launcher mark. This
      was a deliberate exclusion (Android's notification small-icon slot is silhouette-only by OS
      convention).

## 2. Sign-in screen redesign (Phase 2)

- [ ] Sign out (or fresh-install) to reach the sign-in gate. The old static coral mockup is gone —
      confirm a themed, Canvas-drawn wave/blob hero occupies roughly the top 35-45% of the screen,
      with the app's monochrome icon mark centered near the bottom seam of the wave.
- [ ] **Change the accent color in Settings, sign out, and confirm the wave hero re-renders in the
      NEW accent** — repeat for all 5 `AccentColor` values (Mint, Lavender, Coral, Sky, Amber). The
      wave is pure Compose drawing, not a static asset, so it must track the accent live.
- [ ] The "Continue with Google" button is still pinned to the bottom via the same `StickySaveBar`
      shell — confirm it's still reachable one-handed on a tall phone, stays above the nav-bar
      inset, and rises correctly if a system dialog/IME ever appears over this screen.
- [ ] System back on this screen still does nothing (swallowed) — there is still no way past the
      gate except signing in.
- [ ] Below the wave hero, the content area reads "Welcome to Daybook" + a one-line blurb, inside a
      rounded-top "sheet" that visually rises over the wave (dark-theme surface color, not white).

## 3. Onboarding feature-tour wizard (Phase 3, D2)

**Both paths now get the full multi-step tour** — this is the D2 decision, a genuine behavior
change from the previous round (previously, an auto-derivable name path skipped straight to the
app with no tour at all).

- [ ] **Path A — Google account WITH a display name** (the common case): after signing in, the
      first thing shown is a **feature-tip screen** (NOT a name field — the name was silently
      derived from the Google profile). Step through via "Next"; the last step's button reads "Get
      started". Confirm the app only writes the onboarding-complete flag / navigates into the app
      ONCE — no flicker back to onboarding, no duplicate completion.
- [ ] **Path B — no derivable name** (a Google account with no display name set, or restored from
      a backup with no `userName`): the first step IS the name field (unchanged shape from before),
      followed by the SAME tour steps as Path A. "Get started" is disabled until a name is typed.
- [ ] **"Skip" is present on every tour step (not the name step) and ends the wizard immediately**,
      from any step — confirm it still writes the name (auto-derived or previously typed) exactly
      once, same as completing the whole tour.
- [ ] Step-dots indicator above the card advances correctly; tapping through the tour content
      (5 condensed feature tips) reads sensibly and doesn't feel visually busy with the small
      launcher-mark accent.
- [ ] **Double-tap "Get started" / "Skip" rapidly** on a slow connection — confirm onboarding
      completes exactly once (no double-write, no crash) — this is the pre-existing fire-once guard,
      now exercised at the end of a longer step sequence than before.

## 4. Sync bootstrap fix — S-1, the Critical finding (Phase 4)

This is the single highest-blast-radius fix in the whole round — every signed-in user hits
`bootstrap()` on every cold start.

- [ ] **Best test**: use a real account that has been signed in long enough for `evictStaleMonths`
      to have dropped at least one old month locally (or force it — sign in, let some months age out
      of the resident window, e.g. by not touching them for a while, or check `SyncStateStore`'s
      `hydratedMonths` some other way). Force a cold start (kill + relaunch, or reinstall + sign back
      in) and confirm sync reaches `SyncStatus.Idle`/normal operation — NOT a permanent conflict
      prompt. Before this fix, ANY evicted month permanently wedged that account's sync into
      CONFLICT on every future cold start.
- [ ] If sync IS wedged for any reason (paused, error), confirm the new **Today-screen banner**
      appears (a small warning-tinted card near the top, above the week strip) and tapping it
      navigates toward Settings/Account instead of the problem being invisible.

## 5. Spot-check: fixes most likely to have a visible on-device symptom

- [ ] **N-1 (quiet hours + re-nag)**: enable quiet hours in Settings → Notifications & alarms with a
      short window that includes "now" (or wait for it). Let a reminder go unanswered so it refires
      — confirm the refire is deferred to the end of the quiet window instead of buzzing through it,
      and confirm it does NOT re-alert (sound/vibrate) if the original notification is still sitting
      unactioned in the shade (it may still visually update).
- [ ] **N-5 (battery restriction row)**: Settings → Notifications & alarms → confirm a new "Battery"
      permission row appears alongside "Notifications" and "Exact alarms". On a device where
      Daybook is NOT battery-optimization-exempt, it should show as not-granted with a one-tap fix
      that opens the OS's "ignore battery optimizations" dialog for the app.
- [ ] **C-14 (oversized-import rejection)**: Settings → Data → Import, pick a file well over 10MB
      (any large file works for this test, doesn't need to be a real backup) — confirm it's rejected
      immediately with "That file is too large to be a Daybook backup" and the app does not attempt
      to read/parse it (no ANR, no OOM risk).
- [ ] **N-2 / N-7 (notification-reply correctness)**: reply to a Food/Med reminder from the
      notification inline-Reply — confirm the "Logged" ack appears only after a brief delay (it's
      now genuinely waiting for the write to finish, not appearing instantly then possibly being
      wrong). Then try replying to the SAME notification a second time (or an already-answered one)
      — confirm it shows "Already logged — open the app to edit" instead of silently overwriting the
      first answer.
- [ ] **N-6 (reply failure honesty)**: hard to force on a real device, but if a reply ever DOES fail
      (e.g. airplane-mode-adjacent timing edge case), confirm you see a "Couldn't save — tap to
      retry" notification rather than the notification just vanishing with the reply lost.
- [ ] **N-10 (test notification feedback)**: Settings → Notifications & alarms → Diagnostics →
      "Send test notification" — with notifications fully enabled, confirm the caption below the
      button reads "Test notification sent" and the notification appears. With the app's
      notification permission OFF (revoke it in system settings first), confirm the caption instead
      shows the actual block reason instead of nothing happening.

## 6. Regression — everything NOT deliberately changed

- [ ] Habit tracking, streaks, Food/Med reminders, journal-as-habit chat flow, Ongoing-habit cards —
      byte-identical in behavior to the Journal-as-Habit round's build; this round touched sync
      internals, notification plumbing, and the login/onboarding UI, not habit-tracking logic
      itself (the one exception is the journal/backfill save-result plumbing in Phase 9 — see below).
- [ ] **Phase 9 (C-4) journal/backfill save honesty**: back-date-log a habit journal entry or a
      Food/Med intake entry into a month that ISN'T currently loaded while offline (or otherwise
      trigger a `canBackfill` rejection, e.g. picking a date before the habit/task was created) —
      confirm you see an actual rejection message ("That month isn't loaded yet — connect and
      retry." or similar) instead of a false "Saved"/"✓ Entry saved" over data that was silently
      dropped.
- [ ] Account → Delete account (with "also erase local data" checked): confirm it still wipes
      everything the old path did, PLUS now correctly cancels reminders and resets sync state (the
      Phase 11/S-5 fix folded this into the same shared wipe path sign-out already used, closing a
      gap where account-deletion's wipe was weaker than sign-out's).
- [ ] Sign out, sign in as a SECOND account on the same device: confirm the first account's name/
      photo do NOT bleed into the second account's onboarding (Phase 5/S-4 — pre-existing from the
      last round, re-confirm it still holds after this round's changes near the same code).
- [ ] Export → Import a backup you just exported: still round-trips correctly (Phase 13/C-14 only
      changed *rejection* behavior for oversized files, not the normal-size read/write path).

---

## 7. Debug APK (new this round, not part of the plan document itself)

In addition to the signed release APK, this round also produces
**`Daybook-v0.5.5-login-redesign-debug.apk`** — the standard AGP `debug` build type (no explicit
`debug { }` block in `app/build.gradle.kts`, so it uses AGP's defaults). Both APKs were built and
verified directly, not just assumed correct:

- `isDebuggable = true` — **confirmed**: `aapt dump badging Daybook-v0.5.5-login-redesign-debug.apk`
  reports `application-debuggable`; the release APK does NOT report that line.
- `isMinifyEnabled = false` for debug — confirmed by the absence of any `minifyEnabled`/
  `shrinkResources` override in a `debug { }` block (only `release { }` sets `isMinifyEnabled =
  true`, and only the release build went through `minifyReleaseWithR8`/`shrinkReleaseRes` in the
  build log).
- The release APK is signed with the **real release keystore** — **confirmed**:
  `apksigner verify --print-certs Daybook-v0.5.5-login-redesign-release.apk` reports SHA-1
  `39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c`, matching `RELEASE_SIGNING.md` exactly. The debug APK
  is signed with the auto-generated Android debug keystore instead — it cannot be installed over a
  release-signed Daybook install, and vice versa, without an uninstall first (see the banner at the
  top of this document).
- Same versionCode (13) and versionName ("0.5.5") as the release APK — **confirmed** via
  `aapt dump badging` on both files — intentionally unchanged.

Use this build for on-device debugging where `adb logcat` needs to show full (non-obfuscated,
non-minified) stack traces, or where a debugger needs to attach.

## 8. How to send us the next crash

Phase 0 could not produce a proven root-cause fix for the original "crashes after login" report —
only the stopgap in §0 above. **If it happens again, here is exactly how to capture it**, from
easiest to most thorough:

### Easiest path — no computer needed
1. Let the crash happen (or find out it already did — the app doesn't need to be reopened first).
2. Open Daybook → **Settings → scroll to the bottom "About Daybook" footer**.
3. If a crash was captured, you'll see a **"Copy crash log"** link right below the version number.
   Tap it — this copies the entire captured crash trace to your clipboard.
4. Paste it into an email, a message, or wherever you're reporting the bug. That's it — no adb, no
   computer, no developer tools needed.
5. If the "Copy crash log" link is NOT there, the crash may have happened before Phase 0a's handler
   was installed (an old build), or the app died in a way even the handler couldn't catch (very
   rare) — fall back to the adb path below.

### Full adb path — for when the app dies too fast to even show the link, or you want to catch it live
This requires a computer with `adb` (Android Debug Bridge, part of the Android SDK Platform-Tools)
and a USB cable.

1. **Enable Developer Options** on the phone: Settings → About phone → tap **Build number** 7
   times in a row (you'll see a countdown toast: "You are now N steps away from being a
   developer...").
2. **Enable USB debugging**: Settings → System → Developer options → toggle **USB debugging** on.
3. **Connect the phone to the computer via USB.** A prompt appears on the phone asking to allow USB
   debugging from this computer — tap **Allow** (check "Always allow from this computer" if you'll
   do this again).
4. **Confirm the connection**: run `adb devices` — the phone should show up as `device` (not
   `unauthorized`; if it says unauthorized, check the phone screen for the allow prompt again).
5. **Capture the crash live** (do this BEFORE reproducing the crash):
   ```
   adb logcat -b crash
   ```
   or, if that buffer isn't available on the device, the broader:
   ```
   adb logcat *:E
   ```
   Leave this running in a terminal.
6. **Reproduce the crash** on the phone (use the app normally until it happens again).
7. **Copy the terminal output** the moment the crash appears (or a few seconds after, to catch any
   trailing log lines) — Ctrl+C to stop `logcat`, then copy everything from the crash onward.
8. **Attach that output** to the bug report / next round's kickoff.

**One-shot alternative** — if a crash has ALREADY happened and you don't need to catch it live,
you can pull the same file Settings' "Copy crash log" button reads, directly:
```
adb pull /data/data/com.daybook.app/files/crash_log.txt .
```
(Requires USB debugging enabled as in steps 1-4 above, and — on a non-rooted device — that the app
is debuggable OR that you're pulling via a debug build of the app, since a release build's app-
private storage isn't otherwise readable over adb without root. The Settings in-app "Copy crash
log" button works regardless of build type or root status, which is why it's the recommended
easiest path above.)
