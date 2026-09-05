# Daybook — Developer Handover & Learn-Android-From-This-Codebase Course

I wrote every line of this app. I'm handing it to you. You know a little Python — variables,
`if`, `for`/`while`, functions, lists, dicts, `print` — and you have never done app development,
Android, Kotlin, Gradle, or used an IDE for anything bigger than running a script. That is fine.
This document is written so that if you read it top to bottom, doing every **Try it now**, you
will come out the other side able to make real changes to this app safely — and, honestly, able
to work on most Android apps.

This is one file on purpose. It is long on purpose. Every paragraph is meant to earn its place;
if one doesn't, skip it and come back.

---

## How to use this document

**Read it once, in order, doing the exercises.** Then keep it open as a reference forever.

There are three layers:

1. **Part 0** — what the app is and the philosophy behind it. 15 minutes. Read it now.
2. **Modules 1–17** — the course. Each module teaches one concept using this codebase as the
   textbook. Do them *in order*; each assumes the ones before it.
3. **Reference sections** at the end — folder tour, "follow one tap", footguns, glossary,
   where-to-learn-more. Skim them now, use them later.

**Every module has the same five parts:**

- **Concept** — plain language, no jargon I haven't already defined.
- **The Python you already know** — the closest thing in Python, side by side.
- **See it in the real code** — a specific file and line range in *this repo* to open and read,
  with me walking you through it.
- **Try it now** — a small concrete task with the exact commands and the result you should see.
  **Do not skip these.** Reading about code and changing code are different skills; you need both.
- **Checkpoint** — two or three questions. If you can't answer them, re-read the module before
  moving on.

**Rough time budget** (very rough — go slower if you need to):

| Part | Modules | Time |
|---|---|---|
| Setup & orientation | 1 | half a day (mostly downloads) |
| Language | 2 | 2–3 hours |
| Android + build basics | 3, 4 | 2 hours |
| UI | 5, 6 | 3 hours |
| Data layer | 7, 8, 9, 10, 11 | one full day |
| Wiring it together | 12, 13, 14, 15 | one full day |
| Capstone + tests | 16, 17 | one full day |

So: about a working week if you're focused. That's the "First week checklist" the old plan asked
for — it's now the course itself, tracked by the milestone ladder below.

---

## Milestones — "you can now…"

Tick these off as you go. They are the real measure of progress, not "I read the module".

- [ ] **M1 — The app runs.** You have Android Studio installed, this folder open in it, and
      Daybook running on an emulator or your phone. (end of Module 1)
- [ ] **M2 — You changed something visible.** You edited a piece of on-screen text, rebuilt, and
      saw your change in the running app. (Module 5)
- [ ] **M3 — You added a working setting.** You added a new toggle to a Settings screen that
      persists across app restarts, following the pattern in Module 16. (Module 16, lightweight version)
- [ ] **M4 — You changed the database safely.** You added a column to a table with a migration,
      regenerated the schema JSON, added a migration test, and all four build gates pass. (Module 16, full version)
- [ ] **M5 — You shipped a signed release.** You bumped `versionCode`, ran the 4-gate, produced a
      signed `assembleRelease` APK, and installed it on a device. (Module 17 + §"Releasing")
- [ ] **M6 — You can navigate the whole system.** Someone asks "what happens when the user taps ✓
      on a habit?" and you can trace it file by file without this document open. (reference section
      "Follow one tap")

---

## Part 0 — What this app is and why it exists

**Daybook is an offline-first personal tracker for habits and for what you eat / take / journal.**
It reminds you at times you set, and — this is the point — the reminder *asks you a question*
("What did you have?", "Did you do this?") and keeps the answer. Over time you get a log you never
had to consciously keep, plus streaks and simple stats.

**Who uses it:** originally me. One person with a chronic condition (Crohn's — that's why there's
a "red-flag food diary" with a none/maybe/red trigger marker) who wanted a low-friction way to
keep a food-and-symptom log and habit history without opening an app every time.

**The design philosophy, four rules, in priority order:**

1. **Offline and private.** The live data lives in a database *on the phone*. Not backing it up
   must never lose your history. There is no analytics, no tracking. Cloud sync exists but it's a
   *mirror* of the on-device truth, not the truth itself.
2. **Notification-first.** You should be able to do the whole day's logging from the notification
   shade — complete, skip, snooze, reply — without opening the app.
3. **Dark, calm, one-handed.** One dark theme (near-black `#0B0D0F`), pastel card tints, big tap
   targets in the bottom half of the screen, minimal animation. No light theme by deliberate
   decision.
4. **Low friction over features.** Every feature is optional and defaults to "invisible until you
   turn it on". A user who updates the app should see *no behaviour change* until they go looking.

Those four rules explain almost every design decision you'll meet in the code. When you're unsure
whether a change fits, check it against them.

**Current state:** versionName `0.5.5`, versionCode `13`, Room database schema **version 17**
(the journal-habit round bumped the DB schema — `MIGRATION_16_17` — without bumping
versionCode/versionName). Package name `com.daybook.app`. Latest built release:
`Daybook-v0.5.5-journal-habit-release.apk`.

> ### ⚠️ This folder is not a git repository
>
> There is **no version control here.** No history, no "undo the last change", no `git restore`.
> If you delete a file or break something and save, it is gone. Module 1's very first Try-it-now
> is to fix this with `git init`. Until you've done that, be extremely careful, and do not run the
> deletions in `DOC_CLEANUP_PROPOSAL.md`.

---

# MODULE 1 — Your machine, and running the app once

### Concept

An Android app is not like a Python script you run with `python foo.py`. It has to be *compiled*
(translated from Kotlin source into a format the phone understands), *packaged* into an `.apk`
file (a zip — more on that in Module 3), *installed* onto a device or a simulated device
("emulator"), and *launched* by the Android operating system. The tool that does the
compile-and-package step is **Gradle** (Module 4). The program you'll live in that drives all of
this is an IDE — an "Integrated Development Environment" — called **Android Studio**.

Android Studio bundles almost everything you need:

- a **code editor** (like a much smarter text editor),
- the **Android SDK** ("Software Development Kit") — the library of Android's own code your app
  calls into, plus command-line tools like `adb` (the "Android Debug Bridge", used to talk to a
  connected phone),
- an **emulator** — a full Android phone simulated on your computer,
- a **JDK** ("Java Development Kit") — Kotlin compiles down to the same bytecode as Java, so it
  needs a Java toolchain. This project wants **JDK 17** (JDK 21 also works).

This machine already has some of this set up outside Android Studio, because I built releases from
the command line. The important paths:

```
JAVA_HOME  = /home/abhiram/jdk/jdk-17.0.11+9      (JDK 17 — the one the build wants)
             /home/abhiram/.jdks/jbr-21.0.11      (JDK 21 — also works)
ANDROID_HOME = /home/abhiram/android-sdk
```

`local.properties` in the repo root already points at the SDK (`sdk.dir=/home/abhiram/android-sdk`).

### The Python you already know

| Python world | Android world |
|---|---|
| The `python` interpreter | The JDK + the Android runtime on the phone |
| `pip install requests` | Gradle downloading a dependency (Module 4) |
| Running `python foo.py` | Gradle building an APK, then `adb install`-ing and launching it |
| IDLE / VS Code | Android Studio |
| A virtualenv | The SDK + JDK pinned by `local.properties` / `JAVA_HOME` |

The mental jump: in Python the gap between "I edited the file" and "I see the result" is one
keystroke. Here it's a 30-second-to-3-minute build. That's normal. Module 4 explains why and how
to make it hurt less.

### See it in the real code

Nothing to read yet — this module is about your machine. But open these three files in a plain
text editor just to see they exist and are small:

- `local.properties` — one line, the SDK path.
- `keystore.properties` — four lines, the release signing credentials (see also
  `RELEASE_SIGNING.md`).
- `settings.gradle.kts` — declares the project is called "Daybook" and has one module, `:app`.

### Try it now

**1a. Put this folder under version control (do this first, before anything else).**

```bash
cd /home/abhiram/Downloads/app-for-food
git init
git add -A
git commit -m "Baseline: Daybook 0.5.5 (versionCode 13, DB v17) as received"
```

Now `git status` works, and `git restore <file>` or `git checkout -- <file>` can undo a mistake.
From here on, commit whenever something works.

> Note: `.gitignore` already exists and correctly excludes `build/`, `*.apk`, `local.properties`,
> `keystore.properties`, `*.jks`. So the first commit will *not* include the giant APK files or
> your secrets. Good.

**1b. Install Android Studio.** Download it from `developer.android.com/studio`. During first-run
setup let it install the default SDK and at least one system image for the emulator (pick a recent
API level — 34 or 35 is fine).

**1c. Open the project.** In Android Studio: *Open* → pick this folder
(`/home/abhiram/Downloads/app-for-food`). It will "sync Gradle" — this reads the build files and
downloads dependencies. First sync can take several minutes. If it complains it can't find a JDK,
point it at `/home/abhiram/jdk/jdk-17.0.11+9` in *Settings → Build, Execution, Deployment →
Build Tools → Gradle → Gradle JDK*.

**1d. Run it on an emulator.** *Device Manager* (right-hand toolbar) → *Create device* → pick a
phone (e.g. Pixel 6) and the system image you downloaded → Finish. Then press the green ▶ **Run**
button. It builds, boots the emulator, installs Daybook, launches it. You'll hit the Google
sign-in gate — signing in on an emulator needs a Google account added to the emulator (Settings →
Accounts on the emulator), or use a real device.

**1e. (Better) Run it on your own phone.** On the phone: *Settings → About phone → tap "Build
number" seven times* to unlock Developer Options, then *Settings → Developer options → enable USB
debugging*. Plug it in, accept the "allow debugging" prompt, pick it in the device dropdown in
Android Studio, press ▶.

**1f. Build once from the command line**, so you've seen the "real" way I did it:

```bash
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew assembleDebug
```

When it finishes, the APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### Checkpoint

1. Why can't you just "run" a `.kt` file the way you run a `.py` file?
2. What are the three things Android Studio bundles that you'd otherwise install separately?
3. If you delete `MainActivity.kt` by accident right now, how do you get it back? (After 1a: `git
   checkout -- app/src/main/java/com/daybook/app/ui/MainActivity.kt`. Before 1a: you don't — that's
   why 1a is first.)

---

# MODULE 2 — Kotlin for a Python person

### Concept

Kotlin is the language this whole app is written in. It's made by JetBrains (the Android Studio
people) and is *the* language Google recommends for Android. Compared to Python it is:

- **Statically typed** — every variable has a type known at compile time, and the compiler
  refuses to build if the types don't line up. This feels like bureaucracy at first and becomes
  your best friend: a whole class of bugs ("I passed a string where a number was expected") simply
  cannot happen.
- **Null-safe** — the type system tracks whether a value *can* be absent. `String` can never be
  null; `String?` might be. This kills the single most common crash in Java/Android history (the
  `NullPointerException`).
- **Compiled, not interpreted** — see Module 1.
- **Expression-oriented** — `if`, `when`, even a whole `{ }` block can *produce a value*, so you
  assign them to things.

You do not need to master Kotlin before continuing. You need enough to *read* this codebase. Here
is that enough.

### The Python you already know — side by side

**Variables: `val` and `var`**

```kotlin
val name = "Alex"      // like a Python name you never reassign; the compiler ENFORCES it
var count = 0          // reassignable
count = count + 1      // fine
// name = "Sam"        // COMPILE ERROR: val cannot be reassigned
```

```python
name = "Alex"          # nothing stops you writing name = "Sam" later
count = 0
count = count + 1
```

Prefer `val`. Most of this codebase is `val`. A `var` is a small flag that says "this changes".

**Types are written after the name, with a colon**

```kotlin
val n: Int = 5
val label: String = "hi"
val ratio: Double = 0.75
val done: Boolean = false
fun add(a: Int, b: Int): Int { return a + b }   // takes two Ints, returns an Int
```

Often the type is *inferred* and you omit it: `val n = 5` — the compiler knows it's `Int`. In
function signatures and public API you usually write it out for clarity.

**Null and the `?` `?:` `!!` family — "the billion-dollar mistake"**

The inventor of the null reference called it his "billion-dollar mistake". Kotlin's fix: a type
without `?` *cannot hold null*, checked at compile time.

```kotlin
val a: String  = "x"     // never null
val b: String? = null    // the ? means "or null"

// b.length            // COMPILE ERROR: b might be null
b?.length              // "safe call": evaluates to null if b is null, else b.length  -> type Int?
b?.length ?: 0         // "Elvis operator": use b?.length, but if that's null, use 0    -> type Int
b!!.length             // "I promise it's not null" — if it IS, you get a crash HERE, on this line
```

```python
b = None
# b.length            # AttributeError at runtime, somewhere, later, confusingly
len(b) if b is not None else 0   # the ?: pattern, spelled out
```

You will see `?.` and `?:` everywhere in this code. `!!` is used sparingly and deliberately —
each one is a claim the author is making. `getOrNull()`, `runCatching { }`, `.takeIf { }` are
related tools you'll meet.

**`fun` — functions**

```kotlin
fun greet(name: String): String {
    return "Hi, $name"
}

fun greetShort(name: String) = "Hi, $name"   // single-expression form, no braces, no return
```

```python
def greet(name):
    return f"Hi, {name}"
```

**String templates** — `$name` and `${expr}` inside a string, exactly like Python f-strings
(without the `f`):

```kotlin
val s = "You have $count items, ${count * 2} halves"
```

**`data class` — like `@dataclass`**

```kotlin
data class HabitDef(val id: String, val name: String, val snoozeMinutes: Int = 10)
```

```python
from dataclasses import dataclass
@dataclass
class HabitDef:
    id: str
    name: str
    snooze_minutes: int = 10
```

You get a constructor, `==` by value, a readable `toString()`, and `copy()` for free.
`val newDef = def.copy(name = "Renamed")` makes a new object with one field changed and everything
else the same. This codebase uses `.copy()` constantly — it's how you "change" an immutable object.

**`when` — like `if/elif/else`, but tighter, and it's an expression**

```kotlin
val label = when (status) {
    Status.PENDING   -> "waiting"
    Status.COMPLETED -> "done"
    Status.SKIPPED, Status.LOGGED -> "resolved"
    else -> "unknown"
}

when {                               // no subject: just a chain of conditions
    count == 0 -> println("none")
    count < 10 -> println("a few")
    else -> println("lots")
}
```

```python
if status == Status.PENDING:
    label = "waiting"
elif status == Status.COMPLETED:
    label = "done"
elif status in (Status.SKIPPED, Status.LOGGED):
    label = "resolved"
else:
    label = "unknown"
```

**Collections and `.map` / `.filter` — like list comprehensions**

```kotlin
val names  = habits.map { it.title }                       // [h.title for h in habits]
val active = habits.filter { !it.isArchived }              // [h for h in habits if not h.isArchived]
val firstBig = habits.firstOrNull { it.title.length > 20 } // next((h for h ... ), None)
val total  = habits.sumOf { it.snoozeIntervalMinutes }     // sum(h.snooze... for h in habits)
```

`it` is the implicit name for "the current element" when a lambda has one parameter. You can name
it: `habits.map { habit -> habit.title }`.

**Lambdas and the trailing-lambda rule**

A lambda is an anonymous function in `{ }`. Kotlin has one syntax quirk that confuses everyone at
first: **if the last argument to a function is a lambda, you write it *after* the parentheses**,
and if it's the *only* argument you drop the parentheses entirely.

```kotlin
list.filter({ it > 0 })   // legal but nobody writes it this way
list.filter { it > 0 }    // same thing, idiomatic

// two args, last is a lambda:
runCatching(block = { risky() })   // ->
runCatching { risky() }

button(onClick = { doThing() }, label = "Go")            // last arg not a lambda: normal
Column(modifier = Modifier.fillMaxSize()) { Text("hi") } // last arg (content) is a lambda: outside
```

This is *why* Compose code (Module 5) looks like `Column { Row { Text("x") } }` — those `{ }` are
trailing lambdas, not blocks.

**`class`, constructors, `object`**

```kotlin
class Counter(start: Int) {          // primary constructor params in the parentheses
    var value = start
    fun bump() { value++ }
}
val c = Counter(10)                  // no "new" keyword

object Config {                      // a SINGLETON — one instance, ever, created lazily
    const val MAX = 100
    fun describe() = "max is $MAX"
}
Config.describe()                    // called on the type itself, like a Python module-level fn
```

`object` is used in this codebase for stateless helper bundles (`NavConfig`, `ContentHash`,
`BackupStatus`) and for enum-like groupings (`Occurrence`, `Event`).

**`enum class` — a fixed set of named values**

```kotlin
enum class HabitType { INDIVIDUAL, BATCH, STREAK, JOURNAL }
val t = HabitType.BATCH
```

```python
from enum import Enum
class HabitType(Enum):
    INDIVIDUAL = 1
    BATCH = 2
    STREAK = 3
```

**Important project rule:** Room (the database, Module 7) stores an enum *by its name string*, and
UI code lists `Enum.entries` in declaration order. So **you may append a new enum value at the
end, but never reorder or rename existing ones** — you'd silently repoint stored data. Every enum
in `DataModel.kt` has a comment saying exactly this.

**`sealed class` / `sealed interface` — a closed set of *shapes*, each carrying different data**

```kotlin
sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data class Idle(val lastSyncedAtMillis: Long) : SyncStatus
    data object Syncing : SyncStatus
    data class Error(val message: String?) : SyncStatus
}
```

Think of it as an enum where each case can hold its own fields. When you `when` over a sealed
type, the compiler *knows the full list* and forces you to handle every case (or add `else`).
This codebase uses it for `SyncStatus`, `AuthState`, `HydrateResult`, `BootstrapAction`. There is
no clean Python equivalent — the nearest is a set of small dataclasses plus discipline.

**Extension functions — add a method to a type you don't own**

```kotlin
fun String.shout() = this.uppercase() + "!"
"hi".shout()   // "HI!"
```

```python
# Python: you'd write a module-level function shout(s) and call shout("hi")
```

You'll see these as top-level `fun SomeType.doThing(...)`. `isNoScheduleHabit`, the `Modifier.xxx`
chains in Compose, and `awaitCompat()` on a Firebase `Task` are all extension functions.

**`suspend`** — a function that can *pause* without freezing the app. Covered properly in Module 9;
for now, when you see `suspend fun`, read it as "this does slow work (database, network) and must
be called from a coroutine, not from normal code".

### See it in the real code

Open **`app/src/main/java/com/daybook/app/data/model/DataModel.kt`** and read the whole file
(~330 lines). It is the best Kotlin primer in the repo because it's almost all declarations:

- Lines ~13–49: `data class Habit`. Note every property is `val` with a default (`= ...`), the
  `@Entity` / `@ColumnInfo` annotations (Module 7), and the `String?` nullable types
  (`streakStartedAt: Long? = null`). The last field, `journalQuestionsJson`, is the journal-habit
  round's per-habit question list — a plain `String` column, not a separate table.
- Lines ~254–293: the enums — `ColorTag`, `TaskType`, `RedFlag`, `DayOfWeek`, `HabitType` — each
  with its "APPENDED, never reorder" comment and a `companion object` (a place for
  "static"/factory functions attached to the type, like `ColorTag.fromNameOrAuto(...)`). Note
  `HabitType` now has four values (`..., JOURNAL`) and `TaskType` still lists `JOURNAL` too — kept
  only for the append-only rule (an old backup's `TaskType.valueOf("JOURNAL")` must still decode);
  it's no longer offered anywhere in the UI (`FEATURES.md` §5 explains the retirement in full).
- Lines ~317–329: `object Occurrence` and `object Event` — using `object` purely as a namespace
  for a nested `enum class`.

Then open **`app/src/main/java/com/daybook/app/ui/NavConfig.kt`** (48 lines) — a pure `object`
full of small `val`/`fun`, `.split`, `.map`, `.filter`, `.indexOf`, a `when`-free `if` chain. This
is "just Kotlin", no framework. Read every line; you should understand all of it after this module.

### Try it now

**2a. Read and predict.** In `NavConfig.kt`, function `visibleRoutesFrom(csv: String?)`. Before
running anything, write down on paper what it returns for each input:
`"routines,home"`, `""`, `null`, `"home,foodmed,foodmed"`, `"garbage"`.
Then check yourself against the code and the doc comment.

**2b. Write a tiny Kotlin file and run it.** Kotlin ships a REPL-ish scratch mode in Android
Studio (*File → New → Scratch File → Kotlin*). Paste:

```kotlin
data class Habit(val title: String, val archived: Boolean, val snooze: Int = 10)

val habits = listOf(
    Habit("Water", false),
    Habit("Old thing", true, snooze = 30),
    Habit("Walk", false, snooze = 15),
)

val activeTitles = habits.filter { !it.archived }.map { it.title }
val totalSnooze  = habits.sumOf { it.snooze }
val renamed = habits[0].copy(title = "Drink water")

println(activeTitles)   // [Water, Walk]
println(totalSnooze)    // 55
println(renamed)        // Habit(title=Drink water, archived=false, snooze=10)
```

Run it (there's a ▶ in the scratch file gutter). Change things and re-run until the `.filter`,
`.map`, `.copy`, default-argument, and string-template behaviour is boring to you.

**2c. Break it on purpose.** In the scratch file, add `val x: Int = habits[0].title` and try to
run. Read the compile error. That red squiggle *is* static typing doing its job — the thing you
came here to get comfortable with.

### Checkpoint

1. What's the difference between `String` and `String?`, and what does `foo?.bar ?: baz` do?
2. Why is `habits.filter { !it.archived }.map { it.title }` one line here but two operations?
3. Why must you never reorder the values in `enum class TaskType`?

---

# MODULE 3 — What an Android app actually *is*

### Concept

There is **no `main()`** in an Android app. You don't control startup. Android does. Your app is
a bag of *components* that the OS instantiates when it needs them:

- **`Activity`** — roughly "one screen host / one window". Historically an app had many. A modern
  Compose app like this one has essentially **one**: `MainActivity`. Everything you see is Compose
  UI drawn *inside* that single Activity, with in-app navigation swapping what's on screen (Module
  12). The Activity has a lifecycle — `onCreate`, `onResume`, `onStop` — that Android calls.
- **`BroadcastReceiver`** — a small component the OS wakes up to hand it an event: "an alarm you
  set went off", "the device finished booting", "the user tapped the Skip button on a
  notification". This app has three: `AlarmReceiver`, `BootCompletedReceiver`,
  `NotificationActionReceiver` (Module 13).
- **`Application`** — a single object created once when your process starts, before any Activity.
  Good place for one-time setup. Ours is `DaybookApplication`.
- **`Service`, `ContentProvider`** — this app doesn't write its own (it uses a `FileProvider`
  for sharing exports and lets libraries register their own).

**The `AndroidManifest.xml` is the table of contents.** It's the one file Android reads to learn
what components your app has, which permissions it needs, and which Activity is the launcher (the
one that starts when you tap the icon). If a component isn't in the manifest, it doesn't exist as
far as the OS is concerned.

**An APK is a zip.** Rename `app-debug.apk` to `.zip`, unzip it, and you'll find: compiled code
(`classes.dex`), resources (`res/`), assets, the manifest (in binary form), and a signature. That
signature matters — see below.

**Resources (`res/`)** are the non-code parts of the app, referenced from code by generated IDs:

- `res/drawable/` — vector icons (`ic_notif_habit.xml`, `ic_skip.xml`, …), referenced as
  `R.drawable.ic_skip`.
- `res/mipmap-*/` — the launcher icon at various densities.
- `res/values/strings.xml` — user-facing text strings, referenced as `R.string.app_name`. (This
  app barely uses it — see the Try-it-now.)
- `res/font/` — the bundled typefaces for the font picker.
- `res/xml/` — small config files (`file_paths.xml` for the share provider, `backup_rules.xml`).

`R` is a class Gradle generates during the build containing an integer ID for every resource, so
`R.drawable.ic_skip` in code resolves to the right file.

**`applicationId` vs package name.** The `package` in a `.kt` file (`com.daybook.app.ui`) is a
code-organisation namespace, like a Python package. The **`applicationId`** in
`app/build.gradle.kts` (`com.daybook.app`) is the app's *global identity on the device and on the
Play Store* — two APKs with the same `applicationId` are "the same app" and one updates the other.
They happen to share the string `com.daybook.app` here, which is normal.

**Debug vs release build.** Two "build types":

- **debug** — what ▶ Run produces. Signed with a throwaway auto-generated key, not optimised,
  debuggable, larger.
- **release** — what you ship. Signed with *your* keystore (`keystore.properties` →
  `daybook-release.jks`), run through **R8** which shrinks and obfuscates the code
  (`isMinifyEnabled = true`, `isShrinkResources = true`), and *not* debuggable.

**Signing, and why the `.jks` must never change.** Every APK is cryptographically signed. Android
enforces that an update must be signed with the **same key** as the installed version — otherwise
it refuses the install (you'd have to uninstall first, losing local data). So the keystore file
`app/daybook-release.jks` is effectively permanent identity. **Do not regenerate it, do not lose
it.** Details and the "swapping in a production key" caveat are in **`RELEASE_SIGNING.md`** — read
that file now, it's one page.

### The Python you already know

| Python | Android |
|---|---|
| `if __name__ == "__main__":` | *nothing* — Android calls your components; you never call "the app" |
| `setup.py` / `pyproject.toml` metadata (name, version, entry points) | `AndroidManifest.xml` + `app/build.gradle.kts` |
| A `.whl` file (a zip of your package) | An `.apk` file (a zip of your app) |
| `import mypkg.submodule` | `package com.daybook.app.ui` |
| The PyPI project name | `applicationId` |
| Data files bundled via `package_data` | `res/` resources, referenced through `R` |

### See it in the real code

Open **`app/src/main/AndroidManifest.xml`** (~84 lines) and read all of it:

- Top: four `<uses-permission>` — `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`, `INTERNET` + `ACCESS_NETWORK_STATE`. The comment explains why it's
  `SCHEDULE_EXACT_ALARM` (user-grantable) and not `USE_EXACT_ALARM` (Play-restricted).
- `<application android:name=".DaybookApplication" ...>` — names our custom `Application` class.
- The single `<activity android:name=".ui.MainActivity">` with the
  `MAIN` / `LAUNCHER` intent-filter — *this is what makes tapping the icon open MainActivity*.
- `<provider ... FileProvider ...>` — lets the app hand an exported JSON file to the share sheet
  without needing a storage permission.
- Three `<receiver>` entries — `AlarmReceiver` (not exported: only our own alarms trigger it),
  `BootCompletedReceiver` (exported, with an intent-filter for `BOOT_COMPLETED`,
  `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, …), `NotificationActionReceiver` (not exported).

Then open **`app/src/main/res/values/strings.xml`** — three lines. The comment tells you the
Compose UI uses string literals directly in code, so `app_name` (needed by the manifest) is the
only entry. This is unusual (production apps externalise all strings for translation) and it's a
deliberate simplification here.

### Try it now

**3a. Unzip an APK.**

```bash
cd /home/abhiram/Downloads/app-for-food
mkdir -p /tmp/apk-peek && cd /tmp/apk-peek
unzip -o ../../app-for-food/Daybook-v0.5.5-journal-habit-release.apk >/dev/null || \
  unzip -o /home/abhiram/Downloads/app-for-food/Daybook-v0.5.5-journal-habit-release.apk >/dev/null
ls -la
```

You'll see `classes*.dex` (your compiled code), `res/`, `AndroidManifest.xml` (binary),
`META-INF/` (the signature). That's the whole app.

**3b. Find the launcher declaration.** In `AndroidManifest.xml`, locate the `<intent-filter>`
containing `android.intent.action.MAIN` and `android.intent.category.LAUNCHER`. Delete those two
lines *in your head* — what would happen? (Answer: the app installs but has no icon in the
launcher and can't be started normally.)

**3c. Read `RELEASE_SIGNING.md` end to end.** Then answer: if you build a release APK on a machine
that doesn't have `keystore.properties`, what key does it get signed with, and why is that a
problem for shipping an update? (Answer in that file: it falls back to the debug key; a real
update signed with the debug key won't install over one signed with the release key.)

### Checkpoint

1. Where does Android look to find out your app has a `BootCompletedReceiver`?
2. What's the practical difference between the `package` line in a `.kt` file and the
   `applicationId` in `build.gradle.kts`?
3. Why can you never replace `app/daybook-release.jks` with a freshly generated keystore once the
   app is on someone's phone?

---

# MODULE 4 — Gradle, and building

### Concept

**Gradle is three tools in one:** `pip` (it downloads your dependencies), a `Makefile` (it knows
the steps to turn source into an APK and which steps depend on which), and a script runner (you
invoke named "tasks" like `assembleDebug`).

The build is configured by **Kotlin scripts** (`.gradle.kts` files — yes, the build config is
itself Kotlin):

- **`settings.gradle.kts`** (repo root) — names the project (`Daybook`), lists its modules
  (`include(":app")`), and declares where to download dependencies from (`google()`,
  `mavenCentral()`).
- **`build.gradle.kts`** (repo root) — the *top-level* build file. Here it only declares which
  Gradle *plugins* exist and at what version (Android, Kotlin, the Compose compiler, Hilt, kapt,
  serialization, google-services) with `apply false` — meaning "know about these, but don't apply
  them here".
- **`app/build.gradle.kts`** — the **module** build file, the one you'll actually edit. This is
  where `applicationId`, `versionCode`, `versionName`, `minSdk`, `compileSdk`, the signing config,
  the `release`/`debug` build types, and — crucially — the **`dependencies { }`** block live.

**`dependencies { }`** is the `requirements.txt` equivalent. Each line pulls in a library:

```kotlin
implementation("androidx.room:room-runtime:2.6.1")   // "group:artifact:version"
kapt("androidx.room:room-compiler:2.6.1")             // an annotation processor (see below)
implementation(platform("androidx.compose:compose-bom:2024.12.01"))  // a "BOM": a version bundle
```

`implementation("...")` ≈ `pip install`. `platform(... "-bom")` is a "Bill of Materials" — a
single pinned set of mutually-compatible versions for a whole family (all the Compose libraries),
so the individual Compose lines don't need version numbers.

**The wrapper: `./gradlew`.** You never install Gradle. The repo ships a small script (`gradlew`
on Unix, `gradlew.bat` on Windows) plus `gradle/wrapper/` which downloads and runs the *exact*
Gradle version this project expects (8.6). Always build with `./gradlew ...`, never a
system-installed `gradle`.

**Why builds are slow.** Three reasons, all present here:

1. **kapt** — "Kotlin Annotation Processing Tool". Room and Hilt work by *reading annotations*
   (`@Entity`, `@Dao`, `@Inject`, `@HiltViewModel`) at build time and *generating Kotlin/Java
   source* — the actual SQL implementation, the dependency-injection wiring. kapt runs the Kotlin
   compiler twice-ish and is the single biggest time sink. (The newer, faster replacement is
   **KSP**; this project deliberately stays on kapt because Room 2.6.1 + Hilt 2.51 + K2 is a
   known-good combination — see the comment in the top-level `build.gradle.kts`.)
2. **R8** — the release-only shrink/obfuscate/optimise pass. Only bites `assembleRelease`.
3. **Cold start** — the first build after opening the project, or after `./gradlew clean`, has no
   caches.

**The daemon.** Gradle keeps a background JVM ("the daemon") alive between builds so the second
build reuses a warm process. This is why the *first* command-line build in a session is slow and
the next is much faster.

**Where the APK lands:**

```
app/build/outputs/apk/debug/app-debug.apk         <- ./gradlew assembleDebug
app/build/outputs/apk/release/app-release.apk      <- ./gradlew assembleRelease  (signed if keystore.properties present)
```

**The exact build commands I used** (the "4-gate" — Module 17 explains why all four, every time):

```bash
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew \
  testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

JDK 21 at `/home/abhiram/.jdks/jbr-21.0.11` also works as `JAVA_HOME`.

### The Python you already know

| Python | Gradle |
|---|---|
| `requirements.txt` / `pyproject.toml [dependencies]` | the `dependencies { }` block in `app/build.gradle.kts` |
| `pip install -r requirements.txt` | `./gradlew` resolving dependencies on sync |
| `python -m build` | `./gradlew assembleRelease` |
| `pytest` | `./gradlew testDebugUnitTest` |
| `make clean` | `./gradlew clean` |
| `tox` / running several checks | the 4-gate one-liner above |
| pinning `requests==2.31.0` | `"androidx.room:room-runtime:2.6.1"` |
| A tool that reads decorators and writes code (rare in Python) | kapt reading `@Entity` / `@Inject` and generating source |

### See it in the real code

Open **`app/build.gradle.kts`** and read it top to bottom (~130 lines):

- The `plugins { }` block — the same list as the root file, now *applied* (no `apply false`).
- `val keystoreProps = Properties().apply { ... }` — plain Kotlin, at the top of the script,
  loading `keystore.properties` if it exists. `hasReleaseSigning` gates the whole signing config.
- `android { }`:
  - `namespace`, `compileSdk = 34`.
  - `defaultConfig { applicationId; minSdk = 26; targetSdk = 34; versionCode = 13;
    versionName = "0.5.5" }` — **these four lines are what you bump for a release.** Note the
    inline comment tying versionCode/Name to the feature set.
  - `javaCompileOptions { ... "room.schemaLocation" ... }` — tells Room to *export* the schema as
    JSON into `app/schemas/` on every build (Module 15 depends on this).
  - `sourceSets { getByName("androidTest") { assets.srcDir("$projectDir/schemas") } }` — ships
    those JSONs into the instrumented-test APK so `MigrationTest` can load them.
  - `signingConfigs`, `buildTypes { release { isMinifyEnabled = true; ... proguardFiles(...) } }`.
  - `buildFeatures { compose = true; buildConfig = true }` — turns on the Compose compiler and
    generates `BuildConfig` (used to stamp `versionName` into backup files).
- `dependencies { }` — every line has a comment saying *why that exact version* and what breaks if
  you float it. Read them; they encode a lot of hard-won knowledge (compileSdk 34 ceilings on
  Firebase BOM, biometric, credentials, security-crypto).

Also open **`build.gradle.kts`** (root, ~30 lines) and **`gradle.properties`** (~12 lines — JVM
heap, `org.gradle.parallel`, `org.gradle.caching`, kapt worker settings).

### Try it now

**4a. Time a warm vs cold build.**

```bash
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
time ./gradlew assembleDebug            # warm-ish
./gradlew clean
time ./gradlew assembleDebug            # cold — notice how much longer
```

**4b. Read a dependency's justification.** Find the `androidx.biometric:biometric:1.1.0` line in
`app/build.gradle.kts`. The comment explains three consequences of that exact version. Summarise
them in your own words. (This is the kind of comment you should *write* when you pin something.)

**4c. List the tasks.** `./gradlew tasks` prints every task. Find `assembleDebug`,
`assembleRelease`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `clean`,
`lintVitalRelease`. These six are your whole working vocabulary.

**4d. Do NOT do this, but understand why:** `while pgrep -f 'gradle ...'; do sleep 5; done` to
"wait for a build to finish" **hangs forever**, because the `pgrep` pattern matches the watch
command's own arguments. Run Gradle in the foreground and let it finish. (This is in the footguns
list too — it bit me.)

### Checkpoint

1. What are the three jobs Gradle is doing that `pip` alone doesn't?
2. Which file do you edit to add a new library, and what does the `implementation("a:b:c")` string
   mean?
3. Why is `assembleRelease` slower than `assembleDebug` even on a warm daemon?

---

# MODULE 5 — Jetpack Compose: the UI is a function of your data

### Concept

Old Android UI: you defined screens in XML, then in code you *found* a widget by id and *mutated*
it (`textView.setText("hi")`). Every state change was a manual poke. Bugs came from the screen and
the data drifting apart.

**Compose flips it.** You write a **function** that takes your data and *describes* what the
screen should look like for that data. When the data changes, the framework **re-runs your
function** and updates only what changed. You never hold a reference to a widget; you never call
`setText`. The screen is, always, `f(currentData)`.

A UI function is marked **`@Composable`**:

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hi, $name")
}
```

Calling `Greeting("Alex")` doesn't *return* anything you use — it *emits* UI into the tree being
built. Composables call other composables (`Column { Row { Text(...) } }`) to build the screen.
Those `{ }` are trailing lambdas (Module 2) — `Column`'s last parameter is
`content: @Composable () -> Unit`.

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

Order matters (padding before vs after background changes the result). `.dp` is
"density-independent pixels" — a unit that looks the same physical size on any screen density.

**Recomposition** is the name for "the framework re-ran your composable because something it reads
changed". It's cheap and frequent. Your composables must therefore be:

- **fast** (no database calls, no heavy work — that's the ViewModel's job, Module 10),
- **side-effect-free** in the body (don't start a network call directly in a composable; use
  `LaunchedEffect` for that),
- **idempotent** (running twice with the same input gives the same UI).

**Contrast with Python's `print`.** `print("hi")` fires once and the character stream is gone;
there's no "the terminal is a function of my data". Compose is the opposite: there is no
fire-and-forget draw, only "here is what the screen *is*, for this data, right now — and it'll be
recomputed whenever the data moves."

State (`remember`, `mutableStateOf`) is Module 6. This module is just: *what a composable is*.

### The Python you already know

Closest analogy: a **template function** that returns HTML.

```python
def greeting_html(name):
    return f"<p>Hi, {name}</p>"

def page_html(user):
    return "<div>" + greeting_html(user.name) + card_html(user) + "</div>"
```

You compose small functions into big ones, each a pure function of its input. Compose is that,
except (a) it "emits" instead of returning a string, and (b) a runtime watches which inputs each
function read and re-invokes exactly the ones whose inputs changed. Imagine a web framework that
auto-re-renders just the `<p>` when `user.name` changes, without you wiring anything. That's
recomposition.

### See it in the real code

Open **`app/src/main/java/com/daybook/app/ui/onboarding/OnboardingScreen.kt`** and read the whole
file (~138 lines) — it's the simplest full screen in the app.

- `@Composable fun OnboardingScreen(onComplete: (name: String) -> Unit, initialName: String = "")`
  — a composable that takes a **callback** (`onComplete`) it will call when the user is done, and
  a plain `String` default. Screens in this app never touch navigation or the database directly —
  they take callbacks and hand data upward. This is deliberate (testability, reuse).
- `var name by rememberSaveable(initialName) { mutableStateOf(initialName) }` — the one piece of
  state (Module 6 — for now: "a `name` string that, when reassigned, re-runs this function").
- `Column( modifier = Modifier.fillMaxSize().background(...).statusBarsPadding() ) { ... }` — the
  root layout. Inside, another `Column` with `.weight(1f)` (take all remaining vertical space) and
  `.verticalScroll(...)`.
- A `Row { listOf(CardTints.Lavender, ...).forEach { t -> Box(...) } }` — building four coloured
  boxes from a list with `forEach`, each `Box` sized with `Modifier.weight(1f).height(...)`.
- `DaybookTextField(value = name, onValueChange = { name = it }, ...)` — the classic Compose
  pattern: the field is *told* its current value (`value = name`) and *reports* changes back
  (`onValueChange = { name = it }`). The field does not hold its own text; `name` does. This is
  "state hoisting".
- `StickySaveBar { PrimaryButton(text = "Get started", onClick = { onComplete(name) },
  enabled = name.isBlank().not()) }` — a pinned bottom bar; the button is disabled until `name`
  is non-blank, and on click it invokes the callback with the current `name`.
- `@Composable private fun FeatureCard(...)` at the bottom — a small private composable reused
  three times. Note it's a plain function; `private` works exactly like Kotlin `private`.

Now open **`app/src/main/java/com/daybook/app/ui/components/Components.kt`** lines **53–114**, the
`SoftCard` composable — the single card primitive every screen uses:

- It takes a `CardTint` (a colour bundle), an optional `onClick`, and
  `content: @Composable ColumnScope.() -> Unit` (the trailing lambda — the card's insides).
- `val interaction = remember { MutableInteractionSource() }` and
  `val pressed by interaction.collectIsPressedAsState()` — tracks whether the card is currently
  pressed.
- `val scale by animateFloatAsState(if (pressed) 0.97f else 1f, ...)` — an animated number that
  eases from 1.0 to 0.97 when pressed. Reading `scale` in the body means recomposition follows the
  animation.
- The comment at `val scaleMod = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }` is
  important: it's applied **unconditionally** (never behind an `if`) because a conditional
  modifier chain rebuilds twice per tap. This is a real footgun (it's in §Footguns). Leave that
  `graphicsLayer` alone.
- The final `Column(modifier = modifier.then(scaleMod).then(shadowMod).clip(...).background(fill)
  .border(...).then(clickMod).padding(...), content = content)` — the modifier chain assembled in
  a deliberate order.

### Try it now

**5a. M2 — change a visible string and see it.** Open `OnboardingScreen.kt`. Find
`BigHeadline("Welcome to Daybook", ...)` (~line 83). Change it to `"Welcome to Daybook!"`. Rebuild
and run (▶, or `./gradlew installDebug` with a device attached). You'll only see the onboarding
screen on a *fresh* install with a blank Google display name — easier: temporarily also change the
`FeatureCard` titles (~lines 102–104) which you can't see either, OR pick a string that *is*
visible on the main app. Better target: open
`app/src/main/java/com/daybook/app/ui/settings/SettingsScreen.kt`, find `title = "App lock"`
(~line 266), change it to `"App lock 🔒"`, rebuild, open Settings in the app. **That's M2.**
Revert it afterwards (or `git checkout -- ...`).

**5b. Add a log line in a composable and watch it fire.** In `SoftCard` (`Components.kt`), just
inside the function body, add:

```kotlin
android.util.Log.d("SoftCardTrace", "recompose: pressed=$pressed scale=$scale")
```

Rebuild, run, open a screen full of cards (Habits), press and release one while watching
*Logcat* (bottom of Android Studio, filter to `SoftCardTrace`). You'll see it fire many times as
`scale` animates — that's recomposition, live. **Remove the log line when done.**

**5c. Add a second chip to a row.** In `OnboardingScreen.kt` the tint strip builds from
`listOf(CardTints.Lavender, CardTints.Peach, CardTints.Mint, CardTints.Butter)`. Add
`CardTints.Rose` to that list. Rebuild. The strip now has five cells (each still `weight(1f)`, so
they share the width). Revert.

### Checkpoint

1. In Compose, when the data behind a screen changes, what does the framework do — and what do you
   *not* have to do?
2. In `DaybookTextField(value = name, onValueChange = { name = it })`, where does the typed text
   actually live?
3. Why must a `@Composable` function body not do a database query?

---

# MODULE 6 — State, `remember`, and recomposition

### Concept

A composable re-runs (recomposes) when a **state** value it *reads* changes. So you need a way to
create state that the runtime tracks.

- **`mutableStateOf(x)`** — creates an observable holder starting at `x`. Reading `.value` in a
  composable subscribes that composable to changes; writing `.value` triggers recomposition of
  every reader.
- **`remember { ... }`** — "run this block once, on first composition, and give me back the same
  result on every later recomposition." Without `remember`, you'd rebuild the state holder every
  recomposition and it would never appear to change. So the idiom is always
  `remember { mutableStateOf(x) }`.
- **`by`** — a Kotlin "property delegate" that lets you write `var name by remember {
  mutableStateOf("") }` and then use `name` directly (read and assign) instead of `name.value`.
  Pure sugar.
- **`rememberSaveable`** — like `remember`, but also survives the Activity being destroyed and
  recreated (screen rotation, process death). Use it for genuine user input you'd hate to lose;
  plain `remember` for transient UI state (is this menu open right now).
- **`LaunchedEffect(key) { ... }`** — "when this composable first appears (or when `key` changes),
  run this suspend block in a coroutine tied to the composable's lifetime." This is where you
  *start* things: a one-off load, an animation, reacting to a new argument. It's the escape hatch
  for the "no side effects in the body" rule.
- **`derivedStateOf { ... }`** — a computed state that only notifies readers when its *result*
  changes, not every time an input twitches. Used in `MainActivity` to read the pager's settled
  page without recomposing on every drag frame.

**Recomposition rules of thumb:**

- Keep state as *low* in the tree as possible (only the subtree that needs it recomposes).
- "Hoist" state up only when two siblings need to share it or a parent needs to control it
  (that's what `OnboardingScreen` does with `name`).
- Never mutate a list in place and expect a recomposition — assign a new list, or use the
  snapshot-aware `mutableStateListOf`.

### The Python you already know

There isn't a clean one — Python has no reactive UI runtime. The nearest mental model:

```python
# imagine an object that, whenever you set .value, calls every function registered as a "reader"
class State:
    def __init__(self, v): self._v = v; self._readers = set()
    @property
    def value(self):
        _current_composable and self._readers.add(_current_composable)  # subscribe
        return self._v
    @value.setter
    def value(self, v):
        self._v = v
        for r in self._readers: schedule_rerun(r)                       # recompose
```

`remember { }` is "memoise this per-call-site so it isn't recreated each render". `LaunchedEffect`
is "start this background coroutine when the widget mounts, cancel it when it unmounts" — like a
React `useEffect` if you've seen that.

### See it in the real code

**`app/src/main/java/com/daybook/app/ui/MainActivity.kt`**, the `setContent { ... }` block,
roughly **lines 132–316**:

- `var showRationale by remember { mutableStateOf(false) }` (and `permanentlyDenied`,
  `notifFlowResolved`, `showExactAlarmDialog`) — plain transient UI flags for the permission
  dialogs.
- `val notifPermLauncher = rememberLauncherForActivityResult(...) { granted -> ... }` — a
  remembered handle to "ask the OS for a permission and get the answer back in this lambda".
- `LaunchedEffect(Unit) { ... }` (~line 152) — runs **once** when the UI first composes (`Unit`
  never changes). It checks whether `POST_NOTIFICATIONS` is granted and, if not, decides between
  launching the system prompt, showing a rationale, or pointing at settings.
- `LaunchedEffect(notifFlowResolved) { ... }` (~line 216) — runs again **each time
  `notifFlowResolved` flips**, sequencing the exact-alarm ask *after* the notification decision so
  two system dialogs never stack.
- Down in `MainApp()` (~line 389):
  `val settledPage by remember { derivedStateOf { pagerState.currentPage } }` — the comment
  explains this keeps the scaffold from recomposing on every drag frame; it only recomposes when
  the page *settles*.

Also re-open **`OnboardingScreen.kt`**:
`var name by rememberSaveable(initialName) { mutableStateOf(initialName) }` — `rememberSaveable`
so a rotation mid-typing doesn't wipe the name; keyed on `initialName` so if the caller passes a
new seed the field resets.

### Try it now

**6a. Add a throwaway counter to a screen.** In `SettingsScreen.kt`, inside the top-level
`SettingsScreen` composable body, add:

```kotlin
var taps by remember { mutableStateOf(0) }
```

and somewhere visible in its content (e.g. just after the header) add:

```kotlin
androidx.compose.material3.Text(
    "debug taps: $taps",
    modifier = Modifier.clickable { taps++ }
)
```

Rebuild, open Settings, tap the text, watch the number climb — that's `mutableStateOf` +
recomposition. Now rotate the phone: the count resets to 0 (plain `remember`). Change `remember`
to `rememberSaveable`, rebuild, rotate again: it survives. **Revert both edits.**

**6b. Prove `remember` is doing something.** Temporarily remove the `remember { }` wrapper so it's
`var taps by mutableStateOf(0)` (this won't even compile cleanly as a delegate — note the error,
then) try `val taps = mutableStateOf(0)` and `taps.value++` in the click. It'll tick up once and
then appear stuck, because every recomposition rebuilds a fresh `mutableStateOf(0)`. Put the
`remember` back.

**6c. Read `LaunchedEffect` for real.** In `MainActivity.kt`, find the `LaunchedEffect(Unit)` and
the `LaunchedEffect(notifFlowResolved)`. Write one sentence each: *when does this block run, and
why is that the right key?*

### Checkpoint

1. Why is it always `remember { mutableStateOf(...) }` and never just `mutableStateOf(...)` in a
   composable?
2. When would you choose `rememberSaveable` over `remember`?
3. What does `LaunchedEffect(someValue) { }` do differently from putting the same code directly in
   the composable body?

---

# MODULE 7 — Room and the data model: Kotlin classes ↔ database tables

### Concept

**Room** is a library that turns annotated Kotlin classes into **SQLite** tables and gives you
type-safe methods to query them. SQLite is a small database engine built into Android; every app
gets its own file. Think of Room as "an ORM" (if you've met Django's models) or "a dict that
survives the app being killed and restarted".

Three pieces:

- **`@Entity data class`** — one class = one table. Each `val` = one column, tagged with
  `@ColumnInfo(name = "...")`. `@PrimaryKey` marks the unique-id column. `@Index` adds a database
  index (makes some queries fast; costs a little on writes).
- **`@Dao interface`** ("Data Access Object") — an interface where each method is annotated with
  `@Query("SELECT ...")`, `@Insert`, `@Update`, `@Delete`. Room *generates the implementation* at
  build time (this is the kapt step). **The SQL lives here.**
- **`@Database abstract class`** — lists all entities, declares a `version` number, and exposes
  the DAOs. There is exactly one: `AppDatabase`, currently `version = 17`.

**The frozen-schema rule (full treatment in Module 15, but internalise it now):** once a database
schema version has *shipped* to a user, it is frozen forever. You cannot change an `@Entity` and
just rebuild — the app on the user's phone has real data in the old shape. To change the schema
you write a **migration** (SQL that transforms v15 → v16), bump the `version`, and let the build
regenerate the schema JSON. This app is on migration #16 (`MIGRATION_16_17`) and it shows.

**This app's tables** (all in `data/model/DataModel.kt`):

| Table | What it is |
|---|---|
| `habits` | one row per habit (title, times, active days, type, icon, tint, snooze, streak fields, per-habit `journal_questions_json`, …) |
| `habit_occurrences` | one row per *scheduled instance* of a habit (a specific date+time slot), with a `status` (PENDING / COMPLETED / SKIPPED / LOGGED — a Journal habit's answered slot is `LOGGED`), plus `qa_json` for an answered Journal habit slot |
| `habit_events` | append-only log: SHOWN / USER_SNOOZED / COMPLETED / SKIPPED / REPLIED, with a timestamp |
| `food_med_tasks` | one row per intake reminder (Food / Med / Custom — Journal is retired as an Intake type, journal-habit round), plus red-flag defaults, prompt text, motivation |
| `food_med_occurrences` | scheduled instances; carry the reply text, red-flag, journal `qa_json` (legacy — see `FEATURES.md` §5a), … |
| `food_med_events` | the same append-only log shape as `habit_events` |
| `app_settings` | **a single row** (`id = 1`) holding every device-local preference |
| `custom_categories` | reusable Custom-category names; primary key *is* the name (self-deduplicating) |
| `custom_prompts` | reusable prompt messages; same trick |

`journal_questions` — the old global ordered journal question set — **is gone**: dropped by
`MIGRATION_16_17`. A Journal habit's questions now live on `habits.journal_questions_json` instead
of a separate table.

The **habit / occurrence / event** three-table shape is the heart of the app. A habit is the
*rule*. An occurrence is *one time that rule fired* (or should have). An event is *a thing that
happened to an occurrence*. The intake side mirrors it exactly on purpose — same shape means
export/import and stats are shared code, not two parallel systems.

**Why occurrences are separate rows and not computed on the fly:** so history is real, so a
notification has something concrete to attach to, and so "you completed this at 8:03am on the
14th" is a fact in the database, not a re-derivation that could change if you alter the schedule
later.

### The Python you already know

```python
import sqlite3
conn = sqlite3.connect("daybook.db")
conn.execute("""CREATE TABLE habits (id TEXT PRIMARY KEY, title TEXT NOT NULL, snooze_interval_minutes INTEGER)""")
conn.execute("INSERT INTO habits VALUES (?, ?, ?)", (uuid4().hex, "Water", 10))
rows = conn.execute("SELECT * FROM habits WHERE is_archived = 0").fetchall()
```

Room is that, but:

- the `CREATE TABLE` is *generated from* your `@Entity data class` — you never write it by hand
  (except in a migration),
- the `SELECT` string lives in a `@Dao` method and Room checks it compiles against your schema at
  build time (a typo'd column name is a build error, not a 2am crash),
- results come back as your `data class`, not tuples,
- and there's a hard rule that you can't just edit the `CREATE TABLE` after shipping — you migrate.

### See it in the real code

**`app/src/main/java/com/daybook/app/data/model/DataModel.kt`** — re-read it now with database
eyes:

- `@Entity(tableName = "habits") data class Habit(...)` — each `@ColumnInfo(name = "...")`. Note
  `@ColumnInfo(name = "type", defaultValue = "INDIVIDUAL")` — a *schema-level* default (written
  into the SQL). Compare `@ColumnInfo(name = "streak_started_at") val streakStartedAt: Long? = null`
  — nullable, **no** schema `defaultValue`; the `= null` is only a Kotlin default. That distinction
  matters for migrations and for sync (Module 14). Every added field has a comment saying which
  precedent it follows.
- `@Entity(tableName = "habit_occurrences", indices = [ Index(value = ["habit_id",
  "scheduled_for"], unique = true), Index(value = ["scheduled_for"]), Index(value = ["status",
  "scheduled_for"]) ])` — three indexes, each with a comment explaining which slow query it fixed.
- `data class AppSettings(@PrimaryKey val id: Long = 1, ...)` — the single-row settings table; the
  bottom third is the customization round's 20 device-local columns, each
  `@ColumnInfo(defaultValue = ...)` byte-matching a line in `MIGRATION_15_16`.

**`app/src/main/java/com/daybook/app/data/local/AppDatabase.kt`** — `@Database(entities = [
Habit::class, HabitOccurrence::class, HabitEvent::class, FoodMedTask::class,
FoodMedOccurrence::class, FoodMedEvent::class, AppSettings::class, CustomCategory::class,
CustomPrompt::class ], version = 17, exportSchema = true)` and one `abstract fun` per DAO. Note
there's no `JournalQuestion::class` any more — that entity (and its DAO) was deleted along with
the table in the journal-habit round.

**A DAO:** open **`app/src/main/java/com/daybook/app/data/local/HabitDao.kt`** (short). See
`@Query("SELECT * FROM habits WHERE is_archived = 0 ...")`, `@Insert`, `@Update`, and the targeted
single-column updates like `@Query("UPDATE habits SET prompt_message = :v WHERE id = :id")
suspend fun updatePromptMessage(id: String, v: String?)`. Note some methods return `Flow<...>`
(Module 9) and some are `suspend` (Module 9) and some are plain.

**`app/schemas/com.daybook.app.data.local.AppDatabase/17.json`** (the current, latest one — `16.json`
and earlier are just history) — open it, scroll. This is the *generated* description of the v17
schema: every table's `createSql`, every column, every index, and an `identityHash`
(`ab87929e759dfe8996bb20df8447a99a`). You never edit this file; the build writes it. Module 15 is
all about it.

### Try it now

**7a. Inspect the real database from a running app.** With the app running on an emulator/device
via Android Studio, open *App Inspection* (bottom toolbar) → *Database Inspector* → pick
`daybook_database`. You can browse `habits`, `habit_occurrences`, `habit_events` and run live
`SELECT`s. Create a habit in the app, watch a row appear in `habits`; wait for/trigger a reminder,
watch `habit_occurrences` and `habit_events` fill in.

**7b. Trace one habit through three tables.** In the Database Inspector, run:

```sql
SELECT id, title, type, times_json FROM habits;
SELECT id, habit_id, scheduled_for, status FROM habit_occurrences ORDER BY scheduled_for LIMIT 20;
SELECT occurrence_id, action, timestamp FROM habit_events ORDER BY timestamp DESC LIMIT 20;
```

Confirm you can see: a rule, its scheduled instances, and the log of what happened to them.

**7c. Read a DAO query and predict its shape.** In `HabitOccurrenceDao.kt`, find
`getNextPendingForHabit(...)`. What does it return, and what does "next pending" mean in SQL
terms? (Look for `WHERE ... status = 'PENDING' AND scheduled_for >= :floor ORDER BY scheduled_for
LIMIT 1`.)

### Checkpoint

1. What's the difference in responsibility between the `habits` table and the `habit_occurrences`
   table?
2. Where is the SQL for "get all non-archived habits" actually written?
3. What's the practical difference between `@ColumnInfo(defaultValue = "0")` and a plain Kotlin
   `= false` default on an entity property?

---

# MODULE 8 — The DAO/Repository split

### Concept

The DAO is the raw database interface. But the rest of the app — ViewModels, the scheduler, the
sync engine — does **not** talk to DAOs directly. It talks to a **Repository**.

A repository is a plain `@Singleton class` that wraps one or more DAOs and exposes app-meaningful
operations. It's a boring middle layer, and that's the point:

- It's the **one place** to put "get active habits" logic so it isn't copy-pasted.
- It's the **seam** for swapping the data source. The original architecture doc's whole plan for
  "add cloud sync later without rewriting the app" rests on this: ViewModels call
  `habitRepository.observeActiveHabits()`, and whether that's backed by Room, or Room + a sync
  decorator, is invisible to them.
- It keeps DAOs (and Room types) from leaking into the UI layer.

In this app the repositories are thin (they mostly forward to a DAO). That's fine — a thin
consistent layer still buys you the seam and the single home.

### The Python you already know

```python
class HabitRepository:
    def __init__(self, db): self._db = db
    def active_habits(self):
        return self._db.execute("SELECT * FROM habits WHERE is_archived = 0").fetchall()
    def archive(self, habit_id):
        self._db.execute("UPDATE habits SET is_archived = 1 WHERE id = ?", (habit_id,))

# the rest of your program uses HabitRepository, never touches `db` directly
```

That's exactly it. The "never touches `db` directly" convention is the whole value.

### See it in the real code

**`app/src/main/java/com/daybook/app/data/HabitRepository.kt`** — 64 lines, read all of it:

- `@Singleton class HabitRepository @Inject constructor(val database: AppDatabase)` — Hilt
  (Module 11) supplies the `AppDatabase`.
- `suspend fun getActiveHabits(): List<Habit> = database.habitDao().getActiveHabits().first()` —
  forwards to the DAO, turning a `Flow` into a one-shot list with `.first()`.
- `fun observeActiveHabits() = database.habitDao().getActiveHabits()` — exposes the live `Flow`
  for the UI.
- `suspend fun archiveHabit(id: String) = database.habitDao().archiveHabit(id)` — one-liners.
- `suspend fun markStreakBroken(id: String, ...)` — the one method with real logic: read the
  habit, compute the run length with `daysSince`, write `max(longest, run)` and clear the start.
  This is the kind of thing that belongs in a repository, not a DAO and not a ViewModel.

**`app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt`** — 65 lines. Note the pattern:
`ensureRow()` then a targeted single-column DAO update, one method per setting
(`setWeekStart`, `setClock24h`, …). And `observeSettings(): Flow<AppSettings>` for the reactive
read. When you add a setting in Module 16 you'll add a method here.

**`app/src/main/java/com/daybook/app/di/DatabaseModule.kt`** — see how repositories are *provided*:
`@Provides @Singleton fun provideHabitRepository(database: AppDatabase): HabitRepository =
HabitRepository(database)`. (Module 11.)

### Try it now

**8a. Add a read-only repository method.** In `HabitRepository.kt`, add:

```kotlin
suspend fun countActiveHabits(): Int = database.habitDao().getActiveHabits().first().size
```

It should compile with no other change. You won't call it yet — the point is to feel how small
the surface is. (Keep it or revert; harmless.)

**8b. Follow a write end to end.** Pick `archiveHabit`. Search the codebase (`Ctrl+Shift+F` in
Android Studio) for `archiveHabit`. You'll find: `RoutinesViewModel.archiveHabit` →
`habitRepository.archiveHabit(id)` → `HabitDao.archiveHabit` (`@Query("UPDATE habits SET
is_archived = 1 ...")`). Three hops, each layer's job visible.

**8c. Spot the seam.** Open any ViewModel (e.g. `RoutinesViewModel.kt`) and confirm it imports
`HabitRepository`, not `HabitDao` or `AppDatabase`. Grep the whole `ui/` tree for `habitDao(` —
you should find nothing. That "nothing" is the architecture working.

### Checkpoint

1. Why don't ViewModels call DAOs directly?
2. Where would you add "give me the 3 habits with the longest current streak" — DAO, repository,
   or ViewModel — and why?
3. What does `.first()` do to a `Flow` in `getActiveHabits()`?

---

# MODULE 9 — Flow, StateFlow, `suspend`, coroutines

### Concept

Two intertwined ideas: **coroutines** (how the app does slow work without freezing) and **Flow**
(how the app gets a *stream* of values over time, e.g. "the list of habits, and every future
version of it").

**Coroutines / `suspend`.** The screen is drawn by the "main thread". If you do a database read or
a network call on the main thread, the screen freezes until it finishes ("ANR" — "Application Not
Responding" — the OS may kill you). A **`suspend fun`** is a function that can *pause* at certain
points (a database read, a delay, a network round-trip), *let the main thread go do other things*,
and *resume* when the result is ready — without blocking anything.

- You can only call a `suspend fun` from another `suspend fun` or from a **coroutine** started
  with `launch { }` / `viewModelScope.launch { }` / `LaunchedEffect { }`.
- `Dispatchers.IO` / `Dispatchers.Default` = "run this part on a background thread pool".
- `withContext(Dispatchers.IO) { ... }` = "do this bit off the main thread, then come back".

**Contrast with Python `async`/`await`:** very similar shape. `suspend fun` ≈ `async def`,
`launch { }` ≈ scheduling a task on an event loop, `withContext(Dispatchers.IO)` ≈
`loop.run_in_executor(...)`. The big difference: Kotlin coroutines have **structured concurrency**
— a coroutine belongs to a scope (`viewModelScope`, the Activity's `lifecycleScope`), and when
that scope dies, its coroutines are cancelled automatically. No leaked tasks.

**Flow.** A `Flow<T>` is "a stream that will emit zero or more `T`s over time, and can be
collected". A **cold** Flow does nothing until someone collects it.

- Room DAO methods that return `Flow<List<Habit>>` **re-emit a fresh list every time the
  underlying table changes.** This is the magic that keeps the UI live: query once, get updates
  forever.
- **`StateFlow<T>`** is a Flow that *always has a current value* and only emits on change. It's
  the standard type a ViewModel exposes to a screen. `.value` reads the current one.
- Operators: `.map { }`, `.filter { }`, `.combine(other) { a, b -> }`, `.debounce(ms)`,
  `.distinctUntilChanged()`, `.stateIn(scope, ...)` (turn a cold Flow into a hot StateFlow).
- `.first()` collects exactly one value and stops (used in repositories for a one-shot read).

**Python analogy for Flow:** a generator that *keeps yielding new values forever*, and whoever's
looping over it gets pushed the latest. `StateFlow` is that generator plus "and it remembers the
last thing it yielded so a new listener gets it immediately".

```python
def habits_stream():           # a cold generator: nothing happens until you iterate
    while True:
        yield read_habits_from_db()
        wait_for_db_change()
```

### See it in the real code

**`app/src/main/java/com/daybook/app/data/OccurrenceScheduler.kt`** — this file is a coroutine
tour:

- Every public method is `suspend fun` (`syncTask`, `syncHabit`, `completeHabit`, `snoozeFoodMed`,
  …) — they all do database work.
- `syncMutex = Mutex()` and `syncMutex.withLock { ... }` — a coroutine-friendly lock so two
  reminder actions can't corrupt each other's row rewrites. (`Mutex` ≈ `asyncio.Lock`.)
- `db.withTransaction { ... }` — a suspend function that runs the block in one atomic DB
  transaction.
- `db.foodMedTaskDao().observeAllTasks().first()` (in `cancelAllReminders`) — collect one value
  off a Flow.

**`app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt`**:
`fun observeSettings(): Flow<AppSettings> = database.appSettingsDao().observeSettings().map { it
?: AppSettings() }` — a DAO Flow with a `.map` operator layered on to substitute a default row
when the table is empty.

**`app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt`** — the deep end, skim it:

- `val greeting: StateFlow<String> = ...` around line 382 — built by `.map`-ing the settings Flow
  and a time-tick Flow together and calling `renderGreeting(...)`, then `.stateIn(...)` to make it
  a `StateFlow` the screen can read.
- The comment near line 207: "ONE upstream `app_settings` subscription, shared" — a note that they
  deliberately `.combine` once rather than subscribe five times.
- `val heroStyle: StateFlow<String> = settings.map { it.heroStyle }.distinctUntilChanged()...` —
  `distinctUntilChanged()` so the screen doesn't recompose when an unrelated setting changes.

**`app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt`** line ~224:
`changes.debounce(DEBOUNCE_MS).collect { ... doPush(...) }` — a Flow of "something changed" events,
`.debounce(3000)` so a burst of edits produces *one* cloud push 3 seconds after the last edit.

### Try it now

**9a. Write and run a coroutine in a scratch file.**

```kotlin
import kotlinx.coroutines.*

fun main() = runBlocking {
    println("start on ${Thread.currentThread().name}")
    val result = withContext(Dispatchers.Default) {
        Thread.sleep(500)          // pretend slow work
        6 * 7
    }
    println("got $result")
    launch { delay(200); println("from a child coroutine") }
    println("end of main body")
}
```

Run it. Note the order of the prints — `end of main body` before `from a child coroutine`, because
`launch` schedules and returns immediately, and `runBlocking` waits for children before exiting.

**9b. Watch a Room Flow re-emit.** In `HomeViewModel.kt` (or `RoutinesViewModel.kt`), find where a
`Flow` from a DAO is collected/stated. Add a `.onEach { android.util.Log.d("FlowTrace", "emit:
${it.size} items") }` into that chain. Rebuild, run, add and delete a habit in the app while
watching Logcat filtered to `FlowTrace` — each DB change pushes a new emission. Remove the line.

**9c. Explain the debounce.** In `CloudSyncRepository.startDebounce()`, in your own words: what
would go wrong if `changes.collect { doPush() }` had *no* `.debounce`, and the user edited five
fields of a habit quickly?

### Checkpoint

1. What does `suspend` let a function do that a normal function can't, and where can you call one
   from?
2. What makes a Room `Flow<List<Habit>>` different from a one-shot `List<Habit>`?
3. Why does `HomeViewModel` expose `StateFlow`s and not plain `Flow`s to the screen?

---

# MODULE 10 — ViewModel: the brain for one screen

### Concept

A **ViewModel** is a class that holds a screen's data and logic. It is *not* UI. Its jobs:

- Own the screen's state as `StateFlow`s (built from repository Flows).
- Expose functions the screen calls on user actions (`archiveHabit(id)`, `toggleFilter(f)`).
- Do that work in `viewModelScope.launch { }` (a coroutine scope tied to the screen's life).

Its superpower: **it survives configuration changes.** When you rotate the phone, the Activity is
destroyed and recreated, every composable re-runs from scratch — but the *same ViewModel instance*
is handed back. So in-flight loads and current state aren't lost.

A screen gets its ViewModel with `viewModel()` or, in this Hilt app,
`hiltViewModel()` / `@HiltViewModel`. The screen then `collectAsState()`s the ViewModel's
StateFlows and calls its functions.

Rule of thumb: **if it's not "what pixel goes where", it belongs in the ViewModel or below.**
Formatting a date for display can be borderline; deciding *which* habits to show is ViewModel;
computing a streak is a pure function the ViewModel calls.

### The Python you already know

```python
class RoutinesScreenModel:
    def __init__(self, habit_repo):
        self._repo = habit_repo
        self.habits = []          # the screen reads this
        self.show_archived = False

    def refresh(self):
        self.habits = self._repo.active_habits()

    def toggle_archived(self):
        self.show_archived = not self.show_archived
        self.refresh()
```

A ViewModel is that object, plus: it's created for you and cached across "re-renders", its fields
are reactive `StateFlow`s so the screen updates automatically, and its methods run on coroutines.

### See it in the real code

Open **`app/src/main/java/com/daybook/app/ui/routines/RoutinesViewModel.kt`** (it's a medium-size
one — good to read fully). Look for:

- `@HiltViewModel class RoutinesViewModel @Inject constructor(private val habitRepository:
  HabitRepository, private val scheduler: OccurrenceScheduler, ...)` — Hilt injects the
  dependencies (Module 11).
- Private `MutableStateFlow`s and public `StateFlow` mirrors (`_showArchived` /
  `showArchived`) — the "expose read-only, mutate privately" pattern.
- `val habits: StateFlow<List<...>> = combine(habitRepository.observeAllHabits(), _showArchived,
  _sort) { ... }.stateIn(viewModelScope, ...)` — the screen's list, derived reactively from a repo
  Flow plus UI state.
- `fun archiveHabit(id: String) = viewModelScope.launch { habitRepository.archiveHabit(id);
  scheduler.syncHabit(id) }` — a user action: launch a coroutine, do the write, re-sync the
  alarms.

Then see how the screen consumes it — **`RoutinesScreen.kt`** top:
`viewModel: RoutinesViewModel = hiltViewModel()`, then
`val habits by viewModel.habits.collectAsState()`, `val showArchived by
viewModel.showArchived.collectAsState()`, and buttons calling `viewModel::archiveHabit`.

### Try it now

**10a. Add a derived StateFlow and show it.** In `RoutinesViewModel.kt`, add:

```kotlin
val activeCount: StateFlow<Int> =
    habitRepository.observeActiveHabits()
        .map { it.size }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0)
```

In `RoutinesScreen.kt`, near the header, add
`val activeCount by viewModel.activeCount.collectAsState()` and a
`Text("active: $activeCount")`. Rebuild, run, add/archive habits, watch it update live with no
manual refresh. **Revert.**

**10b. Prove it survives rotation.** With 10a still in place, add a habit, then rotate the phone.
The count is still correct instantly (the ViewModel wasn't recreated). Compare with a
`remember { mutableStateOf(0) }` in the screen, which would reset.

**10c. Find the boundary violation (there isn't one — confirm).** Search `RoutinesViewModel.kt`
for `import androidx.compose` — there should be essentially nothing (maybe `@Immutable` on a data
holder). A ViewModel importing Compose UI types is a smell.

### Checkpoint

1. What survives a screen rotation — the composables, the ViewModel, or both?
2. Why does a ViewModel expose `StateFlow` (read-only) but keep a `MutableStateFlow` private?
3. Where does the coroutine that runs `archiveHabit`'s database write come from, and when is it
   cancelled?

---

# MODULE 11 — Hilt and dependency injection

### Concept

`HabitRepository` needs an `AppDatabase`. `RoutinesViewModel` needs a `HabitRepository` and an
`OccurrenceScheduler`. `OccurrenceScheduler` needs the database, `NotificationUtils`, a
`NotificationIdSequence`, and a `Provider<CloudSyncRepository>`. `CloudSyncRepository` needs seven
things. If you built these by hand you'd write:

```kotlin
val db = AppDatabase.build(context)
val notif = NotificationUtils(context)
val ids = NotificationIdSequence()
val scheduler = OccurrenceScheduler(db, notif, ids, ...)
val habitRepo = HabitRepository(db)
val vm = RoutinesViewModel(habitRepo, scheduler, ...)
// ... and keep them all as singletons, in the right order, everywhere
```

**Dependency injection** is having a robot do that. **Hilt** is the robot (built on Dagger). You
annotate:

- `@Inject constructor(...)` on a class — "here's what I need; build me".
- `@Singleton` — "only ever make one".
- `@HiltViewModel` on a ViewModel — "Hilt makes these; screens ask via `hiltViewModel()`".
- `@Module` + `@Provides` — for things Hilt *can't* just construct (interfaces, third-party
  classes, things needing config): a function that returns one. See `DatabaseModule` providing
  `AppDatabase` (needs `Room.databaseBuilder(...)` and the migration list) and the repositories;
  `FirebaseModule` providing `FirebaseAuth`, `FirebaseFirestore`, `CredentialManager`.
- `@HiltAndroidApp` on the `Application`, `@AndroidEntryPoint` on the Activity and each
  `BroadcastReceiver` — the entry points where Hilt injects.

At build time (kapt again) Hilt generates all the wiring. At runtime, `@Inject lateinit var
scheduler: OccurrenceScheduler` in a receiver just *appears*, fully constructed with its whole
dependency tree.

**One real subtlety in this codebase:** `OccurrenceScheduler` takes
`javax.inject.Provider<CloudSyncRepository>`, not `CloudSyncRepository` directly, because
`CloudSyncRepository` depends on `OccurrenceScheduler` — a cycle. A `Provider<T>` is "give me a
`T` *when I ask*, later", which breaks the cycle. You'll meet this pattern if you add a dependency
that loops.

### The Python you already know

Manual DI in Python is just passing arguments:

```python
db = Database()
habit_repo = HabitRepository(db)
scheduler = OccurrenceScheduler(db, NotificationUtils(), NotificationIdSequence())
vm = RoutinesScreenModel(habit_repo, scheduler)
```

Hilt is a framework that reads type annotations and does exactly this construction for you, in
the right order, caching the `@Singleton`s, for the whole app — so no file ever contains that
wiring boilerplate. The cost: a layer of magic and slower builds (kapt).

### See it in the real code

**`app/src/main/java/com/daybook/app/di/DatabaseModule.kt`** (82 lines) — read all of it:

- `@Module @InstallIn(SingletonComponent::class) object DatabaseModule` — a module of provider
  functions, installed app-wide.
- `@Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
  Room.databaseBuilder(context, AppDatabase::class.java, "daybook_database")
  .addMigrations(MIGRATION_2_3, ..., MIGRATION_16_17)
  .fallbackToDestructiveMigrationFrom(1).fallbackToDestructiveMigrationOnDowngrade().build()` —
  **this is where the migration list lives.** Module 15 comes back here.
- Seven `@Provides @Singleton fun provideXxxRepository(database: AppDatabase) = XxxRepository(database)`.

**`app/src/main/java/com/daybook/app/DaybookApplication.kt`**:
`@HiltAndroidApp class DaybookApplication : Application(), Configuration.Provider`, with `@Inject
lateinit var notificationUtils`, `workerFactory`, `cloudSyncRepository`, and an `onCreate()` that
calls `createNotificationChannels()`, `WindowRefreshWorker.enqueue(this)`, and
`cloudSyncRepository.start()`. (There's no `journalQuestionRepository` field any more, and nothing
seeds journal questions on startup — the journal-habit round made questions per-habit, seeded by
the habit form itself the first time a habit is switched to Journal, not by the application at
launch.)

**`app/src/main/java/com/daybook/app/util/alarm/AlarmReceiver.kt`** top:
`@AndroidEntryPoint class AlarmReceiver : BroadcastReceiver()` with `@Inject lateinit var db`,
`notificationUtils`, `scheduler`. The OS constructs the receiver; Hilt fills those three fields
before `onReceive` runs.

**`OccurrenceScheduler.kt`** constructor — the `Provider<CloudSyncRepository>` cycle-breaker,
with the comment explaining why.

### Try it now

**11a. Trace a dependency tree by hand.** Starting from `@AndroidEntryPoint class
NotificationActionReceiver` and its `@Inject lateinit var scheduler: OccurrenceScheduler`, write
out (on paper) every object Hilt must construct to satisfy that one field. Use the
`OccurrenceScheduler` constructor and follow each parameter. You'll end up with the database,
`NotificationUtils`, `NotificationIdSequence`, and the `Provider`. That tree is what `@Inject`
saved you from typing.

**11b. Add a `@Provides` and inject it.** In `DatabaseModule.kt` add:

```kotlin
@Provides @Singleton
fun provideBuildStamp(): String = "handover-course-build"
```

In any ViewModel constructor, add a parameter `private val buildStamp: String` and log it in
`init { }`. Rebuild, run, see it in Logcat. This proves how little ceremony adding an injectable
thing is. **Revert both.**

**11c. Cause and read a Hilt error.** Temporarily give a ViewModel constructor a parameter of a
type nothing provides, e.g. `private val nope: java.io.File`. Build. Read the error — Hilt tells
you, at compile time, exactly which type has no binding and where it's needed. Undo.

### Checkpoint

1. What does `@Inject constructor(...)` tell Hilt to do?
2. When do you need a `@Provides` function instead of just `@Inject`ing a class?
3. Why does `OccurrenceScheduler` take `Provider<CloudSyncRepository>` instead of
   `CloudSyncRepository`?

---

# MODULE 12 — Navigation

### Concept

This app has **one Activity** and does all screen changes with **Navigation-Compose**: a
`NavHost` maps string **routes** (`"main"`, `"settings"`, `"detail/{itemType}/{itemId}"`,
`"respond/{occId}?isHabit={isHabit}"`) to composables. `navController.navigate("settings")` pushes
a screen; `navController.popBackStack()` goes back.

The three top-level tabs are special: they're **not** separate nav routes, they're three pages of
one `HorizontalPager` under the single `"main"` route. Swiping moves between them; the bottom nav
bar taps call `pagerState.scrollToPage(i)`. Everything else (Detail, Add, Edit, Settings sub-
screens, the journal stepper, the habit-journal chat and edit screens, the reply screen) *is* a
stacked nav route drawn over `"main"`.

Why the split: swiping between Today/Habits/Intake should feel like one surface (a pager gives you
that for free), while Detail/Settings are genuine push/pop navigation with a back stack.

Key invariants (also in §Footguns — do not casually change):

- **Today is always page index 0.** `BackHandler`, deep-link fallbacks, and
  `NavConfig.visibleRoutesFrom` all rely on it. The configurable-tabs feature can *hide* Habits or
  Intake but never removes or reorders Today.
- `pagerState` uses `scrollToPage` (instant snap) for tab taps, not `animateScrollToPage` — the
  comment in `MainActivity` explains that animating a 0→2 jump cold-composes two screens mid-fling
  and stutters.
- `beyondViewportPageCount = 1` keeps neighbours warm; changing it back to 0 makes tab arrival lag.

### The Python you already know

The nearest thing is a web router:

```python
routes = {
    "/": home_view,
    "/settings": settings_view,
    "/detail/<type>/<id>": detail_view,
}
# navigate = change the URL; the framework renders the matching view; back button pops history
```

`NavHost` is that router; `navigate("detail/habit/$id")` is setting the URL;
`popBackStack()` is the browser back button; the arguments in `{ }` are path params you read out
of a `SavedStateHandle` in the destination's ViewModel.

### See it in the real code

**`app/src/main/java/com/daybook/app/ui/MainActivity.kt`**:

- The **launch gate** `when { locked -> ...; authState is Loading -> ...; !is SignedIn ->
  SignInGateScreen(); onboardingCompleted == null -> ...; == false -> Onboarding...; else ->
  MainApp() }` (~lines 285–313). Read the big comment block above it — the four stages and the
  "never route from an unsettled snapshot" rule.
- `MainApp()` (~line 349): `rememberNavController()`, `rememberPagerState(initialPage =
  NavConfig.landingIndex(...), pageCount = { visibleRoutes.size })`, the `DaybookScaffold` with
  the bottom nav, and the `NavHost(startDestination = "main") { composable("main") { ... }
  composable("settings") { ... } composable("detail/{itemType}/{itemId}") { ... } ... }`.
- Inside `composable("main")`: `BackHandler(enabled = settledPage != 0) { goToPage(0) }` — system
  back from Habits/Intake returns to Today first. Then the `HorizontalPager` whose page content is
  a `when (visibleRoutes.getOrElse(page) { "home" }) { "home" -> HomeScreen(...); "routines" ->
  RoutinesScreen(...); else -> FoodMedScreen(...) }`.
- The `LaunchedEffect(pendingDeepLink)` (~line 369): a tapped notification's occurrence id routes
  to `journal/...`, `habit_journal_chat/...` (a Journal habit's chat, checked via
  `isHabitJournalOccurrence`), or `respond/...`.

**`app/src/main/java/com/daybook/app/ui/NavConfig.kt`** — the pure logic for the configurable
tabs. You read this in Module 2 already; now it has context.

### Try it now

**12a. Add a temporary debug route.** In `MainActivity.kt`'s `NavHost`, add:

```kotlin
composable("debug_ping") {
    androidx.compose.material3.Text("ping — press back")
}
```

and, in the Settings screen callbacks, temporarily point one `onOpenXxx` at
`{ navController.navigate("debug_ping") }`. Rebuild, run, tap that Settings row → your bare screen
appears → system back pops it. You've added a route. **Revert.**

**12b. Read the deep-link path.** Trace what happens when a habit reminder notification is tapped:
`NotificationUtils.contentIntent(occurrenceId, ...)` sets an extra → `MainActivity.readDeepLink`
reads it into `deepLinkOccurrence` → the `LaunchedEffect(pendingDeepLink)` navigates to
`respond/$occId?isHabit=true`. Find each of those in the code.

**12c. Prove the "Today is index 0" invariant.** In `NavConfig.visibleRoutesFrom`, what does it do
if the stored CSV is `"routines,foodmed"` (no `home`)? (It force-prepends `"home"`.) Why does
`MainApp`'s `BackHandler` check `settledPage != 0` specifically?

### Checkpoint

1. Why are the three tabs a pager rather than three nav routes, but Settings *is* a nav route?
2. How does a screen like `DetailScreen` get its `itemId` argument?
3. What breaks if Today stops being page index 0?

---

# MODULE 13 — The reminder / alarm / notification pipeline

### Concept

This is the historically fragile heart of the app. Take it slowly. The chain, in words:

1. **You create/edit a habit or intake reminder.** The ViewModel calls
   `OccurrenceScheduler.syncHabit(id)` / `syncTask(id)`.
2. **The scheduler generates occurrence rows** for the rolling window (`WINDOW_DAYS = 7`): for
   each active weekday × each configured time, an `*_occurrences` row with `status = PENDING`, a
   deterministic id (`"$itemId:$epochMillis"`), a stable `notification_id` (from
   `NotificationIdSequence`), and the `local_date` string. It rewrites the window *sparing* rows
   whose slot is still wanted (so their armed alarm isn't leaked and re-minted).
3. **It arms exactly one alarm** — the next pending occurrence — via
   `NotificationUtils.scheduleReminderAlarm(...)`, which calls
   `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pendingIntent)`. If quiet hours
   is on and `triggerAt` falls inside the window, `quietDefer` pushes it to the window's end.
   Only *one* alarm per item is ever armed; the next is armed when this one resolves.
4. **Time passes. The alarm fires.** Android delivers a broadcast to **`AlarmReceiver`**
   (`ACTION_FIRE`). It uses `goAsync()` + an 8-second-capped coroutine.
5. **`AlarmReceiver.fireHabit` / `fireFoodMed`**: loads the occurrence row; if it's not `PENDING`
   any more, does nothing (pre-time resolution suppression). Otherwise:
   `NotificationUtils.showHabitNotification(...)` posts the notification, one `SHOWN` event is
   logged (guarded so a duplicate post can't double-log), and a **re-nag alarm** is scheduled for
   `now + snoozeInterval` with `isRefire = true`.
6. **The user acts** — from the shade or in-app. Notification buttons go to
   **`NotificationActionReceiver`** (`ACTION_COMPLETE` / `SKIP` / `SNOOZE` / `REPLY`, or the two
   `ACTION_BATCH_*`). It calls the matching `OccurrenceScheduler` method
   (`completeHabit`, `skipFoodMed`, `snoozeHabit`, `logFoodMed`, …) inside an 8-second-capped
   coroutine, and — in a `finally`, unconditionally, outside the scheduler's mutex — cancels the
   shade notification by id (so it clears even if the row was already resolved).
7. **The scheduler resolves the occurrence**: sets `status`, writes a terminal event
   (`COMPLETED`/`SKIPPED`/`REPLIED`), cancels this occurrence's alarm, and arms the *next*
   scheduled occurrence with `allowCatchup = false` (so resolving one reminder never immediately
   re-fires a different overdue one — that bug made answered reminders "never clear").
8. **The observing Flows re-emit** (the DAO tables changed) → the Today screen recomposes the card
   → if signed in, a Room invalidation marks `pendingPush` and a debounced cloud push follows
   (Module 14).

**Re-arm triggers.** Alarms are volatile — a reboot or an app update wipes every pending alarm.
**`BootCompletedReceiver`** listens for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
`TIMEZONE_CHANGED`, `TIME_SET`, … and runs `scheduler.syncAll()` (walks every active item,
regenerates windows, re-arms). `MainActivity.onCreate` also runs `syncAll()` once per launch when
RESUMED. A daily `WindowRefreshWorker` tops the window up if the app is never opened.

**Permissions.** `POST_NOTIFICATIONS` (Android 13+, runtime prompt) and `SCHEDULE_EXACT_ALARM`
(user-grantable in system settings). Without exact-alarm permission,
`scheduleReminderAlarm` catches the `SecurityException` and falls back to an *inexact* alarm.

### The gotcha that cost me days — notification channels are immutable

On Android 8+, a `NotificationChannel` is created **once**. After that the system **ignores** every
later change to its importance, and a user's "turn this off" on the channel **survives app
updates** (only a full uninstall clears it). So a build that once shipped a broken/blocked channel
will *silently* swallow every `notify()` on it forever, while the app-level
`areNotificationsEnabled()` still returns `true`.

The fix in this codebase: **channel IDs are versioned** — `habits_v2`, `food_med_v2` — and
`createNotificationChannels()` deletes the legacy `habits` / `food_med` on startup. If channel
state is ever suspect again, **bump the suffix** (`_v3`) and delete `_v2`. There's a
`notificationBlockReason()` diagnostic that checks both app-level and per-channel state, surfaced
in Settings and used to suppress-and-log a doomed `notify()`.

### The Python you already know

There's no real Python analogy for `AlarmManager` + `BroadcastReceiver` — it's OS-level scheduling
and callbacks. The closest: a cron job (`AlarmManager` set) that runs a script (`AlarmReceiver`)
which sends a desktop notification with action buttons, and the buttons POST to a tiny local
server (`NotificationActionReceiver`) that updates a SQLite row. Coroutine `goAsync()` +
`withTimeout(8_000)` ≈ "you have ~10 seconds in this callback before the OS considers you hung —
cap your work under that."

### See it in the real code

Read these four, in this order:

1. **`app/src/main/java/com/daybook/app/data/OccurrenceScheduler.kt`** (739 lines — the big one).
   Focus: `slots(...)` (window generation), `syncHabitInternal` / `syncTaskInternal` (the
   spare-don't-remint window rewrite, with a long comment), `armNextHabitInternal` (the
   `allowCatchup` contract — read that doc comment twice), `resolveHabit` / `finishFoodMed` (the
   resolve dance, and why `allowCatchup = false`), `snoozeHabit` (re-nag with `isRefire = true`),
   the `quietDefer` helper, and the `armBatchCheckIn` / `unresolvedBatch` batch path. The pure
   top-level functions at the end (`canBackfill`, `revertShouldRearm`, `isFoodMedEdit`,
   `unresolvedBatch`) are extracted precisely so they can be unit-tested without Room.
2. **`app/src/main/java/com/daybook/app/util/notification/NotificationUtils.kt`** (456 lines).
   Focus: the `CHANNEL_HABITS = "habits_v2"` comment (the immutability gotcha, in full),
   `createNotificationChannels`, `scheduleReminderAlarm` (exact→inexact fallback), the
   `notificationId * 4 + slot` request-code scheme (`RC_FIRE`/`RC_REFIRE`/`RC_OPEN`/`RC_ACTION` —
   why re-arm replaces instead of stacks), `showHabitNotification` / `showFoodMedNotification`
   (the fixed Skip/Snooze/Complete vs Skip/Snooze/Reply order, `RemoteInput` for reply, no reply
   for journal), `postReplyAck` (the Motorola stuck-"sending" workaround), `notificationBlockReason`.
3. **`app/src/main/java/com/daybook/app/util/alarm/AlarmReceiver.kt`** (155 lines). `runAsync`
   (goAsync + SupervisorJob + 8s cap), `fireHabit` / `fireFoodMed` (PENDING check → post → guarded
   SHOWN event → schedule refire), `fireBatch`.
4. **`app/src/main/java/com/daybook/app/util/alarm/NotificationActionReceiver.kt`** (110 lines) and
   **`BootCompletedReceiver.kt`** (75 lines). Note the `finally { cancelNotification(...) }` in the
   action receiver and the `REARM_ACTIONS` set in the boot receiver.

### Try it now

**13a. Add a trace log at each hop and watch a full cycle.** Add a `Log.i("PIPE", "...")` line at
the top of: `OccurrenceScheduler.syncHabitInternal`, `AlarmReceiver.fireHabit`,
`NotificationUtils.showHabitNotification`, `NotificationActionReceiver.onReceive`,
`OccurrenceScheduler.resolveHabit`. Rebuild, run. Create a habit with a time ~2 minutes out. Grant
notifications + exact alarms. Watch Logcat filtered to `PIPE`: you'll see `syncHabitInternal` →
(2 min later) `fireHabit` → `showHabitNotification` → (tap Complete) `onReceive` → `resolveHabit`.
That's the whole spine. **Remove the logs.**

**13b. Break notifications the "channel" way, then fix it.** In `NotificationUtils`, temporarily
change `CHANNEL_HABITS = "habits_v2"` to `"habits_v2_broken"` and set its importance to
`IMPORTANCE_MIN`. Rebuild, install, fire a habit reminder — it arrives silently or not at all. Now
change importance back to `IMPORTANCE_HIGH` **without changing the id** — rebuild, reinstall,
reminder is *still* quiet, because the channel already exists at MIN. Only bumping the id to
`"habits_v2_broken2"` fixes it. Then **revert to `"habits_v2"`**. You just felt the gotcha.

**13c. Explain `allowCatchup`.** In your own words, why does `finishFoodMed` call
`armNextTaskInternal(taskId, allowCatchup = false)` while `syncTaskInternal` calls it with the
default `true`? What user-visible bug does the `false` prevent?

### Checkpoint

1. How many alarms are armed per habit at any moment, and when is the *next* one armed?
2. A user disables the "Habit reminders" channel, then you ship an update that "fixes
   notifications". Do their reminders come back? What's the only thing that makes them come back?
3. What re-arms every alarm after the user reboots their phone?

---

# MODULE 14 — Firebase sync, and the hash rule you must not break

### Concept

**Room is the source of truth. Firestore is a mirror.** Never the other way round.

Firestore (a cloud document database) holds a *derived, gzipped* view of the local data:

```
users/{uid}                    definitions (gzipped Blob), definitionsHash, monthHashes map,
                               revision, updatedAt, deviceId, formatVersion = 3, appVersion
users/{uid}/months/{YYYY-MM}   payload (gzipped Blob of that month's day-logs), contentHash,
                               revision, updatedAt, deviceId
```

Shape decisions:

- The **parent doc** is rewritten only when the *definitions* (habits, intake reminders, custom
  categories, custom prompts, journal questions) change. Answering a reminder does **not** touch
  it.
- History is **one document per local calendar month**, so a write touches one month, and a
  reinstall doesn't download years of history to open the app. Only the current + previous month
  are hydrated into Room; older months are fetched on demand and evicted again once their hash
  matches the cloud.
- Every change is diffed by **hash**: `definitionsHash` (SHA-256 of the canonical definitions
  JSON) and per-month `contentHash`. A push writes only the docs whose hash changed. Incoming
  snapshots whose hash equals what we last applied are ignored (echo-guard).

**Sign-out wipes local data** (`wipeLocalForSignOut`): cancels every alarm, wipes all data tables
in one transaction, resets the sync bookkeeping. Signing back into the same account re-pulls
everything.

### The `@EncodeDefault(EncodeDefault.Mode.NEVER)` rule — read this three times

The backup/wire model (`data/backup/BackupModel.kt`) is serialised with `encodeDefaults = true`
so absent optional fields don't silently drift. But that means **adding a new optional field with
a default value changes the serialised bytes of *every user's* definitions** — even users who
never touch the feature — because the field now appears (with its default) in the JSON. That
changes `definitionsHash`. Which makes every user's app think its definitions changed. Which
forces **every user's app to re-upload its entire history** on next launch.

**This regression shipped three times.** The fix, now guarded by hash tests
(`JournalV2HashTest`, `PerHabitTextHashTest`, `StreakDefHashTest`, `ContentHashTest`,
`HabitJournalHashTest` — the journal-habit round's own guard for `HabitDef.journalQuestions` /
`HabitLog.qaJson`): every *new* optional field on a backup/wire type gets

```kotlin
@EncodeDefault(EncodeDefault.Mode.NEVER)
val newThing: String? = null
```

which means "when this holds its default, write it as **absent**, not as `newThing: null`". A user
who doesn't use the feature then produces byte-identical bytes and a byte-identical hash — zero
churn.

**When you add a synced field (you'll do this in Module 16), this annotation is not optional.**

### The Python you already know

```python
import json, hashlib

def content_hash(definitions, days):
    canonical = json.dumps({"definitions": definitions, "days": days},
                           sort_keys=True, separators=(",", ":"))   # canonical: stable ordering
    return hashlib.sha256(canonical.encode()).hexdigest()

# The @EncodeDefault(NEVER) bug, in Python terms:
#   v1: obj = {"id": 1, "name": "x"}
#   v2 adds an optional field with a default:
#       obj = {"id": 1, "name": "x", "motivation": None}   # <-- different json.dumps output!
#   -> content_hash changes for EVERY object, even ones that never set `motivation`
#   Fix: only include the key when it's actually set:
#       if motivation is not None: obj["motivation"] = motivation
```

`explicitNulls = false` + `@EncodeDefault(NEVER)` together are Kotlin's "only include the key when
it's set".

### See it in the real code

**`app/src/main/java/com/daybook/app/data/backup/BackupModel.kt`** (190 lines) — read all of it:

- `@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)` at the top (enables
  `@EncodeDefault`).
- `DaybookBackup(meta, definitions, days)`, `BackupMeta` (note `formatVersion`, `rangeStart` /
  `rangeEnd`, and that meta is **not** hashed — `exportedAt` changes every export).
- `Definitions` — no longer carries a global `journalQuestions` field at all (the journal-habit
  round retired it); its doc comment explains why and that an old backup's `journalQuestions` key
  is simply ignored on decode.
- `HabitDef` — `streakStartedAt`, `streakLongest`, `promptMessage`, `motivation`, and (journal-habit
  round) `journalQuestions: List<String> = emptyList()` all carry `@EncodeDefault(NEVER)`, each
  with a comment naming the precedent — `journalQuestions` is the newest and points back at
  `streakLongest` and the old, now-removed `Definitions.journalQuestions`.
- `IntakeReminderDef.motivation` — `@EncodeDefault(NEVER)`; the comment notes the *older*
  `promptMessage` field is deliberately left as-is ("fixing it now would churn").

**`app/src/main/java/com/daybook/app/data/sync/ContentHash.kt`** (top 40 lines) — `Canonical` inner
class, `Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }`,
`ofParts(definitions, days)` → canonical string → `sha256Hex`. The class comment explains why
`meta` must never be in the hash.

**`app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt`** (1278 lines — skim, don't
memorise). Anchors: the class doc ("Invariant: Room is the source of truth"), `onAuthState`
(sign-in/out lifecycle), `wipeLocalForSignOut`, `bootstrap` (the
`decideBootstrap(localEmpty, remoteExists, hashesEqual, promptShown)` decision → ATTACH_ONLY /
PUSH_LOCAL / PULL_REMOTE / CONFLICT), `doPush` (partition by month → hash diff → batched write of
only changed docs), `attachTracker` (a Room `InvalidationTracker.Observer` that sets
`pendingPush` on any local write to `DATA_TABLES`), and the `DATA_TABLES` array (note it does
**not** include `app_settings` — settings writes must not cost a cloud round-trip).

**`firestore.rules`** (repo root) — the whole security model: `allow read, write: if
request.auth.uid == uid`, once for `users/{uid}` and again for `users/{uid}/months/{month}` (a
nested match does not inherit).

### Try it now

**14a. Watch the hash echo-guard.** With a signed-in device, add a `Log.i("SYNC", "defsHash=$defsHash
changed=${changed}")` inside `CloudSyncRepository.doPush` just after `defsHash` and `changed` are
computed. Rebuild. Now (i) answer a reminder — Logcat shows a push with `changed=[2026-XX]` and no
defs change; (ii) edit a habit's title — next push shows a new `defsHash`; (iii) trigger a sync
with no changes — `changed=[]`, defs unchanged, nothing written. Remove the log.

**14b. Prove the `@EncodeDefault` rule to yourself in a scratch file.**

```kotlin
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

val json = Json { encodeDefaults = true; explicitNulls = false }

@Serializable data class Bad(val id: Int, val note: String? = null)
@Serializable data class Good(val id: Int, @EncodeDefault(EncodeDefault.Mode.NEVER) val note: String? = null)

fun main() {
    println(json.encodeToString(Bad(1)))    // {"id":1}   -- wait, explicitNulls=false hides it...
    println(json.encodeToString(Bad(1, "x")))// {"id":1,"note":"x"}
    // now try with encodeDefaults=true and a NON-null default:
    val j2 = Json { encodeDefaults = true }
    @Serializable data class Bad2(val id: Int, val n: Int = 0)
    @Serializable data class Good2(val id: Int, @EncodeDefault(EncodeDefault.Mode.NEVER) val n: Int = 0)
    println(j2.encodeToString(Bad2(1)))     // {"id":1,"n":0}   <-- the churn bug
    println(j2.encodeToString(Good2(1)))    // {"id":1}         <-- fixed
}
```

Run it, see `{"id":1,"n":0}` vs `{"id":1}`. That extra `"n":0` is what re-uploaded everyone's
history three times.

**14c. Find the test that guards it.** Open
`app/src/test/java/com/daybook/app/data/sync/PerHabitTextHashTest.kt` and read what it asserts:
that a `HabitDef` with no `promptMessage`/`motivation` hashes identically before/after those
fields existed.

### Checkpoint

1. Which is authoritative when Room and Firestore disagree, and how does the app decide what to
   push?
2. You add `val colorHex: String? = null` to `HabitDef` for sync. What annotation must it carry,
   and what happens to every existing user if you forget it?
3. Why is `app_settings` deliberately excluded from `DATA_TABLES`?

---

# MODULE 15 — Room migrations: the rule you must never break

### Concept

**A shipped database schema is frozen.** The app on a user's phone has real rows in the v15 shape.
You cannot change an `@Entity` and rebuild — Room would open v15 data against a v16 expectation
and either crash or (worse) with a destructive fallback *wipe it*.

To change the schema you do **all** of this, in order:

1. **Change the `@Entity`** (add a column / index / table).
2. **Write `MIGRATION_15_16`** in `data/local/Migrations.kt` — an `object : Migration(15, 16)` with
   `override fun migrate(db)` containing the exact SQL (`ALTER TABLE ... ADD COLUMN ...`,
   `CREATE INDEX ...`, or a create-copy-drop-rename rebuild for a column *removal*).
3. **Bump `@Database(version = 16)`** in `AppDatabase.kt`.
4. **Register it**: add `MIGRATION_15_16` to the `.addMigrations(...)` list in `DatabaseModule.kt`.
5. **Build.** Because `exportSchema = true` and the `room.schemaLocation` arg are set, the build
   writes `app/schemas/com.daybook.app.data.local.AppDatabase/16.json` — the canonical description
   of the new schema, with an `identityHash`.
6. **Commit `16.json`** (it's a source file, not a build artifact).
7. **Add a `MigrationTest` case** in `app/src/androidTest/.../MigrationTest.kt` — create the DB at
   15, run the migration, assert the new column/table exists and old data survived.

**`identityHash`.** Room computes a hash of the schema and stores it in the DB. On open it
compares. If your hand-written migration SQL doesn't produce *exactly* the schema Room expects for
v16 (a different column order, a missing `DEFAULT`, an index name that doesn't match the
`index_<table>_<colA>_<colB>` convention), the hashes disagree and Room throws
`IllegalStateException: Migration didn't properly handle ...`. This is why migration comments in
this repo obsessively say "byte-matches `16.json`" and "index name matches Room's convention".

**`fallbackToDestructiveMigrationFrom(1)`** in `DatabaseModule.kt` — there was never a shippable
v1 schema, and no `1.json` exists, so a stored v1 DB is wiped and recreated rather than crashing.
`fallbackToDestructiveMigrationOnDowngrade()` — if a user somehow runs an older build over a newer
DB, wipe rather than crash. These are *escape hatches for impossible-in-practice cases*, not
permission to skip migrations.

**The incremental-build gotcha (bit this project twice).** If you bump the version and build
*without* a clean, a stale `<n>.json` can get left with the wrong version label or content, and
your `MigrationTest` passes against a lie. **Always run the migration gate on a `clean` build**
(`./gradlew clean compileDebugAndroidTestKotlin` and, on a device, the `MigrationTest`).

**Additive is easy, removal is a rebuild.** SQLite (in the versions Room bundles) has no portable
`DROP COLUMN`. `MIGRATION_4_5` and `MIGRATION_11_12` show the pattern: `CREATE TABLE
app_settings_new (...)` with the desired shape → `INSERT INTO app_settings_new SELECT ... FROM
app_settings` → `DROP TABLE app_settings` → `ALTER TABLE app_settings_new RENAME TO app_settings`.

### The Python you already know

If you've used Alembic or Django migrations: this is exactly that, done by hand.

```python
# Alembic-style
def upgrade():
    op.add_column("app_settings", sa.Column("week_start", sa.String(), nullable=False,
                                            server_default="MONDAY"))
def downgrade():
    op.drop_column("app_settings", "week_start")
```

Room migrations are the `upgrade()` only (no auto-downgrade — that's the destructive fallback),
you write the raw SQL, and there's a hash check that fails loudly if your SQL and the ORM's model
of the new schema don't match to the byte.

### See it in the real code

**`app/src/main/java/com/daybook/app/data/local/Migrations.kt`** — read it start to finish. It's
the single best teacher of this topic because it has fifteen worked examples (`MIGRATION_2_3`
through `MIGRATION_16_17`):

- `MIGRATION_2_3`, `MIGRATION_3_4` — the simplest: one `ALTER TABLE ... ADD COLUMN ... NOT NULL
  DEFAULT ...`.
- `MIGRATION_4_5`, `MIGRATION_11_12` — the create-copy-drop-rename rebuild to *drop* columns, with
  a comment that the new `CREATE TABLE` "mirrors Room's generated schema exactly".
- `MIGRATION_7_8` — five additive statements + a new table, no rebuild.
- `MIGRATION_12_13` — the big one: four `CREATE INDEX` (names matching Room's convention), two
  `ADD COLUMN` + a data backfill (`UPDATE ... SET local_date = date(scheduled_for/1000,
  'unixepoch', 'localtime')`), plus a long comment about the cost at scale and an ANR risk.
- `MIGRATION_13_14` — additive schema **plus a scoped destructive wipe** (deletes only JOURNAL-task
  occurrence+event rows) — showing that a migration can also transform data, carefully.
- `MIGRATION_14_15` — two `ADD COLUMN` on `habits` (one nullable no-default, one `NOT NULL DEFAULT
  0`), with the comment "existing rows read 0, matching the Kotlin default".
- `MIGRATION_15_16` — 20 `ALTER TABLE app_settings ADD COLUMN ... NOT NULL DEFAULT ...` (each
  DEFAULT byte-matching a `@ColumnInfo(defaultValue = ...)` in `DataModel.kt`) + 3 nullable
  no-default columns on `habits`/`food_med_tasks` (the synced ones from Module 14).
- `MIGRATION_16_17` (journal-habit round, the newest) — combines three moves in one migration:
  (a) two additive columns (`habits.journal_questions_json` NOT NULL DEFAULT `''`,
  `habit_occurrences.qa_json` nullable no-default); (b) a **scoped destructive delete** — every
  `food_med_tasks` row of type `JOURNAL`, and its children, deleted children-before-parents
  (events → occurrences → tasks) because neither child table declares a `@ForeignKey`/cascade to
  rely on; (c) `DROP TABLE IF EXISTS journal_questions` outright, no forward-migration of its
  content. It's a good second read after `MIGRATION_13_14` (the other "additive schema + scoped
  destructive wipe" example) because it adds a third move — dropping an entire retired table — on
  top of that pattern.

**`app/src/main/java/com/daybook/app/di/DatabaseModule.kt`** — the `.addMigrations(MIGRATION_2_3,
..., MIGRATION_15_16, MIGRATION_16_17)` list and the two `fallbackToDestructiveMigration*` calls.

**`app/schemas/.../15.json` and `16.json`** — diff them (`git diff --no-index 15.json 16.json`,
or open side by side). The *only* differences are the 23 added columns. That's what a clean
additive migration looks like.

**`app/src/androidTest/java/com/daybook/app/data/local/MigrationTest.kt`** — the pattern:
`helper.createDatabase(TEST_DB, 15).close()` → `helper.runMigrationsAndValidate(TEST_DB, 16, true,
MIGRATION_15_16)` → `db.query("SELECT * FROM app_settings")` → assert the new column names are
present. `runMigrationsAndValidate` is what checks the `identityHash`.

### Try it now

**15a. Read the diff.** Run:

```bash
git diff --no-index app/schemas/com.daybook.app.data.local.AppDatabase/15.json \
                    app/schemas/com.daybook.app.data.local.AppDatabase/16.json | less
```

Confirm every added line corresponds to a `db.execSQL("ALTER TABLE ... ADD COLUMN ...")` in
`MIGRATION_15_16`. This one-to-one correspondence is the thing you're maintaining.

**15b. Trace an `identityHash` mismatch (thought experiment, then real in Module 16).** In
`MIGRATION_15_16`, imagine you wrote `DEFAULT 'monday'` (lowercase) instead of `DEFAULT 'MONDAY'`
for `week_start`, but `DataModel.kt` says `@ColumnInfo(defaultValue = "MONDAY")`. What fails, when,
and with what message? (Answer: `MigrationTest`'s `runMigrationsAndValidate` — and a real device
upgrading v15→v16 — throws `IllegalStateException` about the schema not matching, because the
generated `16.json` `createSql` has `'MONDAY'` and your migration produced `'monday'`.)

**15c. Run the migration gate clean.**

```bash
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
./gradlew clean compileDebugAndroidTestKotlin
```

This compiles `MigrationTest` (and everything else) from cold. On a device/emulator you'd also run
`./gradlew connectedDebugAndroidTest` to actually execute it.

### Checkpoint

1. List the seven steps to add a column to a table safely.
2. What is `identityHash`, and what does a mismatch mean?
3. Why must the migration gate be run on a `clean` build?

---

# MODULE 16 — CAPSTONE: add one new optional field, end to end

This is your graduation exercise. It's the change you'll copy from for the rest of your time on
this app. Do it for real, in a branch, run the 4-gate, then keep it or revert.

**The feature:** add an optional per-habit **"target count"** integer (e.g. "drink water 8 times")
— stored in the DB, editable in the habit form, shown on the habit detail header, carried in
sync + backup with the no-churn guard, tested, and passing all four gates.

> If you want the *lighter* version for **M3** (a device-local settings toggle with no sync, no
> new table column semantics you don't already have), do steps that mirror the customization
> round: add a column to `app_settings` in `MIGRATION_15_16`-style... except you can't reopen a
> shipped migration — and by now that includes the journal-habit round's `MIGRATION_16_17` too. So
> for a genuine new setting you'd bump to DB **v18** with a new `MIGRATION_17_18`. The full
> capstone below already teaches every piece of that. Do the full one.

### Step 0 — branch and baseline

```bash
git checkout -b capstone-target-count
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

Confirm all four are green *before* you change anything, so a later failure is definitely yours.

### Step 1 — the entity (`data/model/DataModel.kt`)

In `data class Habit(...)`, **append** (never insert mid-list — positional call sites):

```kotlin
    // Capstone: optional per-habit target count ("do this N times"). Nullable, NO schema
    // defaultValue (mirrors `streak_started_at` / `custom_category`). Synced via HabitDef.targetCount
    // with @EncodeDefault(NEVER).
    @ColumnInfo(name = "target_count") val targetCount: Int? = null
```

### Step 2 — the migration (`data/local/Migrations.kt`)

You **cannot** edit `MIGRATION_16_17` — the journal-habit round's migration, the newest one that
has shipped (it has shipped conceptually — treat any migration whose `.json` is committed as
frozen). Add a new one at the end:

```kotlin
/**
 * v17 -> v18 (Capstone). One additive nullable column on `habits`, no table rebuild, no data
 * rewritten. NULL = "no target set". Nullable, no schema DEFAULT (mirrors `streak_started_at`).
 * Synced via HabitDef.targetCount with @EncodeDefault(NEVER) so a habit with no target produces a
 * byte-identical definitionsHash.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN target_count INTEGER")
    }
}
```

### Step 3 — bump the version (`data/local/AppDatabase.kt`)

`@Database(entities = [ ... ], version = 18, exportSchema = true)`

### Step 4 — register it (`di/DatabaseModule.kt`)

Add the import, then append to the chain:
`.addMigrations(MIGRATION_2_3, ..., MIGRATION_16_17, MIGRATION_17_18)`

### Step 5 — regenerate + commit the schema JSON

```bash
./gradlew clean assembleDebug
git add app/schemas/com.daybook.app.data.local.AppDatabase/18.json
git diff --cached --stat        # should show ONLY 18.json added
```

Open `18.json`, confirm it's `"version": 18` and the only diff vs `17.json` is the one
`target_count` column on `habits`.

### Step 6 — DAO + repository

`data/local/HabitDao.kt` — add a targeted update:

```kotlin
@Query("UPDATE habits SET target_count = :v WHERE id = :id")
suspend fun updateTargetCount(id: String, v: Int?)
```

`data/HabitRepository.kt` — add:

```kotlin
suspend fun setTargetCount(id: String, v: Int?) = database.habitDao().updateTargetCount(id, v)
```

### Step 7 — the form (`ui/routines/HabitForm.kt` + `ui/routines/AddHabitViewModel.kt` / `EditHabitViewModel`)

In the form ViewModel(s): add `var targetCount by mutableStateOf<Int?>(null)` state, load it from
the habit on edit, include it when building the `Habit` to save. In `HabitForm.kt`, inside the
**Advanced** section (follow the existing "Reminder text" / "Why this matters" fields), add a
numeric `DaybookTextField` bound to `targetCount` (parse to `Int?`, blank = null). Keep it hidden
for `HabitType.STREAK` like the "Reminder text" field is.

### Step 8 — the detail header (`ui/detail/DetailViewModel.kt` + `DetailScreen.kt`)

Add `_targetCount` to the ViewModel, populate it in `loadHabitDetails`, expose a `StateFlow`, and
render "Target: N×" in the detail header near the motivation line when non-null.

### Step 9 — sync + backup, WITH the guard (`data/backup/BackupModel.kt` + `ExportImportRepository.kt`)

In `data class HabitDef(...)`, **append**:

```kotlin
    /** Capstone: optional per-habit target count. `@EncodeDefault(NEVER)` so a null (the default)
     *  is ABSENT in the canonical bytes — a habit with no target sees ZERO definitionsHash change
     *  (mirrors `streakLongest` / `promptMessage`). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val targetCount: Int? = null
```

In `ExportImportRepository.kt`: in the habit → `HabitDef` mapping (in `exportBackup`) set
`targetCount = habit.targetCount`; in `applyRemoteDefinitions` / the definitions upsert, write
`def.targetCount` back onto the `Habit`. Find the existing `promptMessage` / `motivation` handling
and mirror it exactly.

### Step 10 — tests

- **Pure hash test** — copy `PerHabitTextHashTest.kt` to `TargetCountHashTest.kt`: assert a
  `HabitDef` with `targetCount = null` produces a `definitionsHash` byte-identical to one built by
  code that never knew about `targetCount` (in practice: assert the encoded JSON of
  `HabitDef(id="a", name="b", iconKey="x", colorTag="AUTO", createdAt="...")` contains no
  `targetCount` key).
- **Migration test** — in `MigrationTest.kt`, add `migrate17To18_addsTargetCountColumn()`
  following the `migrate15To16` / `migrate16To17` pattern: create at 17, run `MIGRATION_17_18`,
  assert `habits` now has a `target_count` column and a pre-existing row survived with
  `target_count` NULL. Add a `migrateAll_3To18` chain case.
- **Round-trip test** — in `ExportRangeTest` or a new small test, build a `Habit` with
  `targetCount = 8`, export, re-import, assert it survives.

### Step 11 — the 4-gate, clean

```bash
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

All four green. `assembleRelease` must be R8- and `lintVitalRelease`-clean. On a device also run
the `MigrationTest` (`./gradlew connectedDebugAndroidTest`) and manually: upgrade a v17 install →
existing habits read `target_count` NULL, no crash; set a target on one → shows on detail;
sign-out/in round-trips it; a habit with no target causes **no** parent-doc re-push (watch the
Module 14a log).

### Step 12 — commit or revert

```bash
git add -A && git commit -m "Capstone: per-habit target count, end to end (DB v18)"
# or, to throw it away:
git checkout main && git branch -D capstone-target-count
```

**Checkpoint (this is the graduation question):** without looking, list every file you had to
touch and why. You should get: `DataModel.kt` (column), `Migrations.kt` (migration),
`AppDatabase.kt` (version), `DatabaseModule.kt` (register), `18.json` (generated+committed),
`HabitDao.kt` + `HabitRepository.kt` (write path), the form + its ViewModel (input),
`DetailViewModel.kt` + `DetailScreen.kt` (display), `BackupModel.kt` (`@EncodeDefault(NEVER)`
field) + `ExportImportRepository.kt` (map both ways), three test files, and the 4-gate.

---

# MODULE 17 — Testing and the 4-gate

### Concept

Two kinds of test:

- **Unit tests** — `app/src/test/`. Plain JVM JUnit 4. No Android, no device, no Room, no Compose.
  They test **pure functions**: `daysSince`, `deferIfInsideQuietHours`, `calculateHabitStreaks`,
  `renderGreeting`, `NavConfig.*`, `canBackfill`, `ContentHash.*`, `mergeMonth`. This is *why* so
  much logic in this codebase is pulled out into top-level pure functions with no dependencies —
  so it can be tested in milliseconds without a device. There are ~67 of these files.
- **Instrumented / androidTest** — `app/src/androidTest/`. Runs on a real device or emulator
  because it needs the Android framework. This project has exactly two: `MigrationTest` (needs
  real SQLite + Room) and `NavIconInflateTest` (needs the resource system). No Compose UI tests,
  no Espresso — a deliberate scope choice.

**The 4-gate.** Every change I made ran all four of these, every time, and only shipped if all
four were green:

```bash
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew \
  clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

1. **`testDebugUnitTest`** — all unit tests pass.
2. **`assembleDebug`** — the app compiles and packages.
3. **`assembleRelease`** — the *release* build compiles, **and R8 is clean** (no missing-class
   warnings from shrinking) **and `lintVitalRelease` is clean** (Android's release-blocking lint
   checks pass). This gate catches a whole class of "works in debug, broken in the shipped APK"
   problems — a ProGuard/R8 rule missing for a reflectively-used class, a lint error about a
   permission or a hardcoded thing.
4. **`compileDebugAndroidTestKotlin`** — the instrumented tests still *compile* (they're not run
   here — no device — but a broken `MigrationTest` is caught).

Why all four and not just "the tests": debug compiling doesn't prove release compiles (R8 differs);
tests passing doesn't prove the app packages; and the androidTest sources are a separate compile
unit that a refactor can silently break.

**Reading a failure:**

- Unit test fail → the report at `app/build/reports/tests/testDebugUnitTest/index.html` names the
  test, the assertion, expected vs actual.
- `assembleRelease` R8 fail → look for `Missing class` / `can't find referenced` in the output;
  usually needs a `-keep` rule in `app/proguard-rules.pro`.
- `lintVitalRelease` fail → the report at `app/build/reports/lint-results-*.html`; it names the
  rule id and the file+line.
- `MigrationTest` fail (on device) → `IllegalStateException` about schema hash → your migration
  SQL doesn't match the generated `<n>.json`.

### The Python you already know

`testDebugUnitTest` is `pytest` on your pure functions. The other three gates have no Python
equivalent because Python has no separate "optimised release build" — imagine if `pip wheel`
could produce a wheel that imports fine but crashes because a minifier stripped a module you
reflectively imported, and you had a CI step specifically to catch that. That's gate 3.

### See it in the real code

Open a few unit tests and notice they're just Kotlin + `assertEquals`:

- **`app/src/test/java/com/daybook/app/util/streak/OngoingStreakTest.kt`** — tiny, tests
  `daysSince`.
- **`app/src/test/java/com/daybook/app/data/QuietHoursTest.kt`** — the 12 cases (wrap-midnight,
  overdue, degenerate window) for `deferIfInsideQuietHours`.
- **`app/src/test/java/com/daybook/app/ui/NavConfigTest.kt`** — visible-routes / landing-index /
  toggle for the pure `NavConfig` object.
- **`app/src/test/java/com/daybook/app/data/sync/PerHabitTextHashTest.kt`** — the no-churn guard
  from Module 14.
- **`app/src/test/java/com/daybook/app/ui/journal/ChatFlowTest.kt`** (journal-habit round) — tests
  `advanceChat`, the pure bubble-append / index-advance / all-answered maths behind
  `HabitJournalChatScreen`, with no ViewModel or Compose involved. A good second example of "pull
  the logic out into a top-level function so it's testable in milliseconds."
- **`app/src/test/java/com/daybook/app/data/sync/HabitJournalHashTest.kt`** (journal-habit round)
  — the Module 14 no-churn guard for the new `HabitDef.journalQuestions` / `HabitLog.qaJson` wire
  fields; read it right after `PerHabitTextHashTest` above, it's the same pattern one field newer.

And **`app/src/androidTest/java/com/daybook/app/data/local/MigrationTest.kt`** — the
`@get:Rule val helper = MigrationTestHelper(...)` + `createDatabase` + `runMigrationsAndValidate`
pattern.

### Try it now

**17a. Run the unit gate and open the report.**

```bash
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
./gradlew testDebugUnitTest
xdg-open app/build/reports/tests/testDebugUnitTest/index.html   # or open it manually
```

**17b. Write a real one-line test.** Create
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

    @Test fun future_start_is_zero() {
        assertEquals(0, daysSince(startMillis = day, nowMillis = 0L, zone = utc))
    }
}
```

`./gradlew testDebugUnitTest` — it should pass. Now change one expected value to a wrong number,
re-run, and read how the report shows expected vs actual. Then fix it back.

**17c. Make gate 3 fail on purpose.** Add a deliberately unresolvable lint issue — e.g. in
`AndroidManifest.xml` add `<uses-permission android:name="android.permission.SEND_SMS" />` (an
unused dangerous permission). Run `./gradlew lintVitalRelease` and read the report. Then remove it.

### Checkpoint

1. What does each of the four gates catch that the others don't?
2. Where is most of this app's logic tested, and why is it structured as top-level pure functions
   to make that possible?
3. `assembleDebug` passes but `assembleRelease` fails with "Missing class". What's the likely
   cause and where do you fix it?

---

# Releasing (for M5)

Once your change is in and the 4-gate is green:

1. **Bump the version** in `app/build.gradle.kts` `defaultConfig`:
   `versionCode = 14` (must be strictly greater than the installed one, or the APK won't install
   as an update), `versionName = "0.5.6"` (human-readable). Update the trailing comments.
2. **If the DB schema changed**, make sure the new `<n>.json` is committed and a `MigrationTest`
   case covers it.
3. **Deploy Firestore rules if they changed** (they rarely do):
   `firebase deploy --only firestore:rules`.
4. **Build the signed release** (needs `keystore.properties` + `daybook-release.jks` present):
   ```bash
   JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew clean assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`. Rename it to
   `Daybook-v0.5.6-buildN-release.apk` if you keep the naming convention.
5. **Install on a device.** If the previous build had the *same* `versionCode`, Android refuses
   the update — you must uninstall first (which wipes local data; a signed-in user re-pulls from
   the cloud on next sign-in). This is exactly the situation `CUSTOMIZATION_REGRESSION.md`'s big
   warning box describes.
6. **Regression pass.** Work through the newest regression watch-list — currently
   `JOURNAL_HABIT_REGRESSION.md` (the journal-habit round; same role/format as the older
   `CUSTOMIZATION_REGRESSION.md`) — on a real device.

---

# Reference: a guided tour of the folders

```
app/
  build.gradle.kts            the module build script (deps, versions, signing, build types)
  proguard-rules.pro          R8/ProGuard keep-rules for the release build
  schemas/…/<n>.json          Room's exported schema per version (3.json … 17.json) — COMMIT THESE
  src/
    main/
      AndroidManifest.xml     the table of contents (components, permissions, launcher activity)
      java/com/daybook/app/
        DaybookApplication.kt  @HiltAndroidApp; one-time startup (channels, worker, sync.start())
        data/
          model/DataModel.kt            every @Entity + every enum. Start here.
          local/                        Room: AppDatabase, the DAOs, Migrations.kt
          auth/                         Google sign-in (AuthRepository, Credential Manager, avatar fetch)
          backup/BackupModel.kt         the v2 JSON backup / wire model (@EncodeDefault rule lives here)
          sync/                         CloudSyncRepository + SyncLogic, MonthPartitioner, ContentHash,
                                        PayloadCodec (gzip), SyncStateStore, MonthPartitioner
          lock/                         app-lock: AppLockRepository, PinHasher, BiometricGate
          *Repository.kt                the boring middle layer (Habit, FoodMed, AppSettings,
                                        CustomCategory, CustomPrompt, ExportImport) — no more
                                        JournalQuestionRepository; per-habit questions don't need one
          OccurrenceScheduler.kt        THE reminder engine: windows, arm/re-nag, resolve, backfill, batch
          QuietHours.kt, RetentionPolicy.kt, ProfilePhotoStore.kt   small pure/util data helpers
        di/                             Hilt modules: DatabaseModule, FirebaseModule
        ui/
          MainActivity.kt               the ONE activity: launch gate + NavHost + pager
          NavConfig.kt                  pure logic for the configurable bottom-nav
          theme/                        Tokens.kt (colours, spacing, shapes, Motion), Theme.kt,
                                        Type.kt (font picker), Accent.kt (5 accents)
          components/                   shared composables: SoftCard, buttons, WeekStrip, Sheets,
                                        SegmentedControl, Forms, ScreenHeader, Avatar, dialogs …
          home/                         Today screen + HomeViewModel (greeting, hero, week strip, cards)
          routines/                     Habits list + Add/Edit forms + HabitForm + ViewModels
          foodmed/                      Intake list + Add/Edit + FoodMedForm + ViewModels
          journal/                      JournalScreen/ViewModel (legacy Intake-Journal stepper,
                                        retired/unreachable — FEATURES.md §5a) PLUS
                                        HabitJournalChatScreen/ViewModel + HabitJournalEditScreen/
                                        ViewModel (the live per-habit Journal chat + edit UI, §5b)
          detail/                       Detail screen (History/Stats tabs) + paging + ViewModel
          respond/                      the reply/edit screen a notification tap opens
          settings/                     Settings hub + sub-screens + SettingsViewModel
          account/                      sign-in gate, account screen, sync status row, delete-account
          lock/                         LockScreen, AppLockSettingsScreen, LockViewModel
          onboarding/                   the first-run name screen
          icons/                        DaybookIcons (brand glyphs) + Icons (curated set + resolver)
        util/
          alarm/                        AlarmReceiver, BootCompletedReceiver, NotificationActionReceiver
          notification/                 NotificationUtils (channels, posting, PendingIntents),
                                        NotificationIdSequence
          work/                         WindowRefreshWorker (daily window top-up), SyncFlushWorker
          streak/                       StreakCalculator (habit/intake streaks), OngoingStreak (daysSince)
          DateTimeUtils.kt, JsonUtils.kt, StorageUtils.kt, TimeTicker.kt, enums/Converters.kt
      res/                              drawables (notification/nav icons), mipmap (launcher), font/,
                                        values/ (strings.xml — nearly empty, colors, themes), xml/
    test/                               ~67 pure-JVM JUnit4 unit tests (mirrors the package layout)
    androidTest/                        MigrationTest + NavIconInflateTest (device-only)
```

Repo root config (not docs): `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
`gradlew`, `gradle/`, `local.properties`, `keystore.properties`, `firebase.json`, `.firebaserc`,
`firestore.rules`, `firestore.indexes.json`, `.gitignore`.

---

# Reference: follow one tap through the whole system

**"User taps ✓ (Complete) on a habit card on the Today screen."**

1. **`ui/home/HomeScreen.kt`** — the card's `onComplete` lambda fires →
   `viewModel.completeItem(item)` (look for the reminder-card composable and its `onComplete=`).
2. **`ui/home/HomeViewModel.kt`** — `completeItem` (or similarly named) →
   `viewModelScope.launch { scheduler.completeHabit(item.occurrenceId) }`.
3. **`data/OccurrenceScheduler.kt`** `completeHabit(occurrenceId)` → `resolveHabit(occurrenceId,
   Status.COMPLETED, Action.COMPLETED)`, under `syncMutex.withLock`:
   - `notificationUtils.cancelNotification(occ.notificationId)` — clear any shade notification,
     first, before any early return.
   - `db.habitOccurrenceDao().updateStatusResponded(occurrenceId, "COMPLETED", now)` — the write.
   - `db.habitEventDao().insert(HabitEvent(occurrenceId, action = COMPLETED, itemId = occ.habitId))`
     — append-only history.
   - `notificationUtils.cancelReminderAlarm(occ.id, occ.notificationId, isHabit = true)` — kill the
     re-nag alarm.
   - `armNextHabitInternal(occ.habitId, allowCatchup = false)` — arm the *next scheduled*
     occurrence (never re-fire a different overdue one).
4. **Room** commits the `UPDATE` + `INSERT`. The `habit_occurrences` and `habit_events` tables are
   now dirty.
5. **The observing `Flow`s re-emit.** `HomeViewModel`'s day-items flow (built on
   `habitOccurrenceDao().getAllOccurrencesInTimeRange(...)` etc.) produces a new list with this
   occurrence now `COMPLETED`.
6. **`HomeViewModel`'s `StateFlow` updates**, and the "Your progress → Habits" ratio recomputes;
   `StreakCalculator` reruns off the new statuses.
7. **Compose recomposes** just the affected card (it fades/collapses out of the pending list) and
   the progress card's ring.
8. **The scheduler already armed the next occurrence** in step 3.
9. **If signed in:** the Room write fired the `InvalidationTracker.Observer` in
   **`data/sync/CloudSyncRepository.kt`** (`attachTracker`), which set `syncState.pendingPush =
   true` and `changes.tryEmit(Unit)`. After the 3-second `debounce`, `doPush(force = false)` runs:
   it exports, partitions by month, hashes, sees this month's `contentHash` changed, and writes
   *only* that one `users/{uid}/months/{YYYY-MM}` document (plus a parent `monthHashes` update).
   The `definitionsHash` is unchanged (a completion isn't a definition change), so the parent
   `definitions` blob is not rewritten.

Every hop is a file you can open and a function you can breakpoint.

---

# Reference: project-specific footguns

A blunt list. Each of these has bitten someone (usually me).

- **No git here (until you fixed it in Module 1).** If you skipped 1a, do it now.
- **A same-`versionCode` APK will not install as an update.** Android's package manager rejects it.
  You must bump `versionCode`, or uninstall first (wiping local data). This is why every
  regression doc has a scary box about it.
- **Notification channels are immutable once created.** Changing a channel's importance in code
  does nothing after the first install. A user's "block this channel" survives updates. The only
  fix is to **bump the channel-id suffix** (`habits_v2` → `habits_v3`) and delete the old one on
  startup. See `NotificationUtils.CHANNEL_HABITS` comment.
- **The launch-gate "never route from an unsettled snapshot" rule.** In `MainActivity`'s
  `when { }`, every `null` / `Loading` state must render the *same* neutral splash
  (`Box(Modifier.fillMaxSize().background(DaybookColors.Bg))`). Rendering `OnboardingScreen` (or
  anything else) while auth/onboarding state is still resolving reintroduces the
  "onboarding-screen flashes on every launch" bug.
- **`Motion` / `AppShapes` / `Spacing` / `IconSize` in `ui/theme/Tokens.kt` are compile-time
  constants** (plain `object`s), *not* a `CompositionLocal`. Read them directly; don't try to
  "provide" them through the composition. They're deliberately global.
- **`SoftCard`'s `graphicsLayer` is applied unconditionally** (never behind an `if`). A conditional
  modifier swap rebuilds the modifier coordinator chain twice per tap. The v0.5.1 §9
  "yellow-blob GPU leak" fix depends on it staying unconditional. If you touch `SoftCard`, keep
  `scaleMod = Modifier.graphicsLayer { ... }` outside any conditional.
- **The pager: `goToPage` uses `scrollToPage` (instant snap), not `animateScrollToPage`**, and
  `beyondViewportPageCount = 1`. Both are deliberate anti-stutter choices with long comments in
  `MainActivity.MainApp`. Don't "improve" them to animate or to 0 without measuring on a low-end
  device.
- **Today is always page index 0.** `BackHandler`, deep-link fallbacks, and `NavConfig` rely on
  it. The configurable-tabs feature can hide Habits/Intake, never Today, never reorder.
- **kapt makes builds slow** and there's nothing to do about it short of migrating Room + Hilt to
  KSP (a real project, deliberately not done — see the top-level `build.gradle.kts` comment). Warm
  the daemon; don't `clean` unless you need to.
- **`while pgrep -f '<gradle args>'; do ...; done` to wait on a build hangs forever** — the pattern
  matches the watch loop's own command line. Run Gradle in the foreground.
- **Incremental builds can leave a stale schema `<n>.json`.** Always run the migration gate on a
  `clean` build, and eyeball the `git diff` of the new `<n>.json`.
- **`@EncodeDefault(EncodeDefault.Mode.NEVER)` on every new optional backup/wire field.** Forget
  it and every user's app re-uploads its entire history on next launch. This regression shipped
  three times. Hash tests now guard it — run `testDebugUnitTest`.
- **`app_settings` is NOT in `CloudSyncRepository.DATA_TABLES`** on purpose — a settings write
  must not trigger a cloud round-trip. Device-local preferences (accent, font, week-start, quiet
  hours, …) are not synced and not in the backup; a reinstall resets them. That's by design and
  called out in `CUSTOMIZATION_REGRESSION.md`.
- **Firestore rules need `firebase deploy --only firestore:rules`** if you ever change
  `firestore.rules`. The nested `users/{uid}/months/{month}` match does *not* inherit the
  `users/{uid}` rule — both blocks are required or every month write is `PERMISSION_DENIED`.
- **`BroadcastReceiver`s get ~10 seconds** before the OS calls them hung. Every one here uses
  `goAsync()` + `withTimeout(8_000)`. Keep that budget if you add work.
- **Enums stored by Room / rendered by `.entries`**: append only, never reorder or rename
  (`HabitType`, `TaskType`, `RedFlag`, `Occurrence.Status`, `Event.Action`, `ColorTag`).

---

# Reference: where to learn more

Read these *after* the module they support — not before, or they'll be noise.

- **After Module 2 (Kotlin):** the official Kotlin tour — `kotlinlang.org/docs/kotlin-tour.html`
  (interactive, 1–2 hours). Then `kotlinlang.org/docs/coroutines-overview.html` after Module 9.
- **After Module 3–4 (Android + Gradle):** Android's "Build your first app" pathway on
  `developer.android.com/courses/pathways/android-basics-compose-unit-1` — skim it; you've already
  done the equivalent here.
- **After Modules 5–6 (Compose):** the "Jetpack Compose" pathway
  `developer.android.com/courses/pathways/compose`, and "Thinking in Compose"
  `developer.android.com/develop/ui/compose/mental-model`. The **Now in Android** app
  (`github.com/android/nowinandroid`) is Google's reference for exactly this
  Compose + Hilt + Room + Flow architecture — read its code once you've finished Module 12.
- **After Modules 7–8 (Room):** `developer.android.com/training/data-storage/room` and, crucially,
  `developer.android.com/training/data-storage/room/migrating-db-versions` before you do Module 15
  for real.
- **After Module 9 (Flow/coroutines):** `developer.android.com/kotlin/flow` and
  `kotlinlang.org/docs/flow.html`.
- **After Module 11 (Hilt):** `developer.android.com/training/dependency-injection/hilt-android`.
- **After Module 13 (alarms/notifications):**
  `developer.android.com/develop/background-work/services/alarms/schedule` and
  `developer.android.com/develop/ui/views/notifications`.
- **After Module 14 (Firestore):** `firebase.google.com/docs/firestore` and
  `firebase.google.com/docs/rules`.
- **Ongoing:** the release notes for each pinned library (Room, Compose BOM, Hilt, Firebase BOM) —
  every version bump in `app/build.gradle.kts` should be checked against its changelog, because
  this project's dependency comments exist precisely because a careless bump broke something once.

---

# Glossary

- **ADB** — Android Debug Bridge. The command-line tool that talks to a connected device/emulator
  (install APKs, read logs, run shell commands).
- **Activity** — an Android component that hosts one screen/window and has an OS-driven lifecycle
  (`onCreate`/`onResume`/`onStop`). This app has essentially one: `MainActivity`.
- **AGP** — Android Gradle Plugin. The Gradle plugin that teaches Gradle how to build an Android
  app. Pinned to 8.3.2 here.
- **androidTest** — instrumented tests that run on a device/emulator (they need the Android
  framework). Here: `MigrationTest`, `NavIconInflateTest`.
- **ANR** — "Application Not Responding". The OS dialog/kill you get when the main thread is
  blocked too long. Avoided by doing slow work in coroutines off the main thread.
- **APK** — Android Package. The installable app file; technically a signed zip of compiled code +
  resources + manifest.
- **AlarmManager** — the OS service for "run this at an exact wall-clock time", even when the app
  isn't running. Delivers a `PendingIntent` (usually to a `BroadcastReceiver`).
- **BOM** — Bill of Materials. A dependency that pins a whole family of libraries to
  mutually-compatible versions (Compose BOM, Firebase BOM).
- **BroadcastReceiver** — a component the OS instantiates to hand it a single event (alarm fired,
  boot completed, notification button tapped). ~10s to do its work.
- **Compose (Jetpack Compose)** — the declarative UI toolkit. You write `@Composable` functions
  that describe the screen for the current data; the runtime redraws on change.
- **`@Composable`** — annotation marking a function as UI-emitting; can only be called from other
  `@Composable`s.
- **CompositionLocal** — a way to pass a value implicitly down the Compose tree
  (`LocalAccent`, `LocalReduceMotion` here). Contrast `Tokens.kt` which are plain globals.
- **coroutine** — a unit of suspendable work. Started in a scope (`viewModelScope`,
  `lifecycleScope`); cancelled when the scope dies ("structured concurrency").
- **DAO** — Data Access Object. A Room `@Dao` interface whose methods carry the SQL; Room generates
  the implementation.
- **DI** — Dependency Injection. Having a framework construct and wire your objects instead of
  doing it by hand. Here: Hilt.
- **`.dp`** — density-independent pixel. A layout unit that's the same physical size across screen
  densities.
- **entity** — a Room `@Entity data class`; one class = one database table.
- **Firestore** — Firebase's cloud document database. Here it's a gzipped mirror of Room, never
  the source of truth.
- **Flow** — a cold asynchronous stream of values. A Room `Flow<List<T>>` re-emits on every table
  change.
- **Gradle** — the build system (dependency manager + task runner + build-script engine). Invoked
  via `./gradlew`.
- **Hilt** — the DI framework (built on Dagger). Reads `@Inject` / `@HiltViewModel` / `@Module`
  and generates the wiring at build time.
- **IDE** — Integrated Development Environment. Here: Android Studio.
- **`identityHash`** — a hash Room computes of a schema; stored in the DB and in each `<n>.json`.
  A mismatch on open means a migration didn't produce the expected schema.
- **`@Inject`** — marks a constructor (or field, in a receiver) for Hilt to satisfy.
- **JDK** — Java Development Kit. The Java toolchain Kotlin compiles against/onto. This project
  wants JDK 17 (21 works).
- **kapt** — Kotlin Annotation Processing Tool. Runs annotation processors (Room, Hilt) at build
  time to generate source. The main reason builds are slow. Newer alternative: **KSP** (not used
  here, deliberately).
- **keystore / `.jks`** — the file holding the signing key. An update must be signed with the same
  key as the installed version. `app/daybook-release.jks`; credentials in `keystore.properties`.
- **`LaunchedEffect`** — a Compose API to run a suspend block when a composable enters composition
  (or a key changes); the sanctioned place for side effects.
- **lint / `lintVitalRelease`** — Android's static checker. `lintVitalRelease` runs the
  release-blocking subset and is part of the 4-gate.
- **manifest (`AndroidManifest.xml`)** — declares the app's components, permissions, and launcher
  activity. The OS's table of contents for your app.
- **migration** — code (SQL) that transforms the database from one schema version to the next.
  Here: `MIGRATION_2_3` … `MIGRATION_16_17` in `Migrations.kt`.
- **Modifier** — a chainable Compose value attaching layout/appearance/behaviour to a composable.
- **`mutableStateOf` / `remember`** — create observable UI state; `remember` keeps it stable
  across recomposition. `rememberSaveable` also survives rotation/process death.
- **occurrence** — one concrete scheduled instance of a habit/task (a specific date+time slot),
  stored as a row with a `status`. The middle of the habit → occurrence → event model.
- **`PendingIntent`** — a token that lets another process (the OS) fire an `Intent` on your app's
  behalf later. Used for alarms and notification actions; request codes here are `notifId*4+slot`.
- **ProGuard / R8** — the release-build shrinker/obfuscator/optimiser. `isMinifyEnabled = true`.
  Missing `-keep` rules cause "works in debug, broken in release" bugs; `app/proguard-rules.pro`.
- **recomposition** — Compose re-running a composable because a state it read changed.
- **Repository** — the plain class layer between ViewModels and DAOs; the one home for data
  operations and the seam for swapping data sources.
- **Room** — the SQLite ORM: `@Entity` / `@Dao` / `@Database`.
- **SDK (Android SDK)** — Android's own libraries + tools (`adb`, build-tools, platforms).
  `ANDROID_HOME=/home/abhiram/android-sdk`.
- **sealed class/interface** — a closed set of subtypes, each able to carry its own data; `when`
  over one is exhaustive-checked. `SyncStatus`, `AuthState`, `HydrateResult`.
- **StateFlow** — a `Flow` with an always-available current value that emits only on change; the
  standard ViewModel→screen type.
- **`suspend`** — marks a function that can pause without blocking a thread; callable only from a
  coroutine or another `suspend fun`.
- **versionCode / versionName** — integer update ordinal (must strictly increase to install as an
  update) / human string. In `app/build.gradle.kts` `defaultConfig`.
- **ViewModel** — the state-and-logic holder for one screen; survives configuration changes;
  exposes `StateFlow`s, runs work in `viewModelScope`.
- **WorkManager** — the OS-friendly scheduler for deferrable background jobs (here:
  `WindowRefreshWorker` daily, `SyncFlushWorker` on demand). Not for exact-time reminders — that's
  `AlarmManager`.

---

*You've reached the end. If you did every Try-it-now and can answer the checkpoints, you can make
real changes to this app safely, and you've learned the core of modern Android along the way. Keep
this file. When something confuses you in six months, the answer is probably in here — and if it
isn't, add it.*
