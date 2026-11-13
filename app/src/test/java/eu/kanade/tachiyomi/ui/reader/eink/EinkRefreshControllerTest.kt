package eu.kanade.tachiyomi.ui.reader.eink

import android.view.View
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FAST
import eu.kanade.tachiyomi.ui.reader.eink.EinkRefreshMode.FULL
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class EinkRefreshControllerTest {
    private val preferences = ReaderPreferences(InMemoryPreferenceStore())

    @Test
    fun `disabled hardware refresh returns false and schedules nothing`() {
        val backend = FakeBackend()
        val scheduler = FakeScheduler()

        EinkRefreshController(backend, preferences)
            .onPageChanged(null, scheduler) shouldBe false

        scheduler.pending shouldBe null
    }

    @Test
    fun `supported full refresh schedules with configured delay`() {
        preferences.einkHardwareRefresh.set(true)
        preferences.einkRefreshDelayMillis.set(500)
        val backend = FakeBackend(supportedModes = setOf(FULL))
        val scheduler = FakeScheduler()

        EinkRefreshController(backend, preferences)
            .onPageChanged(null, scheduler) shouldBe true

        scheduler.delayMs shouldBe 500L
        scheduler.run()
        backend.refreshes shouldBe listOf(FULL)
    }

    @Test
    fun `interval greater than one only schedules on the nth page`() {
        preferences.einkHardwareRefresh.set(true)
        preferences.einkRefreshInterval.set(2)
        val backend = FakeBackend(supportedModes = setOf(FULL))
        val scheduler = FakeScheduler()
        val controller = EinkRefreshController(backend, preferences)

        controller.onPageChanged(null, scheduler) shouldBe true
        scheduler.pending shouldBe null

        controller.onPageChanged(null, scheduler) shouldBe true
        scheduler.pending shouldNotBe null
    }

    @Test
    fun `unsupported mode returns false so the overlay can fall back`() {
        preferences.einkHardwareRefresh.set(true)
        preferences.einkRefreshMode.set(FAST)
        val backend = FakeBackend(supportedModes = setOf(FULL))

        EinkRefreshController(backend, preferences)
            .onPageChanged(null, FakeScheduler()) shouldBe false
    }

    @Test
    fun `rapid page changes replace the previous pending refresh`() {
        preferences.einkHardwareRefresh.set(true)
        val backend = FakeBackend(supportedModes = setOf(FULL))
        val scheduler = FakeScheduler()
        val controller = EinkRefreshController(backend, preferences)

        controller.onPageChanged(null, scheduler)
        controller.onPageChanged(null, scheduler)

        scheduler.scheduleCount shouldBe 2
        scheduler.run()
        backend.refreshes.size shouldBe 1
    }

    @Test
    fun `pause and resume delegate to the backend`() {
        val backend = FakeBackend()
        val controller = EinkRefreshController(backend, preferences)

        controller.onPause()
        controller.onResume()

        backend.pauses shouldBe 1
        backend.resumes shouldBe 1
    }
}

private class FakeBackend(
    override val platform: String = "fake",
    override val supportedModes: Set<EinkRefreshMode> = setOf(FULL),
) : EinkRefreshBackend {
    val refreshes = mutableListOf<EinkRefreshMode>()
    var pauses = 0
    var resumes = 0

    override fun refresh(view: View?, mode: EinkRefreshMode) {
        refreshes += mode
    }

    override fun onResume() {
        resumes++
    }

    override fun onPause() {
        pauses++
    }
}

private class FakeScheduler : EinkRefreshScheduler {
    var pending: (() -> Unit)? = null
    var delayMs: Long? = null
    var scheduleCount = 0

    override fun schedule(delayMs: Long, action: () -> Unit) {
        scheduleCount++
        this.delayMs = delayMs
        pending = action
    }

    override fun cancel() {
        pending = null
    }

    fun run() {
        pending?.invoke()
        pending = null
    }
}
