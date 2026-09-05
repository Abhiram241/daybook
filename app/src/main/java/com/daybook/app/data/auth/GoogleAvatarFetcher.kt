package com.daybook.app.data.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a Google account photo as raw bytes (FIREBASE_0.5_PLAN.md §5b).
 *
 * The project has no HTTP client and adding one for a single GET is unwarranted, so this uses
 * [HttpURLConnection] directly. A profile photo is cosmetic — [fetch] returns null on **any**
 * failure and never throws, so it can never fail a sign-in.
 */
@Singleton
class GoogleAvatarFetcher @Inject constructor() {

    /** `lh3.googleusercontent.com/...=s96-c` → `...=s512`; no `=` suffix → append `=s512`. */
    fun sized(url: String, px: Int = 512): String =
        if ("=" in url.substringAfterLast('/')) url.substringBeforeLast('=') + "=s$px"
        else "$url=s$px"

    suspend fun fetch(photoUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            (URL(sized(photoUrl)).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                try {
                    inputStream.use { it.readBytes() }
                } finally {
                    disconnect()
                }
            }
        }.onFailure { Log.w("GoogleAvatarFetcher", "fetch failed", it) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }
}
