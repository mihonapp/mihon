package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

@Suppress("OverridingDeprecatedMember")
class StubSource(private val sourceData: SourceData) : Source {

    override val id: Long = sourceData.id

    override val name: String = sourceData.name.ifBlank { id.toString() }

    override val lang: String = sourceData.lang

    override suspend fun getMangaDetails(manga: SManga): SManga {
        throw SourceNotInstalledException()
    }

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.error(SourceNotInstalledException())
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        throw SourceNotInstalledException()
    }

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getChapterList"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.error(SourceNotInstalledException())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        throw SourceNotInstalledException()
    }

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.error(SourceNotInstalledException())
    }

    override fun toString(): String {
        return if (sourceData.isMissingInfo.not()) "$name (${lang.uppercase()})" else id.toString()
    }
}

class SourceNotInstalledException : Exception()
