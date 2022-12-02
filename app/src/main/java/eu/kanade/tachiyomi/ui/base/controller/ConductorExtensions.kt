package eu.kanade.tachiyomi.ui.base.controller

import androidx.core.net.toUri
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import eu.kanade.tachiyomi.util.system.openInBrowser

fun Router.setRoot(controller: Controller, id: Int) {
    setRoot(controller.withFadeTransaction().tag(id.toString()))
}

fun Router.pushController(controller: Controller) {
    pushController(controller.withFadeTransaction())
}

fun Controller.withFadeTransaction(): RouterTransaction {
    return RouterTransaction.with(this)
        .pushChangeHandler(OneWayFadeChangeHandler())
        .popChangeHandler(OneWayFadeChangeHandler())
}

fun Controller.openInBrowser(url: String) {
    activity?.openInBrowser(url.toUri())
}
