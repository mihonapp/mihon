package eu.kanade.tachiyomi.ui.base.delegate

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.security.UnlockActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.view.setSecureScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

interface SecureActivityDelegate {
    fun registerSecureActivity(activity: AppCompatActivity)

    companion object {
        /**
         * Set to true if we need the first activity to authenticate.
         *
         * Always require unlock if app is killed.
         */
        var requireUnlock = true

        fun onApplicationStopped() {
            val preferences = Injekt.get<SecurityPreferences>()
            if (!preferences.useAuthenticator().get()) return

            if (!AuthenticatorUtil.isAuthenticating) {
                // Return if app is closed in locked state
                if (requireUnlock) return
                // Save app close time if lock is delayed
                if (preferences.lockAppAfter().get() > 0) {
                    preferences.lastAppClosed().set(System.currentTimeMillis())
                }
            }
        }

        /**
         * Checks if unlock is needed when app comes foreground.
         */
        fun onApplicationStart() {
            val preferences = Injekt.get<SecurityPreferences>()
            if (!preferences.useAuthenticator().get()) return

            val lastClosedPref = preferences.lastAppClosed()

            // `requireUnlock` can be true on process start or if app was closed in locked state
            if (!AuthenticatorUtil.isAuthenticating && !requireUnlock) {
                requireUnlock = when (val lockDelay = preferences.lockAppAfter().get()) {
                    -1 -> false // Never
                    0 -> true // Always
                    else -> lastClosedPref.get() + lockDelay * 60_000 <= System.currentTimeMillis()
                }
            }

            lastClosedPref.delete()
        }

        fun unlock() {
            requireUnlock = false
        }
    }
}

class SecureActivityDelegateImpl : SecureActivityDelegate, DefaultLifecycleObserver {

    private lateinit var activity: AppCompatActivity

    private val preferences: BasePreferences by injectLazy()
    private val securityPreferences: SecurityPreferences by injectLazy()

    override fun registerSecureActivity(activity: AppCompatActivity) {
        this.activity = activity
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        setSecureScreen()
    }

    override fun onResume(owner: LifecycleOwner) {
        setAppLock()
    }

    private fun setSecureScreen() {
        val secureScreenFlow = securityPreferences.secureScreen().changes()
        val incognitoModeFlow = preferences.incognitoMode().changes()
        combine(secureScreenFlow, incognitoModeFlow) { secureScreen, incognitoMode ->
            secureScreen == SecurityPreferences.SecureScreenMode.ALWAYS ||
                secureScreen == SecurityPreferences.SecureScreenMode.INCOGNITO && incognitoMode
        }
            .onEach { activity.window.setSecureScreen(it) }
            .launchIn(activity.lifecycleScope)
    }

    private fun setAppLock() {
        if (!securityPreferences.useAuthenticator().get()) return
        if (activity.isAuthenticationSupported()) {
            if (!SecureActivityDelegate.requireUnlock) return
            activity.startActivity(Intent(activity, UnlockActivity::class.java))
            activity.overridePendingTransition(0, 0)
        } else {
            securityPreferences.useAuthenticator().set(false)
        }
    }
}
