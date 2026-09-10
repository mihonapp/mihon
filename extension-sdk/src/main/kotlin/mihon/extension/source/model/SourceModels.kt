package mihon.extension.source.model

import kotlinx.serialization.Serializable

@Serializable
data class SManga(
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: Int = UNKNOWN,
    val thumbnailUrl: String? = null,
    val initialized: Boolean = false,
) {
    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
    }
}

@Serializable
data class SChapter(
    val url: String,
    val name: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
)

@Serializable
data class Page(
    val index: Int,
    val url: String = "",
    val imageUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class MangasPage(
    val mangas: List<SManga>,
    val hasNextPage: Boolean,
)
