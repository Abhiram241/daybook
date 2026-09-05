package com.daybook.app.data.lock

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over `androidx.biometric` (v0.5.1 §K, decision 6).
 *
 * **`BIOMETRIC_STRONG` only.** `DEVICE_CREDENTIAL` is never in the allowed set and
 * `setDeviceCredentialAllowed(true)` is never called — the app lock exists to be a second factor
 * *over* the device unlock, so accepting the device PIN would make it decorative.
 *
 * Because `DEVICE_CREDENTIAL` is absent, `PromptInfo.Builder` **requires** a negative button; it is
 * "Use PIN" and it routes to [onFallbackToPin], as do `ERROR_NEGATIVE_BUTTON` and
 * `ERROR_USER_CANCELED`. No error path ever resolves to "unlocked".
 */
@Singleton
class BiometricGate @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system biometric sheet. Must be called from a [FragmentActivity] — this is the
     * requirement that made `MainActivity` extend `FragmentActivity` rather than `ComponentActivity`.
     *
     * Failure-inert: if the prompt cannot be shown at all, [onFallbackToPin] runs, so the caller
     * always ends up somewhere the user can act.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String = "Unlock Daybook",
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onFallbackToPin: () -> Unit
    ) {
        if (!isAvailable()) { onFallbackToPin(); return }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.i(TAG, "biometric error $errorCode: $errString")
                    // Every error — cancel, negative button, lockout, hardware fault — lands on
                    // the PIN pad. Never on "unlocked".
                    onFallbackToPin()
                }

                // onAuthenticationFailed (a non-matching finger) is deliberately not overridden:
                // the system sheet stays up and lets the user try again, which is the right feel.
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use PIN")
            .setConfirmationRequired(false)
            .build()
        runCatching { prompt.authenticate(info) }
            .onFailure { Log.w(TAG, "biometric prompt failed to show", it); onFallbackToPin() }
    }

    private companion object { const val TAG = "BiometricGate" }
}
