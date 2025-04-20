package tachiyomi.domain.blockrule.model

import java.io.Serializable
data class Blockrule(
    val id: Long,
    val name: String,
    val type: Type,
    val rule: String,
    val sort: Long,
    val enable: Boolean = true,
) : Serializable {
    enum class Type{
        AUTHOR_EQUALS,
        AUTHOR_CONTAINS,

        TITLE_REGEX,
        TITLE_CONTAINS,
        TITLE_STARTS_WITH,
        TITLE_ENDS_WITH,
        TITLE_EQUALS,

        DESCRIPTION_REGEX,
        DESCRIPTION_CONTAINS,
    }
}
