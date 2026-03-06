package tachiyomi.domain.manga.model

data class HiddenDuplicate(
    val manga1Id: Long,
    val manga2Id: Long,
)
