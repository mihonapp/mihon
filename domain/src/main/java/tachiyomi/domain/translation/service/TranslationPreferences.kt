package tachiyomi.domain.translation.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

const val DEFAULT_TRANSLATION_SYSTEM_PROMPT =
    "Only translate relevant or critical information: speech and thought bubbles, signs, captions, narration, and author's notes. Ignore sound effects, decorative or unrelated background text, watermark text, and punctuation-only symbols."

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

    val setupFingerprint: Preference<String> = preferenceStore.getString(
        Preference.privateKey("translation_setup_fingerprint"),
        "",
    )
    val setupTestedTranslationModel: Preference<String> = preferenceStore.getString(
        "translation_setup_tested_translation_model",
        "",
    )
    val setupTestedInpaintModel: Preference<String> = preferenceStore.getString(
        "translation_setup_tested_inpaint_model",
        "",
    )
    val setupTestedAt: Preference<Long> = preferenceStore.getLong("translation_setup_tested_at", 0)
    val setupStatus: Preference<String> = preferenceStore.getString("translation_setup_status", "never")
    val setupMessage: Preference<String> = preferenceStore.getString("translation_setup_message", "")
    val cachedModelsJson: Preference<String> = preferenceStore.getString("translation_cached_models_json", "")

    val targetLanguage: Preference<String> = preferenceStore.getString("translation_target_language", "")
    val sourceLanguage: Preference<String> = preferenceStore.getString("translation_source_language", "auto")
    val pipeline: Preference<String> = preferenceStore.getString("translation_pipeline", "gemini_vision")
    val ocrScript: Preference<String> = preferenceStore.getString("translation_ocr_script", "auto")

    val concurrency: Preference<Int> = preferenceStore.getInt("translation_concurrency", 1)
    val parallelRetryLanes: Preference<String> = preferenceStore.getString("translation_parallel_retry_lanes", "1")
    val maxImagesPerBatch: Preference<Int> = preferenceStore.getInt("translation_max_images_per_batch", 38)
    val skipExistingOverlays: Preference<Boolean> = preferenceStore.getBoolean("translation_skip_existing_overlays", true)
    val autoShowOverlay: Preference<Boolean> = preferenceStore.getBoolean("translation_auto_show_overlay", true)
    val overlayTextSizeMode: Preference<String> = preferenceStore.getString("translation_overlay_text_size_mode", "dynamic")
    val overlayTextSizeSp: Preference<Int> = preferenceStore.getInt("translation_overlay_text_size_sp", 16)
    val overlayFontFamily: Preference<String> = preferenceStore.getString("translation_overlay_font_family", "system")
    val overlayTextColor: Preference<String> = preferenceStore.getString("translation_overlay_text_color", "#FF000000")
    val overlayBoxFillColor: Preference<String> = preferenceStore.getString("translation_overlay_box_fill_color", "#D2FFFFFF")
    val overlayBoxStrokeColor: Preference<String> = preferenceStore.getString("translation_overlay_box_stroke_color", "#E6202020")
    val overlayBoxPaddingDp: Preference<Int> = preferenceStore.getInt("translation_overlay_box_padding_dp", 0)
    val overlayTextAlignment: Preference<String> = preferenceStore.getString("translation_overlay_text_alignment", "center")
    val queueSwipeStartAction: Preference<TranslationQueueSwipeAction> = preferenceStore.getEnum(
        "translation_queue_swipe_start_action",
        TranslationQueueSwipeAction.ViewLogs,
    )
    val queueSwipeEndAction: Preference<TranslationQueueSwipeAction> = preferenceStore.getEnum(
        "translation_queue_swipe_end_action",
        TranslationQueueSwipeAction.CancelOrDelete,
    )
    val logSwipeStartAction: Preference<TranslationLogSwipeAction> = preferenceStore.getEnum(
        "translation_log_swipe_start_action",
        TranslationLogSwipeAction.OpenDetails,
    )
    val logSwipeEndAction: Preference<TranslationLogSwipeAction> = preferenceStore.getEnum(
        "translation_log_swipe_end_action",
        TranslationLogSwipeAction.CopyDetails,
    )
    val rawDebugLogging: Preference<Boolean> = preferenceStore.getBoolean("translation_raw_debug_logging", false)
    val enableInpaint: Preference<Boolean> = preferenceStore.getBoolean("translation_enable_inpaint", false)

    val temperature: Preference<Float> = preferenceStore.getFloat("translation_temperature", 1f)
    val topP: Preference<Float> = preferenceStore.getFloat("translation_top_p", 0.95f)
    val topK: Preference<Int> = preferenceStore.getInt("translation_top_k", 64)
    val maxOutputTokens: Preference<Int> = preferenceStore.getInt("translation_max_output_tokens", 65_536)
    val thinkingLevel: Preference<String> = preferenceStore.getString("translation_thinking_level", "high")
    val rawJsonOverride: Preference<String> = preferenceStore.getString("translation_raw_json_override", "")

    val globalInstructions: Preference<String> = preferenceStore.getString(
        "translation_global_instructions",
        DEFAULT_TRANSLATION_SYSTEM_PROMPT,
    )
}

enum class TranslationQueueSwipeAction {
    Disabled,
    ViewLogs,
    RetryOrLogs,
    CancelOrDelete,
}

enum class TranslationLogSwipeAction {
    Disabled,
    OpenDetails,
    CopyDetails,
}
