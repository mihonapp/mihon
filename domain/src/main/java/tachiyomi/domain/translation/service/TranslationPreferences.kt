package tachiyomi.domain.translation.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.translation.model.TranslationLanguage
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

    companion object {
        const val DEFAULT_LIBRE_TRANSLATE_URL = "https://libretranslate.com"
    }
}
