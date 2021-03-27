package eu.kanade.tachiyomi.util.preference

import android.widget.CompoundButton
import android.widget.Spinner
import androidx.annotation.ArrayRes
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.widget.IgnoreFirstSpinnerListener

/**
 * Binds a checkbox or switch view with a boolean preference.
 */
fun CompoundButton.bindToPreference(pref: Preference<Boolean>) {
    isChecked = pref.get()
    setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
}

/**
 * Binds a spinner to an int preference with an optional offset for the value.
 */
fun Spinner.bindToPreference(pref: Preference<Int>, offset: Int = 0) {
    onItemSelectedListener = IgnoreFirstSpinnerListener { position ->
        pref.set(position + offset)
    }
    setSelection(pref.get() - offset, false)
}

/**
 * Binds a spinner to an enum preference.
 */
inline fun <reified T : Enum<T>> Spinner.bindToPreference(pref: Preference<T>) {
    val enumConstants = T::class.java.enumConstants

    onItemSelectedListener = IgnoreFirstSpinnerListener { position ->
        enumConstants?.get(position)?.let { pref.set(it) }
    }

    enumConstants?.indexOf(pref.get())?.let { setSelection(it, false) }
}

/**
 * Binds a spinner to an int preference. The position of the spinner item must
 * correlate with the [intValues] resource item (in arrays.xml), which is a <string-array>
 * of int values that will be parsed here and applied to the preference.
 */
fun Spinner.bindToIntPreference(pref: Preference<Int>, @ArrayRes intValuesResource: Int) {
    val intValues = resources.getStringArray(intValuesResource).map { it.toIntOrNull() }
    onItemSelectedListener = IgnoreFirstSpinnerListener { position ->
        pref.set(intValues[position]!!)
    }
    setSelection(intValues.indexOf(pref.get()), false)
}
