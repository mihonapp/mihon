package exh.search

import exh.anyChar
import ru.lanwen.verbalregex.VerbalExpression

class Text: QueryComponent() {
    val components = mutableListOf<TextComponent>()

    private var regex: VerbalExpression? = null
    private var lenientRegex: VerbalExpression? = null
    private var rawText: String? = null

    fun asRegex(): VerbalExpression {
        if(regex == null) {
            regex = baseBuilder().build()
        }
        return regex!!
    }

    fun asLenientRegex(): VerbalExpression {
        if(lenientRegex == null) {
            lenientRegex = baseBuilder().anything().build()
        }
        return lenientRegex!!
    }

    fun baseBuilder(): VerbalExpression.Builder {
        val builder = VerbalExpression.regex()
        for(component in components) {
            when(component) {
                is StringTextComponent -> builder.then(component.value)
                is SingleWildcard -> builder.anyChar()
                is MultiWildcard -> builder.anything()
            }
        }
        return builder
    }

    fun rawTextOnly() = if(rawText != null)
        rawText!!
    else {
        rawText = components
                .filter { it is StringTextComponent }
                .joinToString(separator = "", transform = {
                    (it as StringTextComponent).value
                })
        rawText!!
    }
}
