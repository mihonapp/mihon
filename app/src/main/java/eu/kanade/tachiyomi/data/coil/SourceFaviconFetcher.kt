package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import okio.FileSystem
import org.json.JSONObject
import org.jsoup.Jsoup
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import java.io.IOException

/**
 * Coil model that resolves to a source's website favicon, so each source of a multi-source
 * extension gets a distinct icon instead of the shared extension app icon.
 */
data class SourceFavicon(val sourceId: Long)

class SourceFaviconKeyer : Keyer<SourceFavicon> {
    override fun key(data: SourceFavicon, options: Options) = "source-favicon;${data.sourceId}"
}

/**
 * Fetches the best icon a source's website advertises — web-manifest icon, `apple-touch-icon`,
 * `<link rel="icon">`, or `/favicon.ico` — largest first, via the source's own client so Cloudflare
 * clearance, User-Agent and cookies apply. Disk-cached, so each site is scraped only once.
 */
class SourceFaviconFetcher(
    private val sourceId: Long,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val sourceManager: SourceManager by injectLazy()
    private val diskCacheKey get() = options.diskCacheKey ?: "source-favicon;$sourceId"

    override suspend fun fetch(): FetchResult {
        readFromDiskCache()?.let { return it.toResult(DataSource.DISK) }

        val source = sourceManager.get(sourceId) as? HttpSource
            ?: throw IOException("Source $sourceId is not an HttpSource")
        val baseUrl = source.baseUrl.toHttpUrlOrNull()
            ?: throw IOException("Source $sourceId has no valid base URL")

        val response = findFaviconCandidates(source, baseUrl)
            .firstNotNullOfOrNull { downloadIcon(source, it) }
            ?: throw IOException("No favicon found for source $sourceId")
        return try {
            writeToDiskCache(response)?.toResult(DataSource.NETWORK)
                ?: SourceFetchResult(
                    ImageSource(response.body.source(), FileSystem.SYSTEM),
                    "image/*",
                    DataSource.NETWORK,
                )
        } catch (e: Exception) {
            response.close()
            throw e
        }
    }

    // Homepage-declared icons largest-first, always ending with /favicon.ico as a guaranteed fallback.
    private suspend fun findFaviconCandidates(source: HttpSource, baseUrl: HttpUrl): List<String> {
        val icons = mutableListOf<SizedIcon>()
        runCatching {
            val html = downloadText(source, baseUrl.toString()) ?: return@runCatching
            val document = Jsoup.parse(html, baseUrl.toString())
            document.select("link[rel~=(?i)icon]").forEach {
                icons += SizedIcon(it.absUrl("href").ifBlank { return@forEach }, parseSize(it.attr("sizes")))
            }
            document.selectFirst("link[rel=manifest]")?.absUrl("href")?.ifBlank { null }
                ?.let { icons += readManifestIcons(source, it) }
        }
        return (icons.sortedByDescending { it.size }.map { it.url } + baseUrl.faviconIco()).distinct()
    }

    private suspend fun readManifestIcons(source: HttpSource, manifestUrl: String): List<SizedIcon> {
        val body = downloadText(source, manifestUrl) ?: return emptyList()
        return runCatching {
            val array = JSONObject(body).optJSONArray("icons") ?: return emptyList()
            val base = manifestUrl.toHttpUrl()
            (0 until array.length()).mapNotNull { i ->
                val icon = array.optJSONObject(i) ?: return@mapNotNull null
                val resolved =
                    base.resolve(icon.optString("src").ifBlank { return@mapNotNull null }) ?: return@mapNotNull null
                SizedIcon(resolved.toString(), parseSize(icon.optString("sizes")))
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun downloadIcon(source: HttpSource, url: String): Response? {
        val response = runCatching { executeRequest(source, url) }.getOrNull() ?: return null
        val looksLikeImage = response.header("Content-Type").orEmpty().startsWith("image", ignoreCase = true) ||
            url.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS
        if (response.isSuccessful && looksLikeImage && response.body.contentLength() != 0L) return response
        response.close()
        return null
    }

    private suspend fun downloadText(source: HttpSource, url: String): String? =
        runCatching { executeRequest(source, url) }.getOrNull()?.use { if (it.isSuccessful) it.body.string() else null }

    private suspend fun executeRequest(source: HttpSource, url: String): Response =
        source.client.newCall(GET(url, source.headers)).await()

    // "48x48" / "any" / "16x16 32x32" → largest declared width.
    private fun parseSize(sizes: String): Int =
        sizes.split(Regex("\\s+")).mapNotNull { it.substringBefore('x').toIntOrNull() }.maxOrNull() ?: 0

    private fun readFromDiskCache(): DiskCache.Snapshot? =
        if (options.diskCachePolicy.readEnabled) imageLoader.diskCache?.openSnapshot(diskCacheKey) else null

    private fun writeToDiskCache(response: Response): DiskCache.Snapshot? {
        if (!options.diskCachePolicy.writeEnabled) return null
        val diskCache = imageLoader.diskCache ?: return null
        val editor = diskCache.openEditor(diskCacheKey) ?: return null
        return try {
            diskCache.fileSystem.write(editor.data) { response.body.source().readAll(this) }
            editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            runCatching { editor.abort() }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toResult(dataSource: DataSource) =
        SourceFetchResult(ImageSource(data, FileSystem.SYSTEM, diskCacheKey, closeable = this), "image/*", dataSource)

    private fun HttpUrl.faviconIco() = newBuilder().encodedPath("/favicon.ico").build().toString()

    private data class SizedIcon(val url: String, val size: Int)

    class Factory : Fetcher.Factory<SourceFavicon> {
        override fun create(data: SourceFavicon, options: Options, imageLoader: ImageLoader): Fetcher =
            SourceFaviconFetcher(data.sourceId, options, imageLoader)
    }
}

private val IMAGE_EXTENSIONS = setOf("ico", "png", "svg", "jpg", "jpeg", "gif", "webp", "bmp")
