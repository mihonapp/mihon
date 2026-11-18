package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CheckboxPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean = false,
    enabled: Boolean = true,
    onCheckedChanged: (Boolean) -> Unit,
) {
    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        widget = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.padding(start = TrailingWidgetBuffer),
            )
        },
        enabled = enabled,
        onPreferenceClick = { onCheckedChanged(!checked) },
    )
}
