package mihon.reader.session

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderActionOrderingTest {
    @Test
    fun `page selection stays before next chapter even when dispatcher runs newest work first`() = runTest {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + NewestFirstDispatcher(StandardTestDispatcher(testScheduler)) +
                CoroutineExceptionHandler { _, error -> errors += error },
        )
        val session = session(scope)
        try {
            session.open(7)
            runCurrent()
            session.state.value.selectedIndex shouldBe 7

            session.dispatch(ReaderAction.SelectPage(7))
            session.dispatch(ReaderAction.Next)
            runCurrent()

            errors shouldBe emptyList()
            session.state.value.chapterId shouldBe 8L
            session.state.value.selectedIndex shouldBe 0
            session.closeAndFlush()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `queued old chapter selection cannot target a directly opened chapter`() = runTest {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> errors += error },
        )
        val session = session(scope)
        try {
            session.open(7)
            runCurrent()
            session.dispatch(ReaderAction.SelectPage(7))
            session.open(8)
            runCurrent()

            errors shouldBe emptyList()
            session.state.value.chapterId shouldBe 8L
            session.state.value.selectedIndex shouldBe 0
            session.closeAndFlush()
        } finally {
            scope.cancel()
        }
    }

    private fun session(scope: CoroutineScope): DefaultReaderSession {
        val assets = (7L..8L).associateWith { id ->
            ReaderChapterAsset(
                mangaId = 1, chapterId = id, mangaTitle = "fixture", chapterName = "chapter-$id",
                storageRoot = Path.of("."), relativePath = Path.of("chapter-$id"), assetKind = "directory",
                sizeBytes = 1, modifiedAt = 1, lastPageRead = if (id == 7L) 7 else 0, read = false,
            )
        }
        return DefaultReaderSession(
            scope = scope,
            catalog = object : ReaderChapterCatalog {
                override fun chapterAsset(chapterId: Long) = assets[chapterId]
                override fun adjacentReadableChapter(chapterId: Long, direction: ChapterDirection) =
                    if (chapterId == 7L && direction == ChapterDirection.NEXT) assets[8] else null
            },
            sourceFactory = ChapterSourceFactory { value ->
                object : ChapterSource {
                    override val asset = value
                    override suspend fun pages() = List(if (value.chapterId == 7L) 8 else 1) { index ->
                        PageDescriptor(PageId("chapter-${value.chapterId}", "$index.png"), 100, 100)
                    }
                    override suspend fun open(pageId: PageId) = error("No image I/O needed for action ordering")
                    override fun close() = Unit
                }
            },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
        )
    }

    private class NewestFirstDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.addFirst(block)
            delegate.dispatch(context) { pending.removeFirst().run() }
        }
    }
}
