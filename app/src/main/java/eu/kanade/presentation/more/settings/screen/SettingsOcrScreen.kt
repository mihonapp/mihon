package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import eu.kanade.presentation.more.settings.Preference
 import eu.kanade.presentation.reader.appbars.OCR_SOURCE_LANGUAGES
import eu.kanade.presentation.reader.appbars.OCR_TARGET_LANGUAGES
import eu.kanade.tachiyomi.data.security.SecureOcrPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsOcrScreen : SearchableSettings {

    @Composable
    override fun getTitleRes() = MR.strings.pref_category_ocr

    @Composable
    override fun getPreferences(): List<Preference> {
        val readerPreferences: ReaderPreferences = Injekt.get()
        val securePrefs: SecureOcrPreferences = Injekt.get()
        
        val ocrEnabled by readerPreferences.ocrTranslateEnabled.collectAsState()
        var showApiKeyDialog by remember { mutableStateOf(false) }
        var currentApiKey by remember { mutableStateOf(securePrefs.getApiKey() ?: "") }

        if (showApiKeyDialog) {
            var tempKey by remember { mutableStateOf(currentApiKey) }
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                title = { Text(stringResource(MR.strings.pref_ocr_api_key)) },
                text = {
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API Key") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        securePrefs.saveApiKey(tempKey)
                        currentApiKey = tempKey
                        showApiKeyDialog = false
                    }) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                }
            )
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_ocr),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = readerPreferences.ocrTranslateEnabled,
                        title = stringResource(MR.strings.pref_ocr_reader_overlay),
                        subtitle = stringResource(MR.strings.pref_ocr_reader_overlay_summary),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = readerPreferences.ocrAutoTranslateMode,
                        title = "Çeviri Modu",
                        subtitle = "Bölüm içindeki diğer sayfaların nasıl çevrileceğini seçin",
                        entries = persistentMapOf(
                            0 to "O an ekranda açık olan sayfayı çevir",
                            1 to "Tüm sayfaları çevir (Arkaplanda önden yükler)"
                        ),
                        enabled = ocrEnabled,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = readerPreferences.ocrTranslateService,
                        title = stringResource(MR.strings.pref_ocr_service),
                        entries = persistentMapOf(
                            ReaderPreferences.TranslateService.GOOGLE_FREE.id to "Google Translate (Free)",
                            ReaderPreferences.TranslateService.DEEPL.id to "DeepL API",
                            ReaderPreferences.TranslateService.GOOGLE_CLOUD.id to "Google Cloud API"
                        ),
                        enabled = ocrEnabled,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_ocr_api_key),
                        subtitle = if (currentApiKey.isEmpty()) "Not Set" else "Active: ***${currentApiKey.takeLast(4)}",
                        onClick = { showApiKeyDialog = true },
                        enabled = ocrEnabled,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = readerPreferences.ocrSourceLanguage,
                        title = stringResource(MR.strings.pref_ocr_source_lang),
                        entries = persistentMapOf(*OCR_SOURCE_LANGUAGES.entries.map { it.key to it.value }.toTypedArray()),
                        enabled = ocrEnabled,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = readerPreferences.ocrTargetLanguage,
                        title = stringResource(MR.strings.pref_ocr_target_lang),
                        entries = persistentMapOf(*OCR_TARGET_LANGUAGES.entries.map { it.key to it.value }.toTypedArray()),
                        enabled = ocrEnabled,
                    )
                )
            )
        )
    }
}
