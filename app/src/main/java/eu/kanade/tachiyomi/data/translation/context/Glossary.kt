package eu.kanade.tachiyomi.data.translation.context

data class GlossaryEntry(val source: String, val target: String)

/**
 * Per manga glossary: names, terms and honorifics that must be translated the same way on every page.
 *
 * Stored and edited as plain text, one entry per line: `source = translation`. Blank lines and lines starting with
 * `#` are ignored. Pure Kotlin so it can be unit tested on the JVM.
 */
object Glossary {

    const val MAX_ENTRIES = 200
    const val MAX_TERM_LENGTH = 80

    fun parse(text: String): List<GlossaryEntry> {
        val seen = HashSet<String>()
        val entries = ArrayList<GlossaryEntry>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val separator = trimmed.indexOf('=')
            if (separator <= 0) continue

            val source = trimmed.substring(0, separator).trim().take(MAX_TERM_LENGTH)
            val target = trimmed.substring(separator + 1).trim().take(MAX_TERM_LENGTH)
            if (source.isEmpty() || target.isEmpty()) continue
            if (!seen.add(source.lowercase())) continue

            entries += GlossaryEntry(source, target)
            if (entries.size >= MAX_ENTRIES) break
        }
        return entries
    }

    /** Normalised text form of [entries], what the edit dialog shows. */
    fun format(entries: List<GlossaryEntry>): String = entries.joinToString("\n") { "${it.source} = ${it.target}" }
}

/** Builds the extra prompt text that carries the glossary and the previous page into a request. */
object PromptContext {

    const val MAX_PREVIOUS_CHARS = 600

    /**
     * @param previousTexts translations of the page before this one, in reading order.
     * @return text to append to the prompt, or an empty string when there is nothing to add.
     */
    fun build(glossary: List<GlossaryEntry>, previousTexts: List<String> = emptyList()): String {
        val parts = ArrayList<String>()

        if (glossary.isNotEmpty()) {
            parts += buildString {
                append("Glossary. Always translate these terms exactly like this:")
                glossary.forEach { append("\n- ").append(it.source).append(" => ").append(it.target) }
            }
        }

        val previous = previousTexts.map(::oneLine).filter { it.isNotEmpty() }.joinToString(" | ")
        if (previous.isNotEmpty()) {
            val clipped = if (previous.length > MAX_PREVIOUS_CHARS) {
                "…" + previous.takeLast(MAX_PREVIOUS_CHARS)
            } else {
                previous
            }
            parts += "Context only, the translated text of the previous page (do not repeat it): $clipped"
        }

        return parts.joinToString("\n\n")
    }

    private fun oneLine(text: String) = text.replace(Regex("\\s+"), " ").trim()
}
