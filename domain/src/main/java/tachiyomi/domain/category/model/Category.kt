package tachiyomi.domain.category.model

import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val contentType: Int = CONTENT_TYPE_ALL, // 0=all, 1=manga, 2=novel
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L

        // Content type constants
        const val CONTENT_TYPE_ALL = 0
        const val CONTENT_TYPE_MANGA = 1
        const val CONTENT_TYPE_NOVEL = 2
    }
}
