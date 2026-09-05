package com.daybook.app.util

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 0a. Exercises [CrashHandler.appendCrash] — the write/cap
 * core of the crash logger — against a real temp file. This project's unit-test setup has no
 * Robolectric/mocking framework, so a real android.content.Context can't be constructed here;
 * appendCrash is the split-out, Context-free core specifically so this logic stays testable
 * without one (see the KDoc on appendCrash).
 */
class CrashHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun logFile(): File = File(tempFolder.root, "crash_log.txt")

    @Test
    fun `appendCrash creates the file and writes the trace`() {
        val f = logFile()
        assertTrue(!f.exists())

        CrashHandler.appendCrash(f, "java.lang.RuntimeException: boom")

        assertTrue(f.exists())
        val content = f.readText()
        assertTrue(content.contains("java.lang.RuntimeException: boom"))
    }

    @Test
    fun `appendCrash keeps the most recent entry first`() {
        val f = logFile()

        CrashHandler.appendCrash(f, "first crash")
        CrashHandler.appendCrash(f, "second crash")

        val content = f.readText()
        assertTrue(content.contains("first crash"))
        assertTrue(content.contains("second crash"))
        // Most recent entries are kept at the front so a truncation drops the oldest text.
        assertTrue(content.indexOf("second crash") < content.indexOf("first crash"))
    }

    @Test
    fun `appendCrash caps the file at ~256KB keeping the most recent entries`() {
        val f = logFile()
        val big = "x".repeat(200_000)

        CrashHandler.appendCrash(f, big)
        assertTrue(f.length() < 256_000)

        // A second big write pushes the combined size over the cap; the result must still be
        // capped and must contain the newest entry (oldest content is what gets dropped).
        CrashHandler.appendCrash(f, big)

        assertTrue(f.length() <= 256_000)
        val content = f.readText()
        assertTrue(content.startsWith(content.substringBefore("x"))) // newest timestamp header present
    }
}
