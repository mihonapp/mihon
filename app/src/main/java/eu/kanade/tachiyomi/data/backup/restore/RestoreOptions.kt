package eu.kanade.tachiyomi.data.backup.restore

data class RestoreOptions(
    val appSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    val library: Boolean = true,
) {
    fun toBooleanArray() = booleanArrayOf(appSettings, sourceSettings, library)

    companion object {
        fun fromBooleanArray(booleanArray: BooleanArray) = RestoreOptions(
            appSettings = booleanArray[0],
            sourceSettings = booleanArray[1],
            library = booleanArray[2],
        )
    }
}
