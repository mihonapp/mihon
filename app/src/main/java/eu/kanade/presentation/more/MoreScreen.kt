package eu.kanade.presentation.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    isFDroid: Boolean,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickBackupAndRestore: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    ScrollbarLazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = TachiyomiBottomNavigationView.withBottomNavPadding(
            WindowInsets.navigationBars.asPaddingValues(),
        ),
    ) {
        if (isFDroid) {
            item {
                WarningBanner(
                    textRes = R.string.fdroid_warning,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://tachiyomi.org/help/faq/#how-do-i-migrate-from-the-f-droid-version")
                    },
                )
            }
        }

        item {
            LogoHeader()
        }

        item {
            AppStateBanners(
                downloadedOnlyMode = downloadedOnly,
                incognitoMode = incognitoMode,
            )
        }

        item {
            SwitchPreferenceWidget(
                title = stringResource(R.string.label_downloaded_only),
                subtitle = stringResource(R.string.downloaded_only_summary),
                icon = Icons.Outlined.CloudOff,
                checked = downloadedOnly,
                onCheckedChanged = onDownloadedOnlyChange,
            )
        }
        item {
            SwitchPreferenceWidget(
                title = stringResource(R.string.pref_incognito_mode),
                subtitle = stringResource(R.string.pref_incognito_mode_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                checked = incognitoMode,
                onCheckedChanged = onIncognitoModeChange,
            )
        }

        item { Divider() }

        item {
            val downloadQueueState = downloadQueueStateProvider()
            TextPreferenceWidget(
                title = stringResource(R.string.label_download_queue),
                subtitle = when (downloadQueueState) {
                    DownloadQueueState.Stopped -> null
                    is DownloadQueueState.Paused -> {
                        val pending = downloadQueueState.pending
                        if (pending == 0) {
                            stringResource(R.string.paused)
                        } else {
                            "${stringResource(R.string.paused)} • ${
                            pluralStringResource(
                                id = R.plurals.download_queue_summary,
                                count = pending,
                                pending,
                            )
                            }"
                        }
                    }
                    is DownloadQueueState.Downloading -> {
                        val pending = downloadQueueState.pending
                        pluralStringResource(id = R.plurals.download_queue_summary, count = pending, pending)
                    }
                },
                icon = Icons.Outlined.GetApp,
                onPreferenceClick = onClickDownloadQueue,
            )
        }
        item {
            TextPreferenceWidget(
                title = stringResource(R.string.categories),
                icon = Icons.Outlined.Label,
                onPreferenceClick = onClickCategories,
            )
        }
        item {
            TextPreferenceWidget(
                title = stringResource(R.string.label_backup),
                icon = Icons.Outlined.SettingsBackupRestore,
                onPreferenceClick = onClickBackupAndRestore,
            )
        }

        item { Divider() }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.label_settings),
                icon = Icons.Outlined.Settings,
                onPreferenceClick = onClickSettings,
            )
        }
        item {
            TextPreferenceWidget(
                title = stringResource(R.string.pref_category_about),
                icon = Icons.Outlined.Info,
                onPreferenceClick = onClickAbout,
            )
        }
        item {
            TextPreferenceWidget(
                title = stringResource(R.string.label_help),
                icon = Icons.Outlined.HelpOutline,
                onPreferenceClick = { uriHandler.openUri(MoreController.URL_HELP) },
            )
        }
    }
}
