# How to push a new build to testers (Firebase App Distribution)

Daybook is a sideloaded, testing-phase app with no Play Store listing, so "in-app updates" means
**Firebase App Distribution**, not Google Play's In-App Updates API. This doc is the practical
how-to; the mechanics (why the SDK behaves this way) are explained inline.

## One-time setup (already done for this project)

- Firebase CLI logged in as `abhiramys.learn@gmail.com` on this machine (`firebase login`).
- App Distribution enabled on project `daybook-v2-1f578` (console → Release & Monitor → App
  Distribution → "Get started", one-time click).
- A tester group named **`testers`** created in the console (App Distribution → Testers and
  Groups → Add group), with tester emails added to it.
- The app's Gradle config (`app/build.gradle.kts`, inside the `release` build type) already
  targets that group:
  ```kotlin
  firebaseAppDistribution {
      releaseNotes = "See commit history for what changed in this build."
      groups = "testers"
  }
  ```

If you ever rename the console group, update `groups = "..."` here to match — the two must be
byte-identical or the push will fail to reach anyone.

## Pushing a new build

**You must bump `versionCode` in `app/build.gradle.kts` before every push.** Firebase identifies
a release by version; pushing the same `versionCode` again just **re-uploads the existing
release** (harmless, but no tester's installed app will detect it as "newer" — confirmed directly
this session: `Re-uploaded already existing release 0.5.6 (14) successfully`). Bump it, then use
either of these two equivalent paths:

### Option A — Gradle (recommended, one command builds + uploads)

```bash
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
  ./gradlew assembleRelease appDistributionUploadRelease
```

This builds the signed release APK and uploads it in one step, using the `firebaseAppDistribution{}`
block already wired into `app/build.gradle.kts`.

### Option B — Raw Firebase CLI (upload an APK you already built)

```bash
firebase appdistribution:distribute /path/to/Daybook-release.apk \
  --app 1:1054765667595:android:4c9078aa9a2181d141fe0e \
  --groups testers
```

Both produce the same result: a new release in App Distribution, visible to everyone in the
`testers` group.

## What testers experience

1. **Email** — everyone in the `testers` group gets an email from Firebase the moment a release
   lands, with a link to install it. This fires regardless of whether their app is open.
2. **In-app prompt** — if a tester has already signed in once as an authorized tester (see below),
   the next time they *open* Daybook, `MainActivity.onResume()` calls
   `FirebaseAppDistribution.getInstance().updateIfNewReleaseAvailable()`, which shows the SDK's
   own "Update available" dialog and handles download + install. This is **not** a background
   push — it only fires on app open, gated by the "Check for updates" toggle in
   Settings → Notifications & alarms (on by default; see below).

### The "Enable testing features" sign-in prompt

The very first time a tester's device calls the update check, Firebase doesn't yet know who they
are, so it shows its own "Enable testing features" screen asking them to sign in with a Google
account. That sign-in is separate from any account Daybook itself uses — it's how Firebase links
*this device* to *a registered tester*. Two outcomes:

- **They sign in with an email that's in the `testers` group** → access granted, update checks
  start working for real from then on.
- **They cancel/decline** → Daybook detects this specific outcome
  (`FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED`) and automatically flips the
  "Check for updates" setting off, so it won't ask again. They can turn it back on manually in
  Settings → Notifications & alarms → "Check for updates" whenever they want.

Being *listed* as a tester in the console is not enough by itself — access is only actually
granted once a real release has been distributed to that tester/group. If you add someone to the
`testers` group but haven't pushed a build since, they'll see "Oops, you don't have access" when
they try to sign in — that's expected, not a bug, until the next push.

## How to verify a push actually worked

- The Gradle task / CLI command prints a console link and a "share this release" link when it
  succeeds — open the console link to see the release listed under App Distribution → Releases.
- Check the versionCode/versionName shown there matches what you just built.
- Testers should get the email within a minute or two.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "Oops, [email] does not have access to test Daybook" | No release has been distributed to that tester yet — being listed in the console Testers page alone grants nothing | Push a real release (`appDistributionUploadRelease` or `firebase appdistribution:distribute`) to a group that email belongs to |
| Push says "Re-uploaded already existing release" | `versionCode` wasn't bumped since the last push | Bump `versionCode` in `app/build.gradle.kts`, rebuild, push again |
| Distribute command can't find the group / pushes to nobody | `groups = "..."` in `app/build.gradle.kts` doesn't match an actual group name in the console | Make the two match exactly (case-sensitive) |
| Tester never gets the in-app "Update available" dialog | They haven't signed in as an authorized tester yet, or they declined once and the "Check for updates" toggle auto-flipped off | Have them retry "Enable testing features" with the correct email, and check the toggle in Settings → Notifications & alarms |
