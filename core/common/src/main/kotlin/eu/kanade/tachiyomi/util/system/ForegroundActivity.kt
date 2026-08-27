package eu.kanade.tachiyomi.util.system

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The activity currently on screen, for the code that needs a real window rather than a context.
 *
 * The Cloudflare bypass is the caller: its WebView only renders a challenge widget, routes touch and
 * reports focus once it is attached to one, and the interceptor it runs from has nothing but the
 * application context. Held weakly and re-checked on read, so a finished activity is never returned.
 *
 * Copied from https://github.com/unseensnick/Reikai/blob/14d3d54/core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/ForegroundActivity.kt
 */
object ForegroundActivity : Application.ActivityLifecycleCallbacks {

    private var last: WeakReference<Activity>? = null

    val current: Activity?
        get() = last?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    fun register(application: Application) = application.registerActivityLifecycleCallbacks(this)

    override fun onActivityResumed(activity: Activity) {
        last = WeakReference(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
