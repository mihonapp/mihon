package eu.kanade.domain.category.model

import android.content.Context
import eu.kanade.tachiyomi.R
import java.io.Serializable
import eu.kanade.tachiyomi.data.database.models.Category as DbCategory

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
) : Serializable {

    companion object {
        val default = { context: Context ->
            Category(
                id = 0,
                name = context.getString(R.string.default_category),
                order = 0,
                flags = 0,
            )
        }
    }
}

fun Category.toDbCategory(): DbCategory = DbCategory.create(name).also {
    it.id = id.toInt()
    it.order = order.toInt()
    it.flags = flags.toInt()
}
