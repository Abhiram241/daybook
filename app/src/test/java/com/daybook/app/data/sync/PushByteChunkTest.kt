package com.daybook.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.3 Phase 1 (S8): [chunkByBytes] must split a push run by BOTH the Firestore batch-write
 * limit and an ~8 MiB byte ceiling, whichever it hits first.
 */
class PushByteChunkTest {

    private val eightMiB = 8L * 1024 * 1024
    private val fourMiB = 4 * 1024 * 1024

    @Test fun fitsInOneCommit_oneChunk() {
        val months = listOf("2026-01" to 10_000, "2026-02" to 20_000, "2026-03" to 5_000)
        val chunks = chunkByBytes(months, eightMiB, 450)
        assertEquals(1, chunks.size)
        assertEquals(listOf("2026-01", "2026-02", "2026-03"), chunks[0])
    }

    @Test fun threeFourMiBMonths_splitAtByteBoundaryNotWriteBoundary() {
        val months = listOf("2026-01" to fourMiB, "2026-02" to fourMiB, "2026-03" to fourMiB)
        val chunks = chunkByBytes(months, eightMiB, 450)
        assertEquals(2, chunks.size)
        assertEquals(listOf("2026-01", "2026-02"), chunks[0])
        assertEquals(listOf("2026-03"), chunks[1])
    }

    @Test fun manyTinyMonths_splitAtWriteBoundary() {
        val months = (1..460).map { "m$it" to 100 }
        val chunks = chunkByBytes(months, eightMiB, 450)
        assertEquals(2, chunks.size)
        assertEquals(450, chunks[0].size)
        assertEquals(10, chunks[1].size)
    }

    @Test fun singleOversizedEntry_stillGetsItsOwnChunk() {
        val months = listOf("big" to (fourMiB * 3))
        val chunks = chunkByBytes(months, eightMiB, 450)
        assertEquals(1, chunks.size)
        assertEquals(listOf("big"), chunks[0])
    }

    @Test fun empty_noChunks() {
        assertEquals(emptyList<List<String>>(), chunkByBytes(emptyList(), eightMiB, 450))
    }
}
