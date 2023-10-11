package tachiyomi.data.updates

import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations

val updateWithRelationMapper: (
    Long,
    String,
    Long,
    String,
    String?,
    Boolean,
    Boolean,
    Long,
    Long,
    Boolean,
    String?,
    Long,
    Long,
    Long,
) -> UpdatesWithRelations = {
        mangaId, mangaTitle, chapterId, chapterName, scanlator, read, bookmark, lastPageRead, sourceId, favorite, thumbnailUrl, coverLastModified, _, dateFetch ->
    UpdatesWithRelations(
        mangaId = mangaId,
        mangaTitle = mangaTitle,
        chapterId = chapterId,
        chapterName = chapterName,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
