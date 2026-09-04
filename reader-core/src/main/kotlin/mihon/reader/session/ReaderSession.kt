package mihon.reader.session

import kotlinx.coroutines.flow.StateFlow
import mihon.reader.model.PageId

interface ReaderSession {
    val state: StateFlow<ReaderState>

    suspend fun open(chapterId: Long)

    fun dispatch(action: ReaderAction)

    suspend fun retry(pageId: PageId)

    suspend fun flushProgress()

    suspend fun closeAndFlush()

    fun cancelWithoutFlush()
}
