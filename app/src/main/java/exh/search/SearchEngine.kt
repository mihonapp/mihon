package exh.search

import exh.metadata.sql.tables.SearchMetadataTable
import exh.metadata.sql.tables.SearchTagTable
import exh.metadata.sql.tables.SearchTitleTable

class SearchEngine {
    private val queryCache = mutableMapOf<String, List<QueryComponent>>()

    fun textToSubQueries(namespace: String?,
                         component: Text?): Pair<String, List<String>>? {
        val maybeLenientComponent = component?.let {
            if (!it.exact)
                    it.asLenientTagQueries()
                else
                    listOf(it.asQuery())
        }
        val componentTagQuery = maybeLenientComponent?.let {
            val params = mutableListOf<String>()
            it.map { q ->
                params += q
                "${SearchTagTable.TABLE}.${SearchTagTable.COL_NAME} LIKE ?"
            }.joinToString(separator = " OR ", prefix = "(", postfix = ")") to params
        }
        return if(namespace != null) {
            var query = """
                (SELECT ${SearchTagTable.COL_MANGA_ID} AS $COL_MANGA_ID FROM ${SearchTagTable.TABLE}
                    WHERE ${SearchTagTable.COL_NAMESPACE} IS NOT NULL
                    AND ${SearchTagTable.COL_NAMESPACE} LIKE ?
            """.trimIndent()
            val params = mutableListOf(escapeLike(namespace))
            if(componentTagQuery != null) {
                query += "\n    AND ${componentTagQuery.first}"
                params += componentTagQuery.second
            }

            "$query)" to params
        } else if(component != null) {
            // Match title + tags
            val tagQuery = """
                SELECT ${SearchTagTable.COL_MANGA_ID} AS $COL_MANGA_ID FROM ${SearchTagTable.TABLE}
                    WHERE ${componentTagQuery!!.first}
            """.trimIndent() to componentTagQuery.second

            val titleQuery = """
                SELECT ${SearchTitleTable.COL_MANGA_ID} AS $COL_MANGA_ID FROM ${SearchTitleTable.TABLE}
                    WHERE ${SearchTitleTable.COL_TITLE} LIKE ?
            """.trimIndent() to listOf(component.asLenientTitleQuery())

            "(${tagQuery.first} UNION ${titleQuery.first})".trimIndent() to
                    (tagQuery.second + titleQuery.second)
        } else null
    }

    fun queryToSql(q: List<QueryComponent>): Pair<String, List<String>> {
        val wheres = mutableListOf<String>()
        val whereParams = mutableListOf<String>()

        val include = mutableListOf<Pair<String, List<String>>>()
        val exclude = mutableListOf<Pair<String, List<String>>>()

        for(component in q) {
            val query = if(component is Text) {
                textToSubQueries(null, component)
            } else if(component is Namespace) {
                if(component.namespace == "uploader") {
                    wheres += "meta.${SearchMetadataTable.COL_UPLOADER} LIKE ?"
                    whereParams += component.tag!!.rawTextEscapedForLike()
                    null
                } else {
                    if(component.tag!!.components.size > 0) {
                        //Match namespace + tags
                        textToSubQueries(component.namespace, component.tag)
                    } else {
                        //Perform namespace search
                        textToSubQueries(component.namespace, null)
                    }
                }
            } else error("Unknown query component!")

            if(query != null) {
                (if(component.excluded) exclude else include) += query
            }
        }

        val completeParams = mutableListOf<String>()
        var baseQuery = """
            SELECT ${SearchMetadataTable.COL_MANGA_ID}
            FROM ${SearchMetadataTable.TABLE} meta
        """.trimIndent()

        include.forEachIndexed { index, pair ->
            baseQuery += "\n" + ("""
                INNER JOIN ${pair.first} i$index
                ON i$index.$COL_MANGA_ID = meta.${SearchMetadataTable.COL_MANGA_ID}
            """.trimIndent())
            completeParams += pair.second
        }


        exclude.forEach {
            wheres += """
                (meta.${SearchMetadataTable.COL_MANGA_ID} NOT IN ${it.first})
            """.trimIndent()
            whereParams += it.second
        }
        if(wheres.isNotEmpty()) {
            completeParams += whereParams
            baseQuery += "\nWHERE\n"
            baseQuery += wheres.joinToString("\nAND\n")
        }
        baseQuery += "\nORDER BY ${SearchMetadataTable.COL_MANGA_ID}"

        return baseQuery to completeParams
    }

    fun parseQuery(query: String, enableWildcard: Boolean = true) = queryCache.getOrPut(query) {
        val res = mutableListOf<QueryComponent>()

        var inQuotes = false
        val queuedRawText = StringBuilder()
        val queuedText = mutableListOf<TextComponent>()
        var namespace: Namespace? = null

        var nextIsExcluded = false
        var nextIsExact = false

        fun flushText() {
            if(queuedRawText.isNotEmpty()) {
                queuedText += StringTextComponent(queuedRawText.toString())
                queuedRawText.setLength(0)
            }
        }

        fun flushToText() = Text().apply {
            components += queuedText
            queuedText.clear()
        }

        fun flushAll() {
            flushText()
            if (queuedText.isNotEmpty() || namespace != null) {
                val component = namespace?.apply {
                    tag = flushToText()
                    namespace = null
                } ?: flushToText()
                component.excluded = nextIsExcluded
                component.exact = nextIsExact
                res += component
            }
        }

        for(char in query.toLowerCase()) {
            if(char == '"') {
                inQuotes = !inQuotes
            } else if(enableWildcard && (char == '?' || char == '_')) {
                flushText()
                queuedText.add(SingleWildcard(char.toString()))
            } else if(enableWildcard && (char == '*' || char == '%')) {
                flushText()
                queuedText.add(MultiWildcard(char.toString()))
            } else if(char == '-') {
                nextIsExcluded = true
            } else if(char == '$') {
                nextIsExact = true
            } else if(char == ':') {
                flushText()
                var flushed = flushToText().rawTextOnly()
                //Map tag aliases
                flushed = when(flushed) {
                    "a" -> "artist"
                    "c", "char" -> "character"
                    "f" -> "female"
                    "g", "creator", "circle" -> "group"
                    "l", "lang" -> "language"
                    "m" -> "male"
                    "p", "series" -> "parody"
                    "r" -> "reclass"
                    else -> flushed
                }
                namespace = Namespace(flushed, null)
            } else if(char == ' ' && !inQuotes) {
                flushAll()
            } else {
                queuedRawText.append(char)
            }
        }
        flushAll()

        res
    }

    companion object {
        private const val COL_MANGA_ID = "cmid"

        fun escapeLike(string: String): String {
            return string.replace("\\", "\\\\")
                    .replace("_", "\\_")
                    .replace("%", "\\%")

        }
    }
}
