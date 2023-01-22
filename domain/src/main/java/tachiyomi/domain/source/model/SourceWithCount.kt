package tachiyomi.domain.source.model

data class SourceWithCount(
    val source: Source,
    val count: Long,
) {

    val id: Long
        get() = source.id

    val name: String
        get() = source.name
}
