package exh.search

import exh.anyChar
import ru.lanwen.verbalregex.VerbalExpression

class Text: QueryComponent() {
    val components = mutableListOf<TextComponent>()

    fun asRegex(): VerbalExpression.Builder {
        val builder = VerbalExpression.regex()
        for(component in components) {
            when(component) {
                is StringTextComponent -> builder.then(component.value)
                is SingleWildcard -> builder.anyChar()
                is MultiWildcard -> builder.zeroOrMore()
            }
        }
        return builder
    }

    fun rawTextOnly() = components
            .filter { it is StringTextComponent }
            .joinToString(separator = "")
}
