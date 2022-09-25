package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceScreen
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.initThenAdd
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.widget.preference.ThemesPreference
import uy.kohesive.injekt.injectLazy
import java.util.Date

class SettingsAppearanceController : SettingsController() {

    private var themesPreference: ThemesPreference? = null
    private val uiPreferences: UiPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_appearance

        preferenceCategory {
            titleRes = R.string.pref_category_theme

            listPreference {
                bindTo(uiPreferences.themeMode())
                titleRes = R.string.pref_theme_mode

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    entriesRes = arrayOf(
                        R.string.theme_system,
                        R.string.theme_light,
                        R.string.theme_dark,
                    )
                    entryValues = arrayOf(
                        ThemeMode.SYSTEM.name,
                        ThemeMode.LIGHT.name,
                        ThemeMode.DARK.name,
                    )
                } else {
                    entriesRes = arrayOf(
                        R.string.theme_light,
                        R.string.theme_dark,
                    )
                    entryValues = arrayOf(
                        ThemeMode.LIGHT.name,
                        ThemeMode.DARK.name,
                    )
                }

                summary = "%s"
            }
            themesPreference = initThenAdd(ThemesPreference(context)) {
                bindTo(uiPreferences.appTheme())
                titleRes = R.string.pref_app_theme

                val appThemes = AppTheme.values().filter {
                    val monetFilter = if (it == AppTheme.MONET) {
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
                bindTo(uiPreferences.themeDarkAmoled())
                titleRes = R.string.pref_dark_theme_pure_black

                visibleIf(uiPreferences.themeMode()) { it != ThemeMode.LIGHT }

                onChange {
                    activity?.let { ActivityCompat.recreate(it) }
                    true
                }
            }
        }

        if (context.isTablet()) {
            preferenceCategory {
                titleRes = R.string.pref_category_navigation

                intListPreference {
                    bindTo(uiPreferences.sideNavIconAlignment())
                    titleRes = R.string.pref_side_nav_icon_alignment
                    entriesRes = arrayOf(
                        R.string.alignment_top,
                        R.string.alignment_center,
                        R.string.alignment_bottom,
                    )
                    entryValues = arrayOf("0", "1", "2")
                    summary = "%s"
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_timestamps

            intListPreference {
                bindTo(uiPreferences.relativeTime())
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
                bindTo(uiPreferences.dateFormat())
                titleRes = R.string.pref_date_format
                entryValues = arrayOf("", "MM/dd/yy", "dd/MM/yy", "yyyy-MM-dd", "dd MMM yyyy", "MMM dd, yyyy")

                val now = Date().time
                entries = entryValues.map { value ->
                    val formattedDate = UiPreferences.dateFormat(value.toString()).format(now)
                    if (value == "") {
                        "${context.getString(R.string.label_default)} ($formattedDate)"
                    } else {
                        "$value ($formattedDate)"
                    }
                }.toTypedArray()

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
