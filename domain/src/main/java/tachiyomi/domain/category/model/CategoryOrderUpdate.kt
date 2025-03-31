package tachiyomi.domain.category.model

data class CategoryOrderUpdate(
    val id: Long,
    val oldOrder: Long,
    val newOrder: Long,
)
