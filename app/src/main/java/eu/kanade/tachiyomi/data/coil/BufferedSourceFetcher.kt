package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.BufferedSource

class BufferedSourceFetcher(
    private val data: BufferedSource,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                source = data,
                fileSystem = options.fileSystem,
            ),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<BufferedSource> {

        override fun create(
            data: BufferedSource,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return BufferedSourceFetcher(data, options)
        }
    }
}
