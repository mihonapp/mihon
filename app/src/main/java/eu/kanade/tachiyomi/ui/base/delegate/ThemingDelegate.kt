package eu.kanade.tachiyomi.ui.base.delegate

import android.app.Activity
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ThemingDelegate {
    fun applyAppTheme(activity: Activity)

    companion object {
        fun getThemeResIds(appTheme: PreferenceValues.AppTheme, isAmoled: Boolean): List<Int> {
            val resIds = mutableListOf<Int>()
            when (appTheme) {
                PreferenceValues.AppTheme.MONET -> {
                    resIds += R.style.Theme_Tachiyomi_Monet
                }
                PreferenceValues.AppTheme.GREEN_APPLE -> {
                    resIds += R.style.Theme_Tachiyomi_GreenApple
                }
                PreferenceValues.AppTheme.LAVENDER -> {
                    resIds += R.style.Theme_Tachiyomi_Lavender
                }
                PreferenceValues.AppTheme.MIDNIGHT_DUSK -> {
                    resIds += R.style.Theme_Tachiyomi_MidnightDusk
                }
                PreferenceValues.AppTheme.STRAWBERRY_DAIQUIRI -> {
                    resIds += R.style.Theme_Tachiyomi_StrawberryDaiquiri
                }
                PreferenceValues.AppTheme.TAKO -> {
                    resIds += R.style.Theme_Tachiyomi_Tako
                }
                PreferenceValues.AppTheme.TEALTURQUOISE -> {
                    resIds += R.style.Theme_Tachiyomi_TealTurquoise
                }
                PreferenceValues.AppTheme.YINYANG -> {
                    resIds += R.style.Theme_Tachiyomi_YinYang
                }
                PreferenceValues.AppTheme.YOTSUBA -> {
                    resIds += R.style.Theme_Tachiyomi_Yotsuba
                }
                else -> {
                    resIds += R.style.Theme_Tachiyomi
                }
            }

            if (isAmoled) {
                resIds += R.style.ThemeOverlay_Tachiyomi_Amoled
            }

            return resIds
        }
    }
}

class ThemingDelegateImpl : ThemingDelegate {
    override fun applyAppTheme(activity: Activity) {
        val preferences = Injekt.get<PreferencesHelper>()
        ThemingDelegate.getThemeResIds(preferences.appTheme().get(), preferences.themeDarkAmoled().get())
            .forEach { activity.setTheme(it) }
    }
}
