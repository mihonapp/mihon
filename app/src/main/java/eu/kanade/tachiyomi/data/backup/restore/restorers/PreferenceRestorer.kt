package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.preference.AndroidPreferenceStore
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.domain.backup.model.BackupPreference
import tachiyomi.domain.backup.model.BackupSourcePreferences
import tachiyomi.domain.backup.model.BooleanPreferenceValue
import tachiyomi.domain.backup.model.FloatPreferenceValue
import tachiyomi.domain.backup.model.IntPreferenceValue
import tachiyomi.domain.backup.model.LongPreferenceValue
import tachiyomi.domain.backup.model.StringPreferenceValue
import tachiyomi.domain.backup.model.StringSetPreferenceValue
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceRestorer(
    private val context: Context,
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {

    fun restoreAppPreferences(preferences: List<BackupPreference>) {
        restorePreferences(preferences, preferenceStore)

        LibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
    }

    fun restoreSourcePreferences(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
    ) {
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            when (value) {
                is IntPreferenceValue -> {
                    if (prefs[key] is Int?) {
                        preferenceStore.getInt(key).set(value.value)
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
                        preferenceStore.getStringSet(key).set(value.value)
                    }
                }
            }
        }
    }
}
