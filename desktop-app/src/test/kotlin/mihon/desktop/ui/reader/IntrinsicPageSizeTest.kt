package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ProgressWriteResult
import mihon.reader.session.ReaderProgressUpdate
import mihon.reader.session.ReaderState
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ReaderChapterAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class IntrinsicPageSizeTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `unknown 1x1 descriptor gets portrait placeholder while known sizes win`() {
        val unknown = descriptor(width = 1, height = 1)

        val placeholder = unknown.withIntrinsicSize(null)
        placeholder.width shouldBe PageSize.PLACEHOLDER.width
        placeholder.height shouldBe PageSize.PLACEHOLDER.height
        (placeholder.width != placeholder.height) shouldBe true

        unknown.withIntrinsicSize(PageSize(800, 1200)) shouldBe descriptor(width = 800, height = 1200)

        val declared = descriptor(width = 600, height = 900)
        declared.withIntrinsicSize(null) shouldBe declared
        declared.withIntrinsicSize(PageSize(600, 900)) shouldBe declared
    }

    @Test
    fun `paged transforms use intrinsic dimensions and never assume a square placeholder`() {
        val intrinsic = descriptor(width = 1, height = 1).withIntrinsicSize(PageSize(400, 1000))
        val viewport = ReaderViewport(800, 500)

        calculatePageTransform(intrinsic, viewport, ScaleMode.FIT_WIDTH, 1f, ReaderPan(0f, 0f)) shouldBe
            ReaderPageTransform(widthPixels = 800, heightPixels = 2000, zoom = 1f, pan = ReaderPan(0f, 0f))
        calculatePageTransform(intrinsic, viewport, ScaleMode.FIT_HEIGHT, 1f, ReaderPan(0f, 0f)) shouldBe
            ReaderPageTransform(widthPixels = 200, heightPixels = 500, zoom = 1f, pan = ReaderPan(0f, 0f))
        calculatePageTransform(intrinsic, viewport, ScaleMode.ORIGINAL, 1f, ReaderPan(0f, 0f)) shouldBe
            ReaderPageTransform(widthPixels = 400, heightPixels = 1000, zoom = 1f, pan = ReaderPan(0f, 0f))

        val placeholderTransform = calculatePageTransform(
            page = descriptor(width = 1, height = 1).withIntrinsicSize(null),
            viewport = ReaderViewport(300, 600),
            scaleMode = ScaleMode.FIT_WIDTH,
            zoom = 1f,
            requestedPan = ReaderPan(0f, 0f),
        )
        placeholderTransform.widthPixels shouldBe 300
        placeholderTransform.heightPixels shouldBe 450
    }

    @Test
    fun `gesture pan bounds use intrinsic dimensions instead of the 1x1 descriptor`() {
        val state = ReaderState.ready(
            chapterId = 7,
            pages = listOf(descriptor(width = 1, height = 1)),
            selectedIndex = 0,
        ).copy(
            mode = ReadingMode.SINGLE_LTR,
            scaleMode = ScaleMode.ORIGINAL,
        )
        val viewport = ReaderViewport(200, 300)

        ReaderGesturePolicy.panBounds(state, viewport, zoom = 8f) shouldBe ReaderPanBounds.ZERO

        val bounds = ReaderGesturePolicy.panBounds(
            state = state,
            viewport = viewport,
            zoom = 8f,
            pageSize = PageSize(400, 1000),
        )
        bounds.maxX shouldBe 1500f
        bounds.maxY shouldBe 3850f
        ReaderGesturePolicy.clampPan(
            state = state,
            viewport = viewport,
            zoom = 8f,
            requested = ReaderPan(5000f, -5000f),
            pageSize = PageSize(400, 1000),
        ) shouldBe ReaderPan(1500f, -3850f)
    }

    @Test
    fun `page size cache evicts oldest and clears by chapter`() {
        val cache = IntrinsicPageSizeCache(maxEntries = 2)
        val first = PageId("chapter-1", "page-0.png")
        val second = PageId("chapter-1", "page-1.png")
        val third = PageId("chapter-2", "page-0.png")

        cache.record(first, PageSize(10, 20))
        cache.record(second, PageSize(20, 30))
        cache.record(third, PageSize(30, 40))

        cache.sizes.value.size shouldBe 2
        cache.sizeOf(first) shouldBe null
        cache.sizeOf(second) shouldBe PageSize(20, 30)
        cache.sizeOf(third) shouldBe PageSize(30, 40)

        cache.clearChapter("chapter-1")
        cache.snapshot().keys shouldBe setOf(third)

        cache.clear()
        cache.sizes.value shouldBe emptyMap<PageId, PageSize>()
    }

    @Test
    fun `paged canvas promotes intrinsic size into the page descriptor`() = runComposeUiTest {
        val pageId = PageId("chapter", "page-0.png")
        val state = ReaderState.ready(
            chapterId = 7,
            pages = listOf(PageDescriptor(pageId, 1, 1)),
            selectedIndex = 0,
        ).copy(
            mode = ReadingMode.SINGLE_LTR,
            scaleMode = ScaleMode.FIT_WIDTH,
        )
        var captured: PageDescriptor? = null

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 1000.dp)) {
                    ReaderCanvas(
                        state = state,
                        onAction = {},
                        pageSizes = mapOf(pageId to PageSize(400, 1000)),
                        pageContent = { page, _, modifier ->
                            captured = page
                            Box(modifier)
                        },
                    )
                }
            }
        }
        waitForIdle()

        captured?.width shouldBe 400
        captured?.height shouldBe 1000
    }

    @Test
    fun `webtoon and vertical item heights follow intrinsic ratio`() = runComposeUiTest {
        val pageId = PageId("chapter", "page-0.png")

        setContinuousCanvas(ReadingMode.WEBTOON, emptyMap())
        waitForIdle()
        var bounds = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        bounds.width shouldBe 800.dp
        bounds.height shouldBe 1200.dp

        setContinuousCanvas(ReadingMode.WEBTOON, mapOf(pageId to PageSize(400, 1000)))
        waitForIdle()
        bounds = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        bounds.width shouldBe 800.dp
        bounds.height shouldBe 2000.dp

        setContinuousCanvas(ReadingMode.VERTICAL, mapOf(pageId to PageSize(400, 1000)))
        waitForIdle()
        bounds = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        bounds.width shouldBe 800.dp
        bounds.height shouldBe 2000.dp
    }

    @Test
    fun `loadFrame records probed page size and shutdown clears the cache`() {
        runBlocking {
            val chapterDir = tempDir.resolve("chapter")
            Files.createDirectories(chapterDir)
            val imageFile = chapterDir.resolve("page-0.png").toFile()
            val image = BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try {
                graphics.color = java.awt.Color.WHITE
                graphics.fillRect(0, 0, 40, 60)
                graphics.color = java.awt.Color.BLACK
                graphics.fillRect(5, 5, 30, 50)
            } finally {
                graphics.dispose()
            }
            ImageIO.write(image, "png", imageFile)

            val asset = ReaderChapterAsset(
                mangaId = 1L,
                chapterId = 7L,
                mangaTitle = "Test manga",
                chapterName = "Chapter 7",
                storageRoot = tempDir,
                relativePath = Path.of("chapter"),
                assetKind = "DIRECTORY",
                sizeBytes = 0L,
                modifiedAt = 0L,
                lastPageRead = 0L,
                read = false,
            )
            val factory = DesktopReaderFactory(
                applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                library = object : ReaderLibraryPort {
                    override fun chapterAsset(chapterId: Long): ReaderChapterAsset? =
                        asset.takeIf { chapterId == asset.chapterId }

                    override fun adjacentReadableChapter(
                        chapterId: Long,
                        direction: ChapterDirection,
                    ): ReaderChapterAsset? = null

                    override suspend fun record(update: ReaderProgressUpdate): ProgressWriteResult =
                        ProgressWriteResult.APPLIED
                },
                settings = DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("prefs.properties"))),
            )
            val pageId = PageId("7", "page-0.png")
            try {
                val frame = factory.loadFrame(pageId, frameIndex = 0)
                try {
                    frame.metadata.width shouldBe 40
                    frame.metadata.height shouldBe 60
                } finally {
                    frame.tile.close()
                }
                factory.pageSizes.sizes.value[pageId] shouldBe PageSize(40, 60)

                val cropped = factory.loadFrame(pageId, frameIndex = 0, cropBorders = true)
                try {
                    cropped.metadata.width shouldBe 30
                    cropped.metadata.height shouldBe 50
                    cropped.tile.image.width shouldBe 30
                    cropped.tile.image.height shouldBe 50
                } finally {
                    cropped.tile.close()
                }
                factory.pageSizes.sizes.value[pageId] shouldBe PageSize(30, 50)
            } finally {
                factory.shutdown()
                factory.closeServices()
            }

            factory.pageSizes.sizes.value shouldBe emptyMap<PageId, PageSize>()
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setContinuousCanvas(
        mode: ReadingMode,
        pageSizes: Map<PageId, PageSize>,
    ) {
        val pageId = PageId("chapter", "page-0.png")
        val state = ReaderState.ready(
            chapterId = 7,
            pages = listOf(PageDescriptor(pageId, 1, 1)),
            selectedIndex = 0,
        ).copy(
            mode = mode,
            scaleMode = ScaleMode.FIT_WIDTH,
            viewport = ReaderViewport(800, 2400),
        )
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 2400.dp)) {
                    ReaderCanvas(
                        state = state,
                        onAction = {},
                        pageSizes = pageSizes,
                    )
                }
            }
        }
    }

    private fun descriptor(width: Int, height: Int) =
        PageDescriptor(PageId("chapter", "page.png"), width, height)
}
