package eu.kanade.tachiyomi.ui.base.presenter

import android.content.Context
import nucleus.view.ViewWithPresenter
import org.greenrobot.eventbus.EventBus
import rx.Observable

open class BasePresenter<V : ViewWithPresenter<*>> : RxPresenter<V>() {

    lateinit var context: Context

    fun registerForEvents() {
        EventBus.getDefault().register(this)
    }

    fun unregisterForEvents() {
        EventBus.getDefault().unregister(this)
    }

    fun <T> Observable<T>.subscribeFirst(onNext: (V, T) -> Unit, onError: ((V, Throwable) -> Unit)? = null)
            = compose(deliverFirst<T>()).subscribe(split(onNext, onError))

    fun <T> Observable<T>.subscribeLatestCache(onNext: (V, T) -> Unit, onError: ((V, Throwable) -> Unit)? = null)
            = compose(deliverLatestCache<T>()).subscribe(split(onNext, onError))

    fun <T> Observable<T>.subscribeReplay(onNext: (V, T) -> Unit, onError: ((V, Throwable) -> Unit)? = null)
            = compose(deliverReplay<T>()).subscribe(split(onNext, onError))

}
