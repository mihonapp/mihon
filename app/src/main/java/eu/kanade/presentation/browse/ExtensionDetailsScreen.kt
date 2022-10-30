package eu.kanade.presentation.browse

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DIVIDER_ALPHA
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsPresenter
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionSourceItem
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun ExtensionDetailsScreen(
    navigateUp: () -> Unit,
    presenter: ExtensionDetailsPresenter,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(R.string.label_extension_info),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        actions = buildList {
                            if (presenter.extension?.isUnofficial == false) {
                                add(
                                    AppBar.Action(
                                        title = stringResource(R.string.whats_new),
                                        icon = Icons.Outlined.History,
                                        onClick = { uriHandler.openUri(presenter.getChangelogUrl()) },
                                    ),
                                )
                                add(
                                    AppBar.Action(
                                        title = stringResource(R.string.action_faq_and_guides),
                                        icon = Icons.Outlined.HelpOutline,
                                        onClick = { uriHandler.openUri(presenter.getReadmeUrl()) },
                                    ),
                                )
                            }
                            addAll(
                                listOf(
                                    AppBar.OverflowAction(
                                        title = stringResource(R.string.action_enable_all),
                                        onClick = { presenter.toggleSources(true) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(R.string.action_disable_all),
                                        onClick = { presenter.toggleSources(false) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(R.string.pref_clear_cookies),
                                        onClick = { presenter.clearCookies() },
                                    ),
                                ),
                            )
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        ExtensionDetails(paddingValues, presenter, onClickSourcePreferences)
    }
}

@Composable
private fun ExtensionDetails(
    contentPadding: PaddingValues,
    presenter: ExtensionDetailsPresenter,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
) {
    when {
        presenter.isLoading -> LoadingScreen()
        presenter.extension == null -> EmptyScreen(
            textResource = R.string.empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            val context = LocalContext.current
            val extension = presenter.extension
            var showNsfwWarning by remember { mutableStateOf(false) }

            ScrollbarLazyColumn(
                contentPadding = contentPadding,
            ) {
                when {
                    extension.isUnofficial ->
                        item {
                            WarningBanner(R.string.unofficial_extension_message)
                        }
                    extension.isObsolete ->
                        item {
                            WarningBanner(R.string.obsolete_extension_message)
                        }
                }

                item {
                    DetailsHeader(
                        extension = extension,
                        onClickUninstall = { presenter.uninstallExtension() },
                        onClickAppInfo = {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", extension.pkgName, null)
                                context.startActivity(this)
                            }
                        },
                        onClickAgeRating = {
                            showNsfwWarning = true
                        },
                    )
                }

                items(
                    items = presenter.sources,
                    key = { it.source.id },
                ) { source ->
                    SourceSwitchPreference(
                        modifier = Modifier.animateItemPlacement(),
                        source = source,
                        onClickSourcePreferences = onClickSourcePreferences,
                        onClickSource = { presenter.toggleSource(it) },
                    )
                }
            }
            if (showNsfwWarning) {
                NsfwWarningDialog(
                    onClickConfirm = {
                        showNsfwWarning = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailsHeader(
    extension: Extension,
    onClickAgeRating: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickAppInfo: () -> Unit,
) {
    val context = LocalContext.current

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 16.dp,
                    bottom = 8.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExtensionIcon(
                modifier = Modifier
                    .size(112.dp),
                extension = extension,
                density = DisplayMetrics.DENSITY_XXXHIGH,
            )

            Text(
                text = extension.name,
                style = MaterialTheme.typography.headlineSmall,
            )

            val strippedPkgName = extension.pkgName.substringAfter("eu.kanade.tachiyomi.extension.")

            Text(
                text = strippedPkgName,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = horizontalPadding * 2,
                    vertical = 8.dp,
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoText(
                modifier = Modifier.weight(1f),
                primaryText = extension.versionName,
                secondaryText = stringResource(R.string.ext_info_version),
            )

            InfoDivider()

            InfoText(
                modifier = Modifier.weight(if (extension.isNsfw) 1.5f else 1f),
                primaryText = LocaleHelper.getSourceDisplayName(extension.lang, context),
                secondaryText = stringResource(R.string.ext_info_language),
            )

            if (extension.isNsfw) {
                InfoDivider()

                InfoText(
                    modifier = Modifier.weight(1f),
                    primaryText = stringResource(R.string.ext_nsfw_short),
                    primaryTextStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    ),
                    secondaryText = stringResource(R.string.ext_info_age_rating),
                    onClick = onClickAgeRating,
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
private fun InfoText(
    modifier: Modifier,
    primaryText: String,
    primaryTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    secondaryText: String,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(interactionSource, indication = null) { onClick() }
    } else {
        Modifier
    }

    Column(
        modifier = modifier.then(clickableModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = primaryTextStyle,
        )

        Text(
            text = secondaryText + if (onClick != null) " ⓘ" else "",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun InfoDivider() {
    Divider(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA),
    )
}

@Composable
private fun SourceSwitchPreference(
    modifier: Modifier = Modifier,
    source: ExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
) {
    val context = LocalContext.current

    TextPreferenceWidget(
        modifier = modifier,
        title = if (source.labelAsName) {
            source.source.toString()
        } else {
            LocaleHelper.getSourceDisplayName(source.source.lang, context)
        },
        widget = {
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

                Switch(
                    checked = source.enabled,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                )
            }
        },
        onPreferenceClick = { onClickSource(source.source.id) },
    )
}

@Composable
private fun NsfwWarningDialog(
    onClickConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(text = stringResource(R.string.ext_nsfw_warning))
        },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        onDismissRequest = onClickConfirm,
    )
}
