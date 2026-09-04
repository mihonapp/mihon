package eu.kanade.tachiyomi.data.download

import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DownloadWorkerSessionTest {
    private val session = DownloadWorkerSession()

    @Test
    fun `persisted work can attach after process recreation`() {
        val worker = Job()
        assertTrue(session.attach(UUID.randomUUID(), worker))
        assertTrue(session.isActive(worker))
    }

    @Test
    fun `manual stop rejects a worker that has not started yet`() {
        val id = UUID.randomUUID()
        session.request(id)
        session.stop()
        assertFalse(session.attach(id, Job()))
    }

    @Test
    fun `replacement rejects stale work and cancels the old worker`() {
        val oldId = UUID.randomUUID()
        val oldWorker = Job()
        session.request(oldId)
        assertTrue(session.attach(oldId, oldWorker))

        val newId = UUID.randomUUID()
        session.request(newId)
        assertTrue(oldWorker.isCancelled)
        assertFalse(session.attach(oldId, Job()))
        assertTrue(session.attach(newId, Job()))
    }

    @Test
    fun `retry of the same work has independent cleanup ownership`() {
        val id = UUID.randomUUID()
        val oldWorker = Job()
        val newWorker = Job()
        assertTrue(session.attach(id, oldWorker))
        assertTrue(session.attach(id, newWorker))

        assertTrue(oldWorker.isCancelled)
        assertFalse(session.owns(oldWorker))
        session.detach(oldWorker)
        assertTrue(session.isActive(newWorker))
    }

    @Test
    fun `resume after manual pause accepts only the new request`() {
        val oldId = UUID.randomUUID()
        val oldWorker = Job()
        session.request(oldId)
        session.attach(oldId, oldWorker)
        session.stop()
        assertTrue(oldWorker.isCancelled)
        assertFalse(session.isActive(oldWorker))

        val newId = UUID.randomUUID()
        session.request(newId)
        assertFalse(session.attach(oldId, Job()))
        assertTrue(session.attach(newId, Job()))
    }
}
