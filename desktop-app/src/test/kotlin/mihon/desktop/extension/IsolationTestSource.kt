package mihon.desktop.extension

import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.File

open class IsolationTestSource(override val id: Long) : WindowsCatalogueSource {
    override val name = "Isolation $id"
    override val lang = "en"
    override suspend fun getPopularManga(page: Int): MangasPage {
        File("own.txt").writeText(id.toString())
        return MangasPage(listOf(SManga(url = "/$id", title = id.toString())), false)
    }
    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
    override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val result = runCatching { File(query).writeText("escaped") }.fold({ "escaped" }, { "denied" })
        return MangasPage(listOf(SManga(url = "/result", title = result)), false)
    }
    override suspend fun getMangaDetails(manga: SManga) = manga
    override suspend fun getChapterList(manga: SManga) = emptyList<SChapter>()
    override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
}
class IsolationSourceOne : IsolationTestSource(901)
class IsolationSourceTwo : IsolationTestSource(902)
