package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val authSupported = remember { context.isAuthenticationSupported() }

        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = useAuthPref,
                title = stringResource(MR.strings.lock_with_biometrics),
                enabled = authSupported,
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.stringResource(MR.strings.lock_with_biometrics),
                    )
                },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.lockAppAfter(),
                title = stringResource(MR.strings.lock_when_idle),
                enabled = authSupported && useAuth,
                entries = LockAfterValues
                    .associateWith {
                        when (it) {
                            -1 -> stringResource(MR.strings.lock_never)
                            0 -> stringResource(MR.strings.lock_always)
                            else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                        }
                    }
                    .toImmutableMap(),
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.stringResource(MR.strings.lock_when_idle),
                    )
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = securityPreferences.hideNotificationContent(),
                title = stringResource(MR.strings.hide_notification_content),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.secureScreen(),
                title = stringResource(MR.strings.secure_screen),
                entries = SecurityPreferences.SecureScreenMode.entries
                    .associateWith { stringResource(it.titleRes) }
                    .toImmutableMap(),
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
        )
    }
}

private val LockAfterValues = persistentListOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
