package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences.Companion.DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY
import tachiyomi.domain.download.service.DownloadPreferences.Companion.DOWNLOAD_NEW_CATEGORIES_PREF_KEY
import tachiyomi.domain.download.service.DownloadPreferences.Companion.REMOVE_EXCLUDE_CATEGORIES_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEFAULT_CATEGORY_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences.Companion.LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences.Companion.LIBRARY_UPDATE_CATEGORIES_PREF_KEY
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceRestorer(
    private val context: Context,
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    private val getCategories by lazy { Injekt.get<GetCategories>() }

    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupCategories: List<BackupCategory>,
    ) {
        restorePreferences(
            preferences,
            preferenceStore,
            backupCategories,
        )

        LibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
    }

    suspend fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private suspend fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        backupCategories: List<BackupCategory> = emptyList(),
    ) {
        val allCategories = getCategories.await()
        val categoriesByName = allCategories.associateBy { it.name }
        val backupCategoriesById = backupCategories.associateBy { it.id.toString() }
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            try {
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            when (key) {
                                // Matching oldId to newId
                                DEFAULT_CATEGORY_PREF_KEY ->
                                    restoreDefaultCategory(
                                        key,
                                        value.value,
                                        preferenceStore,
                                        backupCategories,
                                        categoriesByName,
                                    )
                                else ->
                                    preferenceStore.getInt(key).set(value.value)
                            }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            when (key) {
                                // Matching oldId to newId
                                LIBRARY_UPDATE_CATEGORIES_PREF_KEY, LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
                                DOWNLOAD_NEW_CATEGORIES_PREF_KEY, DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
                                REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
                                ->
                                    restoreCategoriesPreferences(
                                        key,
                                        value.value,
                                        preferenceStore,
                                        backupCategoriesById,
                                        categoriesByName,
                                    )
                                else ->
                                    preferenceStore.getStringSet(key).set(value.value)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PreferenceRestorer", "Failed to restore preference <$key>", e)
            }
        }
    }

    private fun restoreCategoriesPreferences(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
        backupCategoriesById: Map<String, BackupCategory>,
        categoriesByName: Map<String, Category>,
    ) {
        val newValue = value.mapNotNull { oldId ->
            backupCategoriesById[oldId]?.let { backupCategory ->
                categoriesByName[backupCategory.name]?.id?.toString()
            }
        }.toSet()
        if (newValue.isNotEmpty()) {
            preferenceStore.getStringSet(key).set(newValue)
        }
    }

    private fun restoreDefaultCategory(
        key: String,
        value: Int,
        preferenceStore: PreferenceStore,
        backupCategories: List<BackupCategory>,
        categoriesByName: Map<String, Category>,
    ) {
        if (backupCategories.isNotEmpty()) {
            val oldId = value.toLong()
            backupCategories.find { it.id == oldId }
                ?.let { categoriesByName[it.name]?.id?.toInt() }
                ?.let { preferenceStore.getInt(key).set(it) }
        }
    }
}
