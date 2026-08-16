package eu.kanade.presentation.more.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class StorageStep : OnboardingStep {

    private val storagePref = Injekt.get<StoragePreferences>().baseStorageDirectory
    private val folderProvider = Injekt.get<AndroidStorageFolderProvider>()

    private var _isComplete by mutableStateOf(false)

    // Whether the user opened the all-files-access settings screen from this step. Used to set the
    // default location automatically once they return having granted it.
    private var allFilesAccessRequested = false

    override val isComplete: Boolean
        get() = _isComplete

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val handler = LocalUriHandler.current

        val pickStorageLocation = SettingsDataScreen.storageLocationPicker(storagePref)

        // The all-files-access escape hatch only exists on Android 11+. On older versions the
        // legacy storage permission already covers the default shared-storage folder.
        val allFilesAccessSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        if (allFilesAccessSupported) {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner.lifecycle) {
                val observer = object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        if (allFilesAccessRequested &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            Environment.isExternalStorageManager()
                        ) {
                            allFilesAccessRequested = false
                            useDefaultStorageLocation()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                stringResource(
                    MR.strings.onboarding_storage_info,
                    stringResource(MR.strings.app_name),
                    SettingsDataScreen.storageLocationText(storagePref),
                ),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        pickStorageLocation.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        context.toast(MR.strings.file_picker_error)
                    }
                },
            ) {
                Text(stringResource(MR.strings.onboarding_storage_action_select))
            }

            // Fallback for devices where the system folder picker won't grant access to any folder
            // ("Can't use this folder"). With all-files access the app can write to shared storage
            // directly, so it falls back to its default folder there.
            if (allFilesAccessSupported) {
                Text(
                    stringResource(
                        MR.strings.onboarding_storage_all_files_info,
                        stringResource(MR.strings.app_name),
                    ),
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (Environment.isExternalStorageManager()) {
                                useDefaultStorageLocation()
                            } else {
                                requestAllFilesAccess(context)
                            }
                        }
                    },
                ) {
                    Text(stringResource(MR.strings.onboarding_storage_action_all_files))
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(stringResource(MR.strings.onboarding_storage_help_info, stringResource(MR.strings.app_name)))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { handler.openUri(SettingsDataScreen.HELP_URL) },
            ) {
                Text(stringResource(MR.strings.onboarding_storage_help_action))
            }
        }

        LaunchedEffect(Unit) {
            storagePref.changes()
                .collectLatest { _isComplete = storagePref.isSet() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestAllFilesAccess(context: Context) {
        allFilesAccessRequested = true
        val packageUri = "package:${context.packageName}".toUri()
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
            )
        } catch (e: ActivityNotFoundException) {
            // Some OEMs don't expose the per-app screen; fall back to the global list.
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e: ActivityNotFoundException) {
                allFilesAccessRequested = false
                context.toast(MR.strings.file_picker_error)
            }
        }
    }

    private fun useDefaultStorageLocation() {
        // Ensure the folder exists before pointing the app at it, since StorageManager only creates
        // its subdirectories when the base directory already resolves.
        folderProvider.directory().mkdirs()
        storagePref.set(folderProvider.path())
    }
}
