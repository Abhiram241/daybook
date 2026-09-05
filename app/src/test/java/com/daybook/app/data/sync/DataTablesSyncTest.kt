package com.daybook.app.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against the exact bug that caused the "journal_questions" IllegalArgumentException crash
 * after login: [CloudSyncRepository.DATA_TABLES] is a hand-maintained array with no compile-time
 * tie to the real Room schema, so a migration that drops/renames a table can leave a stale entry
 * behind, which then throws at runtime the instant `attachTracker()` registers a Room
 * `InvalidationTracker.Observer` for a table that no longer exists.
 *
 * `AppDatabase`'s `@Database`/`@Entity` annotations use `RetentionPolicy.CLASS` (Room needs them
 * only at kapt time), so they are NOT visible via runtime reflection here — confirmed via
 * `javap` on room-common 2.6.1. Instead this reads the committed, `exportSchema=true` schema JSON
 * under `app/schemas/com.daybook.app.data.local.AppDatabase` (one file per DB version, already
 * this project's source of truth for `MigrationTest`), always picking the HIGHEST-numbered file so
 * this test keeps working without edits the next time a migration bumps the DB version — and
 * asserts every `DATA_TABLES` entry is still a real table, so the next table drop/rename fails
 * this test instead of shipping a runtime crash.
 */
class DataTablesSyncTest {

    @Serializable
    private data class SchemaEntity(val tableName: String)

    @Serializable
    private data class SchemaDatabase(val entities: List<SchemaEntity>)

    @Serializable
    private data class SchemaRoot(val database: SchemaDatabase)

    private val json = Json { ignoreUnknownKeys = true }

    private fun latestSchemaFile(): File {
        val relDir = "schemas/com.daybook.app.data.local.AppDatabase"
        val candidates = listOf(
            File(relDir),
            File("app/$relDir"),
            File(System.getProperty("user.dir"), relDir),
            File(System.getProperty("user.dir"), "app/$relDir"),
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error(
                "Could not locate the committed Room schema directory (tried: " +
                    "${candidates.map { it.absolutePath }}). Is exportSchema still enabled?"
            )
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.maxByOrNull { it.name.removeSuffix(".json").toIntOrNull() ?: -1 }
            ?: error("No schema JSON files found in ${dir.absolutePath}")
    }

    private fun realTableNames(): Set<String> {
        val root = json.decodeFromString(SchemaRoot.serializer(), latestSchemaFile().readText())
        return root.database.entities.map { it.tableName }.toSet()
    }

    @Test fun everyDataTablesEntry_existsInCurrentSchema() {
        val real = realTableNames()
        val stale = CloudSyncRepository.DATA_TABLES.filterNot { it in real }
        assertTrue(
            "DATA_TABLES has stale table name(s) not present in the current Room schema: " +
                "$stale (real tables: $real). A migration likely dropped/renamed a table without " +
                "updating CloudSyncRepository.DATA_TABLES to match — see the journal_questions " +
                "incident documented at that array's declaration site.",
            stale.isEmpty()
        )
    }
}
