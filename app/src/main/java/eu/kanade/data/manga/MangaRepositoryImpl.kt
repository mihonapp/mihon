package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.listOfStringsAdapter
import eu.kanade.data.updateStrategyAdapter
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toLong
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class MangaRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return handler.awaitOne { mangasQueries.getMangaById(id, mangaMapper) }
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return handler.subscribeToOne { mangasQueries.getMangaById(id, mangaMapper) }
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return handler.awaitOneOrNull { mangasQueries.getMangaByUrlAndSource(url, sourceId, mangaMapper) }
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return handler.subscribeToOneOrNull { mangasQueries.getMangaByUrlAndSource(url, sourceId, mangaMapper) }
    }

    override suspend fun getFavorites(): List<Manga> {
        return handler.awaitList { mangasQueries.getFavorites(mangaMapper) }
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        return handler.awaitList { libraryViewQueries.library(libraryManga) }
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return handler.subscribeToList { libraryViewQueries.library(libraryManga) }
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return handler.subscribeToList { mangasQueries.getFavoriteBySourceId(sourceId, mangaMapper) }
    }

    override suspend fun getDuplicateLibraryManga(title: String, sourceId: Long): Manga? {
        return handler.awaitOneOrNull {
            mangasQueries.getDuplicateLibraryManga(title, sourceId, mangaMapper)
        }
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            handler.await { mangasQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.map { categoryId ->
                mangas_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    override suspend fun insert(manga: Manga): Long? {
        return handler.awaitOneOrNull {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun update(update: MangaUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdate(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg mangaUpdates: MangaUpdate) {
        handler.await(inTransaction = true) {
            mangaUpdates.forEach { value ->
                mangasQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(listOfStringsAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite?.toLong(),
                    lastUpdate = value.lastUpdate,
                    initialized = value.initialized?.toLong(),
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    updateStrategy = value.updateStrategy?.let(updateStrategyAdapter::encode),
                )
            }
        }
    }
}
