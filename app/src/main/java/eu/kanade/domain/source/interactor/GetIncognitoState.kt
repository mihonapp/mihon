package eu.kanade.domain.source.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import mihon.feature.mekuri.vault.MekuriVault

@Inject
class GetIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {
    suspend fun await(sourceId: Long?): Boolean {
        if (basePreferences.incognitoMode.get()) return true
        if (sourceId == null) return false
        if (MekuriVault.isSourceInVault(sourceId)) return true // Mekuri: vault sources are always incognito
        val extensionPackage = extensionManager.getExtensionPackage(sourceId) ?: return false

        return extensionPackage in sourcePreferences.incognitoExtensions.get()
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode.changes()

        return combine(
            basePreferences.incognitoMode.changes(),
            sourcePreferences.incognitoExtensions.changes(),
            extensionManager.getExtensionPackageAsFlow(sourceId),
            MekuriVault.filter,
        ) { incognito, incognitoExtensions, extensionPackage, vault ->
            // Mekuri: vault sources are always incognito
            incognito || (extensionPackage in incognitoExtensions) || sourceId in vault.sources
        }
            .distinctUntilChanged()
    }
}
