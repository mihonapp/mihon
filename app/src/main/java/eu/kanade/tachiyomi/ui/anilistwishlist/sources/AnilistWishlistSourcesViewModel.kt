package eu.kanade.tachiyomi.ui.anilistwishlist.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class AnilistWishlistSourcesViewModel(
    @Assisted private val englishTitle: String?,
    @Assisted private val romajiTitle: String?,
    sourceRepository: SourceRepository,
    sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            englishTitle: String?,
            romajiTitle: String?,
        ): AnilistWishlistSourcesViewModel
    }

    private val searchTitles = listOfNotNull(
        englishTitle?.trim()?.takeIf(String::isNotBlank),
        romajiTitle?.trim()?.takeIf(String::isNotBlank),
    ).distinct()

    init {
        viewModelScope.launch {
            combine(
                sourceRepository.getOnlineSources(),
                sourcePreferences.enabledLanguages.changes(),
            ) { sources, enabledLanguages ->
                sources.filter { it.lang in enabledLanguages }
            }
                .catch { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
                .collectLatest { sources ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            sources = sources.sortedWith(
                                compareBy(String.CASE_INSENSITIVE_ORDER) { source -> source.name },
                            ),
                            results = sources.associate { it.id to SearchStatus.Loading },
                        )
                    }

                    if (searchTitles.isEmpty()) {
                        _state.update {
                            it.copy(
                                results = sources.associate { source ->
                                    source.id to SearchStatus.Error
                                },
                            )
                        }
                        return@collectLatest
                    }

                    withContext(Dispatchers.IO.limitedParallelism(5)) {
                        coroutineScope {
                            sources.map { source ->
                                async {
                                    val status = checkSource(source)
                                    _state.update {
                                        it.copy(results = it.results + (source.id to status))
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }
        }
    }

    private suspend fun checkSource(source: Source): SearchStatus {
        var hasResults = false
        var hasError = false

        for (query in searchTitles) {
            try {
                val onlineSource = sourceManager.getOrStub(source.id)
                val page = onlineSource.getSearchManga(
                    1,
                    query,
                    onlineSource.getFilterList(),
                )
                if (page.mangas.isNotEmpty()) hasResults = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                hasError = true
            }
        }

        return when {
            hasResults -> SearchStatus.HasResults
            hasError -> SearchStatus.Error
            else -> SearchStatus.NoResults
        }
    }

    fun toggleHasResults() {
        _state.update { it.copy(onlyShowHasResults = !it.onlyShowHasResults) }
    }

    enum class SearchStatus {
        Loading,
        HasResults,
        NoResults,
        Error,
    }

    data class State(
        val isLoading: Boolean = true,
        val sources: List<Source> = emptyList(),
        val error: String? = null,
        val results: Map<Long, SearchStatus> = emptyMap(),
        val onlyShowHasResults: Boolean = false,
    )
}
