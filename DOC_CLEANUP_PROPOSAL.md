# Doc cleanup proposal — Daybook repo root

Purpose: get the repo root down to the handful of documents a new maintainer actually needs,
without losing anything load-bearing. **Nothing here has been deleted.** The parent will act on
this table after the user approves.

Context you must know before deleting anything:

> **This directory is NOT a git repository.** There is no history, no `git restore`, no undo.
> A `rm` here is permanent. The very first thing the successor should do (see
> `DEVELOPER_HANDOVER.md` §2) is `git init` so this stops being true. Ideally run the deletions
> **after** the first commit, so they are recoverable.

Ground state the docs describe: versionCode 13 / versionName `0.5.5` / Room DB **v16**
(`app/schemas/com.daybook.app.data.local.AppDatabase/16.json`), package `com.daybook.app`,
latest release APK `Daybook-v0.5.5-customization-release.apk`.

---

## 1. Markdown files

| Filename | Verdict | Reason |
|---|---|---|
| `README.md` | **KEEP** | Root readme. Its "Next Steps / Design Plan Compliance" sections are stale (they predate almost the whole app) — worth a rewrite later, but keep the file. |
| `RELEASE_SIGNING.md` | **KEEP** | Current release-signing + keystore reference. Linked from `DEVELOPER_HANDOVER.md` §4. |
| `FEATURES.md` | **KEEP** | New (this task). Complete user- and capability-level feature inventory. |
| `DEVELOPER_HANDOVER.md` | **KEEP** | New (this task). Standalone onboarding manual for the successor. |
| `DOC_CLEANUP_PROPOSAL.md` | **KEEP** | New (this task). This file. |
| `CUSTOMIZATION_OPTIONS.md` | **KEEP** | Live personalization backlog / catalogue. Supersedes `PERSONALIZATION_IDEAS.md`. Header still says "DB v15" — minor staleness, content is current. |
| `CUSTOMIZATION_REGRESSION.md` | **KEEP** | Newest on-device regression watch-list (DB v16, `16.json` identityHash `bc72ee1c65ed5796db11ce3d4a7b2453`). The manual test script for the next device pass; also the clearest single writeup of the customization round's 8 features. |
| `architecture-plan.md` | **DELETE** | Aug-24 first-draft plan. Enduring parts (offline-first rationale, "Room is the live store, JSON is only backup", MVVM+Repository, notification-first, min SDK 26) are folded into `DEVELOPER_HANDOVER.md` §1 and §7. The rest is stale: `SNOOZED` status was never used, the monthly backup nudge was removed, "4 accent options" is now 5 + pastel card tints, swipe-gesture actions were replaced by a pager + inline buttons. |
| `design-plan.md` | **DELETE** | Aug-24 design plan. Enduring principles (dark-only near-black `#0B0D0F`, one-hand layout, fixed notification button grammar, one corner-radius / elevation-via-border system, accent used only for interactive/selected) are folded into `DEVELOPER_HANDOVER.md` §1 and `FEATURES.md`. Specifics are stale: accent names ("Signal Teal" etc.), FAB + swipe model, Inter/Manrope font choice, bottom-nav-not-pager. |
| `PERSONALIZATION_IDEAS.md` | **DELETE** | Flat wishlist. `CUSTOMIZATION_OPTIONS.md` §1 explicitly replaces it, item by item. |
| `CODE_REVIEW.md` | **DELETE** | Pre-rewrite review. Explicitly superseded by `CODEBASE_REVIEW.md`, which is itself now stale. |
| `CODEBASE_REVIEW.md` | **DELETE** | Aug-29 static audit (REV-01…REV-43). Its findings were executed in v0.5.2 / v0.5.3 (added `MIGRATION_1_2` fallback, strip event ids on import, single typed accent, etc.). Historical. |
| `SCALABILITY_SYNC_AUDIT.md` | **DELETE** | S1–S17 / A1–A11 sync + scalability audit. Executed via `MASTER_FIX_PLAN.md`. Historical. The surviving rules (Room is source of truth, `@EncodeDefault(NEVER)`, month partitioning) are in `DEVELOPER_HANDOVER.md` §11. |
| `UI_REDESIGN_AUDIT.md` | **DELETE** | 88-finding UI audit. Executed. Historical. |
| `NOTIFICATION_DEBUG.md` | **DELETE** | Debug notes for the "notifications silently don't show" investigation. Conclusion (notification channels are immutable once created — bump the channel-id suffix) is captured in `DEVELOPER_HANDOVER.md` §10 / §15 and in `NotificationUtils.kt`'s own comments. |
| `PENDING_IMPLEMENTATION.md` | **DELETE** | Aug-24 "not built yet" list (alarms, ViewModels, tests…). All of it since built. |
| `MARKET_ANALYSIS.md` | **DELETE** | Competitive / market positioning notes. Not a codebase document. Move it out of the repo if it's still wanted for product work. |
| `MASTER_FIX_PLAN.md` | **DELETE** | The v0.5.3-round consolidated plan (143 KB). Executed. Large historical plan; the decisions it "baked in" are now just how the app works. |
| `BUG_FIXES_PLAN.md` | **DELETE** | Superseded bug-tracking plan. Items addressed; later rounds have their own regression docs. |
| `IMPROVEMENTS_PLAN.md` | **DELETE** | Superseded catch-all improvements plan. |
| `PERF_CLEANUP_PLAN.md` | **DELETE** | Superseded perf/cleanup plan (K2 migration, dependency trims). Shipped. |
| `LAYOUT_FIXES_PLAN.md` | **DELETE** | Superseded UI layout-fix plan. Shipped. |
| `ui-redesign-plan.md` | **DELETE** | Superseded redesign plan (v1). |
| `ui-redesign-plan-v2.md` | **DELETE** | Superseded redesign plan (v2). |
| `ui-fixes-plan-v3.md` | **DELETE** | Superseded UI-fix plan (v3). |
| `FIREBASE_0.5_PLAN.md` | **DELETE** | Plan for the v0.5 Firebase sync layer. Shipped and then substantially reshaped (single blob → month-partitioned) by `V051_PLAN.md` §N and `MASTER_FIX_PLAN.md`. Current sync model is in `DEVELOPER_HANDOVER.md` §11. |
| `V03_FIXES_PLAN.md` | **DELETE** | Superseded v0.3 fixes plan. |
| `V051_PLAN.md` | **DELETE** | Superseded v0.5.1 plan (§K app-lock, §N month-partitioned sync). The shipped behaviour is now documented in `FEATURES.md` + `DEVELOPER_HANDOVER.md`. |
| `V052_PLAN.md` | **DELETE** | Superseded v0.5.2 plan (habit types, journal, custom categories). Shipped. |
| `V0525_PLAN.md` | **DELETE** | Superseded v0.5.2.5 plan. Shipped. |
| `V053_PLAN.md` | **DELETE** | Superseded v0.5.3 plan. Shipped. |
| `V055_PLAN.md` | **DELETE** | Superseded v0.5.5 plan (sign-in-gate bottom bar, first-login name skip, Ongoing habit). Shipped as build 13. |
| `CUSTOMIZATION_BUILD_PLAN.md` | **DELETE** | Phase-by-phase (P1–P10) build plan for the customization round. Executed — DB v16 is built. Superseded plan file; the feature list survives in `CUSTOMIZATION_OPTIONS.md` and `CUSTOMIZATION_REGRESSION.md`. |
| `V053_REGRESSION.md` | **DELETE** | Old regression list (DB v13). Keep only the newest regression doc. |
| `V054_REGRESSION.md` | **DELETE** | Old regression list (DB v14). Superseded. |
| `V055_REGRESSION.md` | **DELETE** | Regression list for DB v15 / build 13. Superseded by `CUSTOMIZATION_REGRESSION.md` (DB v16), which re-covers the same sign-in-gate + first-login-name items in its own checklist. |

## 2. Text / binary scratch files

| Filename | Verdict | Reason |
|---|---|---|
| `TXT.txt` | **DELETE** | 45 KB raw scratch dump. Not referenced by anything. |
| `bugs.txt` | **DELETE** | ~400-byte scratch bug note. |
| `Bug_Fixes.odt` | **DELETE** | 1.2 MB binary scratch doc. Its content was worked into `BUG_FIXES_PLAN.md` and later regression docs. Binary blobs do not belong in a source tree. |

## 3. APK files (as a group)

There are 13 `Daybook-*.apk` files at the root, totalling ~150 MB. They are already matched by
`*.apk` in `.gitignore`, so none of them will be committed — but they still bloat the working
directory and confuse "which build is current".

| Filename(s) | Verdict | Reason |
|---|---|---|
| `Daybook-v0.5.5-customization-release.apk` | **KEEP** | The current latest release build (DB v16). |
| `Daybook-debug.apk`, `Daybook-release.apk`, `Daybook-v0.5.2-build9-debug.apk`, `Daybook-v0.5.2-build9-release.apk`, `Daybook-v0.5.2-build10-journal-release.apk`, `Daybook-v0.5.2-calendar-anim-release.apk`, `Daybook-v0.5.2-no-tester-popup-release.apk`, `Daybook-v0.5.2-redflag-streakfix-release.apk`, `Daybook-v0.5.2-release.apk`, `Daybook-v0.5.3-build11-release.apk`, `Daybook-v0.5.4-build12-release.apk`, `Daybook-v0.5.5-build13-release.apk` | **DELETE** | Stale build artifacts from earlier versions. Any of them can be regenerated with `./gradlew assembleRelease` / `assembleDebug`. Keeping a shelf of old APKs invites installing or shipping the wrong one. |

## 4. Not documents — leave in place (listed so they are not swept up)

`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`,
`gradle/` (wrapper), `.gitignore`, `app/` (all source, schemas, tests).

**KEEP — not a doc** (config / secrets, referenced by the build):
`firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json`,
`keystore.properties`, `local.properties`, `app/daybook-release.jks`.
`keystore.properties`, `local.properties`, and `*.jks` are gitignored on purpose — do not commit
them, do not delete them.

---

## Summary

- **KEEP (7 docs):** `README.md`, `RELEASE_SIGNING.md`, `FEATURES.md`, `DEVELOPER_HANDOVER.md`,
  `DOC_CLEANUP_PROPOSAL.md`, `CUSTOMIZATION_OPTIONS.md`, `CUSTOMIZATION_REGRESSION.md`.
- **KEEP (1 APK):** `Daybook-v0.5.5-customization-release.apk`.
- **DELETE:** 30 markdown files, 3 scratch files (`TXT.txt`, `bugs.txt`, `Bug_Fixes.odt`), 12 old APKs.
- `architecture-plan.md` and `design-plan.md` are on the DELETE list **because** their still-accurate
  content is now folded into `DEVELOPER_HANDOVER.md` (§1 philosophy, §7 architecture) and `FEATURES.md`.
  If the parent wants a belt-and-braces safety margin, keeping just these two costs little.
