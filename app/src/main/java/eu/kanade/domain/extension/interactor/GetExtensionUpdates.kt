package eu.kanade.domain.extension.interactor

import eu.kanade.core.util.asFlow
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetExtensionUpdates(
    private val preferences: PreferencesHelper,
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<List<Extension.Installed>> {
        val showNsfwSources = preferences.showNsfwSource().get()

        return extensionManager.getInstalledExtensionsObservable().asFlow()
            .map { installed ->
                installed
                    .filter { it.hasUpdate && (showNsfwSources || it.isNsfw.not()) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
    }
}
