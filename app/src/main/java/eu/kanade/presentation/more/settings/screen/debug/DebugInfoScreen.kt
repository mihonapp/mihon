package eu.kanade.presentation.more.settings.screen.debug

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.profileinstaller.ProfileVerifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.guava.await
import tachiyomi.i18n.MR

class DebugInfoScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        PreferenceScaffold(
            titleRes = MR.strings.pref_debug_info,
            onBackPressed = navigator::pop,
            itemsProvider = {
                listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = WorkerInfoScreen.title,
                        onClick = { navigator.push(WorkerInfoScreen()) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = BackupSchemaScreen.title,
                        onClick = { navigator.push(BackupSchemaScreen()) },
                    ),
                    getAppInfoGroup(),
                    getDeviceInfoGroup(),
                )
            },
        )
    }

    @Composable
    private fun getAppInfoGroup(): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = "App info",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Version",
                    subtitle = AboutScreen.getVersionName(false),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Build time",
                    subtitle = AboutScreen.getFormattedBuildTime(),
                ),
                getProfileVerifierPreference(),
                Preference.PreferenceItem.TextPreference(
                    title = "WebView version",
                    subtitle = getWebViewVersion(),
                ),
            ),
        )
    }

    @Composable
    @ReadOnlyComposable
    private fun getWebViewVersion(): String {
        return WebViewUtil.getVersion(LocalContext.current)
    }

    @Composable
    private fun getProfileVerifierPreference(): Preference.PreferenceItem.TextPreference {
        val status by produceState(initialValue = "-") {
            val result = ProfileVerifier.getCompilationStatusAsync().await().profileInstallResultCode
            value = when (result) {
                ProfileVerifier.CompilationStatus.RESULT_CODE_NO_PROFILE -> "No profile installed"
                ProfileVerifier.CompilationStatus.RESULT_CODE_COMPILED_WITH_PROFILE -> "Compiled"
                ProfileVerifier.CompilationStatus.RESULT_CODE_COMPILED_WITH_PROFILE_NON_MATCHING ->
                    "Compiled non-matching"
                ProfileVerifier.CompilationStatus.RESULT_CODE_ERROR_CACHE_FILE_EXISTS_BUT_CANNOT_BE_READ,
                ProfileVerifier.CompilationStatus.RESULT_CODE_ERROR_CANT_WRITE_PROFILE_VERIFICATION_RESULT_CACHE_FILE,
                ProfileVerifier.CompilationStatus.RESULT_CODE_ERROR_PACKAGE_NAME_DOES_NOT_EXIST,
                -> "Error $result"
                ProfileVerifier.CompilationStatus.RESULT_CODE_ERROR_UNSUPPORTED_API_VERSION -> "Not supported"
                ProfileVerifier.CompilationStatus.RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION -> "Pending compilation"
                else -> "Unknown code $result"
            }
        }
        return Preference.PreferenceItem.TextPreference(
            title = "Profile compilation status",
            subtitle = status,
        )
    }

    private fun getDeviceInfoGroup(): Preference.PreferenceGroup {
        val items = persistentListOf<Preference.PreferenceItem<out Any>>().mutate {
            it.add(
                Preference.PreferenceItem.TextPreference(
                    title = "Model",
                    subtitle = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                ),
            )

            if (DeviceUtil.oneUiVersion != null) {
                it.add(
                    Preference.PreferenceItem.TextPreference(
                        title = "OneUI version",
                        subtitle = "${DeviceUtil.oneUiVersion}",
                    ),
                )
            } else if (DeviceUtil.miuiMajorVersion != null) {
                it.add(
                    Preference.PreferenceItem.TextPreference(
                        title = "MIUI version",
                        subtitle = "${DeviceUtil.miuiMajorVersion}",
                    ),
                )
            }

            val androidVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Build.VERSION.RELEASE_OR_CODENAME
            } else {
                Build.VERSION.RELEASE
            }
            it.add(
                Preference.PreferenceItem.TextPreference(
                    title = "Android version",
                    subtitle = "$androidVersion (${Build.DISPLAY})",
                ),
            )
        }

        return Preference.PreferenceGroup(
            title = "Device info",
            preferenceItems = items,
        )
    }
}
