package exh.ui.batchadd

import com.jakewharton.rxrelay.BehaviorRelay
import com.jakewharton.rxrelay.ReplayRelay
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.metadata.nullIfBlank
import kotlin.concurrent.thread

class BatchAddPresenter: BasePresenter<BatchAddController>() {

    private val galleryAdder by lazy { GalleryAdder() }

    val progressTotalRelay = BehaviorRelay.create(0)!!
    val progressRelay = BehaviorRelay.create(0)!!
    var eventRelay: ReplayRelay<String>? = null
    val currentlyAddingRelay = BehaviorRelay.create(STATE_IDLE)!!

    fun addGalleries(galleries: String) {
        eventRelay = ReplayRelay.create()
        val splitGalleries = galleries.split("\n").mapNotNull {
            it.trim().nullIfBlank()
        }

        progressRelay.call(0)
        progressTotalRelay.call(splitGalleries.size)

        currentlyAddingRelay.call(STATE_INPUT_TO_PROGRESS)

        thread {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<String>()

            splitGalleries.forEachIndexed { i, s ->
                val result = galleryAdder.addGallery(s, true)
                if(result is GalleryAddEvent.Success) {
                    succeeded.add(s)
                } else {
                    failed.add(s)
                }
                progressRelay.call(i + 1)
                eventRelay?.call((when (result) {
                    is GalleryAddEvent.Success -> "[OK]"
                    is GalleryAddEvent.Fail -> "[ERROR]"
                }) + " " + result.logMessage)
            }

            //Show report
            val summary = "\nSummary:\nAdded: ${succeeded.size} gallerie(s)\nFailed: ${failed.size} gallerie(s)"
            eventRelay?.call(summary)
        }
    }

    companion object {
        const val STATE_IDLE = 0
        const val STATE_INPUT_TO_PROGRESS = 1
        const val STATE_PROGRESS_TO_INPUT = 2
    }
}
