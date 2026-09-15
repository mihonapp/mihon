package android.widget

/** Editable preference values; no Android window is created. */
open class EditText(val context: android.content.Context) {
    var inputType: Int = 0
    var text: CharSequence = ""
    var singleLine: Boolean = false
        private set
    var selectAllOnFocus: Boolean = false
        private set
    fun setSingleLine(value: Boolean) {
        singleLine = value
    }
    fun setSelectAllOnFocus(value: Boolean) {
        selectAllOnFocus = value
    }
}
