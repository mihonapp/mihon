package eu.kanade.presentation.more.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.LinkIcon
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.copyToClipboard

@Composable
fun AboutScreen(
    nestedScrollInterop: NestedScrollConnection,
    checkVersion: () -> Unit,
    getFormattedBuildTime: () -> String,
    onClickLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item {
            LogoHeader()
        }

        item {
            PreferenceRow(
                title = stringResource(R.string.version),
                subtitle = when {
                    BuildConfig.DEBUG -> {
                        "Debug ${BuildConfig.COMMIT_SHA} (${getFormattedBuildTime()})"
                    }
                    BuildConfig.PREVIEW -> {
                        "Preview r${BuildConfig.COMMIT_COUNT} (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                    }
                    else -> {
                        "Stable ${BuildConfig.VERSION_NAME} (${getFormattedBuildTime()})"
                    }
                },
                onClick = {
                    val deviceInfo = CrashLogUtil(context).getDebugInfo()
                    context.copyToClipboard("Debug information", deviceInfo)
                },
            )
        }

        if (BuildConfig.INCLUDE_UPDATER) {
            item {
                PreferenceRow(
                    title = stringResource(R.string.check_for_updates),
                    onClick = checkVersion,
                )
            }
        }
        if (!BuildConfig.DEBUG) {
            item {
                PreferenceRow(
                    title = stringResource(R.string.whats_new),
                    onClick = { uriHandler.openUri(RELEASE_URL) },
                )
            }
        }

        item {
            PreferenceRow(
                title = stringResource(R.string.help_translate),
                onClick = { uriHandler.openUri("https://tachiyomi.org/help/contribution/#translation") },
            )
        }

        item {
            PreferenceRow(
                title = stringResource(R.string.licenses),
                onClick = onClickLicenses,
            )
        }

        item {
            PreferenceRow(
                title = stringResource(R.string.privacy_policy),
                onClick = { uriHandler.openUri("https://tachiyomi.org/privacy") },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                LinkIcon(
                    label = stringResource(R.string.website),
                    painter = rememberVectorPainter(Icons.Outlined.Public),
                    url = "https://tachiyomi.org",
                )
                LinkIcon(
                    label = "Discord",
                    painter = painterResource(R.drawable.ic_discord_24dp),
                    url = "https://discord.gg/tachiyomi",
                )
                LinkIcon(
                    label = "Twitter",
                    painter = painterResource(R.drawable.ic_twitter_24dp),
                    url = "https://twitter.com/tachiyomiorg",
                )
                LinkIcon(
                    label = "Facebook",
                    painter = painterResource(R.drawable.ic_facebook_24dp),
                    url = "https://facebook.com/tachiyomiorg",
                )
                LinkIcon(
                    label = "Reddit",
                    painter = painterResource(R.drawable.ic_reddit_24dp),
                    url = "https://www.reddit.com/r/Tachiyomi",
                )
                LinkIcon(
                    label = "GitHub",
                    painter = painterResource(R.drawable.ic_github_24dp),
                    url = "https://github.com/tachiyomiorg",
                )
            }
        }
    }
}
