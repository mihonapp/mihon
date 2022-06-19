package eu.kanade.domain.category.model

import java.io.Serializable
import eu.kanade.tachiyomi.data.database.models.Category as DbCategory

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
) : Serializable

fun Category.toDbCategory(): DbCategory = DbCategory.create(name).also {
    it.id = id.toInt()
    it.order = order.toInt()
    it.flags = flags.toInt()
}
