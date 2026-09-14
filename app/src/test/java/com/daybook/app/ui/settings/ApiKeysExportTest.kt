package com.daybook.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** User request ("export API keys and import them via JSON") — round-trip + rejection coverage
 *  for the pure encode/decode functions, isolated from AiKeyStore/EncryptedSharedPreferences. */
class ApiKeysExportTest {

    @Test fun roundTrip_preservesEveryKey() {
        val keys = mapOf("OPENAI" to "sk-abc123", "ANTHROPIC" to "sk-ant-xyz789")
        val json = encodeApiKeysJson(keys)
        assertEquals(keys, decodeApiKeysJson(json))
    }

    @Test fun emptyKeys_roundTrips() {
        assertEquals(emptyMap<String, String>(), decodeApiKeysJson(encodeApiKeysJson(emptyMap())))
    }

    @Test fun garbageText_returnsNull() {
        assertNull(decodeApiKeysJson("not json at all"))
    }

    @Test fun unrelatedButValidJson_decodesToEmptyKeys() {
        // `formatVersion`/`keys` are both optional-with-default and `ignoreUnknownKeys=true`, so
        // JSON that simply doesn't mention either decodes to "no keys" rather than failing — only
        // a genuinely malformed file (bad syntax, or a `keys` value of the wrong TYPE) is rejected.
        assertEquals(emptyMap<String, String>(), decodeApiKeysJson("""{"totally":"unrelated"}"""))
    }

    @Test fun keysFieldWrongType_returnsNull() {
        assertNull(decodeApiKeysJson("""{"formatVersion":1,"keys":["not","a","map"]}"""))
    }

    @Test fun wrongFormatVersion_returnsNull() {
        assertNull(decodeApiKeysJson("""{"formatVersion":99,"keys":{"OPENAI":"sk-abc"}}"""))
    }

    @Test fun blankString_returnsNull() {
        assertNull(decodeApiKeysJson(""))
    }
}
