package eu.kanade.domain.updates.model

import eu.kanade.domain.manga.model.MangaCover

data class UpdatesWithRelations(
    val mangaId: Long,
    val mangaTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: MangaCover,
)
