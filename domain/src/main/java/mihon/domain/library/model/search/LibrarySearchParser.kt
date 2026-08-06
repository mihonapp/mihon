package mihon.domain.library.model.search

class LibrarySearchParser(private val tokens: List<LibrarySearchLexer.Token>) {
    private var index = 0

    private fun peek(): LibrarySearchLexer.Token? = tokens.getOrNull(index)
    private fun advance(): LibrarySearchLexer.Token? = tokens.getOrNull(index++)

    fun parse(): QueryNode {
        if (tokens.isEmpty()) return AndNode(emptyList())
        return parseOr()
    }

    private fun parseOr(): QueryNode {
        val nodes = mutableListOf<QueryNode>()
        nodes.add(parseAnd())
        while (peek() is LibrarySearchLexer.Token.Or) {
            advance()
            nodes.add(parseAnd())
        }
        return if (nodes.size == 1) nodes.first() else OrNode(nodes)
    }

    private fun parseAnd(): QueryNode {
        val nodes = mutableListOf<QueryNode>()

        while (index < tokens.size && peek() !is LibrarySearchLexer.Token.Or &&
            peek() !is LibrarySearchLexer.Token.RParen
        ) {
            if (peek() is LibrarySearchLexer.Token.And) {
                advance()
            }
            nodes.add(parseTerm())
        }

        val filteredNodes = nodes.filter { it !is EmptyQueryNode }
        if (filteredNodes.isEmpty()) return EmptyQueryNode
        return if (filteredNodes.size == 1) filteredNodes.first() else AndNode(filteredNodes)
    }

    private fun parseTerm(): QueryNode {
        var negated = false

        while (peek() is LibrarySearchLexer.Token.Not) {
            advance()
            negated = !negated
        }

        if (peek() is LibrarySearchLexer.Token.LParen) {
            advance()

            val subTree = parseOr()

            if (peek() is LibrarySearchLexer.Token.RParen) {
                advance()
            }

            if (negated) {
                return NotNode(subTree)
            }
            return subTree
        }

        return when (val nextToken = advance()) {
            is LibrarySearchLexer.Token.General -> GeneralQueryNode(nextToken.value, negated)
            is LibrarySearchLexer.Token.Field -> {
                MangaField.fromString(nextToken.field)?.let {
                    FieldQueryNode(it, nextToken.value, negated)
                } ?: GeneralQueryNode("${nextToken.field}:${nextToken.value}", negated)
            }
            is LibrarySearchLexer.Token.CompField -> {
                ComparisonField.fromString(nextToken.field)?.let {
                    ComparisonQueryNode(it, nextToken.value, Comparator.fromString(nextToken.comparator)!!, negated)
                } ?: GeneralQueryNode("${nextToken.field}${nextToken.comparator}${nextToken.value}", negated)
            }
            else -> EmptyQueryNode
        }
    }
}
