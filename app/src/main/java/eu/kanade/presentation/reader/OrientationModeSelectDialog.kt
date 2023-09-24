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
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.presentation.core.components.SettingsIconGrid
import tachiyomi.presentation.core.components.material.IconToggleButton

private val orientationTypeOptions = OrientationType.entries.map { it.stringRes to it }

@Composable
fun OrientationModeSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (Int) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val orientationType = remember(manga) { OrientationType.fromPreference(manga?.orientationType?.toInt()) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Box(modifier = Modifier.padding(vertical = 16.dp)) {
            SettingsIconGrid(R.string.rotation_type) {
                items(orientationTypeOptions) { (stringRes, mode) ->
                    IconToggleButton(
                        checked = mode == orientationType,
                        onCheckedChange = {
                            screenModel.onChangeOrientation(mode)
                            onChange(stringRes)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        imageVector = ImageVector.vectorResource(mode.iconRes),
                        title = stringResource(stringRes),
                    )
                }
            }
        }
    }
}
