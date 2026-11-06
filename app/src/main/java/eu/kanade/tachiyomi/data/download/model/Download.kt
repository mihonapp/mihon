package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.milliseconds

data class Download(
    val source: HttpSource,
    val manga: Manga,
    val chapter: Chapter,
) {
    @Transient
    private val _pagesFlow = MutableStateFlow<List<Page>?>(null)

    @Transient
    val pagesFlow = _pagesFlow.asStateFlow()
    var pages: List<Page>?
        get() = _pagesFlow.value
        set(pages) {
            _pagesFlow.value = pages
        }

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
    val progressFlow = _pagesFlow
        .flatMapLatest { pages ->
            if (pages == null) {
                flowOf(0)
            } else {
                combine(pages.map(Page::progressFlow)) { it.average().toInt() }
            }
        }
        .distinctUntilChanged()
        .debounce(50.milliseconds)

    val progress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).average().toInt()
        }

    /**
     * Latest download attempt. Collected whether or not the queue is showing it.
     */
    data class Failure(
        val attempts: Int,
        val message: String?,
    )

    @Transient
    private val _failureFlow = MutableStateFlow<Failure?>(null)

    @Transient
    val failureFlow = _failureFlow.asStateFlow()

    fun beginAttempt() {
        _failureFlow.update { current ->
            Failure(attempts = (current?.attempts ?: 0) + 1, message = current?.message)
        }
    }

    fun undoAttempt() {
        _failureFlow.update { current ->
            if (current == null) return@update null
            val attempts = current.attempts - 1
            when {
                attempts <= 0 && current.message == null -> null
                attempts <= 0 -> current.copy(attempts = 1)
                else -> current.copy(attempts = attempts)
            }
        }
    }

    fun recordFailure(message: String?) {
        val text = message?.trim()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(120)
        _failureFlow.update { current ->
            Failure(
                attempts = (current?.attempts ?: 0).coerceAtLeast(1),
                message = text ?: current?.message,
            )
        }
    }

    fun clearFailure() {
        _failureFlow.value = null
    }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }
}
