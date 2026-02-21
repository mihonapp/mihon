package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.DuplicateMatchLevel.ExactMatch
import tachiyomi.domain.library.service.LibraryPreferences.DuplicateMatchLevel.FuzzyTitle
import tachiyomi.domain.library.service.LibraryPreferences.DuplicateMatchLevel.TitleSubstring
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {
    private fun fuzzyTitleRegex(manga: Manga): Regex {
        val sanitisedTitle = manga.title.replace(Regex("""[#-.]|[\[-^]|[?|{}]"""), """\\$0""")
        return Regex("""(^|\b)$sanitisedTitle(\b|$)""", option = RegexOption.IGNORE_CASE)
    }

    suspend fun subscribe(
        searchLevel: LibraryPreferences.DuplicateMatchLevel = libraryPreferences.duplicateMatchLevel().get(),
    ): Flow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> {
        val fuzzy = searchLevel != ExactMatch
        return mangaRepository.getAllDuplicateLibraryMangaAsFlow(fuzzy).map { allDuplicatesList ->
            allDuplicatesList.mapNotNull {
                val keyManga = mangaRepository.getMangaByIdWithChapterCount(it.sourceMangaId)
                if (searchLevel == FuzzyTitle &&
                    !fuzzyTitleRegex(keyManga.manga).containsMatchIn(it.manga.title)
                ) {
                    return@mapNotNull null
                }
                Pair(keyManga, MangaWithChapterCount(it.manga, it.chapterCount))
            }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
        }
    }

    suspend operator fun invoke(
        manga: Manga,
        searchLevel: LibraryPreferences.DuplicateMatchLevel = libraryPreferences.duplicateMatchLevel().get(),
    ): List<MangaWithChapterCount> {
        if (manga.title.isBlank()) return listOf()
        return when (searchLevel) {
            ExactMatch -> exactTitleMatch(manga)
            FuzzyTitle -> fuzzyTitleSearch(manga)
            TitleSubstring -> fuzzySubstringSearch(manga)
        }
    }

    private suspend fun exactTitleMatch(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title)
    }

    private suspend fun fuzzyTitleSearch(manga: Manga): List<MangaWithChapterCount> {
        val regex = fuzzyTitleRegex(manga)
        return mangaRepository.getDuplicateLibraryManga(manga.id, "%${manga.title}%")
            // TODO: this likely filters out matches from trackers which we probably wanna keep. Not sure how to solve rn tho
            .filter { regex.containsMatchIn(it.manga.title) }
    }

    private suspend fun fuzzySubstringSearch(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, "%${manga.title}%")
    }
}
