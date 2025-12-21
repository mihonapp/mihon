package tachiyomi.domain.translation.model

/**
 * Represents a translated chapter stored in the database.
 */
data class TranslatedChapter(
    val id: Long = 0,
    val chapterId: Long,
    val mangaId: Long,
    val targetLanguage: String,
    val engineId: String,
    val translatedContent: String,
    val dateTranslated: Long = System.currentTimeMillis(),
    val isCached: Boolean = true,
)

/**
 * Represents a translation task in the queue.
 */
data class TranslationTask(
    val id: Long = 0,
    val chapterId: Long,
    val mangaId: Long,
    val sourceLanguage: String,
    val targetLanguage: String,
    val engineId: Long,
    val priority: Int = 0, // Higher = more priority (manually read chapters get higher priority)
    val status: TranslationStatus = TranslationStatus.QUEUED,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Status of a translation task.
 */
enum class TranslationStatus {
    QUEUED,
    DOWNLOADING,
    TRANSLATING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/**
 * Progress information for translation operations.
 */
data class TranslationProgress(
    val totalChapters: Int,
    val completedChapters: Int,
    val currentChapterName: String?,
    val currentChapterProgress: Float, // 0.0 to 1.0
    val isRunning: Boolean,
    val isPaused: Boolean,
) {
    val overallProgress: Float
        get() = if (totalChapters > 0) {
            (completedChapters + currentChapterProgress) / totalChapters
        } else {
            0f
        }
}

/**
 * Summary of a translation for display in chapter list.
 */
data class TranslationInfo(
    val chapterId: Long,
    val targetLanguage: String,
    val engineId: String,
    val dateTranslated: Long,
)

/**
 * Available languages for a manga's translations.
 */
data class TranslatedLanguages(
    val mangaId: Long,
    val languages: List<String>,
)
