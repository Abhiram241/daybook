package com.daybook.app.data.sync

import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.DaybookBackup
import com.daybook.app.data.backup.Definitions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * The echo-guard content hash (FIREBASE_0.5_PLAN.md §4 / D-note).
 *
 * MUST cover `definitions` + `days` **only** — never `meta`. `BackupMeta.exportedAt` is
 * `nowIso()` and changes on every `exportAllData()` call, so hashing the raw export string
 * would make every export look like a change and the sync loop would never settle.
 *
 * Pure JVM — unit-tested (`ContentHashTest`): two backups differing only in `meta.exportedAt`
 * hash identically.
 */
@OptIn(ExperimentalSerializationApi::class)
object ContentHash {

    @Serializable
    private data class Canonical(val definitions: Definitions, val days: List<DayEntry>)

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    fun of(backup: DaybookBackup): String = ofParts(backup.definitions, backup.days)

    fun ofParts(definitions: Definitions, days: List<DayEntry>): String {
        val canonical = json.encodeToString(Canonical.serializer(), Canonical(definitions, days))
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    /** Parse a full backup JSON string and hash its definitions+days. Returns null if unparseable. */
    fun ofJson(fullBackupJson: String): String? = runCatching {
        val decoded = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
            .decodeFromString(DaybookBackup.serializer(), fullBackupJson)
        of(decoded)
    }.getOrNull()

    /**
     * v0.5.1 §N: per-month day-log hash. Hashed through the same canonical `json` instance as
     * [ofParts], so a month's hash is stable across processes and devices.
     */
    fun ofDays(days: List<DayEntry>): String {
        val canonical = json.encodeToString(ListSerializer(DayEntry.serializer()), days)
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    /**
     * v0.5.1 §N: parent-doc definitions hash. This is what decides whether a push rewrites the
     * parent doc at all — a pure history write (answering a reminder) must not.
     */
    fun ofDefinitions(defs: Definitions): String {
        val canonical = json.encodeToString(Definitions.serializer(), defs)
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
