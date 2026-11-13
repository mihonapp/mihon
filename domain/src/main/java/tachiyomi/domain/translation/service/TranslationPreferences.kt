package tachiyomi.domain.translation.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.domain.translation.model.TranslationLetterCase
import tachiyomi.domain.translation.model.TranslationTextColor
import tachiyomi.domain.translation.model.TranslationProvider
import java.util.Locale

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    preferenceStore: PreferenceStore,
) {

    val provider: Preference<TranslationProvider> = preferenceStore.getEnum(
        "pref_translation_provider",
        TranslationProvider.GOOGLE,
    )

    val sourceLanguage: Preference<TranslationLanguage> = preferenceStore.getEnum(
        "pref_translation_source_language",
        TranslationLanguage.AUTO,
    )

    val targetLanguage: Preference<TranslationLanguage> = preferenceStore.getEnum(
        "pref_translation_target_language",
        TranslationLanguage.fromCode(Locale.getDefault().language).takeIf {
            it != TranslationLanguage.AUTO
        } ?: TranslationLanguage.ENGLISH,
    )

    val deeplApiKey: Preference<String> = preferenceStore.getString(
        Preference.privateKey("pref_translation_deepl_api_key"),
        "",
    )

    val libreTranslateUrl: Preference<String> = preferenceStore.getString(
        "pref_translation_libre_url",
        DEFAULT_LIBRE_TRANSLATE_URL,
    )

    val libreTranslateApiKey: Preference<String> = preferenceStore.getString(
        Preference.privateKey("pref_translation_libre_api_key"),
        "",
    )

    val textColor: Preference<TranslationTextColor> = preferenceStore.getEnum(
        "pref_translation_text_color",
        TranslationTextColor.AUTO,
    )

    val boldText: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_translation_bold",
        true,
    )

    val letterCase: Preference<TranslationLetterCase> = preferenceStore.getEnum(
        "pref_translation_letter_case",
        TranslationLetterCase.ORIGINAL,
    )

    /** 1 = fast, 2 = balanced, 3 = accurate: how much of the page the recognizer is allowed to read. */
    val ocrQuality: Preference<Int> = preferenceStore.getInt(
        "pref_translation_ocr_quality",
        2,
    )

    companion object {
        const val DEFAULT_LIBRE_TRANSLATE_URL = "https://libretranslate.com"
        const val OCR_FAST_WIDTH = 1200
        const val OCR_BALANCED_WIDTH = 2000
        const val OCR_ACCURATE_WIDTH = 2800
    }
}
