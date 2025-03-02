package tachiyomi.data.failed

import tachiyomi.domain.failed.model.FailedUpdate

val failedUpdatesMapper: (Long, String, Long) -> FailedUpdate = { mangaId, errorMessage, isOnline ->
    FailedUpdate(
        mangaId = mangaId,
        errorMessage = errorMessage,
        isOnline = isOnline,
    )
}
