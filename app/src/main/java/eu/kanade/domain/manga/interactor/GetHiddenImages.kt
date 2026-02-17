package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.HiddenImage
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler

class GetHiddenImages(
    private val handler: DatabaseHandler,
) {

    suspend fun await(mangaId: Long): List<HiddenImage> {
        return handler.awaitList {
            hidden_imagesQueries.getByMangaId(mangaId, ::hiddenImageMapper)
        }
    }

    fun subscribe(mangaId: Long): Flow<List<HiddenImage>> {
        return handler.subscribeToList {
            hidden_imagesQueries.getByMangaId(mangaId, ::hiddenImageMapper)
        }
    }
}

internal fun hiddenImageMapper(
    id: Long,
    mangaId: Long,
    imageSha256: String?,
    imageDhash: String?,
    previewImage: ByteArray?,
    scope: Long,
    createdAt: Long,
): HiddenImage {
    return HiddenImage(
        id = id,
        mangaId = mangaId,
        imageSha256 = imageSha256,
        imageDhash = imageDhash,
        previewImage = previewImage,
        scope = when (scope) {
            HiddenImage.Scope.START.value -> HiddenImage.Scope.START
            HiddenImage.Scope.END.value -> HiddenImage.Scope.END
            else -> HiddenImage.Scope.ANY
        },
        createdAt = createdAt,
    )
}
