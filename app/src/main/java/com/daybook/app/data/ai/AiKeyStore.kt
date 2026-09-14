package com.daybook.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.daybook.app.util.recordUnhandledException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One provider's saved key/model, as shown in Settings. */
data class AiProviderState(
    val id: AiProviderId,
    /** Null when no key is saved. Never held longer than needed to mask/reveal/use it. */
    val apiKey: String?,
    val model: String
) {
    val hasKey: Boolean get() = !apiKey.isNullOrBlank()
}

/**
 * DAILY_REPORT_PLAN.md §3.2 — API key storage. Directly reuses the exact pattern
 * `AppLockRepository` already established (`MasterKey` + `EncryptedSharedPreferences`,
 * `data/lock/AppLockRepository.kt:1-40`) — its own file (`daybook_ai_keys`), deliberately NOT
 * `daybook_prefs`, for the same reason `AppLockRepository`'s KDoc gives for its own file:
 * `daybook_prefs` is touched by `SyncStateStore.clearForSignOut()`, and an API key has no business
 * being wiped by that path.
 *
 * **Never synced, never in the JSON export** (§3.2) — this class has no export/import/sync
 * counterpart anywhere in the codebase, by design; the only readers are this Settings screen and
 * the generation flow (§3.5).
 */
@Singleton
class AiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // H1 — set true when [openPrefs] had to fall back to the unencrypted file, so callers (the
    // AI Providers settings screen) can surface that consequential, undisclosed-otherwise state
    // change instead of it only ever reaching `Log.w`.
    private var usingFallbackStorage = false

    private val prefs: SharedPreferences = openPrefs(context)

    private val _isUsingFallbackStorage = MutableStateFlow(usingFallbackStorage)
    val isUsingFallbackStorage: StateFlow<Boolean> = _isUsingFallbackStorage.asStateFlow()

    private val _states = MutableStateFlow(loadAll())
    val states: StateFlow<Map<AiProviderId, AiProviderState>> = _states.asStateFlow()

    // C2 — an undecryptable individual entry (e.g. a Keystore key invalidated after a
    // lock-screen change, or a partially-corrupted file) must not crash the whole app on
    // construction; each read is guarded on its own and treated as "no key/model saved."
    private fun loadAll(): Map<AiProviderId, AiProviderState> =
        AiProviderId.entries.associateWith { id ->
            AiProviderState(
                id = id,
                apiKey = readString(keyOf(id))?.takeIf { it.isNotBlank() },
                model = readString(modelOf(id)).orEmpty()
            )
        }

    private fun readString(key: String): String? = runCatching { prefs.getString(key, null) }
        .onFailure { recordUnhandledException(it) }
        .getOrNull()

    suspend fun setApiKey(id: AiProviderId, apiKey: String?) {
        withContext(Dispatchers.IO) {
            val trimmed = apiKey?.trim()?.takeIf { it.isNotBlank() }
            if (trimmed == null) prefs.edit().remove(keyOf(id)).apply()
            else prefs.edit().putString(keyOf(id), trimmed).apply()
        }
        _states.value = loadAll()
    }

    suspend fun setModel(id: AiProviderId, model: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(modelOf(id), model.trim()).apply()
        }
        _states.value = loadAll()
    }

    /** A masked display value, e.g. `sk-••••1a2b`, or null when no key is saved. */
    fun maskedKey(apiKey: String?): String? {
        if (apiKey.isNullOrBlank()) return null
        val tail = apiKey.takeLast(4)
        return "••••$tail"
    }

    private fun keyOf(id: AiProviderId) = "${id.name}_key"
    private fun modelOf(id: AiProviderId) = "${id.name}_model"

    private fun openPrefs(context: Context): SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as SharedPreferences
    }.getOrElse {
        Log.w(TAG, "EncryptedSharedPreferences unavailable — falling back to plain prefs", it)
        usingFallbackStorage = true
        context.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)
    }

    private companion object {
        const val TAG = "AiKeyStore"
        const val FILE = "daybook_ai_keys"
        /** Same conservative fallback `AppLockRepository` uses for a broken keystore — the worst
         *  case is a weaker at-rest story, not bricking the app. */
        const val FILE_FALLBACK = "daybook_ai_keys_plain"
    }
}
