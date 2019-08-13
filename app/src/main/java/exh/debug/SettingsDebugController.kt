package exh.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.support.v7.preference.PreferenceScreen
import android.text.Html
import android.util.Log
import android.widget.HorizontalScrollView
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.ui.setting.*
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions

class SettingsDebugController : SettingsController() {
    @SuppressLint("SetTextI18n")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "DEBUG MENU"

        preferenceCategory {
            title = "Functions"

            DebugFunctions::class.declaredFunctions.filter {
                it.visibility == KVisibility.PUBLIC
            }.forEach {
                preference {
                    title = it.name.replace(Regex("(.)(\\p{Upper})"), "$1 $2").toLowerCase().capitalize()
                    isPersistent = false

                    onClick {
                        val view = TextView(context)
                        view.setHorizontallyScrolling(true)
                        view.setTextIsSelectable(true)

                        val hView = HorizontalScrollView(context)
                        hView.addView(view)

                        try {
                            val result = it.call(DebugFunctions)
                            view.text = "Function returned result:\n\n$result"
                            MaterialDialog.Builder(context)
                                    .customView(hView, true)
                        } catch(t: Throwable) {
                            view.text = "Function threw exception:\n\n${Log.getStackTraceString(t)}"
                            MaterialDialog.Builder(context)
                                    .customView(hView, true)
                        }.show()
                    }
                }
            }
        }

        preferenceCategory {
            title = "Toggles"

            DebugToggles.values().forEach {
                switchPreference {
                    title = it.name.replace('_', ' ').toLowerCase().capitalize()
                    key = it.prefKey
                    defaultValue = it.default
                    summaryOn = if(it.default) "" else MODIFIED_TEXT
                    summaryOff = if(it.default) MODIFIED_TEXT else ""
                }
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        router.popCurrentController()
    }

    companion object {
        private val MODIFIED_TEXT = Html.fromHtml("<font color='red'>MODIFIED</font>")
    }
}