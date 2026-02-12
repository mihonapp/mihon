package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.HiddenImage
import tachiyomi.data.DatabaseHandler

class UpdateHiddenImageScope(
    private val handler: DatabaseHandler,
) {

    suspend fun await(id: Long, scope: HiddenImage.Scope) {
        handler.await {
            hidden_imagesQueries.updateScope(scope.value, id)
        }
    }
}
