package com.daybook.app.data.hydration

import com.daybook.app.data.health.HealthConnectHydrationWriter
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.HydrationDay
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Hydration habit — Room is the source of truth (synced via `hydration_days`); every save is then
 * mirrored to Health Connect on a best-effort basis. A failed/denied Health Connect write never
 * blocks or undoes the Daybook save — the caller just reports it.
 */
@Singleton
class HydrationRepository @Inject constructor(
    private val database: AppDatabase,
    private val writer: HealthConnectHydrationWriter
) {
    fun observeDay(date: LocalDate): Flow<HydrationDay?> = database.hydrationDao().observe(date.toString())

    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<HydrationDay>> =
        database.hydrationDao().observeRange(start.toString(), end.toString())

    suspend fun getDay(date: LocalDate): HydrationDay? = database.hydrationDao().get(date.toString())

    /** Sets [date]'s single entry to [amountMl] (0 clears it). Returns the Health Connect outcome. */
    suspend fun setDay(date: LocalDate, amountMl: Int): HealthConnectHydrationWriter.Result {
        val now = System.currentTimeMillis()
        if (amountMl <= 0) {
            database.hydrationDao().delete(date.toString())
        } else {
            database.hydrationDao().upsert(HydrationDay(date.toString(), amountMl, now))
        }
        return writer.upsertDay(date, amountMl, version = now)
    }

    suspend fun hasHealthConnectWrite(): Boolean = writer.hasWritePermission()
}

/** Pure helpers shared by the Today card and the settings screen. */
object HydrationUnits {
    const val ML = "ML"
    const val L = "L"

    /** Converts a typed amount in [unit] to whole millilitres; null when not a positive number. */
    fun toMl(input: String, unit: String): Int? {
        val v = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (v <= 0.0 || v.isNaN() || v.isInfinite()) return null
        val ml = if (unit == L) v * 1000.0 else v
        return Math.round(ml).toInt().takeIf { it in 1..20_000 }
    }

    /** "1.25 L" / "1250 ml". */
    fun format(ml: Int, unit: String): String =
        if (unit == L) "${trimNumber(ml / 1000.0)} L" else "$ml ml"

    /** The number alone, for pre-filling a text field in [unit]. */
    fun inputValue(ml: Int, unit: String): String =
        if (unit == L) trimNumber(ml / 1000.0) else ml.toString()

    private fun trimNumber(v: Double): String =
        java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
