package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.readingModeType
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import tachiyomi.presentation.core.components.SettingsChipRow
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

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            SettingsChipRow(R.string.pref_category_reading_mode) {
                readingModeOptions.map { (stringRes, it) ->
                    FilterChip(
                        selected = it == readingMode,
                        onClick = {
                            screenModel.onChangeReadingMode(it)
                            onChange(stringRes)
                        },
                        label = { Text(stringResource(stringRes)) },
                    )
                }
            }
        }
    }
}
