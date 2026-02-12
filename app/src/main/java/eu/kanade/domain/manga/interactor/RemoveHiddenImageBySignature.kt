package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.ui.reader.hiddenimage.DefaultHiddenImageMatcher
import eu.kanade.tachiyomi.ui.reader.hiddenimage.HiddenImageMatcher
import eu.kanade.tachiyomi.ui.reader.hiddenimage.HiddenImageSignature
import tachiyomi.data.DatabaseHandler

class RemoveHiddenImageBySignature(
    private val handler: DatabaseHandler,
    private val matcher: HiddenImageMatcher = DefaultHiddenImageMatcher(),
) {

    suspend fun await(mangaId: Long, signature: HiddenImageSignature) {
        handler.await(inTransaction = true) {
            val current = handler.awaitList {
                hidden_imagesQueries.getByMangaId(mangaId, ::hiddenImageMapper)
            }
            val existing = matcher.findMatch(current, signature) ?: return@await
            hidden_imagesQueries.deleteById(existing.id)
        }
    }
}
