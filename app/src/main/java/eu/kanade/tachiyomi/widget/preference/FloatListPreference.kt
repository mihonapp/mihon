package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

class FloatListPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        ListPreference(context, attrs) {

    override fun persistString(value: String?): Boolean {
        return value != null && persistFloat(value.toFloat())
    }

    override fun getPersistedString(defaultReturnValue: String?): String? {
        // When the underlying preference is using a PreferenceDataStore, there's no way (for now)
        // to check if a value is in the store, so we use a most likely unused value as workaround
        val defaultIntValue = Float.NEGATIVE_INFINITY

        val value = getPersistedFloat(defaultIntValue)
        return if (value != defaultIntValue) {
            value.toString()
        } else {
            defaultReturnValue
        }
    }
}
