package exh.ui.intercept

import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import exh.GalleryAddEvent
import exh.GalleryAdder
import rx.subjects.BehaviorSubject
import kotlin.concurrent.thread

class InterceptActivityPresenter : BasePresenter<InterceptActivity>() {
    private val galleryAdder = GalleryAdder()

    val status = BehaviorSubject.create<InterceptResult>(InterceptResult.Idle())

    @Synchronized
    fun loadGallery(gallery: String) {
        //Do not load gallery if already loading
        if(status.value is InterceptResult.Idle) {
            status.onNext(InterceptResult.Loading())

            //Load gallery async
            thread {
                val result = galleryAdder.addGallery(gallery)

                status.onNext(when (result) {
                    is GalleryAddEvent.Success -> result.manga.id?.let {
                        InterceptResult.Success(it)
                    } ?: InterceptResult.Failure("Manga ID is null!")
                    is GalleryAddEvent.Fail -> InterceptResult.Failure(result.logMessage)
                })
            }
        }
    }
}

sealed class InterceptResult {
    class Idle : InterceptResult()
    class Loading : InterceptResult()
    data class Success(val mangaId: Long): InterceptResult()
    data class Failure(val reason: String): InterceptResult()
}