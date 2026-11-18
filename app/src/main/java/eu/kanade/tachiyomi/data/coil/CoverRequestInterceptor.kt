package eu.kanade.tachiyomi.data.coil

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover

/**
 * De-duplicates concurrent requests for the same cover at the same size. Lists like updates show an entry's cover on
 * every row, and requests that start together would each miss the memory cache and decode the cover again, so the
 * waiting ones are served from the memory cache once the first one finishes.
 */
class CoverRequestInterceptor : Interceptor {

    private val concurrentRequests = DeDupeConcurrentRequests<ImageResult> { it is SuccessResult }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is MangaCover && data !is Manga) return chain.proceed()

        return concurrentRequests.apply(data to chain.size) { chain.proceed() }
    }
}
