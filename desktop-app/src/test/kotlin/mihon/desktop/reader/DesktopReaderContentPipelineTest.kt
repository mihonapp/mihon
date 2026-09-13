package mihon.desktop.reader

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.desktop.ui.reader.ComposeTileBridge
import mihon.desktop.ui.reader.IntrinsicPageSizeCache
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.ImageIoPageDecoder
import mihon.reader.image.ImageMetadata
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.session.AdjacentChapterWarmup
import mihon.reader.session.AnimationCoordinator
import mihon.reader.session.ReaderContentPosition
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopReaderContentPipelineTest {
    @Test
    fun `desktop content exposes frames selected by the core animation coordinator`() = runTest {
        val cache = WeightedTileCache(4L * 1024L * 1024L)
        val budget = BoundedReaderMemoryBudget(4L * 1024L * 1024L) { cache.relievePressure() }
        val pipeline = DesktopReaderContentPipeline(
            decoder = ImageIoPageDecoder(budget),
            cache = cache,
            scope = backgroundScope,
            sourceFactory = ChapterSourceFactory { error("no source expected") },
        )
        val animation = AnimationCoordinator(backgroundScope, loadFrame = { null })
        val bridge = ComposeTileBridge()
        val content = DesktopReaderContent(pipeline, bridge, IntrinsicPageSizeCache(), animation)
        val pageId = PageId("7", "animated.gif")

        content.startAnimation(pageId, ImageMetadata(4, 4, 2, listOf(10, 20), supportsRegionDecode = true))
        runCurrent()
        content.selectedAnimationFrame.value shouldBe mihon.reader.model.FrameId(pageId, 0)
        advanceTimeBy(10)
        runCurrent()
        content.selectedAnimationFrame.value shouldBe mihon.reader.model.FrameId(pageId, 1)
        content.setForeground(false)
        content.selectedAnimationFrame.value shouldBe null

        animation.close()
        pipeline.close()
        bridge.close()
        budget.close()
        cache.close()
    }

    @Test
    fun `visible load and four ahead one behind prefetch share one coordinator`() = runTest {
        val source = CountingSource(asset(7), pageCount = 8)
        val cache = WeightedTileCache(4L * 1024L * 1024L)
        val budget = BoundedReaderMemoryBudget(4L * 1024L * 1024L) { cache.relievePressure() }
        val pipeline = DesktopReaderContentPipeline(
            decoder = ImageIoPageDecoder(budget),
            cache = cache,
            scope = backgroundScope,
            sourceFactory = ChapterSourceFactory { error("no adjacent chapter expected") },
        )

        pipeline.open(source)
        pipeline.updatePosition(
            ReaderContentPosition(
                selectedIndex = 1,
                visiblePages = listOf(source.pages[1].id),
                mode = ReadingMode.SINGLE_LTR,
                direction = NavigationDirection.FORWARD,
                foreground = true,
                contentVisible = true,
            ),
        )
        pipeline.loadVisible(source.pages[1].id)?.close()
        runCurrent()

        source.openedIndexes.distinct().sorted().shouldContainExactly(0, 1, 2, 3, 4, 5)
        val visibleOpenCount = source.openedIndexes.count { it == 1 }
        pipeline.loadVisible(source.pages[1].id)?.close()
        source.openedIndexes.count { it == 1 } shouldBe visibleOpenCount
        // The decoder's conservative reservations may evict unpinned prefetched residents,
        // while source counts still prove that the full window completed inside the budget.
        pipeline.metrics().entryCount shouldBe 1
        pipeline.close()
        budget.close()
        cache.close()
    }

    @Test
    fun `adjacent warmup first enumerates then preloads the first page at the boundary`() = runTest {
        val current = CountingSource(asset(7), pageCount = 8)
        val adjacent = CountingSource(asset(8), pageCount = 3)
        val cache = WeightedTileCache(4L * 1024L * 1024L)
        val budget = BoundedReaderMemoryBudget(4L * 1024L * 1024L) { cache.relievePressure() }
        val pipeline = DesktopReaderContentPipeline(
            decoder = ImageIoPageDecoder(budget),
            cache = cache,
            scope = backgroundScope,
            sourceFactory = ChapterSourceFactory { requested ->
                adjacent.takeIf { requested.chapterId == adjacent.asset.chapterId }
                    ?: error("unexpected adjacent chapter ${requested.chapterId}")
            },
        )
        pipeline.open(current)

        pipeline.warmAdjacent(AdjacentChapterWarmup(adjacent.asset, preloadFirstUnit = false))
        runCurrent()
        adjacent.pagesCalls shouldBe 1
        adjacent.openedIndexes shouldBe emptyList()

        pipeline.warmAdjacent(AdjacentChapterWarmup(adjacent.asset, preloadFirstUnit = true))
        runCurrent()
        adjacent.pagesCalls shouldBe 2
        adjacent.openedIndexes.distinct() shouldBe listOf(0)

        pipeline.close()
        budget.close()
        cache.close()
    }

    private class CountingSource(
        override val asset: ReaderChapterAsset,
        pageCount: Int,
    ) : ChapterSource {
        val pages = (0 until pageCount).map { index ->
            PageDescriptor(PageId(asset.chapterId.toString(), "page-$index.png"), 4, 4)
        }
        val openedIndexes = mutableListOf<Int>()
        var pagesCalls = 0

        override suspend fun pages(): List<PageDescriptor> {
            pagesCalls++
            return pages
        }

        override suspend fun open(pageId: PageId): BoundedPageInput {
            openedIndexes += pages.indexOfFirst { it.id == pageId }
            return BoundedPageInput(ByteArrayInputStream(PNG), PNG.size.toLong())
        }

        override fun close() = Unit
    }

    companion object {
        private val PNG = ByteArrayOutputStream().use { output ->
            val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).apply {
                createGraphics().also { graphics ->
                    graphics.color = Color.RED
                    graphics.fillRect(0, 0, width, height)
                    graphics.dispose()
                }
            }
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }

        private fun asset(chapterId: Long) = ReaderChapterAsset(
            mangaId = 1,
            chapterId = chapterId,
            mangaTitle = "manga",
            chapterName = "chapter-$chapterId",
            storageRoot = Path.of("."),
            relativePath = Path.of("chapter-$chapterId"),
            assetKind = "directory",
            sizeBytes = 1,
            modifiedAt = 1,
            lastPageRead = 0,
            read = false,
        )
    }
}
