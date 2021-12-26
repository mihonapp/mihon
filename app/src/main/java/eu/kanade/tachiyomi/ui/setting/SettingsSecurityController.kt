package eu.kanade.tachiyomi.ui.setting

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.requireAuthentication
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsSecurityController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_security

        if (context.isAuthenticationSupported()) {
            switchPreference {
                bindTo(preferences.useAuthenticator())
                titleRes = R.string.lock_with_biometrics

                requireAuthentication(
                    activity as? FragmentActivity,
                    context.getString(R.string.lock_with_biometrics),
                    context.getString(R.string.confirm_lock_change),
                )
            }

            intListPreference {
                bindTo(preferences.lockAppAfter())
                titleRes = R.string.lock_when_idle
                val values = arrayOf("0", "1", "2", "5", "10", "-1")
                entries = values.mapNotNull {
                    when (it) {
                        "-1" -> context.getString(R.string.lock_never)
                        "0" -> context.getString(R.string.lock_always)
                        else -> resources?.getQuantityString(R.plurals.lock_after_mins, it.toInt(), it)
                    }
                }.toTypedArray()
                entryValues = values
                summary = "%s"
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    if (value == newValue) return@OnPreferenceChangeListener false

                    (activity as? FragmentActivity)?.startAuthentication(
                        activity!!.getString(R.string.lock_when_idle),
                        activity!!.getString(R.string.confirm_lock_change),
                        callback = object : AuthenticatorUtil.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                activity: FragmentActivity?,
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                super.onAuthenticationSucceeded(activity, result)
                                value = newValue as String
                            }

                            override fun onAuthenticationError(
                                activity: FragmentActivity?,
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                super.onAuthenticationError(activity, errorCode, errString)
                                activity?.toast(errString.toString())
                            }
                        }
                    )
                    false
                }

                visibleIf(preferences.useAuthenticator()) { it }
            }
        }

        switchPreference {
            bindTo(preferences.secureScreen())
            titleRes = R.string.secure_screen
            summaryRes = R.string.secure_screen_summary
        }

        switchPreference {
            key = Keys.hideNotificationContent
            titleRes = R.string.hide_notification_content
            defaultValue = false
        }
    }
}
