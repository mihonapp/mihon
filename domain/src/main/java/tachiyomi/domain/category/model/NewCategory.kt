package tachiyomi.domain.category.model

data class NewCategory(
    val name: String,
    val flags: Long,
    val parentId: Long? = null,
)
