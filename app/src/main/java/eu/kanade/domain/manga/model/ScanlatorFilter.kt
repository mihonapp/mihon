package eu.kanade.domain.manga.model

data class ScanlatorFilter(
    val scanlator: String?,
    val priority: Int,
) {
    companion object {
        const val EXCLUDED = -1
    }
}
