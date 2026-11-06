package mihon.feature.mekuri.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.toast

/**
 * Mekuri: asks for the phone's fingerprint / face / PIN and opens the vault on success.
 * Without any screen lock set there is nothing to check against, so the vault opens with a reminder.
 */
suspend fun Context.unlockMekuriVault(): Boolean {
    val activity = findFragmentActivity() ?: return false
    if (!activity.isAuthenticationSupported()) {
        activity.toast("Set a screen lock (fingerprint or PIN) in your phone's settings to protect the vault")
        MekuriVault.unlock()
        return true
    }
    val ok = activity.authenticate(title = "Open the vault", subtitle = null)
    if (ok) MekuriVault.unlock()
    return ok
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var context: Context? = this
    while (context != null) {
        if (context is FragmentActivity) return context
        context = (context as? ContextWrapper)?.baseContext
    }
    return null
}
