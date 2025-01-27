@file:Suppress("UNUSED", "UnusedReceiverParameter", "DEPRECATION")

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.awaitSingle
import mihonx.source.model.UserAgentType
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/**
 * A simple implementation for sources from a website.
 */
abstract class HttpSource : CatalogueSource {

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    /**
     * Version id used to generate the source id. If the site completely changes and urls are
     * incompatible, you may increase this value and it'll be considered as a new source.
     */
    open val versionId: Int = 1

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: sourcename/language/versionId
     * Note the generated id sets the sign bit to 0.
     */
    override val id: Long by lazy { generateId(name, language, versionId) }

    /**
     * Generates a unique ID for the source based on the provided [name], [lang] and
     * [versionId]. It will use the first 16 characters (64 bits) of the MD5 of the string
     * `"${name.lowercase()}/$lang/$versionId"`.
     *
     * Note: the generated ID sets the sign bit to `0`.
     *
     * Can be used to generate outdated IDs, such as when the source name or language
     * needs to be changed but migrations can be avoided.
     *
     * @since extensions-lib 1.5
     * @param name [String] the name of the source
     * @param lang [String] the language of the source
     * @param versionId [Int] the version ID of the source
     * @return a unique ID for the source
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Network service.
     */
    protected val network: NetworkHelper by injectLazy()

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient get() = network.client

    /**
     * Type of UserAgent a source needs
     */
    protected open val supportedUserAgentType: UserAgentType = UserAgentType.Universal

    /**
     * @since extensions-lib 1.6
     */
    // TODO(antsy): Implement
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun getUserAgent(): String = network.defaultUserAgentProvider()

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("User-Agent", getUserAgent())
    }

    /**
     * Returns the image url for the provided [page]. The function is only called if [Page.imageUrl] is null.
     *
     * @param page the page whose source image has to be fetched.
     */
    open suspend fun getImageUrl(page: Page): String = fetchImageUrl(page).awaitSingle()

    /**
     * Returns the request for getting the source image. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    open suspend fun getImage(page: Page): Response {
        return client.newCachelessCallWithProgress(imageRequest(page), page).awaitSuccess()
    }

    /**
     * Assigns the url of the chapter without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the chapter.
     */
    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Assigns the url of the manga without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the manga.
     */
    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Returns the url of the given string without the scheme and domain.
     *
     * @param orig the full url.
     */
    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig.replace(" ", "%20"))
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    /**
     * Returns the url of the provided manga
     *
     * @since extensions-lib 1.4
     * @param manga the manga
     * @return url of the manga
     */
    open fun getMangaUrl(manga: SManga): String {
        return mangaDetailsRequest(manga).url.toString()
    }

    /**
     * Returns the url of the provided chapter
     *
     * @since extensions-lib 1.4
     * @param chapter the chapter
     * @return url of the chapter
     */
    open fun getChapterUrl(chapter: SChapter): String {
        return pageListRequest(chapter).url.toString()
    }

    override fun toString(): String = "$name (${language.uppercase()})"

    @Deprecated("Use the new suspend API instead", replaceWith = ReplaceWith("getDefaultMangaList"))
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    /**
     * Returns the request for the popular manga given the page.
     *
     * @param page the page number to retrieve.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getDefaultMangaList]")
    open fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getDefaultMangaList]")
    open fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Deprecated("Use the new suspend API instead", replaceWith = ReplaceWith("getLatestMangaList"))
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    /**
     * Returns the request for latest manga given the page.
     *
     * @param page the page number to retrieve.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getLatestMangaList]")
    open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getLatestMangaList]")
    open fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Deprecated("Use the new suspend API instead", replaceWith = ReplaceWith("getMangaList"))
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return Observable.defer {
            try {
                client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
            } catch (e: NoClassDefFoundError) {
                // RxJava doesn't handle Errors, which tends to happen during global searches
                // if an old extension using non-existent classes is still around
                throw RuntimeException(e)
            }
        }
            .map { response ->
                searchMangaParse(response)
            }
    }

    /**
     * Returns the request for the search manga given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getMangaList]")
    open fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = throw UnsupportedOperationException()

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getMangaList]")
    open fun searchMangaParse(response: Response): MangasPage = throw RuntimeException("Stub!")

    @Deprecated("Use the new suspend API instead", replaceWith = ReplaceWith("getMangaDetails(manga, true, false)"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    /**
     * Returns the request for the details of a manga. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param manga the manga to be updated.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getMangaDetails]")
    open fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    /**
     * Parses the response from the site and returns the details of a manga.
     *
     * @param response the response from the site.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getMangaDetails]")
    open fun mangaDetailsParse(response: Response): SManga = throw RuntimeException("Stub!")

    @Deprecated("Use the new suspend API instead", replaceWith = ReplaceWith("getMangaDetails(manga, false, true)"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response)
            }
    }

    /**
     * Returns the request for updating the chapter list. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param manga the manga to look for chapters.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getMangaDetails]")
    open fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getMangaDetails]")
    open fun chapterListParse(response: Response): List<SChapter> = throw RuntimeException("Stub!")

    @Deprecated("Use the new suspend API instead", ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response)
            }
    }

    /**
     * Returns the request for getting the page list. Override only if it's needed to override the
     * url, send different headers or request method like POST.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getPageList]")
    protected open fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getPageList]")
    protected open fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param page the page whose source image has to be fetched.
     */
    @Deprecated("Use the new suspend API instead", ReplaceWith("getImageUrl"))
    open fun fetchImageUrl(page: Page): Observable<String> {
        return client.newCall(imageUrlRequest(page))
            .asObservableSuccess()
            .map { imageUrlParse(it) }
    }

    /**
     * Returns the request for getting the url to the source image. Override only if it's needed to
     * override the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getImageUrl]")
    protected open fun imageUrlRequest(page: Page): Request {
        return GET(page.url, headers)
    }

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Directly implement inside [getImageUrl]")
    protected open fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    /**
     * Called before inserting a new chapter into database. Use it if you need to override chapter
     * fields, like the title or the chapter number. Do not change anything to [manga].
     *
     * @param chapter the chapter to be added.
     * @param manga the manga of the chapter.
     */
    @Deprecated("All these modification should be done when constructing the chapter")
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}
}
