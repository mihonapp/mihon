package eu.kanade.domain.manga.interactor

import tachiyomi.data.DatabaseHandler

class ClearHiddenImages(
    private val handler: DatabaseHandler,
) {

    suspend fun await() {
        handler.await {
            hidden_imagesQueries.deleteAll()
        }
    }
}
