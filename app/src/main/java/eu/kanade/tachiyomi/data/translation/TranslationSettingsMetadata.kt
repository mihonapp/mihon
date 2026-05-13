package eu.kanade.tachiyomi.data.translation

import tachiyomi.domain.translation.service.DEFAULT_TRANSLATION_SYSTEM_PROMPT

data class TranslationSettingMetadata(
    val key: String,
    val description: String,
    val defaultValue: String,
    val minValue: String? = null,
    val maxValue: String? = null,
    val options: String? = null,
    val format: String? = null,
) {
    init {
        require(description.isNotBlank()) { "Description is required for $key" }
        require(defaultValue.isNotBlank()) { "Default is required for $key" }
        require((minValue == null) == (maxValue == null)) { "Numeric metadata for $key must define both min and max" }
        require(minValue != null || options != null || format != null) {
            "Non-numeric metadata for $key must define options or format"
        }
    }

    val numeric: Boolean
        get() = minValue != null && maxValue != null

    fun subtitle(
        currentValue: String? = null,
        minOverride: String? = null,
        maxOverride: String? = null,
    ): String {
        return buildString {
            currentValue
                ?.takeIf { it.isNotBlank() }
                ?.let { appendLine("Current = $it") }
            appendLine("Description = $description")
            appendLine("Default = $defaultValue")
            if (numeric) {
                appendLine("Min = ${minOverride ?: minValue}")
                appendLine("Max = ${maxOverride ?: maxValue}")
            } else {
                options?.let { appendLine("Options = $it") }
                format?.let { appendLine("Format = $it") }
            }
        }.trimEnd()
    }
}

object TranslationSettingsMetadata {
    val setupStatus = metadata(
        key = "setup_status",
        description = "Shows whether the saved Gemini setup can list models, ping the selected model, and pass queue preflight.",
        defaultValue = "Needs attention until tested",
        options = "Ready, Needs attention",
    )
    val setupApiKey = metadata(
        key = "setup_api_key",
        description = "Shows whether a Gemini API key is stored locally for setup tests and translation requests.",
        defaultValue = "Missing",
        options = "Configured, Missing",
    )
    val setupTranslationModel = metadata(
        key = "setup_translation_model",
        description = "Shows whether the selected translation model has passed the current setup fingerprint.",
        defaultValue = DEFAULT_GEMINI_TRANSLATION_MODEL,
        options = "Ready, Needs test",
    )
    val setupInpaintModel = metadata(
        key = "setup_inpaint_model",
        description = "Shows whether the inpaint model has passed setup when inpaint is enabled.",
        defaultValue = DEFAULT_GEMINI_TRANSLATION_MODEL,
        options = "Ready, Needs test",
    )
    val setupTest = metadata(
        key = "setup_test",
        description = "Runs model list, setup ping, fingerprint cache, and queue preflight checks before translation work starts.",
        defaultValue = "Manual test",
        options = "Tap to run",
    )
    val geminiApiKey = metadata(
        key = "gemini_api_key",
        description = "Gemini API key used for Google AI Gemini model listing and generateContent requests.",
        defaultValue = "Empty",
        format = "Secret text stored locally; raw value is never shown here",
    )
    val refreshModels = metadata(
        key = "refresh_models",
        description = "Fetches Gemini models that support generateContent and refreshes local model limits.",
        defaultValue = "Manual refresh",
        options = "Tap to refresh",
    )
    val translationModel = metadata(
        key = "translation_model",
        description = "Gemini model used for overlay translation requests.",
        defaultValue = DEFAULT_GEMINI_TRANSLATION_MODEL,
        options = "Any cached Gemini generateContent model",
    )
    val inpaintModel = metadata(
        key = "inpaint_model",
        description = "Gemini model used for inpaint requests when inpaint is enabled.",
        defaultValue = DEFAULT_GEMINI_TRANSLATION_MODEL,
        options = "Any cached Gemini generateContent model",
    )
    val targetLanguage = metadata(
        key = "target_language",
        description = "Language used for translated overlay text.",
        defaultValue = "System display language",
        format = "Language name, for example English",
    )
    val sourceLanguage = metadata(
        key = "source_language",
        description = "Optional source-language hint sent in prompts; Auto lets Gemini infer the source language.",
        defaultValue = "Auto",
        options = "Auto, Japanese, Korean, Chinese",
    )
    val pipeline = metadata(
        key = "pipeline",
        description = "Chooses whether Gemini reads page images directly or receives local OCR text blocks first.",
        defaultValue = "Gemini vision",
        options = "Gemini vision, Local OCR + Gemini",
    )
    val ocrScript = metadata(
        key = "ocr_script",
        description = "Script hint for ML Kit OCR in the local OCR + Gemini pipeline.",
        defaultValue = "Auto",
        options = "Auto, Latin, Japanese, Chinese, Korean, Devanagari",
    )
    val skipExistingOverlays = metadata(
        key = "skip_existing_overlays",
        description = "Skips pages that already have a saved overlay row during normal enqueue; manual retry still overwrites.",
        defaultValue = "On",
        options = "Off, On",
    )
    val autoShowOverlay = metadata(
        key = "auto_show_overlay",
        description = "Shows saved translation overlays automatically in the reader.",
        defaultValue = "On",
        options = "Off, On",
    )
    val overlayTextSizeMode = metadata(
        key = "overlay_text_size_mode",
        description = "Controls the preferred maximum for overlay text fitting before the renderer shrinks text to fit.",
        defaultValue = "Dynamic",
        options = "Dynamic, System, Custom",
    )
    val overlayTextSizeSp = metadata(
        key = "overlay_text_size_sp",
        description = "Custom overlay text size used as the preferred maximum in Custom mode.",
        defaultValue = "16 sp",
        minValue = "8 sp",
        maxValue = "48 sp",
    )
    val overlayFontFamily = metadata(
        key = "overlay_font_family",
        description = "Font family used by overlay text; System follows Android default text rendering.",
        defaultValue = "System",
        options = "System, Sans, Serif, Monospace",
    )
    val overlayTextColor = metadata(
        key = "overlay_text_color",
        description = "Color used for translated overlay text.",
        defaultValue = "#FF000000",
        format = "#AARRGGBB or #RRGGBB",
    )
    val overlayBoxFillColor = metadata(
        key = "overlay_box_fill_color",
        description = "Fill color drawn behind translated overlay text.",
        defaultValue = "#D2FFFFFF",
        format = "#AARRGGBB or #RRGGBB",
    )
    val overlayBoxStrokeColor = metadata(
        key = "overlay_box_stroke_color",
        description = "Stroke color drawn around translation overlay boxes.",
        defaultValue = "#E6202020",
        format = "#AARRGGBB or #RRGGBB",
    )
    val overlayBoxPadding = metadata(
        key = "overlay_box_padding",
        description = "Inner padding between overlay box edges and fitted translated text.",
        defaultValue = "0 dp",
        minValue = "0 dp",
        maxValue = "24 dp",
    )
    val overlayTextAlignment = metadata(
        key = "overlay_text_alignment",
        description = "Horizontal alignment used by Android text layout inside the overlay content rectangle.",
        defaultValue = "Center",
        options = "Center, Start, End",
    )
    val rawDebugLogging = metadata(
        key = "raw_debug_logging",
        description = "Stores detailed redacted request, response, parser, save, and render diagnostics in Translation Queue logs.",
        defaultValue = "Off",
        options = "Off, On",
    )
    val enableInpaint = metadata(
        key = "enable_inpaint",
        description = "Enables optional inpaint work alongside translation overlays.",
        defaultValue = "Off",
        options = "Off, On",
    )
    val temperature = metadata(
        key = "temperature",
        description = "Gemini generation temperature controls randomness in model output.",
        defaultValue = "0.20",
        minValue = "0.00",
        maxValue = "2.00",
    )
    val topP = metadata(
        key = "top_p",
        description = "Gemini nucleus sampling considers tokens until cumulative probability reaches this value.",
        defaultValue = "0.95",
        minValue = "0.00",
        maxValue = "1.00",
    )
    val topK = metadata(
        key = "top_k",
        description = "Gemini top-k sampling limits generation to the most likely tokens before sampling.",
        defaultValue = "40",
        minValue = "1",
        maxValue = "100",
    )
    val maxOutputTokens = metadata(
        key = "max_output_tokens",
        description = "Maximum number of output tokens Gemini may return for one response.",
        defaultValue = DEFAULT_GEMINI_MAX_OUTPUT_TOKENS.toString(),
        minValue = "512",
        maxValue = "Selected model output token limit",
    )
    val thinkingLevel = metadata(
        key = "thinking_level",
        description = "Controls how much model reasoning budget to request when the selected Gemini model supports thinking.",
        defaultValue = "High",
        options = "High, Medium, Low",
    )
    val rawJsonOverride = metadata(
        key = "raw_json_override",
        description = "Optional raw Gemini generation configuration override merged into requests for advanced debugging.",
        defaultValue = "Empty",
        format = "JSON object",
    )
    val systemPrompt = metadata(
        key = "system_prompt",
        description = "Gemini system instruction that steers translation behavior before page content is sent.",
        defaultValue = DEFAULT_TRANSLATION_SYSTEM_PROMPT,
        format = "Plain text system instruction",
    )
    val concurrency = metadata(
        key = "concurrency",
        description = "Number of normal translation worker groups allowed to run at once.",
        defaultValue = "1",
        minValue = "0",
        maxValue = "Unlimited",
    )
    val parallelRetryLanes = metadata(
        key = "parallel_retry_lanes",
        description = "Number of manual retry lanes allowed to run alongside normal translation work.",
        defaultValue = "1",
        minValue = "0",
        maxValue = "Unlimited",
    )
    val maxImagesPerBatch = metadata(
        key = "max_images_per_batch",
        description = "Maximum page images included in one Gemini batch request; zero means All.",
        defaultValue = DEFAULT_TRANSLATION_MAX_IMAGES_PER_BATCH.toString(),
        minValue = "0",
        maxValue = "100",
    )
    val resetMaxImagesPerBatch = metadata(
        key = "reset_max_images_per_batch",
        description = "Restores the batch image limit to the project default.",
        defaultValue = DEFAULT_TRANSLATION_MAX_IMAGES_PER_BATCH.toString(),
        options = "Tap to reset",
    )
    val queueSwipeStart = metadata(
        key = "queue_swipe_start",
        description = "Action assigned to the leading swipe gesture for Translation Queue rows.",
        defaultValue = "View logs",
        options = "Disabled, View logs, Retry or logs, Cancel or delete",
    )
    val queueSwipeEnd = metadata(
        key = "queue_swipe_end",
        description = "Action assigned to the trailing swipe gesture for Translation Queue rows.",
        defaultValue = "Cancel or delete",
        options = "Disabled, View logs, Retry or logs, Cancel or delete",
    )
    val logSwipeStart = metadata(
        key = "log_swipe_start",
        description = "Action assigned to the leading swipe gesture for Translation Log rows.",
        defaultValue = "View logs",
        options = "Disabled, View logs, Retry or logs, Cancel or delete",
    )
    val logSwipeEnd = metadata(
        key = "log_swipe_end",
        description = "Action assigned to the trailing swipe gesture for Translation Log rows.",
        defaultValue = "Disabled",
        options = "Disabled, View logs, Retry or logs, Cancel or delete",
    )
    val clearLogs = metadata(
        key = "clear_logs",
        description = "Deletes Translation Queue log rows while keeping jobs and saved overlays.",
        defaultValue = "Manual action",
        options = "Tap to clear logs",
    )
    val clearQueueLogs = metadata(
        key = "clear_queue_logs",
        description = "Stops translation work, deletes queue rows, and deletes logs while keeping saved overlays.",
        defaultValue = "Manual action",
        options = "Tap to confirm",
    )
    val clearStorage = metadata(
        key = "clear_storage",
        description = "Deletes saved translation overlay pages and generated translation files.",
        defaultValue = "Manual action",
        options = "Tap to clear storage",
    )

    val all: List<TranslationSettingMetadata> = listOf(
        setupStatus,
        setupApiKey,
        setupTranslationModel,
        setupInpaintModel,
        setupTest,
        geminiApiKey,
        refreshModels,
        translationModel,
        inpaintModel,
        targetLanguage,
        sourceLanguage,
        pipeline,
        ocrScript,
        skipExistingOverlays,
        autoShowOverlay,
        overlayTextSizeMode,
        overlayTextSizeSp,
        overlayFontFamily,
        overlayTextColor,
        overlayBoxFillColor,
        overlayBoxStrokeColor,
        overlayBoxPadding,
        overlayTextAlignment,
        rawDebugLogging,
        enableInpaint,
        temperature,
        topP,
        topK,
        maxOutputTokens,
        thinkingLevel,
        rawJsonOverride,
        systemPrompt,
        concurrency,
        parallelRetryLanes,
        maxImagesPerBatch,
        resetMaxImagesPerBatch,
        queueSwipeStart,
        queueSwipeEnd,
        logSwipeStart,
        logSwipeEnd,
        clearLogs,
        clearQueueLogs,
        clearStorage,
    )

    private fun metadata(
        key: String,
        description: String,
        defaultValue: String,
        minValue: String? = null,
        maxValue: String? = null,
        options: String? = null,
        format: String? = null,
    ) = TranslationSettingMetadata(
        key = key,
        description = description,
        defaultValue = defaultValue,
        minValue = minValue,
        maxValue = maxValue,
        options = options,
        format = format,
    )
}
