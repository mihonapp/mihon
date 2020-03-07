package exh.ui.lock

import android.content.Intent
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.injectLazy
import java.util.Date

object LockActivityDelegate {
    private val preferences by injectLazy<PreferencesHelper>()

    var willLock: Boolean = true

    fun doLock(router: Router, animate: Boolean = false) {
        router.pushController(RouterTransaction.with(LockController())
            .popChangeHandler(LockChangeHandler(animate)))
    }


    fun onCreate(activity: FragmentActivity) {
        preferences.secureScreen().asObservable()
                .subscribe {
                    if (it) {
                        activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
    }

    fun onResume(activity: FragmentActivity, router: Router) {
        if (lockEnabled() && !isAppLocked(router) && willLock && !preferences.eh_lockManually().getOrDefault()) {
            doLock(router)
            willLock = false
        }
    }

    private fun isAppLocked(router: Router): Boolean {
        return router.backstack.lastOrNull()?.controller() is LockController
    }

}
