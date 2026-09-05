package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 1 (S6): [defsDelta] decides what a targeted definitions upsert writes and deletes.
 * The load-bearing guarantee: a cross-device RENAME (same id) must never land in `toDelete`.
 */
class DefinitionsUpsertTest {

    @Test fun rename_sameId_neverDeleted() {
        val local = setOf("h1", "h2", "h3")
        val remote = setOf("h1", "h2", "h3")   // h2 was renamed remotely — id unchanged
        val (toUpsert, toDelete) = defsDelta(local, remote)
        assertTrue("h2" in toUpsert)
        assertFalse("h2" in toDelete)
        assertTrue(toDelete.isEmpty())
    }

    @Test fun newRemoteId_isUpsertOnly() {
        val (toUpsert, toDelete) = defsDelta(localIds = setOf("h1"), remoteIds = setOf("h1", "h2"))
        assertTrue("h2" in toUpsert)
        assertFalse("h2" in toDelete)
    }

    @Test fun removedRemoteId_isDeleteOnly() {
        val (toUpsert, toDelete) = defsDelta(localIds = setOf("h1", "h2"), remoteIds = setOf("h1"))
        assertEquals(setOf("h2"), toDelete)
        assertFalse("h2" in toUpsert)
    }

    @Test fun toUpsert_isAlwaysTheWholeRemoteSet() {
        val remote = setOf("a", "b", "c")
        assertEquals(remote, defsDelta(setOf("a", "z"), remote).first)
    }
}
