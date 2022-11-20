package eu.kanade.domain.extension.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetExtensionSources(
    private val preferences: SourcePreferences,
) {

    fun subscribe(extension: Extension.Installed): Flow<List<ExtensionSourceItem>> {
        val isMultiSource = extension.sources.size > 1
        val isMultiLangSingleSource =
            isMultiSource && extension.sources.map { it.name }.distinct().size == 1

        return preferences.disabledSources().changes().map { disabledSources ->
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

data class ExtensionSourceItem(
    val source: Source,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
