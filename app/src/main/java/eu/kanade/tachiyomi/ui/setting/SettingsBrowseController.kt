package eu.kanade.tachiyomi.ui.setting

import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.infoPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.requireAuthentication
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsBrowseController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.browse

        preferenceCategory {
            titleRes = R.string.label_extensions

            switchPreference {
                bindTo(preferences.automaticExtUpdates())
                titleRes = R.string.pref_enable_automatic_extension_updates

                onChange { newValue ->
                    val checked = newValue as Boolean
                    ExtensionUpdateJob.setupTask(activity!!, checked)
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.action_global_search

            switchPreference {
                key = Keys.searchPinnedSourcesOnly
                titleRes = R.string.pref_search_pinned_sources_only
                defaultValue = false
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_nsfw_content

            switchPreference {
                bindTo(preferences.showNsfwSource())
                titleRes = R.string.pref_show_nsfw_source
                summaryRes = R.string.requires_app_restart

                if (context.isAuthenticationSupported() && activity != null) {
                    requireAuthentication(
                        activity as? FragmentActivity,
                        context.getString(R.string.pref_category_nsfw_content),
                        context.getString(R.string.confirm_lock_change),
                    )
                }
            }

            infoPreference(R.string.parental_controls_info)
        }
    }
}
