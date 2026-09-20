# Plan — Exercise muscle-group filter + card redesign, equipment-thumbnail white backing, health source priority (2026-09-20)

Planning round only — **nothing in this document has been implemented.** Every file:line reference
is against the current working tree (uncommitted health-sync/workout-photo work already in place —
DB is at Room schema **v33**, `MIGRATION_32_33` is the last migration in `ALL_MIGRATIONS`). Do not
revert or touch any of that in-progress work; build on top of it.

This plan is written for an implementor agent with no other context. It covers five independent
items (A–E) from the owner's screenshot review. Each item lists: current behavior with file:line,
the exact change, and new files/keys/fields needed. Read `HEALTH_SYNC_BATTERY_AND_IMPORT_AUDIT_2026-09-20.md`
and `HEALTH_RANGE_TAB_AND_SYNC_AUDIT_2026-09-20.md` first if touching item D/E — they map the sync
machinery in detail and this plan does not repeat that map.

---

## Assumptions (ambiguous points, decided so no questions are needed)

1. **Item B ("fix this what I marked", no visible markup)** — read as: fix the inconsistent
   equipment-thumbnail backing in the History list's second card (dark/black equipment icon with no
   light backing vs. the other cards' person illustration on a light blue/white backing). Root cause
   and fix are identical to item C (`ExerciseThumbnail`'s tile background), so B and C are one code
   change applied once, verified on both screens explicitly named in the feedback (History + the
   picker/detail screens item C also covers).
2. **Item A — which screen gets the muscle-group filter.** `AddExerciseScreen.kt` (the exercise
   picker) **already has** a muscle-group filter (`PickerDockedBar`, `showGroupSheet` →
   `MuscleGroup` `SortSheet`, wired since a prior round). `WorkoutDetailScreen.kt`'s "Exercises" list
   (the screen in screenshot 1) has **no filter at all** — this is the actual gap the screenshot is
   about. Plan adds the filter to `WorkoutDetailScreen.kt` only; `AddExerciseScreen.kt` is not
   touched by item A (its filter is unrelated pre-existing work).
3. **Item D default source-priority behavior.** When the user has not chosen a preferred source for
   a metric family, Health Connect's `aggregate()` call still combines every contributing app's
   records (current, "wrong" per the owner's own words). Rather than silently keep summing by
   default, the fix makes "no explicit choice yet" resolve to a **deterministic single-source
   default** — the alphabetically-first package name among the sources seen for that metric on the
   most recent day with data — rather than the sum. Rationale: two Health Connect sources reporting
   the same day's steps are overwhelmingly a mirroring/duplication situation (e.g. Mi Fitness's data
   also flows into Google Fit), not two genuinely additive step counts, so picking one source is a
   safer default than summing; the new Settings section lets the user override this per metric group
   at any time, and the auto-picked source is shown in the "Source" line ("From Mi Fitness (auto)")
   so the choice is never silent.
4. **Item D settings home.** New section lives in Beast Mode's own settings screen
   (`WorkoutSettingsScreen.kt`), not the top-level app Settings screen (`SettingsScreen.kt`) —
   Health Connect / Beast Mode's health sync is entirely a Beast Mode concern (`WorkoutSettingsScreen`
   already hosts "Import my past data", "Refresh now", rest timer sound, etc.), and `SettingsScreen.kt`
   has no existing health-related content to be consistent with.
5. **Item D metric grouping.** One preference per **aggregate family**, matching how
   `HealthConnectReader.dayAggregate` already batches its Health Connect calls (`activitySourceApps`
   is already one union across Steps/Distance/Calories/HeartRate/Hydration/SpO2 — see
   `HealthConnectReader.kt:171-174`): a single "Activity & vitals" preference covers Steps, Distance,
   Active/Total Calories, Heart Rate (avg/min/max/resting), SpO2 and Hydration (all currently read
   off the *same* `AggregationResult`/SpO2-record pair, so they must share one source choice —
   picking different sources per sub-metric within that one `aggregate()` call is not possible without
   N separate Health Connect calls per day, which reintroduces the battery cost the sync audits spent
   two rounds reducing). Weight and Nutrition already have their own independent per-record source
   (`weightReadings`/`nutritionSourceApp`) and get their own two preferences. Sessions are excluded —
   they are never summed/aggregated (each is its own row), so there is nothing to dedupe.
6. **Item E scope.** "Old date's tile" is read as: every `HealthDetailSheet` "Source" row, for every
   `HealthCardKind` and every date (not just today), Day mode and Range mode. The per-card compact
   subtitle on `HealthTabScreen`'s tiles themselves is left as-is (unchanged scope, already terse by
   design) — only the detail-sheet "Source" row (the exact UI in screenshot 3) is in scope.

---

## Item A — Muscle-group filter + card redesign + muscle pill on `WorkoutDetailScreen`'s Exercises list

### Current behavior
`app/src/main/java/com/daybook/app/ui/workout/WorkoutDetailScreen.kt:136-165`:

```kotlin
Spacer(Modifier.height(Spacing.lg))
SectionHeader("Exercises")
}
items(state.exercises.sortedBy { it.orderIndex }, key = { it.id }) { block ->
    val info = state.exerciseInfo[block.exerciseId]
    val tint = muscleTint(info?.primaryMuscle ?: com.daybook.app.data.workout.MuscleGroup.OTHER)
    SoftCard(tint = tint, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ExerciseThumbnail(...)
            Spacer(Modifier.width(Spacing.md))
            Text(info?.name ?: "Exercise", style = DaybookText.CardTitle, color = tint.onFill)
        }
        if (!block.notes.isNullOrBlank()) { Text(...) }
        val blockSets = state.sets.filter { it.workoutExerciseId == block.id }.sortedBy { it.setNumber }
        blockSets.forEach { s -> Text(formatDetailSetLine(s, weightUnit), ...) }
    }
}
```

Issues:
- No filter control — every block in the session always renders, unconditionally.
- No muscle-group pill/badge anywhere on the card — the muscle group is only implied by the card's
  background tint (`muscleTint(...)`), which is not legible as a label (screenshot 1 shows plain
  cards with no text chip).
- Padding/spacing: the `Row` has no `Modifier.padding` of its own — `SoftCard`'s own inner padding
  (`cardInner = 20.dp`, `Tokens.kt:215`) is the only spacing, so the thumbnail/name row, the notes
  line and every set line all sit flush against each other with zero vertical rhythm — no
  `Spacer` between the name row and the first set line, none between set lines
  (`blockSets.forEach { s -> Text(...) }` with no `Spacer`/`Arrangement.spacedBy` between
  iterations). Compare `SessionRow` in `WorkoutHistoryScreen.kt:433-497`, which does use
  `Arrangement.spacedBy`/explicit `Spacer`s throughout — that is the pattern to copy here.

### Exact change

**1. `WorkoutDetailViewModel.kt`** — add filter state (mirrors the existing pattern for
`_muscleVolume`/`_exerciseInfo`, and mirrors `WorkoutHistoryViewModel.selectedMuscleGroupFilter`,
which already exists for the History screen — reuse the same on/off single-select semantics, not a
new pattern):

```kotlin
private val _muscleGroupFilter = MutableStateFlow<MuscleGroup?>(null)
val muscleGroupFilter = _muscleGroupFilter.asStateFlow()
fun setMuscleGroupFilter(group: MuscleGroup?) { _muscleGroupFilter.value = group }
```

Do **not** filter inside `DetailState`/`state` itself (that combine already has a lot feeding it,
and `sets`/`muscleVolume`/stats must stay computed off the *full* exercise list — filtering only
changes which cards are visible, not the session's real stats). Filtering happens in the Composable,
the same way `WorkoutHistoryScreen`'s `sections` already pre-filters at the VM layer for History —
but Detail's stat card must NOT reflect the filter, so keep it screen-side here specifically.

**2. `WorkoutDetailScreen.kt`**:
- Collect `val muscleGroupFilter by viewModel.muscleGroupFilter.collectAsStateWithLifecycle()`.
- Compute the distinct muscle groups actually present in this session, for the chip row:
  `val presentGroups = remember(state.exercises, state.exerciseInfo) { state.exercises.mapNotNull { state.exerciseInfo[it.exerciseId]?.primaryMuscle }.distinct() }`.
- Add a filter chip row as its own `item {}` right after `SectionHeader("Exercises")` (so it scrolls
  with the list, consistent with this screen's existing all-`item`-based layout — no docked/floating
  bar here, unlike `AddExerciseScreen`, since Detail is a single static page, not a picker):

```kotlin
if (presentGroups.size > 1) {
    Spacer(Modifier.height(Spacing.sm))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)
    ) {
        DaybookChip(
            label = "All",
            selected = muscleGroupFilter == null,
            onClick = { viewModel.setMuscleGroupFilter(null) },
            size = ChipSize.Small
        )
        presentGroups.forEach { g ->
            DaybookChip(
                label = MuscleGroupLabels[g].orEmpty(),
                selected = muscleGroupFilter == g,
                onClick = { viewModel.setMuscleGroupFilter(if (muscleGroupFilter == g) null else g) },
                size = ChipSize.Small
            )
        }
    }
}
Spacer(Modifier.height(Spacing.sm))
```
(new imports needed in this file: `androidx.compose.foundation.horizontalScroll`,
`androidx.compose.foundation.rememberScrollState`, `com.daybook.app.ui.components.DaybookChip`,
`com.daybook.app.ui.components.ChipSize`, `com.daybook.app.data.workout.MuscleGroupLabels` —
`MuscleGroup` is already imported via `state.exerciseInfo` usage elsewhere in this package).

- Change the `items(...)` call to filter before rendering, keeping the same `key`:
```kotlin
items(
    state.exercises.sortedBy { it.orderIndex }
        .filter { muscleGroupFilter == null || state.exerciseInfo[it.exerciseId]?.primaryMuscle == muscleGroupFilter },
    key = { it.id }
) { block -> ... }
```
- If the filtered list is empty (a group filter selected but, transiently, nothing matches — should
  not normally happen since chips are built from `presentGroups`, but guard anyway): wrap the
  `items(...)` block's content check, or simplest — compute `val filteredExercises = ...` above the
  `LazyColumn` items and add `if (filteredExercises.isEmpty()) item { EmptyState(...) }` else the
  `items(...)` call, reusing `EmptyState` (already imported pattern in `AddExerciseScreen.kt`,
  import `com.daybook.app.ui.components.EmptyState` here too).

**3. Card redesign (padding/spacing) + muscle pill**, same `SoftCard` block
(`WorkoutDetailScreen.kt:141-164`), rewritten as:

```kotlin
SoftCard(tint = tint, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ExerciseThumbnail(
            imageId = info?.imageId,
            hasStartPeak = info?.hasStartPeak ?: false,
            fallbackIcon = com.daybook.app.ui.icons.DaybookIcons.Category,
            tint = tint,
            size = 44.dp,
            primaryMuscle = info?.primaryMuscle ?: MuscleGroup.OTHER,
            equipment = info?.equipment ?: com.daybook.app.data.workout.Equipment.OTHER
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(info?.name ?: "Exercise", style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1)
            // New — the muscle-group pill (item A part 3). Reuses `DaybookChip` in its
            // non-interactive form (onClick = {} / a display-only variant) so it visually matches
            // every other chip in the app instead of introducing a new pill component.
            Spacer(Modifier.height(4.dp))
            MusclePill(group = info?.primaryMuscle ?: MuscleGroup.OTHER, tint = tint)
        }
    }
    if (!block.notes.isNullOrBlank()) {
        Spacer(Modifier.height(Spacing.sm))
        Text(block.notes!!, style = DaybookText.Caption, color = DaybookColors.TextMuted)
    }
    if (blockSets.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            blockSets.forEach { s ->
                Text(formatDetailSetLine(s, weightUnit), style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
            }
        }
    }
}
```
(`blockSets` must be hoisted above the `Row`/`if` — currently declared mid-block at
`WorkoutDetailScreen.kt:160`, keep it declared before the `Column` that renders it, same position is
fine, just referenced by the new `if (blockSets.isNotEmpty())` wrapper.)

Add a small private composable in this same file for the pill (`MusclePill`), styled as a small
rounded label using `tint.accent`/`tint.onFillMuted` (the same `CardTint` fields `MuscleVolumeBar`
in `WorkoutHistoryScreen.kt:621-629` already uses for a muscle-group label), NOT `DaybookChip`
directly (that component is click/selection-shaped with a `selected` boolean driving its visuals —
a plain read-only badge is simpler and avoids implying the pill is tappable):

```kotlin
@Composable
private fun MusclePill(group: MuscleGroup, tint: CardTint) {
    Box(
        Modifier
            .clip(AppShapes.pill)
            .background(tint.fillRaised)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(MuscleGroupLabels[group].orEmpty(), style = DaybookText.Caption, color = tint.onFillMuted)
    }
}
```
(`AppShapes.pill`, `CardTint` already available via existing imports `com.daybook.app.ui.theme.*` in
this file.)

This fixes: (1) filter — chips above the list; (2) padding/spacing — explicit `Spacer`s between the
name row/notes/sets instead of nothing, and `Arrangement.spacedBy(4.dp)` between individual set
lines instead of a bare `forEach`; (3) muscle pill — `MusclePill` under the exercise name on every
card.

---

## Item B/C — White backing behind every equipment thumbnail image, consistently

### Current behavior
`app/src/main/java/com/daybook/app/ui/workout/ExerciseThumbnail.kt:72-113`. The shared tile modifier:

```kotlin
val tileModifier = modifier
    .size(size)
    .clip(AppShapes.tile)
    .background(tint.fillRaised)
    .border(1.dp, DaybookColors.Hairline, AppShapes.tile)
```
is applied identically to **all three** image branches (RepDB photo, equipment illustration, muscle
illustration) plus the icon fallback. `tint.fillRaised` is the card's own muscle-group pastel tint
(`muscleTint(exercise.primaryMuscle)`, varies per exercise — e.g. a dark/desaturated tint for some
muscle groups), not a fixed light color. The equipment `.webp` assets
(`app/src/main/assets/exercises/images/equipment/*.webp`, e.g. `barbell.webp`, `dumbbell.webp`) are
flat-colored icon-style illustrations (often dark strokes on transparent background) designed to sit
on a **white/light** backing — when `tint.fillRaised` happens to be a darker/desaturated tint (the
History screenshot's second card, Wednesday 16 Sept), the icon reads as a dark blob with no visible
backing, inconsistent with every other card whose tint is light enough that the same background
reads as "light blue/white".

### Exact change
`ExerciseThumbnail.kt` — give the **equipment branch specifically** a fixed light background,
independent of `tint`, so it never varies with the exercise's muscle-group color. Do not change the
RepDB-photo or muscle-illustration branches (those are full-bleed photographic/illustrated content
meant to fill the tile edge-to-edge on the card's own tint — only the equipment icon is a
small-glyph-on-white illustration that needs a fixed backing).

```kotlin
private val EquipmentThumbnailBg = Color(0xFFF5F5F0) // fixed light backing, independent of card tint

val tileModifier = modifier
    .size(size)
    .clip(AppShapes.tile)
    .background(tint.fillRaised)
    .border(1.dp, DaybookColors.Hairline, AppShapes.tile)

// ... existing branches ...
} else if (equipmentSlug != null) {
    AsyncImage(
        model = "file:///android_asset/exercises/images/equipment/$equipmentSlug.webp",
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onError = { equipmentFailed = true },
        modifier = modifier
            .size(size)
            .clip(AppShapes.tile)
            .background(EquipmentThumbnailBg)
            .border(1.dp, DaybookColors.Hairline, AppShapes.tile)
    )
}
```
(new import: `androidx.compose.ui.graphics.Color`, likely already imported in this file — check;
if not, add `import androidx.compose.ui.graphics.Color`.) Use a literal warm off-white
(`0xFFF5F5F0`) rather than pure `Color.White` or `DaybookColors.Surface`/`DaybookColors.Bg` — those
are theme-dependent (dark theme's `Surface`/`Bg` are near-black, which would reproduce the exact bug
being fixed) and this must be a **fixed** light color regardless of app theme, matching the "always
white" ask verbatim. If the app already defines a themed-independent "paper"/"canvas" white token
(check `DaybookColors.kt`/`Tokens.kt` for anything named `Paper`/`White`/`OnLight` before adding the
literal — reuse it if one exists to avoid a second magic color; only fall back to the literal above
if no such constant exists).

Because this fix lives in `ExerciseThumbnail`'s equipment branch, it automatically applies
everywhere the composable is called with `equipment != Equipment.OTHER` — no per-caller changes
needed. Call sites already found (`grep -rn "ExerciseThumbnail(" app/src/main/java`):
- `WorkoutDetailScreen.kt:143` (item A's screen)
- `WorkoutHistoryScreen.kt:434` (item B — the History screenshot's screen)
- `AddExerciseScreen.kt:324` (the exercise picker)
- Verify with a fresh grep before finishing (`grep -rn "ExerciseThumbnail(" app/src/main/java/com/daybook/app`) in case routines/other screens also call it — the fix must cover every call site by construction, so this is a verification step, not a per-site edit.

---

## Item D — Per-metric "preferred health data source" setting, used during aggregation instead of naive summing

### Current behavior (confirmed root cause)
`HealthConnectReader.dayAggregate` (`app/src/main/java/com/daybook/app/data/health/HealthConnectReader.kt:98-198`):

```kotlin
val result = if (metrics.isEmpty()) null else runCatchingCancellable {
    client().aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = filter))
}.getOrNull()
...
steps = result?.valueOrNull(StepsRecord.COUNT_TOTAL),
```

`AggregateRequest`'s real constructor (confirmed by inspecting the compiled class,
`connect-client-1.1.0-alpha08-api.jar`) is:
```
AggregateRequest(metrics: Set<AggregateMetric<*>>, timeRangeFilter: TimeRangeFilter, dataOriginFilter: Set<DataOrigin> = emptySet())
```
The app never passes `dataOriginFilter`, so it defaults to empty, which per the Health Connect
contract means **aggregate across every contributing app's records** — if both Google Fit and Mi
Fitness wrote `StepsRecord`s for the same day (a very common mirroring setup — one app synced into
the other, both then present in Health Connect), `StepsRecord.COUNT_TOTAL` sums both, double-counting
overlapping/duplicate step data. This is exactly the behavior the owner flagged as "wrong" and
exactly why the Steps & distance sheet in screenshot 3 shows `"From Google Fit, Mi Fitness"` — that
line (`SourceAppLabels.labelForCsv`, `HealthLabels.kt:99-101`) already **detects** the multi-source
case, it just never **acts** on it — the underlying number is still the summed one.

Confirmed this is a real, reachable code path (not hypothetical) — `activitySourceApps` already
carries `result?.dataOrigins?.map { it.packageName }` (`HealthConnectReader.kt:171-174`), i.e. the
app already asks Health Connect "who contributed to this aggregate" and gets back >1 answer in
exactly this scenario.

Same shape, separate raw-record reads, for:
- **Weight** — `weightRecords` (`HealthConnectReader.kt:141-152`), `lastWeightKg = weightRecords.maxByOrNull { it.time }?.weight?.inKilograms` — not summed (uses the latest reading, not a sum), but with two sources both writing weight, "latest" can flip-flop between apps rather than consistently preferring one.
- **Nutrition** — `nutritionRecords` (`:157-164`), calories/protein/carbs/fat are `sumOf { ... }` across **all** records regardless of source (`:160-163`) — the same double-count risk as Steps if two nutrition-logging apps both write to Health Connect for the same meal.

### Design

**New `AppSettings` columns** (Room migration `MIGRATION_33_34`, DB version bump to **34**), one
nullable `TEXT` per aggregate family, storing the chosen Health Connect package name (`null` =
"auto", the item-3 default):

```kotlin
// app/src/main/java/com/daybook/app/data/model/DataModel.kt, appended to AppSettings
/** Preferred Health Connect source package for the combined activity/vitals aggregate (Steps,
 *  Distance, Active/Total Calories, Heart rate, SpO2, Hydration — all read off one
 *  `HealthConnectReader.dayAggregate` call, see that function's KDoc for why they share one
 *  preference). Null = auto (deterministic first-seen source, not a sum — see
 *  `HealthSourcePriority` KDoc). DEVICE-LOCAL, same treatment as every app_settings column since
 *  v16: not in DATA_TABLES, not in BackupModel, not in ContentHash — a source preference is a
 *  per-install/per-device choice, not user content to sync. */
@ColumnInfo(name = "health_source_activity") val healthSourceActivity: String? = null,
/** Preferred source for Weight readings. Same null=auto contract as [healthSourceActivity]. */
@ColumnInfo(name = "health_source_weight") val healthSourceWeight: String? = null,
/** Preferred source for Nutrition (calories/protein/carbs/fat). Same contract. */
@ColumnInfo(name = "health_source_nutrition") val healthSourceNutrition: String? = null
```

**`AppSettingsDao.kt`** — three new targeted updates, same pattern as `updateRestTimerSoundUri`:
```kotlin
@Query("UPDATE app_settings SET health_source_activity = :v WHERE id = 1")
suspend fun updateHealthSourceActivity(v: String?)
@Query("UPDATE app_settings SET health_source_weight = :v WHERE id = 1")
suspend fun updateHealthSourceWeight(v: String?)
@Query("UPDATE app_settings SET health_source_nutrition = :v WHERE id = 1")
suspend fun updateHealthSourceNutrition(v: String?)
```

**`AppSettingsRepository.kt`** — three wrapper methods, same `ensureRow(); database.appSettingsDao().updateX(v)` pattern as every other setter in the file (insert after `setCheckForUpdatesEnabled` or near other health-adjacent setters).

**`Migrations.kt`** — append after `MIGRATION_32_33`:
```kotlin
/**
 * v33 -> v34 — [owner feedback, 2026-09-20]. Three additive nullable app_settings columns: the
 * user's preferred Health Connect source package per aggregate family (Activity/Vitals, Weight,
 * Nutrition — see AppSettings' own KDoc on each). Null (the default, no ALTER-time backfill needed
 * since SQLite ALTER TABLE ADD COLUMN defaults every existing row to NULL for a nullable column with
 * no DEFAULT) means "auto" — resolved at read time by HealthSourcePriority, never persisted as a
 * concrete package until the user actually picks one in Settings. Nothing existing is read or
 * rewritten.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_activity TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_weight TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_nutrition TEXT")
    }
}
```
Add `MIGRATION_33_34` to the `ALL_MIGRATIONS` array (`Migrations.kt:918-925`, append at the end).

**`AppDatabase.kt`** — bump `version = 33` to `version = 34` (`AppDatabase.kt:66`), add a one-line
comment above the entities array following the existing house style (see the `v31->v32`/`v32->v33`
comments at `AppDatabase.kt:57-63`) describing the new columns.

**New file `app/src/main/java/com/daybook/app/data/health/HealthSourcePriority.kt`** — the pure
resolution logic, kept separate from `HealthConnectReader` (which stays Health-Connect-API-only per
its own KDoc, `HealthConnectReader.kt:31-39`) and separate from `HealthSyncLogic.kt` (which is about
Room-side merge, a different concern):

```kotlin
package com.daybook.app.data.health

import androidx.health.connect.client.records.metadata.DataOrigin

/**
 * [owner feedback, 2026-09-20] — resolves which Health Connect `DataOrigin`(s) an aggregate read
 * should be restricted to for one metric family, given the user's stored preference (a package name,
 * or null for "auto").
 *
 * - A non-null, non-blank preference restricts the read to exactly that source
 *   (`AggregateRequest.dataOriginFilter = setOf(DataOrigin(pref))`), so Health Connect's own
 *   `aggregate()` never combines it with any other app's records — this is what actually fixes the
 *   double-count, not a client-side re-computation after the fact (Health Connect does not expose a
 *   per-origin breakdown of an already-combined `AggregationResult`).
 * - "Auto" (null preference) is NOT "no filter" (the old, buggy default) — see [resolveAuto]. It
 *   still restricts to exactly one source, chosen deterministically, so the "wrong to add them" bug
 *   is fixed even for a user who never opens Settings. The `dataOrigins` a *previous* unrestricted
 *   read observed (already collected today via `activitySourceApps`/per-record `sourceApp`) is the
 *   candidate list `resolveAuto` picks from.
 */
object HealthSourcePriority {

    /** Package-name sort is deliberately simple/deterministic (not "most records" or "most recent")
     *  so the same install always picks the same source without needing a scan of historical data
     *  first — a user who cares which one wins can always override it in Settings. */
    fun resolveAuto(candidates: Collection<String>): String? = candidates.filter { it.isNotBlank() }.minOrNull()

    /** [preference] wins outright if set; otherwise [resolveAuto] over [observedCandidates]
     *  (e.g. the previous day's `activitySourceApps`/per-record sources, passed in by the caller —
     *  this object does no I/O of its own). Returns null when there is nothing to restrict to (no
     *  preference AND no candidates observed yet), in which case the caller should pass an empty
     *  `dataOriginFilter` (today's behavior — the very first read, before any source is known, must
     *  still return data). */
    fun resolve(preference: String?, observedCandidates: Collection<String>): String? =
        preference?.takeIf { it.isNotBlank() } ?: resolveAuto(observedCandidates)

    fun toDataOriginFilter(resolvedPackage: String?): Set<DataOrigin> =
        resolvedPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
}
```

**`HealthConnectReader.dayAggregate` signature change** — add source-preference parameters so the
caller (`HealthRepository`) can pass the resolved package(s) without this reader knowing about
`AppSettings`/Room (keeps the "only file that imports `androidx.health.connect.*`" boundary intact,
per its own KDoc):

```kotlin
suspend fun dayAggregate(
    date: LocalDate,
    zoneId: ZoneId,
    granted: Set<String>,
    activitySourcePackage: String?,   // resolved package for Steps/Distance/Calories/HR/SpO2/Hydration, or null = no restriction
    weightSourcePackage: String?,
    nutritionSourcePackage: String?
): DayAggregate {
    ...
    val result = if (metrics.isEmpty()) null else runCatchingCancellable {
        client().aggregate(
            AggregateRequest(
                metrics = metrics,
                timeRangeFilter = filter,
                dataOriginFilter = HealthSourcePriority.toDataOriginFilter(activitySourcePackage)
            )
        )
    }.getOrNull()
    // SpO2 raw-record read — filter client-side by dataOrigin.packageName, since ReadRecordsRequest
    // also supports a dataOriginFilter param directly:
    val spo2 = runCatchingCancellable {
        client().readRecords(
            ReadRecordsRequest(
                OxygenSaturationRecord::class, filter,
                dataOriginFilter = if (activitySourcePackage != null) setOf(DataOrigin(activitySourcePackage)) else emptySet()
            )
        ).records
    }.getOrDefault(emptyList())
    ...
    val weightRecords = runCatchingCancellable {
        client().readRecords(
            ReadRecordsRequest(
                WeightRecord::class, filter,
                dataOriginFilter = if (weightSourcePackage != null) setOf(DataOrigin(weightSourcePackage)) else emptySet()
            )
        ).records
    }.getOrDefault(emptyList())
    ...
    val nutritionRecords = runCatchingCancellable {
        client().readRecords(
            ReadRecordsRequest(
                NutritionRecord::class, filter,
                dataOriginFilter = if (nutritionSourcePackage != null) setOf(DataOrigin(nutritionSourcePackage)) else emptySet()
            )
        ).records
    }.getOrDefault(emptyList())
```
(`ReadRecordsRequest` also takes an optional `dataOriginFilter: Set<DataOrigin> = emptySet()` — same
family of API, confirm the exact parameter name against the installed `connect-client-1.1.0-alpha08`
sources/decompiled class before wiring — the `AggregateRequest` one was verified directly against the
compiled class in this repo's Gradle cache; `ReadRecordsRequest` should be spot-checked the same way
if it does not compile, but this is the standard, documented Health Connect API shape.)

**Auto-resolution needs a candidate list before any preference exists.** Simplest correct source: at
the top of `HealthRepository.fetchDay` (or wherever `dayAggregate` is called, `HealthRepository.kt:420`
area), do a cheap **first pass with no origin filter restricted to just the changes/dataOrigins
lookup** — actually simpler: reuse the *previous stored day's* `activitySourceApps` CSV
(`HealthDao`, already persisted) as the auto candidate pool, since by the time a second sync happens
there is already a stored day to look at. For the very first-ever sync (no stored day yet to seed
`resolveAuto`), fall back to `null` (no restriction) for that single call — acceptable because it's a
one-time bootstrap read, not the steady state the owner is reporting on, and `activitySourceApps`
recorded by that first unrestricted read then seeds every subsequent auto-resolution. Implement as:

```kotlin
// HealthRepository.kt, near fetchDay
private suspend fun resolvedSourcePreferences(): SourcePreferences {
    val settings = appSettingsRepository.getSettings()
    val recentCandidates = healthDao.mostRecentActivitySourceApps() // new DAO query, see below
        ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    return SourcePreferences(
        activity = HealthSourcePriority.resolve(settings.healthSourceActivity, recentCandidates),
        weight = HealthSourcePriority.resolve(settings.healthSourceWeight, recentCandidates),
        nutrition = HealthSourcePriority.resolve(settings.healthSourceNutrition, recentCandidates)
    )
}
data class SourcePreferences(val activity: String?, val weight: String?, val nutrition: String?)
```
New `HealthDao` query (add next to the other single-column health lookups):
```kotlin
@Query("SELECT activity_source_apps FROM health_days WHERE activity_source_apps IS NOT NULL ORDER BY local_date DESC LIMIT 1")
suspend fun mostRecentActivitySourceApps(): String?
```
Call `resolvedSourcePreferences()` once per `pullWindow` (not once per day — the sources don't change
within one sync pass) and thread `activity`/`weight`/`nutrition` into every `dayAggregate(...)` call
in the loop.

**Why this fixes the "adding 2 sources" bug**: restricting `AggregateRequest.dataOriginFilter` to one
`DataOrigin` makes Health Connect's own `aggregate()` compute `COUNT_TOTAL`/`ENERGY_TOTAL`/etc. only
from that one app's records for the window — no client-side subtraction/estimation is needed, and no
existing correctness logic (`HealthSyncLogic.mergeHealthDay`, `automaticWindowFloor`, D-1/D-2 fixes
from the September audits) needs to change, since this is purely a change to what gets fed into the
existing pipeline, not a new merge step.

### Settings UI — `WorkoutSettingsScreen.kt` / `WorkoutSettingsViewModel.kt`

Add a new section "Data source priority" (three rows: Activity & vitals / Weight / Nutrition), same
list-item + bottom-sheet-picker pattern this screen already uses for other single-choice settings
(check the existing "Rest timer sound" or similar row in `WorkoutSettingsScreen.kt` for the exact
row/sheet composable names and copy that pattern verbatim — do not invent a new settings-row style).
Each row's current value is displayed via `SourceAppLabels.labelFor(pref) ?: "Auto"`; tapping opens a
picker listing every distinct package name seen in `HealthDao.mostRecentActivitySourceApps()` (parse
the CSV) formatted through `SourceAppLabels.labelFor`, plus an explicit "Auto" option (`null`) at the
top. `WorkoutSettingsViewModel` exposes:
```kotlin
val healthSourceActivity = appSettingsRepository.observeSettings().map { it.healthSourceActivity }...
fun setHealthSourceActivity(pkg: String?) = viewModelScope.launch { appSettingsRepository.setHealthSourceActivity(pkg) }
// ...and the weight/nutrition equivalents
```
On any of the three changing, trigger the same "Refresh now" pull path this screen already exposes
(`WorkoutSettingsViewModel.kt:137`, per the earlier audit's map) so the change takes effect without
waiting for the next automatic sync — call the existing refresh function, do not add a second one.

---

## Item E — Show "Source" consistently for every vitals tile, every date (current + historical)

### Current behavior
`HealthDetailSheet.kt:94-220` — the `DetailRows` composable **already** renders a `"Source"`
`DetailRow` for STEPS (`:110`), CALORIES (`:120`), HEART_RATE (`:134`), SPO2 (`:173`), WEIGHT
(`:183`), HYDRATION (`:192`) and NUTRITION (`:216`), for both `isDay` and Range-mode branches. The
gap is not in the UI code, it's in the **data**: `DetailRow` silently renders nothing when its
`value` is null (`DetailRow`, `HealthDetailSheet.kt:248-249`: `if (value == null) return`), and
`activitySource` is built from `day?.activitySourceApps` (`:104`) — the `health_days.activity_source_apps`
column, added by `MIGRATION_32_33` (`Migrations.kt:902-907`) as a **nullable column with no
backfill** (`ALTER TABLE health_days ADD COLUMN activity_source_apps TEXT` — every pre-existing row
gets `NULL`).

A historical day's row was written by a pull that happened **before** this column existed, or by a
pull whose window didn't happen to re-touch that day since (per the September audits'
`automaticWindowFloor` cap of 90 days, `HealthSyncLogic.kt:76-89`, an automatic resync never reaches
back past 90 days). So:
- Any day older than the last time it was re-synced (which, for most historical days, is "never,
  since the column was added") has `activitySourceApps = NULL` in `health_days` forever, unless the
  user does an explicit "Import my past data" (365-day window) again.
- `SourceAppLabels.labelForCsv(null)` returns `null` (`HealthLabels.kt:99-101`, guarded by
  `?.takeIf { it.isNotBlank() }`), so `activitySource` is `null`, so the "Source" `DetailRow` renders
  nothing for that day — exactly the "doesn't show for old dates" symptom, while today's (or any
  recently-re-synced) day, whose row was written after the column existed, shows it fine — which is
  why the screenshot (today's Steps sheet) shows the source line at all.
- WEIGHT and NUTRITION's source rows (`state.dayWeightReadings`/`day?.nutritionSourceApp`) are
  **not** affected the same way — `nutrition_source_app` and the weight readings' own `sourceApp` are
  columns that existed before this round (older, always populated when there was any data at all) —
  so this is specifically a `activity_source_apps` (Steps/Calories/Heart rate/SpO2/Hydration)
  back-fill gap, not a general "source is only shown for today" bug in the UI layer.

### Exact change

This is a **data backfill + a defensive UI fallback**, not a UI logic rewrite (the UI already does
the right thing whenever the data exists):

**1. Backfill via a bounded, one-time resync**, so old rows get `activity_source_apps` populated
without re-opening the unbounded-resync problem the September audits (B-1) already fixed. Add to
`HealthRepository.kt`, near `importPastData()` (`:95-139`):

```kotlin
/** [owner feedback, 2026-09-20] — one-time backfill so every already-synced day (not just days
 *  touched by a future resync) gets `activity_source_apps` populated, without reopening B-1's
 *  unbounded-window bug (HEALTH_SYNC_BATTERY_AND_IMPORT_AUDIT_2026-09-20.md §1.2). Bounded to
 *  exactly the local date range `health_days` already has rows for (`healthDao.earliestLocalDate()`
 *  to today), i.e. it never reads further back than the user's own history already covers, and it
 *  reuses the same `pullWindow` merge-aware upsert (HealthSyncLogic.mergeHealthDay) so it can only
 *  ever fill in a null `activitySourceApps`, never touch/overwrite any other field. Runs once,
 *  gated by a new `app_settings` flag so it is never repeated on every app start. */
suspend fun backfillActivitySourceApps() {
    val settings = appSettingsRepository.getSettings()
    if (settings.activitySourceBackfillDone) return
    val earliest = healthDao.earliestLocalDate() ?: run {
        appSettingsRepository.setActivitySourceBackfillDone(true); return
    }
    val start = LocalDate.parse(earliest)
    val today = LocalDate.now(zoneId)
    runCatchingCancellable { pullWindow(start, today, isBackfill = true) }
    appSettingsRepository.setActivitySourceBackfillDone(true)
}
```
New `HealthDao` query: `@Query("SELECT MIN(local_date) FROM health_days") suspend fun earliestLocalDate(): String?`.
New `AppSettings` column (fold into the **same** `MIGRATION_33_34` migration as item D, since both
are additive `app_settings` columns being added in the same round — one migration, not two):
```kotlin
@ColumnInfo(name = "activity_source_backfill_done", defaultValue = "0") val activitySourceBackfillDone: Boolean = false
```
```sql
ALTER TABLE app_settings ADD COLUMN activity_source_backfill_done INTEGER NOT NULL DEFAULT 0
```
Add the matching `AppSettingsDao.updateActivitySourceBackfillDone` / `AppSettingsRepository.setActivitySourceBackfillDone`, same pattern as every other boolean flag in that file (e.g. `setExerciseDetailsPromptDismissed`).

Call `healthRepository.backfillActivitySourceApps()` once from `HealthRepository`'s existing
app-resume pull path (`pullOnResume`, per the audit's map at `HealthRepository.kt:81-85`) — launch it
as a fire-and-forget `viewModelScope`/repository-scope coroutine **after** the normal resume pull
completes, so it never competes with or blocks the foreground sync, and it is a plain bounded window
read (same cost model as a normal resync over that range — bounded by how much history the user
already has, which by definition already exists in Room, so this is not a new "365 day" style blow-up)
gated to run only once thanks to the `activitySourceBackfillDone` flag.

**2. Defensive UI fallback for the remaining edge case** (a day with genuinely zero recorded activity
data ever, or the backfill hasn't run yet on a fresh install with old data restored from a Beast Mode
backup that predates this column) — change `HealthDetailSheet.kt`'s `activitySource` line (`:104`) to
show an explicit "unknown source" caption instead of silently omitting the row, so the row is
**always present** once there is a value to show for that metric at all:

```kotlin
val activitySource = SourceAppLabels.labelForCsv(if (isDay) day?.activitySourceApps else agg.activitySourceApps)
    ?.let { "From $it" }
    ?: run {
        // Only show the "unknown" fallback when there IS a value for this metric on this day/range
        // (no point saying "source unknown" under a blank/no-data row) — the per-kind branches below
        // already gate their own DetailRow calls on the metric value being non-null, so passing this
        // fallback string through unconditionally here is safe: DetailRow("Source", ...) still no-ops
        // when the caller's own value check already skipped the row entirely above it.
        "Source unknown (synced before source tracking was added)"
    }
```
This still respects "a day with literally nothing to show shows nothing" (each per-kind branch above
already independently checks its own metric value before rendering anything for that kind — the
Source row simply always accompanies whatever else that kind did render). Apply the same
fallback text for WEIGHT (`:183`, wrap `SourceAppLabels.labelFor(...)?.let { "From $it" }` with the
same `?: "Source unknown..."`, but only inside the existing `if (isDay)` branch since that row only
exists for Day mode today) and NUTRITION (`:216`, currently `SourceAppLabels.labelFor(sourceApp) ?: "Another app"` — already has a fallback, leave as-is, it is not affected by this gap since `nutrition_source_app` isn't a backfill-affected column).

**Range mode** — `agg.activitySourceApps` (`HealthAggregate`, referenced at `HealthDetailSheet.kt:104`)
is computed by `aggregateHealthDays` (`HealthAggregation.kt`) unioning each day's own
`activitySourceApps` across the range — once step 1's backfill has run, this becomes populated
automatically for historical ranges too, no separate Range-mode fix needed (verify
`HealthAggregation.kt`'s exact field name/union logic before wiring — grep `activitySourceApps` in
that file to confirm it already unions rather than only reading the range's most recent day).

---

## Build/verify checklist

1. `./gradlew :app:compileDebugKotlin` — confirms every Kotlin change (items A–E) compiles, including
   the new `HealthSourcePriority.kt` file, the `WorkoutDetailScreen.kt`/`WorkoutDetailViewModel.kt`/
   `ExerciseThumbnail.kt`/`HealthConnectReader.kt`/`HealthRepository.kt`/`HealthDetailSheet.kt`/
   `WorkoutSettingsScreen.kt`/`WorkoutSettingsViewModel.kt` edits and the `AppSettings`/`AppSettingsDao`/
   `Migrations.kt`/`AppDatabase.kt` schema changes.
2. `./gradlew :app:testDebugUnitTest` — exercises `ExerciseCatalogTest.kt` (unaffected but must still
   pass) and any existing Health aggregation unit tests; add no new test files unless the implementor
   chooses to (not required by this plan, but `HealthSourcePriority.resolveAuto`/`resolve` are pure
   functions and are good candidates for a quick unit test if time allows).
3. `./gradlew :app:connectedDebugAndroidTest --tests "*.MigrationTest"` (or run `MigrationTest.kt`
   directly) — **required**, not optional: it must exercise `fullOpenAtLatestVersion()` at the new
   version 34 and a migrate-from-33 path through `MIGRATION_33_34`, per that test's own stated purpose
   (`Migrations.kt:909-917`'s comment references it explicitly as the drift-catcher for
   `ALL_MIGRATIONS`).
4. Manual smoke check on a device/emulator with Health Connect installed and at least two source apps
   (or simulate via Health Connect's own "add data" test tooling) — confirm: (a) `WorkoutDetailScreen`'s
   Exercises list shows the new filter chips and muscle pills with visibly improved spacing; (b) an
   equipment-thumbnail card in History (and every other screen) shows a consistent light backing
   regardless of the exercise's muscle-group tint; (c) Beast Mode Settings has the new "Data source
   priority" section and changing a preference is reflected in the next Steps & distance sheet's
   "Source" line; (d) a historical (e.g. 2+ months old) day's detail sheet shows a Source row.
5. `./gradlew :app:lintDebug` — standard project hygiene check, run last.

### Critical Files for Implementation
- app/src/main/java/com/daybook/app/ui/workout/WorkoutDetailScreen.kt
- app/src/main/java/com/daybook/app/ui/workout/WorkoutDetailViewModel.kt
- app/src/main/java/com/daybook/app/ui/workout/ExerciseThumbnail.kt
- app/src/main/java/com/daybook/app/data/health/HealthConnectReader.kt
- app/src/main/java/com/daybook/app/data/HealthRepository.kt
- app/src/main/java/com/daybook/app/data/local/Migrations.kt
- app/src/main/java/com/daybook/app/ui/workout/health/HealthDetailSheet.kt
- app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsScreen.kt
