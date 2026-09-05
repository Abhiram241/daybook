# Daybook — Handover & Learn-Android-From-This-Codebase Course

You are about to take over an Android app called **Daybook**. This document assumes you can
program a little — you know what a *variable* is, what an `if` and a loop and a *function* are,
maybe you have written a *class* — and that is all. You have never built a mobile app, never
written Kotlin, never used Android Studio or Gradle. That is fine. This file is written so that
if you read it top to bottom and do every **Try it** exercise, you will come out able to:

- check the project out, build it, and run it on a phone or an emulator;
- find your way around the code;
- make a small change on purpose and see it in the running app;
- and, along the way, actually understand modern Android development as a subject — not just this
  one app's quirks.

It is long. That is deliberate. Every section is meant to teach one thing. Skim nothing on the
first pass; skip back to it later as a reference.

---

## How to use this document

Read it **once, in order, doing the exercises.** Then keep it open forever as a reference.

There are three layers:

1. **Part 0** — what the app is and the ideas behind it. 15 minutes. Read it now.
2. **Modules 1–15** — the course. Each module teaches one concept, then shows you exactly where
   that concept lives in this codebase, then gives you a tiny hands-on task, then asks you a
   couple of checkpoint questions. Do them **in order** — each one leans on the ones before it.
3. **Reference sections** at the end — a "make your first change" walkthrough, a list of ways to
   break things (and how not to), a glossary of every term, and a file-map table. Skim them now,
   use them later.

**Every module has the same shape:**

- **Concept** — plain words. Every piece of jargon is defined the first time it appears.
- **An everyday-terms analogy** — the closest non-programming or plain-code picture.
- **See it in the real code** — a specific file (and rough line numbers) in *this repo* to open
  and read, with a walk-through.
- **Try it** — a small, concrete task with the exact commands and the result you should see.
  **Do not skip these.** Reading code and changing code are different skills.
- **Checkpoint** — two or three questions. If you cannot answer them, re-read the module.

**Rough time budget** (very rough):

| Part | Modules | Time |
|---|---|---|
| Setup & first run | 1 | half a day (mostly downloads) |
| Language | 2 | 2–3 hours |
| Android + build basics | 3, 4 | 2–3 hours |
| The UI | 5, 6 | 3 hours |
| The data layer | 7, 8, 9, 10, 11 | one full day |
| Wiring it together | 12, 13, 14 | one full day |
| Build & release | 15 | half a day |
| Your first change | walkthrough | half a day |

So: roughly a focused working week.

---

## What you'll be able to do at each checkpoint

Tick these off as you go. They matter more than "I read the module".

- [ ] **C1 — The app runs.** Android Studio is installed, this folder is open in it, and Daybook
      is running on an emulator or your own phone. *(end of Module 1)*
- [ ] **C2 — You changed something visible.** You edited a piece of on-screen text, rebuilt, and
      saw the change in the running app. *(Module 5)*
- [ ] **C3 — You can read the data layer.** You can point at where a habit is stored, where the
      SQL for "get all habits" lives, and what turns a database change into a screen update.
      *(Modules 7–9)*
- [ ] **C4 — You added a working setting.** You added a new toggle to a Settings screen that
      survives an app restart, following the walkthrough. *(the "make your first change" section)*
- [ ] **C5 — You changed the database safely.** You added a column to a table with a *migration*,
      the schema JSON regenerated, and all four build gates pass. *(Module 7 + the walkthrough)*
- [ ] **C6 — You shipped a build.** You bumped `versionCode`, ran the 4-gate, produced a signed
      release APK, and pushed it to testers. *(Module 15)*
- [ ] **C7 — You can trace the whole system.** Someone asks "what happens when the user taps the
      checkmark on a habit?" and you can follow it file by file without this document open.
      *(the "follow one tap" reference)*

---

## Part 0 — What Daybook is, and why it exists

**Daybook is an offline-first personal tracker for habits and for what you eat, take, or want to
journal about.** You set reminder times. When a reminder fires, it does not just nag you — it
*asks a question* ("Did you do this?", "What did you have?") and keeps your answer. Over weeks you
end up with a log you never had to sit down and write, plus streaks and simple stats.

**Who it is for:** originally one person with a chronic gut condition (Crohn's — that is why there
is a "red-flag food diary" with a *none / maybe / red* trigger marker) who wanted a low-effort way
to keep a food-and-symptom log and a habit history without opening an app every single time.

**Four design rules, in priority order.** Almost every decision in the code traces back to one of
these. When you are unsure whether a change fits, check it against them.

1. **Offline and private.** The real data lives in a database *on the phone*. If the user never
   backs up, they must never lose their history. There is no analytics and no tracking. Cloud
   sync exists, but it is a *copy* of the on-phone truth, never the truth itself.
2. **Notification-first.** The user should be able to do a whole day of logging from the phone's
   notification shade — complete, skip, snooze, reply — without opening the app.
3. **Dark, calm, one-handed.** One dark theme (near-black background `#0B0D0F`), soft pastel card
   colours, big tap targets low on the screen, minimal animation. There is **no light theme**, on
   purpose.
4. **Low friction over features.** Every feature is optional and defaults to "invisible until you
   turn it on". Someone who updates the app should notice *no behaviour change* until they go
   looking for one.

**Current state (as of this handover):**

| Thing | Value |
|---|---|
| App name / package | Daybook / `com.daybook.app` |
| `versionName` (human) | `0.5.6` |
| `versionCode` (update counter) | `20` |
| Room database schema version | `19` |
| Platforms | Android only. `minSdk 26` (Android 8.0), `targetSdk 34` (Android 14) |
| Theme | Dark only |
| Data | Offline-first; optional Google sign-in + cloud sync |
| Source control | Git. GitHub: `https://github.com/Abhiram241/daybook` |

### The big-picture shape

Daybook is built in **layers**. Data flows up from the database to the screen, and user actions
flow back down. Around that core sit three "side systems" that deal with time, background work,
and the cloud.

```
                       ┌──────────────────────────────────────────────┐
                       │                THE SCREEN                     │
                       │   Jetpack Compose  (@Composable functions)    │
                       │   "the screen is a picture of the data"       │
                       └───────────────▲──────────────┬───────────────┘
              reads state (StateFlow)  │              │  calls functions on user taps
                       ┌───────────────┴──────────────▼───────────────┐
                       │                 ViewModel                     │
                       │   one per screen. Holds the screen's state,   │
                       │   survives phone rotation, runs slow work     │
                       │   on coroutines.                              │
                       └───────────────▲──────────────┬───────────────┘
                    live streams (Flow)│              │ suspend function calls
                       ┌───────────────┴──────────────▼───────────────┐
                       │                Repository                     │
                       │   a thin, boring middle layer. The one place  │
                       │   "get active habits" is written.             │
                       └───────────────▲──────────────┬───────────────┘
                                       │              │
                       ┌───────────────┴──────────────▼───────────────┐
                       │              Room  +  DAO                     │
                       │   Kotlin classes  <->  SQLite tables on disk. │
                       │   THE SOURCE OF TRUTH.                        │
                       └──────────────────────────────────────────────┘

   SIDE SYSTEMS (all read/write the same Room tables through repositories/the scheduler):

   ┌───────────────────────────┐  ┌───────────────────────────┐  ┌────────────────────────────┐
   │  AlarmManager + Notifs    │  │  WorkManager              │  │  Firebase (optional)       │
   │  exact-time reminders.    │  │  deferrable background    │  │  Google sign-in (Auth) +   │
   │  An alarm fires -> a      │  │  jobs. Here: a daily      │  │  Cloud Firestore sync.     │
   │  BroadcastReceiver posts  │  │  "top up the reminder     │  │  Firestore is a gzipped    │
   │  a notification with      │  │  window" worker + a       │  │  MIRROR of Room, never the │
   │  Skip / Snooze / Done.    │  │  sync-flush worker.       │  │  source of truth.          │
   └───────────────────────────┘  └───────────────────────────┘  └────────────────────────────┘
```

The single class that ties the reminder machinery to the database is
`data/OccurrenceScheduler.kt` — you will meet it in Module 13.

---

# MODULE 1 — Your machine, and running the app once

### Concept

A Python or JavaScript script runs the moment you type `python foo.py` or `node foo.js`. An
Android app does not work like that. It has to be:

1. **compiled** — translated from Kotlin source text into a form the phone can run;
2. **packaged** — zipped up into a single `.apk` file (an "Android Package"; it really is a zip);
3. **signed** — stamped with a cryptographic key so Android can tell one build from another;
4. **installed** — copied onto a device or a *simulated* device (an "emulator");
5. **launched** — started by the Android operating system, not by you.

The tool that does steps 1–3 is **Gradle** (Module 4). The program you will spend your day in,
which drives all five steps, is an **IDE** — an "Integrated Development Environment" — called
**Android Studio**.

Android Studio bundles almost everything you need:

- a **code editor** (a much smarter text editor: it knows Kotlin, jumps to definitions, renames
  safely, shows errors as you type);
- the **Android SDK** ("Software Development Kit") — Android's own library code that your app
  calls into, plus command-line tools. The most important tool is **`adb`**, the "Android Debug
  Bridge", which talks to a connected phone or emulator (install an APK, read its logs, run a
  shell command on it);
- an **emulator** — a whole Android phone simulated on your computer;
- a **JDK** ("Java Development Kit") — Kotlin compiles down to the same low-level format ("JVM
  bytecode") as the Java language, so it needs a Java toolchain. This project wants **JDK 17**.

This machine already has the JDK and SDK set up *outside* Android Studio, because the previous
developer built releases from a terminal. The important paths:

```
JAVA_HOME    = /home/abhiram/jdk/jdk-17.0.11+9       (JDK 17 — the version the build wants)
ANDROID_HOME = /home/abhiram/android-sdk
```

`local.properties` in the repo root already points at the SDK
(`sdk.dir=/home/abhiram/android-sdk`). `local.properties` is machine-specific and is *not* stored
in git — every developer has their own.

### An everyday-terms analogy

Think of the Python interpreter as a chef who cooks straight from a recipe card as you hand it
over. Android is a factory: the recipe (your Kotlin) goes to a production line (Gradle) that
turns it into a sealed, labelled package (the APK), a truck delivers it (`adb install`), and only
then does the shop (the phone's OS) put it on the shelf and let a customer use it. The gap
between "I edited the recipe" and "I can taste the result" is minutes, not seconds. That is
normal here. Module 4 explains why and how to make it hurt less.

### See it in the real code

Nothing to read in the app itself yet — this module is about your machine. But open these three
small files just to see they exist:

- **`local.properties`** — one line, the SDK path.
- **`settings.gradle.kts`** — declares the project is called "Daybook" and contains one *module*
  named `:app`.
- **`RELEASE_SIGNING.md`** — one page, explains the signing key. Read it now; you will need it in
  Module 15. Do **not** copy any password out of it into notes or other files.

### Try it

**1a. Get Android Studio.** Download it from `developer.android.com/studio`. During first-run
setup, let it install the default SDK and at least one "system image" for the emulator (pick a
recent API level — 34 or 35).

**1b. Open the project.** In Android Studio choose *Open* and pick this folder
(`/home/abhiram/Downloads/app-for-food`). It will "sync Gradle" — read the build files and
download every library the project depends on. The first sync can take several minutes. If it
says it cannot find a JDK, point it at `/home/abhiram/jdk/jdk-17.0.11+9` under
*Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*.

**1c. Run it on your own phone (best) or an emulator.**

- *On your phone:* open *Settings → About phone* and tap "Build number" seven times to unlock
  "Developer options", then in *Settings → Developer options* turn on "USB debugging". Plug the
  phone in, accept the "allow debugging" prompt, pick the phone in the device dropdown at the top
  of Android Studio, and press the green ▶ **Run** button.
- *On an emulator:* open *Device Manager* (right-hand toolbar) → *Create device* → pick a phone
  (e.g. Pixel 6) and the system image you downloaded → Finish. Then press ▶.

Either way, Android Studio builds the app, installs it, and launches it. **You will hit a Google
sign-in screen** — the app requires it. On a real phone, sign in with your Google account. On an
emulator you first need to add a Google account to the emulator (its own *Settings → Accounts*).

**1d. Build once from the terminal**, so you have seen the "real" way the previous developer did
it:

```bash
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew assembleDebug
```

When it finishes, the built app is at `app/build/outputs/apk/debug/app-debug.apk`.

**1e. Make a safety commit.** This *is* a git repo, but make your own checkpoint before you start
changing things:

```bash
cd /home/abhiram/Downloads/app-for-food
git status                 # see what's here
git switch -c my-onboarding # work on a branch, never straight on the main branch
```

From now on, commit whenever something works. If you break a file, `git restore <path>` undoes
your uncommitted change to it.

### Checkpoint

1. Why can you not simply "run" a `.kt` file the way you run a `.py` file?
2. Name the four big things Android Studio bundles that you would otherwise install one by one.
3. What is `adb`, and what is an emulator?

---

# MODULE 2 — Kotlin, starting from almost nothing

### Concept

The whole app is written in **Kotlin**. It is made by JetBrains (the company behind Android
Studio) and is the language Google recommends for Android. Compared with a scripting language
like Python or JavaScript, Kotlin is:

- **Statically typed.** Every variable has a *type* (a category of value: whole number, text,
  true/false, a `Habit`, a list of `Habit`s...) that is fixed and known before the program runs.
  The compiler refuses to build if the types do not line up. This feels like paperwork at first
  and quickly becomes a safety net: a whole class of bug ("I passed text where a number was
  expected") simply cannot happen.
- **Null-safe.** "Null" means "no value here". Kotlin's type system tracks whether a value is
  *allowed* to be null. `String` can never be null; `String?` (with the question mark) might be.
  This kills the single most common crash in the history of Android.
- **Compiled, not interpreted** — see Module 1.
- **Expression-oriented.** An `if`, a `when`, even a whole `{ }` block can *produce a value* that
  you assign to something.

You do **not** need to master Kotlin before continuing. You need enough to *read* this codebase.
Here is that enough.

### The pieces, with tiny examples

**Variables: `val` and `var`.**

```kotlin
val name = "Alex"     // a value that never changes. The compiler ENFORCES that.
var count = 0         // a value that can be reassigned
count = count + 1     // fine
// name = "Sam"       // COMPILE ERROR: a val cannot be reassigned
```

Prefer `val`. Most of this codebase is `val`. A `var` is a small flag that says "this changes".

**Types are written after the name, with a colon.**

```kotlin
val n: Int = 5              // Int  = whole number
val label: String = "hi"    // String = text
val ratio: Double = 0.75    // Double = number with a fractional part
val done: Boolean = false   // Boolean = true or false
fun add(a: Int, b: Int): Int { return a + b }   // takes two Ints, returns an Int
```

Often the compiler can work the type out and you leave it off: `val n = 5` is obviously an `Int`.
In function signatures you usually write it for clarity.

**Null, and the `?` `?:` `!!` family.**

```kotlin
val a: String  = "x"    // can never be null
val b: String? = null   // the ? means "String, or null"

// b.length            // COMPILE ERROR: b might be null, so .length might blow up
b?.length              // "safe call": gives null if b is null, else b.length      (type: Int?)
b?.length ?: 0         // "Elvis operator": use b?.length, but if THAT is null use 0 (type: Int)
b!!.length             // "I swear it is not null" — if it IS, the app crashes HERE, on this line
```

You will see `?.` and `?:` everywhere. `!!` is used rarely and on purpose — each one is a promise
the author is making.

**Functions: `fun`.**

```kotlin
fun greet(name: String): String {
    return "Hi, $name"
}

fun greetShort(name: String) = "Hi, $name"   // short form: one expression, no braces, no return
```

**String templates** — `$name` and `${expression}` inside a string put a value into the text:

```kotlin
val s = "You have $count items, ${count * 2} halves"
```

**`data class` — a plain bundle of named values.**

```kotlin
data class HabitDef(val id: String, val name: String, val snoozeMinutes: Int = 10)
```

`= 10` is a **default value**: if the caller does not supply `snoozeMinutes`, it is `10`. From a
`data class` you get, for free: a constructor, value-based equality (`==` compares the contents),
a readable text form, and **`copy()`**:

```kotlin
val a = HabitDef(id = "1", name = "Water")
val b = a.copy(name = "Drink water")   // a new object: same id, same snooze, new name
```

This codebase uses `.copy()` constantly. It is how you "change" a value that is otherwise
immutable — you make a new one with one field different.

**`when` — like a chain of `if / else if / else`, but tighter, and it produces a value.**

```kotlin
val label = when (status) {
    Status.PENDING   -> "waiting"
    Status.COMPLETED -> "done"
    Status.SKIPPED, Status.LOGGED -> "resolved"
    else -> "unknown"
}

when {                                  // no subject: just a list of conditions
    count == 0 -> println("none")
    count < 10 -> println("a few")
    else -> println("lots")
}
```

**Collections and `.map` / `.filter`.** A `List<Habit>` is an ordered collection of `Habit`s.

```kotlin
val names  = habits.map { it.title }              // a new list of every habit's title
val active = habits.filter { !it.isArchived }     // a new list of only the non-archived habits
val firstBig = habits.firstOrNull { it.title.length > 20 }  // first match, or null
val total  = habits.sumOf { it.snoozeIntervalMinutes }      // add a number up across the list
```

`{ ... }` here is a **lambda** — a small nameless function passed as an argument. `it` is the
automatic name for "the current element". You can name it yourself:
`habits.map { habit -> habit.title }`.

**The trailing-lambda rule** (this one confuses everyone at first, and it explains why the UI
code looks the way it does): *if the last argument to a function is a lambda, you write it after
the parentheses; if it is the only argument, you drop the parentheses entirely.*

```kotlin
list.filter({ it > 0 })   // legal but nobody writes it like this
list.filter { it > 0 }    // same thing, the normal way

Column(modifier = Modifier.fillMaxSize()) { Text("hi") }   // the { Text("hi") } is the last arg
```

That is why Compose code (Module 5) reads like `Column { Row { Text("x") } }` — those braces are
trailing lambdas, not code blocks.

**`class` and `object`.**

```kotlin
class Counter(start: Int) {         // constructor parameters go in the parentheses
    var value = start
    fun bump() { value++ }
}
val c = Counter(10)                 // no "new" keyword

object Config {                     // a SINGLETON: exactly one of it, ever, created on first use
    const val MAX = 100
    fun describe() = "max is $MAX"
}
Config.describe()
```

This codebase uses `object` for stateless bundles of helpers (`NavConfig`, `ContentHash`).

**`enum class` — a fixed set of named values.**

```kotlin
enum class HabitType { INDIVIDUAL, BATCH, STREAK, JOURNAL }
val t = HabitType.BATCH
```

**Project rule you must internalise now:** the database (Module 7) stores an enum value *by its
name text*, and UI code lists an enum's values *in the order they are declared*. So you may
**add a new value at the end**, but **never reorder or rename** existing ones — you would
silently repoint stored data. Every enum in `data/model/DataModel.kt` carries a comment saying
exactly this.

**`sealed class` / `sealed interface` — a closed set of *shapes*, each carrying its own data.**

```kotlin
sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data class Idle(val lastSyncedAtMillis: Long) : SyncStatus
    data object Syncing : SyncStatus
    data class Error(val message: String?) : SyncStatus
}
```

Think of it as an enum where each case can hold different fields. When you `when` over a sealed
type, the compiler knows the full list and forces you to handle every case. This app uses it for
`SyncStatus`, `AuthState`, `HydrateResult`.

**Extension functions — add a function to a type you did not write.**

```kotlin
fun String.shout() = this.uppercase() + "!"
"hi".shout()   // "HI!"
```

You will see these as top-level `fun SomeType.doThing(...)`.

**`suspend`** — a function that can *pause* without freezing the app. Covered properly in
Module 9. For now, when you see `suspend fun`, read it as "this does slow work (database,
network) and can only be called from certain places".

### See it in the real code

Open **`app/src/main/java/com/daybook/app/data/model/DataModel.kt`** (about 343 lines) and read
the whole thing. It is the best Kotlin primer in the repo because it is almost all declarations:

- **Lines ~13–52: `data class Habit`.** Every property is a `val` with a default (`= ...`). Note
  the `@Entity` / `@ColumnInfo` annotations (Module 7) and the nullable types like
  `streakStartedAt: Long? = null`.
- **Lines ~268–312: the enums** — `ColorTag`, `TaskType`, `RedFlag`, `DayOfWeek`, `HabitType` —
  each with its "APPENDED, never reorder" comment and, for some, a `companion object` (a place to
  hang helper/factory functions on the type itself). `HabitType` has four values ending in
  `JOURNAL`; `TaskType` still lists a `JOURNAL` value that is kept only so old stored data
  decodes — it is no longer offered anywhere in the UI (see `FEATURES.md` §5).
- **Lines ~331–343: `object Occurrence` and `object Event`** — using `object` purely as a
  namespace around a nested `enum class` (`Occurrence.Status`, `Event.Action`).

Then open **`app/src/main/java/com/daybook/app/ui/NavConfig.kt`** (about 48 lines). It is a pure
`object` full of small `val`/`fun`, `.split`, `.map`, `.filter`, `.indexOf`. No framework, just
Kotlin. After this module you should understand every line of it.

### Try it

**2a. Read and predict.** In `NavConfig.kt`, find `visibleRoutesFrom(csv: String?)`. On paper,
before running anything, write what it returns for each input: `"routines,home"`, `""`, `null`,
`"home,foodmed,foodmed"`, `"garbage"`. Then check yourself against the code and its comment.

**2b. Run tiny Kotlin.** In Android Studio: *File → New → Scratch File → Kotlin*. Paste and run
(there is a ▶ in the gutter):

```kotlin
data class Habit(val title: String, val archived: Boolean, val snooze: Int = 10)

val habits = listOf(
    Habit("Water", false),
    Habit("Old thing", true, snooze = 30),
    Habit("Walk", false, snooze = 15),
)

val activeTitles = habits.filter { !it.archived }.map { it.title }
val totalSnooze  = habits.sumOf { it.snooze }
val renamed      = habits[0].copy(title = "Drink water")

println(activeTitles)   // [Water, Walk]
println(totalSnooze)    // 55
println(renamed)        // Habit(title=Drink water, archived=false, snooze=10)
```

Change things and re-run until `.filter`, `.map`, `.copy`, default arguments, and string
templates feel boring.

**2c. Break it on purpose.** Add `val x: Int = habits[0].title` and try to run. Read the compile
error. That red underline *is* static typing doing its job.

### Checkpoint

1. What is the difference between `String` and `String?`, and what does `foo?.bar ?: baz` do?
2. Why can you add a value to the end of `enum class TaskType` but never reorder it?
3. In `Column(modifier = ...) { Text("hi") }`, what is the `{ Text("hi") }` part, in terms of
   Kotlin syntax?

---

# MODULE 3 — What an Android app actually *is*

### Concept

**There is no `main()` function in an Android app.** You do not control startup — Android does.
Your app is a bag of *components* that the operating system creates when it needs them:

- **`Activity`** — roughly "one screen host / one window". Old apps had many. A modern app built
  with Compose, like this one, has essentially **one**: `MainActivity`. Everything you see is
  Compose UI drawn *inside* that single Activity, with in-app navigation swapping what is on
  screen (Module 12). An Activity has a **lifecycle** — Android calls `onCreate` when it is
  created, `onResume` when it comes to the foreground, `onStop` when it is hidden — and your code
  hooks those.
- **`BroadcastReceiver`** — a small component the OS wakes up to hand it a single event: "an
  alarm you set went off", "the phone finished booting", "the user tapped Skip on your
  notification". This app has three: `AlarmReceiver`, `BootCompletedReceiver`,
  `NotificationActionReceiver` (Module 13). A receiver gets roughly **10 seconds** to do its work
  before the OS considers it hung.
- **`Application`** — one object created once when your app's process starts, before any Activity.
  A good place for one-time setup. Ours is `DaybookApplication`.
- **`Service`, `ContentProvider`** — this app writes none of its own (it uses a stock
  `FileProvider` to share exported files, and lets libraries register their own).

**`AndroidManifest.xml` is the table of contents.** It is the one file Android reads to learn
what components your app has, what permissions it needs, and which Activity to open when the user
taps the icon (the "launcher" Activity). If a component is not in the manifest, it does not exist
as far as the OS is concerned.

**An APK is a zip.** Rename `app-debug.apk` to `.zip`, unzip it, and you find: compiled code
(`classes.dex` files), resources (`res/`), the manifest in a binary form, and a signature.

**Resources (`res/`)** are the non-code parts of the app, referenced from code through generated
integer IDs on a class called `R`:

- `res/drawable/` — vector icons (`ic_notif_habit.xml`, `ic_skip.xml`), used as `R.drawable.ic_skip`;
- `res/mipmap-*/` — the launcher icon at various screen densities;
- `res/font/` — the bundled typefaces for the in-app font picker;
- `res/values/strings.xml` — user-facing text, used as `R.string.app_name`. This app barely uses
  it: the Compose UI puts text directly in code, so `app_name` (which the manifest needs) is
  almost the only entry. That is unusual for a production app and is a deliberate simplification
  here;
- `res/xml/` — small config files (`file_paths.xml` for the share feature, `backup_rules.xml`).

**`applicationId` vs package name.** The `package com.daybook.app.ui` line at the top of a `.kt`
file is a code-organisation namespace. The **`applicationId`** in `app/build.gradle.kts`
(`com.daybook.app`) is the app's *global identity on the device and on the Play Store*: two APKs
with the same `applicationId` are "the same app" and one updates the other. They happen to share
the text `com.daybook.app` here, which is normal.

**Debug vs release.** Two "build types":

- **debug** — what the ▶ Run button makes. Signed with a throwaway auto-generated key, not
  optimised, larger, and *debuggable* (you can attach the debugger, read verbose logs).
- **release** — what you ship. Signed with *the project's own* key (`keystore.properties` →
  `app/daybook-release.jks`), run through **R8** which shrinks and obfuscates the code
  (`isMinifyEnabled = true`, `isShrinkResources = true`), and not debuggable.

**Signing, and why the key file must never change.** Every APK is cryptographically signed.
Android refuses to install an update unless it is signed with the **same key** as the version
already on the phone — otherwise the user has to uninstall first, losing local data. So
`app/daybook-release.jks` is effectively a permanent identity. **Do not regenerate it, do not
lose it.** Details are in `RELEASE_SIGNING.md`.

### An everyday-terms analogy

Most languages have a project descriptor file — a list of the package's name, its version, and
its "entry points" (which function runs when you start it). `AndroidManifest.xml` plus
`app/build.gradle.kts` together play that role here — except the entry point is not a function
*you* call. It is a component the OS decides to create. You never start "the app"; you declare
the parts, and Android runs the part it needs when it needs it.

### See it in the real code

Open **`app/src/main/AndroidManifest.xml`** (about 90 lines) and read all of it:

- Top: six `<uses-permission>` lines — `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `INTERNET`,
  `ACCESS_NETWORK_STATE`. Each has a comment saying why. Note the comment explaining why it is
  `SCHEDULE_EXACT_ALARM` (which the user can grant) and not `USE_EXACT_ALARM` (which the Play
  Store restricts to alarm-clock apps).
- `<application android:name=".DaybookApplication" ...>` — names our custom `Application` class.
- The single `<activity android:name=".ui.MainActivity" ...>` with:
  - `android:windowSoftInputMode="adjustResize"` — tells Android to *shrink* the app's content
    area when the on-screen keyboard opens (rather than sliding the whole window up). The comment
    explains that this app is edge-to-edge and relies on Compose keyboard-inset handling, and
    that the wrong setting once pushed a screen's header off the top when the keyboard appeared.
  - the `MAIN` / `LAUNCHER` `<intent-filter>` — *this* is what makes tapping the icon open
    `MainActivity`.
- `<provider ... FileProvider ...>` — lets the app hand an exported file to the system share
  sheet without needing a storage permission.
- A second `<provider ... InitializationProvider ...>` with `tools:node="remove"` on the
  WorkManager initializer — the app initialises WorkManager itself (Module 13), so the automatic
  one is switched off.
- Three `<receiver>` entries — `AlarmReceiver` (not exported: only our own alarms trigger it),
  `BootCompletedReceiver` (exported, with an `<intent-filter>` for `BOOT_COMPLETED`,
  `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`, and an exact-alarm-permission-changed
  action), `NotificationActionReceiver` (not exported).

Then open **`app/src/main/res/values/strings.xml`** — just a few lines, with a comment explaining
why it is nearly empty.

### Try it

**3a. Unzip an APK.**

```bash
cd /home/abhiram/Downloads/app-for-food
mkdir -p /tmp/apk-peek && cd /tmp/apk-peek
unzip -o /home/abhiram/Downloads/app-for-food/Daybook-v0.5.6-build20-release.apk >/dev/null
ls -la
```

You will see `classes*.dex` (your compiled code), `res/`, `AndroidManifest.xml` (binary),
`META-INF/` (the signature). That is the whole app.

**3b. Find the launcher declaration.** In `AndroidManifest.xml`, find the `<intent-filter>` that
contains both `android.intent.action.MAIN` and `android.intent.category.LAUNCHER`. In your head,
delete those two lines — what happens? *(The app installs but has no icon in the launcher and
cannot be started normally.)*

**3c. Read `RELEASE_SIGNING.md` end to end.** Then answer: if you build a release APK on a
machine that does *not* have `keystore.properties`, what key gets used, and why would that be a
problem for shipping an update? *(The answer is in that file.)*

### Checkpoint

1. Where does Android look to discover that your app has a `BootCompletedReceiver`?
2. What is the practical difference between the `package` line in a `.kt` file and the
   `applicationId` in `build.gradle.kts`?
3. Why can you never swap in a freshly generated `daybook-release.jks` once the app is on
   someone's phone?

---

# MODULE 4 — Gradle, and building

### Concept

**Gradle is three tools in one:**

1. a **dependency downloader** (like `pip` / `npm`: it fetches the libraries your code uses);
2. a **build recipe engine** (like `make`: it knows the steps to turn source into an APK and
   which steps depend on which);
3. a **task runner** (you invoke named jobs: `assembleDebug`, `testDebugUnitTest`, `clean`).

The build is configured by **Kotlin scripts** — files ending `.gradle.kts`. Yes, the build
config is itself written in Kotlin.

- **`settings.gradle.kts`** (repo root) — names the project ("Daybook"), lists its modules
  (`include(":app")`), and says where to download libraries from (`google()`, `mavenCentral()`).
- **`build.gradle.kts`** (repo root) — the *top-level* build file. It declares which Gradle
  *plugins* exist and at what version (the Android plugin, the Kotlin plugin, the Compose
  compiler plugin, Hilt, kapt, serialization, Google Services, Crashlytics, App Distribution),
  each with `apply false` — meaning "know about these; do not switch them on here".
- **`app/build.gradle.kts`** — the **module** build file, the one you will actually edit. It
  holds `applicationId`, `versionCode`, `versionName`, `minSdk`, `compileSdk`, `targetSdk`, the
  signing config, the `debug` / `release` build types, and — most importantly — the
  **`dependencies { }`** block.

**`dependencies { }`** is the "list of libraries" block. Each line pulls one in:

```kotlin
implementation("androidx.room:room-runtime:2.6.1")   // format is "group:artifact:version"
kapt("androidx.room:room-compiler:2.6.1")             // an annotation processor (see below)
implementation(platform("androidx.compose:compose-bom:2024.12.01"))   // a version bundle ("BOM")
```

`implementation("...")` roughly equals one `pip install` line. `platform(... "-bom")` is a "Bill
of Materials": a single pinned set of mutually-compatible versions for a whole family of
libraries (all the Compose libraries), so the individual Compose lines below it do not each need
a version number.

**The wrapper: `./gradlew`.** You never install Gradle yourself. The repo ships a small script
(`gradlew` for Unix/macOS, `gradlew.bat` for Windows) plus a `gradle/wrapper/` folder that
downloads and runs the *exact* Gradle version this project expects (**8.6**). Always build with
`./gradlew ...`, never a Gradle you installed some other way.

**Why builds are slow.** Three reasons, all present here:

1. **kapt** — "Kotlin Annotation Processing Tool". Room and Hilt work by *reading annotations*
   (`@Entity`, `@Dao`, `@Inject`) at build time and *generating source code* — the actual SQL,
   the dependency-injection wiring. kapt runs an extra compile pass and is the biggest single
   time cost. (There is a newer, faster replacement called **KSP**; this project deliberately
   stays on kapt because the exact versions of Room + Hilt + the Kotlin compiler here are a
   known-good combination — see the comment in the root `build.gradle.kts`.)
2. **R8** — the release-only shrink/obfuscate/optimise pass. Only affects `assembleRelease`.
3. **Cold start** — the first build after opening the project, or after `./gradlew clean`, has no
   caches to reuse.

**The daemon.** Gradle keeps a background process alive between builds so the second build reuses
a warm process. That is why the first terminal build in a session is slow and the next is much
faster.

**Where the built app lands:**

```
app/build/outputs/apk/debug/app-debug.apk        <- ./gradlew assembleDebug
app/build/outputs/apk/release/app-release.apk     <- ./gradlew assembleRelease  (signed if keystore.properties is present)
```

**The environment variables.** Every build command in this project is prefixed with the JDK and
SDK paths:

```bash
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew <tasks>
```

You can `export` them once per terminal session instead of repeating them.

**The "4-gate".** The rule in this project is: *nothing ships unless all four of these pass, run
from a clean state.*

```bash
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

Module 15 explains why all four. There are about **446** JVM unit tests today.

### An everyday-terms analogy

If you have used `pip install -r requirements.txt` followed by `python -m build` followed by
`pytest`, you have done the shape of what Gradle does — just with one tool instead of three, a
config file written in a real programming language, and an extra step (kapt) that reads
annotations in your code and writes new code from them.

### See it in the real code

Open **`app/build.gradle.kts`** and read it top to bottom (about 200 lines):

- `plugins { }` — the same list as the root file, now actually applied.
- `val keystoreProps = Properties().apply { ... }` near the top — plain Kotlin that loads
  `keystore.properties` if the file exists. `hasReleaseSigning` gates the whole signing config;
  if the file is absent the release build falls back to the debug key so it still assembles.
- `android { }`:
  - `namespace`, `compileSdk = 34`.
  - `defaultConfig { applicationId; minSdk = 26; targetSdk = 34; versionCode = 20;
    versionName = "0.5.6" }` — **these are the lines you bump for a release.** The inline
    comments record what each recent build changed.
  - `javaCompileOptions { ... "room.schemaLocation" ... }` — tells Room to *export* the database
    schema as JSON into `app/schemas/` on every build (Module 7 depends on this).
  - `sourceSets { getByName("androidTest") { assets.srcDir("$projectDir/schemas") } }` — ships
    those JSON files into the instrumented-test APK so the migration test can load them.
  - `signingConfigs`, `buildTypes { release { isMinifyEnabled = true; ... proguardFiles(...);
    firebaseAppDistribution { groups = "testers" } } }` — the release type also declares its
    Firebase App Distribution target group (Module 15).
  - `buildFeatures { compose = true; buildConfig = true }` — turns on the Compose compiler and
    generates a `BuildConfig` class (used to stamp `versionName` into exported backup files).
- `dependencies { }` — nearly every line carries a comment explaining *why that exact version*
  and what breaks if you bump it carelessly. Read them; they encode real, hard-won knowledge
  (the `compileSdk 34` ceiling on the Firebase, biometric, credentials, and security libraries).

Also open the root **`build.gradle.kts`** (about 35 lines) and **`gradle.properties`** (JVM heap,
`org.gradle.parallel`, `org.gradle.caching`, kapt worker settings) and
**`gradle/wrapper/gradle-wrapper.properties`** (the pinned Gradle 8.6 download URL).

### Try it

**4a. Time a warm build versus a cold one.**

```bash
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
time ./gradlew assembleDebug     # warm-ish
./gradlew clean
time ./gradlew assembleDebug     # cold — notice how much longer
```

**4b. Read a dependency's justification.** Find the `androidx.biometric:biometric:1.1.0` line in
`app/build.gradle.kts`. Its comment explains three consequences of pinning that exact version.
Put them in your own words. This is the kind of comment you should *write* when you pin
something.

**4c. List the tasks.** Run `./gradlew tasks`. Find `assembleDebug`, `assembleRelease`,
`testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `clean`, `lintVitalRelease`. Those six are
your whole working vocabulary.

### Checkpoint

1. What are the three jobs Gradle does that `pip` alone does not?
2. Which file do you edit to add a new library, and what do the three parts of
   `"androidx.room:room-runtime:2.6.1"` mean?
3. Why is `assembleRelease` slower than `assembleDebug` even on a warm daemon?

---

# MODULE 5 — Jetpack Compose: the screen is a picture of the data

### Concept

The old way to build an Android screen: describe it in an XML file, then in code *find* a widget
by its ID and *change* it (`textView.setText("hi")`). Every change to what is on screen was a
manual poke. Bugs came from the screen and the underlying data drifting apart.

**Jetpack Compose flips this around.** You write a **function** that takes your data and
*describes* what the screen should look like for that data. When the data changes, the framework
**re-runs your function** and updates only the parts that changed. You never hold a reference to a
widget; you never call `setText`. The screen is always `f(current data)`.

A UI function is marked **`@Composable`**:

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hi, $name")
}
```

Calling `Greeting("Alex")` does not *return* anything you use — it *emits* a piece of UI into the
screen being built. Composables call other composables (`Column { Row { Text(...) } }`) to build
the whole screen. Those braces are trailing lambdas (Module 2).

**`Modifier`** is how you attach layout and appearance to a composable — size, padding,
background, click handling — as a chain:

```kotlin
Text(
    "Hi",
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .background(Color.Red)
)
```

Order matters (padding before vs after background gives a different result). **`.dp`** is
"density-independent pixels" — a length unit that looks the same physical size on any screen.

**Recomposition** is the name for "the framework re-ran your composable because something it
reads changed". It is cheap and it happens often. So your composables must be:

- **fast** — no database calls, no heavy work (that is the ViewModel's job, Module 10);
- **free of side effects in the body** — do not start a network request straight from a
  composable; use `LaunchedEffect` for that (Module 6);
- **repeatable** — running twice with the same input gives the same UI.

State (`remember`, `mutableStateOf`) is Module 6. This module is just: *what a composable is*.

### An everyday-terms analogy

The closest picture is a template function that returns HTML:

```
function greetingHtml(name) { return "<p>Hi, " + name + "</p>" }
function pageHtml(user)     { return "<div>" + greetingHtml(user.name) + cardHtml(user) + "</div>" }
```

You compose small functions into big ones, each a pure function of its input. Compose is that,
except (a) it "emits" instead of returning a string, and (b) a runtime watches which inputs each
function read and re-invokes exactly the ones whose inputs changed — you never wire that up.

### See it in the real code

Open **`app/src/main/java/com/daybook/app/ui/onboarding/OnboardingScreen.kt`** and read the whole
file (about 140 lines) — it is the simplest complete screen in the app:

- `@Composable fun OnboardingScreen(onComplete: (name: String) -> Unit, initialName: String = "")`
  — a composable that takes a **callback** (`onComplete`, a function it will call when the user
  is done) and a plain `String` with a default. Screens in this app never touch navigation or the
  database directly — they take callbacks and hand data upward. That is deliberate: it makes them
  easy to test and reuse.
- `var name by rememberSaveable(initialName) { mutableStateOf(initialName) }` — the one piece of
  state (Module 6; for now read it as "a `name` string that, when reassigned, re-runs this
  function").
- `Column(modifier = Modifier.fillMaxSize().background(...).statusBarsPadding()) { ... }` — the
  root layout. Inside, another `Column` with `.weight(1f)` (take all the remaining vertical
  space) and `.verticalScroll(...)`.
- `DaybookTextField(value = name, onValueChange = { name = it }, ...)` — the classic Compose
  pattern: the text field is *told* its current value (`value = name`) and *reports* changes back
  (`onValueChange = { name = it }`). The field does not hold its own text; `name` does. This is
  called "state hoisting".
- `StickySaveBar { PrimaryButton(text = "Get started", onClick = { onComplete(name) }, enabled =
  name.isBlank().not()) }` — a bar pinned to the bottom; the button is disabled until `name` is
  non-blank, and on click it calls the callback with the current `name`.
- `@Composable private fun FeatureCard(...)` at the bottom — a small private composable reused
  three times.

Now open **`app/src/main/java/com/daybook/app/ui/components/Components.kt`** and find the
**`SoftCard`** composable — the single card primitive every screen uses. Read its body:

- It takes a card tint (a colour bundle), an optional `onClick`, and
  `content: @Composable ColumnScope.() -> Unit` (the trailing lambda — the card's insides).
- It tracks whether the card is currently pressed and animates a slight shrink
  (`animateFloatAsState`) while it is.
- The comment on the `Modifier.graphicsLayer { ... }` line matters: that modifier is applied
  **unconditionally** (never inside an `if`), because a conditional modifier chain rebuilds
  twice per tap. Leave that alone — it is in the footgun list.

### Try it

**5a. C2 — change a visible string and see it.** Open
`app/src/main/java/com/daybook/app/ui/settings/SettingsScreen.kt`. Find `title = "App lock"` (it
is one of the settings-hub rows, around line 258). Change it to `"App lock 🔒"`. Rebuild and run
(press ▶, or `./gradlew installDebug` with a device attached), open Settings in the app, and see
your change. **That is C2.** Revert it afterwards (`git restore` the file).

**5b. Watch recomposition happen.** In `SoftCard` (in `Components.kt`), just inside the function
body, add:

```kotlin
android.util.Log.d("SoftCardTrace", "recompose")
```

Rebuild, run, open a screen full of cards (the Habits tab), press and release one while watching
**Logcat** (bottom of Android Studio; filter to `SoftCardTrace`). You will see it fire many times
as the press animation runs — that is recomposition, live. **Remove the log line when done.**

**5c. Add a cell to a row.** In `OnboardingScreen.kt` the little colour strip near the top builds
from a `listOf(...)` of card tints. Add one more tint to that list. Rebuild. The strip now has one
more cell, each still sharing the width. Revert.

### Checkpoint

1. When the data behind a screen changes, what does Compose do — and what do you *not* have to
   do?
2. In `DaybookTextField(value = name, onValueChange = { name = it })`, where does the typed text
   actually live?
3. Why must a `@Composable` function body not run a database query?

---

# MODULE 6 — State, `remember`, and recomposition

### Concept

A composable re-runs (recomposes) when a **state** value it *reads* changes. So you need a way to
make state the runtime can track.

- **`mutableStateOf(x)`** — creates an observable holder starting at `x`. Reading it inside a
  composable *subscribes* that composable to changes; writing it triggers recomposition of every
  reader.
- **`remember { ... }`** — "run this block once, the first time this composable runs, and give me
  back the same result on every later recomposition." Without `remember`, you would rebuild the
  state holder on every recomposition and it would never appear to change. So the idiom is always
  `remember { mutableStateOf(x) }`.
- **`by`** — a small Kotlin convenience ("property delegation") that lets you write
  `var name by remember { mutableStateOf("") }` and then use `name` directly for both reading and
  assigning, instead of `name.value`. Pure sugar.
- **`rememberSaveable`** — like `remember`, but the value also survives the Activity being
  destroyed and recreated (screen rotation, or the OS killing the app in the background to save
  memory). Use it for real user input you would hate to lose; plain `remember` for throwaway UI
  state ("is this menu open right now").
- **`LaunchedEffect(key) { ... }`** — "when this composable first appears (or when `key`
  changes), run this suspend block in a coroutine tied to the composable's lifetime." This is
  where you *start* things: a one-off load, an animation, reacting to a new argument. It is the
  sanctioned escape hatch from the "no side effects in the body" rule.
- **`derivedStateOf { ... }`** — a computed state that only notifies its readers when its
  *result* changes, not every time an input twitches. `MainActivity` uses it to read the swipe
  pager's settled page without recomposing on every drag frame.

**Rules of thumb:**

- Keep state as *low* in the tree as possible — only the part that needs it recomposes.
- "Hoist" state up only when two sibling composables must share it, or a parent must control it.
- Never change a list in place and expect a recomposition — assign a *new* list.

### An everyday-terms analogy

Imagine an object whose "set value" method automatically calls every function that ever read it:

```
class State:
    def __init__(self, v): self._v = v; self._readers = set()
    def get(self):        register_current_function_as_reader(self); return self._v
    def set(self, v):     self._v = v; [schedule_rerun(r) for r in self._readers]
```

`remember { }` is "make this once and keep it, do not rebuild it each render". `LaunchedEffect`
is "start this background job when the widget appears, cancel it when the widget goes away".

### See it in the real code

**`app/src/main/java/com/daybook/app/ui/MainActivity.kt`**, the `setContent { ... }` block
(roughly lines 150–320):

- `var showRationale by remember { mutableStateOf(false) }` and friends — plain throwaway UI
  flags for the permission dialogs.
- `val notifPermLauncher = rememberLauncherForActivityResult(...) { granted -> ... }` — a
  remembered handle for "ask the OS for a permission and get the answer back in this lambda".
- `LaunchedEffect(Unit) { ... }` — runs **once** when the UI first appears (`Unit` never
  changes). It checks whether the notification permission is granted and decides whether to
  prompt.
- A second `LaunchedEffect(notifFlowResolved) { ... }` — runs again **each time
  `notifFlowResolved` flips**, sequencing the exact-alarm permission request *after* the
  notification decision so two system dialogs never stack.
- Further down, inside `MainApp()`:
  `val settledPage by remember { derivedStateOf { pagerState.currentPage } }` — the comment
  explains this stops the scaffold recomposing on every drag frame; it only recomposes when the
  page *settles*.

Also re-open **`OnboardingScreen.kt`**:
`var name by rememberSaveable(initialName) { mutableStateOf(initialName) }` — `rememberSaveable`
so a rotation mid-typing does not wipe the name; keyed on `initialName` so if the caller passes a
new seed the field resets.

### Try it

**6a. Add a throwaway counter to a screen.** In `SettingsScreen.kt`, inside the top-level
`SettingsScreen` composable body, add:

```kotlin
var taps by remember { mutableStateOf(0) }
```

and somewhere visible in its content add:

```kotlin
androidx.compose.material3.Text(
    "debug taps: $taps",
    modifier = androidx.compose.foundation.clickable(onClick = { taps++ })
)
```

Rebuild, open Settings, tap the text, watch the number climb — `mutableStateOf` plus
recomposition. Now rotate the phone: the count resets to 0 (plain `remember`). Change `remember`
to `rememberSaveable`, rebuild, rotate again: it survives. **Revert both edits.**

**6b. Prove `remember` is doing something.** Temporarily change it to
`val taps = mutableStateOf(0)` (no `remember`), and use `taps.value++` in the click. It ticks up
once and then appears stuck — every recomposition rebuilds a fresh `mutableStateOf(0)`. Put
`remember` back.

**6c. Read a `LaunchedEffect` for real.** In `MainActivity.kt`, find the `LaunchedEffect(Unit)`
and the `LaunchedEffect(notifFlowResolved)`. Write one sentence for each: *when does this block
run, and why is that the right key?*

### Checkpoint

1. Why is it always `remember { mutableStateOf(...) }` and never just `mutableStateOf(...)` in a
   composable?
2. When would you choose `rememberSaveable` over `remember`?
3. What does `LaunchedEffect(x) { ... }` do that putting the same code straight in the composable
   body does not?

---

# MODULE 7 — Room: Kotlin classes that are database tables

### Concept

**Room** is a library that turns annotated Kotlin classes into **SQLite** tables and gives you
type-checked methods to read and write them. SQLite is a small database engine built into
Android; every app gets its own private database file. Think of Room as "a dictionary that
survives the app being killed and restarted", with real tables and real queries underneath.

Three pieces:

- **`@Entity data class`** — one class equals one table. Each `val` is one column, tagged with
  `@ColumnInfo(name = "...")`. `@PrimaryKey` marks the unique-ID column. `@Index` adds a
  database index (makes some queries fast; costs a little on every write).
- **`@Dao interface`** ("Data Access Object") — an interface whose methods are each annotated
  with `@Query("SELECT ...")`, `@Insert`, `@Update`, or `@Delete`. Room *generates the
  implementation* at build time (this is the kapt step). **The SQL lives here.**
- **`@Database abstract class`** — lists every entity, declares a `version` number, and exposes
  the DAOs. There is exactly one: `AppDatabase`, currently **`version = 19`**.

**This app's tables** (all defined in `data/model/DataModel.kt`):

| Table | What it holds |
|---|---|
| `habits` | one row per habit — title, times, active weekdays, type, icon, tint, snooze, streak fields, per-habit journal questions |
| `habit_occurrences` | one row per *scheduled instance* of a habit (a specific date+time slot), each with a `status` (`PENDING` / `COMPLETED` / `SKIPPED` / `LOGGED`) |
| `habit_events` | an append-only log: `SHOWN` / `USER_SNOOZED` / `COMPLETED` / `SKIPPED` / `REPLIED`, each with a timestamp |
| `food_med_tasks` | one row per intake reminder (Food / Med / Custom), plus red-flag defaults, prompt text, motivation note |
| `food_med_occurrences` | scheduled instances of an intake reminder; carry the reply text, red-flag marker, etc. |
| `food_med_events` | the same append-only log shape as `habit_events` |
| `app_settings` | **a single row** (`id = 1`) holding every device-local preference |
| `custom_categories` | reusable Custom-category names; the primary key *is* the name (so it self-deduplicates) |
| `custom_prompts` | reusable prompt messages; same trick |

The **habit → occurrence → event** three-table shape is the heart of the app. A habit is the
*rule*. An occurrence is *one time that rule fired (or should have)*. An event is *a thing that
happened to an occurrence*. The intake side mirrors it exactly on purpose — same shape means
export/import and stats are shared code, not two parallel systems.

**Why occurrences are stored rows and not computed on the fly:** so history is real, so a
notification has a concrete thing to attach to, and so "you completed this at 8:03 on the 14th"
is a fact in the database, not something re-derived that could change if you edit the schedule
later.

### The frozen-schema rule — the single most important thing in this module

**Once a database schema version has *shipped* to a user, it is frozen forever.** The app on that
user's phone has real rows in the old shape. You cannot just change an `@Entity` and rebuild —
Room would open the old data against the new expectation and either crash or, with the
destructive fallback, *wipe it*.

To change the schema you do **all** of this, in order:

1. **Change the `@Entity`** (add a column / index / table) in `data/model/DataModel.kt`.
2. **Write a migration** in `data/local/Migrations.kt` — an `object : Migration(18, 19)` with an
   `override fun migrate(db)` containing the exact SQL (`ALTER TABLE ... ADD COLUMN ...`,
   `CREATE INDEX ...`, or a "create new table, copy, drop, rename" rebuild for a column
   *removal*, since SQLite here has no simple `DROP COLUMN`).
3. **Bump `@Database(version = ...)`** in `data/local/AppDatabase.kt`.
4. **Register the migration** — add it to the `.addMigrations(...)` chain in
   `di/DatabaseModule.kt`.
5. **Build.** Because `exportSchema = true` and the schema-location option are set, the build
   writes `app/schemas/com.daybook.app.data.local.AppDatabase/<n>.json` — the canonical
   description of the new schema, including an `identityHash`.
6. **Commit that `<n>.json`.** It is a source file, not a build artifact.
7. **Add a migration test case** in `app/src/androidTest/.../MigrationTest.kt` — create the DB at
   the old version, run the migration, assert the new column/table exists and old data survived.

**`identityHash`.** Room computes a hash of the schema and stores it in the database. On open it
compares. If your hand-written migration SQL does not produce *exactly* the schema Room expects
(a different column order, a missing `DEFAULT`, an index name that does not match Room's
`index_<table>_<colA>_<colB>` convention), the hashes disagree and Room throws
`IllegalStateException: Migration didn't properly handle ...`. That is why migration comments in
this repo obsessively say "byte-matches `<n>.json`".

**Run the migration gate on a `clean` build.** An incremental build can leave a stale `<n>.json`
with the wrong content, and your test then passes against a lie. This has bitten the project
before.

**The escape hatches** in `DatabaseModule.kt` — `fallbackToDestructiveMigrationFrom(1)` (there
was never a shippable v1, so a stored v1 DB is wiped rather than crashing) and
`fallbackToDestructiveMigrationOnDowngrade()` (if an older build somehow runs over a newer DB,
wipe rather than crash) — are for impossible-in-practice cases. They are **not** permission to
skip writing a migration.

### An everyday-terms analogy

If you have used a database migration tool like Alembic or Django's migrations: Room migrations
are that, done by hand. You write the raw `ALTER TABLE` SQL yourself, there is no automatic
"downgrade", and there is a hash check that fails loudly if your SQL and the ORM's model of the
new schema disagree even slightly.

### See it in the real code

- **`app/src/main/java/com/daybook/app/data/model/DataModel.kt`** — re-read it with "these are
  tables" in mind. Note `@ColumnInfo(name = "type", defaultValue = "INDIVIDUAL")` (a
  *schema-level* default, written into the SQL) versus `val streakStartedAt: Long? = null`
  (nullable, no schema default — the `= null` is only a Kotlin default). That distinction matters
  for migrations and for sync (Module 14).
- **`app/src/main/java/com/daybook/app/data/local/AppDatabase.kt`** — the `@Database(entities =
  [...], version = 19, exportSchema = true)` line and one `abstract fun` per DAO.
- **`app/src/main/java/com/daybook/app/data/local/HabitDao.kt`** — a real DAO. See
  `@Query("SELECT * FROM habits WHERE is_archived = 0 ...")`, `@Insert`, `@Update`, and targeted
  single-column updates like
  `@Query("UPDATE habits SET prompt_message = :v WHERE id = :id") suspend fun updatePromptMessage(id: String, v: String?)`.
  Some methods return `Flow<...>` (Module 9), some are `suspend`, some are plain.
- **`app/src/main/java/com/daybook/app/data/local/Migrations.kt`** — read it start to finish. It
  has seventeen worked examples, `MIGRATION_2_3` through `MIGRATION_18_19`:
  - `MIGRATION_2_3`, `MIGRATION_3_4` — the simplest: one `ALTER TABLE ... ADD COLUMN ... NOT NULL
    DEFAULT ...`.
  - `MIGRATION_4_5`, `MIGRATION_11_12` — the "create new table, copy rows, drop old, rename"
    rebuild used to *remove* columns.
  - `MIGRATION_12_13` — the big one: four `CREATE INDEX` (names matching Room's convention), two
    `ADD COLUMN` plus a data backfill, and a long comment about cost at scale.
  - `MIGRATION_15_16` — twenty `ALTER TABLE app_settings ADD COLUMN` (each `DEFAULT`
    byte-matching a `@ColumnInfo(defaultValue = ...)` in `DataModel.kt`) plus three nullable
    columns on other tables.
  - `MIGRATION_16_17` — additive columns **plus** a scoped data deletion (removes retired
    Journal-type intake rows and their children) **plus** `DROP TABLE IF EXISTS journal_questions`.
  - `MIGRATION_17_18` — two `ALTER TABLE app_settings ADD COLUMN` for a short-lived per-section
    accent feature. **Those two columns (`habits_accent_color`, `intake_accent_color`) are now
    dead** — the feature was reverted in the next round, but dropping columns is risky, so they
    were left in place, unread and unwritten.
  - `MIGRATION_18_19` (the newest) — a single additive column:
    `ALTER TABLE app_settings ADD COLUMN check_for_updates_enabled INTEGER NOT NULL DEFAULT 1`,
    the flag behind the "Check for updates" setting (Module 15).
- **`app/schemas/com.daybook.app.data.local.AppDatabase/`** — `3.json` through `19.json`. Open
  `19.json`: it is the *generated* description of the current schema — every table's `createSql`,
  every column, every index, plus the `identityHash`. You never edit this file; the build writes
  it.
- **`app/src/androidTest/java/com/daybook/app/data/local/MigrationTest.kt`** — the pattern:
  `helper.createDatabase(TEST_DB, 18).close()` then
  `helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)` then a `SELECT` to assert
  the new column is present. `runMigrationsAndValidate` is what checks the `identityHash`.

### Try it

**7a. Inspect the real database from a running app.** With the app running from Android Studio,
open *App Inspection* (bottom toolbar) → *Database Inspector* → pick `daybook_database`. Create a
habit in the app, watch a row appear in `habits`. Trigger a reminder, watch `habit_occurrences`
and `habit_events` fill in.

**7b. Trace one habit through three tables.** In the Database Inspector, run:

```sql
SELECT id, title, type FROM habits;
SELECT id, habit_id, scheduled_for, status FROM habit_occurrences ORDER BY scheduled_for LIMIT 20;
SELECT occurrence_id, action, timestamp FROM habit_events ORDER BY timestamp DESC LIMIT 20;
```

Confirm you can see a rule, its scheduled instances, and the log of what happened to them.

**7c. Read the schema diff.** In a terminal:

```bash
cd /home/abhiram/Downloads/app-for-food
git diff --no-index app/schemas/com.daybook.app.data.local.AppDatabase/18.json \
                    app/schemas/com.daybook.app.data.local.AppDatabase/19.json
```

The only difference should be the one `check_for_updates_enabled` column on `app_settings`. That
one-to-one match between a migration's `ADD COLUMN` and the schema JSON diff is the thing you are
maintaining whenever you touch the database.

### Checkpoint

1. What is the difference in responsibility between the `habits` table and the
   `habit_occurrences` table?
2. Where is the SQL for "get all non-archived habits" actually written?
3. List the seven steps to add a column to a table safely, and say what `identityHash` is.

---

# MODULE 8 — The DAO / Repository split

### Concept

The DAO is the raw database interface. But the rest of the app — ViewModels, the scheduler, the
sync engine — does **not** talk to DAOs directly. It talks to a **Repository**.

A repository is a plain `@Singleton class` that wraps one or more DAOs and exposes
app-meaningful operations. It is a boring middle layer, and that is the point:

- It is the **one place** to put a piece of logic like "get active habits" so it is not
  copy-pasted into ten screens.
- It is the **seam** for swapping the data source. The whole plan for "add cloud sync later
  without rewriting the app" rests on this: a ViewModel calls
  `habitRepository.observeActiveHabits()`, and whether that is backed by Room alone, or Room plus
  a sync layer, is invisible to it.
- It keeps Room's types from leaking up into the UI code.

In this app the repositories are thin — they mostly forward to a DAO. That is fine; a thin,
consistent layer still buys you the seam and the single home for each operation.

### An everyday-terms analogy

```
class HabitRepository:
    def __init__(self, db): self._db = db
    def active_habits(self):   return self._db.query("SELECT * FROM habits WHERE is_archived = 0")
    def archive(self, id):     self._db.exec("UPDATE habits SET is_archived = 1 WHERE id = ?", id)

# the rest of the program uses HabitRepository and never touches `db` directly
```

The "never touches `db` directly" convention is the whole value.

### See it in the real code

**`app/src/main/java/com/daybook/app/data/HabitRepository.kt`** — about 65 lines, read all of it:

- `@Singleton class HabitRepository @Inject constructor(val database: AppDatabase)` — Hilt
  (Module 11) supplies the `AppDatabase`.
- `suspend fun getActiveHabits(): List<Habit> = database.habitDao().getActiveHabits().first()` —
  forwards to the DAO, turning a live `Flow` into a one-shot list with `.first()`.
- `fun observeActiveHabits() = database.habitDao().getActiveHabits()` — exposes the live `Flow`
  for the UI.
- `suspend fun archiveHabit(id: String) = database.habitDao().archiveHabit(id)` — a one-liner.
- `suspend fun markStreakBroken(id: String, ...)` — the one method with real logic: read the
  habit, compute the run length, write `max(longest, run)` and clear the start date. This kind of
  small computation belongs in a repository, not in a DAO and not in a ViewModel.

**`app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt`** — note the pattern: an
`ensureRow()` call followed by a targeted single-column DAO update, one method per setting
(`setWeekStart`, `setClock24h`, ...), plus `observeSettings(): Flow<AppSettings>` for the
reactive read. When you add a setting in the "make your first change" walkthrough, you will add a
method here.

**`app/src/main/java/com/daybook/app/di/DatabaseModule.kt`** — see how repositories are *provided*
to the rest of the app: `@Provides @Singleton fun provideHabitRepository(database: AppDatabase) =
HabitRepository(database)` (Module 11).

### Try it

**8a. Follow a write end to end.** In Android Studio, search the whole project (Edit → Find →
Find in Files) for `archiveHabit`. You will find the chain:
`RoutinesViewModel.archiveHabit` → `habitRepository.archiveHabit(id)` → `HabitDao.archiveHabit`
(`@Query("UPDATE habits SET is_archived = 1 ...")`). Three hops, each layer's job visible.

**8b. Confirm the seam.** Open any ViewModel (e.g. `RoutinesViewModel.kt`) and check it imports
`HabitRepository`, not `HabitDao` or `AppDatabase`. Then search the whole `ui/` folder for
`habitDao(` — you should find nothing. That "nothing" is the architecture working.

**8c. Add a read-only repository method.** In `HabitRepository.kt`, add:

```kotlin
suspend fun countActiveHabits(): Int = database.habitDao().getActiveHabits().first().size
```

It compiles with no other change. You will not call it — the point is to feel how small the
surface is. Keep it or revert; harmless.

### Checkpoint

1. Why do ViewModels not call DAOs directly?
2. Where would you add "give me the 3 habits with the longest current streak" — the DAO, the
   repository, or the ViewModel — and why?
3. What does `.first()` do to a `Flow`?

---

# MODULE 9 — Coroutines, `suspend`, and `Flow`

### Concept

Two intertwined ideas: **coroutines** (how the app does slow work without freezing) and **Flow**
(how the app receives a *stream* of values over time).

**Coroutines / `suspend`.** The screen is drawn by one special thread called the "main thread".
If you do a database read or a network call on the main thread, the screen freezes until it
finishes — and if it takes too long, Android shows an "App Not Responding" dialog and may kill
you. A **`suspend fun`** is a function that can *pause* at certain points (a database read, a
delay, a network round-trip), let the main thread go do other things, and *resume* when the
result is ready — without blocking anything.

- You can only call a `suspend fun` from another `suspend fun`, or from a **coroutine** started
  with `launch { }` / `viewModelScope.launch { }` / `LaunchedEffect { }`.
- `withContext(Dispatchers.IO) { ... }` means "do this part on a background thread pool, then
  come back to where you were".
- Coroutines have **structured concurrency**: every coroutine belongs to a *scope*
  (`viewModelScope`, an Activity's `lifecycleScope`), and when that scope ends, its coroutines
  are cancelled automatically. Nothing leaks.

**Flow.** A `Flow<T>` is "a stream that will emit zero or more values of type `T` over time". A
**cold** flow does nothing until something *collects* it.

- Room DAO methods that return `Flow<List<Habit>>` **re-emit a fresh list every time the
  underlying table changes.** This is the magic that keeps the UI live: you query once, and you
  get updates forever.
- **`StateFlow<T>`** is a flow that *always has a current value* and only emits when it changes.
  It is the standard type a ViewModel exposes to a screen. `.value` reads the current one.
- Common operators: `.map { }`, `.filter { }`, `.combine(other) { a, b -> }`, `.debounce(ms)`
  (wait for a quiet gap before emitting), `.distinctUntilChanged()`, `.stateIn(scope, ...)` (turn
  a cold flow into a hot `StateFlow`), `.first()` (collect exactly one value and stop).

### An everyday-terms analogy

`suspend` / `launch` / `withContext` map closely onto `async` / `await` / "run this on a worker"
in other languages. A `Flow` is like a generator that keeps yielding new values forever, pushing
the latest to whoever is looping over it. A `StateFlow` is that generator plus "and it remembers
the last value it yielded, so a new listener gets it immediately".

### See it in the real code

**`app/src/main/java/com/daybook/app/data/OccurrenceScheduler.kt`** — a coroutine tour:

- Every public method is a `suspend fun` (`syncTask`, `syncHabit`, `completeHabit`,
  `snoozeFoodMed`, ...) because they all touch the database.
- `syncMutex = Mutex()` with `syncMutex.withLock { ... }` — a coroutine-friendly lock so two
  reminder actions cannot corrupt each other's row rewrites.
- `db.withTransaction { ... }` — a suspend function that runs the block as one atomic database
  transaction.

**`app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt`** —
`fun observeSettings(): Flow<AppSettings> = database.appSettingsDao().observeSettings().map { it ?: AppSettings() }`
— a DAO flow with a `.map` layered on to substitute a default row when the table is empty.

**`app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt`** — the deep end; skim it:

- `val greeting: StateFlow<String> = ...` — built by `.map`-ping the settings flow and a
  time-tick flow together, then `.stateIn(...)` to make it a `StateFlow` the screen can read.
- A comment noting there is *one* upstream `app_settings` subscription, shared — they `.combine`
  once rather than subscribe five times.
- `.distinctUntilChanged()` on a derived flow so the screen does not recompose when an unrelated
  setting changes.

**`app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt`** — a flow of "something
changed" events with `.debounce(...)` so a burst of edits produces *one* cloud push a few seconds
after the last edit, not one per keystroke.

### Try it

**9a. Run a coroutine in a scratch file.**

```kotlin
import kotlinx.coroutines.*

fun main() = runBlocking {
    println("start on ${Thread.currentThread().name}")
    val result = withContext(Dispatchers.Default) {
        Thread.sleep(500)   // pretend slow work
        6 * 7
    }
    println("got $result")
    launch { delay(200); println("from a child coroutine") }
    println("end of main body")
}
```

Run it. Note the print order: `end of main body` comes before `from a child coroutine`, because
`launch` schedules and returns immediately, and `runBlocking` waits for its children before
exiting.

**9b. Watch a Room flow re-emit.** In `HomeViewModel.kt` (or `RoutinesViewModel.kt`), find where
a flow from a DAO is collected, and add
`.onEach { android.util.Log.d("FlowTrace", "emit: ${it.size} items") }` into that chain. Rebuild,
run, add and delete a habit while watching Logcat filtered to `FlowTrace` — every database change
pushes a new emission. Remove the line.

**9c. Explain the debounce.** In `CloudSyncRepository`, find the `.debounce(...)` on the changes
flow. In your own words: what would go wrong if it collected with *no* debounce and the user
edited five fields of a habit quickly?

### Checkpoint

1. What does `suspend` let a function do that a normal function cannot, and where can you call
   one from?
2. What makes a Room `Flow<List<Habit>>` different from a one-shot `List<Habit>`?
3. Why does `HomeViewModel` expose `StateFlow`s and not plain `Flow`s to the screen?

---

# MODULE 10 — ViewModel: the brain of one screen

### Concept

A **ViewModel** is a class that holds one screen's data and logic. It is *not* UI. Its jobs:

- Own the screen's state as `StateFlow`s (usually built from repository flows).
- Expose functions the screen calls on user actions (`archiveHabit(id)`, `toggleFilter(f)`).
- Do that work in `viewModelScope.launch { }` — a coroutine scope tied to the screen's life.

Its superpower: **it survives configuration changes.** When you rotate the phone, the Activity is
destroyed and recreated and every composable re-runs from scratch — but the *same ViewModel
instance* is handed back. In-flight loads and current state are not lost.

A screen gets its ViewModel with `hiltViewModel()` (this is a Hilt app; Module 11). The screen
then reads the ViewModel's `StateFlow`s with `collectAsState()` and calls its functions.

Rule of thumb: **if it is not "which pixel goes where", it belongs in the ViewModel or below.**
Deciding *which* habits to show is ViewModel work. Computing a streak is a pure function the
ViewModel calls. Formatting a date for display is borderline.

### An everyday-terms analogy

```
class RoutinesScreenModel:
    def __init__(self, habit_repo):
        self._repo = habit_repo
        self.habits = []
        self.show_archived = False
    def toggle_archived(self):
        self.show_archived = not self.show_archived
        self.habits = self._repo.active_habits()
```

A ViewModel is that object, plus: it is created for you and cached across "re-renders", its
fields are reactive `StateFlow`s so the screen updates automatically, and its methods run on
coroutines.

### See it in the real code

Open **`app/src/main/java/com/daybook/app/ui/routines/RoutinesViewModel.kt`** and read it fully:

- `@HiltViewModel class RoutinesViewModel @Inject constructor(private val habitRepository:
  HabitRepository, private val scheduler: OccurrenceScheduler, ...)` — Hilt injects the
  dependencies (Module 11).
- Private `MutableStateFlow`s with public read-only `StateFlow` mirrors (`_showArchived` /
  `showArchived`) — the "expose read-only, change privately" pattern.
- `val habits: StateFlow<List<...>> = combine(habitRepository.observeAllHabits(), _showArchived,
  _sort) { ... }.stateIn(viewModelScope, ...)` — the screen's list, derived reactively from a
  repository flow plus UI state.
- `fun archiveHabit(id: String) = viewModelScope.launch { habitRepository.archiveHabit(id);
  scheduler.syncHabit(id) }` — a user action: launch a coroutine, do the write, re-sync the
  alarms.

Then see the screen consume it — **`RoutinesScreen.kt`** near the top:
`val viewModel: RoutinesViewModel = hiltViewModel()`, then
`val habits by viewModel.habits.collectAsState()`, and buttons that call `viewModel::archiveHabit`.

### Try it

**10a. Add a derived StateFlow and show it.** In `RoutinesViewModel.kt`, add:

```kotlin
val activeCount: kotlinx.coroutines.flow.StateFlow<Int> =
    habitRepository.observeActiveHabits()
        .map { it.size }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0)
```

In `RoutinesScreen.kt`, near the header, add
`val activeCount by viewModel.activeCount.collectAsState()` and a `Text("active: $activeCount")`.
Rebuild, run, add and archive habits, watch it update live with no manual refresh. **Revert.**

**10b. Prove it survives rotation.** With 10a still in place, add a habit, then rotate the phone.
The count is still correct instantly — the ViewModel was not recreated.

**10c. Confirm the boundary.** Search `RoutinesViewModel.kt` for `import androidx.compose` — there
should be essentially nothing. A ViewModel importing Compose UI types is a code smell.

### Checkpoint

1. What survives a screen rotation — the composables, the ViewModel, or both?
2. Why does a ViewModel expose a read-only `StateFlow` but keep a `MutableStateFlow` private?
3. Where does the coroutine that runs `archiveHabit`'s database write come from, and when is it
   cancelled?

---

# MODULE 11 — Hilt and dependency injection

### Concept

`HabitRepository` needs an `AppDatabase`. `RoutinesViewModel` needs a `HabitRepository` and an
`OccurrenceScheduler`. `OccurrenceScheduler` needs the database, notification helpers, an ID
sequence, and more. `CloudSyncRepository` needs about seven things. If you built all that by
hand, every file that used a ViewModel would contain a paragraph of "make this, then make that
with the first thing, keep them all as singletons, in the right order".

**Dependency injection** means having a framework do that construction and wiring for you.
**Hilt** is that framework (it is built on an older one called Dagger). You annotate:

- `@Inject constructor(...)` on a class — "here is what I need; build me".
- `@Singleton` — "only ever make one of these".
- `@HiltViewModel` on a ViewModel — "Hilt makes these; screens ask for one via `hiltViewModel()`".
- `@Module` + `@Provides` — for things Hilt cannot just construct itself (interfaces, third-party
  classes, anything needing configuration): a function that returns one. See `DatabaseModule`
  providing `AppDatabase` (which needs `Room.databaseBuilder(...)` and the migration list) and
  the repositories; `FirebaseModule` providing `FirebaseAuth`, `FirebaseFirestore`,
  `CredentialManager`.
- `@HiltAndroidApp` on the `Application`; `@AndroidEntryPoint` on the Activity and each
  `BroadcastReceiver` — the entry points where Hilt is allowed to inject.

At build time (kapt again) Hilt generates all the wiring. At runtime, a field like
`@Inject lateinit var scheduler: OccurrenceScheduler` in a receiver just *appears*, fully
constructed with its whole dependency tree, before the receiver's `onReceive` runs.

**One real subtlety here:** `OccurrenceScheduler` takes `javax.inject.Provider<CloudSyncRepository>`,
not `CloudSyncRepository` directly, because `CloudSyncRepository` in turn depends on
`OccurrenceScheduler` — a cycle. A `Provider<T>` means "give me a `T` *when I ask*, later", which
breaks the cycle. You will meet this pattern if you ever add a dependency that loops.

### An everyday-terms analogy

Manual DI in plain code is just passing arguments:

```
db          = Database()
habit_repo  = HabitRepository(db)
scheduler   = OccurrenceScheduler(db, NotificationUtils(), NotificationIdSequence())
vm          = RoutinesScreenModel(habit_repo, scheduler)
```

Hilt is a framework that reads the type annotations and does exactly this — in the right order,
caching the singletons, for the whole app — so no file has to contain that boilerplate. The cost
is a layer of "magic" and slower builds (kapt).

### See it in the real code

- **`app/src/main/java/com/daybook/app/di/DatabaseModule.kt`** — read all of it. `@Module
  @InstallIn(SingletonComponent::class) object DatabaseModule`, then
  `@Provides @Singleton fun provideDatabase(...) = Room.databaseBuilder(...)
  .addMigrations(MIGRATION_2_3, ..., MIGRATION_18_19)
  .fallbackToDestructiveMigrationFrom(1).fallbackToDestructiveMigrationOnDowngrade().build()` —
  **this is where the migration list lives** — followed by one `@Provides` per repository.
- **`app/src/main/java/com/daybook/app/di/FirebaseModule.kt`** — provides `FirebaseAuth`,
  `FirebaseFirestore`, `CredentialManager`. These are classes Hilt cannot construct directly, so
  a `@Provides` function does it.
- **`app/src/main/java/com/daybook/app/DaybookApplication.kt`** —
  `@HiltAndroidApp class DaybookApplication : Application(), Configuration.Provider`, with
  `@Inject lateinit var` fields for `notificationUtils`, `workerFactory`, `cloudSyncRepository`,
  and an `onCreate()` that installs a crash handler, creates notification channels, enqueues the
  daily window-refresh worker, and calls `cloudSyncRepository.start()`.
- **`app/src/main/java/com/daybook/app/util/alarm/AlarmReceiver.kt`** — top:
  `@AndroidEntryPoint class AlarmReceiver : BroadcastReceiver()` with
  `@Inject lateinit var db`, `notificationUtils`, `scheduler`. The OS constructs the receiver;
  Hilt fills those fields before `onReceive` runs.

### Try it

**11a. Trace a dependency tree on paper.** Start from `@AndroidEntryPoint class
NotificationActionReceiver` and its `@Inject lateinit var scheduler: OccurrenceScheduler`. Using
the `OccurrenceScheduler` constructor, write out every object Hilt must build to satisfy that one
field. That tree is what `@Inject` saved you from typing.

**11b. Add a `@Provides` and inject it.** In `DatabaseModule.kt` add:

```kotlin
@Provides @Singleton
fun provideBuildStamp(): String = "handover-course-build"
```

In any ViewModel constructor, add a parameter `private val buildStamp: String` and log it in an
`init { }` block. Rebuild, run, see it in Logcat. This shows how little ceremony adding an
injectable thing takes. **Revert both.**

**11c. Cause and read a Hilt error.** Temporarily give a ViewModel constructor a parameter of a
type nothing provides, e.g. `private val nope: java.io.File`. Build. Read the error — Hilt tells
you, at compile time, exactly which type has no binding and where it was needed. Undo.

### Checkpoint

1. What does `@Inject constructor(...)` tell Hilt to do?
2. When do you need a `@Provides` function instead of just `@Inject`-ing a class?
3. Why does `OccurrenceScheduler` take `Provider<CloudSyncRepository>` instead of
   `CloudSyncRepository`?

---

# MODULE 12 — Navigation between screens

### Concept

This app has **one Activity** and does all screen changes with **Navigation-Compose**: a
`NavHost` maps string **routes** — `"main"`, `"settings"`, `"detail/{itemType}/{itemId}"`,
`"add_habit"`, `"habit_journal_chat/{arg0}/{slotMillis}"` — to composables.
`navController.navigate("settings")` pushes a screen; `navController.popBackStack()` goes back.

The three top-level tabs are special: they are **not** separate routes. They are three pages of
one `HorizontalPager` under the single `"main"` route. Swiping moves between them; tapping the
bottom nav bar snaps to a page. Everything else — Detail, Add, Edit, Settings sub-screens, the
journal chat and edit screens, the reply screen — *is* a stacked route drawn over `"main"`.

Why the split: swiping between Today / Habits / Intake should feel like one continuous surface (a
pager gives you that for free), while Detail and Settings are genuine push/pop navigation with a
back stack.

Key invariants (in the footgun list — do not casually change):

- **Today is always page index 0.** The system-back handler, deep-link fallbacks, and
  `NavConfig.visibleRoutesFrom` all rely on it. The "configurable tabs" feature can *hide* Habits
  or Intake but never removes or reorders Today.
- Tab taps use an instant snap (`scrollToPage`), not an animated scroll — animating a jump from
  page 0 to page 2 cold-composes two screens mid-fling and stutters.

### An everyday-terms analogy

A web router:

```
routes = {
  "/":                     home_view,
  "/settings":             settings_view,
  "/detail/<type>/<id>":   detail_view,
}
```

`NavHost` is that router; `navigate("detail/habit/$id")` is setting the URL; `popBackStack()` is
the browser back button; the `{ }` parts of a route are path parameters the destination reads
out.

### See it in the real code

**`app/src/main/java/com/daybook/app/ui/MainActivity.kt`**:

- **The launch gate** — a `when { ... }` block around line 285:
  `locked -> LockScreen(...)`; `authState is AuthState.Loading -> <neutral splash>`;
  `authState !is AuthState.SignedIn -> SignInGateScreen()`;
  `onboardingCompleted == null -> <neutral splash>`;
  `onboardingCompleted == false -> OnboardingScreen(...)`; `else -> MainApp()`. Read the comment
  above it: the four stages (app lock → auth → onboarding → the app) and the rule "never route
  from an unsettled snapshot" (every `null` / `Loading` state must render the *same* neutral
  splash, or the onboarding screen flashes on every launch).
- **`MainApp()`** (around line 366): `rememberNavController()`, a pager state whose start page
  comes from `NavConfig.landingIndex(...)`, the scaffold with the bottom nav, and the `NavHost`
  with `startDestination = "main"` and a `composable(...)` per route (around lines 508–645).
- Inside `composable("main")`: `BackHandler(enabled = settledPage != 0) { goToPage(0) }` —
  system back from Habits/Intake returns to Today first. Then the `HorizontalPager` whose page
  content is a `when` on the visible route (`"home"` → `HomeScreen(...)`, `"routines"` →
  `RoutinesScreen(...)`, else → `FoodMedScreen(...)`).
- **`LaunchedEffect(pendingDeepLink)`** (around line 385): a tapped notification's occurrence ID
  routes to `habit_journal_chat/...`, `habit_journal_edit/...`, `journal/...`, or `respond/...`
  depending on what kind of occurrence it is.

**`app/src/main/java/com/daybook/app/ui/NavConfig.kt`** — the pure logic for the configurable
bottom-nav (which tabs are visible, in what order, which one to land on). You read this in
Module 2; now it has context.

### Try it

**12a. Add a temporary debug route.** In `MainActivity.kt`'s `NavHost`, add:

```kotlin
composable("debug_ping") {
    androidx.compose.material3.Text("ping — press back")
}
```

and, in one of the Settings-hub `onOpen...` callbacks, temporarily point it at
`{ navController.navigate("debug_ping") }`. Rebuild, run, tap that Settings row → your bare
screen appears → system back pops it. **Revert.**

**12b. Prove the "Today is index 0" invariant.** In `NavConfig.visibleRoutesFrom`, what does it
do if the stored list is `"routines,foodmed"` (no `home`)? Why does `MainApp`'s `BackHandler`
check `settledPage != 0` specifically?

### Checkpoint

1. Why are the three tabs a pager rather than three routes, but Settings *is* a route?
2. How does a screen like `DetailScreen` receive its `itemId` argument?
3. What breaks if Today stops being page index 0?

---

# MODULE 13 — Background work and time: the reminder pipeline

### Concept

This is the historically fragile heart of the app. Two OS tools do the "later" work:

- **`WorkManager`** — for *deferrable* background jobs. "Run this sometime in the next day, when
  it is convenient, and survive reboots." Not precise. This app uses it for one thing:
  `WindowRefreshWorker`, a daily job that keeps the rolling window of future reminder rows topped
  up even if the app is never opened. (There is also a `SyncFlushWorker` for pushing pending
  cloud changes.)
- **`AlarmManager`** — for *exact* wall-clock times. "Fire at 08:00, even if the app is not
  running, even if the phone is dozing." This is what every actual reminder uses.

**The chain, in words:**

1. **The user creates or edits a habit / intake reminder.** The ViewModel calls
   `OccurrenceScheduler.syncHabit(id)` or `syncTask(id)`.
2. **The scheduler generates occurrence rows** for the rolling window (7 days): for each active
   weekday and each configured time, one `*_occurrences` row with `status = PENDING`, a
   deterministic ID, a stable notification ID, and the local date. It rewrites the window while
   *sparing* rows whose slot is still wanted (so their armed alarm is not lost and re-created).
3. **It arms exactly one alarm** — the next pending occurrence — via
   `NotificationUtils.scheduleReminderAlarm(...)`, which calls
   `AlarmManager.setExactAndAllowWhileIdle(...)`. If the exact-alarm permission is missing it
   catches the `SecurityException` and falls back to an *inexact* alarm. If quiet hours is on and
   the time falls inside it, the alarm is deferred to the window's end. **Only one alarm per item
   is ever armed**; the next is armed when the current one resolves.
4. **Time passes. The alarm fires.** Android delivers a broadcast to **`AlarmReceiver`**. It uses
   `goAsync()` plus a coroutine capped at ~8 seconds (the receiver has ~10 before the OS calls it
   hung).
5. **`AlarmReceiver`** loads the occurrence row; if it is no longer `PENDING` (the user already
   resolved it in-app), it does nothing. Otherwise it posts the notification via
   `NotificationUtils`, logs one `SHOWN` event, and schedules a **re-nag alarm** for
   `now + snoozeInterval` — so the reminder keeps coming back until it is resolved.
6. **The user acts** — from the shade or in-app. Notification buttons go to
   **`NotificationActionReceiver`**, which calls the matching `OccurrenceScheduler` method
   (`completeHabit`, `skipFoodMed`, `snoozeHabit`, `logFoodMed`, ...) inside another ~8-second
   coroutine, and — in a `finally`, unconditionally — cancels the shade notification by ID.
7. **The scheduler resolves the occurrence**: sets the `status`, writes a terminal event, cancels
   this occurrence's alarm, and arms the *next scheduled* occurrence (never immediately
   re-firing a different overdue one).
8. **The observing flows re-emit** (the tables changed) → the Today screen recomposes the card →
   if signed in, a debounced cloud push follows (Module 14).

**Re-arm triggers.** Alarms are volatile — a reboot or an app update wipes every pending alarm.
**`BootCompletedReceiver`** listens for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
`TIMEZONE_CHANGED`, `TIME_SET`, and the exact-alarm-permission-changed broadcast, and runs a full
re-sync (walk every active item, regenerate windows, re-arm). `MainActivity` also runs a full
sync once per launch.

### The gotcha that cost days — notification channels are immutable

On Android 8 and up, a `NotificationChannel` is created **once**. After that the system
**ignores** every later change to its importance, and a user's "turn this channel off" survives
app updates (only a full uninstall clears it). So a build that once shipped a broken or blocked
channel will *silently* swallow every notification on it forever, while the app-level "are
notifications enabled" check still says yes.

The fix in this codebase: **channel IDs are versioned** — `habits_v2`, `food_med_v2` — and on
startup the code deletes the legacy `habits` / `food_med` channels. If channel state is ever
suspect again, **bump the suffix** (`_v3`) and delete `_v2`. There is a `notificationBlockReason()`
diagnostic that checks both app-level and per-channel state, surfaced in Settings.

### An everyday-terms analogy

There is no clean scripting analogy for `AlarmManager` + `BroadcastReceiver` — it is OS-level
scheduling and callbacks. The closest: a cron job that runs a script which pops up a desktop
notification with buttons, and the buttons hit a tiny local server that updates a database row.
The ~8-second cap is "you have about ten seconds in this callback before the OS thinks you have
hung — keep your work under that".

### See it in the real code

Read these, in order:

1. **`app/src/main/java/com/daybook/app/data/OccurrenceScheduler.kt`** (large — the reminder
   engine). Focus on: the window-generation helpers, `syncHabitInternal` / `syncTaskInternal`
   (the spare-don't-recreate window rewrite, with a long comment), the "arm the next one"
   functions and their `allowCatchup` contract, the resolve functions, the quiet-hours deferral
   helper, and the batch check-in path. The pure top-level functions at the end (`canBackfill`,
   `unresolvedBatch`, ...) are pulled out precisely so they can be unit-tested without a
   database.
2. **`app/src/main/java/com/daybook/app/util/notification/NotificationUtils.kt`** — the versioned
   channel constants and the immutability comment, `createNotificationChannels`,
   `scheduleReminderAlarm` (exact → inexact fallback), the request-code scheme that makes a
   re-arm *replace* rather than *stack* alarms, `showHabitNotification` / `showFoodMedNotification`
   (the fixed Skip / Snooze / Complete vs Skip / Snooze / Reply button order; `RemoteInput` for
   the inline reply field), and `notificationBlockReason`.
3. **`app/src/main/java/com/daybook/app/util/alarm/AlarmReceiver.kt`** — the `goAsync()` + timeout
   wrapper, the "is it still PENDING?" check, posting, the guarded `SHOWN` event, scheduling the
   re-nag.
4. **`app/src/main/java/com/daybook/app/util/alarm/NotificationActionReceiver.kt`** and
   **`BootCompletedReceiver.kt`** — the `finally { cancelNotification(...) }` in the action
   receiver, and the set of broadcast actions that trigger a re-arm in the boot receiver.
5. **`app/src/main/java/com/daybook/app/util/work/WindowRefreshWorker.kt`** — a `CoroutineWorker`
   that Hilt injects `OccurrenceScheduler` into; `enqueue()` is called from `DaybookApplication`.

### Try it

**13a. Trace a full cycle with logs.** Add a `Log.i("PIPE", "...")` at the top of, roughly:
`OccurrenceScheduler.syncHabitInternal`, `AlarmReceiver.fireHabit`,
`NotificationUtils.showHabitNotification`, `NotificationActionReceiver.onReceive`, and the habit
resolve function in `OccurrenceScheduler`. Rebuild, run. Create a habit with a time about two
minutes out. Grant notifications and exact alarms. Watch Logcat filtered to `PIPE`: sync → (two
minutes later) fire → show → (tap Complete) onReceive → resolve. That is the whole spine.
**Remove the logs.**

**13b. Feel the channel gotcha.** In `NotificationUtils`, temporarily change the habits channel
ID to `"habits_v2_broken"` and set its importance to the lowest level. Rebuild, install, fire a
habit reminder — it arrives silently or not at all. Now set the importance back to high
*without changing the ID*, rebuild, reinstall — still silent, because the channel already exists
at the low level. Only changing the ID again fixes it. **Then revert to `"habits_v2"`.**

### Checkpoint

1. How many alarms are armed per habit at any one moment, and when is the *next* one armed?
2. A user turns off the "Habit reminders" channel, then you ship an update that "fixes
   notifications". Do their reminders come back? What is the only thing that makes them come
   back?
3. What re-arms every alarm after the user reboots the phone?

---

# MODULE 14 — Firebase: sign-in and cloud sync, gently

### Concept

Firebase is a set of Google cloud services. This app uses three, all **optional to the core
experience** and all **failure-inert** (a Firebase call that fails never blocks a screen or a
launch):

- **Firebase Auth (Google sign-in).** The one and only sign-in method. The app shows a sign-in
  gate on first launch. Sign-in uses the modern *Credential Manager* API. Signing in identifies
  the user so their data can sync across devices.
- **Cloud Firestore (sync).** A cloud document database. **Room is the source of truth. Firestore
  is a mirror** — never the other way around. Firestore holds a *derived, gzipped* copy of the
  local data:
  - `users/{uid}` — the *definitions* (habits, intake reminders, custom categories/prompts) as a
    compressed blob, plus a `definitionsHash`, a per-month summary of hashes, and a revision
    number. This document is rewritten *only* when a definition changes — answering a reminder
    does not touch it.
  - `users/{uid}/months/{YYYY-MM}` — one document per calendar month of history, as a compressed
    blob plus a `contentHash`. A write touches one month; a reinstall does not download years of
    history to open the app. Only the current and previous month are loaded into Room; older
    months are fetched on demand and dropped again once their hash matches the cloud.
- **Firebase Crashlytics** — crash reporting. And **Firebase App Distribution** — how new builds
  reach testers, and how the app checks for updates (Module 15).

**How the app decides what to push:** every change is compared by **hash**. A push writes only
the documents whose hash changed. An incoming change whose hash equals what the app last applied
is ignored (an "echo guard"). On first sign-in a small decision (`ATTACH_ONLY` / `PUSH_LOCAL` /
`PULL_REMOTE` / `CONFLICT`) works out whether to adopt the cloud data, upload the local data, or
ask the user.

**Sign-out wipes local data.** A real sign-out cancels every alarm and notification, wipes all
data tables in one transaction, and resets the sync bookkeeping. Signing back into the *same*
account re-pulls everything from the cloud.

**Offline:** once signed in, the app is fully usable with no network. Sync just resumes when the
network returns.

### The hash-churn rule you must not break

The backup/wire model (`data/backup/BackupModel.kt`) is serialised in a way where **adding a new
optional field with a default value would change the serialised bytes of *every* user's
definitions** — even users who never touch the new feature — because the field now appears (with
its default) in the JSON. That changes `definitionsHash`. Which makes every user's app think its
definitions changed. Which forces **every user's app to re-upload its entire history** on the
next launch.

This regression has shipped more than once. The fix, now guarded by unit tests: every *new*
optional field on a backup/wire type gets

```kotlin
@EncodeDefault(EncodeDefault.Mode.NEVER)
val newThing: String? = null
```

which means "when this holds its default, write it as **absent**, not as `newThing: null`". A
user who does not use the feature then produces byte-identical output and a byte-identical hash —
zero churn. **When you add a synced field, this annotation is not optional.**

### An everyday-terms analogy

```
def content_hash(definitions, days):
    canonical = json.dumps({"definitions": definitions, "days": days}, sort_keys=True, separators=(",", ":"))
    return sha256(canonical)

# the churn bug, in plain terms:
#   v1: {"id": 1, "name": "x"}
#   v2 adds an optional field with a default:  {"id": 1, "name": "x", "motivation": None}
#   -> the hash changes for EVERY object, even ones that never set "motivation"
#   fix: only include the key when it is actually set
```

### See it in the real code

- **`app/src/main/java/com/daybook/app/data/auth/AuthRepository.kt`** — Google sign-in via
  Credential Manager, and a sealed `AuthState` type with a `Loading` state, a `SignedIn` state
  (carrying the display name), and a signed-out state.
- **`app/src/main/java/com/daybook/app/data/backup/BackupModel.kt`** — the backup/wire model.
  Every recently added optional field carries `@EncodeDefault(EncodeDefault.Mode.NEVER)` with a
  comment naming the precedent it follows.
- **`app/src/main/java/com/daybook/app/data/sync/ContentHash.kt`** — the canonical-JSON + SHA-256
  hashing. The class comment explains why the `meta` block must never be part of the hash (it
  contains a timestamp that changes every export).
- **`app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt`** (large — skim). The
  class doc states the invariant ("Room is the source of truth"). Anchors: the sign-in/out
  lifecycle, `wipeLocalForSignOut`, the first-sign-in bootstrap decision, the push (partition by
  month → hash diff → write only changed docs), and the `InvalidationTracker.Observer` that marks
  a pending push on any local write to the data tables. Note the data-tables list deliberately
  does **not** include `app_settings` — a settings write must not cost a cloud round-trip.
- **`firestore.rules`** (repo root) — the whole security model:
  `allow read, write: if request.auth.uid == uid`, once for `users/{uid}` and again for
  `users/{uid}/months/{month}` (a nested match does not inherit the parent's rule).

### Try it

**14a. Prove the `@EncodeDefault` rule in a scratch file.**

```kotlin
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

fun main() {
    val j = Json { encodeDefaults = true }
    @Serializable data class Bad(val id: Int, val n: Int = 0)
    @Serializable data class Good(val id: Int, @EncodeDefault(EncodeDefault.Mode.NEVER) val n: Int = 0)
    println(j.encodeToString(Bad(1)))    // {"id":1,"n":0}   <- the churn bug
    println(j.encodeToString(Good(1)))   // {"id":1}         <- fixed
}
```

That extra `"n":0` is what re-uploaded everyone's history.

**14b. Find the test that guards it.** Open a hash test under
`app/src/test/java/com/daybook/app/data/sync/` (there are several — `PerHabitTextHashTest`,
`HabitJournalHashTest`, `ContentHashTest`). Read what it asserts: that a definition with no
optional fields set hashes identically before and after those fields existed.

**14c. Read the rules.** Open `firestore.rules` and explain, in one sentence, why there are two
`match` blocks and not one.

### Checkpoint

1. Which is authoritative when Room and Firestore disagree?
2. You add `val colorHex: String? = null` to a synced definition type. What annotation must it
   carry, and what happens to every existing user if you forget it?
3. Why is `app_settings` deliberately excluded from the sync's data-tables list?

---

# MODULE 15 — The build and release pipeline

### Concept

Two kinds of test back the release process:

- **Unit tests** — `app/src/test/`. Plain JVM tests, no Android, no device, no database, no
  Compose. They test **pure functions**: date maths, quiet-hours deferral, streak calculation,
  greeting rendering, `NavConfig`, content hashing. This is *why* so much logic in this codebase
  is pulled out into small top-level functions with no dependencies — so it can be tested in
  milliseconds. There are about **446** of these tests across ~74 files.
- **Instrumented tests** — `app/src/androidTest/`. Run on a real device or emulator because they
  need the Android framework. This project has three: `MigrationTest` (needs real SQLite + Room),
  `ChunkedDeleteTest` (a large-delete edge case), and `NavIconInflateTest` (needs the resource
  system). No Compose UI tests, no Espresso — a deliberate scope choice.

**The 4-gate.** Every change runs all four of these, from a clean state, and only ships if all
four are green:

```bash
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew \
  clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

1. **`testDebugUnitTest`** — all unit tests pass.
2. **`assembleDebug`** — the app compiles and packages.
3. **`assembleRelease`** — the *release* build compiles, **R8 is clean** (no missing-class
   warnings from shrinking), **and `lintVitalRelease` is clean** (Android's release-blocking
   lint checks pass). This gate catches "works in debug, broken in the shipped APK" problems.
4. **`compileDebugAndroidTestKotlin`** — the instrumented tests still *compile* (they are not run
   here — no device — but a broken `MigrationTest` is caught).

Why all four: debug compiling does not prove release compiles (R8 differs); tests passing does
not prove the app packages; and the androidTest sources are a separate compile unit a refactor
can silently break.

**Release signing.** The `release` build type is signed with the project's real key. The
credentials live in `keystore.properties` at the repo root (gitignored), and the key file is
`app/daybook-release.jks` (also gitignored). If `keystore.properties` is absent, the release
build falls back to the debug key so it still assembles — but such an APK will *not* install as
an update over a properly signed one. Full details, and the "swapping in a production key"
caveat, are in **`RELEASE_SIGNING.md`**. Do not copy any password out of that file.

**Over-the-air updates: Firebase App Distribution.** Daybook is sideloaded — there is no Play
Store listing — so "in-app updates" means Firebase App Distribution, not Play's update API.

- **Pushing a build:**
  ```bash
  JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
    ./gradlew assembleRelease appDistributionUploadRelease
  ```
  This builds the signed release APK and uploads it to the `testers` group in one step (the
  target group is declared in the `firebaseAppDistribution { }` block in `app/build.gradle.kts`).
  There is also a raw CLI path (`firebase appdistribution:distribute <apk> --app <id> --groups
  testers`). Full workflow, including what testers experience and how the "Enable testing
  features" sign-in prompt works, is in **`HOW_TO_PUSH_UPDATES.md`**.
- **The versionCode rule.** You **must** bump `versionCode` in `app/build.gradle.kts` before
  every push. Firebase identifies a release by its version; pushing the same `versionCode` again
  just *re-uploads the existing release*, and no tester's app will see it as newer. This has
  bitten the project twice.
- **The in-app check.** `MainActivity.onResume()` calls
  `InAppUpdateChecker.checkForUpdate(...)` (in `util/update/`), which asks App Distribution
  whether a newer release exists and, if so, shows the SDK's own update dialog. It is gated by
  the "Check for updates" toggle in *Settings → Notifications & alarms* (on by default; it flips
  itself off if the user declines the App Distribution sign-in prompt).

### See it in the real code

- Open a couple of unit tests and notice they are just Kotlin + `assertEquals`:
  `app/src/test/java/com/daybook/app/util/streak/OngoingStreakTest.kt` (tiny),
  `app/src/test/java/com/daybook/app/data/QuietHoursTest.kt` (many wrap-midnight cases),
  `app/src/test/java/com/daybook/app/ui/NavConfigTest.kt`.
- `app/src/androidTest/java/com/daybook/app/data/local/MigrationTest.kt` — the
  `MigrationTestHelper` + `createDatabase` + `runMigrationsAndValidate` pattern.
- `app/build.gradle.kts` — the `signingConfigs` / `buildTypes { release { ... } }` block and the
  `firebaseAppDistribution { groups = "testers" }` inside it.
- `app/src/main/java/com/daybook/app/util/update/InAppUpdateChecker.kt` — about 45 lines; the
  whole update-check surface.

### Try it

**15a. Run the unit gate and open the report.**

```bash
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
./gradlew testDebugUnitTest
# then open app/build/reports/tests/testDebugUnitTest/index.html
```

**15b. Write a real one-line test.** Create
`app/src/test/java/com/daybook/app/util/streak/DaysSinceHandoverTest.kt`:

```kotlin
package com.daybook.app.util.streak

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class DaysSinceHandoverTest {
    private val utc = ZoneId.of("UTC")
    private val day = 86_400_000L

    @Test fun started_today_is_one() {
        assertEquals(1, daysSince(startMillis = 0L, nowMillis = 0L, zone = utc))
    }
    @Test fun started_yesterday_is_two() {
        assertEquals(2, daysSince(startMillis = 0L, nowMillis = day, zone = utc))
    }
}
```

Run `./gradlew testDebugUnitTest` — it should pass. Change one expected value to a wrong number,
re-run, read how the report shows expected vs actual, then fix it back. (Check the real signature
of `daysSince` in `util/streak/OngoingStreak.kt` first and adjust the call if needed.)

**15c. Run the full 4-gate.**

```bash
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

Time it. This is what you will run before every change from now on.

### Checkpoint

1. What does each of the four gates catch that the others do not?
2. Why must you bump `versionCode` before every App Distribution push?
3. Where does the release build get its signing key, and what happens if `keystore.properties`
   is missing?

---

# Make your first real change — a new settings toggle, end to end

This is the graduation exercise. It is the change you will copy from for most small work on this
app: a **device-local setting** — a toggle that persists across restarts, touching the database,
a DAO, a repository, a ViewModel, and a screen. It does **not** need sync (device-local settings
are not synced), so it is the smallest complete "vertical slice" you can do.

**The feature:** add a toggle **"Confirm before deleting"** to `app_settings` (default on),
surface it in *Settings → Appearance*, and read it in the ViewModel that shows the
delete-confirmation dialog. (If a simpler target helps, do the same steps for a cosmetic toggle
like "Show seconds in timestamps" that nothing else reads yet — the mechanics are identical.)

### Step 0 — branch and baseline

```bash
git switch -c first-change-confirm-delete
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

Confirm all four are green *before* you touch anything, so a later failure is definitely yours.

### Step 1 — the column (`data/model/DataModel.kt`)

In `data class AppSettings(...)`, **append** a property (never insert one mid-list — the order is
part of the schema):

```kotlin
    // First-change exercise: device-local, additive, default true.
    @ColumnInfo(name = "confirm_before_delete", defaultValue = "1")
    val confirmBeforeDelete: Boolean = true
```

Because this is a `Boolean` with a *schema-level* `defaultValue`, the migration's `DEFAULT` must
match it byte for byte (`1`).

### Step 2 — the migration (`data/local/Migrations.kt`)

You **cannot** edit `MIGRATION_18_19` — treat any migration whose `.json` is committed as frozen.
Add a new one at the end:

```kotlin
/**
 * v19 -> v20 (first-change exercise). One additive column on `app_settings`, no table rebuild.
 * NOT NULL DEFAULT 1 byte-matches @ColumnInfo(name = "confirm_before_delete", defaultValue = "1").
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN confirm_before_delete INTEGER NOT NULL DEFAULT 1")
    }
}
```

### Step 3 — bump the version (`data/local/AppDatabase.kt`)

`@Database(entities = [ ... ], version = 20, exportSchema = true)`

### Step 4 — register it (`di/DatabaseModule.kt`)

Add the import, then append to the chain:
`.addMigrations(MIGRATION_2_3, ..., MIGRATION_18_19, MIGRATION_19_20)`

### Step 5 — regenerate and commit the schema JSON

```bash
./gradlew clean assembleDebug
git add app/schemas/com.daybook.app.data.local.AppDatabase/20.json
git diff --cached --stat        # should show ONLY 20.json added
```

Open `20.json`, confirm `"version": 20` and that the only difference from `19.json` is the one
`confirm_before_delete` column.

### Step 6 — DAO and repository

`data/local/AppSettingsDao.kt` — add a targeted update, following the existing ones:

```kotlin
@Query("UPDATE app_settings SET confirm_before_delete = :v WHERE id = 1")
suspend fun setConfirmBeforeDelete(v: Boolean)
```

`data/AppSettingsRepository.kt` — add, mirroring `setWeekStart` etc.:

```kotlin
suspend fun setConfirmBeforeDelete(v: Boolean) {
    ensureRow()
    database.appSettingsDao().setConfirmBeforeDelete(v)
}
```

### Step 7 — the Settings screen and its ViewModel

In `ui/settings/SettingsViewModel.kt`: expose the value as a `StateFlow<Boolean>` derived from
`appSettingsRepository.observeSettings().map { it.confirmBeforeDelete }`, and add a function
`fun setConfirmBeforeDelete(v: Boolean) = viewModelScope.launch { appSettingsRepository.setConfirmBeforeDelete(v) }`.

In `ui/settings/SettingsScreen.kt`, inside the *Appearance* sub-screen, add a toggle row
following an existing one (search for an existing `Switch` / toggle row in that file and copy its
shape), bound to the new `StateFlow` and function.

### Step 8 — read it somewhere

Find `ConfirmDeleteDialog` usage (search the project). In the ViewModel that decides whether to
show it, read the new setting and skip straight to the delete when it is off. (Optional for the
exercise — wiring the setting through is the lesson; a consumer just proves it.)

### Step 9 — tests

- **Migration test** — in `MigrationTest.kt`, add a `migrate19To20_addsConfirmBeforeDeleteColumn()`
  case following the `migrate18To19` pattern: create at 19, run `MIGRATION_19_20`, assert
  `app_settings` now has a `confirm_before_delete` column and a pre-existing row reads `1`. Add
  the new version to the "migrate all the way" chain case.
- **A tiny unit test** if you extracted any pure logic in Step 8.

### Step 10 — the 4-gate, clean

```bash
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

All four green. On a device, also install a build from *before* your change, then install your
new build over it (an update, not a fresh install) and confirm existing settings survive and the
new toggle defaults to on.

### Step 11 — commit

```bash
git add -A
git commit -m "First change: 'confirm before deleting' setting, end to end (DB v20)"
```

**Graduation question:** without looking, list every file you had to touch and why. You should
get: `DataModel.kt` (column), `Migrations.kt` (migration), `AppDatabase.kt` (version),
`DatabaseModule.kt` (register), `20.json` (generated + committed), `AppSettingsDao.kt` +
`AppSettingsRepository.kt` (write path), `SettingsViewModel.kt` + `SettingsScreen.kt` (the
toggle), a consumer, the migration test, and the 4-gate.

---

# How to not break things

A blunt list. Each of these has bitten someone.

- **The 4-gate is not optional.** `clean testDebugUnitTest assembleDebug assembleRelease
  compileDebugAndroidTestKotlin`, all green, before anything ships. Debug compiling does not
  prove release compiles.
- **A shipped database schema is frozen.** Never change an `@Entity` without a migration, a
  version bump, the regenerated `<n>.json` committed, and a migration test. Run the migration
  gate on a `clean` build — an incremental build can leave a stale schema JSON that makes the
  test pass against a lie.
- **Bump `versionCode` before every App Distribution push.** Same `versionCode` = Firebase
  silently re-uploads the old release and no tester sees it as new. Also: a same-`versionCode`
  APK will not install as an update at all — the user would have to uninstall first, wiping local
  data.
- **Notification channels are immutable once created.** Changing a channel's importance in code
  does nothing after the first install, and a user's "block this channel" survives updates. The
  only fix is to bump the channel-ID suffix (`habits_v2` → `habits_v3`) and delete the old one.
- **Every new optional field on a backup/wire type needs
  `@EncodeDefault(EncodeDefault.Mode.NEVER)`.** Forget it and every user's app re-uploads its
  entire history on the next launch. Unit tests now guard this — run `testDebugUnitTest`.
- **`app_settings` is deliberately not synced.** Device-local preferences (accent, font,
  week-start, quiet hours, ...) are not in the cloud and not in the backup; a reinstall resets
  them. That is by design.
- **Dark theme only. Offline-first.** There is no light theme and there never will be. Every
  network call must be failure-inert and off the launch path. The one deliberate exception is the
  sign-in gate.
- **Today is always page index 0.** The back handler, deep-link fallbacks, and `NavConfig` all
  rely on it. The configurable-tabs feature can hide Habits or Intake, never Today, never
  reorder.
- **"Never route from an unsettled snapshot."** In `MainActivity`'s launch-gate `when`, every
  `null` / `Loading` state must render the same neutral splash. Rendering a real screen while
  auth/onboarding state is still resolving reintroduces the "onboarding flashes on every launch"
  bug.
- **Enums stored by Room / listed by declaration order: append only.** Never reorder or rename
  `HabitType`, `TaskType`, `RedFlag`, `Occurrence.Status`, `Event.Action`, `ColorTag`.
- **BroadcastReceivers get ~10 seconds.** Every one here uses `goAsync()` + an ~8-second timeout.
  Keep that budget if you add work.
- **If you change `firestore.rules`, deploy it:** `firebase deploy --only firestore:rules`. The
  nested `users/{uid}/months/{month}` match does not inherit the `users/{uid}` rule — both blocks
  are required.
- **`google-services.json` and `keystore.properties` are needed for full builds.**
  `google-services.json` (in `app/`) is required for anything touching Firebase;
  `keystore.properties` + `app/daybook-release.jks` are required for a properly signed release.
  Both are on this machine; both are gitignored.

---

# Glossary

- **ADB (Android Debug Bridge)** — command-line tool that talks to a connected device/emulator
  (install APKs, read logs, run shell commands).
- **AAB (Android App Bundle)** — an alternative publish format for the Play Store. This app ships
  APKs, not AABs.
- **Activity** — an Android component that hosts one screen/window and has an OS-driven lifecycle
  (`onCreate` / `onResume` / `onStop`). This app has essentially one: `MainActivity`.
- **AGP (Android Gradle Plugin)** — the Gradle plugin that teaches Gradle how to build an Android
  app. Pinned to 8.3.2 here.
- **androidTest** — instrumented tests that run on a device/emulator. Here: `MigrationTest`,
  `ChunkedDeleteTest`, `NavIconInflateTest`.
- **ANR ("Application Not Responding")** — the OS dialog/kill you get when the main thread is
  blocked too long. Avoided by doing slow work in coroutines off the main thread.
- **APK (Android Package)** — the installable app file; technically a signed zip of compiled code
  + resources + manifest.
- **AlarmManager** — the OS service for "run this at an exact wall-clock time", even when the app
  is not running.
- **BOM (Bill of Materials)** — a dependency that pins a whole family of libraries to
  mutually-compatible versions (the Compose BOM, the Firebase BOM).
- **BroadcastReceiver** — a component the OS instantiates to hand it a single event (alarm fired,
  boot completed, notification button tapped). ~10 seconds to do its work.
- **Coil** — the image-loading library used to render the profile photo.
- **cold flow / hot flow** — a cold `Flow` does nothing until something collects it, and does its
  work per-collector. A hot flow (like `StateFlow`) is always running and shares one stream of
  values among all collectors.
- **Compose (Jetpack Compose)** — the declarative UI toolkit. You write `@Composable` functions
  that describe the screen for the current data; the runtime redraws on change.
- **`@Composable`** — annotation marking a function as UI-emitting; can only be called from other
  `@Composable`s.
- **CompositionLocal** — a way to pass a value implicitly down the Compose tree (`LocalAccent`,
  `LocalReduceMotion` here).
- **coroutine** — a unit of suspendable work. Started in a scope (`viewModelScope`,
  `lifecycleScope`); cancelled when the scope ends ("structured concurrency").
- **Crashlytics** — Firebase's crash-reporting service.
- **Dagger** — the older dependency-injection framework that Hilt is built on.
- **DAO (Data Access Object)** — a Room `@Dao` interface whose methods carry the SQL; Room
  generates the implementation.
- **DI (Dependency Injection)** — having a framework construct and wire your objects instead of
  doing it by hand. Here: Hilt.
- **`.dp` (density-independent pixel)** — a layout unit that is the same physical size across
  screen densities.
- **entity** — a Room `@Entity data class`; one class equals one database table.
- **Firebase App Distribution** — how test builds are delivered to testers, and how the app
  checks for updates (there is no Play Store listing).
- **Firestore (Cloud Firestore)** — Firebase's cloud document database. Here it is a gzipped
  mirror of Room, never the source of truth.
- **Flow** — a cold asynchronous stream of values. A Room `Flow<List<T>>` re-emits on every table
  change.
- **Gradle** — the build system (dependency manager + task runner + build-script engine). Invoked
  via `./gradlew`. Pinned to 8.6.
- **Hilt** — the DI framework (built on Dagger). Reads `@Inject` / `@HiltViewModel` / `@Module`
  and generates the wiring at build time.
- **IDE (Integrated Development Environment)** — here, Android Studio.
- **`identityHash`** — a hash Room computes of a schema; stored in the DB and in each `<n>.json`.
  A mismatch on open means a migration did not produce the expected schema.
- **IME (Input Method Editor)** — the on-screen keyboard. `windowSoftInputMode` and
  `Modifier.imePadding()` control how the UI reacts when it opens.
- **`@Inject`** — marks a constructor (or a field, in a receiver) for Hilt to satisfy.
- **JDK (Java Development Kit)** — the Java toolchain Kotlin compiles onto. This project wants
  JDK 17.
- **kapt (Kotlin Annotation Processing Tool)** — runs annotation processors (Room, Hilt) at build
  time to generate source. The main reason builds are slow. Newer alternative: KSP (not used
  here, deliberately).
- **keystore / `.jks`** — the file holding the signing key. An update must be signed with the
  same key as the installed version. `app/daybook-release.jks`; credentials in
  `keystore.properties`.
- **KSP (Kotlin Symbol Processing)** — a faster replacement for kapt. Not used here.
- **`LaunchedEffect`** — a Compose API to run a suspend block when a composable enters
  composition (or a key changes); the sanctioned place for side effects.
- **lint / `lintVitalRelease`** — Android's static checker. `lintVitalRelease` runs the
  release-blocking subset and is part of the 4-gate.
- **manifest (`AndroidManifest.xml`)** — declares the app's components, permissions, and launcher
  activity. The OS's table of contents for your app.
- **migration** — code (SQL) that transforms the database from one schema version to the next.
  Here: `MIGRATION_2_3` … `MIGRATION_18_19` in `Migrations.kt`.
- **minSdk / targetSdk / compileSdk** — the oldest Android version the app runs on (26), the
  version the app is tested and declares behaviour against (34), and the SDK version the code is
  compiled with (34).
- **Modifier** — a chainable Compose value attaching layout/appearance/behaviour to a composable.
- **`mutableStateOf` / `remember`** — create observable UI state; `remember` keeps it stable
  across recomposition. `rememberSaveable` also survives rotation/process death.
- **occurrence** — one concrete scheduled instance of a habit/task (a specific date+time slot),
  stored as a row with a `status`. The middle of the habit → occurrence → event model.
- **OTA (over-the-air)** — delivering an app update without a cable. Here: Firebase App
  Distribution.
- **`PendingIntent`** — a token that lets another process (the OS) fire an action on your app's
  behalf later. Used for alarms and notification buttons.
- **ProGuard / R8** — the release-build shrinker/obfuscator/optimiser. `isMinifyEnabled = true`.
  Missing "keep" rules cause "works in debug, broken in release" bugs; rules live in
  `app/proguard-rules.pro`.
- **recomposition** — Compose re-running a composable because a state value it read changed.
- **Repository** — the plain class layer between ViewModels and DAOs; the one home for a data
  operation and the seam for swapping data sources.
- **Room** — the SQLite object-mapping library: `@Entity` / `@Dao` / `@Database`.
- **SAF (Storage Access Framework)** — Android's system file-picker/sharing mechanism, used for
  JSON export/import without a broad storage permission.
- **SDK (Android SDK)** — Android's own libraries plus tools (`adb`, build-tools, platforms).
  `ANDROID_HOME=/home/abhiram/android-sdk`.
- **sealed class / interface** — a closed set of subtypes, each able to carry its own data; a
  `when` over one is checked for completeness. `SyncStatus`, `AuthState`, `HydrateResult`.
- **StateFlow** — a `Flow` with an always-available current value that emits only on change; the
  standard ViewModel → screen type.
- **`suspend`** — marks a function that can pause without blocking a thread; callable only from a
  coroutine or another `suspend fun`.
- **versionCode / versionName** — an integer update counter (must strictly increase to install as
  an update) and a human-readable string. In `app/build.gradle.kts` `defaultConfig`. Currently
  `20` / `"0.5.6"`.
- **ViewModel** — the state-and-logic holder for one screen; survives configuration changes;
  exposes `StateFlow`s, runs work in `viewModelScope`.
- **WorkManager** — the OS-friendly scheduler for deferrable background jobs (here:
  `WindowRefreshWorker` daily, `SyncFlushWorker` on demand). Not for exact-time reminders — that
  is `AlarmManager`.

---

# Where things live — a file map

| Area | Main files |
|---|---|
| **App entry / one-time setup** | `DaybookApplication.kt` (channels, worker, sync start, crash handler) |
| **The single Activity, launch gate, navigation** | `ui/MainActivity.kt`, `ui/NavConfig.kt`, `ui/components/Navigation.kt` |
| **Theme (dark only)** | `ui/theme/Theme.kt`, `ui/theme/Tokens.kt` (colours, spacing, shapes, motion), `ui/theme/Type.kt` (5 fonts, default Literata), `ui/theme/Accent.kt` (5 accents, default Lavender) |
| **Data model (every table + every enum)** | `data/model/DataModel.kt` |
| **Room: database, DAOs, migrations** | `data/local/AppDatabase.kt`, `data/local/*Dao.kt`, `data/local/Migrations.kt`, `app/schemas/.../3.json … 19.json` |
| **Repositories (the boring middle layer)** | `data/HabitRepository.kt`, `data/FoodMedRepository.kt`, `data/AppSettingsRepository.kt`, `data/CustomCategoryRepository.kt`, `data/CustomPromptRepository.kt`, `data/ExportImportRepository.kt` |
| **The reminder engine** | `data/OccurrenceScheduler.kt`, `data/QuietHours.kt` |
| **Alarms + notifications** | `util/alarm/AlarmReceiver.kt`, `util/alarm/BootCompletedReceiver.kt`, `util/alarm/NotificationActionReceiver.kt`, `util/notification/NotificationUtils.kt`, `util/notification/NotificationIdSequence.kt` |
| **Background jobs** | `util/work/WindowRefreshWorker.kt`, `util/work/SyncFlushWorker.kt` |
| **Streaks** | `util/streak/StreakCalculator.kt`, `util/streak/OngoingStreak.kt` |
| **Auth (Google sign-in)** | `data/auth/AuthRepository.kt`, `data/auth/GoogleAvatarFetcher.kt`, `data/auth/TaskAwait.kt`, `ui/account/SignInGate.kt`, `ui/account/SignInContent.kt` |
| **Cloud sync** | `data/sync/CloudSyncRepository.kt`, `data/sync/SyncLogic.kt`, `data/sync/MonthPartitioner.kt`, `data/sync/ContentHash.kt`, `data/sync/PayloadCodec.kt`, `data/sync/SyncStateStore.kt`, `data/RetentionPolicy.kt`, `firestore.rules` |
| **Backup / export / import** | `data/backup/BackupModel.kt`, `data/ExportImportRepository.kt`, `util/StorageUtils.kt` |
| **App lock (PIN + biometric)** | `data/lock/AppLockRepository.kt`, `data/lock/PinHasher.kt`, `data/lock/BiometricGate.kt`, `ui/lock/LockScreen.kt`, `ui/lock/AppLockSettingsScreen.kt` |
| **DI wiring** | `di/DatabaseModule.kt`, `di/FirebaseModule.kt` |
| **Today screen** | `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/components/WeekStrip.kt` (calendar + "Back to today") |
| **Habits (Routines)** | `ui/routines/RoutinesScreen.kt`, `ui/routines/RoutinesViewModel.kt`, `ui/routines/HabitForm.kt`, `ui/routines/AddHabitScreen.kt` + `EditHabitScreen.kt` + their ViewModels, `data/JournalQuestionListEdits.kt` |
| **Intake (Food / Med)** | `ui/foodmed/FoodMedScreen.kt`, `ui/foodmed/FoodMedViewModel.kt`, `ui/foodmed/FoodMedForm.kt` |
| **Journal (per-habit, chat style)** | `ui/journal/HabitJournalChatScreen.kt` + ViewModel, `ui/journal/HabitJournalEditScreen.kt` + ViewModel. (`ui/journal/JournalScreen.kt` + ViewModel are the retired legacy stepper — see `FEATURES.md` §5a.) |
| **Detail (History / Stats tabs)** | `ui/detail/DetailScreen.kt`, `ui/detail/DetailViewModel.kt`, `ui/detail/DetailPaging.kt` |
| **Reply / respond screen** | `ui/respond/RespondScreen.kt` |
| **Settings** | `ui/settings/SettingsScreen.kt`, `ui/settings/NavigationSettingsScreen.kt`, `ui/settings/SettingsViewModel.kt` |
| **Account screen / delete account** | `ui/account/AccountScreen.kt`, `ui/account/AccountViewModel.kt` |
| **Onboarding** | `ui/onboarding/OnboardingScreen.kt` |
| **Shared UI components** | `ui/components/` — `Components.kt` (`SoftCard`), `Forms.kt`, `Sheets.kt`, `SortSheet.kt`, `SegmentedControl.kt`, `ScreenHeader.kt`, `BackHeader.kt`, `StickySaveBar.kt`, `Avatar.kt`, `TextLink.kt`, `WaveHero.kt`, `ConfirmDeleteDialog.kt`, `DaybookAlertDialog.kt`, `UndoSnack.kt` |
| **Icons** | `ui/icons/` |
| **In-app update check** | `util/update/InAppUpdateChecker.kt` |
| **Misc utilities** | `util/DateTimeUtils.kt`, `util/JsonUtils.kt`, `util/TimeTicker.kt`, `util/CrashHandler.kt`, `util/ViewModelExt.kt`, `util/enums/Converters.kt` |
| **Tests** | `app/src/test/` (~446 JVM unit tests, ~74 files), `app/src/androidTest/` (`MigrationTest`, `ChunkedDeleteTest`, `NavIconInflateTest`) |
| **Docs** | `README.md`, `HANDOVER.md` (this file), `FEATURES.md`, `RELEASE_SIGNING.md`, `HOW_TO_PUSH_UPDATES.md` |

---

# Reference: follow one tap through the whole system

**"The user taps the checkmark (Complete) on a habit card on the Today screen."**

1. **`ui/home/HomeScreen.kt`** — the card's `onComplete` lambda fires → `viewModel.completeItem(item)`.
2. **`ui/home/HomeViewModel.kt`** — `completeItem` → `viewModelScope.launch {
   scheduler.completeHabit(item.occurrenceId) }`.
3. **`data/OccurrenceScheduler.kt`** — `completeHabit(occurrenceId)` → the habit resolve function,
   under a mutex lock:
   - cancel any shade notification for this occurrence, *first*, before any early return;
   - `UPDATE habit_occurrences SET status = 'COMPLETED', ...` — the write;
   - `INSERT` a `COMPLETED` row into `habit_events` — append-only history;
   - cancel this occurrence's re-nag alarm;
   - arm the *next scheduled* occurrence (with catch-up off, so resolving one reminder never
     re-fires a different overdue one).
4. **Room** commits the `UPDATE` + `INSERT`. The `habit_occurrences` and `habit_events` tables
   are now changed.
5. **The observing flows re-emit.** `HomeViewModel`'s day-items flow produces a new list with
   this occurrence now `COMPLETED`.
6. **`HomeViewModel`'s `StateFlow` updates**, the "Your progress → Habits" ratio recomputes, and
   the streak calculator re-runs off the new statuses.
7. **Compose recomposes** just the affected card (it fades out of the pending list) and the
   progress ring.
8. **If signed in:** the Room write triggered the sync's `InvalidationTracker.Observer`, which
   marked a pending push. After the debounce, the push runs, sees this month's `contentHash`
   changed, and writes *only* that one `users/{uid}/months/{YYYY-MM}` document. The
   `definitionsHash` is unchanged (a completion is not a definition change), so the parent
   document is not rewritten.

Every hop is a file you can open and a function you can set a breakpoint in.

---

*You have reached the end. If you did every "Try it" and can answer the checkpoints, you can make
real changes to this app safely — and you have learned the core of modern Android along the way.
Keep this file. When something confuses you in six months, the answer is probably in here; if it
is not, add it.*
