package eu.kanade.domain.manga.model

data class ScanlatorFilter(
    val scanlator: String?,
    val priority: Int,
    val excluded: Boolean,
)

