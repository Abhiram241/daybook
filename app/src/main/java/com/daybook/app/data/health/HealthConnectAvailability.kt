package com.daybook.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient

/**
 * B1 (§6.2) — `getSdkStatus` wrapper + a pure state model. Never branches on `Build.VERSION`
 * directly; `HealthConnectClient.getSdkStatus()` already abstracts "needs the Play Store app"
 * (Android 9-13) vs "built into the framework" (Android 14+).
 */
enum class HealthConnectSdkState {
    /** Health Connect cannot run on this device/OS at all (includes Android 8.0-8.1). */
    UNAVAILABLE,
    /** The provider app exists but needs updating before it can be used. */
    UPDATE_REQUIRED,
    /** The provider is installed and usable. */
    AVAILABLE
}

object HealthConnectAvailability {
    private const val TAG = "HealthConnectAvail"

    /** §5.2 / manifest — required so `getSdkStatus()` can see the provider on Android 13 and
     *  below, via the `<queries><package .../></queries>` manifest declaration. */
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    /**
     * Never throws. `getSdkStatus` is a cheap local check (no IPC to the provider), but is
     * wrapped anyway per C6 — every Health Connect call is failure-inert.
     */
    fun state(context: Context): HealthConnectSdkState = runCatching {
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectSdkState.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectSdkState.UPDATE_REQUIRED
            else -> HealthConnectSdkState.UNAVAILABLE
        }
    }.getOrElse {
        Log.e(TAG, "getSdkStatus failed", it)
        HealthConnectSdkState.UNAVAILABLE
    }

    /** The OS Health Connect app's own per-app revoke screen — Daybook cannot itself revoke a
     *  grant, so "Disconnect" (§7.4) opens this instead. Isolation rule (§7.3): this keeps the
     *  `HealthConnectClient` import out of `ui/workout/WorkoutSettingsScreen.kt`. */
    fun manageDataIntent(context: Context) = HealthConnectClient.getHealthConnectManageDataIntent(context)

    /** The Health Connect app's own settings screen, for the "Which data is shared" sheet's
     *  "Change what's shared" row (§6.2/§7.4). */
    fun settingsIntent() = android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    fun client(context: Context): HealthConnectClient? = runCatching {
        if (state(context) != HealthConnectSdkState.AVAILABLE) null
        else HealthConnectClient.getOrCreate(context)
    }.getOrElse {
        Log.e(TAG, "HealthConnectClient.getOrCreate failed", it)
        null
    }
}
