package com.daybook.app.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun newFileName(rangeStart: String? = null, rangeEnd: String? = null): String {
        // v0.5.3 Phase 6 (D2): a date-range export stamps the span into the filename so the file is
        // self-describing in the Downloads list, e.g. daybook-backup-2026-03-01_2026-03-31.json.
        if (rangeStart != null && rangeEnd != null) {
            return "daybook-backup-${rangeStart}_${rangeEnd}.json"
        }
        val ts = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "daybook-backup-$ts.json"
    }

    /**
     * Writes [json] into the device's public Downloads folder so it is visible in
     * the Files app / any file manager. Returns a human-readable location string.
     */
    fun saveExport(json: String, rangeStart: String? = null, rangeEnd: String? = null): String {
        val filename = newFileName(rangeStart, rangeEnd)
        val bytes = json.toByteArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads")
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "Could not open Downloads file for writing" }
                out.write(bytes)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Downloads/$filename"
        }

        // API 26–28: no MediaStore Downloads collection. Write to the app's shared
        // external files dir (no permission needed) and let the user use "Share backup".
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** Writes [json] to app cache and returns a shareable content:// URI. */
    fun writeShareFile(json: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, newFileName())
        file.writeText(json)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun readText(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 13 (C-14, Medium): the picked file's size in bytes via
     * `OpenableColumns.SIZE`, or null when it can't be determined (some content providers omit the
     * column) — a caller that can't get a size should fail open rather than refuse a legitimate
     * import it just can't measure. Queried, not read: [readText] is never called for this.
     */
    fun fileSizeBytes(uri: Uri): Long? = runCatching {
        var size: Long? = null
        val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !it.isNull(idx)) size = it.getLong(idx)
            }
        }
        size
    }.getOrNull()
}
