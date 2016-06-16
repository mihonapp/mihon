package eu.kanade.tachiyomi.widget.preference

import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.view.View
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import kotlinx.android.synthetic.main.pref_library_columns.view.*
import uy.kohesive.injekt.injectLazy

class LibraryColumnsDialog : PreferenceDialogFragmentCompat() {

    companion object {

        fun newInstance(preference: Preference): LibraryColumnsDialog {
            val fragment = LibraryColumnsDialog()
            val bundle = Bundle(1)
            bundle.putString("key", preference.key)
            fragment.arguments = bundle
            return fragment
        }
    }

    var portrait: Int = 0
    var landscape: Int = 0

    val preferences: PreferencesHelper by injectLazy()

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        portrait = preferences.portraitColumns().getOrDefault()
        landscape = preferences.landscapeColumns().getOrDefault()

        view.portrait_columns.value = portrait
        view.landscape_columns.value = landscape

        view.portrait_columns.setOnValueChangedListener { picker, oldValue, newValue ->
            portrait = newValue
        }

        view.landscape_columns.setOnValueChangedListener { picker, oldValue, newValue ->
            landscape = newValue
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            preferences.portraitColumns().set(portrait)
            preferences.landscapeColumns().set(landscape)
        }
    }

}
