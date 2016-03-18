package eu.kanade.tachiyomi.ui.reader

import android.app.Dialog
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.widget.PopupWindow
import android.widget.SeekBar
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.listener.SimpleSeekBarListener
import kotlinx.android.synthetic.main.reader_popup.view.*
import java.lang.ref.WeakReference

class ReaderPopupMenu(activity: ReaderActivity, val view: View) : PopupWindow(view, WRAP_CONTENT, WRAP_CONTENT) {

    val activity = WeakReference(activity)

    init {
        animationStyle = R.style.reader_settings_popup_animation
        setValues(activity.preferences)
    }

    private fun setValues(preferences: PreferencesHelper) = with(view) {
        enable_transitions.isChecked = preferences.enableTransitions().getOrDefault()
        show_page_number.isChecked = preferences.showPageNumber().getOrDefault()
        hide_status_bar.isChecked = preferences.hideStatusBar().getOrDefault()
        keep_screen_on.isChecked = preferences.keepScreenOn().getOrDefault()
        reader_theme.isChecked = preferences.readerTheme().getOrDefault() == 1

        setDecoderInitial(preferences.imageDecoder().getOrDefault())

        // Add a listener to change the corresponding setting
        enable_transitions.setOnCheckedChangeListener { v, isChecked ->
            preferences.enableTransitions().set(isChecked)
        }

        show_page_number.setOnCheckedChangeListener { v, isChecked ->
            preferences.showPageNumber().set(isChecked)
        }

        hide_status_bar.setOnCheckedChangeListener { v, isChecked ->
            preferences.hideStatusBar().set(isChecked)
        }

        keep_screen_on.setOnCheckedChangeListener { v, isChecked ->
            preferences.keepScreenOn().set(isChecked)
        }

        reader_theme.setOnCheckedChangeListener { v, isChecked ->
            preferences.readerTheme().set(if (isChecked) 1 else 0)
        }

        image_decoder_container.setOnClickListener { v ->
            showImmersiveDialog(MaterialDialog.Builder(view.context)
                    .title(R.string.pref_image_decoder)
                    .items(R.array.image_decoders)
                    .itemsCallbackSingleChoice(preferences.imageDecoder().getOrDefault(), { dialog, itemView, which, text ->
                        preferences.imageDecoder().set(which)
                        setDecoderInitial(which)
                        true
                    })
                    .build())
        }

        activity.get().subscriptions.add(preferences.customBrightness().asObservable()
                .subscribe { isEnabled ->
                    custom_brightness.isChecked = isEnabled
                    brightness_seekbar.isEnabled = isEnabled
                })

        custom_brightness.setOnCheckedChangeListener { v, isChecked ->
            preferences.customBrightness().set(isChecked)
        }

        brightness_seekbar.max = 100
        brightness_seekbar.progress = Math.round(
                preferences.customBrightnessValue().getOrDefault() * brightness_seekbar.max)
        brightness_seekbar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    preferences.customBrightnessValue().set(progress.toFloat() / seekBar.max)
                }
            }
        })
    }

    private fun setDecoderInitial(decoder: Int) {
        val initial: String
        when (decoder) {
            0 -> initial = "R"
            1 -> initial = "S"
            else -> initial = ""
        }
        view.image_decoder_initial.text = initial
    }

    private fun showImmersiveDialog(dialog: Dialog) {
        // Hack to not leave immersive mode
        dialog.window.setFlags(FLAG_NOT_FOCUSABLE, FLAG_NOT_FOCUSABLE)
        dialog.show()
        dialog.window.decorView.systemUiVisibility = activity.get().window.decorView.systemUiVisibility
        dialog.window.clearFlags(FLAG_NOT_FOCUSABLE)
    }

}