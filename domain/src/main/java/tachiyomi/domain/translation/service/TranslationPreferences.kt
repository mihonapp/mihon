package tachiyomi.domain.translation.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    preferenceStore: PreferenceStore,
) {
    val geminiApiKey: Preference<String> = preferenceStore.getString(
        Preference.privateKey("translation_gemini_api_key"),
        "",
    )

    val geminiModel: Preference<String> = preferenceStore.getString(
        "translation_gemini_model",
        "gemini-3-flash-preview",
    )

    val geminiInpaintModel: Preference<String> = preferenceStore.getString(
        "translation_gemini_inpaint_model",
        "gemini-2.5-flash-image",
    )

    val targetLanguage: Preference<String> = preferenceStore.getString("translation_target_language", "")
    val sourceLanguage: Preference<String> = preferenceStore.getString("translation_source_language", "")
    val pipeline: Preference<String> = preferenceStore.getString("translation_pipeline", "gemini_vision")
    val ocrScript: Preference<String> = preferenceStore.getString("translation_ocr_script", "auto")

    val concurrency: Preference<Int> = preferenceStore.getInt("translation_concurrency", 1)
    val skipExistingOverlays: Preference<Boolean> = preferenceStore.getBoolean("translation_skip_existing_overlays", true)
    val autoShowOverlay: Preference<Boolean> = preferenceStore.getBoolean("translation_auto_show_overlay", true)
    val rawDebugLogging: Preference<Boolean> = preferenceStore.getBoolean("translation_raw_debug_logging", false)
    val enableInpaint: Preference<Boolean> = preferenceStore.getBoolean("translation_enable_inpaint", false)

    val temperature: Preference<Float> = preferenceStore.getFloat("translation_temperature", 0.2f)
    val topP: Preference<Float> = preferenceStore.getFloat("translation_top_p", 0.9f)
    val topK: Preference<Int> = preferenceStore.getInt("translation_top_k", 40)
    val maxOutputTokens: Preference<Int> = preferenceStore.getInt("translation_max_output_tokens", 4096)
    val thinkingBudget: Preference<Int> = preferenceStore.getInt("translation_thinking_budget", 1024)
    val rawJsonOverride: Preference<String> = preferenceStore.getString("translation_raw_json_override", "")

    val globalInstructions: Preference<String> = preferenceStore.getString("translation_global_instructions", "")
}
