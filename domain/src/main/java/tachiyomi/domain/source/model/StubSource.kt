package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : Source {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override suspend fun getMangaDetails(manga: SManga): SManga {
        throw SourceNotInstalledException()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.error(SourceNotInstalledException())
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        throw SourceNotInstalledException()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getChapterList"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.error(SourceNotInstalledException())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        throw SourceNotInstalledException()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.error(SourceNotInstalledException())
    }

    override fun toString(): String {
        return if (isInvalid.not()) "$name (${lang.uppercase()})" else id.toString()
    }
}

class SourceNotInstalledException : Exception()
