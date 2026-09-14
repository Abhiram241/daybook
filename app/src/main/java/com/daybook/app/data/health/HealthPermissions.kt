package com.daybook.app.data.health

import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

/**
 * B1 (§5.1, §6.1.8, §6.2) — the 12-type MVP read-permission set, requested all at once (§6.1.8 —
 * not a 6-then-6 split). Isolation rule (§7.3): this is one of the handful of files that may
 * import `androidx.health.connect.*` — nothing in `ui/` or `HealthRepository` does.
 */
object HealthPermissions {

    /** The 12 MVP record types this app ever reads. Order matches §6.1.8's list. */
    val MVP_READ_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class)
    )

    /**
     * §5.1 R4 — `connect-client:1.1.0-alpha08` lacks the `PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND`
     * / `PERMISSION_READ_HEALTH_DATA_HISTORY` constants (added alpha09/alpha10). The permission
     * strings themselves are OS-level and stable, so they're declared as literals here — the exact
     * same strings the manifest declares — and passed to the same request contract. If the older
     * client's contract rejects an unknown string, the caller (`HealthRepository`) catches it and
     * falls back to foreground-only + a 30-day window (§6.3/§6.2's "Permission sheet threw" row).
     */
    const val PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    const val PERMISSION_READ_HEALTH_DATA_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    /** Pure — no Health Connect call. The set of [required] not present in [granted]. */
    fun missingPermissions(granted: Set<String>, required: Set<String> = MVP_READ_PERMISSIONS): Set<String> =
        required - granted

    /**
     * The OS consent-sheet launcher contract, for `rememberLauncherForActivityResult` at the
     * settings-screen composable (§7.4's "Connect" button / the `Health` tab's empty-state
     * button). A plain `ActivityResultContract`, not one of Compose's built-in ones — this is the
     * supported way to launch it from Compose without any extra Activity-side registration.
     */
    fun requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** The full request set for the initial "Connect" tap — the 12 MVP types in one call
     *  (§6.1.8), no optional extras (those are requested separately, §5.1/§6.3). */
    fun initialRequestSet(): Set<String> = MVP_READ_PERMISSIONS

    /** The optional extras, requested separately per §6.3 — background reads + history depth. */
    fun optionalExtras(): Set<String> =
        setOf(PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND, PERMISSION_READ_HEALTH_DATA_HISTORY)

    /** Human-readable label for a permission string, for the "Which data is shared" sheet (§7.4). */
    val LABELS: Map<String, String> = mapOf(
        HealthPermission.getReadPermission(StepsRecord::class) to "Steps",
        HealthPermission.getReadPermission(DistanceRecord::class) to "Distance",
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) to "Active calories",
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) to "Total calories",
        HealthPermission.getReadPermission(ExerciseSessionRecord::class) to "Workouts",
        HealthPermission.getReadPermission(SleepSessionRecord::class) to "Sleep",
        HealthPermission.getReadPermission(HeartRateRecord::class) to "Heart rate",
        HealthPermission.getReadPermission(RestingHeartRateRecord::class) to "Resting heart rate",
        HealthPermission.getReadPermission(OxygenSaturationRecord::class) to "Oxygen saturation (SpO₂)",
        HealthPermission.getReadPermission(WeightRecord::class) to "Weight",
        HealthPermission.getReadPermission(HydrationRecord::class) to "Hydration",
        HealthPermission.getReadPermission(NutritionRecord::class) to "Nutrition"
    )
}
