package eu.kanade.domain.category.model

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import java.io.Serializable
import eu.kanade.tachiyomi.data.database.models.Category as DbCategory

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
) : Serializable {

    val displayMode: Long
        get() = flags and DisplayModeSetting.MASK

    val sortMode: Long
        get() = flags and SortModeSetting.MASK

    val sortDirection: Long
        get() = flags and SortDirectionSetting.MASK

    companion object {
        val default = { context: Context ->
            Category(
                id = 0,
                name = context.getString(R.string.label_default),
                order = 0,
                flags = 0,
            )
        }
    }
}

internal fun List<Category>.anyWithName(name: String): Boolean {
    return any { name.equals(it.name, ignoreCase = true) }
}

fun Category.toDbCategory(): DbCategory = CategoryImpl().also {
    it.name = name
    it.id = id.toInt()
    it.order = order.toInt()
    it.flags = flags.toInt()
}
