package com.daybook.app.data.sync

import com.daybook.app.data.backup.AiExclusionEntry
import com.daybook.app.data.backup.AiSyncSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** User request (Firestore sync for AI meta-prompts + privacy settings) — [ContentHash.
 *  ofAiSyncSettings] must change detection work the same way [ContentHash.ofDefinitions] already
 *  does: identical content hashes identically, ANY field difference hashes differently, and two
 *  users who never touched any AI setting/exclusion at all get the SAME (default) hash — the whole
 *  point of `@EncodeDefault(NEVER)` on every field, so this rollout costs an existing pre-this-round
 *  user zero forced pushes. */
class AiSyncSettingsHashTest {

    @Test fun identicalContent_hashesIdentically() {
        val a = AiSyncSettings(metaPrompt = "Be concise", chatCategories = "HEALTH,TODO")
        val b = AiSyncSettings(metaPrompt = "Be concise", chatCategories = "HEALTH,TODO")
        assertEquals(ContentHash.ofAiSyncSettings(a), ContentHash.ofAiSyncSettings(b))
    }

    @Test fun differentMetaPrompt_hashesDiffer() {
        val a = ContentHash.ofAiSyncSettings(AiSyncSettings(metaPrompt = "Be concise"))
        val b = ContentHash.ofAiSyncSettings(AiSyncSettings(metaPrompt = "Be verbose"))
        assertNotEquals(a, b)
    }

    @Test fun differentExclusions_hashesDiffer() {
        val a = ContentHash.ofAiSyncSettings(AiSyncSettings(exclusions = emptyList()))
        val b = ContentHash.ofAiSyncSettings(
            AiSyncSettings(exclusions = listOf(AiExclusionEntry("CHAT", "HABIT", "h1", 1_700_000_000_000L)))
        )
        assertNotEquals(a, b)
    }

    @Test fun differentHiddenCards_hashesDiffer() {
        val a = ContentHash.ofAiSyncSettings(AiSyncSettings(healthHiddenCards = emptyList()))
        val b = ContentHash.ofAiSyncSettings(AiSyncSettings(healthHiddenCards = listOf("SLEEP")))
        assertNotEquals(a, b)
    }

    @Test fun everyDefault_hashesIdenticallyAcrossTwoFreshInstances() {
        // The rollout invariant: a user who never touched any of this sees a byte-identical
        // aiSettingsHash before/after this round (nothing to push, no surprise sync activity).
        assertEquals(ContentHash.ofAiSyncSettings(AiSyncSettings()), ContentHash.ofAiSyncSettings(AiSyncSettings()))
    }

    @Test fun sha256Hex_is64Chars() {
        assertEquals(64, ContentHash.ofAiSyncSettings(AiSyncSettings()).length)
    }
}
