package com.daybook.app.data

import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.CustomCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.5.2 §4 — saved, reusable CUSTOM/JOURNAL category names.
 *
 * Normalisation rule (pinned in [normaliseCategory]): **trim only, case-preserving,
 * case-sensitive uniqueness.** "Snack" and "snack" are two categories. Case-insensitive dedupe
 * would need `COLLATE NOCASE` on the primary key and a migration change — not worth it.
 *
 * Removing a category does **not** touch tasks that already reference it: `FoodMedTask.customCategory`
 * is a denormalised copy by design (that is why the column is a `String?`, not a foreign key), so an
 * existing task keeps its label.
 */
@Singleton
class CustomCategoryRepository @Inject constructor(private val database: AppDatabase) {

    fun observeNames(): Flow<List<String>> = database.customCategoryDao().observeNames()

    /** Normalise → dedupe-by-insert. Returns the canonical stored name, or null if blank. */
    suspend fun addIfAbsent(raw: String): String? {
        val name = normaliseCategory(raw) ?: return null
        database.customCategoryDao().insert(CustomCategory(name = name))
        return name
    }

    suspend fun remove(name: String) {
        normaliseCategory(name)?.let { database.customCategoryDao().deleteByName(it) }
    }
}

/** Pure: the one normalisation rule, testable without Room. Trim only; blank → null. */
fun normaliseCategory(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }
