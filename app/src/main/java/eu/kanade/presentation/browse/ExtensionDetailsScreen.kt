package eu.kanade.presentation.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsPresenter
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionSourceItem
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun ExtensionDetailsScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: ExtensionDetailsPresenter,
    onClickUninstall: () -> Unit,
    onClickAppInfo: () -> Unit,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
) {
    val extension = presenter.extension

    if (extension == null) {
        EmptyScreen(textResource = R.string.empty_screen)
        return
    }

    val sources by presenter.sourcesState.collectAsState()

    LazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        if (extension.isObsolete) {
            item {
                WarningBanner(R.string.obsolete_extension_message)
            }
        }

        if (extension.isUnofficial) {
            item {
                WarningBanner(R.string.unofficial_extension_message)
            }
        }

        item {
            DetailsHeader(extension, onClickUninstall, onClickAppInfo)
        }

        items(
            items = sources,
            key = { it.source.id },
        ) { source ->
            SourceSwitchPreference(
                modifier = Modifier.animateItemPlacement(),
                source = source,
                onClickSourcePreferences = onClickSourcePreferences,
                onClickSource = onClickSource,
            )
        }
    }
}

@Composable
private fun WarningBanner(@StringRes textRes: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(textRes),
            color = MaterialTheme.colorScheme.onError,
        )
    }
}

@Composable
private fun DetailsHeader(
    extension: Extension,
    onClickUninstall: () -> Unit,
    onClickAppInfo: () -> Unit,
) {
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 16.dp,
                bottom = 8.dp,
            ),
        ) {
            ExtensionIcon(
                modifier = Modifier
                    .height(56.dp)
                    .width(56.dp),
                extension = extension,
            )

            Column(
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.ext_version_info, extension.versionName),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.ext_language_info, LocaleHelper.getSourceDisplayName(extension.lang, context)),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (extension.isNsfw) {
                    Text(
                        text = stringResource(R.string.ext_nsfw_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = extension.pkgName,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 8.dp,
                bottom = 16.dp,
            ),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onClickUninstall,
            ) {
                Text(stringResource(R.string.ext_uninstall))
            }

            Spacer(Modifier.width(16.dp))

            Button(
                modifier = Modifier.weight(1f),
                onClick = onClickAppInfo,
            ) {
                Text(
                    text = stringResource(R.string.ext_app_info),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Divider()
    }
}

@Composable
private fun SourceSwitchPreference(
    modifier: Modifier = Modifier,
    source: ExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
) {
    val context = LocalContext.current

    PreferenceRow(
        modifier = modifier,
        title = if (source.labelAsName) {
            source.source.toString()
        } else {
            LocaleHelper.getSourceDisplayName(source.source.lang, context)
        },
        onClick = { onClickSource(source.source.id) },
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (source.source is ConfigurableSource) {
                    IconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.label_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Switch(checked = source.enabled, onCheckedChange = null)
            }
        },
    )
}
