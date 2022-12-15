package eu.kanade.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

val DownloadedOnlyBannerBackgroundColor
    @Composable get() = MaterialTheme.colorScheme.tertiary
val IncognitoModeBannerBackgroundColor
    @Composable get() = MaterialTheme.colorScheme.primary

@Composable
fun WarningBanner(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(textRes),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error)
            .padding(16.dp),
        color = MaterialTheme.colorScheme.onError,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun AppStateBanners(
    downloadedOnlyMode: Boolean,
    incognitoMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    Column(modifier = modifier) {
        if (downloadedOnlyMode) {
            DownloadedOnlyModeBanner(
                modifier = Modifier.windowInsetsPadding(insets),
            )
        }
        if (incognitoMode) {
            IncognitoModeBanner(
                modifier = if (!downloadedOnlyMode) {
                    Modifier.windowInsetsPadding(insets)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun DownloadedOnlyModeBanner(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.label_downloaded_only),
        modifier = Modifier
            .background(DownloadedOnlyBannerBackgroundColor)
            .fillMaxWidth()
            .padding(4.dp)
            .then(modifier),
        color = MaterialTheme.colorScheme.onTertiary,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun IncognitoModeBanner(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.pref_incognito_mode),
        modifier = Modifier
            .background(IncognitoModeBannerBackgroundColor)
            .fillMaxWidth()
            .padding(4.dp)
            .then(modifier),
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
    )
}
