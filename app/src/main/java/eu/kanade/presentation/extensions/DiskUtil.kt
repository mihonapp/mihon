package eu.kanade.presentation.extensions

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.rememberPermissionState
import eu.kanade.tachiyomi.util.storage.DiskUtil

/**
 * Launches request for [Manifest.permission.WRITE_EXTERNAL_STORAGE] permission
 */
@Composable
fun DiskUtil.RequestStoragePermission() {
    val permissionState = rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
    LaunchedEffect(Unit) {
        permissionState.launchPermissionRequest()
    }
}
