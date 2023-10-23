package eu.kanade.presentation.permissions

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Launches request for [Manifest.permission.WRITE_EXTERNAL_STORAGE] permission
 */
object PermissionRequestHelper {

    @Composable
    fun requestStoragePermission() {
        val permissionState = rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
        LaunchedEffect(Unit) {
            permissionState.launchPermissionRequest()
        }
    }
}
