package mihon.domain.library.model.search

import kotlin.text.get

object LibrarySearchLexer {

    private val regex = Regex(
        """
            # Operators
            (?<LParen> \( )|
            (?<RParen> \) )|
            (?<NOT> -(?![\s,]) )|
            (?<OR> \|\| )|
            (?<AND> && )|

            # Comparison fields
            (?<CompField> [a-zA-Z_][a-zA-Z0-9_]* )
            (?<Comparator> >=|<=|>|<|= )
            (?: " (?<CompValQuoted> [^"]* ) " | ' (?<CompValSingleQuoted> [^']* ) ' | (?<CompVal> [^\s,()]+ ))|

            # Key-Value fields
            (?<Field> [a-zA-Z_][a-zA-Z0-9_]* ) :
            (?: " (?<FieldValQuoted> [^"]* ) " | ' (?<FieldValSingleQuoted> [^']* ) ' | (?<FieldVal> [^\s,()]+ ))|

            # General catch-all
            (?: " (?<GeneralQuoted> [^"]* ) " | ' (?<GeneralSingleQuoted> [^']* ) ' | (?<General> [^\s,()]+ ))|

            # Seperator
            (?<Separator> [\s,]+ )
        """.trimIndent(),
        RegexOption.COMMENTS,
    )

    sealed interface Token {
        data object LParen : Token
        data object RParen : Token
        data object And : Token
        data object Or : Token
        data object Not : Token
        data class CompField(val field: String, val comparator: String, val value: String) : Token
        data class Field(val field: String, val value: String) : Token
        data class General(val value: String) : Token
    }

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matches = regex.findAll(input)

        for (match in matches) {
            val groups = match.groups
            when {
                groups["Separator"] != null -> continue
                groups["AND"] != null -> tokens.add(Token.And)
                groups["OR"] != null -> tokens.add(Token.Or)
                groups["NOT"] != null -> tokens.add(Token.Not)
                groups["LParen"] != null -> tokens.add(Token.LParen)
                groups["RParen"] != null -> tokens.add(Token.RParen)

                groups["CompField"] != null -> {
                    tokens.add(
                        Token.CompField(
                            field = groups["CompField"]!!.value,
                            comparator = groups["Comparator"]!!.value,
                            value = groups["CompValQuoted"]?.value
                                ?: groups["CompValSingleQuoted"]?.value
                                ?: groups["CompVal"]!!.value,
                        ),
                    )
                }

                groups["Field"] != null -> {
                    tokens.add(
                        Token.Field(
                            field = groups["Field"]!!.value,
                            value = groups["FieldValQuoted"]?.value
                                ?: groups["FieldValSingleQuoted"]?.value
                                ?: groups["FieldVal"]!!.value,
                        ),
                    )
                }

                else -> {
                    val value = groups["GeneralQuoted"]?.value
                        ?: groups["GeneralSingleQuoted"]?.value
                        ?: groups["General"]!!.value
                    tokens.add(Token.General(value))
                }
            }
        }

        return tokens
    }
}
