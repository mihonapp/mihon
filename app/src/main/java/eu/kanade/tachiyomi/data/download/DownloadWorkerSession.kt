package eu.kanade.tachiyomi.data.download

import kotlinx.coroutines.Job
import java.util.UUID

internal class DownloadWorkerSession {
    // Worker callbacks and user actions must change the downloader atomically.
    val lock = Any()

    private var requestedId: UUID? = null
    private var stopped = false
    private var worker: Job? = null

    fun request(id: UUID) = synchronized(lock) {
        requestedId = id
        stopped = false
        worker?.cancel()
    }

    fun stop() = synchronized(lock) {
        stopped = true
        worker?.cancel()
    }

    fun attach(id: UUID, job: Job): Boolean = synchronized(lock) {
        if (stopped || !job.isActive || (requestedId != null && requestedId != id)) return false
        worker?.cancel()
        worker = job
        true
    }

    fun isActive(job: Job): Boolean = synchronized(lock) {
        !stopped && worker === job && job.isActive
    }

    fun owns(job: Job): Boolean = synchronized(lock) { worker === job }

    fun detach(job: Job) = synchronized(lock) {
        if (worker === job) worker = null
    }
}
