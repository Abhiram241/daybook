package com.daybook.app.data.auth

/**
 * Maps an auth/credential throwable to a user-facing string (FIREBASE_0.5_PLAN.md §3 table).
 *
 * The decision is driven purely off the exception's simple class name + (for
 * `FirebaseAuthException`) its `errorCode` string, so the core logic in [mapAuthError] with
 * explicit arguments is unit-testable on the JVM with no Firebase instance
 * (see `AuthErrorsTest`).
 *
 * Returns `null` for the silent case (the user tapped away from the Google sheet) — callers
 * treat null as "show nothing".
 */
object AuthErrors {

    const val RECENT_LOGIN_REQUIRED = "Please sign in again to confirm this."
    const val OFFLINE = "You're offline. Daybook still works — sync will catch up."
    const val NO_GOOGLE_ACCOUNT = "No Google account on this device."
    const val GOOGLE_UNAVAILABLE = "Google sign-in isn't available on this device."
    const val GENERIC = "Something went wrong. Try again."

    /** Production entry point. Unwraps the Firebase error code, then delegates. */
    fun mapAuthError(t: Throwable?): String? {
        if (t == null) return GENERIC
        val name = t.javaClass.simpleName
        val code = runCatching {
            (t as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
        }.getOrNull()
        return mapAuthError(name, code, t.message)
    }

    /**
     * Pure decision over the exception's simple class name, its Firebase `errorCode` (nullable)
     * and its message. No Android or Firebase-instance types — this is what the unit test drives.
     */
    fun mapAuthError(exceptionSimpleName: String, errorCode: String?, message: String?): String? {
        // Credential Manager cancellation is silent — the user dismissed the sheet.
        if (exceptionSimpleName.contains("GetCredentialCancellationException") ||
            exceptionSimpleName.contains("CancellationException") && exceptionSimpleName.contains("Credential")
        ) return null

        return when (exceptionSimpleName) {
            // v0.5.2: email/password auth is removed. A residual FirebaseAuthUserCollisionException
            // is only possible for a legacy password account already in the project; with
            // emailPassword disabled server-side no new one can be created, so it now falls
            // through to GENERIC rather than carrying its own copy.
            "FirebaseAuthRecentLoginRequiredException" -> RECENT_LOGIN_REQUIRED
            "FirebaseNetworkException" -> OFFLINE
            "NoCredentialException" -> NO_GOOGLE_ACCOUNT
            else -> when {
                // Any other Credential Manager failure → Google isn't usable here.
                exceptionSimpleName.contains("GetCredentialException") ||
                    exceptionSimpleName.contains("CreateCredentialException") ||
                    exceptionSimpleName.contains("GetCredentialProviderConfigurationException") ->
                    GOOGLE_UNAVAILABLE
                errorCode == "ERROR_REQUIRES_RECENT_LOGIN" -> RECENT_LOGIN_REQUIRED
                else -> GENERIC
            }
        }
    }
}
