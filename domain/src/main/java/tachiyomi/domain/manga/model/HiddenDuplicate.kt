package tachiyomi.domain.manga.model

data class HiddenDuplicate(
    val id: Long,
    val manga1Id: Long,
    val manga2Id: Long,
)
