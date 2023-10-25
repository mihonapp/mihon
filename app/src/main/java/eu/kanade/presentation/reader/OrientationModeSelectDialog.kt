package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.orientationType
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.presentation.core.components.SettingsIconGrid
import tachiyomi.presentation.core.components.material.IconToggleButton
import tachiyomi.presentation.core.util.ThemePreviews

@Composable
fun OrientationModeSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (Int) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val orientationType = remember(manga) { OrientationType.fromPreference(manga?.orientationType?.toInt()) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        DialogContent(
            orientationType = orientationType,
            onChangeOrientation = {
                screenModel.onChangeOrientation(it)
                onChange(it.stringRes)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun DialogContent(
    orientationType: OrientationType,
    onChangeOrientation: (OrientationType) -> Unit,
) {
    Box(modifier = Modifier.padding(vertical = 16.dp)) {
        SettingsIconGrid(R.string.rotation_type) {
            items(OrientationType.entries) { mode ->
                IconToggleButton(
                    checked = mode == orientationType,
                    onCheckedChange = {
                        onChangeOrientation(mode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    imageVector = ImageVector.vectorResource(mode.iconRes),
                    title = stringResource(mode.stringRes),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun DialogContentPreview() {
    TachiyomiTheme {
        DialogContent(
            orientationType = OrientationType.DEFAULT,
            onChangeOrientation = {},
        )
    }
}
