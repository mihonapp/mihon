package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.IOException

@Execution(ExecutionMode.CONCURRENT)
class BackupStreamTest {

    private val parser = ProtoBuf

    @Test
    fun `writer output is the plain encoding`() {
        val backup = backup()

        val bytes = write(backup)

        bytes shouldBe encode(backup)
        encode(parser.decodeFromByteArray(Backup.serializer(), bytes)) shouldBe encode(backup)
    }

    @Test
    fun `reader reads the plain encoding`() {
        val backup = backup()

        val (entries, metadata) = read(encode(backup))

        entries.map(::encode) shouldBe backup.backupManga.map(::encode)
        encode(metadata) shouldBe encode(backup.copy(backupManga = emptyList()))
    }

    @Test
    fun `reader counts entries without decoding them`() {
        val backup = backup()
        val reader = BackupReader(Buffer().write(encode(backup)), parser)

        var count = 0
        while (reader.nextEntry()) {
            reader.skipEntry()
            count++
        }

        count shouldBe backup.backupManga.size
        encode(reader.readMetadata()) shouldBe encode(backup.copy(backupManga = emptyList()))
    }

    @Test
    fun `reader takes fields in any order`() {
        val backup = backup()
        val (first, second, third) = backup.backupManga
        val input = Buffer()
            .write(encode(Backup(emptyList(), backupSources = backup.backupSources)))
            .write(encode(Backup(listOf(first))))
            // Fields Backup doesn't know, one of each remaining wire type
            .writeByte(3 shl 3 or 0).writeByte(0x96).writeByte(0x01)
            .writeByte(4 shl 3 or 1).writeLongLe(42)
            .write(encode(Backup(listOf(second), backupCategories = backup.backupCategories)))
            .writeByte(5 shl 3 or 5).writeIntLe(42)
            .write(encode(Backup(emptyList(), backupPreferences = backup.backupPreferences)))
            .write(encode(Backup(listOf(third), backupExtensionStores = backup.backupExtensionStores)))
            .write(encode(Backup(emptyList(), backupSourcePreferences = backup.backupSourcePreferences)))
            .readByteArray()

        val (entries, metadata) = read(input)

        entries.map(::encode) shouldBe backup.backupManga.map(::encode)
        encode(metadata) shouldBe encode(backup.copy(backupManga = emptyList()))
    }

    @Test
    fun `empty backup`() {
        val sink = Buffer()
        val writer = BackupWriter(sink, parser)
        writer.writeMetadata(Backup(emptyList()))

        writer.isEmpty shouldBe true
        sink.size shouldBe 0L

        val (entries, metadata) = read(ByteArray(0))
        entries shouldBe emptyList()
        encode(metadata) shouldBe ByteArray(0)
    }

    @Test
    fun `truncated input throws`() {
        val bytes = encode(backup())

        // Cut inside the last entry, inside the metadata, and right after a tag
        val entryEnd = encode(Backup(backup().backupManga)).size
        listOf(entryEnd - 1, bytes.size - 1, entryEnd + 1).forEach { size ->
            assertThrows<IOException> { read(bytes.copyOf(size)) }
        }
    }

    @Test
    fun `garbage input throws`() {
        listOf(
            // Wire type 7 doesn't exist
            byteArrayOf(0x0F),
            // Field number 0
            byteArrayOf(0x02, 0x00),
            // A varint that never ends
            ByteArray(11) { 0xFF.toByte() },
            // An entry longer than the input
            byteArrayOf(0x0A, 0x10, 0x01),
            // An entry that isn't a BackupManga
            byteArrayOf(0x0A, 0x02, 0x0F, 0x00),
        ).forEach { input ->
            assertThrows<IOException> { read(input) }
        }
    }

    private fun write(backup: Backup): ByteArray {
        val sink = Buffer()
        val writer = BackupWriter(sink, parser)
        backup.backupManga.forEach(writer::writeEntry)
        writer.writeMetadata(backup.copy(backupManga = emptyList()))
        writer.isEmpty shouldBe false
        return sink.readByteArray()
    }

    private fun read(bytes: ByteArray): Pair<List<BackupManga>, Backup> {
        val reader = BackupReader(Buffer().write(bytes), parser)
        val entries = buildList {
            while (reader.nextEntry()) add(reader.readEntry())
        }
        return entries to reader.readMetadata()
    }

    private fun encode(backup: Backup) = parser.encodeToByteArray(Backup.serializer(), backup)

    private fun encode(manga: BackupManga) = parser.encodeToByteArray(BackupManga.serializer(), manga)

    private fun backup() = Backup(
        backupManga = listOf(
            BackupManga(
                source = 1L,
                url = "/manga/1",
                title = "First",
                genre = listOf("Action", "Drama"),
                chapters = listOf(
                    BackupChapter(url = "/chapter/1", name = "Chapter 1", read = true, chapterNumber = 1f),
                    BackupChapter(url = "/chapter/2", name = "Chapter 2", bookmark = true, lastPageRead = 4),
                ),
                categories = listOf(0L, 1L),
                tracking = listOf(BackupTracking(syncId = 2, libraryId = 3L, title = "First", mediaId = 7L)),
                history = listOf(BackupHistory(url = "/chapter/1", lastRead = 1_700_000_000_000L, readDuration = 60)),
                excludedScanlators = listOf("Group"),
            ),
            BackupManga(source = 2L, url = "/manga/2", title = "Second", favorite = false),
            BackupManga(
                source = 1L,
                url = "/manga/3",
                title = "Third",
                description = "x".repeat(300),
                chapters = List(200) { BackupChapter(url = "/chapter/3/$it", name = "Chapter $it") },
            ),
        ),
        backupCategories = listOf(
            BackupCategory(name = "Reading", order = 0),
            BackupCategory(name = "Done", order = 1),
        ),
        backupSources = listOf(BackupSource(name = "One", sourceId = 1L), BackupSource(name = "Two", sourceId = 2L)),
        backupPreferences = listOf(
            BackupPreference("flag", BooleanPreferenceValue(true)),
            BackupPreference("tags", StringSetPreferenceValue(setOf("a", "b"))),
        ),
        backupSourcePreferences = listOf(
            BackupSourcePreferences("source_1", listOf(BackupPreference("lang", StringPreferenceValue("en")))),
        ),
        backupExtensionStores = listOf(
            BackupExtensionStore(
                indexUrl = "https://example.org/index.min.json",
                name = "Store",
                badgeLabel = null,
                signingKey = "key",
                contactWebsite = "https://example.org",
                contactDiscord = null,
                isLegacy = false,
                extensionListUrl = null,
            ),
        ),
    )
}
