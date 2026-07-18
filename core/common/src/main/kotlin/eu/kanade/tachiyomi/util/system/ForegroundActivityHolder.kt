package eu.kanade.tachiyomi.util.system

import android.app.Activity
import java.lang.ref.WeakReference

/** Weakly tracks the resumed [Activity] (set from the Application's ActivityLifecycleCallbacks) so
 *  the Cloudflare solver can attach its WebView to a real window. */
object ForegroundActivityHolder {

    private var reference: WeakReference<Activity> = WeakReference(null)

    var activity: Activity?
        get() = reference.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        set(value) {
            reference = WeakReference(value)
        }
}
