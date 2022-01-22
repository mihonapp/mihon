package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.initThenAdd
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.widget.preference.ThemesPreference
import java.util.Date
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

class SettingsAppearanceController : SettingsController() {

    private var themesPreference: ThemesPreference? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_appearance

        preferenceCategory {
            titleRes = R.string.pref_category_theme

            listPreference {
                bindTo(preferences.themeMode())
                titleRes = R.string.pref_theme_mode

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    entriesRes = arrayOf(
                        R.string.theme_system,
                        R.string.theme_light,
                        R.string.theme_dark
                    )
                    entryValues = arrayOf(
                        Values.ThemeMode.system.name,
                        Values.ThemeMode.light.name,
                        Values.ThemeMode.dark.name
                    )
                } else {
                    entriesRes = arrayOf(
                        R.string.theme_light,
                        R.string.theme_dark
                    )
                    entryValues = arrayOf(
                        Values.ThemeMode.light.name,
                        Values.ThemeMode.dark.name
                    )
                }

                summary = "%s"
            }
            themesPreference = initThenAdd(ThemesPreference(context)) {
                bindTo(preferences.appTheme())
                titleRes = R.string.pref_app_theme

                val appThemes = Values.AppTheme.values().filter {
                    val monetFilter = if (it == Values.AppTheme.MONET) {
                        DeviceUtil.isDynamicColorAvailable
                    } else {
                        true
                    }
                    it.titleResId != null && monetFilter
                }
                entries = appThemes

                onChange {
                    activity?.let { ActivityCompat.recreate(it) }
                    true
                }
            }
            switchPreference {
                bindTo(preferences.themeDarkAmoled())
                titleRes = R.string.pref_dark_theme_pure_black

                visibleIf(preferences.themeMode()) { it != Values.ThemeMode.light }

                onChange {
                    activity?.let { ActivityCompat.recreate(it) }
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_navigation

            if (context.isTablet()) {
                intListPreference {
                    bindTo(preferences.sideNavIconAlignment())
                    titleRes = R.string.pref_side_nav_icon_alignment
                    entriesRes = arrayOf(
                        R.string.alignment_top,
                        R.string.alignment_center,
                        R.string.alignment_bottom,
                    )
                    entryValues = arrayOf("0", "1", "2")
                    summary = "%s"
                }
            } else {
                switchPreference {
                    bindTo(preferences.hideBottomBarOnScroll())
                    titleRes = R.string.pref_hide_bottom_bar_on_scroll
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_timestamps

            intListPreference {
                bindTo(preferences.relativeTime())
                titleRes = R.string.pref_relative_format
                val values = arrayOf("0", "2", "7")
                entryValues = values
                entries = values.map {
                    when (it) {
                        "0" -> context.getString(R.string.off)
                        "2" -> context.getString(R.string.pref_relative_time_short)
                        else -> context.getString(R.string.pref_relative_time_long)
                    }
                }.toTypedArray()
                summary = "%s"
            }

            listPreference {
                key = Keys.dateFormat
                titleRes = R.string.pref_date_format
                entryValues = arrayOf("", "MM/dd/yy", "dd/MM/yy", "yyyy-MM-dd", "dd MMM yyyy", "MMM dd, yyyy")

                val now = Date().time
                entries = entryValues.map { value ->
                    val formattedDate = preferences.dateFormat(value.toString()).format(now)
                    if (value == "") {
                        "${context.getString(R.string.label_default)} ($formattedDate)"
                    } else {
                        "$value ($formattedDate)"
                    }
                }.toTypedArray()

                defaultValue = ""
                summary = "%s"
            }
        }
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        themesPreference?.let {
            outState.putInt(THEMES_SCROLL_POSITION, it.lastScrollPosition ?: 0)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        themesPreference?.lastScrollPosition = savedViewState.getInt(THEMES_SCROLL_POSITION, 0)
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        themesPreference = null
    }
}

private const val THEMES_SCROLL_POSITION = "themesScrollPosition"
