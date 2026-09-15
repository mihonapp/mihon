@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.library.backup

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.library.backup.suwayomifixture.Backup
import mihon.desktop.library.backup.suwayomifixture.BackupCategory
import mihon.desktop.library.backup.suwayomifixture.BackupChapter
import mihon.desktop.library.backup.suwayomifixture.BackupHistory
import mihon.desktop.library.backup.suwayomifixture.BackupManga
import mihon.desktop.library.backup.suwayomifixture.BackupTracking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class SuwayomiBackupCompatibilityTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `export disambiguates colliding desktop category orders without losing links`() {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("categories.db")).use { repository ->
            repository.transaction {
                val first = upsertCategory(mihon.desktop.library.model.CategoryRecord(name = "First", sortOrder = 0))
                val second = upsertCategory(mihon.desktop.library.model.CategoryRecord(name = "Second", sortOrder = 0))
                val manga = insertManga(mihon.desktop.library.model.MangaRecord(sourceId = 5, url = "/m", title = "M"))
                linkCategory(manga, first)
                linkCategory(manga, second)
            }
            val backup = AndroidBackupExporter(repository).createBackup()
            AndroidBackupValidator().validate(backup)
            backup.backupManga.single().categories.toSet().size shouldBe 2
            backup.backupCategories.sortedBy { it.order }.map { it.name } shouldBe listOf("First", "Second")
        }
    }

    @Test
    fun `empty official protobuf library is a valid restore`() {
        val file = tempDir.resolve("empty.tachibk")
        Files.write(file, ProtoBuf.encodeToByteArray(Backup.serializer(), Backup()))
        AndroidBackupCodec().decode(file).backupManga shouldBe emptyList()
    }

    @Test
    fun `export reads all library tables inside one transaction`() {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("snapshot.db")).use { repository ->
            var inTransaction = false
            val monitored = object :
                mihon.desktop.library.repository.LibraryRepository by repository,
                mihon.desktop.library.repository.LibraryMutationPort by repository {
                override fun <T> transaction(block: mihon.desktop.library.repository.LibraryMutationPort.() -> T): T {
                    inTransaction = true
                    return try {
                        repository.transaction(block)
                    } finally {
                        inTransaction = false
                    }
                }
                override fun allMangaSnapshot(): List<mihon.desktop.library.model.MangaRecord> {
                    check(inTransaction) { "Backup snapshot must be transactionally consistent" }
                    return repository.allMangaSnapshot()
                }
            }
            AndroidBackupExporter(monitored).createBackup().backupManga shouldBe emptyList()
        }
    }

    @Test
    fun `official model serializer backup preserves unnamed source and category ordering on repeat restore`() {
        val fixture = Backup(
            backupCategories = listOf(BackupCategory("Later", 8), BackupCategory("First", 0)),
            backupManga = listOf(
                BackupManga(
                    source = 876543210987654321L,
                    url = "/synthetic/manga",
                    title = "Synthetic compatibility sample",
                    categories = listOf(0, 8),
                    chapters = listOf(BackupChapter("/chapter/1", "One", bookmark = true, lastPageRead = 7)),
                    history = listOf(BackupHistory("/chapter/1", 12345)),
                    tracking = listOf(BackupTracking(syncId = 1, libraryId = 5, mediaId = 1234567890123L)),
                ),
            ),
        )
        val input = tempDir.resolve("official-model-synthetic.tachibk")
        GZIPOutputStream(Files.newOutputStream(input)).use {
            it.write(ProtoBuf.encodeToByteArray(Backup.serializer(), fixture))
        }
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("library.db")).use { repository ->
            val importer = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), repository)
            importer.import(input, 100).counts.mangaInserted shouldBe 1L
            importer.import(input, 200).counts.mangaInserted shouldBe 0L
            val output = tempDir.resolve("exported.tachibk")
            AndroidBackupExporter(repository).export(output)
            val restored = GZIPInputStream(Files.newInputStream(output)).use {
                ProtoBuf.decodeFromByteArray(Backup.serializer(), it.readBytes())
            }
            restored.backupManga.size shouldBe 1
            restored.backupCategories.sortedBy { it.order }.map { it.name } shouldBe listOf("First", "Later")
            val manga = restored.backupManga.single()
            manga.source shouldBe fixture.backupManga.single().source
            manga.categories.sorted() shouldBe listOf(0, 8)
            manga.chapters.single().bookmark shouldBe true
            manga.chapters.single().lastPageRead shouldBe 7
            manga.history.single().lastRead shouldBe 12345L
            manga.tracking.single().mediaId shouldBe 1234567890123L
        }
    }

    @Test
    fun `failed atomic replacement cleans owned temp file and preserves destination`() {
        val destination = tempDir.resolve("backup.tachibk")
        Files.createDirectory(destination)
        Files.writeString(destination.resolve("keep"), "original")
        shouldThrowAny { AndroidBackupCodec().encode(AndroidBackup(emptyList()), destination) }
        Files.readString(destination.resolve("keep")) shouldBe "original"
        Files.list(tempDir).use { it.map { p -> p.fileName.toString() }.toList() } shouldBe listOf("backup.tachibk")
    }
}
