package eu.kanade.domain.translation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun enabled() = preferenceStore.getBoolean("pref_translation_enabled", false)

    // Private key: filtered out of backups by PreferenceBackupCreator.
    fun apiKey() = preferenceStore.getString(Preference.privateKey("pref_nano_banana_api_key"), "")

    // Language name sent to the model, e.g. "English", "Arabic".
    fun targetLanguage() = preferenceStore.getString(
        "pref_translation_target_lang",
        TranslationOptions.DEFAULT_LANGUAGE,
    )

    fun model() = preferenceStore.getString("pref_translation_model", TranslationOptions.DEFAULT_MODEL)

    // "redraw" (Nano Banana image model, paid) or "overlay" (free text model, text drawn by the app).
    fun mode() = preferenceStore.getString("pref_translation_mode", TranslationOptions.MODE_REDRAW)

    fun textModel() = preferenceStore.getString("pref_translation_text_model", TranslationOptions.DEFAULT_TEXT_MODEL)

    // Overlay mode: also translate sound effects (usually looks bad, so off by default).
    fun translateSfx() = preferenceStore.getBoolean("pref_translation_sfx", false)

    // Overlay mode: minimum pause between requests, to stay under free tier rate limits.
    // Per manga glossary text, one `source = translation` per line. See Glossary.
    fun glossary(mangaId: Long) = preferenceStore.getString("pref_translation_glossary_$mangaId", "")

    // Overlay mode: how the translated text looks (see RenderStyle). Applied when a chapter is translated.
    fun fontStyle() = preferenceStore.getString("pref_translation_font", "bold")

    fun textSizePercent() = preferenceStore.getInt("pref_translation_text_size", 100)

    fun placement() = preferenceStore.getString("pref_translation_placement", "replace")

    // The Translate dialog's "only while charging and on Wi-Fi" switch, remembered for next time.
    fun onlyWhenIdle() = preferenceStore.getBoolean("pref_translation_only_idle", false)

    fun requestDelaySeconds() = preferenceStore.getInt("pref_translation_delay_seconds", 6)

    // Writes a step by step text log of every translated chapter into the Local source folder.
    fun saveLog() = preferenceStore.getBoolean("pref_translation_save_log", true)
}
