package eu.kanade.tachiyomi.ui.browse.source.custom

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.custom.CustomNovelSource
import eu.kanade.tachiyomi.source.custom.CustomSourceConfig
import eu.kanade.tachiyomi.source.custom.CustomSourceManager
import eu.kanade.tachiyomi.source.custom.CustomSourceTemplates
import eu.kanade.tachiyomi.source.custom.SourceTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ScreenModel for managing custom sources
 */
class CustomSourcesScreenModel(
    private val customSourceManager: CustomSourceManager = Injekt.get(),
) : ScreenModel {

    private val _customSources = MutableStateFlow<List<CustomNovelSource>>(emptyList())
    val customSources: StateFlow<List<CustomNovelSource>> = _customSources.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadSources()
    }

    private fun loadSources() {
        screenModelScope.launch {
            _isLoading.value = true
            try {
                customSourceManager.customSources.collect { sources ->
                    _customSources.value = sources
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load sources: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get a specific source config by ID
     */
    fun getSourceConfig(sourceId: Long): CustomSourceConfig? {
        return _customSources.value.find { it.id == sourceId }?.config
    }

    /**
     * Create a new source config from a template
     */
    fun createFromTemplate(
        templateName: String,
        name: String,
        baseUrl: String,
    ): CustomSourceConfig? {
        return customSourceManager.fromTemplate(templateName, name, baseUrl)
    }

    /**
     * Create a new custom source
     */
    suspend fun createSource(config: CustomSourceConfig): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                customSourceManager.validateConfig(config)
                val result = customSourceManager.createSource(config)
                result.fold(
                    onSuccess = { source -> Result.success(source.id) },
                    onFailure = { Result.failure(it) },
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Update an existing custom source
     */
    suspend fun updateSource(sourceId: Long, config: CustomSourceConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                customSourceManager.validateConfig(config)
                customSourceManager.updateSource(sourceId, config)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Delete a custom source
     */
    fun deleteSource(sourceId: Long) {
        screenModelScope.launch {
            try {
                customSourceManager.deleteSource(sourceId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete source: ${e.message}"
            }
        }
    }

    /**
     * Export a source to JSON
     */
    fun exportSource(sourceId: Long): String? {
        return try {
            customSourceManager.exportSource(sourceId)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to export source: ${e.message}"
            null
        }
    }

    /**
     * Import a source from JSON
     */
    suspend fun importSource(json: String): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val result = customSourceManager.importSource(json)
                result.fold(
                    onSuccess = { source -> Result.success(source.id) },
                    onFailure = { Result.failure(it) },
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Test a custom source
     */
    suspend fun testSource(sourceId: Long): SourceTestResult? {
        return withContext(Dispatchers.IO) {
            val config = getSourceConfig(sourceId) ?: return@withContext null
            customSourceManager.testSource(config)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
