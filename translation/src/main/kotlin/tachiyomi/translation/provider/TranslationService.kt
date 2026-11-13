package tachiyomi.translation.provider

/**
 * Runs a page's entries through a provider: batches what the provider can take, caches by text,
 * and falls back to one-by-one requests when a batch comes back the wrong shape.
 */
class TranslationService(
    private val provider: TranslationProvider,
    private val useContext: Boolean = true,
) {
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_CACHE
    }

    suspend fun translate(
        texts: List<String>,
        from: String?,
        to: String,
        context: List<String> = emptyList(),
    ): List<TranslationOutcome> {
        if (texts.isEmpty()) return emptyList()

        val results = arrayOfNulls<TranslationOutcome>(texts.size)
        val pending = mutableListOf<Int>()

        texts.forEachIndexed { index, text ->
            val cached = cache[cacheKey(from, to, text)]
            if (cached != null) {
                results[index] = TranslationOutcome.Success(listOf(cached), 0L)
            } else {
                pending += index
            }
        }

        if (pending.isEmpty()) return results.map { it!! }

        val maxBatch = provider.capabilities.maxBatch.coerceAtLeast(1)
        pending.chunked(maxBatch).forEach { chunk ->
            val batchTexts = chunk.map { texts[it] }
            val outcome = provider.translate(
                TranslationRequest(
                    texts = batchTexts,
                    from = from,
                    to = to,
                    context = if (useContext && provider.capabilities.supportsContext) context else emptyList(),
                ),
            )

            when {
                outcome is TranslationOutcome.Success && outcome.texts.size == chunk.size -> {
                    chunk.forEachIndexed { i, index ->
                        val translated = outcome.texts[i]
                        cache[cacheKey(from, to, texts[index])] = translated
                        results[index] = TranslationOutcome.Success(listOf(translated), outcome.elapsedMillis)
                    }
                }

                chunk.size > 1 -> {
                    // Wrong shape or hard failure on a batch: retry item by item so one bad line
                    // does not lose the whole bubble group.
                    chunk.forEach { index ->
                        results[index] = translateSingle(texts[index], from, to, context)
                    }
                }

                else -> {
                    results[chunk.first()] = outcome
                }
            }
        }

        return results.map { it ?: TranslationOutcome.Failure(ErrorKind.BAD_RESPONSE) }
    }

    fun clear() = cache.clear()

    private suspend fun translateSingle(
        text: String,
        from: String?,
        to: String,
        context: List<String>,
    ): TranslationOutcome {
        val outcome = provider.translate(
            TranslationRequest(
                texts = listOf(text),
                from = from,
                to = to,
                context = if (useContext && provider.capabilities.supportsContext) context else emptyList(),
            ),
        )
        if (outcome is TranslationOutcome.Success && outcome.texts.isNotEmpty()) {
            cache[cacheKey(from, to, text)] = outcome.texts.first()
        }
        return outcome
    }

    private fun cacheKey(from: String?, to: String, text: String): String =
        "${from.orEmpty()}|$to|${text.length}|${text.hashCode()}|$text"

    companion object {
        private const val MAX_CACHE = 200
    }
}

/** Builds the context list handed to LLM providers from the entries that precede [index]. */
fun buildContext(sources: List<String>, index: Int, limit: Int = 4): List<String> =
    sources.subList(0, index.coerceIn(0, sources.size))
        .filter { it.isNotBlank() }
        .takeLast(limit)
