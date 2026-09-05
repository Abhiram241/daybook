# Mi Band 10 → Daybook Integration: Feasibility Report

**Date of research:** 2026-09-04
**Scope:** Can Daybook (Kotlin/Compose/Room/Hilt/Firebase) pull fitness/health stats from a Xiaomi Smart Band 10?
**Status:** Research only. No app code was written or changed.

---

## 1. Direct answer

**Partially — yes, but only as a delayed, batch-style read of summary stats, not a live feed from the watch.**

The device is real (Xiaomi Smart Band 10, released June 2025) and its Android companion app, **Mi Fitness**, officially writes health data into **Health Connect**, Android's platform health-data store. That means Daybook can legitimately read steps, heart rate, sleep, SpO2, calories and workouts using Google's official, supported `androidx.health.connect:connect-client` API, without ever touching the band over Bluetooth, without reverse-engineering anything, and without the user giving up the official app. That is the good news, and it's a genuinely low-effort, durable path.

The caveats are real and you should price them in. Xiaomi offers **no public developer API** of its own for reading band data — Health Connect is the only official door, and it is a door Xiaomi controls the far side of. Mi Fitness's Health Connect bridge is, by current user reports, inconsistent: it sometimes syncs some metrics and not others, an update in July 2026 reportedly degraded it, and data frequently doesn't leave the band at all until the user opens the Mi Fitness app. So Daybook would be reading a store that is populated on someone else's unreliable schedule. Plan for "yesterday's and today-so-far's numbers, usually," not "what my body is doing now."

---

## 2. Can this be realtime?

**No. Not on any of the three paths, not in a way Daybook can use.**

The closest thing to realtime here is Path B (Gadgetbridge), and even that doesn't get you there: Gadgetbridge can display live heart rate *inside its own app*, but the Gadgetbridge documentation states plainly that "3rd party realtime heart rate access is not support, even with the vendor app," meaning the band does not expose a live heart-rate stream that any other Android app — Daybook included — can subscribe to. Path A (Health Connect) is architecturally a store-and-forward database, not a stream: its public API offers only pull-based polling (`getChanges` / `getChangesToken`), there is no push callback for a reading app, and the data only arrives there after a multi-hop delay chain (band buffers → syncs to Mi Fitness, often only when that app is opened → Mi Fitness writes to Health Connect → Daybook polls). Realistically that is *minutes* at absolute best, *tens of minutes to hours* typically, and *"not until you open Mi Fitness"* in the failure case that reviewers describe most often. Path C (export) is measured in days.

If live heart-rate streaming is a hard requirement for what you want to build, the honest answer is that the Xiaomi Smart Band 10 is the wrong hardware for it, and no amount of Daybook-side work fixes that. A band or chest strap that exposes the **standard BLE Heart Rate Service** (0x180D) — which the Xiaomi bands deliberately do not — is what you'd need, and Daybook could talk to that directly with plain Android Bluetooth APIs.

---

## 3. How Mi Band 10 data normally flows

### The device

- **Correct name:** *Xiaomi Smart Band 10.* Xiaomi dropped the "Mi" prefix from the band line years ago; "Mi Band 10" is the colloquial name and is what Gadgetbridge still calls it internally. It is a real, shipping product — announced and released in **China in June 2025**, with the global release following within days. A **Smart Band 10 Pro** and a **Ceramic Edition** also exist.
- **Operating system:** Xiaomi **Vela** (an RTOS built on the open-source NuttX kernel), branded under the HyperOS umbrella. It received a HyperOS 3 update in December 2025.
- **This matters:** the Band 10 does **not** run **Zepp OS**. Older Mi Bands (e.g. Band 7) were Zepp OS-based because they were built by Huami/Zepp Health; Xiaomi has since moved the band line onto its own platform.

### The companion app — and a naming trap worth avoiding

There are two different apps from two different companies, and a lot of low-quality web guides conflate them:

| App | Package | Vendor | Covers |
|---|---|---|---|
| **Mi Fitness** (a.k.a. Xiaomi Wear) | `com.xiaomi.wearable` | **Xiaomi** | Smart Band 7 Pro / 8 / 9 / **10**, Redmi Watch, Xiaomi Watch |
| **Zepp Life** (formerly Mi Fit) | `com.xiaomi.hm.health` | **Zepp Health** (formerly Huami) | Older Mi Bands (≤ 6-ish), legacy devices |

**The Smart Band 10 pairs with Mi Fitness.** Several SEO articles claim "Mi Fitness (formerly Zepp Life)" — this is **false** and led me to double-check every claim sourced from them. Zepp Health's well-publicised January 2025 expansion of Health Connect support to 26 data types applies to the **Zepp/Zepp Life** apps and therefore to **Amazfit and older Mi Bands — not to your Band 10.** Your Band 10's Health Connect support comes from Xiaomi's own Mi Fitness app, and is a separate (and less well-documented) thing.

### The flow

```
Xiaomi Smart Band 10
        │  Bluetooth LE (proprietary "Xiaomi protobuf" protocol, encrypted,
        │  auth-keyed — NOT a standard BLE health profile)
        ▼
   Mi Fitness app  (com.xiaomi.wearable)
        │
        ├──► Xiaomi cloud  ── required: "Sync with the cloud" must be ON
        │                     before any third-party sync works at all
        │
        └──► third-party targets:
               • Health Connect  ← the one that matters for Daybook
               • Strava, Google Fit (OAuth account-binding partners)
               • Apple Health (iOS only)
```

Per Xiaomi's own support FAQ, third-party sync is gated behind cloud sync: *"Open the Mi Fitness APP >> [Profile] >> [Settings] >> [Sync with the cloud] >> enable [Sync with the cloud]"* then *"[Profile] >> [Third-party data]"*. So even the "local" Health Connect path has a **Xiaomi-account and cloud dependency** in front of it. That's worth knowing if you care about the data staying on-device.

### Is there an official Xiaomi developer API?

**No.** There is no public, consumer-facing Xiaomi or Zepp developer API that lets an independent Android app read a paired band's health data. This is consistent with the historical position (Xiaomi/Zepp have never offered this to indie developers) and I found nothing indicating it has changed. Commercial health-data aggregators — the companies whose entire business is having these integrations — route Xiaomi through Health Connect on Android and Apple Health on iOS, which is strong evidence that no direct API exists to be bought or applied for.

**The Zepp OS SDK is a different thing entirely.** It builds "mini programs" that run **on the watch's own screen** (a Device App in JavaScript, plus a Side App hosted in the phone-side Zepp app). It is not a mechanism for pulling data **off** the band into a separate Android app. It's also doubly irrelevant here, because the Band 10 runs Vela, not Zepp OS. And even on the Zepp OS Mi Bands where it did apply, developers reported that Xiaomi never implemented the phone-communication API, so band apps couldn't talk to the phone anyway.

---

## 4. Path A — Health Connect (official)

**Verdict: viable, recommended. Confidence: high that the mechanism exists; moderate that it's reliable day-to-day.**

### What works

Mi Fitness officially supports Health Connect. This is stated in Xiaomi's own Google Play listing permission rationale (app last updated **27 July 2026**):

> **Health Connect:** Synchronize your fitness and health data to Google Health Connect

Setup is user-side, in Mi Fitness: *Profile → Settings → Sync with the cloud* (enable), then *Profile → Third-party data → Health Connect*, then pick which data types to share. The user then grants Daybook read access in Health Connect's own permission UI.

**Available data types** (per aggregator documentation of what actually arrives from Xiaomi on Android): steps, distance, floors climbed, calories (total/active/basal), heart rate (average, granular samples, min/max, resting), oxygen saturation, workout/activity events with type and duration, and sleep — start/end, duration, time in bed, and **light/REM/deep stage breakdown on Android specifically**. That is comfortably enough for a journaling app.

**The direction is right for you.** A Play Store reviewer (Aug 2026) complains that Mi Fitness *"can only send data to Health Connect... impossible to receive data."* That's a real limitation of Mi Fitness as a hub — but Daybook only wants to **read**, so Mi Fitness being write-only into Health Connect is exactly the direction you need.

### What doesn't work / what to worry about

**Reliability is the weak link, and it is not under your control.** Current Play Store reviews of Mi Fitness (all 2026, sampled from the live listing):

- *"the synchronization between Health Connect and Mi Fitness is extremely buggy. Suggested workarounds don't work. It randomly synchronizes some stats, but not others."* — Jul 2026, app v3.56.1i
- *"The new update of July 2026 removes the connection to health connect through third party devices... now health connect isn't always connected well to the app."* — Jul 2026
- *"my steps and calories etc for some days don't get recorded if I don't open the app... it's very inconsistent"* — Aug 2026
- *"It won't sync any data past 00:00 up to the time I open the app."* — Aug 2026
- *"Data synchronisation is really good and useful. On occasions it may take a few minutes, but it just works."* — Aug 2026 (the positive case)

Read together: the bridge exists and works, but is flaky, has regressed at least once recently, and **frequently requires the user to open Mi Fitness before data moves at all.** Design Daybook to degrade gracefully — show "last synced at X," never assume today's data is complete, and never build a feature that breaks when a day is missing.

Other constraints:

- **Xiaomi cloud sync must be on.** No cloud account, no Health Connect bridge.
- **Health Connect availability.** It's built into Android 14+; on Android 13 and below it's a separately-installed Google app. Daybook's `minSdk` is 26, so you must handle "Health Connect not available/not installed" as a first-class state.
- **Google Play policy.** If Daybook is distributed on Play, declaring Health Connect permissions requires an approved health-data declaration, a privacy policy, and prominent in-app disclosure. Read-only, no-network-egress use is the easiest version of that conversation to have. (If Daybook is sideloaded, this doesn't apply.)

### Realtime characteristics — Path A

**Not realtime, and structurally incapable of being realtime.** Health Connect is a store-and-forward database with a **pull-only** public API. I verified this directly against the `HealthConnectClient` API reference: the only change-tracking surface is `getChangesToken()` / `getChanges()`. **`registerForDataNotifications` does not exist in the public API** — there is no push, no callback, no live subscription for a reading app. Google's own sync guidance tells apps to check for changes when they come to the foreground and to poll periodically while foregrounded.

The latency is the *sum* of a chain, and Daybook only controls the last link:

| Hop | Typical delay | Controlled by |
|---|---|---|
| Band → Mi Fitness (BLE) | Minutes, **or not until the app is opened** | Xiaomi / user |
| Mi Fitness → Xiaomi cloud → Health Connect | Minutes to hours; sometimes drops metrics | Xiaomi |
| Health Connect → Daybook | Your poll interval (≥15 min via WorkManager) | **You** |

Realistic end-to-end: **best case a few minutes; typical case tens of minutes to hours; worst case "when the user next opens Mi Fitness."** Tightening Daybook's poll interval buys you almost nothing, because you'd just be polling an empty store more often.

One footnote for completeness: Health Connect *does* document a genuinely low-latency route via `CompanionDeviceService` + BLE GATT notifications — but that is for an app that **owns the BLE connection to the wearable and writes into Health Connect itself.** That's Path B's problem, not something a reader can opt into.

---

## 5. Path B — Gadgetbridge / unofficial BLE

**Verdict: technically works for the band, but a bad fit for Daybook. Confidence: high.**

### What works

[Gadgetbridge](https://gadgetbridge.org) is a mature open-source Android app that talks directly to many wearables over BLE, no vendor app and no cloud. It **does support the Mi Band 10**, listed as:

> **Mostly supported** — "Missing a few features, but it should be enough to cover essential daily tasks."
> **Requires Xiaomi token to pair.**
> **Experimental** — "As we don't have that device it is not known if it works correctly."

A detailed user report (Gadgetbridge issue #5323) confirms working: steps and goals, sleep with stages, heart rate (including realtime *within Gadgetbridge*), SpO2, stress, calendar sync, notifications and calls, find-device, battery. Missing: calorie data beyond goals, moving/standing time, stride data, irregular-HR alert config, alarm read-back. By July 2026 the maintainers' assessment was that *"for daily HR/sleep tracking, notifications, and workout recording, the Mi Band 10 works well today."* Firmware upgrades (1.1.155 → 3.2.1+) did **not** break pairing, which is a better durability record than I expected.

### The trade-offs

**1. You must effectively give up Mi Fitness.** A BLE band maintains one app connection at a time. Gadgetbridge's guidance is to uninstall or disable the vendor app, while *not* unpairing (unpairing invalidates the auth key). The Band 10 report says users got by with force-stopping one app before using the other — workable, but a miserable thing to ask of a user every day. **And note this kills Path A:** no Mi Fitness means no Health Connect bridge. These two paths are mutually exclusive in practice.

**2. Pairing requires manual auth-key extraction.** You must pair with Mi Fitness first, then dig the key (labelled `encryptKey`) out of `/sdcard/Android/data/com.xiaomi.wearable/files/log/XiaomiFit.main.log` and paste it into Gadgetbridge. Reporters note this file is reachable with a file manager on **Android 14 and below**; scoped-storage tightening makes it harder on newer versions. This is a one-time-per-device chore, but it is not something Daybook can automate for a user.

**3. — the decisive one — Gadgetbridge has no data-read API for other apps.** I checked its Intent API documentation specifically. It is a **trigger/command** API only: connect/disconnect, change settings, trigger an activity sync, trigger a database or ZIP export, manage alarms, debug actions, PebbleKit, raw BLE GATT. The only broadcasts back out are connection status, sync completion, and export success/failure. There is **no ContentProvider, no read intent for steps/HR/sleep, and no live health-data broadcast.** It also all requires the user to manually enable *Settings → Developer options → Intent API*.

So "Daybook integrates with Gadgetbridge" concretely means one of two things:

- **(a) Poll its exports.** Daybook fires `TRIGGER_DATABASE_EXPORT`, waits for the success broadcast, then reads and parses Gadgetbridge's exported SQLite/ZIP. This is a **batch pull, not realtime** — no better than Path A on latency, while being far uglier: you'd be parsing another app's internal, unversioned schema with no stability guarantee, and it can break on any Gadgetbridge release.

- **(b) Build the BLE stack into Daybook.** Daybook would have to fully replace Gadgetbridge's role: implement the proprietary encrypted Xiaomi protobuf protocol, the auth handshake, activity-file parsing, and reconnection handling — for a device the Gadgetbridge team itself doesn't own and flags experimental. This is an enormous, permanently-maintained reverse-engineering commitment. **Also: Gadgetbridge is GPLv3.** Lifting its protocol implementation into Daybook would oblige you to license Daybook under GPLv3. That is a licensing decision, not just an engineering one.

### Realtime characteristics — Path B

This is the only path with *any* claim to live data, and it still doesn't deliver for Daybook.

Gadgetbridge lists "Realtime stats (steps, heart rate)" among supported Xiaomi-protobuf features, and the Band 10 reporter confirms realtime HR working — **inside Gadgetbridge's own UI, over its own live BLE connection.** But the Gadgetbridge documentation is explicit that this cannot be handed to anyone else:

> "3rd party realtime heart rate access is not support, even with the vendor app. This means that other apps such as OpenTracks will not be able to use these devices for heart rate measurement"

If even OpenTracks — a well-known app that Gadgetbridge actively supports as a heart-rate consumer — can't get live HR from these bands, Daybook can't either. And the "even with the vendor app" clause is the important half: this is a limitation of **the band**, not of Gadgetbridge. So option (b) above wouldn't rescue realtime HR either; you'd do all that reverse-engineering work and still not have a live stream.

**Net: Path B could give Daybook fresher batch data than Path A (a live BLE connection syncs without waiting on Mi Fitness), at the cost of the official app, a manual key extraction, a fragile export-parsing integration or a GPLv3 rewrite — and still no realtime.**

---

## 6. Path C — manual / export

**Verdict: fine for a one-off data dump, useless as an app feature. Confidence: high.**

**Official route.** Xiaomi supports exporting Mi Fitness cloud health data through the standard account privacy tooling: sign in at `account.xiaomi.com` → **Privacy → Manage** and request a data export. This is a GDPR-style account data request — legitimate, ToS-clean, and delivered as a downloadable archive after a processing delay measured in **days**.

**Unofficial route.** Several community tools poll Xiaomi's private cloud API with a token scraped from the app or from the browser session on the export page — e.g. `kevinkwee/Mi-Fitness-Sync` (Python CLI for Mi Fitness workout data, GPX export), `rolandsz/Mi-Fit-and-Zepp-workout-exporter`, and a macOS variant that dumps the desktop app's SQLite DB to CSV. These work, but: they use an undocumented private API, they depend on extracting an `apptoken` (sometimes needing root or manual devtools work), they sit in ToS grey territory, and they break whenever Xiaomi changes the endpoint. Fine for a personal script; not something to ship inside Daybook.

**Realtime: no, obviously.** The official export is a batch delivered days later. The unofficial cloud API is at best a periodic poll of a cloud that is itself downstream of the same "user must open Mi Fitness" bottleneck. Neither is a candidate for anything live.

Realistically this path's value is **one-time backfill** — seeding Daybook with historical data at setup — not ongoing sync.

---

## 7. Recommendation

**Take Path A (Health Connect), read-only, on a WorkManager schedule — but scope the feature to what the data can actually support.**

Reasoning:

1. **It's the only official, durable path.** It's a Google-supported API, it survives Xiaomi firmware and app updates far better than any reverse-engineered alternative, and it costs the user nothing — they keep Mi Fitness and keep using their band exactly as they do now.
2. **The effort ratio is dramatically better.** Path A is roughly a dependency, a permission flow, a repository, a worker and a settings toggle. Path B is either a fragile foreign-schema parser or a multi-month protocol reimplementation with a licence change attached.
3. **Path B's only real advantage is freshness, and freshness isn't achievable anyway.** The band refuses third-party realtime HR at the firmware level. Once realtime is off the table for every path, Path B is paying a very high price for a modest latency improvement.
4. **Path C isn't an ongoing integration.** Keep it in your back pocket for a one-time historical backfill if you want one.

**Scope the feature accordingly.** Build it as *"Daybook shows your daily body stats alongside your journal entries"* — yesterday's sleep, today's step count so far, resting heart rate trend. Do **not** build anything that assumes fresh, complete, or timely data: no live HR display, no "you hit your step goal!" real-time trigger, no streak logic that a missing sync day would silently corrupt. Given the July 2026 regression reports, also treat "the bridge broke entirely for a while" as a state the UI must survive.

**If live heart rate is actually what you want,** don't build any of this. The Band 10 can't provide it to third-party apps under any path. That would be a hardware-change conversation.

---

## 8. If you want to proceed — the shape of the work

Not an implementation plan. Enough to decide whether to greenlight one.

### Dependency

```kotlin
implementation("androidx.health.connect:connect-client:1.1.0")  // stable, released 2025-10-08
```

**Compatibility check against Daybook as it stands:** the library needs `compileSdk 34` (Android 14) and supports `minSdk 24`. Daybook is at `compileSdk 34` / `minSdk 26` / `targetSdk 34`, so **1.1.0 should drop in without an SDK bump.** Worth flagging though: `app/build.gradle.kts` carries several comments pinning dependencies to "the last version that builds against compileSdk 34" (Firebase BoM, credentials, biometric, security-crypto). The project is deliberately parked at 34 and is already at the edge of that constraint — so verify the Health Connect build before committing, and be aware that the `1.2.0-alpha*` line is where future development goes.

### Permission model — note that this is *not* normal runtime permissions

Health Connect has its own permission system and its own system permission screen.

- Declare in the manifest: `android.permission.health.READ_STEPS`, `READ_HEART_RATE`, `READ_SLEEP`, plus whichever of `READ_TOTAL_CALORIES_BURNED` / `READ_DISTANCE` / `READ_OXYGEN_SATURATION` / `READ_EXERCISE` you want.
- Request via `PermissionController.createRequestPermissionResultContract()` — **not** `ActivityResultContracts.RequestPermission`.
- Health Connect requires apps to handle `ACTION_SHOW_PERMISSIONS_RATIONALE` (an activity/activity-alias with that intent filter) or the permission request is rejected.
- Optional: `PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND` if you want the worker to read without the app being foregrounded. **This one matters** — without it, a background `WorkManager` read may not be permitted, which somewhat defeats scheduled sync. Check its behaviour on the user's actual OS version early; it is the single most likely thing to surprise you.
- Handle "Health Connect not installed / not available" (pre-Android 14) as a real UI state.

### Data mapping

| Health Connect record | Daybook use |
|---|---|
| `StepsRecord` | daily step total |
| `HeartRateRecord` | samples → resting / avg / max per day |
| `SleepSessionRecord` (+ stages) | duration, bedtime/waketime, light/REM/deep |
| `TotalCaloriesBurnedRecord`, `DistanceRecord` | optional secondary stats |
| `OxygenSaturationRecord` | optional |
| `ExerciseSessionRecord` | workout events |

Read pattern: `getChangesToken()` for the record types, persist it, then `getChanges()` on each run. **Token expires after 30 days unused** — keep a fallback that does a time-range read from the last sync timestamp and dedupes, or you'll silently lose data for any user who leaves the app alone for a month.

### Where it plugs into Daybook

- **New repository, parallel to the existing ones.** `data/health/HealthConnectRepository.kt`, sitting alongside `HabitRepository`, `FoodMedRepository`, `JournalQuestionRepository` etc. in `app/src/main/java/com/daybook/app/data/`. Hilt-injected like the rest; add a `di/HealthModule.kt` or extend `di/DatabaseModule.kt`.
- **Persistence.** New Room entity + DAO under `data/local/`, an `AppDatabase` version bump, and a migration added to `data/local/Migrations.kt`. Cache locally so the UI never blocks on Health Connect and so gaps are visible rather than invisible.
- **Sync cadence — scheduled pull, not a live observer.** New `util/work/HealthSyncWorker.kt` next to the existing `WindowRefreshWorker` and `SyncFlushWorker`, following the same `@HiltWorker` + `CoroutineWorker` + `enqueueUniquePeriodicWork(ExistingPeriodicWorkPolicy.UPDATE)` pattern that `WindowRefreshWorker` already establishes. WorkManager's minimum periodic interval is 15 minutes; something like **every 2–6 hours, plus an opportunistic refresh when the app comes to the foreground**, matches the data's real freshness without burning battery polling an empty store. A live observer buys nothing — there's nothing to observe.
- **Settings toggle.** Add to `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt`, backed by a new column via `AppSettingsRepository` / `AppSettingsDao` (another migration). Default **off**; the toggle should launch the Health Connect permission flow and surface last-sync status and a manual "sync now."
- **Keep it one-way and read-only.** Don't write back into Health Connect — it avoids write-permission policy scrutiny and avoids any chance of a sync loop.
- **Decide deliberately about Firebase.** Daybook already syncs to Firestore via `data/sync/CloudSyncRepository`. **My recommendation: do not push health data through it initially.** It's a materially more sensitive data class than journal entries, it raises the Play policy bar, and it's easy to add later and hard to un-leak.

**Rough size:** a focused chunk of work — one repository, one worker, one entity + migration, one settings surface, one permission flow, plus UI wherever you want the stats to appear. The permission/availability edge cases and the Play declaration (if you ship on Play) will take longer than the data plumbing.

---

## 9. Sources

**Currency note:** research conducted 2026-09-04. Play Store data was fetched live and is current as of that date. Xiaomi's own documentation is thin on Android Health Connect specifics, so the strongest evidence for Path A is Xiaomi's Play listing text plus corroboration from a commercial aggregator and current user reports.

### Device and companion app
- [Xiaomi Smart Band 10 — Xiaomi Global](https://www.mi.com/global/product/xiaomi-smart-band-10/) — official product page
- [Xiaomi Smart Band 10 review — GSMArena](https://www.gsmarena.com/xiaomi_smart_band_10_review_-news-68687.php) and [TechRadar](https://www.techradar.com/health-fitness/fitness-trackers/xiaomi-smart-band-10-review) — release timing (June 2025), Mi Fitness as companion app
- [HyperOS 3 update hits Xiaomi's Band 10 — Gizmochina](https://www.gizmochina.com/2025/12/02/hyperos-3-update-hits-xiaomis-band-10-and-redmi-note-14/) — Dec 2025; Vela/HyperOS, not Zepp OS
- [Zepp Life guide — Android Authority](https://www.androidauthority.com/zepp-life-mi-fit-app-3266258/) — Zepp Life vs Mi Fitness distinction, and the warning not to run multiple vendor apps at once

### Path A — Health Connect
- [Mi Fitness (Xiaomi Wear) — Google Play](https://play.google.com/store/apps/details?id=com.xiaomi.wearable&hl=en_US) — **primary source.** Listing updated 27 Jul 2026. Contains Xiaomi's own statement: *"Health Connect: Synchronize your fitness and health data to Google Health Connect."* All quoted user reviews were parsed from this live listing with their timestamps (Jun–Aug 2026).
- [Xiaomi FAQ KA-230357 (UK)](https://www.mi.com/uk/support/faq/details/KA-230357/) and [KA-517372 (Global)](https://www.mi.com/global/support/faq/details/KA-517372/) — third-party data sync steps; confirms the "Sync with the cloud" prerequisite. *Caveat: these FAQs are written for Band 9 / 9 Active and are somewhat generic about which third-party platform is meant.*
- [Mi Fitness (Xiaomi) — ROOK Tech Documentation](https://docs.tryrook.io/data-sources/xiaomi/) — commercial aggregator; confirms Health Connect is the Android route for Xiaomi data and enumerates the data types that actually arrive
- [Synchronize data — Android Developers](https://developer.android.com/health-and-fitness/health-connect/sync-data) — Changes API, change tokens, 30-day expiry, background read permission, `CompanionDeviceService`
- [`HealthConnectClient` API reference](https://developer.android.com/reference/androidx/health/connect/client/HealthConnectClient) — **verified directly:** only `getChanges` / `getChangesToken` exist; no `registerForDataNotifications`
- [Health Connect release notes](https://developer.android.com/jetpack/androidx/releases/health-connect) — 1.1.0 stable (8 Oct 2025), 1.2.0-alpha06 (26 Aug 2026), compileSdk/minSdk requirements
- [Zepp Health can now sync much more data with Health Connect — Gadgets & Wearables](https://gadgetsandwearables.com/2025/01/24/zepp-health-connect/) — Jan 2025, 26 data types. **Applies to Zepp/Zepp Life (Amazfit, older Mi Bands), NOT to the Band 10.** Included to document why it's a red herring.

### Path B — Gadgetbridge
- [Xiaomi protobuf watches — Gadgetbridge docs](https://gadgetbridge.org/basics/topics/xiaomi-protobuf/) — supported/missing feature lists; source of the *"3rd party realtime heart rate access is not support, even with the vendor app"* quote
- [Xiaomi devices — Gadgetbridge](https://gadgetbridge.org/gadgets/wearables/xiaomi/) — Mi Band 10 listed "Mostly supported", "Requires Xiaomi token to pair", "Experimental"
- [Issue #5323 — "[Mi Smart Band 10] My Experience"](https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/5323) — detailed real-world report through Jul 2026; auth key location (`encryptKey` in `XiaomiFit.main.log`), Mi Fitness force-stop coexistence, firmware upgrade survival
- [Issue #5029 — Mi Smart Band 10](https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/5029) — original support request
- [Intents / Intent API — Gadgetbridge docs](https://gadgetbridge.org/internals/automations/intents/) — **verified:** trigger-only API, no read endpoints, no ContentProvider, no live health broadcast; requires Developer options
- [Huami/Xiaomi server pairing — Gadgetbridge](https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/) — auth key extraction; guidance to remove/disable the vendor app but not unpair

### Path C — export
- [Xiaomi support KA-11566 — How to download/export data from Mi Fitness](https://www.mi.com/global/support/article/KA-11566/) — official export via `account.xiaomi.com` → Privacy → Manage. *Caveat: page returned HTTP 403 to direct fetch; content confirmed via search result excerpt only.*
- [kevinkwee/Mi-Fitness-Sync](https://github.com/kevinkwee/Mi-Fitness-Sync) — unofficial Python CLI for Mi Fitness cloud workout data
- [rolandsz/Mi-Fit-and-Zepp-workout-exporter](https://github.com/rolandsz/Mi-Fit-and-Zepp-workout-exporter) and [Export Mi Fit and Zepp workout data](https://rolandszabo.com/posts/export-mi-fit-and-zepp-workout-data/) — token extraction methods
- [hayeon17kim/mi-fitness-mac-export](https://github.com/hayeon17kim/mi-fitness-mac-export) — macOS Mi Fitness SQLite → CSV

### Could not verify confidently — flagged
- **Exact Health Connect data types Mi Fitness writes.** Xiaomi publishes no per-type list for Android. The type list in §4 is assembled from aggregator documentation and should be **empirically confirmed** by installing Health Connect and inspecting what Mi Fitness actually writes on your own device. This is a 10-minute check and I'd do it before writing any code.
- **Mi Fitness → Health Connect write frequency.** No documented figure from Xiaomi. The latency estimates in §2 and §4 are inferred from user reports, not measured.
- **The July 2026 Health Connect regression.** Sourced from a single Play review. Direction and rough timing look credible, but the precise scope is unconfirmed.
- **Several widely-circulated "how to connect Xiaomi to Health Connect" guides are unreliable** — `reaction-club.com` and `healthychronos.com` both surfaced high in search results and one states "Mi Fitness (formerly Zepp Life)", which is factually wrong. Neither was used as a load-bearing source here.
- **Whether `PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND` behaves as needed on the user's specific Android version.** Documented as available, but its practical behaviour with `WorkManager` should be prototyped before the design depends on it.
