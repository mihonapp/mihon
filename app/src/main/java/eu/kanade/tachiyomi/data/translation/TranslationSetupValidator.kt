package eu.kanade.tachiyomi.data.translation

import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

class TranslationSetupValidator(
    private val preferences: TranslationPreferences = Injekt.get(),
    private val listModels: suspend (String) -> List<GeminiModel> = { apiKey ->
        Injekt.get<GeminiTranslationClient>().listModels(apiKey)
    },
    private val testGenerateContent: suspend (String, String) -> Unit = { apiKey, model ->
        Injekt.get<GeminiTranslationClient>().testGenerateContent(apiKey, model)
    },
) {
    fun readiness(): TranslationSetupReadiness {
        val apiKey = preferences.geminiApiKey.get().trim()
        val translationModel = preferences.geminiModel.get().ifBlank { DEFAULT_GEMINI_TRANSLATION_MODEL }
        val inpaintEnabled = preferences.enableInpaint.get()
        val inpaintModel = preferences.geminiInpaintModel.get().trim()
        val currentFingerprint = setupFingerprint(apiKey, translationModel, inpaintModel.takeIf { inpaintEnabled })
        val apiKeyPresent = apiKey.isNotBlank()
        val statusReady = preferences.setupStatus.get() == SETUP_STATUS_READY
        val fingerprintReady = preferences.setupFingerprint.get() == currentFingerprint
        val translationModelReady = preferences.setupTestedTranslationModel.get() == translationModel
        val inpaintModelReady = !inpaintEnabled || preferences.setupTestedInpaintModel.get() == inpaintModel
        val ready = apiKeyPresent && statusReady && fingerprintReady && translationModelReady && inpaintModelReady
        return TranslationSetupReadiness(
            ready = ready,
            apiKeyPresent = apiKeyPresent,
            translationModelReady = ready && translationModelReady,
            inpaintModelReady = !inpaintEnabled || (ready && inpaintModelReady),
            inpaintRequired = inpaintEnabled,
            translationModel = translationModel,
            inpaintModel = inpaintModel,
            testedAt = preferences.setupTestedAt.get(),
            message = when {
                ready -> preferences.setupMessage.get().ifBlank { "Translation setup ready" }
                !apiKeyPresent -> "Add a Gemini API key, then test setup."
                preferences.setupStatus.get() == SETUP_STATUS_FAILED -> {
                    preferences.setupMessage.get().ifBlank { "Translation setup test failed. Test setup again." }
                }
                preferences.setupFingerprint.get().isBlank() -> "Test translation setup before translating."
                else -> "Translation setup changed. Test setup again."
            },
        )
    }

    suspend fun testSetup(): TranslationSetupResult {
        val apiKey = preferences.geminiApiKey.get().trim()
        if (apiKey.isBlank()) {
            return fail("Add a Gemini API key, then test setup.")
        }

        val translationModel = preferences.geminiModel.get().ifBlank { DEFAULT_GEMINI_TRANSLATION_MODEL }
        val inpaintEnabled = preferences.enableInpaint.get()
        val inpaintModel = preferences.geminiInpaintModel.get().trim()

        return runCatching {
            val models = listModels(apiKey)
            val ids = models.map { it.id }.toSet()
            require(translationModel in ids) {
                "Selected translation model is unavailable: $translationModel"
            }
            if (inpaintEnabled) {
                require(inpaintModel.isNotBlank()) { "Select an inpaint model, or turn off inpaint." }
                require(inpaintModel in ids) {
                    "Selected inpaint model is unavailable: $inpaintModel"
                }
            }

            testGenerateContent(apiKey, translationModel)
            val fingerprint = setupFingerprint(apiKey, translationModel, inpaintModel.takeIf { inpaintEnabled })
            preferences.setupFingerprint.set(fingerprint)
            preferences.setupTestedTranslationModel.set(translationModel)
            preferences.setupTestedInpaintModel.set(inpaintModel.takeIf { inpaintEnabled }.orEmpty())
            preferences.setupTestedAt.set(System.currentTimeMillis())
            preferences.setupStatus.set(SETUP_STATUS_READY)
            preferences.setupMessage.set("Translation setup ready")

            TranslationSetupResult(
                ready = true,
                message = "Translation setup ready",
                models = models,
            )
        }.getOrElse { error ->
            fail(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    private fun fail(message: String): TranslationSetupResult {
        preferences.setupFingerprint.set("")
        preferences.setupStatus.set(SETUP_STATUS_FAILED)
        preferences.setupMessage.set(message)
        return TranslationSetupResult(
            ready = false,
            message = message,
            models = emptyList(),
        )
    }

    companion object {
        const val SETUP_STATUS_READY = "ready"
        const val SETUP_STATUS_FAILED = "failed"

        fun setupFingerprint(
            apiKey: String,
            translationModel: String,
            inpaintModel: String?,
        ): String {
            val input = listOf(apiKey.trim(), translationModel, inpaintModel.orEmpty()).joinToString("\u001f")
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString(separator = "") { "%02x".format(it) }
        }
    }
}

data class TranslationSetupReadiness(
    val ready: Boolean,
    val apiKeyPresent: Boolean,
    val translationModelReady: Boolean,
    val inpaintModelReady: Boolean,
    val inpaintRequired: Boolean,
    val translationModel: String,
    val inpaintModel: String,
    val testedAt: Long,
    val message: String,
)

data class TranslationSetupResult(
    val ready: Boolean,
    val message: String,
    val models: List<GeminiModel>,
)
