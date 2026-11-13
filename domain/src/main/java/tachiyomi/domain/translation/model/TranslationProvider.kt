package tachiyomi.domain.translation.model

enum class TranslationProvider {
    GOOGLE,
    DEEPL,
    LIBRE_TRANSLATE,
    OPENAI,
    GEMINI,
    CLAUDE,
    ;

    /** Chat models take a key of their own and answer a whole page at once. */
    val isAi: Boolean
        get() = this == OPENAI || this == GEMINI || this == CLAUDE
}
