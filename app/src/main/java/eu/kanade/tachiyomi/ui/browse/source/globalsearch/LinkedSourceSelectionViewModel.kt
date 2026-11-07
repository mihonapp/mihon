package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import mihon.domain.source.interactor.FetchLinkedChapters
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.source.model.LinkedSourceManga
import mihon.domain.source.model.linkedSources
import mihon.domain.source.model.withoutLinkedSources
import mihon.domain.source.service.LinkedSourceScanlators
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class LinkedSourceSelectionViewModel(
    @Assisted private val mangaId: Long,
    @Assisted private val sessionId: String,
    private val sessions: LinkedSourceDraftSessions,
    private val mangaRepository: MangaRepository,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val fetchLinkedChapters: FetchLinkedChapters,
    private val sourceManager: SourceManager,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ViewModel() {

    val manga = mangaRepository.getMangaByIdAsFlow(mangaId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val messages = Channel<String>(Channel.UNLIMITED)
    val errors = messages.receiveAsFlow()
    private val session = sessions.acquire(sessionId)
    val draft = session.draft.state
    private var scanlatorSearchJob: Job? = null
    private val _scanlatorSelection = MutableStateFlow<ScanlatorSelection?>(null)
    val scanlatorSelection = _scanlatorSelection.asStateFlow()

    data class ScanlatorSelection(
        val target: Manga,
        val sourceName: String,
        val loading: Boolean = true,
        val available: Set<String> = emptySet(),
        val summaries: Map<String, LinkedSourceScanlators.Summary> = emptyMap(),
        val error: String? = null,
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            session.draft.initialize(mangaRepository.getMangaById(mangaId).linkedSources())
        }
    }

    override fun onCleared() {
        sessions.release(sessionId)
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long, sessionId: String): LinkedSourceSelectionViewModel
    }

    fun toggleSource(target: Manga) {
        if (scanlatorSelection.value != null || draft.value.links == null || draft.value.saving ||
            draft.value.applied
        ) {
            return
        }
        if (draft.value.links.orEmpty().any { it.sourceId == target.source && it.url == target.url }) {
            session.draft.remove(target.source)
            return
        }
        val pending = ScanlatorSelection(target, "")
        _scanlatorSelection.value = pending
        scanlatorSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = mangaRepository.getMangaById(mangaId)
                if (!current.favorite || target.source == current.source) {
                    _scanlatorSelection.value = null
                    return@launch
                }
                val named = pending.copy(sourceName = sourceManager.getOrStub(target.source).name)
                _scanlatorSelection.value = named
                val chapters = fetchLinkedChapters.inspectScanlators(target.toLink())
                val summaries = LinkedSourceScanlators.summaries(target.title, chapters)
                val available = summaries.keys
                if (available.size > 1) {
                    _scanlatorSelection.value =
                        named.copy(loading = false, available = available, summaries = summaries)
                } else {
                    session.draft.select(target.toLink())
                    _scanlatorSelection.value = null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _scanlatorSelection.value = pending.copy(loading = false, error = e.message.orEmpty())
            }
        }
    }

    fun dismissScanlators() {
        scanlatorSearchJob?.cancel()
        _scanlatorSelection.value = null
    }

    fun retryScanlators() {
        val target = scanlatorSelection.value?.target ?: return
        dismissScanlators()
        toggleSource(target)
    }

    fun confirmScanlators(scanlators: Set<String>) {
        val pending = scanlatorSelection.value ?: return
        if (pending.loading || pending.error != null || scanlators.isEmpty() ||
            !pending.available.containsAll(scanlators)
        ) {
            return
        }
        session.draft.select(pending.target.toLink(scanlators))
        _scanlatorSelection.value = null
    }

    private fun Manga.toLink(scanlators: Set<String>? = null) = LinkedSourceManga(
        source,
        url,
        title,
        memo.withoutLinkedSources(),
        scanlators,
    )

    fun clear() {
        if (draft.value.saving) return
        dismissScanlators()
        session.draft.clear()
    }

    fun apply() {
        if (scanlatorSelection.value != null) return
        val links = session.draft.beginApply() ?: return
        appScope.launch(Dispatchers.IO) {
            try {
                val current = mangaRepository.getMangaById(mangaId)
                check(current.favorite) { "Title is no longer in the library" }
                mangaRepository.setSourceLinks(mangaId, links)
                session.draft.finishApply(success = true)
                val result = updateMangaFromRemote(
                    mangaRepository.getMangaById(mangaId),
                    fetchDetails = false,
                    fetchChapters = true,
                ).getOrThrow()
                if (result.sourceFailures.isNotEmpty()) messages.send(result.sourceFailures.joinToString("\n"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!draft.value.applied) session.draft.finishApply(success = false)
                messages.send(e.message.orEmpty())
            }
        }
    }
}
