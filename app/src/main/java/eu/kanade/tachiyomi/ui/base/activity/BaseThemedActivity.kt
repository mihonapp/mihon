package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext
import uy.kohesive.injekt.injectLazy

abstract class BaseThemedActivity : AppCompatActivity() {

    val preferences: PreferencesHelper by injectLazy()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(preferences)
        super.onCreate(savedInstanceState)
    }

    companion object {
        fun AppCompatActivity.applyAppTheme(preferences: PreferencesHelper) {
            getThemeResIds(preferences.appTheme().get(), preferences.themeDarkAmoled().get())
                .forEach { setTheme(it) }
        }

        fun getThemeResIds(appTheme: PreferenceValues.AppTheme, isAmoled: Boolean): List<Int> {
            val resIds = mutableListOf<Int>()
            when (appTheme) {
                PreferenceValues.AppTheme.MONET -> {
                    resIds += R.style.Theme_Tachiyomi_Monet
                }
                PreferenceValues.AppTheme.BLUE -> {
                    resIds += R.style.Theme_Tachiyomi_Blue
                    resIds += R.style.ThemeOverlay_Tachiyomi_ColoredBars
                }
                PreferenceValues.AppTheme.GREEN_APPLE -> {
                    resIds += R.style.Theme_Tachiyomi_GreenApple
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
