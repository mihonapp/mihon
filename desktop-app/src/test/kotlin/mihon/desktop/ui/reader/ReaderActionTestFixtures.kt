package mihon.desktop.ui.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderState

internal class TestReaderSession(initial: ReaderState) : ReaderSession {
    val mutable = MutableStateFlow(initial)
    override val state: StateFlow<ReaderState> = mutable
    val actions = mutableListOf<ReaderAction>()
    var retryRequests = 0
    var closeRequests = 0
    var cancelRequests = 0
    var closeFailure: Throwable? = null

    override suspend fun open(chapterId: Long) = Unit

    override fun dispatch(action: ReaderAction) {
        actions += action
        mutable.value = runCatching { mutable.value.reduce(action) }.getOrDefault(mutable.value)
    }

    override suspend fun retry(pageId: PageId) {
        retryRequests++
    }

    override suspend fun flushProgress() = Unit

    override suspend fun closeAndFlush() {
        closeRequests++
        closeFailure?.let { throw it }
    }

    override fun cancelWithoutFlush() {
        cancelRequests++
    }
}

internal fun testPage(index: Int): PageDescriptor = PageDescriptor(
    PageId("chapter", "page-$index.png"),
    width = 600,
    height = 900,
)

internal fun testReaderState(
    chapterId: Long = 7,
    pageCount: Int = 4,
    selectedIndex: Int = 0,
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    mode: ReadingMode = ReadingMode.SINGLE_LTR,
): ReaderState = ReaderState.ready(
    chapterId = chapterId,
    pages = List(pageCount) { index -> testPage(index) },
    selectedIndex = selectedIndex,
).copy(
    mode = mode,
    scaleMode = ScaleMode.FIT_WIDTH,
    hasPreviousChapter = hasPreviousChapter,
    hasNextChapter = hasNextChapter,
)
