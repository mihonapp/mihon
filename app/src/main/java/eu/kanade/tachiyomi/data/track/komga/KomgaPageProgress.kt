package eu.kanade.tachiyomi.data.track.komga

/** Outcome of a chapter-open pull, adding [Failure] to the raw [KomgaBookProgress]. */
sealed interface KomgaPullResult {
    data class Recorded(val page: Int) : KomgaPullResult
    data object Absent : KomgaPullResult
    data object Failure : KomgaPullResult
}

/** What the reader should do once a chapter-open pull resolves. */
sealed interface ReconciliationAction {
    data object NoOp : ReconciliationAction
    data class JumpTo(val page: Int) : ReconciliationAction
    data object Reset : ReconciliationAction
}

/**
 * Reconciliation only jumps forward: remote wins only when it's strictly ahead of [currentPage].
 * Absent remote progress is an explicit reset. A hard pull failure is a no-op.
 */
fun reconcile(currentPage: Int, pullResult: KomgaPullResult): ReconciliationAction = when (pullResult) {
    is KomgaPullResult.Recorded -> if (pullResult.page > currentPage) {
        ReconciliationAction.JumpTo(pullResult.page)
    } else {
        ReconciliationAction.NoOp
    }
    KomgaPullResult.Absent -> ReconciliationAction.Reset
    KomgaPullResult.Failure -> ReconciliationAction.NoOp
}

/**
 * Push suppression for a single chapter-viewing session. New sessions start suppressed;
 * [onPullResolved] lifts suppression unless the pull hard-failed.
 */
class KomgaChapterSession {
    var isPushSuppressed = true
        private set

    fun onPullResolved(result: KomgaPullResult, currentPage: Int): ReconciliationAction {
        isPushSuppressed = result is KomgaPullResult.Failure
        return reconcile(currentPage, result)
    }
}
