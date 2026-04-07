package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// ── Shared language tables (top-15 world languages + extras) ─────────────────

/** Languages available as OCR *source* (must be supported by an ML Kit recogniser). */
val OCR_SOURCE_LANGUAGES = linkedMapOf(
    "auto" to "Detect Language",
    "ja"   to "Japanese (日本語)",
    "zh"   to "Chinese Simplified (中文简体)",
    "zh-TW" to "Chinese Traditional (中文繁體)",
    "ko"   to "Korean (한국어)",
    "hi"   to "Hindi (हिन्दी)",
    "bn"   to "Bengali (বাংলা)",
    "en"   to "English",
    "es"   to "Spanish (Español)",
    "fr"   to "French (Français)",
    "de"   to "German (Deutsch)",
    "pt"   to "Portuguese (Português)",
    "ru"   to "Russian (Русский)",
    "ar"   to "Arabic (العربية)",
    "tr"   to "Turkish (Türkçe)",
    "it"   to "Italian (Italiano)",
    "id"   to "Indonesian (Bahasa Indonesia)",
    "vi"   to "Vietnamese (Tiếng Việt)",
)

/** Languages available as translation *target* (all languages Google Translate supports). */
val OCR_TARGET_LANGUAGES = linkedMapOf(
    "tr"    to "Turkish (Türkçe)",
    "en"    to "English",
    "zh-CN" to "Chinese Simplified (中文简体)",
    "zh-TW" to "Chinese Traditional (中文繁體)",
    "ja"    to "Japanese (日本語)",
    "ko"    to "Korean (한국어)",
    "hi"    to "Hindi (हिन्दी)",
    "bn"    to "Bengali (বাংলা)",
    "es"    to "Spanish (Español)",
    "fr"    to "French (Français)",
    "de"    to "German (Deutsch)",
    "pt"    to "Portuguese (Português)",
    "ru"    to "Russian (Русский)",
    "ar"    to "Arabic (العربية)",
    "it"    to "Italian (Italiano)",
    "id"    to "Indonesian (Bahasa Indonesia)",
    "vi"    to "Vietnamese (Tiếng Việt)",
    "nl"    to "Dutch (Nederlands)",
    "pl"    to "Polish (Polski)",
    "uk"    to "Ukrainian (Українська)",
)

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrQuickSettingsDialog(
    onDismissRequest: () -> Unit,
    readerPreferences: ReaderPreferences = Injekt.get(),
) {
    val sourceLang by readerPreferences.ocrSourceLanguage.collectAsState()
    val targetLang by readerPreferences.ocrTargetLanguage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Quick Translation Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // ── Source Language ──────────────────────────────────────────
                var sourceExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = it },
                ) {
                    OutlinedTextField(
                        value = OCR_SOURCE_LANGUAGES[sourceLang] ?: sourceLang,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Source Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = sourceExpanded,
                        onDismissRequest = { sourceExpanded = false },
                    ) {
                        OCR_SOURCE_LANGUAGES.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    readerPreferences.ocrSourceLanguage.set(key)
                                    sourceExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Target Language ──────────────────────────────────────────
                var targetExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = targetExpanded,
                    onExpandedChange = { targetExpanded = it },
                ) {
                    OutlinedTextField(
                        value = OCR_TARGET_LANGUAGES[targetLang] ?: targetLang,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = targetExpanded,
                        onDismissRequest = { targetExpanded = false },
                    ) {
                        OCR_TARGET_LANGUAGES.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    readerPreferences.ocrTargetLanguage.set(key)
                                    targetExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Done")
            }
        },
    )
}
