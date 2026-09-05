package com.daybook.app.util.update

import android.app.Activity
import android.util.Log
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException

/**
 * Accent-updates round (Phase 6) — in-app update checks via Firebase App Distribution, the
 * free/Spark-tier mechanism for this sideloaded testing-phase app (Play Core's In-App Updates
 * API doesn't apply — this app has no Play listing).
 *
 * [updateIfNewReleaseAvailable] combines checking for a new release AND handling the whole
 * update flow (sign-in-if-needed, an "Update available" dialog, download progress, install
 * prompt) with App Distribution's own built-in UI — no custom prompt to build here.
 *
 * Only meaningful for a build that was itself installed via an App Distribution invite link; a
 * plain sideloaded APK handed over some other way was never registered as tester-authenticated,
 * so this call is inert (fails silently) for it — see ACCENT_UPDATES_PLAN.md Phase 6.
 */
object InAppUpdateChecker {
    private const val TAG = "InAppUpdateChecker"

    /**
     * [onAuthenticationCanceled] fires ONLY when the tester explicitly dismisses/cancels the
     * SDK's own "Enable testing features" sign-in prompt (`Status.AUTHENTICATION_CANCELED`) — the
     * one outcome that means "stop asking me", as opposed to every other failure mode (no
     * network, no App Distribution access yet, host activity interrupted, etc.) which should NOT
     * disable future checks since they're transient/unrelated to tester intent.
     */
    fun checkForUpdate(activity: Activity, onAuthenticationCanceled: () -> Unit = {}) {
        FirebaseAppDistribution.getInstance()
            .updateIfNewReleaseAvailable()
            .addOnFailureListener { e ->
                Log.w(TAG, "update check failed (expected for non-App-Distribution installs)", e)
                if (e is FirebaseAppDistributionException &&
                    e.errorCode == FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED
                ) {
                    onAuthenticationCanceled()
                }
            }
    }
}
