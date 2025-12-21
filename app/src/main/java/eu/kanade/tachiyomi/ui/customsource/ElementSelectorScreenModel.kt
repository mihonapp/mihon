package eu.kanade.tachiyomi.ui.customsource

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.custom.ChapterSelectors
import eu.kanade.tachiyomi.source.custom.ContentSelectors
import eu.kanade.tachiyomi.source.custom.CustomSourceConfig
import eu.kanade.tachiyomi.source.custom.CustomSourceManager
import eu.kanade.tachiyomi.source.custom.DetailSelectors
import eu.kanade.tachiyomi.source.custom.MangaListSelectors
import eu.kanade.tachiyomi.source.custom.SourceSelectors
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Screen model for managing Element Selector state and converting
 * user selections into a custom source configuration.
 */
class ElementSelectorScreenModel(
    private val initialUrl: String,
    private val customSourceManager: CustomSourceManager = Injekt.get(),
) : StateScreenModel<ElementSelectorScreenModel.State>(State()) {

    private val json = Json { prettyPrint = true }

    data class State(
        val isLoading: Boolean = false,
        val sourceName: String = "",
        val baseUrl: String = "",
        val config: SelectorConfig = SelectorConfig(),
        val savedSuccessfully: Boolean = false,
        val error: String? = null,
        // Preview data for confirmation UI
        val previewData: StepPreviewData? = null,
    )

    init {
        mutableState.update { it.copy(baseUrl = initialUrl) }
    }

    fun updateSourceName(name: String) {
        mutableState.update { it.copy(sourceName = name) }
    }

    fun updatePreviewData(preview: StepPreviewData) {
        mutableState.update { it.copy(previewData = preview) }
    }

    fun clearPreview() {
        mutableState.update { it.copy(previewData = null) }
    }

    fun saveConfig(config: SelectorConfig) {
        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }

            try {
                // Convert SelectorConfig to CustomSourceConfig
                val sourceConfig = convertToCustomSourceConfig(config)

                // Save via CustomSourceManager
                val result = customSourceManager.createSource(sourceConfig)

                result.fold(
                    onSuccess = {
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                savedSuccessfully = true,
                                config = config,
                            )
                        }
                    },
                    onFailure = { e ->
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message,
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message,
                    )
                }
            }
        }
    }

    private fun convertToCustomSourceConfig(selectorConfig: SelectorConfig): CustomSourceConfig {
        val baseUrl = selectorConfig.baseUrl.trimEnd('/')

        return CustomSourceConfig(
            name = selectorConfig.sourceName.ifEmpty { "Custom Source" },
            baseUrl = baseUrl,
            language = "en",
            popularUrl = "$baseUrl/{page}",
            latestUrl = if (selectorConfig.newNovelsSelector.isNotEmpty()) "$baseUrl/{page}" else null,
            searchUrl = selectorConfig.searchUrl.ifEmpty { "$baseUrl/?s={query}&page={page}" },
            selectors = SourceSelectors(
                popular = MangaListSelectors(
                    list = selectorConfig.trendingSelector,
                    link = selectorConfig.novelTitleSelector,
                    title = selectorConfig.novelTitleSelector,
                    cover = selectorConfig.novelCoverSelector,
                    nextPage = selectorConfig.paginationPattern,
                ),
                search = MangaListSelectors(
                    list = selectorConfig.trendingSelector, // Usually same as popular
                    link = selectorConfig.novelTitleSelector,
                    title = selectorConfig.novelTitleSelector,
                    cover = selectorConfig.novelCoverSelector,
                    nextPage = selectorConfig.paginationPattern,
                ),
                details = DetailSelectors(
                    title = selectorConfig.novelPageTitleSelector,
                    description = selectorConfig.novelDescriptionSelector,
                    cover = selectorConfig.novelCoverPageSelector,
                    genre = selectorConfig.novelTagsSelector,
                ),
                chapters = ChapterSelectors(
                    list = selectorConfig.chapterListSelector,
                    link = findItemSelector(selectorConfig.chapterItems),
                    name = findItemSelector(selectorConfig.chapterItems),
                ),
                content = ContentSelectors(
                    primary = selectorConfig.chapterContentSelector,
                    fallbacks = listOf(".chapter-content", "#chapter-content", ".content"),
                    removeSelectors = listOf(".ads", "script", ".share", ".navigation"),
                ),
            ),
        )
    }

    private fun findItemSelector(items: List<String>): String {
        if (items.isEmpty()) return ""
        if (items.size == 1) return items.first()

        // Find common parent selector
        val parts = items.map { it.split(" > ", " ") }
        val minLen = parts.minOf { it.size }

        val common = mutableListOf<String>()
        for (i in 0 until minLen - 1) {
            val part = parts.first()[i]
            if (parts.all { it.getOrNull(i) == part }) {
                common.add(part)
            } else {
                break
            }
        }

        return if (common.isNotEmpty()) {
            common.joinToString(" > ") + " > *"
        } else {
            items.first()
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    fun exportConfigAsJson(config: SelectorConfig): String {
        return json.encodeToString(convertToCustomSourceConfig(config))
    }
}

/**
 * Preview data for step confirmation UI
 */
data class StepPreviewData(
    val stepName: String,
    val detectedTitle: String? = null,
    val detectedUrl: String? = null,
    val detectedImageUrl: String? = null,
    val detectedDescription: String? = null,
    val detectedChaptersTotal: Int? = null,
    val detectedChapterFirstUrl: String? = null,
    val detectedChapterLastUrl: String? = null,
    val sampleContentStart: String? = null,
    val sampleContentEnd: String? = null,
    val searchResultsCount: Int? = null,
    val sampleSearchResults: List<String>? = null,
)
