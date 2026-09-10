package mihon.reader.source

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StandaloneModernImageSourceTest {

    @Test
    fun `standalone modern images are selected by magic instead of filename`(
        @TempDir tempDir: Path,
    ): Unit = runBlocking {
        val fixtures = mapOf(
            "webp.bin" to ("RIFF" + "0000" + "WEBP").toByteArray(),
            "avif.bin" to bmff("avif"),
            "heif.bin" to bmff("heic"),
            "jxl.bin" to byteArrayOf(0xFF.toByte(), 0x0A),
            "tiff.bin" to byteArrayOf(0x49, 0x49, 0x2A, 0x00),
        )
        fixtures.forEach { (name, bytes) -> Files.write(tempDir.resolve(name), bytes) }

        val factory = LocalChapterSourceFactory()
        fixtures.keys.forEach { name ->
            factory.create(ArchiveFixtures.asset(tempDir, name, "IMAGE")).use { source ->
                source.pages().map { it.id.entryName }.shouldContainExactly(name)
            }
        }
    }

    private fun bmff(brand: String): ByteArray =
        byteArrayOf(0, 0, 0, 24) + "ftyp".toByteArray() + brand.toByteArray() + byteArrayOf(0, 0, 0, 0)
}
