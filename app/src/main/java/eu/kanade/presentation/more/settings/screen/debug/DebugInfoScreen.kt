package eu.kanade.presentation.more.settings.screen.debug

import android.os.Build
import android.webkit.WebView
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
import eu.kanade.presentation.more.settings.screen.AboutScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.DeviceUtil
import kotlinx.coroutines.guava.await

object DebugInfoScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        PreferenceScaffold(
            titleRes = R.string.pref_debug_info,
            onBackPressed = navigator::pop,
            itemsProvider = {
                listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = WorkerInfoScreen.title,
                        onClick = { navigator.push(WorkerInfoScreen) },
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
            preferenceItems = listOf(
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
        val webView = WebView.getCurrentWebViewPackage() ?: return "how did you get here?"
        val pm = LocalContext.current.packageManager
        val label = webView.applicationInfo.loadLabel(pm)
        val version = webView.versionName
        return "$label $version"
    }

    @Composable
    private fun getProfileVerifierPreference(): Preference.PreferenceItem.TextPreference {
        val status by produceState(initialValue = "-") {
            val result = ProfileVerifier.getCompilationStatusAsync().await().profileInstallResultCode
            value = when (result) {
                ProfileVerifier.CompilationStatus.RESULT_CODE_NO_PROFILE -> "No profile installed"
                ProfileVerifier.CompilationStatus.RESULT_CODE_COMPILED_WITH_PROFILE -> "Compiled"
                ProfileVerifier.CompilationStatus.RESULT_CODE_COMPILED_WITH_PROFILE_NON_MATCHING -> "Compiled non-matching"
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
        val items = mutableListOf(
            Preference.PreferenceItem.TextPreference(
                title = "Model",
                subtitle = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            ),
        )

        if (DeviceUtil.oneUiVersion != null) {
            items += Preference.PreferenceItem.TextPreference(
                title = "OneUI version",
                subtitle = "${DeviceUtil.oneUiVersion}",
            )
        } else if (DeviceUtil.miuiMajorVersion != null) {
            items += Preference.PreferenceItem.TextPreference(
                title = "MIUI version",
                subtitle = "${DeviceUtil.miuiMajorVersion}",
            )
        }

        val androidVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Build.VERSION.RELEASE_OR_CODENAME
        } else {
            Build.VERSION.RELEASE
        }
        items += Preference.PreferenceItem.TextPreference(
            title = "Android version",
            subtitle = "$androidVersion (${Build.DISPLAY})",
        )

        return Preference.PreferenceGroup(
            title = "Device info",
            preferenceItems = items,
        )
    }
}
