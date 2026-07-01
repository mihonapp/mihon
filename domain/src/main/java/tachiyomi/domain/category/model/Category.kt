package tachiyomi.domain.category.model

import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID
    val isIncognito: Boolean = (flags and INCOGNITO_MASK) !=0L

    companion object {
        const val UNCATEGORIZED_ID = 0L
        const val INCOGNITO_MASK = 0x2L
    }
}
