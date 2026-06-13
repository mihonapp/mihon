package mihon.domain.source.interactor

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import logcat.LogPriority
import mihon.domain.source.models.RemoteMangaUpdate
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import java.time.Instant

class UpdateMangaFromRemote(
    private val sourceManager: SourceManager,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val coverCache: CoverCache,
    private val libraryPreferences: LibraryPreferences,
    private val downloadManager: DownloadManager,
) {
    suspend operator fun invoke(
        manga: Manga,
        fetchDetails: Boolean = false,
        fetchChapters: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): Result<RemoteMangaUpdate> {
        val source = sourceManager.getOrStub(manga.source)
        return invoke(
            source = source,
            manga = manga,
            fetchDetails = fetchDetails,
            fetchChapters = fetchChapters,
            manualFetch = manualFetch,
        )
    }

    suspend operator fun invoke(
        source: Source,
        manga: Manga,
        fetchDetails: Boolean = false,
        fetchChapters: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): Result<RemoteMangaUpdate> {
        return try {
            val chapters = chapterRepository.getChapterByMangaId(manga.id)
            val update = withIOContext {
                source.getMangaUpdate(
                    manga = manga.toSManga(),
                    chapters = chapters.map(Chapter::toSChapter),
                    fetchDetails = fetchDetails,
                    fetchChapters = fetchChapters,
                )
            }
            awaitUpdateFromSource(manga, update.manga, manualFetch)
            val newChapters = syncChaptersWithSource.await(
                rawSourceChapters = update.chapters,
                manga = manga,
                source = source,
                manualFetch = manualFetch,
                fetchWindow = fetchWindow,
            )
            val updatedManga = mangaRepository.getMangaById(manga.id)

            Result.success(RemoteMangaUpdate(manga = updatedManga, newChapters = newChapters))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.failure(e)
        }
    }

    private suspend fun awaitUpdateFromSource(
        localManga: Manga,
        remoteManga: SManga,
        manualFetch: Boolean,
    ): Boolean {
        val remoteTitle = try {
            remoteManga.title
        } catch (_: UninitializedPropertyAccessException) {
            ""
        }

        // if the manga isn't a favorite (or 'update titles' preference is enabled), set its title from source and update in db
        val title =
            if (remoteTitle.isNotEmpty() && (!localManga.favorite || libraryPreferences.updateMangaTitles.get())) {
                remoteTitle
            } else {
                null
            }

        val coverLastModified = when {
            // Never refresh covers if the url is empty to avoid "losing" existing covers
            remoteManga.thumbnail_url.isNullOrEmpty() -> null
            !manualFetch && localManga.thumbnailUrl == remoteManga.thumbnail_url -> null
            localManga.isLocal() -> Instant.now().toEpochMilli()
            localManga.hasCustomCover(coverCache) -> {
                coverCache.deleteFromCache(localManga, false)
                null
            }
            else -> {
                coverCache.deleteFromCache(localManga, false)
                Instant.now().toEpochMilli()
            }
        }

        val thumbnailUrl = remoteManga.thumbnail_url?.takeIf { it.isNotEmpty() }

        val success = mangaRepository.update(
            MangaUpdate(
                id = localManga.id,
                title = title,
                coverLastModified = coverLastModified,
                author = remoteManga.author,
                artist = remoteManga.artist,
                description = remoteManga.description,
                genre = remoteManga.getGenres(),
                thumbnailUrl = thumbnailUrl,
                status = remoteManga.status.toLong(),
                updateStrategy = remoteManga.update_strategy,
                initialized = true,
            ),
        )
        if (success && title != null) {
            downloadManager.renameManga(localManga, title)
        }
        return success
    }
}
