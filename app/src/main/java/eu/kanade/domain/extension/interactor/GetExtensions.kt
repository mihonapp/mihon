package eu.kanade.domain.extension.interactor

import eu.kanade.core.util.asFlow
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

typealias ExtensionSegregation = Triple<List<Extension.Installed>, List<Extension.Untrusted>, List<Extension.Available>>

class GetExtensions(
    private val preferences: PreferencesHelper,
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<ExtensionSegregation> {
        val showNsfwSources = preferences.showNsfwSource().get()

        return combine(
            preferences.enabledLanguages().asFlow(),
            extensionManager.getInstalledExtensionsObservable().asFlow(),
            extensionManager.getUntrustedExtensionsObservable().asFlow(),
            extensionManager.getAvailableExtensionsObservable().asFlow(),
        ) { _activeLanguages, _installed, _untrusted, _available ->

            val installed = _installed
                .filter { it.hasUpdate.not() && (showNsfwSources || it.isNsfw.not()) }
                .sortedWith(
                    compareBy<Extension.Installed> { it.isObsolete.not() }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )

            val untrusted = _untrusted
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val available = _available
                .filter { extension ->
                    _installed.none { it.pkgName == extension.pkgName } &&
                        _untrusted.none { it.pkgName == extension.pkgName } &&
                        extension.lang in _activeLanguages &&
                        (showNsfwSources || extension.isNsfw.not())
                }

            Triple(installed, untrusted, available)
        }
    }
}
