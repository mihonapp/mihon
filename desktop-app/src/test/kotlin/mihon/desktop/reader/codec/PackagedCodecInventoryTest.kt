package mihon.desktop.reader.codec

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PackagedCodecInventoryTest {
    @Test
    fun `staged codec is pinned licensed and contains required delegates`() {
        val executable = PackagedReaderCodec.executablePath()
        val codecHome = requireNotNull(executable.parent)
        Files.isRegularFile(executable) shouldBe true
        Files.isRegularFile(codecHome.resolve("LICENSE.txt")) shouldBe true
        Files.readString(codecHome.resolve("THIRD-PARTY-NOTICES.txt")) shouldContain "7.1.2-31"

        val process = ProcessBuilder(executable.toString(), "-version")
            .directory(codecHome.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() shouldBe 0
        output shouldContain "ImageMagick 7.1.2-31 Q8 x64"
        val delegates = output.lineSequence()
            .first { it.startsWith("Delegates (built-in):") }
            .substringAfter(':')
            .trim()
            .split(' ')
        delegates.shouldContainAll("heic", "jpeg", "jxl", "png", "tiff", "webp")
    }
}
