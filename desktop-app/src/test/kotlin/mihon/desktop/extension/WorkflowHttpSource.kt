package mihon.desktop.extension

import eu.kanade.tachiyomi.network.NetworkHelper
import mihon.extension.source.WindowsHttpSource
import mihon.extension.source.WindowsImageSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import okhttp3.Request

/** Synthetic local HTTP fixture, executed from a real packaged JAR inside AppContainer. */
class WorkflowHttpSource : WindowsHttpSource, WindowsImageSource {
    override val id = 99001L
    override val name = "Workflow fixture"
    override val lang = "en"
    override val baseUrl = "http://127.0.0.1"
    private fun bytes(url: String): ByteArray = NetworkHelper().client.newCall(Request.Builder().url(url).build())
        .execute().use { response ->
            check(response.isSuccessful)
            requireNotNull(response.body).bytes()
        }
    private fun text(url: String) = bytes(url).toString(Charsets.UTF_8)
    override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage =
        MangasPage(listOf(SManga(url = "$query/manga", title = text("$query/search"))), false)
    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
    override suspend fun getMangaDetails(manga: SManga) = manga.copy(author = text(manga.url), initialized = true)
    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        (1..text("${manga.url}/chapters").toInt()).map {
            SChapter(url = "${manga.url}/$it", name = "Chapter $it", chapterNumber = it.toFloat())
        }
    override suspend fun getPageList(chapter: SChapter): List<Page> =
        text("${chapter.url}/pages").lines().filter {
            it.isNotBlank()
        }.mapIndexed { index, url -> Page(index, url, url) }
    override suspend fun getImage(page: Page) = bytes(requireNotNull(page.imageUrl))
}
