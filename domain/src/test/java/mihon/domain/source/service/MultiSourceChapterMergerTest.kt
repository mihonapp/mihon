package mihon.domain.source.service

import eu.kanade.tachiyomi.source.model.SChapter
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mihon.domain.source.model.ChapterOrigin
import mihon.domain.source.model.LinkedSourceManga
import mihon.domain.source.model.assignedSourceId
import mihon.domain.source.model.chapterOrigins
import mihon.domain.source.model.linkedSources
import mihon.domain.source.model.storageSources
import mihon.domain.source.model.withAssignedSource
import mihon.domain.source.model.withChapterOrigins
import mihon.domain.source.model.withLinkedSources
import mihon.domain.source.model.withStorageSources
import mihon.domain.source.model.withoutLinkedSources
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class MultiSourceChapterMergerTest {
    @Test
    fun `abbreviated volume and season labels do not collapse repeated chapter numbers`() {
        val chapters = listOf(remote(1f, "/v1", "v1 Ch.1"), remote(1f, "/s2", "s2 Ch.1"))
        merge(chapters, chapters).size shouldBe 4
    }

    @Test
    fun `refresh retains the current provider selected by the download queue`() {
        val chosen = ChapterOrigin.from(2, remote(1f, "/b"))
        val existing = Chapter.create().copy(
            id = 1,
            url = "/original",
            name = "Chapter 1",
            chapterNumber = 1.0,
            memo = JsonObject(emptyMap()).withChapterOrigins(listOf(chosen)).withAssignedSource(2),
        )
        val merged = merge(listOf(remote(1f, "/a")), listOf(remote(1f, "/b")), listOf(existing))
        merged.single().memo.chapterOrigins().first().sourceId shouldBe 2L
        merged.single().url shouldBe "/original"
    }

    @Test
    fun `removing a link retains storage source history and hides app keys from extensions`() {
        val memo = JsonObject(emptyMap())
            .withLinkedSources(listOf(LinkedSourceManga(2, "/title", "Title")))
            .withStorageSources(setOf(1, 2))
            .withLinkedSources(emptyList())
        memo.linkedSources() shouldBe emptyList()
        memo.storageSources(1) shouldBe setOf(1L, 2L)
        memo.withoutLinkedSources() shouldBe JsonObject(emptyMap())
    }

    @Test
    fun `a source with multiple scanlations contributes only one alternative per chapter`() {
        val merged = merge(listOf(remote(1f, "/a"), remote(1f, "/b")), listOf(remote(1f, "/c")))
        merged.single().memo.chapterOrigins().map { it.sourceId } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `partially read variant is retained when existing scanlations are combined`() {
        val downloaded = Chapter.create().copy(id = 1, url = "/a", name = "Chapter 1", chapterNumber = 1.0)
        val partial = downloaded.copy(id = 2, url = "/b", lastPageRead = 5)
        val merged = merge(
            listOf(remote(1f, "/a")),
            listOf(remote(1f, "/c")),
            listOf(downloaded, partial),
            setOf(1, 2),
        )
        merged.single().url shouldBe "/b"
    }

    private fun remote(number: Float, url: String = "/$number", name: String = "Chapter $number") =
        SChapter.create().also {
            it.url = url
            it.name = name
            it.chapter_number = number
        }

    private fun merge(
        a: List<SChapter>,
        b: List<SChapter>,
        existing: List<Chapter> = emptyList(),
        protected: Set<Long> = emptySet(),
    ) = MultiSourceChapterMerger.merge(1, listOf(1L to a, 2L to b), existing, protected)

    @Test
    fun `overlapping chapters are deduplicated with alternatives but no download assignment`() {
        val chapters = (1..6).map { remote(it.toFloat()) }
        val merged = merge(chapters, chapters).asReversed()
        merged.size shouldBe 6
        merged.map { it.memo.chapterOrigins().first().sourceId } shouldBe List(6) { 1L }
        merged.all { it.memo.assignedSourceId() == null } shouldBe true
        merged.all { it.memo.chapterOrigins().size == 2 } shouldBe true
    }

    @Test
    fun `queuing a subset assigns only those chapters and refresh preserves that subset`() {
        val remote = (1..4).map { remote(it.toFloat()) }
        val merged = merge(remote, remote).asReversed().mapIndexed { i, raw ->
            Chapter.create().copy(
                id = i.toLong() + 1,
                url = raw.url,
                name = raw.name,
                chapterNumber = raw.chapter_number.toDouble(),
                memo = raw.memo,
            )
        }
        val queued = ChapterDownloadPlanner.plan(listOf(merged[1], merged[3]), 1, setOf(1, 2), emptyMap())
        queued.map { it.assignedSourceId() } shouldBe listOf(1L, 2L)
        val queuedById = queued.associateBy { it.id }
        val current = merged.map { queuedById[it.id] ?: it }
        val refreshed = MultiSourceChapterMerger.merge(
            1,
            listOf(1L to remote, 2L to remote),
            current,
            queuedById.keys,
            queuedById.keys,
        ).asReversed()
        refreshed.map { it.memo.assignedSourceId() } shouldBe listOf(null, 1L, null, 2L)
        refreshed.all { it.memo.chapterOrigins().size == 2 } shouldBe true
    }

    @Test
    fun `bookmarking an unqueued chapter does not assign a provider`() {
        val old = Chapter.create().copy(
            id = 5,
            url = "/original",
            name = "Chapter 1",
            chapterNumber = 1.0,
            bookmark = true,
            memo = JsonObject(emptyMap()).withChapterOrigins(listOf(ChapterOrigin.from(2, remote(1f)))),
        )
        merge(listOf(remote(1f)), listOf(remote(1f)), listOf(old), setOf(5))
            .single().memo.assignedSourceId() shouldBe null
    }

    @Test
    fun `legacy downloaded chapter keeps its actual provider as a saved assignment`() {
        val old = Chapter.create().copy(
            id = 5,
            url = "/original",
            name = "Chapter 1",
            chapterNumber = 1.0,
            memo = JsonObject(emptyMap()).withChapterOrigins(listOf(ChapterOrigin.from(2, remote(1f)))),
        )
        val refreshed = MultiSourceChapterMerger.merge(
            1,
            listOf(1L to listOf(remote(1f)), 2L to listOf(remote(1f))),
            listOf(old),
            setOf(5),
            setOf(5),
        ).single()
        refreshed.memo.assignedSourceId() shouldBe 2L
        refreshed.url shouldBe old.url
    }

    @Test
    fun `chapters only available on one source are included`() {
        val merged = merge(listOf(remote(1f)), listOf(remote(2f))).asReversed()
        merged.map { it.chapter_number } shouldBe listOf(1f, 2f)
        merged.last().memo.chapterOrigins().first().sourceId shouldBe 2L
    }

    @Test
    fun `unknown chapters and specials are not guessed to be equivalent`() {
        val chapters = listOf(remote(-1f, "/extra", "Extra"), remote(1f, "/special", "Special 1"))
        merge(chapters, chapters).size shouldBe 4
    }

    @Test
    fun `decimal chapter identity survives float conversion`() {
        val existing = Chapter.create().copy(id = 7, url = "/original", name = "Chapter 1.1", chapterNumber = 1.1)
        val merged = merge(listOf(remote(1.1f)), listOf(remote(1.1f)), listOf(existing))
        merged.single().url shouldBe "/original"
    }

    @Test
    fun `downloaded and partially read chapters retain their provider and storage names`() {
        val origin = ChapterOrigin.from(2, remote(2f, "/b2"))
        val existing = Chapter.create().copy(
            id = 8,
            url = "mihon://number:2.0",
            name = "Existing chapter",
            chapterNumber = 2.0,
            memo = JsonObject(emptyMap()).withChapterOrigins(listOf(origin)),
        )
        val merged = merge(listOf(remote(2f)), listOf(remote(2f, "/new-b2")), listOf(existing), setOf(8))
        merged.single().url shouldBe existing.url
        merged.single().name shouldBe existing.name
        merged.single().memo.chapterOrigins().first() shouldBe origin
    }

    @Test
    fun `protected chapters remain when removed from remote lists`() {
        val existing = Chapter.create().copy(id = 9, url = "/3", name = "Chapter 3", chapterNumber = 3.0)
        merge(emptyList(), emptyList(), listOf(existing), setOf(9)).single().url shouldBe "/3"
    }

    @Test
    fun `same remote URL on different sources does not collide for unknown chapters`() {
        val merged = merge(listOf(remote(-1f, "/same", "A")), listOf(remote(-1f, "/same", "B")))
        merged.map { it.url }.distinct().size shouldBe 2
    }

    @Test
    fun `confirmed links and origin metadata round trip without overwriting source metadata`() {
        val metadata = JsonObject(mapOf("extension" to JsonPrimitive("value")))
        val links = listOf(LinkedSourceManga(2, "/title", "Title", metadata))
        val saved = metadata.withLinkedSources(links)
        saved.linkedSources() shouldBe links
        saved["extension"] shouldBe metadata["extension"]
        val origin = ChapterOrigin.from(2, remote(1f).also { it.memo = metadata })
        metadata.withChapterOrigins(listOf(origin)).chapterOrigins().single().toSChapter().memo shouldBe metadata
    }
}
