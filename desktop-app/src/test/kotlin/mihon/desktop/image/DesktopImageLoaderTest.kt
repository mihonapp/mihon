package mihon.desktop.image

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class DesktopImageLoaderTest {

    private fun createPngBytes(width: Int = 16, height: Int = 16, color: Color = Color.BLUE): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    @Test
    fun `loads image from local file`(@TempDir tempDir: Path) = runBlocking {
        val cacheDir = tempDir.resolve("cache")
        val imageLoader = DesktopImageLoader(diskCacheDir = cacheDir)

        val imageFile = tempDir.resolve("cover.png")
        Files.write(imageFile, createPngBytes(20, 30))

        val bitmap = imageLoader.load(ImageRequest(uri = imageFile.toUri().toString()))
        assertNotNull(bitmap)
        assertEquals(20, bitmap?.width)
        assertEquals(30, bitmap?.height)
    }

    @Test
    fun `loads image from cbz archive`(@TempDir tempDir: Path) = runBlocking {
        val cacheDir = tempDir.resolve("cache")
        val imageLoader = DesktopImageLoader(diskCacheDir = cacheDir)

        val cbzFile = tempDir.resolve("manga.cbz")
        ZipOutputStream(FileOutputStream(cbzFile.toFile())).use { zip ->
            zip.putNextEntry(ZipEntry("001_cover.png"))
            zip.write(createPngBytes(25, 35))
            zip.closeEntry()
        }

        val bitmap = imageLoader.load(ImageRequest(uri = cbzFile.toString()))
        assertNotNull(bitmap)
        assertEquals(25, bitmap?.width)
        assertEquals(35, bitmap?.height)
    }

    @Test
    fun `loads image from local manga directory`(@TempDir tempDir: Path) = runBlocking {
        val cacheDir = tempDir.resolve("cache")
        val imageLoader = DesktopImageLoader(diskCacheDir = cacheDir)

        val mangaDir = tempDir.resolve("My Manga")
        Files.createDirectories(mangaDir)
        Files.write(mangaDir.resolve("cover.png"), createPngBytes(15, 25))

        val bitmap = imageLoader.load(ImageRequest(uri = null, localMangaPath = mangaDir))
        assertNotNull(bitmap)
        assertEquals(15, bitmap?.width)
        assertEquals(25, bitmap?.height)
    }

    @Test
    fun `loads avif cover discovered in local manga directory`(@TempDir tempDir: Path) = runBlocking {
        val codec = mihon.desktop.reader.codec.PackagedReaderCodec.executablePath().toFile()
        assertTrue(codec.isFile, "packaged image codec fixture is required")
        val mangaDir = tempDir.resolve("AVIF Manga")
        Files.createDirectories(mangaDir)
        val png = tempDir.resolve("source.png")
        Files.write(png, createPngBytes(19, 27))
        val avif = mangaDir.resolve("cover.avif")
        val process = ProcessBuilder(codec.absolutePath, png.toString(), avif.toString()).start()
        assertEquals(0, process.waitFor(), process.errorStream.readAllBytes().decodeToString())

        val bitmap = DesktopImageLoader(tempDir.resolve("cache")).load(
            ImageRequest(uri = null, localMangaPath = mangaDir),
        )
        assertNotNull(bitmap)
        assertEquals(19, bitmap?.width)
        assertEquals(27, bitmap?.height)
    }

    @Test
    fun `decodes jpeg png webp gif and avif fixtures`(@TempDir tempDir: Path) = runBlocking {
        val codec = mihon.desktop.reader.codec.PackagedReaderCodec.executablePath().toFile()
        val source = tempDir.resolve("source.png")
        Files.write(source, createPngBytes(23, 31))
        val loader = DesktopImageLoader(tempDir.resolve("cache"))

        listOf("jpg", "png", "webp", "gif", "avif").forEach { extension ->
            val target = tempDir.resolve("fixture.$extension")
            val process = ProcessBuilder(codec.absolutePath, source.toString(), target.toString()).start()
            assertEquals(0, process.waitFor(), process.errorStream.readAllBytes().decodeToString())
            val bitmap = loader.load(ImageRequest(uri = target.toString()))
            assertNotNull(bitmap, "$extension fixture should decode")
            assertEquals(23, bitmap?.width, extension)
            assertEquals(31, bitmap?.height, extension)
        }
    }

    @Test
    fun `custom cover takes priority over uri`(@TempDir tempDir: Path) = runBlocking {
        val cacheDir = tempDir.resolve("cache")
        val coversDir = tempDir.resolve("covers")
        val customManager = CustomCoverManager(coversDir)
        val imageLoader = DesktopImageLoader(diskCacheDir = cacheDir, customCoverManager = customManager)

        val mangaId = 77L
        val customCoverFile = tempDir.resolve("custom.png")
        Files.write(customCoverFile, createPngBytes(40, 50, Color.GREEN))
        customManager.setCustomCover(mangaId, customCoverFile)

        val regularFile = tempDir.resolve("regular.png")
        Files.write(regularFile, createPngBytes(10, 10, Color.RED))

        val bitmap = imageLoader.load(ImageRequest(uri = regularFile.toString(), mangaId = mangaId))
        assertNotNull(bitmap)
        // Custom cover dimensions should be used
        assertEquals(40, bitmap?.width)
        assertEquals(50, bitmap?.height)
    }

    @Test
    fun `serves repeated requests from memory cache`(@TempDir tempDir: Path) = runBlocking {
        val cacheDir = tempDir.resolve("cache")
        val imageLoader = DesktopImageLoader(diskCacheDir = cacheDir)

        val imageFile = tempDir.resolve("temp_cover.png")
        Files.write(imageFile, createPngBytes(12, 18))

        val uri = imageFile.toUri().toString()
        val first = imageLoader.load(ImageRequest(uri = uri))
        assertNotNull(first)

        // Delete underlying file
        Files.delete(imageFile)

        // Should still return from memory cache
        val second = imageLoader.load(ImageRequest(uri = uri))
        assertNotNull(second)
        assertSame(first, second)
    }

    @Test
    fun `clear memory and disk cache`(@TempDir tempDir: Path) = runBlocking {
        val cacheDir = tempDir.resolve("cache")
        val imageLoader = DesktopImageLoader(diskCacheDir = cacheDir)

        val dummyCachedFile = cacheDir.resolve("abc123.img")
        Files.writeString(dummyCachedFile, "cached")
        assertTrue(Files.exists(dummyCachedFile))

        imageLoader.putInMemory(
            "test",
            createPngBytes().let {
                org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()
            },
        )
        assertNotNull(imageLoader.getFromMemory("test"))

        imageLoader.clearMemoryCache()
        assertNull(imageLoader.getFromMemory("test"))

        imageLoader.clearDiskCache()
        assertFalse(Files.exists(dummyCachedFile))
    }
}
