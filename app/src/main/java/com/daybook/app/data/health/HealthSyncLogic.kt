package com.daybook.app.data.health

import com.daybook.app.data.model.HealthDay
import java.time.LocalDate

/**
 * HEALTH_SYNC_BATTERY_AND_IMPORT_AUDIT_2026-09-20.md — pure decision logic pulled out of
 * `HealthRepository` so it can be unit tested without a Room/Health Connect harness, following
 * this codebase's `*Logic.kt` convention (see `WorkoutLogic.kt`).
 */

/** D-2 fix — true when every nullable metric column is null, i.e. this read found nothing at all
 *  for the day (as opposed to a genuine zero, which is a real value). Used to decide whether a
 *  window actually pulled real data (D-4) — never to decide whether to write the row, since
 *  [mergeHealthDay] already makes an empty read harmless on its own. */
fun isEmptyHealthDay(day: HealthDay): Boolean =
    day.steps == null && day.distanceMeters == null && day.activeCalories == null &&
        day.totalCalories == null && day.restingHeartRate == null && day.avgHeartRate == null &&
        day.minHeartRate == null && day.maxHeartRate == null && day.sleepMinutes == null &&
        day.sleepDeepMinutes == null && day.sleepLightMinutes == null && day.sleepRemMinutes == null &&
        day.sleepAwakeMinutes == null && day.sleepStartMillis == null && day.sleepEndMillis == null &&
        day.spo2Percent == null && day.spo2MinPercent == null && day.spo2MaxPercent == null &&
        day.weightKg == null && day.hydrationMl == null && day.nutritionCalories == null &&
        day.nutritionProteinGrams == null && day.nutritionCarbsGrams == null &&
        day.nutritionFatGrams == null && day.nutritionSourceApp == null

/**
 * D-2 fix — the actual "column merge" `OnConflictStrategy.REPLACE` doesn't give you: every field
 * [incoming] came back null for keeps whatever [existing] already had stored, instead of nulling
 * it out. A field [incoming] DID return a value for always wins (a real reading, including a
 * legitimate zero, is never overridden by stale storage). `localDate`/`updatedAt` always come
 * from [incoming] — this is still "the latest read", just one that can no longer erase what it
 * didn't itself re-observe.
 */
fun mergeHealthDay(existing: HealthDay?, incoming: HealthDay): HealthDay {
    if (existing == null) return incoming
    return incoming.copy(
        steps = incoming.steps ?: existing.steps,
        distanceMeters = incoming.distanceMeters ?: existing.distanceMeters,
        activeCalories = incoming.activeCalories ?: existing.activeCalories,
        totalCalories = incoming.totalCalories ?: existing.totalCalories,
        restingHeartRate = incoming.restingHeartRate ?: existing.restingHeartRate,
        avgHeartRate = incoming.avgHeartRate ?: existing.avgHeartRate,
        minHeartRate = incoming.minHeartRate ?: existing.minHeartRate,
        maxHeartRate = incoming.maxHeartRate ?: existing.maxHeartRate,
        sleepMinutes = incoming.sleepMinutes ?: existing.sleepMinutes,
        sleepDeepMinutes = incoming.sleepDeepMinutes ?: existing.sleepDeepMinutes,
        sleepLightMinutes = incoming.sleepLightMinutes ?: existing.sleepLightMinutes,
        sleepRemMinutes = incoming.sleepRemMinutes ?: existing.sleepRemMinutes,
        sleepAwakeMinutes = incoming.sleepAwakeMinutes ?: existing.sleepAwakeMinutes,
        sleepStartMillis = incoming.sleepStartMillis ?: existing.sleepStartMillis,
        sleepEndMillis = incoming.sleepEndMillis ?: existing.sleepEndMillis,
        spo2Percent = incoming.spo2Percent ?: existing.spo2Percent,
        spo2MinPercent = incoming.spo2MinPercent ?: existing.spo2MinPercent,
        spo2MaxPercent = incoming.spo2MaxPercent ?: existing.spo2MaxPercent,
        weightKg = incoming.weightKg ?: existing.weightKg,
        hydrationMl = incoming.hydrationMl ?: existing.hydrationMl,
        nutritionCalories = incoming.nutritionCalories ?: existing.nutritionCalories,
        nutritionProteinGrams = incoming.nutritionProteinGrams ?: existing.nutritionProteinGrams,
        nutritionCarbsGrams = incoming.nutritionCarbsGrams ?: existing.nutritionCarbsGrams,
        nutritionFatGrams = incoming.nutritionFatGrams ?: existing.nutritionFatGrams,
        nutritionSourceApp = incoming.nutritionSourceApp ?: existing.nutritionSourceApp,
        activitySourceApps = incoming.activitySourceApps ?: existing.activitySourceApps
    )
}

/** B-3 fix — true when [a] and [b] are the same stored day in every column EXCEPT `updatedAt`
 *  (which always differs — every read re-stamps "now" — and is therefore not itself a reason to
 *  write the row). */
fun sameHealthDayPayload(a: HealthDay, b: HealthDay): Boolean = a.copy(updatedAt = 0L) == b.copy(updatedAt = 0L)

/**
 * B-1 fix — the deepest date an AUTOMATIC (non-import) pull is allowed to reach. Used to be
 * whichever was earlier of the flat 30-day default or [backfillThrough] (recorded by a prior
 * "Import my past data"), with no floor of its own — so one import permanently turned every later
 * automatic resync into a 365-day pass. [capDays] bounds how deep an automatic pull can ever go,
 * regardless of how deep an explicit import previously reached; only [HealthRepository
 * .importPastData] itself is allowed past this cap.
 *
 * D-2 fix (the other half) — without `READ_HEALTH_DATA_HISTORY` ([hasHistoryAccess] false), Health
 * Connect silently caps every read at ~30 days on its own: reading further back than that returns
 * an empty (not failed) result for every one of those extra days, for nothing. So an automatic
 * pull never asks for more than the flat default window when history access isn't held, matching
 * what the platform would give it anyway.
 */
fun automaticWindowFloor(
    today: LocalDate,
    backfillThrough: LocalDate?,
    hasHistoryAccess: Boolean,
    defaultFloorDays: Long = 29L,
    capDays: Long = 90L
): LocalDate {
    val defaultFloor = today.minusDays(defaultFloorDays)
    if (!hasHistoryAccess || backfillThrough == null) return defaultFloor
    val deeper = if (backfillThrough.isBefore(defaultFloor)) backfillThrough else defaultFloor
    val cap = today.minusDays(capDays - 1)
    return if (deeper.isBefore(cap)) cap else deeper
}
