package com.daybook.app.ui.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User request ("export API keys and import them via JSON — that way it never hits the cloud") —
 * a completely separate, standalone file format and flow. This must NEVER be referenced by
 * `ExportImportRepository`'s main backup/export, `CloudSyncRepository`, or `data/backup/BackupModel.kt`
 * — those stay exactly as documented ("never synced, never in the JSON export") for API keys
 * ([com.daybook.app.data.ai.AiKeyStore]); this file is the one and only export/import path for
 * them, and a future pass must not fold it into any of the above.
 */
private const val API_KEYS_FORMAT_VERSION = 1

@Serializable
private data class ApiKeysFile(
    val formatVersion: Int = API_KEYS_FORMAT_VERSION,
    val keys: Map<String, String> = emptyMap()
)

private val encodeJson = Json { prettyPrint = true; encodeDefaults = true }
private val decodeJson = Json { ignoreUnknownKeys = true }

/** [keys] keyed by [com.daybook.app.data.ai.AiProviderId.name]. */
fun encodeApiKeysJson(keys: Map<String, String>): String =
    encodeJson.encodeToString(ApiKeysFile.serializer(), ApiKeysFile(keys = keys))

/** Never throws — a malformed file, a wrong `formatVersion`, or non-JSON text all just return
 *  null so the caller can show a clear rejection message instead of crashing. */
fun decodeApiKeysJson(text: String): Map<String, String>? = runCatching {
    val parsed = decodeJson.decodeFromString(ApiKeysFile.serializer(), text)
    if (parsed.formatVersion != API_KEYS_FORMAT_VERSION) return null
    parsed.keys
}.getOrNull()
