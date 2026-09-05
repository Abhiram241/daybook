package com.daybook.app.ui.account

import com.daybook.app.util.safeLaunch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.ProfilePhotoStore
import com.daybook.app.data.auth.AuthErrors
import com.daybook.app.data.auth.AuthOutcome
import com.daybook.app.data.auth.AuthRepository
import com.daybook.app.data.auth.AuthState
import com.daybook.app.data.auth.GoogleAvatarFetcher
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.data.sync.ConflictInfo
import com.daybook.app.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class AccountForm(
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudSync: CloudSyncRepository,
    private val settingsRepository: AppSettingsRepository,
    private val profilePhotoStore: ProfilePhotoStore,
    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-5): `database` was only ever used by the now-
    // deleted `wipeLocalData()` — removed along with it (see `deleteAccount`, which now calls
    // `cloudSync.wipeAllLocalData()` instead).
    private val googleAvatarFetcher: GoogleAvatarFetcher
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    val authState: StateFlow<AuthState> =
        authRepository.state.stateIn(viewModelScope, started, AuthState.Loading)

    val syncStatus: StateFlow<SyncStatus> =
        cloudSync.status.stateIn(viewModelScope, started, SyncStatus.Disabled)

    val conflict: StateFlow<ConflictInfo?> =
        cloudSync.conflict.stateIn(viewModelScope, started, null)

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form.asStateFlow()

    private val userName: StateFlow<String> = settingsRepository.observeSettings()
        .map { it.userName }
        .stateIn(viewModelScope, started, "")

    /** "Not signed in" / "abhiram@… · Synced 2 min ago" — for the Settings hub row. */
    val accountSubtitle: StateFlow<String> =
        combine(authRepository.state, cloudSync.status) { st, sync ->
            when (st) {
                is AuthState.SignedIn -> {
                    val who = st.email ?: "Signed in"
                    val tail = when (sync) {
                        is SyncStatus.Idle -> " · " + relativeSync(sync.lastSyncedAtMillis)
                        SyncStatus.Syncing -> " · Syncing…"
                        SyncStatus.Offline -> " · Offline"
                        is SyncStatus.Error -> " · Sync error"
                        SyncStatus.Disabled -> ""
                        SyncStatus.Paused -> " · Sync paused"   // v0.5.3 Phase 3 (finding 19)
                    }
                    who + tail
                }
                else -> "Not signed in"
            }
        }.stateIn(viewModelScope, started, "Not signed in")

    /** displayName → userName one-tap suggestion (5d). Null unless userName is blank. */
    val suggestedDisplayName: StateFlow<String?> =
        combine(authRepository.state, userName) { st, name ->
            (st as? AuthState.SignedIn)?.displayName?.takeIf { it.isNotBlank() && name.isBlank() }
        }.stateIn(viewModelScope, started, null)

    /** "Use Google photo" row is offered whenever a signed-in user has a photo URL (5c). */
    val canUseGooglePhoto: StateFlow<Boolean> =
        authRepository.state.map { st ->
            (st as? AuthState.SignedIn)?.photoUrl != null
        }.stateIn(viewModelScope, started, false)

    // ---------------------------------------------------------------- form editing

    fun clearMessage() = _form.update { it.copy(message = null) }

    // ---------------------------------------------------------------- Google

    fun continueWithGoogle(activityContext: Context) = launchBusy {
        when (val o = authRepository.signInWithGoogle(activityContext)) {
            AuthOutcome.Success -> { applyGooglePhotoIfEligible(); _form.update { it.copy(busy = false, message = null) } }
            is AuthOutcome.Error -> _form.update { it.copy(busy = false, message = o.message) }
            AuthOutcome.NeedsReauth -> _form.update { it.copy(busy = false) }
        }
    }

    /** §5c — after a Google sign-in only. Guards: null photoUrl / custom photo exists / download fails. */
    private suspend fun applyGooglePhotoIfEligible() {
        val st = authRepository.state.value as? AuthState.SignedIn ?: return
        val url = st.photoUrl ?: return
        if (profilePhotoStore.currentPath() != null) return          // never clobber a manual pick
        val bytes = googleAvatarFetcher.fetch(url) ?: return
        val path = runCatching { profilePhotoStore.save(bytes) }.getOrNull() ?: return
        settingsRepository.setProfilePhotoPath(path)
    }

    /** Explicit "Use Google photo" row — same code with the currentPath() guard dropped. */
    fun useGooglePhoto() = launchBusy {
        val st = authRepository.state.value as? AuthState.SignedIn
        val url = st?.photoUrl
        if (url == null) { _form.update { it.copy(busy = false, message = "No Google photo available.") }; return@launchBusy }
        val bytes = googleAvatarFetcher.fetch(url)
        if (bytes == null) { _form.update { it.copy(busy = false, message = "Couldn't fetch the Google photo.") }; return@launchBusy }
        val path = runCatching { profilePhotoStore.save(bytes) }.getOrNull()
        if (path != null) settingsRepository.setProfilePhotoPath(path)
        _form.update { it.copy(busy = false, message = if (path != null) "Profile photo updated." else "Couldn't save that photo.") }
    }

    fun useDisplayNameAsName() = safeLaunch {
        val dn = (authState.value as? AuthState.SignedIn)?.displayName?.trim().orEmpty()
        if (dn.isNotEmpty()) settingsRepository.setUserName(dn)
    }

    // ---------------------------------------------------------------- sync

    fun syncNow() = safeLaunch { runCatching { cloudSync.syncNow() } }
    fun resolveConflict(restoreFromCloud: Boolean) = safeLaunch { cloudSync.resolveConflict(restoreFromCloud) }
    fun dismissConflict() = cloudSync.dismissConflict()

    // ---------------------------------------------------------------- sign-out / delete

    /**
     * v0.5.3 Phase 1 (D3): the local-data wipe is NOT here — it runs in
     * [CloudSyncRepository.wipeLocalForSignOut], reached via `AuthState.SignedOut` in
     * `onAuthState`, so token-revocation sign-out is covered by the same one path.
     */
    fun signOut() = safeLaunch { authRepository.signOut() }

    /** D-del. Deletes the Firestore doc first, then the user; optional separate local wipe. */
    fun deleteAccount(alsoEraseLocal: Boolean, activityContext: Context) {
        launchBusy {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 5 (S-2, High): the old code discarded this
            // result and deleted the Auth account regardless — a failed remote delete orphaned the
            // Firestore doc under a UID nobody could ever sign back in as. Now: no remote delete,
            // no Auth delete either.
            val remoteDeleted = runCatching { cloudSync.deleteRemoteDoc() }.getOrDefault(false)
            if (!remoteDeleted) {
                _form.update {
                    it.copy(busy = false, message = "Couldn't reach the cloud — connect and try again.")
                }
                return@launchBusy
            }
            var outcome = authRepository.deleteAccount()
            if (outcome == AuthOutcome.NeedsReauth) {
                if (authRepository.reauthenticateWithGoogle(activityContext) is AuthOutcome.Success) {
                    outcome = authRepository.deleteAccount()
                }
            }
            when (val o = outcome) {
                AuthOutcome.Success -> {
                    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-5): was this ViewModel's own
                    // second, weaker, non-transactional wipe (`wipeLocalData`, deleted) — missing
                    // customCategoryDao/customPromptDao deletes, `scheduler.cancelAllReminders()`,
                    // and `syncState.reset()`. Now the same shared body `wipeLocalForSignOut` uses.
                    if (alsoEraseLocal) runCatching { cloudSync.wipeAllLocalData() }
                    _form.update { AccountForm(message = "Account deleted." + if (alsoEraseLocal) " Local data erased." else " Your data on this phone is untouched.") }
                }
                AuthOutcome.NeedsReauth ->
                    _form.update { it.copy(busy = false, message = "Please sign in again, then retry deletion.") }
                is AuthOutcome.Error ->
                    _form.update { it.copy(busy = false, message = o.message) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun launchBusy(block: suspend () -> Unit) {
        _form.update { it.copy(busy = true, message = null) }
        safeLaunch {
            try { block() } catch (t: Throwable) {
                _form.update { it.copy(busy = false, message = AuthErrors.mapAuthError(t) ?: AuthErrors.GENERIC) }
            }
        }
    }

    private fun relativeSync(millis: Long): String {
        if (millis <= 0L) return "Not synced yet"
        val d = System.currentTimeMillis() - millis
        return when {
            d < TimeUnit.MINUTES.toMillis(1) -> "Synced just now"
            d < TimeUnit.HOURS.toMillis(1) -> "Synced ${TimeUnit.MILLISECONDS.toMinutes(d)} min ago"
            d < TimeUnit.DAYS.toMillis(1) -> "Synced ${TimeUnit.MILLISECONDS.toHours(d)} h ago"
            else -> "Synced ${TimeUnit.MILLISECONDS.toDays(d)} d ago"
        }
    }
}
