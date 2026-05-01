package eu.kanade.presentation.more.settings.screen

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.DEFAULT_GEMINI_TRANSLATION_MODEL
import eu.kanade.tachiyomi.data.translation.GeminiTranslationClient
import eu.kanade.tachiyomi.data.translation.TranslationRepository
import eu.kanade.tachiyomi.util.system.toast
import java.io.File
import kotlin.math.roundToInt

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val preferences = remember { Injekt.get<TranslationPreferences>() }
        val client = remember { Injekt.get<GeminiTranslationClient>() }
        val repository = remember { Injekt.get<TranslationRepository>() }

        val selectedModel by preferences.geminiModel.collectAsState()
        val selectedInpaintModel by preferences.geminiInpaintModel.collectAsState()
        val temperature by preferences.temperature.collectAsState()
        val topP by preferences.topP.collectAsState()
        val topK by preferences.topK.collectAsState()
        val maxOutputTokens by preferences.maxOutputTokens.collectAsState()
        val thinkingBudget by preferences.thinkingBudget.collectAsState()
        val concurrency by preferences.concurrency.collectAsState()

        var models by remember(selectedModel, selectedInpaintModel) {
            mutableStateOf(listOf(DEFAULT_GEMINI_TRANSLATION_MODEL, selectedModel, selectedInpaintModel).distinct())
        }
        var modelStatus by remember { mutableStateOf<String?>(null) }

        fun refreshModels(showReadyToast: Boolean) {
            scope.launch {
                val apiKey = preferences.geminiApiKey.get().trim()
                if (apiKey.isBlank()) {
                    context.toast(MR.strings.pref_translation_gemini_api_key_summary)
                    return@launch
                }
                runCatching {
                    client.listModels(apiKey).map { it.id }.distinct().sorted()
                }.onSuccess { fetchedModels ->
                    models = (fetchedModels + selectedModel + selectedInpaintModel)
                        .filter { it.isNotBlank() }
                        .distinct()
                    modelStatus = "${models.size} models"
                    if (DEFAULT_GEMINI_TRANSLATION_MODEL !in fetchedModels) {
                        context.toast(MR.strings.translation_model_unavailable)
                    } else if (showReadyToast) {
                        context.toast(MR.strings.translation_model_ready)
                    }
                }.onFailure { error ->
                    modelStatus = error.message
                    context.toast(error.message ?: "Gemini model refresh failed")
                }
            }
        }

        val modelEntries = models.associateWith { it }.toImmutableMap()

        return listOf(
            Preference.PreferenceItem.InfoPreference(
                stringResource(MR.strings.pref_translation_privacy_notice),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.geminiApiKey,
                        title = stringResource(MR.strings.pref_translation_gemini_api_key),
                        subtitle = stringResource(MR.strings.pref_translation_gemini_api_key_summary),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.translation_model_refresh),
                        subtitle = modelStatus ?: stringResource(MR.strings.translation_model_refresh_summary),
                        onClick = { refreshModels(showReadyToast = false) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.translation_model_test),
                        subtitle = selectedModel,
                        onClick = { refreshModels(showReadyToast = true) },
                    ),
                    Preference.PreferenceItem.BasicListPreference(
                        value = selectedModel,
                        entries = modelEntries,
                        title = stringResource(MR.strings.pref_translation_model),
                        onValueChanged = { preferences.geminiModel.set(it) },
                    ),
                    Preference.PreferenceItem.BasicListPreference(
                        value = selectedInpaintModel,
                        entries = modelEntries,
                        title = stringResource(MR.strings.pref_translation_inpaint_model),
                        onValueChanged = { preferences.geminiInpaintModel.set(it) },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.targetLanguage,
                        title = stringResource(MR.strings.pref_translation_target_language),
                        subtitle = "%s",
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.sourceLanguage,
                        title = stringResource(MR.strings.pref_translation_source_language),
                        subtitle = "%s",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_pipeline),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.pipeline,
                        entries = mapOf(
                            "gemini_vision" to stringResource(MR.strings.pref_translation_pipeline_gemini_vision),
                            "local_ocr_gemini" to stringResource(MR.strings.pref_translation_pipeline_local_ocr_gemini),
                        ).toImmutableMap(),
                        title = stringResource(MR.strings.pref_translation_pipeline),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.ocrScript,
                        entries = mapOf(
                            "auto" to stringResource(MR.strings.label_auto),
                            "latin" to "Latin",
                            "japanese" to "Japanese",
                            "chinese" to "Chinese",
                            "korean" to "Korean",
                            "devanagari" to "Devanagari",
                        ).toImmutableMap(),
                        title = stringResource(MR.strings.pref_translation_ocr_script),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = preferences.skipExistingOverlays,
                        title = stringResource(MR.strings.pref_translation_skip_existing),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = preferences.autoShowOverlay,
                        title = stringResource(MR.strings.pref_translation_show_overlays),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = preferences.rawDebugLogging,
                        title = stringResource(MR.strings.pref_translation_raw_debug_logs),
                        subtitle = stringResource(MR.strings.pref_translation_raw_debug_logs_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = preferences.enableInpaint,
                        title = stringResource(MR.strings.pref_translation_enable_inpaint),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_generation),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = (temperature * 100).roundToInt(),
                        valueRange = 0..200,
                        title = stringResource(MR.strings.pref_translation_temperature),
                        valueString = "%.2f".format(temperature),
                        onValueChanged = { preferences.temperature.set(it / 100f) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = (topP * 100).roundToInt(),
                        valueRange = 0..100,
                        title = stringResource(MR.strings.pref_translation_top_p),
                        valueString = "%.2f".format(topP),
                        onValueChanged = { preferences.topP.set(it / 100f) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = topK,
                        valueRange = 1..100,
                        title = stringResource(MR.strings.pref_translation_top_k),
                        onValueChanged = { preferences.topK.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = maxOutputTokens,
                        valueRange = 512..8192 step 256,
                        title = stringResource(MR.strings.pref_translation_max_tokens),
                        onValueChanged = { preferences.maxOutputTokens.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = thinkingBudget,
                        valueRange = -1..4096 step 128,
                        title = stringResource(MR.strings.pref_translation_thinking_budget),
                        onValueChanged = { preferences.thinkingBudget.set(it) },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.rawJsonOverride,
                        title = stringResource(MR.strings.pref_translation_raw_json_override),
                        subtitle = "%s",
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.globalInstructions,
                        title = stringResource(MR.strings.pref_translation_global_instructions),
                        subtitle = "%s",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_queue),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = concurrency,
                        valueRange = 1..4,
                        title = stringResource(MR.strings.pref_translation_concurrency),
                        onValueChanged = { preferences.concurrency.set(it) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_translation_clear_logs),
                        onClick = {
                            scope.launch {
                                repository.clearLogs()
                                context.toast(MR.strings.pref_translation_clear_logs)
                            }
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_translation_clear_storage),
                        onClick = {
                            scope.launch {
                                repository.clearPages()
                                clearTranslationFiles(context)
                                context.toast(MR.strings.pref_translation_clear_storage)
                            }
                        },
                    ),
                ),
            ),
        )
    }
}

private fun clearTranslationFiles(context: Context) {
    File(context.filesDir, "translations").deleteRecursively()
}
