package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import java.io.Serializable
import eu.kanade.domain.category.model.Category as DomainCategory

interface Category : Serializable {

    var id: Int?

    var name: String

    var order: Int

    var flags: Int

    private fun setFlags(flag: Int, mask: Int) {
        flags = flags and mask.inv() or (flag and mask)
    }

    var displayMode: Int
        get() = flags and DisplayModeSetting.MASK.toInt()
        set(mode) = setFlags(mode, DisplayModeSetting.MASK.toInt())

    var sortMode: Int
        get() = flags and SortModeSetting.MASK.toInt()
        set(mode) = setFlags(mode, SortModeSetting.MASK.toInt())

    var sortDirection: Int
        get() = flags and SortDirectionSetting.MASK.toInt()
        set(mode) = setFlags(mode, SortDirectionSetting.MASK.toInt())
}

fun Category.toDomainCategory(): DomainCategory? {
    val categoryId = id ?: return null
    return DomainCategory(
        id = categoryId.toLong(),
        name = this.name,
        order = this.order.toLong(),
        flags = this.flags.toLong(),
    )
}
