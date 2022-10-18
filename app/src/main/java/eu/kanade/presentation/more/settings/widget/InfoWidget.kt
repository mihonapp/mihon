package eu.kanade.presentation.more.settings.widget

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.secondaryItemAlpha
import eu.kanade.tachiyomi.R

@Composable
internal fun InfoWidget(text: String) {
    Column(
        modifier = Modifier
            .padding(horizontal = PrefsHorizontalPadding, vertical = 16.dp)
            .secondaryItemAlpha(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(
    name = "Light",
    showBackground = true,
)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,

)
@Composable
private fun InfoWidgetPreview() {
    TachiyomiTheme {
        Surface {
            InfoWidget(text = stringResource(id = R.string.download_ahead_info))
        }
    }
}
