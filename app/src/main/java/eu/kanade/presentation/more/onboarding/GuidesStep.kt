package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun GuidesStep(
    onRestoreBackup: () -> Unit,
) {
    val handler = LocalUriHandler.current

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(MR.strings.onboarding_guides_new_user, stringResource(MR.strings.app_name)))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { handler.openUri(GETTING_STARTED_URL) },
        ) {
            Text(stringResource(MR.strings.getting_started_guide))
        }

        HorizontalDivider()

        Text(stringResource(MR.strings.onboarding_guides_returning_user, stringResource(MR.strings.app_name)))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRestoreBackup,
        ) {
            Text(stringResource(MR.strings.pref_restore_backup))
        }
    }
}

const val GETTING_STARTED_URL = "https://tachiyomi.org/docs/guides/getting-started"
