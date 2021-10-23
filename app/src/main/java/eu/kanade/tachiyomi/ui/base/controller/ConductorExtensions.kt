package eu.kanade.tachiyomi.ui.base.controller

import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.openInBrowser

fun Router.setRoot(controller: Controller, id: Int) {
    setRoot(controller.withFadeTransaction().tag(id.toString()))
}

fun Router.popControllerWithTag(tag: String): Boolean {
    val controller = getControllerWithTag(tag)
    if (controller != null) {
        popController(controller)
        return true
    }
    return false
}

fun Controller.requestPermissionsSafe(permissions: Array<String>, requestCode: Int) {
    val activity = activity ?: return
    permissions.forEach { permission ->
        if (ContextCompat.checkSelfPermission(activity, permission) != PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), requestCode)
        }
    }
}

fun Controller.withFadeTransaction(): RouterTransaction {
    return RouterTransaction.with(this)
        .pushChangeHandler(OneWayFadeChangeHandler())
        .popChangeHandler(OneWayFadeChangeHandler())
}

fun Controller.openInBrowser(url: String) {
    activity?.openInBrowser(url.toUri())
}

/**
 * Returns [MainActivity]'s app bar height
 */
fun Controller.getMainAppBarHeight(): Int {
    return (activity as? MainActivity)?.binding?.appbar?.measuredHeight ?: 0
}
