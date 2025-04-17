package tachiyomi.domain.manga.interactor

import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(
        manga: Manga,
        searchLevel: LibraryPreferences.DuplicateMatchLevel = libraryPreferences.duplicateMatchLevel().get(),
    ): List<MangaWithChapterCount> {
        return when (searchLevel) {
            LibraryPreferences.DuplicateMatchLevel.ExactMatch -> exactTitleMatch(manga)
            LibraryPreferences.DuplicateMatchLevel.FuzzyTitle -> fuzzyTitleSearch(manga)
            LibraryPreferences.DuplicateMatchLevel.TitleSubstring -> fuzzySubstringSearch(manga)
        }
    }

    private suspend fun exactTitleMatch(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title)
    }

    private suspend fun fuzzyTitleSearch(manga: Manga): List<MangaWithChapterCount> {
        val regex = Regex("""\b${manga.title}\b""", option = RegexOption.IGNORE_CASE)
        return mangaRepository.getDuplicateLibraryManga(manga.id, "%${manga.title}%")
            .filter { regex.containsMatchIn(it.manga.title) }
    }

    private suspend fun fuzzySubstringSearch(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, "%${manga.title}%")
    }
}
