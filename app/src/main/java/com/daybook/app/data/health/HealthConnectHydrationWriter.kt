package com.daybook.app.data.health

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Volume
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Hydration habit — the app's ONLY Health Connect write. One `HydrationRecord` per local day, keyed
 * by a stable `clientRecordId` ("daybook-hydration-yyyy-MM-dd"): Health Connect replaces an
 * existing record with the same id when the new `clientRecordVersion` is higher, so editing a day's
 * amount updates that day's single record instead of adding another one.
 */
class HealthConnectHydrationWriter(private val context: Context) {

    enum class Result { WRITTEN, NO_PERMISSION, UNAVAILABLE, FAILED }

    private fun client() = HealthConnectAvailability.client(context)

    suspend fun hasWritePermission(): Boolean = runCatching {
        client()?.permissionController?.getGrantedPermissions()?.contains(WRITE_PERMISSION) == true
    }.getOrDefault(false)

    /** Writes (or replaces) [date]'s record; [amountMl] <= 0 deletes it instead. */
    suspend fun upsertDay(date: LocalDate, amountMl: Int, version: Long, zoneId: ZoneId = ZoneId.systemDefault()): Result {
        val client = client() ?: return Result.UNAVAILABLE
        if (!hasWritePermission()) return Result.NO_PERMISSION
        return runCatching {
            if (amountMl <= 0) {
                client.deleteRecords(HydrationRecord::class, emptyList(), listOf(clientIdFor(date)))
            } else {
                val start = date.atStartOfDay(zoneId)
                // A record may not end in the future: today's ends "now", a past day's at its last minute.
                val now = ZonedDateTime.now(zoneId)
                val dayEnd = date.plusDays(1).atStartOfDay(zoneId).minusMinutes(1)
                val end = if (now.isBefore(dayEnd)) maxOf(now, start.plusMinutes(1)) else dayEnd
                client.insertRecords(
                    listOf(
                        HydrationRecord(
                            startTime = start.toInstant(),
                            startZoneOffset = start.offset,
                            endTime = end.toInstant(),
                            endZoneOffset = end.offset,
                            volume = Volume.milliliters(amountMl.toDouble()),
                            metadata = Metadata(clientRecordId = clientIdFor(date), clientRecordVersion = version)
                        )
                    )
                )
            }
            Result.WRITTEN
        }.getOrElse {
            android.util.Log.w("HydrationWriter", "Health Connect write failed for $date", it)
            Result.FAILED
        }
    }

    companion object {
        val WRITE_PERMISSION: String = HealthPermission.getWritePermission(HydrationRecord::class)
        fun clientIdFor(date: LocalDate) = "daybook-hydration-$date"
    }
}
