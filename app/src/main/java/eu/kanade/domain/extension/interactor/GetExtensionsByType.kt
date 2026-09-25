package eu.kanade.domain.extension.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.extension.model.Extensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Inject
class GetExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<Extensions> {
        val enabledContentWarnings = preferences.enabledContentWarnings.get()

        return combine(
            preferences.enabledLanguages.changes(),
            extensionManager.loadedExtensionsFlow,
            extensionManager.notLoadedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguages, _loaded, _notLoaded, _available ->
            val (updates, loaded) = _loaded
                .sortedWith(
                    compareBy<Extension.Loaded> { !it.isObsolete }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .partition { it.hasUpdate }

            val notLoaded = _notLoaded
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val available = _available
                .filter { extension ->
                    _loaded.none { it.pkgName == extension.pkgName } &&
                        _notLoaded.none { it.pkgName == extension.pkgName } &&
                        extension.contentWarning in enabledContentWarnings
                }
                .flatMap { ext ->
                    ext.sources.filter { it.lang in enabledLanguages }
                        .map {
                            ext.copy(
                                name = it.name,
                                lang = it.lang,
                                pkgName = "${ext.pkgName}-${it.id}",
                                sources = listOf(it),
                            )
                        }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            Extensions(updates, loaded, available, notLoaded)
        }
    }
}
