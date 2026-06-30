package eu.kanade.tachiyomi.ui.browse.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateSearchScreenModel(
    val mangaId: Long,
    getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : SearchScreenModel() {

    private val migrationSources by lazy { sourcePreferences.migrationSources.get() }

    override val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { migrationSources.indexOf(it.id) },
        )
    }

    private val _chapterCountDeltas = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val chapterCountDeltas: StateFlow<Map<Long, Int>> = _chapterCountDeltas.asStateFlow()

    private val requestedIds = mutableSetOf<Long>()
    private val semaphore = Semaphore(5)

    @Volatile
    private var fromChapterCountCache: Int? = null
    private val fromChapterCountMutex = Mutex()

    init {
        screenModelScope.launch {
            val manga = getManga.await(mangaId)!!
            mutableState.update {
                it.copy(
                    from = manga,
                    searchQuery = manga.title,
                )
            }
            search()
        }
    }

    override fun getEnabledSources(): List<Source> {
        return migrationSources.mapNotNull { sourceManager.get(it) }
    }

    fun loadChapterCountDelta(manga: Manga) {
        val enabled = sourcePreferences.migrationShowChapterCountDelta.get()
        val fromId = state.value.from?.id
        val alreadyRequested = manga.id in requestedIds

        if (!shouldRequestChapterCountDelta(manga.id, fromId, enabled, alreadyRequested)) return

        requestedIds.add(manga.id)

        screenModelScope.launchIO {
            try {
                semaphore.withPermit {
                    val fromManga = state.value.from ?: return@withPermit
                    val fromChapterCount = fromChapterCount(fromManga) ?: return@withPermit
                    val candidateChapterCount = fetchChapterCount(manga) ?: return@withPermit
                    val delta = chapterCountDelta(fromChapterCount, candidateChapterCount)
                    _chapterCountDeltas.update { it + (manga.id to delta) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch chapter count delta for manga ${manga.id}" }
            }
        }
    }

    private suspend fun fromChapterCount(fromManga: Manga): Int? {
        fromChapterCountCache?.let { return it }
        return fromChapterCountMutex.withLock {
            fromChapterCountCache ?: fetchChapterCount(fromManga)?.also { fromChapterCountCache = it }
        }
    }

    private suspend fun fetchChapterCount(manga: Manga): Int? {
        val source = sourceManager.get(manga.source) ?: return null
        val update = source.getMangaUpdate(
            manga = manga.toSManga(),
            chapters = emptyList(),
            fetchDetails = false,
            fetchChapters = true,
        )
        return update.chapters.size
    }
}

internal fun chapterCountDelta(fromCount: Int, candidateCount: Int): Int {
    return candidateCount - fromCount
}

internal fun shouldRequestChapterCountDelta(
    mangaId: Long,
    fromId: Long?,
    enabled: Boolean,
    alreadyRequested: Boolean,
): Boolean {
    return enabled && mangaId != fromId && !alreadyRequested
}
