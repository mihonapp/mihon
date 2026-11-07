package mihon.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/**
 * The identity digest decides whether a device publishes an entry, so two devices that agree on every
 * decision must compute the same one. Each case below that must not count was, or would have become,
 * an entry two real devices handed back and forth on every round.
 */
@Execution(ExecutionMode.CONCURRENT)
class SyncCodecTest {

    private val codec = SyncCodec(ProtoBuf)

    @Test
    fun `bookkeeping particular to one device does not count`() {
        assertSameIdentity { manga ->
            manga.dateAdded = 1_700_000_000_000
            manga.chapterListAt = 1_759_000_000_000
            manga.chapters.forEach { chapter ->
                chapter.dateFetch = 1_700_000_000_000
                chapter.dateUpload = 1_600_000_000_000
            }
        }
    }

    @Test
    fun `order does not count`() {
        assertSameIdentity { manga ->
            manga.chapters = manga.chapters.reversed()
            manga.history = manga.history.reversed()
            manga.tracking = manga.tracking.reversed()
            manga.categories = manga.categories.reversed()
            manga.excludedScanlators = manga.excludedScanlators.reversed()
            manga.genre = manga.genre.reversed()
        }
    }

    @Test
    fun `what the source provides does not count`() {
        assertSameIdentity { manga ->
            manga.title = "Renamed by the source"
            manga.artist = "Someone else"
            manga.author = "Someone else"
            manga.description = "A fresher blurb"
            manga.genre = listOf("Drama")
            manga.status = 2
            manga.thumbnailUrl = "https://cdn.example/cover.webp?token=rotated"
            manga.initialized = false
            manga.memo = """{"path":"/read/other/"}""".toByteArray()
            manga.chapters.forEach { chapter ->
                chapter.name = "Renamed by the source"
                chapter.scanlator = "Another group"
                chapter.chapterNumber = 99f
                chapter.sourceOrder = 99
                chapter.memo = """{"path":"/ep/other/"}""".toByteArray()
            }
        }
    }

    @Test
    fun `what the tracker reports does not count`() {
        assertSameIdentity { manga ->
            manga.tracking.forEach { track ->
                track.title = "Title on the tracker"
                track.trackingUrl = "https://tracker.example/other"
                track.totalChapters = 120
                track.score = 9f
                track.status = 2
                track.startedReadingDate = 1_700_000_000_000
                track.finishedReadingDate = 1_710_000_000_000
                track.private = true
            }
        }
    }

    @Test
    fun `every decision the user makes counts`() {
        val changes = listOf(
            change("removed from the library") { it.favorite = false },
            change("favourite decided at another time") { it.favoriteModifiedAt = 2_000 },
            change("notes") { it.notes = "Reread from episode 3" },
            change("reading mode") { it.viewer_flags = 4 },
            change("chapter sorting") { it.chapterFlags = 2 },
            change("update strategy") { it.updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE },
            change("categories") { it.categories = it.categories + 3L },
            change("excluded scanlators") { it.excludedScanlators = it.excludedScanlators + "Group C" },
            change("chapter read") { it.chapters[1].read = true },
            change("chapter bookmarked") { it.chapters[1].bookmark = true },
            change("page reached") { it.chapters[1].lastPageRead = 7 },
            change("reading decided at another time") { it.chapters[1].readModifiedAt = 2_000 },
            change("new chapter") { it.chapters = it.chapters + chapter("/ep-3/") },
            change("history read at") { it.history[0].lastRead = 2_000 },
            change("history duration") { it.history[0].readDuration = 2_000 },
            change("tracked chapter") { it.tracking[0].lastChapterRead = 5f },
            change("new tracker") {
                it.tracking = it.tracking + BackupTracking(syncId = 9, libraryId = 0, mediaId = 9)
            },
        )

        val reference = digestOf(entry())
        changes.forEach { (label, mutate) ->
            withClue(label) {
                digestOf(entry().also(mutate)) shouldNotBe reference
            }
        }
    }

    @Test
    fun `the digest leaves the published payload untouched`() {
        val payload = codec.encode(Backup(backupManga = listOf(entry())))
        codec.identityDigest(payload)

        val published = codec.decode(payload).backupManga.single()
        published.title shouldBe "A Druid's Healing Days"
        published.thumbnailUrl shouldBe "https://cdn.example/cover.webp"
        published.chapters.map { it.name } shouldBe listOf("Episode /ep-1/", "Episode /ep-2/")
    }

    @Test
    fun `a shared document is compared by content, not by how it was compressed`() {
        val document = Backup(
            backupManga = emptyList(),
            backupSources = listOf(BackupSource(name = "Source", sourceId = 2_499_283_573_021_220_255)),
        )
        val ours = codec.encode(document)
        // Another device, another zlib: same content, different bytes.
        val theirs = gzip(ProtoBuf.encodeToByteArray(Backup.serializer(), document), Deflater.NO_COMPRESSION)

        theirs.contentEquals(ours) shouldBe false
        codec.contentDigest(codec.decode(theirs)) shouldBe codec.contentDigest(codec.decode(ours))
    }

    private fun gzip(raw: ByteArray, level: Int): ByteArray {
        val out = ByteArrayOutputStream()
        object : GZIPOutputStream(out) {
            init {
                def.setLevel(level)
            }
        }.use { it.write(raw) }
        return out.toByteArray()
    }

    private fun change(label: String, mutate: (BackupManga) -> Unit) = label to mutate

    private fun assertSameIdentity(change: (BackupManga) -> Unit) {
        digestOf(entry().also(change)) shouldBe digestOf(entry())
    }

    private fun digestOf(manga: BackupManga): String =
        codec.identityDigest(codec.encode(Backup(backupManga = listOf(manga))))

    private fun entry() = BackupManga(
        source = 1L,
        url = "/series/a-druids-healing-days",
        title = "A Druid's Healing Days",
        artist = "Artist",
        author = "Author",
        description = "Blurb",
        genre = listOf("Fantasy", "Slice of life"),
        status = 1,
        thumbnailUrl = "https://cdn.example/cover.webp",
        favorite = true,
        favoriteModifiedAt = 1_000L,
        viewer_flags = 1,
        initialized = true,
        categories = listOf(1L, 2L),
        excludedScanlators = listOf("Group A", "Group B"),
        chapters = listOf(
            chapter("/ep-1/").apply {
                read = true
                lastPageRead = 30
                readModifiedAt = 1_000
            },
            chapter("/ep-2/"),
        ),
        history = listOf(
            BackupHistory(url = "/ep-1/", lastRead = 1_000, readDuration = 600),
            BackupHistory(url = "/ep-2/", lastRead = 900, readDuration = 60),
        ),
        tracking = listOf(
            BackupTracking(syncId = 1, libraryId = 10, mediaId = 100, lastChapterRead = 1f, status = 1, score = 7f),
            BackupTracking(syncId = 2, libraryId = 20, mediaId = 200, lastChapterRead = 1f),
        ),
    )

    private fun chapter(url: String) = BackupChapter(
        url = url,
        name = "Episode $url",
        scanlator = "Group A",
        chapterNumber = 1f,
    )
}
