# Keyboard-covers-text-field bug — handover (UNRESOLVED as of build 41)

> **Update — build 42 (fix attempt #3, awaiting on-device confirmation).** Code analysis found a
> geometric cause, not a timing one: the `BringIntoViewRequester` was attached to the inner
> `BasicTextField`, whose bounds exclude the 14dp padding of the field box around it. Scrollable
> bring-into-view scrolls the *minimum* distance, so it stopped when the bare text line reached the
> keyboard's top edge. The box's bottom padding and the send button beside it stayed under the
> keyboard, no matter how often or how late it was re-issued. That explains the fixed-size,
> timing-independent shortfall. Fix: new `Modifier.bringIntoViewWhileImeOpens()` (`Forms.kt`)
> reveals the element's whole bounds + 16dp. It is applied to the whole `DaybookTextField` Column
> and to the whole Home `ReminderCard` while its reply row has focus. It re-fires on every frame of
> `WindowInsets.ime`. The build-40 note below that this read is "always zero" under `imePadding()`
> is incorrect: consumption only affects other inset-padding modifiers, not a direct
> `WindowInsets.ime.getBottom()` read (`WorkoutSessionScreen` relies on the same read).
> `adjustResize` + edge-to-edge is the combination Compose recommends, so the manifest was left
> unchanged.

**Status: NOT FIXED.** Two attempted fixes (build 40, build 41) have both shipped and the user
confirmed, after installing build 41, that the bug is still present. Do not repeat those two
approaches — read "What was already tried and ruled out" below before writing any code.

## Prompt to open a fresh session with

> Daybook's Today screen has a keyboard-covering bug: tap the reply icon on a reminder card (e.g.
> "Lunch"), tap into the "What did you have?" field, and the keyboard covers the bottom portion of
> that field instead of the whole card scrolling fully above the keyboard. Two prior fix attempts
> (documented in `KEYBOARD_COVER_BUG_HANDOVER.md` at repo root) did not fix it — read that file
> first, in full, before changing any code. In particular: don't just retry different
> `bringIntoView()` timing tweaks in `DaybookTextField` (`ui/components/Forms.kt`) — that's the
> mechanism already tried twice. The file's "Leading hypothesis" section names a more likely root
> cause (an `adjustResize` + edge-to-edge conflict) that hasn't been tested yet. Start by following
> the "Diagnostic protocol for the next session" section's steps in order, starting with confirming
> which build is actually installed on the test device.

## The bug, precisely

- Screen: Today (`ui/home/HomeScreen.kt`), a reminder card's inline reply row (any FOOD/MED intake
  card — "Lunch" in the repro video — via the paper-plane "Reply" icon, which opens a
  `DaybookTextField` placeholder-labelled e.g. "What did you have?").
- Repro: tap Reply to expand the row, tap into the text field. The keyboard rises. **Expected**
  (per the user's reference screenshot from earlier in this thread): the whole card scrolls up so
  the entire card — field, "Trigger flag" row, everything — sits fully above the keyboard.
  **Actual**: the field ends up with roughly its bottom third rendered *underneath* the keyboard's
  top edge (verified visually — see "How this was diagnosed" below).
- This is NOT specific to the Home reply field — `DaybookTextField` (`ui/components/Forms.kt`) is
  a shared component used by many forms across the app, so whatever the real cause is, it likely
  affects any of them where a field ends up need real scrolling to clear the keyboard.

## What was already tried and ruled out

All three of these shipped as real builds; do not re-attempt any of them as if they were untried.

### Build 39 (versionCode 39) — unrelated fixes, baseline before this bug thread
Two unrelated fixes shipped together, not relevant to this bug but noted for version-history
continuity: sleep-session date bucketing (`HealthConnectReader.kt`, now buckets by `endTime`'s
local date instead of `startTime`'s) and Today's "show completed" filter made session-only
(`HomeViewModel.kt`, `setShowResolved` no longer persists to `AppSettingsRepository`).

### Build 40 (versionCode 40) — DEAD CODE, never actually ran
Hypothesis at the time: the original single `delay(250)` before `bringIntoView()` was a guess that
could fire before the keyboard's show animation settled. Fix attempt: read
`WindowInsets.ime.getBottom(LocalDensity.current)` inside `DaybookTextField` and re-issue
`bringIntoView()` in a `LaunchedEffect(isFieldFocused, imeBottomPx)` every time that value changed.

**Why it did nothing:** `Modifier.imePadding()` is applied on the ancestor `LazyColumn`
(`HomeScreen.kt:151`, `Modifier.fillMaxSize().imePadding()`). `imePadding()` is documented Compose
behavior to *consume* the ime inset so descendants don't double-apply it. `DaybookTextField` is a
descendant of that `LazyColumn` (it's rendered inside a list item), so its own read of
`WindowInsets.ime` always saw an already-consumed (zero) value. The `LaunchedEffect` condition
(`imeBottomPx > 0`) never became true, so it never fired. The only thing actually running was the
pre-existing immediate (no-delay) `bringIntoView()` call in `onFocusEvent`, which still raced the
keyboard's own show animation exactly as before. This was caught by diagnosis, not by the user
re-testing — see below.

### Build 41 (versionCode 41) — retry loop, user confirmed STILL BROKEN
Fix attempt: stop reading `WindowInsets.ime` entirely (since it's unreachable from that point in
the tree, per above). Instead, on focus, call `bringIntoViewRequester.bringIntoView()` repeatedly —
`repeat(15) { bringIntoView(); delay(40) }`, i.e. up to ~600ms of retries — so each attempt
re-evaluates the field's position against the ancestor `LazyColumn`'s actual current viewport,
converging once the keyboard animation (and the card's own expand animation) settles, regardless of
how long that takes.

**Current state in the repo:** this is what's on `main` right now (commit `458ffbe`). The user
installed this build and reported the bug is still present. **The reason this didn't work has not
been diagnosed yet** — see "Leading hypothesis" and "What hasn't been ruled out" below for where to
look next; don't assume it's simply "not enough retries" without checking those first.

## How this was diagnosed (build 40's failure specifically)

There is no `ffmpeg`/`ffprobe` on this machine, but `mpv` is installed and can extract frames
headlessly:

```bash
mpv --vo=image --vo-image-outdir=<dir> --frames=1 --start=<seconds> <video.mp4>
```

This was used to pull frames at ~0.3s intervals from the user's screen recording, which confirmed:
the fixed header (greeting/date/hero) stayed correctly in place; the calendar strip and "Your
progress" section correctly scrolled off the top (that part is normal, expected list-scroll
behavior, not a bug); but the reply field's bottom edge — and the send button beside it — were
visibly rendered underneath the keyboard's top edge, cut off. That visual evidence is what led to
finding the `imePadding()`-consumption dead-code bug in build 40. **The same frame-extraction
technique should be used again on any new repro video** rather than guessing from the text
description alone — cropping the region right at the keyboard's top edge (e.g. with Python
Pillow's `Image.crop`) makes the cut-off obvious at full resolution.

## Leading hypothesis (not yet tested)

This app combines two things that are a known-conflicting pattern:

- `AndroidManifest.xml:62` — `android:windowSoftInputMode="adjustResize"` on `MainActivity`.
- `MainActivity.kt:148` — `WindowCompat.setDecorFitsSystemWindows(window, false)` (edge-to-edge).

`adjustResize` tells the OS to physically resize the Activity window's content area when the
keyboard shows — a mechanism designed for the *pre*-edge-to-edge / non-Compose-insets model.
`setDecorFitsSystemWindows(false)` (edge-to-edge) is the mechanism Compose's own
`WindowInsets`/`imePadding()` expects to be paired with instead, where the window does *not*
resize and insets are delivered as data for the app to react to itself. Running both at once is a
combination Android's own docs and several open Compose issues call out as producing inconsistent
behavior across OEMs/API levels — e.g. the window may partially resize AND deliver ime insets,
causing padding to be double-counted (or the reported ime inset value to not match the keyboard's
actual real on-screen height), which would explain a persistent, timing-independent
"under-by-a-fixed-amount" cover that no amount of retrying `bringIntoView()` can fix, because the
*target rect it's scrolling to* is being computed from the wrong number in the first place.

This has NOT been tested or confirmed — it's the most likely lead based on the symptom
(consistently short by roughly the same amount, unaffected by retry count/duration), not a
verified root cause.

## Diagnostic protocol for the next session (do these in order)

1. **Confirm which build is actually installed before writing any more code.** Firebase App
   Distribution does not auto-update a sideloaded install — the user has to open the distribution
   link/email and manually reinstall each time. It's possible builds 39/40/41 were shipped faster
   than they were actually installed between rounds. Settings → About & help footer shows
   `"Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"`
   (`ui/settings/SettingsScreen.kt:374`) — ask the user to screenshot that before trusting any new
   repro video. If it doesn't say `0.7.2 (41)` or later, the "still broken" report may be against
   stale code, not a real second failure of the build-41 fix.
2. **Get ground truth on the real ime inset**, independent of Compose's per-subtree consumption
   bookkeeping. E.g. temporarily add a raw `ViewCompat.setOnApplyWindowInsetsListener` (or a
   `WindowInsetsAnimation.Callback`) at the `MainActivity` root (not inside any already-`imePadding`'d
   subtree) and `Log.d` the ime bottom value on every callback during a repro. Compare that against
   what the keyboard visually occupies on screen (from a fresh frame-extracted screenshot) to see
   whether Compose's understanding of the keyboard height actually matches reality.
3. **Test removing the `adjustResize`/edge-to-edge conflict directly**: try
   `android:windowSoftInputMode="adjustPan"` or dropping the attribute (letting Compose's insets
   handling do all the work, which is the standard edge-to-edge pattern) and see if the bug
   changes character at all. This is a cheap, fast experiment before touching `DaybookTextField`
   again.
4. Only if the above rules out the insets-conflict theory, go back to `DaybookTextField` — but at
   that point, consider testing on a **minimal isolated screen** (no `LazyColumn`, no
   `AnimatedVisibility` card-expand racing the keyboard) to remove confounding variables, since the
   Home reply row has both an expand animation *and* a lazy-list ancestor stacked on top of the ime
   handling.

## Files touched this session (for context, not necessarily still correct)

- `app/src/main/java/com/daybook/app/ui/components/Forms.kt` — `DaybookTextField`'s bring-into-view
  logic (build 40 and 41's changes; build 41's retry-loop version is what's currently on `main`).
- `app/src/main/java/com/daybook/app/data/health/HealthConnectReader.kt` — unrelated, build 39.
- `app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt` — unrelated, build 39.
- `app/build.gradle.kts` — version bumps for all three builds.

## Version history through this bug thread

| Build | versionCode | versionName | What shipped |
|---|---|---|---|
| (prior session) | 38 | 0.7.1 | Daily Report round 2 — unrelated baseline |
| Fix round 1 | 39 | 0.7.2 | Sleep-date bucketing fix + Today filter session-only fix (unrelated to this bug) |
| Fix round 2 | 40 | 0.7.2 | Keyboard-cover attempt #1 — **dead code**, never ran (ime read consumed by ancestor) |
| Fix round 3 | 41 | 0.7.2 | Keyboard-cover attempt #2 — retry loop — **user confirmed still broken** |

Per the user's standing versioning convention ([[appforfood-versioning]] in Claude's memory):
bump `versionCode` on every build; only bump `versionName` for an actual new feature round, not a
bug-fix patch. `versionName` should stay `0.7.2` until whatever eventually fixes this (and any
other pending fixes) ships as a confirmed-working round.

## Delivery reminder

Release APKs for this project are routinely >30MB — `SendUserFile` will reject them. Use
Firebase App Distribution (`./gradlew appDistributionUploadRelease`, already wired to the
`testers` group in `app/build.gradle.kts`) as done for builds 39–41, or the SSH-over-Tailscale
flow in `SSH_ACCESS_SETUP.md` if a raw APK file needs to go to the user directly.
