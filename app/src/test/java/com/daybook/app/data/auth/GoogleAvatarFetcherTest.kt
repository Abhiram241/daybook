package com.daybook.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the pure URL-sizing rule (FIREBASE_0.5_PLAN.md §5b / §10). */
class GoogleAvatarFetcherTest {

    private val fetcher = GoogleAvatarFetcher()

    @Test fun rewritesExistingSizeSuffix() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocABC=s512",
            fetcher.sized("https://lh3.googleusercontent.com/a/ACg8ocABC=s96-c")
        )
    }

    @Test fun appendsWhenNoSuffix() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocABC=s512",
            fetcher.sized("https://lh3.googleusercontent.com/a/ACg8ocABC")
        )
    }

    @Test fun honoursCustomPixelSize() {
        assertTrue(fetcher.sized("https://x/y=s96-c", px = 256).endsWith("=s256"))
    }

    @Test fun nonGoogleUrl_staysStructurallyValid() {
        val out = fetcher.sized("https://example.com/pic.png")
        assertTrue(out.startsWith("https://example.com/pic.png"))
        assertTrue(out.endsWith("=s512"))
    }

    @Test fun querylessPathWithEqualsOnlyInEarlierSegment() {
        // '=' only appears before the last '/', so the suffix must be appended, not spliced.
        val out = fetcher.sized("https://host/a=b/photo")
        assertEquals("https://host/a=b/photo=s512", out)
    }
}
