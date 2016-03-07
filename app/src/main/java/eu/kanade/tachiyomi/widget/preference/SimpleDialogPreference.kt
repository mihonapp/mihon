package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.support.v7.preference.DialogPreference
import android.support.v7.preference.R.attr
import android.util.AttributeSet

open class SimpleDialogPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = attr.dialogPreferenceStyle, defStyleRes: Int = 0) :
        DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

}
