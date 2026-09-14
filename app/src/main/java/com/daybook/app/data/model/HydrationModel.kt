package com.daybook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Hydration habit (DB v31, MIGRATION_30_31) — ONE entry per local calendar day: the total water the
 * user logged for that day, always stored in millilitres (the ml/L choice is display-only).
 * Editing a day replaces its row. Synced like any other day data (`DayEntry.hydrationMl`) and
 * mirrored to Health Connect as one `HydrationRecord` per day.
 */
@Entity(tableName = "hydration_days")
data class HydrationDay(
    @PrimaryKey @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "amount_ml") val amountMl: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
