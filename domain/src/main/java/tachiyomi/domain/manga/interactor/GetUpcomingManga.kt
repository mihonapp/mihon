package tachiyomi.domain.manga.interactor

import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
) {

    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun await(): List<Manga> {
        return mangaRepository.getUpcomingManga(includedStatuses)
    }
}
