package eu.kanade.tachiyomi.ui.base.delegate

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
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
        fun onApplicationStopped() {
            val preferences = Injekt.get<PreferencesHelper>()
            if (!preferences.useAuthenticator().get()) return
            if (lockState != LockState.ACTIVE) {
                preferences.lastAppClosed().set(Date().time)
            }
            if (!AuthenticatorUtil.isAuthenticating) {
                lockState = if (preferences.lockAppAfter().get() >= 0) {
                    LockState.PENDING
                } else {
                    LockState.ACTIVE
                }
            }
        }

        fun unlock() {
            lockState = LockState.INACTIVE
            Injekt.get<PreferencesHelper>().lastAppClosed().delete()
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

    private val preferences: PreferencesHelper by injectLazy()

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
        val secureScreenFlow = preferences.secureScreen().asFlow()
        val incognitoModeFlow = preferences.incognitoMode().asFlow()
        combine(secureScreenFlow, incognitoModeFlow) { secureScreen, incognitoMode ->
            secureScreen == PreferenceValues.SecureScreenMode.ALWAYS ||
                secureScreen == PreferenceValues.SecureScreenMode.INCOGNITO && incognitoMode
        }
            .onEach { activity.window.setSecureScreen(it) }
            .launchIn(activity.lifecycleScope)
    }

    private fun setAppLock() {
        if (!preferences.useAuthenticator().get()) return
        if (activity.isAuthenticationSupported()) {
            updatePendingLockStatus()
            if (!isAppLocked()) return
            activity.startActivity(Intent(activity, UnlockActivity::class.java))
            activity.overridePendingTransition(0, 0)
        } else {
            preferences.useAuthenticator().set(false)
        }
    }

    private fun updatePendingLockStatus() {
        val lastClosedPref = preferences.lastAppClosed()
        val lockDelay = 60000 * preferences.lockAppAfter().get()
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
