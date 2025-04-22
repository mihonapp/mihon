package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.DuplicateMatchLevel.ExactMatch
import tachiyomi.domain.library.service.LibraryPreferences.DuplicateMatchLevel.FuzzyTitle
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {
    private fun titleFuzzyRegex(manga: Manga): Regex {
        return Regex("""\b${manga.title}\b""", option = RegexOption.IGNORE_CASE)
    }

    suspend fun subscribe(
        searchLevel: LibraryPreferences.DuplicateMatchLevel = libraryPreferences.duplicateMatchLevel().get(),
    ): Flow<Map<MangaWithChapterCount, List<MangaWithChapterCount>>> {
        val fuzzy = searchLevel != ExactMatch
        return mangaRepository.getAllDuplicateLibraryMangaAsFlow(fuzzy).map { allDuplicatesList ->
            allDuplicatesList.mapNotNull {
                val keyManga = mangaRepository.getMangaByIdWithChapterCount(it.sourceMangaId)
                if (searchLevel == FuzzyTitle &&
                    !titleFuzzyRegex(keyManga.manga).containsMatchIn(it.manga.title)
                ) {
                    return@mapNotNull null
                }
                Pair(keyManga, MangaWithChapterCount(it.manga, it.chapterCount))
            }.sortedBy { it.second.manga.title.lowercase() }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .toList()
                .sortedBy { it.first.manga.title.lowercase() }
                .sortedByDescending { it.second.count() }
                .toMap()
        }
    }

    suspend operator fun invoke(
        manga: Manga,
        searchLevel: LibraryPreferences.DuplicateMatchLevel = libraryPreferences.duplicateMatchLevel().get(),
    ): List<MangaWithChapterCount> {
        return when (searchLevel) {
            ExactMatch -> exactTitleMatch(manga)
            LibraryPreferences.DuplicateMatchLevel.FuzzyTitle -> fuzzyTitleSearch(manga)
            LibraryPreferences.DuplicateMatchLevel.TitleSubstring -> fuzzySubstringSearch(manga)
        }
    }

    private suspend fun exactTitleMatch(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title)
    }

    private suspend fun fuzzyTitleSearch(manga: Manga): List<MangaWithChapterCount> {
        val regex = titleFuzzyRegex(manga)
        return mangaRepository.getDuplicateLibraryManga(manga.id, "%${manga.title}%")
            .filter { regex.containsMatchIn(it.manga.title) }
    }

    private suspend fun fuzzySubstringSearch(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, "%${manga.title}%")
    }
}
