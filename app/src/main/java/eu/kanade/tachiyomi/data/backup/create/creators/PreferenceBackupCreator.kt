package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences.Companion.DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY
import tachiyomi.domain.download.service.DownloadPreferences.Companion.DOWNLOAD_NEW_CATEGORIES_PREF_KEY
import tachiyomi.domain.download.service.DownloadPreferences.Companion.REMOVE_EXCLUDE_CATEGORIES_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEFAULT_CATEGORY_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences.Companion.LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences.Companion.LIBRARY_UPDATE_CATEGORIES_PREF_KEY
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceBackupCreator(
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    private val getCategories by lazy { Injekt.get<GetCategories>() }
    private val libraryPreferences by lazy { Injekt.get<LibraryPreferences>() }

    suspend fun createApp(includePrivatePreferences: Boolean): List<BackupPreference> {
        val allCategories = getCategories.await()

        return preferenceStore.getAll().toBackupPreferences()
            .map { backupPreference ->
                when (backupPreference.key) {
                    // Convert CategoryId to CategoryOrder
                    DEFAULT_CATEGORY_PREF_KEY -> {
                        val id = (backupPreference.value as IntPreferenceValue).value.toLong()
                        val value = allCategories.find { it.id == id }?.order?.toInt()
                            ?: libraryPreferences.defaultCategory().defaultValue()
                        BackupPreference(
                            backupPreference.key,
                            IntPreferenceValue(value),
                        )
                    }
                    // Convert CategoryId to CategoryOrder
                    LIBRARY_UPDATE_CATEGORIES_PREF_KEY, LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
                    DOWNLOAD_NEW_CATEGORIES_PREF_KEY, DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
                    REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
                    -> {
                        val categoryIds = (backupPreference.value as StringSetPreferenceValue).value
                        val categories = allCategories.associateBy({ it.id.toString() }, { it.order.toString() })
                        BackupPreference(
                            backupPreference.key,
                            StringSetPreferenceValue(
                                categoryIds.mapNotNull { categories[it] }.toSet(),
                            ),
                        )
                    }
                    else -> backupPreference
                }
            }
            .withPrivatePreferences(includePrivatePreferences)
    }

    fun createSource(includePrivatePreferences: Boolean): List<BackupSourcePreferences> {
        return sourceManager.getCatalogueSources()
            .filterIsInstance<ConfigurableSource>()
            .map {
                BackupSourcePreferences(
                    it.preferenceKey(),
                    it.sourcePreferences().all.toBackupPreferences()
                        .withPrivatePreferences(includePrivatePreferences),
                )
            }
            .filter { it.prefs.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.toBackupPreferences(): List<BackupPreference> {
        return this
            .filterKeys { !Preference.isAppState(it) }
            .mapNotNull { (key, value) ->
                when (value) {
                    is Int -> BackupPreference(key, IntPreferenceValue(value))
                    is Long -> BackupPreference(key, LongPreferenceValue(value))
                    is Float -> BackupPreference(key, FloatPreferenceValue(value))
                    is String -> BackupPreference(key, StringPreferenceValue(value))
                    is Boolean -> BackupPreference(key, BooleanPreferenceValue(value))
                    is Set<*> -> (value as? Set<String>)?.let {
                        BackupPreference(key, StringSetPreferenceValue(it))
                    }
                    else -> null
                }
            }
    }

    private fun List<BackupPreference>.withPrivatePreferences(include: Boolean) =
        if (include) {
            this
        } else {
            this.filter { !Preference.isPrivate(it.key) }
        }
}
