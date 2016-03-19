package eu.kanade.tachiyomi.ui.base.presenter

import android.content.Context
import nucleus.view.ViewWithPresenter
import org.greenrobot.eventbus.EventBus

open class BasePresenter<V : ViewWithPresenter<*>> : RxPresenter<V>() {

    lateinit var context: Context

    fun registerForEvents() {
        EventBus.getDefault().register(this)
    }

    fun unregisterForEvents() {
        EventBus.getDefault().unregister(this)
    }

}
