package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.support.v7.preference.ListPreference
import android.util.AttributeSet

class IntListPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        ListPreference(context, attrs) {

    override fun persistString(value: String?): Boolean {
        return value != null && persistInt(value.toInt())
    }

    override fun getPersistedString(defaultReturnValue: String?): String? {
        if (sharedPreferences.contains(key)) {
            return getPersistedInt(0).toString()
        } else {
            return defaultReturnValue
        }
    }
}