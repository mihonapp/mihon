package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class Download(
    val source: CatalogueSource,
    val mangaId: Long,
    val mangaTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val chapterUrl: String,
    val chapterScanlator: String?,
    val chapterDateUpload: Long,
    val chapterNumber: Double,
) {
    var pages: List<Page>? = null

    /**
     * Optional error details for the most recent failure.
     * Not persisted; used for UI display/copy.
     */
    @Transient
    var error: String? = null

    fun setError(e: Throwable) {
        val message = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
        error = buildString {
            append(message)
            append('\n')
            append(e.stackTraceToString())
        }
    }

    fun clearError() {
        error = null
    }

    val totalProgress: Int
        get() = pages?.sumOf(Page::progress) ?: 0

    val downloadedImages: Int
        get() = pages?.count { it.status == Page.State.Ready } ?: 0

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    val progressFlow = flow {
        if (pages == null) {
            emit(0)
            while (pages == null) {
                delay(50)
            }
        }

        val progressFlows = pages!!.map(Page::progressFlow)
        emitAll(combine(progressFlows) { it.average().toInt() })
    }
        .distinctUntilChanged()
        .debounce(50)

    val progress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).average().toInt()
        }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        fun from(
            manga: tachiyomi.domain.manga.model.Manga,
            chapter: tachiyomi.domain.chapter.model.Chapter,
            source: CatalogueSource,
        ): Download {
            return Download(
                source = source,
                mangaId = manga.id,
                mangaTitle = manga.title,
                chapterId = chapter.id,
                chapterName = chapter.name,
                chapterUrl = chapter.url,
                chapterScanlator = chapter.scanlator,
                chapterDateUpload = chapter.dateUpload,
                chapterNumber = chapter.chapterNumber,
            )
        }

        suspend fun fromChapterId(
            chapterId: Long,
            getChapter: GetChapter = Injekt.get(),
            getManga: GetManga = Injekt.get(),
            sourceManager: SourceManager = Injekt.get(),
        ): Download? {
            val chapter = getChapter.await(chapterId) ?: return null
            val manga = getManga.await(chapter.mangaId) ?: return null
            val source = sourceManager.get(manga.source) as? CatalogueSource ?: return null

            return from(manga = manga, chapter = chapter, source = source)
        }
    }

    fun toDomainChapter(): Chapter {
        return Chapter(
            id = chapterId,
            mangaId = mangaId,
            read = false,
            bookmark = false,
            lastPageRead = 0,
            dateFetch = 0,
            sourceOrder = 0,
            url = chapterUrl,
            name = chapterName,
            dateUpload = chapterDateUpload,
            chapterNumber = chapterNumber,
            scanlator = chapterScanlator,
            lastModifiedAt = 0,
            version = 1,
            locked = false,
        )
    }
}
