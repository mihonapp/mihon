package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.Material3RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.InfoScreen
import tachiyomi.presentation.core.util.ThemePreviews

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    onOpenInBrowser: () -> Unit,
    onRejectUpdate: () -> Unit,
    onAcceptUpdate: () -> Unit,
) {
    InfoScreen(
        icon = Icons.Outlined.NewReleases,
        headingText = stringResource(R.string.update_check_notification_update_available),
        subtitleText = versionName,
        acceptText = stringResource(R.string.update_check_confirm),
        onAcceptClick = onAcceptUpdate,
        rejectText = stringResource(R.string.action_not_now),
        onRejectClick = onRejectUpdate,
    ) {
        Material3RichText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.large),
            style = RichTextStyle(
                stringStyle = RichTextStringStyle(
                    linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                ),
            ),
        ) {
            Markdown(content = changelogInfo)

            TextButton(
                onClick = onOpenInBrowser,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(R.string.update_check_open))
                Spacer(modifier = Modifier.width(MaterialTheme.padding.tiny))
                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null)
            }
        }
    }
}

@ThemePreviews
@Composable
private fun NewUpdateScreenPreview() {
    TachiyomiTheme {
        NewUpdateScreen(
            versionName = "v0.99.9",
            changelogInfo = """
                ## Yay
                Foobar
                
                ### More info
                - Hello
                - World
            """.trimIndent(),
            onOpenInBrowser = {},
            onRejectUpdate = {},
            onAcceptUpdate = {},
        )
    }
}
