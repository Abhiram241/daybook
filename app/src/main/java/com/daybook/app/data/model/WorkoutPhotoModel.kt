package com.daybook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Item 12 (BEAST_MODE_ROUND_2026-09-16_PLAN.md, §S1 sign-off) — up to 3 optional photos taken
 * with the stock system camera at the end of a workout session, shown in History below the
 * workout image. `filePath` points at a JPEG in `filesDir/workout_photos/` (see
 * `data.WorkoutPhotoStore`), never a content:// URI — the file is copied and downscaled into app
 * storage immediately after capture, exactly like `ProfilePhotoStore`'s recipe.
 *
 * Deliberately **NOT** `@Serializable` and deliberately **NOT** added to
 * `CloudSyncRepository.DATA_TABLES` / `BackupModel` / `ContentHash` (S2 sign-off: "recommended
 * change is NONE") — a month document has a 1 MB hard limit and a single photographed workout can
 * approach 300 KB base64-encoded; syncing photos risks silently stopping a whole month of real
 * workout data from syncing. Photos are device-local only, same treatment as
 * `app_settings.profile_photo_path`. They ARE included in the manual "Export my data" JSON file
 * (a user-initiated file with no size limit) — see `ExportImportRepository.exportWorkoutPhotos`.
 *
 * No foreign key, matching the schema-wide policy (`WorkoutDao.kt`'s own note: no table in this
 * schema has one — deletion is handled explicitly, one transaction per delete site).
 */
@Entity(tableName = "workout_session_photos", indices = [Index("session_id")])
data class WorkoutSessionPhoto(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Firebase Storage backup round (2026-09-20) — the `users/{uid}/workout_photos/{sessionId}/
     *  {filename}` Storage path once uploaded, null until the upload succeeds. Used to (a) skip
     *  re-uploading a photo that's already backed up, (b) rehydrate the local file if it's ever
     *  missing (reinstall / second device) — see WorkoutPhotoCloudSync's KDoc. */
    @ColumnInfo(name = "cloud_path") val cloudPath: String? = null,
    /** Non-null once a Storage upload has actually succeeded; null means "not yet uploaded". */
    @ColumnInfo(name = "cloud_uploaded_at") val cloudUploadedAt: Long? = null
)
