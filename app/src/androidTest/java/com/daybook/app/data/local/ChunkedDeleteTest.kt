package com.daybook.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.Occurrence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 6 (S-3): a synthetic 1200-row month exercising the
 * chunked-delete pattern `ExportImportRepository.importMonth` now uses in place of a single
 * `NOT IN (:keep)` query with a `keep` list that can exceed SQLite's 999-bound-variable limit.
 *
 * This asserts the CORRECTNESS of the pattern (candidate fetch with no `keep` bound, Kotlin-side
 * Set exclusion, chunked `deleteByIds` calls) rather than trying to reproduce the historical
 * `SQLiteException` directly — the actual bound-variable ceiling is a property of the SQLite
 * version bundled with the OS the test happens to run on (older on real minSdk-26 devices, likely
 * newer here), so a "does it throw" assertion would be environment-dependent. Deleting exactly the
 * right rows, in chunks, is the invariant that matters and holds regardless of that ceiling.
 */
@RunWith(AndroidJUnit4::class)
class ChunkedDeleteTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun chunkedDeleteRemovesExactlyTheCandidateMinusKeepSet() = runBlocking {
        val dao = db.habitOccurrenceDao()
        val monthPrefix = "2024-06"
        val before = 1_800_000_000_000L // well after every synthetic row's scheduled_for below

        // 1200 rows > SQLite's 999-bound-variable ceiling — the scenario S-3 describes.
        val rows = (0 until 1200).map { i ->
            HabitOccurrence(
                id = "occ-$i",
                habitId = "habit-1",
                scheduledFor = 1_700_000_000_000L + i,
                status = Occurrence.Status.PENDING,
                notificationId = i,
                localDate = "$monthPrefix-15"
            )
        }
        rows.chunked(500).forEach { dao.insertAll(*it.toTypedArray()) }

        // Every 10th row is "kept" (mirrors incomingIds/keepIfShownPending in importMonth).
        val keepSet = rows.filterIndexed { i, _ -> i % 10 == 0 }.mapTo(HashSet()) { it.id }

        val candidates = dao.pendingIdsByLocalMonthBefore(monthPrefix, before)
        assertEquals(1200, candidates.size)

        val toDelete = candidates - keepSet
        assertEquals(1080, toDelete.size) // 1200 - 120 kept

        toDelete.chunked(900).forEach { chunk ->
            if (chunk.isNotEmpty()) dao.deleteByIds(chunk)
        }

        val remaining = dao.getAllOccurrences().first()
        assertEquals(120, remaining.size)
        assertTrue(remaining.all { it.id in keepSet })
    }
}
