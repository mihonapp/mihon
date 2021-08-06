package eu.kanade.tachiyomi.ui.base.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

abstract class BaseThemedActivity : AppCompatActivity() {

    val preferences: PreferencesHelper by injectLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(preferences)
        super.onCreate(savedInstanceState)
    }

    companion object {
        fun AppCompatActivity.applyAppTheme(preferences: PreferencesHelper) {
            val resIds = mutableListOf<Int>()
            when (preferences.appTheme().get()) {
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

            if (preferences.themeDarkAmoled().get()) {
                resIds += R.style.ThemeOverlay_Tachiyomi_Amoled
            }

            resIds.forEach {
                setTheme(it)
            }
        }
    }
}
