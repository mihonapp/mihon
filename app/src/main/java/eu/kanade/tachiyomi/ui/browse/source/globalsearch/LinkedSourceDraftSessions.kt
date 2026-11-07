package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.domain.source.service.LinkedSourceDraft

@Inject
@SingleIn(AppScope::class)
class LinkedSourceDraftSessions {
    class Session(val draft: LinkedSourceDraft = LinkedSourceDraft()) {
        var references: Int = 0
    }

    private val sessions = mutableMapOf<String, Session>()

    @Synchronized
    fun acquire(id: String): Session = sessions.getOrPut(id) { Session() }.also { it.references++ }

    @Synchronized
    fun release(id: String) {
        val session = sessions[id] ?: return
        if (--session.references == 0) sessions.remove(id)
    }
}
