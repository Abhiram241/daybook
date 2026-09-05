package com.daybook.app.data

import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.CustomPrompt
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.5.3 — saved, reusable intake prompt messages. Verbatim shape of [CustomCategoryRepository].
 *
 * Normalisation rule (pinned in [normalisePrompt]): **trim only, case-preserving,
 * case-sensitive uniqueness** — same rule as [normaliseCategory].
 *
 * Removing a prompt does **not** touch tasks that already reference it: `FoodMedTask.promptMessage`
 * is a denormalised copy by design.
 */
@Singleton
class CustomPromptRepository @Inject constructor(private val database: AppDatabase) {

    fun observeNames(): Flow<List<String>> = database.customPromptDao().observeNames()

    /** Normalise → dedupe-by-insert. Returns the canonical stored name, or null if blank. */
    suspend fun addIfAbsent(raw: String): String? {
        val name = normalisePrompt(raw) ?: return null
        database.customPromptDao().insert(CustomPrompt(name = name))
        return name
    }

    suspend fun remove(name: String) {
        normalisePrompt(name)?.let { database.customPromptDao().deleteByName(it) }
    }
}

/** Pure: trim only; blank → null. Case-sensitive uniqueness, same rule as normaliseCategory. */
fun normalisePrompt(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }
