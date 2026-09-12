package com.daybook.app.data.workout

import java.io.File

/** Locates the two real Hevy export fixtures, mirroring `DataTablesSyncTest`'s multi-candidate
 *  path resolution so these tests work regardless of Gradle's working directory. */
internal object HevyFixtures {
    private fun locate(name: String): File {
        val rel = "src/test/resources/hevy/$name"
        val candidates = listOf(
            File(rel), File("app/$rel"),
            File(System.getProperty("user.dir"), rel),
            File(System.getProperty("user.dir"), "app/$rel")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate $name (tried: ${candidates.map { it.absolutePath }})")
    }

    /** The 34-row hand-checked sample. */
    fun sampleText(): String = locate("workout_data.csv").readText()

    /** The user's real 4,734-row / 299-session export. */
    fun fullText(): String = locate("workout_datasum.csv").readText()
}
