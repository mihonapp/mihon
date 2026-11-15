package eu.kanade.domain.source.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.library.service.LibraryVisibility
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import java.util.SortedMap

@Inject
class GetLanguagesWithSources(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
    private val libraryVisibility: LibraryVisibility,
) {

    fun subscribe(): Flow<SortedMap<String, List<Source>>> {
        return combine(
            preferences.enabledLanguages.changes(),
            preferences.disabledSources.changes(),
            repository.getOnlineSources(),
            libraryVisibility.hiddenSourceIdsFlow(),
        ) { enabledLanguage, disabledSource, onlineSources, hiddenSourceIds ->
            onlineSources
                .filterNot { it.id in hiddenSourceIds }
                .sortedWith(
                    compareBy<Source> { it.id.toString() in disabledSource }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .groupBy { it.lang }
                .toSortedMap(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }
}
