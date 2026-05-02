package tachiyomi.domain.category.model

import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val isSuper: Boolean,
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID || id == UNCATEGORIZED_SUPER_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L
        const val UNCATEGORIZED_SUPER_ID = -1L
    }
}
