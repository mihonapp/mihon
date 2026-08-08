package eu.kanade.tachiyomi.data.translation

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.GetJobForQueue
import tachiyomi.data.GetJobsForQueue
import tachiyomi.data.Translation_boxes
import tachiyomi.data.Translation_jobs
import tachiyomi.data.Translation_logs
import tachiyomi.data.Translation_pages
import tachiyomi.data.subscribeToList
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationRepository(
    private val database: Database = Injekt.get(),
) {
    fun observeJobs(): Flow<List<Translation_jobs>> {
        return database.translationsQueries.getJobs().subscribeToList()
    }

    fun observeJobsForQueue(): Flow<List<TranslationQueueItem>> {
        return database.translationsQueries.getJobsForQueue()
            .subscribeToList()
            .map { jobs -> jobs.map(GetJobsForQueue::toQueueItem) }
    }

    suspend fun getJobForQueue(id: Long): TranslationQueueItem? {
        return database.translationsQueries.getJobForQueue(id)
            .awaitAsOneOrNull()
            ?.toQueueItem()
    }

    fun observeLogs(): Flow<List<Translation_logs>> {
        return database.translationsQueries.getLogs().subscribeToList()
    }

    fun observePagesByChapter(chapterId: Long, targetLanguage: String): Flow<List<Translation_pages>> {
        return database.translationsQueries.observePagesByChapter(chapterId, targetLanguage).subscribeToList()
    }

    suspend fun getPendingJobs(): List<Translation_jobs> {
        return database.translationsQueries.getPendingJobs().awaitAsList()
    }

    suspend fun getFreshRunningJobs(
        now: Long = System.currentTimeMillis(),
        activeJobIds: Set<Long> = emptySet(),
    ): List<Translation_jobs> {
        return database.translationsQueries.getJobsByStatus(TranslationJobStatus.Running.value)
            .awaitAsList()
            .filter { job ->
                TranslationRunningJobPolicy.blocksNewClaims(job, now, activeJobIds)
            }
    }

    suspend fun requeueStaleRunningJobs(
        now: Long = System.currentTimeMillis(),
        activeJobIds: Set<Long> = emptySet(),
    ): Int {
        val staleJobs = database.translationsQueries.getJobsByStatus(TranslationJobStatus.Running.value)
            .awaitAsList()
            .filter { job ->
                TranslationRunningJobPolicy.isRecoverableStale(job, now, activeJobIds)
            }
        if (staleJobs.isEmpty()) return 0
        database.transaction {
            staleJobs.forEach { job ->
                val nextStatus = TranslationRunningJobPolicy.requeueStatusForStoppedWorker(job)
                updateJobStatus(
                    job = job,
                    status = nextStatus,
                    errorMessage = null,
                    attempts = job.attempts,
                )
                insertLog(
                    jobId = job._id,
                    pageId = null,
                    level = TranslationLogLevel.Warning,
                    tag = "queue",
                    message = "Recovered stale running translation job",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "recover_stale_running",
                        jobId = job._id,
                        previousStatus = job.status,
                        nextStatus = nextStatus.value,
                        reason = "Running job exceeded worker lease",
                        extra = mapOf(
                            "worker_kind" to TranslationRunningJobPolicy.kindForClaimToken(job).value,
                            "age_ms" to (now - job.updated_at),
                            "stale_after_ms" to TranslationRunningJobPolicy.STALE_RUNNING_MS,
                        ),
                    ),
                )
            }
        }
        return staleJobs.size
    }

    suspend fun requeueRunningJobsForStoppedWorker(
        reason: String,
        now: Long = System.currentTimeMillis(),
    ): Int {
        val runningJobs = database.translationsQueries.getJobsByStatus(TranslationJobStatus.Running.value)
            .awaitAsList()
        if (runningJobs.isEmpty()) return 0
        database.transaction {
            runningJobs.forEach { job ->
                val nextStatus = TranslationRunningJobPolicy.requeueStatusForStoppedWorker(job)
                updateJobStatus(
                    job = job,
                    status = nextStatus,
                    errorMessage = null,
                    attempts = job.attempts,
                )
                insertLog(
                    jobId = job._id,
                    pageId = null,
                    level = TranslationLogLevel.Warning,
                    tag = "queue",
                    message = "Recovered stopped translation worker job",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "recover_stopped_worker",
                        jobId = job._id,
                        previousStatus = job.status,
                        nextStatus = nextStatus.value,
                        reason = reason,
                        extra = mapOf(
                            "worker_kind" to TranslationRunningJobPolicy.kindForClaimToken(job).value,
                            "age_ms" to (now - job.updated_at),
                        ),
                    ),
                )
            }
        }
        return runningJobs.size
    }

    suspend fun getPage(
        chapterId: Long,
        pageIndex: Long,
        targetLanguage: String,
    ): Translation_pages? {
        return database.translationsQueries
            .getPage(chapterId, pageIndex, targetLanguage)
            .awaitAsOneOrNull()
    }

    suspend fun getPagesByChapter(chapterId: Long, targetLanguage: String): List<SavedTranslationPage> {
        return database.translationsQueries
            .getPagesByChapter(chapterId, targetLanguage)
            .awaitAsList()
            .map { page ->
                SavedTranslationPage(
                    page = page,
                    boxes = database.translationsQueries.getBoxesForPage(page._id).awaitAsList(),
                )
            }
    }

    suspend fun getPageRowsByChapter(chapterId: Long, targetLanguage: String): List<Translation_pages> {
        return database.translationsQueries
            .getPagesByChapter(chapterId, targetLanguage)
            .awaitAsList()
    }

    suspend fun getActiveJobsByChapter(
        mangaId: Long,
        chapterId: Long,
        pipeline: String,
        mode: TranslationMode,
        targetLanguage: String,
    ): List<Translation_jobs> {
        return database.translationsQueries
            .getActiveJobsByChapter(
                mangaId = mangaId,
                chapterId = chapterId,
                scope = TranslationScope.Image.value,
                pipeline = pipeline,
                mode = mode.value,
                targetLanguage = targetLanguage,
            )
            .awaitAsList()
    }

    suspend fun getSavedPage(
        chapterId: Long,
        pageIndex: Long,
        targetLanguage: String,
    ): SavedTranslationPage? {
        val page = getPage(chapterId, pageIndex, targetLanguage) ?: return null
        return SavedTranslationPage(
            page = page,
            boxes = database.translationsQueries.getBoxesForPage(page._id).awaitAsList(),
        )
    }

    suspend fun enqueueJob(
        mangaId: Long,
        chapterId: Long?,
        pageIndex: Long?,
        scope: TranslationScope,
        pipeline: String,
        mode: TranslationMode,
        model: String,
        targetLanguage: String,
        sourceLanguage: String?,
        overwrite: Boolean,
        progressTotal: Long = 1,
    ): TranslationEnqueueResult {
        var result: TranslationEnqueueResult? = null
        database.transaction {
            val duplicate = database.translationsQueries.getActiveMatchingJob(
                mangaId = mangaId,
                chapterId = chapterId,
                pageIndex = pageIndex,
                scope = scope.value,
                pipeline = pipeline,
                mode = mode.value,
                targetLanguage = targetLanguage,
            ).awaitAsOneOrNull()
            if (duplicate != null) {
                insertLog(
                    jobId = duplicate._id,
                    pageId = null,
                    level = TranslationLogLevel.Info,
                    tag = "queue",
                    message = "Skipped duplicate translation job",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "duplicate_skip",
                        jobId = duplicate._id,
                        previousStatus = duplicate.status,
                        nextStatus = duplicate.status,
                        reason = "Active matching job already exists",
                        extra = mapOf(
                            "manga_id" to mangaId,
                            "chapter_id" to chapterId,
                            "page_index" to pageIndex,
                            "scope" to scope.value,
                            "pipeline" to pipeline,
                            "mode" to mode.value,
                            "target_language" to targetLanguage.ifBlank { "app language" },
                        ),
                    ),
                )
                result = TranslationEnqueueResult(jobId = duplicate._id, inserted = false)
                return@transaction
            }

            val now = System.currentTimeMillis()
            database.translationsQueries.insertJob(
                mangaId = mangaId,
                chapterId = chapterId,
                pageIndex = pageIndex,
                scope = scope.value,
                pipeline = pipeline,
                mode = mode.value,
                model = model,
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage,
                overwrite = overwrite,
                status = TranslationJobStatus.Queued.value,
                progressTotal = progressTotal,
                createdAt = now,
            )
            val insertedJobId = database.translationsQueries.lastInsertedJobId().awaitAsOne()
            result = TranslationEnqueueResult(
                jobId = insertedJobId,
                inserted = true,
            )
            insertLog(
                jobId = insertedJobId,
                pageId = null,
                level = TranslationLogLevel.Info,
                tag = "queue",
                message = "Queued translation job",
                details = TranslationLogDetailsFormatter.queueState(
                    action = "enqueue",
                    jobId = insertedJobId,
                    previousStatus = null,
                    nextStatus = TranslationJobStatus.Queued.value,
                    extra = mapOf(
                        "manga_id" to mangaId,
                        "chapter_id" to chapterId,
                        "page_index" to pageIndex,
                        "scope" to scope.value,
                        "pipeline" to pipeline,
                        "mode" to mode.value,
                        "model" to model,
                        "target_language" to targetLanguage.ifBlank { "app language" },
                        "source_language" to sourceLanguage,
                        "overwrite" to overwrite,
                    ),
                ),
            )
        }
        return requireNotNull(result)
    }

    suspend fun enqueueImageJobs(
        mangaId: Long,
        chapterId: Long,
        pageIndexes: List<Int>,
        pipeline: String,
        mode: TranslationMode,
        model: String,
        targetLanguage: String,
        sourceLanguage: String?,
        overwrite: Boolean,
    ): TranslationBulkEnqueueResult {
        var queued = 0
        var skipped = 0
        var raceDuplicates = 0
        val now = System.currentTimeMillis()
        database.transaction {
            pageIndexes.distinct().forEach { pageIndex ->
                val duplicate = database.translationsQueries.getActiveMatchingJob(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    pageIndex = pageIndex.toLong(),
                    scope = TranslationScope.Image.value,
                    pipeline = pipeline,
                    mode = mode.value,
                    targetLanguage = targetLanguage,
                ).awaitAsOneOrNull()
                if (duplicate != null) {
                    skipped++
                    raceDuplicates++
                    insertLog(
                        jobId = duplicate._id,
                        pageId = null,
                        level = TranslationLogLevel.Info,
                        tag = "queue",
                        message = "Skipped duplicate translation job",
                        details = TranslationLogDetailsFormatter.queueState(
                            action = "duplicate_skip",
                            jobId = duplicate._id,
                            previousStatus = duplicate.status,
                            nextStatus = duplicate.status,
                            reason = "Active matching job already exists",
                            extra = mapOf(
                                "manga_id" to mangaId,
                                "chapter_id" to chapterId,
                                "page_index" to pageIndex,
                                "scope" to TranslationScope.Image.value,
                                "pipeline" to pipeline,
                                "mode" to mode.value,
                                "target_language" to targetLanguage.ifBlank { "app language" },
                            ),
                        ),
                    )
                    return@forEach
                }

                database.translationsQueries.insertJob(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    pageIndex = pageIndex.toLong(),
                    scope = TranslationScope.Image.value,
                    pipeline = pipeline,
                    mode = mode.value,
                    model = model,
                    targetLanguage = targetLanguage,
                    sourceLanguage = sourceLanguage,
                    overwrite = overwrite,
                    status = TranslationJobStatus.Queued.value,
                    progressTotal = 1,
                    createdAt = now,
                )
                val insertedJobId = database.translationsQueries.lastInsertedJobId().awaitAsOne()
                queued++
                insertLog(
                    jobId = insertedJobId,
                    pageId = null,
                    level = TranslationLogLevel.Info,
                    tag = "queue",
                    message = "Queued translation job",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "enqueue",
                        jobId = insertedJobId,
                        previousStatus = null,
                        nextStatus = TranslationJobStatus.Queued.value,
                        extra = mapOf(
                            "manga_id" to mangaId,
                            "chapter_id" to chapterId,
                            "page_index" to pageIndex,
                            "scope" to TranslationScope.Image.value,
                            "pipeline" to pipeline,
                            "mode" to mode.value,
                            "model" to model,
                            "target_language" to targetLanguage.ifBlank { "app language" },
                            "source_language" to sourceLanguage,
                            "overwrite" to overwrite,
                        ),
                    ),
                )
            }
        }
        return TranslationBulkEnqueueResult(queued, skipped, raceDuplicates)
    }

    suspend fun updateJobStatus(
        job: Translation_jobs,
        status: TranslationJobStatus,
        errorMessage: String? = null,
        attempts: Long = job.attempts,
    ) {
        database.translationsQueries.updateJobStatus(
            status = status.value,
            attempts = attempts,
            errorMessage = errorMessage?.take(MAX_ERROR_LENGTH),
            updatedAt = System.currentTimeMillis(),
            id = job._id,
        )
    }

    suspend fun updateJobProgress(job: Translation_jobs, current: Long, total: Long) {
        database.translationsQueries.updateJobProgress(
            progressCurrent = current,
            progressTotal = total,
            updatedAt = System.currentTimeMillis(),
            id = job._id,
        )
    }

    suspend fun heartbeatRunningJobs(jobs: List<Translation_jobs>) {
        if (jobs.isEmpty()) return
        val updatedAt = System.currentTimeMillis()
        database.transaction {
            jobs.distinctBy { it._id }.forEach { job ->
                database.translationsQueries.heartbeatRunningJob(
                    updatedAt = updatedAt,
                    id = job._id,
                )
            }
        }
    }

    suspend fun retryJob(job: Translation_jobs, forceOverwrite: Boolean) {
        database.translationsQueries.retryJob(
            overwrite = if (forceOverwrite) true else job.overwrite,
            updatedAt = System.currentTimeMillis(),
            id = job._id,
        )
    }

    suspend fun retryJobs(jobs: List<Translation_jobs>, forceOverwrite: Boolean): Int {
        var retried = 0
        database.transaction {
            jobs.distinctBy { it._id }.forEach { job ->
                database.translationsQueries.retryJob(
                    overwrite = if (forceOverwrite) true else job.overwrite,
                    updatedAt = System.currentTimeMillis(),
                    id = job._id,
                )
                retried++
            }
        }
        return retried
    }

    suspend fun deleteJob(id: Long) {
        database.translationsQueries.deleteJob(id)
    }

    suspend fun clearFinishedJobs() {
        database.translationsQueries.clearFinishedJobs()
    }

    suspend fun clearAllJobsAndLogs() {
        database.transaction {
            database.translationsQueries.clearLogs()
            database.translationsQueries.clearJobs()
        }
    }

    suspend fun clearLogs() {
        database.translationsQueries.clearLogs()
    }

    suspend fun clearPages() {
        database.translationsQueries.clearPages()
    }

    suspend fun deletePage(id: Long) {
        database.translationsQueries.deletePage(id)
    }

    suspend fun requeuePausedAuthJobs(reason: String): Long {
        val jobs = database.translationsQueries.getJobsByStatus(TranslationJobStatus.PausedAuth.value).awaitAsList()
        database.transaction {
            jobs.forEach { job ->
                updateJobStatus(
                    job = job,
                    status = TranslationJobStatus.Queued,
                    errorMessage = null,
                    attempts = 0,
                )
                insertLog(
                    jobId = job._id,
                    pageId = null,
                    level = TranslationLogLevel.Info,
                    tag = "queue",
                    message = "Requeued paused auth translation",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "auto_requeue_auth",
                        jobId = job._id,
                        previousStatus = job.status,
                        nextStatus = TranslationJobStatus.Queued.value,
                        reason = reason,
                    ),
                )
            }
        }
        return jobs.size.toLong()
    }

    suspend fun pausePendingJobsForSetup(message: String): Long {
        val jobs = getPendingJobs()
        database.transaction {
            jobs.forEach { job ->
                updateJobStatus(
                    job = job,
                    status = TranslationJobStatus.PausedAuth,
                    errorMessage = message,
                )
                insertLog(
                    jobId = job._id,
                    pageId = null,
                    level = TranslationLogLevel.Warning,
                    tag = "queue",
                    message = "Paused translation queue",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "pause_setup_not_ready",
                        jobId = job._id,
                        previousStatus = job.status,
                        nextStatus = TranslationJobStatus.PausedAuth.value,
                        reason = message,
                    ),
                )
            }
        }
        return jobs.size.toLong()
    }

    suspend fun claimJobs(
        jobs: List<Translation_jobs>,
        kind: TranslationWorkKind,
        claimToken: String,
    ): List<Translation_jobs> {
        val claimed = mutableListOf<Translation_jobs>()
        database.transaction {
            jobs.forEach { job ->
                val nextAttempt = job.attempts + 1
                database.translationsQueries.claimJob(
                    attempts = nextAttempt,
                    claimToken = claimToken,
                    updatedAt = System.currentTimeMillis(),
                    id = job._id,
                )
                val refreshed = database.translationsQueries.getJob(job._id).awaitAsOneOrNull()
                if (refreshed?.status == TranslationJobStatus.Running.value && refreshed.error_message == claimToken) {
                    claimed += refreshed.copy(error_message = null)
                    insertLog(
                        jobId = job._id,
                        pageId = null,
                        level = TranslationLogLevel.Debug,
                        tag = "queue",
                        message = "Claimed translation job",
                        details = TranslationLogDetailsFormatter.queueState(
                            action = "claim_job",
                            jobId = job._id,
                            previousStatus = job.status,
                            nextStatus = TranslationJobStatus.Running.value,
                            extra = mapOf(
                                "worker_kind" to kind.value,
                                "attempt" to nextAttempt,
                            ) + TranslationClaimToken.publicLogFields(claimToken),
                        ),
                    )
                } else {
                    insertLog(
                        jobId = job._id,
                        pageId = null,
                        level = TranslationLogLevel.Debug,
                        tag = "queue",
                        message = "Skipped unclaimed translation job",
                        details = TranslationLogDetailsFormatter.queueState(
                            action = "claim_skip",
                            jobId = job._id,
                            previousStatus = job.status,
                            nextStatus = refreshed?.status,
                            reason = "Job claimed or changed by another worker",
                            extra = mapOf("worker_kind" to kind.value),
                        ),
                    )
                }
            }
        }
        return claimed
    }

    suspend fun resumeAllJobs(skipExistingOverlays: Boolean, includeRunning: Boolean): TranslationResumeAllResult {
        val candidates = database.translationsQueries.getResumeCandidateJobs().awaitAsList()
        var requeued = 0
        var skippedExisting = 0
        var skippedRunning = 0
        database.transaction {
            candidates.forEach { job ->
                if (job.status == TranslationJobStatus.Running.value && !includeRunning) {
                    skippedRunning++
                    return@forEach
                }
                val chapterId = job.chapter_id
                val pageIndex = job.page_index
                val hasSavedOverlay = if (
                    skipExistingOverlays &&
                    job.status != TranslationJobStatus.ManualRetry.value &&
                    job.scope == TranslationScope.Image.value &&
                    chapterId != null &&
                    pageIndex != null
                ) {
                    TranslationSavedOverlayPolicy.shouldSkipExistingOverlay(
                        hasSavedPageRow = database.translationsQueries.getPage(
                            chapterId = chapterId,
                            pageIndex = pageIndex,
                            targetLanguage = job.target_language.ifBlank {
                                TranslationLanguages.defaultTargetLanguage()
                            },
                        ).awaitAsOneOrNull() != null,
                        overwrite = job.overwrite,
                    )
                } else {
                    false
                }
                if (hasSavedOverlay) {
                    skippedExisting++
                    insertLog(
                        jobId = job._id,
                        pageId = null,
                        level = TranslationLogLevel.Info,
                        tag = "queue",
                        message = "Resume skipped existing overlay",
                        details = TranslationLogDetailsFormatter.queueState(
                            action = "resume_skip_existing_overlay",
                            jobId = job._id,
                            previousStatus = job.status,
                            nextStatus = job.status,
                            extra = mapOf(
                                "chapter_id" to job.chapter_id,
                                "page_index" to job.page_index,
                                "target_language" to job.target_language,
                            ),
                        ),
                    )
                    return@forEach
                }
                database.translationsQueries.resumeJob(
                    updatedAt = System.currentTimeMillis(),
                    id = job._id,
                )
                requeued++
                insertLog(
                    jobId = job._id,
                    pageId = null,
                    level = TranslationLogLevel.Info,
                    tag = "queue",
                    message = "Resume requeued translation job",
                    details = TranslationLogDetailsFormatter.queueState(
                        action = "resume_all_requeue",
                        jobId = job._id,
                        previousStatus = job.status,
                        nextStatus = TranslationJobStatus.Queued.value,
                        extra = mapOf("include_running" to includeRunning),
                    ),
                )
            }
        }
        return TranslationResumeAllResult(
            requeued = requeued,
            skippedExisting = skippedExisting,
            skippedRunning = skippedRunning,
        )
    }

    suspend fun saveOverlay(
        mangaId: Long,
        chapterId: Long,
        pageIndex: Long,
        sourceImageKey: String,
        model: String,
        targetLanguage: String,
        sourceLanguage: String?,
        pipeline: String,
        imageWidth: Int?,
        imageHeight: Int?,
        overlay: TranslationOverlayResult,
    ): Translation_pages {
        return saveOverlays(
            listOf(
                TranslationOverlaySaveRequest(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    sourceImageKey = sourceImageKey,
                    model = model,
                    targetLanguage = targetLanguage,
                    sourceLanguage = sourceLanguage,
                    pipeline = pipeline,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    overlay = overlay,
                ),
            ),
        ).single()
    }

    suspend fun saveOverlays(requests: List<TranslationOverlaySaveRequest>): List<Translation_pages> {
        if (requests.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val savedPages = mutableListOf<Translation_pages>()
        database.transaction {
            requests.forEach { request ->
                database.translationsQueries.upsertPage(
                    mangaId = request.mangaId,
                    chapterId = request.chapterId,
                    pageIndex = request.pageIndex,
                    sourceImageKey = request.sourceImageKey,
                    model = request.model,
                    targetLanguage = request.targetLanguage,
                    sourceLanguage = request.sourceLanguage ?: request.overlay.sourceLanguage,
                    pipeline = request.pipeline,
                    imageWidth = request.imageWidth?.toLong(),
                    imageHeight = request.imageHeight?.toLong(),
                    createdAt = now,
                )
                val page = database.translationsQueries
                    .getPage(request.chapterId, request.pageIndex, request.targetLanguage)
                    .awaitAsOne()
                savedPages += page
                database.translationsQueries.deleteBoxesForPage(page._id)
                request.overlay.boxes.mapNotNull { box ->
                    TranslationOverlayPersistenceGuard.normalizedGeometryOrNull(box)
                        ?.let { geometry -> box to geometry }
                }.forEachIndexed { index, (box, geometry) ->
                    database.translationsQueries.insertBox(
                        pageId = page._id,
                        x = geometry.x.toDouble(),
                        y = geometry.y.toDouble(),
                        width = geometry.width.toDouble(),
                        height = geometry.height.toDouble(),
                        originalText = box.originalText,
                        translatedText = box.translatedText,
                        textType = box.textType,
                        confidence = box.confidence?.toDouble(),
                        styleJson = null,
                        sortOrder = index.toLong(),
                    )
                }
            }
        }
        return savedPages
    }

    suspend fun ensurePage(
        mangaId: Long,
        chapterId: Long,
        pageIndex: Long,
        sourceImageKey: String,
        model: String,
        targetLanguage: String,
        sourceLanguage: String?,
        pipeline: String,
    ): Translation_pages {
        val now = System.currentTimeMillis()
        database.translationsQueries.upsertPage(
            mangaId = mangaId,
            chapterId = chapterId,
            pageIndex = pageIndex,
            sourceImageKey = sourceImageKey,
            model = model,
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage,
            pipeline = pipeline,
            imageWidth = null,
            imageHeight = null,
            createdAt = now,
        )
        return database.translationsQueries
            .getPage(chapterId, pageIndex, targetLanguage)
            .awaitAsOne()
    }

    suspend fun replaceBoxes(pageId: Long, boxes: List<TranslationBoxEdit>) {
        database.transaction {
            database.translationsQueries.deleteBoxesForPage(pageId)
            boxes.forEachIndexed { index, box ->
                val geometry = TranslationBoxGeometryNormalizer.normalize(
                    x = box.x.toFloat(),
                    y = box.y.toFloat(),
                    width = box.width.toFloat(),
                    height = box.height.toFloat(),
                )
                database.translationsQueries.insertBox(
                    pageId = pageId,
                    x = geometry.x.toDouble(),
                    y = geometry.y.toDouble(),
                    width = geometry.width.toDouble(),
                    height = geometry.height.toDouble(),
                    originalText = box.originalText,
                    translatedText = box.translatedText,
                    textType = box.textType,
                    confidence = box.confidence,
                    styleJson = box.styleJson,
                    sortOrder = index.toLong(),
                )
            }
            database.translationsQueries.touchPage(
                updatedAt = System.currentTimeMillis(),
                id = pageId,
            )
        }
    }

    suspend fun insertLog(
        jobId: Long?,
        pageId: Long?,
        level: TranslationLogLevel,
        tag: String,
        message: String,
        details: String? = null,
    ) {
        database.translationsQueries.insertLog(
            jobId = jobId,
            pageId = pageId,
            createdAt = System.currentTimeMillis(),
            level = level.value,
            tag = tag,
            message = message,
            details = details?.let(TranslationLogRedactor::redact),
        )
    }

    suspend fun hasActiveMatchingJob(
        mangaId: Long,
        chapterId: Long?,
        pageIndex: Long?,
        scope: TranslationScope,
        pipeline: String,
        mode: TranslationMode,
        targetLanguage: String,
    ): Boolean {
        return database.translationsQueries.getActiveMatchingJob(
            mangaId = mangaId,
            chapterId = chapterId,
            pageIndex = pageIndex,
            scope = scope.value,
            pipeline = pipeline,
            mode = mode.value,
            targetLanguage = targetLanguage,
        ).awaitAsOneOrNull() != null
    }

    companion object {
        private const val MAX_ERROR_LENGTH = 4_000
    }
}

data class TranslationEnqueueResult(
    val jobId: Long,
    val inserted: Boolean,
)

data class TranslationBulkEnqueueResult(
    val queued: Int,
    val skipped: Int,
    val raceDuplicates: Int,
)

data class TranslationOverlaySaveRequest(
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Long,
    val sourceImageKey: String,
    val model: String,
    val targetLanguage: String,
    val sourceLanguage: String?,
    val pipeline: String,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val overlay: TranslationOverlayResult,
)

data class SavedTranslationPage(
    val page: Translation_pages,
    val boxes: List<Translation_boxes>,
)

fun TranslationQueueItem.toJob(): Translation_jobs {
    return Translation_jobs(
        _id = id,
        manga_id = mangaId,
        chapter_id = chapterId,
        page_index = pageIndex,
        scope = scope,
        pipeline = pipeline,
        mode = mode,
        model = model,
        target_language = targetLanguage,
        source_language = sourceLanguage,
        overwrite = overwrite,
        status = status,
        progress_current = progressCurrent,
        progress_total = progressTotal,
        attempts = attempts,
        created_at = createdAt,
        updated_at = updatedAt,
        error_message = errorMessage,
    )
}

private fun GetJobsForQueue.toQueueItem(): TranslationQueueItem {
    return TranslationQueueItem(
        id = _id,
        mangaId = manga_id,
        mangaTitle = manga_title,
        chapterId = chapter_id,
        chapterName = chapter_name,
        chapterNumber = chapter_number,
        pageIndex = page_index,
        scope = scope,
        pipeline = pipeline,
        mode = mode,
        model = model,
        targetLanguage = target_language,
        sourceLanguage = source_language,
        overwrite = overwrite,
        status = status,
        progressCurrent = progress_current,
        progressTotal = progress_total,
        attempts = attempts,
        createdAt = created_at,
        updatedAt = updated_at,
        errorMessage = TranslationClaimToken.publicErrorMessage(error_message),
    )
}

private fun GetJobForQueue.toQueueItem(): TranslationQueueItem {
    return TranslationQueueItem(
        id = _id,
        mangaId = manga_id,
        mangaTitle = manga_title,
        chapterId = chapter_id,
        chapterName = chapter_name,
        chapterNumber = chapter_number,
        pageIndex = page_index,
        scope = scope,
        pipeline = pipeline,
        mode = mode,
        model = model,
        targetLanguage = target_language,
        sourceLanguage = source_language,
        overwrite = overwrite,
        status = status,
        progressCurrent = progress_current,
        progressTotal = progress_total,
        attempts = attempts,
        createdAt = created_at,
        updatedAt = updated_at,
        errorMessage = TranslationClaimToken.publicErrorMessage(error_message),
    )
}

enum class TranslationScope(val value: String) {
    Image("image"),
    Chapter("chapter"),
    Manga("manga"),
}

enum class TranslationMode(val value: String) {
    Overlay("overlay"),
}

enum class TranslationJobStatus(val value: String) {
    Queued("queued"),
    Running("running"),
    Retrying("retrying"),
    ManualRetry("manual_retry"),
    Completed("completed"),
    Failed("failed"),
    Cancelled("cancelled"),
    PausedAuth("paused_auth"),
    PausedQuota("paused_quota"),
}

enum class TranslationLogLevel(val value: String) {
    Debug("debug"),
    Info("info"),
    Warning("warning"),
    Error("error"),
}
