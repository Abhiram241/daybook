package com.daybook.app.util

import com.daybook.app.data.backup.DaybookBackup
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serialisation for the v2 backup format ([DaybookBackup]).
 *
 * The v1 `ExportBundle` (a raw dump of every Room row) is gone — see BackupModel.kt. Files in that
 * format no longer parse into this shape, and ExportImportRepository rejects them with a plain
 * "made by an older version" message rather than half-importing them.
 */
@Singleton
class JsonUtils @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        // A legacy/unknown enum name coerces to the property's default instead of throwing and
        // aborting the whole import (REV-12).
        coerceInputValues = true
        // A field missing from an older/hand-edited file falls back to the declared default.
        explicitNulls = false
    }

    fun encode(backup: DaybookBackup): String =
        json.encodeToString(DaybookBackup.serializer(), backup)

    fun decode(text: String): DaybookBackup =
        json.decodeFromString(DaybookBackup.serializer(), text)

    /** ISO-8601 in UTC, e.g. "2026-08-29T14:03:11Z". */
    fun nowIso(): String = toIso(System.currentTimeMillis())

    fun toIso(epochMillis: Long): String = isoFormat().format(Date(epochMillis))

    /** Parses an ISO-8601 UTC stamp produced by [toIso]; null when it isn't one. */
    fun fromIso(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching { isoFormat().parse(text)?.time }.getOrNull()
    }

    // SimpleDateFormat is not thread-safe and this is a @Singleton shared by the export/import
    // paths, so each call gets its own instance rather than a shared field.
    private fun isoFormat() =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
}
