package eu.kanade.tachiyomi.data.backup.create

import eu.kanade.tachiyomi.data.backup.BackupWriter
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toJsonObject
import mihon.domain.source.model.ChapterOrigin
import mihon.domain.source.model.LinkedSourceManga
import mihon.domain.source.model.assignedSourceId
import mihon.domain.source.model.chapterOrigins
import mihon.domain.source.model.linkedSources
import mihon.domain.source.model.withAssignedSource
import mihon.domain.source.model.withChapterOrigins
import mihon.domain.source.model.withLinkedSources
import mihon.domain.source.model.withStorageSources
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class OfficialBackupConverterTest {
    private val emptyMemo = JsonObject(emptyMap())
    private val sourceMemo = JsonObject(mapOf("token" to JsonPrimitive("extension-data")))
    private val primary = origin(1, "/primary/1")
    private val alternate = origin(2, "/alternate/1")

    @Test
    fun `split uses real URLs and copies completed progress bookmarks and library settings`() {
        val manga = manga(chapter(listOf(primary, alternate)).also { it.read = true })
        manga.categories = listOf(3)
        manga.notes = "my notes"
        val entries = OfficialBackupConverter.convert(manga)

        entries.map { it.source to it.url } shouldBe listOf(1L to "/series", 2L to "/other-series")
        entries.map { it.chapters.single().url } shouldBe listOf(primary.url, alternate.url)
        entries.map { it.chapters.single().read } shouldBe listOf(true, true)
        entries.map { it.chapters.single().bookmark } shouldBe listOf(true, true)
        entries.map { it.categories } shouldBe listOf(listOf(3L), listOf(3L))
        entries.map { it.notes } shouldBe listOf("my notes", "my notes")
        entries[1].title shouldBe "Alternate title"
        entries[1].initialized shouldBe false
        // Export must not rewrite the live/native backup identity.
        manga.chapters.single().url shouldBe "mihon://number:1"
        manga.memo.toJsonObject().linkedSources().size shouldBe 1
    }

    @Test
    fun `partial page and history follow the assigned edition only`() {
        val manga = manga(chapter(listOf(primary, alternate), assigned = 2))
        manga.history = listOf(BackupHistory("mihon://number:1", 123, 456))
        val entries = OfficialBackupConverter.convert(manga)

        entries.map { it.chapters.single().lastPageRead } shouldBe listOf(0L, 17L)
        entries[0].history shouldBe emptyList()
        entries[1].history shouldBe listOf(BackupHistory(alternate.url, 123, 456))
    }

    @Test
    fun `unassigned partial progress follows the representative edition`() {
        val entries = OfficialBackupConverter.convert(manga(chapter(listOf(alternate, primary))))
        entries.map { it.chapters.single().lastPageRead } shouldBe listOf(0L, 17L)
    }

    @Test
    fun `source-only specials are not invented on the primary source`() {
        val special = alternate.copy(name = "Special", chapterNumber = -1F)
        val entries = OfficialBackupConverter.convert(manga(chapter(listOf(special))))
        entries[0].chapters shouldBe emptyList()
        entries[1].chapters.single().name shouldBe "Special"
        entries[1].chapters.single().chapterNumber shouldBe -1F
    }

    @Test
    fun `routing metadata is removed while extension metadata is preserved`() {
        val entries = OfficialBackupConverter.convert(manga(chapter(listOf(primary, alternate), assigned = 2)))
        entries.forEach { entry ->
            entry.memo.toJsonObject().linkedSources() shouldBe emptyList()
            entry.memo.toJsonObject().containsKey("mihon.storageSources") shouldBe false
            val memo = entry.chapters.single().memo.toJsonObject()
            memo.chapterOrigins() shouldBe emptyList()
            memo.assignedSourceId() shouldBe null
            memo shouldBe sourceMemo
        }
        entries[1].memo.toJsonObject() shouldBe sourceMemo
    }

    @Test
    fun `ordinary single source backup remains usable`() {
        val chapter = BackupChapter("/chapter", "Chapter", read = true, lastPageRead = 12)
        val manga =
            BackupManga(1, "/series", chapters = listOf(chapter), history = listOf(BackupHistory("/chapter", 9)))
        val result = OfficialBackupConverter.convert(manga).single()
        result.chapters.single().url shouldBe "/chapter"
        result.chapters.single().lastPageRead shouldBe 12L
        result.history shouldBe manga.history
    }

    @Test
    fun `history is remapped even when chapter export is disabled`() {
        val manga = manga(chapter(listOf(primary, alternate), assigned = 2))
        manga.history = listOf(BackupHistory("mihon://number:1", 123))
        val entries = OfficialBackupConverter.convert(manga, includeChapters = false)
        entries.all { it.chapters.isEmpty() } shouldBe true
        entries[1].history.single().url shouldBe alternate.url
    }

    @Test
    fun `unresolvable removed sources fail rather than silently losing progress`() {
        assertThrows<IOException> {
            OfficialBackupConverter.convert(manga(chapter(listOf(origin(3, "/removed/1")))))
        }
        assertThrows<IOException> {
            OfficialBackupConverter.convert(manga(BackupChapter("mihon://number:1", "Chapter")))
        }
        assertThrows<IOException> {
            OfficialBackupConverter.convert(manga(chapter(listOf(primary), assigned = 2)))
        }
    }

    @Test
    fun `export is readable by the existing whole backup protobuf decoder`() {
        val entries = OfficialBackupConverter.convert(manga(chapter(listOf(primary, alternate))))
        val buffer = Buffer()
        val writer = BackupWriter(buffer, ProtoBuf)
        entries.forEach(writer::writeEntry)
        writer.writeMetadata(Backup(emptyList()))
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), buffer.readByteArray())
        decoded.backupManga.map { it.chapters.single().url } shouldBe listOf(primary.url, alternate.url)
    }

    @Test
    fun `old worker options retain native backup mode and new options round trip`() {
        BackupOptions.fromBooleanArray(BackupOptions().asBooleanArray().take(10).toBooleanArray())
            .officialCompatibility shouldBe false
        val options = BackupOptions(officialCompatibility = true)
        BackupOptions.fromBooleanArray(options.asBooleanArray()) shouldBe options
    }

    private fun origin(source: Long, url: String) = ChapterOrigin(
        source,
        url,
        "Chapter 1",
        "Group",
        100,
        1F,
        sourceMemo,
    )

    private fun chapter(origins: List<ChapterOrigin>, assigned: Long? = null) = BackupChapter(
        "mihon://number:1",
        "Chapter 1",
        bookmark = true,
        lastPageRead = 17,
        memo = emptyMemo.withChapterOrigins(origins).withAssignedSource(assigned).toByteArray(),
    )

    private fun manga(chapter: BackupChapter) = BackupManga(
        1,
        "/series",
        "Title",
        chapters = listOf(chapter),
        initialized = true,
        memo = emptyMemo.withLinkedSources(listOf(LinkedSourceManga(2, "/other-series", "Alternate title", sourceMemo)))
            .withStorageSources(setOf(1, 2)).toByteArray(),
    )
}
