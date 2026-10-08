package tachiyomi.domain.manga.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject

data class MangaRemoteUpdate(
    val id: Long,
    val title: String?,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val updateStrategy: UpdateStrategy,
    val memo: JsonObject,
    val initialized: Boolean,
    val coverLastModified: Long?,
)
