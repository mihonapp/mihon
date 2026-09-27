package eu.kanade.tachiyomi.extension.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@SingleIn(AppScope::class)
class ExtensionApi(
    private val repository: ExtensionStoreRepository,
    private val updateExtensionStores: UpdateExtensionStores,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext { repository.fetchExtensions() }
    }

    /**
     * @param installedExtensions Extensions already read by [eu.kanade.tachiyomi.extension.ExtensionManager],
     * loaded or not. Only their versions and signatures are read, so there's nothing to gain from reading them
     * a second time.
     */
    suspend fun checkForUpdates(installedExtensions: List<Extension.Installed>) {
        updateExtensionStores()

        val extensions = findExtensions()

        val extensionsWithUpdate = installedExtensions.filter { it.findUpdate(extensions) != null }

        if (extensionsWithUpdate.isNotEmpty()) {
            extensionUpdateNotifier.promptUpdates(extensionsWithUpdate.map { it.name })
        }
    }
}
