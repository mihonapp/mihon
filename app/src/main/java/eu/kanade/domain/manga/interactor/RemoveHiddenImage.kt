package eu.kanade.domain.manga.interactor

import tachiyomi.data.DatabaseHandler

class RemoveHiddenImage(
    private val handler: DatabaseHandler,
) {

    suspend fun await(id: Long) {
        handler.await {
            hidden_imagesQueries.deleteById(id)
        }
    }
}
