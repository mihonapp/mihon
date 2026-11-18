package eu.kanade.tachiyomi.data.backup.create

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toJsonObject
import mihon.domain.source.model.ChapterOrigin
import mihon.domain.source.model.LinkedSourceManga
import mihon.domain.source.model.assignedSourceId
import mihon.domain.source.model.chapterOrigins
import mihon.domain.source.model.linkedSources
import mihon.domain.source.model.withoutChapterOrigins
import mihon.domain.source.model.withoutLinkedSources
import java.io.IOException

/** Export real provider entries without changing the live library or its download identities. */
object OfficialBackupConverter {
    fun convert(manga: BackupManga, includeChapters: Boolean = true): List<BackupManga> {
        val memo = manga.memo.toJsonObject()
        val providers = listOf(LinkedSourceManga(manga.source, manga.url, manga.title, memo)) +
            memo.linkedSources().filterNot { it.sourceId == manga.source }
        val chapters = manga.chapters.map { chapter ->
            val chapterMemo = chapter.memo.toJsonObject()
            val origins = chapterMemo.chapterOrigins().ifEmpty {
                if (chapter.url.startsWith("mihon://")) {
                    throw IOException(
                        "Cannot export ${manga.title}: a chapter has no original source URL. Keep a normal backup and refresh its linked sources.",
                    )
                }
                listOf(
                    ChapterOrigin(
                        manga.source,
                        chapter.url,
                        chapter.name,
                        chapter.scanlator,
                        chapter.dateUpload,
                        chapter.chapterNumber,
                        chapterMemo,
                    ),
                )
            }
            val selected = chapterMemo.assignedSourceId()?.let { assigned ->
                origins.firstOrNull { it.sourceId == assigned }
                    ?: throw IOException(
                        "Cannot export ${manga.title}: the assigned chapter edition is missing. Keep a normal backup and refresh its linked sources.",
                    )
            } ?: origins.first()
            if (origins.any { origin -> providers.none { it.sourceId == origin.sourceId } }) {
                throw IOException(
                    "Cannot export ${manga.title}: a retained chapter belongs to an unlinked source. Relink that title or keep a normal backup.",
                )
            }
            if (origins.any { it.url.startsWith("mihon://") }) {
                throw IOException(
                    "Cannot export ${manga.title}: an original source URL is missing. Keep a normal backup and refresh its linked sources.",
                )
            }
            Edition(chapter, origins, selected)
        }
        val historyUrls = chapters.associate { it.chapter.url to it.selected }
        return providers.map { provider ->
            val providerChapters = chapters.flatMap { edition ->
                edition.origins.filter { it.sourceId == provider.sourceId }.map { origin ->
                    edition.chapter.forOrigin(origin, edition.selected == origin)
                }
            }
            manga.forProvider(provider).also { entry ->
                entry.chapters = if (includeChapters) providerChapters else emptyList()
                entry.history = manga.history.mapNotNull { history ->
                    val origin = historyUrls[history.url] ?: return@mapNotNull null
                    if (origin.sourceId == provider.sourceId) history.copy(url = origin.url) else null
                }
            }
        }
    }

    private data class Edition(
        val chapter: BackupChapter,
        val origins: List<ChapterOrigin>,
        val selected: ChapterOrigin,
    )

    private fun BackupChapter.forOrigin(origin: ChapterOrigin, selected: Boolean) = BackupChapter(
        url = origin.url,
        name = origin.name,
        scanlator = origin.scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = if (selected) lastPageRead else 0,
        dateFetch = dateFetch,
        dateUpload = origin.dateUpload,
        chapterNumber = origin.chapterNumber,
        sourceOrder = sourceOrder,
        memo = origin.memo.withoutChapterOrigins().toByteArray(),
    )

    private fun BackupManga.forProvider(provider: LinkedSourceManga) = BackupManga(
        source = provider.sourceId,
        url = provider.url,
        title = provider.title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = if (provider.sourceId == source) thumbnailUrl else null,
        dateAdded = dateAdded,
        viewer = viewer,
        categories = categories,
        tracking = tracking,
        favorite = favorite,
        chapterFlags = chapterFlags,
        viewer_flags = viewer_flags,
        updateStrategy = updateStrategy,
        favoriteModifiedAt = favoriteModifiedAt,
        excludedScanlators = excludedScanlators,
        notes = notes,
        initialized = provider.sourceId == source && initialized,
        memo = provider.memo.withoutLinkedSources().toByteArray(),
        nextUpdate = nextUpdate,
    )
}
