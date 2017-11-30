package exh.search

import exh.metadata.models.SearchableGalleryMetadata
import exh.metadata.models.Tag
import io.realm.Case
import io.realm.RealmQuery

class SearchEngine {

    private val queryCache = mutableMapOf<String, List<QueryComponent>>()

    fun <T : SearchableGalleryMetadata> filterResults(rQuery: RealmQuery<T>,
                                                      query: List<QueryComponent>,
                                                      titleFields: List<String>):
            RealmQuery<T> {
        var queryEmpty = true

        fun matchTagList(namespace: String?,
                         component: Text?,
                         excluded: Boolean) {
            when {
                excluded -> rQuery.not()
                queryEmpty -> queryEmpty = false
                else -> rQuery.or()
            }

            rQuery.beginGroup()
            //Match namespace if specified
            namespace?.let {
                rQuery.equalTo("${SearchableGalleryMetadata::tags.name}.${Tag::namespace.name}",
                        it,
                        Case.INSENSITIVE)
            }
            //Match tag name if specified
            component?.let {
                rQuery.beginGroup()
                val q = if (!it.exact)
                    it.asLenientTagQueries()
                else
                    listOf(it.asQuery())
                q.forEachIndexed { index, s ->
                    if(index > 0)
                        rQuery.or()

                    rQuery.like("${SearchableGalleryMetadata::tags.name}.${Tag::name.name}", s, Case.INSENSITIVE)
                }
                rQuery.endGroup()
            }
            rQuery.endGroup()
        }

        for(component in query) {
            if(component is Text) {
                if(component.excluded)
                    rQuery.not()

                rQuery.beginGroup()

                //Match title
                titleFields.forEachIndexed { index, s ->
                    queryEmpty = false
                    if(index > 0)
                        rQuery.or()

                    rQuery.like(s, component.asLenientTitleQuery(), Case.INSENSITIVE)
                }

                //Match tags
                matchTagList(null, component, false) //We already deal with exclusions here
                rQuery.endGroup()
            } else if(component is Namespace) {
                if(component.namespace == "uploader") {
                    queryEmpty = false
                    //Match uploader
                    rQuery.equalTo(SearchableGalleryMetadata::uploader.name,
                            component.tag!!.rawTextOnly(),
                            Case.INSENSITIVE)
                } else {
                    if(component.tag!!.components.size > 0) {
                        //Match namespace + tags
                        matchTagList(component.namespace, component.tag!!, component.tag!!.excluded)
                    } else {
                        //Perform namespace search
                        matchTagList(component.namespace, null, component.excluded)
                    }
                }
            }
        }
        return rQuery
    }

    fun parseQuery(query: String) = queryCache.getOrPut(query, {
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
            } else if(char == '?' || char == '_') {
                flushText()
                queuedText.add(SingleWildcard())
            } else if(char == '*' || char == '%') {
                flushText()
                queuedText.add(MultiWildcard())
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
    })
}
