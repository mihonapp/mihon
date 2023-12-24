package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.domain.backup.model.BackupPreference
import tachiyomi.domain.backup.model.BackupSourcePreferences
import tachiyomi.domain.backup.model.BooleanPreferenceValue
import tachiyomi.domain.backup.model.FloatPreferenceValue
import tachiyomi.domain.backup.model.IntPreferenceValue
import tachiyomi.domain.backup.model.LongPreferenceValue
import tachiyomi.domain.backup.model.StringPreferenceValue
import tachiyomi.domain.backup.model.StringSetPreferenceValue
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceBackupCreator(
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {

    fun backupAppPreferences(): List<BackupPreference> {
        return preferenceStore.getAll().toBackupPreferences()
    }

    fun backupSourcePreferences(): List<BackupSourcePreferences> {
        return sourceManager.getCatalogueSources()
            .filterIsInstance<ConfigurableSource>()
            .map {
                BackupSourcePreferences(
                    it.preferenceKey(),
                    it.sourcePreferences().all.toBackupPreferences(),
                )
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.toBackupPreferences(): List<BackupPreference> {
        return this.filterKeys {
            !Preference.isPrivate(it) && !Preference.isAppState(it)
        }
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
}
