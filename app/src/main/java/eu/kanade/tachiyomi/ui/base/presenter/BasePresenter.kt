package eu.kanade.tachiyomi.ui.base.presenter

import android.os.Bundle
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.tachiyomi.core.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import nucleus.presenter.RxPresenter

open class BasePresenter<V> : RxPresenter<V>() {

    var presenterScope: CoroutineScope = MainScope()

    override fun onCreate(savedState: Bundle?) {
        try {
            super.onCreate(savedState)
        } catch (e: NullPointerException) {
            // Swallow this error. This should be fixed in the library but since it's not critical
            // (only used by restartables) it should be enough. It saves me a fork.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenterScope.cancel()
    }

    // We're trying to avoid using Rx, so we "undeprecate" this
    @Suppress("DEPRECATION")
    override fun getView(): V? {
        return super.getView()
    }

    fun <T> Preference<T>.asState() = PreferenceMutableState(this, presenterScope)
}
