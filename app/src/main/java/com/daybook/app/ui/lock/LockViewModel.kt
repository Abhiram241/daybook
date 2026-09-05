package com.daybook.app.ui.lock

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.ProfilePhotoStore
import com.daybook.app.data.lock.AppLockRepository
import com.daybook.app.data.lock.BiometricGate
import com.daybook.app.data.lock.LockTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel the app-lock UI binds to (v0.5.1 §K). Owned by the logic agent; the `LockScreen`
 * composable, the PIN pad and the "App lock" settings sub-screen are the UI agent's (§K-UI) and
 * should drive everything through this surface rather than touching [AppLockRepository] directly.
 *
 * Decision 8: there is deliberately **no** attempt counter and **no** cooldown. [wrongPin] is a
 * one-shot flag for the shake animation, nothing more.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val repo: AppLockRepository,
    val biometricGate: BiometricGate,
    // v0.5.2 §6: both purely local — the lock screen renders pre-auth and must not touch Firebase.
    profilePhotoStore: ProfilePhotoStore,
    settingsRepository: AppSettingsRepository
) : ViewModel() {

    /** filesDir path of the profile photo, read once on construction. Null when none is set. */
    val photoPath: String? = profilePhotoStore.currentPath()

    val userName: StateFlow<String> =
        settingsRepository.observeSettings().map { it.userName }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val isEnabled: StateFlow<Boolean> = repo.isEnabled
    val isLocked: StateFlow<Boolean> = repo.isLocked
    val timeout: StateFlow<LockTimeout> = repo.timeout

    /** Digits entered so far on the pad, 0..4. The screen renders one filled dot per digit. */
    private val _entry = MutableStateFlow("")
    val entry: StateFlow<String> = _entry.asStateFlow()

    /** Set for one frame after a wrong PIN so the dot row can shake, then cleared by [clearError]. */
    private val _wrongPin = MutableStateFlow(false)
    val wrongPin: StateFlow<Boolean> = _wrongPin.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun biometricsAvailable(): Boolean = biometricGate.isAvailable()

    fun clearError() { _wrongPin.value = false }

    /** Appends a digit; auto-submits on the fourth. */
    fun onDigit(d: Char) {
        if (_busy.value || _entry.value.length >= 4) return
        _wrongPin.value = false
        _entry.value = _entry.value + d
        if (_entry.value.length == 4) submit()
    }

    fun onBackspace() {
        if (_busy.value) return
        _wrongPin.value = false
        _entry.value = _entry.value.dropLast(1)
    }

    private fun submit() {
        val pin = _entry.value
        safeLaunch {
            _busy.value = true
            val ok = repo.verifyPin(pin)
            _busy.value = false
            if (ok) {
                _entry.value = ""
                repo.unlock()
            } else {
                _wrongPin.value = true
                _entry.value = ""
            }
        }
    }

    /** Call from the lock screen after a successful [BiometricGate.prompt]. */
    fun onBiometricSuccess() {
        _entry.value = ""
        repo.unlock()
    }

    // ---- settings sub-screen (§K-UI) -------------------------------------------------

    fun setTimeout(t: LockTimeout) = repo.setTimeout(t)

    fun hasPin(): Boolean = repo.hasPin()

    /** Enable flow: set PIN → confirm → this. Returns false for a non-4-digit PIN. */
    fun enable(pin: String, onResult: (Boolean) -> Unit) {
        safeLaunch { onResult(repo.enable(pin)) }
    }

    /** Turning the switch off. [bypassPin] = true only after a successful biometric prompt. */
    fun disable(pin: String, bypassPin: Boolean = false, onResult: (Boolean) -> Unit) {
        safeLaunch { onResult(repo.disable(pin, bypassPin)) }
    }

    fun changePin(current: String, new: String, bypassCurrent: Boolean = false, onResult: (Boolean) -> Unit) {
        safeLaunch { onResult(repo.changePin(current, new, bypassCurrent)) }
    }
}
