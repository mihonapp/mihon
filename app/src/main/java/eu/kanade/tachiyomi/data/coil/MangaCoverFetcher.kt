package eu.kanade.tachiyomi.data.coil

import coil3.Extras
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.getOrDefault
import coil3.request.Options
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Call
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import java.io.File

/**
 * A [Fetcher] that fetches cover image for [Manga] object.
 *
 * It uses [Manga.thumbnailUrl] if custom cover is not set by the user.
 * Disk caching for library items is handled by [CoverCache], otherwise
 * handled by Coil's [DiskCache].
 *
 * Available request parameter:
 * - [USE_CUSTOM_COVER_KEY]: Use custom cover if set by user, default is true
 */
class MangaCoverFetcher(
    private val url: String?,
    private val isLibraryManga: Boolean,
    private val options: Options,
    private val coverFileLazy: Lazy<File?>,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val getSource: suspend () -> HttpSource?,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : BaseImageFetcher(
    url = url,
    isLibraryManga = isLibraryManga,
    options = options,
    imageFileLazy = coverFileLazy,
    diskCacheKeyLazy = diskCacheKeyLazy,
    getSource = getSource,
    callFactoryLazy = callFactoryLazy,
    imageLoader = imageLoader,
    name = "cover",
) {

    override suspend fun fetch(): FetchResult {
        // Use custom cover if exists, which only library manga can have
        val useCustomCover = options.extras.getOrDefault(USE_CUSTOM_COVER_KEY)
        if (useCustomCover && isLibraryManga) {
            val customCoverFile = customCoverFileLazy.value
            if (customCoverFile.exists()) {
                return fileLoader(customCoverFile)
            }
        }

        // diskCacheKey is thumbnail_url
        if (url == null) error("No cover specified")
        return when (getResourceType(url)) {
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> fileUriLoader(url)
            // Requests for the same cover at different sizes or through Manga and MangaCover would each download it
            Type.URL -> concurrentRequests.apply(diskCacheKey) { httpLoader() }
            null -> error("Invalid image")
        }
    }

    class MangaFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val coverCache: CoverCache,
        private val sourceManager: SourceManager,
    ) : Fetcher.Factory<Manga> {

        override fun create(data: Manga, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                url = data.thumbnailUrl,
                isLibraryManga = data.favorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.thumbnailUrl) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.id) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                getSource = { sourceManager.get(data.source) as? HttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    class MangaCoverFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
        private val coverCache: CoverCache,
        private val sourceManager: SourceManager,
    ) : Fetcher.Factory<MangaCover> {

        override fun create(data: MangaCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(
                url = data.url,
                isLibraryManga = data.isMangaFavorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.url) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.mangaId) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                getSource = { sourceManager.get(data.sourceId) as? HttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        val USE_CUSTOM_COVER_KEY = Extras.Key(true)
        private val concurrentRequests = DeDupeConcurrentRequests<FetchResult>()
    }
}
