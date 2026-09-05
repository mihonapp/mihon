package mihon.extension.source

import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga

interface WindowsSource {
    val id: Long
    val name: String
    val lang: String
    val supportsLatest: Boolean
        get() = true
}

interface WindowsCatalogueSource : WindowsSource {
    suspend fun getPopularManga(page: Int): MangasPage
    suspend fun getLatestUpdates(page: Int): MangasPage
    suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage
    suspend fun getMangaDetails(manga: SManga): SManga
    suspend fun getChapterList(manga: SManga): List<SChapter>
    suspend fun getPageList(chapter: SChapter): List<Page>
    fun getFilterList(): FilterList = FilterList()
}

interface WindowsHttpSource : WindowsCatalogueSource {
    val baseUrl: String
    val headers: Map<String, String>
        get() = emptyMap()
    val rateLimitMillis: Long
        get() = 0L
}
