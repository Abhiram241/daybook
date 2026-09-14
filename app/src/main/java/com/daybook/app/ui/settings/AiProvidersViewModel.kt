package com.daybook.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.ai.AiKeyStore
import com.daybook.app.data.ai.AiModel
import com.daybook.app.data.ai.AiModelListResult
import com.daybook.app.data.ai.AiProviderId
import com.daybook.app.data.ai.AiProviderRegistry
import com.daybook.app.data.ai.AiProviderState
import com.daybook.app.util.StorageUtils
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** DAILY_REPORT_PLAN.md §3.2 — Settings' "AI Providers" section.
 *
 * Round 2 (Feature 1): "Test" now calls the provider's list-models endpoint instead of a trivial
 * completion — a successful test both verifies the key AND gives the user a picker of models to
 * choose from, replacing the need to type a model name by hand. A failed listing falls back
 * gracefully to the free-text model field with a plain-language inline error (never blocks manual
 * entry).
 */
@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val keyStore: AiKeyStore,
    private val storageUtils: StorageUtils
) : ViewModel() {

    val providerStates: StateFlow<Map<AiProviderId, AiProviderState>> =
        keyStore.states.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** H1 — true when [AiKeyStore] had to fall back to unencrypted storage, surfaced as a
     *  one-line warning banner instead of only ever reaching `Log.w`. */
    val isUsingFallbackStorage: StateFlow<Boolean> =
        keyStore.isUsingFallbackStorage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setApiKey(id: AiProviderId, apiKey: String?) {
        safeLaunch {
            keyStore.setApiKey(id, apiKey)
            // L2 — cleared after the write completes (inside the same coroutine) so nothing
            // can race a stale key value in between the clear and the persisted write.
            // A changed/removed key invalidates any model list fetched under the old key.
            _modelLists.value = _modelLists.value - id
        }
    }

    fun setModel(id: AiProviderId, model: String) {
        safeLaunch { keyStore.setModel(id, model) }
    }

    fun maskedKey(apiKey: String?): String? = keyStore.maskedKey(apiKey)

    // v0.5.3-style isExporting pattern: per-provider "testing…" state + a fixed-height result slot.
    private val _testingProvider = MutableStateFlow<AiProviderId?>(null)
    val testingProvider: StateFlow<AiProviderId?> = _testingProvider.asStateFlow()

    private val _testResults = MutableStateFlow<Map<AiProviderId, String>>(emptyMap())
    val testResults: StateFlow<Map<AiProviderId, String>> = _testResults.asStateFlow()

    /** Feature 1 — the models found for a provider's most recently tested key, `null` until a
     *  successful test. The picker (`BottomSheetMenu`) is populated from this. */
    private val _modelLists = MutableStateFlow<Map<AiProviderId, List<AiModel>>>(emptyMap())
    val modelLists: StateFlow<Map<AiProviderId, List<AiModel>>> = _modelLists.asStateFlow()

    /** §3.2 / Feature 1 — "Test this key" now lists the key's available models (which also
     *  proves the key works) rather than sending a trivial completion. */
    fun testKey(id: AiProviderId, apiKey: String, model: String) {
        if (apiKey.isBlank()) {
            _testResults.value = _testResults.value + (id to "Enter a key first.")
            return
        }
        // H4 — guard against a double-tap on the same provider's "Test" action launching two
        // concurrent lookups that race each other's `_testingProvider`/`_testResults` writes.
        if (_testingProvider.value == id) return
        safeLaunch {
            _testingProvider.value = id
            _testResults.value = _testResults.value - id
            _modelLists.value = _modelLists.value - id
            val provider = AiProviderRegistry.forId(id)
            val result = withContext(Dispatchers.IO) { provider.listModels(apiKey) }
            when (result) {
                is AiModelListResult.Success -> {
                    _modelLists.value = _modelLists.value + (id to result.models)
                    val freeCount = result.models.count { it.free == true }
                    _testResults.value = _testResults.value + (id to (
                        if (freeCount > 0) "Key verified — found ${result.models.size} model(s), $freeCount free."
                        else "Key verified — found ${result.models.size} model(s)."
                    ))
                }
                is AiModelListResult.Failure -> {
                    // Graceful fallback (per plan): never block manual entry in the free-text field.
                    _testResults.value = _testResults.value + (id to
                        "${result.message} You can still type a model name manually below.")
                }
            }
            _testingProvider.value = null
        }
    }

    fun selectModel(id: AiProviderId, modelId: String) {
        setModel(id, modelId)
    }

    fun clearTestResult(id: AiProviderId) {
        _testResults.value = _testResults.value - id
    }

    // --------------------------------------------------------------------------------------
    // User request ("export API keys and import them via JSON — that way it never hits the
    // cloud") — a completely separate flow from the main Daybook JSON backup/restore
    // (SettingsViewModel.exportRange/importFromUri) and from CloudSyncRepository: this reads/
    // writes ONLY AiKeyStore's EncryptedSharedPreferences, via ui/settings/ApiKeysExport.kt's
    // pure encode/decode. Never touches Room, Firestore, or BackupModel.
    // --------------------------------------------------------------------------------------
    private val _isBusyWithKeysFile = MutableStateFlow(false)
    val isBusyWithKeysFile: StateFlow<Boolean> = _isBusyWithKeysFile.asStateFlow()

    private val _keysFileResult = MutableStateFlow<String?>(null)
    val keysFileResult: StateFlow<String?> = _keysFileResult.asStateFlow()

    fun exportApiKeys() {
        safeLaunch {
            _isBusyWithKeysFile.value = true
            _keysFileResult.value = null
            try {
                val keys = providerStates.value.values
                    .filter { it.hasKey }
                    .associate { it.id.name to it.apiKey!! }
                if (keys.isEmpty()) {
                    _keysFileResult.value = "No keys saved yet — nothing to export."
                    return@safeLaunch
                }
                val json = encodeApiKeysJson(keys)
                val location = withContext(Dispatchers.IO) { storageUtils.saveApiKeysExport(json) }
                _keysFileResult.value = "Exported ${keys.size} key(s) to $location"
            } catch (t: Throwable) {
                _keysFileResult.value = "Export failed: ${t.message}"
            } finally {
                _isBusyWithKeysFile.value = false
            }
        }
    }

    fun importApiKeysFromUri(uri: android.net.Uri) {
        safeLaunch {
            _isBusyWithKeysFile.value = true
            _keysFileResult.value = null
            try {
                val json = withContext(Dispatchers.IO) { storageUtils.readText(uri) }
                if (json.isNullOrBlank()) {
                    _keysFileResult.value = "Could not read the selected file"
                    return@safeLaunch
                }
                val keys = decodeApiKeysJson(json)
                if (keys == null) {
                    _keysFileResult.value = "That file doesn't look like a Daybook API-keys export."
                    return@safeLaunch
                }
                var applied = 0
                for ((idName, key) in keys) {
                    val id = AiProviderId.fromNameOrNull(idName) ?: continue
                    if (key.isBlank()) continue
                    keyStore.setApiKey(id, key)
                    applied++
                }
                _keysFileResult.value = if (applied > 0) {
                    "Imported $applied key(s)."
                } else {
                    "That file didn't contain any keys Daybook recognised."
                }
            } catch (t: Throwable) {
                _keysFileResult.value = "Import failed: ${t.message}"
            } finally {
                _isBusyWithKeysFile.value = false
            }
        }
    }
}
