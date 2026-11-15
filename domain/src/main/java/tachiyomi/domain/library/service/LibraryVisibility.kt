package tachiyomi.domain.library.service

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.repository.MangaRepository

/** Visibility of hidden library entries and their sources across app screens and widgets. */
@Inject
class LibraryVisibility(
    private val preferences: LibraryPreferences,
    private val mangaRepository: MangaRepository,
    private val hiddenLibrarySession: HiddenLibrarySession,
) {
    fun hiddenCategoryIds(): List<Long> = categoryIds(preferences.hiddenCategories.get())

    fun changes(): Flow<List<Long>> = preferences.hiddenCategories.changes()
        .map(::categoryIds).distinctUntilChanged()

    suspend fun hiddenMangaIds(): Set<Long> {
        val categories = hiddenCategoryIds()
        if (categories.isEmpty()) return emptySet()
        return mangaRepository.getHiddenLibraryMangaIds(categories)
    }

    fun hiddenMangaIdsFlow(): Flow<Set<Long>> = changes().flatMapLatest { categories ->
        if (categories.isEmpty()) {
            flowOf(emptySet())
        } else {
            mangaRepository.getHiddenLibraryMangaIdsAsFlow(categories)
        }
    }.distinctUntilChanged()

    fun hiddenSourceIdsFlow(): Flow<Set<Long>> = combine(
        preferences.hideSourcesInHiddenCategories.changes(),
        changes(),
    ) { enabled, categories ->
        if (enabled) categories else emptyList()
    }.distinctUntilChanged().flatMapLatest { categories ->
        if (categories.isEmpty()) flowOf(emptySet()) else mangaRepository.getHiddenLibrarySourceIdsAsFlow(categories)
    }.combine(hiddenLibrarySession.unlocked) { sources, unlocked ->
        if (unlocked) emptySet() else sources
    }.distinctUntilChanged()

    private fun categoryIds(hidden: Set<String>): List<Long> = hidden.mapNotNull(String::toLongOrNull)
}

fun LibraryManga.isHiddenInLibrary(hiddenCategories: Set<String>): Boolean =
    categories.any { it.toString() in hiddenCategories }
