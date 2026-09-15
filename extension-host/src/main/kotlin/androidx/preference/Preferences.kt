package androidx.preference

import android.content.Context
import android.content.SharedPreferences

open class Preference(open val context: Context) {
    open var key: String = ""
    open var title: CharSequence? = null
    open var summary: CharSequence? = null
    open var defaultValue: Any? = null
    open var isEnabled: Boolean = true
    open var isVisible: Boolean = true
    open var onPreferenceChangeListener: OnPreferenceChangeListener? = null
    open var onPreferenceClickListener: OnPreferenceClickListener? = null

    open fun getSharedPreferences(): SharedPreferences? =
        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)

    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any): Boolean
    }

    fun interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }
}

open class PreferenceGroup(context: Context) : Preference(context) {
    private val preferences = mutableListOf<Preference>()

    open fun addPreference(preference: Preference): Boolean {
        preferences.add(preference)
        return true
    }

    open fun removePreference(preference: Preference): Boolean = preferences.remove(preference)
    open fun removeAll() = preferences.clear()
    open fun getPreferenceCount(): Int = preferences.size
    open fun getPreference(index: Int): Preference? = preferences.getOrNull(index)
}

open class PreferenceScreen(context: Context) : PreferenceGroup(context)

open class DialogPreference(context: Context) : Preference(context) {
    open var dialogTitle: CharSequence? = null
    open var dialogMessage: CharSequence? = null
    open var positiveButtonText: CharSequence? = null
    open var negativeButtonText: CharSequence? = null
}

open class ListPreference(context: Context) : DialogPreference(context) {
    open var entries: Array<CharSequence>? = null
    open var entryValues: Array<CharSequence>? = null
    open var value: String? = null
    open fun findIndexOfValue(value: String?): Int {
        val vals = entryValues ?: return -1
        return vals.indexOfFirst { it.toString() == value }
    }
}

open class MultiSelectListPreference(context: Context) : DialogPreference(context) {
    open var entries: Array<CharSequence>? = null
    open var entryValues: Array<CharSequence>? = null
    open var values: Set<String> = emptySet()
}

open class CheckBoxPreference(context: Context) : Preference(context) {
    open var isChecked: Boolean = false
}

open class SwitchPreferenceCompat(context: Context) : Preference(context) {
    open var isChecked: Boolean = false
    open var summaryOn: CharSequence? = null
    open var summaryOff: CharSequence? = null
}

open class EditTextPreference(context: Context) : DialogPreference(context) {
    open var text: String? = null
    open var onBindEditTextListener: OnBindEditTextListener? = null
    fun interface OnBindEditTextListener {
        fun onBindEditText(editText: android.widget.EditText)
    }
}
