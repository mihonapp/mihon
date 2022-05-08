package eu.kanade.domain.extension.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionSourceItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetExtensionSources(
    private val preferences: PreferencesHelper,
) {

    fun subscribe(extension: Extension.Installed): Flow<List<ExtensionSourceItem>> {
        val isMultiSource = extension.sources.size > 1
        val isMultiLangSingleSource =
            isMultiSource && extension.sources.map { it.name }.distinct().size == 1

        return preferences.disabledSources().asFlow().map { disabledSources ->
            fun Source.isEnabled() = id.toString() !in disabledSources

            extension.sources
                .map { source ->
                    ExtensionSourceItem(
                        source = source,
                        enabled = source.isEnabled(),
                        labelAsName = isMultiSource && isMultiLangSingleSource.not(),
                    )
                }
        }
    }
}
