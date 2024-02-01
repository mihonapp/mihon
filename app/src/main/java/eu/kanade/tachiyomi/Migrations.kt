package eu.kanade.tachiyomi

import android.content.Context
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

object Migrations {

    /**
     * Performs a migration when the application is updated.
     *
     * @return true if a migration is performed, false otherwise.
     */
    @Suppress("SameReturnValue")
    fun upgrade(
        context: Context,
        preferenceStore: PreferenceStore,
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
        }

        return false
    }
}
