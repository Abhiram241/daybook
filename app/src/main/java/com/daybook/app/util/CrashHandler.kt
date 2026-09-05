package com.daybook.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

/**
 * Local, offline-first crash logger. Installed as the process-wide default uncaught-exception
 * handler so that any future crash is written to internal storage before the process dies —
 * letting a user retrieve the trace later (Settings > About > "Copy crash log") without needing
 * a connected device at the moment of the crash. See LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 0a.
 *
 * Always re-delivers to the previous handler afterwards so the OS's own crash dialog/ANR
 * behavior is preserved — this only *observes* the crash, it never suppresses it.
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val previous = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching { appendCrash(crashLogFile(context), Log.getStackTraceString(e)) }
        previous?.uncaughtException(t, e)
    }

    companion object {
        private const val MAX_BYTES = 256_000

        fun crashLogFile(context: Context): File = File(context.filesDir, "crash_log.txt")

        /**
         * The testable core of the write path: appends [stackTraceText] to [file], capping the
         * total size at ~256KB and keeping the most recent entries. Split out from
         * [uncaughtException] (rather than constructing a fake [Context] in tests — this module's
         * unit tests have no Robolectric/mocking framework available) so `CrashHandlerTest` can
         * exercise the exact on-disk write/cap behavior against a real temp file.
         */
        internal fun appendCrash(file: File, stackTraceText: String) {
            val text = "${Instant.now()} :: $stackTraceText\n\n"
            val existing = if (file.exists() && file.length() < MAX_BYTES) file.readText() else ""
            file.writeText((text + existing).take(MAX_BYTES))
        }
    }
}
