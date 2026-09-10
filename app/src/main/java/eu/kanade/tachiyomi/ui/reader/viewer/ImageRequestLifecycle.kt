package eu.kanade.tachiyomi.ui.reader.viewer

import coil3.request.Disposable

/**
 * Owns the asynchronous image request associated with a reusable reader page view.
 */
internal class ImageRequestLifecycle {

    private var generation = 0L
    private var disposable: Disposable? = null

    fun start(): Long {
        generation++
        disposable?.dispose()
        disposable = null
        return generation
    }

    fun track(generation: Long, disposable: Disposable) {
        if (isCurrent(generation)) {
            this.disposable = disposable
        } else {
            disposable.dispose()
        }
    }

    fun isCurrent(generation: Long): Boolean {
        return generation == this.generation
    }

    fun dispose() {
        start()
    }
}
