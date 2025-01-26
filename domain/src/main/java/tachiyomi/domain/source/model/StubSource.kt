package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

class StubSource(
    override val id: Long,
    override val name: String,
    override val language: String,
) : Source {

    private val isInvalid: Boolean = name.isBlank() || language.isBlank()

    override val hasSearchFilters: Boolean get() = throw SourceNotInstalledException()

    override val hasLatestListing: Boolean get() = throw SourceNotInstalledException()

    override suspend fun getSearchFilters(): FilterList = throw SourceNotInstalledException()

    override suspend fun getDefaultMangaList(page: Int): MangasPage = throw SourceNotInstalledException()

    override suspend fun getLatestMangaList(page: Int): MangasPage = throw SourceNotInstalledException()

    override suspend fun getMangaList(query: String, filters: FilterList, page: Int): MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getMangaDetails(
        manga: SManga,
        updateManga: Boolean,
        fetchChapters: Boolean,
    ): Pair<SManga, List<SChapter>> = throw SourceNotInstalledException()

    override suspend fun getPageList(chapter: SChapter): List<Page> = throw SourceNotInstalledException()

    override fun toString(): String = if (!isInvalid) "$name (${language.uppercase()})" else id.toString()

    companion object {
        fun from(source: Source): StubSource {
            return StubSource(id = source.id, name = source.name, language = source.language)
        }
    }
}

class SourceNotInstalledException : Exception()
