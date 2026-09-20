package com.daybook.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
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
 * Item 12 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — owns up to 3 photos per workout session on
 * disk, in `filesDir/workout_photos/`. A near-copy of [ProfilePhotoStore]'s recipe (same
 * `MAX_EDGE`/`JPEG_QUALITY`, same decode chain — [ImageDecoder] first, [BitmapFactory] + manual
 * EXIF fallback, raw-bytes last resort), with two deliberate differences:
 *  - a subdirectory, not the `filesDir` root — a root full of `workout_<millis>.jpg` alongside
 *    `profile_*.jpg` would be unmanageable, and the subdir is what `file_paths.xml` exposes to
 *    the stock camera via [FileProvider];
 *  - **no "delete the others" step** — `ProfilePhotoStore` deletes every other file after each
 *    save because there is exactly one profile photo; here there are up to three per session and
 *    many sessions, and `WorkoutRepository`/`ExportImportRepository` own deletion (see
 *    `WorkoutSessionPhoto`'s KDoc for the full list of cleanup call sites).
 *
 * The capture flow: [newCaptureFile] creates an empty target file and hands back a
 * `content://…fileprovider` Uri for `ActivityResultContracts.TakePicture()` to write into; on a
 * `true` result the caller passes the file back through [persistCapturedFile], which runs it
 * through the same downscale/EXIF pipeline (the camera writes a full-resolution image, often
 * several MB — this app wants ~150-300 KB) and returns the final stored path. On `false`,
 * [discardCaptureFile] deletes the now-empty file.
 */
@Singleton
class WorkoutPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "WorkoutPhotoStore"
        const val SUBDIR = "workout_photos"
        const val PREFIX = "workout_"
        const val SUFFIX = ".jpg"
        const val MAX_EDGE = 1024
        const val JPEG_QUALITY = 90
    }

    private fun dir(): File = File(context.filesDir, SUBDIR).apply { mkdirs() }

    /** A fresh, empty, unique target for the stock camera to write into, plus the
     *  `content://…fileprovider` Uri to launch `TakePicture()` with. The caller (the ViewModel)
     *  must hold [File.getAbsolutePath] in its `SavedStateHandle`, not a `remember` — the camera
     *  is a separate activity and can kill this process while it's in the foreground. */
    fun newCaptureFile(): File = File(dir(), "$PREFIX${System.currentTimeMillis()}$SUFFIX")

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** The camera returned `false` (cancelled, or failed to write) — clean up the empty file
     *  `newCaptureFile` created so it doesn't linger as an orphan with no Room row. */
    fun discardCaptureFile(file: File) {
        if (file.exists() && !file.delete()) Log.w(TAG, "could not delete cancelled capture ${file.absolutePath}")
    }

    /** Item 12 — restoring a manual "Export my data" file's `workoutPhotos`. [bytes] are already
     *  a fully-processed JPEG (the same bytes [persistCapturedFile] would have produced on the
     *  exporting device), so this just writes them under a fresh unique name — no re-decode, no
     *  re-downscale. A fresh name (not the exported blob's original filename/path, which belongs
     *  to a different device) avoids any collision with whatever is already in this subdir. */
    suspend fun saveImportedPhoto(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = File(dir(), "$PREFIX${System.currentTimeMillis()}_${(0..9999).random()}$SUFFIX")
        target.writeBytes(bytes)
        target.absolutePath
    }

    /** The camera wrote a full-resolution image into [file] — re-decode it through the same
     *  downscale/EXIF pipeline [ProfilePhotoStore] uses and overwrite [file] in place with the
     *  smaller result. Returns the same absolute path on success; null if the file the camera
     *  claimed to write is unreadable or empty (caller should treat this like a cancelled
     *  capture). */
    suspend fun persistCapturedFile(file: File): String? = withContext(Dispatchers.IO) {
        val bytes = try {
            file.readBytes()
        } catch (t: Throwable) {
            Log.w(TAG, "persistCapturedFile: reading ${file.absolutePath} failed", t)
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            Log.w(TAG, "persistCapturedFile: no bytes at ${file.absolutePath}")
            discardCaptureFile(file)
            return@withContext null
        }
        val bitmap = decodeBounded(bytes)
        if (bitmap != null) {
            val scaled = try {
                scaleToMaxEdge(bitmap)
            } catch (t: Throwable) {
                Log.w(TAG, "persistCapturedFile: scale failed", t)
                bitmap
            }
            try {
                file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }
        // If decode failed, [file] already holds the camera's original full-resolution JPEG —
        // leave it as-is (matches ProfilePhotoStore's raw-bytes-last-resort behaviour) rather
        // than discarding a photo the user just took.
        Log.i(TAG, "persisted workout photo -> ${file.absolutePath} (${file.length()} bytes)")
        file.absolutePath
    }

    /** Deletes the given files if they exist. No-op for any that are already gone. Called with
     *  paths collected BEFORE a Room transaction and run AFTER it commits — disk I/O has no
     *  business holding a database write lock. */
    suspend fun deleteFiles(paths: Collection<String>) = withContext(Dispatchers.IO) {
        for (p in paths) {
            val f = File(p)
            if (f.exists() && !f.delete()) Log.w(TAG, "could not delete ${f.absolutePath}")
        }
    }

    /** Startup orphan sweep (cheap insurance) — every file in [SUBDIR] with no matching path in
     *  [knownPaths]. Guards against any cleanup path above being missed, or a crash between a
     *  row delete and its file delete. */
    suspend fun sweepOrphans(knownPaths: Set<String>) = withContext(Dispatchers.IO) {
        val files = dir().listFiles() ?: return@withContext
        for (f in files) {
            if (f.isFile && f.absolutePath !in knownPaths) {
                if (!f.delete()) Log.w(TAG, "sweepOrphans: could not delete ${f.absolutePath}")
                else Log.i(TAG, "sweepOrphans: removed orphaned ${f.absolutePath}")
            }
        }
    }

    // ------------------------------------------------------------------ decode chain (ProfilePhotoStore's recipe, verbatim)

    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.isMutableRequired = false
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

    private fun applyExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (t: Throwable) {
            Log.w(TAG, "applyExif: reading orientation failed", t)
            return bitmap
        }
        val m = android.graphics.Matrix()
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
