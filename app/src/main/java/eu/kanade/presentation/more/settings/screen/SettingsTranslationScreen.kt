package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.translation.TranslationPreferences
import eu.kanade.tachiyomi.ui.reader.translation.TranslationSourceLanguage
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import okhttp3.OkHttpClient
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.translation.ocr.MangaOcrCatalog
import tachiyomi.translation.ocr.MangaOcrStore
import tachiyomi.translation.ocr.OcrModelStore
import tachiyomi.translation.ocr.OcrPreset
import tachiyomi.translation.ocr.OcrPresetCatalog
import tachiyomi.translation.provider.ProviderRegistry

object SettingsTranslationScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val prefs = remember { context.appGraph.translationPreferences }
        val providerId by prefs.providerId.collectAsState()
        val presetId by prefs.ocrPresetId.collectAsState()
        val detPresetId by prefs.detPresetId.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_translation),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.enabled,
                        title = stringResource(MR.strings.pref_translation_enabled),
                        subtitle = stringResource(MR.strings.pref_translation_enabled_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_group_language),
                preferenceItems = listOf(
                    // 源语言只在阅读界面的译文框顶部切换（决定 OCR 引擎与字典，切换即重扫），
                    // 设置页不再重复提供。
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.targetLanguage,
                        entries = mapOf("zh-CN" to stringResource(MR.strings.pref_translation_target_chinese)),
                        title = stringResource(MR.strings.pref_translation_target_language),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "OCR 文字识别引擎（请按需下载）",
                preferenceItems = listOf(
                    Preference.PreferenceItem.CustomPreference(title = "整页检测模型") {
                        PresetModelRow(
                            title = "整页检测模型 · RapidOCR",
                            required = true,
                            detection = true,
                            preset = OcrPresetCatalog.byId(detPresetId) ?: OcrPresetCatalog.defaultDet,
                        )
                    },
                    Preference.PreferenceItem.CustomPreference(title = "通用识别模型") {
                        PresetModelRow(
                            title = "通用识别模型（英文） · RapidOCR",
                            preset = OcrPresetCatalog.byId(presetId) ?: OcrPresetCatalog.default,
                        )
                    },
                    Preference.PreferenceItem.CustomPreference(title = "日文识别模型") {
                        MangaOcrModelRow()
                    },
                    Preference.PreferenceItem.CustomPreference(title = "韩文识别模型") {
                        PresetModelRow(
                            title = "韩文识别模型 · RapidOCR",
                            preset = OcrPresetCatalog.languagePresets.first(),
                        )
                    },
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_group_engine),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.providerId,
                        entries = providerEntries(),
                        title = stringResource(MR.strings.pref_translation_provider),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.azureKey,
                        title = stringResource(MR.strings.pref_translation_azure_key),
                        visible = providerId == ProviderRegistry.AZURE,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.azureRegion,
                        title = stringResource(MR.strings.pref_translation_azure_region),
                        visible = providerId == ProviderRegistry.AZURE,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.googleCloudKey,
                        title = stringResource(MR.strings.pref_translation_google_key),
                        visible = providerId == ProviderRegistry.GOOGLE_CLOUD,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.llmKey,
                        title = stringResource(MR.strings.pref_translation_llm_key),
                        visible = providerId == ProviderRegistry.LLM,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.llmBaseUrl,
                        title = stringResource(MR.strings.pref_translation_llm_base_url),
                        visible = providerId == ProviderRegistry.LLM,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.llmModel,
                        title = stringResource(MR.strings.pref_translation_llm_model),
                        visible = providerId == ProviderRegistry.LLM,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.useContext,
                        title = stringResource(MR.strings.pref_translation_context),
                        subtitle = stringResource(MR.strings.pref_translation_context_summary),
                        visible = providerId == ProviderRegistry.LLM,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_group_privacy),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.networkConsent,
                        title = stringResource(MR.strings.pref_translation_network_consent),
                        subtitle = stringResource(MR.strings.pref_translation_network_consent_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_group_panel),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.panelHeightFraction,
                        entries = mapOf(
                            0.30f to "30%",
                            0.35f to "35%",
                            0.40f to "40%",
                            0.45f to "45%",
                        ),
                        title = stringResource(MR.strings.pref_translation_panel_height),
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun providerEntries(): Map<String, String> = mapOf(
        ProviderRegistry.EDGE to stringResource(MR.strings.pref_translation_provider_edge),
        ProviderRegistry.AZURE to stringResource(MR.strings.pref_translation_provider_azure),
        ProviderRegistry.GOOGLE to stringResource(MR.strings.pref_translation_provider_google),
        ProviderRegistry.GOOGLE_CLOUD to stringResource(MR.strings.pref_translation_provider_google_cloud),
        ProviderRegistry.LLM to stringResource(MR.strings.pref_translation_provider_llm),
    )
}


@Composable
private fun PresetModelRow(
    title: String,
    preset: OcrPreset,
    required: Boolean = false,
    detection: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { OcrModelStore(context, OkHttpClient()) }
    // Detection tars ship no character dictionary, so the full resolve() (charset >= 100 lines)
    // always fails for them; they are validated by the onnx file alone (isModelInstalled).
    val isReady: (String) -> Boolean =
        if (detection) store::isModelInstalled else store::isInstalled
    var installed by remember(preset.id) { mutableStateOf(isReady(preset.id)) }
    var progress by remember(preset.id) { mutableStateOf<Float?>(null) }
    var error by remember(preset.id) { mutableStateOf<String?>(null) }

    ModelRowLayout(
        title = title,
        status = buildString {
            if (required) append("前置必装 · ")
            if (installed) {
                append("已加载 · ${store.installedBytes(preset.id) / 1_048_576} MB")
            } else {
                append("未下载 · 约 ${preset.sizeBytes / 1_048_576} MB")
            }
        },
        physicalName = preset.fileName,
        progress = progress,
        error = error,
        installed = installed,
        onDownload = {
            scope.launch {
                error = null
                progress = 0f
                store.download(preset) { progress = it }
                    .onSuccess { installed = true }
                    .onFailure { error = it.message ?: "下载失败" }
                progress = null
            }
        },
        onDelete = {
            store.delete(preset.id)
            installed = false
        },
    )
}

@Composable
private fun MangaOcrModelRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { MangaOcrStore(context, OkHttpClient()) }
    var installed by remember { mutableStateOf(store.isInstalled()) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ModelRowLayout(
        title = "日文识别模型 · Manga OCR",
        status = if (installed) {
            "已加载 · ${store.installedBytes() / 1_048_576} MB"
        } else {
            "未下载 · 约 ${MangaOcrCatalog.APPROX_BYTES / 1_048_576} MB"
        },
        physicalName = "encoder.onnx · decoder.onnx · vocab.txt",
        progress = progress,
        error = error,
        installed = installed,
        onDownload = {
            scope.launch {
                error = null
                progress = 0f
                store.download { progress = it }
                    .onSuccess { installed = true }
                    .onFailure { error = it.message ?: "下载失败" }
                progress = null
            }
        },
        onDelete = {
            store.delete()
            installed = false
        },
    )
}

@Composable
private fun ModelRowLayout(
    title: String,
    status: String,
    physicalName: String,
    progress: Float?,
    error: String?,
    installed: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            Text(
                status,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                physicalName,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 2.dp),
            )
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
            error?.let {
                Text(
                    text = it,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
        }
        if (installed) {
            TextButton(onClick = onDelete) {
                Text("删除")
            }
        } else {
            TextButton(
                enabled = progress == null,
                onClick = onDownload,
            ) {
                Text(if (progress == null) "下载" else "下载中")
            }
        }
    }
}
