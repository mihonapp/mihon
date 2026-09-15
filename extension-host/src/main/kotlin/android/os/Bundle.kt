package android.os

/** In-memory values only; Android Parcel transport is not available. */
class Bundle {
    private val values = mutableMapOf<String, Any?>()
    fun putString(key: String, value: String?) {
        values[key] = value
    }
    fun getString(key: String): String? = values[key] as? String
    fun putInt(key: String, value: Int) {
        values[key] = value
    }

    @JvmOverloads fun getInt(key: String, defaultValue: Int = 0): Int = values[key] as? Int ?: defaultValue
    fun containsKey(key: String): Boolean = key in values
}
