package mihon.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.onboarding.OnboardingStep
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.sync.job.SyncJob
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Offers to link a Google account while the app is being set up, which is when someone moving to a new
 * device wants their library back; otherwise they have to know to look for the sync in the settings.
 *
 * Linking from here also turns the sync on, since syncing is the only reason to link at this point, and
 * the first round starts as soon as the account is linked. The step is optional and never holds the
 * setup back.
 */
internal class SyncOnboardingStep : OnboardingStep {

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val auth = remember { context.appGraph.googleDriveAuth }
        val syncPreferences = remember { context.appGraph.syncPreferences }
        val accountEmail by syncPreferences.accountEmail().collectAsState()
        val refreshToken by syncPreferences.refreshToken().collectAsState()

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(stringResource(MR.strings.onboarding_sync_info, stringResource(MR.strings.app_name)))

            if (refreshToken.isNotBlank()) {
                Text(
                    text = stringResource(MR.strings.onboarding_sync_linked, accountEmail),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        syncPreferences.isEnabled().set(true)
                        SyncJob.setupTask(context)
                        try {
                            uriHandler.openUri(auth.buildAuthorizationUrl().toString())
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Could not open the Google sign-in page" }
                            context.toast(MR.strings.sync_login_failed)
                        }
                    },
                ) {
                    Text(stringResource(MR.strings.pref_sync_sign_in))
                }
                Text(
                    text = stringResource(MR.strings.onboarding_sync_later),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
