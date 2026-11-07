package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import eu.kanade.tachiyomi.data.cache.ThumbnailCache
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Call
import tachiyomi.domain.chapter.model.ChapterThumbnail
import tachiyomi.domain.source.service.SourceManager
import java.io.File

/**
 * A [Fetcher] that fetches thumbnail image for [Chapter] object.
 *
 * It uses [Chapter.thumbnailUrl].
 * Disk caching for library items is handled by [ThumbnailCache], otherwise
 * handled by Coil's [DiskCache].
 */
class ChapterThumbnailFetcher(
    private val url: String?,
    private val isLibraryManga: Boolean,
    private val options: Options,
    private val thumbnailFileLazy: Lazy<File?>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val getSource: suspend () -> HttpSource?,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : BaseImageFetcher(
    url = url,
    isLibraryManga = isLibraryManga,
    options = options,
    imageFileLazy = thumbnailFileLazy,
    diskCacheKeyLazy = diskCacheKeyLazy,
    getSource = getSource,
    callFactoryLazy = callFactoryLazy,
    imageLoader = imageLoader,
    name = "thumbnail",
) {

    override suspend fun fetch(): FetchResult? {
        // diskCacheKey is thumbnail_url
        if (url == null) error("No cover specified")
        return when (getResourceType(url)) {
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> fileUriLoader(url)
            // Requests for the same cover at different sizes or through ChapterThumbnail would each download it
            Type.URL -> concurrentRequests.apply(diskCacheKey) { httpLoader() }
            null -> error("Invalid image")
        }
    }

    class ChapterThumbnailFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val thumbnailCache: ThumbnailCache,
        private val sourceManager: SourceManager,
    ) : Fetcher.Factory<ChapterThumbnail> {
        override fun create(data: ChapterThumbnail, options: Options, imageLoader: ImageLoader): Fetcher? {
            return ChapterThumbnailFetcher(
                url = data.url,
                isLibraryManga = data.isMangaFavorite,
                options = options,
                thumbnailFileLazy = lazy { thumbnailCache.getCoverFile(data.url) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                getSource = { sourceManager.get(data.sourceId) as? HttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        private val concurrentRequests = DeDupeConcurrentRequests<FetchResult>()
    }
}
