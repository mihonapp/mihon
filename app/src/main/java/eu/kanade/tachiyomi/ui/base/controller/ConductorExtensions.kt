package eu.kanade.tachiyomi.ui.base.controller

import com.bluelinelabs.conductor.Router

fun Router.popControllerWithTag(tag: String): Boolean {
    val controller = getControllerWithTag(tag)
    if (controller != null) {
        popController(controller)
        return true
    }
    return false
}