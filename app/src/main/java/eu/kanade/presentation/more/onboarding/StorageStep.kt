package eu.kanade.presentation.more.onboarding

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun StorageStep(
    storagePref: Preference<String>,
) {
    val context = LocalContext.current
    val pickStorageLocation = SettingsDataScreen.storageLocationPicker(storagePref)

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    }
}
