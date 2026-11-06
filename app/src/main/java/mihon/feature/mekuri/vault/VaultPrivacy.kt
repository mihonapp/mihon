package mihon.feature.mekuri.vault

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Mekuri: while the vault is open, keep the screen out of screenshots and the recent-apps preview.
 * Leaves the flag alone if something else (Mihon's secure-screen setting) already set it.
 */
@Composable
fun VaultScreenPrivacy() {
    val unlocked by MekuriVault.unlocked.collectAsStateWithLifecycle()
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(unlocked, window) {
        val alreadySecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        val added = unlocked && !alreadySecure
        if (added) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (added) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
