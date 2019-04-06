package exh.search

import exh.plusAssign
import exh.search.SearchEngine.Companion.escapeLike

class Text: QueryComponent() {
    val components = mutableListOf<TextComponent>()

    private var query: String? = null
    private var lenientTitleQuery: String? = null
    private var lenientTagQueries: List<String>? = null
    private var rawText: String? = null

    fun asQuery(): String {
        if(query == null) {
            query = rBaseBuilder().toString()
        }
        return query!!
    }

    fun asLenientTitleQuery(): String {
        if(lenientTitleQuery == null) {
            lenientTitleQuery = StringBuilder("%").append(rBaseBuilder()).append("%").toString()
        }
        return lenientTitleQuery!!
    }

    fun asLenientTagQueries(): List<String> {
        if(lenientTagQueries == null) {
            lenientTagQueries = listOf(
                    //Match beginning of tag
                    rBaseBuilder().append("%").toString(),
                    //Tag word matcher (that matches multiple words)
                    //Can't make it match a single word in Realm :(
                    StringBuilder(" ").append(rBaseBuilder()).append(" ").toString(),
                    StringBuilder(" ").append(rBaseBuilder()).toString(),
                    rBaseBuilder().append(" ").toString()
            )
        }
        return lenientTagQueries!!
    }

    fun rBaseBuilder(): StringBuilder {
        val builder = StringBuilder()
        for(component in components) {
            when(component) {
                is StringTextComponent -> builder += escapeLike(component.value)
                is SingleWildcard -> builder += "_"
                is MultiWildcard -> builder += "%"
            }
        }
        return builder
    }

    fun rawTextOnly() = if(rawText != null)
        rawText!!
    else {
        rawText = components
                .joinToString(separator = "", transform = { it.rawText })
        rawText!!
    }

    fun rawTextEscapedForLike() = escapeLike(rawTextOnly())
}
