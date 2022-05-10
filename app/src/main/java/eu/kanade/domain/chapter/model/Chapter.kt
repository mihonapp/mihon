package eu.kanade.domain.chapter.model

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val dateFetch: Long,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Long,
    val chapterNumber: Float,
    val scanlator: String?,
)
