package mihon.domain.source.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.chapter.model.copyFromSChapter
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import mihon.domain.source.model.ChapterOrigin
import mihon.domain.source.model.LinkedSourceManga
import mihon.domain.source.model.acceptsScanlator
import mihon.domain.source.model.linkedSources
import mihon.domain.source.model.origins
import mihon.domain.source.model.withChapterOrigins
import mihon.domain.source.model.withoutLinkedSources
import tachiyomi.data.chapter.ChapterSanitizer
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

@Inject
@SingleIn(AppScope::class)
class FetchLinkedChapters(private val sourceManager: SourceManager) {
    private val slots = Semaphore(4)

    data class Result(val chapters: List<SChapter>, val failures: List<String>)

    suspend fun inspectScanlators(link: LinkedSourceManga): List<SChapter> = slots.withPermit { loadChapters(link) }

    private suspend fun loadChapters(link: LinkedSourceManga): List<SChapter> {
        val source = sourceManager.get(link.sourceId) as? HttpSource ?: error("Source not installed")
        val remote = SManga.create().also {
            it.url = link.url
            it.title = link.title
            it.memo = link.memo
        }
        return withTimeout(30_000) {
            source.getMangaUpdate(remote, emptyList(), fetchDetails = false, fetchChapters = true).chapters
        }.also { check(it.isNotEmpty()) { "No chapters returned" } }
    }

    suspend operator fun invoke(
        manga: Manga,
        existing: List<Chapter>,
    ): Result = coroutineScope {
        val links = listOf(LinkedSourceManga(manga.source, manga.url, manga.title, manga.memo.withoutLinkedSources())) +
            manga.linkedSources()
        val results = links.map { link ->
            async {
                slots.withPermit {
                    try {
                        val source = sourceManager.get(link.sourceId) as? HttpSource ?: error("Source not installed")
                        val remote = SManga.create().also {
                            it.url = link.url
                            it.title = link.title
                            it.memo = link.memo
                        }
                        // Request a full list; incremental source state belongs to that source, not the aggregate.
                        val chapters = loadChapters(link).filter { link.acceptsScanlator(it.scanlator) }
                        @Suppress("DEPRECATION")
                        chapters.forEach {
                            it.name = with(ChapterSanitizer) { it.name.sanitize(link.title) }
                            source.prepareNewChapter(it, remote)
                        }
                        val numbers = ChapterRecognition.parseChapterNumbers(
                            link.title,
                            chapters.map { Chapter.create().copyFromSChapter(it) },
                        )
                        chapters.zip(numbers).forEach { (chapter, number) ->
                            chapter.chapter_number = number.chapter?.let(ChapterRecognition::toDouble)?.toFloat() ?: -1f
                        }
                        Triple(link.sourceId, chapters, null)
                    } catch (e: Exception) {
                        if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                        // A failing source must not erase its known chapters or their alternatives.
                        val retained = existing.flatMap { chapter ->
                            chapter.origins().ifEmpty {
                                if (link.sourceId ==
                                    manga.source
                                ) {
                                    listOf(ChapterOrigin.from(manga.source, chapter.toSChapter()))
                                } else {
                                    emptyList()
                                }
                            }
                        }.filter { it.sourceId == link.sourceId && link.acceptsScanlator(it.scanlator) }
                            .distinctBy { it.url }.map { it.toSChapter() }
                        Triple(
                            link.sourceId,
                            retained,
                            "${sourceManager.getOrStub(link.sourceId)}: ${e.message.orEmpty()}",
                        )
                    }
                }
            }
        }.awaitAll()
        check(results.any { it.third == null }) { results.mapNotNull { it.third }.joinToString("\n") }
        Result(
            results.flatMap { (sourceId, chapters, _) ->
                chapters.map { chapter ->
                    val origin = ChapterOrigin.from(sourceId, chapter)
                    SChapter.create().also {
                        it.copyFrom(chapter)
                        it.memo = chapter.memo.withChapterOrigins(listOf(origin))
                    }
                }
            },
            results.mapNotNull { it.third },
        )
    }
}
