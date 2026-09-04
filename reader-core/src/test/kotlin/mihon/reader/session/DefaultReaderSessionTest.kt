package mihon.reader.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultReaderSessionTest {
    @Test
    fun `runtime generation source gives every session opening a unique increasing token`() {
        val generations = AtomicReaderGenerationSource()

        generations.nextGeneration() shouldBe 0L
        generations.nextGeneration() shouldBe 1L
        generations.nextGeneration() shouldBe 2L
    }

    @Test
    fun `reader state reducer rejects invalid zoom and preserves persisted page identity for rtl`() {
        val initial = ReaderState.ready(
            chapterId = 7L,
            pages = pages(7L, 3),
            selectedIndex = 1,
            viewport = ReaderViewport(100, 200),
        )

        val rtl = initial.reduce(ReaderAction.ChangeMode(mihon.reader.model.ReadingMode.SINGLE_RTL))

        rtl.selectedIndex shouldBe 1
        shouldThrow<IllegalArgumentException> { rtl.reduce(ReaderAction.SetZoom(Float.NaN)) }
        shouldThrow<IllegalArgumentException> {
            rtl.reduce(ReaderAction.SetPan(ReaderPan(Float.POSITIVE_INFINITY, 0f)))
        }
    }

    @Test
    fun `open clamps long persistence before converting and requests its restored visible page`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = Long.MAX_VALUE)
        val source = FakeSource(asset, pages(7, 3))
        val requested = mutableListOf<mihon.reader.model.PageId>()
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = object : ReaderChapterCatalog {
                override fun chapterAsset(chapterId: Long) = asset.takeIf { it.chapterId == chapterId }

                override fun adjacentReadableChapter(
                    chapterId: Long,
                    direction: mihon.reader.source.ChapterDirection,
                ): ReaderChapterAsset? = null
            },
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            visibleContent = { page ->
                requested += page
                null
            },
        )

        session.open(7)
        runCurrent()

        session.state.value.selectedIndex shouldBe 2
        session.state.value.visiblePages.shouldBe(listOf(source.descriptors[2].id))
        requested.shouldBe(listOf(source.descriptors[2].id))
    }

    @Test
    fun `animation loops with probed durations and pauses when hidden`() = runTest {
        val frames = mutableListOf<mihon.reader.model.FrameId>()
        val coordinator = AnimationCoordinator(backgroundScope, loadFrame = { frame ->
            frames += frame
            null
        })
        val page = mihon.reader.model.PageId("7", "animated.gif")

        coordinator.start(page, AnimationProbe(frameCount = 2, frameDurationsMillis = listOf(10, 20)))
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        coordinator.setContentVisible(false)
        advanceTimeBy(100)
        runCurrent()

        frames.shouldBe(listOf(mihon.reader.model.FrameId(page, 0), mihon.reader.model.FrameId(page, 1)))
    }

    @Test
    fun `empty chapter has a typed empty chapter reader error`() {
        mihon.reader.model.ReaderErrorCode.EMPTY_CHAPTER shouldBe mihon.reader.model.ReaderErrorCode.EMPTY_CHAPTER
    }

    @Test
    fun `dual page next advances to the next logical spread`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = 0)
        val source = FakeSource(asset, pages(7, 5))
        val requested = mutableListOf<mihon.reader.model.PageId>()
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = catalog(asset),
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            settings = ReaderSettings(mode = mihon.reader.model.ReadingMode.DUAL_LTR),
            visibleContent = { page ->
                requested += page
                null
            },
        )
        session.open(7)

        session.dispatch(ReaderAction.Next)
        runCurrent()

        session.state.value.selectedIndex shouldBe 2
        requested.last() shouldBe source.descriptors[2].id
    }

    private fun pages(chapterId: Long, count: Int) =
        (0 until count).map { index ->
            mihon.reader.model.PageDescriptor(
                id = mihon.reader.model.PageId(chapterId.toString(), "page-$index.jpg"),
                width = 100,
                height = 200,
            )
        }

    private fun asset(chapterId: Long, lastPageRead: Long) = ReaderChapterAsset(
        mangaId = 1,
        chapterId = chapterId,
        mangaTitle = "manga",
        chapterName = "chapter",
        storageRoot = Path.of("."),
        relativePath = Path.of("chapter-$chapterId"),
        assetKind = "directory",
        sizeBytes = 1,
        modifiedAt = 1,
        lastPageRead = lastPageRead,
        read = false,
    )

    private fun catalog(asset: ReaderChapterAsset) = object : ReaderChapterCatalog {
        override fun chapterAsset(chapterId: Long) = asset.takeIf { it.chapterId == chapterId }

        override fun adjacentReadableChapter(
            chapterId: Long,
            direction: mihon.reader.source.ChapterDirection,
        ): ReaderChapterAsset? = null
    }

    private class FakeSource(
        override val asset: ReaderChapterAsset,
        val descriptors: List<mihon.reader.model.PageDescriptor>,
    ) : ChapterSource {
        override suspend fun pages() = descriptors

        override suspend fun open(pageId: mihon.reader.model.PageId) =
            BoundedPageInput(ByteArrayInputStream(byteArrayOf()), 0)

        override fun close() = Unit
    }
}
