package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ai.GeminiService
import eu.kanade.domain.base.BasePreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import eu.kanade.tachiyomi.data.export.LibraryExporter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RecommendationsScreenModel(
    private val getFavorites: GetFavorites = Injekt.get(),
    private val geminiService: GeminiService = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
) : StateScreenModel<RecommendationsScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        // Load persisted AI recommendations
        val persistedRecommendations = basePreferences.aiRecommendations().get()
        if (persistedRecommendations.isNotBlank()) {
            val parsed = parseRecommendations(persistedRecommendations)
            mutableState.update { 
                it.copy(
                    aiRecommendations = persistedRecommendations,
                    parsedRecommendations = parsed
                ) 
            }
        }
    }

    private fun parseRecommendations(text: String): ImmutableList<String> {
        return text.lines()
            .map { it.trim() }
            .filter { line ->
                // Filter out empty lines and common non-title patterns
                line.isNotBlank() &&
                line.length >= 2 &&
                !line.startsWith("#") &&
                !line.startsWith("Here") &&
                !line.startsWith("Based on") &&
                !line.contains("recommendations") &&
                !line.contains("library") &&
                !line.contains(":") &&
                // Remove any remaining markdown or formatting
                !line.startsWith("*") &&
                !line.startsWith("-") &&
                !line.matches(Regex("^\\d+\\..*")) && // Remove numbered lists
                line.length < 100 // Titles shouldn't be super long
            }
            .distinct()
            .toImmutableList()
    }

    fun getAiRecommendations() {
        val apiKey = basePreferences.geminiAiApiKey().get()
        if (apiKey.isBlank()) {
            mutableState.update {
                it.copy(recommendationsError = "Gemini API key not configured. Please set it in Settings > AI.")
            }
            return
        }

        screenModelScope.launchIO {
            try {
                mutableState.update { it.copy(isLoadingRecommendations = true, recommendationsError = null) }

                val favorites = getFavorites.await()
                if (favorites.isEmpty()) {
                    mutableState.update {
                        it.copy(
                            isLoadingRecommendations = false,
                            recommendationsError = "No manga in library to analyze."
                        )
                    }
                    return@launchIO
                }

                val csvData = LibraryExporter.generateCsvData(
                    favorites,
                    LibraryExporter.ExportOptions(
                        includeTitle = true,
                        includeAuthor = true,
                        includeArtist = true
                    )
                )

                val recommendations = geminiService.getRecommendations(csvData)

                // Persist recommendations locally
                basePreferences.aiRecommendations().set(recommendations)

                // Parse recommendations into list
                val parsed = parseRecommendations(recommendations)

                if (parsed.isEmpty()) {
                    mutableState.update {
                        it.copy(
                            isLoadingRecommendations = false,
                            recommendationsError = "Failed to parse recommendations. Try again."
                        )
                    }
                    return@launchIO
                }

                mutableState.update {
                    it.copy(
                        isLoadingRecommendations = false,
                        aiRecommendations = recommendations,
                        parsedRecommendations = parsed
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update {
                    it.copy(
                        isLoadingRecommendations = false,
                        recommendationsError = "Failed to get recommendations: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun clearAiRecommendations() {
        screenModelScope.launchIO {
            basePreferences.aiRecommendations().set("")
            mutableState.update {
                it.copy(
                    aiRecommendations = null,
                    recommendationsError = null,
                    parsedRecommendations = persistentListOf()
                )
            }
        }
    }

    sealed interface Event {
        data object FailedFetchingRecommendations : Event
    }

    @Immutable
    data class State(
        val isLoadingRecommendations: Boolean = false,
        val aiRecommendations: String? = null,
        val recommendationsError: String? = null,
        val parsedRecommendations: ImmutableList<String> = persistentListOf(),
    )
}