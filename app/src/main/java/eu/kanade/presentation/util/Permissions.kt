package eu.kanade.presentation.util

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun rememberRequestPackageInstallsPermissionState(initialValue: Boolean = false): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var installGranted by remember { mutableStateOf(initialValue) }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                installGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else {
                    @Suppress("DEPRECATION")
                    Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) != 0
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return installGranted
}
