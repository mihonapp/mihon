package eu.kanade.tachiyomi.data.track.komga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KomgaPageProgressTest {

    @Test
    fun `fresh session is push-suppressed before any pull resolves`() {
        val session = KomgaChapterSession()

        session.isPushSuppressed shouldBe true
    }

    @Test
    fun `remote strictly ahead jumps forward and lifts suppression`() {
        val session = KomgaChapterSession()

        val action = session.onPullResolved(KomgaPullResult.Recorded(page = 42), currentPage = 10)

        action shouldBe ReconciliationAction.JumpTo(42)
        session.isPushSuppressed shouldBe false
    }

    @Test
    fun `remote equal to current page is a no-op and still lifts suppression`() {
        val session = KomgaChapterSession()

        val action = session.onPullResolved(KomgaPullResult.Recorded(page = 10), currentPage = 10)

        action shouldBe ReconciliationAction.NoOp
        session.isPushSuppressed shouldBe false
    }

    @Test
    fun `remote behind current page never rewinds and still lifts suppression`() {
        val session = KomgaChapterSession()

        // A slow pull resolving after the reader has already advanced further locally.
        val action = session.onPullResolved(KomgaPullResult.Recorded(page = 5), currentPage = 20)

        action shouldBe ReconciliationAction.NoOp
        session.isPushSuppressed shouldBe false
    }

    @Test
    fun `absent remote progress is an explicit reset overriding forward-only, and lifts suppression`() {
        val session = KomgaChapterSession()

        // Remote is "absent" (e.g. marked unread via Komga's UI), even though local is way ahead.
        val action = session.onPullResolved(KomgaPullResult.Absent, currentPage = 50)

        action shouldBe ReconciliationAction.Reset
        session.isPushSuppressed shouldBe false
    }

    @Test
    fun `a late-resolving success still lifts suppression and reconciles`() {
        val session = KomgaChapterSession()
        session.isPushSuppressed shouldBe true

        // No timeout concept here: however long the pull took, resolving successfully lifts
        // suppression and yields the same reconciliation decision as an immediate resolution.
        val action = session.onPullResolved(KomgaPullResult.Recorded(page = 7), currentPage = 3)

        action shouldBe ReconciliationAction.JumpTo(7)
        session.isPushSuppressed shouldBe false
    }

    @Test
    fun `a hard pull failure is a no-op and leaves the session push-suppressed`() {
        val session = KomgaChapterSession()

        val action = session.onPullResolved(KomgaPullResult.Failure, currentPage = 10)

        action shouldBe ReconciliationAction.NoOp
        session.isPushSuppressed shouldBe true
    }

    @Test
    fun `a fresh chapter session starts independent of a prior session's outcome`() {
        val failedSession = KomgaChapterSession()
        failedSession.onPullResolved(KomgaPullResult.Failure, currentPage = 10)
        failedSession.isPushSuppressed shouldBe true

        // Opening the next chapter creates a brand-new session, unaffected by the previous one.
        val nextSession = KomgaChapterSession()

        nextSession.isPushSuppressed shouldBe true
        val action = nextSession.onPullResolved(KomgaPullResult.Recorded(page = 1), currentPage = 0)
        action shouldBe ReconciliationAction.JumpTo(1)
        nextSession.isPushSuppressed shouldBe false
    }
}
