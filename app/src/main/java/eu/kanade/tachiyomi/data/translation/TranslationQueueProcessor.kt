package eu.kanade.tachiyomi.data.translation

import android.app.Application
import android.graphics.BitmapFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.data.Translation_jobs
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.time.TimeSource

class TranslationQueueProcessor(
    private val context: Application = Injekt.get(),
    private val repository: TranslationRepository = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
    private val gemini: GeminiTranslationClient = Injekt.get(),
    private val ocrClient: LocalOcrClient = Injekt.get(),
    private val imageResolver: TranslationImageResolver = Injekt.get(),
    private val notifier: TranslationNotifier = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    suspend fun processPending(): TranslationProcessResult = coroutineScope {
        val pendingJobs = repository.getPendingJobs()
        if (pendingJobs.isEmpty()) {
            return@coroutineScope TranslationProcessResult.Idle
        }

        val concurrency = preferences.concurrency.get().coerceIn(1, 4)
        val groups = TranslationPendingJobBatcher.groupPendingJobs(
            jobs = pendingJobs,
            maxImagesPerBatch = preferences.normalizedMaxImagesPerBatch(),
        )
        var retryLater = false
        groups.chunked(concurrency).forEach { batch ->
            val results = batch.map { group -> async { processJobGroup(group) } }.awaitAll()
            when {
                TranslationProcessResult.Paused in results -> return@coroutineScope TranslationProcessResult.Paused
                TranslationProcessResult.RetryLater in results -> retryLater = true
            }
        }
        if (retryLater) TranslationProcessResult.RetryLater else TranslationProcessResult.Completed
    }

    private suspend fun processJobGroup(group: TranslationBatchJobGroup): TranslationProcessResult {
        return if (group.jobs.size > 1 && group.jobs.all { it.scope == TranslationScope.Image.value }) {
            processImageBatch(group.jobs)
        } else {
            processJob(group.first)
        }
    }

    private suspend fun processJob(job: Translation_jobs): TranslationProcessResult {
        val runningJob = startJob(job)
        return try {
            when (job.scope) {
                TranslationScope.Image.value -> {
                    val chapterId = requireNotNull(job.chapter_id) { "Image job missing chapter id" }
                    val pageIndex = requireNotNull(job.page_index) { "Image job missing page index" }.toInt()
                    processPage(runningJob, chapterId, pageIndex, current = 0, total = 1)
                    repository.updateJobProgress(runningJob, current = 1, total = 1)
                    notifier.showJobProgress(runningJob, current = 1, total = 1, status = TranslationJobStatus.Running)
                }
                TranslationScope.Chapter.value -> {
                    val chapterId = requireNotNull(job.chapter_id) { "Chapter job missing chapter id" }
                    val pageCount = imageResolver.getPageCount(job.manga_id, chapterId)
                    repository.updateJobProgress(runningJob, current = 0, total = pageCount.toLong())
                    for (pageIndex in 0 until pageCount) {
                        processPage(runningJob, chapterId, pageIndex, current = pageIndex.toLong(), total = pageCount.toLong())
                        repository.updateJobProgress(runningJob, current = pageIndex + 1L, total = pageCount.toLong())
                        notifier.showJobProgress(
                            runningJob,
                            current = pageIndex + 1L,
                            total = pageCount.toLong(),
                            status = TranslationJobStatus.Running,
                        )
                    }
                }
                else -> error("Unsupported translation scope: ${job.scope}")
            }
            completeJob(runningJob)
            TranslationProcessResult.Completed
        } catch (e: Throwable) {
            handleFailure(runningJob, e, runningJob.attempts)
        }
    }

    private suspend fun startJob(job: Translation_jobs): Translation_jobs {
        val attempt = job.attempts + 1
        val runningJob = job.copy(attempts = attempt, status = TranslationJobStatus.Running.value)
        repository.updateJobStatus(job, TranslationJobStatus.Running, attempts = attempt)
        notifier.showJobProgress(runningJob, current = 0, total = runningJob.progress_total, status = TranslationJobStatus.Running)
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Info,
            tag = "queue",
            message = "Started ${job.scope} translation",
            details = TranslationLogDetailsFormatter.queueState(
                action = "start_job",
                jobId = job._id,
                previousStatus = job.status,
                nextStatus = TranslationJobStatus.Running.value,
                extra = mapOf(
                    "model" to job.model,
                    "pipeline" to job.pipeline,
                    "mode" to job.mode,
                    "attempt" to attempt,
                    "target_language" to job.target_language.ifBlank { "app language" },
                    "source_language" to job.source_language,
                ),
            ),
        )
        return runningJob
    }

    private suspend fun completeJob(job: Translation_jobs) {
        repository.updateJobStatus(job, TranslationJobStatus.Completed, attempts = job.attempts)
        notifier.showJobProgress(job, job.progress_total, job.progress_total, TranslationJobStatus.Completed)
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Info,
            tag = "queue",
            message = "Completed ${job.scope} translation",
        )
    }

    private suspend fun processImageBatch(jobs: List<Translation_jobs>): TranslationProcessResult {
        val runningJobs = jobs.map { startJob(it) }
        val prepared = mutableListOf<PreparedTranslationPage>()
        val earlyResults = mutableListOf<TranslationProcessResult>()

        runningJobs.forEachIndexed { index, job ->
            try {
                val chapterId = requireNotNull(job.chapter_id) { "Image job missing chapter id" }
                val pageIndex = requireNotNull(job.page_index) { "Image job missing page index" }.toInt()
                val targetLanguage = job.target_language.ifBlank { TranslationLanguages.defaultTargetLanguage() }
                if (!job.overwrite && repository.getPage(chapterId, pageIndex.toLong(), targetLanguage) != null) {
                    repository.insertLog(
                        jobId = job._id,
                        pageId = null,
                        level = TranslationLogLevel.Info,
                        tag = "queue",
                        message = "Skipped existing overlay",
                        details = "chapter=$chapterId, page=$pageIndex, batch=${index + 1}/${runningJobs.size}",
                    )
                    repository.updateJobProgress(job, current = 1, total = 1)
                    completeJob(job.copy(progress_current = 1, progress_total = 1))
                    return@forEachIndexed
                }

                val image = imageResolver.resolvePage(job.manga_id, chapterId, pageIndex)
                val generationConfig = preferences.toGenerationConfig(job.model)
                repository.insertLog(
                    jobId = job._id,
                    pageId = null,
                    level = TranslationLogLevel.Debug,
                    tag = "request",
                    message = "Prepared batch page translation",
                    details = buildString {
                        appendLine("batch=${index + 1}/${runningJobs.size}")
                        appendLine("chapter=$chapterId")
                        appendLine("page=$pageIndex")
                        appendLine("mime=${image.mimeType}")
                        appendLine("size=${image.width}x${image.height}")
                        appendLine("config=${generationConfig.toGeminiJson(json)}")
                    }.takeIf { preferences.rawDebugLogging.get() },
                )
                prepared += PreparedTranslationPage(
                    job = job,
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    image = image,
                    generationConfig = generationConfig,
                    targetLanguage = targetLanguage,
                )
            } catch (e: Throwable) {
                earlyResults += handleFailure(job, e, job.attempts)
            }
        }

        if (prepared.isEmpty()) {
            return mergeResults(earlyResults)
        }

        val translated = try {
            translatePreparedBatch(prepared)
        } catch (e: Throwable) {
            val failed = prepared.map { page -> handleFailure(page.job, e, page.job.attempts) }
            return mergeResults(earlyResults + failed)
        }
        val batchResults = mutableListOf<TranslationProcessResult>()
        prepared.forEach { page ->
            val overlay = translated[page.job._id]
            if (overlay == null) {
                batchResults += fallbackSinglePage(page, "batch_missing_page")
                return@forEach
            }
            batchResults += savePreparedOverlay(page, overlay)
        }

        return mergeResults(earlyResults + batchResults)
    }

    private suspend fun translatePreparedBatch(
        pages: List<PreparedTranslationPage>,
    ): Map<Long, TranslationOverlayResult> {
        val first = pages.first()
        val batchResult = try {
            if (first.job.pipeline == "local_ocr_gemini") {
                translatePreparedOcrBatch(pages)
            } else {
                gemini.translatePageImages(
                    apiKey = preferences.geminiApiKey.get(),
                    model = first.job.model,
                    pages = pages.map { page ->
                        TranslationBatchImageInput(
                            pageIndex = page.pageIndex,
                            imageBytes = page.image.bytes,
                            mimeType = page.image.mimeType,
                            width = page.image.width,
                            height = page.image.height,
                        )
                    },
                    targetLanguage = first.targetLanguage,
                    sourceLanguage = TranslationLanguages.sourcePromptLabel(first.job.source_language),
                    generationConfig = first.generationConfig,
                    extraInstructions = preferences.globalInstructions.get(),
                    jobId = first.job._id,
                )
            }
        } catch (e: Throwable) {
            if (e is GeminiApiException) {
                throw e
            }
            repository.insertLog(
                jobId = first.job._id,
                pageId = null,
                level = TranslationLogLevel.Warning,
                tag = "api",
                message = "Batch translation parse failed; falling back to single pages",
                details = TranslationLogDetailsFormatter.queueState(
                    action = "batch_fallback_all",
                    jobId = first.job._id,
                    previousStatus = TranslationJobStatus.Running.value,
                    nextStatus = TranslationJobStatus.Running.value,
                    reason = e.message ?: e::class.simpleName.orEmpty(),
                    extra = mapOf(
                        "pages" to pages.joinToString { it.pageIndex.toString() },
                        "exception_class" to (e::class.qualifiedName ?: e::class.simpleName.orEmpty()),
                        "stack_trace" to e.stackTraceToString(),
                    ),
                ),
            )
            return emptyMap()
        }

        val duplicateIndexes = batchResult
            .groupBy { it.pageIndex }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateIndexes.isNotEmpty()) {
            repository.insertLog(
                jobId = first.job._id,
                pageId = null,
                level = TranslationLogLevel.Warning,
                tag = "api",
                message = "Batch translation returned duplicate pages",
                details = "duplicate_page_indexes=${duplicateIndexes.joinToString()}",
            )
        }

        val resultsByPage = batchResult.associateBy { it.pageIndex }
        val missing = pages.map { it.pageIndex }.filter { it !in resultsByPage.keys }
        if (missing.isNotEmpty()) {
            repository.insertLog(
                jobId = first.job._id,
                pageId = null,
                level = TranslationLogLevel.Warning,
                tag = "api",
                message = "Batch translation missing pages",
                details = "missing_page_indexes=${missing.joinToString()}",
            )
        }

        return pages.mapNotNull { page ->
            resultsByPage[page.pageIndex]?.let { page.job._id to it.overlay }
        }.toMap()
    }

    private suspend fun translatePreparedOcrBatch(
        pages: List<PreparedTranslationPage>,
    ): List<TranslationBatchOverlayResult> {
        val immediateResults = mutableListOf<TranslationBatchOverlayResult>()
        val ocrInputs = mutableListOf<TranslationBatchOcrInput>()
        pages.forEach { page ->
            val report = recognizeOcr(page.job, page.image)
            val blocks = report.blocks.filter { TranslationOverlaySanitizer.isRelevantText(it.text) }
            repository.insertLog(
                jobId = page.job._id,
                pageId = null,
                level = TranslationLogLevel.Debug,
                tag = "ocr",
                message = "Local OCR completed",
                details = ocrDetails(report, blocks, page.pageIndex),
            )
            if (blocks.isEmpty()) {
                immediateResults += TranslationBatchOverlayResult(
                    pageIndex = page.pageIndex,
                    overlay = TranslationOverlayResult(
                        sourceLanguage = TranslationLanguages.sourcePromptLabel(page.job.source_language) ?: "auto",
                        targetLanguage = page.targetLanguage,
                        boxes = emptyList(),
                    ),
                )
            } else {
                ocrInputs += TranslationBatchOcrInput(pageIndex = page.pageIndex, blocks = blocks)
            }
        }

        if (ocrInputs.isEmpty()) {
            return immediateResults
        }

        return immediateResults + gemini.translateOcrBlockBatch(
            apiKey = preferences.geminiApiKey.get(),
            model = pages.first().job.model,
            pages = ocrInputs,
            targetLanguage = pages.first().targetLanguage,
            sourceLanguage = TranslationLanguages.sourcePromptLabel(pages.first().job.source_language),
            generationConfig = pages.first().generationConfig,
            extraInstructions = preferences.globalInstructions.get(),
            jobId = pages.first().job._id,
        )
    }

    private suspend fun fallbackSinglePage(
        page: PreparedTranslationPage,
        reason: String,
    ): TranslationProcessResult {
        repository.insertLog(
            jobId = page.job._id,
            pageId = null,
            level = TranslationLogLevel.Warning,
            tag = "queue",
            message = "Retrying page outside batch",
            details = TranslationLogDetailsFormatter.queueState(
                action = reason,
                jobId = page.job._id,
                previousStatus = TranslationJobStatus.Running.value,
                nextStatus = TranslationJobStatus.Running.value,
                extra = mapOf(
                    "chapter" to page.chapterId,
                    "page" to page.pageIndex,
                ),
            ),
        )
        return try {
            val overlay = if (page.job.pipeline == "local_ocr_gemini") {
                translateWithLocalOcr(page.job, page.image, page.targetLanguage, page.generationConfig)
            } else {
                gemini.translatePageImage(
                    apiKey = preferences.geminiApiKey.get(),
                    model = page.job.model,
                    imageBytes = page.image.bytes,
                    mimeType = page.image.mimeType,
                    targetLanguage = page.targetLanguage,
                    sourceLanguage = TranslationLanguages.sourcePromptLabel(page.job.source_language),
                    generationConfig = page.generationConfig,
                    extraInstructions = preferences.globalInstructions.get(),
                    jobId = page.job._id,
                )
            }
            savePreparedOverlay(page, overlay)
        } catch (e: Throwable) {
            handleFailure(page.job, e, page.job.attempts)
        }
    }

    private suspend fun savePreparedOverlay(
        page: PreparedTranslationPage,
        overlay: TranslationOverlayResult,
    ): TranslationProcessResult {
        return try {
            saveOverlayForPage(
                job = page.job,
                chapterId = page.chapterId,
                pageIndex = page.pageIndex,
                image = page.image,
                overlay = overlay,
                targetLanguage = page.targetLanguage,
                elapsedMs = null,
            )
            repository.updateJobProgress(page.job, current = 1, total = 1)
            notifier.showJobProgress(page.job, current = 1, total = 1, status = TranslationJobStatus.Running)
            completeJob(page.job.copy(progress_current = 1, progress_total = 1))
            TranslationProcessResult.Completed
        } catch (e: Throwable) {
            handleFailure(page.job, e, page.job.attempts)
        }
    }

    private fun mergeResults(results: List<TranslationProcessResult>): TranslationProcessResult {
        return when {
            TranslationProcessResult.Paused in results -> TranslationProcessResult.Paused
            TranslationProcessResult.RetryLater in results -> TranslationProcessResult.RetryLater
            else -> TranslationProcessResult.Completed
        }
    }

    private suspend fun processPage(
        job: Translation_jobs,
        chapterId: Long,
        pageIndex: Int,
        current: Long,
        total: Long,
    ) {
        val targetLanguage = job.target_language.ifBlank { TranslationLanguages.defaultTargetLanguage() }
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
        val generationConfig = preferences.toGenerationConfig(job.model)
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
            "local_ocr_gemini" -> translateWithLocalOcr(job, image, targetLanguage, generationConfig)
            else -> gemini.translatePageImage(
                apiKey = preferences.geminiApiKey.get(),
                model = job.model,
                imageBytes = image.bytes,
                mimeType = image.mimeType,
                targetLanguage = targetLanguage,
                sourceLanguage = TranslationLanguages.sourcePromptLabel(job.source_language),
                generationConfig = generationConfig,
                extraInstructions = preferences.globalInstructions.get(),
                jobId = job._id,
            )
        }

        saveOverlayForPage(
            job = job,
            chapterId = chapterId,
            pageIndex = pageIndex,
            image = image,
            overlay = overlay,
            targetLanguage = targetLanguage,
            elapsedMs = mark.elapsedNow().inWholeMilliseconds,
        )
    }

    private suspend fun saveOverlayForPage(
        job: Translation_jobs,
        chapterId: Long,
        pageIndex: Int,
        image: TranslationPageImage,
        overlay: TranslationOverlayResult,
        targetLanguage: String,
        elapsedMs: Long?,
    ) {
        val inpaintUri = if (job.wantsInpaint()) {
            generateInpaint(job, pageIndex, image, overlay, targetLanguage)
        } else {
            null
        }

        val sanitizedOverlay = TranslationOverlaySanitizer.sanitize(overlay)

        val savedPage = repository.saveOverlay(
            mangaId = job.manga_id,
            chapterId = chapterId,
            pageIndex = pageIndex.toLong(),
            sourceImageKey = image.sourceImageKey,
            model = job.model,
            targetLanguage = targetLanguage,
            sourceLanguage = TranslationLanguages.sourcePromptLabel(job.source_language),
            pipeline = job.pipeline,
            imageWidth = image.width,
            imageHeight = image.height,
            inpaintImageUri = inpaintUri,
            overlay = sanitizedOverlay,
        )

        repository.insertLog(
            jobId = job._id,
            pageId = savedPage._id,
            level = TranslationLogLevel.Info,
            tag = "page",
            message = "Saved translation overlay",
            details = buildString {
                appendLine("chapter=$chapterId, page=$pageIndex, boxes=${sanitizedOverlay.boxes.size}")
                appendLine("dropped_boxes=${overlay.boxes.size - sanitizedOverlay.boxes.size}")
                appendLine("elapsed_ms=${elapsedMs ?: "-"}")
                appendLine("source_language=${sanitizedOverlay.sourceLanguage ?: TranslationLanguages.sourcePromptLabel(job.source_language) ?: "auto"}")
                appendLine("target_language=${sanitizedOverlay.targetLanguage ?: targetLanguage}")
                sanitizedOverlay.boxes.forEachIndexed { index, box ->
                    appendLine("${index + 1}. ${box.originalText} => ${box.translatedText}")
                }
            },
        )
    }

    private suspend fun translateWithLocalOcr(
        job: Translation_jobs,
        image: TranslationPageImage,
        targetLanguage: String,
        generationConfig: TranslationGenerationConfig,
    ): TranslationOverlayResult {
        val report = recognizeOcr(job, image)
        val blocks = report.blocks.filter { TranslationOverlaySanitizer.isRelevantText(it.text) }
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Debug,
            tag = "ocr",
            message = "Local OCR completed",
            details = ocrDetails(report, blocks, pageIndex = job.page_index?.toInt()),
        )
        if (blocks.isEmpty()) {
            repository.insertLog(
                jobId = job._id,
                pageId = null,
                level = TranslationLogLevel.Info,
                tag = "ocr",
                message = "No translatable OCR text",
                details = "Saved empty overlay for page because OCR produced no relevant text after filtering.",
            )
            return TranslationOverlayResult(
                sourceLanguage = TranslationLanguages.sourcePromptLabel(job.source_language) ?: "auto",
                targetLanguage = targetLanguage,
                boxes = emptyList(),
            )
        }
        return gemini.translateOcrBlocks(
            apiKey = preferences.geminiApiKey.get(),
            model = job.model,
            blocks = blocks,
            targetLanguage = targetLanguage,
            sourceLanguage = TranslationLanguages.sourcePromptLabel(job.source_language),
            generationConfig = generationConfig,
            extraInstructions = preferences.globalInstructions.get(),
            jobId = job._id,
        )
    }

    private suspend fun recognizeOcr(
        job: Translation_jobs,
        image: TranslationPageImage,
    ): OcrRecognitionReport {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            ?: error("Unable to decode page image for OCR")
        return try {
            ocrClient.recognizeDetailed(
                bitmap = bitmap,
                script = OcrScript.fromPreference(preferences.ocrScript.get()),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun ocrDetails(
        report: OcrRecognitionReport,
        blocks: List<OcrTextBlock>,
        pageIndex: Int?,
    ): String {
        return buildString {
            appendLine("page=${pageIndex ?: "-"}")
            appendLine("scripts:")
            report.scriptResults.forEach { result ->
                appendLine("${result.script}: success=${result.success}, blocks=${result.blocks}, error=${result.error ?: "-"}")
            }
            appendLine("raw_blocks=${report.blocks.size}")
            appendLine("filtered_blocks=${blocks.size}")
            blocks.forEach { block -> appendLine("${block.id}: ${block.text}") }
        }
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
                jobId = job._id,
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
            notifier.showJobProgress(job, job.progress_current, job.progress_total, status, message)
            repository.insertLog(
                job._id,
                null,
                TranslationLogLevel.Error,
                "queue",
                "Paused translation queue",
                TranslationLogDetailsFormatter.queueState(
                    action = "pause_api_error",
                    jobId = job._id,
                    previousStatus = job.status,
                    nextStatus = status.value,
                    reason = message,
                    extra = errorDetails(error) + mapOf("attempt" to attempt, "http_code" to (error as? GeminiApiException)?.code),
                ),
            )
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
        notifier.showJobProgress(
            job = job,
            current = job.progress_current,
            total = job.progress_total,
            status = if (canRetry) TranslationJobStatus.Retrying else TranslationJobStatus.Failed,
            message = message,
        )
        repository.insertLog(
            jobId = job._id,
            pageId = null,
            level = TranslationLogLevel.Error,
            tag = "queue",
            message = if (canRetry) "Translation failed; retry scheduled" else "Translation failed",
            details = TranslationLogDetailsFormatter.queueState(
                action = if (canRetry) "retry_scheduled" else "fail_job",
                jobId = job._id,
                previousStatus = job.status,
                nextStatus = if (canRetry) TranslationJobStatus.Retrying.value else TranslationJobStatus.Failed.value,
                reason = message,
                extra = errorDetails(error) + mapOf(
                    "attempt" to attempt,
                    "transient" to transient,
                    "http_code" to (error as? GeminiApiException)?.code,
                ),
            ),
        )
        return if (canRetry) TranslationProcessResult.RetryLater else TranslationProcessResult.Completed
    }

    private fun TranslationPreferences.toGenerationConfig(model: String): TranslationGenerationConfig {
        val cachedModels = TranslationModelLimits.decodeModels(cachedModelsJson.get(), json)
        return TranslationGenerationConfig(
            temperature = temperature.get(),
            topP = topP.get(),
            topK = topK.get(),
            maxOutputTokens = TranslationModelLimits.maxOutputTokensFor(
                requested = max(1, maxOutputTokens.get()),
                selectedModel = model,
                cachedModels = cachedModels,
            ),
            thinkingLevel = thinkingLevel.get(),
            rawJsonOverride = rawJsonOverride.get(),
        )
    }

    private fun Translation_jobs.wantsInpaint(): Boolean {
        return mode == TranslationMode.Inpaint.value || mode == TranslationMode.OverlayAndInpaint.value
    }

    private fun errorDetails(error: Throwable): Map<String, Any?> {
        return mapOf(
            "exception_class" to (error::class.qualifiedName ?: error::class.simpleName.orEmpty()),
            "exception_message" to error.message,
            "stack_trace" to error.stackTraceToString(),
        )
    }

    companion object {
        private const val MAX_ATTEMPTS = 3L
        private val TRANSIENT_HTTP_CODES = setOf(408, 409, 425, 500, 502, 503, 504)
    }
}

private data class PreparedTranslationPage(
    val job: Translation_jobs,
    val chapterId: Long,
    val pageIndex: Int,
    val image: TranslationPageImage,
    val generationConfig: TranslationGenerationConfig,
    val targetLanguage: String,
)

enum class TranslationProcessResult {
    Idle,
    Completed,
    RetryLater,
    Paused,
}
