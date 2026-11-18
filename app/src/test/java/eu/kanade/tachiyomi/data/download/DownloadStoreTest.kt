package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

class DownloadStoreTest {

    @Test
    fun `restore drops chapters hidden since serialization and retains visible queue order`() = runTest {
        val fixture = Fixture()
        val first = fixture.chapter(30)
        val hiddenLater = fixture.chapter(10)
        val last = fixture.chapter(20)
        fixture.store.addAll(listOf(first, hiddenLater, last).map(fixture::download))

        // The saved queue predates the hide; only the current database row knows it is hidden.
        fixture.chapters[hiddenLater.id] = hiddenLater.copy(hidden = true)
        fixture.chapters[first.id] = first.copy(name = "Updated chapter title", read = true)

        // A new store instance models an application restart against the same persisted queue.
        val restored = fixture.newStore().restore()

        restored.map { it.chapter.id } shouldBe listOf(30L, 20L)
        restored.first().chapter.name shouldBe "Updated chapter title"
        restored.first().chapter.read shouldBe true
        restored.all { !it.chapter.hidden } shouldBe true
        fixture.saved.isEmpty() shouldBe true
    }

    @Test
    fun `restore uses current visibility and discards missing chapter rows`() = runTest {
        val fixture = Fixture()
        val unhidden = fixture.chapter(1).copy(hidden = true)
        val deleted = fixture.chapter(2)
        fixture.store.addAll(listOf(unhidden, deleted).map(fixture::download))
        fixture.chapters[unhidden.id] = unhidden.copy(hidden = false)
        fixture.chapters.remove(deleted.id)

        fixture.newStore().restore().map { it.chapter } shouldBe listOf(unhidden.copy(hidden = false))
        fixture.saved.isEmpty() shouldBe true
    }

    private class Fixture {
        val saved = linkedMapOf<String, String>()
        val chapters = mutableMapOf<Long, Chapter>()
        private val context = mockk<Context>()
        private val preferences = mockk<SharedPreferences>()
        private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        private val source = mockk<HttpSource>()
        private val sourceManager = mockk<SourceManager>()
        private val getManga = mockk<GetManga>()
        private val getChapter = mockk<GetChapter>()
        private val manga = Manga.create().copy(id = 1, source = 7)

        init {
            every { context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE) } returns preferences
            // Deliberately return reverse insertion order: restore must use the serialized order.
            every { preferences.all } answers { saved.entries.reversed().associate { it.key to it.value } }
            every { preferences.edit() } returns editor
            every { editor.putString(any(), any()) } answers {
                saved[firstArg()] = requireNotNull(secondArg<String?>())
                editor
            }
            every { editor.clear() } answers {
                saved.clear()
                editor
            }
            coEvery { sourceManager.get(manga.source) } returns source
            coEvery { getManga.await(manga.id) } returns manga
            coEvery { getChapter.await(any<Long>()) } answers { chapters[firstArg()] }
        }

        val store = newStore()

        fun newStore() = DownloadStore(context, sourceManager, Json, getManga, getChapter)

        fun chapter(id: Long) = Chapter.create().copy(id = id, mangaId = manga.id).also { chapters[id] = it }

        fun download(chapter: Chapter) = Download(source, manga, chapter)
    }
}
