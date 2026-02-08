package eu.kanade.tachiyomi.data.translation

import android.content.Context
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.engine.LibreTranslateEngine
import eu.kanade.tachiyomi.source.fetchNovelPageText
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.model.TranslatedChapter
import tachiyomi.domain.translation.model.TranslationProgress
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.model.TranslationStatus
import tachiyomi.domain.translation.model.TranslationTask
import tachiyomi.domain.translation.repository.TranslatedChapterRepository
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service for managing translation queue and executing translations.
 */
class TranslationService(
    private val context: Context,
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
    private val translationEngineManager: TranslationEngineManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val translatedChapterRepository: TranslatedChapterRepository = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val updateChapter: tachiyomi.domain.chapter.interactor.UpdateChapter = Injekt.get(),
) {
    // Lazy-loaded to avoid circular dependency during initialization
    private val downloadManager: DownloadManager by lazy { Injekt.get() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queue = ConcurrentLinkedQueue<TranslationTask>()

    private var translationJob: Job? = null

    private val _progressState = MutableStateFlow(
        TranslationProgress(
            totalChapters = 0,
            completedChapters = 0,
            currentChapterName = null,
            currentChapterProgress = 0f,
            isRunning = false,
            isPaused = false,
        ),
    )
    val progressState = _progressState.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    /**
     * Add a chapter to the translation queue.
     */
    fun enqueue(
        manga: Manga,
        chapter: Chapter,
        priority: Int = 0,
    ) {
        if (!translationPreferences.translationEnabled().get()) return

        val task = TranslationTask(
            chapterId = chapter.id,
            mangaId = manga.id,
            sourceLanguage = translationPreferences.sourceLanguage().get(),
            targetLanguage = translationPreferences.targetLanguage().get(),
            engineId = 0, // Will be determined at translation time
            priority = priority,
            status = TranslationStatus.QUEUED,
            retryCount = 0,
        )

        // Add to queue if not already present
        if (queue.none { it.chapterId == chapter.id }) {
            queue.add(task)
            updateProgress()
            // Auto-start queue processing if not already running
            start()
        }
    }

    /**
     * Add multiple chapters to the translation queue.
     */
    fun enqueueAll(
        manga: Manga,
        chapters: List<Chapter>,
        priority: Int = 0,
    ) {
        if (!translationPreferences.translationEnabled().get()) return

        chapters.forEach { chapter ->
            enqueue(manga, chapter, priority)
        }
    }

    /**
     * Remove a chapter from the queue.
     */
    fun dequeue(chapterId: Long) {
        queue.removeIf { it.chapterId == chapterId }
        updateProgress()
    }

    /**
     * Clear all items from the queue.
     */
    fun clearQueue() {
        queue.clear()
        updateProgress()
    }

    /**
     * Start processing the translation queue.
     */
    fun start() {
        if (translationJob?.isActive == true) return

        _isPaused.value = false

        translationJob = scope.launch {
            while (isActive && queue.isNotEmpty()) {
                if (_isPaused.value) {
                    delay(500)
                    continue
                }

                // Get highest priority task
                val task = queue.poll() ?: break

                try {
                    _progressState.update { current ->
                        current.copy(
                            isRunning = true,
                            currentChapterName = "Chapter ${task.chapterId}",
                            currentChapterProgress = 0f,
                        )
                    }

                    translateChapter(task)

                    _progressState.update { current ->
                        current.copy(
                            completedChapters = current.completedChapters + 1,
                            currentChapterProgress = 1f,
                        )
                    }

                    // Rate limiting delay
                    val delayMs = translationPreferences.rateLimitDelayMs().get()
                    if (delayMs > 0) {
                        delay(delayMs.toLong())
                    }
                } catch (e: CancellationException) {
                    // Translation was cancelled (e.g., user navigated away), don't log as error
                    logcat(LogPriority.DEBUG) { "Translation cancelled for chapter ${task.chapterId}" }
                    throw e // Re-throw to stop processing
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Translation failed for chapter ${task.chapterId}" }
                    // Re-queue failed task with lower priority if retry count is low
                    val retryCount = task.retryCount + 1
                    if (retryCount < 2) {
                        queue.add(
                            task.copy(
                                status = TranslationStatus.FAILED,
                                errorMessage = e.message,
                                retryCount = retryCount,
                            ),
                        )
                    } else {
                        logcat(LogPriority.ERROR) { "Max retries reached for chapter ${task.chapterId}, skipping" }
                    }
                }
            }

            _progressState.update { current ->
                current.copy(
                    isRunning = false,
                    currentChapterName = null,
                    currentChapterProgress = 0f,
                )
            }
        }
    }

    /**
     * Pause translation processing.
     */
    fun pause() {
        _isPaused.value = true
        _progressState.update { it.copy(isPaused = true) }
    }

    /**
     * Resume translation processing.
     */
    fun resume() {
        _isPaused.value = false
        _progressState.update { it.copy(isPaused = false) }
        if (translationJob?.isActive != true && queue.isNotEmpty()) {
            start()
        }
    }

    /**
     * Stop translation processing.
     */
    fun stop() {
        translationJob?.cancel()
        translationJob = null
        _progressState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                currentChapterName = null,
                currentChapterProgress = 0f,
            )
        }
    }

    /**
     * Check if translation service is running.
     */
    fun isRunning(): Boolean = translationJob?.isActive == true

    /**
     * Get the current queue size.
     */
    fun queueSize(): Int = queue.size

    /**
     * Translate a single chapter.
     */
    private suspend fun translateChapter(task: TranslationTask) = withContext(Dispatchers.IO) {
        val engine = translationEngineManager.getEngine()
            ?: throw IllegalStateException("No translation engine available")

        // Get chapter and manga from database
        val chapter = getChapter.await(task.chapterId)
            ?: throw IllegalStateException("Chapter ${task.chapterId} not found")
        val manga = getManga.await(task.mangaId)
            ?: throw IllegalStateException("Manga ${task.mangaId} not found")
        val source = sourceManager.get(manga.source)
            ?: throw IllegalStateException("Source ${manga.source} not found")

        logcat(LogPriority.DEBUG) { "Starting translation for chapter: ${chapter.name}" }

        // Check if already translated
        val existingTranslation = translatedChapterRepository.getTranslatedChapter(task.chapterId, task.targetLanguage)
        if (existingTranslation != null) {
            logcat(LogPriority.DEBUG) { "Chapter ${chapter.name} already translated, skipping" }
            return@withContext
        }

        // Try to get content from downloaded chapter first, fall back to fetching from source
        val allContent = getChapterContent(chapter, manga, source)

        if (allContent.isBlank()) {
            logcat(LogPriority.WARN) { "No text content found in chapter" }
            return@withContext
        }

        // Extract text from HTML
        val plainText = extractTextFromHtml(allContent)
        val paragraphs = plainText.split("\n\n").filter { it.isNotBlank() }

        logcat(LogPriority.DEBUG) { "Translating ${paragraphs.size} paragraphs for chapter ${chapter.name}" }

        // Translate the content
        val result = engine.translate(paragraphs, task.sourceLanguage, task.targetLanguage)

        when (result) {
            is TranslationResult.Success -> {
                val translatedTexts = result.translatedTexts
                if (translatedTexts.isEmpty()) throw IllegalStateException("No translation returned")

                // Wrap in HTML
                val translatedHtml = translatedTexts.joinToString("") { paragraph ->
                    "<p>${paragraph.trim().replace("\n", "<br/>")}</p>"
                }

                // Save to database
                val translatedChapter = TranslatedChapter(
                    chapterId = task.chapterId,
                    mangaId = task.mangaId,
                    targetLanguage = task.targetLanguage,
                    engineId = engine.id.toString(),
                    translatedContent = translatedHtml,
                    dateTranslated = System.currentTimeMillis(),
                    isCached = true,
                )
                translatedChapterRepository.upsertTranslation(translatedChapter)

                // Update chapter name with translated title if available
                try {
                    val nameResult = engine.translateSingle(chapter.name, task.sourceLanguage, task.targetLanguage)
                    if (nameResult is TranslationResult.Success) {
                        val translatedName = nameResult.translatedTexts.firstOrNull()
                        if (!translatedName.isNullOrBlank() && translatedName != chapter.name) {
                            val newName = "${chapter.name} [$translatedName]"
                            updateChapter.await(
                                tachiyomi.domain.chapter.model.ChapterUpdate(
                                    id = chapter.id,
                                    name = newName,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to translate chapter name" }
                }

                logcat(LogPriority.DEBUG) { "Successfully translated and saved chapter ${chapter.name}" }

                // Update progress to complete
                _progressState.update { current ->
                    current.copy(currentChapterProgress = 1f)
                }
            }
            is TranslationResult.Error -> {
                throw Exception("Translation failed: ${result.message}")
            }
        }
    }

    /**
     * Get chapter content either from downloaded files or directly from source.
     */
    private suspend fun getChapterContent(
        chapter: Chapter,
        manga: Manga,
        source: eu.kanade.tachiyomi.source.Source,
    ): String {
        // First try to get content from downloaded chapter
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        )

        if (chapterDir != null) {
            val htmlFiles = chapterDir.listFiles()?.filter {
                it.isFile && it.name?.endsWith(".html") == true
            }?.sortedBy { it.name } ?: emptyList()

            if (htmlFiles.isNotEmpty()) {
                logcat(LogPriority.DEBUG) { "Reading content from downloaded chapter" }
                val content = StringBuilder()
                htmlFiles.forEachIndexed { index, file ->
                    val fileContent = context.contentResolver.openInputStream(file.uri)?.use {
                        it.bufferedReader().readText()
                    } ?: ""
                    content.append(fileContent)
                    if (index < htmlFiles.size - 1) {
                        content.append("\n\n")
                    }

                    // Update progress
                    _progressState.update { current ->
                        current.copy(currentChapterProgress = (index + 1f) / (htmlFiles.size * 2))
                    }
                }
                return content.toString()
            }
        }

        // Fall back to fetching from source
        logcat(LogPriority.DEBUG) { "Fetching content from source for chapter: ${chapter.name}" }

        val source = sourceManager.get(manga.source)
            ?: throw IllegalStateException("Source not found for id=${manga.source}")

        if (!source.isNovelSource()) {
            throw IllegalStateException("Source ${source.name} is not a novel source")
        }

        // Create page object for the chapter
        val page = Page(0, chapter.url, chapter.url)

        // Update progress
        _progressState.update { current ->
            current.copy(currentChapterProgress = 0.3f)
        }

        // Fetch content from source
        val content = source.fetchNovelPageText(page)

        // Update progress
        _progressState.update { current ->
            current.copy(currentChapterProgress = 0.5f)
        }

        return content
    }

    private fun updateProgress() {
        _progressState.update { current ->
            current.copy(
                totalChapters = queue.size + current.completedChapters,
            )
        }
    }

    /**
     * Translate text using the configured engine.
     */
    suspend fun translateText(
        text: String,
        sourceLanguage: String = translationPreferences.sourceLanguage().get(),
        targetLanguage: String = translationPreferences.targetLanguage().get(),
    ): TranslationResult {
        val engine = translationEngineManager.getEngine()
            ?: return TranslationResult.Error("No translation engine available")

        return engine.translateSingle(text, sourceLanguage, targetLanguage)
    }

    /**
     * Translate chapter content in real-time (for reader).
     * Extracts text from HTML, translates it, and reconstructs the HTML structure.
     * Saves translations to database for future use.
     */
    suspend fun translateChapterContent(
        content: String,
        chapterId: Long? = null,
        mangaId: Long? = null,
        sourceLanguage: String? = null,
        targetLanguage: String? = null,
    ): String {
        // if (!translationPreferences.translationEnabled().get()) {
        //     return content
        // }

        val srcLang = sourceLanguage ?: translationPreferences.sourceLanguage().get()
        val tgtLang = targetLanguage ?: translationPreferences.targetLanguage().get()

        // Don't translate if source and target are the same
        if (srcLang == tgtLang) {
            return content
        }

        // Check for existing translation in database
        if (chapterId != null) {
            val existingTranslation = translatedChapterRepository.getTranslatedChapter(chapterId, tgtLang)
            if (existingTranslation != null) {
                logcat(LogPriority.DEBUG) { "Using cached translation for chapter $chapterId (lang: $tgtLang)" }
                return existingTranslation.translatedContent
            }
        }

        // Extract plain text from HTML for translation (more efficient and accurate)
        val plainText = extractTextFromHtml(content)

        // Translate the plain text
        return when (val result = translateText(plainText, srcLang, tgtLang)) {
            is TranslationResult.Success -> {
                val translatedText = result.translatedTexts.firstOrNull() ?: return content
                // Wrap translated text in proper HTML paragraphs
                val translatedHtml = wrapTextInHtml(translatedText)

                // Save translation to database
                if (chapterId != null && mangaId != null) {
                    val engine = translationEngineManager.getEngine()
                    val translatedChapter = TranslatedChapter(
                        chapterId = chapterId,
                        mangaId = mangaId,
                        targetLanguage = tgtLang,
                        engineId = engine?.id?.toString() ?: "unknown",
                        translatedContent = translatedHtml,
                        dateTranslated = System.currentTimeMillis(),
                        isCached = true,
                    )
                    try {
                        translatedChapterRepository.upsertTranslation(translatedChapter)
                        logcat(LogPriority.DEBUG) { "Saved translation for chapter $chapterId (lang: $tgtLang)" }
                    } catch (e: CancellationException) {
                        logcat(LogPriority.DEBUG) { "Translation save was cancelled for chapter $chapterId" }
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to save translation to database" }
                    }
                }

                translatedHtml
            }
            is TranslationResult.Error -> {
                logcat(LogPriority.WARN) { "Translation failed: ${result.message}" }
                content // Return original content on error
            }
        }
    }

    /**
     * Get translated content from database if available.
     */
    suspend fun getTranslatedContent(
        chapterId: Long,
        targetLanguage: String? = null,
    ): String? {
        val tgtLang = targetLanguage ?: translationPreferences.targetLanguage().get()
        return translatedChapterRepository.getTranslatedChapter(chapterId, tgtLang)?.translatedContent
    }

    /**
     * Check if a translation exists for a chapter.
     */
    suspend fun hasTranslation(
        chapterId: Long,
        targetLanguage: String? = null,
    ): Boolean {
        val tgtLang = targetLanguage ?: translationPreferences.targetLanguage().get()
        return translatedChapterRepository.hasTranslation(chapterId, tgtLang)
    }

    /**
     * Get all available translation languages for a manga.
     */
    suspend fun getTranslatedLanguages(mangaId: Long): List<String> {
        return translatedChapterRepository.getTranslatedLanguagesForManga(mangaId)
    }

    /**
     * Delete a translation.
     */
    suspend fun deleteTranslation(chapterId: Long, targetLanguage: String) {
        translatedChapterRepository.deleteTranslation(chapterId, targetLanguage)
    }

    /**
     * Get last used target language (for quick translate).
     */
    fun getLastTargetLanguage(): String {
        return translationPreferences.targetLanguage().get()
    }

    /**
     * Set target language (for language picker).
     */
    fun setTargetLanguage(language: String) {
        translationPreferences.targetLanguage().set(language)
    }

    suspend fun detectLanguage(text: String): String? {
        val engine = translationEngineManager.getSelectedEngine()

        if (engine is LibreTranslateEngine) {
            // Use a sample of text for detection to save bandwidth/time
            val sample = text.take(500)
            return engine.detectLanguage(sample)
        }
        return null
    }

    /**
     * Extract plain text from HTML content.
     * Preserves paragraph structure by converting to newlines.
     */
    private fun extractTextFromHtml(html: String): String {
        return html
            // Convert paragraph and line breaks to newlines
            .replace(Regex("</p>\\s*<p[^>]*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            // Remove all HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Decode common HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            // Clean up excessive whitespace
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Wrap plain text back into HTML paragraphs.
     */
    private fun wrapTextInHtml(text: String): String {
        return text
            .split("\n\n")
            .filter { it.isNotBlank() }
            .joinToString("") { paragraph ->
                "<p>${paragraph.trim().replace("\n", "<br/>")}</p>"
            }
    }

    companion object {
        /**
         * Priority values for translation queue.
         */
        const val PRIORITY_LOW = 0
        const val PRIORITY_NORMAL = 50
        const val PRIORITY_HIGH = 100
        const val PRIORITY_MANUAL_READ = 200 // User manually opened chapter
    }
}
