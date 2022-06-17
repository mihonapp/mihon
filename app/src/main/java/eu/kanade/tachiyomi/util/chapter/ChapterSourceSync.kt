package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.data.database.models.Chapter as DbChapter
import eu.kanade.tachiyomi.data.database.models.Manga as DbManga

/**
 * Helper method for syncing the list of chapters from the source with the ones from the database.
 *
 * @param rawSourceChapters a list of chapters from the source.
 * @param manga the manga of the chapters.
 * @param source the source of the chapters.
 * @return a pair of new insertions and deletions.
 */
suspend fun syncChaptersWithSource(
    rawSourceChapters: List<SChapter>,
    manga: DbManga,
    source: Source,
    syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
): Pair<List<DbChapter>, List<DbChapter>> {
    val domainManga = manga.toDomainManga() ?: return Pair(emptyList(), emptyList())
    val (added, deleted) = syncChaptersWithSource.await(rawSourceChapters, domainManga, source)

    val addedDbChapters = added.map { it.toDbChapter() }
    val deletedDbChapters = deleted.map { it.toDbChapter() }

    return Pair(addedDbChapters, deletedDbChapters)
}
