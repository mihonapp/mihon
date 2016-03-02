package eu.kanade.tachiyomi.ui.base.presenter

import android.content.Context
import android.os.Bundle
import icepick.Icepick
import nucleus.view.ViewWithPresenter
import org.greenrobot.eventbus.EventBus

open class BasePresenter<V : ViewWithPresenter<*>> : RxPresenter<V>() {

    lateinit var context: Context

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        Icepick.restoreInstanceState(this, savedState)
    }

    override fun onSave(state: Bundle) {
        super.onSave(state)
        Icepick.saveInstanceState(this, state)
    }

    fun registerForEvents() {
        EventBus.getDefault().register(this)
    }

    fun unregisterForEvents() {
        EventBus.getDefault().unregister(this)
    }

}
