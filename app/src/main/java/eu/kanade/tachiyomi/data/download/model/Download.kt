package eu.kanade.tachiyomi.data.download.model

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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class Download(
    val source: HttpSource,
    val manga: Manga,
    val chapter: Chapter,
) {
    var pages: List<Page>? = null

    /**
     * Progress for full chapter downloads (0-100).
     * This is used when downloading complete chapter archives instead of individual pages.
     */
    @Transient
    private val _fullChapterProgressFlow = MutableStateFlow(0)

    /**
     * Indicates if this download is using full chapter download mode.
     */
    @Transient
    var isFullChapterDownload: Boolean = false

    val totalProgress: Int
        get() = if (isFullChapterDownload) {
            _fullChapterProgressFlow.value
        } else {
            pages?.sumOf(Page::progress) ?: 0
        }

    val downloadedImages: Int
        get() = if (isFullChapterDownload) {
            if (_fullChapterProgressFlow.value >= 100) 1 else 0
        } else {
            pages?.count { it.status == Page.State.Ready } ?: 0
        }

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    val progressFlow = flow {
        if (isFullChapterDownload) {
            emitAll(_fullChapterProgressFlow)
        } else {
            if (pages == null) {
                emit(0)
                while (pages == null) {
                    delay(50)
                }
            }

            val progressFlows = pages!!.map(Page::progressFlow)
            emitAll(combine(progressFlows) { it.average().toInt() })
        }
    }
        .distinctUntilChanged()
        .debounce(50)

    val progress: Int
        get() = if (isFullChapterDownload) {
            _fullChapterProgressFlow.value
        } else {
            val pages = pages ?: return 0
            pages.map(Page::progress).average().toInt()
        }

    /**
     * Updates the progress for full chapter downloads.
     * This should only be called when isFullChapterDownload is true.
     *
     * @param progress the progress value (0-100)
     */
    fun updateFullChapterProgress(progress: Int) {
        if (isFullChapterDownload) {
            _fullChapterProgressFlow.value = progress.coerceIn(0, 100)
        }
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

            return Download(source, manga, chapter)
        }
    }
}
