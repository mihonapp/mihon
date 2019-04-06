package exh.debug

import android.support.v7.preference.PreferenceScreen
import android.util.Log
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import kotlin.reflect.full.declaredFunctions

class SettingsDebugController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "DEBUG MENU"

        DebugFunctions::class.declaredFunctions.forEach {
            preference {
                title = it.name.replace(Regex("(.)(\\p{Upper})"), "$1 $2").toLowerCase().capitalize()
                isPersistent = false

                onClick {
                    try {
                        val result = it.call(DebugFunctions)
                        MaterialDialog.Builder(context)
                                .content("Function returned result:\n\n$result")
                    } catch(t: Throwable) {
                        MaterialDialog.Builder(context)
                                .content("Function threw exception:\n\n${Log.getStackTraceString(t)}")
                    }.show()
                }
            }
        }
    }
}