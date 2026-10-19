package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter

data class ReaderChapter(private val loadedChapter: Chapter) {

    /**
     * The chapter as it currently stands, which drifts from [loadedChapter] as the reader records
     * progress against it. Equality stays on the chapter it was loaded with, so a chapter held by
     * the viewers keeps comparing equal to itself while being read.
     */
    var chapter: Chapter = loadedChapter
        private set

    val stateFlow = MutableStateFlow<State>(State.Wait)
    var state: State
        get() = stateFlow.value
        set(value) {
            stateFlow.value = value
        }

    val pages: List<ReaderPage>?
        get() = (state as? State.Loaded)?.pages

    var pageLoader: PageLoader? = null

    var requestedPage: Int = 0

    private var references = 0

    fun update(transform: (Chapter) -> Chapter) {
        chapter = transform(chapter)
    }

    fun ref() {
        references++
    }

    fun unref() {
        references--
        if (references == 0) {
            if (pageLoader != null) {
                logcat { "Recycling chapter ${chapter.name}" }
            }
            pageLoader?.recycle()
            pageLoader = null
            state = State.Wait
        }
    }

    sealed interface State {
        data object Wait : State
        data object Loading : State
        data class Error(val error: Throwable) : State
        data class Loaded(val pages: List<ReaderPage>) : State
    }
}
