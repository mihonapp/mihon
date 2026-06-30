package tachiyomi.domain.reader

/**
 * Splits user input into tags. Semicolons and newlines separate tags so multi-word
 * phrases (e.g. "web comic") stay intact. Trims each segment.
 */
fun parseTagInput(raw: String): List<String> =
    raw.split(';', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
