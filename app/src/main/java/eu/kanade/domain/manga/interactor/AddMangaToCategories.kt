package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.interactor.AddTracks
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock

@Inject
class AddMangaToCategories(
    private val mangaRepository: MangaRepository,
    private val categoryRepository: CategoryRepository,
    private val defaultChapterFlags: SetMangaDefaultChapterFlags,
    private val addTracks: AddTracks,
    private val sourceManager: SourceManager,
) {
    suspend fun await(mangaIds: List<Long>, categoryIds: List<Long>): Int {
        val ids = mangaIds.distinct()
        val chosenCategories = categoryIds.filter { it > 0 }.toSet()
        for (id in ids) {
            // Read current state: an item selected on a previous page may have changed since then.
            val manga = mangaRepository.getMangaById(id)
            if (!manga.favorite) {
                defaultChapterFlags.await(manga)
                check(mangaRepository.update(MangaUpdate(id) { favoriteAt = Clock.System.now() }))
            }
            val categories = categoryRepository.getCategoriesByMangaId(id)
                .map { it.id }
                .filter { it > 0 }
                .toSet() + chosenCategories
            mangaRepository.setMangaCategories(id, categories.toList())
            if (!manga.favorite) {
                addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
            }
        }
        return ids.size
    }
}
