package mihon.domain.upcoming.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import tachiyomi.domain.library.service.LibraryVisibility
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

@Inject
class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
    private val libraryVisibility: LibraryVisibility,
) {

    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )

    fun subscribe(
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Manga>> {
        return libraryVisibility.changes().flatMapLatest { hiddenCategories ->
            mangaRepository.getUpcomingManga(
                includedStatuses,
                excludedCategories = excludedCategories.union(hiddenCategories).toList(),
                includedCategories = includedCategories,
            )
        }
    }
}
