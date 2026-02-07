package eu.kanade.tachiyomi.data.translation

import android.content.Context
import eu.kanade.tachiyomi.data.translation.engine.CustomHttpTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.DeepLTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.DeepSeekTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslateScraperEngine
import eu.kanade.tachiyomi.data.translation.engine.HuggingFaceTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.LibreTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.OllamaTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenAITranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.SystranTranslateEngine
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Manager for translation engines.
 * Provides access to available engines and manages the selected engine.
 */
class TranslationEngineManager(
    private val context: Context = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) {
    /**
     * List of all available translation engines.
     */
    val engines: List<TranslationEngine> by lazy {
        listOf(
            LibreTranslateEngine(), // Free, open-source
            OpenAITranslateEngine(), // Paid, high quality
            DeepSeekTranslateEngine(), // Paid, affordable
            OllamaTranslateEngine(), // Local AI, free
            HuggingFaceTranslateEngine(), // Free, limited languages
            SystranTranslateEngine(), // Paid, enterprise quality
            DeepLTranslateEngine(), // Paid, high quality
            GoogleTranslateEngine(), // Paid, comprehensive
            GoogleTranslateScraperEngine(), // Free, scraper
            CustomHttpTranslateEngine(), // Custom HTTP endpoint
        )
    }

    /**
     * Get the currently selected translation engine.
     */
    fun getSelectedEngine(): TranslationEngine {
        val selectedId = preferences.selectedEngineId().get()
        return engines.find { it.id == selectedId } ?: engines.first()
    }

    /**
     * Get an engine by its ID.
     */
    fun getEngineById(id: Long): TranslationEngine? {
        return engines.find { it.id == id }
    }

    /**
     * Set the selected translation engine.
     */
    fun setSelectedEngine(engine: TranslationEngine) {
        preferences.selectedEngineId().set(engine.id)
    }

    /**
     * Get engines that work offline (no rate limiting needed).
     */
    fun getOfflineEngines(): List<TranslationEngine> {
        return engines.filter { it.isOffline }
    }

    /**
     * Get engines that require rate limiting.
     */
    fun getRateLimitedEngines(): List<TranslationEngine> {
        return engines.filter { it.isRateLimited }
    }

    /**
     * Check if the selected engine requires rate limiting.
     */
    fun selectedEngineRequiresRateLimit(): Boolean {
        return getSelectedEngine().isRateLimited
    }

    /**
     * Get the currently configured engine, or null if not configured.
     */
    fun getEngine(): TranslationEngine? {
        val engine = getSelectedEngine()
        return if (engine.isConfigured()) engine else null
    }

    /**
     * Get supported languages for the selected engine.
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return getSelectedEngine().supportedLanguages
    }

    /**
     * Engine IDs for reference.
     */
    companion object {
        const val ENGINE_GOOGLE_ML_KIT = 0L
        const val ENGINE_LIBRE_TRANSLATE = 1L
        const val ENGINE_OPENAI = 2L
        const val ENGINE_DEEPSEEK = 3L
        const val ENGINE_OLLAMA = 4L
        const val ENGINE_SYSTRAN = 5L
        const val ENGINE_DEEPL = 6L
        const val ENGINE_GOOGLE = 7L
    }
}
