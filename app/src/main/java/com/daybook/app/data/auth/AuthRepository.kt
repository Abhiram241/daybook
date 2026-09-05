package com.daybook.app.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.daybook.app.R
import com.daybook.app.data.auth.AuthErrors.mapAuthError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the Firebase session, bridged from an [FirebaseAuth.AuthStateListener]. */
sealed interface AuthState {
    /** Before the first auth callback. The listener fires synchronously on registration, so this
     *  is effectively transient and never blocks the launch path. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(
        val uid: String,
        val email: String?,
        val displayName: String?,
        val photoUrl: String?
    ) : AuthState
}

/** Result of an auth operation. [Error.message] == null means "stay silent" (user dismissed). */
sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Error(val message: String?) : AuthOutcome
    /** `user.delete()` / re-auth-gated op needs a fresh login first. */
    data object NeedsReauth : AuthOutcome
}

/**
 * Firebase Authentication wrapper (FIREBASE_0.5_PLAN.md §3; v0.5.2 = Google-only).
 *
 * Offline-first (R9): the [AuthState.SignedIn] snapshot is restored from local storage by the
 * `AuthStateListener` with no network round trip, so a cold launch in airplane mode still opens
 * instantly signed in. Every method here is failure-inert — nothing throws to the caller.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        auth.addAuthStateListener { fa -> _state.value = snapshot(fa) }
        // Token revocation (account deleted/disabled elsewhere): a forced refresh that fails with
        // FirebaseAuthInvalidUserException means the session is dead — sign out locally. Any other
        // failure (offline) is ignored so this never disturbs an offline session.
        val idTokenListener = FirebaseAuth.IdTokenListener { fa ->
            val user = fa.currentUser
            if (user != null) {
                scope.launch {
                    val err = runCatching { user.getIdToken(true).awaitCompat() }.exceptionOrNull()
                    if (err != null && err.javaClass.simpleName == "FirebaseAuthInvalidUserException") {
                        Log.w(TAG, "session revoked — local sign-out")
                        runCatching { auth.signOut() }
                    }
                }
            }
        }
        auth.addIdTokenListener(idTokenListener)
    }

    private fun snapshot(fa: FirebaseAuth): AuthState {
        val u = fa.currentUser ?: return AuthState.SignedOut
        return AuthState.SignedIn(
            uid = u.uid,
            email = u.email,
            displayName = u.displayName,
            photoUrl = u.photoUrl?.toString()
        )
    }

    /** Force a re-emit of the current session (e.g. after a fresh Google sign-in updates the
     *  display name / photo on the FirebaseUser). */
    fun refreshState() { _state.value = snapshot(auth) }

    // ---------------------------------------------------------------- Google (Credential Manager)

    /**
     * Full Google sign-in. Needs an **Activity** context for the bottom sheet — pass one from
     * the call site, never `@ApplicationContext`.
     */
    suspend fun signInWithGoogle(activityContext: Context): AuthOutcome {
        val idToken = when (val r = googleIdToken(activityContext)) {
            is TokenResult.Ok -> r.idToken
            is TokenResult.Failed -> return AuthOutcome.Error(r.message)
        }
        return try {
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(cred).awaitCompat()
            refreshState()
            AuthOutcome.Success
        } catch (t: Throwable) {
            AuthOutcome.Error(mapAuthError(t))
        }
    }

    // ---------------------------------------------------------------- re-auth / delete / sign-out

    suspend fun reauthenticateWithGoogle(activityContext: Context): AuthOutcome {
        val idToken = when (val r = googleIdToken(activityContext)) {
            is TokenResult.Ok -> r.idToken
            is TokenResult.Failed -> return AuthOutcome.Error(r.message)
        }
        return try {
            auth.currentUser?.reauthenticate(GoogleAuthProvider.getCredential(idToken, null))?.awaitCompat()
            AuthOutcome.Success
        } catch (t: Throwable) {
            AuthOutcome.Error(mapAuthError(t))
        }
    }

    /**
     * Deletes the Firebase user. Caller is responsible for deleting the Firestore doc **first**
     * (while the token is still valid) and for any opt-in local wipe — see D-del. Returns
     * [AuthOutcome.NeedsReauth] when Firebase demands a fresh login (the normal path for anyone
     * signed in more than a few minutes).
     */
    suspend fun deleteAccount(): AuthOutcome = try {
        auth.currentUser?.delete()?.awaitCompat()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        AuthOutcome.Success
    } catch (t: Throwable) {
        if (t.javaClass.simpleName == "FirebaseAuthRecentLoginRequiredException") AuthOutcome.NeedsReauth
        else AuthOutcome.Error(mapAuthError(t))
    }

    /** `signOut()` + `clearCredentialState` — without the latter the next Google sign-in silently
     *  re-selects the same account with no chooser. Never touches Room or the profile photo. */
    suspend fun signOut() {
        runCatching { auth.signOut() }
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    // ---------------------------------------------------------------- internals

    private sealed interface TokenResult {
        data class Ok(val idToken: String) : TokenResult
        data class Failed(val message: String?) : TokenResult
    }

    private suspend fun googleIdToken(activityContext: Context): TokenResult = withContext(Dispatchers.Main) {
        val serverClientId = context.getString(R.string.default_web_client_id)
        // 1) authorized-accounts filter → one-tap for returning users.
        val filtered = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(true)
                    .setAutoSelectEnabled(true)
                    .build()
            ).build()
        try {
            return@withContext TokenResult.Ok(
                extractIdToken(credentialManager.getCredential(activityContext, filtered))
            )
        } catch (e: NoCredentialException) {
            Log.i(TAG, "no authorized Google account — falling back to the full chooser")
        } catch (e: GetCredentialException) {
            return@withContext TokenResult.Failed(mapAuthError(e))
        } catch (t: Throwable) {
            return@withContext TokenResult.Failed(mapAuthError(t))
        }
        // 2) explicit "Sign in with Google" — every account on the device.
        val full = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        try {
            TokenResult.Ok(extractIdToken(credentialManager.getCredential(activityContext, full)))
        } catch (t: Throwable) {
            TokenResult.Failed(mapAuthError(t))
        }
    }

    private fun extractIdToken(response: androidx.credentials.GetCredentialResponse): String {
        val cred = response.credential
        if (cred is CustomCredential &&
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(cred.data).idToken
        }
        error("Unexpected credential type: ${cred.type}")
    }

    private companion object { const val TAG = "AuthRepository" }
}
