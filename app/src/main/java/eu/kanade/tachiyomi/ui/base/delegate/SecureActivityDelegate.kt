package eu.kanade.tachiyomi.ui.base.delegate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.security.UnlockActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.view.setSecureScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mihon.app.di.appGraph

interface SecureActivityDelegate {
    fun registerSecureActivity(activity: AppCompatActivity)

    companion object {
        /**
         * Set to true if we need the first activity to authenticate.
         *
         * Always require unlock if app is killed.
         */
        private var requireUnlock = true
        private var backgroundedAt: Long? = null

        fun onApplicationStopped(context: Context) {
            context.appGraph.hiddenLibrarySession.lock()
            backgroundedAt = SystemClock.elapsedRealtime()
        }

        /**
         * Checks if unlock is needed when app comes foreground.
         */
        fun onApplicationStart(context: Context) {
            val closedAt = backgroundedAt ?: return
            backgroundedAt = null
            val preferences = context.appGraph.securityPreferences
            if (!preferences.useAuthenticator.get()) return

            if (!AuthenticatorUtil.isAuthenticating && !requireUnlock) {
                val lockDelay = preferences.lockAppAfter.get()
                requireUnlock = lockDelay != -1 && SystemClock.elapsedRealtime() - closedAt >= lockDelay * 60_000L
            }
        }

        fun unlock() {
            requireUnlock = false
        }

        fun needsUnlock(preferences: SecurityPreferences): Boolean =
            preferences.useAuthenticator.get() && requireUnlock
    }
}

class SecureActivityDelegateImpl : SecureActivityDelegate, DefaultLifecycleObserver {

    private lateinit var activity: AppCompatActivity
    private var protectWhilePaused = false

    @Inject private lateinit var preferences: BasePreferences

    @Inject private lateinit var securityPreferences: SecurityPreferences

    override fun registerSecureActivity(activity: AppCompatActivity) {
        this.activity = activity
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        activity.appGraph.inject(this)
        updateSecureScreen()
        combine(
            securityPreferences.secureScreen.changes(),
            preferences.incognitoMode.changes(),
            activity.appGraph.hiddenLibrarySession.unlocked,
            securityPreferences.useAuthenticator.changes(),
        ) { _, _, _, _ -> Unit }
            .onEach { updateSecureScreen() }
            .launchIn(activity.lifecycleScope)
    }

    override fun onResume(owner: LifecycleOwner) {
        protectWhilePaused = false
        updateSecureScreen()
        setAppLock()
    }

    override fun onPause(owner: LifecycleOwner) {
        protectWhilePaused = securityPreferences.useAuthenticator.get() ||
            activity.appGraph.hiddenLibrarySession.unlocked.value
        updateSecureScreen()
    }

    private fun updateSecureScreen() {
        val secureScreen = securityPreferences.secureScreen.get()
        val hiddenUnlocked = activity.appGraph.hiddenLibrarySession.unlocked.value
        activity.window.setSecureScreen(
            protectWhilePaused || hiddenUnlocked || SecureActivityDelegate.needsUnlock(securityPreferences) ||
                secureScreen == SecurityPreferences.SecureScreenMode.ALWAYS ||
                (secureScreen == SecurityPreferences.SecureScreenMode.INCOGNITO && preferences.incognitoMode.get()),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(
                !protectWhilePaused && !securityPreferences.useAuthenticator.get() && !hiddenUnlocked,
            )
        }
    }

    private fun setAppLock() {
        val locked = SecureActivityDelegate.needsUnlock(securityPreferences)
        activity.findViewById<View>(android.R.id.content).isInvisible = locked
        if (!locked) return
        activity.startActivity(Intent(activity, UnlockActivity::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}
