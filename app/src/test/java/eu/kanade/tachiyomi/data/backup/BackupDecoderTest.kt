package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

class BackupDecoderTest {

    private val decoder = BackupDecoder(mockk(relaxed = true), ProtoBuf, mockk(relaxed = true))
    private val expected = Backup(
        backupManga = emptyList(),
        backupSources = listOf(BackupSource(name = "Test", sourceId = 1L)),
    )
    private val encoded = ProtoBuf.encodeToByteArray(Backup.serializer(), expected)

    @Test
    fun `decodes an uncompressed protobuf backup`() {
        decoder.decode(encoded.inputStream()) shouldBe expected
    }

    @Test
    fun `decodes a gzip protobuf backup`() {
        val gzipped = ByteArrayOutputStream().let { out ->
            GZIPOutputStream(out).use { it.write(encoded) }
            out.toByteArray()
        }

        decoder.decode(gzipped.inputStream()) shouldBe expected
    }

    @Test
    fun `rejects a JSON backup`() {
        shouldThrow<IOException> {
            decoder.decode("{}".byteInputStream())
        }
    }
}
