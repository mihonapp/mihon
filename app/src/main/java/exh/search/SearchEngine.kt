package exh.search

import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.Tag
import ru.lanwen.verbalregex.VerbalExpression
import java.util.*

class SearchEngine {
    //TODO Namespace alias
    fun matches(metadata: ExGalleryMetadata, query: List<QueryComponent>): Boolean {

        fun matchTagList(tags: List<Tag>,
                         component: Text,
                         builder: VerbalExpression.Builder,
                         built: VerbalExpression): Boolean {
            //Match tags
            val tagMatcher = if(!component.exact)
                builder.anything().build()
            else
                built
            //Match beginning of tag
            if (tags.find {
                tagMatcher.testExact(it.name)
            } != null) {
                if(component.excluded) return false
            } else {
                //No tag matched for this component
                return false
            }
            return true
        }

        for(component in query) {
            if(component is Text) {
                val builder = component.asRegex()
                val built = builder.build()
                //Match title
                if (built.test(metadata.title?.toLowerCase())
                        || built.test(metadata.altTitle?.toLowerCase())) {
                    continue
                }
                //Match tags
                if(!matchTagList(metadata.tags.entries.flatMap(MutableMap.MutableEntry<String, ArrayList<Tag>>::value),
                        component,
                        builder,
                        built))
                    return false
            } else if(component is Namespace) {
                //Match namespace
                val ns = metadata.tags.entries.filter {
                    it.key == component.namespace.rawTextOnly()
                }.flatMap { it.value }
                //Match tags
                val builder = component.tag!!.asRegex()
                val built = builder.build()
                if(!matchTagList(ns, component.tag!!, builder, built))
                    return false
            }
        }
        return true
    }

    fun parseQuery(query: String): List<QueryComponent> {
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
            if (queuedText.isNotEmpty()) {
                val component = namespace?.apply {
                    tag = flushToText()
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
                namespace = Namespace(flushToText(), null)
            } else if(char == ' ' && !inQuotes) {
                flushAll()
            } else {
                queuedRawText.append(char)
            }
        }
        flushAll()

        return res
    }
}
