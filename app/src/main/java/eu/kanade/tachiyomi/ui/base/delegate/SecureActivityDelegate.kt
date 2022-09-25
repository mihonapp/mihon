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
import java.util.Date

interface SecureActivityDelegate {
    fun registerSecureActivity(activity: AppCompatActivity)

    companion object {
        fun onApplicationCreated() {
            val lockDelay = Injekt.get<SecurityPreferences>().lockAppAfter().get()
            if (lockDelay <= 0) {
                // Restore always active/on start app lock
                // Delayed lock will be restored later on activity resume
                lockState = LockState.ACTIVE
            }
        }

        fun onApplicationStopped() {
            val preferences = Injekt.get<SecurityPreferences>()
            if (!preferences.useAuthenticator().get()) return
            if (lockState != LockState.ACTIVE) {
                preferences.lastAppClosed().set(Date().time)
            }
            if (!AuthenticatorUtil.isAuthenticating) {
                val lockAfter = preferences.lockAppAfter().get()
                lockState = if (lockAfter > 0) {
                    LockState.PENDING
                } else if (lockAfter == -1) {
                    // Never lock on idle
                    LockState.INACTIVE
                } else {
                    LockState.ACTIVE
                }
            }
        }

        fun unlock() {
            lockState = LockState.INACTIVE
            Injekt.get<SecurityPreferences>().lastAppClosed().delete()
        }
    }
}

private var lockState = LockState.INACTIVE

private enum class LockState {
    INACTIVE,
    PENDING,
    ACTIVE,
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
            updatePendingLockStatus()
            if (!isAppLocked()) return
            activity.startActivity(Intent(activity, UnlockActivity::class.java))
            activity.overridePendingTransition(0, 0)
        } else {
            securityPreferences.useAuthenticator().set(false)
        }
    }

    private fun updatePendingLockStatus() {
        val lastClosedPref = securityPreferences.lastAppClosed()
        val lockDelay = 60000 * securityPreferences.lockAppAfter().get()
        if (lastClosedPref.isSet() && lockDelay > 0) {
            // Restore pending status in case app was killed
            lockState = LockState.PENDING
        }
        if (lockState != LockState.PENDING) {
            return
        }
        if (Date().time >= lastClosedPref.get() + lockDelay) {
            // Activate lock after delay
            lockState = LockState.ACTIVE
        }
    }

    private fun isAppLocked(): Boolean {
        return lockState == LockState.ACTIVE
    }
}
