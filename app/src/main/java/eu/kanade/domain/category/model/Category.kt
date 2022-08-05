package eu.kanade.domain.category.model

import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    val displayMode: Long
        get() = flags and DisplayModeSetting.MASK

    val sortMode: Long
        get() = flags and SortModeSetting.MASK

    val sortDirection: Long
        get() = flags and SortDirectionSetting.MASK

    companion object {

        const val UNCATEGORIZED_ID = 0L
    }
}

internal fun List<Category>.anyWithName(name: String): Boolean {
    return any { name.equals(it.name, ignoreCase = true) }
}
