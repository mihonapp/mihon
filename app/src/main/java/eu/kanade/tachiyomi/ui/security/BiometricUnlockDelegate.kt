package eu.kanade.tachiyomi.ui.security

import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.injectLazy
import java.util.Date

object BiometricUnlockDelegate {

    private val preferences by injectLazy<PreferencesHelper>()

    var locked: Boolean = true

    fun onResume(activity: FragmentActivity) {
        val lockApp = preferences.useBiometricLock().getOrDefault()
        if (lockApp && BiometricManager.from(activity).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            if (isAppLocked()) {
                val intent = Intent(activity, BiometricUnlockActivity::class.java)
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
            }
        } else if (lockApp) {
            preferences.useBiometricLock().set(false)
        }
    }

    private fun isAppLocked(): Boolean {
        return locked &&
                (preferences.lockAppAfter().getOrDefault() <= 0
                        || Date().time >= preferences.lastAppUnlock().getOrDefault() + 60 * 1000 * preferences.lockAppAfter().getOrDefault())
    }

}
