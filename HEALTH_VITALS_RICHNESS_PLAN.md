# HEALTH_VITALS_RICHNESS_PLAN.md

**Status: PLAN ONLY — not implemented.** No `.kt`/`.xml`/`.gradle.kts`/`.json` file has been
touched to produce this document. Companion to `HEALTH_AND_WORKOUT_PLAN.md` (the "main plan") —
kept in its own file because it's a focused, self-contained follow-up to Round B (Health Connect),
not a rewrite of it. Cross-references the main plan by section number throughout; nothing here
contradicts it.

## 0. Why this exists

User observation, mid-review of the shipped Health tab: some Health Connect metrics — heart rate,
oxygen saturation, weight — are naturally sampled **more than once a day** (Mi Fitness shows a
timestamped reading list for each). Round B's shipped design (main plan §7.1) only carries a daily
average for oxygen and, per §7.1's own `HealthDay.weightKg` comment, "the day's last reading" for
weight. **Verified against the actual shipped code before writing this plan** (not assumed):

- `HealthConnectReader.dayAggregate` (`data/health/HealthConnectReader.kt:83-141`) reads
  `HeartRateRecord` via `AggregateRequest(BPM_AVG, BPM_MIN, BPM_MAX)` — heart rate **already**
  captures a range, not just one number. Nothing to add here; heart rate is the reference shape
  the other two should match.
- Oxygen saturation has no Health Connect `AggregateMetric` at all (it's a single-instant reading
  type, not interval-shaped), so the reader already falls back to `readRecords` + averaging in
  Kotlin (`HealthConnectReader.kt:106-109`) — the raw per-day records are already in memory when
  this runs. **Adding min/max costs nothing extra: no new read, just two more `Kotlin` reductions
  over a list Daybook already has.**
- Weight is read via `AggregateRequest(WeightRecord.WEIGHT_AVG)` (`HealthConnectReader.kt:96,133`)
  — **this is an average of the day's readings, not "the day's last reading" as the `HealthModel.kt`
  comment on `HealthDay.weightKg` claims.** That comment and the code have already drifted apart;
  independent of the richer-data feature below, this is worth calling out as a pre-existing
  doc/code mismatch to fix in the same pass (V1 below folds the fix in rather than opening a
  separate one-line ticket for it).

## 1. Decision

- **SpO2 → add a min/max range, exactly mirroring heart rate's shape.** Same mechanism (already
  read raw, just reduce further), same card idiom (`Average` + `Range`, like the Heart rate tile),
  same `HealthDetailSheet` idiom (main plan §11.2 once that ships — Lowest/Highest rows).
- **Weight → become genuinely multi-reading, not an average passed off as "last reading."** Weight
  readings are discrete, deliberate events (a person steps on a scale, maybe twice a day — morning
  and post-workout) — the right representation is a **list of the day's readings**, mirroring how
  `HealthSession` is already its own table rather than columns squeezed onto `HealthDay` (main plan
  §7.1's own precedent). `HealthDay.weightKg` becomes the **actual last reading of the day** (fixing
  the code to match its existing comment, using the same real data the new table stores), kept as
  a denormalised column so the fast common-path read (the grid card, §11.1) still needs no join.
- **Neither change touches the record types Round B already scoped** — no new Health Connect
  permission, no new record class, no change to §6.1's MVP/LATER/NEVER table. This is entirely
  about squeezing more honest detail out of data Daybook already has permission to read.

## 2. Schema

One migration, `MIGRATION_24_25` (Round B shipped as `MIGRATION_23_24` / DB v24 — confirmed live in
`AppDatabase.kt`, `version = 24`, `app/schemas/.../24.json` present — so this is the next slot, not
a renumbering of anything shipped). `AppDatabase` v24 → **v25**, one new schema JSON (`25.json`),
one new `MigrationTest.migrate24To25` case — same one-round-one-migration discipline as the main
plan's C8.

```kotlin
// data/model/HealthModel.kt — two additive columns on the existing HealthDay entity.
@ColumnInfo(name = "spo2_min_percent") val spo2MinPercent: Float? = null,
@ColumnInfo(name = "spo2_max_percent") val spo2MaxPercent: Float? = null,
```

```kotlin
// data/model/HealthModel.kt — new entity, same file, same reasoning as HealthSession living
// alongside HealthDay: a list-shaped fact doesn't belong squeezed into HealthDay's columns.
@Serializable
@Entity(
    tableName = "health_weight_readings",
    indices = [Index("local_date")]
)
data class HealthWeightReading(
    // Health Connect Record.metadata.id — stable, so a re-read upserts rather than duplicating,
    // exactly like HealthSession.id (§7.1's existing rule for anything Health-Connect-sourced).
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "at_millis") val atMillis: Long,
    // ALWAYS kg — same storage rule as HealthDay.weightKg / WorkoutSet.weightKg.
    @ColumnInfo(name = "weight_kg") val weightKg: Float,
    @ColumnInfo(name = "source_app") val sourceApp: String? = null
)
```

Migration SQL, additive only (main plan C2/Ri3 — nullable columns, no `NOT NULL DEFAULT`, no table
rebuild):

```kotlin
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE health_days ADD COLUMN spo2_min_percent REAL")
        db.execSQL("ALTER TABLE health_days ADD COLUMN spo2_max_percent REAL")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_weight_readings` (" +
                "`id` TEXT NOT NULL, `local_date` TEXT NOT NULL, `at_millis` INTEGER NOT NULL, " +
                "`weight_kg` REAL NOT NULL, `source_app` TEXT, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_weight_readings_local_date` ON `health_weight_readings` (`local_date`)")
    }
}
```

`weight_kg` is `NOT NULL` on this table specifically (unlike `HealthDay.weightKg`, which stays
nullable) — each row's whole reason to exist is a reading that happened, so there is no "reading
present but value absent" state here; the null-means-no-data rule (Ri3) still applies at the
`HealthDay`/day-summary level where absence is meaningful.

## 3. Reader changes (`data/health/HealthConnectReader.kt`)

- **SpO2**: `dayAggregate`'s existing `spo2` list (already fetched, `HealthConnectReader.kt:106-108`)
  gets two more reductions alongside the existing average:
  ```kotlin
  val spo2Min = spo2.minOfOrNull { it.percentage.value }
  val spo2Max = spo2.maxOfOrNull { it.percentage.value }
  ```
  added to `DayAggregate` and threaded into `HealthRepository`'s upsert the same way
  `avgHeartRate`/`minHeartRate`/`maxHeartRate` already are.
- **Weight**: replace the `AggregateRequest(WeightRecord.WEIGHT_AVG)` metric with a raw
  `readRecords(ReadRecordsRequest(WeightRecord::class, filter))` call — same pattern already used
  for `OxygenSaturationRecord` and `NutritionRecord` two lines below it in the same function, so
  this isn't a new idiom, just applying the existing one to a third record type. From the raw list:
  `HealthDay.weightKg` = the reading with the latest `time`; each raw record becomes one
  `HealthWeightReading` row (`id` = `record.metadata.id`, `atMillis` = `record.time.toEpochMilli()`,
  `sourceApp` = `record.metadata.dataOrigin.packageName`).
- No new permission, no new `AggregateRequest` metric family — `WeightRecord.WEIGHT_AVG` is simply
  dropped from the mixed-metric `AggregateRequest` set (`HealthConnectReader.kt:88-98`) since the
  raw read now supplies everything the aggregate used to, plus more.

## 4. Repository / DAO

- `HealthRepository`'s day-pull path: after upserting `HealthDay`, upsert every
  `HealthWeightReading` for that day in the same transaction (mirrors how it already upserts
  `HealthSession` rows alongside the day). The changes-token delta-read path (main plan §6.3) and
  the 30-day/365-day full-resync path both funnel through the same upsert call, so no separate
  wiring is needed for either cadence.
- `HealthDao` gains:
  - `observeWeightReadingsForDay(localDate: String): Flow<List<HealthWeightReading>>`
  - `observeWeightReadingsInRange(start: String, end: String): Flow<List<HealthWeightReading>>`
  - a range-delete (`deleteWeightReadingsBetween`) for month eviction (§5 below) — same shape as
    the existing `HealthDao` range-delete for `health_days`/`health_sessions`.

## 5. Sync, export, eviction

- `CloudSyncRepository.DATA_TABLES` gains `"health_weight_readings"`; `DataTablesSyncTest` updated
  (main plan's own tripwire test, §0's warning about this being a manual, easy-to-skip step still
  applies here).
- `BackupModel.kt`'s `HealthDayLog` (main plan §7.5) gains one field:
  ```kotlin
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val weightReadings: List<HealthWeightReadingLog> = emptyList()   // empty == omitted, same
                                                                    // explicitNulls=false hash-
                                                                    // neutrality rule as §4.2
  ```
  with `HealthWeightReadingLog(atMillis: Long, weightKg: Float, sourceApp: String?)`. Goes into
  **both** split exports the same way the rest of `HealthDayLog` already does — it's part of the
  Beast Mode JSON (main plan §7.5.1), not the main Daybook one, since all Health data lives there.
- `ExportImportRepository`'s health-related call sites (the same ones main plan §7.5 already lists
  for `health_days`/`health_sessions`) gain the weight-readings table alongside them — full-replace
  import, month-merge, month eviction. **Eviction is the trap named once already in the main plan
  (§7.5's own warning about `evictMonth`) — repeating it here on purpose**: an evicted month must
  delete that month's `health_weight_readings` rows too, or they silently re-push forever.

## 6. UI

- **Oxygen card** (grid tile, main plan §11.1's fixed-height tile): second row becomes `Range`
  (`min–max`), exactly mirroring the Heart rate tile's own `Average` / `Range` pair — visually
  consistent, not a new layout idiom.
- **Weight card**: stays a single headline value — **the day's actual last reading**, now correct
  rather than an average mislabelled as one. No layout change to the grid tile itself.
- **Detail screen / sheet** (`HealthDetailSheet.kt` today; `HealthMetricDetailScreen.kt` once main
  plan §11.2 ships):
  - SpO2 detail gains `Lowest` / `Highest` rows alongside the existing `Average` — the min/max is
    now real data, not absent as it is today.
  - Weight detail: if the day (or, in Range mode, any day in the window) has **more than one**
    reading, render an expandable **"N readings today"** list — time + value, newest first — the
    same "list, not chart" idiom the main plan already uses for per-exercise history
    (main plan §3.1) and for band workout sessions. A day with exactly one reading shows just the
    single value, no list affordance (nothing to expand into).
  - `Range` mode's weight figure stays what `aggregateHealthDays` already computes today (the mean
    of each day's `weightKg` — now a mean of real last-readings rather than a mean of daily
    averages, a quiet but real accuracy improvement with no code path change needed in
    `HealthAggregation.kt`).
  - The per-metric hide rule (main plan R32/§7.4, `visibleHealthCards`) is untouched — a day with
    zero weight readings still hides the Weight card entirely, exactly as today.

## 7. Phase list

| Phase | Work | Gate |
|---|---|---|
| V1 | `HealthModel.kt` additive columns + `HealthWeightReading` entity, `MIGRATION_24_25`, `AppDatabase` v25, DI, `25.json`, `MigrationTest.migrate24To25`. Also fixes the stale "day's last reading" comment vs. the actual `WEIGHT_AVG` code it currently describes, as part of the same touch. | tests green |
| V2 | `HealthConnectReader`: SpO2 min/max reductions, Weight raw-record read + per-reading mapping; unit tests for both reducers | tests green |
| V3 | `HealthDao` new queries, `HealthRepository` upsert of `HealthWeightReading` rows alongside the day pull (all three cadences: on-resume, daily `WindowRefreshWorker`, manual refresh/import-past) | manual pass with the real band + a scale that writes to Mi Fitness (or a Health Connect debug insert) |
| V4 | Sync: `DATA_TABLES`, `HealthDayLog.weightReadings`, the `ExportImportRepository` call sites, eviction | tests green — before any UI |
| V5 | UI: Oxygen card's Range row, Weight detail's "N readings today" list, both in whichever detail surface is live at the time (today's `HealthDetailSheet` if V5 lands before main plan §11.2, or `HealthMetricDetailScreen` if after) | manual pass with a day that has 0/1/2+ weight readings and confirm the list only appears at 2+ |
