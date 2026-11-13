package tachiyomi.translation.ocr

/**
 * A downloadable recognition model. Mirrors the catalog shape used by `kiss-translator`
 * (`src/localAi/core/catalog.ts`): official Paddle inference weights, checksummed.
 *
 * Two hosting layouts exist:
 * - PaddleX publishes one tarball per model that already contains the dictionary
 *   ([fileName] plus [OcrPresetCatalog.BASE_URL]).
 * - RapidOCR re-exports the per-language models as a bare `.onnx` on ModelScope, with the
 *   dictionary stored next to the Paddle weights ([url] + [charsetUrl]).
 */
data class OcrPreset(
    val id: String,
    val displayName: String,
    val task: String = TASK_REC,
    /** Language codes this model can read; [LANGUAGE_MULTILINGUAL] means "many scripts". */
    val languages: List<String>,
    val sizeBytes: Long,
    val fileName: String,
    val sha256: String,
    /** Absolute URL of the `.onnx` file; null means the PaddleX tarball layout. */
    val url: String? = null,
    /** Dictionary shipped separately from the weights (direct-file presets only). */
    val charsetUrl: String? = null,
    val charsetFileName: String? = null,
    val charsetSha256: String? = null,
) {
    val isDirectFile: Boolean get() = url != null

    fun supports(language: String): Boolean =
        languages.contains(language) || languages.contains(LANGUAGE_MULTILINGUAL)

    /** PP-OCRv6 tiny has no Japanese dictionary, so "multilingual" alone is not enough. */
    val supportsJapanese: Boolean get() = supports("ja")
    val supportsKorean: Boolean get() = supports("ko")

    companion object {
        const val LANGUAGE_MULTILINGUAL = "multilingual"
        const val TASK_REC = "ocr-recognition"
        const val TASK_DET = "ocr-detection"
    }
}

/**
 * Closed preset list. The app ships this catalog and lets the user pick one; it never imports
 * third-party model files, so manifest mistakes are caught at release time instead of runtime.
 *
 * Engine choice follows the source language: Japanese runs Manga OCR, Korean and English run
 * these RapidOCR models. Per the RapidOCR model list
 * (https://rapidai.github.io/RapidOCRDocs/main/model_list/):
 * - Korean has a dedicated PP-OCRv5 recogniser that beats the multilingual models.
 * - English uses the generic model; Japanese is not covered by RapidOCR here at all, so the
 *   Japanese and English dedicated exports are deliberately not offered.
 * - Detection is script-agnostic, so one DB model serves every language.
 */
object OcrPresetCatalog {
    const val BASE_URL = "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0"

    /** RapidOCR's ONNX exports; the dictionary lives under the matching `paddle/` folder. */
    const val RAPID_URL = "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0"

    const val ID_TINY = "ppocr-v6-tiny-rec"
    const val ID_SMALL = "ppocr-v6-small-rec"
    const val ID_MEDIUM = "ppocr-v6-medium-rec"
    const val ID_V5_MOBILE = "ppocr-v5-mobile-rec"

    /** Language-specific recognisers, tried before the multilingual fallbacks.
     *  Only Korean keeps one: Japanese is handled by Manga OCR and English by the generic
     *  model, so their dedicated exports are deliberately not offered. */
    const val ID_KO_V5 = "ppocr-v5-korean-mobile"

    val languagePresets: List<OcrPreset> = listOf(
        OcrPreset(
            id = ID_KO_V5,
            displayName = "PP-OCRv5 韩文 mobile",
            languages = listOf("ko"),
            sizeBytes = 13_488_748L,
            fileName = "korean_PP-OCRv5_rec_mobile.onnx",
            sha256 = "cd6e2ea50f6943ca7271eb8c56a877a5a90720b7047fe9c41a2e541a25773c9b",
            url = "$RAPID_URL/onnx/PP-OCRv5/rec/korean_PP-OCRv5_rec_mobile.onnx",
            charsetUrl = "$RAPID_URL/paddle/PP-OCRv5/rec/korean_PP-OCRv5_rec_mobile/ppocrv5_korean_dict.txt",
            charsetFileName = "ppocrv5_korean_dict.txt",
            charsetSha256 = "a88071c68c01707489baa79ebe0405b7beb5cca229f4fc94cc3ef992328802d7",
        ),
    )

    /** Multilingual fallbacks; usable for every source language but weaker on Japanese. */
    val multilingualPresets: List<OcrPreset> = listOf(
        OcrPreset(
            id = ID_TINY,
            displayName = "PP-OCRv6 tiny",
            // Tiny dropped Japanese from its dictionary; Korean and English are still covered.
            // Declared explicitly so it is never offered as a Japanese fallback.
            languages = listOf("zh", "en", "ko"),
            sizeBytes = 4_526_080L,
            fileName = "PP-OCRv6_tiny_rec_onnx_infer.tar",
            sha256 = "1e13b22717b1edd89d4cde4fda272b6c17d5b505c97c2baea99da1a3a2d54b29",
        ),
        OcrPreset(
            id = ID_SMALL,
            displayName = "PP-OCRv6 small",
            languages = listOf(OcrPreset.LANGUAGE_MULTILINGUAL),
            sizeBytes = 21_319_680L,
            fileName = "PP-OCRv6_small_rec_onnx_infer.tar",
            sha256 = "d267ab077a44a0eedb1ea8f8c542d263f211de8e9d7a029bf9fcfff7e5a88fb1",
        ),
        OcrPreset(
            id = ID_MEDIUM,
            displayName = "PP-OCRv6 medium",
            languages = listOf(OcrPreset.LANGUAGE_MULTILINGUAL),
            sizeBytes = 76_718_080L,
            fileName = "PP-OCRv6_medium_rec_onnx_infer.tar",
            sha256 = "d8cc46c7163c83a151aef8fce5856b965860df90a875029216d605b0f607eaec",
        ),
        OcrPreset(
            id = ID_V5_MOBILE,
            displayName = "PP-OCRv5 mobile（中英）",
            // Declared zh + en only; must not be offered for Japanese or Korean sources.
            languages = listOf("zh", "en"),
            sizeBytes = 16_701_440L,
            fileName = "PP-OCRv5_mobile_rec_onnx_infer.tar",
            sha256 = "f7e792bc836f36e7ef895ad47c426d75b0b75b1650caa6d63fe9418441ffba8c",
        ),
    )

    val presets: List<OcrPreset> = languagePresets + multilingualPresets

    /** DB text detection, same Paddle source as the recognisers. Script-agnostic. */
    val detPresets: List<OcrPreset> = listOf(
        OcrPreset(
            id = "ppocr-v6-tiny-det",
            displayName = "PP-OCRv6 tiny 检测",
            task = OcrPreset.TASK_DET,
            languages = listOf(OcrPreset.LANGUAGE_MULTILINGUAL),
            sizeBytes = 1_792_000L,
            fileName = "PP-OCRv6_tiny_det_onnx_infer.tar",
            sha256 = "ff6ab415b0a6e0c488550f2fb5d5046f1719848df220b2dc21b56402a65bc05d",
        ),
        OcrPreset(
            id = "ppocr-v6-small-det",
            displayName = "PP-OCRv6 small 检测",
            task = OcrPreset.TASK_DET,
            languages = listOf(OcrPreset.LANGUAGE_MULTILINGUAL),
            sizeBytes = 9_891_840L,
            fileName = "PP-OCRv6_small_det_onnx_infer.tar",
            sha256 = "d218f6fbf0f1c23d2161bd6ac7f5eaa6104fa89955c09290497e31008e2618e4",
        ),
        OcrPreset(
            id = "ppocr-v6-medium-det",
            displayName = "PP-OCRv6 medium 检测",
            task = OcrPreset.TASK_DET,
            languages = listOf(OcrPreset.LANGUAGE_MULTILINGUAL),
            sizeBytes = 62_044_160L,
            fileName = "PP-OCRv6_medium_det_onnx_infer.tar",
            sha256 = "c5adb0b15de1b1838934eba1dd72e7529e7d80132216c6ee6d26eba6fa054fcf",
        ),
    )

    val defaultDet: OcrPreset = detPresets.first { it.id == "ppocr-v6-medium-det" }

    val default: OcrPreset = presets.first { it.id == ID_SMALL }

    fun byId(id: String?): OcrPreset? = (presets + detPresets).firstOrNull { it.id == id }

    fun downloadUrl(preset: OcrPreset): String = preset.url ?: "$BASE_URL/${preset.fileName}"

    /** The dedicated recogniser for [language] ("ja", "ko", "en"), if one is published. */
    fun languagePreset(language: String): OcrPreset? =
        languagePresets.firstOrNull { it.languages.contains(language) }

    /**
     * Recognisers worth trying for [language], best first: the dedicated model, then the
     * multilingual ones. `preferred` (the user's generic pick) is kept ahead of the others so a
     * deliberately downloaded fallback is not ignored.
     */
    fun candidatesFor(language: String, preferred: String?): List<OcrPreset> {
        val ids = buildList {
            languagePreset(language)?.let { add(it.id) }
            preferred?.let { add(it) }
            add(ID_SMALL)
            add(ID_MEDIUM)
            add(ID_TINY)
            add(ID_V5_MOBILE)
        }.distinct()
        return ids.mapNotNull { id -> presets.firstOrNull { it.id == id } }
            .filter { preset -> preset.supports(language) || preset.languages.contains(OcrPreset.LANGUAGE_MULTILINGUAL) }
    }

    /** Presets that can read [language] ("ja", "ko", "en", "zh"). */
    fun forLanguage(language: String): List<OcrPreset> = presets.filter { it.supports(language) }
}
