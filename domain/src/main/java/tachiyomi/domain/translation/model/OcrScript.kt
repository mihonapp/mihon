package tachiyomi.domain.translation.model

/**
 * A script the app can extract text from on-device. Each one is handled by its own ML Kit model.
 */
enum class OcrScript {
    LATIN,
    JAPANESE,
    CHINESE,
    KOREAN,
}
