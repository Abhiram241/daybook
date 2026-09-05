package com.daybook.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins every row of the FIREBASE_0.5_PLAN.md §3 error table. Drives the pure
 * `mapAuthError(simpleName, errorCode, message)` overload — no Firebase instance.
 */
class AuthErrorsTest {

    private fun map(name: String, code: String? = null, msg: String? = null) =
        AuthErrors.mapAuthError(name, code, msg)

    @Test fun userCollision_nowFallsThroughToGeneric() =
        assertEquals(AuthErrors.GENERIC, map("FirebaseAuthUserCollisionException"))

    @Test fun recentLoginRequired() =
        assertEquals(AuthErrors.RECENT_LOGIN_REQUIRED, map("FirebaseAuthRecentLoginRequiredException"))

    @Test fun offline() =
        assertEquals(AuthErrors.OFFLINE, map("FirebaseNetworkException"))

    @Test fun cancellation_isSilent() {
        assertNull(map("GetCredentialCancellationException"))
    }

    @Test fun noGoogleAccount() =
        assertEquals(AuthErrors.NO_GOOGLE_ACCOUNT, map("NoCredentialException"))

    @Test fun otherCredentialFailure_isGoogleUnavailable() =
        assertEquals(AuthErrors.GOOGLE_UNAVAILABLE, map("GetCredentialProviderConfigurationException"))

    @Test fun unknownThrowable_isGeneric() =
        assertEquals(AuthErrors.GENERIC, map("IllegalStateException"))

    @Test fun throwableOverload_nullIsGeneric() =
        assertEquals(AuthErrors.GENERIC, AuthErrors.mapAuthError(null as Throwable?))

    @Test fun throwableOverload_runtimeException() =
        assertEquals(AuthErrors.GENERIC, AuthErrors.mapAuthError(RuntimeException("boom")))
}
