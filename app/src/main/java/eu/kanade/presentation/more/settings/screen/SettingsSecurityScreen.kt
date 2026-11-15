package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.telemetryIncluded
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.firebase_summary
import tachiyomi.i18n.hide_notification_content
import tachiyomi.i18n.komikita_hide_category_sources
import tachiyomi.i18n.komikita_hide_category_sources_summary
import tachiyomi.i18n.komikita_lock_categories
import tachiyomi.i18n.komikita_lock_categories_summary
import tachiyomi.i18n.lock_after_mins
import tachiyomi.i18n.lock_always
import tachiyomi.i18n.lock_never
import tachiyomi.i18n.lock_when_idle
import tachiyomi.i18n.lock_with_biometrics
import tachiyomi.i18n.onboarding_permission_analytics
import tachiyomi.i18n.onboarding_permission_analytics_description
import tachiyomi.i18n.onboarding_permission_crashlytics
import tachiyomi.i18n.onboarding_permission_crashlytics_description
import tachiyomi.i18n.pref_category_security
import tachiyomi.i18n.pref_firebase
import tachiyomi.i18n.pref_security
import tachiyomi.i18n.secure_screen
import tachiyomi.i18n.secure_screen_summary
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val securityPreferences = remember { context.appGraph.securityPreferences }
        val privacyPreferences = remember { context.appGraph.privacyPreferences }
        return buildList(2) {
            add(getSecurityGroup(securityPreferences))
            if (!telemetryIncluded) return@buildList
            add(getFirebaseGroup(privacyPreferences))
        }
    }

    @Composable
    private fun getSecurityGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val authSupported = remember { context.isAuthenticationSupported() }
        val libraryPreferences = remember { context.appGraph.libraryPreferences }
        val useAuthPref = securityPreferences.useAuthenticator
        val useAuth by useAuthPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_security),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.authenticateHiddenCategories,
                    title = stringResource(MR.strings.komikita_lock_categories),
                    subtitle = stringResource(MR.strings.komikita_lock_categories_summary),
                    onValueChanged = {
                        val authenticated = (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.komikita_lock_categories),
                            allowUnsupported = false,
                        )
                        if (authenticated) context.appGraph.hiddenLibrarySession.lock()
                        authenticated
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.hideSourcesInHiddenCategories,
                    title = stringResource(MR.strings.komikita_hide_category_sources),
                    subtitle = stringResource(MR.strings.komikita_hide_category_sources_summary),
                    onValueChanged = { enabled ->
                        if (enabled || !securityPreferences.authenticateHiddenCategories.get() ||
                            libraryPreferences.hiddenCategories.get().isEmpty()
                        ) {
                            true
                        } else {
                            (context as FragmentActivity).authenticate(
                                title = context.stringResource(MR.strings.komikita_hide_category_sources),
                                allowUnsupported = false,
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = useAuthPref,
                    title = stringResource(MR.strings.lock_with_biometrics),
                    visible = authSupported,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_with_biometrics),
                            allowUnsupported = false,
                        )
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.lockAppAfter,
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                            }
                        },
                    title = stringResource(MR.strings.lock_when_idle),
                    visible = authSupported && useAuth,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_when_idle),
                            allowUnsupported = false,
                        )
                    },
                ),

                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.hideNotificationContent,
                    title = stringResource(MR.strings.hide_notification_content),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.secureScreen,
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.secure_screen),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
            ),
        )
    }

    @Composable
    private fun getFirebaseGroup(
        privacyPreferences: PrivacyPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_firebase),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.crashlytics,
                    title = stringResource(MR.strings.onboarding_permission_crashlytics),
                    subtitle = stringResource(MR.strings.onboarding_permission_crashlytics_description),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.analytics,
                    title = stringResource(MR.strings.onboarding_permission_analytics),
                    subtitle = stringResource(MR.strings.onboarding_permission_analytics_description),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.firebase_summary)),
            ),
        )
    }
}

private val LockAfterValues = listOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
