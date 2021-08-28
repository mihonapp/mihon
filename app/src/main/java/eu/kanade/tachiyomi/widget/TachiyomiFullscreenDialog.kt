package eu.kanade.tachiyomi.widget

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatDialog
import eu.kanade.tachiyomi.R

class TachiyomiFullscreenDialog(context: Context, view: View) : AppCompatDialog(context, R.style.ThemeOverlay_Tachiyomi_Dialog_Fullscreen) {

    init {
        setContentView(view)
    }
}
