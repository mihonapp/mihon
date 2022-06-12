package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.listOfStringsAdapter
import eu.kanade.data.toLong
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class MangaRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return handler.awaitOne { mangasQueries.getMangaById(id, mangaMapper) }
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return handler.subscribeToList { mangasQueries.getFavoriteBySourceId(sourceId, mangaMapper) }
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

    override suspend fun update(update: MangaUpdate): Boolean {
        return try {
            handler.await {
                mangasQueries.update(
                    source = update.source,
                    url = update.url,
                    artist = update.artist,
                    author = update.author,
                    description = update.description,
                    genre = update.genre?.let(listOfStringsAdapter::encode),
                    title = update.title,
                    status = update.status,
                    thumbnailUrl = update.thumbnailUrl,
                    favorite = update.favorite?.toLong(),
                    lastUpdate = update.lastUpdate,
                    initialized = update.initialized?.toLong(),
                    viewer = update.viewerFlags,
                    chapterFlags = update.chapterFlags,
                    coverLastModified = update.coverLastModified,
                    dateAdded = update.dateAdded,
                    mangaId = update.id,
                )
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }
}
