package eu.kanade.presentation.more.settings.database.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.util.selectedBackground
import eu.kanade.tachiyomi.R

@Composable
fun ClearDatabaseItem(
    source: Source,
    count: Long,
    isSelected: Boolean,
    onClickSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .clickable(onClick = onClickSelect)
            .padding(horizontal = 8.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(source = source)
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Text(
                text = source.visualName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = stringResource(id = R.string.clear_database_source_item_count, count))
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClickSelect() },
        )
    }
}
