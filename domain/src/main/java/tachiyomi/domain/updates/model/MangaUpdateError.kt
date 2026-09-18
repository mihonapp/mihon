package tachiyomi.domain.updates.model

data class MangaUpdateError(
    val mangaId: Long,
    val errorMessage: String?,
    val timestamp: Long,
)
