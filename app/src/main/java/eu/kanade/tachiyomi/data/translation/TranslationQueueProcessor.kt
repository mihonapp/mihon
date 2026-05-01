package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.graphics.BitmapFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import tachiyomi.data.Translation_jobs
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.time.TimeSource
import tachiyomi.core.common.util.system.ImageUtil

class TranslationQueueProcessor(
    private val context: Application = Injekt.get(),
    private val repository: TranslationRepository = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
    private val gemini: GeminiTranslationClient = Injekt.get(),
    private val ocrClient: LocalOcrClient = Injekt.get(),
    private val imageResolver: TranslationImageResolver = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    suspend fun processPending(): TranslationProcessResult = coroutineScope {
        val pendingJobs = repository.getPendingJobs()
        if (pendingJobs.isEmpty()) {
            return@coroutineScope TranslationProcessResult.Idle
        }

        val concurrency = preferences.concurrency.get().coerceIn(1, 4)
        var retryLater = false
        pendingJobs.chunked(concurrency).forEach { batch ->
            val results = batch.map { job -> async { processJob(job) } }.awaitAll()
            when {
                TranslationProcessResult.Paused in results -> return@coroutineScope TranslationProcessResult.Paused
                TranslationProcessResult.RetryLater in results -> retryLater = true
            }
        }
        if (retryLater) TranslationProcessResult.RetryLater else TranslationProcessResult.Completed
    }

    private suspend fun processJob(job: Translation_jobs): TranslationProcessResult {
        val attempt = job.attempts + 1
        val runningJob = job.copy(attempts = attempt, status = TranslationJobStatus.Running.value)
        repository.updateJobStatus(job, TranslationJobStatus.Running, attempts = attempt)
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Info,
            tag = "queue",
            message = "Started ${job.scope} translation",
            details = "model=${job.model}, pipeline=${job.pipeline}, mode=${job.mode}, attempt=$attempt",
        )

        return try {
            when (job.scope) {
                TranslationScope.Image.value -> {
                    val chapterId = requireNotNull(job.chapter_id) { "Image job missing chapter id" }
                    val pageIndex = requireNotNull(job.page_index) { "Image job missing page index" }.toInt()
                    processPage(runningJob, chapterId, pageIndex, current = 0, total = 1)
                    repository.updateJobProgress(runningJob, current = 1, total = 1)
                }
                TranslationScope.Chapter.value -> {
                    val chapterId = requireNotNull(job.chapter_id) { "Chapter job missing chapter id" }
                    val pageCount = imageResolver.getPageCount(job.manga_id, chapterId)
                    repository.updateJobProgress(runningJob, current = 0, total = pageCount.toLong())
                    for (pageIndex in 0 until pageCount) {
                        processPage(runningJob, chapterId, pageIndex, current = pageIndex.toLong(), total = pageCount.toLong())
                        repository.updateJobProgress(runningJob, current = pageIndex + 1L, total = pageCount.toLong())
                    }
                }
                else -> error("Unsupported translation scope: ${job.scope}")
            }
            repository.updateJobStatus(runningJob, TranslationJobStatus.Completed, attempts = attempt)
            repository.insertLog(
                jobId = job._id,
                pageId = null,
                level = TranslationLogLevel.Info,
                tag = "queue",
                message = "Completed ${job.scope} translation",
            )
            TranslationProcessResult.Completed
        } catch (e: Throwable) {
            handleFailure(runningJob, e, attempt)
        }
    }

    private suspend fun processPage(
        job: Translation_jobs,
        chapterId: Long,
        pageIndex: Int,
        current: Long,
        total: Long,
    ) {
        val targetLanguage = job.target_language.ifBlank { Locale.getDefault().displayLanguage.ifBlank { "English" } }
        if (!job.overwrite && repository.getPage(chapterId, pageIndex.toLong(), targetLanguage) != null) {
            repository.insertLog(
                jobId = job._id,
                pageId = null,
                level = TranslationLogLevel.Info,
                tag = "queue",
                message = "Skipped existing overlay",
                details = "chapter=$chapterId, page=$pageIndex, progress=${current + 1}/$total",
            )
            return
        }

        val mark = TimeSource.Monotonic.markNow()
        val image = imageResolver.resolvePage(job.manga_id, chapterId, pageIndex)
        val generationConfig = preferences.toGenerationConfig()
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Debug,
            tag = "request",
            message = "Prepared page translation",
            details = buildString {
                append("chapter=$chapterId, page=$pageIndex, mime=${image.mimeType}, ")
                append("size=${image.width}x${image.height}, config=${generationConfig.toGeminiJson(json)}")
            }.takeIf { preferences.rawDebugLogging.get() },
        )

        val overlay = when (job.pipeline) {
            "local_ocr_gemini" -> translateWithLocalOcr(job, image, targetLanguage)
            else -> gemini.translatePageImage(
                apiKey = preferences.geminiApiKey.get(),
                model = job.model,
                imageBytes = image.bytes,
                mimeType = image.mimeType,
                targetLanguage = targetLanguage,
                sourceLanguage = job.source_language,
                generationConfig = generationConfig,
                extraInstructions = preferences.globalInstructions.get(),
            )
        }

        val inpaintUri = if (job.wantsInpaint()) {
            generateInpaint(job, pageIndex, image, overlay, targetLanguage)
        } else {
            null
        }

        val savedPage = repository.saveOverlay(
            mangaId = job.manga_id,
            chapterId = chapterId,
            pageIndex = pageIndex.toLong(),
            sourceImageKey = image.sourceImageKey,
            model = job.model,
            targetLanguage = targetLanguage,
            sourceLanguage = job.source_language,
            pipeline = job.pipeline,
            imageWidth = image.width,
            imageHeight = image.height,
            inpaintImageUri = inpaintUri,
            overlay = overlay,
        )

        repository.insertLog(
            jobId = job._id,
            pageId = savedPage._id,
            level = TranslationLogLevel.Info,
            tag = "page",
            message = "Saved translation overlay",
            details = buildString {
                appendLine("chapter=$chapterId, page=$pageIndex, boxes=${overlay.boxes.size}")
                appendLine("elapsed_ms=${mark.elapsedNow().inWholeMilliseconds}")
                appendLine("source_language=${overlay.sourceLanguage ?: job.source_language ?: "auto"}")
                appendLine("target_language=${overlay.targetLanguage ?: targetLanguage}")
                overlay.boxes.forEachIndexed { index, box ->
                    appendLine("${index + 1}. ${box.originalText} => ${box.translatedText}")
                }
            },
        )
    }

    private suspend fun translateWithLocalOcr(
        job: Translation_jobs,
        image: TranslationPageImage,
        targetLanguage: String,
    ): TranslationOverlayResult {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            ?: error("Unable to decode page image for OCR")
        val blocks = ocrClient.recognize(
            bitmap = bitmap,
            script = OcrScript.fromPreference(preferences.ocrScript.get()),
        )
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Debug,
            tag = "ocr",
            message = "Local OCR completed",
            details = buildString {
                appendLine("blocks=${blocks.size}")
                blocks.forEach { block -> appendLine("${block.id}: ${block.text}") }
            },
        )
        return gemini.translateOcrBlocks(
            apiKey = preferences.geminiApiKey.get(),
            model = job.model,
            blocks = blocks,
            targetLanguage = targetLanguage,
            sourceLanguage = job.source_language,
            generationConfig = preferences.toGenerationConfig(),
            extraInstructions = preferences.globalInstructions.get(),
        )
    }

    private suspend fun generateInpaint(
        job: Translation_jobs,
        pageIndex: Int,
        image: TranslationPageImage,
        overlay: TranslationOverlayResult,
        targetLanguage: String,
    ): String? {
        return try {
            val bytes = gemini.generateInpaintImage(
                apiKey = preferences.geminiApiKey.get(),
                model = preferences.geminiInpaintModel.get(),
                imageBytes = image.bytes,
                mimeType = image.mimeType,
                overlay = overlay,
                targetLanguage = targetLanguage,
            ) ?: return null
            val mime = ImageUtil.findImageType { ByteArrayInputStream(bytes) } ?: ImageUtil.ImageType.JPEG
            val dir = File(context.filesDir, "translations/inpaint").also { it.mkdirs() }
            val file = File(dir, "${job.chapter_id}-$pageIndex-${targetLanguage.hashCode()}.${mime.extension}")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Throwable) {
            repository.insertLog(
                jobId = job._id,
                pageId = null,
                level = TranslationLogLevel.Warning,
                tag = "inpaint",
                message = "Inpaint failed; overlay remains available",
                details = e.message,
            )
            null
        }
    }

    private suspend fun handleFailure(
        job: Translation_jobs,
        error: Throwable,
        attempt: Long,
    ): TranslationProcessResult {
        val errorBody = (error as? GeminiApiException)?.errorBody
        val message = errorBody ?: error.message ?: error::class.simpleName.orEmpty()
        val status = when ((error as? GeminiApiException)?.code) {
            401, 403 -> TranslationJobStatus.PausedAuth
            429 -> TranslationJobStatus.PausedQuota
            else -> null
        }
        if (status != null) {
            repository.updateJobStatus(job, status, errorMessage = message, attempts = attempt)
            repository.insertLog(job._id, null, TranslationLogLevel.Error, "queue", "Paused translation queue", message)
            return TranslationProcessResult.Paused
        }

        val transient = error is IOException || (error as? GeminiApiException)?.code in TRANSIENT_HTTP_CODES
        val canRetry = transient && attempt < MAX_ATTEMPTS
        repository.updateJobStatus(
            job = job,
            status = if (canRetry) TranslationJobStatus.Retrying else TranslationJobStatus.Failed,
            errorMessage = message,
            attempts = attempt,
        )
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Error,
            tag = "queue",
            message = if (canRetry) "Translation failed; retry scheduled" else "Translation failed",
            details = "attempt=$attempt, transient=$transient, error=$message",
        )
        return if (canRetry) TranslationProcessResult.RetryLater else TranslationProcessResult.Completed
    }

    private fun TranslationPreferences.toGenerationConfig(): TranslationGenerationConfig {
        return TranslationGenerationConfig(
            temperature = temperature.get(),
            topP = topP.get(),
            topK = topK.get(),
            maxOutputTokens = max(1, maxOutputTokens.get()),
            thinkingBudget = thinkingBudget.get().takeIf { it >= 0 },
            rawJsonOverride = rawJsonOverride.get(),
        )
    }

    private fun Translation_jobs.wantsInpaint(): Boolean {
        return mode == TranslationMode.Inpaint.value || mode == TranslationMode.OverlayAndInpaint.value
    }

    companion object {
        private const val MAX_ATTEMPTS = 3L
        private val TRANSIENT_HTTP_CODES = setOf(408, 409, 425, 500, 502, 503, 504)
    }
}

enum class TranslationProcessResult {
    Idle,
    Completed,
    RetryLater,
    Paused,
}
