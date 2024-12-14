package tachiyomi.domain.failed.model

data class FailedUpdate(
    val mangaId: Long,
    val errorMessage: String,
    val isOnline: Long,
)
