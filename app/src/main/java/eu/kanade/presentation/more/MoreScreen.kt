package eu.kanade.presentation.more

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.more.MorePresenter
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView

@Composable
fun MoreScreen(
    presenter: MorePresenter,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickBackupAndRestore: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val downloadQueueState by presenter.downloadQueueState.collectAsState()

    ScrollbarLazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = TachiyomiBottomNavigationView.withBottomNavPadding(),
    ) {
        item {
            LogoHeader()
        }

        item {
            AppStateBanners(
                downloadedOnlyMode = presenter.downloadedOnly.value,
                incognitoMode = presenter.incognitoMode.value,
            )
        }

        item {
            SwitchPreferenceWidget(
                title = stringResource(R.string.label_downloaded_only),
                subtitle = stringResource(R.string.downloaded_only_summary),
                icon = Icons.Outlined.CloudOff,
                checked = presenter.downloadedOnly.value,
                onCheckedChanged = { presenter.downloadedOnly.value = it },
            )
        }
        item {
            SwitchPreferenceWidget(
                title = stringResource(R.string.pref_incognito_mode),
                subtitle = stringResource(R.string.pref_incognito_mode_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                checked = presenter.incognitoMode.value,
                onCheckedChanged = { presenter.incognitoMode.value = it },
            )
        }

        item { Divider() }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.label_download_queue),
                subtitle = when (downloadQueueState) {
                    DownloadQueueState.Stopped -> null
                    is DownloadQueueState.Paused -> {
                        val pending = (downloadQueueState as DownloadQueueState.Paused).pending
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
                        val pending = (downloadQueueState as DownloadQueueState.Downloading).pending
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
