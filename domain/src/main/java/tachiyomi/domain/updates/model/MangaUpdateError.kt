package tachiyomi.domain.updates.model

import kotlin.time.Instant

data class MangaUpdateError(
    val mangaId: Long,
    val errorMessage: String?,
    val timestamp: Instant,
)
