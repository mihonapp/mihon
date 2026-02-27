package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class GuidesStep(
    private val onRestoreBackup: () -> Unit,
) : OnboardingStep {

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val handler = LocalUriHandler.current

        // Create storage directory when arriving on this page
        LaunchedEffect(Unit) {
            try {
                val storagePref = Injekt.get<StoragePreferences>().baseStorageDirectory()
                val storageUri = storagePref.get()

                if (storageUri.isNotEmpty()) {
                    // Safely parse URI
                    val uri = try {
                        java.net.URI(storageUri)
                    } catch (e: java.net.URISyntaxException) {
                        logcat(LogPriority.ERROR) { "Invalid storage URI: $storageUri" }
                        return@LaunchedEffect
                    }

                    // Validate URI scheme (must be file://)
                    if (uri.scheme != null && uri.scheme != "file") {
                        logcat(LogPriority.WARN) { "Non-file URI scheme: ${uri.scheme}, skipping directory creation" }
                        return@LaunchedEffect
                    }

                    val storageFile = try {
                        java.io.File(uri)
                    } catch (e: IllegalArgumentException) {
                        logcat(LogPriority.ERROR) { "Cannot create File from URI: $uri" }
                        return@LaunchedEffect
                    }

                    val fullPath = storageFile.absolutePath

                    if (!storageFile.exists()) {
                        val created = storageFile.mkdirs()
                        logcat(LogPriority.INFO) { "Storage directory created: $fullPath (success: $created)" }

                        if (created && storageFile.exists()) {
                            context.toast(MR.strings.folder_created_successfully)
                        } else if (!storageFile.exists()) {
                            context.toast(MR.strings.error_path_cannot_create)
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Error creating storage directory: ${e.message}" }
                context.toast(MR.strings.error_path_cannot_create)
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(stringResource(MR.strings.onboarding_guides_new_user, stringResource(MR.strings.app_name)))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { handler.openUri(GETTING_STARTED_URL) },
            ) {
                Text(stringResource(MR.strings.getting_started_guide))
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(stringResource(MR.strings.onboarding_guides_returning_user, stringResource(MR.strings.app_name)))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRestoreBackup,
            ) {
                Text(stringResource(MR.strings.pref_restore_backup))
            }
        }
    }
}

const val GETTING_STARTED_URL = "https://mihon.app/docs/guides/getting-started"

@PreviewLightDark
@Composable
private fun GuidesStepPreview() {
    TachiyomiPreviewTheme {
        GuidesStep(
            onRestoreBackup = {},
        ).Content()
    }
}
