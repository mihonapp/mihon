package mihon.desktop.reader.codec

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PackagedReaderCodecTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `explicit codec property wins over packaged and development locations`() {
        val previous = System.getProperty(PackagedReaderCodec.CODEC_PROPERTY)
        val explicit = temporaryDirectory.resolve("custom/magick.exe").toAbsolutePath()
        try {
            System.setProperty(PackagedReaderCodec.CODEC_PROPERTY, explicit.toString())
            PackagedReaderCodec.executablePath() shouldBe explicit.normalize()
        } finally {
            if (previous == null) {
                System.clearProperty(PackagedReaderCodec.CODEC_PROPERTY)
            } else {
                System.setProperty(PackagedReaderCodec.CODEC_PROPERTY, previous)
            }
        }
    }
}
