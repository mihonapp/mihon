package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.HiddenImage
import eu.kanade.tachiyomi.ui.reader.hiddenimage.DefaultHiddenImageMatcher
import eu.kanade.tachiyomi.ui.reader.hiddenimage.HiddenImageMatcher
import eu.kanade.tachiyomi.ui.reader.hiddenimage.HiddenImageSignature
import tachiyomi.data.DatabaseHandler

class SetHiddenImage(
    private val handler: DatabaseHandler,
    private val matcher: HiddenImageMatcher = DefaultHiddenImageMatcher(),
) {

    suspend fun await(
        mangaId: Long,
        signature: HiddenImageSignature,
        scope: HiddenImage.Scope,
    ) {
        handler.await(inTransaction = true) {
            val current = handler.awaitList {
                hidden_imagesQueries.getByMangaId(mangaId, ::hiddenImageMapper)
            }

            val existing = matcher.findMatch(current, signature)
            if (existing != null) {
                val targetScope = when {
                    existing.scope == HiddenImage.Scope.ANY || scope == HiddenImage.Scope.ANY -> HiddenImage.Scope.ANY
                    existing.scope != scope -> HiddenImage.Scope.ANY
                    else -> existing.scope
                }
                hidden_imagesQueries.update(
                    mangaId = mangaId,
                    imageUrl = signature.imageUrl,
                    normalizedImageUrl = signature.normalizedImageUrl,
                    imageSha256 = signature.imageSha256,
                    imageDhash = signature.imageDhash,
                    scope = targetScope.value,
                    id = existing.id,
                )
                return@await
            }

            hidden_imagesQueries.insert(
                mangaId = mangaId,
                imageUrl = signature.imageUrl,
                normalizedImageUrl = signature.normalizedImageUrl,
                imageSha256 = signature.imageSha256,
                imageDhash = signature.imageDhash,
                scope = scope.value,
                createdAt = System.currentTimeMillis(),
            )
        }
    }
}
