# APK_CHAT_DELIVERY_PLAN.md

**Status: IDEA ONLY. Not implemented.** The user is phone-only for this round (no terminal, no
adb) and asked how to get a build to them purely through chat. This documents the options
considered and the recommended path for a future round — nothing here has been built yet.

## The constraint

- Chat file delivery (`SendUserFile`) has a hard 30MB cap.
- A plain debug APK for this app is ~46-48MB. That's not from native libs (checked: each ABI's
  `.so` payload is ~10KB, negligible) — it's unminified multidex `classes*.dex` across the app's
  full dependency set (Firebase, Compose, Hilt, etc.) plus the RepDB exercise image assets
  (~1-2MB). None of that shrinks by picking a single CPU architecture.
- The user cannot run any command (no `cat`/`copy` to reassemble a split file, no `adb install`)
  — whatever gets delivered has to be a single file, installable straight from the phone.

## Options considered

1. **Split the APK into <30MB chunks, send both, user reassembles.** Rejected — requires a
   command the user can't run from a phone.
2. **Upload to Google Drive via the Drive MCP tool and share a link.** Rejected — that tool only
   accepts inline file content (base64) in the tool call itself; a 46MB file is ~61MB of base64,
   far too large for one tool call (would blow through the conversation's token budget on its
   own, if it didn't just fail outright). No local-file-path upload option exists in that tool.
3. **Host it in an Artifact and link to it.** Rejected — Artifacts explicitly block any
   viewer-side download (`<a download>`, script-driven saves are inert in the sandbox), and the
   artifact asset store's allowed file types (image/video/PDF/font/CSS/JS/text) don't include
   `.apk` anyway. There's no way to hand back a working "tap to download and install" link this
   way.
4. **USB + `adb install` directly from this machine.** Works with zero transfer, but requires the
   user to be at a computer with a USB cable — not available this round (phone-only).
5. **Build a signed *release* APK instead of debug.** ✅ **Recommended.** This repo already has
   release-build minification wired up and working
   (`app/build.gradle.kts` → `buildTypes.release`: `isMinifyEnabled = true`,
   `isShrinkResources = true`, `proguard-rules.pro`) — it's the exact build type used for every
   real release so far, so the R8/shrink rules are already proven, not experimental. Stripping
   unused code should bring the APK comfortably under 30MB, small enough for `SendUserFile` to
   deliver directly in chat, no linking/splitting/hosting workaround needed at all.

## The catch with option 5

`keystore.properties` exists in this repo, so `assembleRelease` produces a build signed with the
**real release key**, not the debug key. Android refuses to install an APK over an existing install
signed with a different key. Since the last two builds sent this round were plain `debug` APKs
(debug-signed), the user's phone currently has a **debug-signed** install (if they sideloaded
either of them). Consequences for whoever picks this up next:

- The user must **uninstall the current app** before sideloading a signed-release build, even
  though `versionCode`/`versionName` won't have changed.
- This is actually just the project's original standing deliverable format (see
  `daybook-round-workflow` memory: "Deliverable is a signed release APK... Same versionCode across
  builds → tell the user to uninstall the old app before sideloading") — this round temporarily
  deviated to debug APKs at the user's request, and this plan is really "go back to the normal
  release-APK workflow," not a new mechanism.

## What to do when this is picked up

1. Confirm with the user they're ready to uninstall + reinstall (data loss risk: local Room DB is
   on-device; if Firestore cloud sync is on and healthy this is safe, but confirm before telling
   them to uninstall — don't just assume).
2. `JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ./gradlew assembleRelease` (JDK 17 needed — see note
   below).
3. Verify the output APK size is actually under 30MB before attempting `SendUserFile` (if R8
   doesn't shrink it enough, fall back to another option above rather than assuming it fits).
4. Send via `SendUserFile`.

## Environment note (unrelated to this idea, but relevant to any future build)

The default JDK on this machine is JDK 21, which is missing `jlink`, so plain `assembleDebug`/
`assembleRelease` fails under it. Both builds this round used
`JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9` explicitly. Future rounds building on this machine
will likely need the same override.
