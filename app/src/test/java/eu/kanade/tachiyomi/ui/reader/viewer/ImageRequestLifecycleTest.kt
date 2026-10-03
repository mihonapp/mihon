package eu.kanade.tachiyomi.ui.reader.viewer

import coil3.request.Disposable
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageRequestLifecycleTest {

    @Test
    fun `starting a new request disposes and invalidates the previous request`() {
        val lifecycle = ImageRequestLifecycle()
        val firstDisposable = mockk<Disposable>(relaxed = true)
        val firstGeneration = lifecycle.start()
        lifecycle.track(firstGeneration, firstDisposable)

        val secondGeneration = lifecycle.start()

        verify(exactly = 1) { firstDisposable.dispose() }
        assertFalse(lifecycle.isCurrent(firstGeneration))
        assertTrue(lifecycle.isCurrent(secondGeneration))
    }

    @Test
    fun `a request tracked after its view was rebound is disposed immediately`() {
        val lifecycle = ImageRequestLifecycle()
        val staleDisposable = mockk<Disposable>(relaxed = true)
        val staleGeneration = lifecycle.start()

        lifecycle.start()
        lifecycle.track(staleGeneration, staleDisposable)

        verify(exactly = 1) { staleDisposable.dispose() }
    }

    @Test
    fun `recycling the view disposes the request and invalidates its callbacks`() {
        val lifecycle = ImageRequestLifecycle()
        val disposable = mockk<Disposable>(relaxed = true)
        val generation = lifecycle.start()
        lifecycle.track(generation, disposable)

        lifecycle.dispose()

        verify(exactly = 1) { disposable.dispose() }
        assertFalse(lifecycle.isCurrent(generation))
    }
}
