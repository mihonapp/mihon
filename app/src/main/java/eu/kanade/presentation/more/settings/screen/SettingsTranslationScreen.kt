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
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.DEFAULT_GEMINI_MAX_OUTPUT_TOKENS
import eu.kanade.tachiyomi.data.translation.DEFAULT_GEMINI_TRANSLATION_MODEL
import eu.kanade.tachiyomi.data.translation.GeminiModel
import eu.kanade.tachiyomi.data.translation.GeminiTranslationClient
import eu.kanade.tachiyomi.data.translation.TranslationModelLimits
import eu.kanade.tachiyomi.data.translation.TranslationRepository
import eu.kanade.tachiyomi.data.translation.TranslationSetupValidator
import eu.kanade.tachiyomi.data.translation.TranslationJob
import eu.kanade.tachiyomi.data.translation.TranslationThinkingLevel
import eu.kanade.tachiyomi.util.system.toast
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import tachiyomi.core.common.i18n.stringResource as contextStringResource

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
        val setupValidator = remember { Injekt.get<TranslationSetupValidator>() }
        val json = remember { Injekt.get<Json>() }

        val apiKey by preferences.geminiApiKey.collectAsState()
        val selectedModel by preferences.geminiModel.collectAsState()
        val selectedInpaintModel by preferences.geminiInpaintModel.collectAsState()
        val enableInpaint by preferences.enableInpaint.collectAsState()
        val setupFingerprint by preferences.setupFingerprint.collectAsState()
        val setupTestedTranslationModel by preferences.setupTestedTranslationModel.collectAsState()
        val setupTestedInpaintModel by preferences.setupTestedInpaintModel.collectAsState()
        val setupTestedAt by preferences.setupTestedAt.collectAsState()
        val setupStatus by preferences.setupStatus.collectAsState()
        val setupMessage by preferences.setupMessage.collectAsState()
        val cachedModelsJson by preferences.cachedModelsJson.collectAsState()
        val temperature by preferences.temperature.collectAsState()
        val topP by preferences.topP.collectAsState()
        val topK by preferences.topK.collectAsState()
        val maxOutputTokens by preferences.maxOutputTokens.collectAsState()
        val concurrency by preferences.concurrency.collectAsState()

        var modelMetadata by remember(cachedModelsJson) {
            mutableStateOf(TranslationModelLimits.decodeModels(cachedModelsJson, json))
        }
        var modelStatus by remember { mutableStateOf<String?>(null) }
        val models = remember(modelMetadata, selectedModel, selectedInpaintModel) {
            (modelMetadata.map { it.id } + DEFAULT_GEMINI_TRANSLATION_MODEL + selectedModel + selectedInpaintModel)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        val readiness = remember(
            apiKey,
            selectedModel,
            selectedInpaintModel,
            enableInpaint,
            setupFingerprint,
            setupTestedTranslationModel,
            setupTestedInpaintModel,
            setupTestedAt,
            setupStatus,
            setupMessage,
        ) {
            setupValidator.readiness()
        }

        fun cacheModels(fetchedModels: List<GeminiModel>) {
            val generateContentModels = fetchedModels.filter { model ->
                model.supportedGenerationMethods.any { it.equals("generateContent", ignoreCase = true) }
            }
            modelMetadata = generateContentModels
            preferences.cachedModelsJson.set(TranslationModelLimits.encodeModels(generateContentModels, json))
            modelStatus = "${generateContentModels.size} models"
        }

        fun refreshModels(showReadyToast: Boolean) {
            scope.launch {
                val apiKey = preferences.geminiApiKey.get().trim()
                if (apiKey.isBlank()) {
                    context.toast(MR.strings.pref_translation_gemini_api_key_summary)
                    return@launch
                }
                runCatching {
                    client.listModels(apiKey)
                }.onSuccess { fetchedModels ->
                    cacheModels(fetchedModels)
                    if (DEFAULT_GEMINI_TRANSLATION_MODEL !in fetchedModels.map { it.id }) {
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

        fun runSetupTest() {
            scope.launch {
                val result = setupValidator.testSetup()
                if (result.models.isNotEmpty()) {
                    cacheModels(result.models)
                }
                context.toast(result.message)
                if (result.ready) {
                    val requeued = repository.requeuePausedAuthJobs("Setup test passed")
                    if (requeued > 0) {
                        TranslationJob.start(context)
                        context.toast(context.contextStringResource(MR.strings.translation_resume_requeued, requeued))
                    }
                }
            }
        }

        val modelEntries = models.associateWith { it }.toImmutableMap()
        val setupLastTested = readiness.testedAt
            .takeIf { it > 0 }
            ?.let { "\n${stringResource(MR.strings.translation_setup_last_tested, formatSetupTestedAt(it))}" }
            .orEmpty()
        val maxOutputTokenSliderMax = max(
            1,
            TranslationModelLimits.outputTokenLimitFor(selectedModel, modelMetadata)
                ?: DEFAULT_GEMINI_MAX_OUTPUT_TOKENS,
        )
        val maxOutputTokenSliderMin = min(1024, maxOutputTokenSliderMax)
        val maxOutputTokenSliderSteps = max(0, ((maxOutputTokenSliderMax - maxOutputTokenSliderMin) / 1024) - 1)
        val maxOutputTokenSliderValue = TranslationModelLimits.maxOutputTokensFor(
            requested = maxOutputTokens,
            selectedModel = selectedModel,
            cachedModels = modelMetadata,
        ).coerceIn(maxOutputTokenSliderMin, maxOutputTokenSliderMax)

        return listOf(
            Preference.PreferenceItem.InfoPreference(
                stringResource(MR.strings.pref_translation_privacy_notice),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_setup),
                preferenceItems = buildList<Preference.PreferenceItem<out Any, out Any>> {
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(
                                if (readiness.ready) {
                                    MR.strings.translation_setup_ready
                                } else {
                                    MR.strings.translation_setup_needs_attention
                                },
                            ),
                            subtitle = readiness.message + setupLastTested,
                        ),
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(MR.strings.pref_translation_gemini_api_key),
                            subtitle = stringResource(
                                if (readiness.apiKeyPresent) {
                                    MR.strings.translation_setup_configured
                                } else {
                                    MR.strings.translation_setup_missing
                                },
                            ),
                        ),
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(MR.strings.pref_translation_model),
                            subtitle = stringResource(
                                if (readiness.translationModelReady) {
                                    MR.strings.translation_setup_model_ready
                                } else {
                                    MR.strings.translation_setup_model_needs_test
                                },
                                readiness.translationModel,
                            ),
                        ),
                    )
                    if (readiness.inpaintRequired) {
                        add(
                            Preference.PreferenceItem.TextPreference(
                                title = stringResource(MR.strings.pref_translation_inpaint_model),
                                subtitle = stringResource(
                                    if (readiness.inpaintModelReady) {
                                        MR.strings.translation_setup_model_ready
                                    } else {
                                        MR.strings.translation_setup_model_needs_test
                                    },
                                    readiness.inpaintModel,
                                ),
                            ),
                        )
                    }
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(MR.strings.translation_setup_test),
                            subtitle = stringResource(MR.strings.translation_setup_test_summary),
                            onClick = { runSetupTest() },
                        ),
                    )
                }.toPersistentList(),
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
                        value = maxOutputTokenSliderValue,
                        valueRange = maxOutputTokenSliderMin..maxOutputTokenSliderMax,
                        steps = maxOutputTokenSliderSteps,
                        title = stringResource(MR.strings.pref_translation_max_tokens),
                        onValueChanged = { preferences.maxOutputTokens.set(it) },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.thinkingLevel,
                        entries = mapOf(
                            TranslationThinkingLevel.High.value to stringResource(MR.strings.pref_translation_thinking_level_high),
                            TranslationThinkingLevel.Medium.value to stringResource(MR.strings.pref_translation_thinking_level_medium),
                            TranslationThinkingLevel.Low.value to stringResource(MR.strings.pref_translation_thinking_level_low),
                        ).toImmutableMap(),
                        title = stringResource(MR.strings.pref_translation_thinking_level),
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

private fun formatSetupTestedAt(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}
