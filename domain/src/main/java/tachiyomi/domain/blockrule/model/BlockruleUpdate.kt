package tachiyomi.domain.blockrule.model

data class BlockruleUpdate(
    val id: Long,
    val name: String? = null,
    val rule: String? = null,
    val type: String? = null,
    val sort: Long? = null,
    val enable: Long? = null,
)
