package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import eu.kanade.domain.manga.model.readingModeType
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import tachiyomi.presentation.core.components.SettingsIconGrid
import tachiyomi.presentation.core.components.material.IconToggleButton
import tachiyomi.presentation.core.components.material.padding

private val readingModeOptions = ReadingModeType.entries.map { it.stringRes to it }

@Composable
fun ReadingModeSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (Int) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val readingMode = remember(manga) { ReadingModeType.fromPreference(manga?.readingModeType?.toInt()) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Box(modifier = Modifier.padding(vertical = MaterialTheme.padding.medium)) {
            SettingsIconGrid(R.string.pref_category_reading_mode) {
                items(readingModeOptions) { (stringRes, mode) ->
                    IconToggleButton(
                        checked = mode == readingMode,
                        onCheckedChange = {
                            screenModel.onChangeReadingMode(mode)
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
