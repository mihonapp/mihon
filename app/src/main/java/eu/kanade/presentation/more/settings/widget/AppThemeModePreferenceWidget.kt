package eu.kanade.presentation.more.settings.widget

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.domain.ui.model.ThemeMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    mapOf(
        ThemeMode.SYSTEM to MR.strings.theme_system,
        ThemeMode.LIGHT to MR.strings.theme_light,
        ThemeMode.DARK to MR.strings.theme_dark,
    )
} else {
    mapOf(
        ThemeMode.LIGHT to MR.strings.theme_light,
        ThemeMode.DARK to MR.strings.theme_dark,
    )
}

@Composable
internal fun AppThemeModePreferenceWidget(
    value: ThemeMode,
    onItemClick: (ThemeMode) -> Unit,
) {
    BasePreferenceWidget(
        subcomponent = {
            MultiChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrefsHorizontalPadding),
            ) {
                options.onEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        checked = mode == value,
                        onCheckedChange = { onItemClick(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            options.size,
                        ),
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
    )
}
