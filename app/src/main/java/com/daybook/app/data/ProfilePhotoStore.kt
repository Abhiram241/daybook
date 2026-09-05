package com.daybook.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single profile photo on disk (L5).
 *
 * The system photo picker hands back a `content://` Uri whose read grant does not survive a
 * process restart, so the bytes are copied into the app's own `filesDir` and only that path is
 * persisted in [com.daybook.app.data.model.AppSettings.profilePhotoPath].
 *
 * Section 11 hardening: the Uri is read **once** into a `ByteArray` (some providers fail the
 * second `openInputStream`), then decoded from those bytes via [ImageDecoder] (HEIC/WebP-safe,
 * applies EXIF orientation itself) with a [BitmapFactory] + manual-EXIF fallback, and finally a
 * last-resort raw-bytes copy so Coil can still decode formats `BitmapFactory` chokes on. It only
 * throws when the byte read itself fails.
 *
 * UI (picker launcher / composables) lives in the Settings screens — this class is UI-free.
 * Public API — [save] / [clear] / [currentPath] — is unchanged.
 */
@Singleton
class ProfilePhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "ProfilePhotoStore"
        const val LEGACY_FILE_NAME = "profile.jpg"
        const val PREFIX = "profile_"
        const val SUFFIX = ".jpg"
        const val MAX_EDGE = 1024
        const val JPEG_QUALITY = 90
    }

    private fun storedFiles(): List<File> =
        (context.filesDir.listFiles { f ->
            f.isFile && (f.name == LEGACY_FILE_NAME || (f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX)))
        } ?: emptyArray()).sortedByDescending { it.lastModified() }

    /**
     * Absolute path of the current profile photo, or null when nothing has been saved.
     * The newest `profile_<millis>.jpg` wins; the legacy `profile.jpg` is honoured if it's all
     * that's there.
     */
    fun currentPath(): String? = storedFiles().firstOrNull()?.absolutePath

    /**
     * Copies [uri] into app storage under a **unique** filename (`profile_<millis>.jpg`),
     * downscaled so the longest edge is at most [MAX_EDGE], then removes every older copy.
     * A fresh path each time is deliberate: [com.daybook.app.data.model.AppSettings.profilePhotoPath]
     * is observed as a StateFlow and Coil caches by key, so re-using one path would make a
     * re-picked photo silently not refresh.
     * Returns the absolute path of the written file. Throws only if the Uri's bytes can't be read.
     */
    suspend fun save(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            Log.w(TAG, "save: reading $uri failed", t)
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            Log.w(TAG, "save: no bytes read for $uri")
            throw java.io.IOException("Cannot read $uri")
        }
        Log.i(TAG, "save: read ${bytes.size} bytes from $uri")
        persist(bytes, source = uri.toString())
    }

    /**
     * New entry point for already-in-memory bytes — the Google avatar download (§5b).
     * Overload resolution between [Uri] and [ByteArray] is unambiguous; callers need no change.
     */
    suspend fun save(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) throw java.io.IOException("Empty image")
        persist(bytes, source = "bytes[${bytes.size}]")
    }

    /** Everything the old `save(uri)` did after the byte read — decode chain, MAX_EDGE downscale,
     *  unique `profile_<millis>.jpg` name, older-copy sweep — verbatim. [source] is a log subject. */
    private fun persist(bytes: ByteArray, source: String): String {
        val bitmap: Bitmap? = decodeBounded(bytes)
        val target = File(context.filesDir, "$PREFIX${System.currentTimeMillis()}$SUFFIX")

        if (bitmap != null) {
            val scaled = try {
                scaleToMaxEdge(bitmap)
            } catch (t: Throwable) {
                Log.w(TAG, "save: scale failed for $source", t)
                bitmap
            }
            try {
                target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        } else {
            // Last resort: every decode path failed — persist the original bytes and let Coil
            // decode them at render time (it handles formats BitmapFactory can't). The `.jpg`
            // name is cosmetic; Coil sniffs the content, and Avatar only checks the file exists.
            Log.w(TAG, "save: all decode paths failed for $source, writing ${bytes.size} raw bytes")
            target.outputStream().use { it.write(bytes) }
        }

        // Drop every other copy (older uniques + the legacy fixed name).
        storedFiles().filter { it.absolutePath != target.absolutePath }.forEach { old ->
            if (!old.delete()) Log.w(TAG, "could not delete stale ${old.absolutePath}")
        }
        Log.i(TAG, "saved profile photo -> ${target.absolutePath} (${target.length()} bytes)")
        return target.absolutePath
    }

    /** Deletes every stored profile photo. No-op when there isn't one. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        storedFiles().forEach { f ->
            if (f.exists() && !f.delete()) Log.w(TAG, "could not delete ${f.absolutePath}")
        }
        Unit
    }

    /**
     * Decode [bytes] to a bitmap whose long edge is roughly [MAX_EDGE] or less, trying every
     * path before giving up: [ImageDecoder] first (HEIC/WebP, auto EXIF), then [BitmapFactory]
     * from the same bytes with manual EXIF. Returns null only when nothing could decode it.
     */
    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.isMutableRequired = false
                    // Software allocation so the result can be re-encoded via Bitmap.compress().
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val longest = maxOf(w, h)
                    if (longest > MAX_EDGE && longest > 0) {
                        val r = MAX_EDGE.toFloat() / longest
                        decoder.setTargetSize(
                            (w * r).toInt().coerceAtLeast(1),
                            (h * r).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "decodeBounded: ImageDecoder failed, trying BitmapFactory", t)
            }
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (raw == null) {
                Log.w(TAG, "decodeBounded: BitmapFactory returned null")
                null
            } else {
                applyExif(raw, bytes)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "decodeBounded: BitmapFactory failed", t)
            null
        }
    }

    /** Rotate/flip [bitmap] to match the EXIF orientation stored in [bytes]. */
    private fun applyExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (t: Throwable) {
            Log.w(TAG, "applyExif: reading orientation failed", t)
            return bitmap
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (t: Throwable) {
            Log.w(TAG, "applyExif: rotate failed", t)
            bitmap
        }
    }

    /** Largest power-of-two subsample that still leaves both edges at or above [MAX_EDGE]. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= MAX_EDGE && height / (sample * 2) >= MAX_EDGE) sample *= 2
        return sample
    }

    private fun scaleToMaxEdge(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= MAX_EDGE) return src
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
