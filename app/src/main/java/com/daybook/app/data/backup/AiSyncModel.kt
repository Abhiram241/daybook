package com.daybook.app.data.backup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * User request (Firestore sync for AI meta-prompts + privacy settings) — small, whole-value,
 * settings-shaped data synced on the SAME per-user parent doc as [Definitions] (own field, own
 * hash — see `ContentHash.ofAiSyncSettings` / `SyncStateStore.aiSettingsHash`), never
 * month-partitioned, since none of this is history data.
 *
 * Deliberately kept OUT of [Definitions]/[DaybookBackup]: the plain JSON file backup/restore path
 * (`ExportImportRepository.exportBackup`/`importAllData`/`importRange`) is untouched by this — these
 * fields travel ONLY through Firestore (`CloudSyncRepository`), never through a
 * `daybook-backup-*.json` file, preserving every existing "device-local, NOT in BackupModel"
 * comment's original intent for the file-export path specifically.
 *
 * API keys ([com.daybook.app.data.ai.AiKeyStore]) are explicitly excluded from this and every other
 * sync/export path — see `ui/settings/ApiKeysExport.kt` for the separate, cloud-never JSON
 * export/import that covers those instead.
 */
@Serializable
data class AiSyncSettings(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val metaPrompt: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chatMetaPrompt: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val reportCategories: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chatCategories: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chatRangeStart: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val chatRangeEnd: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val exclusions: List<AiExclusionEntry> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val healthHiddenCards: List<String> = emptyList()
)

/** Wire counterpart of `com.daybook.app.data.model.AiExclusion` — same fields, Firestore-friendly
 *  camelCase names. */
@Serializable
data class AiExclusionEntry(
    val scope: String,
    val kind: String,
    val targetId: String,
    val createdAt: Long
)
