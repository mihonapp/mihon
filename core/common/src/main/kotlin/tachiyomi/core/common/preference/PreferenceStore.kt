package tachiyomi.core.common.preference

interface PreferenceStore {

    fun getString(key: String, defaultValue: String = ""): Preference<String>

    fun getLong(key: String, defaultValue: Long = 0): Preference<Long>

    fun getInt(key: String, defaultValue: Int = 0): Preference<Int>

    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float>

    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean>

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>>

    fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T>

    fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T>

    fun getAll(): Map<String, *>
}

fun PreferenceStore.getLongArray(
    key: String,
    defaultValue: List<Long>,
): Preference<List<Long>> {
    return getObjectFromString(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.joinToString(",") },
        deserializer = { it.split(",").mapNotNull { l -> l.toLongOrNull() } },
    )
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnum(
    key: String,
    defaultValue: T,
): Preference<T> {
    return getObjectFromString(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.name },
        deserializer = {
            try {
                enumValueOf(it)
            } catch (e: IllegalArgumentException) {
                defaultValue
            }
        },
    )
}
