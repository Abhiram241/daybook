package com.daybook.app.data.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * gzip codec for the Firestore payload blob (FIREBASE_0.5_PLAN.md §4 / D1).
 *
 * The v2 day array is extremely repetitive and compresses ~8–12×, which is what keeps the
 * 1 MiB Firestore doc cap decades away. Pure JVM — unit-tested (`PayloadCodecTest`).
 *
 * v0.5.3 Phase 7 (audit A7 — blob size at scale, informational): a 10× user's full file export
 * is ≈2.9 MB of JSON; a single per-month cloud doc is ≈120 KB raw / ~15 KB gzipped. Both are
 * comfortably inside every relevant limit (Firestore's 1 MiB/doc, the app's own SOFT/HARD
 * warn thresholds in [CloudSyncRepository]) at every scale considered in SCALABILITY_SYNC_AUDIT.
 * No action — recorded so the headroom is not re-derived.
 */
object PayloadCodec {

    fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    fun gzipString(s: String): ByteArray = gzip(s.toByteArray(Charsets.UTF_8))

    fun gunzipToString(bytes: ByteArray): String = String(gunzip(bytes), Charsets.UTF_8)
}
