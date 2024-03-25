package eu.kanade.tachiyomi

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import logcat.LogPriority
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

object Migrations {

    /**
     * Performs a migration when the application is updated.
     *
     * @return true if a migration is performed, false otherwise.
     */
    @Suppress("SameReturnValue", "MagicNumber")
    fun upgrade(
        context: Context,
        preferenceStore: PreferenceStore,
        sourcePreferences: SourcePreferences,
        extensionRepoRepository: ExtensionRepoRepository,
    ): Boolean {
        val lastVersionCode = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        val oldVersion = lastVersionCode.get()
        if (oldVersion < BuildConfig.VERSION_CODE) {
            lastVersionCode.set(BuildConfig.VERSION_CODE)

            // Always set up background tasks to ensure they're running
            LibraryUpdateJob.setupTask(context)
            BackupCreateJob.setupTask(context)

            // Fresh install
            if (oldVersion == 0) {
                return false
            }

            val coroutineScope = CoroutineScope(Dispatchers.IO)

            if (oldVersion < 7) {
                coroutineScope.launchIO {
                    for ((index, source) in sourcePreferences.extensionRepos().get().withIndex()) {
                        try {
                            extensionRepoRepository.upsertRepo(
                                source,
                                "Repo #${index + 1}",
                                null,
                                source,
                                "NOFINGERPRINT-${index + 1}",
                            )
                        } catch (e: SaveExtensionRepoException) {
                            logcat(LogPriority.ERROR, e) { "Error Migrating Extension Repo with baseUrl: $source" }
                        }
                    }
                    sourcePreferences.extensionRepos().delete()
                }
            }
        }

        return false
    }
}
