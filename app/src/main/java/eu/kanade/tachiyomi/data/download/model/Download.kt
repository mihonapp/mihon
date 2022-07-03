package eu.kanade.tachiyomi.data.download.model

import eu.kanade.domain.chapter.interactor.GetChapter
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import rx.subjects.PublishSubject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class Download(
    val source: HttpSource,
    val manga: Manga,
    val chapter: Chapter,
    var pages: List<Page>? = null,
) {

    @Volatile
    @Transient
    var totalProgress: Int = 0

    @Volatile
    @Transient
    var downloadedImages: Int = 0

    @Volatile
    @Transient
    var status: State = State.NOT_DOWNLOADED
        set(status) {
            field = status
            statusSubject?.onNext(this)
            statusCallback?.invoke(this)
        }

    @Transient
    private var statusSubject: PublishSubject<Download>? = null

    @Transient
    private var statusCallback: ((Download) -> Unit)? = null

    val progress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).average().toInt()
        }

    fun setStatusSubject(subject: PublishSubject<Download>?) {
        statusSubject = subject
    }

    fun setStatusCallback(f: ((Download) -> Unit)?) {
        statusCallback = f
    }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromChapterId(
            chapterId: Long,
            getChapter: GetChapter = Injekt.get(),
            getManga: GetManga = Injekt.get(),
            sourceManager: SourceManager = Injekt.get(),
        ): Download? {
            val chapter = getChapter.await(chapterId) ?: return null
            val manga = getManga.await(chapter.mangaId) ?: return null
            val source = sourceManager.get(manga.source) as? HttpSource ?: return null

            return Download(source, manga, chapter.toDbChapter())
        }
    }
}
